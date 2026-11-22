package com.android.support;

import android.content.Context;

/**
 * SecurityNative — native integrity & Ed25519 verification bridge.
 */
public final class SecurityNative {

    static {
        try {
            System.loadLibrary("ModXLab");
        } catch (Throwable ignored) {
        }
    }

    public static boolean preload(Context ctx) {
        if (ctx == null) return false;
        try {
            Context appCtx = ctx.getApplicationContext();
            return verifyNative(appCtx != null ? appCtx : ctx);
        } catch (Throwable t) {
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