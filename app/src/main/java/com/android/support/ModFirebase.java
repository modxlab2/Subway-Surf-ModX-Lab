package com.android.support;

import android.content.Context;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-backed authentication and public update access.
 *
 * - Login verification runs inside Appwrite Function "verifyLogin"
 *   (requires an Appwrite user session, obtained via anonymous session).
 * - Never fetches /User records or compares passwords on the client.
 * - Endpoint, project ID, and function ID come from native obfuscated getters.
 */
public final class ModFirebase {

    private ModFirebase() { }

    /**
     * Authenticates against the Appwrite "verifyLogin" server function.
     * Returns the parsed function JSON response (or null on transport failure).
     */
    public static JSONObject login(Context ctx, String username, String password, String deviceId) {
        if (ctx == null || username == null || username.isEmpty()
                || password == null || password.isEmpty()
                || deviceId == null || deviceId.isEmpty()) {
            return errorJson("bad_input");
        }
        try {
            String endpoint = SecurityNative.getAppwriteEndpoint();
            String projectId = SecurityNative.getAppwriteProjectId();
            String functionId = SecurityNative.getVerifyLoginFunctionId();
            if (isEmpty(endpoint) || isEmpty(projectId) || isEmpty(functionId)) {
                return errorJson("native_config");
            }

            String[] sessionErr = new String[1];
            String sessionCookie = getOrCreateSessionCookie(ctx, endpoint, projectId, false, sessionErr);
            if (isEmpty(sessionCookie)) {
                return errorJson(!isEmpty(sessionErr[0]) ? sessionErr[0] : "network");
            }

            PinnedHttp.HttpResponse execResp = executeVerifyLogin(
                    endpoint, projectId, functionId, sessionCookie, username, password, deviceId);

            // If session expired/invalid, refresh anonymous session once and retry
            if (execResp != null && execResp.code == 401) {
                sessionCookie = getOrCreateSessionCookie(ctx, endpoint, projectId, true, sessionErr);
                if (isEmpty(sessionCookie)) {
                    return errorJson(!isEmpty(sessionErr[0]) ? sessionErr[0] : "http_401");
                }
                execResp = executeVerifyLogin(
                        endpoint, projectId, functionId, sessionCookie, username, password, deviceId);
            }

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
            return new JSONObject(responseBody);
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
            String username,
            String password,
            String deviceId) throws Exception {

        JSONObject innerPayload = new JSONObject();
        innerPayload.put("username", username);
        innerPayload.put("password", password);
        innerPayload.put("deviceId", deviceId);

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

    /**
     * Fetch public update info from Firebase /update.json.
     */
    public static JSONObject fetchUpdate() {
        try {
            String urlStr = SecurityNative.getUpdateUrl();
            if (isEmpty(urlStr)) return null;

            String raw = PinnedHttp.get(urlStr);
            if (isEmpty(raw) || "null".equals(raw)) return null;
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}