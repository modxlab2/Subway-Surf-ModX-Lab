package com.android.support;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ModX Lab — Main entry point
 * Thread-safe single-launch guarantee.
 */
public class Main {

    private static final String TAG = "Mod_Main";

    static {
        try {
            System.loadLibrary("ModXLab");
        } catch (Throwable t) {
            Log.e(TAG, "native load failed: " + t.getMessage());
        }
    }

    private static native void CheckOverlayPermission(Context context);
    private static native void setNativeCrashDir(String dir);

    private static final Object sLock = new Object();
    private static Menu sMenu = null;
    private static final AtomicBoolean sLaunchAttempted = new AtomicBoolean(false);

    private Main() { }

    // ==================================================================
    // Public API
    // ==================================================================

    public static void Start(final Context context) {
        if (context == null) return;
        if (!SecurityNative.preload(context)) return;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Start(context);
                }
            });
            return;
        }

        if (hasLaunched()) return;

        CrashHandler.init(context, false);

        try {
            java.io.File extDir = context.getExternalFilesDir(null);
            if (extDir != null) {
                setNativeCrashDir(extDir.getAbsolutePath());
            } else {
                setNativeCrashDir("/storage/emulated/0/Documents");
            }
        } catch (Throwable e) {
            try { setNativeCrashDir("/storage/emulated/0/Documents"); } catch (Throwable ignored) { }
        }

        try {
            CheckOverlayPermission(context);
        } catch (Throwable t) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diag(201, "CheckOverlayPermission failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    public static void startInProcessFallback(Context context) {
        if (context == null) return;
        if (!SecurityNative.preload(context)) return;
        if (!sLaunchAttempted.compareAndSet(false, true)) return;

        CrashHandler.init(context, true);

        try {
            Menu menu = new Menu(context);
            synchronized (sLock) { sMenu = menu; }
            boolean canOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
            if (canOverlay) {
                try {
                    menu.SetWindowManagerWindowService();
                    menu.ShowMenu();
                    return;
                } catch (Throwable t) {
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diag(203, "WindowService overlay failed, trying Activity window: " + t.getClass().getSimpleName());
                    }
                }
            }
            if (context instanceof Activity) {
                menu.SetWindowManagerActivity();
                menu.ShowMenu();
                return;
            }
            throw new IllegalStateException("No overlay or Activity window available");
        } catch (Throwable t) {
            Log.e(TAG, "In-process fallback failed: " + t);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diag(203, "In-process fallback failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            synchronized (sLock) { sMenu = null; }
            sLaunchAttempted.set(false);
        }
    }

    public static void StartWithoutPermission(Context context) {
        if (context == null) return;
        if (!SecurityNative.preload(context)) return;

        // Atomic single-launch guard
        if (!sLaunchAttempted.compareAndSet(false, true)) return;

        CrashHandler.init(context, true);

        if (!(context instanceof Activity)) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diag(202, "StartWithoutPermission skipped: context is not an Activity");
            }
            Toast.makeText(context,
                "Grant overlay permission in Settings to show mod menu",
                Toast.LENGTH_LONG).show();
            sLaunchAttempted.set(false);
            return;
        }

        try {
            Menu menu = new Menu(context);
            synchronized (sLock) { sMenu = menu; }
            menu.SetWindowManagerActivity();
            menu.ShowMenu();
        } catch (Throwable t) {
            Log.e(TAG, "Menu launch failed: " + t);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diag(202, "Activity window fallback failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            synchronized (sLock) { sMenu = null; }
            sLaunchAttempted.set(false);
            try {
                Toast.makeText(context,
                    "Mod menu failed: " + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
        }
    }

    public static void onDestroy() {
        Menu menu;
        synchronized (sLock) {
            menu = sMenu;
            sMenu = null;
        }
        if (menu != null) {
            try { menu.onDestroy(); } catch (Throwable ignored) { }
        }
    }

    public static Menu getMenu() {
        synchronized (sLock) { return sMenu; }
    }

    public static boolean hasLaunched() {
        return sLaunchAttempted.get();
    }
}