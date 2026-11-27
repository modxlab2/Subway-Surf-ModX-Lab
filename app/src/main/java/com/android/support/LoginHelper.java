package com.android.support;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
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
import android.graphics.PorterDuff;
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
import android.view.animation.LinearInterpolator;
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

    // ================= MODX LAB WEBSITE DARK THEME =================
    private static final int COLOR_FIELD_BG    = ModxTheme.INPUT;
    private static final int COLOR_FIELD_BORD  = ModxTheme.LINE_STRONG;
    private static final int COLOR_ACCENT      = ModxTheme.ACCENT;
    private static final int COLOR_ACCENT_HI   = ModxTheme.ACCENT_HOVER;
    private static final int COLOR_ACCENT_DEEP = ModxTheme.ACCENT_PRESS;
    private static final int COLOR_SUCCESS     = ModxTheme.SUCCESS;
    private static final int COLOR_DANGER      = ModxTheme.DANGER;
    private static final int COLOR_WARN        = ModxTheme.WARNING;
    private static final int COLOR_TEXT        = ModxTheme.FG;
    private static final int COLOR_TEXT_MUTED  = ModxTheme.MUTED;
    private static final int COLOR_HINT        = ModxTheme.SUBTLE;
    private static final int COLOR_ON_ACCENT   = ModxTheme.ON_ACCENT;

    private static final int COLOR_DIALOG_TITLE = ModxTheme.FG;
    private static final int COLOR_DIALOG_SUB   = ModxTheme.MUTED;
    private static final int COLOR_DIALOG_SUB2  = ModxTheme.SUBTLE;
    private static final int COLOR_DIALOG_BODY  = ModxTheme.MUTED;
    private static final int COLOR_CTA          = ModxTheme.ACCENT;

    private static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int ICON_USER = 0;
    private static final int ICON_LOCK = 1;

    private final Context ctx;
    private final boolean overlayRequired;
    private final Callback callback;
    private final TextActionModeHost actionModeHost;

    private EditText editUser, editPass;
    private LinearLayout cardView, headerView, diagRowView, cbRowView;
    private LinearLayout userBox, passBox;
    private ImageView userIconView, passIconView, userPasteBtn, passPasteBtn;
    private FrameLayout userChip, passChip;
    private TextView headerTitleView, headerSubtitleView, diagBadgeBtn, selfTestBadgeBtn;
    private CustomCheck rememberCb, showCb;
    private FrameLayout loginBtn;
    private LinearLayout loginBtnContent;
    private ImageView loginBtnSpinner;
    private TextView loginBtnLabel;
    private ObjectAnimator loginSpinnerAnimator;
    private volatile boolean loginButtonBusy = false;
    private LinearLayout statusRow;
    private ImageView statusIcon;
    private TextView statusTxt;
    private float uiScale = 1.0f;
    private Runnable onContentMetricsChanged;
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
        this.tfRegular = ModxTheme.FONT_REGULAR;
        this.tfMedium  = ModxTheme.FONT_MEDIUM;
        this.tfBold    = ModxTheme.FONT_SEMIBOLD;
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

    public void setMenuWindowDragging(boolean dragging) {
        if (actionModeHost != null) {
            if (dragging) {
                actionModeHost.onWindowDragStarted();
            } else {
                actionModeHost.onWindowDragEnded();
            }
        }
    }

    public void repositionTextActionMode() {
        if (actionModeHost != null) {
            actionModeHost.repositionActiveMode();
        }
    }

    public boolean isTouchOnShowCheck(float rawX, float rawY) {
        if (showCb == null || !showCb.isShown()) return false;
        int[] loc = new int[2];
        showCb.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX <= loc[0] + showCb.getWidth()
                && rawY >= loc[1] && rawY <= loc[1] + showCb.getHeight();
    }

    public void setOnContentMetricsChanged(Runnable listener) {
        this.onContentMetricsChanged = listener;
    }

    private void notifyContentMetricsChanged() {
        if (onContentMetricsChanged != null) {
            onContentMetricsChanged.run();
        }
    }

    public void showExternalStatus(final String message, final boolean showExpiredDialog) {
        if (destroyed) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (destroyed) return;
                if (!TextUtils.isEmpty(message)) {
                    setStatus(message, COLOR_DANGER,
                            ModxIcons.alertCircle(COLOR_DANGER, sdp(15)));
                }
                if (showExpiredDialog) {
                    showKeyExpiredDialog();
                }
            }
        });
    }

    public void cleanup() {
        destroyed = true;
        onContentMetricsChanged = null;
        if (loginSpinnerAnimator != null) {
            loginSpinnerAnimator.cancel();
            loginSpinnerAnimator = null;
        }
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
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                safeDisplayMetrics(ctx)));
    }

    private int sdp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v * uiScale,
                safeDisplayMetrics(ctx)));
    }

    public View buildView() {
        ModFirebase.prewarm(ctx);
        FrameLayout wrapper = new FrameLayout(ctx);
        wrapper.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

        final LinearLayout card = new LinearLayout(ctx);
        cardView = card;
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(8));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundColor(Color.TRANSPARENT);
        card.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
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
        userPasteBtn = makePasteButton(editUser);
        userBox.addView(userPasteBtn);

        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(44));
        uLp.setMargins(0, 0, 0, dp(8));
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
        passPasteBtn = makePasteButton(editPass);
        passBox.addView(passPasteBtn);

        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(44));
        pLp.setMargins(0, 0, 0, dp(6));
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
        cbRowView = cbRow;
        cbRow.setOrientation(LinearLayout.HORIZONTAL);
        cbRow.setGravity(Gravity.CENTER_VERTICAL);
        cbRow.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        cbRow.setPadding(0, 0, 0, 0);

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
                    SecureTokenStore.clearLastUser(ctx);
                }
            }
        });

        cbRow.addView(rememberCb);
        cbRow.addView(showCb);
        card.addView(cbRow);

        loginBtn = new FrameLayout(ctx);
        loginBtn.setClickable(true);
        loginBtn.setFocusable(true);
        loginBtn.setContentDescription("Sign in");
        loginBtn.setBackground(ModxTheme.loginPrimaryButtonBg(ctx, COLOR_ACCENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            loginBtn.setElevation(0f);
        }
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(42));
        bLp.setMargins(0, dp(6), 0, 0);
        loginBtn.setLayoutParams(bLp);

        loginBtnContent = new LinearLayout(ctx);
        loginBtnContent.setOrientation(LinearLayout.HORIZONTAL);
        loginBtnContent.setGravity(Gravity.CENTER);
        loginBtnContent.setDuplicateParentStateEnabled(true);

        loginBtnSpinner = new ImageView(ctx);
        loginBtnSpinner.setImageDrawable(ModxIcons.loader(COLOR_ON_ACCENT, dp(15)));
        loginBtnSpinner.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lspLp = new LinearLayout.LayoutParams(dp(15), dp(15));
        lspLp.setMargins(0, 0, dp(7), 0);
        loginBtnSpinner.setLayoutParams(lspLp);
        loginBtnSpinner.setVisibility(View.GONE);
        loginBtnContent.addView(loginBtnSpinner);

        loginBtnLabel = new TextView(ctx);
        loginBtnLabel.setText("SIGN IN");
        loginBtnLabel.setTextColor(COLOR_ON_ACCENT);
        loginBtnLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
        loginBtnLabel.setTypeface(tfBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            loginBtnLabel.setLetterSpacing(0.04f);
        }
        loginBtnLabel.setIncludeFontPadding(false);
        loginBtnLabel.setGravity(Gravity.CENTER);
        loginBtnLabel.setSingleLine(true);
        loginBtnLabel.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        loginBtnContent.addView(loginBtnLabel);

        loginBtn.addView(loginBtnContent,
                new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER));
        card.addView(loginBtn);

        statusRow = new LinearLayout(ctx);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER);
        statusRow.setBackgroundColor(Color.TRANSPARENT);
        statusRow.setMinimumHeight(0);
        statusRow.setAlpha(0f);
        statusRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        srLp.setMargins(0, dp(6), 0, 0);
        statusRow.setLayoutParams(srLp);

        statusIcon = new ImageView(ctx);
        statusIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams siLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        siLp.gravity = Gravity.CENTER_VERTICAL;
        siLp.setMargins(0, 0, dp(6), 0);
        statusIcon.setLayoutParams(siLp);
        statusIcon.setVisibility(View.GONE);
        statusRow.addView(statusIcon);

        statusTxt = new TextView(ctx);
        statusTxt.setText("");
        statusTxt.setTextColor(COLOR_TEXT_MUTED);
        statusTxt.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
        statusTxt.setTypeface(tfMedium);
        statusTxt.setGravity(Gravity.CENTER);
        statusTxt.setMaxLines(2);
        statusTxt.setEllipsize(TextUtils.TruncateAt.END);
        statusTxt.setIncludeFontPadding(false);
        statusTxt.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        statusRow.addView(statusTxt);
        card.addView(statusRow);

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loginButtonBusy || loginInProgress || savedCheckInProgress) return;
                ModxTheme.hapticTap(v);
                v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(90)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (loginBtn != null) {
                                    loginBtn.animate().scaleX(1f).scaleY(1f)
                                            .setDuration(220)
                                            .setInterpolator(new OvershootInterpolator(1.2f))
                                            .start();
                                }
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
            SecureTokenStore.saveLastUser(ctx, tokenUser);
            editUser.setText(tokenUser);
            rememberCb.setChecked(true);
            final String devId = getOrCreateDeviceId();
            startSavedSessionCheck(savedToken, devId);
        } else {
            String lastUser = SecureTokenStore.loadLastUser(ctx);
            if (!TextUtils.isEmpty(lastUser)) {
                editUser.setText(lastUser);
                rememberCb.setChecked(true);
            }
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
        headerView = header;
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        hLp.setMargins(0, 0, 0, dp(BuildConfig.SEC_DIAG ? 8 : 10));
        header.setLayoutParams(hLp);

        TextView title = new TextView(ctx);
        headerTitleView = title;
        title.setText("Welcome Back");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f);
        title.setTypeface(tfBold);
        title.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        }
        title.setIncludeFontPadding(false);
        title.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
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
        headerSubtitleView = subtitle;
        subtitle.setText("Sign in to continue to your account");
        subtitle.setTextColor(COLOR_TEXT_MUTED);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        subtitle.setTypeface(tfRegular);
        subtitle.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            subtitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        }
        subtitle.setIncludeFontPadding(false);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        sLp.setMargins(0, dp(3), 0, 0);
        subtitle.setLayoutParams(sLp);
        header.addView(subtitle);

        if (BuildConfig.SEC_DIAG) {
            LinearLayout diagRow = new LinearLayout(ctx);
            diagRowView = diagRow;
            diagRow.setOrientation(LinearLayout.HORIZONTAL);
            diagRow.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            rLp.setMargins(0, dp(8), 0, 0);
            diagRow.setLayoutParams(rLp);

            final TextView diagBtn = new TextView(ctx);
            diagBadgeBtn = diagBtn;
            diagBtn.setText("DIAG LOGS [" + TextActionModeHost.getDiagModeLabel() + "]");
            diagBtn.setTextColor(COLOR_ACCENT);
            diagBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f);
            diagBtn.setTypeface(tfBold);
            diagBtn.setGravity(Gravity.CENTER);
            diagBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
            diagBtn.setBackground(ModxTheme.pillBadgeBg(
                    ctx, ModxTheme.ACCENT_SOFT, ModxTheme.ACCENT_LINE));
            diagBtn.setClickable(true);
            diagBtn.setFocusable(true);
            final Handler tapHandler = new Handler(Looper.getMainLooper());
            final int[] tapCount = new int[]{ 0 };
            final Runnable openLogsRunnable = new Runnable() {
                @Override
                public void run() {
                    tapCount[0] = 0;
                    DiagLogViewer.show(ctx, overlayRequired);
                }
            };
            diagBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tapHandler.removeCallbacks(openLogsRunnable);
                    tapCount[0]++;
                    if (tapCount[0] >= 3) {
                        tapCount[0] = 0;
                        TextActionModeHost.cycleDiagMode();
                        if (actionModeHost != null) {
                            actionModeHost.finishActiveMode("diag-mode-switch", false);
                            actionModeHost.prepare();
                        }
                        String modeLabel = TextActionModeHost.getDiagModeLabel();
                        diagBtn.setText("DIAG LOGS [" + modeLabel + "]");
                        setStatus("Toolbar mode: " + modeLabel, COLOR_TEXT_MUTED,
                                ModxIcons.info(COLOR_TEXT_MUTED, sdp(15)));
                        SecurityNative.diagUi("diag toolbar mode switched -> " + modeLabel);
                    } else {
                        tapHandler.postDelayed(openLogsRunnable, 340L);
                    }
                }
            });
            diagRow.addView(diagBtn);

            TextView selfTestBtn = new TextView(ctx);
            selfTestBadgeBtn = selfTestBtn;
            selfTestBtn.setText("SELF TEST");
            selfTestBtn.setTextColor(ModxTheme.INFO);
            selfTestBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f);
            selfTestBtn.setTypeface(tfBold);
            selfTestBtn.setGravity(Gravity.CENTER);
            selfTestBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
            selfTestBtn.setBackground(ModxTheme.pillBadgeBg(
                    ctx, ModxTheme.badgeBg(ModxTheme.INFO), ModxTheme.badgeBorder(ModxTheme.INFO)));
            LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            stLp.setMargins(dp(6), 0, 0, 0);
            selfTestBtn.setLayoutParams(stLp);
            selfTestBtn.setClickable(true);
            selfTestBtn.setFocusable(true);
            selfTestBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ModxTheme.hapticTap(v);
                    DiagSelfTestRunner.runAndShow(ctx, overlayRequired);
                }
            });
            diagRow.addView(selfTestBtn);

            header.addView(diagRow);
        }

        return header;
    }

    private LinearLayout makeFieldContainer() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(ModxTheme.fieldBoxBg(ctx, COLOR_ACCENT, false));
        box.setClipToPadding(false);
        return box;
    }

    private FrameLayout makeIconChip(int iconType) {
        FrameLayout chip = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
        lp.setMargins(dp(10), 0, dp(4), 0);
        chip.setLayoutParams(lp);
        chip.setBackgroundColor(Color.TRANSPARENT);

        ImageView iv = new ImageView(ctx);
        int sizePx = dp(18);
        iv.setImageDrawable(iconType == ICON_USER
                ? ModxIcons.user(COLOR_TEXT_MUTED, sizePx)
                : ModxIcons.lock(COLOR_TEXT_MUTED, sizePx));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER);
        iv.setLayoutParams(ivLp);
        chip.addView(iv);
        return chip;
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
        private final int touchSlop;
        private int touchStartScrollX = 0;

        HostAwareEditText(Context context,
                          String fieldTag,
                          TextActionModeHost host,
                          boolean isPasswordField) {
            super(context);
            this.fieldTag = fieldTag;
            this.host = host;
            this.isPasswordField = isPasswordField;
            this.touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
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
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                touchStartScrollX = getScrollX();
            }
            boolean handled = super.onTouchEvent(event);
            if (action == MotionEvent.ACTION_MOVE && host != null) {
                if (Math.abs(getScrollX() - touchStartScrollX) > Math.max(2, touchSlop / 2)) {
                    host.onOwnerUserScrolled(this);
                }
            }
            return handled;
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
            if (!hasWindowFocus && host != null) {
                host.onOwnerWindowFocusLost(this);
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
                    host.finishActiveMode("window-invisible", false);
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
                host.onOwnerTextChanged(this);
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
                copy.setTintMode(PorterDuff.Mode.SRC_IN);
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
        final SvgIcon pasteIcon = ModxIcons.clipboardPaste(COLOR_TEXT_MUTED, dp(18));
        btn.setImageDrawable(pasteIcon);
        btn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // 40x44dp touch target; padding leaves crisp 20x20dp icon area
        btn.setPadding(dp(10), dp(12), dp(10), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(44));
        lp.setMargins(0, 0, dp(4), 0);
        btn.setLayoutParams(lp);
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setContentDescription("Paste");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(ModxTheme.dpf(ctx, ModxTheme.RADIUS_DP));
            mask.setColor(ModxTheme.TOOLBAR_RIPPLE_MASK);
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(withAlpha(COLOR_ACCENT, 0x1F)), null, mask);
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
                ModxTheme.hapticTap(v);
                pasteFromClipboard(target);
                v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                btn.animate().scaleX(1f).scaleY(1f).setDuration(160)
                                        .setInterpolator(new OvershootInterpolator(1.8f)).start();
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

    public void applyScale(float rawScale) {
        if (destroyed || cardView == null) return;
        float s = Math.max(0.72f, Math.min(1.15f, rawScale));
        if (Math.abs(s - uiScale) < 0.005f) return;
        uiScale = s;

        cardView.setPadding(sdp(16), sdp(10), sdp(16), sdp(8));

        if (headerView != null && headerView.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams hLp = (LinearLayout.LayoutParams) headerView.getLayoutParams();
            hLp.bottomMargin = sdp(BuildConfig.SEC_DIAG ? 8 : 10);
            headerView.setLayoutParams(hLp);
        }
        if (headerTitleView != null) {
            headerTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f * s);
        }
        if (headerSubtitleView != null) {
            headerSubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(9.5f, 12f * s));
            if (headerSubtitleView.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams sLp = (LinearLayout.LayoutParams) headerSubtitleView.getLayoutParams();
                sLp.topMargin = sdp(3);
                headerSubtitleView.setLayoutParams(sLp);
            }
        }
        if (diagRowView != null && diagRowView.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams rLp = (LinearLayout.LayoutParams) diagRowView.getLayoutParams();
            rLp.topMargin = sdp(8);
            diagRowView.setLayoutParams(rLp);
        }
        if (diagBadgeBtn != null) {
            diagBadgeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f * s);
            diagBadgeBtn.setPadding(sdp(10), sdp(4), sdp(10), sdp(4));
        }
        if (selfTestBadgeBtn != null) {
            selfTestBadgeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f * s);
            selfTestBadgeBtn.setPadding(sdp(10), sdp(4), sdp(10), sdp(4));
            if (selfTestBadgeBtn.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams stLp = (LinearLayout.LayoutParams) selfTestBadgeBtn.getLayoutParams();
                stLp.leftMargin = sdp(6);
                selfTestBadgeBtn.setLayoutParams(stLp);
            }
        }

        if (userBox != null && userBox.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams uLp = (LinearLayout.LayoutParams) userBox.getLayoutParams();
            uLp.height = sdp(44);
            uLp.bottomMargin = sdp(8);
            userBox.setLayoutParams(uLp);
        }
        if (passBox != null && passBox.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams pLp = (LinearLayout.LayoutParams) passBox.getLayoutParams();
            pLp.height = sdp(44);
            pLp.bottomMargin = sdp(6);
            passBox.setLayoutParams(pLp);
        }

        scaleChip(userChip, userIconView);
        scaleChip(passChip, passIconView);
        scaleInputField(editUser);
        scaleInputField(editPass);
        scalePasteBtn(userPasteBtn);
        scalePasteBtn(passPasteBtn);

        if (cbRowView != null) {
            cbRowView.setPadding(0, 0, 0, 0);
        }
        if (rememberCb != null) rememberCb.applyScale(s);
        if (showCb != null) showCb.applyScale(s);

        if (loginBtn != null && loginBtn.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams bLp = (LinearLayout.LayoutParams) loginBtn.getLayoutParams();
            bLp.height = sdp(42);
            bLp.topMargin = sdp(6);
            loginBtn.setLayoutParams(bLp);
        }
        if (loginBtnSpinner != null && loginBtnSpinner.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams spLp = (LinearLayout.LayoutParams) loginBtnSpinner.getLayoutParams();
            int sz = sdp(15);
            spLp.width = sz;
            spLp.height = sz;
            spLp.rightMargin = sdp(7);
            loginBtnSpinner.setLayoutParams(spLp);
        }
        if (loginBtnLabel != null) {
            loginBtnLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(11f, 13f * s));
        }

        if (statusRow != null) {
            statusRow.setMinimumHeight(0);
            if (statusRow.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams srLp = (LinearLayout.LayoutParams) statusRow.getLayoutParams();
                srLp.topMargin = sdp(6);
                statusRow.setLayoutParams(srLp);
            }
        }
        if (statusIcon != null && statusIcon.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams siLp = (LinearLayout.LayoutParams) statusIcon.getLayoutParams();
            int sz = sdp(14);
            siLp.width = sz;
            siLp.height = sz;
            siLp.rightMargin = sdp(6);
            statusIcon.setLayoutParams(siLp);
        }
        if (statusTxt != null) {
            statusTxt.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(9.5f, 11f * s));
        }
    }

    private void scaleChip(FrameLayout chip, ImageView iv) {
        if (chip != null && chip.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) chip.getLayoutParams();
            int boxSz = sdp(28);
            lp.width = boxSz;
            lp.height = boxSz;
            lp.leftMargin = sdp(10);
            lp.rightMargin = sdp(4);
            chip.setLayoutParams(lp);
        }
        if (iv != null && iv.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams ilp = (FrameLayout.LayoutParams) iv.getLayoutParams();
            int sz = sdp(17);
            ilp.width = sz;
            ilp.height = sz;
            iv.setLayoutParams(ilp);
        }
    }

    private void scaleInputField(EditText et) {
        if (et == null) return;
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(10.5f, 13f * uiScale));
        et.setPadding(sdp(8), 0, sdp(4), 0);
    }

    private void scalePasteBtn(ImageView btn) {
        if (btn == null) return;
        btn.setPadding(sdp(10), sdp(12), sdp(10), sdp(12));
        if (btn.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) btn.getLayoutParams();
            lp.width = sdp(38);
            lp.height = sdp(44);
            lp.rightMargin = sdp(4);
            btn.setLayoutParams(lp);
        }
    }

    private void setLoginButtonLoading(boolean loading) {
        loginButtonBusy = loading;
        if (loginBtn == null) return;
        loginBtn.setAlpha(1f);
        loginBtn.setEnabled(true);
        loginBtn.setClickable(!loading);
        if (loginBtnLabel != null) {
            loginBtnLabel.setText(loading ? "Signing in..." : "SIGN IN");
        }
        if (loginBtnSpinner != null) {
            if (loading) {
                loginBtnSpinner.setVisibility(View.VISIBLE);
                if (loginSpinnerAnimator == null) {
                    loginSpinnerAnimator = ObjectAnimator.ofFloat(loginBtnSpinner, "rotation", 0f, 360f);
                    loginSpinnerAnimator.setDuration(750L);
                    loginSpinnerAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                    loginSpinnerAnimator.setInterpolator(new LinearInterpolator());
                }
                if (!loginSpinnerAnimator.isStarted()) {
                    loginSpinnerAnimator.start();
                }
            } else {
                if (loginSpinnerAnimator != null) {
                    loginSpinnerAnimator.cancel();
                }
                loginBtnSpinner.setRotation(0f);
                loginBtnSpinner.setVisibility(View.GONE);
            }
        }
    }

    private void setStatus(final String msg, final int color) {
        setStatus(msg, color, null);
    }

    private void setStatus(final String msg, final int color, final Drawable icon) {
        if (statusRow == null || statusTxt == null) return;
        Runnable update = new Runnable() {
            @Override
            public void run() {
                if (destroyed || statusRow == null || statusTxt == null) return;
                statusRow.animate().cancel();
                if (TextUtils.isEmpty(msg)) {
                    statusRow.setVisibility(View.GONE);
                    statusRow.setAlpha(0f);
                    statusTxt.setText("");
                    if (statusIcon != null) {
                        statusIcon.setImageDrawable(null);
                        statusIcon.setVisibility(View.GONE);
                    }
                    notifyContentMetricsChanged();
                    return;
                }
                boolean wasHidden = (statusRow.getVisibility() != View.VISIBLE || statusRow.getAlpha() < 0.05f);
                statusRow.setVisibility(View.VISIBLE);
                statusTxt.setText(msg);
                statusTxt.setTextColor(color);
                if (statusIcon != null) {
                    if (icon != null) {
                        statusIcon.setImageDrawable(icon);
                        statusIcon.setVisibility(View.VISIBLE);
                    } else {
                        statusIcon.setImageDrawable(null);
                        statusIcon.setVisibility(View.GONE);
                    }
                }
                if (wasHidden) {
                    statusRow.setAlpha(0f);
                    statusRow.animate().alpha(1f).setDuration(200).start();
                } else {
                    statusRow.setAlpha(0.45f);
                    statusRow.animate().alpha(1f).setDuration(150).start();
                }
                notifyContentMetricsChanged();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update.run();
        } else {
            mainHandler.post(update);
        }
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
        int iconPx = sdp(15);
        if ("expired".equals(reason)) {
            setStatus("Key expired", COLOR_DANGER, ModxIcons.lock(COLOR_DANGER, iconPx));
            showKeyExpiredDialog();
        } else if ("blocked".equals(reason)) {
            setStatus("Account blocked", COLOR_DANGER, ModxIcons.lock(COLOR_DANGER, iconPx));
            showKeyExpiredDialog();
        } else if ("device_mismatch".equals(reason)) {
            setStatus("Registered to another device", COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
        } else if ("user_not_found".equals(reason)) {
            setStatus("Account no longer exists", COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
        } else if ("rate_limited".equals(reason)) {
            if (retryAfterSec >= 60) {
                int mins = (retryAfterSec + 59) / 60;
                setStatus("Too many attempts. Try again in " + mins + " min",
                        COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
            } else if (retryAfterSec > 0) {
                setStatus("Too many attempts. Try again in " + retryAfterSec + "s",
                        COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
            } else {
                setStatus("Too many attempts. Try again later",
                        COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
            }
        } else if ("no_match".equals(reason)
                || "invalid_credentials".equals(reason)) {
            setStatus("Invalid username or password", COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
        } else if ("session_invalid".equals(reason)) {
            setStatus(forSavedSession ? "Saved login expired or invalid" : "Invalid server signature",
                    COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
        } else if ("no_permission".equals(reason)) {
            setStatus("Missing INTERNET permission", COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
        } else if ("tls_pin".equals(reason)) {
            setStatus("SSL pin mismatch", COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
        } else if ("tls_error".equals(reason)) {
            setStatus("SSL handshake error", COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
        } else if ("native_config".equals(reason)) {
            setStatus("Native config error", COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
        } else if (forSavedSession) {
            setStatus("No internet. Connect to the internet to use your saved login.",
                    COLOR_WARN, ModxIcons.wifiOff(COLOR_WARN, iconPx));
        } else if ("no_internet".equals(reason)) {
            setStatus("No internet / DNS blocked", COLOR_WARN, ModxIcons.wifiOff(COLOR_WARN, iconPx));
        } else if ("connect_error".equals(reason)) {
            setStatus("Server unreachable", COLOR_WARN, ModxIcons.wifiOff(COLOR_WARN, iconPx));
        } else if ("timeout".equals(reason)) {
            setStatus("Connection timed out", COLOR_WARN, ModxIcons.wifiOff(COLOR_WARN, iconPx));
        } else if (reason != null && reason.startsWith("http_")) {
            setStatus("Server error (" + reason.toUpperCase() + ")",
                    COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
        } else if ("network".equals(reason)) {
            setStatus("Connection failed", COLOR_WARN, ModxIcons.wifiOff(COLOR_WARN, iconPx));
        } else {
            setStatus("Login failed (" + reason + ")", COLOR_DANGER, ModxIcons.alertCircle(COLOR_DANGER, iconPx));
        }
    }

    private void startSavedSessionCheck(final String savedToken, final String deviceId) {
        if (destroyed || loginInProgress || savedCheckInProgress) return;
        if (TextUtils.isEmpty(savedToken) || TextUtils.isEmpty(deviceId)) return;

        savedCheckInProgress = true;
        cancelSavedSessionRetry();

        setLoginButtonLoading(true);
        setStatus("Checking saved login...", COLOR_TEXT_MUTED, ModxIcons.loader(COLOR_TEXT_MUTED, sdp(15)));

        new Thread(new Runnable() {
            @Override
            public void run() {
                final long tStart = android.os.SystemClock.elapsedRealtime();
                boolean ok = false;
                String reason = "network";
                String freshToken = "", userOut = "", respNonce = "";
                int retryAfterSec = 0;

                if (!SecurityNative.verifyBeforeLogin(ctx)) {
                    reason = "native_config";
                } else {
                    final String sentNonce = ModFirebase.generateNonce();
                    final JSONObject r = ModFirebase.verifySession(ctx, savedToken, deviceId, sentNonce);

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

                    if (BuildConfig.SEC_DIAG) {
                        long elapsed = android.os.SystemClock.elapsedRealtime() - tStart;
                        SecurityNative.diag(302, "saved_session ok=" + ok + " reason=" + reason + " ms=" + elapsed);
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
                                if (!loginInProgress) {
                                    setLoginButtonLoading(false);
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
                        SecureTokenStore.saveLastUser(ctx, userOut);
                    } else {
                        SecureTokenStore.clearToken(ctx);
                        SecureTokenStore.clearLastUser(ctx);
                    }

                    final String fToken = freshToken;
                    final String fUser = userOut;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            savedCheckInProgress = false;
                            if (destroyed || loginInProgress) return;
                            offlineRetryCount = 0;
                            setLoginButtonLoading(false);
                            setStatus("Welcome back!", COLOR_SUCCESS, ModxIcons.checkCircle(COLOR_SUCCESS, sdp(15)));
                            checkUpdateAfterLogin(fToken, fUser, deviceId, sentNonce);
                        }
                    });
                    return;
                }

                final String fFailReason = reason;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        savedCheckInProgress = false;
                        if (destroyed) return;
                        if (!loginInProgress) {
                            setLoginButtonLoading(false);
                        }
                        applyLoginFailureStatus(fFailReason, 0, true);
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
            setStatus("Please fill in all fields", COLOR_WARN, ModxIcons.alertCircle(COLOR_WARN, sdp(15)));
            return;
        }

        cancelSavedSessionRetry();
        loginInProgress = true;
        setLoginButtonLoading(true);
        setStatus("Verifying credentials...", COLOR_TEXT_MUTED, ModxIcons.loader(COLOR_TEXT_MUTED, sdp(15)));

        final boolean shouldRemember = (rememberCb != null && rememberCb.isChecked());
        if (!shouldRemember) {
            SecureTokenStore.clearToken(ctx);
            SecureTokenStore.clearLastUser(ctx);
        }

        final String deviceId = getOrCreateDeviceId();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final long tStart = android.os.SystemClock.elapsedRealtime();
                boolean ok = false;
                String reason = "network";
                String token = "", userOut = "", respNonce = "";
                int retryAfterSec = 0;

                if (!SecurityNative.verifyBeforeLogin(ctx)) {
                    reason = "native_config";
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            loginInProgress = false;
                            if (destroyed) return;
                            setLoginButtonLoading(false);
                            applyLoginFailureStatus("native_config", 0, false);
                        }
                    });
                    return;
                }

                final Thread updPrefetch = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ModFirebase.fetchUpdate(false);
                    }
                });
                updPrefetch.setDaemon(true);
                updPrefetch.start();

                final String sentNonce = ModFirebase.generateNonce();
                final JSONObject r = ModFirebase.login(ctx, inputUser, inputPass, deviceId, sentNonce);

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

                if (BuildConfig.SEC_DIAG) {
                    long elapsed = android.os.SystemClock.elapsedRealtime() - tStart;
                    SecurityNative.diag(302, "manual_login ok=" + ok + " reason=" + reason + " ms=" + elapsed);
                }

                if (!ok) {
                    final String fReason = reason;
                    final int fRetry = retryAfterSec;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            loginInProgress = false;
                            if (destroyed) return;
                            setLoginButtonLoading(false);
                            applyLoginFailureStatus(fReason, fRetry, false);
                        }
                    });
                    return;
                }

                if (shouldRemember) {
                    SecureTokenStore.saveToken(ctx, token);
                    SecureTokenStore.saveLastUser(ctx, userOut);
                } else {
                    SecureTokenStore.clearToken(ctx);
                    SecureTokenStore.clearLastUser(ctx);
                }

                final String fToken = token;
                final String fUser = userOut;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loginInProgress = false;
                        if (destroyed) return;
                        setLoginButtonLoading(false);
                        setStatus("Welcome back!", COLOR_SUCCESS, ModxIcons.checkCircle(COLOR_SUCCESS, sdp(15)));
                        Toast.makeText(ctx, "Login Success", Toast.LENGTH_SHORT).show();
                        checkUpdateAfterLogin(fToken, fUser, deviceId, sentNonce);
                    }
                });
            }
        }).start();
    }

    private static String sanitizeUpdateUrl(String rawUrl) {
        final String fallback = "https://t.me/kayesahmmedpro";
        if (TextUtils.isEmpty(rawUrl)) return fallback;
        String trimmed = rawUrl.trim();
        if (trimmed.length() > 512) return fallback;
        try {
            Uri uri = Uri.parse(trimmed);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return fallback;
            if (!TextUtils.isEmpty(uri.getUserInfo())) return fallback;
            String host = uri.getHost();
            if (TextUtils.isEmpty(host)) return fallback;
            String h = host.toLowerCase(java.util.Locale.US);
            boolean allowed = "t.me".equals(h)
                    || "telegram.me".equals(h)
                    || "telegram.dog".equals(h)
                    || "github.com".equals(h)
                    || "objects.githubusercontent.com".equals(h)
                    || "raw.githubusercontent.com".equals(h)
                    || "modxlab.com".equals(h)
                    || h.endsWith(".modxlab.com");
            return allowed ? uri.toString() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
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
                    String safeUrl = sanitizeUpdateUrl(up.optString("url", ""));
                    String current = getVersionName();
                    if (!TextUtils.isEmpty(latest) && !current.equals(latest)) {
                        final String fv = latest, fm = msg, fu = safeUrl;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() { showUpdateDialog(fv, fm, fu, token, user, deviceId, nonce); }
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
        card.setBackground(ModxTheme.dialogCardBg(ctx));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(8));
        }
        return card;
    }

    private FrameLayout newDialogIconHolder(int mainColor, Drawable glyph) {
        FrameLayout iconHolder = new FrameLayout(ctx);
        int size = dp(44);
        LinearLayout.LayoutParams ihLp = new LinearLayout.LayoutParams(size, size);
        ihLp.setMargins(0, 0, 0, dp(12));
        iconHolder.setLayoutParams(ihLp);
        boolean isDangerTone = (mainColor == COLOR_DANGER);
        iconHolder.setBackground(ModxTheme.pillBadgeBg(
                ctx,
                isDangerTone ? ModxTheme.RAISED : ModxTheme.badgeBg(mainColor),
                isDangerTone ? ModxTheme.LINE_STRONG : ModxTheme.badgeBorder(mainColor)));

        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(glyph);
        iconHolder.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
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
            btn.setLetterSpacing(0.04f);
            btn.setStateListAnimator(null);
            btn.setElevation(0f);
        }
        btn.setBackground(ModxTheme.primaryButtonBg(ctx, COLOR_CTA));
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
        btn.setTextColor(COLOR_TEXT);
        btn.setTextSize(12f);
        btn.setTypeface(tfMedium);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btn.setStateListAnimator(null);
            btn.setElevation(0f);
        }
        btn.setBackground(ModxTheme.secondaryButtonBg(ctx));
        btn.setMinHeight(0); btn.setMinimumHeight(0);
        btn.setMinWidth(0);  btn.setMinimumWidth(0);
        btn.setPadding(dp(18), 0, dp(18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(42));
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
                                  final String updateUrl,
                                  final String token, final String user,
                                  final String deviceId, final String nonce) {
        dismissTextActionMode();
        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];
        final LinearLayout card = newDialogCard();
        card.addView(newDialogIconHolder(COLOR_CTA, ModxIcons.download(COLOR_CTA, dp(22))));

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

        final String safeTarget = sanitizeUpdateUrl(updateUrl);
        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(safeTarget));
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
        card.addView(newDialogIconHolder(COLOR_DANGER, ModxIcons.lock(COLOR_DANGER, dp(22))));

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
        private final FrameLayout box;
        private final GradientDrawable boxBg;
        private final View checkmark;
        private final TextView labelView;
        private boolean checked;
        private CheckListener listener;

        CustomCheck(String label, boolean initial) {
            super(ctx);
            this.checked = initial;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(36));
            setPadding(0, dp(4), 0, dp(4));

            box = new FrameLayout(ctx);
            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(dp(17), dp(17));
            boxLp.setMargins(0, 0, dp(8), 0);
            box.setLayoutParams(boxLp);

            boxBg = new GradientDrawable();
            boxBg.setCornerRadius(dp(4));
            boxBg.setStroke(dp(1.5f), initial ? COLOR_ACCENT : COLOR_FIELD_BORD);
            boxBg.setColor(initial ? withAlpha(COLOR_ACCENT, 0x22) : ModxTheme.INPUT);
            box.setBackground(boxBg);

            checkmark = new View(ctx);
            FrameLayout.LayoutParams cmLp = new FrameLayout.LayoutParams(dp(9), dp(9), Gravity.CENTER);
            checkmark.setLayoutParams(cmLp);
            GradientDrawable cmBg = new GradientDrawable();
            cmBg.setColor(COLOR_ACCENT);
            cmBg.setCornerRadius(dp(2));
            checkmark.setBackground(cmBg);
            checkmark.setVisibility(initial ? View.VISIBLE : View.GONE);
            box.addView(checkmark);

            labelView = new TextView(ctx);
            labelView.setText(label);
            labelView.setTextColor(initial ? COLOR_TEXT : COLOR_TEXT_MUTED);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
            labelView.setTypeface(tfMedium);
            labelView.setIncludeFontPadding(false);

            addView(box);
            addView(labelView);

            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    ModxTheme.hapticTap(v);
                    toggle();
                }
            });
        }

        void applyScale(float s) {
            setMinimumHeight(dp(36f * s));
            setPadding(0, dp(4f * s), 0, dp(4f * s));
            if (box.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams bLp = (LinearLayout.LayoutParams) box.getLayoutParams();
                int bsz = dp(17f * s);
                bLp.width = bsz;
                bLp.height = bsz;
                bLp.rightMargin = dp(8f * s);
                box.setLayoutParams(bLp);
            }
            if (checkmark.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams cmLp = (FrameLayout.LayoutParams) checkmark.getLayoutParams();
                int csz = dp(9f * s);
                cmLp.width = csz;
                cmLp.height = csz;
                checkmark.setLayoutParams(cmLp);
            }
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(9.5f, 11f * s));
        }

        void setListener(CheckListener l) { this.listener = l; }
        void setChecked(boolean value) { if (this.checked != value) toggle(); }
        boolean isChecked() { return checked; }

        private void toggle() {
            checked = !checked;
            checkmark.setVisibility(checked ? View.VISIBLE : View.GONE);
            boxBg.setStroke(dp(1.5f), checked ? COLOR_ACCENT : COLOR_FIELD_BORD);
            boxBg.setColor(checked ? withAlpha(COLOR_ACCENT, 0x22) : ModxTheme.INPUT);
            labelView.setTextColor(checked ? COLOR_TEXT : COLOR_TEXT_MUTED);
            if (listener != null) listener.onChanged(checked);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return ModxTheme.withAlpha(color, alpha);
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
}