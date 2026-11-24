package com.android.support;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * Hosts real framework ActionMode (floating text-selection toolbar on API 23+,
 * legacy ActionMode on API 21-22) for EditTexts living inside a WindowManager.addView()
 * window, with SEC_DIAG diagnostics ("MXL_UI") and an automatic PopupWindow fallback
 * attached to the EditText's own window if the framework path fails at runtime.
 */
final class TextActionModeHost {

    private final Context ctx;
    private final boolean overlayRequired;

    private Dialog hostDialog;
    private View dummyView;
    private boolean hostAttached = false;

    private ActionMode activeMode;
    private EditText activeOwner;
    private boolean destroyed = false;

    private boolean finishingByHost = false;
    private boolean modeItemClicked = false;
    private long modeCreatedAtMs = 0L;
    private boolean retryPending = false;
    private boolean fallbackTriggering = false;

    private PopupWindow fallbackPopup;
    private EditText fallbackOwner;

    private final int[] etLoc = new int[2];
    private final int[] dummyLoc = new int[2];

    private EditText observedEditText;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private int lastOwnerScreenX = Integer.MIN_VALUE;
    private int lastOwnerScreenY = Integer.MIN_VALUE;
    private int lastOwnerScrollX = Integer.MIN_VALUE;

    TextActionModeHost(Context context, boolean overlayRequired) {
        this.ctx = context;
        this.overlayRequired = overlayRequired;
    }

    void prepare() {
        if (destroyed || ctx == null) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("prepare skipped: destroyed=" + destroyed + " ctxNull=" + (ctx == null));
            }
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("prepare skipped: not on main looper");
            }
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
            final Dialog dialog = new Dialog(ctx, android.R.style.Theme_DeviceDefault_NoActionBar);
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
                lp.gravity = Gravity.TOP | Gravity.START;
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
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("startActionMode skipped: not on main looper");
            }
            return null;
        }

        if (activeMode != null && activeOwner != owner) {
            finishActiveMode("switch-owner");
        }
        dismissFallbackToolbar();

        if (!isHostReady()) {
            if (BuildConfig.SEC_DIAG) {
                boolean showing = false;
                try { showing = hostDialog != null && hostDialog.isShowing(); } catch (Throwable ignored) { }
                SecurityNative.diagUi("startActionMode: host not ready (showing=" + showing
                        + ", attached=" + hostAttached
                        + ", token=" + (dummyView != null && dummyView.getWindowToken() != null)
                        + ", shown=" + (dummyView != null && dummyView.isShown())
                        + ", size=" + (dummyView != null ? dummyView.getWidth() + "x" + dummyView.getHeight() : "null")
                        + ") -> scheduling 1-frame retry");
            }
            prepare();
            scheduleOneFrameRetry(owner, origCallback, type, hasTypeArg, 0);
            return null;
        }

        return startFrameworkMode(owner, origCallback, type, hasTypeArg, false);
    }

    private void scheduleOneFrameRetry(final EditText owner,
                                       final ActionMode.Callback origCallback,
                                       final int type,
                                       final boolean hasTypeArg,
                                       final int attempt) {
        if (retryPending) return;
        retryPending = true;
        owner.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                retryPending = false;
                if (destroyed || !isViewAttached(owner) || !owner.hasFocus()) {
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("1-frame retry aborted: destroyed=" + destroyed
                                + " attached=" + isViewAttached(owner)
                                + " focus=" + owner.hasFocus());
                    }
                    return;
                }
                if (activeMode != null) {
                    return;
                }
                if (isHostReady()) {
                    ActionMode retried = startFrameworkMode(owner, origCallback, type, hasTypeArg, true);
                    if (retried != null) {
                        return;
                    }
                } else if (attempt < 1) {
                    scheduleOneFrameRetry(owner, origCallback, type, hasTypeArg, attempt + 1);
                    return;
                } else if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("1-frame retry: host still not ready -> switching to fallback");
                }
                showFallbackToolbar(owner, "retry-not-ready-or-null");
            }
        });
    }

    private ActionMode startFrameworkMode(final EditText owner,
                                          final ActionMode.Callback origCallback,
                                          final int type,
                                          final boolean hasTypeArg,
                                          final boolean isRetry) {
        try {
            finishingByHost = false;
            modeItemClicked = false;
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
                            SecurityNative.diagUi("ActionMode.onClick: itemId=" + (item != null ? item.getItemId() : -1));
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

            if (mode != null) {
                activeMode = mode;
                activeOwner = owner;
                attachPositionObserver(owner);
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("toolbar path used: framework-host (type=" + type
                            + ", overlay=" + overlayRequired
                            + ", retry=" + isRetry + ")");
                }
            } else {
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("startFrameworkMode returned null (type=" + type
                            + ", retry=" + isRetry + ") -> fallback");
                }
                if (!isRetry) {
                    showFallbackToolbar(owner, "framework-returned-null");
                }
            }
            return mode;
        } catch (Throwable t) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("startFrameworkMode threw: " + SecurityNative.diagException(t));
            }
            if (!isRetry) {
                showFallbackToolbar(owner, "exception-" + t.getClass().getSimpleName());
            }
            return null;
        }
    }

    private void onHostModeCreated(final EditText owner, ActionMode m, Menu menu, boolean ok) {
        modeCreatedAtMs = SystemClock.uptimeMillis();
        modeItemClicked = false;
        int vis = countVisibleMenuItems(menu);
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("ActionMode.onCreate: ret=" + ok + " visibleItems=" + vis);
        }
        if (!ok || vis == 0) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("ActionMode.onCreate unusable (ret=" + ok + ", visibleItems=" + vis + ") -> fallback");
            }
            owner.post(new Runnable() {
                @Override
                public void run() {
                    if (!destroyed && isViewAttached(owner) && owner.hasFocus()) {
                        showFallbackToolbar(owner, "empty-create-menu");
                    }
                }
            });
        }
    }

    private void onHostModePrepared(final EditText owner, Menu menu, boolean ok) {
        int vis = countVisibleMenuItems(menu);
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("ActionMode.onPrepare: ret=" + ok + " visibleItems=" + vis);
        }
        if (vis == 0) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("ActionMode.onPrepare has 0 visible items -> fallback");
            }
            owner.post(new Runnable() {
                @Override
                public void run() {
                    if (!destroyed && isViewAttached(owner) && owner.hasFocus()) {
                        showFallbackToolbar(owner, "empty-prepare-menu");
                    }
                }
            });
        }
    }

    private void onHostModeDestroyed(final EditText owner, ActionMode mode) {
        long ageMs = SystemClock.uptimeMillis() - modeCreatedAtMs;
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("ActionMode.onDestroy: ageMs=" + ageMs
                    + " byHost=" + finishingByHost
                    + " clicked=" + modeItemClicked
                    + " stack=" + SecurityNative.diagCallerStack(3, 6));
        }
        boolean wasActive = (activeMode == mode);
        if (wasActive) {
            activeMode = null;
            activeOwner = null;
            detachPositionObserver();
        }
        if (!finishingByHost && !modeItemClicked && ageMs < 200L && !destroyed
                && owner != null && isViewAttached(owner) && owner.hasFocus()) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("ActionMode destroyed prematurely (" + ageMs + "ms) -> fallback");
            }
            owner.post(new Runnable() {
                @Override
                public void run() {
                    if (!destroyed && isViewAttached(owner) && owner.hasFocus() && activeMode == null) {
                        showFallbackToolbar(owner, "premature-destroy-" + ageMs + "ms");
                    }
                }
            });
        }
        finishingByHost = false;
    }

    void invalidateContentRectIfOwner(EditText owner) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && activeMode != null
                && activeOwner == owner) {
            Api23Helper.invalidateContentRect(activeMode);
        }
    }

    void invalidateMenuIfOwner(EditText owner) {
        if (activeMode != null && activeOwner == owner) {
            try {
                activeMode.invalidate();
            } catch (Throwable ignored) { }
        } else if (fallbackPopup != null && fallbackPopup.isShowing() && fallbackOwner == owner) {
            showFallbackToolbar(owner, "refresh-menu");
        }
    }

    void onEditTextFocusLost(EditText owner) {
        if (activeOwner == owner) {
            finishActiveMode("focus-lost");
        }
        if (fallbackOwner == owner) {
            dismissFallbackToolbar();
        }
    }

    void onEditTextDetached(EditText owner) {
        if (activeOwner == owner) {
            finishActiveMode("edittext-detached");
        }
        if (fallbackOwner == owner) {
            dismissFallbackToolbar();
        }
        if (observedEditText == owner) {
            detachPositionObserver();
        }
    }

    void finishActiveMode() {
        finishActiveMode("caller");
    }

    void finishActiveMode(String reason) {
        detachPositionObserver();
        dismissFallbackToolbar();
        if (activeMode != null) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("finishActiveMode: reason=" + reason);
            }
            ActionMode modeToFinish = activeMode;
            activeMode = null;
            activeOwner = null;
            finishingByHost = true;
            try {
                modeToFinish.finish();
            } catch (Throwable ignored) { }
            finishingByHost = false;
        } else {
            activeOwner = null;
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
            finishActiveMode("host-invisible");
            dismissDialogOnly();
        }
    }

    void destroy() {
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("destroy() called");
        }
        destroyed = true;
        finishActiveMode("destroy");
        dismissDialogOnly();
    }

    private void computeContentRect(EditText owner,
                                    ActionMode.Callback origCallback,
                                    ActionMode mode,
                                    Rect outRect) {
        if (outRect == null) return;
        outRect.setEmpty();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Api23Helper.fillOriginalContentRect(origCallback, mode, owner, outRect);
        }
        int bL = outRect.left, bT = outRect.top, bR = outRect.right, bB = outRect.bottom;
        if (outRect.left == 0 && outRect.top == 0
                && outRect.right == 0 && outRect.bottom == 0) {
            outRect.set(0, 0, Math.max(1, owner.getWidth()), Math.max(1, owner.getHeight()));
        }
        if (dummyView != null) {
            owner.getLocationOnScreen(etLoc);
            dummyView.getLocationOnScreen(dummyLoc);
            outRect.offset(etLoc[0] - dummyLoc[0], etLoc[1] - dummyLoc[1]);
            if (BuildConfig.SEC_DIAG) {
                boolean offScreen = outRect.right <= 0 || outRect.bottom <= 0
                        || outRect.left >= dummyView.getWidth()
                        || outRect.top >= dummyView.getHeight();
                SecurityNative.diagUi("onGetContentRect: before=[" + bL + "," + bT + "," + bR + "," + bB
                        + "] etLoc=[" + etLoc[0] + "," + etLoc[1]
                        + "] dummyLoc=[" + dummyLoc[0] + "," + dummyLoc[1]
                        + "] after=[" + outRect.left + "," + outRect.top + "," + outRect.right + "," + outRect.bottom
                        + "] dummySize=" + dummyView.getWidth() + "x" + dummyView.getHeight()
                        + (offScreen ? " OFF_SCREEN" : ""));
            }
        }
    }

    // =========================================================================
    // Automatic Fallback Toolbar PopupWindow (anchored to EditText's own window)
    // =========================================================================
    private void showFallbackToolbar(final EditText owner, final String reason) {
        if (destroyed || owner == null || fallbackTriggering) return;
        if (!isViewAttached(owner) || owner.getWindowToken() == null) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("fallback aborted: owner not attached/no windowToken (reason=" + reason + ")");
            }
            return;
        }
        fallbackTriggering = true;
        try {
            dismissFallbackToolbar();

            final boolean masked = owner.getTransformationMethod() instanceof PasswordTransformationMethod;
            final int selStart = Math.max(0, owner.getSelectionStart());
            final int selEnd = Math.max(0, owner.getSelectionEnd());
            final int sMin = Math.min(selStart, selEnd);
            final int sMax = Math.max(selStart, selEnd);
            final boolean hasSelection = sMax > sMin;
            final int len = owner.getText() != null ? owner.getText().length() : 0;
            final boolean allSelected = len > 0 && sMin == 0 && sMax == len;
            final boolean hasClip = hasClipboardText(ctx);

            final boolean showCut = hasSelection && !masked;
            final boolean showCopy = hasSelection && !masked;
            final boolean showPaste = hasClip;
            final boolean showSelectAll = len > 0 && !allSelected;

            if (!showCut && !showCopy && !showPaste && !showSelectAll) {
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("fallback skipped: 0 available actions (masked="
                            + masked + ", hasSel=" + hasSelection + ", hasClip=" + hasClip + ", len=" + len + ")");
                }
                return;
            }

            DisplayMetrics dm = safeMetrics(ctx);
            float density = dm.density > 0f ? dm.density : 2f;

            LinearLayout bar = new LinearLayout(ctx);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#242834"));
            bg.setCornerRadius(dp(18, density));
            bg.setStroke(dp(1, density), Color.parseColor("#3DDB87"));
            bar.setBackground(bg);
            int hPad = dp(6, density);
            bar.setPadding(hPad, 0, hPad, 0);

            int added = 0;
            if (showCut) {
                addFallbackAction(bar, owner, android.R.id.cut,
                        ctx.getString(android.R.string.cut), density, added > 0);
                added++;
            }
            if (showCopy) {
                addFallbackAction(bar, owner, android.R.id.copy,
                        ctx.getString(android.R.string.copy), density, added > 0);
                added++;
            }
            if (showPaste) {
                addFallbackAction(bar, owner, android.R.id.paste,
                        ctx.getString(android.R.string.paste), density, added > 0);
                added++;
            }
            if (showSelectAll) {
                addFallbackAction(bar, owner, android.R.id.selectAll,
                        ctx.getString(android.R.string.selectAll), density, added > 0);
                added++;
            }

            bar.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST));
            int popW = bar.getMeasuredWidth();
            int popH = bar.getMeasuredHeight();

            PopupWindow popup = new PopupWindow(bar,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    false);
            popup.setTouchable(true);
            popup.setOutsideTouchable(false);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                popup.setElevation(dp(8, density));
            }

            owner.getLocationOnScreen(etLoc);
            int anchorCenterX = etLoc[0] + owner.getWidth() / 2;
            try {
                Layout layout = owner.getLayout();
                if (layout != null) {
                    int midOff = (sMin + sMax) / 2;
                    float primaryX = layout.getPrimaryHorizontal(midOff);
                    anchorCenterX = etLoc[0] + owner.getTotalPaddingLeft() - owner.getScrollX()
                            + Math.round(primaryX);
                }
            } catch (Throwable ignored) { }

            int margin = dp(8, density);
            int x = Math.max(margin, Math.min(anchorCenterX - popW / 2, dm.widthPixels - popW - margin));
            int yAbove = etLoc[1] - popH - dp(8, density);
            int yBelow = etLoc[1] + owner.getHeight() + dp(8, density);
            int y = yAbove >= dp(24, density) ? yAbove : Math.min(yBelow, dm.heightPixels - popH - margin);

            popup.showAtLocation(owner, Gravity.NO_GRAVITY, x, y);
            this.fallbackPopup = popup;
            this.fallbackOwner = owner;

            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("toolbar path used: fallback (reason=" + reason
                        + ", actions=" + added
                        + ", masked=" + masked
                        + ", pos=" + x + "," + y + ")");
            }
        } catch (Throwable t) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("fallback failed: " + SecurityNative.diagException(t));
            }
            dismissFallbackToolbar();
        } finally {
            fallbackTriggering = false;
        }
    }

    private void addFallbackAction(LinearLayout bar,
                                   final EditText owner,
                                   final int menuId,
                                   String label,
                                   float density,
                                   boolean withDivider) {
        if (withDivider) {
            View div = new View(ctx);
            div.setBackgroundColor(Color.parseColor("#3A3F4E"));
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                    Math.max(1, Math.round(density)), dp(18, density));
            bar.addView(div, dLp);
        }
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#ECE8DF"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        int hPad = dp(12, density);
        tv.setPadding(hPad, 0, hPad, 0);
        tv.setMinHeight(dp(40, density));
        tv.setClickable(true);
        tv.setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tv.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(0x333DDB87), null, null));
        }
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("fallback.onClick: itemId=" + menuId);
                }
                try {
                    owner.onTextContextMenuItem(menuId);
                } catch (Throwable t) {
                    if (BuildConfig.SEC_DIAG) {
                        SecurityNative.diagUi("fallback onTextContextMenuItem failed: "
                                + SecurityNative.diagException(t));
                    }
                }
                if (menuId == android.R.id.selectAll) {
                    owner.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!destroyed && isViewAttached(owner) && owner.hasFocus()) {
                                showFallbackToolbar(owner, "refresh-after-selectAll");
                            }
                        }
                    });
                } else {
                    dismissFallbackToolbar();
                }
            }
        });
        bar.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40, density)));
    }

    void dismissFallbackToolbar() {
        if (fallbackPopup != null) {
            try {
                if (fallbackPopup.isShowing()) {
                    fallbackPopup.dismiss();
                }
            } catch (Throwable ignored) { }
            fallbackPopup = null;
        }
        fallbackOwner = null;
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

    private void attachPositionObserver(final EditText owner) {
        detachPositionObserver();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || owner == null) {
            return;
        }
        owner.getLocationOnScreen(etLoc);
        lastOwnerScreenX = etLoc[0];
        lastOwnerScreenY = etLoc[1];
        lastOwnerScrollX = owner.getScrollX();
        observedEditText = owner;

        preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (activeMode == null || activeOwner != owner || !isViewAttached(owner)) {
                    return true;
                }
                owner.getLocationOnScreen(etLoc);
                int sx = owner.getScrollX();
                if (etLoc[0] != lastOwnerScreenX
                        || etLoc[1] != lastOwnerScreenY
                        || sx != lastOwnerScrollX) {
                    lastOwnerScreenX = etLoc[0];
                    lastOwnerScreenY = etLoc[1];
                    lastOwnerScrollX = sx;
                    Api23Helper.invalidateContentRect(activeMode);
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
        if (act.isFinishing()) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("prepare skipped: Activity isFinishing");
            }
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && act.isDestroyed()) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("prepare skipped: Activity isDestroyed");
            }
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

    private static int countVisibleMenuItems(Menu menu) {
        if (menu == null) return 0;
        int visible = 0;
        int count = menu.size();
        for (int i = 0; i < count; i++) {
            MenuItem mi = menu.getItem(i);
            if (mi != null && mi.isVisible()) {
                visible++;
            }
        }
        return visible;
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
                        SecurityNative.diagUi("ActionMode.onClick: itemId="
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
                    host.computeContentRect(owner, origCallback, mode, outRect);
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
            } else {
                outRect.set(0, 0, Math.max(1, owner.getWidth()), Math.max(1, owner.getHeight()));
            }
        }

        static void invalidateContentRect(ActionMode mode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mode != null) {
                try {
                    mode.invalidateContentRect();
                } catch (Throwable ignored) { }
            }
        }
    }
}
