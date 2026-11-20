#!/usr/bin/env node
'use strict';

/**
 * One-time migration, key generation & user creation utility:
 *
 * 1) Generate an Ed25519 keypair (never touches Firebase):
 *      node scripts/migrate-passwords.js --gen-keys
 *
 * 2) Hash a single password (for pasting into Firebase Console):
 *      node scripts/migrate-passwords.js --hash "YourPassword"
 *
 * 3) Add a new user directly to Firebase RTDB /User (with salted scrypt hash):
 *      FIREBASE_DB_URL="https://modx-lab-a5e41-default-rtdb.firebaseio.com" \
 *      FIREBASE_SERVICE_ACCOUNT_FILE="./service-account.json" \
 *      node scripts/migrate-passwords.js --add-user testuser TestPass123 30
 *
 * 4) Dry-run password migration (shows what would be hashed):
 *      FIREBASE_DB_URL="https://modx-lab-a5e41-default-rtdb.firebaseio.com" \
 *      FIREBASE_SERVICE_ACCOUNT_FILE="./service-account.json" \
 *      node scripts/migrate-passwords.js --dry-run
 *
 * 5) Execute password migration (writes passHash and deletes plaintext pass):
 *      FIREBASE_DB_URL="https://modx-lab-a5e41-default-rtdb.firebaseio.com" \
 *      FIREBASE_SERVICE_ACCOUNT_FILE="./service-account.json" \
 *      node scripts/migrate-passwords.js
 *
 * You may also pass FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_DB_SECRET instead of FIREBASE_SERVICE_ACCOUNT_FILE.
 */

const crypto = require('node:crypto');
const fs = require('node:fs');

const SCRYPT_N = 16384;
const SCRYPT_R = 8;
const SCRYPT_P = 1;
const SCRYPT_KEYLEN = 64;

function base64UrlEncode(buf) {
  return Buffer.from(buf)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function generateEd25519Keypair() {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');
  const pkcs8Der = privateKey.export({ format: 'der', type: 'pkcs8' });
  const spkiDer = publicKey.export({ format: 'der', type: 'spki' });

  // Ed25519 SPKI DER is 12-byte ASN.1 header + 32-byte raw public key
  const rawPublicHex = spkiDer.subarray(spkiDer.length - 32).toString('hex');
  // Ed25519 PKCS8 DER is 16-byte ASN.1 header + 32-byte raw seed
  const rawPrivateSeedHex = pkcs8Der.subarray(pkcs8Der.length - 32).toString('hex');

  console.log('=== Ed25519 Keypair Generated ===\n');
  console.log('1) Appwrite Function Env Var (ED25519_PRIVATE_KEY) [SECRET - SERVER ONLY]:');
  console.log(rawPrivateSeedHex);
  console.log('\n2) GitHub Secret / Security.cpp (ED25519_PUBLIC_KEY) [PUBLIC - EMBED IN APK]:');
  console.log(rawPublicHex);
  console.log('\nKeep the private key secret. Never put it in the Android repo or APK.');
}

function hashPasswordScrypt(password) {
  return new Promise((resolve, reject) => {
    const salt = crypto.randomBytes(16);
    crypto.scrypt(
      password,
      salt,
      SCRYPT_KEYLEN,
      { N: SCRYPT_N, r: SCRYPT_R, p: SCRYPT_P, maxmem: 64 * 1024 * 1024 },
      (err, derived) => {
        if (err) return reject(err);
        resolve(
          `scrypt$${SCRYPT_N}$${SCRYPT_R}$${SCRYPT_P}$${salt.toString('hex')}$${derived.toString('hex')}`
        );
      }
    );
  });
}

async function getFirebaseAuth() {
  let saRaw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON || '';
  if (!saRaw && process.env.FIREBASE_SERVICE_ACCOUNT_FILE) {
    saRaw = fs.readFileSync(process.env.FIREBASE_SERVICE_ACCOUNT_FILE, 'utf8');
  }

  if (saRaw.trim()) {
    const sa = JSON.parse(saRaw);
    const nowSec = Math.floor(Date.now() / 1000);
    const header = base64UrlEncode(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
    const claims = base64UrlEncode(
      JSON.stringify({
        iss: sa.client_email,
        scope:
          'https://www.googleapis.com/auth/firebase.database https://www.googleapis.com/auth/userinfo.email',
        aud: sa.token_uri || 'https://oauth2.googleapis.com/token',
        iat: nowSec,
        exp: nowSec + 3600,
      })
    );
    const signingInput = `${header}.${claims}`;
    const signer = crypto.createSign('RSA-SHA256');
    signer.update(signingInput);
    const assertion = `${signingInput}.${base64UrlEncode(signer.sign(sa.private_key))}`;

    const res = await fetch(sa.token_uri || 'https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        assertion,
      }),
    });
    if (!res.ok) throw new Error(`OAuth token request failed: HTTP ${res.status}`);
    const json = await res.json();
    return { headers: { Authorization: `Bearer ${json.access_token}` }, queryAuth: '' };
  }

  const secret = (process.env.FIREBASE_DB_SECRET || '').trim();
  if (secret) {
    return { headers: {}, queryAuth: `auth=${encodeURIComponent(secret)}` };
  }

  throw new Error(
    'Provide FIREBASE_SERVICE_ACCOUNT_FILE, FIREBASE_SERVICE_ACCOUNT_JSON, or FIREBASE_DB_SECRET'
  );
}

async function main() {
  const argv = process.argv.slice(2);
  const args = new Set(argv);
  if (args.has('--gen-keys')) {
    generateEd25519Keypair();
    return;
  }

  const hashIdx = argv.indexOf('--hash');
  if (hashIdx !== -1) {
    const pw = argv[hashIdx + 1];
    if (!pw) throw new Error('Usage: node scripts/migrate-passwords.js --hash "<password>"');
    console.log(await hashPasswordScrypt(pw));
    return;
  }

  const addIdx = argv.indexOf('--add-user');
  if (addIdx !== -1) {
    const rawUser = argv[addIdx + 1];
    const rawPass = argv[addIdx + 2];
    const days = Number(argv[addIdx + 3] || 30);
    if (!rawUser || !rawPass || !Number.isFinite(days) || days <= 0) {
      throw new Error('Usage: node scripts/migrate-passwords.js --add-user <username> <password> [days]');
    }
    const dbUrl = (process.env.FIREBASE_DB_URL || '').trim().replace(/\/+$/, '');
    if (!dbUrl) throw new Error('FIREBASE_DB_URL is required');
    const { headers: authHeaders, queryAuth } = await getFirebaseAuth();
    const buildUrl = (p) => (queryAuth ? `${dbUrl}${p}?${queryAuth}` : `${dbUrl}${p}`);
    const username = rawUser.trim().toLowerCase();
    const safeNode = username.replace(/[.#$\[\]\/]/g, '_');
    const passHash = await hashPasswordScrypt(rawPass);
    const putRes = await fetch(buildUrl(`/User/${encodeURIComponent(safeNode)}.json`), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...authHeaders },
      body: JSON.stringify({
        user: username,
        passHash,
        status: 'true',
        rgtime: Date.now(),
        duration_days: days,
      }),
    });
    if (!putRes.ok) throw new Error(`Failed to create user: HTTP ${putRes.status}`);
    console.log(`[CREATED] /User/${safeNode} (user=${username}, duration_days=${days})`);
    return;
  }

  const dryRun = args.has('--dry-run');
  const dbUrl = (process.env.FIREBASE_DB_URL || '').trim().replace(/\/+$/, '');
  if (!dbUrl) {
    throw new Error('FIREBASE_DB_URL is required');
  }

  const { headers: authHeaders, queryAuth } = await getFirebaseAuth();
  const buildUrl = (p) => (queryAuth ? `${dbUrl}${p}?${queryAuth}` : `${dbUrl}${p}`);

  const listRes = await fetch(buildUrl('/User.json'), { headers: authHeaders });
  if (!listRes.ok) {
    throw new Error(`Failed to fetch /User.json: HTTP ${listRes.status}`);
  }

  const users = await listRes.json();
  if (!users || typeof users !== 'object') {
    console.log('No users found under /User.');
    return;
  }

  let migrated = 0;
  let skipped = 0;

  for (const [nodeKey, record] of Object.entries(users)) {
    if (!record || typeof record !== 'object') {
      skipped++;
      continue;
    }

    const hasPlain = typeof record.pass === 'string' && record.pass.length > 0;
    const hasHash = typeof record.passHash === 'string' && record.passHash.startsWith('scrypt$');

    if (!hasPlain && hasHash) {
      skipped++;
      continue;
    }
    if (!hasPlain) {
      console.warn(`[WARN] /User/${nodeKey} (${record.user || '?'}) has neither pass nor passHash; skipping.`);
      skipped++;
      continue;
    }

    const passHash = hasHash ? record.passHash : await hashPasswordScrypt(record.pass);
    const normalizedUser =
      typeof record.user === 'string' ? record.user.trim().toLowerCase() : record.user;

    if (dryRun) {
      console.log(`[DRY-RUN] Would migrate /User/${nodeKey} (${normalizedUser}): set passHash, delete pass`);
      migrated++;
      continue;
    }

    const patchRes = await fetch(buildUrl(`/User/${encodeURIComponent(nodeKey)}.json`), {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders,
      },
      body: JSON.stringify({
        user: normalizedUser,
        passHash,
        pass: null,
      }),
    });

    if (!patchRes.ok) {
      throw new Error(`Failed to update /User/${nodeKey}: HTTP ${patchRes.status}`);
    }

    console.log(`[MIGRATED] /User/${nodeKey} (${normalizedUser})`);
    migrated++;
  }

  console.log(`\nDone. Migrated: ${migrated}, Skipped: ${skipped}${dryRun ? ' (dry-run)' : ''}`);
}

main().catch((err) => {
  console.error(`Error: ${err.message}`);
  process.exit(1);
});
