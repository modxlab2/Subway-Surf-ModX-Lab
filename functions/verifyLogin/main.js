'use strict';

const crypto = require('node:crypto');

// ============================================================================
// Configuration (from Appwrite Function Environment Variables ONLY)
//   - FIREBASE_DB_URL: e.g. https://modx-lab-a5e41-default-rtdb.firebaseio.com
//   - FIREBASE_SERVICE_ACCOUNT_JSON: JSON string of Firebase Service Account
//     (or FIREBASE_DB_SECRET as fallback legacy database secret)
//   - ED25519_PRIVATE_KEY: PKCS8 PEM or 64-hex (32-byte seed) / base64 PKCS8
//   - SCRYPT_N (optional): >= 16384 target work factor for login auto-upgrade
// ============================================================================

const RATE_WINDOW_MS = 15 * 60 * 1000;          // 15 minutes sliding window
const MAX_FAILED_PER_USER = 5;
const MAX_FAILED_PER_DEVICE = 10;
const MAX_FAILED_PER_IP = 15;
const BASE_LOCKOUT_MS = 15 * 60 * 1000;         // 15m -> 30m -> 60m ...
const MAX_LOCKOUT_MS = 24 * 60 * 60 * 1000;     // 24h cap
const LOCK_LEVEL_TTL_MS = 24 * 60 * 60 * 1000;  // Escalation memory

const SHORT_SESSION_TTL_MS = 15 * 60 * 1000;    // Short-lived active session (15 min)
const MAX_SESSION_AGE_MS = 30 * 24 * 60 * 60 * 1000; // Remembered refresh max (30 days)

const GLOBAL_WINDOW_MS = 60 * 1000;             // 1 minute container circuit breaker
const GLOBAL_MAX_REQ_PER_MIN = 600;
const GLOBAL_MAX_FAIL_PER_MIN = 120;

const TARGET_SCRYPT_N = Math.max(
  16384,
  Math.min(65536, Number(process.env.SCRYPT_N || 16384) || 16384)
);
const TARGET_SCRYPT_R = 8;
const TARGET_SCRYPT_P = 1;
const TARGET_SCRYPT_KEYLEN = 64;

// Valid scrypt$16384$8$1 hash used only for constant-time unknown-user scrypt execution
const DUMMY_SCRYPT_HASH =
  'scrypt$16384$8$1$00112233445566778899aabbccddeeff$' +
  '9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e' +
  '7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a';

// Per-warm-container caches
const memRateCache = new Map();
let globalWindowStart = Date.now();
let globalReqCount = 0;
let globalFailCount = 0;

let cachedGoogleToken = null;
let cachedGoogleTokenExpMs = 0;
let cachedGoogleTokenPromise = null;
let cachedServiceAccount = null;
let cachedServiceAccountRaw = null;

let cachedKeyRaw = null;
let cachedPrivateKey = null;
let cachedPublicKey = null;

function base64UrlEncode(buf) {
  return Buffer.from(buf)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function base64UrlDecode(str) {
  if (!str || typeof str !== 'string') return null;
  if (!/^[A-Za-z0-9_-]+={0,2}$/.test(str)) return null;
  const normalized = str.replace(/-/g, '+').replace(/_/g, '/');
  const pad = (4 - (normalized.length % 4)) % 4;
  return Buffer.from(normalized + '='.repeat(pad), 'base64');
}

function normalizeText(raw) {
  if (raw === undefined || raw === null) return '';
  return String(raw).normalize('NFC');
}

function normalizeNonce(raw) {
  if (raw === undefined || raw === null) return '';
  const s = String(raw).trim();
  if (!s || s.length < 16 || s.length > 128) return '';
  if (!/^[A-Za-z0-9_-]+$/.test(s)) return '';
  return s;
}

function getEd25519Keys() {
  const raw = process.env.ED25519_PRIVATE_KEY;
  if (!raw || typeof raw !== 'string' || !raw.trim()) {
    throw new Error('ED25519_PRIVATE_KEY env var is missing');
  }
  const trimmed = raw.trim();
  if (cachedKeyRaw === trimmed && cachedPrivateKey && cachedPublicKey) {
    return { privateKey: cachedPrivateKey, publicKey: cachedPublicKey };
  }

  let priv;
  if (trimmed.includes('BEGIN PRIVATE KEY')) {
    priv = crypto.createPrivateKey({
      key: trimmed.replace(/\\n/g, '\n'),
      format: 'pem',
      type: 'pkcs8',
    });
  } else if (/^[0-9a-fA-F]{64}$/.test(trimmed)) {
    const seed = Buffer.from(trimmed, 'hex');
    const pkcs8Prefix = Buffer.from('302e020100300506032b657004220420', 'hex');
    priv = crypto.createPrivateKey({
      key: Buffer.concat([pkcs8Prefix, seed]),
      format: 'der',
      type: 'pkcs8',
    });
  } else {
    const der = Buffer.from(trimmed, 'base64');
    priv = crypto.createPrivateKey({ key: der, format: 'der', type: 'pkcs8' });
  }

  const pub = crypto.createPublicKey(priv);
  cachedKeyRaw = trimmed;
  cachedPrivateKey = priv;
  cachedPublicKey = pub;
  return { privateKey: priv, publicKey: pub };
}

function getParsedServiceAccount(saRaw) {
  if (cachedServiceAccount && cachedServiceAccountRaw === saRaw) {
    return cachedServiceAccount;
  }
  const parsed = JSON.parse(saRaw);
  cachedServiceAccount = parsed;
  cachedServiceAccountRaw = saRaw;
  return parsed;
}

async function getFirebaseAuthParams() {
  const saRaw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (saRaw && saRaw.trim()) {
    if (cachedGoogleToken && Date.now() < cachedGoogleTokenExpMs - 60000) {
      return { headers: { Authorization: `Bearer ${cachedGoogleToken}` }, queryAuth: '' };
    }
    if (cachedGoogleTokenPromise) {
      return cachedGoogleTokenPromise;
    }

    cachedGoogleTokenPromise = (async () => {
      try {
        const nowSec = Math.floor(Date.now() / 1000);
        const sa = getParsedServiceAccount(saRaw);
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
      } finally {
        cachedGoogleTokenPromise = null;
      }
    })();

    return cachedGoogleTokenPromise;
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

  if (options.returnMeta) {
    const etag = res.headers.get('etag') || '';
    if (res.status === 412) {
      return { preconditionFailed: true, etag, data: null };
    }
    if (!res.ok) {
      throw new Error(`RTDB ${options.method || 'GET'} failed: HTTP ${res.status}`);
    }
    const text = await res.text();
    return {
      preconditionFailed: false,
      etag,
      data: !text || text === 'null' ? null : JSON.parse(text),
    };
  }

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

function checkGlobalCircuitBreaker() {
  const now = Date.now();
  if (now - globalWindowStart > GLOBAL_WINDOW_MS) {
    globalWindowStart = now;
    globalReqCount = 0;
    globalFailCount = 0;
  }
  globalReqCount++;
  if (globalReqCount > GLOBAL_MAX_REQ_PER_MIN || globalFailCount > GLOBAL_MAX_FAIL_PER_MIN) {
    const retryAfterSec = Math.max(
      1,
      Math.ceil((globalWindowStart + GLOBAL_WINDOW_MS - now) / 1000)
    );
    return { blocked: true, retryAfterSec };
  }
  return { blocked: false, retryAfterSec: 0 };
}

function recordGlobalFailure() {
  const now = Date.now();
  if (now - globalWindowStart > GLOBAL_WINDOW_MS) {
    globalWindowStart = now;
    globalReqCount = 1;
    globalFailCount = 1;
    return;
  }
  globalFailCount++;
}

function evaluateRateRecord(rec, maxAttempts) {
  const now = Date.now();
  if (!rec || typeof rec !== 'object') {
    return { blocked: false, count: 0, lockLevel: 0, retryAfterSec: 0 };
  }
  if (rec.lockedUntil && now < rec.lockedUntil) {
    return {
      blocked: true,
      count: rec.count || maxAttempts,
      lockLevel: rec.lockLevel || 1,
      retryAfterSec: Math.max(1, Math.ceil((rec.lockedUntil - now) / 1000)),
    };
  }
  const lockLevel =
    rec.lastLockAt && now - rec.lastLockAt <= LOCK_LEVEL_TTL_MS ? Number(rec.lockLevel || 0) : 0;

  if (!rec.windowStart || now - rec.windowStart > RATE_WINDOW_MS) {
    return { blocked: false, count: 0, lockLevel, retryAfterSec: 0 };
  }
  if ((rec.count || 0) >= maxAttempts) {
    return {
      blocked: true,
      count: rec.count,
      lockLevel: Math.max(1, lockLevel),
      retryAfterSec: Math.max(1, Math.ceil((rec.windowStart + RATE_WINDOW_MS - now) / 1000)),
    };
  }
  return { blocked: false, count: rec.count || 0, lockLevel, retryAfterSec: 0 };
}

async function fetchRateRecord(bucketKey) {
  try {
    const dbRec = await rtdbFetch(`/_rate_limits/${bucketKey}.json`);
    if (dbRec && typeof dbRec === 'object') {
      memRateCache.set(bucketKey, dbRec);
      return dbRec;
    }
  } catch (_) {
    // Fall back to warm-container memory cache on transient RTDB error
  }
  return memRateCache.get(bucketKey) || null;
}

async function checkRateLimit(bucketKey, maxAttempts) {
  const rec = await fetchRateRecord(bucketKey);
  return evaluateRateRecord(rec, maxAttempts);
}

async function recordFailure(bucketKey, maxAttempts) {
  for (let attempt = 0; attempt < 2; attempt++) {
    const now = Date.now();
    let rec = memRateCache.get(bucketKey);
    let etag = '';
    try {
      const meta = await rtdbFetch(`/_rate_limits/${bucketKey}.json`, {
        headers: { 'X-Firebase-ETag': 'true' },
        returnMeta: true,
      });
      etag = meta.etag || '';
      if (meta.data && typeof meta.data === 'object') rec = meta.data;
      else if (meta.data === null) rec = null;
    } catch (_) {}

    const prevLevel =
      rec && rec.lastLockAt && now - rec.lastLockAt <= LOCK_LEVEL_TTL_MS
        ? Number(rec.lockLevel || 0)
        : 0;

    let nextRec;
    if (!rec || !rec.windowStart || now - rec.windowStart > RATE_WINDOW_MS) {
      nextRec = {
        count: 1,
        windowStart: now,
        lockedUntil: 0,
        lockLevel: prevLevel,
        lastLockAt: rec && rec.lastLockAt ? rec.lastLockAt : 0,
      };
    } else {
      nextRec = {
        count: (rec.count || 0) + 1,
        windowStart: rec.windowStart,
        lockedUntil: rec.lockedUntil || 0,
        lockLevel: prevLevel,
        lastLockAt: rec.lastLockAt || 0,
      };
    }

    if (nextRec.count >= maxAttempts) {
      const newLevel = Math.min(prevLevel + 1, 7);
      const lockoutDuration = Math.min(
        MAX_LOCKOUT_MS,
        BASE_LOCKOUT_MS * Math.pow(2, newLevel - 1)
      );
      nextRec.lockedUntil = now + lockoutDuration;
      nextRec.lockLevel = newLevel;
      nextRec.lastLockAt = now;
      nextRec.count = 0; // Reset counter after applying escalated lock
    }

    memRateCache.set(bucketKey, nextRec);
    try {
      const putOpts = {
        method: 'PUT',
        body: nextRec,
        returnMeta: true,
      };
      if (etag) {
        putOpts.headers = { 'If-Match': etag };
      }
      const res = await rtdbFetch(`/_rate_limits/${bucketKey}.json?print=silent`, putOpts);
      if (res && res.preconditionFailed) {
        continue; // Retry once with updated ETag
      }
      return;
    } catch (_) {
      return;
    }
  }
}

async function clearRateLimit(bucketKey) {
  memRateCache.delete(bucketKey);
  try {
    await rtdbFetch(`/_rate_limits/${bucketKey}.json?print=silent`, { method: 'DELETE' });
  } catch (_) {}
}

function parseScryptParts(storedHash) {
  if (!storedHash || typeof storedHash !== 'string') return null;
  const parts = storedHash.split('$');
  if (parts.length !== 6 || parts[0] !== 'scrypt') return null;

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
    r < 1 ||
    r > 32 ||
    p < 1 ||
    p > 8 ||
    !/^[0-9a-fA-F]+$/.test(saltHex) ||
    !/^[0-9a-fA-F]+$/.test(hashHex) ||
    hashHex.length < 64
  ) {
    return null;
  }

  return { N, r, p, saltHex, hashHex, keylen: hashHex.length / 2 };
}

function verifyScryptPassword(password, storedHash) {
  return new Promise((resolve) => {
    const parsed = parseScryptParts(storedHash);
    if (!parsed) return resolve({ ok: false, needsUpgrade: false });

    const expected = Buffer.from(parsed.hashHex, 'hex');
    const salt = Buffer.from(parsed.saltHex, 'hex');
    crypto.scrypt(
      password,
      salt,
      expected.length,
      { N: parsed.N, r: parsed.r, p: parsed.p, maxmem: 128 * 1024 * 1024 },
      (err, derived) => {
        if (err || !derived || derived.length !== expected.length) {
          return resolve({ ok: false, needsUpgrade: false });
        }
        try {
          const ok = crypto.timingSafeEqual(derived, expected);
          const needsUpgrade =
            ok &&
            (parsed.N < TARGET_SCRYPT_N ||
              parsed.r < TARGET_SCRYPT_R ||
              parsed.p < TARGET_SCRYPT_P ||
              parsed.keylen < TARGET_SCRYPT_KEYLEN);
          resolve({ ok, needsUpgrade });
        } catch (_) {
          resolve({ ok: false, needsUpgrade: false });
        }
      }
    );
  });
}

function hashScryptTarget(password) {
  return new Promise((resolve, reject) => {
    const salt = crypto.randomBytes(16);
    crypto.scrypt(
      password,
      salt,
      TARGET_SCRYPT_KEYLEN,
      {
        N: TARGET_SCRYPT_N,
        r: TARGET_SCRYPT_R,
        p: TARGET_SCRYPT_P,
        maxmem: 128 * 1024 * 1024,
      },
      (err, derived) => {
        if (err) return reject(err);
        resolve(
          `scrypt$${TARGET_SCRYPT_N}$${TARGET_SCRYPT_R}$${TARGET_SCRYPT_P}$${salt.toString('hex')}$${derived.toString('hex')}`
        );
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

function isReasonableIp(ip) {
  if (!ip || ip.length > 64) return false;
  return /^[0-9a-fA-F:.]+$/.test(ip);
}

function getClientIp(req) {
  const headers = req.headers || {};
  const getH = (name) => headers[name] || headers[name.toLowerCase()] || '';

  const awIp = String(getH('x-appwrite-client-ip')).trim();
  if (isReasonableIp(awIp)) return awIp;

  const cfRay = String(getH('cf-ray')).trim();
  const cfIp = String(getH('cf-connecting-ip')).trim();
  if (cfRay && isReasonableIp(cfIp)) return cfIp;

  const realIp = String(getH('x-real-ip') || getH('X-Real-IP')).trim();
  if (isReasonableIp(realIp)) return realIp;

  const fwd = String(getH('x-forwarded-for') || getH('X-Forwarded-For')).trim();
  if (fwd) {
    const parts = fwd
      .split(',')
      .map((s) => s.trim())
      .filter(isReasonableIp);
    if (parts.length > 0) {
      // Proxy appends peer IP at the end; rightmost resists client-spoofed left entries
      return parts[parts.length - 1];
    }
  }
  return 'unknown';
}

function signEd25519Token(privateKey, payloadObj) {
  const payloadB64 = base64UrlEncode(JSON.stringify(payloadObj));
  const sig = crypto.sign(null, Buffer.from(payloadB64, 'ascii'), privateKey);
  return `${payloadB64}.${base64UrlEncode(sig)}`;
}

function buildTokenPayload(dbUser, licenseExpiryMs, tokenVersion, deviceId, nonce, now) {
  const maxRemember = now + MAX_SESSION_AGE_MS;
  const rememberExpiryMs =
    licenseExpiryMs > 0 ? Math.min(maxRemember, licenseExpiryMs) : maxRemember;
  return {
    u: dbUser,
    e: licenseExpiryMs,
    se: now + SHORT_SESSION_TTL_MS,
    re: rememberExpiryMs,
    tv: tokenVersion,
    d: deviceId,
    i: now,
    n: nonce,
  };
}

function verifyEd25519Token(publicKey, token, expectedDeviceId) {
  if (!token || typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 2 || !parts[0] || !parts[1]) return null;

  const [payloadB64, sigB64] = parts;
  const sigBuf = base64UrlDecode(sigB64);
  if (!sigBuf || sigBuf.length !== 64) return null;

  const sigOk = crypto.verify(null, Buffer.from(payloadB64, 'ascii'), publicKey, sigBuf);
  if (!sigOk) return null;

  const payloadBuf = base64UrlDecode(payloadB64);
  if (!payloadBuf || payloadBuf.length === 0) return null;

  let payload;
  try {
    payload = JSON.parse(payloadBuf.toString('utf8'));
  } catch (_) {
    return null;
  }
  if (!payload || typeof payload !== 'object') return null;

  const tokUser = normalizeText(payload.u || '').trim().toLowerCase();
  const tokDevice = String(payload.d || '').trim();
  const tokIssued = Number(payload.i);
  const tokExpiry = Number(payload.e);
  const tokSessionExp = Number(payload.se);
  const tokRememberExp = Number(payload.re);
  const tokVersion = Number(payload.tv);
  const now = Date.now();

  if (!tokUser || tokUser.length > 128) return null;
  if (!tokDevice || tokDevice !== expectedDeviceId) return null;
  if (!Number.isFinite(tokIssued) || tokIssued <= 0) return null;
  if (tokIssued > now + 2 * 60 * 1000) return null;
  if (now - tokIssued > MAX_SESSION_AGE_MS) return null;
  if (!Number.isFinite(tokExpiry) || tokExpiry < 0) return null;
  if (tokExpiry > 0 && now > tokExpiry) return null;
  if (!Number.isFinite(tokSessionExp) || tokSessionExp <= tokIssued) return null;
  if (!Number.isFinite(tokRememberExp) || tokRememberExp <= tokIssued || now > tokRememberExp) {
    return null;
  }
  if (!Number.isInteger(tokVersion) || tokVersion < 1) return null;

  return {
    username: tokUser,
    deviceId: tokDevice,
    issuedAt: tokIssued,
    expiryMs: tokExpiry,
    sessionExpiryMs: tokSessionExp,
    rememberExpiryMs: tokRememberExp,
    tokenVersion: tokVersion,
  };
}

async function fetchUserRecord(username) {
  const encodedUser = encodeURIComponent(`"${username}"`);
  const matched = await rtdbFetch(
    `/User.json?orderBy=%22user%22&equalTo=${encodedUser}&limitToFirst=1`
  );
  if (!matched || typeof matched !== 'object') return null;
  for (const k of Object.keys(matched)) {
    const candidate = matched[k];
    if (
      candidate &&
      typeof candidate === 'object' &&
      normalizeText(candidate.user || '').trim().toLowerCase() === username
    ) {
      return { ...candidate, _nodeKey: k };
    }
  }
  return null;
}

function checkAccountEligibility(userRecord, deviceId) {
  if (String(userRecord.status) !== 'true') {
    return { ok: false, status: 403, reason: 'blocked' };
  }
  if (userRecord.device_id && String(userRecord.device_id).trim() !== deviceId) {
    return { ok: false, status: 403, reason: 'device_mismatch' };
  }
  const expiryMs = computeExpiryMs(userRecord);
  if (expiryMs > 0 && Date.now() > expiryMs) {
    return { ok: false, status: 403, reason: 'expired' };
  }
  return { ok: true, expiryMs };
}

function getUserTokenVersion(userRecord) {
  const tv = Number(userRecord.tokenVersion);
  return Number.isInteger(tv) && tv >= 1 ? tv : 1;
}

module.exports = async ({ req, res, error }) => {
  const tStart = Date.now();
  let wantDiag = false;
  try {
    let body = {};
    if (req.bodyJson && typeof req.bodyJson === 'object') {
      body = req.bodyJson;
    } else if (typeof req.body === 'string' && req.body.trim()) {
      body = JSON.parse(req.body);
    } else if (req.body && typeof req.body === 'object') {
      body = req.body;
    }

    wantDiag = body.diag === true;
    const action = String(body.action || 'login').trim().toLowerCase();

    const globalCheck = checkGlobalCircuitBreaker();
    if (globalCheck.blocked) {
      return res.json(
        { ok: false, reason: 'rate_limited', retryAfterSec: globalCheck.retryAfterSec },
        429
      );
    }

    // Lightweight warm-up endpoint (no RTDB /User read, no secrets exposed)
    if (action === 'ping') {
      const tDbStart = Date.now();
      if (body.warmDb === true) {
        await getFirebaseAuthParams().catch(() => null);
        getEd25519Keys();
      }
      const resp = { ok: true, pong: true };
      if (wantDiag) {
        resp.serverTiming = {
          dbMs: Date.now() - tDbStart,
          scryptMs: 0,
          signMs: 0,
          totalMs: Date.now() - tStart,
        };
      }
      return res.json(resp, 200);
    }

    const clientIp = getClientIp(req);
    const ipBucket = safeKey('ip', clientIp);

    if (action === 'verify_session') {
      const token = String(body.token || '').trim();
      const deviceId = String(body.deviceId || '').trim();
      const nonce = normalizeNonce(body.nonce);

      if (
        !token ||
        token.length > 4096 ||
        !deviceId ||
        deviceId.length > 128 ||
        !nonce
      ) {
        return res.json({ ok: false, reason: 'bad_input' }, 400);
      }

      const { privateKey, publicKey } = getEd25519Keys();
      const claims = verifyEd25519Token(publicKey, token, deviceId);
      const devBucket = safeKey('d', deviceId);

      if (!claims) {
        recordGlobalFailure();
        const ipLimit = await checkRateLimit(ipBucket, MAX_FAILED_PER_IP);
        if (ipLimit.blocked) {
          return res.json(
            { ok: false, reason: 'rate_limited', retryAfterSec: ipLimit.retryAfterSec },
            429
          );
        }
        await Promise.all([
          recordFailure(ipBucket, MAX_FAILED_PER_IP),
          recordFailure(devBucket, MAX_FAILED_PER_DEVICE),
        ]);
        return res.json({ ok: false, reason: 'session_invalid' }, 401);
      }

      const userBucket = safeKey('u', claims.username);
      const tDbStart = Date.now();
      const [ipLimit, userLimit, devLimit, userRecord] = await Promise.all([
        checkRateLimit(ipBucket, MAX_FAILED_PER_IP),
        checkRateLimit(userBucket, MAX_FAILED_PER_USER),
        checkRateLimit(devBucket, MAX_FAILED_PER_DEVICE),
        fetchUserRecord(claims.username),
      ]);
      const dbMs = Date.now() - tDbStart;

      if (ipLimit.blocked || userLimit.blocked || devLimit.blocked) {
        const retryAfterSec = Math.max(
          ipLimit.retryAfterSec,
          userLimit.retryAfterSec,
          devLimit.retryAfterSec
        );
        return res.json({ ok: false, reason: 'rate_limited', retryAfterSec }, 429);
      }

      if (!userRecord) {
        recordGlobalFailure();
        await Promise.all([
          recordFailure(userBucket, MAX_FAILED_PER_USER),
          recordFailure(devBucket, MAX_FAILED_PER_DEVICE),
          recordFailure(ipBucket, MAX_FAILED_PER_IP),
        ]);
        return res.json({ ok: false, reason: 'user_not_found' }, 404);
      }

      const currentTv = getUserTokenVersion(userRecord);
      const revokedAt = Number(userRecord.revokedAt || 0);
      if (
        claims.tokenVersion !== currentTv ||
        (Number.isFinite(revokedAt) && revokedAt > 0 && claims.issuedAt < revokedAt)
      ) {
        recordGlobalFailure();
        return res.json({ ok: false, reason: 'session_invalid' }, 401);
      }

      const eligibility = checkAccountEligibility(userRecord, deviceId);
      if (!eligibility.ok) {
        return res.json({ ok: false, reason: eligibility.reason }, eligibility.status);
      }

      const clearTasks = [];
      if (userLimit.count > 0 || userLimit.lockLevel > 0) clearTasks.push(clearRateLimit(userBucket));
      if (devLimit.count > 0 || devLimit.lockLevel > 0) clearTasks.push(clearRateLimit(devBucket));
      if (clearTasks.length > 0) {
        await Promise.all(clearTasks);
      }

      const tSignStart = Date.now();
      const now = Date.now();
      const dbUser = normalizeText(userRecord.user || claims.username).trim();
      const freshToken = signEd25519Token(
        privateKey,
        buildTokenPayload(dbUser, eligibility.expiryMs, currentTv, deviceId, nonce, now)
      );
      const signMs = Date.now() - tSignStart;

      const resp = {
        ok: true,
        token: freshToken,
        user: dbUser,
        status: 'true',
        expiry: String(eligibility.expiryMs),
        nonce,
      };
      if (wantDiag) {
        resp.serverTiming = {
          dbMs,
          scryptMs: 0,
          signMs,
          totalMs: Date.now() - tStart,
        };
      }
      return res.json(resp, 200);
    }

    if (action !== 'login') {
      return res.json({ ok: false, reason: 'bad_input' }, 400);
    }

    const username = normalizeText(body.username || '').trim().toLowerCase();
    const password = normalizeText(body.password || '');
    const deviceId = String(body.deviceId || '').trim();
    const nonce = normalizeNonce(body.nonce);

    if (
      !username ||
      !password ||
      !deviceId ||
      !nonce ||
      username.length > 128 ||
      password.length > 256 ||
      deviceId.length > 128
    ) {
      return res.json({ ok: false, reason: 'bad_input' }, 400);
    }

    const userBucket = safeKey('u', username);
    const devBucket = safeKey('d', deviceId);

    const tDbStart = Date.now();
    const [userLimit, ipLimit, devLimit, userRecord] = await Promise.all([
      checkRateLimit(userBucket, MAX_FAILED_PER_USER),
      checkRateLimit(ipBucket, MAX_FAILED_PER_IP),
      checkRateLimit(devBucket, MAX_FAILED_PER_DEVICE),
      fetchUserRecord(username),
    ]);
    const dbMs = Date.now() - tDbStart;

    if (userLimit.blocked || ipLimit.blocked || devLimit.blocked) {
      const retryAfterSec = Math.max(
        userLimit.retryAfterSec,
        ipLimit.retryAfterSec,
        devLimit.retryAfterSec
      );
      return res.json({ ok: false, reason: 'rate_limited', retryAfterSec }, 429);
    }

    // Constant-time timing parity: ALWAYS run scrypt even when userRecord is missing
    const tScryptStart = Date.now();
    const hashToCheck =
      userRecord && typeof userRecord.passHash === 'string' && userRecord.passHash
        ? userRecord.passHash
        : DUMMY_SCRYPT_HASH;
    const scryptResult = await verifyScryptPassword(password, hashToCheck);
    const scryptMs = Date.now() - tScryptStart;

    if (!userRecord || !userRecord.passHash || !scryptResult.ok) {
      recordGlobalFailure();
      await Promise.all([
        recordFailure(userBucket, MAX_FAILED_PER_USER),
        recordFailure(devBucket, MAX_FAILED_PER_DEVICE),
        recordFailure(ipBucket, MAX_FAILED_PER_IP),
      ]);
      return res.json({ ok: false, reason: 'invalid_credentials' }, 401);
    }

    const eligibility = checkAccountEligibility(userRecord, deviceId);
    if (!eligibility.ok) {
      return res.json({ ok: false, reason: eligibility.reason }, eligibility.status);
    }

    const postTasks = [];
    if (userLimit.count > 0 || userLimit.lockLevel > 0) postTasks.push(clearRateLimit(userBucket));
    if (devLimit.count > 0 || devLimit.lockLevel > 0) postTasks.push(clearRateLimit(devBucket));
    if (scryptResult.needsUpgrade && userRecord._nodeKey) {
      postTasks.push(
        hashScryptTarget(password)
          .then((upgradedHash) =>
            rtdbFetch(`/User/${encodeURIComponent(userRecord._nodeKey)}.json?print=silent`, {
              method: 'PATCH',
              body: { passHash: upgradedHash },
            })
          )
          .catch(() => null)
      );
    }

    const tSignStart = Date.now();
    const { privateKey } = getEd25519Keys();
    const currentTv = getUserTokenVersion(userRecord);
    const now = Date.now();
    const dbUser = normalizeText(userRecord.user || username).trim();
    const token = signEd25519Token(
      privateKey,
      buildTokenPayload(dbUser, eligibility.expiryMs, currentTv, deviceId, nonce, now)
    );
    const signMs = Date.now() - tSignStart;

    if (postTasks.length > 0) {
      await Promise.all(postTasks);
    }

    const resp = {
      ok: true,
      token,
      user: dbUser,
      status: 'true',
      expiry: String(eligibility.expiryMs),
      nonce,
    };
    if (wantDiag) {
      resp.serverTiming = {
        dbMs,
        scryptMs,
        signMs,
        totalMs: Date.now() - tStart,
      };
    }
    return res.json(resp, 200);
  } catch (err) {
    if (error) error(`verifyLogin internal error: ${err.message}`);
    return res.json({ ok: false, reason: 'server_error' }, 500);
  }
};
