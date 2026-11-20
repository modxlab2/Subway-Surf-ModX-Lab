package com.android.support;

import android.content.Context;
import android.content.SharedPreferences;

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

    private static final String PREF_AW_SESSION = "aw_session_cookie";

    private ModFirebase() { }

    /**
     * Authenticates against the Appwrite "verifyLogin" server function.
     * Returns the parsed function JSON response (or null on transport failure).
     */
    public static JSONObject login(Context ctx, String username, String password, String deviceId) {
        if (ctx == null || username == null || username.isEmpty()
                || password == null || password.isEmpty()
                || deviceId == null || deviceId.isEmpty()) {
            return null;
        }
        try {
            String endpoint = SecurityNative.getAppwriteEndpoint();
            String projectId = SecurityNative.getAppwriteProjectId();
            String functionId = SecurityNative.getVerifyLoginFunctionId();
            if (isEmpty(endpoint) || isEmpty(projectId) || isEmpty(functionId)) {
                return null;
            }

            String sessionCookie = getOrCreateSessionCookie(ctx, endpoint, projectId, false);
            if (isEmpty(sessionCookie)) {
                return null;
            }

            PinnedHttp.HttpResponse execResp = executeVerifyLogin(
                    endpoint, projectId, functionId, sessionCookie, username, password, deviceId);

            // If session expired/invalid, refresh anonymous session once and retry
            if (execResp != null && execResp.code == 401) {
                sessionCookie = getOrCreateSessionCookie(ctx, endpoint, projectId, true);
                if (isEmpty(sessionCookie)) return null;
                execResp = executeVerifyLogin(
                        endpoint, projectId, functionId, sessionCookie, username, password, deviceId);
            }

            if (execResp == null || isEmpty(execResp.body)) {
                return null;
            }

            if (execResp.code < 200 || execResp.code >= 300) {
                return null;
            }

            JSONObject wrapper = new JSONObject(execResp.body);
            String responseBody = wrapper.optString("responseBody", "");
            if (isEmpty(responseBody)) {
                return null;
            }
            return new JSONObject(responseBody);
        } catch (Exception e) {
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
            Context ctx, String endpoint, String projectId, boolean forceRefresh) {
        SharedPreferences prefs = ctx.getApplicationContext()
                .getSharedPreferences("KEY", Context.MODE_PRIVATE);

        if (!forceRefresh) {
            String cached = prefs.getString(PREF_AW_SESSION, "");
            if (!isEmpty(cached)) return cached;
        } else {
            prefs.edit().remove(PREF_AW_SESSION).apply();
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Appwrite-Project", projectId);

        String url = endpoint + "/account/sessions/anonymous";
        PinnedHttp.HttpResponse resp = PinnedHttp.postJson(url, "{}", headers);
        if (resp == null || resp.code < 200 || resp.code >= 300) {
            return null;
        }

        String cookieName = "a_session_" + projectId;
        String cookieVal = null;

        for (String setCookie : resp.setCookies) {
            if (setCookie == null) continue;
            String firstPart = setCookie.split(";", 2)[0].trim();
            if (firstPart.startsWith(cookieName + "=")
                    || firstPart.startsWith("a_session_")) {
                cookieVal = firstPart;
                break;
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

        if (!isEmpty(cookieVal)) {
            prefs.edit().putString(PREF_AW_SESSION, cookieVal).apply();
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