package com.android.support;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.UUID;

public class LoginHelper {

    private static final String DBG = "ModXDebug";

    public interface Callback {
        void onLoginSuccess(String token, String user, String deviceId);
    }

    public interface CheckListener {
        void onChanged(boolean checked);
    }

    // ================= GREEN THEME =================
    private static final int COLOR_FIELD_BG    = Color.parseColor("#2A2D35");
    private static final int COLOR_FIELD_BORD  = Color.parseColor("#3E424C");
    private static final int COLOR_ACCENT      = Color.parseColor("#3DDB87");
    private static final int COLOR_ACCENT_HI   = Color.parseColor("#6EE7A9");
    private static final int COLOR_ACCENT_DEEP = Color.parseColor("#2BB673");
    private static final int COLOR_SUCCESS     = Color.parseColor("#4FBA82");
    private static final int COLOR_DANGER      = Color.parseColor("#C25C56");
    private static final int COLOR_WARN        = Color.parseColor("#E0A94D");
    private static final int COLOR_TEXT        = Color.parseColor("#ECE8DF");
    private static final int COLOR_TEXT_MUTED  = Color.parseColor("#8D8F99");
    private static final int COLOR_HINT        = Color.parseColor("#7A7D87");
    private static final int COLOR_ON_ACCENT   = Color.parseColor("#0C2418");

    private static final int COLOR_DIALOG_BG    = Color.parseColor("#1D2029");
    private static final int COLOR_DIALOG_BG_TOP= Color.parseColor("#242835");
    private static final int COLOR_DIALOG_TITLE = Color.parseColor("#ECE8DF");
    private static final int COLOR_DIALOG_SUB   = Color.parseColor("#A7A9B2");
    private static final int COLOR_DIALOG_SUB2  = Color.parseColor("#7D7F89");
    private static final int COLOR_DIALOG_BODY  = Color.parseColor("#C6C8D0");
    private static final int COLOR_CTA          = Color.parseColor("#3DDB87");
    private static final int COLOR_OUTLINE      = Color.parseColor("#3A3D49");

    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int ICON_USER = 0;
    private static final int ICON_LOCK = 1;
    private static final float ICON_STROKE_GRID = 1.65f;

    private final Context ctx;
    private final boolean overlayRequired;
    private final Callback callback;
    private final TextActionModeHost actionModeHost;

    private EditText editUser, editPass;
    private LinearLayout userBox, passBox;
    private ImageView userIconView, passIconView;
    private FrameLayout userChip, passChip;
    private CustomCheck rememberCb, showCb;
    private Button loginBtn;
    private TextView statusTxt;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed = false;
    private volatile boolean loginInProgress = false;
    private volatile boolean savedCheckInProgress = false;
    private boolean keyExpiredDialogShowing = false;
    private Object networkCallback = null;
    private Runnable retrySavedSessionRunnable = null;
    private int offlineRetryCount = 0;

    private Typeface tfRegular, tfMedium, tfBold;

    public LoginHelper(Context context, Callback cb) {
        this(context, !(context instanceof android.app.Activity), cb);
    }

    public LoginHelper(Context context, boolean overlayRequired, Callback cb) {
        this.ctx = context;
        this.overlayRequired = overlayRequired;
        this.callback = cb;
        this.actionModeHost = new TextActionModeHost(context, overlayRequired);
        SecureTokenStore.purgeLegacyPlaintext(context);
        this.tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
        this.tfMedium  = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        this.tfBold    = Typeface.create("sans-serif", Typeface.BOLD);
    }

    public void prepareActionModeHost() {
        if (actionModeHost != null) {
            actionModeHost.prepare();
        }
    }

    public void dismissTextActionMode() {
        if (actionModeHost != null) {
            actionModeHost.finishActiveMode("login-helper");
        }
    }

    public void onHostVisibilityChanged(boolean visible) {
        if (actionModeHost != null) {
            actionModeHost.onVisibilityChanged(visible);
        }
    }

    public void showExternalStatus(final String message, final boolean showExpiredDialog) {
        if (destroyed) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (destroyed) return;
                if (!TextUtils.isEmpty(message)) {
                    setStatus(message, COLOR_DANGER);
                }
                if (showExpiredDialog) {
                    showKeyExpiredDialog();
                }
            }
        });
    }

    public void cleanup() {
        destroyed = true;
        cancelSavedSessionRetry();
        if (actionModeHost != null) {
            actionModeHost.destroy();
        }
    }

    private static DisplayMetrics safeDisplayMetrics(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        try {
            if (context != null) {
                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null && wm.getDefaultDisplay() != null) {
                    wm.getDefaultDisplay().getMetrics(dm);
                    if (dm.widthPixels > 0 && dm.heightPixels > 0 && dm.density > 0f) {
                        return dm;
                    }
                }
            }
        } catch (Exception ignored) { }
        return android.content.res.Resources.getSystem().getDisplayMetrics();
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                safeDisplayMetrics(ctx));
    }

    public View buildView() {
        FrameLayout wrapper = new FrameLayout(ctx);
        wrapper.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

        final LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundColor(Color.TRANSPARENT);
        root.addView(card);

        card.addView(makeSimpleHeader());

        userBox = makeFieldContainer();
        userChip = makeIconChip(ICON_USER);
        userIconView = (ImageView) userChip.getChildAt(0);
        userBox.addView(userChip);

        editUser = makeInput("user", false);
        editUser.setHint("Username");
        editUser.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editUser.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editUser.setTypeface(tfRegular);
        editUser.setSingleLine(true);
        LinearLayout.LayoutParams ueLp = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f);
        editUser.setLayoutParams(ueLp);
        enableRichTextInteraction(editUser);
        userBox.addView(editUser);
        final ImageView userPasteBtn = makePasteButton(editUser);
        userBox.addView(userPasteBtn);

        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(48));
        uLp.setMargins(0, 0, 0, dp(10));
        userBox.setLayoutParams(uLp);
        card.addView(userBox);

        passBox = makeFieldContainer();
        passChip = makeIconChip(ICON_LOCK);
        passIconView = (ImageView) passChip.getChildAt(0);
        passBox.addView(passChip);

        editPass = makeInput("pass", true);
        editPass.setHint("Password or license key");
        editPass.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editPass.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editPass.setTypeface(tfRegular);
        editPass.setSingleLine(true);
        editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
        attachPasswordActionModeGuard(editPass);
        LinearLayout.LayoutParams peLp = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f);
        editPass.setLayoutParams(peLp);
        enableRichTextInteraction(editPass);
        passBox.addView(editPass);
        final ImageView passPasteBtn = makePasteButton(editPass);
        passBox.addView(passPasteBtn);

        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(48));
        pLp.setMargins(0, 0, 0, dp(8));
        passBox.setLayoutParams(pLp);
        card.addView(passBox);

        attachFieldFocus(userBox, editUser, userIconView, userPasteBtn, "user");
        attachFieldFocus(passBox, editPass, passIconView, passPasteBtn, "pass");

        editUser.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    editPass.requestFocus();
                    editPass.setSelection(editPass.getText().length());
                    forceShowKeyboard(editPass);
                    return true;
                }
                return false;
            }
        });

        editPass.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    dismissTextActionMode();
                    hideKeyboard(editPass);
                    performLogin();
                    return true;
                }
                return false;
            }
        });

        LinearLayout cbRow = new LinearLayout(ctx);
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbRow.setGravity(Gravity.CENTER_VERTICAL);
        cbRow.setPadding(0, dp(2), 0, dp(2));

        rememberCb = new CustomCheck("Remember", false);
        rememberCb.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        showCb = new CustomCheck("Show", false);
        showCb.setListener(new CheckListener() {
            @Override
            public void onChanged(boolean checked) {
                int selStart = editPass.getSelectionStart();
                int selEnd = editPass.getSelectionEnd();
                if (checked) {
                    editPass.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    editPass.setTransformationMethod(
                            android.text.method.HideReturnsTransformationMethod.getInstance());
                } else {
                    editPass.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_PASSWORD
                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    editPass.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
                editPass.setTypeface(tfRegular);
                editPass.setSingleLine(true);
                int len = editPass.getText() != null ? editPass.getText().length() : 0;
                if (selStart >= 0 && selEnd >= 0 && selStart <= len && selEnd <= len) {
                    editPass.setSelection(selStart, selEnd);
                }
                if (actionModeHost != null) {
                    actionModeHost.invalidateMenuIfOwner(editPass);
                }
            }
        });

        rememberCb.setListener(new CheckListener() {
            @Override
            public void onChanged(boolean checked) {
                if (!checked) {
                    cancelSavedSessionRetry();
                    SecureTokenStore.clearToken(ctx);
                }
            }
        });

        cbRow.addView(rememberCb);
        cbRow.addView(showCb);
        card.addView(cbRow);

        loginBtn = new Button(ctx);
        loginBtn.setText("SIGN IN");
        loginBtn.setAllCaps(false);
        loginBtn.setTextColor(COLOR_ON_ACCENT);
        loginBtn.setTextSize(12.5f);
        loginBtn.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            loginBtn.setLetterSpacing(0.14f);
        }

        GradientDrawable lb = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_ACCENT_HI, COLOR_ACCENT, COLOR_ACCENT_DEEP});
        lb.setCornerRadius(dp(13));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(0x30000000), lb, null);
            loginBtn.setBackground(ripple);
            loginBtn.setElevation(dp(4));
        } else {
            loginBtn.setBackground(lb);
        }
        loginBtn.setMinHeight(0); loginBtn.setMinimumHeight(0);
        loginBtn.setMinWidth(0);  loginBtn.setMinimumWidth(0);
        loginBtn.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(46));
        bLp.setMargins(0, dp(10), 0, 0);
        loginBtn.setLayoutParams(bLp);
        card.addView(loginBtn);

        statusTxt = new TextView(ctx);
        statusTxt.setText("");
        statusTxt.setTextColor(COLOR_TEXT_MUTED);
        statusTxt.setTextSize(10f);
        statusTxt.setTypeface(tfMedium);
        statusTxt.setGravity(Gravity.CENTER);
        statusTxt.setSingleLine(true);
        statusTxt.setEllipsize(TextUtils.TruncateAt.END);
        statusTxt.setIncludeFontPadding(false);
        statusTxt.setAlpha(0f);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(15));
        sLp.setMargins(0, dp(6), 0, 0);
        statusTxt.setLayoutParams(sLp);
        card.addView(statusTxt);

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(0.975f).scaleY(0.975f).setDuration(100)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                loginBtn.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(260)
                                        .setInterpolator(new OvershootInterpolator(1.3f))
                                        .start();
                            }
                        }).start();
                dismissTextActionMode();
                hideKeyboard(editUser);
                hideKeyboard(editPass);
                performLogin();
            }
        });

        final String savedToken = SecureTokenStore.loadToken(ctx);
        final String tokenUser = SecureTokenStore.extractUserFromToken(savedToken);
        if (!TextUtils.isEmpty(savedToken) && !TextUtils.isEmpty(tokenUser)) {
            editUser.setText(tokenUser);
            rememberCb.setChecked(true);
            final String devId = getOrCreateDeviceId();
            startSavedSessionCheck(savedToken, devId);
        }

        wrapper.addView(root, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        card.setAlpha(0f);
        card.setTranslationY(dp(10));
        card.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                card.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                card.animate().alpha(1f).translationY(0f).setDuration(340)
                        .setInterpolator(new DecelerateInterpolator(1.6f)).start();
            }
        });

        wrapper.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (actionModeHost != null) {
                    actionModeHost.prepare();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                cleanup();
            }
        });

        return wrapper;
    }

    private View makeSimpleHeader() {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        hLp.setMargins(0, 0, 0, dp(18));
        header.setLayoutParams(hLp);

        TextView title = new TextView(ctx);
        title.setText("Welcome Back");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(20f);
        title.setTypeface(tfBold);
        title.setGravity(Gravity.CENTER);
        if (BuildConfig.SEC_DIAG) {
            final Handler holdHandler = new Handler(Looper.getMainLooper());
            final int touchSlop = android.view.ViewConfiguration.get(ctx).getScaledTouchSlop();
            final float[] downPos = new float[2];
            final boolean[] holdFired = new boolean[1];
            final Runnable openDiagRunnable = new Runnable() {
                @Override
                public void run() {
                    holdFired[0] = true;
                    DiagLogViewer.show(ctx, overlayRequired);
                }
            };
            title.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            holdFired[0] = false;
                            downPos[0] = event.getRawX();
                            downPos[1] = event.getRawY();
                            holdHandler.removeCallbacks(openDiagRunnable);
                            holdHandler.postDelayed(openDiagRunnable, 2000L);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            if (Math.abs(event.getRawX() - downPos[0]) > touchSlop
                                    || Math.abs(event.getRawY() - downPos[1]) > touchSlop) {
                                holdHandler.removeCallbacks(openDiagRunnable);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            holdHandler.removeCallbacks(openDiagRunnable);
                            return holdFired[0];
                    }
                    return false;
                }
            });
        }
        header.addView(title);

        TextView subtitle = new TextView(ctx);
        subtitle.setText("Sign in to continue to your account");
        subtitle.setTextColor(COLOR_TEXT_MUTED);
        subtitle.setTextSize(11.5f);
        subtitle.setTypeface(tfRegular);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        sLp.setMargins(0, dp(3), 0, 0);
        subtitle.setLayoutParams(sLp);
        header.addView(subtitle);

        return header;
    }

    private LinearLayout makeFieldContainer() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_FIELD_BG);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), COLOR_FIELD_BORD);
        box.setBackground(bg);
        box.setClipToPadding(false);
        return box;
    }

    private FrameLayout makeIconChip(int iconType) {
        FrameLayout chip = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
        lp.setMargins(dp(10), 0, dp(6), 0);
        chip.setLayoutParams(lp);
        chip.setBackgroundColor(Color.TRANSPARENT);

        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(createFieldSvgIcon(iconType, COLOR_TEXT_MUTED));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER);
        iv.setLayoutParams(ivLp);
        chip.addView(iv);
        return chip;
    }

    private static SvgIcon createFieldSvgIcon(int iconType, int color) {
        if (iconType == ICON_USER) {
            return new SvgIcon(color, ICON_STROKE_GRID)
                    .addPath("M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2")
                    .addCircle(12f, 7f, 4f);
        }
        return new SvgIcon(color, ICON_STROKE_GRID)
                .addRect(3f, 11f, 18f, 11f, 2f, 2f)
                .addPath("M7 11V7a5 5 0 0 1 10 0v4");
    }

    private static SvgIcon createPasteSvgIcon(int color) {
        return new SvgIcon(color, ICON_STROKE_GRID)
                .addPath("M11 14h10")
                .addPath("M16 4h2a2 2 0 0 1 2 2v1.344")
                .addPath("m17 18 4-4-4-4")
                .addPath("M8 4H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 1.793-1.113")
                .addRect(8f, 2f, 8f, 4f, 1f, 1f);
    }

    private EditText makeInput(final String which, final boolean isPasswordField) {
        EditText e = new HostAwareEditText(ctx, which, actionModeHost, isPasswordField);
        e.setTag(which);
        e.setHintTextColor(COLOR_HINT);
        e.setTextColor(COLOR_TEXT);
        e.setTextSize(13f);
        e.setTypeface(tfRegular);
        e.setSingleLine(true);
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        e.setClickable(true);
        e.setCursorVisible(true);
        e.setSelectAllOnFocus(false);
        e.setBackground(null);
        e.setIncludeFontPadding(false);
        e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        e.setPadding(dp(8), 0, dp(4), 0);
        e.setHighlightColor(withAlpha(COLOR_ACCENT, 0x55));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            applyAccentSelectionDrawables(e);
        }
        return e;
    }

    private static boolean isPasswordMasked(EditText et) {
        return et != null && (et.getTransformationMethod() instanceof PasswordTransformationMethod);
    }

    private static final class HostAwareEditText extends EditText {
        private final String fieldTag;
        private final TextActionModeHost host;
        private final boolean isPasswordField;

        HostAwareEditText(Context context,
                          String fieldTag,
                          TextActionModeHost host,
                          boolean isPasswordField) {
            super(context);
            this.fieldTag = fieldTag;
            this.host = host;
            this.isPasswordField = isPasswordField;
        }

        @Override
        public boolean onTextContextMenuItem(int id) {
            if (isPasswordField && isPasswordMasked(this)
                    && (id == android.R.id.cut || id == android.R.id.copy)) {
                return false;
            }
            return super.onTextContextMenuItem(id);
        }

        @Override
        public boolean performLongClick() {
            int selS = getSelectionStart();
            int selE = getSelectionEnd();
            int len = getText() != null ? getText().length() : 0;
            boolean res = super.performLongClick();
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("EditText(" + fieldTag + ").performLongClick: res=" + res
                        + " sel=[" + selS + "," + selE + "]"
                        + " newSel=[" + getSelectionStart() + "," + getSelectionEnd() + "]"
                        + " len=" + len
                        + " hasFocus=" + hasFocus()
                        + " winFocus=" + hasWindowFocus());
            }
            return res;
        }

        @Override
        public ActionMode startActionMode(ActionMode.Callback callback) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("EditText(" + fieldTag + ").startActionMode(1-arg) called"
                        + " hasFocus=" + hasFocus()
                        + " winFocus=" + hasWindowFocus());
            }
            ActionMode mode = (host != null)
                    ? host.startActionMode(this, callback)
                    : super.startActionMode(callback);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("EditText(" + fieldTag + ").startActionMode(1-arg) -> "
                        + (mode != null ? "non-null" : "NULL"));
            }
            return mode;
        }

        @Override
        public ActionMode startActionMode(ActionMode.Callback callback, int type) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("EditText(" + fieldTag + ").startActionMode(type=" + type + ") called"
                        + " hasFocus=" + hasFocus()
                        + " winFocus=" + hasWindowFocus());
            }
            ActionMode mode = (host != null)
                    ? host.startActionMode(this, callback, type)
                    : super.startActionMode(callback, type);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("EditText(" + fieldTag + ").startActionMode(type=" + type + ") -> "
                        + (mode != null ? "non-null" : "NULL"));
            }
            return mode;
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            super.onWindowFocusChanged(hasWindowFocus);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("EditText(" + fieldTag + ").onWindowFocusChanged: winFocus="
                        + hasWindowFocus + " hasFocus=" + hasFocus());
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("EditText(" + fieldTag + ").onAttachedToWindow: winFocus="
                        + hasWindowFocus());
            }
            if (host != null) {
                host.prepare();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("EditText(" + fieldTag + ").onDetachedFromWindow");
            }
            if (host != null) {
                host.onEditTextDetached(this);
            }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("EditText(" + fieldTag + ").onWindowVisibilityChanged: vis="
                        + visibility);
            }
            if (host != null) {
                if (visibility == View.VISIBLE) {
                    host.prepare();
                } else {
                    host.finishActiveMode("window-invisible");
                }
            }
        }

        @Override
        protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("EditText(" + fieldTag + ").onFocusChanged: focused="
                        + focused + " winFocus=" + hasWindowFocus());
            }
            if (!focused && host != null) {
                host.onEditTextFocusLost(this);
            }
        }

        @Override
        protected void onSelectionChanged(int selStart, int selEnd) {
            super.onSelectionChanged(selStart, selEnd);
            if (host != null) {
                host.invalidateContentRectIfOwner(this);
            }
        }

        @Override
        protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
            super.onTextChanged(text, start, lengthBefore, lengthAfter);
            if (host != null) {
                host.dismissFallbackToolbar();
            }
        }
    }

    private void attachPasswordActionModeGuard(final EditText passField) {
        ActionMode.Callback cb = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, android.view.Menu menu) {
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("PasswordGuard.onCreate: items="
                            + (menu != null ? menu.size() : 0));
                }
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, android.view.Menu menu) {
                boolean masked = isPasswordMasked(passField);
                int before = (menu != null) ? menu.size() : 0;
                if (menu != null && masked) {
                    menu.removeItem(android.R.id.cut);
                    menu.removeItem(android.R.id.copy);
                }
                int after = (menu != null) ? menu.size() : 0;
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("PasswordGuard.onPrepare: masked=" + masked
                            + " before=" + before + " after=" + after);
                }
                return menu != null;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) { }
        };
        passField.setCustomSelectionActionModeCallback(cb);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            passField.setCustomInsertionActionModeCallback(cb);
        }
    }

    private void applyAccentSelectionDrawables(EditText e) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setShape(GradientDrawable.RECTANGLE);
            cursor.setColor(COLOR_ACCENT);
            cursor.setSize(Math.max(2, dp(2)), dp(18));
            e.setTextCursorDrawable(cursor);

            e.setTextSelectHandle(tintOrFallbackHandle(e.getTextSelectHandle(), 0));
            e.setTextSelectHandleLeft(tintOrFallbackHandle(e.getTextSelectHandleLeft(), -1));
            e.setTextSelectHandleRight(tintOrFallbackHandle(e.getTextSelectHandleRight(), 1));
        } catch (Exception ignored) { }
    }

    private Drawable tintOrFallbackHandle(Drawable d, int align) {
        if (d != null) {
            Drawable copy = d.mutate();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                copy.setTint(COLOR_ACCENT);
            }
            return copy;
        }
        return new HandleDrawable(COLOR_ACCENT, align, dp(20));
    }

    private void enableRichTextInteraction(final EditText e) {
        e.setLongClickable(true);
        e.setCursorVisible(true);
        e.setFocusableInTouchMode(true);
        e.setFocusable(true);
        e.setClickable(true);
    }

    private ImageView makePasteButton(final EditText target) {
        final ImageView btn = new ImageView(ctx);
        final SvgIcon pasteIcon = createPasteSvgIcon(COLOR_TEXT_MUTED);
        btn.setImageDrawable(pasteIcon);
        btn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // 40x42dp touch target; padding leaves exact 20x20dp drawable bounds
        btn.setPadding(dp(10), dp(11), dp(10), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(42));
        lp.setMargins(0, 0, dp(4), 0);
        btn.setLayoutParams(lp);
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setContentDescription("Paste");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable mask = new GradientDrawable();
            mask.setShape(GradientDrawable.OVAL);
            mask.setColor(Color.WHITE);
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(withAlpha(COLOR_ACCENT, 0x18)), null, mask);
            btn.setBackground(ripple);
        }

        btn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        pasteIcon.setColor(COLOR_ACCENT_HI);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        pasteIcon.setColor(target.hasFocus() ? COLOR_ACCENT_HI : COLOR_TEXT_MUTED);
                        break;
                }
                return false;
            }
        });

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasteFromClipboard(target);
                v.animate().scaleX(0.86f).scaleY(0.86f).setDuration(85)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                btn.animate().scaleX(1f).scaleY(1f).setDuration(170)
                                        .setInterpolator(new OvershootInterpolator(2.0f)).start();
                            }
                        }).start();
            }
        });
        return btn;
    }

    private void pasteFromClipboard(EditText target) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                setStatus("Clipboard is empty", COLOR_WARN);
                return;
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                setStatus("Clipboard is empty", COLOR_WARN);
                return;
            }
            CharSequence pasted = clip.getItemAt(0).coerceToText(ctx);
            if (pasted == null || pasted.length() == 0) {
                setStatus("Clipboard is empty", COLOR_WARN);
                return;
            }
            String text = pasted.toString().trim();
            dismissTextActionMode();
            target.setText(text);
            target.setSelection(target.getText().length());
            target.requestFocus();
            forceShowKeyboard(target);
        } catch (Exception e) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("pasteFromClipboard failed: " + SecurityNative.diagException(e));
            }
        }
    }

    private void attachFieldFocus(final LinearLayout box, final EditText et,
                                   final ImageView icon, final ImageView pasteBtn,
                                   final String tag) {
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                final GradientDrawable bg = (GradientDrawable) box.getBackground();
                int fromColor = hasFocus ? COLOR_FIELD_BORD : COLOR_ACCENT;
                int toColor   = hasFocus ? COLOR_ACCENT      : COLOR_FIELD_BORD;

                ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
                va.setDuration(200);
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        bg.setStroke(dp(hasFocus ? 1.5f : 1f), (Integer) a.getAnimatedValue());
                    }
                });
                va.start();

                if (icon.getDrawable() instanceof SvgIcon) {
                    ((SvgIcon) icon.getDrawable()).animateColor(
                            hasFocus ? COLOR_ACCENT_HI : COLOR_TEXT_MUTED, 200);
                }
                if (pasteBtn != null && pasteBtn.getDrawable() instanceof SvgIcon) {
                    ((SvgIcon) pasteBtn.getDrawable()).animateColor(
                            hasFocus ? COLOR_ACCENT_HI : COLOR_TEXT_MUTED, 200);
                }
                if (hasFocus) {
                    et.post(new Runnable() {
                        @Override
                        public void run() {
                            forceShowKeyboard(et);
                            if (et.getWidth() > 0 && et.getHeight() > 0) {
                                et.requestRectangleOnScreen(
                                        new Rect(0, 0, et.getWidth(), et.getHeight()), false);
                            }
                        }
                    });
                }
            }
        });
    }

    private void forceShowKeyboard(final EditText et) {
        if (et == null) return;
        try {
            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return;
            imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
        } catch (Exception e) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("forceShowKeyboard failed: " + SecurityNative.diagException(e));
            }
        }
    }

    private void hideKeyboard(EditText et) {
        try {
            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && et != null) {
                imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void setStatus(final String msg, final int color) {
        if (statusTxt == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                statusTxt.animate().cancel();
                statusTxt.animate().alpha(0f).setDuration(120)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                statusTxt.setText(msg);
                                statusTxt.setTextColor(color);
                                statusTxt.animate().alpha(1f).setDuration(220).start();
                            }
                        }).start();
            }
        });
    }

    private String getVersionName() {
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private String getOrCreateDeviceId() {
        String cached = SecureTokenStore.loadDeviceId(ctx);
        if (!TextUtils.isEmpty(cached)) return cached;

        String raw = "";
        try {
            raw = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) { }

        StringBuilder clean = new StringBuilder();
        if (raw != null) {
            for (int i = 0; i < raw.length() && clean.length() < 64; i++) {
                char c = raw.charAt(i);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z')
                        || (c >= 'A' && c <= 'Z') || c == '_' || c == '-') {
                    clean.append(c);
                }
            }
        }
        String id = clean.toString();
        if (TextUtils.isEmpty(id)) {
            id = UUID.randomUUID().toString().replace("-", "");
        }
        SecureTokenStore.saveDeviceId(ctx, id);
        return id;
    }

    private static boolean isServerRejectionReason(String reason) {
        return "session_invalid".equals(reason)
                || "blocked".equals(reason)
                || "expired".equals(reason)
                || "device_mismatch".equals(reason)
                || "user_not_found".equals(reason)
                || "bad_input".equals(reason);
    }

    private void applyLoginFailureStatus(String reason, int retryAfterSec, boolean forSavedSession) {
        if ("expired".equals(reason)) {
            setStatus("Key expired", COLOR_DANGER);
            showKeyExpiredDialog();
        } else if ("blocked".equals(reason)) {
            setStatus("Account blocked", COLOR_DANGER);
            showKeyExpiredDialog();
        } else if ("device_mismatch".equals(reason)) {
            setStatus("Registered to another device", COLOR_DANGER);
        } else if ("user_not_found".equals(reason)) {
            setStatus("Account no longer exists", COLOR_DANGER);
        } else if ("rate_limited".equals(reason)) {
            if (retryAfterSec >= 60) {
                int mins = (retryAfterSec + 59) / 60;
                setStatus("Too many attempts. Try again in " + mins + " min", COLOR_DANGER);
            } else if (retryAfterSec > 0) {
                setStatus("Too many attempts. Try again in " + retryAfterSec + "s", COLOR_DANGER);
            } else {
                setStatus("Too many attempts. Try again later", COLOR_DANGER);
            }
        } else if ("no_match".equals(reason)
                || "invalid_credentials".equals(reason)) {
            setStatus("Invalid username or password", COLOR_DANGER);
        } else if ("session_invalid".equals(reason)) {
            setStatus(forSavedSession ? "Saved login expired or invalid" : "Invalid server signature", COLOR_DANGER);
        } else if ("no_permission".equals(reason)) {
            setStatus("Missing INTERNET permission", COLOR_DANGER);
        } else if ("tls_pin".equals(reason)) {
            setStatus("SSL pin mismatch", COLOR_DANGER);
        } else if ("tls_error".equals(reason)) {
            setStatus("SSL handshake error", COLOR_DANGER);
        } else if ("native_config".equals(reason)) {
            setStatus("Native config error", COLOR_DANGER);
        } else if (forSavedSession) {
            setStatus("No internet. Connect to the internet to use your saved login.", COLOR_WARN);
        } else if ("no_internet".equals(reason)) {
            setStatus("No internet / DNS blocked", COLOR_DANGER);
        } else if ("connect_error".equals(reason)) {
            setStatus("Server unreachable", COLOR_DANGER);
        } else if ("timeout".equals(reason)) {
            setStatus("Connection timed out", COLOR_DANGER);
        } else if (reason != null && reason.startsWith("http_")) {
            setStatus("Server error (" + reason.toUpperCase() + ")", COLOR_DANGER);
        } else if ("network".equals(reason)) {
            setStatus("Connection failed", COLOR_DANGER);
        } else {
            setStatus("Login failed (" + reason + ")", COLOR_DANGER);
        }
    }

    private void startSavedSessionCheck(final String savedToken, final String deviceId) {
        if (destroyed || loginInProgress || savedCheckInProgress) return;
        if (TextUtils.isEmpty(savedToken) || TextUtils.isEmpty(deviceId)) return;

        savedCheckInProgress = true;
        cancelSavedSessionRetry();

        if (loginBtn != null) {
            loginBtn.setEnabled(false);
            loginBtn.setAlpha(0.55f);
            loginBtn.setText("SIGN IN");
        }
        setStatus("Checking saved login...", COLOR_ACCENT_HI);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String sentNonce = ModFirebase.generateNonce();
                final JSONObject r = ModFirebase.verifySession(ctx, savedToken, deviceId, sentNonce);

                boolean ok = false;
                String reason = "network";
                String freshToken = "", userOut = "", respNonce = "";
                int retryAfterSec = 0;

                if (r != null) {
                    ok = r.optBoolean("ok", false);
                    reason = r.optString("reason", "unknown");
                    freshToken = r.optString("token", "");
                    userOut = r.optString("user", "");
                    respNonce = r.optString("nonce", "");
                    retryAfterSec = r.optInt("retryAfterSec", 0);
                }

                if (ok) {
                    if (TextUtils.isEmpty(freshToken)
                            || TextUtils.isEmpty(userOut)
                            || !sentNonce.equals(respNonce)
                            || !SecurityNative.verifySessionToken(freshToken, userOut, deviceId, sentNonce)) {
                        ok = false;
                        reason = "session_invalid";
                    }
                }

                if (!ok) {
                    final String fReason = reason;
                    final int fRetry = retryAfterSec;
                    final boolean rejected = isServerRejectionReason(fReason);
                    if (rejected) {
                        SecureTokenStore.clearToken(ctx);
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            savedCheckInProgress = false;
                            if (destroyed) return;
                            if (!loginInProgress && loginBtn != null) {
                                loginBtn.setEnabled(true);
                                loginBtn.setAlpha(1f);
                                loginBtn.setText("SIGN IN");
                            }
                            applyLoginFailureStatus(fReason, fRetry, true);
                            if (!rejected && !"rate_limited".equals(fReason)
                                    && rememberCb != null && rememberCb.isChecked()
                                    && !TextUtils.isEmpty(SecureTokenStore.loadToken(ctx))) {
                                scheduleSavedSessionRetry(deviceId);
                            }
                        }
                    });
                    return;
                }

                if (destroyed || loginInProgress) {
                    savedCheckInProgress = false;
                    return;
                }

                final boolean keepSaved = (rememberCb == null || rememberCb.isChecked());
                if (keepSaved) {
                    SecureTokenStore.saveToken(ctx, freshToken);
                } else {
                    SecureTokenStore.clearToken(ctx);
                }

                final String fToken = freshToken;
                final String fUser = userOut;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        savedCheckInProgress = false;
                        if (destroyed || loginInProgress) return;
                        offlineRetryCount = 0;
                        if (loginBtn != null) {
                            loginBtn.setEnabled(true);
                            loginBtn.setAlpha(1f);
                            loginBtn.setText("SIGN IN");
                        }
                        setStatus("Welcome back!", COLOR_SUCCESS);
                        checkUpdateAfterLogin(fToken, fUser, deviceId, sentNonce);
                    }
                });
            }
        }).start();
    }

    private boolean isNetworkAvailableKnown(boolean[] knownOut) {
        if (knownOut != null && knownOut.length > 0) knownOut[0] = false;
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network active = cm.getActiveNetwork();
                if (knownOut != null && knownOut.length > 0) knownOut[0] = true;
                return active != null;
            }
            @SuppressWarnings("deprecation")
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (knownOut != null && knownOut.length > 0) knownOut[0] = true;
            return ni != null && ni.isConnected();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private boolean tryRegisterNetworkCallback(final String deviceId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || networkCallback != null) {
            return networkCallback != null;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    mainHandler.removeCallbacksAndMessages("net_avail");
                    mainHandler.postAtTime(new Runnable() {
                        @Override
                        public void run() {
                            triggerPendingSavedRetry(deviceId);
                        }
                    }, "net_avail", android.os.SystemClock.uptimeMillis() + 700L);
                }
            };
            cm.registerDefaultNetworkCallback(cb);
            networkCallback = cb;
            return true;
        } catch (Throwable ignored) {
            networkCallback = null;
            return false;
        }
    }

    private void triggerPendingSavedRetry(String deviceId) {
        if (destroyed || loginInProgress || savedCheckInProgress) return;
        if (rememberCb == null || !rememberCb.isChecked()) return;
        if (editPass != null && editPass.getText() != null && editPass.getText().length() > 0) {
            return;
        }
        String latest = SecureTokenStore.loadToken(ctx);
        if (TextUtils.isEmpty(latest)) return;
        startSavedSessionCheck(latest, deviceId);
    }

    private void scheduleSavedSessionRetry(final String deviceId) {
        if (destroyed || loginInProgress || savedCheckInProgress) return;
        final boolean hasCallback = tryRegisterNetworkCallback(deviceId);

        if (retrySavedSessionRunnable != null) {
            mainHandler.removeCallbacks(retrySavedSessionRunnable);
        }
        offlineRetryCount++;
        long delayMs = hasCallback ? 12000L : Math.min(15000L, 4000L + offlineRetryCount * 2000L);
        retrySavedSessionRunnable = new Runnable() {
            @Override
            public void run() {
                if (destroyed || loginInProgress || savedCheckInProgress) return;
                if (rememberCb == null || !rememberCb.isChecked()) return;
                String latest = SecureTokenStore.loadToken(ctx);
                if (TextUtils.isEmpty(latest)) return;

                boolean[] known = new boolean[1];
                boolean connected = isNetworkAvailableKnown(known);
                if (!known[0] || connected) {
                    triggerPendingSavedRetry(deviceId);
                } else {
                    scheduleSavedSessionRetry(deviceId);
                }
            }
        };
        mainHandler.postDelayed(retrySavedSessionRunnable, delayMs);
    }

    private void cancelSavedSessionRetry() {
        mainHandler.removeCallbacksAndMessages("net_avail");
        if (retrySavedSessionRunnable != null) {
            mainHandler.removeCallbacks(retrySavedSessionRunnable);
            retrySavedSessionRunnable = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager)
                        ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback((ConnectivityManager.NetworkCallback) networkCallback);
                }
            } catch (Throwable ignored) { }
            networkCallback = null;
        }
    }

    private void performLogin() {
        if (loginInProgress || savedCheckInProgress) return;

        final String inputUser = editUser.getText().toString().trim().toLowerCase();
        final String inputPass = editPass.getText().toString().trim();

        if (TextUtils.isEmpty(inputUser) || TextUtils.isEmpty(inputPass)) {
            setStatus("Please fill in all fields", COLOR_WARN);
            return;
        }

        cancelSavedSessionRetry();
        loginInProgress = true;
        loginBtn.setEnabled(false);
        loginBtn.setAlpha(0.75f);
        loginBtn.setText("SIGNING IN...");
        setStatus("Verifying credentials...", COLOR_ACCENT_HI);

        final boolean shouldRemember = (rememberCb != null && rememberCb.isChecked());
        if (!shouldRemember) {
            SecureTokenStore.clearToken(ctx);
        }

        final String deviceId = getOrCreateDeviceId();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String sentNonce = ModFirebase.generateNonce();
                final JSONObject r = ModFirebase.login(ctx, inputUser, inputPass, deviceId, sentNonce);

                boolean ok = false;
                String reason = "network";
                String token = "", userOut = "", respNonce = "";
                int retryAfterSec = 0;

                if (r != null) {
                    ok = r.optBoolean("ok", false);
                    reason = r.optString("reason", "unknown");
                    token = r.optString("token", "");
                    userOut = r.optString("user", "");
                    respNonce = r.optString("nonce", "");
                    retryAfterSec = r.optInt("retryAfterSec", 0);
                }

                if (ok) {
                    if (TextUtils.isEmpty(token)
                            || TextUtils.isEmpty(userOut)
                            || !sentNonce.equals(respNonce)
                            || !SecurityNative.verifySessionToken(token, userOut, deviceId, sentNonce)) {
                        ok = false;
                        reason = "session_invalid";
                    }
                }

                if (!ok) {
                    final String fReason = reason;
                    final int fRetry = retryAfterSec;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            loginInProgress = false;
                            if (destroyed) return;
                            loginBtn.setEnabled(true);
                            loginBtn.setAlpha(1f);
                            loginBtn.setText("SIGN IN");
                            applyLoginFailureStatus(fReason, fRetry, false);
                        }
                    });
                    return;
                }

                if (shouldRemember) {
                    SecureTokenStore.saveToken(ctx, token);
                } else {
                    SecureTokenStore.clearToken(ctx);
                }

                final String fToken = token;
                final String fUser = userOut;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loginInProgress = false;
                        if (destroyed) return;
                        loginBtn.setEnabled(true);
                        loginBtn.setAlpha(1f);
                        loginBtn.setText("SIGN IN");
                        setStatus("Welcome back!", COLOR_SUCCESS);
                        Toast.makeText(ctx, "Login Success", Toast.LENGTH_SHORT).show();
                        checkUpdateAfterLogin(fToken, fUser, deviceId, sentNonce);
                    }
                });
            }
        }).start();
    }

    private void checkUpdateAfterLogin(final String token, final String user,
                                       final String deviceId, final String nonce) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject updateJson = ModFirebase.fetchUpdate();
                if (updateJson == null) { proceedToMenu(token, user, deviceId, nonce); return; }
                try {
                    JSONObject up = updateJson.optJSONObject("up");
                    if (up == null) { proceedToMenu(token, user, deviceId, nonce); return; }
                    String latest = up.optString("version", "");
                    String msg = up.optString("message", "");
                    String current = getVersionName();
                    if (!TextUtils.isEmpty(latest) && !current.equals(latest)) {
                        final String fv = latest, fm = msg;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() { showUpdateDialog(fv, fm, token, user, deviceId, nonce); }
                        });
                    } else {
                        proceedToMenu(token, user, deviceId, nonce);
                    }
                } catch (Exception e) {
                    proceedToMenu(token, user, deviceId, nonce);
                }
            }
        }).start();
    }

    private void proceedToMenu(final String token, final String user,
                               final String deviceId, final String nonce) {
        if (destroyed) return;
        if (TextUtils.isEmpty(token) || TextUtils.isEmpty(user) || TextUtils.isEmpty(nonce)) {
            mainHandler.post(new Runnable() {
                @Override public void run() { setStatus("Session invalid", COLOR_DANGER); }
            });
            return;
        }
        if (!SecurityNative.verifySessionToken(token, user, deviceId, nonce)) {
            mainHandler.post(new Runnable() {
                @Override public void run() { setStatus("Session invalid or expired", COLOR_DANGER); }
            });
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (destroyed) return;
                cleanup();
                if (callback != null) callback.onLoginSuccess(token, user, deviceId);
            }
        });
    }

    private LinearLayout newDialogCard() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(18));
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_DIALOG_BG_TOP, COLOR_DIALOG_BG});
        cardBg.setCornerRadius(dp(10));
        cardBg.setStroke(dp(1), COLOR_OUTLINE);
        card.setBackground(cardBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(14));
        }
        return card;
    }

    private FrameLayout newDialogIconHolder(int mainColor, Drawable glyph) {
        FrameLayout iconHolder = new FrameLayout(ctx);
        int outer = dp(54);
        LinearLayout.LayoutParams ihLp = new LinearLayout.LayoutParams(outer, outer);
        ihLp.setMargins(0, 0, 0, dp(12));
        iconHolder.setLayoutParams(ihLp);

        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setColor(withAlpha(mainColor, 0x2A));
        View glowView = new View(ctx);
        glowView.setBackground(glow);
        iconHolder.addView(glowView, new FrameLayout.LayoutParams(outer, outer, Gravity.CENTER));

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(mainColor);
        View circleView = new View(ctx);
        circleView.setBackground(circle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            circleView.setElevation(dp(5));
        }
        iconHolder.addView(circleView, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));

        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(glyph);
        iconHolder.addView(icon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        return iconHolder;
    }

    private Button newPrimaryDialogButton(String text) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(COLOR_ON_ACCENT);
        btn.setTextSize(12.5f);
        btn.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btn.setLetterSpacing(0.08f);
        }
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_ACCENT_HI, COLOR_CTA, COLOR_ACCENT_DEEP});
        bg.setCornerRadius(dp(24));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(0x2A000000), bg, null);
            btn.setBackground(ripple);
            btn.setElevation(dp(3));
        } else {
            btn.setBackground(bg);
        }
        btn.setMinHeight(0); btn.setMinimumHeight(0);
        btn.setMinWidth(0);  btn.setMinimumWidth(0);
        btn.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(44));
        lp.setMargins(0, dp(16), 0, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button newSecondaryDialogButton(String text) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(COLOR_DIALOG_SUB);
        btn.setTextSize(11.5f);
        btn.setTypeface(tfMedium);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(dp(1.5f), COLOR_OUTLINE);
        bg.setCornerRadius(dp(24));
        btn.setBackground(bg);
        btn.setMinHeight(0); btn.setMinimumHeight(0);
        btn.setMinWidth(0);  btn.setMinimumWidth(0);
        btn.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(40));
        lp.setMargins(0, dp(8), 0, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void presentPremiumDialog(final android.app.AlertDialog[] ref, final LinearLayout card,
                                       boolean cancelable,
                                       final android.content.DialogInterface.OnCancelListener onCancel) {
        DisplayMetrics dm = safeDisplayMetrics(ctx);
        final int screenW = dm.widthPixels;
        final int screenH = dm.heightPixels;

        MaxHeightScrollView scroll = new MaxHeightScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setMaxHeightPx((int) (screenH * 0.80f));
        scroll.addView(card, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        FrameLayout dialogRoot = new FrameLayout(ctx);
        int hPad = dp(16);
        dialogRoot.setPadding(hPad, hPad, hPad, hPad);
        dialogRoot.addView(scroll, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        card.setAlpha(0f);
        card.setScaleX(0.9f);
        card.setScaleY(0.9f);

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(ctx);
        b.setView(dialogRoot);
        final android.app.AlertDialog d = b.create();
        d.setCanceledOnTouchOutside(false);
        d.setCancelable(cancelable);
        if (cancelable && onCancel != null) d.setOnCancelListener(onCancel);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            boolean canOverlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx);
            if (!(ctx instanceof android.app.Activity) || canOverlay) {
                if (Build.VERSION.SDK_INT >= 26) d.getWindow().setType(2038);
                else d.getWindow().setType(2002);
            }
        }
        ref[0] = d;
        d.show();

        if (d.getWindow() != null) {
            int maxWidthPx = dp(330);
            int wanted = Math.min(maxWidthPx, (int) (screenW * 0.9f));
            WindowManager.LayoutParams lp = d.getWindow().getAttributes();
            lp.width = wanted;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            d.getWindow().setAttributes(lp);
        }

        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(230)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
    }

    private void showUpdateDialog(String version, String msg,
                                  final String token, final String user,
                                  final String deviceId, final String nonce) {
        dismissTextActionMode();
        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        final LinearLayout card = newDialogCard();
        card.addView(newDialogIconHolder(COLOR_CTA, new UpdateIcon(COLOR_ON_ACCENT)));

        TextView title = new TextView(ctx);
        title.setText("NEW UPDATE");
        title.setTextColor(COLOR_DIALOG_TITLE);
        title.setTextSize(15f);
        title.setTypeface(tfBold);
        title.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) title.setLetterSpacing(0.06f);
        card.addView(title);

        TextView versionView = new TextView(ctx);
        versionView.setText("Version " + version + " is available now");
        versionView.setTextColor(COLOR_DIALOG_SUB);
        versionView.setTextSize(11.5f);
        versionView.setTypeface(tfRegular);
        versionView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        vLp.setMargins(0, dp(5), 0, 0);
        versionView.setLayoutParams(vLp);
        card.addView(versionView);

        TextView currentView = new TextView(ctx);
        currentView.setText("Your version: " + getVersionName());
        currentView.setTextColor(COLOR_DIALOG_SUB2);
        currentView.setTextSize(10f);
        currentView.setTypeface(tfRegular);
        currentView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams curLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        curLp.setMargins(0, dp(2), 0, 0);
        currentView.setLayoutParams(curLp);
        card.addView(currentView);

        if (msg != null && !msg.trim().isEmpty()) {
            TextView msgView = new TextView(ctx);
            msgView.setText(msg);
            msgView.setTextColor(COLOR_DIALOG_BODY);
            msgView.setTextSize(10.5f);
            msgView.setTypeface(tfRegular);
            msgView.setGravity(Gravity.CENTER);
            msgView.setMaxLines(4);
            msgView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            mLp.setMargins(0, dp(10), 0, 0);
            msgView.setLayoutParams(mLp);
            card.addView(msgView);
        }

        Button updateBtn = newPrimaryDialogButton("UPDATE NOW");
        card.addView(updateBtn);
        Button continueBtn = newSecondaryDialogButton("Continue for now");
        card.addView(continueBtn);

        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/kayesahmmedpro"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception e) { }
            }
        });

        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ref[0] != null) ref[0].dismiss();
                proceedToMenu(token, user, deviceId, nonce);
            }
        });

        presentPremiumDialog(ref, card, false, null);
    }

    private void showKeyExpiredDialog() {
        if (keyExpiredDialogShowing) return;
        keyExpiredDialogShowing = true;
        dismissTextActionMode();

        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        final LinearLayout card = newDialogCard();
        card.addView(newDialogIconHolder(COLOR_DANGER, new LockIcon(Color.WHITE)));

        TextView title = new TextView(ctx);
        title.setText("ACCESS EXPIRED");
        title.setTextColor(COLOR_DIALOG_TITLE);
        title.setTextSize(15f);
        title.setTypeface(tfBold);
        title.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) title.setLetterSpacing(0.06f);
        card.addView(title);

        TextView body = new TextView(ctx);
        body.setText("Your subscription has ended or the account is blocked.\n\nContact the seller to renew access.");
        body.setTextColor(COLOR_DIALOG_BODY);
        body.setTextSize(11f);
        body.setTypeface(tfRegular);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        bLp.setMargins(0, dp(10), 0, 0);
        body.setLayoutParams(bLp);
        card.addView(body);

        Button contact = newPrimaryDialogButton("CONTACT SELLER");
        card.addView(contact);

        contact.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                keyExpiredDialogShowing = false;
                if (ref[0] != null) ref[0].dismiss();
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/kayesahmmedpro"));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception e) { }
            }
        });

        presentPremiumDialog(ref, card, true, new android.content.DialogInterface.OnCancelListener() {
            @Override public void onCancel(android.content.DialogInterface di) {
                keyExpiredDialogShowing = false;
            }
        });
    }

    private static class MaxHeightScrollView extends ScrollView {
        private int maxHeightPx = Integer.MAX_VALUE;
        MaxHeightScrollView(Context c) { super(c); }
        void setMaxHeightPx(int px) { this.maxHeightPx = px; }
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int hSpec = heightMeasureSpec;
            if (maxHeightPx > 0 && maxHeightPx < Integer.MAX_VALUE) {
                int size = View.MeasureSpec.getSize(heightMeasureSpec);
                int mode = View.MeasureSpec.getMode(heightMeasureSpec);
                if (mode == View.MeasureSpec.UNSPECIFIED || size > maxHeightPx) {
                    hSpec = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST);
                }
            }
            super.onMeasure(widthMeasureSpec, hSpec);
        }
    }

    private class CustomCheck extends LinearLayout {
        private final GradientDrawable boxBg;
        private final View checkmark;
        private boolean checked;
        private CheckListener listener;

        CustomCheck(String label, boolean initial) {
            super(ctx);
            this.checked = initial;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout box = new FrameLayout(ctx);
            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(dp(16), dp(16));
            boxLp.setMargins(0, 0, dp(8), 0);
            box.setLayoutParams(boxLp);

            boxBg = new GradientDrawable();
            boxBg.setCornerRadius(dp(4));
            boxBg.setStroke(dp(1.5f), initial ? COLOR_ACCENT : COLOR_FIELD_BORD);
            boxBg.setColor(initial ? withAlpha(COLOR_ACCENT, 0x22) : Color.TRANSPARENT);
            box.setBackground(boxBg);

            checkmark = new View(ctx);
            FrameLayout.LayoutParams cmLp = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER);
            checkmark.setLayoutParams(cmLp);
            GradientDrawable cmBg = new GradientDrawable();
            cmBg.setColor(COLOR_ACCENT);
            cmBg.setCornerRadius(dp(2));
            checkmark.setBackground(cmBg);
            checkmark.setVisibility(initial ? View.VISIBLE : View.GONE);
            box.addView(checkmark);

            TextView labelView = new TextView(ctx);
            labelView.setText(label);
            labelView.setTextColor(COLOR_TEXT_MUTED);
            labelView.setTextSize(10f);
            labelView.setTypeface(tfRegular);

            addView(box);
            addView(labelView);

            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { toggle(); }
            });
        }

        void setListener(CheckListener l) { this.listener = l; }
        void setChecked(boolean value) { if (this.checked != value) toggle(); }
        boolean isChecked() { return checked; }

        private void toggle() {
            checked = !checked;
            checkmark.setVisibility(checked ? View.VISIBLE : View.GONE);
            boxBg.setStroke(dp(1.5f), checked ? COLOR_ACCENT : COLOR_FIELD_BORD);
            boxBg.setColor(checked ? withAlpha(COLOR_ACCENT, 0x22) : Color.TRANSPARENT);
            if (listener != null) listener.onChanged(checked);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static class HandleDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int align;
        private final int sizePx;

        HandleDrawable(int color, int align, int sizePx) {
            this.align = align;
            this.sizePx = Math.max(12, sizePx);
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public int getIntrinsicWidth() { return sizePx; }
        @Override public int getIntrinsicHeight() { return sizePx; }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;
            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f, b.top);
            canvas.scale(s, s);
            path.reset();
            if (align < 0) {
                path.moveTo(20f, 2f);
                path.lineTo(20f, 14f);
                path.cubicTo(20f, 18.4f, 16.4f, 22f, 12f, 22f);
                path.cubicTo(7.6f, 22f, 4f, 18.4f, 4f, 14f);
                path.cubicTo(4f, 9.6f, 7.6f, 6f, 12f, 6f);
                path.lineTo(20f, 2f);
            } else if (align > 0) {
                path.moveTo(4f, 2f);
                path.lineTo(12f, 6f);
                path.cubicTo(16.4f, 6f, 20f, 9.6f, 20f, 14f);
                path.cubicTo(20f, 18.4f, 16.4f, 22f, 12f, 22f);
                path.cubicTo(7.6f, 22f, 4f, 18.4f, 4f, 14f);
                path.lineTo(4f, 2f);
            } else {
                path.moveTo(12f, 2f);
                path.lineTo(18.5f, 9.5f);
                path.cubicTo(20.5f, 12f, 20.5f, 16f, 18.2f, 18.8f);
                path.cubicTo(15f, 22.5f, 9f, 22.5f, 5.8f, 18.8f);
                path.cubicTo(3.5f, 16f, 3.5f, 12f, 5.5f, 9.5f);
                path.close();
            }
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class UpdateIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        UpdateIcon(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;
            paint.setStrokeWidth(2.2f * s);
            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f,
                             b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);
            path.reset(); path.moveTo(12f, 3f); path.lineTo(12f, 15f);
            canvas.drawPath(path, paint);
            path.reset(); path.moveTo(6f, 10f); path.lineTo(12f, 16f); path.lineTo(18f, 10f);
            canvas.drawPath(path, paint);
            path.reset(); path.moveTo(4f, 20.5f); path.lineTo(20f, 20.5f);
            canvas.drawPath(path, paint);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static class LockIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        LockIcon(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;
            paint.setStrokeWidth(2.2f * s);
            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f,
                             b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);
            path.reset();
            rect.set(5f, 10.5f, 19f, 21f);
            canvas.drawRoundRect(rect, 2f, 2f, paint);
            path.moveTo(8.5f, 10.5f);
            path.lineTo(8.5f, 7.5f);
            path.cubicTo(8.5f, 5.0f, 10.2f, 3f, 12f, 3f);
            path.cubicTo(13.8f, 3f, 15.5f, 5.0f, 15.5f, 7.5f);
            path.lineTo(15.5f, 10.5f);
            canvas.drawPath(path, paint);
            canvas.drawCircle(12f, 15.5f, 1.2f, paint);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}