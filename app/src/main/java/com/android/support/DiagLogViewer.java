package com.android.support;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import java.util.ArrayDeque;

/**
 * In-app live diagnostic log viewer (compiled out of non-SEC_DIAG release builds).
 */
final class DiagLogViewer {

    private static final int MAX_LINES = 200;

    private DiagLogViewer() { }

    static void show(final Context ctx, final boolean overlayRequired) {
        if (!BuildConfig.SEC_DIAG || ctx == null) return;

        final DisplayMetrics dm = getMetrics(ctx);
        final float density = dm.density > 0f ? dm.density : 2f;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12, density);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#141720"));
        bg.setCornerRadius(dp(12, density));
        bg.setStroke(dp(1, density), Color.parseColor("#3DDB87"));
        card.setBackground(bg);

        TextView title = new TextView(ctx);
        title.setText("Live Diag Logs (PID " + Process.myPid() + " / API " + Build.VERSION.SDK_INT + ")");
        title.setTextColor(Color.parseColor("#3DDB87"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);

        final ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(Color.parseColor("#0B0D12"));
        logBg.setCornerRadius(dp(8, density));
        logBg.setStroke(dp(1, density), Color.parseColor("#2A2F3D"));
        scroll.setBackground(logBg);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (dm.heightPixels * 0.45f));
        sLp.setMargins(0, dp(8, density), 0, dp(8, density));
        scroll.setLayoutParams(sLp);

        final TextView logTv = new TextView(ctx);
        logTv.setText("Loading logs...");
        logTv.setTextColor(Color.parseColor("#D9DEE8"));
        logTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        logTv.setTypeface(Typeface.MONOSPACE);
        logTv.setTextIsSelectable(true);
        int logPad = dp(8, density);
        logTv.setPadding(logPad, logPad, logPad, logPad);
        scroll.addView(logTv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(scroll);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);

        final AlertDialog[] dlgRef = new AlertDialog[1];

        Button copyBtn = makeBtn(ctx, "Copy", "#2BB673", "#0C2418", density);
        Button clearBtn = makeBtn(ctx, "Clear", "#3A3F4D", "#ECE8DF", density);
        Button closeBtn = makeBtn(ctx, "Close", "#262934", "#A7A9B2", density);

        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                0, dp(36, density), 1f);
        bLp.setMargins(0, 0, dp(6, density), 0);
        copyBtn.setLayoutParams(bLp);

        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                0, dp(36, density), 1f);
        cLp.setMargins(0, 0, dp(6, density), 0);
        clearBtn.setLayoutParams(cLp);

        LinearLayout.LayoutParams xLp = new LinearLayout.LayoutParams(
                0, dp(36, density), 1f);
        closeBtn.setLayoutParams(xLp);

        btnRow.addView(copyBtn);
        btnRow.addView(clearBtn);
        btnRow.addView(closeBtn);
        card.addView(btnRow);

        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                logTv.setText("(cleared)");
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
        try {
            dialog.show();
            if (w != null) {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.width = Math.min(dp(360, density), (int) (dm.widthPixels * 0.94f));
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                w.setAttributes(lp);
            }
        } catch (Throwable t) {
            SecurityNative.diagUi("DiagLogViewer.show failed: " + SecurityNative.diagException(t));
            return;
        }

        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String logs = readOwnProcessLogs();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        logTv.setText(logs);
                        scroll.post(new Runnable() {
                            @Override
                            public void run() {
                                scroll.fullScroll(View.FOCUS_DOWN);
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private static String readOwnProcessLogs() {
        if (!BuildConfig.SEC_DIAG) return "";
        int pid = Process.myPid();
        ArrayDeque<String> lines = new ArrayDeque<String>(MAX_LINES);
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
                    if (lines.size() >= MAX_LINES) {
                        lines.removeFirst();
                    }
                    lines.addLast(line);
                }
            }
            proc.waitFor();
        } catch (Throwable t) {
            return "Failed to read logcat: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        } finally {
            try { if (reader != null) reader.close(); } catch (Throwable ignored) { }
            try { if (proc != null) proc.destroy(); } catch (Throwable ignored) { }
        }
        if (lines.isEmpty()) {
            return "(No MXL / MXL_UI / AndroidRuntime lines found for PID " + pid + ")";
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

    private static Button makeBtn(Context ctx, String text, String bgHex, String fgHex, float density) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor(fgHex));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(6, density), 0, dp(6, density), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(bgHex));
        g.setCornerRadius(dp(8, density));
        b.setBackground(g);
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
