package com.android.support;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
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

/**
 * Hosts real framework ActionMode (floating text-selection toolbar on API 23+,
 * legacy ActionMode on API 21-22) for EditTexts living inside a WindowManager.addView()
 * window (where ViewRootImpl has no DecorView and returns null from startActionModeForChild).
 */
final class TextActionModeHost {

    private static final String DBG_TAG = "MXL_UI";

    private final Context ctx;
    private final boolean overlayRequired;

    private Dialog hostDialog;
    private View dummyView;
    private ActionMode activeMode;
    private EditText activeOwner;
    private boolean destroyed = false;

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
        if (destroyed || ctx == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        if (hostDialog != null && hostDialog.isShowing() && dummyView != null) {
            return;
        }
        if (!canShowHostWindow()) {
            return;
        }
        try {
            dismissDialogOnly();

            Dialog dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            Context dlgCtx = dialog.getContext();
            FrameLayout container = new FrameLayout(dlgCtx);
            container.setBackgroundColor(Color.TRANSPARENT);

            View dummy = new View(dlgCtx);
            dummy.setBackgroundColor(Color.TRANSPARENT);
            dummy.setClickable(false);
            dummy.setFocusable(false);
            dummy.setFocusableInTouchMode(false);
            container.addView(dummy, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            dialog.setContentView(container, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
                if (overlayRequired || !(ctx instanceof Activity)) {
                    int winType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE;
                    w.setType(winType);
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
            debugLog("prepare: host dialog shown (overlay=" + overlayRequired + ")");
        } catch (Throwable t) {
            debugLog("prepare failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            dismissDialogOnly();
        }
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
            return null;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return null;
        }

        if (activeMode != null && activeOwner != owner) {
            finishActiveMode();
        }

        prepare();
        if (dummyView == null || dummyView.getWindowToken() == null || !dummyView.isShown()) {
            debugLog("startActionMode skipped: host dummyView not attached/visible yet");
            return null;
        }

        try {
            final ActionMode mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasTypeArg) {
                mode = Api23Helper.start(this, dummyView, owner, origCallback, type);
            } else {
                ActionMode.Callback legacyWrapped = new ActionMode.Callback() {
                    @Override
                    public boolean onCreateActionMode(ActionMode m, Menu menu) {
                        return origCallback.onCreateActionMode(m, menu);
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode m, Menu menu) {
                        return origCallback.onPrepareActionMode(m, menu);
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode m, MenuItem item) {
                        return origCallback.onActionItemClicked(m, item);
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode m) {
                        onHostModeDestroyed(m);
                        origCallback.onDestroyActionMode(m);
                    }
                };
                mode = dummyView.startActionMode(legacyWrapped);
            }

            if (mode != null) {
                activeMode = mode;
                activeOwner = owner;
                attachPositionObserver(owner);
                debugLog("startActionMode succeeded (type=" + type + ", overlay=" + overlayRequired + ")");
            } else {
                debugLog("startActionMode returned null (type=" + type + ")");
            }
            return mode;
        } catch (Throwable t) {
            debugLog("startActionMode failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
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
        }
    }

    void onEditTextFocusLost(EditText owner) {
        if (activeOwner == owner) {
            finishActiveMode();
        }
    }

    void onEditTextDetached(EditText owner) {
        if (activeOwner == owner) {
            finishActiveMode();
        }
        if (observedEditText == owner) {
            detachPositionObserver();
        }
    }

    void finishActiveMode() {
        detachPositionObserver();
        if (activeMode != null) {
            ActionMode modeToFinish = activeMode;
            activeMode = null;
            activeOwner = null;
            try {
                modeToFinish.finish();
            } catch (Throwable ignored) { }
        } else {
            activeOwner = null;
        }
    }

    void onVisibilityChanged(boolean visible) {
        if (destroyed) return;
        if (visible) {
            prepare();
        } else {
            finishActiveMode();
            dismissDialogOnly();
        }
    }

    void destroy() {
        destroyed = true;
        finishActiveMode();
        dismissDialogOnly();
    }

    private void onHostModeDestroyed(ActionMode mode) {
        if (activeMode == mode) {
            activeMode = null;
            activeOwner = null;
            detachPositionObserver();
        }
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
        if (outRect.left == 0 && outRect.top == 0
                && outRect.right == 0 && outRect.bottom == 0) {
            outRect.set(0, 0, Math.max(1, owner.getWidth()), Math.max(1, owner.getHeight()));
        }
        if (dummyView != null) {
            owner.getLocationOnScreen(etLoc);
            dummyView.getLocationOnScreen(dummyLoc);
            outRect.offset(etLoc[0] - dummyLoc[0], etLoc[1] - dummyLoc[1]);
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
                if (activeMode == null || activeOwner != owner || !owner.isAttachedToWindow()) {
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
        if (act.isFinishing()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && act.isDestroyed()) {
            return false;
        }
        return true;
    }

    private void dismissDialogOnly() {
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

    private static void debugLog(String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(DBG_TAG, msg);
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
                    return origCallback.onCreateActionMode(mode, menu);
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return origCallback.onPrepareActionMode(mode, menu);
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return origCallback.onActionItemClicked(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    host.onHostModeDestroyed(mode);
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
