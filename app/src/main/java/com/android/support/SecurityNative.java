package com.android.support;

import android.content.Context;

/**
 * SecurityNative — native integrity & Ed25519 verification bridge.
 */
public final class SecurityNative {

    private static volatile Throwable sLoadError;

    static {
        try {
            System.loadLibrary("ModXLab");
        } catch (Throwable t) {
            sLoadError = t;
            if (BuildConfig.SEC_DIAG) {
                diag(201, "System.loadLibrary(ModXLab) failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    public static void diag(int stage, String msg) {
        if (BuildConfig.SEC_DIAG) {
            android.util.Log.println(android.util.Log.INFO, "MXL", "[" + stage + "] " + msg);
        }
    }

    public static boolean preload(Context ctx) {
        if (ctx == null) return false;
        if (sLoadError != null) {
            if (BuildConfig.SEC_DIAG) {
                diag(201, "preload aborted: native lib unavailable (" + sLoadError.getClass().getSimpleName() + ": " + sLoadError.getMessage() + ")");
            }
            return false;
        }
        try {
            Context appCtx = ctx.getApplicationContext();
            boolean ok = verifyNative(appCtx != null ? appCtx : ctx);
            if (!ok && BuildConfig.SEC_DIAG) {
                diag(113, "SecurityNative.preload failed (verifyNative returned false)");
            }
            return ok;
        } catch (Throwable t) {
            if (BuildConfig.SEC_DIAG) {
                diag(201, "verifyNative threw " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return false;
        }
    }

    private static native boolean verifyNative(Context ctx);

    // =================================================================
    // Ed25519 Session Token & Obfuscated Config APIs
    // =================================================================
    public static native boolean verifySessionToken(String token,
                                                    String user,
                                                    String deviceId);
    public static native String getUpdateUrl();
    public static native String getAppwriteEndpoint();
    public static native String getAppwriteProjectId();
    public static native String getVerifyLoginFunctionId();

    private SecurityNative() { }
}