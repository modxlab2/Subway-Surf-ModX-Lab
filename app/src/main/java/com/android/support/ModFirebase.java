package com.android.support;

import android.content.Context;
import android.os.SystemClock;
import android.util.Base64;

import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-backed authentication and public update access.
 *
 * - Login & session verification run inside Appwrite Function "verifyLogin"
 *   (requires an Appwrite user session, obtained via anonymous session).
 * - Never fetches /User records or compares passwords on the client.
 * - Endpoint, project ID, and function ID come from native obfuscated getters.
 */
public final class ModFirebase {

    private static final SecureRandom sNonceRandom = new SecureRandom();
    private static final Object sSessionLock = new Object();
    private static final long PREWARM_COOLDOWN_MS = 20000L;
    private static final long UPDATE_CACHE_TTL_MS = 60000L;

    private static final AtomicBoolean sPrewarming = new AtomicBoolean(false);
    private static volatile long sLastPrewarmAtMs = 0L;
    private static volatile JSONObject sCachedUpdateJson = null;
    private static volatile long sCachedUpdateAtMs = 0L;

    private ModFirebase() { }

    public static String generateNonce() {
        byte[] bytes = new byte[24];
        sNonceRandom.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    /**
     * Background warm-up on login screen mount:
     * 1) Pre-initializes TLS factories for Appwrite & Firebase
     * 2) Ensures an Appwrite anonymous session cookie is ready
     * 3) Pings verifyLogin so the container, Google OAuth token cache, and Ed25519 keys are hot
     * 4) Pre-fetches /update.json into short-lived memory cache
     */
    public static void prewarm(final Context ctx) {
        if (ctx == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - sLastPrewarmAtMs < PREWARM_COOLDOWN_MS) return;
        if (!sPrewarming.compareAndSet(false, true)) return;
        sLastPrewarmAtMs = now;

        final Context appCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long t0 = SystemClock.elapsedRealtime();
                    final String endpoint = SecurityNative.getAppwriteEndpoint();
                    final String projectId = SecurityNative.getAppwriteProjectId();
                    final String functionId = SecurityNative.getVerifyLoginFunctionId();
                    final String updateUrl = SecurityNative.getUpdateUrl();
                    PinnedHttp.prewarm(endpoint, updateUrl);

                    Thread updThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            fetchUpdate(true);
                        }
                    });
                    updThread.setDaemon(true);
                    updThread.start();

                    if (!isEmpty(endpoint) && !isEmpty(projectId) && !isEmpty(functionId)) {
                        String[] errOut = new String[1];
                        String cookie = getOrCreateSessionCookie(appCtx, endpoint, projectId, false, errOut);
                        if (!isEmpty(cookie)) {
                            JSONObject pingPayload = new JSONObject();
                            pingPayload.put("action", "ping");
                            pingPayload.put("warmDb", true);
                            if (BuildConfig.SEC_DIAG) {
                                pingPayload.put("diag", true);
                            }
                            PinnedHttp.HttpResponse resp = executeVerifyLogin(
                                    endpoint, projectId, functionId, cookie, pingPayload);
                            if (resp != null && resp.code == 401) {
                                cookie = getOrCreateSessionCookie(appCtx, endpoint, projectId, true, errOut);
                                if (!isEmpty(cookie)) {
                                    executeVerifyLogin(endpoint, projectId, functionId, cookie, pingPayload);
                                }
                            }
                        }
                    }

                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diag(302, "prewarm done in " + (SystemClock.elapsedRealtime() - t0) + "ms");
                    }
                } catch (Exception ignored) {
                } finally {
                    sPrewarming.set(false);
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * Authenticates against the Appwrite "verifyLogin" server function.
     * Returns the parsed function JSON response.
     */
    public static JSONObject login(Context ctx, String username, String password, String deviceId, String nonce) {
        if (ctx == null || isEmpty(username) || isEmpty(password)
                || isEmpty(deviceId) || isEmpty(nonce)) {
            return errorJson("bad_input");
        }
        try {
            JSONObject innerPayload = new JSONObject();
            innerPayload.put("action", "login");
            innerPayload.put("username", username);
            innerPayload.put("password", password);
            innerPayload.put("deviceId", deviceId);
            innerPayload.put("nonce", nonce);
            if (BuildConfig.SEC_DIAG) {
                innerPayload.put("diag", true);
            }
            return callVerifyFunction(ctx, "login", innerPayload);
        } catch (Exception e) {
            return errorJson("bad_input");
        }
    }

    /**
     * Online verification for a remembered or active session token.
     */
    public static JSONObject verifySession(Context ctx, String token, String deviceId, String nonce) {
        if (ctx == null || isEmpty(token) || isEmpty(deviceId) || isEmpty(nonce)) {
            return errorJson("bad_input");
        }
        try {
            JSONObject innerPayload = new JSONObject();
            innerPayload.put("action", "verify_session");
            innerPayload.put("token", token);
            innerPayload.put("deviceId", deviceId);
            innerPayload.put("nonce", nonce);
            if (BuildConfig.SEC_DIAG) {
                innerPayload.put("diag", true);
            }
            return callVerifyFunction(ctx, "verify_session", innerPayload);
        } catch (Exception e) {
            return errorJson("bad_input");
        }
    }

    private static JSONObject callVerifyFunction(Context ctx, String actionLabel, JSONObject innerPayload) {
        long tStart = SystemClock.elapsedRealtime();
        try {
            String endpoint = SecurityNative.getAppwriteEndpoint();
            String projectId = SecurityNative.getAppwriteProjectId();
            String functionId = SecurityNative.getVerifyLoginFunctionId();
            if (isEmpty(endpoint) || isEmpty(projectId) || isEmpty(functionId)) {
                return errorJson("native_config");
            }

            long tCookieStart = SystemClock.elapsedRealtime();
            String[] sessionErr = new String[1];
            String sessionCookie = getOrCreateSessionCookie(ctx, endpoint, projectId, false, sessionErr);
            long cookieMs = SystemClock.elapsedRealtime() - tCookieStart;
            if (isEmpty(sessionCookie)) {
                return errorJson(!isEmpty(sessionErr[0]) ? sessionErr[0] : "network");
            }

            long tExecStart = SystemClock.elapsedRealtime();
            PinnedHttp.HttpResponse execResp = executeVerifyLogin(
                    endpoint, projectId, functionId, sessionCookie, innerPayload);

            // If session expired/invalid, refresh anonymous session once and retry
            if (execResp != null && execResp.code == 401) {
                sessionCookie = getOrCreateSessionCookie(ctx, endpoint, projectId, true, sessionErr);
                if (isEmpty(sessionCookie)) {
                    return errorJson(!isEmpty(sessionErr[0]) ? sessionErr[0] : "http_401");
                }
                execResp = executeVerifyLogin(
                        endpoint, projectId, functionId, sessionCookie, innerPayload);
            }
            long execMs = SystemClock.elapsedRealtime() - tExecStart;

            if (execResp == null) {
                return errorJson(PinnedHttp.getLastError());
            }

            if (execResp.code < 200 || execResp.code >= 300) {
                return errorJson("http_" + execResp.code);
            }

            if (isEmpty(execResp.body)) {
                return errorJson("empty_body");
            }

            JSONObject wrapper = new JSONObject(execResp.body);
            String responseBody = wrapper.optString("responseBody", "");
            if (isEmpty(responseBody)) {
                return errorJson("empty_func_body");
            }
            JSONObject result = new JSONObject(responseBody);

            if (BuildConfig.SEC_DIAG) {
                long totalMs = SystemClock.elapsedRealtime() - tStart;
                JSONObject st = result.optJSONObject("serverTiming");
                String stStr = (st != null) ? st.toString() : "none";
                SecurityNative.diag(302, actionLabel + " cookieMs=" + cookieMs
                        + " execMs=" + execMs + " totalMs=" + totalMs + " server=" + stStr);
            }

            return result;
        } catch (Exception e) {
            return errorJson("network");
        }
    }

    private static JSONObject errorJson(String reason) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("ok", false);
            obj.put("reason", reason);
            return obj;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static PinnedHttp.HttpResponse executeVerifyLogin(
            String endpoint,
            String projectId,
            String functionId,
            String sessionCookie,
            JSONObject innerPayload) throws Exception {

        JSONObject innerHeaders = new JSONObject();
        innerHeaders.put("content-type", "application/json");

        JSONObject execBody = new JSONObject();
        execBody.put("body", innerPayload.toString());
        execBody.put("async", false);
        execBody.put("path", "/");
        execBody.put("method", "POST");
        execBody.put("headers", innerHeaders);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Appwrite-Project", projectId);
        headers.put("Cookie", sessionCookie);

        String url = endpoint + "/functions/" + functionId + "/executions";
        return PinnedHttp.postJson(url, execBody.toString(), headers);
    }

    private static String getOrCreateSessionCookie(
            Context ctx, String endpoint, String projectId, boolean forceRefresh, String[] errOut) {
        synchronized (sSessionLock) {
            if (!forceRefresh) {
                String cached = SecureTokenStore.loadAppwriteSession(ctx);
                if (!isEmpty(cached) && !cached.startsWith("a_session_" + projectId + "_legacy=")) {
                    return cached;
                }
            } else {
                SecureTokenStore.clearAppwriteSession(ctx);
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("X-Appwrite-Project", projectId);

            String url = endpoint + "/account/sessions/anonymous";
            PinnedHttp.HttpResponse resp = PinnedHttp.postJson(url, "{}", headers);
            if (resp == null) {
                if (errOut != null && errOut.length > 0) errOut[0] = PinnedHttp.getLastError();
                return null;
            }
            if (resp.code < 200 || resp.code >= 300) {
                if (errOut != null && errOut.length > 0) errOut[0] = "http_" + resp.code;
                return null;
            }

            String cookieName = "a_session_" + projectId;
            String cookieVal = null;
            String fallbackCookie = null;

            for (String setCookie : resp.setCookies) {
                if (setCookie == null) continue;
                String firstPart = setCookie.split(";", 2)[0].trim();
                if (firstPart.startsWith(cookieName + "=")) {
                    cookieVal = firstPart;
                    break;
                } else if (fallbackCookie == null && firstPart.startsWith("a_session_")) {
                    fallbackCookie = firstPart;
                }
            }

            if (isEmpty(cookieVal) && !isEmpty(resp.body)) {
                try {
                    JSONObject json = new JSONObject(resp.body);
                    String secret = json.optString("secret", "");
                    if (!isEmpty(secret)) {
                        cookieVal = cookieName + "=" + secret;
                    }
                } catch (Exception ignored) { }
            }

            if (isEmpty(cookieVal)) {
                cookieVal = fallbackCookie;
            }

            if (!isEmpty(cookieVal)) {
                SecureTokenStore.saveAppwriteSession(ctx, cookieVal);
            } else if (errOut != null && errOut.length > 0) {
                errOut[0] = "no_session_cookie";
            }
            return cookieVal;
        }
    }

    /**
     * Fetch public update info from Firebase /update.json (uses short-lived prewarm cache).
     */
    public static JSONObject fetchUpdate() {
        return fetchUpdate(false);
    }

    public static JSONObject fetchUpdate(boolean forceNetwork) {
        long now = SystemClock.elapsedRealtime();
        JSONObject cached = sCachedUpdateJson;
        if (!forceNetwork && cached != null && (now - sCachedUpdateAtMs) < UPDATE_CACHE_TTL_MS) {
            return cached;
        }
        try {
            long t0 = SystemClock.elapsedRealtime();
            String urlStr = SecurityNative.getUpdateUrl();
            if (isEmpty(urlStr)) return null;

            String raw = PinnedHttp.get(urlStr);
            if (isEmpty(raw) || "null".equals(raw)) return null;
            JSONObject parsed = new JSONObject(raw);
            sCachedUpdateJson = parsed;
            sCachedUpdateAtMs = SystemClock.elapsedRealtime();
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diag(302, "fetchUpdate netMs=" + (sCachedUpdateAtMs - t0));
            }
            return parsed;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}