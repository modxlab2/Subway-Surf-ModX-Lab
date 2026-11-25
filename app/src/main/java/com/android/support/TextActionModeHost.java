package com.android.support;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Hosts text-selection / insertion ActionMode for EditTexts living inside
 * WindowManager.addView() windows (both Activity and Service overlay modes).
 *
 * Strategy:
 *  1) Always tries the real framework ActionMode path first (via a transparent
 *     non-touchable/non-focusable host Dialog matching the Menu's window type).
 *  2) Falls back automatically at runtime to HostedPopupActionMode (a system-
 *     faithful themed PopupWindow implementing ActionMode) only when the
 *     framework path fails (null, exception, premature destroy, 0 items, or
 *     content rect never requested within 180ms).
 *  3) In SEC_DIAG builds only, supports cycling AUTO -> FW -> POPUP for testing.
 */
final class TextActionModeHost {

    static final int DIAG_MODE_AUTO            = 0;
    static final int DIAG_MODE_FORCE_FRAMEWORK = 1;
    static final int DIAG_MODE_FORCE_POPUP     = 2;

    private static volatile int diagMode = DIAG_MODE_AUTO;

    private final Context ctx;
    private final boolean overlayRequired;

    private Dialog hostDialog;
    private View dummyView;
    private boolean hostAttached = false;

    private ActionMode activeMode;
    private EditText activeOwner;
    private ActionMode.Callback activeOrigCallback;
    private int activeType = 0;
    private boolean activeHasTypeArg = false;
    private boolean destroyed = false;

    private boolean finishingByHost = false;
    private boolean modeItemClicked = false;
    private boolean frameworkCreatedOk = false;
    private int frameworkPreparedVisibleCount = 0;
    private int frameworkContentRectCalls = 0;
    private long modeCreatedAtMs = 0L;
    private boolean suppressedForDrag = false;
    private Runnable healthCheckRunnable = null;

    private final int[] etScreenLoc = new int[2];
    private final int[] etWinLoc = new int[2];
    private final int[] dummyLoc = new int[2];
    private final Rect tmpVisRect = new Rect();

    private EditText observedEditText;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private int lastObsScreenX = Integer.MIN_VALUE;
    private int lastObsScreenY = Integer.MIN_VALUE;
    private int lastObsWinX = Integer.MIN_VALUE;
    private int lastObsWinY = Integer.MIN_VALUE;
    private int lastObsScrollX = Integer.MIN_VALUE;
    private int lastObsSelStart = Integer.MIN_VALUE;
    private int lastObsSelEnd = Integer.MIN_VALUE;
    private int lastObsVisBottom = Integer.MIN_VALUE;
    private int lastObsVisTop = Integer.MIN_VALUE;

    TextActionModeHost(Context context, boolean overlayRequired) {
        this.ctx = context;
        this.overlayRequired = overlayRequired;
    }

    static int getDiagMode() {
        return BuildConfig.SEC_DIAG ? diagMode : DIAG_MODE_AUTO;
    }

    static int cycleDiagMode() {
        if (!BuildConfig.SEC_DIAG) return DIAG_MODE_AUTO;
        diagMode = (diagMode + 1) % 3;
        return diagMode;
    }

    static String getDiagModeLabel() {
        if (!BuildConfig.SEC_DIAG) return "";
        switch (diagMode) {
            case DIAG_MODE_FORCE_FRAMEWORK: return "FW";
            case DIAG_MODE_FORCE_POPUP:     return "POPUP";
            default:                        return "AUTO";
        }
    }

    void prepare() {
        if (destroyed || ctx == null) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("prepare skipped: destroyed=" + destroyed + " ctxNull=" + (ctx == null));
            }
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }

        if (hostDialog != null) {
            boolean showing = false;
            try { showing = hostDialog.isShowing(); } catch (Throwable ignored) { }
            boolean tokenMissing = dummyView == null || (hostAttached && dummyView.getWindowToken() == null);
            if (showing && dummyView != null && !tokenMissing) {
                return;
            }
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("prepare: recreating host dialog (showing=" + showing
                        + " attached=" + hostAttached
                        + " token=" + (dummyView != null && dummyView.getWindowToken() != null) + ")");
            }
            dismissDialogOnly();
        }

        if (!canShowHostWindow()) {
            return;
        }

        try {
            Context themedDlgCtx = createDeviceFloatingThemeContext(ctx);
            final Dialog dialog = new Dialog(themedDlgCtx, android.R.style.Theme_DeviceDefault_NoActionBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            Context dlgCtx = dialog.getContext();
            FrameLayout container = new FrameLayout(dlgCtx);
            container.setBackgroundColor(Color.TRANSPARENT);

            final View dummy = new View(dlgCtx);
            dummy.setBackgroundColor(Color.TRANSPARENT);
            dummy.setClickable(false);
            dummy.setFocusable(false);
            dummy.setFocusableInTouchMode(false);
            dummy.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    hostAttached = true;
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("host dummyView attached (token="
                                + (v.getWindowToken() != null)
                                + ", winFocus=" + v.hasWindowFocus()
                                + ", size=" + v.getWidth() + "x" + v.getHeight() + ")");
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    hostAttached = false;
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("host dummyView detached");
                    }
                }
            });
            if (BuildConfig.SEC_DIAG) {
                dummy.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    private int prevW = -1, prevH = -1;
                    @Override
                    public void onLayoutChange(View v, int l, int t, int r, int b,
                                               int ol, int ot, int or, int ob) {
                        int w = r - l;
                        int h = b - t;
                        if (w != prevW || h != prevH) {
                            prevW = w;
                            prevH = h;
                            SecurityNative.diagUi("host dummyView layout: size=" + w + "x" + h);
                        }
                    }
                });
            }
            container.addView(dummy, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            dialog.setContentView(container, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            Window w = dialog.getWindow();
            int resolvedWinType = -1;
            if (w != null) {
                w.setFormat(PixelFormat.TRANSLUCENT);
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
                if (overlayRequired || !(ctx instanceof Activity)) {
                    resolvedWinType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE;
                    w.setType(resolvedWinType);
                } else {
                    resolvedWinType = WindowManager.LayoutParams.TYPE_APPLICATION;
                }
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }

            dialog.show();

            if (w != null) {
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                lp.x = 0;
                lp.y = 0;
                lp.dimAmount = 0f;
                w.setAttributes(lp);
            }

            this.hostDialog = dialog;
            this.dummyView = dummy;
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("prepare: host dialog shown (ctx="
                        + ctx.getClass().getSimpleName()
                        + ", isActivity=" + (ctx instanceof Activity)
                        + ", overlay=" + overlayRequired
                        + ", winType=" + resolvedWinType + ")");
            }
        } catch (Throwable t) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("prepare failed: " + SecurityNative.diagException(t));
            }
            dismissDialogOnly();
        }
    }

    boolean isHostReady() {
        if (hostDialog == null || dummyView == null) return false;
        try {
            if (!hostDialog.isShowing()) return false;
        } catch (Throwable ignored) {
            return false;
        }
        if (!hostAttached || dummyView.getWindowToken() == null || !dummyView.isShown()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !dummyView.isAttachedToWindow()) {
            return false;
        }
        return dummyView.getWidth() > 0 && dummyView.getHeight() > 0;
    }

    ActionMode startActionMode(EditText owner, ActionMode.Callback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return startActionMode(owner, callback, ActionMode.TYPE_PRIMARY);
        }
        return startActionModeInternal(owner, callback, 0, false);
    }

    ActionMode startActionMode(EditText owner, ActionMode.Callback callback, int type) {
        return startActionModeInternal(owner, callback, type, true);
    }

    private ActionMode startActionModeInternal(final EditText owner,
                                               final ActionMode.Callback origCallback,
                                               final int type,
                                               final boolean hasTypeArg) {
        if (destroyed || owner == null || origCallback == null) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("startActionMode skipped: destroyed=" + destroyed
                        + " ownerNull=" + (owner == null)
                        + " cbNull=" + (origCallback == null));
            }
            return null;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return null;
        }

        if (activeMode != null) {
            finishActiveMode(activeOwner != owner ? "switch-owner" : "restart-mode", false);
        }
        cancelHealthCheck(owner);
        suppressedForDrag = false;

        final int modePref = getDiagMode();

        if (modePref == DIAG_MODE_FORCE_POPUP) {
            return startPopupCloneMode(owner, origCallback, type, hasTypeArg, "diag-force-popup");
        }

        if (!isHostReady()) {
            prepare();
        }

        boolean ready = isHostReady();
        boolean tokenPresent = dummyView != null && dummyView.getWindowToken() != null;
        if (!ready && !(modePref == DIAG_MODE_FORCE_FRAMEWORK && tokenPresent)) {
            if (BuildConfig.SEC_DIAG) {
                boolean showing = false;
                try { showing = hostDialog != null && hostDialog.isShowing(); } catch (Throwable ignored) { }
                SecurityNative.diagUi("framework-host skipped (mode=" + getDiagModeLabel()
                        + "): host not ready (showing=" + showing
                        + ", attached=" + hostAttached
                        + ", token=" + tokenPresent
                        + ", shown=" + (dummyView != null && dummyView.isShown())
                        + ", size=" + (dummyView != null ? dummyView.getWidth() + "x" + dummyView.getHeight() : "null")
                        + ")");
            }
            if (modePref == DIAG_MODE_FORCE_FRAMEWORK) {
                return null;
            }
            return startPopupCloneMode(owner, origCallback, type, hasTypeArg, "framework-host-not-ready");
        }

        FrameworkAttempt attempt = tryStartFrameworkMode(owner, origCallback, type, hasTypeArg);
        if (attempt.mode != null) {
            return attempt.mode;
        }

        if (modePref == DIAG_MODE_FORCE_FRAMEWORK) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("toolbar path failed (force-framework, no fallback): reason=" + attempt.failReason);
            }
            return null;
        }

        return startPopupCloneMode(owner, origCallback, type, hasTypeArg, attempt.failReason);
    }

    private static final class FrameworkAttempt {
        final ActionMode mode;
        final String failReason;

        FrameworkAttempt(ActionMode mode, String failReason) {
            this.mode = mode;
            this.failReason = failReason;
        }
    }

    private FrameworkAttempt tryStartFrameworkMode(final EditText owner,
                                                   final ActionMode.Callback origCallback,
                                                   final int type,
                                                   final boolean hasTypeArg) {
        try {
            finishingByHost = false;
            modeItemClicked = false;
            frameworkCreatedOk = false;
            frameworkPreparedVisibleCount = 0;
            frameworkContentRectCalls = 0;
            modeCreatedAtMs = SystemClock.uptimeMillis();

            final ActionMode mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasTypeArg) {
                mode = Api23Helper.start(this, dummyView, owner, origCallback, type);
            } else {
                ActionMode.Callback legacyWrapped = new ActionMode.Callback() {
                    @Override
                    public boolean onCreateActionMode(ActionMode m, Menu menu) {
                        boolean ok = origCallback.onCreateActionMode(m, menu);
                        onHostModeCreated(owner, m, menu, ok);
                        return ok;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode m, Menu menu) {
                        boolean ok = origCallback.onPrepareActionMode(m, menu);
                        onHostModePrepared(owner, menu, ok);
                        return ok;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode m, MenuItem item) {
                        modeItemClicked = true;
                        if (BuildConfig.SEC_DIAG) {
                            SecurityNative.diagUi("framework.onClick: itemId="
                                    + (item != null ? item.getItemId() : -1));
                        }
                        return origCallback.onActionItemClicked(m, item);
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode m) {
                        onHostModeDestroyed(owner, m);
                        origCallback.onDestroyActionMode(m);
                    }
                };
                mode = dummyView.startActionMode(legacyWrapped);
            }

            if (mode == null) {
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("framework-host returned null (type=" + type
                            + ", mode=" + getDiagModeLabel() + ")");
                }
                return new FrameworkAttempt(null, "framework-returned-null");
            }

            if (!frameworkCreatedOk || frameworkPreparedVisibleCount <= 0) {
                String why = !frameworkCreatedOk ? "framework-create-false" : "framework-0-items";
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("framework-host unusable synchronously (" + why
                            + ", visibleItems=" + frameworkPreparedVisibleCount + ")");
                }
                finishingByHost = true;
                try { mode.finish(); } catch (Throwable ignored) { }
                finishingByHost = false;
                return new FrameworkAttempt(null, why);
            }

            activeMode = mode;
            activeOwner = owner;
            activeOrigCallback = origCallback;
            activeType = type;
            activeHasTypeArg = hasTypeArg;
            attachPositionObserver(owner);

            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("toolbar path chosen: framework-host (mode=" + getDiagModeLabel()
                        + ", type=" + type
                        + ", overlay=" + overlayRequired
                        + ", api=" + Build.VERSION.SDK_INT
                        + ", items=" + frameworkPreparedVisibleCount + ")");
            }

            if (getDiagMode() == DIAG_MODE_AUTO
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && type == ActionMode.TYPE_FLOATING) {
                scheduleFrameworkHealthCheck(owner, mode, origCallback, type, hasTypeArg);
            }
            return new FrameworkAttempt(mode, null);
        } catch (Throwable t) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("framework-host threw: " + SecurityNative.diagException(t));
            }
            return new FrameworkAttempt(null, "framework-exception-" + t.getClass().getSimpleName());
        }
    }

    private void scheduleFrameworkHealthCheck(final EditText owner,
                                              final ActionMode fwMode,
                                              final ActionMode.Callback origCallback,
                                              final int type,
                                              final boolean hasTypeArg) {
        cancelHealthCheck(owner);
        healthCheckRunnable = new Runnable() {
            @Override
            public void run() {
                healthCheckRunnable = null;
                if (destroyed || activeMode != fwMode || activeOwner != owner) {
                    return;
                }
                if (!isViewAttached(owner) || !owner.hasFocus() || suppressedForDrag) {
                    return;
                }
                if (frameworkContentRectCalls == 0) {
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("framework-host toolbar not shown (onGetContentRect=0 after 180ms) -> falling back to popup-clone");
                    }
                    fallbackFromBrokenFramework(owner, origCallback, type, hasTypeArg, "framework-not-shown-timeout");
                }
            }
        };
        owner.postDelayed(healthCheckRunnable, 180L);
    }

    private void cancelHealthCheck(EditText owner) {
        if (healthCheckRunnable != null && owner != null) {
            owner.removeCallbacks(healthCheckRunnable);
        }
        healthCheckRunnable = null;
    }

    private void fallbackFromBrokenFramework(final EditText owner,
                                             final ActionMode.Callback origCallback,
                                             final int type,
                                             final boolean hasTypeArg,
                                             final String reason) {
        if (destroyed || owner == null || !isViewAttached(owner) || !owner.hasFocus()) return;
        if (getDiagMode() == DIAG_MODE_FORCE_FRAMEWORK) return;

        final int s = Math.max(0, owner.getSelectionStart());
        final int e = Math.max(0, owner.getSelectionEnd());

        finishActiveMode(reason, false);

        owner.post(new Runnable() {
            @Override
            public void run() {
                if (destroyed || !isViewAttached(owner) || !owner.hasFocus() || activeMode != null) {
                    return;
                }
                try {
                    if (owner.getText() instanceof Spannable) {
                        int len = owner.getText().length();
                        int cs = Math.min(s, len);
                        int ce = Math.min(e, len);
                        Selection.setSelection(owner.getText(), cs, ce);
                    }
                } catch (Throwable ignored) { }
                startPopupCloneMode(owner, origCallback, type, hasTypeArg, reason);
            }
        });
    }

    private ActionMode startPopupCloneMode(final EditText owner,
                                           final ActionMode.Callback origCallback,
                                           final int type,
                                           final boolean hasTypeArg,
                                           final String reason) {
        try {
            HostedPopupActionMode mode = new HostedPopupActionMode(
                    this, ctx, owner, origCallback, type, hasTypeArg);
            if (!mode.start()) {
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("popup-clone aborted: 0 usable items (reason=" + reason + ")");
                }
                return null;
            }
            activeMode = mode;
            activeOwner = owner;
            activeOrigCallback = origCallback;
            activeType = type;
            activeHasTypeArg = hasTypeArg;
            attachPositionObserver(owner);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("toolbar path chosen: popup-clone (reason=" + reason
                        + ", mode=" + getDiagModeLabel()
                        + ", type=" + type
                        + ", overlay=" + overlayRequired
                        + ", api=" + Build.VERSION.SDK_INT + ")");
            }
            return mode;
        } catch (Throwable t) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("popup-clone failed (" + reason + "): "
                        + SecurityNative.diagException(t));
            }
            return null;
        }
    }

    private void onHostModeCreated(EditText owner, ActionMode m, Menu menu, boolean ok) {
        modeCreatedAtMs = SystemClock.uptimeMillis();
        modeItemClicked = false;
        frameworkCreatedOk = ok;
        sanitizePasswordMenu(owner, menu);
        int vis = countVisibleEnabledMenuItems(menu);
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("framework.onCreate: ret=" + ok + " visibleItems=" + vis);
        }
    }

    private void onHostModePrepared(EditText owner, Menu menu, boolean ok) {
        sanitizePasswordMenu(owner, menu);
        int vis = countVisibleEnabledMenuItems(menu);
        frameworkPreparedVisibleCount = vis;
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("framework.onPrepare: ret=" + ok + " visibleItems=" + vis);
        }
    }

    private void onHostModeDestroyed(final EditText owner, ActionMode mode) {
        long ageMs = SystemClock.uptimeMillis() - modeCreatedAtMs;
        cancelHealthCheck(owner);
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("framework.onDestroy: ageMs=" + ageMs
                    + " byHost=" + finishingByHost
                    + " clicked=" + modeItemClicked);
        }
        boolean wasActive = (activeMode == mode);
        final ActionMode.Callback savedCb = activeOrigCallback;
        final int savedType = activeType;
        final boolean savedHasType = activeHasTypeArg;
        if (wasActive) {
            activeMode = null;
            activeOwner = null;
            activeOrigCallback = null;
            detachPositionObserver();
        }
        if (!finishingByHost && !modeItemClicked && ageMs < 200L && !destroyed
                && getDiagMode() == DIAG_MODE_AUTO
                && owner != null && isViewAttached(owner) && owner.hasFocus()
                && savedCb != null) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("framework destroyed prematurely (" + ageMs + "ms) -> fallback to popup-clone");
            }
            final int s = Math.max(0, owner.getSelectionStart());
            final int e = Math.max(0, owner.getSelectionEnd());
            owner.post(new Runnable() {
                @Override
                public void run() {
                    if (!destroyed && isViewAttached(owner) && owner.hasFocus() && activeMode == null) {
                        try {
                            if (owner.getText() instanceof Spannable) {
                                int len = owner.getText().length();
                                Selection.setSelection(owner.getText(), Math.min(s, len), Math.min(e, len));
                            }
                        } catch (Throwable ignored) { }
                        startPopupCloneMode(owner, savedCb, savedType, savedHasType,
                                "premature-destroy-" + ageMs + "ms");
                    }
                }
            });
        }
        finishingByHost = false;
    }

    void invalidateContentRectIfOwner(EditText owner) {
        if (activeMode == null || activeOwner != owner || suppressedForDrag) return;
        if (activeMode instanceof HostedPopupActionMode) {
            ((HostedPopupActionMode) activeMode).updatePopupPosition();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Api23Helper.invalidateContentRect(activeMode);
        }
    }

    void invalidateMenuIfOwner(EditText owner) {
        if (activeMode != null && activeOwner == owner) {
            try {
                activeMode.invalidate();
            } catch (Throwable ignored) { }
        }
    }

    void repositionActiveMode() {
        if (activeMode == null || activeOwner == null || suppressedForDrag) return;
        if (activeMode instanceof HostedPopupActionMode) {
            ((HostedPopupActionMode) activeMode).updatePopupPosition();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Api23Helper.invalidateContentRect(activeMode);
        }
    }

    void onWindowDragStarted() {
        if (suppressedForDrag) return;
        suppressedForDrag = true;
        if (activeMode == null) return;
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("toolbar hidden during window drag/resize");
        }
        if (activeMode instanceof HostedPopupActionMode) {
            ((HostedPopupActionMode) activeMode).hideForDrag();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                activeMode.hide(30000L);
            } catch (Throwable ignored) { }
        }
    }

    void onWindowDragEnded() {
        if (!suppressedForDrag) return;
        suppressedForDrag = false;
        if (activeMode == null || activeOwner == null) return;
        final EditText owner = activeOwner;
        owner.post(new Runnable() {
            @Override
            public void run() {
                if (destroyed || suppressedForDrag || activeMode == null || activeOwner != owner) {
                    return;
                }
                if (!isViewAttached(owner) || !owner.hasFocus()) {
                    finishActiveMode("drag-end-unfocused", false);
                    return;
                }
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("toolbar re-shown after window drag/resize");
                }
                if (activeMode instanceof HostedPopupActionMode) {
                    ((HostedPopupActionMode) activeMode).showAfterDrag();
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        activeMode.hide(0L);
                        Api23Helper.invalidateContentRect(activeMode);
                    } catch (Throwable ignored) { }
                }
            }
        });
    }

    void onOwnerTextChanged(EditText owner) {
        if (activeOwner == owner && activeMode != null) {
            finishActiveMode("text-changed", true);
        }
    }

    void onOwnerUserScrolled(EditText owner) {
        if (activeOwner == owner && activeMode != null) {
            finishActiveMode("field-scrolled", true);
        }
    }

    void onOwnerWindowFocusLost(EditText owner) {
        if (activeOwner == owner && activeMode != null) {
            finishActiveMode("window-focus-lost", true);
        }
    }

    void onEditTextFocusLost(EditText owner) {
        if (activeOwner == owner) {
            finishActiveMode("focus-lost", true);
        }
    }

    void onEditTextDetached(EditText owner) {
        if (activeOwner == owner) {
            finishActiveMode("edittext-detached", false);
        }
        if (observedEditText == owner) {
            detachPositionObserver();
        }
    }

    void finishActiveMode() {
        finishActiveMode("caller", true);
    }

    void finishActiveMode(String reason) {
        finishActiveMode(reason, true);
    }

    void finishActiveMode(String reason, boolean animateOut) {
        cancelHealthCheck(activeOwner);
        detachPositionObserver();
        suppressedForDrag = false;
        if (activeMode != null) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("finishActiveMode: reason=" + reason);
            }
            ActionMode modeToFinish = activeMode;
            activeMode = null;
            activeOwner = null;
            activeOrigCallback = null;
            finishingByHost = true;
            try {
                if (modeToFinish instanceof HostedPopupActionMode) {
                    ((HostedPopupActionMode) modeToFinish).finish(animateOut);
                } else {
                    modeToFinish.finish();
                }
            } catch (Throwable ignored) { }
            finishingByHost = false;
        } else {
            activeOwner = null;
            activeOrigCallback = null;
        }
    }

    void onVisibilityChanged(boolean visible) {
        if (destroyed) return;
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("onVisibilityChanged: visible=" + visible);
        }
        if (visible) {
            prepare();
        } else {
            finishActiveMode("host-invisible", false);
            dismissDialogOnly();
        }
    }

    void destroy() {
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("destroy() called");
        }
        destroyed = true;
        finishActiveMode("destroy", false);
        dismissDialogOnly();
    }

    private void computeFrameworkContentRect(EditText owner,
                                             ActionMode.Callback origCallback,
                                             ActionMode mode,
                                             Rect outRect) {
        if (outRect == null) return;
        frameworkContentRectCalls++;
        outRect.setEmpty();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Api23Helper.fillOriginalContentRect(origCallback, mode, owner, outRect);
        }
        int bL = outRect.left, bT = outRect.top, bR = outRect.right, bB = outRect.bottom;
        if (outRect.width() <= 0 || outRect.height() <= 0) {
            outRect.set(0, 0, Math.max(1, owner.getWidth()), Math.max(1, owner.getHeight()));
        }
        if (dummyView != null) {
            owner.getLocationOnScreen(etScreenLoc);
            owner.getLocationInWindow(etWinLoc);
            dummyView.getLocationOnScreen(dummyLoc);
            int winOffX = etScreenLoc[0] - etWinLoc[0];
            int winOffY = etScreenLoc[1] - etWinLoc[1];
            outRect.offset(etScreenLoc[0] - dummyLoc[0], etScreenLoc[1] - dummyLoc[1]);
            if (BuildConfig.SEC_DIAG) {
                Rect vis = computeVisibleDisplayFrame(ctx, owner, safeMetrics(ctx));
                boolean offScreen = outRect.right <= 0 || outRect.bottom <= 0
                        || outRect.left >= dummyView.getWidth()
                        || outRect.top >= dummyView.getHeight();
                SecurityNative.diagUi("framework.onGetContentRect: contentRect=[" + bL + "," + bT + "," + bR + "," + bB
                        + "] winOffset=[" + winOffX + "," + winOffY
                        + "] dummyLoc=[" + dummyLoc[0] + "," + dummyLoc[1]
                        + "] mapped=[" + outRect.left + "," + outRect.top + "," + outRect.right + "," + outRect.bottom
                        + "] visFrame=[" + vis.left + "," + vis.top + "," + vis.right + "," + vis.bottom
                        + "] dummySize=" + dummyView.getWidth() + "x" + dummyView.getHeight()
                        + (offScreen ? " OFF_SCREEN" : ""));
            }
        }
    }

    private void attachPositionObserver(final EditText owner) {
        detachPositionObserver();
        if (owner == null) return;
        owner.getLocationOnScreen(etScreenLoc);
        owner.getLocationInWindow(etWinLoc);
        computeVisibleDisplayFrame(ctx, owner, safeMetrics(ctx), tmpVisRect);
        lastObsScreenX = etScreenLoc[0];
        lastObsScreenY = etScreenLoc[1];
        lastObsWinX = etWinLoc[0];
        lastObsWinY = etWinLoc[1];
        lastObsScrollX = owner.getScrollX();
        lastObsSelStart = owner.getSelectionStart();
        lastObsSelEnd = owner.getSelectionEnd();
        lastObsVisTop = tmpVisRect.top;
        lastObsVisBottom = tmpVisRect.bottom;
        observedEditText = owner;

        preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (activeMode == null || activeOwner != owner || !isViewAttached(owner) || suppressedForDrag) {
                    return true;
                }
                owner.getLocationOnScreen(etScreenLoc);
                owner.getLocationInWindow(etWinLoc);
                computeVisibleDisplayFrame(ctx, owner, safeMetrics(ctx), tmpVisRect);
                int sx = owner.getScrollX();
                int ss = owner.getSelectionStart();
                int se = owner.getSelectionEnd();
                if (etScreenLoc[0] != lastObsScreenX
                        || etScreenLoc[1] != lastObsScreenY
                        || etWinLoc[0] != lastObsWinX
                        || etWinLoc[1] != lastObsWinY
                        || sx != lastObsScrollX
                        || ss != lastObsSelStart
                        || se != lastObsSelEnd
                        || tmpVisRect.top != lastObsVisTop
                        || tmpVisRect.bottom != lastObsVisBottom) {
                    lastObsScreenX = etScreenLoc[0];
                    lastObsScreenY = etScreenLoc[1];
                    lastObsWinX = etWinLoc[0];
                    lastObsWinY = etWinLoc[1];
                    lastObsScrollX = sx;
                    lastObsSelStart = ss;
                    lastObsSelEnd = se;
                    lastObsVisTop = tmpVisRect.top;
                    lastObsVisBottom = tmpVisRect.bottom;
                    repositionActiveMode();
                }
                return true;
            }
        };
        try {
            owner.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        } catch (Throwable ignored) { }
    }

    private void detachPositionObserver() {
        if (observedEditText != null && preDrawListener != null) {
            try {
                ViewTreeObserver vto = observedEditText.getViewTreeObserver();
                if (vto.isAlive()) {
                    vto.removeOnPreDrawListener(preDrawListener);
                }
            } catch (Throwable ignored) { }
        }
        observedEditText = null;
        preDrawListener = null;
    }

    private boolean canShowHostWindow() {
        if (overlayRequired || !(ctx instanceof Activity)) {
            return true;
        }
        Activity act = (Activity) ctx;
        if (act.isFinishing()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && act.isDestroyed()) {
            return false;
        }
        return true;
    }

    private void dismissDialogOnly() {
        hostAttached = false;
        if (hostDialog != null) {
            try {
                if (hostDialog.isShowing()) {
                    hostDialog.dismiss();
                }
            } catch (Throwable ignored) { }
            hostDialog = null;
            dummyView = null;
        }
    }

    private static void sanitizePasswordMenu(EditText owner, Menu menu) {
        if (owner == null || menu == null) return;
        if (!(owner.getTransformationMethod() instanceof PasswordTransformationMethod)) {
            return;
        }
        for (int i = menu.size() - 1; i >= 0; i--) {
            MenuItem mi = menu.getItem(i);
            if (mi == null) continue;
            int id = mi.getItemId();
            boolean allowed = (id == android.R.id.paste
                    || id == android.R.id.pasteAsPlainText
                    || id == android.R.id.selectAll);
            if (!allowed) {
                mi.setVisible(false);
                menu.removeItem(id);
            }
        }
    }

    private static int countVisibleEnabledMenuItems(Menu menu) {
        if (menu == null) return 0;
        int count = 0;
        for (int i = 0; i < menu.size(); i++) {
            MenuItem mi = menu.getItem(i);
            if (mi != null && mi.isVisible() && mi.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasClipboardText(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return false;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return false;
            CharSequence txt = clip.getItemAt(0).coerceToText(ctx);
            return txt != null && txt.length() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isViewAttached(View v) {
        if (v == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return v.isAttachedToWindow();
        }
        return v.getWindowToken() != null;
    }

    private static int dp(int v, float density) {
        return Math.round(v * density);
    }

    private static DisplayMetrics safeMetrics(Context ctx) {
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

    private static Context createDeviceFloatingThemeContext(Context baseCtx) {
        int nightMask = baseCtx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = (nightMask == Configuration.UI_MODE_NIGHT_YES);
        int themeRes = isNight
                ? android.R.style.Theme_DeviceDefault
                : android.R.style.Theme_DeviceDefault_Light;
        return new ContextThemeWrapper(baseCtx, themeRes);
    }

    private static Rect computeVisibleDisplayFrame(Context ctx, View anchor, DisplayMetrics dm) {
        Rect out = new Rect();
        computeVisibleDisplayFrame(ctx, anchor, dm, out);
        return out;
    }

    private static void computeVisibleDisplayFrame(Context ctx, View anchor, DisplayMetrics dm, Rect out) {
        int boundsW = Math.max(1, dm.widthPixels);
        int boundsH = Math.max(1, dm.heightPixels);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    Rect b = wm.getCurrentWindowMetrics().getBounds();
                    if (b != null && b.width() > 0 && b.height() > 0) {
                        boundsW = b.width();
                        boundsH = b.height();
                    }
                }
            } catch (Throwable ignored) { }
        }

        out.set(0, 0, boundsW, boundsH);
        if (anchor != null) {
            Rect winVis = new Rect();
            try {
                anchor.getWindowVisibleDisplayFrame(winVis);
                if (winVis.width() > 0 && winVis.height() > 0) {
                    out.left = Math.max(0, winVis.left);
                    out.top = Math.max(0, winVis.top);
                    out.right = Math.min(boundsW, winVis.right);
                    out.bottom = Math.min(boundsH, winVis.bottom);
                }
            } catch (Throwable ignored) { }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    WindowInsets rwi = anchor.getRootWindowInsets();
                    if (rwi != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Insets ins = rwi.getInsets(WindowInsets.Type.systemBars()
                                    | WindowInsets.Type.displayCutout()
                                    | WindowInsets.Type.ime());
                            out.left = Math.max(out.left, ins.left);
                            out.top = Math.max(out.top, ins.top);
                            out.right = Math.min(out.right, boundsW - ins.right);
                            out.bottom = Math.min(out.bottom, boundsH - ins.bottom);
                        } else {
                            out.left = Math.max(out.left, rwi.getSystemWindowInsetLeft());
                            out.top = Math.max(out.top, rwi.getSystemWindowInsetTop());
                            out.right = Math.min(out.right, boundsW - rwi.getSystemWindowInsetRight());
                            out.bottom = Math.min(out.bottom, boundsH - rwi.getSystemWindowInsetBottom());
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                DisplayCutout cutout = rwi.getDisplayCutout();
                                if (cutout != null) {
                                    out.left = Math.max(out.left, cutout.getSafeInsetLeft());
                                    out.top = Math.max(out.top, cutout.getSafeInsetTop());
                                    out.right = Math.min(out.right, boundsW - cutout.getSafeInsetRight());
                                    out.bottom = Math.min(out.bottom, boundsH - cutout.getSafeInsetBottom());
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) { }
            }
        }
        if (out.width() <= 0 || out.height() <= 0) {
            out.set(0, 0, boundsW, boundsH);
        }
    }

    // Isolated API 23+ class so Dalvik/ART on API 21-22 never tries to resolve ActionMode.Callback2.
    private static final class Api23Helper {
        private Api23Helper() { }

        static ActionMode start(final TextActionModeHost host,
                                View dummyView,
                                final EditText owner,
                                final ActionMode.Callback origCallback,
                                int type) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
            ActionMode.Callback2 wrapper = new ActionMode.Callback2() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    boolean ok = origCallback.onCreateActionMode(mode, menu);
                    host.onHostModeCreated(owner, mode, menu, ok);
                    return ok;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    boolean ok = origCallback.onPrepareActionMode(mode, menu);
                    host.onHostModePrepared(owner, menu, ok);
                    return ok;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    host.modeItemClicked = true;
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("framework.onClick: itemId="
                                + (item != null ? item.getItemId() : -1));
                    }
                    return origCallback.onActionItemClicked(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    host.onHostModeDestroyed(owner, mode);
                    origCallback.onDestroyActionMode(mode);
                }

                @Override
                public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                    host.computeFrameworkContentRect(owner, origCallback, mode, outRect);
                }
            };
            return dummyView.startActionMode(wrapper, type);
        }

        static void fillOriginalContentRect(ActionMode.Callback origCallback,
                                            ActionMode mode,
                                            EditText owner,
                                            Rect outRect) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
            if (origCallback instanceof ActionMode.Callback2) {
                ((ActionMode.Callback2) origCallback).onGetContentRect(mode, owner, outRect);
            }
        }

        static void invalidateContentRect(ActionMode mode) {
            if (mode instanceof HostedPopupActionMode) {
                ((HostedPopupActionMode) mode).updatePopupPosition();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mode != null) {
                try {
                    mode.invalidateContentRect();
                } catch (Throwable ignored) { }
            }
        }
    }

    // =========================================================================
    // System-faithful ActionMode backed by a themed PopupWindow anchored on owner
    // =========================================================================
    private static final class HostedPopupActionMode extends ActionMode {
        private final TextActionModeHost host;
        private final Context ctx;
        private final Context themedCtx;
        private final EditText owner;
        private final ActionMode.Callback origCallback;
        private final Menu menu;
        private final Rect tmpCallbackRect = new Rect();
        private final Rect tmpVisFrame = new Rect();
        private final int[] ownerScreen = new int[2];
        private final int[] ownerWin = new int[2];

        private final int themeBgColor;
        private final int themeTextColor;
        private final int themeHighlightColor;

        private PopupWindow popup;
        private FrameLayout rootContainer;
        private LinearLayout panelCard;
        private boolean overflowOpen = false;
        private final ArrayList<MenuItem> lastItems = new ArrayList<MenuItem>();

        private CharSequence title;
        private CharSequence subtitle;
        private View customView;
        private boolean finished = false;
        private boolean hiddenForDrag = false;
        private int lastCardW = 0;
        private int lastCardH = 0;
        private int shadowPadSides = 0;
        private int shadowPadTop = 0;
        private int shadowPadBottom = 0;

        private int lastLoggedWinX = Integer.MIN_VALUE;
        private int lastLoggedWinY = Integer.MIN_VALUE;
        private String lastLoggedFlip = "";

        HostedPopupActionMode(TextActionModeHost host,
                              Context ctx,
                              EditText owner,
                              ActionMode.Callback origCallback,
                              int type,
                              boolean hasTypeArg) {
            this.host = host;
            this.ctx = ctx;
            this.themedCtx = createDeviceFloatingThemeContext(ctx);
            this.owner = owner;
            this.origCallback = origCallback;
            this.menu = new PopupMenu(this.themedCtx, owner).getMenu();

            this.themeBgColor = ModxTheme.CARD;
            this.themeTextColor = ModxTheme.FG;
            this.themeHighlightColor = ModxTheme.accentSoft(ModxTheme.ACCENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasTypeArg) {
                try {
                    setType(type);
                } catch (Throwable ignored) { }
            }
        }

        private static int resolveThemeColor(Context c, int attr, int fallback) {
            TypedArray ta = null;
            try {
                ta = c.obtainStyledAttributes(new int[]{ attr });
                ColorStateList csl = ta.getColorStateList(0);
                if (csl != null) return csl.getDefaultColor();
                return ta.getColor(0, fallback);
            } catch (Throwable ignored) {
                return fallback;
            } finally {
                if (ta != null) ta.recycle();
            }
        }

        boolean start() {
            boolean created = origCallback.onCreateActionMode(this, menu);
            if (!created) {
                origCallback.onDestroyActionMode(this);
                finished = true;
                return false;
            }
            origCallback.onPrepareActionMode(this, menu);
            sanitizePasswordMenu(owner, menu);
            ArrayList<MenuItem> items = collectOrderedItems();
            if (items.isEmpty()) {
                origCallback.onDestroyActionMode(this);
                finished = true;
                return false;
            }
            overflowOpen = false;
            buildAndShowPopup(items, true);
            return true;
        }

        @Override
        public void invalidate() {
            if (finished) return;
            try {
                origCallback.onPrepareActionMode(this, menu);
            } catch (Throwable ignored) { }
            sanitizePasswordMenu(owner, menu);
            if (hiddenForDrag) return;
            ArrayList<MenuItem> items = collectOrderedItems();
            if (items.isEmpty()) {
                finish(true);
                return;
            }
            overflowOpen = false;
            boolean animate = (popup == null || !popup.isShowing());
            buildAndShowPopup(items, animate);
        }

        @Override
        public void finish() {
            finish(true);
        }

        void finish(boolean animateOut) {
            if (finished) return;
            finished = true;
            if (animateOut) {
                dismissPopupAnimated();
            } else {
                dismissPopupImmediate();
            }
            if (host.activeMode == this) {
                host.activeMode = null;
                host.activeOwner = null;
                host.activeOrigCallback = null;
                host.detachPositionObserver();
            }
            try {
                origCallback.onDestroyActionMode(this);
            } catch (Throwable ignored) { }
        }

        void hideForDrag() {
            hiddenForDrag = true;
            dismissPopupImmediate();
        }

        void showAfterDrag() {
            hiddenForDrag = false;
            if (finished || !isViewAttached(owner) || !owner.hasFocus()) return;
            ArrayList<MenuItem> items = collectOrderedItems();
            if (items.isEmpty()) {
                finish(false);
                return;
            }
            overflowOpen = false;
            buildAndShowPopup(items, true);
        }

        @Override public Menu getMenu() { return menu; }
        @Override public CharSequence getTitle() { return title; }
        @Override public void setTitle(CharSequence title) { this.title = title; }
        @Override public void setTitle(int resId) { this.title = ctx.getString(resId); }
        @Override public CharSequence getSubtitle() { return subtitle; }
        @Override public void setSubtitle(CharSequence subtitle) { this.subtitle = subtitle; }
        @Override public void setSubtitle(int resId) { this.subtitle = ctx.getString(resId); }
        @Override public View getCustomView() { return customView; }
        @Override public void setCustomView(View view) { this.customView = view; }
        @Override public MenuInflater getMenuInflater() { return new MenuInflater(themedCtx); }

        private ArrayList<MenuItem> collectOrderedItems() {
            boolean masked = owner.getTransformationMethod() instanceof PasswordTransformationMethod;
            int selA = Math.max(0, owner.getSelectionStart());
            int selB = Math.max(0, owner.getSelectionEnd());
            boolean hasSelection = Math.max(selA, selB) > Math.min(selA, selB);
            boolean hasClip = hasClipboardText(ctx);

            ArrayList<MenuItem> valid = new ArrayList<MenuItem>();
            int count = menu != null ? menu.size() : 0;
            for (int i = 0; i < count; i++) {
                MenuItem mi = menu.getItem(i);
                if (mi == null || !mi.isVisible() || !mi.isEnabled()) continue;
                int id = mi.getItemId();
                if (masked && id != android.R.id.paste
                        && id != android.R.id.pasteAsPlainText
                        && id != android.R.id.selectAll) {
                    continue;
                }
                if (!hasSelection) {
                    if (!hasClip || id != android.R.id.paste) {
                        continue;
                    }
                }
                String label = resolveRealLabel(mi);
                if (label == null) continue;
                valid.add(mi);
            }

            ArrayList<MenuItem> ordered = new ArrayList<MenuItem>(valid.size());
            int[] priorityIds = new int[]{
                    android.R.id.cut,
                    android.R.id.copy,
                    android.R.id.paste,
                    android.R.id.selectAll
            };
            for (int pid : priorityIds) {
                for (int i = 0; i < valid.size(); i++) {
                    MenuItem mi = valid.get(i);
                    if (mi.getItemId() == pid && !ordered.contains(mi)) {
                        ordered.add(mi);
                    }
                }
            }
            for (int i = 0; i < valid.size(); i++) {
                MenuItem mi = valid.get(i);
                if (!ordered.contains(mi)) {
                    ordered.add(mi);
                }
            }
            return ordered;
        }

        private String resolveRealLabel(MenuItem mi) {
            CharSequence cs = mi.getTitle();
            if (cs != null) {
                String trimmed = cs.toString().trim();
                if (!trimmed.isEmpty()) return trimmed;
            }
            int id = mi.getItemId();
            if (id == android.R.id.cut) return ctx.getString(android.R.string.cut);
            if (id == android.R.id.copy) return ctx.getString(android.R.string.copy);
            if (id == android.R.id.paste) return ctx.getString(android.R.string.paste);
            if (id == android.R.id.selectAll) return ctx.getString(android.R.string.selectAll);
            return null;
        }

        private TextView makeMenuItemView(final MenuItem item, String label, float density, boolean verticalRow) {
            TextView tv = new TextView(themedCtx);
            tv.setText(label);
            tv.setTextColor(themeTextColor);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tv.setGravity(verticalRow ? (Gravity.CENTER_VERTICAL | Gravity.START) : Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setIncludeFontPadding(false);
            int hPad = dp(16, density);
            tv.setPadding(hPad, 0, hPad, 0);
            int minW = dp(verticalRow ? 140 : 48, density);
            int minH = dp(48, density);
            tv.setMinWidth(minW);
            tv.setMinimumWidth(minW);
            tv.setMinHeight(minH);
            tv.setMinimumHeight(minH);
            tv.setClickable(true);
            tv.setFocusable(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tv.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(themeHighlightColor), null, new ColorDrawable(Color.WHITE)));
            }
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("popup-clone.onClick: itemId=" + item.getItemId()
                                + " title=" + resolveRealLabel(item));
                    }
                    boolean handled = false;
                    try {
                        handled = origCallback.onActionItemClicked(HostedPopupActionMode.this, item);
                    } catch (Throwable ignored) { }
                    if (!handled) {
                        try {
                            owner.onTextContextMenuItem(item.getItemId());
                        } catch (Throwable ignored) { }
                    }
                    if (!finished) {
                        if (item.getItemId() == android.R.id.selectAll) {
                            owner.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!finished && isViewAttached(owner)) {
                                        invalidate();
                                    }
                                }
                            });
                        } else {
                            finish(true);
                        }
                    }
                }
            });
            return tv;
        }

        private View makeOverflowIconButton(final ArrayList<MenuItem> allItems,
                                            final float density,
                                            final boolean isBackButton) {
            FrameLayout btn = new FrameLayout(themedCtx);
            int size = dp(48, density);
            btn.setMinimumWidth(size);
            btn.setMinimumHeight(size);
            btn.setClickable(true);
            btn.setFocusable(false);
            btn.setContentDescription(isBackButton ? "Back" : "More options");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                btn.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(themeHighlightColor), null, new ColorDrawable(Color.WHITE)));
            }
            ImageView icon = new ImageView(themedCtx);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            int iconPx = dp(20, density);
            icon.setImageDrawable(isBackButton
                    ? ModxIcons.arrowLeft(themeTextColor, iconPx)
                    : ModxIcons.moreVertical(themeTextColor, iconPx));
            btn.addView(icon, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    overflowOpen = !isBackButton;
                    buildAndShowPopup(allItems, false);
                }
            });
            return btn;
        }

        private void buildAndShowPopup(ArrayList<MenuItem> items, boolean animateIn) {
            if (finished || hiddenForDrag || !isViewAttached(owner) || owner.getWindowToken() == null) {
                dismissPopupImmediate();
                return;
            }
            lastItems.clear();
            lastItems.addAll(items);

            DisplayMetrics dm = safeMetrics(ctx);
            float density = dm.density > 0f ? dm.density : 2f;
            computeVisibleDisplayFrame(ctx, owner, dm, tmpVisFrame);

            shadowPadSides = dp(10, density);
            shadowPadTop = dp(6, density);
            shadowPadBottom = dp(14, density);

            int maxCardW = Math.min(
                    Math.max(dp(160, density), tmpVisFrame.width() - dp(16, density)),
                    dp(360, density));
            int rowH = dp(48, density);

            ArrayList<TextView> rowButtons = new ArrayList<TextView>(items.size());
            int[] itemWidths = new int[items.size()];
            int totalItemsW = 0;
            int hSpec = View.MeasureSpec.makeMeasureSpec(rowH, View.MeasureSpec.EXACTLY);
            int wSpec = View.MeasureSpec.makeMeasureSpec(maxCardW, View.MeasureSpec.AT_MOST);
            for (int i = 0; i < items.size(); i++) {
                MenuItem mi = items.get(i);
                TextView tv = makeMenuItemView(mi, resolveRealLabel(mi), density, false);
                tv.measure(wSpec, hSpec);
                int w = Math.max(dp(48, density), tv.getMeasuredWidth());
                rowButtons.add(tv);
                itemWidths[i] = w;
                totalItemsW += w;
            }

            ArrayList<MenuItem> primaryItems = new ArrayList<MenuItem>();
            ArrayList<TextView> primaryButtons = new ArrayList<TextView>();
            ArrayList<MenuItem> overflowItems = new ArrayList<MenuItem>();

            if (totalItemsW <= maxCardW) {
                primaryItems.addAll(items);
                primaryButtons.addAll(rowButtons);
            } else {
                int overflowBtnW = dp(48, density);
                int avail = Math.max(dp(64, density), maxCardW - overflowBtnW);
                int used = 0;
                for (int i = 0; i < items.size(); i++) {
                    if (used + itemWidths[i] <= avail || primaryItems.isEmpty()) {
                        primaryItems.add(items.get(i));
                        primaryButtons.add(rowButtons.get(i));
                        used += itemWidths[i];
                    } else {
                        overflowItems.add(items.get(i));
                    }
                }
            }

            if (overflowItems.isEmpty()) {
                overflowOpen = false;
            }

            LinearLayout card = new LinearLayout(themedCtx);
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(themeBgColor);
            cardBg.setCornerRadius(ModxTheme.RADIUS_DP * density);
            cardBg.setStroke(Math.max(1, dp(1, density)), ModxTheme.LINE);
            card.setBackground(cardBg);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                card.setElevation(dp(4, density));
                card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                card.setClipToOutline(true);
            }

            if (!overflowOpen) {
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                for (int i = 0; i < primaryButtons.size(); i++) {
                    card.addView(primaryButtons.get(i), new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, rowH));
                }
                if (!overflowItems.isEmpty()) {
                    card.addView(makeOverflowIconButton(items, density, false),
                            new LinearLayout.LayoutParams(dp(48, density), rowH));
                }
            } else {
                card.setOrientation(LinearLayout.VERTICAL);
                card.addView(makeOverflowIconButton(items, density, true),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH));
                for (int i = 0; i < overflowItems.size(); i++) {
                    MenuItem mi = overflowItems.get(i);
                    TextView tv = makeMenuItemView(mi, resolveRealLabel(mi), density, true);
                    card.addView(tv, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, rowH));
                }
            }

            card.measure(
                    View.MeasureSpec.makeMeasureSpec(maxCardW, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(tmpVisFrame.height(), View.MeasureSpec.AT_MOST));
            lastCardW = Math.max(dp(48, density), card.getMeasuredWidth());
            lastCardH = Math.max(rowH, card.getMeasuredHeight());

            FrameLayout container = new FrameLayout(themedCtx);
            container.setBackgroundColor(Color.TRANSPARENT);
            container.setPadding(shadowPadSides, shadowPadTop, shadowPadSides, shadowPadBottom);
            container.setClipToPadding(false);
            container.setClipChildren(false);
            container.addView(card, new FrameLayout.LayoutParams(lastCardW, lastCardH));

            int pwTotalW = lastCardW + shadowPadSides * 2;
            int pwTotalH = lastCardH + shadowPadTop + shadowPadBottom;

            int[] winPos = computePopupWindowPos(dm, density, lastCardW, lastCardH, items.size(), true);

            if (popup != null && popup.isShowing()) {
                rootContainer = container;
                panelCard = card;
                popup.setContentView(container);
                try {
                    popup.update(safeUpdateCoord(winPos[0]), safeUpdateCoord(winPos[1]), pwTotalW, pwTotalH);
                    return;
                } catch (Throwable ignored) {
                    dismissPopupImmediate();
                }
            }

            dismissPopupImmediate();
            rootContainer = container;
            panelCard = card;

            PopupWindow pw = new PopupWindow(container, pwTotalW, pwTotalH, false);
            pw.setTouchable(true);
            pw.setOutsideTouchable(false);
            pw.setClippingEnabled(false);
            pw.setAnimationStyle(0);
            pw.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                pw.setElevation(0f);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                pw.setAttachedInDecor(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pw.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
            }

            try {
                pw.showAtLocation(owner, Gravity.TOP | Gravity.LEFT, winPos[0], winPos[1]);
                this.popup = pw;
            } catch (Throwable t) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        pw.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL);
                        pw.showAtLocation(owner, Gravity.TOP | Gravity.LEFT, winPos[0], winPos[1]);
                        this.popup = pw;
                    } catch (Throwable t2) {
                        if (BuildConfig.SEC_DIAG) {
                            SecurityNative.diagUi("popup-clone.showAtLocation failed: "
                                    + SecurityNative.diagException(t2));
                        }
                    }
                } else if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("popup-clone.showAtLocation failed: "
                            + SecurityNative.diagException(t));
                }
            }

            if (this.popup != null && animateIn && panelCard != null) {
                panelCard.setAlpha(0f);
                panelCard.setScaleX(0.92f);
                panelCard.setScaleY(0.92f);
                panelCard.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        }

        void updatePopupPosition() {
            if (finished || hiddenForDrag || !isViewAttached(owner)) return;
            if (popup == null || !popup.isShowing() || lastCardW <= 0 || lastCardH <= 0) {
                return;
            }
            DisplayMetrics dm = safeMetrics(ctx);
            float density = dm.density > 0f ? dm.density : 2f;
            int[] winPos = computePopupWindowPos(dm, density, lastCardW, lastCardH, lastItems.size(), false);
            try {
                popup.update(safeUpdateCoord(winPos[0]), safeUpdateCoord(winPos[1]), -1, -1);
            } catch (Throwable ignored) { }
        }

        private static int safeUpdateCoord(int c) {
            return (c == -1) ? -2 : c;
        }

        private int[] computePopupWindowPos(DisplayMetrics dm,
                                            float density,
                                            int cardW,
                                            int cardH,
                                            int itemCount,
                                            boolean forceLog) {
            computeVisibleDisplayFrame(ctx, owner, dm, tmpVisFrame);

            tmpCallbackRect.setEmpty();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Api23Helper.fillOriginalContentRect(origCallback, this, owner, tmpCallbackRect);
            }
            owner.getLocationOnScreen(ownerScreen);
            owner.getLocationInWindow(ownerWin);
            int winOffX = ownerScreen[0] - ownerWin[0];
            int winOffY = ownerScreen[1] - ownerWin[1];

            int sA = Math.max(0, owner.getSelectionStart());
            int sB = Math.max(0, owner.getSelectionEnd());
            int sMin = Math.min(sA, sB);
            int sMax = Math.max(sA, sB);

            int localLeft = 0;
            int localRight = Math.max(1, owner.getWidth());
            int localTextTop = Math.max(0, owner.getTotalPaddingTop());
            int localTextBottom = Math.max(localTextTop + 1, owner.getHeight() - owner.getTotalPaddingBottom());
            int selCenterX = ownerScreen[0] + owner.getWidth() / 2;

            Layout layout = owner.getLayout();
            if (layout != null) {
                try {
                    int line = layout.getLineForOffset(sMin);
                    int lineEnd = layout.getLineForOffset(sMax);
                    int contentTopOff = owner.getTotalPaddingTop() - owner.getScrollY();
                    int contentLeftOff = owner.getTotalPaddingLeft() - owner.getScrollX();
                    localTextTop = contentTopOff + layout.getLineTop(line);
                    localTextBottom = contentTopOff + layout.getLineBottom(lineEnd);
                    float px1 = layout.getPrimaryHorizontal(sMin);
                    float px2 = (sMax != sMin) ? layout.getPrimaryHorizontal(sMax) : px1;
                    int x1 = Math.round(contentLeftOff + Math.min(px1, px2));
                    int x2 = Math.round(contentLeftOff + Math.max(px1, px2));
                    localLeft = x1;
                    localRight = Math.max(x1 + 1, x2);
                    int centerLocalX = Math.max(0, Math.min(owner.getWidth(), (x1 + x2) / 2));
                    selCenterX = ownerScreen[0] + centerLocalX;
                } catch (Throwable ignored) { }
            } else if (tmpCallbackRect.width() > 0 && tmpCallbackRect.height() > 0) {
                localLeft = tmpCallbackRect.left;
                localRight = tmpCallbackRect.right;
                localTextTop = tmpCallbackRect.top;
                localTextBottom = tmpCallbackRect.bottom;
                int centerLocalX = Math.max(0, Math.min(owner.getWidth(), tmpCallbackRect.centerX()));
                selCenterX = ownerScreen[0] + centerLocalX;
            }

            int selScreenTop = ownerScreen[1] + localTextTop;
            int selScreenBottom = ownerScreen[1] + localTextBottom;
            int cbScreenBottom = (tmpCallbackRect.height() > 0)
                    ? (ownerScreen[1] + tmpCallbackRect.bottom)
                    : selScreenBottom;

            int margin = dp(8, density);
            int minScreenX = tmpVisFrame.left + margin;
            int maxScreenX = Math.max(minScreenX, tmpVisFrame.right - cardW - margin);
            int cardScreenX = Math.max(minScreenX, Math.min(selCenterX - cardW / 2, maxScreenX));

            int gap = dp(8, density);
            int handleClearance = dp(24, density);
            int yAbove = selScreenTop - cardH - gap;
            int yBelow = Math.max(selScreenBottom + handleClearance + gap, cbScreenBottom + gap);

            int minTop = tmpVisFrame.top + dp(4, density);
            int maxBottomTop = Math.max(minTop, tmpVisFrame.bottom - cardH - dp(4, density));

            int cardScreenY;
            String flipDecision;
            if (yAbove >= minTop) {
                cardScreenY = Math.min(yAbove, maxBottomTop);
                flipDecision = "ABOVE";
            } else if (yBelow <= maxBottomTop) {
                cardScreenY = yBelow;
                flipDecision = "BELOW(no-room-above)";
            } else {
                int spaceAbove = selScreenTop - tmpVisFrame.top;
                int spaceBelow = tmpVisFrame.bottom - selScreenBottom;
                if (spaceBelow > spaceAbove) {
                    cardScreenY = maxBottomTop;
                    flipDecision = "CLAMPED_BELOW";
                } else {
                    cardScreenY = minTop;
                    flipDecision = "CLAMPED_ABOVE";
                }
            }

            int pwScreenX = cardScreenX - shadowPadSides;
            int pwScreenY = cardScreenY - shadowPadTop;
            int winX = pwScreenX - winOffX;
            int winY = pwScreenY - winOffY;

            if (BuildConfig.SEC_DIAG) {
                boolean changed = forceLog
                        || Math.abs(winX - lastLoggedWinX) > 2
                        || Math.abs(winY - lastLoggedWinY) > 2
                        || !flipDecision.equals(lastLoggedFlip);
                if (changed) {
                    lastLoggedWinX = winX;
                    lastLoggedWinY = winY;
                    lastLoggedFlip = flipDecision;
                    SecurityNative.diagUi("popup-clone.layout: contentRect=[" + localLeft + "," + localTextTop
                            + "," + localRight + "," + localTextBottom
                            + "] cbRect=[" + tmpCallbackRect.left + "," + tmpCallbackRect.top
                            + "," + tmpCallbackRect.right + "," + tmpCallbackRect.bottom
                            + "] winOffset=[" + winOffX + "," + winOffY
                            + "] visFrame=[" + tmpVisFrame.left + "," + tmpVisFrame.top
                            + "," + tmpVisFrame.right + "," + tmpVisFrame.bottom
                            + "] flip=" + flipDecision
                            + " yAbove=" + yAbove + " yBelow=" + yBelow
                            + " finalCardScreen=[" + cardScreenX + "," + cardScreenY
                            + "] finalWinLocal=[" + winX + "," + winY
                            + "] cardSize=" + cardW + "x" + cardH
                            + " items=" + itemCount + " overflow=" + overflowOpen);
                }
            }

            return new int[]{ winX, winY };
        }

        private void dismissPopupImmediate() {
            if (panelCard != null) {
                panelCard.animate().cancel();
            }
            if (popup != null) {
                try {
                    if (popup.isShowing()) {
                        popup.dismiss();
                    }
                } catch (Throwable ignored) { }
                popup = null;
            }
            rootContainer = null;
            panelCard = null;
        }

        private void dismissPopupAnimated() {
            final PopupWindow closingPw = popup;
            final View animatingCard = panelCard;
            popup = null;
            rootContainer = null;
            panelCard = null;
            if (closingPw == null) return;

            if (animatingCard == null || host.destroyed || !isViewAttached(owner)) {
                try {
                    if (closingPw.isShowing()) closingPw.dismiss();
                } catch (Throwable ignored) { }
                return;
            }

            animatingCard.animate().cancel();
            animatingCard.animate()
                    .alpha(0f)
                    .setDuration(100L)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (closingPw.isShowing()) closingPw.dismiss();
                            } catch (Throwable ignored) { }
                        }
                    })
                    .start();
        }
    }

    private static final class ToolbarGlyphDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float density;
        private final boolean isBack;

        ToolbarGlyphDrawable(int color, float density, boolean isBack) {
            this.density = density;
            this.isBack = isBack;
            paint.setColor(color);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            if (!isBack) {
                paint.setStyle(Paint.Style.FILL);
                float r = 1.75f * density;
                float step = 5.5f * density;
                canvas.drawCircle(cx, cy - step, r, paint);
                canvas.drawCircle(cx, cy, r, paint);
                canvas.drawCircle(cx, cy + step, r, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.8f * density);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                float s = 6f * density;
                path.reset();
                path.moveTo(cx + s, cy);
                path.lineTo(cx - s, cy);
                path.moveTo(cx - s * 0.25f, cy - s * 0.75f);
                path.lineTo(cx - s, cy);
                path.lineTo(cx - s * 0.25f, cy + s * 0.75f);
                canvas.drawPath(path, paint);
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
