'use strict';

const crypto = require('node:crypto');

// ============================================================================
// Configuration (from Appwrite Function Environment Variables ONLY)
//   - FIREBASE_DB_URL: e.g. https://modx-lab-5a6ee-default-rtdb.firebaseio.com
//   - FIREBASE_SERVICE_ACCOUNT_JSON: JSON string of Firebase Service Account
//     (or FIREBASE_DB_SECRET as fallback legacy database secret)
//   - ED25519_PRIVATE_KEY: PKCS8 PEM or 64-hex (32-byte seed) / base64 PKCS8
// ============================================================================

const RATE_WINDOW_MS = 15 * 60 * 1000; // 15 minutes
const MAX_FAILED_PER_USER = 5;
const MAX_FAILED_PER_IP = 15;
const LOCKOUT_MS = 15 * 60 * 1000; // 15 minutes

// In-memory fallback rate-limit cache (per warm container) + RTDB persistence
const memRateCache = new Map();

// Cached Google OAuth2 access token for Firebase RTDB
let cachedGoogleToken = null;
let cachedGoogleTokenExpMs = 0;

function base64UrlEncode(buf) {
  return Buffer.from(buf)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function parseEd25519PrivateKey(raw) {
  if (!raw || typeof raw !== 'string') {
    throw new Error('ED25519_PRIVATE_KEY env var is missing');
  }
  const trimmed = raw.trim();
  if (trimmed.includes('BEGIN PRIVATE KEY')) {
    return crypto.createPrivateKey({
      key: trimmed.replace(/\\n/g, '\n'),
      format: 'pem',
      type: 'pkcs8',
    });
  }
  // 32-byte raw seed in hex (64 chars) -> wrap in RFC 8410 PKCS8 DER prefix
  if (/^[0-9a-fA-F]{64}$/.test(trimmed)) {
    const seed = Buffer.from(trimmed, 'hex');
    const pkcs8Prefix = Buffer.from('302e020100300506032b657004220420', 'hex');
    return crypto.createPrivateKey({
      key: Buffer.concat([pkcs8Prefix, seed]),
      format: 'der',
      type: 'pkcs8',
    });
  }
  // Base64 PKCS8 DER
  const der = Buffer.from(trimmed, 'base64');
  return crypto.createPrivateKey({ key: der, format: 'der', type: 'pkcs8' });
}

async function getFirebaseAuthParams() {
  const saRaw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (saRaw && saRaw.trim()) {
    const nowSec = Math.floor(Date.now() / 1000);
    if (cachedGoogleToken && Date.now() < cachedGoogleTokenExpMs - 60000) {
      return { headers: { Authorization: `Bearer ${cachedGoogleToken}` }, queryAuth: '' };
    }

    const sa = JSON.parse(saRaw);
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
    const signature = base64UrlEncode(signer.sign(sa.private_key));
    const assertion = `${signingInput}.${signature}`;

    const tokenRes = await fetch(sa.token_uri || 'https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        assertion,
      }),
    });

    if (!tokenRes.ok) {
      throw new Error(`OAuth token exchange failed: HTTP ${tokenRes.status}`);
    }
    const tokenJson = await tokenRes.json();
    cachedGoogleToken = tokenJson.access_token;
    cachedGoogleTokenExpMs = Date.now() + Number(tokenJson.expires_in || 3600) * 1000;
    return { headers: { Authorization: `Bearer ${cachedGoogleToken}` }, queryAuth: '' };
  }

  const dbSecret = (process.env.FIREBASE_DB_SECRET || '').trim();
  if (dbSecret) {
    return { headers: {}, queryAuth: `auth=${encodeURIComponent(dbSecret)}` };
  }

  throw new Error('Missing FIREBASE_SERVICE_ACCOUNT_JSON (or FIREBASE_DB_SECRET)');
}

async function rtdbFetch(pathWithQuery, options = {}) {
  const baseUrl = (process.env.FIREBASE_DB_URL || '').trim().replace(/\/+$/, '');
  if (!baseUrl) {
    throw new Error('FIREBASE_DB_URL env var is missing');
  }
  const { headers: authHeaders, queryAuth } = await getFirebaseAuthParams();
  const sep = pathWithQuery.includes('?') ? '&' : '?';
  const fullUrl = queryAuth
    ? `${baseUrl}${pathWithQuery}${sep}${queryAuth}`
    : `${baseUrl}${pathWithQuery}`;

  const res = await fetch(fullUrl, {
    method: options.method || 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders,
      ...(options.headers || {}),
    },
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (!res.ok) {
    throw new Error(`RTDB ${options.method || 'GET'} failed: HTTP ${res.status}`);
  }
  const text = await res.text();
  if (!text || text === 'null') return null;
  return JSON.parse(text);
}

function safeKey(prefix, value) {
  const hash = crypto.createHash('sha256').update(`${prefix}:${value}`).digest('hex');
  return `${prefix}_${hash.slice(0, 32)}`;
}

async function checkRateLimit(bucketKey, maxAttempts) {
  const now = Date.now();
  let rec = memRateCache.get(bucketKey);
  try {
    const dbRec = await rtdbFetch(`/_rate_limits/${bucketKey}.json`);
    if (dbRec && typeof dbRec === 'object') {
      rec = dbRec;
    }
  } catch (_) {
    // Fall back to in-memory cache if RTDB rate-limit read fails transiently
  }

  if (!rec) return { blocked: false, count: 0, retryAfterSec: 0 };

  if (rec.lockedUntil && now < rec.lockedUntil) {
    return {
      blocked: true,
      count: rec.count || maxAttempts,
      retryAfterSec: Math.max(1, Math.ceil((rec.lockedUntil - now) / 1000)),
    };
  }
  if (!rec.windowStart || now - rec.windowStart > RATE_WINDOW_MS) {
    return { blocked: false, count: 0, retryAfterSec: 0 };
  }
  if ((rec.count || 0) >= maxAttempts) {
    return {
      blocked: true,
      count: rec.count,
      retryAfterSec: Math.max(1, Math.ceil((rec.windowStart + RATE_WINDOW_MS - now) / 1000)),
    };
  }
  return { blocked: false, count: rec.count || 0, retryAfterSec: 0 };
}

async function recordFailure(bucketKey, maxAttempts) {
  const now = Date.now();
  let rec = memRateCache.get(bucketKey);
  try {
    const dbRec = await rtdbFetch(`/_rate_limits/${bucketKey}.json`);
    if (dbRec && typeof dbRec === 'object') rec = dbRec;
  } catch (_) {}

  if (!rec || !rec.windowStart || now - rec.windowStart > RATE_WINDOW_MS) {
    rec = { count: 1, windowStart: now, lockedUntil: 0 };
  } else {
    rec = {
      count: (rec.count || 0) + 1,
      windowStart: rec.windowStart,
      lockedUntil: rec.lockedUntil || 0,
    };
  }
  if (rec.count >= maxAttempts) {
    rec.lockedUntil = now + LOCKOUT_MS;
  }
  memRateCache.set(bucketKey, rec);
  try {
    await rtdbFetch(`/_rate_limits/${bucketKey}.json`, { method: 'PUT', body: rec });
  } catch (_) {}
}

async function clearRateLimit(bucketKey) {
  memRateCache.delete(bucketKey);
  try {
    await rtdbFetch(`/_rate_limits/${bucketKey}.json`, { method: 'DELETE' });
  } catch (_) {}
}

function verifyScryptPassword(password, storedHash) {
  return new Promise((resolve) => {
    if (!storedHash || typeof storedHash !== 'string') return resolve(false);
    const parts = storedHash.split('$');
    if (parts.length !== 6 || parts[0] !== 'scrypt') return resolve(false);

    const N = Number(parts[1]);
    const r = Number(parts[2]);
    const p = Number(parts[3]);
    const saltHex = parts[4];
    const hashHex = parts[5];

    if (
      !Number.isInteger(N) ||
      !Number.isInteger(r) ||
      !Number.isInteger(p) ||
      N < 1024 ||
      N > 131072 ||
      !/^[0-9a-fA-F]+$/.test(saltHex) ||
      !/^[0-9a-fA-F]+$/.test(hashHex)
    ) {
      return resolve(false);
    }

    const expected = Buffer.from(hashHex, 'hex');
    const salt = Buffer.from(saltHex, 'hex');
    crypto.scrypt(
      password,
      salt,
      expected.length,
      { N, r, p, maxmem: 64 * 1024 * 1024 },
      (err, derived) => {
        if (err || !derived || derived.length !== expected.length) {
          return resolve(false);
        }
        try {
          resolve(crypto.timingSafeEqual(derived, expected));
        } catch (_) {
          resolve(false);
        }
      }
    );
  });
}

function parseExpireDate(s) {
  if (!s || typeof s !== 'string') return -1;
  const trimmed = s.trim();
  const m = trimmed.match(
    /^(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2})(?:\s+([+-])(\d{1,2})(?::(\d{1,2}))?)?$/
  );
  if (!m) return -1;
  const Y = Number(m[1]);
  const M = Number(m[2]);
  const D = Number(m[3]);
  const h = Number(m[4]);
  const min = Number(m[5]);
  const hasTZ = Boolean(m[6]);
  const tzSign = m[6] || '+';
  const tzH = Number(m[7] || 0);
  const tzM = Number(m[8] || 0);

  if (Y < 2020 || Y > 2200 || M < 1 || M > 12 || D < 1 || D > 31) return -1;
  if (h < 0 || h > 23 || min < 0 || min > 59) return -1;
  if (hasTZ && (tzH < 0 || tzH > 14 || tzM < 0 || tzM > 59)) return -1;

  const utcMs = Date.UTC(Y, M - 1, D, h, min, 0, 0);
  let offMs = (tzH * 3600 + tzM * 60) * 1000;
  if (tzSign === '-') offMs = -offMs;
  return utcMs - offMs;
}

function computeExpiryMs(rec) {
  if (rec.expire_date) {
    const ms = parseExpireDate(String(rec.expire_date));
    if (ms > 0) return ms;
  }
  if (rec.time !== undefined && rec.time !== null && rec.time !== '') {
    const d = Number(rec.time);
    if (Number.isFinite(d) && d > 1e11 && d < 9.2e18) return Math.trunc(d);
  }
  const rgMs = Number(rec.rgtime);
  if (Number.isFinite(rgMs) && rgMs > 1e11 && rgMs < 9.2e18) {
    if (rec.duration_hours !== undefined && rec.duration_hours !== '') {
      const h = Number(rec.duration_hours);
      if (Number.isFinite(h) && h > 0 && h < 1e6) {
        return Math.trunc(rgMs + h * 3600000);
      }
    }
    if (rec.duration_days !== undefined && rec.duration_days !== '') {
      const d = Number(rec.duration_days);
      if (Number.isFinite(d) && d > 0 && d < 36500) {
        return Math.trunc(rgMs + d * 86400000);
      }
    }
  }
  return 0;
}

function getClientIp(req) {
  const headers = req.headers || {};
  const fwd = headers['x-forwarded-for'] || headers['X-Forwarded-For'] || '';
  if (fwd) return String(fwd).split(',')[0].trim();
  return String(
    headers['cf-connecting-ip'] || headers['x-real-ip'] || headers['X-Real-IP'] || 'unknown'
  ).trim();
}

function signEd25519Token(privateKey, payloadObj) {
  const payloadB64 = base64UrlEncode(JSON.stringify(payloadObj));
  const sig = crypto.sign(null, Buffer.from(payloadB64, 'ascii'), privateKey);
  return `${payloadB64}.${base64UrlEncode(sig)}`;
}

module.exports = async ({ req, res, error }) => {
  try {
    let body = {};
    if (req.bodyJson && typeof req.bodyJson === 'object') {
      body = req.bodyJson;
    } else if (typeof req.body === 'string' && req.body.trim()) {
      body = JSON.parse(req.body);
    } else if (req.body && typeof req.body === 'object') {
      body = req.body;
    }

    const username = String(body.username || '').trim().toLowerCase();
    const password = String(body.password || '');
    const deviceId = String(body.deviceId || '').trim();

    if (
      !username ||
      !password ||
      !deviceId ||
      username.length > 128 ||
      password.length > 256 ||
      deviceId.length > 128
    ) {
      return res.json({ ok: false, reason: 'bad_input' }, 400);
    }

    const clientIp = getClientIp(req);
    const userBucket = safeKey('u', username);
    const ipBucket = safeKey('ip', clientIp);

    const [userLimit, ipLimit] = await Promise.all([
      checkRateLimit(userBucket, MAX_FAILED_PER_USER),
      checkRateLimit(ipBucket, MAX_FAILED_PER_IP),
    ]);

    if (userLimit.blocked || ipLimit.blocked) {
      const retryAfterSec = Math.max(userLimit.retryAfterSec, ipLimit.retryAfterSec);
      return res.json({ ok: false, reason: 'rate_limited', retryAfterSec }, 429);
    }

    const encodedUser = encodeURIComponent(`"${username}"`);
    const matched = await rtdbFetch(`/User.json?orderBy=%22user%22&equalTo=${encodedUser}`);

    let userRecord = null;
    if (matched && typeof matched === 'object') {
      for (const k of Object.keys(matched)) {
        const candidate = matched[k];
        if (
          candidate &&
          typeof candidate === 'object' &&
          String(candidate.user || '').trim().toLowerCase() === username
        ) {
          userRecord = candidate;
          break;
        }
      }
    }

    if (!userRecord || !userRecord.passHash) {
      await Promise.all([
        recordFailure(userBucket, MAX_FAILED_PER_USER),
        recordFailure(ipBucket, MAX_FAILED_PER_IP),
      ]);
      return res.json({ ok: false, reason: 'invalid_credentials' }, 401);
    }

    const passwordOk = await verifyScryptPassword(password, userRecord.passHash);
    if (!passwordOk) {
      await Promise.all([
        recordFailure(userBucket, MAX_FAILED_PER_USER),
        recordFailure(ipBucket, MAX_FAILED_PER_IP),
      ]);
      return res.json({ ok: false, reason: 'invalid_credentials' }, 401);
    }

    if (String(userRecord.status) !== 'true') {
      return res.json({ ok: false, reason: 'blocked' }, 403);
    }

    if (userRecord.device_id && String(userRecord.device_id).trim() !== deviceId) {
      return res.json({ ok: false, reason: 'device_mismatch' }, 403);
    }

    const expiryMs = computeExpiryMs(userRecord);
    if (expiryMs > 0 && Date.now() > expiryMs) {
      return res.json({ ok: false, reason: 'expired' }, 403);
    }

    await clearRateLimit(userBucket);

    const privateKey = parseEd25519PrivateKey(process.env.ED25519_PRIVATE_KEY);
    const dbUser = String(userRecord.user || username).trim();
    const token = signEd25519Token(privateKey, {
      u: dbUser,
      e: expiryMs,
      d: deviceId,
      i: Date.now(),
    });

    return res.json(
      {
        ok: true,
        token,
        user: dbUser,
        status: 'true',
        expiry: String(expiryMs),
      },
      200
    );
  } catch (err) {
    if (error) error(`verifyLogin internal error: ${err.message}`);
    return res.json({ ok: false, reason: 'server_error' }, 500);
  }
};
