package com.android.support;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
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
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Hosts text-selection / insertion ActionMode for EditTexts living inside
 * WindowManager.addView() windows (both Activity and Service overlay modes).
 *
 * Strategy:
 *  - Default everywhere (AUTO): displays a system-faithful ToolbarWindow via
 *    WindowManager.addView() in the exact same window type/token family as
 *    the Menu window, with no helper Dialog overhead and absolute screen coords.
 *  - SEC_DIAG-only "FW" mode (force framework): keeps the legacy Dialog-hosted
 *    framework ActionMode path strictly for diagnostic comparison.
 */
final class TextActionModeHost {

    static final int DIAG_MODE_AUTO            = 0;
    static final int DIAG_MODE_FORCE_FRAMEWORK = 1;

    private static volatile int diagMode = DIAG_MODE_AUTO;

    private final Context ctx;
    private final boolean overlayRequired;

    // Only created in SEC_DIAG builds when DIAG_MODE_FORCE_FRAMEWORK is explicitly enabled
    private Dialog hostDialog;
    private View dummyView;
    private boolean hostAttached = false;

    private ActionMode activeMode;
    private EditText activeOwner;
    private ActionMode.Callback activeOrigCallback;
    private boolean destroyed = false;

    private boolean finishingByHost = false;
    private boolean modeItemClicked = false;
    private boolean frameworkCreatedOk = false;
    private int frameworkPreparedVisibleCount = 0;
    private int frameworkContentRectCalls = 0;
    private long modeCreatedAtMs = 0L;
    private boolean suppressedForDrag = false;

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
    private int lastObsScrollY = Integer.MIN_VALUE;
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
        diagMode = (diagMode + 1) % 2;
        return diagMode;
    }

    static String getDiagModeLabel() {
        if (!BuildConfig.SEC_DIAG) return "";
        return (diagMode == DIAG_MODE_FORCE_FRAMEWORK) ? "FW" : "AUTO";
    }

    void prepare() {
        if (destroyed || ctx == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }

        // Remove helper Dialog entirely from automatic path
        if (!BuildConfig.SEC_DIAG || getDiagMode() != DIAG_MODE_FORCE_FRAMEWORK) {
            dismissDialogOnly();
            return;
        }

        if (hostDialog != null) {
            boolean showing = false;
            try { showing = hostDialog.isShowing(); } catch (Throwable ignored) { }
            boolean tokenMissing = dummyView == null || (hostAttached && dummyView.getWindowToken() == null);
            if (showing && dummyView != null && !tokenMissing) {
                return;
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
                                + (v.getWindowToken() != null) + ")");
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
                SecurityNative.diagUi("prepare(force-framework): host dialog shown (winType="
                        + resolvedWinType + ")");
            }
        } catch (Throwable t) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("prepare(force-framework) failed: "
                        + SecurityNative.diagException(t));
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
        suppressedForDrag = false;

        if (BuildConfig.SEC_DIAG && getDiagMode() == DIAG_MODE_FORCE_FRAMEWORK) {
            if (!isHostReady()) {
                prepare();
            }
            boolean ready = isHostReady();
            boolean tokenPresent = dummyView != null && dummyView.getWindowToken() != null;
            if (!ready && !tokenPresent) {
                SecurityNative.diagUi("toolbar path failed (force-framework, host not ready)");
                return null;
            }
            FrameworkAttempt attempt = tryStartFrameworkMode(owner, origCallback, type, hasTypeArg);
            if (attempt.mode != null) {
                return attempt.mode;
            }
            SecurityNative.diagUi("toolbar path failed (force-framework, no fallback): reason="
                    + attempt.failReason);
            return null;
        }

        dismissDialogOnly();
        String reason = overlayRequired
                ? "overlay-required"
                : (Build.VERSION.SDK_INT >= 31 ? "api31+" : "activity-default");
        return startOverlayWindowMode(owner, origCallback, type, hasTypeArg, reason);
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
                    SecurityNative.diagUi("framework-host returned null (type=" + type + ")");
                }
                return new FrameworkAttempt(null, "framework-returned-null");
            }

            if (!frameworkCreatedOk || frameworkPreparedVisibleCount <= 0) {
                String why = !frameworkCreatedOk ? "framework-create-false" : "framework-0-items";
                finishingByHost = true;
                try { mode.finish(); } catch (Throwable ignored) { }
                finishingByHost = false;
                return new FrameworkAttempt(null, why);
            }

            activeMode = mode;
            activeOwner = owner;
            activeOrigCallback = origCallback;
            attachPositionObserver(owner);

            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("toolbar path chosen: framework-host (mode=" + getDiagModeLabel()
                        + ", type=" + type
                        + ", overlay=" + overlayRequired
                        + ", api=" + Build.VERSION.SDK_INT
                        + ", items=" + frameworkPreparedVisibleCount + ")");
            }
            return new FrameworkAttempt(mode, null);
        } catch (Throwable t) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("framework-host threw: " + SecurityNative.diagException(t));
            }
            return new FrameworkAttempt(null, "framework-exception-" + t.getClass().getSimpleName());
        }
    }

    private ActionMode startOverlayWindowMode(final EditText owner,
                                              final ActionMode.Callback origCallback,
                                              final int type,
                                              final boolean hasTypeArg,
                                              final String reason) {
        try {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("toolbar path: overlay-window (reason=" + reason
                        + ", mode=" + getDiagModeLabel()
                        + ", type=" + type
                        + ", overlay=" + overlayRequired
                        + ", api=" + Build.VERSION.SDK_INT + ")");
            }
            HostedPopupActionMode mode = new HostedPopupActionMode(
                    this, ctx, owner, origCallback, type, hasTypeArg);
            if (!mode.start()) {
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("overlay-window aborted: 0 usable items (reason=" + reason + ")");
                }
                return null;
            }
            activeMode = mode;
            activeOwner = owner;
            activeOrigCallback = origCallback;
            attachPositionObserver(owner);
            return mode;
        } catch (Throwable t) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("overlay-window failed (" + reason + "): "
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
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("framework.onDestroy: ageMs=" + ageMs
                    + " byHost=" + finishingByHost
                    + " clicked=" + modeItemClicked);
        }
        if (activeMode == mode) {
            activeMode = null;
            activeOwner = null;
            activeOrigCallback = null;
            detachPositionObserver();
        }
        finishingByHost = false;
    }

    void invalidateContentRectIfOwner(EditText owner) {
        if (activeMode == null || activeOwner != owner || suppressedForDrag) return;
        if (activeMode instanceof HostedPopupActionMode) {
            ((HostedPopupActionMode) activeMode).updateWindowPosition();
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
            ((HostedPopupActionMode) activeMode).updateWindowPosition();
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

    private int lastHostVisible = -1;

    void onVisibilityChanged(boolean visible) {
        if (destroyed) return;
        int next = visible ? 1 : 0;
        if (lastHostVisible == next) return;
        lastHostVisible = next;
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
        lastHostVisible = -1;
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
                SecurityNative.diagUi("framework.onGetContentRect: contentRect=[" + bL + "," + bT + "," + bR + "," + bB
                        + "] winOffset=[" + winOffX + "," + winOffY
                        + "] mapped=[" + outRect.left + "," + outRect.top + "," + outRect.right + "," + outRect.bottom
                        + "] visFrame=[" + vis.left + "," + vis.top + "," + vis.right + "," + vis.bottom + "]");
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
        lastObsScrollY = owner.getScrollY();
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
                int sy = owner.getScrollY();
                int ss = owner.getSelectionStart();
                int se = owner.getSelectionEnd();
                if (etScreenLoc[0] != lastObsScreenX
                        || etScreenLoc[1] != lastObsScreenY
                        || etWinLoc[0] != lastObsWinX
                        || etWinLoc[1] != lastObsWinY
                        || sx != lastObsScrollX
                        || sy != lastObsScrollY
                        || ss != lastObsSelStart
                        || se != lastObsSelEnd
                        || tmpVisRect.top != lastObsVisTop
                        || tmpVisRect.bottom != lastObsVisBottom) {
                    lastObsScreenX = etScreenLoc[0];
                    lastObsScreenY = etScreenLoc[1];
                    lastObsWinX = etWinLoc[0];
                    lastObsWinY = etWinLoc[1];
                    lastObsScrollX = sx;
                    lastObsScrollY = sy;
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
                mi.setEnabled(false);
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
            // Prefer ClipDescription so Android 12+ does not fire "pasted from clipboard" banner on caret tap
            ClipDescription desc = cm.getPrimaryClipDescription();
            if (desc != null) {
                if (desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                        || desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
                    return true;
                }
                int mimeCount = desc.getMimeTypeCount();
                for (int i = 0; i < mimeCount; i++) {
                    String mime = desc.getMimeType(i);
                    if (mime != null && mime.startsWith("text/")) {
                        return true;
                    }
                }
                return false;
            }
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

    private static boolean isNightMode(Context baseCtx) {
        int nightMask = baseCtx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return (nightMask == Configuration.UI_MODE_NIGHT_YES);
    }

    private static Context createDeviceFloatingThemeContext(Context baseCtx) {
        return new ContextThemeWrapper(baseCtx, android.R.style.Theme_DeviceDefault);
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
                ((HostedPopupActionMode) mode).updateWindowPosition();
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
    // Tiny dedicated overlay window added with WindowManager.addView()
    // Uses the exact same window type/token family as Menu and screen coordinates.
    // =========================================================================
    private static final class ToolbarWindow {
        private final TextActionModeHost host;
        private final Context ctx;
        private final Context themedCtx;

        private WindowManager windowManager;
        private WindowManager.LayoutParams windowParams;
        private FrameLayout rootContainer;
        private View panelCard;
        private boolean windowAdded = false;

        // Track closing view during 100ms fade-out so rapid re-open/destroy never leaks
        private WindowManager closingWm;
        private View closingRoot;

        ToolbarWindow(TextActionModeHost host, Context ctx, Context themedCtx) {
            this.host = host;
            this.ctx = ctx;
            this.themedCtx = themedCtx;
        }

        boolean isShowing() {
            return windowAdded && rootContainer != null && windowManager != null;
        }

        void showOrUpdate(View card,
                          int cardW,
                          int cardH,
                          int padSides,
                          int padTop,
                          int padBottom,
                          int screenX,
                          int screenY,
                          boolean animateIn) {
            if (!host.canShowHostWindow()) {
                dismissImmediate();
                return;
            }

            if (windowAdded && rootContainer != null && windowManager != null && windowParams != null) {
                if (panelCard != null) {
                    panelCard.animate().cancel();
                }
                rootContainer.setPadding(padSides, padTop, padSides, padBottom);
                rootContainer.removeAllViews();
                rootContainer.addView(card, new FrameLayout.LayoutParams(cardW, cardH));
                panelCard = card;
                windowParams.x = screenX;
                windowParams.y = screenY;
                try {
                    windowManager.updateViewLayout(rootContainer, windowParams);
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("ToolbarWindow.update: pos=[" + screenX + "," + screenY
                                + "] cardSize=" + cardW + "x" + cardH);
                    }
                    return;
                } catch (Throwable t) {
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("ToolbarWindow.update failed: "
                                + SecurityNative.diagException(t));
                    }
                    dismissImmediate();
                }
            }

            dismissPendingClosingWindow();
            dismissImmediate();

            WindowManager wm = resolveWindowManager();
            if (wm == null) return;

            FrameLayout container = new FrameLayout(themedCtx);
            container.setBackgroundColor(Color.TRANSPARENT);
            container.setPadding(padSides, padTop, padSides, padBottom);
            container.setClipToPadding(false);
            container.setClipChildren(false);
            container.addView(card, new FrameLayout.LayoutParams(cardW, cardH));

            int winType = resolveWindowType();
            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    winType,
                    flags,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.x = screenX;
            lp.y = screenY;
            lp.dimAmount = 0f;
            lp.windowAnimations = 0;

            try {
                wm.addView(container, lp);
                this.windowManager = wm;
                this.windowParams = lp;
                this.rootContainer = container;
                this.panelCard = card;
                this.windowAdded = true;
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("ToolbarWindow.add: type=" + winType
                            + " flags=0x" + Integer.toHexString(flags)
                            + " pos=[" + screenX + "," + screenY
                            + "] cardSize=" + cardW + "x" + cardH
                            + " overlay=" + host.overlayRequired);
                }
            } catch (Throwable t) {
                this.windowAdded = false;
                this.rootContainer = null;
                this.panelCard = null;
                this.windowParams = null;
                this.windowManager = null;
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("ToolbarWindow.add failed (type=" + winType + "): "
                            + SecurityNative.diagException(t));
                }
                return;
            }

            if (animateIn) {
                card.setPivotX(cardW / 2f);
                card.setPivotY(cardH / 2f);
                card.setAlpha(0f);
                card.setScaleX(0.92f);
                card.setScaleY(0.92f);
                card.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        }

        void updatePosition(int screenX, int screenY) {
            if (!windowAdded || rootContainer == null || windowManager == null || windowParams == null) {
                return;
            }
            if (windowParams.x == screenX && windowParams.y == screenY) {
                return;
            }
            windowParams.x = screenX;
            windowParams.y = screenY;
            try {
                windowManager.updateViewLayout(rootContainer, windowParams);
            } catch (Throwable t) {
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("ToolbarWindow.updatePosition failed: "
                            + SecurityNative.diagException(t));
                }
            }
        }

        void dismissImmediate() {
            if (panelCard != null) {
                panelCard.animate().cancel();
            }
            if (windowAdded && rootContainer != null && windowManager != null) {
                removeViewSafely(windowManager, rootContainer, "immediate");
            }
            windowAdded = false;
            rootContainer = null;
            panelCard = null;
            windowParams = null;
            windowManager = null;
            dismissPendingClosingWindow();
        }

        void dismissAnimated(EditText owner) {
            if (!windowAdded || rootContainer == null || windowManager == null) {
                dismissPendingClosingWindow();
                windowAdded = false;
                rootContainer = null;
                panelCard = null;
                windowParams = null;
                windowManager = null;
                return;
            }
            dismissPendingClosingWindow();

            final WindowManager wm = windowManager;
            final View closingContainer = rootContainer;
            final View animatingCard = panelCard;
            windowAdded = false;
            rootContainer = null;
            panelCard = null;
            windowParams = null;
            windowManager = null;

            if (animatingCard == null || host.destroyed || !isViewAttached(owner)) {
                removeViewSafely(wm, closingContainer, "animated-fallback");
                return;
            }

            closingWm = wm;
            closingRoot = closingContainer;

            animatingCard.animate().cancel();
            animatingCard.animate()
                    .alpha(0f)
                    .setDuration(100L)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (closingRoot == closingContainer) {
                                closingWm = null;
                                closingRoot = null;
                            }
                            removeViewSafely(wm, closingContainer, "animated");
                        }
                    })
                    .start();
        }

        private void dismissPendingClosingWindow() {
            if (closingRoot != null && closingWm != null) {
                closingRoot.animate().cancel();
                removeViewSafely(closingWm, closingRoot, "pending-cleanup");
            }
            closingWm = null;
            closingRoot = null;
        }

        private static void removeViewSafely(WindowManager wm, View view, String tag) {
            if (wm == null || view == null) return;
            try {
                wm.removeViewImmediate(view);
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("ToolbarWindow.remove: mode=" + tag);
                }
            } catch (Throwable t1) {
                try {
                    wm.removeView(view);
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("ToolbarWindow.remove(fallback): mode=" + tag);
                    }
                } catch (Throwable ignored) { }
            }
        }

        private WindowManager resolveWindowManager() {
            if (!host.overlayRequired && ctx instanceof Activity) {
                return ((Activity) ctx).getWindowManager();
            }
            return (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        }

        private int resolveWindowType() {
            if (host.overlayRequired || !(ctx instanceof Activity)) {
                return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;
            }
            return WindowManager.LayoutParams.TYPE_APPLICATION;
        }
    }

    // =========================================================================
    // System-faithful ActionMode backed by a real Menu & ToolbarWindow overlay
    // =========================================================================
    private static final class HostedPopupActionMode extends ActionMode {
        private final TextActionModeHost host;
        private final Context ctx;
        private final Context themedCtx;
        private final EditText owner;
        private final ActionMode.Callback origCallback;
        private final Menu menu;
        private final ToolbarWindow toolbarWindow;
        private final Rect tmpCallbackRect = new Rect();
        private final Rect tmpVisFrame = new Rect();
        private final int[] ownerScreen = new int[2];

        private final int themeBgColor;
        private final int themeBorderColor;
        private final int themeTextColor;
        private final int themeDisabledTextColor;
        private final int themeHighlightColor;

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
            this.toolbarWindow = new ToolbarWindow(host, ctx, this.themedCtx);

            this.themeBgColor = ModxTheme.CARD;
            this.themeBorderColor = ModxTheme.LINE_STRONG;
            this.themeTextColor = ModxTheme.FG;
            this.themeDisabledTextColor = ModxTheme.SUBTLE;
            this.themeHighlightColor = ModxTheme.accentSoft(ModxTheme.ACCENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasTypeArg) {
                try {
                    setType(type);
                } catch (Throwable ignored) { }
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
            buildAndShowWindow(items, true);
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
            boolean animate = !toolbarWindow.isShowing();
            buildAndShowWindow(items, animate);
        }

        @Override
        public void invalidateContentRect() {
            updateWindowPosition();
        }

        @Override
        public void finish() {
            finish(true);
        }

        void finish(boolean animateOut) {
            if (finished) return;
            finished = true;
            if (animateOut) {
                toolbarWindow.dismissAnimated(owner);
            } else {
                toolbarWindow.dismissImmediate();
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
            toolbarWindow.dismissImmediate();
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
            buildAndShowWindow(items, true);
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

        private Drawable makeToolbarItemBg(float density) {
            final float cornerRadius = ModxTheme.RADIUS_DP * density;
            GradientDrawable normal = new GradientDrawable();
            normal.setColor(Color.TRANSPARENT);
            normal.setCornerRadius(cornerRadius);

            GradientDrawable pressed = new GradientDrawable();
            pressed.setColor(themeHighlightColor);
            pressed.setCornerRadius(cornerRadius);

            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed}, pressed);
            states.addState(new int[0], normal);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                GradientDrawable mask = new GradientDrawable();
                mask.setColor(ModxTheme.TOOLBAR_RIPPLE_MASK);
                mask.setCornerRadius(cornerRadius);
                return new RippleDrawable(
                        ColorStateList.valueOf(themeHighlightColor),
                        null,
                        mask);
            }
            return states;
        }

        private TextView makeMenuItemView(final MenuItem item, String label, float density, boolean verticalRow) {
            TextView tv = new TextView(themedCtx);
            tv.setText(label);
            boolean enabled = item != null && item.isEnabled();
            tv.setEnabled(enabled);
            tv.setTextColor(new ColorStateList(
                    new int[][]{
                            new int[]{-android.R.attr.state_enabled},
                            new int[0]
                    },
                    new int[]{
                            themeDisabledTextColor,
                            themeTextColor
                    }));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tv.setGravity(verticalRow ? (Gravity.CENTER_VERTICAL | Gravity.START) : Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setIncludeFontPadding(false);
            int hPad = dp(12, density);
            tv.setPadding(hPad, 0, hPad, 0);
            int minW = dp(verticalRow ? 120 : 40, density);
            int minH = dp(40, density);
            tv.setMinWidth(minW);
            tv.setMinimumWidth(minW);
            tv.setMinHeight(minH);
            tv.setMinimumHeight(minH);
            tv.setClickable(enabled);
            tv.setFocusable(false);
            tv.setBackground(makeToolbarItemBg(density));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (item == null || !item.isEnabled()) return;
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("ToolbarWindow.onClick: itemId=" + item.getItemId()
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
            int size = dp(40, density);
            btn.setMinimumWidth(size);
            btn.setMinimumHeight(size);
            btn.setClickable(true);
            btn.setFocusable(false);
            btn.setContentDescription(isBackButton ? "Back" : "More options");
            btn.setBackground(makeToolbarItemBg(density));
            ImageView icon = new ImageView(themedCtx);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            icon.setImageDrawable(new ToolbarGlyphDrawable(themeTextColor, density, isBackButton));
            btn.addView(icon, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    overflowOpen = !isBackButton;
                    buildAndShowWindow(allItems, false);
                }
            });
            return btn;
        }

        private void buildAndShowWindow(ArrayList<MenuItem> items, boolean animateIn) {
            if (finished || hiddenForDrag || !isViewAttached(owner) || owner.getWindowToken() == null) {
                toolbarWindow.dismissImmediate();
                return;
            }
            lastItems.clear();
            lastItems.addAll(items);

            DisplayMetrics dm = safeMetrics(ctx);
            float density = dm.density > 0f ? dm.density : 2f;
            computeVisibleDisplayFrame(ctx, owner, dm, tmpVisFrame);

            shadowPadSides = dp(10, density);
            shadowPadTop = dp(6, density);
            shadowPadBottom = dp(10, density);

            int maxCardW = Math.min(
                    Math.max(dp(140, density), tmpVisFrame.width() - dp(24, density)),
                    dp(300, density));
            int rowH = dp(40, density);

            ArrayList<TextView> rowButtons = new ArrayList<TextView>(items.size());
            int[] itemWidths = new int[items.size()];
            int totalItemsW = 0;
            int hSpec = View.MeasureSpec.makeMeasureSpec(rowH, View.MeasureSpec.EXACTLY);
            int wSpec = View.MeasureSpec.makeMeasureSpec(maxCardW, View.MeasureSpec.AT_MOST);
            for (int i = 0; i < items.size(); i++) {
                MenuItem mi = items.get(i);
                TextView tv = makeMenuItemView(mi, resolveRealLabel(mi), density, false);
                tv.measure(wSpec, hSpec);
                int w = Math.max(dp(40, density), tv.getMeasuredWidth());
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
                int overflowBtnW = dp(40, density);
                int avail = Math.max(dp(56, density), maxCardW - overflowBtnW);
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

            final float cornerRadiusPx = ModxTheme.RADIUS_DP * density;
            final int borderPx = Math.max(1, dp(1, density));

            LinearLayout card = new LinearLayout(themedCtx);
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(themeBgColor);
            cardBg.setCornerRadius(cornerRadiusPx);
            cardBg.setStroke(borderPx, themeBorderColor);
            card.setBackground(cardBg);
            card.setPadding(borderPx, borderPx, borderPx, borderPx);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                card.setElevation(dp(6, density));
                card.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                    }
                });
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
                            new LinearLayout.LayoutParams(dp(40, density), rowH));
                }
            } else {
                card.setOrientation(LinearLayout.VERTICAL);
                card.addView(makeOverflowIconButton(items, density, true),
                        new LinearLayout.LayoutParams(dp(40, density), rowH));
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

            int[] winPos = computeToolbarWindowPos(dm, density, lastCardW, lastCardH, items.size(), true);
            toolbarWindow.showOrUpdate(
                    card,
                    lastCardW,
                    lastCardH,
                    shadowPadSides,
                    shadowPadTop,
                    shadowPadBottom,
                    winPos[0],
                    winPos[1],
                    animateIn);
        }

        void updateWindowPosition() {
            if (finished || hiddenForDrag || !isViewAttached(owner)) return;
            if (!toolbarWindow.isShowing() || lastCardW <= 0 || lastCardH <= 0) {
                return;
            }
            DisplayMetrics dm = safeMetrics(ctx);
            float density = dm.density > 0f ? dm.density : 2f;
            int[] winPos = computeToolbarWindowPos(dm, density, lastCardW, lastCardH, lastItems.size(), false);
            toolbarWindow.updatePosition(winPos[0], winPos[1]);
        }

        private int[] computeToolbarWindowPos(DisplayMetrics dm,
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

            int sA = Math.max(0, owner.getSelectionStart());
            int sB = Math.max(0, owner.getSelectionEnd());
            int sMin = Math.min(sA, sB);
            int sMax = Math.max(sA, sB);

            int padLeft = owner.getTotalPaddingLeft();
            int padRight = owner.getTotalPaddingRight();
            int innerLeft = padLeft;
            int innerRight = Math.max(innerLeft + 1, owner.getWidth() - padRight);

            int localLeft = innerLeft;
            int localRight = innerRight;
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
                    if (line == lineEnd) {
                        float px1 = layout.getPrimaryHorizontal(sMin);
                        float px2 = (sMax != sMin) ? layout.getPrimaryHorizontal(sMax) : px1;
                        int x1 = Math.round(contentLeftOff + Math.min(px1, px2));
                        int x2 = Math.round(contentLeftOff + Math.max(px1, px2));
                        int visX1 = Math.max(innerLeft, Math.min(innerRight, x1));
                        int visX2 = Math.max(innerLeft, Math.min(innerRight, x2));
                        localLeft = visX1;
                        localRight = Math.max(visX1 + 1, visX2);
                        selCenterX = ownerScreen[0] + (visX1 + visX2) / 2;
                    } else {
                        localLeft = innerLeft;
                        localRight = innerRight;
                        selCenterX = ownerScreen[0] + (innerLeft + innerRight) / 2;
                    }
                } catch (Throwable ignored) { }
            } else if (tmpCallbackRect.width() > 0 && tmpCallbackRect.height() > 0) {
                localLeft = tmpCallbackRect.left;
                localRight = tmpCallbackRect.right;
                localTextTop = tmpCallbackRect.top;
                localTextBottom = tmpCallbackRect.bottom;
                int centerLocalX = Math.max(0, Math.min(owner.getWidth(), tmpCallbackRect.centerX()));
                selCenterX = ownerScreen[0] + centerLocalX;
            }

            int selScreenLeft = ownerScreen[0] + localLeft;
            int selScreenRight = ownerScreen[0] + localRight;
            int selScreenTop = ownerScreen[1] + Math.max(0, Math.min(owner.getHeight(), localTextTop));
            int selScreenBottom = ownerScreen[1] + Math.max(1, Math.min(owner.getHeight(), localTextBottom));
            int cbScreenBottom = (tmpCallbackRect.height() > 0)
                    ? (ownerScreen[1] + tmpCallbackRect.bottom)
                    : selScreenBottom;

            int margin = dp(8, density);
            int minScreenX = tmpVisFrame.left + margin;
            int maxScreenX = Math.max(minScreenX, tmpVisFrame.right - cardW - margin);
            int cardScreenX = Math.max(minScreenX, Math.min(selCenterX - cardW / 2, maxScreenX));

            int gap = dp(8, density);
            int handleClearance = dp(28, density);
            int yAbove = selScreenTop - cardH - gap;
            int yBelow = Math.max(selScreenBottom + handleClearance + gap, cbScreenBottom + gap);

            int minTop = tmpVisFrame.top + margin;
            int maxBottomTop = Math.max(minTop, tmpVisFrame.bottom - cardH - margin);

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

            // Absolute SCREEN coordinates directly for WindowManager.addView(TOP | LEFT)
            int winScreenX = cardScreenX - shadowPadSides;
            int winScreenY = cardScreenY - shadowPadTop;

            if (BuildConfig.SEC_DIAG) {
                boolean changed = forceLog
                        || Math.abs(winScreenX - lastLoggedWinX) > 2
                        || Math.abs(winScreenY - lastLoggedWinY) > 2
                        || !flipDecision.equals(lastLoggedFlip);
                if (changed) {
                    lastLoggedWinX = winScreenX;
                    lastLoggedWinY = winScreenY;
                    lastLoggedFlip = flipDecision;
                    SecurityNative.diagUi("overlay-window.layout: selScreenRect=[" + selScreenLeft + "," + selScreenTop
                            + "," + selScreenRight + "," + selScreenBottom
                            + "] visFrame=[" + tmpVisFrame.left + "," + tmpVisFrame.top
                            + "," + tmpVisFrame.right + "," + tmpVisFrame.bottom
                            + "] flip=" + flipDecision
                            + " yAbove=" + yAbove + " yBelow=" + yBelow
                            + " finalCardScreen=[" + cardScreenX + "," + cardScreenY
                            + "] finalWinScreen=[" + winScreenX + "," + winScreenY
                            + "] cardSize=" + cardW + "x" + cardH
                            + " items=" + itemCount + " overflow=" + overflowOpen);
                }
            }

            return new int[]{ winScreenX, winScreenY };
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
                path.moveTo(cx - s * 0.25f, cy + s * 0.75f);
                canvas.drawPath(path, paint);
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
