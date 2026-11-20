'use strict';

const crypto = require('node:crypto');

// ============================================================================
// Private Mobile Admin Panel — Appwrite Function (node-18.0)
//
// Required Environment Variables (Appwrite Function Settings):
//   - ADMIN_PASSWORD               : Secret password to unlock this panel
//   - FIREBASE_DB_URL              : e.g. https://modx-lab-a5e41-default-rtdb.firebaseio.com
//   - FIREBASE_SERVICE_ACCOUNT_JSON: Full JSON of Firebase Service Account (Secret)
//     (or FIREBASE_DB_SECRET as fallback legacy database secret)
// ============================================================================

const SCRYPT_N = 16384;
const SCRYPT_R = 8;
const SCRYPT_P = 1;
const SCRYPT_KEYLEN = 64;

const RATE_WINDOW_MS = 15 * 60 * 1000;
const MAX_FAILED_ADMIN_PER_IP = 5;
const LOCKOUT_MS = 15 * 60 * 1000;
const TOKEN_TTL_MS = 2 * 60 * 60 * 1000; // 2 hours

const memRateCache = new Map();
let cachedGoogleToken = null;
let cachedGoogleTokenExpMs = 0;

function base64UrlEncode(buf) {
  return Buffer.from(buf)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function base64UrlDecode(str) {
  let s = String(str || '').replace(/-/g, '+').replace(/_/g, '/');
  while (s.length % 4 !== 0) s += '=';
  return Buffer.from(s, 'base64');
}

function sha256Buf(str) {
  return crypto.createHash('sha256').update(String(str || ''), 'utf8').digest();
}

function constTimeStringEqual(a, b) {
  const ha = sha256Buf(a);
  const hb = sha256Buf(b);
  return crypto.timingSafeEqual(ha, hb);
}

function getSigningSecret() {
  const pw = (process.env.ADMIN_PASSWORD || '').trim();
  const sa = (process.env.FIREBASE_SERVICE_ACCOUNT_JSON || process.env.FIREBASE_DB_SECRET || '').trim();
  const explicit = (process.env.ADMIN_TOKEN_SECRET || '').trim();
  if (explicit) return sha256Buf(explicit);
  return crypto.createHash('sha256').update(`admin-panel:${pw}:${sa.slice(0, 128)}`).digest();
}

function createSessionToken() {
  const payload = base64UrlEncode(
    JSON.stringify({
      role: 'admin',
      exp: Date.now() + TOKEN_TTL_MS,
      nonce: crypto.randomBytes(12).toString('hex'),
    })
  );
  const sig = base64UrlEncode(
    crypto.createHmac('sha256', getSigningSecret()).update(payload).digest()
  );
  return `${payload}.${sig}`;
}

function verifySessionToken(token) {
  if (!token || typeof token !== 'string') return false;
  const parts = token.split('.');
  if (parts.length !== 2) return false;
  const [payloadB64, sigB64] = parts;
  if (!payloadB64 || !sigB64) return false;

  const expectedSig = base64UrlEncode(
    crypto.createHmac('sha256', getSigningSecret()).update(payloadB64).digest()
  );
  if (!constTimeStringEqual(sigB64, expectedSig)) return false;

  try {
    const data = JSON.parse(base64UrlDecode(payloadB64).toString('utf8'));
    if (!data || data.role !== 'admin') return false;
    if (!Number.isFinite(data.exp) || Date.now() > data.exp) return false;
    return true;
  } catch (_) {
    return false;
  }
}

function hashPasswordScrypt(password) {
  return new Promise((resolve, reject) => {
    const salt = crypto.randomBytes(16);
    crypto.scrypt(
      String(password),
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

async function getFirebaseAuthParams() {
  const saRaw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (saRaw && saRaw.trim()) {
    if (cachedGoogleToken && Date.now() < cachedGoogleTokenExpMs - 60000) {
      return { headers: { Authorization: `Bearer ${cachedGoogleToken}` }, queryAuth: '' };
    }

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

  throw new Error('Missing FIREBASE_SERVICE_ACCOUNT_JSON');
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

function getClientIp(req) {
  const headers = req.headers || {};
  const fwd = headers['x-forwarded-for'] || headers['X-Forwarded-For'] || '';
  if (fwd) return String(fwd).split(',')[0].trim();
  return String(
    headers['cf-connecting-ip'] || headers['x-real-ip'] || headers['X-Real-IP'] || 'unknown'
  ).trim();
}

function safeKey(prefix, value) {
  const hash = crypto.createHash('sha256').update(`${prefix}:${value}`).digest('hex');
  return `${prefix}_${hash.slice(0, 32)}`;
}

async function checkAdminRateLimit(bucketKey) {
  const now = Date.now();
  let rec = memRateCache.get(bucketKey);
  try {
    const dbRec = await rtdbFetch(`/_rate_limits/${bucketKey}.json`);
    if (dbRec && typeof dbRec === 'object') rec = dbRec;
  } catch (_) {}

  if (!rec) return { blocked: false, retryAfterSec: 0 };
  if (rec.lockedUntil && now < rec.lockedUntil) {
    return {
      blocked: true,
      retryAfterSec: Math.max(1, Math.ceil((rec.lockedUntil - now) / 1000)),
    };
  }
  if (!rec.windowStart || now - rec.windowStart > RATE_WINDOW_MS) {
    return { blocked: false, retryAfterSec: 0 };
  }
  if ((rec.count || 0) >= MAX_FAILED_ADMIN_PER_IP) {
    return {
      blocked: true,
      retryAfterSec: Math.max(1, Math.ceil((rec.windowStart + RATE_WINDOW_MS - now) / 1000)),
    };
  }
  return { blocked: false, retryAfterSec: 0 };
}

async function recordAdminFailure(bucketKey) {
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
  if (rec.count >= MAX_FAILED_ADMIN_PER_IP) {
    rec.lockedUntil = now + LOCKOUT_MS;
  }
  memRateCache.set(bucketKey, rec);
  try {
    await rtdbFetch(`/_rate_limits/${bucketKey}.json`, { method: 'PUT', body: rec });
  } catch (_) {}
}

async function clearAdminRateLimit(bucketKey) {
  memRateCache.delete(bucketKey);
  try {
    await rtdbFetch(`/_rate_limits/${bucketKey}.json`, { method: 'DELETE' });
  } catch (_) {}
}

function computeExpiryMs(rec) {
  if (rec.time !== undefined && rec.time !== null && rec.time !== '') {
    const d = Number(rec.time);
    if (Number.isFinite(d) && d > 1e11 && d < 9.2e18) return Math.trunc(d);
  }
  const rgMs = Number(rec.rgtime);
  if (Number.isFinite(rgMs) && rgMs > 1e11 && rgMs < 9.2e18) {
    if (rec.duration_hours !== undefined && rec.duration_hours !== '') {
      const h = Number(rec.duration_hours);
      if (Number.isFinite(h) && h > 0 && h < 1e6) return Math.trunc(rgMs + h * 3600000);
    }
    if (rec.duration_days !== undefined && rec.duration_days !== '') {
      const d = Number(rec.duration_days);
      if (Number.isFinite(d) && d > 0 && d < 36500) return Math.trunc(rgMs + d * 86400000);
    }
  }
  return 0;
}

const HTML_PAGE = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<title>ModXLab Admin Panel</title>
<style>
  :root {
    --bg: #111319;
    --card: #1a1e28;
    --field: #252a37;
    --border: #333949;
    --accent: #3ddb87;
    --accent-dark: #28a865;
    --danger: #e05d56;
    --warn: #e0a94d;
    --text: #ece8df;
    --muted: #8f94a3;
  }
  * { box-sizing: border-box; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
  body {
    margin: 0; padding: 14px; background: var(--bg); color: var(--text);
    display: flex; justify-content: center;
  }
  .wrap { width: 100%; max-width: 520px; }
  .card {
    background: var(--card); border: 1px solid var(--border);
    border-radius: 14px; padding: 16px; margin-bottom: 14px;
    box-shadow: 0 8px 24px rgba(0,0,0,.35);
  }
  h1 { margin: 0 0 4px; font-size: 20px; color: var(--accent); }
  h2 { margin: 0 0 12px; font-size: 16px; }
  .sub { margin: 0 0 14px; font-size: 12.5px; color: var(--muted); }
  label { display: block; font-size: 12px; color: var(--muted); margin: 10px 0 5px; }
  input, select {
    width: 100%; padding: 11px 12px; border-radius: 10px;
    border: 1px solid var(--border); background: var(--field); color: var(--text);
    font-size: 14px; outline: none;
  }
  input:focus, select:focus { border-color: var(--accent); }
  .row { display: flex; gap: 10px; }
  .row > div { flex: 1; }
  button {
    width: 100%; padding: 12px; border: 0; border-radius: 10px;
    font-size: 14px; font-weight: 700; cursor: pointer; margin-top: 12px;
  }
  .btn-primary { background: var(--accent); color: #092014; }
  .btn-primary:active { background: var(--accent-dark); }
  .btn-sec { background: var(--field); color: var(--text); border: 1px solid var(--border); }
  .btn-danger { background: rgba(224,93,86,.18); color: #ff8e88; border: 1px solid rgba(224,93,86,.4); }
  .btn-sm { width: auto; margin: 0; padding: 7px 11px; font-size: 12px; border-radius: 8px; }
  .topbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
  .msg {
    margin-top: 10px; padding: 9px 11px; border-radius: 8px;
    font-size: 13px; display: none;
  }
  .msg.ok { display: block; background: rgba(61,219,135,.15); color: #7ef2b3; border: 1px solid rgba(61,219,135,.35); }
  .msg.err { display: block; background: rgba(224,93,86,.15); color: #ff9a94; border: 1px solid rgba(224,93,86,.35); }
  .user-item {
    border: 1px solid var(--border); background: var(--field);
    border-radius: 10px; padding: 12px; margin-bottom: 10px;
  }
  .user-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
  .uname { font-weight: 700; font-size: 15px; word-break: break-all; }
  .badge {
    font-size: 11px; font-weight: 700; padding: 3px 8px; border-radius: 999px;
  }
  .b-ok { background: rgba(61,219,135,.18); color: var(--accent); }
  .b-bad { background: rgba(224,93,86,.2); color: #ff8e88; }
  .b-warn { background: rgba(224,169,77,.2); color: var(--warn); }
  .meta { font-size: 12px; color: var(--muted); margin: 6px 0 10px; line-height: 1.45; }
  .actions { display: flex; flex-wrap: wrap; gap: 7px; }
  .hidden { display: none; }
</style>
</head>
<body>
<div class="wrap">
  <div id="loginCard" class="card">
    <h1>ModXLab Admin</h1>
    <p class="sub">Enter your private Admin Password to manage users &amp; keys.</p>
    <label for="adminPw">Admin Password</label>
    <input id="adminPw" type="password" placeholder="Enter ADMIN_PASSWORD" autocomplete="current-password">
    <button id="loginBtn" class="btn-primary">Unlock Admin Panel</button>
    <div id="loginMsg" class="msg"></div>
  </div>

  <div id="appArea" class="hidden">
    <div class="topbar">
      <div>
        <h1>ModXLab Admin</h1>
        <div class="sub" style="margin:0">Firebase RTDB (Encrypted scrypt)</div>
      </div>
      <button id="logoutBtn" class="btn-sec btn-sm">Lock</button>
    </div>

    <div class="card">
      <h2>Create / Update User</h2>
      <p class="sub" style="margin-bottom:8px">
        Leave <b>Device ID</b> empty so all users can log in with the same Username &amp; Password.
      </p>
      <label for="fUser">Username</label>
      <input id="fUser" type="text" placeholder="e.g. modxlab" autocapitalize="none" autocomplete="off">

      <label for="fPass">Password (leave blank only when updating existing user days/status)</label>
      <input id="fPass" type="text" placeholder="e.g. MyPass123" autocapitalize="none" autocomplete="off">

      <div class="row">
        <div>
          <label for="fDays">Validity (Days)</label>
          <input id="fDays" type="number" value="30" min="1" max="3650">
        </div>
        <div>
          <label for="fStatus">Status</label>
          <select id="fStatus">
            <option value="true">Active (true)</option>
            <option value="false">Blocked (false)</option>
          </select>
        </div>
      </div>

      <label for="fDevice">Device ID Lock (Optional — keep empty for all users)</label>
      <input id="fDevice" type="text" placeholder="Optional — empty = unlimited devices" autocomplete="off">

      <button id="saveUserBtn" class="btn-primary">Save User</button>
      <div id="saveMsg" class="msg"></div>
    </div>

    <div class="card">
      <div class="topbar" style="margin-bottom:8px">
        <h2 style="margin:0">Users List</h2>
        <button id="refreshBtn" class="btn-sec btn-sm">Refresh</button>
      </div>
      <div id="usersBox"><div class="sub">Loading...</div></div>
    </div>

    <div class="card">
      <h2>App Update Notice (/update)</h2>
      <div class="row">
        <div>
          <label for="uVer">Latest Version</label>
          <input id="uVer" type="text" placeholder="e.g. 1.0">
        </div>
      </div>
      <label for="uTxt">Update Message</label>
      <input id="uTxt" type="text" placeholder="Optional message shown in update popup">
      <button id="saveUpdateBtn" class="btn-sec">Save Update Info</button>
      <div id="updMsg" class="msg"></div>
    </div>
  </div>
</div>

<script>
(function(){
  const TOKEN_KEY = 'modx_admin_tok';
  const $ = (id) => document.getElementById(id);

  function getToken() { return sessionStorage.getItem(TOKEN_KEY) || ''; }
  function setToken(t) {
    if (t) sessionStorage.setItem(TOKEN_KEY, t);
    else sessionStorage.removeItem(TOKEN_KEY);
  }

  function showMsg(el, text, isErr) {
    el.textContent = text;
    el.className = 'msg ' + (isErr ? 'err' : 'ok');
  }
  function clearMsg(el) { el.className = 'msg'; el.textContent = ''; }

  async function api(action, data) {
    const headers = { 'Content-Type': 'application/json' };
    const tok = getToken();
    if (tok) headers['X-Admin-Token'] = tok;

    const res = await fetch(window.location.pathname, {
      method: 'POST',
      headers,
      body: JSON.stringify(Object.assign({ action }, data || {}))
    });
    const json = await res.json().catch(() => ({ ok: false, reason: 'Invalid JSON response' }));
    if (res.status === 401 && action !== 'auth') {
      setToken('');
      showLocked();
    }
    return { status: res.status, json };
  }

  function showLocked() {
    $('appArea').classList.add('hidden');
    $('loginCard').classList.remove('hidden');
  }

  function showUnlocked() {
    $('loginCard').classList.add('hidden');
    $('appArea').classList.remove('hidden');
    clearMsg($('loginMsg'));
    loadDashboard();
  }

  async function doLogin() {
    const pw = $('adminPw').value;
    if (!pw) { showMsg($('loginMsg'), 'Enter admin password', true); return; }
    $('loginBtn').disabled = true;
    $('loginBtn').textContent = 'Checking...';
    const { json } = await api('auth', { adminPassword: pw });
    $('loginBtn').disabled = false;
    $('loginBtn').textContent = 'Unlock Admin Panel';
    if (json && json.ok && json.token) {
      $('adminPw').value = '';
      setToken(json.token);
      showUnlocked();
    } else {
      const msg = json.reason === 'rate_limited'
        ? 'Too many wrong attempts. Try again in ' + (json.retryAfterSec || 60) + 's'
        : (json.reason || 'Wrong admin password');
      showMsg($('loginMsg'), msg, true);
    }
  }

  function formatExpiry(expMs) {
    if (!expMs || expMs <= 0) return 'No expiry';
    const diff = expMs - Date.now();
    const dStr = new Date(expMs).toLocaleDateString();
    if (diff <= 0) return 'Expired (' + dStr + ')';
    const daysLeft = Math.ceil(diff / 86400000);
    return daysLeft + ' day(s) left (until ' + dStr + ')';
  }

  function renderUsers(users) {
    const box = $('usersBox');
    box.innerHTML = '';
    if (!users || users.length === 0) {
      const p = document.createElement('div');
      p.className = 'sub';
      p.textContent = 'No users created yet.';
      box.appendChild(p);
      return;
    }

    users.forEach((u) => {
      const item = document.createElement('div');
      item.className = 'user-item';

      const head = document.createElement('div');
      head.className = 'user-head';

      const name = document.createElement('div');
      name.className = 'uname';
      name.textContent = u.user;

      const badge = document.createElement('span');
      const isExpired = u.expiryMs > 0 && Date.now() > u.expiryMs;
      if (u.status !== 'true') {
        badge.className = 'badge b-bad';
        badge.textContent = 'BLOCKED';
      } else if (isExpired) {
        badge.className = 'badge b-warn';
        badge.textContent = 'EXPIRED';
      } else {
        badge.className = 'badge b-ok';
        badge.textContent = 'ACTIVE';
      }
      head.appendChild(name);
      head.appendChild(badge);

      const meta = document.createElement('div');
      meta.className = 'meta';
      meta.textContent =
        'Expiry: ' + formatExpiry(u.expiryMs) +
        ' | Device: ' + (u.device_id ? u.device_id : 'Any device (All users)');

      const actions = document.createElement('div');
      actions.className = 'actions';

      const editBtn = document.createElement('button');
      editBtn.className = 'btn-sec btn-sm';
      editBtn.textContent = 'Edit / New Pass';
      editBtn.onclick = () => {
        $('fUser').value = u.user;
        $('fPass').value = '';
        $('fDays').value = String(u.duration_days || 30);
        $('fStatus').value = u.status === 'true' ? 'true' : 'false';
        $('fDevice').value = u.device_id || '';
        window.scrollTo({ top: 0, behavior: 'smooth' });
      };

      const toggleBtn = document.createElement('button');
      toggleBtn.className = 'btn-sec btn-sm';
      toggleBtn.textContent = u.status === 'true' ? 'Block' : 'Unblock';
      toggleBtn.onclick = async () => {
        const nextStatus = u.status === 'true' ? 'false' : 'true';
        await api('setStatus', { nodeKey: u.nodeKey, status: nextStatus });
        loadDashboard();
      };

      const delBtn = document.createElement('button');
      delBtn.className = 'btn-danger btn-sm';
      delBtn.textContent = 'Delete';
      delBtn.onclick = async () => {
        if (!confirm('Delete user "' + u.user + '"?')) return;
        await api('deleteUser', { nodeKey: u.nodeKey });
        loadDashboard();
      };

      actions.appendChild(editBtn);
      actions.appendChild(toggleBtn);
      actions.appendChild(delBtn);

      item.appendChild(head);
      item.appendChild(meta);
      item.appendChild(actions);
      box.appendChild(item);
    });
  }

  async function loadDashboard() {
    const { json } = await api('dashboard', {});
    if (!json || !json.ok) return;
    renderUsers(json.users || []);
    if (json.update) {
      $('uVer').value = json.update.version || '';
      $('uTxt').value = json.update.message || '';
    }
  }

  $('loginBtn').onclick = doLogin;
  $('adminPw').addEventListener('keydown', (e) => { if (e.key === 'Enter') doLogin(); });
  $('logoutBtn').onclick = () => { setToken(''); showLocked(); };
  $('refreshBtn').onclick = loadDashboard;

  $('saveUserBtn').onclick = async () => {
    clearMsg($('saveMsg'));
    const username = $('fUser').value.trim().toLowerCase();
    const password = $('fPass').value;
    const days = Number($('fDays').value || 30);
    const status = $('fStatus').value;
    const deviceId = $('fDevice').value.trim();

    if (!username) { showMsg($('saveMsg'), 'Username is required', true); return; }

    $('saveUserBtn').disabled = true;
    const { json } = await api('upsertUser', { username, password, days, status, deviceId });
    $('saveUserBtn').disabled = false;

    if (json && json.ok) {
      $('fPass').value = '';
      showMsg($('saveMsg'), 'Saved user "' + username + '" successfully!', false);
      loadDashboard();
    } else {
      showMsg($('saveMsg'), (json && json.reason) || 'Failed to save user', true);
    }
  };

  $('saveUpdateBtn').onclick = async () => {
    clearMsg($('updMsg'));
    const version = $('uVer').value.trim();
    const message = $('uTxt').value.trim();
    const { json } = await api('setUpdate', { version, message });
    if (json && json.ok) {
      showMsg($('updMsg'), 'Update info saved!', false);
    } else {
      showMsg($('updMsg'), (json && json.reason) || 'Failed to update', true);
    }
  };

  if (getToken()) {
    showUnlocked();
  }
})();
</script>
</body>
</html>`;

function sendHtml(res, html, statusCode, headers) {
  if (typeof res.text === 'function') {
    return res.text(html, statusCode, headers);
  }
  return res.send(html, statusCode, headers);
}

module.exports = async ({ req, res, error }) => {
  try {
    const method = String(req.method || 'GET').toUpperCase();

    if (method === 'GET') {
      return sendHtml(res, HTML_PAGE, 200, {
        'content-type': 'text/html; charset=utf-8',
        'cache-control': 'no-store',
        'x-frame-options': 'DENY',
      });
    }

    const configuredAdminPw = (process.env.ADMIN_PASSWORD || '').trim();
    if (configuredAdminPw.length < 6) {
      return res.json(
        { ok: false, reason: 'ADMIN_PASSWORD env var is missing or too short (min 6 chars)' },
        503
      );
    }

    let body = {};
    if (req.bodyJson && typeof req.bodyJson === 'object') {
      body = req.bodyJson;
    } else if (typeof req.body === 'string' && req.body.trim()) {
      body = JSON.parse(req.body);
    } else if (req.body && typeof req.body === 'object') {
      body = req.body;
    }

    const action = String(body.action || '');

    if (action === 'auth') {
      const clientIp = getClientIp(req);
      const ipBucket = safeKey('adm_ip', clientIp);
      const limit = await checkAdminRateLimit(ipBucket);
      if (limit.blocked) {
        return res.json(
          { ok: false, reason: 'rate_limited', retryAfterSec: limit.retryAfterSec },
          429
        );
      }

      const supplied = String(body.adminPassword || '').trim();
      if (!supplied || !constTimeStringEqual(supplied, configuredAdminPw)) {
        await recordAdminFailure(ipBucket);
        return res.json({ ok: false, reason: 'Invalid admin password' }, 401);
      }

      await clearAdminRateLimit(ipBucket);
      return res.json({ ok: true, token: createSessionToken() }, 200);
    }

    const headers = req.headers || {};
    const tok = String(headers['x-admin-token'] || headers['X-Admin-Token'] || '').trim();
    if (!verifySessionToken(tok)) {
      return res.json({ ok: false, reason: 'Session expired. Please unlock again.' }, 401);
    }

    if (action === 'dashboard') {
      const [usersRaw, updateRaw] = await Promise.all([
        rtdbFetch('/User.json'),
        rtdbFetch('/update/up.json').catch(() => null),
      ]);

      const users = [];
      if (usersRaw && typeof usersRaw === 'object') {
        for (const [nodeKey, rec] of Object.entries(usersRaw)) {
          if (!rec || typeof rec !== 'object') continue;
          users.push({
            nodeKey,
            user: String(rec.user || nodeKey),
            status: String(rec.status || 'false'),
            duration_days: Number(rec.duration_days || 0),
            device_id: rec.device_id ? String(rec.device_id) : '',
            expiryMs: computeExpiryMs(rec),
          });
        }
      }
      users.sort((a, b) => a.user.localeCompare(b.user));

      return res.json(
        {
          ok: true,
          users,
          update: updateRaw && typeof updateRaw === 'object' ? updateRaw : { version: '', message: '' },
        },
        200
      );
    }

    if (action === 'upsertUser') {
      const username = String(body.username || '').trim().toLowerCase();
      const password = String(body.password || '');
      const days = Number(body.days || 30);
      const status = String(body.status) === 'false' ? 'false' : 'true';
      const deviceId = String(body.deviceId || '').trim();

      if (!username || username.length > 64) {
        return res.json({ ok: false, reason: 'Invalid username' }, 400);
      }
      if (!Number.isFinite(days) || days <= 0 || days >= 36500) {
        return res.json({ ok: false, reason: 'Validity days must be between 1 and 36500' }, 400);
      }

      const safeNode = username.replace(/[.#$\[\]\/]/g, '_');
      const existing = await rtdbFetch(`/User/${encodeURIComponent(safeNode)}.json`);

      if (!password && (!existing || !existing.passHash)) {
        return res.json({ ok: false, reason: 'Password is required for a new user' }, 400);
      }

      const patch = {
        user: username,
        pass: null,
        status,
        rgtime: Date.now(),
        duration_days: days,
        device_id: deviceId || null,
      };
      if (password) {
        patch.passHash = await hashPasswordScrypt(password);
      }

      await rtdbFetch(`/User/${encodeURIComponent(safeNode)}.json`, {
        method: 'PATCH',
        body: patch,
      });

      // Clear any login rate-limit lock for this username when admin saves/resets it
      await clearAdminRateLimit(safeKey('u', username));

      return res.json({ ok: true }, 200);
    }

    if (action === 'setStatus') {
      const nodeKey = String(body.nodeKey || '').trim();
      const status = String(body.status) === 'true' ? 'true' : 'false';
      if (!nodeKey || /[.#$\[\]\/]/.test(nodeKey)) {
        return res.json({ ok: false, reason: 'Invalid nodeKey' }, 400);
      }
      await rtdbFetch(`/User/${encodeURIComponent(nodeKey)}.json`, {
        method: 'PATCH',
        body: { status },
      });
      return res.json({ ok: true }, 200);
    }

    if (action === 'deleteUser') {
      const nodeKey = String(body.nodeKey || '').trim();
      if (!nodeKey || /[.#$\[\]\/]/.test(nodeKey)) {
        return res.json({ ok: false, reason: 'Invalid nodeKey' }, 400);
      }
      await rtdbFetch(`/User/${encodeURIComponent(nodeKey)}.json`, { method: 'DELETE' });
      return res.json({ ok: true }, 200);
    }

    if (action === 'setUpdate') {
      const version = String(body.version || '').trim();
      const message = String(body.message || '').trim();
      await rtdbFetch('/update/up.json', {
        method: 'PUT',
        body: { version, message },
      });
      return res.json({ ok: true }, 200);
    }

    return res.json({ ok: false, reason: 'Unknown action' }, 400);
  } catch (err) {
    if (error) error(`adminPanel error: ${err.message}`);
    return res.json({ ok: false, reason: 'Server error' }, 500);
  }
};
