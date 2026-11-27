package com.android.support;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * In-app live diagnostic log panel (compiled out of non-SEC_DIAG release builds).
 */
final class DiagLogViewer {

    private static final int MAX_LOGCAT_LINES = 200;
    private static final int MAX_LIVE_LINES   = 300;

    private static final ArrayDeque<String> LIVE_LINES = new ArrayDeque<String>(MAX_LIVE_LINES);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private static AlertDialog activeDialog = null;
    private static TextView activeLogTv = null;
    private static ScrollView activeScroll = null;
    private static String cachedLogcatSection = "";

    private DiagLogViewer() { }

    static void appendLiveLine(final String line) {
        if (!BuildConfig.SEC_DIAG || line == null) return;
        final String stamped;
        synchronized (LIVE_LINES) {
            if (LIVE_LINES.size() >= MAX_LIVE_LINES) {
                LIVE_LINES.removeFirst();
            }
            stamped = TIME_FMT.format(new Date()) + " " + line;
            LIVE_LINES.addLast(stamped);
        }
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                refreshOpenPanelText();
            }
        });
    }

    static void clearLiveLines() {
        if (!BuildConfig.SEC_DIAG) return;
        synchronized (LIVE_LINES) {
            LIVE_LINES.clear();
            cachedLogcatSection = "";
        }
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                if (activeLogTv != null) {
                    activeLogTv.setText("(cleared)");
                }
            }
        });
    }

    static void dismissActive() {
        if (!BuildConfig.SEC_DIAG) return;
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                if (activeDialog != null) {
                    try {
                        if (activeDialog.isShowing()) {
                            activeDialog.dismiss();
                        }
                    } catch (Throwable ignored) { }
                }
                activeDialog = null;
                activeLogTv = null;
                activeScroll = null;
            }
        });
    }

    static void show(final Context ctx, final boolean overlayRequired) {
        if (!BuildConfig.SEC_DIAG || ctx == null) return;

        if (activeDialog != null && activeDialog.isShowing()) {
            refreshOpenPanelText();
            return;
        }

        final DisplayMetrics dm = getMetrics(ctx);
        final float density = dm.density > 0f ? dm.density : 2f;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14, density);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(ModxTheme.dialogCardBg(ctx));

        TextView title = new TextView(ctx);
        title.setText("Live Diag Logs (PID " + Process.myPid() + " / API " + Build.VERSION.SDK_INT + ")");
        title.setTextColor(ModxTheme.ACCENT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        title.setTypeface(ModxTheme.FONT_SEMIBOLD);
        card.addView(title);

        final ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setBackground(ModxTheme.roundRect(ctx, ModxTheme.SURFACE, ModxTheme.LINE, 1f, ModxTheme.RADIUS_DP));
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (dm.heightPixels * 0.45f));
        sLp.setMargins(0, dp(10, density), 0, dp(10, density));
        scroll.setLayoutParams(sLp);

        final TextView logTv = new TextView(ctx);
        logTv.setText("Loading logs...");
        logTv.setTextColor(ModxTheme.FG);
        logTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        logTv.setTypeface(Typeface.MONOSPACE);
        logTv.setTextIsSelectable(true);
        int logPad = dp(10, density);
        logTv.setPadding(logPad, logPad, logPad, logPad);
        scroll.addView(logTv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(scroll);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);

        final AlertDialog[] dlgRef = new AlertDialog[1];

        Button testBtn  = makeBtn(ctx, "Self Test", true, density);
        Button copyBtn  = makeBtn(ctx, "Copy", false, density);
        Button clearBtn = makeBtn(ctx, "Clear", false, density);
        Button closeBtn = makeBtn(ctx, "Close", false, density);

        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                0, dp(38, density), 1.2f);
        tLp.setMargins(0, 0, dp(5, density), 0);
        testBtn.setLayoutParams(tLp);

        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                0, dp(38, density), 0.9f);
        bLp.setMargins(0, 0, dp(5, density), 0);
        copyBtn.setLayoutParams(bLp);

        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                0, dp(38, density), 0.9f);
        cLp.setMargins(0, 0, dp(5, density), 0);
        clearBtn.setLayoutParams(cLp);

        LinearLayout.LayoutParams xLp = new LinearLayout.LayoutParams(
                0, dp(38, density), 0.9f);
        closeBtn.setLayoutParams(xLp);

        btnRow.addView(testBtn);
        btnRow.addView(copyBtn);
        btnRow.addView(clearBtn);
        btnRow.addView(closeBtn);
        card.addView(btnRow);

        testBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                DiagSelfTestRunner.runAndShow(ctx, overlayRequired);
            }
        });

        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                try {
                    ClipboardManager cm = (ClipboardManager)
                            ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText(
                                "MXL_Logs", logTv.getText()));
                        Toast.makeText(ctx, "Logs copied", Toast.LENGTH_SHORT).show();
                    }
                } catch (Throwable ignored) { }
            }
        });

        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                clearLiveLines();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            java.lang.Process p = Runtime.getRuntime().exec(
                                    new String[]{"logcat", "-c"});
                            p.waitFor();
                        } catch (Throwable ignored) { }
                    }
                }).start();
            }
        });

        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                if (dlgRef[0] != null) {
                    try { dlgRef[0].dismiss(); } catch (Throwable ignored) { }
                }
            }
        });

        Context dialogCtx = (ctx instanceof Activity)
                ? ctx
                : new ContextThemeWrapper(ctx,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                ? android.R.style.Theme_Material_Dialog_Alert
                                : android.R.style.Theme_Holo_Dialog);
        AlertDialog.Builder b = new AlertDialog.Builder(dialogCtx);
        b.setView(card);
        AlertDialog dialog = b.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if (activeDialog == dlgRef[0]) {
                    activeDialog = null;
                    activeLogTv = null;
                    activeScroll = null;
                }
            }
        });
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (overlayRequired || !(ctx instanceof Activity)) {
                int winType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;
                w.setType(winType);
            }
            w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
        dlgRef[0] = dialog;
        activeDialog = dialog;
        activeLogTv = logTv;
        activeScroll = scroll;
        try {
            dialog.show();
            if (w != null) {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.width = Math.min(dp(370, density), (int) (dm.widthPixels * 0.94f));
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                w.setAttributes(lp);
            }
        } catch (Throwable t) {
            activeDialog = null;
            activeLogTv = null;
            activeScroll = null;
            SecurityNative.diagUi("DiagLogViewer.show failed: " + SecurityNative.diagException(t));
            return;
        }

        refreshOpenPanelText();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String logs = readOwnProcessLogs();
                synchronized (LIVE_LINES) {
                    cachedLogcatSection = logs;
                }
                MAIN_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshOpenPanelText();
                    }
                });
            }
        }).start();
    }

    private static void refreshOpenPanelText() {
        if (!BuildConfig.SEC_DIAG) return;
        if (activeDialog == null || !activeDialog.isShowing() || activeLogTv == null) return;

        StringBuilder sb = new StringBuilder();
        synchronized (LIVE_LINES) {
            if (!cachedLogcatSection.isEmpty()) {
                sb.append("=== Logcat ===\n").append(cachedLogcatSection);
                if (!cachedLogcatSection.endsWith("\n")) sb.append('\n');
            }
            if (!LIVE_LINES.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("=== Live Stream / Self Test ===\n");
                for (String l : LIVE_LINES) {
                    sb.append(l).append('\n');
                }
            }
        }
        if (sb.length() == 0) {
            sb.append("(Waiting for MXL / MXL_UI / Self Test output...)");
        }
        activeLogTv.setText(sb.toString());
        if (activeScroll != null) {
            final ScrollView sc = activeScroll;
            sc.post(new Runnable() {
                @Override
                public void run() {
                    sc.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    private static String readOwnProcessLogs() {
        if (!BuildConfig.SEC_DIAG) return "";
        int pid = Process.myPid();
        ArrayDeque<String> lines = new ArrayDeque<String>(MAX_LOGCAT_LINES);
        String[] cmd = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? new String[]{"logcat", "-d", "-v", "time", "--pid=" + pid}
                : new String[]{"logcat", "-d", "-v", "time", "-s", "MXL:V", "MXL_UI:V", "AndroidRuntime:E"};
        java.lang.Process proc = null;
        BufferedReader reader = null;
        try {
            proc = Runtime.getRuntime().exec(cmd);
            reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (matchesFilter(line)) {
                    if (lines.size() >= MAX_LOGCAT_LINES) {
                        lines.removeFirst();
                    }
                    lines.addLast(line);
                }
            }
            proc.waitFor();
        } catch (Throwable t) {
            return "(logcat unavailable: " + t.getClass().getSimpleName() + ")";
        } finally {
            try { if (reader != null) reader.close(); } catch (Throwable ignored) { }
            try { if (proc != null) proc.destroy(); } catch (Throwable ignored) { }
        }
        if (lines.isEmpty()) {
            return "(No logcat entries for PID " + pid + ")";
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private static boolean matchesFilter(String line) {
        return line.contains("MXL_UI")
                || line.contains("MXL")
                || line.contains("Mod_Main")
                || line.contains("Mod_Menu")
                || line.contains("AndroidRuntime");
    }

    private static Button makeBtn(Context ctx, String text, boolean primary, float density) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(primary ? ModxTheme.ON_ACCENT : ModxTheme.MUTED);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        b.setTypeface(primary ? ModxTheme.FONT_SEMIBOLD : ModxTheme.FONT_MEDIUM);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            b.setStateListAnimator(null);
        }
        b.setPadding(dp(6, density), 0, dp(6, density), 0);
        b.setBackground(primary
                ? ModxTheme.primaryButtonBg(ctx, ModxTheme.ACCENT)
                : ModxTheme.secondaryButtonBg(ctx));
        return b;
    }

    private static int dp(int v, float density) {
        return Math.round(v * density);
    }

    private static DisplayMetrics getMetrics(Context ctx) {
        DisplayMetrics dm = new DisplayMetrics();
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) {
                wm.getDefaultDisplay().getMetrics(dm);
                if (dm.widthPixels > 0 && dm.heightPixels > 0 && dm.density > 0f) return dm;
            }
        } catch (Throwable ignored) { }
        return android.content.res.Resources.getSystem().getDisplayMetrics();
    }
}
