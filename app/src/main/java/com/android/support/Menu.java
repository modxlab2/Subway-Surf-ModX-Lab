//Please don't replace listeners with lambda!

package com.android.support;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.PopupWindow;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewParent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;
import android.os.Looper;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;

public class Menu {

    public static final String TAG = "Mod_Menu";

    // ---- ModX Lab Website Dark Palette Tokens ----
    int COLOR_ACCENT      = ModxTheme.ACCENT;
    int COLOR_ACCENT_2    = ModxTheme.ACCENT_PRESS;
    int COLOR_ACCENT_3    = ModxTheme.ACCENT_HOVER;
    int COLOR_SUCCESS     = ModxTheme.SUCCESS;
    int COLOR_DANGER      = ModxTheme.DANGER;

    int COLOR_BG_TOP      = ModxTheme.PAGE;
    int COLOR_BG_BOTTOM   = ModxTheme.PAGE;
    int COLOR_CARD        = ModxTheme.CARD;
    int COLOR_CARD_HI     = ModxTheme.CARD_HOVER;
    int COLOR_CARD_BORDER = ModxTheme.LINE;
    int COLOR_TRACK       = ModxTheme.RAISED;
    int COLOR_TEXT_MUTED  = ModxTheme.MUTED;
    int BTN_GRAD_1        = ModxTheme.ACCENT;
    int BTN_GRAD_2        = ModxTheme.ACCENT_PRESS;

    // Legacy refs
    int TEXT_COLOR            = ModxTheme.ACCENT;
    int TEXT_COLOR_2          = ModxTheme.FG;
    int BTN_COLOR             = ModxTheme.ACCENT;
    int MENU_BG_COLOR         = ModxTheme.PAGE;
    int MENU_FEATURE_BG_COLOR = ModxTheme.CARD;
    int BORDER_COLOR          = ModxTheme.LINE;

    int ToggleON  = ModxTheme.ACCENT;
    int ToggleOFF = ModxTheme.MUTED;
    int BtnON     = ModxTheme.ACCENT;
    int BtnOFF    = ModxTheme.MUTED;
    int CategoryBG    = ModxTheme.SURFACE;
    int SeekBarColor  = ModxTheme.ACCENT;
    int SeekBarProgressColor = ModxTheme.ACCENT;
    int CheckBoxColor = ModxTheme.ACCENT;
    int RadioColor    = ModxTheme.ACCENT;
    int CollapseColor = ModxTheme.CARD;
    String NumberTxtColor = "#20C997";

    // ---- Default menu / window dimensions ----
    private static final int   POS_X       = 5;
    private static final int   POS_Y       = 100;
    private static final int   MENU_WIDTH  = 272;
    private static final int   MENU_HEIGHT = 352;
    private static final float MENU_CORNER = ModxTheme.RADIUS_DP;
    private static final float ICON_SIZE   = 65f;
    private static final float ICON_ALPHA  = 1f;

    // ---- Crisp card frame ----
    private static final int   BORDER_DP = 1;
    private static final float ICON_CORNER_FRACTION = 0.26f;

    // ---- Responsive / resize settings ----
    private static final boolean SHOW_TAB_ICONS = true;      // false = text-only tabs
    private static final int   MIN_MENU_WIDTH_DP  = 172;
    private static final int   MIN_MENU_HEIGHT_DP = 196;
    private static final int   COMPACT_TABS_BELOW_DP = 205;  // below this width tabs become icon-only
    private static final float MIN_SCALE_FACTOR = 0.70f;
    private static final float MAX_SCALE_FACTOR = 1.50f;

    private View resizeHandle;
    private float currentScale = 1.0f;
    private boolean compactMode = false;
    private int lastSidebarW = -1;

    RelativeLayout mCollapsed, mRootContainer;
    LinearLayout   mExpanded, mCollapse;
    WindowManager             mWindowManager;
    WindowManager.LayoutParams vmParams;
    ImageView  startimage;
    FrameLayout rootFrame;
    FrameLayout menuFrame;

    private LinearLayout sidebarLayout;
    private LinearLayout contentLayout;
    private ScrollView sidebarScroll;
    private ScrollView contentScrollView;
    private HashMap<String, LinearLayout> categoryViewsMap;
    private ArrayList<String> categoryNames;
    private LinearLayout mainContainer;
    
    // Header views — hidden on login screen, shown after login
    private View mHeaderView = null;
    private View mShimmerView = null;
    private TextView mTitleView = null;
    private View mTitleDot = null;
    private TextView mProView = null;
    private ImageView mSettingsIcon = null;
    private View mShimmerViewRef = null;
    private float mCurrentHueF = 162f;
    private TextView mSubTitleView = null;
    private Button mMinimizeBtn = null;
    private Button mHideBtn = null;
    private View mHideHoldProgress = null;
    private TextView mProBadgeText = null;
    private TextView mMenuToastView = null;
    private Runnable mMenuToastHideRunnable = null;
    private int mColorPickerOriginalColor = 0;

    // ===== Teleport Pad touch overlay =====
    private View tpPadView = null;
    private WindowManager tpPadWindowManager = null;
    private WindowManager.LayoutParams tpPadParams = null;

    public static Menu instance = null;
    public static native boolean IsSmoothTeleportActive();

    private Typeface fontRegular;
    private Typeface fontMedium;
    private Typeface fontBold;

    // base (unscaled) text size + padding for every scaled TextView
    private final WeakHashMap<TextView, float[]> textBase = new WeakHashMap<TextView, float[]>();
    private final ArrayList<TabHolder> tabHolders = new ArrayList<TabHolder>();
    private final ArrayList<ArrayAdapter<String>> spinnerAdapters = new ArrayList<ArrayAdapter<String>>();
    // ================================================================
    // Mode Preset Specification (matches Main.cpp cases 800 / 801 / 802)
    // Edit this single table to customize Simple / Max / Ultra Max.
    // Max is a strict superset of Simple; Ultra Max is a strict superset of Max.
    // ================================================================
    private static final int MODE_SIMPLE = 800;
    private static final int MODE_MAX    = 801;
    private static final int MODE_ULTRA  = 802;
    private static final int MODE_RESET  = 803;

    private static final int PRESET_TOGGLE  = 1;
    private static final int PRESET_SEEKBAR = 2;
    private static final int PRESET_SPINNER = 3;
    private static final int PRESET_COLOR   = 4;

    private static final class PresetItem {
        final int featNum;
        final int type;
        final int onValue;
        final int offValue;

        PresetItem(int featNum, int type, int onValue, int offValue) {
            this.featNum  = featNum;
            this.type     = type;
            this.onValue  = onValue;
            this.offValue = offValue;
        }
    }

    private static final PresetItem[] SIMPLE_PRESET_ITEMS = new PresetItem[] {
        new PresetItem(104, PRESET_TOGGLE,  1,    0), // Auto Revive
        new PresetItem(110, PRESET_TOGGLE,  1,    0), // Unlimited Coins
        new PresetItem(150, PRESET_TOGGLE,  1,    0), // Free Shipping
        new PresetItem(103, PRESET_SEEKBAR, 5,    0), // Jump Height (displays 35)
        new PresetItem(151, PRESET_SEEKBAR, 9999, 0)  // Extra Coins
    };

    private static final PresetItem[] MAX_EXTRA_ITEMS = new PresetItem[] {
        new PresetItem(108, PRESET_TOGGLE,  1,   0), // God Mode
        new PresetItem(106, PRESET_TOGGLE,  1,   0), // Fast Lane
        new PresetItem(140, PRESET_TOGGLE,  1,   0), // Infinite Powerup
        new PresetItem(102, PRESET_SEEKBAR, 999, 0), // Max Jumps
        new PresetItem(145, PRESET_SEEKBAR, 999, 0)  // Power Duration
    };

    private static final PresetItem[] ULTRA_EXTRA_ITEMS = new PresetItem[] {
        new PresetItem(130, PRESET_TOGGLE,  1,    0), // Low Gravity
        new PresetItem(131, PRESET_TOGGLE,  1,    0), // Wall Hack
        new PresetItem(132, PRESET_TOGGLE,  1,    0), // No Impact
        new PresetItem(142, PRESET_TOGGLE,  1,    0), // Freeze Trains
        new PresetItem(119, PRESET_TOGGLE,  1,    0), // Enable Chams
        new PresetItem(161, PRESET_SPINNER, 1,    0), // Camera Mode -> Follow Camera (1)
        new PresetItem(152, PRESET_SEEKBAR, 9999, 0), // Extra Keys
        new PresetItem(153, PRESET_SEEKBAR, 9999, 0)  // Extra Score
    };

    private static PresetItem[] concatPresetItems(PresetItem[]... groups) {
        int total = 0;
        for (PresetItem[] g : groups) total += g.length;
        PresetItem[] out = new PresetItem[total];
        int idx = 0;
        for (PresetItem[] g : groups) {
            System.arraycopy(g, 0, out, idx, g.length);
            idx += g.length;
        }
        return out;
    }

    private static LinkedHashMap<Integer, PresetItem[]> buildModePresets() {
        LinkedHashMap<Integer, PresetItem[]> map = new LinkedHashMap<Integer, PresetItem[]>();
        map.put(MODE_SIMPLE, SIMPLE_PRESET_ITEMS);
        map.put(MODE_MAX,    concatPresetItems(SIMPLE_PRESET_ITEMS, MAX_EXTRA_ITEMS));
        map.put(MODE_ULTRA,  concatPresetItems(SIMPLE_PRESET_ITEMS, MAX_EXTRA_ITEMS, ULTRA_EXTRA_ITEMS));
        return map;
    }

    private static final LinkedHashMap<Integer, PresetItem[]> MODE_PRESETS = buildModePresets();
    private static final int[] MODE_FEATNUMS = { MODE_SIMPLE, MODE_MAX, MODE_ULTRA };

    private static boolean isResetByNative803(int featNum) {
        switch (featNum) {
            case 102: case 103: case 104: case 106: case 107: case 108:
            case 110: case 119: case 130: case 131: case 132: case 133:
            case 134: case 135: case 136: case 137: case 138: case 140:
            case 141: case 142: case 143: case 144: case 145: case 150:
            case 151: case 152: case 153: case 154: case 160: case 161:
                return true;
            default:
                return false;
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int mProgrammaticDepth = 0;

    private boolean isProgrammaticUpdate() {
        return mProgrammaticDepth > 0;
    }

    private void beginProgrammaticUpdate() {
        mProgrammaticDepth++;
    }

    private void endProgrammaticUpdate() {
        if (mProgrammaticDepth > 0) {
            mProgrammaticDepth--;
        }
    }

    private Runnable showMenuRunnable = null;
    private Runnable subGuardRunnable = null;
    private int subGuardChecks = 0;
    private boolean mSyncingTpMarkers = false;

    // ===== Mode buttons mutual exclusion =====
    private static class ModeButtonHolder {
        int featNum;
        String name;
        LinearLayout row;
        ImageView iconView;
        TextView titleView;
        TextView summaryView;
        TextView badgeView;
        boolean[] state;
    }
    private final HashMap<Integer, ModeButtonHolder> mModeButtons =
            new HashMap<Integer, ModeButtonHolder>();

    private static class OtherButtonOnOffHolder {
        int featNum;
        String name;
        Button button;
        boolean[] state;
        boolean defaultVal;
    }
    private final HashMap<Integer, OtherButtonOnOffHolder> mOtherButtonOnOffs =
            new HashMap<Integer, OtherButtonOnOffHolder>();

    // ===== Toggle tracking (for Mode & Reset sync) =====
    private static class ToggleHolder {
        int featNum;
        String featName;
        ToggleView toggle;
        GradientDrawable rowBg;
        boolean[] state;
        boolean defaultVal;
    }
    private final HashMap<Integer, ToggleHolder> mToggles =
            new HashMap<Integer, ToggleHolder>();

    // ===== SeekBar tracking (for Mode & Reset sync) =====
    private static class SeekBarHolder {
        int featNum;
        String featName;
        SeekBar seekBar;
        TextView chip;
        SeekTrackDrawable trackDrawable;
        GradientDrawable thumbCore;
        int min;
        int max;
        int defaultVal;
    }
    private final HashMap<Integer, SeekBarHolder> mSeekBars =
            new HashMap<Integer, SeekBarHolder>();

    // ===== Spinner tracking (for Mode & Reset sync) =====
    private static class SpinnerHolder {
        int featNum;
        String featName;
        TextView nameView;
        LinearLayout dropdownList;
        SvgIcon chevIcon;
        String[] items;
        int currentIdx;
    }
    private final HashMap<Integer, SpinnerHolder> mSpinners =
            new HashMap<Integer, SpinnerHolder>();

    // ===== CheckBox / Radio / ColorPicker / Input holders (for Reset & Preset sync) =====
    private static class CheckBoxHolder {
        int featNum;
        String featName;
        CheckBox checkBox;
        GradientDrawable bg;
        boolean defaultVal;
    }
    private final HashMap<Integer, CheckBoxHolder> mCheckBoxes =
            new HashMap<Integer, CheckBoxHolder>();

    private static class RadioGroupHolder {
        int featNum;
        String featName;
        RadioGroup radioGroup;
        TextView titleView;
        String selectedName;
        int currentIndex;
    }
    private final HashMap<Integer, RadioGroupHolder> mRadioGroups =
            new HashMap<Integer, RadioGroupHolder>();

    private static class ColorPickerHolder {
        int featNum;
        String featName;
        LinearLayout row;
        View dotView;
        TextView hexView;
        int defaultColor;
        int currentColor;
    }
    private final HashMap<Integer, ColorPickerHolder> mColorPickers =
            new HashMap<Integer, ColorPickerHolder>();

    private static class InputHolder {
        int featNum;
        String featName;
        Button button;
        String currentValueText = "";
    }
    private final HashMap<Integer, InputHolder> mInputNums   = new HashMap<Integer, InputHolder>();
    private final HashMap<Integer, InputHolder> mInputLNums  = new HashMap<Integer, InputHolder>();
    private final HashMap<Integer, InputHolder> mInputTexts  = new HashMap<Integer, InputHolder>();

    private static class CollapseHolder {
        LinearLayout headRow;
        TextView titleView;
        SvgIcon chevIcon;
    }
    private final ArrayList<TextView> mCategoryViews = new ArrayList<TextView>();
    private final ArrayList<View> mCategoryLines = new ArrayList<View>();
    private final ArrayList<CollapseHolder> mCollapseHolders = new ArrayList<CollapseHolder>();
    private final ArrayList<Button> mLinkButtons = new ArrayList<Button>();
    private LinearLayout mMenuColorRow = null;
    private View mMenuColorDot = null;
    private TextView mMenuColorHex = null;
    private View mBottomDivider = null;
    private TeleportPadView mMenuTeleportPad = null;
    private AlertDialog activeColorPickerDialog = null;
    private final ArrayList<AlertDialog> activeDialogs = new ArrayList<AlertDialog>();

    boolean stopChecking, overlayRequired;
    Context getContext;

    private int savedWindowFlags = 0;
private boolean windowIsFocusable = false;
private boolean isLoggedIn = false;
private boolean loginOverflowActive = false;
private boolean loginResizingNow = false;
private int lastLoginViewportH = -1;
private int lastLoginContentH = -1;
private int lastLoginScreenW = -1;
private int lastLoginScreenH = -1;
private int lastLoginVisBottom = -1;
private int lastLoggedLoginDelta = Integer.MIN_VALUE;
private View.OnLayoutChangeListener loginLayoutChangeListener = null;
private android.view.ViewTreeObserver.OnGlobalLayoutListener loginGlobalLayoutListener = null;
private LoginHelper activeLoginHelper = null;
private View sidebarDivider = null;
private int effectivePosY = POS_Y;
private static final long SESSION_REVERIFY_INTERVAL_MS = 10L * 60L * 1000L;
private static final long SESSION_REVERIFY_RETRY_MS = 2L * 60L * 1000L;
private final Handler sessionVerifyHandler = new Handler(android.os.Looper.getMainLooper());
private String activeSessionToken = "";
private String activeSessionUser = "";
private String activeSessionDeviceId = "";
private boolean sessionReverifyInFlight = false;
private Runnable sessionReverifyRunnable = null;
    private static final HashMap<Integer, Integer> SEEK_DEFAULTS = new HashMap<Integer, Integer>();
    // (empty — no defaults needed for current features)
    
    native void Init(Context context, TextView title, TextView subTitle);
    native String Icon();
    native String IconWebViewData();
    native String[] GetFeatureList();
    native String[] SettingsList();
    native boolean IsGameLibLoaded();

    
    public static native void SetTeleportTargetNorm(float nx, float ny);
public static native boolean GetTeleportEnabled();


    public Menu(Context context) {
    instance = this;
        getContext = context;
        Preferences.context = context;
        // ★ SavePref state disk থেকে read করে static-এ রাখি — feature build-এর আগেই
Preferences.init(context);

        
        // ==== Load saved theme color (only persisted when Save is ON) ====
        try {
            int savedAccent = Preferences.getMenuAccentColor();
            if (savedAccent != 0) {
                int safe = ensureReadableAccent(savedAccent);
                COLOR_ACCENT   = safe;
                COLOR_ACCENT_2 = ModxTheme.accentPress(safe);
                COLOR_ACCENT_3 = ModxTheme.accentHover(safe);
                TEXT_COLOR = safe;
                BTN_COLOR  = safe;
                BTN_GRAD_1 = safe;
                BTN_GRAD_2 = COLOR_ACCENT_2;
                SeekBarColor = safe;
                SeekBarProgressColor = safe;
                CheckBoxColor = safe;
                RadioColor    = safe;
                ToggleON      = safe;
                BtnON         = safe;
                NumberTxtColor = ModxTheme.hex6(safe);
            }
            mCurrentHueF = getHueFromColor(COLOR_ACCENT);
        } catch (Exception ignored) { }

        fontRegular = ModxTheme.FONT_REGULAR;
        fontMedium  = ModxTheme.FONT_MEDIUM;
        fontBold    = ModxTheme.FONT_SEMIBOLD;

        rootFrame = new FrameLayout(context);
        mRootContainer = new RelativeLayout(context);
        mCollapsed = new RelativeLayout(context);
        mCollapsed.setVisibility(View.VISIBLE);
        mCollapsed.setAlpha(ICON_ALPHA);

        int orientation = (screenW() > screenH())
                ? Configuration.ORIENTATION_LANDSCAPE
                : Configuration.ORIENTATION_PORTRAIT;

        effectivePosY = POS_Y;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            effectivePosY = 10;
        }

        int initW = Math.min(dp(MENU_WIDTH), Math.max(dp(MIN_MENU_WIDTH_DP), screenW() - dp(10)));
        int initH = Math.min(dp(MENU_HEIGHT), Math.max(dp(MIN_MENU_HEIGHT_DP), screenH() - effectivePosY - dp(8)));

        int loginMinH = dp(340);
        if (initH < loginMinH && screenH() > loginMinH + effectivePosY) {
            initH = loginMinH;
        }

        // ---------------- Frame: crisp card border (ModX Lab #131312 + #31302E) ----------------
        menuFrame = new FrameLayout(context);
        menuFrame.setVisibility(View.GONE);
        menuFrame.setLayoutParams(new RelativeLayout.LayoutParams(initW, initH));
        menuFrame.setClipChildren(false);
        menuFrame.setClipToPadding(false);
        final int inset = dp(BORDER_DP);
        menuFrame.setPadding(inset, inset, inset, inset);
        menuFrame.setBackground(ModxTheme.roundRect(
                context, ModxTheme.PAGE, ModxTheme.LINE, 1f, MENU_CORNER));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            menuFrame.setElevation(dp(2));
        }

        // Re-apply responsive scale whenever the width changes
        menuFrame.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    int w = right - left;
                    if (w > 0 && w != (oldRight - oldLeft)) {
                        menuFrame.post(new Runnable() {
                                @Override
                                public void run() {
                                    applyResponsiveScale(menuFrame.getWidth());
                                }
                            });
                    }
                }
            });

        // ---------------- Menu surface ----------------
        mExpanded = new LinearLayout(context);
        mExpanded.setOrientation(LinearLayout.VERTICAL);
        final float innerRadius = Math.max(0f, dpf(MENU_CORNER) - dp(BORDER_DP));
        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(ModxTheme.PAGE);
        menuBg.setCornerRadius(innerRadius);
        mExpanded.setBackground(menuBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mExpanded.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), innerRadius);
                    }
                });
            mExpanded.setClipToOutline(true);
        }
        menuFrame.addView(mExpanded, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        // ---------------- Collapsed icon (with animated neon ring) ----------------
        startimage = new ImageView(context);
        int iconPx = (int) TypedValue.applyDimension(1, ICON_SIZE, safeDisplayMetrics(context));
        startimage.setLayoutParams(new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
        startimage.setScaleType(ImageView.ScaleType.FIT_XY);
        byte[] decode = Base64.decode(Icon(), 0);
        startimage.setImageBitmap(BitmapFactory.decodeByteArray(decode, 0, decode.length));
        startimage.setOnTouchListener(onTouchListener());

        final String iconWebData = IconWebViewData();
        WebView wView = null;
        ViewOutlineProvider iconOutline = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final float iconRadius = iconPx * ICON_CORNER_FRACTION;
            iconOutline = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), iconRadius);
                }
            };
            startimage.setOutlineProvider(iconOutline);
            startimage.setClipToOutline(true);
        }

        if (iconWebData != null && !iconWebData.isEmpty()) {
            wView = new WebView(context);
            wView.setLayoutParams(new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                wView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                        ViewParent parent = view.getParent();
                        if (parent instanceof ViewGroup) {
                            ((ViewGroup) parent).removeView(view);
                        }
                        view.destroy();
                        return true;
                    }
                });
            }
            wView.loadData("<html><head></head><body style=\"margin:0;padding:0\">" + "<img src=\"" + iconWebData + "\" width=\"" + ICON_SIZE + "\" height=\"" + ICON_SIZE + "\"></body></html>", "text/html", "utf-8");
            wView.setBackgroundColor(0x00000000);
            wView.setAlpha(ICON_ALPHA);
            wView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            wView.setOnTouchListener(onTouchListener());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && iconOutline != null) {
                wView.setOutlineProvider(iconOutline);
                wView.setClipToOutline(true);
            }
        }

        FrameLayout iconHolder = new FrameLayout(context);
        int holderPx = iconPx + dp(22);
        iconHolder.setLayoutParams(new RelativeLayout.LayoutParams(holderPx, holderPx));
        
        iconHolder.setOnTouchListener(onTouchListener());
        mCollapsed.addView(iconHolder);

        // ---------------- Header ----------------
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(ModxTheme.SURFACE);
        header.setPadding(dp(12), dp(9), dp(8), dp(8));
        header.setOnTouchListener(onTouchListener());

        LinearLayout brand = new LinearLayout(context);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        final TextView title = new TextView(context);
        title.setTextSize(17f);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setTextColor(ModxTheme.FG);
        title.setTypeface(fontBold);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setIncludeFontPadding(false);
        title.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        View titleDot = new View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(COLOR_ACCENT);
        titleDot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotLp.setMargins(dp(6), dp(1), 0, 0);
        titleDot.setLayoutParams(dotLp);

        titleRow.addView(title);
        titleRow.addView(titleDot);

        TextView subTitle = new TextView(context);
        subTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        subTitle.setMarqueeRepeatLimit(-1);
        subTitle.setSingleLine(true);
        subTitle.setSelected(true);
        subTitle.setTextColor(ModxTheme.MUTED);
        subTitle.setTypeface(fontRegular);
        subTitle.setTextSize(10f);
        subTitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        subTitle.setIncludeFontPadding(false);
        subTitle.setPadding(0, dp(2), 0, 0);
        subTitle.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        brand.addView(titleRow);
        brand.addView(subTitle);

        final TextView proTitle = new TextView(context);
        proTitle.setText("PRO");
        proTitle.setTextSize(9.5f);
        proTitle.setGravity(Gravity.CENTER);
        proTitle.setTextColor(COLOR_ACCENT);
        proTitle.setTypeface(fontBold);
        proTitle.setIncludeFontPadding(false);
        proTitle.setPadding(dp(7), dp(2), dp(7), dp(2));
        proTitle.setBackground(ModxTheme.pillBadgeBg(
                context, ModxTheme.accentSoft(COLOR_ACCENT), ModxTheme.accentLine(COLOR_ACCENT)));
        LinearLayout.LayoutParams proLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        proLp.setMargins(dp(6), 0, dp(6), 0);
        proTitle.setLayoutParams(proLp);

        final ImageView settings = new ImageView(context);
        settings.setImageDrawable(ModxIcons.settings(COLOR_ACCENT, dp(19)));
        settings.setScaleType(ImageView.ScaleType.FIT_CENTER);
        settings.setPadding(dp(10), dp(10), dp(10), dp(10));
        settings.setBackground(ModxTheme.ghostIconBg(context, COLOR_ACCENT));
        settings.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        settings.setContentDescription("Settings");
        settings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ModxTheme.hapticTap(v);
                    v.animate().rotationBy(90f).setDuration(260)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    selectTabByName("Settings");
                }
            });

        header.addView(brand);
        header.addView(proTitle);
        header.addView(settings);

        View headerLine = makeDivider(false);
        mTitleView = title;
        mTitleDot = titleDot;
        mSubTitleView = subTitle;
        mProView = proTitle;
        mSettingsIcon = settings;
        mShimmerViewRef = headerLine;

        // ---------------- Body: sidebar + content ----------------
        mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.HORIZONTAL);
        mainContainer.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f));

        SlimScrollView sbScroll = new SlimScrollView(context);
        sbScroll.setLayoutParams(new LinearLayout.LayoutParams(dp(96), MATCH_PARENT));
        sbScroll.setBackgroundColor(ModxTheme.SURFACE);
        sbScroll.setSolidBgColor(ModxTheme.SURFACE);
        sbScroll.setAccentColor(COLOR_ACCENT);
        sbScroll.setVerticalFadingEdgeEnabled(true);
        sbScroll.setFadingEdgeLength(dp(12));
        sidebarScroll = sbScroll;

        sidebarLayout = new LinearLayout(context);
        sidebarLayout.setOrientation(LinearLayout.VERTICAL);
        sidebarLayout.setPadding(dp(6), dp(6), dp(6), dp(6));
        sidebarScroll.addView(sidebarLayout);

        SlimScrollView ctScroll = new SlimScrollView(context);
        ctScroll.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
        ctScroll.setFillViewport(true);
        ctScroll.setBackgroundColor(ModxTheme.PAGE);
        ctScroll.setSolidBgColor(ModxTheme.PAGE);
        ctScroll.setAccentColor(COLOR_ACCENT);
        ctScroll.setVerticalFadingEdgeEnabled(true);
        ctScroll.setFadingEdgeLength(dp(12));
        contentScrollView = ctScroll;

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(2), dp(6), dp(6), dp(8));
        contentScrollView.addView(contentLayout);

        mainContainer.addView(sidebarScroll);
        sidebarDivider = makeDivider(true);
        mainContainer.addView(sidebarDivider);
        mainContainer.addView(contentScrollView);

        // ---------------- Bottom bar (HIDE/KILL + MINIMIZE) ----------------
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackgroundColor(ModxTheme.SURFACE);
        bottomBar.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        bottomBar.setPadding(dp(8), dp(7), dp(28), dp(7));

        FrameLayout hideContainer = new FrameLayout(context);
        LinearLayout.LayoutParams hideLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        hideLp.setMargins(0, 0, dp(6), 0);
        hideContainer.setLayoutParams(hideLp);

        final Button hideBtn = new Button(context);
        hideBtn.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        hideBtn.setBackground(ModxTheme.dangerOutlineButtonBg(context, false));
        hideBtn.setText("HIDE/KILL (Hold)");
        hideBtn.setAllCaps(false);
        hideBtn.setTextColor(ModxTheme.DANGER);
        hideBtn.setTextSize(11f);
        hideBtn.setTypeface(fontMedium);
        hideBtn.setSingleLine(true);
        hideBtn.setEllipsize(TextUtils.TruncateAt.END);
        hideBtn.setGravity(Gravity.CENTER);
        hideBtn.setMinHeight(0);
        hideBtn.setMinimumHeight(0);
        hideBtn.setMinWidth(0);
        hideBtn.setMinimumWidth(0);
        hideBtn.setPadding(dp(8), 0, dp(8), 0);
        flatten(hideBtn);

        final View hideProgressBar = new View(context);
        FrameLayout.LayoutParams hpLp = new FrameLayout.LayoutParams(MATCH_PARENT, dp(2), Gravity.BOTTOM);
        hpLp.setMargins(dp(3), 0, dp(3), dp(1));
        hideProgressBar.setLayoutParams(hpLp);
        hideProgressBar.setBackgroundColor(ModxTheme.DANGER);
        hideProgressBar.setPivotX(0f);
        hideProgressBar.setScaleX(0f);
        hideProgressBar.setAlpha(0f);
        mHideHoldProgress = hideProgressBar;

        hideBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(90).start();
                        hideProgressBar.animate().cancel();
                        hideProgressBar.setScaleX(0f);
                        hideProgressBar.setAlpha(0.95f);
                        hideProgressBar.animate().scaleX(1f)
                                .setDuration(ViewConfiguration.getLongPressTimeout())
                                .setInterpolator(new LinearInterpolator()).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(180)
                                .setInterpolator(new DecelerateInterpolator()).start();
                        hideProgressBar.animate().cancel();
                        hideProgressBar.animate().alpha(0f).setDuration(120).start();
                        break;
                }
                return false;
            }
        });

        hideBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    ModxTheme.hapticTap(view);
                    collapseMenu(0f);
                    Toast.makeText(view.getContext(), "Icon hidden", Toast.LENGTH_SHORT).show();
                }
            });
        hideBtn.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View view) {
                    ModxTheme.hapticTap(view);
                    Toast.makeText(view.getContext(), "Menu killed", Toast.LENGTH_LONG).show();
                    cleanupMenuResources("menu_hide_kill");
                    return false;
                }
            });

        hideContainer.addView(hideBtn);
        hideContainer.addView(hideProgressBar);

        Button closeBtn = new Button(context);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, dp(38)));
        closeBtn.setBackground(ModxTheme.secondaryButtonBg(context));
        closeBtn.setText("MINIMIZE");
        closeBtn.setAllCaps(false);
        closeBtn.setTextColor(ModxTheme.FG);
        closeBtn.setTextSize(11f);
        closeBtn.setTypeface(fontMedium);
        closeBtn.setSingleLine(true);
        closeBtn.setMinHeight(0);
        closeBtn.setMinimumHeight(0);
        closeBtn.setMinWidth(0);
        closeBtn.setMinimumWidth(0);
        closeBtn.setPadding(dp(10), 0, dp(10), 0);
        closeBtn.setCompoundDrawablesWithIntrinsicBounds(
                ModxIcons.minimize(ModxTheme.MUTED, dp(14)), null, null, null);
        closeBtn.setCompoundDrawablePadding(dp(5));
        flatten(closeBtn);
        addPressAnim(closeBtn);
        closeBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    ModxTheme.hapticTap(view);
                    collapseMenu(ICON_ALPHA);
                }
            });

        bottomBar.addView(hideContainer);
        bottomBar.addView(closeBtn);

        mHideBtn      = hideBtn;
        mMinimizeBtn  = closeBtn;
        mProBadgeText = proTitle;
        mHeaderView   = header;
        mShimmerView  = headerLine;
        mExpanded.addView(header);
        mExpanded.addView(headerLine);
        mExpanded.addView(mainContainer);
        mBottomDivider = makeDivider(false);
        mExpanded.addView(mBottomDivider);
        mExpanded.addView(bottomBar);

        mRootContainer.addView(mCollapsed);
        mRootContainer.addView(menuFrame);

        if (wView != null) {
            iconHolder.addView(wView);
        } else {
            iconHolder.addView(startimage);
        }

        categoryViewsMap = new HashMap<String, LinearLayout>();
        categoryNames = new ArrayList<String>();

        // ---------------- In-menu snackbar pill ----------------
        TextView toastTv = new TextView(context);
        toastTv.setTextColor(ModxTheme.FG);
        toastTv.setTextSize(11f);
        toastTv.setTypeface(fontMedium);
        toastTv.setGravity(Gravity.CENTER);
        toastTv.setMaxLines(2);
        toastTv.setEllipsize(TextUtils.TruncateAt.END);
        toastTv.setIncludeFontPadding(false);
        toastTv.setPadding(dp(12), dp(7), dp(12), dp(7));
        toastTv.setBackground(ModxTheme.pillBadgeBg(context, ModxTheme.CARD, ModxTheme.LINE_STRONG));
        toastTv.setVisibility(View.GONE);
        toastTv.setAlpha(0f);
        FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        tLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tLp.setMargins(dp(16), 0, dp(16), dp(52));
        toastTv.setLayoutParams(tLp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            toastTv.setElevation(dp(6));
        }
        mMenuToastView = toastTv;
        menuFrame.addView(toastTv);

        // ---------------- Resize handle (diagonal grip) ----------------
        resizeHandle = new View(context);
        resizeHandle.setBackground(makeGripDrawable(ModxTheme.SUBTLE, dpf(1.6f)));
        int hSize = dp(20);
        FrameLayout.LayoutParams rhLp = new FrameLayout.LayoutParams(hSize, hSize);
        rhLp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        rhLp.setMargins(0, 0, inset + dp(4), inset + dp(4));
        resizeHandle.setLayoutParams(rhLp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            resizeHandle.setElevation(dp(30));
        }
        resizeHandle.setOnTouchListener(resizeTouchListener);
        menuFrame.addView(resizeHandle);

        try {
            boolean canOverlayTp = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || android.provider.Settings.canDrawOverlays(context);
            if (canOverlayTp) {
                tpPadView = new TeleportPadTouchView(context);
                WindowManager tpWm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

                int tpType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

                tpPadParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    tpType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    PixelFormat.TRANSPARENT);
                tpPadParams.gravity = Gravity.TOP | Gravity.START;

                int sw = screenW();
                int sh = screenH();
                float dens = safeDisplayMetrics(getContext).density;
                int padW = (int)(220 * dens);
                int padH = (int)(130 * dens);
                tpPadParams.x = sw - padW - dp(20);
                tpPadParams.y = sh - padH - dp(140);

                tpWm.addView(tpPadView, tpPadParams);
                tpPadWindowManager = tpWm;
                tpPadView.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "TPPad add failed: " + e);
        }

        Init(context, title, subTitle);

        title.setText("ModX Lab");
        subTitle.setText("Subway Surf V3.70.2");

        final TextView fSub = subTitle;
        subGuardChecks = 0;
        subGuardRunnable = new Runnable() {
            @Override
            public void run() {
                if (stopChecking || instance == null) return;
                subGuardChecks++;
                try {
                    CharSequence s = fSub.getText();
                    if (s != null && s.toString().contains("Waiting")) {
                        fSub.setText("Subway Surf V3.70.2");
                    }
                } catch (Exception ignored) { }
                if (subGuardChecks < 15 && !stopChecking) {
                    mainHandler.postDelayed(this, 1000L);
                }
            }
        };
        mainHandler.postDelayed(subGuardRunnable, 1000L);
    }

    // ================================================================
    // Small helpers
    // ================================================================
    private Typeface loadFont(String[] assetNames, String fallbackFamily, int fallbackStyle) {
        try {
            return Typeface.create(fallbackFamily, fallbackStyle);
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static double linearizeChannel(double c) {
        return (c <= 0.04045) ? (c / 12.92) : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double relativeLuminance(int color) {
        double r = linearizeChannel(Color.red(color) / 255.0);
        double g = linearizeChannel(Color.green(color) / 255.0);
        double b = linearizeChannel(Color.blue(color) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double contrastRatio(int c1, int c2) {
        double l1 = relativeLuminance(c1);
        double l2 = relativeLuminance(c2);
        double bright = Math.max(l1, l2);
        double dark = Math.min(l1, l2);
        return (bright + 0.05) / (dark + 0.05);
    }

    private static int bestTextOnColor(int bgColor) {
        return ModxTheme.onAccent(bgColor);
    }

    private void showMenuToast(final String message) {
        if (TextUtils.isEmpty(message) || mMenuToastView == null) return;
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (mMenuToastView == null) return;
                if (mMenuToastHideRunnable != null) {
                    mainHandler.removeCallbacks(mMenuToastHideRunnable);
                }
                mMenuToastView.animate().cancel();
                mMenuToastView.setText(message);
                mMenuToastView.setBackground(ModxTheme.pillBadgeBg(
                        getContext, ModxTheme.CARD, ModxTheme.accentLine(COLOR_ACCENT)));
                mMenuToastView.setVisibility(View.VISIBLE);
                mMenuToastView.setAlpha(0f);
                mMenuToastView.setTranslationY(dp(6));
                mMenuToastView.animate().alpha(1f).translationY(0f).setDuration(150).start();
                mMenuToastHideRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (mMenuToastView == null) return;
                        mMenuToastView.animate().alpha(0f).setDuration(180)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mMenuToastView != null) {
                                            mMenuToastView.setVisibility(View.GONE);
                                        }
                                    }
                                }).start();
                    }
                };
                mainHandler.postDelayed(mMenuToastHideRunnable, 1800L);
            }
        });
    }

    private int ensureReadableAccent(int rawColor) {
        return ModxTheme.ensureReadableAccent(rawColor);
    }

    private static boolean isLight(int color) {
        return ModxTheme.isLight(color);
    }

    private static String hex(int color) {
        return ModxTheme.hex6(color);
    }

    private LinearLayout.LayoutParams rowLp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private GradientDrawable cardBg(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) {
            g.setStroke(dp(1), stroke);
        }
        return g;
    }

    private GradientDrawable gradBg(int c1, int c2, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void flatten(Button b) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            b.setStateListAnimator(null);
        }
    }

    private void addPressAnim(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            view.animate().scaleX(0.985f).scaleY(0.985f).alpha(0.92f).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
                            break;
                    }
                    return false;
                }
            });
    }

    private void animateStroke(final GradientDrawable g, int from, int to) {
        if (g == null) return;
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        va.setDuration(200);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    g.setStroke(dp(1), (Integer) a.getAnimatedValue());
                }
            });
        va.start();
    }

    private View makeDivider(boolean vertical) {
        View v = new View(getContext);
        if (vertical) {
            v.setLayoutParams(new LinearLayout.LayoutParams(dp(1), MATCH_PARENT));
        } else {
            v.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, dp(1)));
        }
        v.setBackgroundColor(ModxTheme.LINE);
        return v;
    }

    private static DisplayMetrics safeDisplayMetrics(Context ctx) {
        DisplayMetrics dm = new DisplayMetrics();
        try {
            if (ctx != null) {
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null && wm.getDefaultDisplay() != null) {
                    wm.getDefaultDisplay().getMetrics(dm);
                    if (dm.widthPixels > 0 && dm.heightPixels > 0 && dm.density > 0f) return dm;
                }
            }
        } catch (Exception ignored) {}
        return Resources.getSystem().getDisplayMetrics();
    }

    private DisplayMetrics displayMetrics() {
        return safeDisplayMetrics(getContext);
    }

    private int screenW() { return displayMetrics().widthPixels; }
    private int screenH() { return displayMetrics().heightPixels; }

    // ================================================================
    // Expand / collapse with animation
    // ================================================================
    private void expandMenu() {
        menuFrame.animate().cancel();
        mCollapsed.setVisibility(View.GONE);
        menuFrame.setVisibility(View.VISIBLE);
        menuFrame.setPivotX(0f);
        menuFrame.setPivotY(0f);
        menuFrame.setAlpha(0f);
        menuFrame.setScaleX(0.92f);
        menuFrame.setScaleY(0.92f);
        menuFrame.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(new DecelerateInterpolator(1.6f))
            .start();
        menuFrame.post(new Runnable() {
            @Override
            public void run() {
                keepInsideScreen();
                applyResponsiveScale(menuFrame.getWidth());
                if (!isLoggedIn && !windowIsFocusable) {
                    setWindowFocusable(true);
                }
                if (!isLoggedIn && activeLoginHelper != null) {
                    activeLoginHelper.prepareActionModeHost();
                    activeLoginHelper.onHostVisibilityChanged(true);
                }
                if (!isLoggedIn) {
                    updateLoginOverflowScroll(false);
                }
            }
        });
    }

    private void collapseMenu(float iconAlpha) {
        Preferences.flush("menu_collapse");
        if (activeLoginHelper != null) {
            activeLoginHelper.onHostVisibilityChanged(false);
        }
        setWindowFocusable(false);
        mCollapsed.setVisibility(View.VISIBLE);
        mCollapsed.setAlpha(iconAlpha);
        mCollapsed.setScaleX(0.75f);
        mCollapsed.setScaleY(0.75f);
        mCollapsed.animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(new DecelerateInterpolator(1.4f)).start();

        menuFrame.animate().cancel();
        menuFrame.animate()
            .alpha(0f).scaleX(0.94f).scaleY(0.94f)
            .setDuration(150)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    menuFrame.setVisibility(View.GONE);
                    menuFrame.setAlpha(1f);
                    menuFrame.setScaleX(1f);
                    menuFrame.setScaleY(1f);
                }
            })
            .start();
    }

    // Move the overlay window but keep it inside the screen
    private void moveWindow(int x, int y) {
        if (vmParams == null || mWindowManager == null || rootFrame == null) return;
        int vw = rootFrame.getWidth();
        int vh = rootFrame.getHeight();
        int maxX = Math.max(0, screenW() - vw);
        int maxY = Math.max(0, screenH() - vh);
        vmParams.x = Math.max(0, Math.min(x, maxX));
        vmParams.y = Math.max(0, Math.min(y, maxY));
        try {
            mWindowManager.updateViewLayout(rootFrame, vmParams);
        } catch (Exception e) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("moveWindow updateViewLayout failed: " + SecurityNative.diagException(e));
            }
        }
    }

    // Shrink / reposition the menu if it is bigger than the current screen (e.g. after rotation)
    private void keepInsideScreen() {
        if (vmParams == null || mWindowManager == null || rootFrame == null) return;
        try {
            int sw = screenW();
            int sh = screenH();
            ViewGroup.LayoutParams lp = menuFrame.getLayoutParams();
            int minW = dp(MIN_MENU_WIDTH_DP);
            int minH = dp(MIN_MENU_HEIGHT_DP);
            int maxW = Math.max(minW, sw - dp(4));
            int maxH = Math.max(minH, sh - dp(4));
            boolean changed = false;
            if (lp.width > maxW) { lp.width = maxW; changed = true; }
            if (lp.height > maxH) { lp.height = maxH; changed = true; }
            if (changed) menuFrame.setLayoutParams(lp);

            int vw = menuFrame.getVisibility() == View.VISIBLE ? lp.width : rootFrame.getWidth();
            int vh = menuFrame.getVisibility() == View.VISIBLE ? lp.height : rootFrame.getHeight();
            int nx = Math.max(0, Math.min(vmParams.x, Math.max(0, sw - vw)));
            int ny = Math.max(0, Math.min(vmParams.y, Math.max(0, sh - vh)));
            if (changed || nx != vmParams.x || ny != vmParams.y) {
                vmParams.x = nx;
                vmParams.y = ny;
                mWindowManager.updateViewLayout(rootFrame, vmParams);
            }
            if (!isLoggedIn && contentScrollView != null) {
                contentScrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        updateLoginOverflowScroll(false);
                    }
                });
            }
        } catch (Exception e) {
            if (BuildConfig.SEC_DIAG) {
                SecurityNative.diagUi("keepInsideScreen updateViewLayout failed: " + SecurityNative.diagException(e));
            }
        }
    }

    // ================================================================
    // Resize grip (three diagonal lines)
    // ================================================================
    private Drawable makeGripDrawable(final int color, final float strokePx) {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                p.setColor(color);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokePx);
                p.setStrokeCap(Paint.Cap.ROUND);
            }

            @Override
            public void draw(Canvas canvas) {
                int W = getBounds().width();
                int H = getBounds().height();
                if (W <= 0 || H <= 0) return;
                float m = strokePx + 1f;
                float[] d = {0.34f, 0.62f, 0.90f};
                for (int i = 0; i < d.length; i++) {
                    float len = Math.min(W, H) * d[i];
                    canvas.drawLine(W - m - len, H - m, W - m, H - m - len, p);
                }
            }

            @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
            @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    // ================================================================
    // Clean toggle switch (ModX Theme — subtle 1.5dp ring, no neon bloom)
    // ================================================================
    private static class ToggleView extends View {
        interface Listener { void onChanged(boolean checked); }

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final ArgbEvaluator argb = new ArgbEvaluator();
        private final float density;
        private int onColor, onColor2, offColor;
        private boolean checked;
        private float t = 0f;
        private float scale = 1f;
        private ValueAnimator anim;
        private Listener listener;

        ToggleView(Context context, int onColor, int onColor2, int offColor) {
            super(context);
            this.density = safeDisplayMetrics(context).density;
            this.onColor = onColor;
            this.onColor2 = onColor2;
            this.offColor = offColor;
            thumbPaint.setStyle(Paint.Style.FILL);
            trackPaint.setStyle(Paint.Style.FILL);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1f * density);
        }

        void setColors(int onColor, int onColor2, int offColor) {
            this.onColor = onColor;
            this.onColor2 = onColor2;
            this.offColor = offColor;
            invalidate();
        }

        void setListener(Listener l) { listener = l; }

        void setScale(float s) {
            if (Math.abs(s - scale) > 0.005f) {
                scale = s;
                requestLayout();
                invalidate();
            }
        }

        boolean isChecked() { return checked; }

        void setChecked(boolean value, boolean animate) {
            checked = value;
            float target = value ? 1f : 0f;
            if (anim != null) anim.cancel();
            if (!animate) {
                t = target;
                invalidate();
                return;
            }
            anim = ValueAnimator.ofFloat(t, target);
            anim.setDuration(200);
            anim.setInterpolator(new DecelerateInterpolator(1.4f));
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        t = (Float) a.getAnimatedValue();
                        invalidate();
                    }
                });
            anim.start();
        }

        void toggle() {
            setChecked(!checked, true);
            if (listener != null) listener.onChanged(checked);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(Math.round(50f * scale * density), Math.round(28f * scale * density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = scale * density;
            float trackW = 38f * s;
            float trackH = 21f * s;
            float left = 3f * s;
            float top = (getHeight() - trackH) / 2f;
            float r = trackH / 2f;
            float tc = Math.max(0f, Math.min(1f, t));

            trackPaint.setColor((Integer) argb.evaluate(tc, offColor, onColor));
            rect.set(left, top, left + trackW, top + trackH);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            if (tc < 0.99f) {
                borderPaint.setStrokeWidth(1f * s);
                borderPaint.setColor(ModxTheme.withAlpha(ModxTheme.LINE_STRONG, Math.round(255f * (1f - tc))));
                canvas.drawRoundRect(rect, r, r, borderPaint);
            }

            float thumbR = r - 2.8f * s;
            float pos = Math.max(0f, Math.min(1f, tc));
            float cx = left + r + (trackW - 2f * r) * pos;
            float cy = top + r;

            int activeThumb = ModxTheme.onAccent(onColor);
            thumbPaint.setColor((Integer) argb.evaluate(tc, ModxTheme.MUTED, activeThumb));
            canvas.drawCircle(cx, cy, thumbR, thumbPaint);
        }
    }

    // ================================================================
    // Seek bar track: crisp 4dp rounded bar
    // ================================================================
    private static class SeekTrackDrawable extends Drawable {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float barH;
        private int c1;
        private int c2;

        SeekTrackDrawable(float barH, int trackColor, int c1, int c2) {
            this.barH = barH;
            this.c1 = c1;
            this.c2 = c2;
            trackPaint.setColor(trackColor);
            progPaint.setColor(c1);
        }

        void setColors(int c1, int c2) {
            this.c1 = c1;
            this.c2 = c2;
            progPaint.setColor(c1);
            invalidateSelf();
        }

        @Override
        protected boolean onLevelChange(int level) {
            invalidateSelf();
            return true;
        }

        @Override public int getIntrinsicHeight() { return (int) barH; }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0) return;
            float cy = b.exactCenterY();
            float top = cy - barH / 2f;
            float bottom = cy + barH / 2f;
            float r = barH / 2f;

            rect.set(b.left, top, b.right, bottom);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            float frac = getLevel() / 10000f;
            float right = b.left + b.width() * frac;
            if (right - b.left > 0.5f) {
                rect.set(b.left, top, Math.max(right, b.left + barH), bottom);
                canvas.drawRoundRect(rect, r, r, progPaint);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================
    // Slim, themed scroll view (~60% scrollbar alpha + solid fading edge)
    // ================================================================
    private static class SlimScrollView extends ScrollView {
        private static final int ACTIVE_ALPHA = 153;
        private static final int IDLE_ALPHA = 0;

        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int accentColor = ModxTheme.ACCENT;
        private int solidBgColor = ModxTheme.PAGE;

        void setAccentColor(int c) {
            this.accentColor = c;
            invalidate();
        }

        void setSolidBgColor(int c) {
            this.solidBgColor = c;
        }

        @Override
        public int getSolidColor() {
            return solidBgColor;
        }
        private final RectF rect = new RectF();
        private final float density;
        private final int touchSlop;
        private int barAlpha = IDLE_ALPHA;
        private boolean scrollingEnabled = true;
        private boolean loginScrollMode = false;
        private float downX, downY;
        private boolean horizontalLock = false;
        private boolean verticalLock = false;
        private Runnable onUserVerticalScrollListener = null;

        void setOnUserVerticalScrollListener(Runnable r) {
            this.onUserVerticalScrollListener = r;
        }

        void setLoginScrollMode(boolean loginMode) {
            this.loginScrollMode = loginMode;
            if (!loginMode) {
                horizontalLock = false;
                verticalLock = false;
            }
        }

        void setScrollingEnabled(boolean enabled) {
            if (this.scrollingEnabled == enabled) return;
            this.scrollingEnabled = enabled;
            if (!enabled) {
                removeCallbacks(fadeRunnable);
                barAlpha = IDLE_ALPHA;
                if (getScrollX() != 0 || getScrollY() != 0) {
                    super.scrollTo(0, 0);
                }
            }
            invalidate();
        }

        void showScrollbarBriefly() {
            if (scrollingEnabled) {
                flashBar();
            }
        }

        private final Runnable fadeRunnable = new Runnable() {
            @Override
            public void run() {
                barAlpha = Math.max(IDLE_ALPHA, barAlpha - 25);
                invalidate();
                if (barAlpha > IDLE_ALPHA) postDelayed(this, 30);
            }
        };

        SlimScrollView(Context context) {
            super(context);
            density = safeDisplayMetrics(context).density;
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            setVerticalScrollBarEnabled(false);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setWillNotDraw(false);
        }

        private void flashBar() {
            if (!scrollingEnabled) return;
            barAlpha = ACTIVE_ALPHA;
            removeCallbacks(fadeRunnable);
            postDelayed(fadeRunnable, 700);
            invalidate();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (!scrollingEnabled) return false;
            if (loginScrollMode) {
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    downX = ev.getX();
                    downY = ev.getY();
                    horizontalLock = false;
                    verticalLock = false;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (!horizontalLock && !verticalLock) {
                        float adx = Math.abs(ev.getX() - downX);
                        float ady = Math.abs(ev.getY() - downY);
                        if (adx > touchSlop || ady > touchSlop) {
                            if (adx > ady) {
                                horizontalLock = true;
                                return false;
                            } else {
                                verticalLock = true;
                            }
                        }
                    }
                    if (horizontalLock) {
                        return false;
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    horizontalLock = false;
                    verticalLock = false;
                }
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (!scrollingEnabled) return false;
            int prevScrollY = getScrollY();
            boolean res = super.onTouchEvent(ev);
            if (loginScrollMode
                    && ev.getActionMasked() == MotionEvent.ACTION_MOVE
                    && getScrollY() != prevScrollY
                    && onUserVerticalScrollListener != null) {
                onUserVerticalScrollListener.run();
            }
            return res;
        }

        @Override
        public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
            if (!scrollingEnabled) return false;
            return super.requestChildRectangleOnScreen(child, rectangle, immediate);
        }

        @Override
        public void scrollTo(int x, int y) {
            if (!scrollingEnabled) {
                if (getScrollX() != 0 || getScrollY() != 0) {
                    super.scrollTo(0, 0);
                }
                return;
            }
            super.scrollTo(x, y);
        }

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            if (scrollingEnabled && (l != oldl || t != oldt)) flashBar();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(fadeRunnable);
            super.onDetachedFromWindow();
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (!scrollingEnabled || getChildCount() == 0) return;

            final int viewH = getHeight();
            final int contentH = getChildAt(0).getHeight();
            final int range = contentH - viewH;
            if (viewH <= 0 || range <= 0) return;

            final float inset = 4f * density;
            final float trackH = viewH - inset * 2f;
            float thumbH = trackH * viewH / (float) contentH;
            thumbH = Math.max(Math.min(24f * density, trackH), thumbH);

            final float progress = Math.max(0f, Math.min(1f, getScrollY() / (float) range));
            final float w = 3f * density;
            final float right = getScrollX() + getWidth() - 1.5f * density;
            final float left = right - w;
            final float trackTop = getScrollY() + inset;
            final float thumbTop = trackTop + (trackH - thumbH) * progress;
            final float r = w / 2f;

            trackPaint.setColor(ModxTheme.MUTED);
            trackPaint.setAlpha(barAlpha * 24 / 255);
            rect.set(left, trackTop, right, trackTop + trackH);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            thumbPaint.setColor(accentColor);
            thumbPaint.setAlpha(barAlpha);
            rect.set(left, thumbTop, right, thumbTop + thumbH);
            canvas.drawRoundRect(rect, r, r, thumbPaint);
        }
    }

    private static class TabHolder {
        String name;
        LinearLayout root;
        ImageView icon;
        Drawable iconDrawable;
        TextView label;
    }

    // ================================================================
    // Resize handle touch listener
    // ================================================================
    private View.OnTouchListener resizeTouchListener = new View.OnTouchListener() {
        private int startW, startH;
        private float startX, startY;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                        startW = menuFrame.getWidth();
                        startH = menuFrame.getHeight();
                        if (startW <= 0) startW = Menu.this.dp(MENU_WIDTH);
                        if (startH <= 0) startH = Menu.this.dp(MENU_HEIGHT);
                        startX = event.getRawX();
                        startY = event.getRawY();
                        if (!isLoggedIn) {
                            loginResizingNow = true;
                            if (activeLoginHelper != null) {
                                activeLoginHelper.setMenuWindowDragging(true);
                            }
                        }
                        v.setBackground(makeGripDrawable(COLOR_ACCENT, dpf(1.7f)));
                        return true;
                    }
                case MotionEvent.ACTION_MOVE: {
                        int dx = (int)(event.getRawX() - startX);
                        int dy = (int)(event.getRawY() - startY);
                        int newW = startW + dx;
                        int newH = startH + dy;

                        int minW = Menu.this.dp(MIN_MENU_WIDTH_DP);
                        int minH = Menu.this.dp(MIN_MENU_HEIGHT_DP);
                        int maxW = Math.max(minW, Menu.this.screenW() - (vmParams != null ? vmParams.x : 0));
                        int maxH = Math.max(minH, Menu.this.screenH() - (vmParams != null ? vmParams.y : 0));
                        newW = Math.max(minW, Math.min(newW, maxW));
                        newH = Math.max(minH, Math.min(newH, maxH));

                        ViewGroup.LayoutParams mlp = menuFrame.getLayoutParams();
                        if (mlp.width != newW || mlp.height != newH) {
                            mlp.width  = newW;
                            mlp.height = newH;
                            menuFrame.setLayoutParams(mlp);
                        }

                        if (mWindowManager != null && rootFrame != null && vmParams != null) {
                            try {
                                mWindowManager.updateViewLayout(rootFrame, vmParams);
                            } catch (Exception e) {
                                if (BuildConfig.SEC_DIAG) {
                                    SecurityNative.diagUi("resize(MOVE) updateViewLayout failed: " + SecurityNative.diagException(e));
                                }
                            }
                        }
                        if (!isLoggedIn && contentScrollView instanceof SlimScrollView) {
                            ((SlimScrollView) contentScrollView).showScrollbarBriefly();
                        }
                        return true;
                    }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                        v.setBackground(makeGripDrawable(ModxTheme.SUBTLE, dpf(1.6f)));
                        if (mWindowManager != null && rootFrame != null && vmParams != null) {
                            try {
                                mWindowManager.updateViewLayout(rootFrame, vmParams);
                            } catch (Exception e) {
                                if (BuildConfig.SEC_DIAG) {
                                    SecurityNative.diagUi("resize(END) updateViewLayout failed: " + SecurityNative.diagException(e));
                                }
                            }
                        }
                        if (!isLoggedIn && contentScrollView != null) {
                            contentScrollView.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateLoginOverflowScroll(true);
                                    loginResizingNow = false;
                                    if (activeLoginHelper != null) {
                                        activeLoginHelper.setMenuWindowDragging(false);
                                    }
                                }
                            });
                        } else {
                            loginResizingNow = false;
                        }
                        return true;
                    }
            }
            return false;
        }
    };

    // ================================================================
    // Responsive layout: text / padding scale + sidebar width + compact tabs
    // ================================================================
    private void applyResponsiveScale(int menuWidthPx) {
        if (menuWidthPx <= 0) return;
        int designW = dp(MENU_WIDTH);
        if (designW <= 0) return;

        float scale = (float) menuWidthPx / (float) designW;
        if (scale < MIN_SCALE_FACTOR) scale = MIN_SCALE_FACTOR;
        if (scale > MAX_SCALE_FACTOR) scale = MAX_SCALE_FACTOR;

        int frameInset = menuFrame.getPaddingLeft() + menuFrame.getPaddingRight();
        int innerW = Math.max(1, menuWidthPx - frameInset);

        boolean compact = SHOW_TAB_ICONS && menuWidthPx < dp(COMPACT_TABS_BELOW_DP);
        int sidebarW = compact
            ? dp(48)
            : Math.max(dp(84), Math.min(dp(140), Math.round(innerW * 0.37f)));

        boolean changed = Math.abs(scale - currentScale) >= 0.01f
            || compact != compactMode
            || sidebarW != lastSidebarW;
        if (!changed) return;

        currentScale = scale;
        compactMode = compact;

        if (sidebarW != lastSidebarW && sidebarScroll != null) {
            lastSidebarW = sidebarW;
            ViewGroup.LayoutParams slp = sidebarScroll.getLayoutParams();
            slp.width = sidebarW;
            sidebarScroll.setLayoutParams(slp);
        }

        rescaleAll();
        applyTabMetrics();
    }

    private void rescaleAll() {
        scaleTextViewsIn(mExpanded);
        // category pages that are not currently attached
        for (LinearLayout cat : categoryViewsMap.values()) {
            if (cat != null && cat.getParent() == null) scaleTextViewsIn(cat);
        }
        // refresh spinner rows so they follow the new scale
        for (ArrayAdapter<String> ad : spinnerAdapters) {
            try { ad.notifyDataSetChanged(); } catch (Exception ignored) {}
        }
    }

    private void scaleTextViewsIn(View v) {
        if (v == null) return;
        if (v instanceof WebView || v instanceof AdapterView) return;

        if (v instanceof ToggleView) {
            ((ToggleView) v).setScale(currentScale);
        } else if (v instanceof TextView) {
            TextView tv = (TextView) v;
            if ("no_scale".equals(tv.getTag())) return;
            float[] base = textBase.get(tv);
            if (base == null) {
                base = new float[] {
                    tv.getTextSize(),        // px
                    tv.getPaddingLeft(),
                    tv.getPaddingTop(),
                    tv.getPaddingRight(),
                    tv.getPaddingBottom()
                };
                textBase.put(tv, base);
            }
            float s = currentScale;
            try {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, base[0] * s);
                tv.setPadding((int) (base[1] * s), (int) (base[2] * s), (int) (base[3] * s), (int) (base[4] * s));
            } catch (Exception ignored) {}
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                scaleTextViewsIn(vg.getChildAt(i));
            }
        }
    }

    private void applyTabMetrics() {
        float s = currentScale;
        int iconSize = Math.max(dp(14), (int) (dp(18) * s));
        for (TabHolder h : tabHolders) {
            boolean iconOnly = compactMode && h.icon != null;
            if (h.icon != null) {
                ViewGroup.LayoutParams ilp = h.icon.getLayoutParams();
                if (ilp.width != iconSize || ilp.height != iconSize) {
                    ilp.width = iconSize;
                    ilp.height = iconSize;
                    h.icon.setLayoutParams(ilp);
                }
            }
            h.label.setVisibility(iconOnly ? View.GONE : View.VISIBLE);
            h.root.setGravity(iconOnly ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
            h.root.setPadding((int) (dp(iconOnly ? 2 : 9) * s), (int) (dp(9) * s),
                              (int) (dp(iconOnly ? 2 : 9) * s), (int) (dp(9) * s));
        }
    }

    // ================================================================
    // Window drag (title bar + collapsed icon)
    // ================================================================
    private View.OnTouchListener onTouchListener() {
        return new View.OnTouchListener() {
            private float initialTouchX, initialTouchY;
            private int   initialX, initialY;
            private boolean moved;
            private final int slop = ViewConfiguration.get(getContext).getScaledTouchSlop();

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (vmParams == null) return true;
                switch (motionEvent.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = vmParams.x;
                        initialY = vmParams.y;
                        initialTouchX = motionEvent.getRawX();
                        initialTouchY = motionEvent.getRawY();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE: {
                            float dx = motionEvent.getRawX() - initialTouchX;
                            float dy = motionEvent.getRawY() - initialTouchY;
                            if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                                moved = true;
                                if (!isLoggedIn && activeLoginHelper != null) {
                                    activeLoginHelper.setMenuWindowDragging(true);
                                }
                            }
                            if (moved) {
                                menuFrame.setAlpha(0.7f);
                                mCollapsed.setAlpha(0.6f);
                                moveWindow(initialX + (int) dx, initialY + (int) dy);
                            }
                            return true;
                        }

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        menuFrame.setAlpha(1f);
                        mCollapsed.setAlpha(1f);
                        if (moved && !isLoggedIn && activeLoginHelper != null) {
                            activeLoginHelper.setMenuWindowDragging(false);
                        }
                        if (!moved && motionEvent.getActionMasked() == MotionEvent.ACTION_UP && isViewCollapsed()) {
                            try {
                                expandMenu();
                            } catch (NullPointerException e) {}
                        }
                        return true;

                    default:
                        return false;
                }
            }
        };
    }

    // ================================================================
    // Build features / categories
    // ================================================================
    private String getCurrentSelectedTabName() {
        for (int i = 0; i < tabHolders.size(); i++) {
            TabHolder t = tabHolders.get(i);
            if (isTabSelected(t)) {
                return t.name;
            }
        }
        return null;
    }

    private void buildFeaturesAndCategories(String[] features) {
        final String prevTabName = getCurrentSelectedTabName();
        final int prevContentScrollY = (contentScrollView != null) ? contentScrollView.getScrollY() : 0;
        final int prevSidebarScrollY = (sidebarScroll != null) ? sidebarScroll.getScrollY() : 0;

        textBase.clear();
        categoryViewsMap.clear();
        categoryNames.clear();
        tabHolders.clear();
        spinnerAdapters.clear();
        mModeButtons.clear();
        mOtherButtonOnOffs.clear();
        mToggles.clear();
        mSeekBars.clear();
        mSpinners.clear();
        mCheckBoxes.clear();
        mRadioGroups.clear();
        mColorPickers.clear();
        mInputNums.clear();
        mInputLNums.clear();
        mInputTexts.clear();
        mCategoryViews.clear();
        mCategoryLines.clear();
        mCollapseHolders.clear();
        mLinkButtons.clear();
        mMenuColorRow = null;
        mMenuColorDot = null;
        mMenuColorHex = null;
        mMenuTeleportPad = null;
        sidebarLayout.removeAllViews();
        contentLayout.removeAllViews();

        String currentCategory = "Main";
        LinearLayout defaultLayout = newCategoryLayout();
        categoryViewsMap.put(currentCategory, defaultLayout);
        categoryNames.add(currentCategory);

        int subFeat = 0;
        for (int i = 0; i < features.length; i++) {
            String feature = features[i];
            boolean switchedOn = false;
            if (feature.contains("_True")) switchedOn = true;
            feature = feature.replaceFirst("_True", "");

            boolean isCollapseAdd = feature.contains("CollapseAdd_");
            if (isCollapseAdd) feature = feature.replaceFirst("CollapseAdd_", "");

            String[] str = feature.split("_");
            int featNum;
            if (TextUtils.isDigitsOnly(str[0]) || str[0].matches("-[0-9]*")) {
                featNum = Integer.parseInt(str[0]);
                feature = feature.replaceFirst(str[0] + "_", "");
                subFeat++;
            } else {
                featNum = i - subFeat;
            }
            String[] strSplit = feature.split("_");
            String type = strSplit[0];
            String featName = strSplit.length > 1 ? strSplit[1] : "";

            if (type.equals("Category")) {
                currentCategory = strSplit[1];
                if (!categoryNames.contains(currentCategory)) {
                    categoryNames.add(currentCategory);
                    categoryViewsMap.put(currentCategory, newCategoryLayout());
                }
            } else {
                LinearLayout targetLayout = (isCollapseAdd) ? mCollapse : categoryViewsMap.get(currentCategory);
                if (targetLayout == null) targetLayout = categoryViewsMap.get("Main");

                switch (type) {
                    case "Toggle": Switch(targetLayout, featNum, featName, switchedOn); break;
                    case "SeekBar": SeekBar(targetLayout, featNum, featName, Integer.parseInt(strSplit[2]), Integer.parseInt(strSplit[3])); break;
                    case "Button": Button(targetLayout, featNum, featName); break;
                    case "ButtonOnOff": ButtonOnOff(targetLayout, featNum, featName, switchedOn); break;
                    case "Spinner": Spinner(targetLayout, featNum, featName, strSplit[2]); break;
                    case "InputText": InputText(targetLayout, featNum, featName); break;
                    case "InputValue": InputNum(targetLayout, featNum, strSplit.length == 3 ? strSplit[2] : strSplit[1], strSplit.length == 3 ? Integer.parseInt(strSplit[1]) : 0); break;
                    case "InputLValue": InputLNum(targetLayout, featNum, strSplit.length == 3 ? strSplit[2] : strSplit[1], strSplit.length == 3 ? Long.parseLong(strSplit[1]) : 0); break;
                    case "CheckBox": CheckBox(targetLayout, featNum, featName, switchedOn); break;
                    case "RadioButton": RadioButton(targetLayout, featNum, featName, strSplit[2]); break;
                    case "Collapse": Collapse(targetLayout, featName, switchedOn); break;
                    case "ButtonLink": ButtonLink(targetLayout, featName, strSplit[2]); break;
                    case "RichTextView": TextView(targetLayout, featName); break;
                    case "SmallTextView": TextView(targetLayout, featNum, featName, true); break;
                    case "RichWebView": WebTextView(targetLayout, featName); break;
                    case "ColorPicker": ColorPicker(targetLayout, featNum, featName, strSplit.length > 2 ? strSplit[2] : "#00FF88"); break;
                    case "TeleportPadWidget": TeleportPad(targetLayout); break;
                }
            }
        }

        if (!categoryNames.contains("Power")) {
            categoryNames.add("Power");
            categoryViewsMap.put("Power", newCategoryLayout());
        }

        // Ensure Settings category always exists
        if (!categoryNames.contains("Settings")) {
            categoryNames.add("Settings");
            categoryViewsMap.put("Settings", newCategoryLayout());
        }

        LinearLayout settingsLay = categoryViewsMap.get("Settings");
        if (settingsLay != null) {
            settingsLay.removeAllViews();

            Switch(settingsLay, -1, "Save features preference", Preferences.isSaveEnabled());

            Category(settingsLay, "Menu Color Theme");

            settingsLay.addView(makeMenuColorButton(
                    COLOR_ACCENT, "Menu Color", -29));
            settingsLay.addView(makeMenuColorButton(
                    FeatureStateStore.DEFAULT_MENU_COLOR, "Reset to Default", -28));

            if (BuildConfig.SEC_DIAG) {
                Category(settingsLay, "Diagnostics");
                settingsLay.addView(makeDiagSettingsStrip());
            }

            Category(settingsLay, "Community");

            ButtonTelegram(settingsLay, "Join our Telegram",
                    "https://t.me/kayesahmmedpro");

            Button(settingsLay, -6, "Close Settings");
        }
        setupSidebarTabs(prevTabName, prevContentScrollY, prevSidebarScrollY);
        rescaleAll();
        applyTabMetrics();
        if (isLoggedIn) {
            Preferences.pushRestoredSnapshotToNativeOnce();
        }
    }

    private LinearLayout makeDiagSettingsStrip() {
        LinearLayout row = new LinearLayout(getContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(rowLp(6, 4, 6, 4));
        if (!BuildConfig.SEC_DIAG) return row;

        TextView diagChip = new TextView(getContext);
        diagChip.setText("DIAG LOGS");
        diagChip.setTextColor(COLOR_ACCENT);
        diagChip.setTextSize(10.5f);
        diagChip.setTypeface(fontBold);
        diagChip.setGravity(Gravity.CENTER);
        diagChip.setMinHeight(dp(40));
        diagChip.setPadding(dp(10), dp(6), dp(10), dp(6));
        diagChip.setBackground(ModxTheme.pillBadgeBg(
                getContext, ModxTheme.accentSoft(COLOR_ACCENT), ModxTheme.accentLine(COLOR_ACCENT)));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        dLp.setMargins(0, 0, dp(6), 0);
        diagChip.setLayoutParams(dLp);
        diagChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                DiagLogViewer.show(getContext, overlayRequired);
            }
        });

        TextView testChip = new TextView(getContext);
        testChip.setText("SELF TEST");
        testChip.setTextColor(ModxTheme.INFO);
        testChip.setTextSize(10.5f);
        testChip.setTypeface(fontBold);
        testChip.setGravity(Gravity.CENTER);
        testChip.setMinHeight(dp(40));
        testChip.setPadding(dp(10), dp(6), dp(10), dp(6));
        testChip.setBackground(ModxTheme.pillBadgeBg(
                getContext, ModxTheme.badgeBg(ModxTheme.INFO), ModxTheme.badgeBorder(ModxTheme.INFO)));
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        testChip.setLayoutParams(tLp);
        testChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                DiagSelfTestRunner.runAndShow(getContext, overlayRequired);
            }
        });

        row.addView(diagChip);
        row.addView(testChip);
        return row;
    }

    private LinearLayout newCategoryLayout() {
        LinearLayout l = new LinearLayout(getContext);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    // ================================================================
    // Sidebar tabs
    // ================================================================
    private void setupSidebarTabs() {
        setupSidebarTabs(null, 0, 0);
    }

    private void setupSidebarTabs(final String restoreTabName, final int restoreContentY, final int restoreSidebarY) {
        tabHolders.clear();
        sidebarLayout.removeAllViews();

        TabHolder restoredHolder = null;
        for (final String catName : categoryNames) {
            final TabHolder h = new TabHolder();
            h.name = catName;

            LinearLayout tab = new LinearLayout(getContext);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            lp.setMargins(0, dp(2), 0, dp(2));
            tab.setLayoutParams(lp);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setMinimumHeight(dp(42));
            tab.setPadding(dp(9), dp(9), dp(9), dp(9));
            tab.setTag(h);
            h.root = tab;

            if (SHOW_TAB_ICONS) {
                h.iconDrawable = ModxIcons.tabIconFor(getContext, catName, COLOR_TEXT_MUTED, 18);
                ImageView iv = new ImageView(getContext);
                iv.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageDrawable(h.iconDrawable);
                h.icon = iv;
                tab.addView(iv);
            }

            TextView tabText = new TextView(getContext);
            tabText.setText(catName);
            tabText.setTextColor(COLOR_TEXT_MUTED);
            tabText.setTextSize(12f);
            tabText.setTypeface(fontMedium);
            tabText.setSingleLine(true);
            tabText.setEllipsize(TextUtils.TruncateAt.END);
            tabText.setIncludeFontPadding(false);
            tabText.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
            lpText.setMargins(SHOW_TAB_ICONS ? dp(7) : 0, 0, 0, 0);
            tabText.setLayoutParams(lpText);
            h.label = tabText;
            tab.addView(tabText);

            tab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ModxTheme.hapticTap(v);
                        selectTab(h);
                    }
                });
            addPressAnim(tab);

            tabHolders.add(h);
            sidebarLayout.addView(tab);
            styleTab(h, false);
            if (restoreTabName != null && restoreTabName.equals(catName)) {
                restoredHolder = h;
            }
        }

        if (restoredHolder != null) {
            selectTab(restoredHolder, false);
            if (contentScrollView != null && restoreContentY > 0) {
                contentScrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (contentScrollView != null) {
                            contentScrollView.scrollTo(0, restoreContentY);
                        }
                    }
                });
            }
            if (sidebarScroll != null && restoreSidebarY > 0) {
                sidebarScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        if (sidebarScroll != null) {
                            sidebarScroll.scrollTo(0, restoreSidebarY);
                        }
                    }
                });
            }
        } else if (tabHolders.size() > 0) {
            selectTab(tabHolders.get(0), true);
        }
    }

    private void styleTab(TabHolder h, boolean selected) {
        if (selected) {
            Drawable bg = ModxTheme.pillBadgeBg(
                    getContext, ModxTheme.accentSoft(COLOR_ACCENT), ModxTheme.accentLine(COLOR_ACCENT));
            h.root.setBackground(ModxTheme.withInteractiveRipple(
                    getContext, bg, ModxTheme.withAlpha(COLOR_ACCENT, 0x24), ModxTheme.RADIUS_DP));
        } else {
            h.root.setBackground(ModxTheme.withInteractiveRipple(
                    getContext, ModxTheme.transparentBg(), 0x16FFFFFF, ModxTheme.RADIUS_DP));
        }
        h.label.setTextColor(selected ? ModxTheme.FG : COLOR_TEXT_MUTED);
        h.label.setTypeface(selected ? fontBold : fontMedium);
        if (h.iconDrawable instanceof SvgIcon) {
            ((SvgIcon) h.iconDrawable).setColor(selected ? COLOR_ACCENT : COLOR_TEXT_MUTED);
        }
    }

    private void selectTab(TabHolder selected) {
        selectTab(selected, true);
    }

    private void selectTab(TabHolder selected, boolean animate) {
        for (TabHolder t : tabHolders) styleTab(t, t == selected);

        contentLayout.removeAllViews();
        LinearLayout selectedCatView = categoryViewsMap.get(selected.name);
        if (selectedCatView != null) {
            if (selectedCatView.getParent() != null) {
                ((ViewGroup) selectedCatView.getParent()).removeView(selectedCatView);
            }
            contentLayout.addView(selectedCatView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        }
        contentScrollView.scrollTo(0, 0);

        contentLayout.animate().cancel();
        if (animate) {
            contentLayout.setAlpha(0f);
            contentLayout.setTranslationY(dp(6));
            contentLayout.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(new DecelerateInterpolator(1.5f)).start();
        } else {
            contentLayout.setAlpha(1f);
            contentLayout.setTranslationY(0f);
        }
    }

    private boolean selectTabByName(String name) {
        for (TabHolder h : tabHolders) {
            if (h.name.equals(name)) {
                selectTab(h);
                return true;
            }
        }
        return false;
    }

    public void ShowMenu() {
        rootFrame.addView(mRootContainer);
        if (showMenuRunnable != null) {
            mainHandler.removeCallbacks(showMenuRunnable);
        }
        showMenuRunnable = new Runnable() {
            @Override
            public void run() {
                if (stopChecking || instance == null) return;
                if (!isLoggedIn) {
                    showLoginScreen();
                } else {
                    if (contentScrollView instanceof SlimScrollView) {
                        ((SlimScrollView) contentScrollView).setScrollingEnabled(true);
                    }
                    buildFeaturesAndCategories(GetFeatureList());
                }
            }
        };
        mainHandler.postDelayed(showMenuRunnable, 300L);
    }

// ================================================================
// Recursively hide any TextView containing "Waiting for game lib"
// (LoginHelper বা অন্য কোথাও থেকে আসতে পারে)
// ================================================================
private void hideWaitingTexts(View root) {
    if (root == null) return;
    if (root instanceof TextView) {
        TextView tv = (TextView) root;
        CharSequence txt = tv.getText();
        if (txt != null) {
            String s = txt.toString().toLowerCase();
            if (s.contains("waiting") || s.contains("force load")
                || s.contains("game lib")) {
                tv.setVisibility(View.GONE);
                ViewParent p = tv.getParent();
                if (p instanceof View) {
                    ((View) p).setVisibility(View.GONE);
                }
            }
        }
    } else if (root instanceof ViewGroup) {
        ViewGroup vg = (ViewGroup) root;
        for (int i = 0; i < vg.getChildCount(); i++) {
            hideWaitingTexts(vg.getChildAt(i));
        }
    }
}
private void updateLoginOverflowScroll(boolean flashIfOverflow) {
    if (isLoggedIn || !(contentScrollView instanceof SlimScrollView) || contentLayout == null) {
        return;
    }
    SlimScrollView ssv = (SlimScrollView) contentScrollView;
    int viewportH = ssv.getHeight() - ssv.getPaddingTop() - ssv.getPaddingBottom();
    if (viewportH <= 0) return;

    int availW = contentLayout.getWidth() - contentLayout.getPaddingLeft() - contentLayout.getPaddingRight();
    if (availW <= 0) {
        availW = ssv.getWidth() - ssv.getPaddingLeft() - ssv.getPaddingRight()
                - contentLayout.getPaddingLeft() - contentLayout.getPaddingRight();
    }
    if (availW <= 0) return;

    int measuredContentH = contentLayout.getPaddingTop() + contentLayout.getPaddingBottom();
    int unspecH = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
    for (int i = 0; i < contentLayout.getChildCount(); i++) {
        View child = contentLayout.getChildAt(i);
        if (child.getVisibility() == View.GONE) continue;

        int cw = availW;
        ViewGroup.LayoutParams clp = child.getLayoutParams();
        if (clp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) clp;
            measuredContentH += mlp.topMargin + mlp.bottomMargin;
            cw = Math.max(1, cw - mlp.leftMargin - mlp.rightMargin);
        }

        View measureTarget = child;
        int extraH = 0;
        int targetW = Math.max(1, cw - child.getPaddingLeft() - child.getPaddingRight());
        if (child instanceof ViewGroup && ((ViewGroup) child).getChildCount() == 1) {
            View inner = ((ViewGroup) child).getChildAt(0);
            if (inner.getVisibility() != View.GONE) {
                measureTarget = inner;
                extraH += child.getPaddingTop() + child.getPaddingBottom();
                ViewGroup.LayoutParams ilp = inner.getLayoutParams();
                if (ilp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams imlp = (ViewGroup.MarginLayoutParams) ilp;
                    extraH += imlp.topMargin + imlp.bottomMargin;
                    targetW = Math.max(1, targetW - imlp.leftMargin - imlp.rightMargin);
                }
            }
        } else {
            targetW = Math.max(1, cw);
        }

        int wSpec = View.MeasureSpec.makeMeasureSpec(targetW, View.MeasureSpec.EXACTLY);
        measureTarget.measure(wSpec, unspecH);
        measuredContentH += extraH + measureTarget.getMeasuredHeight();
    }

    int enableTolPx = dp(6);
    int disableTolPx = dp(2);
    int deltaPx = measuredContentH - viewportH;
    boolean wasOverflow = loginOverflowActive;
    boolean overflow = wasOverflow ? (deltaPx > -disableTolPx) : (deltaPx > enableTolPx);

    loginOverflowActive = overflow;
    ssv.setLoginScrollMode(overflow);
    ssv.setScrollingEnabled(overflow);
    if (overflow && flashIfOverflow) {
        ssv.showScrollbarBriefly();
    }

    if (BuildConfig.SEC_DIAG) {
        if (wasOverflow != overflow || loginResizingNow || deltaPx != lastLoggedLoginDelta) {
            lastLoggedLoginDelta = deltaPx;
            float density = safeDisplayMetrics(getContext).density;
            if (density <= 0f) density = 1f;
            SecurityNative.diagUi(String.format(java.util.Locale.US,
                    "loginScroll: vpH=%dpx(%.1fdp) contentH=%dpx(%.1fdp) delta=%dpx(%.1fdp) tol=+6/-2dp was=%s now=%s resizing=%s flash=%s",
                    viewportH, viewportH / density,
                    measuredContentH, measuredContentH / density,
                    deltaPx, deltaPx / density,
                    wasOverflow, overflow, loginResizingNow, (overflow && flashIfOverflow)));
        }
    }
}

private void detachLoginScrollListeners() {
    loginOverflowActive = false;
    loginResizingNow = false;
    lastLoginViewportH = -1;
    lastLoginContentH = -1;
    lastLoginScreenW = -1;
    lastLoginScreenH = -1;
    lastLoginVisBottom = -1;
    lastLoggedLoginDelta = Integer.MIN_VALUE;
    if (contentScrollView != null && loginLayoutChangeListener != null) {
        contentScrollView.removeOnLayoutChangeListener(loginLayoutChangeListener);
    }
    if (contentLayout != null && loginLayoutChangeListener != null) {
        contentLayout.removeOnLayoutChangeListener(loginLayoutChangeListener);
    }
    if (contentScrollView != null && loginGlobalLayoutListener != null) {
        try {
            android.view.ViewTreeObserver vto = contentScrollView.getViewTreeObserver();
            if (vto.isAlive()) {
                vto.removeOnGlobalLayoutListener(loginGlobalLayoutListener);
            }
        } catch (Exception ignored) { }
    }
    if (contentScrollView instanceof SlimScrollView) {
        ((SlimScrollView) contentScrollView).setLoginScrollMode(false);
    }
    loginLayoutChangeListener = null;
    loginGlobalLayoutListener = null;
}

private static boolean isSessionRevokedReason(String reason) {
    return "blocked".equals(reason)
            || "expired".equals(reason)
            || "device_mismatch".equals(reason)
            || "user_not_found".equals(reason)
            || "session_invalid".equals(reason)
            || "bad_input".equals(reason);
}

private void startPeriodicSessionCheck() {
    startPeriodicSessionCheck(SESSION_REVERIFY_INTERVAL_MS);
}

private void startPeriodicSessionCheck(long delayMs) {
    stopPeriodicSessionCheck();
    if (!isLoggedIn || stopChecking) return;
    sessionReverifyRunnable = new Runnable() {
        @Override
        public void run() {
            runPeriodicSessionCheck();
        }
    };
    sessionVerifyHandler.postDelayed(sessionReverifyRunnable, delayMs);
}

private void stopPeriodicSessionCheck() {
    if (sessionReverifyRunnable != null) {
        sessionVerifyHandler.removeCallbacks(sessionReverifyRunnable);
        sessionReverifyRunnable = null;
    }
    sessionReverifyInFlight = false;
}

private void runPeriodicSessionCheck() {
    if (!isLoggedIn || stopChecking || instance == null) return;
    if (sessionReverifyInFlight) {
        startPeriodicSessionCheck();
        return;
    }

    final String tokenToCheck = !TextUtils.isEmpty(activeSessionToken)
            ? activeSessionToken
            : SecureTokenStore.loadToken(getContext);
    final String deviceIdToCheck = !TextUtils.isEmpty(activeSessionDeviceId)
            ? activeSessionDeviceId
            : SecureTokenStore.loadDeviceId(getContext);

    if (TextUtils.isEmpty(tokenToCheck) || TextUtils.isEmpty(deviceIdToCheck)) {
        forceLogoutToLogin("session_invalid");
        return;
    }

    sessionReverifyInFlight = true;
    new Thread(new Runnable() {
        @Override
        public void run() {
            final String sentNonce = ModFirebase.generateNonce();
            final org.json.JSONObject r = ModFirebase.verifySession(
                    getContext, tokenToCheck, deviceIdToCheck, sentNonce);

            boolean ok = false;
            String reason = "network";
            String freshToken = "";
            String userOut = "";
            String respNonce = "";

            if (r != null) {
                ok = r.optBoolean("ok", false);
                reason = r.optString("reason", "unknown");
                freshToken = r.optString("token", "");
                userOut = r.optString("user", "");
                respNonce = r.optString("nonce", "");
            }

            if (ok) {
                if (TextUtils.isEmpty(freshToken)
                        || TextUtils.isEmpty(userOut)
                        || !sentNonce.equals(respNonce)
                        || !SecurityNative.verifySessionToken(freshToken, userOut, deviceIdToCheck, sentNonce)) {
                    ok = false;
                    reason = "session_invalid";
                }
            }

            final boolean fOk = ok;
            final String fReason = reason;
            final String fFreshToken = freshToken;
            final String fUserOut = userOut;

            if (fOk && !TextUtils.isEmpty(SecureTokenStore.loadToken(getContext))) {
                SecureTokenStore.saveToken(getContext, fFreshToken);
            }

            sessionVerifyHandler.post(new Runnable() {
                @Override
                public void run() {
                    sessionReverifyInFlight = false;
                    if (!isLoggedIn || stopChecking || instance == null) return;
                    if (fOk) {
                        activeSessionToken = fFreshToken;
                        activeSessionUser = fUserOut;
                        activeSessionDeviceId = deviceIdToCheck;
                        startPeriodicSessionCheck(SESSION_REVERIFY_INTERVAL_MS);
                    } else if (isSessionRevokedReason(fReason)) {
                        forceLogoutToLogin(fReason);
                    } else {
                        // Offline or transient network error: retry faster within 25m monotonic TTL
                        startPeriodicSessionCheck(SESSION_REVERIFY_RETRY_MS);
                    }
                }
            });
        }
    }).start();
}

private void forceLogoutToLogin(final String reason) {
    SecureTokenStore.clearToken(getContext);
    SecurityNative.clearVerifiedSession();
    Preferences.onSessionCleared();
    Runnable task = new Runnable() {
        @Override
        public void run() {
            stopPeriodicSessionCheck();
            dismissAllTrackedDialogs();
            activeSessionToken = "";
            activeSessionUser = "";
            activeSessionDeviceId = "";
            isLoggedIn = false;
            setTeleportPadVisible(false);
            showLoginScreen(reason);
        }
    };
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
        task.run();
    } else {
        sessionVerifyHandler.post(task);
    }
}

private void showLoginScreen() {
    showLoginScreen(null);
}

private void showLoginScreen(final String forcedLogoutReason) {
    if (isLoggedIn) return;

    detachLoginScrollListeners();

    if (mHeaderView  != null) mHeaderView.setVisibility(View.GONE);
    if (mShimmerView != null) mShimmerView.setVisibility(View.GONE);
    if (sidebarScroll != null) sidebarScroll.setVisibility(View.GONE);
    if (sidebarDivider != null) sidebarDivider.setVisibility(View.GONE);

    contentLayout.removeAllViews();
    if (contentScrollView instanceof SlimScrollView) {
        ((SlimScrollView) contentScrollView).setLoginScrollMode(false);
        ((SlimScrollView) contentScrollView).setScrollingEnabled(false);
    }
    setWindowFocusable(true);

    if (activeLoginHelper != null) {
        activeLoginHelper.cleanup();
        activeLoginHelper = null;
    }

    final LoginHelper loginHelper = new LoginHelper(getContext, overlayRequired, new LoginHelper.Callback() {
        @Override
        public void onLoginSuccess(String token, String user, String deviceId) {
            detachLoginScrollListeners();
            if (activeLoginHelper != null) {
                activeLoginHelper.cleanup();
                activeLoginHelper = null;
            }
            activeSessionToken = token != null ? token : "";
            activeSessionUser = user != null ? user : "";
            activeSessionDeviceId = deviceId != null ? deviceId : "";
            boolean accentReset = Preferences.onSessionAuthenticated(activeSessionUser);
            setWindowFocusable(false);
            isLoggedIn = true;

            if (accentReset) {
                int defAccent = ensureReadableAccent(FeatureStateStore.DEFAULT_MENU_COLOR);
                mCurrentHueF = getHueFromColor(defAccent);
                applyMenuColorLive(defAccent);
            }

            if (mHeaderView  != null) mHeaderView.setVisibility(View.VISIBLE);
            if (mShimmerView != null) mShimmerView.setVisibility(View.VISIBLE);
            if (sidebarScroll != null) sidebarScroll.setVisibility(View.VISIBLE);
            if (sidebarDivider != null) sidebarDivider.setVisibility(View.VISIBLE);

            if (contentScrollView instanceof SlimScrollView) {
                ((SlimScrollView) contentScrollView).setOnUserVerticalScrollListener(null);
                ((SlimScrollView) contentScrollView).setLoginScrollMode(false);
                ((SlimScrollView) contentScrollView).setScrollingEnabled(true);
            }
            contentLayout.removeAllViews();
            buildFeaturesAndCategories(GetFeatureList());
            startPeriodicSessionCheck();
        }
    });
    activeLoginHelper = loginHelper;

    final View loginView = loginHelper.buildView();
    if (!TextUtils.isEmpty(forcedLogoutReason)) {
        if ("blocked".equals(forcedLogoutReason)) {
            loginHelper.showExternalStatus("Account blocked", true);
        } else if ("expired".equals(forcedLogoutReason)) {
            loginHelper.showExternalStatus("Key expired", true);
        } else if ("device_mismatch".equals(forcedLogoutReason)) {
            loginHelper.showExternalStatus("Registered to another device", false);
        } else if ("user_not_found".equals(forcedLogoutReason)) {
            loginHelper.showExternalStatus("Account no longer exists", false);
        } else {
            loginHelper.showExternalStatus("Session invalid or expired", false);
        }
    }

    // ================================================================
    // Drag wrapper — EditText touch সরাসরি EditText-কে দেই,
    // card background বা অন্য জায়গায় drag করলে window move হয়;
    // আর login overflow scroll চালু থাকলে vertical drag scroll করে,
    // horizontal drag window move করে।
    // ================================================================
    FrameLayout dragWrapper = new FrameLayout(Menu.this.getContext) {
        private float downX, downY;
        private int   startWindowX, startWindowY;
        private boolean dragging = false;
        private boolean touchInEditText = false;
        private final Rect hitRect = new Rect();
        private final int slop =
                ViewConfiguration.get(Menu.this.getContext).getScaledTouchSlop();

        private boolean isPointInChildEditText(View v, float x, float y) {
            if (v == null || v.getVisibility() != View.VISIBLE) return false;
            if (v instanceof EditText) {
                hitRect.set(0, 0, v.getWidth(), v.getHeight());
                offsetDescendantRectToMyCoords(v, hitRect);
                return hitRect.contains(Math.round(x), Math.round(y));
            }
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                    if (isPointInChildEditText(vg.getChildAt(i), x, y)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                touchInEditText = isPointInChildEditText(this, ev.getX(), ev.getY());
                if (touchInEditText) {
                    dragging = false;
                    if (loginOverflowActive && getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return super.dispatchTouchEvent(ev);
                }
                downX = ev.getRawX();
                downY = ev.getRawY();
                if (vmParams != null) {
                    startWindowX = vmParams.x;
                    startWindowY = vmParams.y;
                }
                dragging = false;
                // Let children see DOWN first (so Button/CheckBox work)
                super.dispatchTouchEvent(ev);
                // ALWAYS claim DOWN on non-EditText areas -> guarantees MOVE/UP
                return true;
            }

            if (touchInEditText) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    touchInEditText = false;
                }
                return super.dispatchTouchEvent(ev);
            }

            switch (action) {
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getRawX() - downX;
                    float dy = ev.getRawY() - downY;
                    if (!dragging) {
                        float adx = Math.abs(dx);
                        float ady = Math.abs(dy);
                        if (adx > slop || ady > slop) {
                            if (loginOverflowActive && ady >= adx) {
                                return false;
                            }
                            dragging = true;
                            if (getParent() != null) {
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            loginHelper.setMenuWindowDragging(true);
                            // Cancel children's gesture
                            MotionEvent cancel = MotionEvent.obtain(ev);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            super.dispatchTouchEvent(cancel);
                            cancel.recycle();
                        }
                    }
                    if (dragging) {
                        if (vmParams != null) {
                            menuFrame.setAlpha(0.75f);
                            moveWindow(startWindowX + (int) dx,
                                       startWindowY + (int) dy);
                        }
                        return true;
                    }
                    // Not dragging yet -> pass MOVE to children
                    super.dispatchTouchEvent(ev);
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    menuFrame.setAlpha(1f);
                    boolean wasDragging = dragging;
                    dragging = false;
                    if (!wasDragging || action == MotionEvent.ACTION_CANCEL) {
                        super.dispatchTouchEvent(ev);
                    }
                    if (wasDragging) {
                        loginHelper.setMenuWindowDragging(false);
                    } else if (action == MotionEvent.ACTION_UP
                            && !loginHelper.isTouchOnShowCheck(ev.getRawX(), ev.getRawY())) {
                        loginHelper.dismissTextActionMode();
                    }
                    return true;
                }
            }
            return super.dispatchTouchEvent(ev);
        }
    };

    dragWrapper.addView(loginView,
            new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
    contentLayout.addView(dragWrapper,
            new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
    if (contentScrollView instanceof SlimScrollView) {
        ((SlimScrollView) contentScrollView).setOnUserVerticalScrollListener(new Runnable() {
            @Override
            public void run() {
                if (activeLoginHelper != null) {
                    activeLoginHelper.dismissTextActionMode();
                }
            }
        });
    }
    loginHelper.prepareActionModeHost();

    loginLayoutChangeListener = new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (isLoggedIn) return;
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                updateLoginOverflowScroll(loginResizingNow);
            }
        }
    };
    contentScrollView.addOnLayoutChangeListener(loginLayoutChangeListener);
    contentLayout.addOnLayoutChangeListener(loginLayoutChangeListener);

    lastLoginScreenW = screenW();
    lastLoginScreenH = screenH();
    loginGlobalLayoutListener = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
        private final Rect visRect = new Rect();
        @Override
        public void onGlobalLayout() {
            if (isLoggedIn || contentScrollView == null || contentLayout == null) return;
            int sw = screenW();
            int sh = screenH();
            boolean screenChanged = false;
            if (sw != lastLoginScreenW || sh != lastLoginScreenH) {
                lastLoginScreenW = sw;
                lastLoginScreenH = sh;
                screenChanged = true;
                keepInsideScreen();
            }
            int vh = contentScrollView.getHeight();
            int ch = contentLayout.getHeight();
            int visBottom = -1;
            if (rootFrame != null) {
                try {
                    rootFrame.getWindowVisibleDisplayFrame(visRect);
                    visBottom = visRect.bottom;
                } catch (Exception ignored) { }
            }
            if (vh != lastLoginViewportH || ch != lastLoginContentH || visBottom != lastLoginVisBottom || screenChanged) {
                boolean kbChanged = (visBottom != lastLoginVisBottom);
                lastLoginViewportH = vh;
                lastLoginContentH = ch;
                lastLoginVisBottom = visBottom;
                updateLoginOverflowScroll(loginResizingNow);
                if (kbChanged && loginOverflowActive && rootFrame != null) {
                    View focused = rootFrame.findFocus();
                    if (focused instanceof EditText && focused.getHeight() > 0) {
                        focused.requestRectangleOnScreen(
                                new Rect(0, 0, focused.getWidth(), focused.getHeight()), false);
                    }
                }
                if ((kbChanged || screenChanged) && activeLoginHelper != null) {
                    activeLoginHelper.repositionTextActionMode();
                }
            }
        }
    };
    contentScrollView.getViewTreeObserver().addOnGlobalLayoutListener(loginGlobalLayoutListener);

// ★ Login screen থেকে "Waiting for game lib" সরাই
// ★ Multiple passes — LoginHelper যা-ই add করুক, hide হবে
for (final long delay : new long[]{ 50, 200, 500, 1000, 2000, 4000 }) {
    contentLayout.postDelayed(new Runnable() {
        @Override
        public void run() {
            hideWaitingTexts(contentLayout);
        }
    }, delay);
}
    if (isViewCollapsed()) {
        menuFrame.post(new Runnable() {
            @Override
            public void run() {
                try { expandMenu(); } catch (Exception ignored) { }
            }
        });
    }

    menuFrame.postDelayed(new Runnable() {
        @Override
        public void run() {
            if (isLoggedIn) return;
            try {
                ViewGroup.LayoutParams lp = menuFrame.getLayoutParams();
                int orientation = (screenW() > screenH())
                        ? Configuration.ORIENTATION_LANDSCAPE
                        : Configuration.ORIENTATION_PORTRAIT;
                int targetH;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    targetH = screenH() - dp(15);
                } else {
                    targetH = dp(352);
                    if (targetH > screenH() - vmParams.y - dp(10)) {
                        targetH = screenH() - vmParams.y - dp(10);
                    }
                }
                if (lp.height != targetH) {
                    lp.height = targetH;
                    menuFrame.setLayoutParams(lp);
                    if (mWindowManager != null) {
                        mWindowManager.updateViewLayout(rootFrame, vmParams);
                    }
                }
                contentScrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        updateLoginOverflowScroll(false);
                    }
                });
            } catch (Exception e) {
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("showLoginScreen layout post failed: " + SecurityNative.diagException(e));
                }
            }
        }
    }, 250);
}
    @SuppressLint("WrongConstant")
    public void SetWindowManagerWindowService() {
        int iparams = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 2038 : 2002;
        vmParams = new WindowManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, iparams, 8 | FLAG_TRANSLUCENT_STATUS, -3);
        vmParams.gravity = 51;
        vmParams.x = POS_X;
        vmParams.y = effectivePosY;
        vmParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    vmParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                           | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
        mWindowManager = (WindowManager) getContext.getSystemService(getContext.WINDOW_SERVICE);
        mWindowManager.addView(rootFrame, vmParams);
        overlayRequired = true;
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("Menu window added (service): type=" + vmParams.type
                    + " flags=0x" + Integer.toHexString(vmParams.flags)
                    + " ctx=" + getContext.getClass().getSimpleName());
        }
    }

    @SuppressLint("WrongConstant")
    public void SetWindowManagerActivity() {
        vmParams = new WindowManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, POS_X, POS_Y, WindowManager.LayoutParams.TYPE_APPLICATION, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH, PixelFormat.TRANSPARENT);
        vmParams.gravity = 51;
        vmParams.x = POS_X;
        vmParams.y = effectivePosY;
        mWindowManager = ((Activity) getContext).getWindowManager();
        mWindowManager.addView(rootFrame, vmParams);
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("Menu window added (activity): type=" + vmParams.type
                    + " flags=0x" + Integer.toHexString(vmParams.flags)
                    + " ctx=" + getContext.getClass().getSimpleName());
        }
    }

    private boolean isViewCollapsed() { return rootFrame == null || mCollapsed.getVisibility() == View.VISIBLE; }
    private int convertDipToPixels(int i) { return (int)((i * safeDisplayMetrics(getContext).density) + 0.5f); }
    private int dp(int i) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) i, safeDisplayMetrics(getContext)); }
    private float dpf(float v) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, safeDisplayMetrics(getContext)); }
    public void setVisibility(int view) {
        if (rootFrame != null) rootFrame.setVisibility(view);
        if (activeLoginHelper != null) {
            activeLoginHelper.onHostVisibilityChanged(view == View.VISIBLE && !isLoggedIn);
        }
    }

private void setWindowFocusable(boolean focusable) {
    if (vmParams == null || mWindowManager == null || rootFrame == null) {
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("setWindowFocusable(" + focusable + ") skipped: wm="
                    + (mWindowManager != null) + " vmParams=" + (vmParams != null)
                    + " root=" + (rootFrame != null));
        }
        return;
    }
    try {
        if (focusable) {
            if (!windowIsFocusable) {
                int oldFlags = vmParams.flags;
                savedWindowFlags = oldFlags;

                vmParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                vmParams.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
                vmParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;

                vmParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                                       | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;

                windowIsFocusable = true;
                mWindowManager.updateViewLayout(rootFrame, vmParams);
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("setWindowFocusable(true): oldFlags=0x"
                            + Integer.toHexString(oldFlags)
                            + " newFlags=0x" + Integer.toHexString(vmParams.flags)
                            + " softInput=0x" + Integer.toHexString(vmParams.softInputMode)
                            + " token=" + (rootFrame.getWindowToken() != null));
                }
            }
        } else {
            if (windowIsFocusable) {
                int oldFlags = vmParams.flags;
                try {
                    View focused = rootFrame.findFocus();
                    if (focused != null) focused.clearFocus();
                } catch (Exception ignored) { }

                vmParams.flags = savedWindowFlags;
                vmParams.softInputMode = 0;
                windowIsFocusable = false;
                mWindowManager.updateViewLayout(rootFrame, vmParams);
                if (BuildConfig.SEC_DIAG) {
                    SecurityNative.diagUi("setWindowFocusable(false): oldFlags=0x"
                            + Integer.toHexString(oldFlags)
                            + " newFlags=0x" + Integer.toHexString(vmParams.flags)
                            + " token=" + (rootFrame.getWindowToken() != null));
                }

                try {
                    InputMethodManager imm = (InputMethodManager)
                            getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(rootFrame.getWindowToken(), 0);
                } catch (Exception ignored) { }
            }
        }
    } catch (Exception e) {
        Log.e(TAG, "setWindowFocusable: " + e);
        if (BuildConfig.SEC_DIAG) {
            SecurityNative.diagUi("setWindowFocusable(" + focusable + ") failed: "
                    + SecurityNative.diagException(e));
        }
    }
}
    
    
    public void setTeleportPadVisible(boolean visible) {
        if (tpPadView != null) {
            tpPadView.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (!visible && tpPadView instanceof TeleportPadTouchView) {
                ((TeleportPadTouchView) tpPadView).clearMarker();
            }
        }
        if (!visible && mMenuTeleportPad != null) {
            mMenuTeleportPad.clearMarker();
        }
    }

    private void syncTeleportMarkersFromFloating(float nx, float ny) {
        if (mSyncingTpMarkers) return;
        mSyncingTpMarkers = true;
        try {
            if (mMenuTeleportPad != null) {
                mMenuTeleportPad.setMarkerNorm(nx, ny);
            }
        } finally {
            mSyncingTpMarkers = false;
        }
    }

    private void syncTeleportMarkersFromMenu(float nx, float ny) {
        if (mSyncingTpMarkers) return;
        mSyncingTpMarkers = true;
        try {
            if (tpPadView instanceof TeleportPadTouchView) {
                ((TeleportPadTouchView) tpPadView).setMarkerNorm(nx, ny);
            }
        } finally {
            mSyncingTpMarkers = false;
        }
    }

    private void dismissAllTrackedDialogs() {
        if (activeColorPickerDialog != null) {
            try {
                if (activeColorPickerDialog.isShowing()) {
                    activeColorPickerDialog.dismiss();
                }
            } catch (Throwable ignored) { }
            activeColorPickerDialog = null;
        }
        ArrayList<AlertDialog> copy = new ArrayList<AlertDialog>(activeDialogs);
        activeDialogs.clear();
        for (int i = 0; i < copy.size(); i++) {
            AlertDialog d = copy.get(i);
            if (d != null) {
                try {
                    if (d.isShowing()) d.dismiss();
                } catch (Throwable ignored) { }
            }
        }
        if (BuildConfig.SEC_DIAG) {
            DiagLogViewer.dismissActive();
        }
    }

    private void cleanupMenuResources(String flushReason) {
        Preferences.flush(flushReason);
        Preferences.onSessionCleared();
        stopChecking = true;
        isLoggedIn = false;
        stopPeriodicSessionCheck();
        if (showMenuRunnable != null) {
            mainHandler.removeCallbacks(showMenuRunnable);
            showMenuRunnable = null;
        }
        if (subGuardRunnable != null) {
            mainHandler.removeCallbacks(subGuardRunnable);
            subGuardRunnable = null;
        }
        if (mMenuToastHideRunnable != null) {
            mainHandler.removeCallbacks(mMenuToastHideRunnable);
            mMenuToastHideRunnable = null;
        }
        if (mHideHoldProgress != null) {
            mHideHoldProgress.animate().cancel();
        }
        dismissAllTrackedDialogs();
        activeSessionToken = "";
        activeSessionUser = "";
        activeSessionDeviceId = "";
        detachLoginScrollListeners();
        if (activeLoginHelper != null) {
            activeLoginHelper.cleanup();
            activeLoginHelper = null;
        }
        if (rootFrame != null && mWindowManager != null) {
            try { rootFrame.removeView(mRootContainer); } catch (Exception ignored) { }
            try { mWindowManager.removeView(rootFrame); } catch (Exception ignored) { }
        }
        if (tpPadView != null && tpPadWindowManager != null) {
            try { tpPadWindowManager.removeView(tpPadView); } catch (Exception ignored) { }
        }
        if (instance == this) {
            instance = null;
        }
    }

    public void onDestroy() {
        cleanupMenuResources("menu_onDestroy");
    }

    private Button dialogButton(String text, boolean primary) {
        Button b = new Button(getContext);
        b.setText(text);
        b.setAllCaps(false);
        b.setTypeface(fontBold);
        b.setTextSize(12.5f);
        b.setMinHeight(dp(42));
        b.setMinimumHeight(dp(42));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        if (primary) {
            b.setTextColor(ModxTheme.onAccent(COLOR_ACCENT));
            b.setBackground(ModxTheme.primaryButtonBg(getContext, COLOR_ACCENT));
        } else {
            b.setTextColor(ModxTheme.FG);
            b.setBackground(ModxTheme.secondaryButtonBg(getContext));
        }
        flatten(b);
        addPressAnim(b);
        return b;
    }

    private AlertDialog showStyledDialog(String title, View body,
                                         String posText, final Runnable onPos,
                                         String negText, final Runnable onNeg) {
        final Context ctx = getContext;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(14));
        box.setBackground(ModxTheme.dialogCardBg(ctx));

        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(ModxTheme.FG);
        t.setTypeface(fontBold);
        t.setTextSize(16f);
        t.setIncludeFontPadding(false);
        box.addView(t);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        blp.setMargins(0, dp(12), 0, 0);
        box.addView(body, blp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        rlp.setMargins(0, dp(14), 0, 0);
        box.addView(row, rlp);

        final AlertDialog[] ref = new AlertDialog[1];

        if (negText != null) {
            Button neg = dialogButton(negText, false);
            neg.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ModxTheme.hapticTap(v);
                        if (onNeg != null) onNeg.run();
                        if (ref[0] != null) ref[0].dismiss();
                    }
                });
            row.addView(neg);
        }
        if (posText != null) {
            Button pos = dialogButton(posText, true);
            pos.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ModxTheme.hapticTap(v);
                        if (onPos != null) onPos.run();
                        if (ref[0] != null) ref[0].dismiss();
                    }
                });
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            plp.setMargins(dp(8), 0, 0, 0);
            row.addView(pos, plp);
        }

        Context dialogCtx = (ctx instanceof Activity)
                ? ctx
                : new ContextThemeWrapper(ctx,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                ? android.R.style.Theme_Material_Dialog_Alert
                                : android.R.style.Theme_Holo_Dialog);
        AlertDialog.Builder builder = new AlertDialog.Builder(dialogCtx);
        builder.setView(box);
        final AlertDialog dialog = builder.create();
        ref[0] = dialog;
        activeDialogs.add(dialog);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                activeDialogs.remove(dialog);
            }
        });
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (overlayRequired || !(ctx instanceof Activity)) {
                w.setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 2038 : 2002);
            }
            w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
        try {
            dialog.show();
            Window shownW = dialog.getWindow();
            if (shownW != null) {
                shownW.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            }
        } catch (Throwable ignored) {
            activeDialogs.remove(dialog);
        }
        return dialog;
    }

    private EditText makeEditText() {
        EditText e = new EditText(getContext);
        e.setTextColor(ModxTheme.FG);
        e.setHintTextColor(ModxTheme.SUBTLE);
        e.setTypeface(fontMedium);
        e.setTextSize(14f);
        e.setSingleLine(true);
        e.setBackground(ModxTheme.fieldBoxBg(getContext, COLOR_ACCENT, true));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }

    // Shared keyboard behaviour for all input dialogs
    private void showInputDialog(String title, final EditText editText, final Runnable onOk) {
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    try {
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            if (hasFocus) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                            else imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                        }
                    } catch (Throwable ignored) { }
                }
            });
        editText.requestFocus();

        showStyledDialog(title, editText,
            "OK", new Runnable() {
                @Override
                public void run() {
                    onOk.run();
                    try {
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                    } catch (Throwable ignored) { }
                }
            },
            "Cancel", new Runnable() {
                @Override
                public void run() {
                    try {
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                    } catch (Throwable ignored) { }
                }
            });
    }

    // ================================================================
    // ColorPicker — visual color chart dialog (feature #108)
    // ================================================================
    private void ColorPicker(LinearLayout linLayout, final int featNum, final String featName, final String defaultHex) {
        final int[] PRESET_COLORS = {
            0xFF20C997, 0xFFFF00FF, 0xFF00FFFF, 0xFFFF4D4F,
            0xFF22C55E, 0xFF38BDF8, 0xFFFACC15, 0xFFFB923C,
            0xFFEC4899, 0xFFA855F7, 0xFFFAF9F5, 0xFF131312,
            0xFFFFD700, 0xFF60A5FA, 0xFFA3E635, 0xFFFF6B35,
            0xFF22D3EE, 0xFF4ADE80, 0xFFF43F5E, 0xFF818CF8,
            0xFF2DD4BF, 0xFFFDE047, 0xFFF472B6, 0xFFE2E8F0
        };
        final String[] COLOR_NAMES = {
            "ModX Emerald", "Magenta", "Cyan", "Red", "Green", "Sky Blue",
            "Yellow", "Orange", "Pink", "Purple", "Off White", "Obsidian",
            "Gold", "Azure", "Lime", "Coral",
            "Turquoise", "Mint", "Rose", "Indigo",
            "Teal", "Lemon", "Blush", "Silver"
        };

        final LinearLayout row = new LinearLayout(getContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        row.setLayoutParams(rowLp(6, 4, 6, 4));
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(ModxTheme.withInteractiveRipple(
                getContext, ModxTheme.cardSurface(getContext, ModxTheme.LINE),
                0x1AFFFFFF, ModxTheme.RADIUS_DP));
        addPressAnim(row);

        TextView labelTv = new TextView(getContext);
        labelTv.setText(featName);
        labelTv.setTextColor(ModxTheme.FG);
        labelTv.setTypeface(fontMedium);
        labelTv.setTextSize(12.5f);
        labelTv.setIncludeFontPadding(false);
        labelTv.setMaxLines(1);
        labelTv.setEllipsize(TextUtils.TruncateAt.END);
        labelTv.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        final TextView hexView = new TextView(getContext);
        hexView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hexView.setTextSize(11f);
        hexView.setTextColor(ModxTheme.MUTED);
        hexView.setIncludeFontPadding(false);
        hexView.setPadding(dp(7), dp(3), dp(7), dp(3));
        hexView.setBackground(ModxTheme.pillBadgeBg(getContext, ModxTheme.SURFACE, ModxTheme.LINE));
        LinearLayout.LayoutParams hxLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        hxLp.setMargins(dp(8), 0, dp(8), 0);
        hexView.setLayoutParams(hxLp);

        final View dotView = new View(getContext);
        dotView.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));

        row.addView(labelTv);
        row.addView(hexView);
        row.addView(dotView);

        int defaultColor = ModxTheme.ACCENT;
        try { defaultColor = Color.parseColor(defaultHex); }
        catch (Exception ignored) { }
        final int defaultColorFinal = defaultColor;
        int savedColor = Preferences.readInitialColor(featName, featNum, defaultColorFinal);

        final ColorPickerHolder cpHolder = new ColorPickerHolder();
        cpHolder.featNum      = featNum;
        cpHolder.featName     = featName;
        cpHolder.row          = row;
        cpHolder.dotView      = dotView;
        cpHolder.hexView      = hexView;
        cpHolder.defaultColor = defaultColorFinal;
        cpHolder.currentColor = savedColor;
        mColorPickers.put(featNum, cpHolder);

        applyColorButton(cpHolder, savedColor);

        row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ModxTheme.hapticTap(view);
                    ScrollView scroll = new ScrollView(getContext);
                    LinearLayout grid = new LinearLayout(getContext);
                    grid.setOrientation(LinearLayout.VERTICAL);
                    grid.setPadding(dp(2), dp(2), dp(2), dp(2));

                    final AlertDialog[] dialogRef = new AlertDialog[1];

                    final int cols = 4;
                    for (int i = 0; i < PRESET_COLORS.length; i += cols) {
                        LinearLayout sRow = new LinearLayout(getContext);
                        sRow.setOrientation(LinearLayout.HORIZONTAL);
                        sRow.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

                        int added = 0;
                        for (int j = 0; j < cols && i + j < PRESET_COLORS.length; j++) {
                            final int color = PRESET_COLORS[i + j];
                            final String cname = COLOR_NAMES[i + j];

                            LinearLayout cell = new LinearLayout(getContext);
                            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
                            cellLp.setMargins(dp(3), dp(4), dp(3), dp(4));
                            cell.setLayoutParams(cellLp);
                            cell.setOrientation(LinearLayout.VERTICAL);
                            cell.setGravity(Gravity.CENTER);
                            cell.setPadding(dp(4), dp(6), dp(4), dp(6));
                            cell.setBackground(ModxTheme.withInteractiveRipple(
                                    getContext,
                                    ModxTheme.roundRect(getContext, ModxTheme.SURFACE,
                                            (color == cpHolder.currentColor) ? COLOR_ACCENT : ModxTheme.LINE,
                                            (color == cpHolder.currentColor) ? 1.5f : 1f, ModxTheme.RADIUS_DP),
                                    0x1AFFFFFF, ModxTheme.RADIUS_DP));

                            View swatch = new View(getContext);
                            swatch.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
                            GradientDrawable sw = new GradientDrawable();
                            sw.setShape(GradientDrawable.OVAL);
                            sw.setColor(color);
                            sw.setStroke(dp(1), ModxTheme.LINE_STRONG);
                            swatch.setBackground(sw);

                            TextView label = new TextView(getContext);
                            label.setText(cname);
                            label.setTextColor(ModxTheme.MUTED);
                            label.setTypeface(fontRegular);
                            label.setTextSize(9.5f);
                            label.setGravity(Gravity.CENTER);
                            label.setMaxLines(1);
                            label.setEllipsize(TextUtils.TruncateAt.END);
                            LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                            lblLp.setMargins(0, dp(4), 0, 0);
                            label.setLayoutParams(lblLp);

                            cell.addView(swatch);
                            cell.addView(label);
                            addPressAnim(cell);

                            cell.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v2) {
                                        ModxTheme.hapticTap(v2);
                                        cpHolder.currentColor = color;
                                        applyColorButton(cpHolder, color);
                                        Preferences.changeFeatureColor(featName, featNum, color);
                                        showMenuToast(featName + ": " + cname);
                                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                                    }
                                });

                            sRow.addView(cell);
                            added++;
                        }
                        for (int k = added; k < cols; k++) {
                            View empty = new View(getContext);
                            empty.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
                            sRow.addView(empty);
                        }
                        grid.addView(sRow);
                    }

                    scroll.addView(grid);
                    dialogRef[0] = showStyledDialog("Pick ESP Color", scroll, null, null, "Cancel", null);
                }
            });

        linLayout.addView(row);
    }

    private void applyColorButton(ColorPickerHolder holder, int color) {
        if (holder == null) return;
        if (holder.dotView != null) {
            GradientDrawable sw = new GradientDrawable();
            sw.setShape(GradientDrawable.OVAL);
            sw.setColor(0xFF000000 | color);
            sw.setStroke(Math.max(1, dp(1)), ModxTheme.LINE_STRONG);
            holder.dotView.setBackground(sw);
        }
        if (holder.hexView != null) {
            holder.hexView.setText(hex(color));
        }
    }

    private void featureList(String[] listFT, LinearLayout linearLayout) {
        int featNum, subFeat = 0;
        LinearLayout llBak = linearLayout;
        for (int i = 0; i < listFT.length; i++) {
            boolean switchedOn = false;
            String feature = listFT[i];
            if (feature.contains("_True")) { switchedOn = true; feature = feature.replaceFirst("_True", ""); }
            linearLayout = llBak;
            if (feature.contains("CollapseAdd_")) { linearLayout = mCollapse; feature = feature.replaceFirst("CollapseAdd_", ""); }
            String[] str = feature.split("_");
            if (TextUtils.isDigitsOnly(str[0]) || str[0].matches("-[0-9]*")) { featNum = Integer.parseInt(str[0]); feature = feature.replaceFirst(str[0] + "_", ""); subFeat++; } else { featNum = i - subFeat; }
            String[] strSplit = feature.split("_");
            switch (strSplit[0]) {
                case "Toggle": Switch(linearLayout, featNum, strSplit[1], switchedOn); break;
                case "SeekBar": SeekBar(linearLayout, featNum, strSplit[1], Integer.parseInt(strSplit[2]), Integer.parseInt(strSplit[3])); break;
                case "Button": Button(linearLayout, featNum, strSplit[1]); break;
                case "ButtonOnOff": ButtonOnOff(linearLayout, featNum, strSplit[1], switchedOn); break;
                case "Spinner": Spinner(linearLayout, featNum, strSplit[1], strSplit[2]); break;
                case "InputText": InputText(linearLayout, featNum, strSplit[1]); break;
                case "InputValue": if (strSplit.length == 3) InputNum(linearLayout, featNum, strSplit[2], Integer.parseInt(strSplit[1])); if (strSplit.length == 2) InputNum(linearLayout, featNum, strSplit[1], 0); break;
                case "InputLValue": if (strSplit.length == 3) InputLNum(linearLayout, featNum, strSplit[2], Long.parseLong(strSplit[1])); if (strSplit.length == 2) InputLNum(linearLayout, featNum, strSplit[1], 0); break;
                case "CheckBox": CheckBox(linearLayout, featNum, strSplit[1], switchedOn); break;
                case "RadioButton": RadioButton(linearLayout, featNum, strSplit[1], strSplit[2]); break;
                case "Collapse": Collapse(linearLayout, strSplit[1], switchedOn); subFeat++; break;
                case "ButtonLink": subFeat++; ButtonLink(linearLayout, strSplit[1], strSplit[2]); break;
                case "Category": subFeat++; Category(linearLayout, strSplit[1]);  break;
                case "RichTextView": subFeat++; TextView(linearLayout, strSplit[1]);  break;
                case "SmallTextView": subFeat++; TextView(linearLayout, featNum, strSplit[1], true); break;
                case "RichWebView": subFeat++; WebTextView(linearLayout, strSplit[1]); break;
                case "ColorPicker": subFeat++; ColorPicker(linearLayout, featNum, strSplit[1], strSplit.length > 2 ? strSplit[2] : "#00FF88"); break;
            }
        }
    }


    private void Switch(LinearLayout linLayout, final int featNum, final String featName, boolean swiOn) {
        final LinearLayout row = new LinearLayout(getContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        row.setLayoutParams(rowLp(6, 3, 6, 3));
        row.setPadding(dp(12), dp(9), dp(10), dp(9));

        boolean initial = Preferences.readInitialBool(featName, featNum, swiOn);
        final GradientDrawable rowBg = ModxTheme.roundRect(
                getContext,
                ModxTheme.CARD,
                initial ? ModxTheme.accentLine(ToggleON) : ModxTheme.LINE,
                1f,
                ModxTheme.RADIUS_DP);
        row.setBackground(ModxTheme.withInteractiveRipple(
                getContext, rowBg, ModxTheme.withAlpha(COLOR_ACCENT, 0x1F), ModxTheme.RADIUS_DP));

        TextView label = new TextView(getContext);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        label.setText(featName);
        label.setTextColor(ModxTheme.FG);
        label.setTypeface(fontMedium);
        label.setTextSize(12.5f);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setIncludeFontPadding(false);
        label.setPadding(0, 0, dp(8), 0);

        final ToggleView toggle = new ToggleView(getContext, COLOR_ACCENT, COLOR_ACCENT_3, COLOR_TRACK);
        toggle.setChecked(initial, false);
        if (featNum == 710) {
            setTeleportPadVisible(initial);
        }

        final ToggleHolder holder = new ToggleHolder();
        holder.featNum    = featNum;
        holder.featName   = featName;
        holder.toggle     = toggle;
        holder.rowBg      = rowBg;
        holder.state      = new boolean[]{ initial };
        holder.defaultVal = swiOn;
        mToggles.put(featNum, holder);

        toggle.setListener(new ToggleView.Listener() {
            @Override
            public void onChanged(boolean bool) {
                holder.state[0] = bool;
                animateStroke(rowBg,
                        bool ? ModxTheme.LINE : ModxTheme.accentLine(ToggleON),
                        bool ? ModxTheme.accentLine(ToggleON) : ModxTheme.LINE);

                if (!isProgrammaticUpdate()) {
                    Preferences.changeFeatureBool(featName, featNum, bool);
                }

                if (featNum == 710) {
                    setTeleportPadVisible(bool);
                }
            }
        });

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                toggle.toggle();
            }
        });
        addPressAnim(row);

        row.addView(label);
        row.addView(toggle);
        linLayout.addView(row);
    }

    private void SeekBar(LinearLayout linLayout, final int featNum, final String featName, final int min, final int max) {
        Integer def = SEEK_DEFAULTS.get(featNum);
        int defVal = (def != null) ? def.intValue() : min;
        if (defVal < min) defVal = min;
        if (defVal > max) defVal = max;
        final int defaultVal = defVal;

        int startVal = Preferences.readInitialInt(featName, featNum, defaultVal);
        if (startVal < min) startVal = min;
        if (startVal > max) startVal = max;

        LinearLayout card = new LinearLayout(getContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setLayoutParams(rowLp(6, 3, 6, 3));
        card.setBackground(ModxTheme.cardSurface(getContext, ModxTheme.LINE));
        card.setPadding(dp(12), dp(9), dp(12), dp(8));

        LinearLayout head = new LinearLayout(getContext);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        head.setClipToPadding(false);

        TextView label = new TextView(getContext);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        label.setText(featName);
        label.setTextColor(ModxTheme.FG);
        label.setTypeface(fontMedium);
        label.setTextSize(12.5f);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setIncludeFontPadding(false);
        label.setPadding(0, 0, dp(8), 0);

        final TextView chip = new TextView(getContext);
        chip.setTag("no_scale");
        chip.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        int displayStart = (featNum == 103) ? (startVal + 30) : startVal;
        chip.setText(String.valueOf(displayStart));
        chip.setTextColor(COLOR_ACCENT);
        chip.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        chip.setTextSize(11.5f);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setMinHeight(dp(26));
        chip.setPadding(dp(9), dp(3), dp(9), dp(3));
        chip.setBackground(ModxTheme.pillBadgeBg(
                getContext, ModxTheme.accentSoft(COLOR_ACCENT), ModxTheme.accentLine(COLOR_ACCENT)));

        head.addView(label);
        head.addView(chip);

        final SeekBar seekBar = new SeekBar(getContext);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        sbLp.setMargins(0, dp(4), 0, 0);
        seekBar.setLayoutParams(sbLp);
        seekBar.setPadding(dp(8), dp(2), dp(8), dp(2));
        seekBar.setMinimumHeight(dp(26));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            seekBar.setMinHeight(dp(26));
            seekBar.setMaxHeight(dp(28));
        }

        seekBar.setMax(max);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) seekBar.setMin(min);
        SeekTrackDrawable trackDrawable = new SeekTrackDrawable(
                dpf(4f), COLOR_TRACK, COLOR_ACCENT, COLOR_ACCENT_3);
        seekBar.setProgressDrawable(trackDrawable);

        GradientDrawable thumbCore = makeSeekThumbDrawable();
        seekBar.setThumb(thumbCore);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) seekBar.setSplitTrack(false);
        seekBar.setProgress(startVal);

        final boolean[] isDraggingSeekBar = new boolean[]{ false };
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onStartTrackingTouch(SeekBar s) {
                    isDraggingSeekBar[0] = true;
                }
                @Override
                public void onStopTrackingTouch(SeekBar s) {
                    isDraggingSeekBar[0] = false;
                    if (Preferences.isSaveEnabled()) {
                        Preferences.flush("seekbar_stop");
                    }
                }
                @Override
                public void onProgressChanged(SeekBar s, int i, boolean z) {
                    int val = i < min ? min : i;
                    if (val != i) {
                        seekBar.setProgress(val);
                        return;
                    }
                    int disp = (featNum == 103) ? (val + 30) : val;
                    chip.setText(String.valueOf(disp));
                    if (z) {
                        ModxTheme.hapticTick(s);
                    }
                    if (!isProgrammaticUpdate()) {
                        if (z && isDraggingSeekBar[0]) {
                            Preferences.changeFeatureIntDeferred(featName, featNum, val);
                        } else {
                            Preferences.changeFeatureInt(featName, featNum, val);
                        }
                    }
                }
            });

        chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ModxTheme.hapticTap(v);
                    final int dMin = (featNum == 103) ? (min + 30) : min;
                    final int dMax = (featNum == 103) ? (max + 30) : max;
                    final EditText editText = makeEditText();
                    editText.setHint(dMin + " .. " + dMax);
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
                    editText.setText(chip.getText());
                    editText.setSelection(editText.getText().length());
                    showInputDialog(featName, editText, new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    String raw = editText.getText().toString().trim();
                                    if (raw.isEmpty()) return;
                                    int entered = Integer.parseInt(raw);
                                    int internal = (featNum == 103) ? (entered - 30) : entered;
                                    if (internal < min) internal = min;
                                    if (internal > max) internal = max;
                                    setSeekBarValue(featNum, internal, false);
                                } catch (NumberFormatException ignored) { }
                            }
                        });
                }
            });

        chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    ModxTheme.hapticTap(v);
                    setSeekBarValue(featNum, defaultVal, false);
                    showMenuToast("Reset " + featName + " to default");
                    return true;
                }
            });

        SeekBarHolder sbHolder = new SeekBarHolder();
        sbHolder.featNum       = featNum;
        sbHolder.featName      = featName;
        sbHolder.seekBar       = seekBar;
        sbHolder.chip          = chip;
        sbHolder.trackDrawable = trackDrawable;
        sbHolder.thumbCore     = thumbCore;
        sbHolder.min           = min;
        sbHolder.max           = max;
        sbHolder.defaultVal    = defaultVal;
        mSeekBars.put(featNum, sbHolder);

        card.addView(head);
        card.addView(seekBar);
        linLayout.addView(card);
    }

    private GradientDrawable makeSeekThumbDrawable() {
        GradientDrawable core = new GradientDrawable();
        core.setShape(GradientDrawable.OVAL);
        core.setColor(ModxTheme.FG);
        core.setStroke(Math.max(2, dp(2)), COLOR_ACCENT);
        int sz = dp(16);
        core.setSize(sz, sz);
        return core;
    }

    private Drawable makeSeekThumb() {
        return makeSeekThumbDrawable();
    }

    private void Button(LinearLayout linLayout, final int featNum, final String featName) {
        final Button button = new Button(getContext);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setAllCaps(false);
        button.setText(Html.fromHtml(featName));
        button.setTypeface(fontBold);
        button.setTextSize(12.5f);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));

        if (featNum == -6) {
            button.setTextColor(ModxTheme.FG);
            button.setBackground(ModxTheme.secondaryButtonBg(getContext));
        } else {
            button.setTextColor(ModxTheme.onAccent(COLOR_ACCENT));
            button.setBackground(ModxTheme.primaryButtonBg(getContext, COLOR_ACCENT));
        }

        flatten(button);
        addPressAnim(button);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                if (featNum == 997) {
                    selectTabByName("Camera");
                    return;
                }
                switch (featNum) {
                    case -6:
                        if (categoryNames.size() > 0 && selectTabByName(categoryNames.get(0))) {
                            return;
                        }
                        collapseMenu(ICON_ALPHA);
                        return;
                    case -100:
                        stopChecking = true;
                        break;
                }
                Preferences.triggerActionButton(featName, featNum);
            }
        });
        linLayout.addView(button);
    }

    private LinearLayout makeMenuColorButton(final int color, final String label, final int featNum) {
        final LinearLayout row = new LinearLayout(getContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        row.setLayoutParams(rowLp(6, 4, 6, 4));
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(ModxTheme.withInteractiveRipple(
                getContext, ModxTheme.cardSurface(getContext, ModxTheme.LINE),
                ModxTheme.withAlpha(COLOR_ACCENT, 0x1F), ModxTheme.RADIUS_DP));

        TextView labelTv = new TextView(getContext);
        labelTv.setText(label);
        labelTv.setTextColor(ModxTheme.FG);
        labelTv.setTypeface(fontMedium);
        labelTv.setTextSize(12.5f);
        labelTv.setIncludeFontPadding(false);
        labelTv.setMaxLines(1);
        labelTv.setEllipsize(TextUtils.TruncateAt.END);
        labelTv.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        TextView hexTv = new TextView(getContext);
        hexTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        hexTv.setTextSize(11f);
        hexTv.setTextColor(ModxTheme.MUTED);
        hexTv.setIncludeFontPadding(false);
        hexTv.setPadding(dp(7), dp(3), dp(7), dp(3));
        hexTv.setBackground(ModxTheme.pillBadgeBg(getContext, ModxTheme.SURFACE, ModxTheme.LINE));
        LinearLayout.LayoutParams hxLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        hxLp.setMargins(dp(8), 0, dp(8), 0);
        hexTv.setLayoutParams(hxLp);

        View dot = new View(getContext);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));

        row.addView(labelTv);
        row.addView(hexTv);
        row.addView(dot);

        applyMenuColorRowStyle(dot, hexTv, color);
        if (featNum == -29) {
            mMenuColorRow = row;
            mMenuColorDot = dot;
            mMenuColorHex = hexTv;
        }

        addPressAnim(row);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                if (featNum == -29) {
                    showMenuColorPickerDialog();
                } else if (featNum == -28) {
                    resetMenuColorToDefaultInPlace();
                }
            }
        });
        return row;
    }

    private void applyMenuColorRowStyle(View dot, TextView hexTv, int color) {
        if (dot != null) {
            GradientDrawable sw = new GradientDrawable();
            sw.setShape(GradientDrawable.OVAL);
            sw.setColor(0xFF000000 | color);
            sw.setStroke(Math.max(1, dp(1)), ModxTheme.LINE_STRONG);
            dot.setBackground(sw);
        }
        if (hexTv != null) {
            hexTv.setText(hex(color));
        }
    }

    private void ButtonLink(LinearLayout linLayout, final String featName, final String url) {
        final Button button = new Button(getContext);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setAllCaps(false);
        button.setTextColor(COLOR_ACCENT);
        button.setText(featName);
        button.setTypeface(fontMedium);
        button.setTextSize(12.5f);
        button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(ModxTheme.withInteractiveRipple(
                getContext,
                ModxTheme.pillBadgeBg(getContext, ModxTheme.accentSoft(COLOR_ACCENT), ModxTheme.accentLine(COLOR_ACCENT)),
                ModxTheme.withAlpha(COLOR_ACCENT, 0x24),
                ModxTheme.RADIUS_DP));
        button.setCompoundDrawablesWithIntrinsicBounds(
                null, null, ModxIcons.externalLink(COLOR_ACCENT, dp(15)), null);
        button.setCompoundDrawablePadding(dp(8));
        flatten(button);
        addPressAnim(button);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ModxTheme.hapticTap(v);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setData(Uri.parse(url));
                    getContext.startActivity(intent);
                }
            });
        mLinkButtons.add(button);
        linLayout.addView(button);
    }

    private void applyOnOffStyle(Button button, String name, boolean on) {
        if (on) {
            button.setBackground(ModxTheme.withInteractiveRipple(
                    getContext,
                    ModxTheme.roundRect(getContext, ModxTheme.accentSoft(ToggleON), ModxTheme.accentLine(ToggleON), 1f, ModxTheme.RADIUS_DP),
                    ModxTheme.withAlpha(ToggleON, 0x24), ModxTheme.RADIUS_DP));
            button.setText(Html.fromHtml(name + "  <font color='" + hex(ToggleON) + "'><b>ON</b></font>"));
        } else {
            button.setBackground(ModxTheme.withInteractiveRipple(
                    getContext,
                    ModxTheme.cardSurface(getContext, ModxTheme.LINE),
                    0x1AFFFFFF, ModxTheme.RADIUS_DP));
            button.setText(Html.fromHtml(name + "  <font color='" + hex(ModxTheme.SUBTLE) + "'><b>OFF</b></font>"));
        }
    }

    private String modeSummaryFor(int featNum) {
        if (featNum == MODE_SIMPLE) return "Auto Revive · Unlimited Coins & Shop · Jump 35";
        if (featNum == MODE_MAX)    return "Simple + God Mode · Max Jump · Powerup · Fast Lane";
        if (featNum == MODE_ULTRA)  return "Max + Chams · Low Gravity · Follow Cam · Wall Hack";
        return "";
    }

    private void applyModeCardStyle(ModeButtonHolder h, boolean on) {
        if (h == null || h.row == null) return;
        if (on) {
            Drawable bg = ModxTheme.roundRect(
                    getContext, ModxTheme.accentSoft(COLOR_ACCENT),
                    ModxTheme.accentLine(COLOR_ACCENT), 1.5f, ModxTheme.RADIUS_DP);
            h.row.setBackground(ModxTheme.withInteractiveRipple(
                    getContext, bg, ModxTheme.withAlpha(COLOR_ACCENT, 0x24), ModxTheme.RADIUS_DP));
        } else {
            h.row.setBackground(ModxTheme.withInteractiveRipple(
                    getContext, ModxTheme.cardSurface(getContext, ModxTheme.LINE),
                    0x1AFFFFFF, ModxTheme.RADIUS_DP));
        }
        if (h.iconView != null) {
            h.iconView.setImageDrawable(on
                    ? ModxIcons.checkCircle(COLOR_ACCENT, dp(18))
                    : ModxIcons.circle(ModxTheme.SUBTLE, dp(18)));
        }
        if (h.titleView != null) {
            h.titleView.setTextColor(ModxTheme.FG);
        }
        if (h.summaryView != null) {
            h.summaryView.setTextColor(on ? ModxTheme.FG : ModxTheme.MUTED);
        }
        if (h.badgeView != null) {
            h.badgeView.setText(on ? "Active" : "Off");
            h.badgeView.setTextColor(on ? ModxTheme.onAccent(COLOR_ACCENT) : ModxTheme.SUBTLE);
            h.badgeView.setBackground(ModxTheme.pillBadgeBg(
                    getContext,
                    on ? COLOR_ACCENT : ModxTheme.SURFACE,
                    on ? COLOR_ACCENT : ModxTheme.LINE));
        }
    }

    private void ButtonOnOff(LinearLayout linLayout, final int featNum, String featName, final boolean switchedOn) {
        final String finalfeatName = featName.replace("OnOff_", "");
        final boolean isMode = (featNum == MODE_SIMPLE || featNum == MODE_MAX || featNum == MODE_ULTRA);
        final boolean isResetAll = (featNum == MODE_RESET);

        if (isMode) {
            final LinearLayout row = new LinearLayout(getContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(52));
            row.setLayoutParams(rowLp(6, 3, 6, 3));
            row.setPadding(dp(11), dp(9), dp(11), dp(9));
            addPressAnim(row);

            ImageView iconView = new ImageView(getContext);
            LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(18), dp(18));
            iLp.setMargins(0, 0, dp(9), 0);
            iconView.setLayoutParams(iLp);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            LinearLayout textCol = new LinearLayout(getContext);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

            TextView titleView = new TextView(getContext);
            titleView.setText(finalfeatName);
            titleView.setTextColor(ModxTheme.FG);
            titleView.setTypeface(fontBold);
            titleView.setTextSize(12.5f);
            titleView.setMaxLines(1);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setIncludeFontPadding(false);

            TextView summaryView = new TextView(getContext);
            summaryView.setText(modeSummaryFor(featNum));
            summaryView.setTextColor(ModxTheme.MUTED);
            summaryView.setTypeface(fontRegular);
            summaryView.setTextSize(10.5f);
            summaryView.setMaxLines(1);
            summaryView.setEllipsize(TextUtils.TruncateAt.END);
            summaryView.setIncludeFontPadding(false);
            summaryView.setPadding(0, dp(2), 0, 0);

            textCol.addView(titleView);
            textCol.addView(summaryView);

            TextView badgeView = new TextView(getContext);
            badgeView.setTag("no_scale");
            badgeView.setTypeface(fontBold);
            badgeView.setTextSize(10f);
            badgeView.setIncludeFontPadding(false);
            badgeView.setGravity(Gravity.CENTER);
            badgeView.setPadding(dp(8), dp(3), dp(8), dp(3));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            bLp.setMargins(dp(8), 0, 0, 0);
            badgeView.setLayoutParams(bLp);

            row.addView(iconView);
            row.addView(textCol);
            row.addView(badgeView);

            final boolean initialState = Preferences.readInitialBool(finalfeatName, featNum, switchedOn);
            final boolean[] state = new boolean[]{ initialState };

            final ModeButtonHolder h = new ModeButtonHolder();
            h.featNum     = featNum;
            h.name        = finalfeatName;
            h.row         = row;
            h.iconView    = iconView;
            h.titleView   = titleView;
            h.summaryView = summaryView;
            h.badgeView   = badgeView;
            h.state       = state;
            mModeButtons.put(featNum, h);
            applyModeCardStyle(h, initialState);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ModxTheme.hapticTap(v);
                    handleModeButtonToggle(h);
                }
            });
            linLayout.addView(row);
            return;
        }

        if (isResetAll) {
            final LinearLayout row = new LinearLayout(getContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(48));
            row.setLayoutParams(rowLp(6, 5, 6, 4));
            row.setPadding(dp(11), dp(9), dp(11), dp(9));
            row.setBackground(ModxTheme.dangerOutlineButtonBg(getContext, false));
            addPressAnim(row);

            final ImageView iconView = new ImageView(getContext);
            LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(18), dp(18));
            iLp.setMargins(0, 0, dp(9), 0);
            iconView.setLayoutParams(iLp);
            iconView.setImageDrawable(ModxIcons.refreshCw(ModxTheme.DANGER, dp(18)));

            LinearLayout textCol = new LinearLayout(getContext);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

            final TextView titleView = new TextView(getContext);
            titleView.setText(finalfeatName);
            titleView.setTextColor(ModxTheme.DANGER);
            titleView.setTypeface(fontBold);
            titleView.setTextSize(12.5f);
            titleView.setMaxLines(1);
            titleView.setIncludeFontPadding(false);

            final TextView subView = new TextView(getContext);
            subView.setText("Restores every toggle, slider & preset");
            subView.setTextColor(ModxTheme.MUTED);
            subView.setTypeface(fontRegular);
            subView.setTextSize(10.5f);
            subView.setMaxLines(1);
            subView.setEllipsize(TextUtils.TruncateAt.END);
            subView.setIncludeFontPadding(false);
            subView.setPadding(0, dp(2), 0, 0);

            textCol.addView(titleView);
            textCol.addView(subView);

            row.addView(iconView);
            row.addView(textCol);

            final boolean[] armed = new boolean[]{ false };
            final Runnable[] disarmHolder = new Runnable[1];
            final Runnable resetDoneRevert = new Runnable() {
                @Override
                public void run() {
                    if (row.getParent() != null) {
                        row.setBackground(ModxTheme.dangerOutlineButtonBg(getContext, false));
                        titleView.setText(finalfeatName);
                        titleView.setTextColor(ModxTheme.DANGER);
                        subView.setText("Restores every toggle, slider & preset");
                        iconView.setImageDrawable(ModxIcons.refreshCw(ModxTheme.DANGER, dp(18)));
                    }
                }
            };
            disarmHolder[0] = new Runnable() {
                @Override
                public void run() {
                    armed[0] = false;
                    resetDoneRevert.run();
                }
            };

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ModxTheme.hapticTap(v);
                    if (!armed[0]) {
                        armed[0] = true;
                        row.removeCallbacks(disarmHolder[0]);
                        row.setBackground(ModxTheme.dangerOutlineButtonBg(getContext, true));
                        titleView.setText("Tap again to confirm reset");
                        subView.setText("Clears all active toggles, sliders & presets");
                        row.postDelayed(disarmHolder[0], 2500L);
                        return;
                    }
                    armed[0] = false;
                    row.removeCallbacks(disarmHolder[0]);
                    resetAllFeaturesAction(row, titleView, subView, iconView, finalfeatName, resetDoneRevert);
                }
            });
            linLayout.addView(row);
            return;
        }

        final Button button = new Button(getContext);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTextColor(ModxTheme.FG);
        button.setAllCaps(false);
        button.setTypeface(fontMedium);
        button.setTextSize(12.5f);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        flatten(button);
        addPressAnim(button);

        final boolean initialState = Preferences.readInitialBool(finalfeatName, featNum, switchedOn);
        final boolean[] state = new boolean[]{ initialState };
        applyOnOffStyle(button, finalfeatName, state[0]);

        OtherButtonOnOffHolder oh = new OtherButtonOnOffHolder();
        oh.featNum    = featNum;
        oh.name       = finalfeatName;
        oh.button     = button;
        oh.state      = state;
        oh.defaultVal = switchedOn;
        mOtherButtonOnOffs.put(featNum, oh);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                state[0] = !state[0];
                Preferences.changeFeatureBool(finalfeatName, featNum, state[0]);
                applyOnOffStyle(button, finalfeatName, state[0]);
            }
        });
        linLayout.addView(button);
    }

    private void runOnMainThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }

    private void handleModeButtonToggle(final ModeButtonHolder holder) {
        if (holder == null) return;
        final int featNum = holder.featNum;
        final String featName = holder.name;
        final boolean[] state = holder.state;
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (isProgrammaticUpdate()) return;
                beginProgrammaticUpdate();
                Preferences.beginBatch();
                try {
                    boolean newState = !state[0];

                    if (newState) {
                        // Build lookup of features controlled by the NEW mode
                        PresetItem[] newItems = MODE_PRESETS.get(featNum);
                        HashMap<Integer, PresetItem> newItemMap = new HashMap<Integer, PresetItem>();
                        if (newItems != null) {
                            for (int i = 0; i < newItems.length; i++) {
                                newItemMap.put(newItems[i].featNum, newItems[i]);
                            }
                        }

                        // Step 1: Turn any other active Mode OFF in UI + Store (no Changes(other, false)),
                        // and reset ONLY features belonging to the old Mode that are NOT in the new Mode.
                        for (int fn : MODE_FEATNUMS) {
                            if (fn == featNum) continue;
                            ModeButtonHolder other = mModeButtons.get(fn);
                            if (other != null && other.state[0]) {
                                other.state[0] = false;
                                applyModeCardStyle(other, false);
                                Preferences.forceSyncBool(other.name, other.featNum, false, false);

                                PresetItem[] oldItems = MODE_PRESETS.get(fn);
                                if (oldItems != null) {
                                    for (int i = 0; i < oldItems.length; i++) {
                                        PresetItem oi = oldItems[i];
                                        if (!newItemMap.containsKey(oi.featNum)) {
                                            applyPresetItem(oi, false, true);
                                        }
                                    }
                                }
                            } else {
                                Preferences.forceSyncBool("", fn, false, false);
                            }
                        }

                        // Step 2: Update clicked Mode card (UI + Store + single native Changes(newMode, true))
                        state[0] = true;
                        applyModeCardStyle(holder, true);
                        Preferences.forceSyncBool(featName, featNum, true, true);

                        // Step 3: Reconcile all preset items in UI + Store (callNative=false; Changes(newMode, true) already set native!)
                        applyModeToMenuInternal(featNum, true, false);
                    } else {
                        // Turning active Mode OFF: UI + Store + single native Changes(featNum, false)
                        state[0] = false;
                        applyModeCardStyle(holder, false);
                        Preferences.forceSyncBool(featName, featNum, false, true);
                        applyModeToMenuInternal(featNum, false, false);
                    }
                } finally {
                    Preferences.endBatch(true);
                    endProgrammaticUpdate();
                }
            }
        });
    }

    private void applyPresetItem(PresetItem item, boolean enabled, boolean callNative) {
        if (item == null) return;
        int targetVal = enabled ? item.onValue : item.offValue;
        switch (item.type) {
            case PRESET_TOGGLE:
                setToggleState(item.featNum, targetVal != 0, true, callNative);
                break;
            case PRESET_SEEKBAR:
                setSeekBarValue(item.featNum, targetVal, true, callNative);
                break;
            case PRESET_SPINNER:
                setSpinnerValue(item.featNum, targetVal, true, callNative);
                break;
            case PRESET_COLOR:
                setColorPickerValue(item.featNum, targetVal, true, callNative);
                break;
        }
    }

    private void resetAllFeaturesAction(final LinearLayout resetRow, final TextView titleView,
                                        final TextView subView, final ImageView iconView,
                                        final String resetName, final Runnable revertRunnable) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (isProgrammaticUpdate()) return;
                beginProgrammaticUpdate();
                Preferences.beginBatch();
                try {
                    // 1. Turn all Mode cards OFF (UI + Store; native 803 resets all game globals)
                    for (int fn : MODE_FEATNUMS) {
                        ModeButtonHolder mh = mModeButtons.get(fn);
                        if (mh != null) {
                            mh.state[0] = false;
                            applyModeCardStyle(mh, false);
                            Preferences.forceSyncBool(mh.name, mh.featNum, false, false);
                        } else {
                            Preferences.forceSyncBool("", fn, false, false);
                        }
                    }

                    // 2. Reset any non-mode ButtonOnOff controls
                    for (OtherButtonOnOffHolder oh : mOtherButtonOnOffs.values()) {
                        setOtherButtonOnOffState(oh.featNum, oh.defaultVal, true, !isResetByNative803(oh.featNum));
                    }

                    // 3. Reset all Toggles (NEVER touch featNum -1 "Save features preference")
                    for (ToggleHolder th : mToggles.values()) {
                        if (th.featNum == -1) continue;
                        setToggleState(th.featNum, th.defaultVal, true, !isResetByNative803(th.featNum));
                    }

                    // 4. Reset all SeekBars to default
                    for (SeekBarHolder sb : mSeekBars.values()) {
                        setSeekBarValue(sb.featNum, sb.defaultVal, true, !isResetByNative803(sb.featNum));
                    }

                    // 5. Reset all Spinners to index 0 (native 803 resets 161, NOT 125/126)
                    for (SpinnerHolder sp : mSpinners.values()) {
                        setSpinnerValue(sp.featNum, 0, true, !isResetByNative803(sp.featNum));
                    }

                    // 6. Reset all CheckBoxes to default
                    for (CheckBoxHolder cb : mCheckBoxes.values()) {
                        setCheckBoxState(cb.featNum, cb.defaultVal, true, !isResetByNative803(cb.featNum));
                    }

                    // 7. Reset all RadioGroups to 0 (unselected)
                    for (RadioGroupHolder rg : mRadioGroups.values()) {
                        setRadioGroupValue(rg.featNum, 0, true, !isResetByNative803(rg.featNum));
                    }

                    // 8. Reset all ColorPickers to defaultColor
                    for (ColorPickerHolder cp : mColorPickers.values()) {
                        setColorPickerValue(cp.featNum, cp.defaultColor, true, !isResetByNative803(cp.featNum));
                    }

                    // 9. Reset all InputNum / InputLNum / InputText controls
                    for (InputHolder ih : mInputNums.values()) {
                        ih.currentValueText = "0";
                        ih.button.setText(Html.fromHtml(ih.featName + ": <font color='" + NumberTxtColor + "'>0</font>"));
                        Preferences.forceSyncInt(ih.featName, ih.featNum, 0, !isResetByNative803(ih.featNum));
                    }
                    for (InputHolder ih : mInputLNums.values()) {
                        ih.currentValueText = "0";
                        ih.button.setText(Html.fromHtml(ih.featName + ": <font color='" + NumberTxtColor + "'>0</font>"));
                        Preferences.forceSyncLong(ih.featName, ih.featNum, 0L, !isResetByNative803(ih.featNum));
                    }
                    for (InputHolder ih : mInputTexts.values()) {
                        ih.currentValueText = "";
                        ih.button.setText(Html.fromHtml(ih.featName + ": <font color='" + NumberTxtColor + "'></font>"));
                        Preferences.forceSyncString(ih.featName, ih.featNum, "", !isResetByNative803(ih.featNum));
                    }

                    // 10. Clear any stale feature keys in Store/Disk while preserving Save flag & Menu Color
                    Preferences.resetAllFeaturesInStore();
                    setTeleportPadVisible(false);

                    // 11. Trigger native Reset All (803) and keep 803 itself unlatched (OFF)
                    Preferences.triggerResetAllButton(resetName, MODE_RESET);
                } finally {
                    Preferences.endBatch(true);
                    endProgrammaticUpdate();
                }

                showMenuToast("All features reset");
                if (revertRunnable != null && resetRow != null) {
                    resetRow.removeCallbacks(revertRunnable);
                    resetRow.setBackground(ModxTheme.pillBadgeBg(
                            getContext, ModxTheme.badgeBg(ModxTheme.SUCCESS), ModxTheme.badgeBorder(ModxTheme.SUCCESS)));
                    if (titleView != null) {
                        titleView.setText("Done");
                        titleView.setTextColor(ModxTheme.SUCCESS);
                    }
                    if (subView != null) {
                        subView.setText("All features restored to defaults");
                    }
                    if (iconView != null) {
                        iconView.setImageDrawable(ModxIcons.checkCircle(ModxTheme.SUCCESS, dp(18)));
                    }
                    resetRow.postDelayed(revertRunnable, 900);
                }
            }
        });
    }

    // ================================================================
    // Telegram button — Sky blue (#229ED9) + Telegram paper-plane icon
    // ================================================================
    private void ButtonTelegram(LinearLayout linLayout, final String label, final String url) {
        final Button button = new Button(getContext);
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(fontBold);
        button.setTextSize(12.5f);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setText(label);
        button.setGravity(Gravity.CENTER);

        button.setBackground(ModxTheme.withInteractiveRipple(
                getContext,
                ModxTheme.roundRect(getContext, ModxTheme.TELEGRAM, ModxTheme.TELEGRAM_DEEP, 1f, ModxTheme.RADIUS_DP),
                0x26FFFFFF, ModxTheme.RADIUS_DP));

        button.setCompoundDrawablesWithIntrinsicBounds(
                ModxIcons.telegramPlane(Color.WHITE, dp(18)), null, null, null);
        button.setCompoundDrawablePadding(dp(8));

        flatten(button);
        addPressAnim(button);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setData(Uri.parse(url));
                    getContext.startActivity(intent);
                } catch (Exception ignored) { }
            }
        });
        linLayout.addView(button);
    }

    private void setToggleState(int featNum, boolean newState) {
        setToggleState(featNum, newState, false, true);
    }

    private void setToggleState(int featNum, boolean newState, boolean forceSync) {
        setToggleState(featNum, newState, forceSync, true);
    }

    private void setToggleState(int featNum, boolean newState, boolean forceSync, boolean callNative) {
        ToggleHolder h = mToggles.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncBool("", featNum, newState, callNative);
            }
            return;
        }
        beginProgrammaticUpdate();
        try {
            boolean prev = h.state[0];
            h.state[0] = newState;
            if (h.toggle.isChecked() != newState) {
                h.toggle.setChecked(newState, true);
            } else {
                h.toggle.setChecked(newState, false);
            }
            if (prev != newState) {
                animateStroke(h.rowBg,
                        newState ? ModxTheme.LINE : ModxTheme.accentLine(ToggleON),
                        newState ? ModxTheme.accentLine(ToggleON) : ModxTheme.LINE);
            } else if (h.rowBg != null) {
                h.rowBg.setStroke(dp(1), newState ? ModxTheme.accentLine(ToggleON) : ModxTheme.LINE);
            }
            if (featNum == 710) setTeleportPadVisible(newState);
        } finally {
            endProgrammaticUpdate();
        }
        if (forceSync) {
            Preferences.forceSyncBool(h.featName, featNum, newState, callNative);
        } else {
            Preferences.changeFeatureBool(h.featName, featNum, newState);
        }
    }

    private void setOtherButtonOnOffState(int featNum, boolean newState, boolean forceSync, boolean callNative) {
        OtherButtonOnOffHolder h = mOtherButtonOnOffs.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncBool("", featNum, newState, callNative);
            }
            return;
        }
        h.state[0] = newState;
        applyOnOffStyle(h.button, h.name, newState);
        if (forceSync) {
            Preferences.forceSyncBool(h.name, featNum, newState, callNative);
        } else {
            Preferences.changeFeatureBool(h.name, featNum, newState);
        }
    }

    private void setCheckBoxState(int featNum, boolean newState, boolean forceSync) {
        setCheckBoxState(featNum, newState, forceSync, true);
    }

    private void setCheckBoxState(int featNum, boolean newState, boolean forceSync, boolean callNative) {
        CheckBoxHolder h = mCheckBoxes.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncBool("", featNum, newState, callNative);
            }
            return;
        }
        beginProgrammaticUpdate();
        try {
            boolean prev = h.checkBox.isChecked();
            h.checkBox.setChecked(newState);
            if (prev != newState) {
                animateStroke(h.bg,
                        newState ? ModxTheme.LINE : ModxTheme.accentLine(CheckBoxColor),
                        newState ? ModxTheme.accentLine(CheckBoxColor) : ModxTheme.LINE);
            } else if (h.bg != null) {
                h.bg.setStroke(dp(1), newState ? ModxTheme.accentLine(CheckBoxColor) : ModxTheme.LINE);
            }
        } finally {
            endProgrammaticUpdate();
        }
        if (forceSync) {
            Preferences.forceSyncBool(h.featName, featNum, newState, callNative);
        } else {
            Preferences.changeFeatureBool(h.featName, featNum, newState);
        }
    }

    private void setSeekBarValue(int featNum, int value) {
        setSeekBarValue(featNum, value, false, true);
    }

    private void setSeekBarValue(int featNum, int value, boolean forceSync) {
        setSeekBarValue(featNum, value, forceSync, true);
    }

    private void setSeekBarValue(int featNum, int value, boolean forceSync, boolean callNative) {
        SeekBarHolder h = mSeekBars.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncInt("", featNum, value, callNative);
            }
            return;
        }
        int clamped = value;
        if (clamped < h.min) clamped = h.min;
        if (clamped > h.max) clamped = h.max;
        beginProgrammaticUpdate();
        try {
            h.seekBar.setProgress(clamped);
            int disp = (featNum == 103) ? (clamped + 30) : clamped;
            h.chip.setText(String.valueOf(disp));
        } finally {
            endProgrammaticUpdate();
        }
        if (forceSync) {
            Preferences.forceSyncInt(h.featName, featNum, clamped, callNative);
        } else {
            Preferences.changeFeatureInt(h.featName, featNum, clamped);
        }
    }

    private void setSpinnerValue(int featNum, int index) {
        setSpinnerValue(featNum, index, false, true);
    }

    private void setSpinnerValue(int featNum, int index, boolean forceSync) {
        setSpinnerValue(featNum, index, forceSync, true);
    }

    private void setSpinnerValue(int featNum, int index, boolean forceSync, boolean callNative) {
        SpinnerHolder h = mSpinners.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncInt("", featNum, index, callNative);
            }
            return;
        }
        if (h.items == null || h.items.length == 0) return;
        int clamped = Math.max(0, Math.min(index, h.items.length - 1));

        h.currentIdx = clamped;
        h.nameView.setText(h.featName + ": " + h.items[clamped]);
        for (int j = 0; j < h.dropdownList.getChildCount(); j++) {
            View child = h.dropdownList.getChildAt(j);
            if (child instanceof TextView) {
                TextView t = (TextView) child;
                if (j == clamped) {
                    t.setTextColor(COLOR_ACCENT);
                    t.setBackground(ModxTheme.roundRect(getContext, ModxTheme.accentSoft(COLOR_ACCENT), 0, 0f, 4f));
                } else {
                    t.setTextColor(ModxTheme.FG);
                    t.setBackground(null);
                }
            }
        }
        if (forceSync) {
            Preferences.forceSyncInt(h.featName, featNum, clamped, callNative);
        } else {
            Preferences.changeFeatureInt(h.featName, featNum, clamped);
        }
    }

    private void setRadioGroupValue(int featNum, int index, boolean forceSync, boolean callNative) {
        RadioGroupHolder h = mRadioGroups.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncInt("", featNum, index, callNative);
            }
            return;
        }
        beginProgrammaticUpdate();
        int resolvedIndex = index;
        try {
            if (resolvedIndex <= 0 || resolvedIndex >= h.radioGroup.getChildCount()) {
                resolvedIndex = 0;
                h.radioGroup.clearCheck();
                h.selectedName = null;
                h.currentIndex = 0;
                h.titleView.setText(h.featName + ":");
            } else {
                View child = h.radioGroup.getChildAt(resolvedIndex);
                if (child instanceof RadioButton) {
                    ((RadioButton) child).setChecked(true);
                    String radioName = ((RadioButton) child).getText().toString();
                    h.selectedName = radioName;
                    h.currentIndex = resolvedIndex;
                    h.titleView.setText(Html.fromHtml(h.featName + ": <font color='" + NumberTxtColor + "'>"
                            + TextUtils.htmlEncode(radioName) + "</font>"));
                } else {
                    resolvedIndex = 0;
                    h.radioGroup.clearCheck();
                    h.selectedName = null;
                    h.currentIndex = 0;
                    h.titleView.setText(h.featName + ":");
                }
            }
        } finally {
            endProgrammaticUpdate();
        }
        if (forceSync) {
            Preferences.forceSyncInt(h.featName, featNum, resolvedIndex, callNative);
        } else {
            Preferences.changeFeatureInt(h.featName, featNum, resolvedIndex);
        }
    }

    private void setColorPickerValue(int featNum, int color, boolean forceSync) {
        setColorPickerValue(featNum, color, forceSync, true);
    }

    private void setColorPickerValue(int featNum, int color, boolean forceSync, boolean callNative) {
        ColorPickerHolder h = mColorPickers.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncColor("", featNum, color, callNative);
            }
            return;
        }
        h.currentColor = color;
        applyColorButton(h, color);
        if (forceSync) {
            Preferences.forceSyncColor(h.featName, featNum, color, callNative);
        } else {
            Preferences.changeFeatureColor(h.featName, featNum, color);
        }
    }

    private void applyModeToMenu(final int modeFeatNum, final boolean enabled) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (isProgrammaticUpdate()) return;
                beginProgrammaticUpdate();
                Preferences.beginBatch();
                try {
                    applyModeToMenuInternal(modeFeatNum, enabled, true);
                } finally {
                    Preferences.endBatch(true);
                    endProgrammaticUpdate();
                }
            }
        });
    }

    private void applyModeToMenuInternal(int modeFeatNum, boolean enabled, boolean callNative) {
        PresetItem[] items = MODE_PRESETS.get(modeFeatNum);
        if (items == null) return;
        for (int i = 0; i < items.length; i++) {
            applyPresetItem(items[i], enabled, callNative);
        }
    }
    // ================================================================
    // Inline Dropdown (ModX Theme)
    // ================================================================
    private void Spinner(LinearLayout linLayout, final int featNum, final String featName, final String list) {
        final Context ctx = getContext;
        final List<String> lists = new LinkedList<String>(Arrays.asList(list.split(",")));

        final LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(rowLp(6, 3, 6, 3));

        final LinearLayout spinnerRow = new LinearLayout(ctx);
        spinnerRow.setBackground(ModxTheme.withInteractiveRipple(
                ctx, ModxTheme.cardSurface(ctx, ModxTheme.LINE),
                ModxTheme.withAlpha(COLOR_ACCENT, 0x1A), ModxTheme.RADIUS_DP));
        spinnerRow.setGravity(Gravity.CENTER_VERTICAL);
        spinnerRow.setMinimumHeight(dp(42));
        spinnerRow.setPadding(dp(12), dp(10), dp(12), dp(10));
        spinnerRow.setOrientation(LinearLayout.HORIZONTAL);

        final TextView tvName = new TextView(ctx);
        tvName.setTextColor(ModxTheme.FG);
        tvName.setTypeface(fontMedium);
        tvName.setTextSize(12.5f);
        tvName.setIncludeFontPadding(false);
        tvName.setMaxLines(1);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        final ImageView arrowIv = new ImageView(ctx);
        final SvgIcon chevIcon = ModxIcons.chevronDown(COLOR_ACCENT, dp(18));
        arrowIv.setImageDrawable(chevIcon);
        arrowIv.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));

        spinnerRow.addView(tvName);
        spinnerRow.addView(arrowIv);

        final int savedIdx = Preferences.readInitialInt(featName, featNum, 0);
        final int initIdx = (savedIdx >= 0 && savedIdx < lists.size()) ? savedIdx : 0;
        tvName.setText(featName + ": " + lists.get(initIdx));

        final LinearLayout dropdownList = new LinearLayout(ctx);
        dropdownList.setOrientation(LinearLayout.VERTICAL);
        dropdownList.setVisibility(View.GONE);
        dropdownList.setBackground(ModxTheme.roundRect(
                ctx, ModxTheme.SURFACE, ModxTheme.LINE, 1f, ModxTheme.RADIUS_DP));
        dropdownList.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        listLp.setMargins(0, dp(4), 0, 0);
        dropdownList.setLayoutParams(listLp);

        for (int i = 0; i < lists.size(); i++) {
            final int index = i;
            TextView tv = new TextView(ctx);
            tv.setText(lists.get(i));
            tv.setTextColor(ModxTheme.FG);
            tv.setTypeface(fontMedium);
            tv.setTextSize(12f);
            tv.setIncludeFontPadding(false);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setMinimumHeight(dp(40));
            tv.setPadding(dp(12), dp(9), dp(12), dp(9));

            if (index == initIdx) {
                tv.setTextColor(COLOR_ACCENT);
                tv.setBackground(ModxTheme.roundRect(ctx, ModxTheme.accentSoft(COLOR_ACCENT), 0, 0f, 4f));
            }

            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ModxTheme.hapticTap(v);
                    setSpinnerValue(featNum, index);
                    dropdownList.setVisibility(View.GONE);
                    arrowIv.animate().rotation(0f).setDuration(200).start();
                }
            });
            dropdownList.addView(tv);
        }

        spinnerRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ModxTheme.hapticTap(v);
                if (dropdownList.getVisibility() == View.VISIBLE) {
                    dropdownList.setVisibility(View.GONE);
                    arrowIv.animate().rotation(0f).setDuration(200).start();
                } else {
                    dropdownList.setVisibility(View.VISIBLE);
                    arrowIv.animate().rotation(180f).setDuration(200).start();
                }
            }
        });
        addPressAnim(spinnerRow);

        SpinnerHolder spHolder = new SpinnerHolder();
        spHolder.featNum      = featNum;
        spHolder.featName     = featName;
        spHolder.nameView     = tvName;
        spHolder.dropdownList = dropdownList;
        spHolder.chevIcon     = chevIcon;
        spHolder.items        = lists.toArray(new String[0]);
        spHolder.currentIdx   = initIdx;
        mSpinners.put(featNum, spHolder);

        container.addView(spinnerRow);
        container.addView(dropdownList);
        linLayout.addView(container);
    }

    private void InputNum(LinearLayout linLayout, final int featNum, final String featName, final int maxValue) {
        final Button button = new Button(getContext);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        int num = Preferences.readInitialInt(featName, featNum, 0);
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
        button.setAllCaps(false);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTypeface(fontMedium);
        button.setTextSize(12.5f);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(ModxTheme.withInteractiveRipple(
                getContext, ModxTheme.cardSurface(getContext, ModxTheme.LINE),
                ModxTheme.withAlpha(COLOR_ACCENT, 0x1A), ModxTheme.RADIUS_DP));
        button.setTextColor(ModxTheme.FG);
        flatten(button);
        addPressAnim(button);
        final InputHolder inHolder = new InputHolder();
        inHolder.featNum          = featNum;
        inHolder.featName         = featName;
        inHolder.button           = button;
        inHolder.currentValueText = String.valueOf(num);
        mInputNums.put(featNum, inHolder);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ModxTheme.hapticTap(view);
                    final EditText editText = makeEditText();
                    if (maxValue != 0) editText.setHint("Max: " + maxValue);
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    editText.setKeyListener(DigitsKeyListener.getInstance("0123456789-"));
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(11)});
                    if (!TextUtils.isEmpty(inHolder.currentValueText)) {
                        editText.setText(inHolder.currentValueText);
                        editText.setSelection(editText.getText().length());
                    }
                    showInputDialog("Input number", editText, new Runnable() {
                            @Override
                            public void run() {
                                int parsed;
                                String inp = editText.getText().toString().trim();
                                if (inp.isEmpty()) {
                                    parsed = 0;
                                } else if ("-".equals(inp)) {
                                    try { parsed = Integer.parseInt(inHolder.currentValueText); }
                                    catch (NumberFormatException ex) { parsed = 0; }
                                } else {
                                    try {
                                        parsed = Integer.parseInt(inp);
                                        if (maxValue != 0 && parsed > maxValue) parsed = maxValue;
                                    } catch (NumberFormatException ex) {
                                        if (inp.startsWith("-")) {
                                            parsed = Integer.MIN_VALUE;
                                        } else {
                                            parsed = maxValue != 0 ? maxValue : Integer.MAX_VALUE;
                                        }
                                    }
                                }
                                inHolder.currentValueText = String.valueOf(parsed);
                                button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + parsed + "</font>"));
                                Preferences.changeFeatureInt(featName, featNum, parsed);
                            }
                        });
                }
            });
        linLayout.addView(button);
    }

    private void InputLNum(LinearLayout linLayout, final int featNum, final String featName, final long maxValue) {
        final Button button = new Button(getContext);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        long num = Preferences.readInitialLong(featName, featNum, 0L);
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
        button.setAllCaps(false);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTypeface(fontMedium);
        button.setTextSize(12.5f);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(ModxTheme.withInteractiveRipple(
                getContext, ModxTheme.cardSurface(getContext, ModxTheme.LINE),
                ModxTheme.withAlpha(COLOR_ACCENT, 0x1A), ModxTheme.RADIUS_DP));
        button.setTextColor(ModxTheme.FG);
        flatten(button);
        addPressAnim(button);
        final InputHolder ilHolder = new InputHolder();
        ilHolder.featNum          = featNum;
        ilHolder.featName         = featName;
        ilHolder.button           = button;
        ilHolder.currentValueText = String.valueOf(num);
        mInputLNums.put(featNum, ilHolder);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ModxTheme.hapticTap(view);
                    final EditText editText = makeEditText();
                    if (maxValue != 0) editText.setHint("Max: " + maxValue);
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    editText.setKeyListener(DigitsKeyListener.getInstance("0123456789-"));
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
                    if (!TextUtils.isEmpty(ilHolder.currentValueText)) {
                        editText.setText(ilHolder.currentValueText);
                        editText.setSelection(editText.getText().length());
                    }
                    showInputDialog("Input number", editText, new Runnable() {
                            @Override
                            public void run() {
                                long parsed;
                                String inp = editText.getText().toString().trim();
                                if (inp.isEmpty()) {
                                    parsed = 0L;
                                } else if ("-".equals(inp)) {
                                    try { parsed = Long.parseLong(ilHolder.currentValueText); }
                                    catch (NumberFormatException ex) { parsed = 0L; }
                                } else {
                                    try {
                                        parsed = Long.parseLong(inp);
                                        if (maxValue != 0 && parsed > maxValue) parsed = maxValue;
                                    } catch (NumberFormatException ex) {
                                        if (inp.startsWith("-")) {
                                            parsed = Long.MIN_VALUE;
                                        } else {
                                            parsed = maxValue != 0 ? maxValue : Long.MAX_VALUE;
                                        }
                                    }
                                }
                                ilHolder.currentValueText = String.valueOf(parsed);
                                button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + parsed + "</font>"));
                                Preferences.changeFeatureLong(featName, featNum, parsed);
                            }
                        });
                }
            });
        linLayout.addView(button);
    }

    private void InputText(LinearLayout linLayout, final int featNum, final String featName) {
        final Button button = new Button(getContext);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(dp(42));
        String string = Preferences.readInitialString(featName, featNum, "");
        String safeText = (string != null) ? string : "";
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>"
                + TextUtils.htmlEncode(safeText) + "</font>"));
        button.setAllCaps(false);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTypeface(fontMedium);
        button.setTextSize(12.5f);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setBackground(ModxTheme.withInteractiveRipple(
                getContext, ModxTheme.cardSurface(getContext, ModxTheme.LINE),
                ModxTheme.withAlpha(COLOR_ACCENT, 0x1A), ModxTheme.RADIUS_DP));
        button.setTextColor(ModxTheme.FG);
        flatten(button);
        addPressAnim(button);
        final InputHolder itHolder = new InputHolder();
        itHolder.featNum          = featNum;
        itHolder.featName         = featName;
        itHolder.button           = button;
        itHolder.currentValueText = safeText;
        mInputTexts.put(featNum, itHolder);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ModxTheme.hapticTap(view);
                    final EditText editText = makeEditText();
                    if (!TextUtils.isEmpty(itHolder.currentValueText)) {
                        editText.setText(itHolder.currentValueText);
                        editText.setSelection(editText.getText().length());
                    }
                    showInputDialog("Input text", editText, new Runnable() {
                            @Override
                            public void run() {
                                String str = editText.getText().toString();
                                itHolder.currentValueText = str;
                                button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>"
                                        + TextUtils.htmlEncode(str) + "</font>"));
                                Preferences.changeFeatureString(featName, featNum, str);
                            }
                        });
                }
            });
        linLayout.addView(button);
    }

    private void CheckBox(LinearLayout linLayout, final int featNum, final String featName, boolean switchedOn) {
        final CheckBox checkBox = new CheckBox(getContext);
        boolean initial = Preferences.readInitialBool(featName, featNum, switchedOn);
        final GradientDrawable cbBg = ModxTheme.roundRect(
                getContext, ModxTheme.CARD,
                initial ? ModxTheme.accentLine(CheckBoxColor) : ModxTheme.LINE,
                1f, ModxTheme.RADIUS_DP);
        checkBox.setLayoutParams(rowLp(6, 3, 6, 3));
        checkBox.setBackground(ModxTheme.withInteractiveRipple(
                getContext, cbBg, ModxTheme.withAlpha(CheckBoxColor, 0x1A), ModxTheme.RADIUS_DP));
        checkBox.setText(featName);
        checkBox.setTextColor(ModxTheme.FG);
        checkBox.setTypeface(fontMedium);
        checkBox.setTextSize(12.5f);
        checkBox.setMinimumHeight(dp(42));
        checkBox.setPadding(dp(10), dp(9), dp(10), dp(9));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) checkBox.setButtonTintList(ColorStateList.valueOf(CheckBoxColor));
        checkBox.setChecked(initial);

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton bv, boolean isChecked) {
                    if (!isProgrammaticUpdate()) {
                        ModxTheme.hapticTap(bv);
                        animateStroke(cbBg,
                                isChecked ? ModxTheme.LINE : ModxTheme.accentLine(CheckBoxColor),
                                isChecked ? ModxTheme.accentLine(CheckBoxColor) : ModxTheme.LINE);
                        Preferences.changeFeatureBool(featName, featNum, isChecked);
                    }
                }
            });
        CheckBoxHolder cbHolder = new CheckBoxHolder();
        cbHolder.featNum    = featNum;
        cbHolder.featName   = featName;
        cbHolder.checkBox   = checkBox;
        cbHolder.bg         = cbBg;
        cbHolder.defaultVal = switchedOn;
        mCheckBoxes.put(featNum, cbHolder);
        linLayout.addView(checkBox);
    }

    private void RadioButton(LinearLayout linLayout, final int featNum, String featName, final String list) {
        final List<String> lists = new LinkedList<String>(Arrays.asList(list.split(",")));
        final TextView textView = new TextView(getContext);
        textView.setText(featName + ":");
        textView.setTextColor(ModxTheme.FG);
        textView.setTypeface(fontBold);
        textView.setTextSize(12.5f);
        textView.setPadding(0, dp(2), 0, dp(4));
        final RadioGroup radioGroup = new RadioGroup(getContext);
        radioGroup.setLayoutParams(rowLp(6, 3, 6, 3));
        radioGroup.setBackground(ModxTheme.cardSurface(getContext, ModxTheme.LINE));
        radioGroup.setPadding(dp(12), dp(8), dp(12), dp(8));
        radioGroup.setOrientation(LinearLayout.VERTICAL);
        radioGroup.addView(textView);
        final RadioGroupHolder rgHolder = new RadioGroupHolder();
        rgHolder.featNum    = featNum;
        rgHolder.featName   = featName;
        rgHolder.radioGroup = radioGroup;
        rgHolder.titleView  = textView;
        rgHolder.currentIndex = 0;
        for (int i = 0; i < lists.size(); i++) {
            final RadioButton Radioo = new RadioButton(getContext);
            final String finalfeatName = featName, radioName = lists.get(i);
            View.OnClickListener rl = new View.OnClickListener() {
                public void onClick(View v) {
                    if (isProgrammaticUpdate()) return;
                    ModxTheme.hapticTap(v);
                    int idx = radioGroup.indexOfChild(Radioo);
                    rgHolder.selectedName = radioName;
                    rgHolder.currentIndex = idx;
                    textView.setText(Html.fromHtml(finalfeatName + ": <font color='" + NumberTxtColor + "'>"
                            + TextUtils.htmlEncode(radioName) + "</font>"));
                    Preferences.changeFeatureInt(finalfeatName, featNum, idx);
                }
            };
            Radioo.setText(lists.get(i));
            Radioo.setTextColor(ModxTheme.MUTED);
            Radioo.setTypeface(fontRegular);
            Radioo.setTextSize(12f);
            Radioo.setMinimumHeight(dp(40));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) Radioo.setButtonTintList(ColorStateList.valueOf(RadioColor));
            Radioo.setOnClickListener(rl);
            radioGroup.addView(Radioo);
        }
        int index = Preferences.readInitialInt(featName, featNum, 0);
        if (index > 0 && index <= lists.size() && index < radioGroup.getChildCount()) {
            rgHolder.selectedName = lists.get(index - 1);
            rgHolder.currentIndex = index;
            textView.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>"
                    + TextUtils.htmlEncode(lists.get(index - 1)) + "</font>"));
            ((RadioButton) radioGroup.getChildAt(index)).setChecked(true);
        }
        mRadioGroups.put(featNum, rgHolder);
        linLayout.addView(radioGroup);
    }

    // Smooth height animation for collapsible sections
    private void animateSection(final View sub, boolean expand) {
        Object old = sub.getTag();
        if (old instanceof ValueAnimator) {
            ((ValueAnimator) old).removeAllListeners();
            ((ValueAnimator) old).cancel();
        }
        final ViewGroup.LayoutParams lp = sub.getLayoutParams();
        View parent = (View) sub.getParent();
        int from = sub.getVisibility() == View.VISIBLE ? sub.getHeight() : 0;
        int to = 0;
        if (expand) {
            int pw = parent != null ? parent.getWidth() : 0;
            if (pw <= 0) {
                sub.setVisibility(View.VISIBLE);
                lp.height = WRAP_CONTENT;
                sub.requestLayout();
                return;
            }
            sub.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            to = sub.getMeasuredHeight();
            lp.height = from;
            sub.setVisibility(View.VISIBLE);
            sub.requestLayout();
        }

        final boolean exp = expand;
        ValueAnimator va = ValueAnimator.ofInt(from, to);
        va.setDuration(230);
        va.setInterpolator(new DecelerateInterpolator(1.5f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    lp.height = (Integer) a.getAnimatedValue();
                    sub.requestLayout();
                }
            });
        va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    lp.height = WRAP_CONTENT;
                    if (!exp) sub.setVisibility(View.GONE);
                    sub.requestLayout();
                    sub.setTag(null);
                }
            });
        sub.setTag(va);
        va.start();
    }

    private void Collapse(LinearLayout linLayout, final String text, final boolean expanded) {
        LinearLayout.LayoutParams llp = rowLp(6, 5, 6, 3);
        LinearLayout collapse = new LinearLayout(getContext);
        collapse.setLayoutParams(llp);
        collapse.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout collapseSub = new LinearLayout(getContext);
        collapseSub.setPadding(0, dp(4), 0, dp(2));
        collapseSub.setOrientation(LinearLayout.VERTICAL);
        collapseSub.setVisibility(View.GONE);
        mCollapse = collapseSub;

        LinearLayout headRow = new LinearLayout(getContext);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.setMinimumHeight(dp(42));
        headRow.setBackground(ModxTheme.withInteractiveRipple(
                getContext, ModxTheme.cardSurface(getContext, ModxTheme.LINE),
                ModxTheme.withAlpha(COLOR_ACCENT, 0x1A), ModxTheme.RADIUS_DP));

        final TextView textView = new TextView(getContext);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        textView.setText(text);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextColor(ModxTheme.FG);
        textView.setTypeface(fontBold);
        textView.setTextSize(12.5f);
        textView.setIncludeFontPadding(false);
        textView.setPadding(dp(12), dp(11), dp(6), dp(11));

        final ImageView chevron = new ImageView(getContext);
        SvgIcon chevIcon = ModxIcons.chevronDown(COLOR_ACCENT, dp(18));
        chevron.setImageDrawable(chevIcon);
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        chLp.setMargins(0, 0, dp(10), 0);
        chevron.setLayoutParams(chLp);

        if (expanded) {
            collapseSub.setVisibility(View.VISIBLE);
            chevron.setRotation(180f);
        }

        headRow.setOnClickListener(new View.OnClickListener() {
                boolean isChecked = expanded;
                @Override
                public void onClick(View v) {
                    ModxTheme.hapticTap(v);
                    isChecked = !isChecked;
                    chevron.animate().rotation(isChecked ? 180f : 0f).setDuration(230).setInterpolator(new DecelerateInterpolator()).start();
                    animateSection(collapseSub, isChecked);
                }
            });
        addPressAnim(headRow);

        CollapseHolder ch = new CollapseHolder();
        ch.headRow   = headRow;
        ch.titleView = textView;
        ch.chevIcon  = chevIcon;
        mCollapseHolders.add(ch);

        headRow.addView(textView);
        headRow.addView(chevron);
        collapse.addView(headRow);
        collapse.addView(collapseSub);
        linLayout.addView(collapse);
    }

    private void Category(LinearLayout linLayout, String text) {
        LinearLayout wrap = new LinearLayout(getContext);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setLayoutParams(rowLp(6, 4, 6, 2));

        TextView textView = new TextView(getContext);
        String plain = Html.fromHtml(text).toString().trim().toUpperCase(java.util.Locale.US);
        textView.setText(plain);
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setTextColor(ModxTheme.SUBTLE);
        textView.setTypeface(fontBold);
        textView.setTextSize(10.5f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textView.setLetterSpacing(0.06f);
        }
        textView.setIncludeFontPadding(false);
        textView.setPadding(dp(2), dp(8), dp(2), dp(4));

        View divider = new View(getContext);
        divider.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, Math.max(1, dp(1))));
        divider.setBackgroundColor(ModxTheme.HAIRLINE);

        mCategoryViews.add(textView);
        mCategoryLines.add(divider);
        wrap.addView(textView);
        wrap.addView(divider);
        linLayout.addView(wrap);
    }

    private void TextView(LinearLayout linLayout, String text) {
        TextView(linLayout, -1, text, false);
    }

    private void TextView(LinearLayout linLayout, int featNum, String text, boolean small) {
        if (featNum == 992) {
            LinearLayout accordion = new LinearLayout(getContext);
            accordion.setOrientation(LinearLayout.VERTICAL);
            accordion.setLayoutParams(rowLp(6, 6, 6, 4));
            accordion.setBackground(ModxTheme.cardSurface(getContext, ModxTheme.LINE));

            LinearLayout headRow = new LinearLayout(getContext);
            headRow.setOrientation(LinearLayout.HORIZONTAL);
            headRow.setGravity(Gravity.CENTER_VERTICAL);
            headRow.setMinimumHeight(dp(40));
            headRow.setPadding(dp(11), dp(8), dp(10), dp(8));

            ImageView infoIv = new ImageView(getContext);
            infoIv.setImageDrawable(ModxIcons.info(ModxTheme.INFO, dp(15)));
            LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(15), dp(15));
            iLp.setMargins(0, 0, dp(8), 0);
            infoIv.setLayoutParams(iLp);

            TextView headTitle = new TextView(getContext);
            headTitle.setText("What do the modes do?");
            headTitle.setTextColor(ModxTheme.MUTED);
            headTitle.setTypeface(fontMedium);
            headTitle.setTextSize(11.5f);
            headTitle.setIncludeFontPadding(false);
            headTitle.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

            final ImageView chevIv = new ImageView(getContext);
            chevIv.setImageDrawable(ModxIcons.chevronDown(ModxTheme.SUBTLE, dp(16)));
            chevIv.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));

            headRow.addView(infoIv);
            headRow.addView(headTitle);
            headRow.addView(chevIv);

            final LinearLayout bodyWrap = new LinearLayout(getContext);
            bodyWrap.setOrientation(LinearLayout.VERTICAL);
            bodyWrap.setVisibility(View.GONE);
            bodyWrap.setPadding(dp(11), 0, dp(11), dp(10));

            String cleaned = text == null ? "" : text.replaceFirst("^(<br\\s*/?>\\s*)+", "");
            cleaned = cleaned.replace("#3DDB87", ModxTheme.hex6(COLOR_ACCENT));
            TextView bodyTv = new TextView(getContext);
            bodyTv.setText(Html.fromHtml(cleaned));
            bodyTv.setTextColor(ModxTheme.MUTED);
            bodyTv.setTypeface(fontRegular);
            bodyTv.setTextSize(10.5f);
            bodyTv.setLineSpacing(dpf(2f), 1.0f);
            bodyWrap.addView(bodyTv);

            addPressAnim(headRow);
            headRow.setOnClickListener(new View.OnClickListener() {
                boolean open = false;
                @Override
                public void onClick(View v) {
                    ModxTheme.hapticTap(v);
                    open = !open;
                    chevIv.animate().rotation(open ? 180f : 0f).setDuration(200).start();
                    animateSection(bodyWrap, open);
                }
            });

            accordion.addView(headRow);
            accordion.addView(bodyWrap);
            linLayout.addView(accordion);
            return;
        }

        if (featNum == 993 || featNum == 996) {
            final boolean isInfo = (featNum == 993);
            int tone = isInfo ? ModxTheme.INFO : ModxTheme.WARNING;

            LinearLayout strip = new LinearLayout(getContext);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            strip.setGravity(Gravity.CENTER_VERTICAL);
            strip.setLayoutParams(rowLp(6, 4, 6, 4));
            strip.setPadding(dp(10), dp(8), dp(10), dp(8));
            strip.setBackground(ModxTheme.withInteractiveRipple(
                    getContext,
                    ModxTheme.pillBadgeBg(getContext, ModxTheme.badgeBg(tone), ModxTheme.badgeBorder(tone)),
                    ModxTheme.withAlpha(tone, 0x22), ModxTheme.RADIUS_DP));

            ImageView iconIv = new ImageView(getContext);
            iconIv.setImageDrawable(isInfo ? ModxIcons.info(tone, dp(15)) : ModxIcons.alertCircle(tone, dp(15)));
            LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(15), dp(15));
            iLp.setMargins(0, 0, dp(8), 0);
            iconIv.setLayoutParams(iLp);

            String cleaned = text == null ? "" : text.replace("⚠ ", "").replace("⚠", "");
            cleaned = cleaned.replace("#3DDB87", ModxTheme.hex6(COLOR_ACCENT));
            cleaned = cleaned.replace("#8D8F99", ModxTheme.hex6(ModxTheme.MUTED));
            TextView tv = new TextView(getContext);
            tv.setText(Html.fromHtml(cleaned));
            tv.setTextColor(ModxTheme.FG);
            tv.setTypeface(fontRegular);
            tv.setTextSize(10.5f);
            tv.setLineSpacing(dpf(1.5f), 1.0f);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

            strip.addView(iconIv);
            strip.addView(tv);

            if (isInfo) {
                addPressAnim(strip);
                strip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ModxTheme.hapticTap(v);
                        selectTabByName("Camera");
                    }
                });
            }
            linLayout.addView(strip);
            return;
        }

        TextView textView = new TextView(getContext);
        textView.setText(Html.fromHtml(text));
        textView.setTextColor(ModxTheme.MUTED);
        textView.setTypeface(fontRegular);
        textView.setTextSize(small ? 10.5f : 12f);
        textView.setPadding(dp(10), dp(5), dp(10), dp(5));
        linLayout.addView(textView);
    }

    private void WebTextView(LinearLayout linLayout, String text) {
        WebView wView = new WebView(getContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    ViewParent parent = view.getParent();
                    if (parent instanceof ViewGroup) {
                        ((ViewGroup) parent).removeView(view);
                    }
                    view.destroy();
                    return true;
                }
            });
        }
        wView.loadData(text, "text/html", "utf-8");
        wView.setBackgroundColor(0x00000000);
        wView.setPadding(0, dp(4), 0, dp(4));
        wView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        linLayout.addView(wView);
    }

    static String actionName(int a) {
        switch (a) {
            case MotionEvent.ACTION_DOWN:    return "DOWN";
            case MotionEvent.ACTION_UP:      return "UP";
            case MotionEvent.ACTION_MOVE:    return "MOVE";
            case MotionEvent.ACTION_CANCEL:  return "CANCEL";
            case MotionEvent.ACTION_OUTSIDE: return "OUTSIDE";
            default: return "OTHER_" + a;
        }
    }

    private static int darken(int color, float factor) {
        int r = (int)(Color.red(color) * factor);
        int g = (int)(Color.green(color) * factor);
        int b = (int)(Color.blue(color) * factor);
        return Color.argb(Color.alpha(color),
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b)));
    }

    private static int lighten(int color, float factor) {
        int r = Color.red(color) + (int)((255 - Color.red(color)) * factor);
        int g = Color.green(color) + (int)((255 - Color.green(color)) * factor);
        int b = Color.blue(color) + (int)((255 - Color.blue(color)) * factor);
        return Color.argb(Color.alpha(color),
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b)));
    }

    /**
     * Live preview — applies accent tokens in-place without rebuilding views or writing to disk.
     */
    private void applyMenuColorLive(int baseColor) {
        int safeColor = ensureReadableAccent(baseColor);
        COLOR_ACCENT   = safeColor;
        COLOR_ACCENT_2 = ModxTheme.accentPress(safeColor);
        COLOR_ACCENT_3 = ModxTheme.accentHover(safeColor);
        TEXT_COLOR = safeColor;
        BTN_COLOR  = safeColor;
        BTN_GRAD_1 = safeColor;
        BTN_GRAD_2 = COLOR_ACCENT_2;
        SeekBarColor = safeColor;
        SeekBarProgressColor = safeColor;
        CheckBoxColor = safeColor;
        RadioColor    = safeColor;
        ToggleON      = safeColor;
        BtnON         = safeColor;
        NumberTxtColor = ModxTheme.hex6(safeColor);

        updateHeaderColors();

        if (resizeHandle != null) {
            resizeHandle.setBackground(makeGripDrawable(ModxTheme.SUBTLE, dpf(1.6f)));
        }

        for (TabHolder h : tabHolders) styleTab(h, isTabSelected(h));

        updateThemedControlsInPlace();
    }

    private void updateThemedControlsInPlace() {
        if (mMenuColorDot != null || mMenuColorHex != null) {
            applyMenuColorRowStyle(mMenuColorDot, mMenuColorHex, COLOR_ACCENT);
        }
        for (ToggleHolder th : mToggles.values()) {
            if (th.toggle != null) {
                th.toggle.setColors(COLOR_ACCENT, COLOR_ACCENT_3, COLOR_TRACK);
            }
            if (th.rowBg != null && th.state != null && th.state[0]) {
                th.rowBg.setStroke(Math.max(1, dp(1)), ModxTheme.accentLine(ToggleON));
            }
        }
        for (ModeButtonHolder mh : mModeButtons.values()) {
            if (mh != null && mh.state != null) {
                applyModeCardStyle(mh, mh.state[0]);
            }
        }
        for (OtherButtonOnOffHolder oh : mOtherButtonOnOffs.values()) {
            if (oh != null && oh.button != null && oh.state != null) {
                applyOnOffStyle(oh.button, oh.name, oh.state[0]);
            }
        }
        for (SeekBarHolder sb : mSeekBars.values()) {
            if (sb.trackDrawable != null) {
                sb.trackDrawable.setColors(COLOR_ACCENT, COLOR_ACCENT_3);
            }
            if (sb.thumbCore != null) {
                sb.thumbCore.setStroke(Math.max(2, dp(2)), COLOR_ACCENT);
            }
            if (sb.seekBar != null) {
                sb.seekBar.invalidate();
            }
            if (sb.chip != null) {
                sb.chip.setTextColor(COLOR_ACCENT);
                sb.chip.setBackground(ModxTheme.pillBadgeBg(
                        getContext, ModxTheme.accentSoft(COLOR_ACCENT), ModxTheme.accentLine(COLOR_ACCENT)));
            }
        }
        for (SpinnerHolder sp : mSpinners.values()) {
            if (sp.chevIcon != null) {
                sp.chevIcon.setColor(COLOR_ACCENT);
            }
            if (sp.dropdownList != null) {
                sp.dropdownList.setBackground(ModxTheme.roundRect(
                        getContext, ModxTheme.SURFACE, ModxTheme.LINE, 1f, ModxTheme.RADIUS_DP));
                for (int j = 0; j < sp.dropdownList.getChildCount(); j++) {
                    View child = sp.dropdownList.getChildAt(j);
                    if (child instanceof TextView) {
                        TextView tv = (TextView) child;
                        if (j == sp.currentIdx) {
                            tv.setTextColor(COLOR_ACCENT);
                            tv.setBackground(ModxTheme.roundRect(getContext, ModxTheme.accentSoft(COLOR_ACCENT), 0, 0f, 4f));
                        } else {
                            tv.setTextColor(ModxTheme.FG);
                            tv.setBackground(null);
                        }
                    }
                }
            }
        }
        for (CheckBoxHolder cb : mCheckBoxes.values()) {
            if (cb.checkBox != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cb.checkBox.setButtonTintList(ColorStateList.valueOf(CheckBoxColor));
                }
                if (cb.bg != null && cb.checkBox.isChecked()) {
                    cb.bg.setStroke(Math.max(1, dp(1)), ModxTheme.accentLine(CheckBoxColor));
                }
            }
        }
        for (RadioGroupHolder rg : mRadioGroups.values()) {
            if (rg.radioGroup != null) {
                for (int i = 0; i < rg.radioGroup.getChildCount(); i++) {
                    View c = rg.radioGroup.getChildAt(i);
                    if (c instanceof RadioButton && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ((RadioButton) c).setButtonTintList(ColorStateList.valueOf(RadioColor));
                    }
                }
            }
            if (rg.titleView != null && rg.selectedName != null) {
                rg.titleView.setText(Html.fromHtml(rg.featName + ": <font color='" + NumberTxtColor + "'>"
                        + TextUtils.htmlEncode(rg.selectedName) + "</font>"));
            }
        }
        updateInputHoldersTheme(mInputNums);
        updateInputHoldersTheme(mInputLNums);
        updateInputHoldersTheme(mInputTexts);

        for (TextView catTv : mCategoryViews) {
            if (catTv != null) {
                catTv.setTextColor(ModxTheme.SUBTLE);
            }
        }
        for (View div : mCategoryLines) {
            if (div != null) {
                div.setBackgroundColor(ModxTheme.HAIRLINE);
            }
        }
        for (CollapseHolder ch : mCollapseHolders) {
            if (ch.headRow != null) {
                ch.headRow.setBackground(ModxTheme.withInteractiveRipple(
                        getContext, ModxTheme.cardSurface(getContext, ModxTheme.LINE),
                        ModxTheme.withAlpha(COLOR_ACCENT, 0x1A), ModxTheme.RADIUS_DP));
            }
            if (ch.titleView != null) {
                ch.titleView.setTextColor(ModxTheme.FG);
            }
            if (ch.chevIcon != null) {
                ch.chevIcon.setColor(COLOR_ACCENT);
            }
        }
        for (Button lb : mLinkButtons) {
            if (lb != null) {
                lb.setTextColor(COLOR_ACCENT);
                lb.setBackground(ModxTheme.withInteractiveRipple(
                        getContext,
                        ModxTheme.pillBadgeBg(getContext, ModxTheme.accentSoft(COLOR_ACCENT), ModxTheme.accentLine(COLOR_ACCENT)),
                        ModxTheme.withAlpha(COLOR_ACCENT, 0x24),
                        ModxTheme.RADIUS_DP));
                lb.setCompoundDrawablesWithIntrinsicBounds(
                        null, null, ModxIcons.externalLink(COLOR_ACCENT, dp(15)), null);
            }
        }
        if (mMenuTeleportPad != null) {
            mMenuTeleportPad.setAccentColor(COLOR_ACCENT);
        }
    }

    private void updateInputHoldersTheme(HashMap<Integer, InputHolder> map) {
        for (InputHolder ih : map.values()) {
            if (ih.button != null) {
                ih.button.setText(Html.fromHtml(ih.featName + ": <font color='" + NumberTxtColor + "'>"
                        + TextUtils.htmlEncode(ih.currentValueText != null ? ih.currentValueText : "") + "</font>"));
            }
        }
    }

    private void resetMenuColorToDefaultInPlace() {
        int defAccent = ensureReadableAccent(FeatureStateStore.DEFAULT_MENU_COLOR);
        mCurrentHueF = getHueFromColor(defAccent);
        applyMenuColorLive(defAccent);
        Preferences.setMenuAccentColor(defAccent);
        showMenuToast(Preferences.isSaveEnabled() ? "Menu color reset & saved" : "Menu color reset");
    }

    /**
     * Commit — Apply button commits theme in-place and persists if Save is enabled.
     */
    private void applyMenuColor(final int baseColor) {
        int safeAccent = ensureReadableAccent(baseColor);
        mCurrentHueF = getHueFromColor(safeAccent);
        applyMenuColorLive(safeAccent);
        Preferences.recordMenuColorUsed(safeAccent);
        Preferences.setMenuAccentColor(safeAccent);
        showMenuToast(Preferences.isSaveEnabled() ? "Menu color saved" : "Menu color applied");
    }

    private boolean isTabSelected(TabHolder h) {
        for (TabHolder t : tabHolders) {
            LinearLayout catView = categoryViewsMap.get(t.name);
            if (catView != null && catView.getParent() == contentLayout) {
                return t == h;
            }
        }
        return false;
    }

    /**
     * Header (ModX Lab / PRO / gear / footer buttons) live token update.
     */
    private void updateHeaderColors() {
        if (mTitleView != null) {
            mTitleView.setTextColor(ModxTheme.FG);
        }
        if (mTitleDot != null) {
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(COLOR_ACCENT);
            mTitleDot.setBackground(dotBg);
        }
        if (mSubTitleView != null) {
            mSubTitleView.setTextColor(ModxTheme.MUTED);
        }

        if (mProView != null) {
            mProView.setBackground(ModxTheme.pillBadgeBg(
                    getContext, ModxTheme.accentSoft(COLOR_ACCENT), ModxTheme.accentLine(COLOR_ACCENT)));
            mProView.setTextColor(COLOR_ACCENT);
        }

        if (mSettingsIcon != null) {
            mSettingsIcon.setImageDrawable(ModxIcons.settings(COLOR_ACCENT, dp(19)));
            mSettingsIcon.setBackground(ModxTheme.ghostIconBg(getContext, COLOR_ACCENT));
        }

        if (mMinimizeBtn != null) {
            mMinimizeBtn.setBackground(ModxTheme.secondaryButtonBg(getContext));
            mMinimizeBtn.setTextColor(ModxTheme.FG);
            mMinimizeBtn.setCompoundDrawablesWithIntrinsicBounds(
                    ModxIcons.minimize(ModxTheme.MUTED, dp(14)), null, null, null);
        }

        if (mHideBtn != null) {
            mHideBtn.setBackground(ModxTheme.dangerOutlineButtonBg(getContext, false));
            mHideBtn.setTextColor(ModxTheme.DANGER);
        }

        if (mShimmerViewRef != null) {
            mShimmerViewRef.setBackgroundColor(ModxTheme.LINE);
        }
        if (sidebarDivider != null) {
            sidebarDivider.setBackgroundColor(ModxTheme.LINE);
        }
        if (mBottomDivider != null) {
            mBottomDivider.setBackgroundColor(ModxTheme.LINE);
        }
        if (sidebarScroll instanceof SlimScrollView) {
            ((SlimScrollView) sidebarScroll).setAccentColor(COLOR_ACCENT);
        }
        if (contentScrollView instanceof SlimScrollView) {
            ((SlimScrollView) contentScrollView).setAccentColor(COLOR_ACCENT);
        }
    }

// ================================================================
// Hue Ring View (strict ring hit-test, zero onDraw allocations)
// ================================================================
private static class ColorWheelView extends View {
    interface Listener {
        void onHueChanged(float hueDegrees, boolean fromCancel);
    }

    private static final int[] HUE_RING_COLORS = new int[13];
    static {
        float[] tmp = new float[]{ 0f, 1f, 1f };
        for (int i = 0; i <= 12; i++) {
            tmp[0] = (i == 12) ? 0f : (i * 30f);
            HUE_RING_COLORS[i] = Color.HSVToColor(tmp);
        }
    }

    private final Paint ringPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerDarkPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] markerHsv = new float[]{ 0f, 1f, 1f };

    private final float density;
    private SweepGradient sweepShader;
    private float cx, cy, outerR, ringWidth, ringMidR, innerR, swatchR, hitInnerR, hitOuterR;
    private float currentHue = 145f;
    private float preDragHue = 145f;
    private int previewColor = ModxTheme.ACCENT;
    private int lastHapticStep = -1;
    private boolean dragging = false;
    private Listener listener;

    ColorWheelView(Context ctx, float initialHue, int initialColor) {
        super(ctx);
        this.density = safeDisplayMetrics(ctx).density;
        this.currentHue = normalizeHue(initialHue);
        this.previewColor = 0xFF000000 | initialColor;
        setHapticFeedbackEnabled(true);

        ringPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.2f * density);
        borderPaint.setColor(ModxTheme.withAlpha(ModxTheme.SURFACE, 0xCC));

        centerPaint.setStyle(Paint.Style.FILL);
        centerRingPaint.setStyle(Paint.Style.STROKE);
        centerRingPaint.setStrokeWidth(1.5f * density);
        centerRingPaint.setColor(ModxTheme.withAlpha(ModxTheme.FG, 0x44));

        markerFillPaint.setStyle(Paint.Style.FILL);
        markerWhitePaint.setStyle(Paint.Style.STROKE);
        markerWhitePaint.setStrokeWidth(2.6f * density);
        markerWhitePaint.setColor(ModxTheme.FG);

        markerDarkPaint.setStyle(Paint.Style.STROKE);
        markerDarkPaint.setStrokeWidth(1.4f * density);
        markerDarkPaint.setColor(ModxTheme.withAlpha(ModxTheme.SURFACE, 0xDD));
    }

    private static float normalizeHue(float h) {
        float r = h % 360f;
        return (r < 0f) ? (r + 360f) : r;
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    void setState(float hue, int previewColor) {
        this.currentHue = normalizeHue(hue);
        this.previewColor = 0xFF000000 | previewColor;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        cx = w * 0.5f;
        cy = h * 0.5f;
        outerR = Math.min(w, h) * 0.5f - 10f * density;
        if (outerR < 24f * density) outerR = 24f * density;
        ringWidth = Math.max(22f * density, Math.min(28f * density, outerR * 0.27f));
        ringPaint.setStrokeWidth(ringWidth);
        ringMidR = outerR - ringWidth * 0.5f;
        innerR = outerR - ringWidth;
        swatchR = Math.max(0f, innerR - 9f * density);

        // Strict hit band around the ring only (ignores center swatch & outside dead zones)
        hitInnerR = Math.max(swatchR + 2f * density, innerR - 6f * density);
        hitOuterR = outerR + 8f * density;

        sweepShader = new SweepGradient(cx, cy, HUE_RING_COLORS, null);
        ringPaint.setShader(sweepShader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (sweepShader == null || outerR <= 0f) return;

        // 1. Hue Ring
        canvas.drawCircle(cx, cy, ringMidR, ringPaint);
        canvas.drawCircle(cx, cy, outerR, borderPaint);
        canvas.drawCircle(cx, cy, innerR, borderPaint);

        // 2. Center live preview swatch (dead zone for ring touch)
        if (swatchR > 2f * density) {
            centerPaint.setColor(previewColor);
            canvas.drawCircle(cx, cy, swatchR, centerPaint);
            canvas.drawCircle(cx, cy, swatchR, centerRingPaint);
        }

        // 3. Large high-contrast marker on hue ring
        double rad = Math.toRadians(currentHue);
        float mx = cx + (float) Math.cos(rad) * ringMidR;
        float my = cy + (float) Math.sin(rad) * ringMidR;
        float mr = Math.max(9.5f * density, ringWidth * 0.43f);

        markerHsv[0] = currentHue;
        markerHsv[1] = 1f;
        markerHsv[2] = 1f;
        markerFillPaint.setColor(Color.HSVToColor(markerHsv));
        canvas.drawCircle(mx, my, mr, markerFillPaint);
        canvas.drawCircle(mx, my, mr, markerWhitePaint);
        canvas.drawCircle(mx, my, mr + 1.6f * density, markerDarkPaint);
    }

    private float angleFromPoint(float x, float y) {
        float deg = (float) Math.toDegrees(Math.atan2(y - cy, x - cx));
        return normalizeHue(deg);
    }

    private void tickHaptic(boolean force) {
        int step = Math.round(currentHue / 15f);
        if (force || step != lastHapticStep) {
            lastHapticStep = step;
            try {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            } catch (Throwable ignored) { }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float dx = event.getX() - cx;
                float dy = event.getY() - cy;
                float dist = (float) Math.hypot(dx, dy);
                // Ignore center swatch, gap, and outside corners completely
                if (dist < hitInnerR || dist > hitOuterR) {
                    return false;
                }
                dragging = true;
                preDragHue = currentHue;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                currentHue = angleFromPoint(event.getX(), event.getY());
                tickHaptic(true);
                invalidate();
                if (listener != null) listener.onHueChanged(currentHue, false);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                currentHue = angleFromPoint(event.getX(), event.getY());
                tickHaptic(false);
                invalidate();
                if (listener != null) listener.onHueChanged(currentHue, false);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (!dragging) return false;
                dragging = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                currentHue = angleFromPoint(event.getX(), event.getY());
                invalidate();
                if (listener != null) listener.onHueChanged(currentHue, false);
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) return false;
                dragging = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                currentHue = preDragHue;
                invalidate();
                if (listener != null) listener.onHueChanged(preDragHue, true);
                return true;
            }
        }
        return false;
    }
}

// ================================================================
// 2D Saturation / Brightness Square (zero onDraw allocations)
// ================================================================
private static class SatValSquareView extends View {
    interface Listener {
        void onSatValChanged(float sat, float val, boolean fromCancel);
    }

    private final Paint satPaint           = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valPaint           = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerFillPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerWhitePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerDarkPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF boxRect            = new RectF();
    private final float[] tmpHsv           = new float[]{ 0f, 1f, 1f };

    private final float density;
    private final float cornerR;
    private final float pad;
    private LinearGradient horizShader;
    private LinearGradient vertShader;
    private float hue = 145f;
    private float shaderHue = -1f;
    private float sat = 0.72f;
    private float val = 0.86f;
    private float preDragSat = 0.72f;
    private float preDragVal = 0.86f;
    private boolean dragging = false;
    private Listener listener;

    SatValSquareView(Context ctx, float hue, float sat, float val) {
        super(ctx);
        this.density = safeDisplayMetrics(ctx).density;
        this.cornerR = ModxTheme.RADIUS_DP * density;
        this.pad = 10f * density;
        this.hue = hue;
        this.sat = clamp01(sat);
        this.val = clamp01(val);
        setHapticFeedbackEnabled(true);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.2f * density);
        borderPaint.setColor(ModxTheme.LINE_STRONG);

        markerFillPaint.setStyle(Paint.Style.FILL);
        markerWhitePaint.setStyle(Paint.Style.STROKE);
        markerWhitePaint.setStrokeWidth(2.5f * density);
        markerWhitePaint.setColor(ModxTheme.FG);

        markerDarkPaint.setStyle(Paint.Style.STROKE);
        markerDarkPaint.setStrokeWidth(1.4f * density);
        markerDarkPaint.setColor(ModxTheme.withAlpha(ModxTheme.SURFACE, 0xDD));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    void setHSV(float h, float s, float v) {
        float nh = ((h % 360f) + 360f) % 360f;
        boolean hueChanged = Math.abs(nh - this.hue) > 0.01f;
        this.hue = nh;
        this.sat = clamp01(s);
        this.val = clamp01(v);
        if (hueChanged) {
            rebuildHorizShader();
        }
        invalidate();
    }

    private void rebuildHorizShader() {
        if (boxRect.width() <= 0f) return;
        tmpHsv[0] = hue;
        tmpHsv[1] = 1f;
        tmpHsv[2] = 1f;
        int pureHue = Color.HSVToColor(tmpHsv);
        horizShader = new LinearGradient(
                boxRect.left, 0f, boxRect.right, 0f,
                0xFFFFFFFF, pureHue, Shader.TileMode.CLAMP);
        satPaint.setShader(horizShader);
        shaderHue = hue;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        boxRect.set(pad, pad, Math.max(pad + 1f, w - pad), Math.max(pad + 1f, h - pad));
        vertShader = new LinearGradient(
                0f, boxRect.top, 0f, boxRect.bottom,
                0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
        valPaint.setShader(vertShader);
        rebuildHorizShader();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (horizShader == null || vertShader == null || boxRect.width() <= 0f) return;
        if (Math.abs(shaderHue - hue) > 0.01f) {
            rebuildHorizShader();
        }

        // Exact HSV rendering via cached White->Hue + Transparent->Black gradients
        canvas.drawRoundRect(boxRect, cornerR, cornerR, satPaint);
        canvas.drawRoundRect(boxRect, cornerR, cornerR, valPaint);
        canvas.drawRoundRect(boxRect, cornerR, cornerR, borderPaint);

        // Large double-bordered marker
        float mx = boxRect.left + sat * boxRect.width();
        float my = boxRect.top + (1f - val) * boxRect.height();
        float mr = 9.5f * density;

        tmpHsv[0] = hue;
        tmpHsv[1] = sat;
        tmpHsv[2] = val;
        markerFillPaint.setColor(Color.HSVToColor(tmpHsv));
        canvas.drawCircle(mx, my, mr, markerFillPaint);
        canvas.drawCircle(mx, my, mr, markerWhitePaint);
        canvas.drawCircle(mx, my, mr + 1.5f * density, markerDarkPaint);
    }

    private void updateFromPoint(float x, float y) {
        float w = boxRect.width();
        float h = boxRect.height();
        if (w <= 0f || h <= 0f) return;
        sat = clamp01((x - boxRect.left) / w);
        val = 1f - clamp01((y - boxRect.top) / h);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float x = event.getX();
                float y = event.getY();
                if (x < 0f || x > getWidth() || y < 0f || y > getHeight()) {
                    return false;
                }
                dragging = true;
                preDragSat = sat;
                preDragVal = val;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                try {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                } catch (Throwable ignored) { }
                updateFromPoint(x, y);
                if (listener != null) listener.onSatValChanged(sat, val, false);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                updateFromPoint(event.getX(), event.getY());
                if (listener != null) listener.onSatValChanged(sat, val, false);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (!dragging) return false;
                dragging = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                updateFromPoint(event.getX(), event.getY());
                if (listener != null) listener.onSatValChanged(sat, val, false);
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) return false;
                dragging = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                sat = preDragSat;
                val = preDragVal;
                invalidate();
                if (listener != null) listener.onSatValChanged(preDragSat, preDragVal, true);
                return true;
            }
        }
        return false;
    }
}

private static class PickerMaxHeightScrollView extends ScrollView {
    private int maxHeightPx;

    PickerMaxHeightScrollView(Context ctx, int maxHeightPx) {
        super(ctx);
        this.maxHeightPx = maxHeightPx;
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    void setMaxHeightPx(int maxHeightPx) {
        if (this.maxHeightPx != maxHeightPx) {
            this.maxHeightPx = maxHeightPx;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeightPx > 0) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            int capped = (mode == MeasureSpec.UNSPECIFIED)
                    ? maxHeightPx
                    : Math.min(size, maxHeightPx);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}

private static final int[] CURATED_ACCENT_PRESETS = new int[] {
    ModxTheme.ACCENT, // ModX Emerald #20C997 (Default)
    0xFF22D3EE, // Cyan
    0xFF38BDF8, // Sky Blue
    0xFF60A5FA, // Azure
    0xFF818CF8, // Indigo
    0xFFA855F7, // Electric Purple
    0xFFEC4899, // Neon Pink
    0xFFF43F5E, // Crimson Rose
    0xFFFB923C, // Amber Orange
    0xFFFACC15, // Gold
    0xFFA3E635, // Lime
    0xFFE2E8F0  // Silver Frost
};

private static Integer parseHexColorStrict(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.startsWith("#")) s = s.substring(1);
    if (s.length() == 8) {
        s = s.substring(2);
    }
    if (s.length() != 6) return null;
    for (int i = 0; i < 6; i++) {
        char ch = s.charAt(i);
        boolean ok = (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
        if (!ok) return null;
    }
    try {
        int rgb = Integer.parseInt(s, 16);
        return 0xFF000000 | rgb;
    } catch (Exception e) {
        return null;
    }
}

private static class SwatchRingHolder {
    final GradientDrawable bg;
    final int color;

    SwatchRingHolder(GradientDrawable bg, int color) {
        this.bg = bg;
        this.color = color;
    }
}

private View makeSwatchItem(final int color, final View.OnClickListener onClick,
                            final ArrayList<SwatchRingHolder> ringsOut) {
    FrameLayout hitBox = new FrameLayout(getContext);
    LinearLayout.LayoutParams hitLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
    hitBox.setLayoutParams(hitLp);
    hitBox.setClickable(true);
    hitBox.setFocusable(true);
    hitBox.setContentDescription("Color " + hex(color));

    View dot = new View(getContext);
    int dotSize = dp(26);
    FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER);
    dot.setLayoutParams(dotLp);

    GradientDrawable gd = new GradientDrawable();
    gd.setShape(GradientDrawable.OVAL);
    gd.setColor(0xFF000000 | color);
    gd.setStroke(dp(1), ModxTheme.LINE_STRONG);
    dot.setBackground(gd);

    if (ringsOut != null) {
        ringsOut.add(new SwatchRingHolder(gd, 0xFF000000 | color));
    }

    hitBox.addView(dot);
    addPressAnim(hitBox);
    hitBox.setOnClickListener(onClick);
    return hitBox;
}

// ================================================================
// Professional Color Palette Dialog
//  • Preview swatch + #RRGGBB + Original compare chip + Reset
//  • Hue ring (strict hit-test) + 2D S/V square (zero onDraw allocs)
//  • Hex input with validation (overlay + standalone keyboard safe)
//  • 12 curated presets + last 5 recent colors
//  • Live preview (no persist); persist ONLY on Apply (Save-gated)
// ================================================================
private void showMenuColorPickerDialog() {
    if (activeColorPickerDialog != null && activeColorPickerDialog.isShowing()) {
        return;
    }
    mColorPickerOriginalColor = COLOR_ACCENT;

    final AlertDialog[] dialogRef = new AlertDialog[1];
    final float[] curHsv = new float[3];
    Color.colorToHSV(0xFF000000 | COLOR_ACCENT, curHsv);
    final int[] rawPickedHolder = new int[]{ 0xFF000000 | COLOR_ACCENT };
    final boolean[] updatingHex = new boolean[]{ false };

    final int swInit = screenW();
    final int shInit = screenH();
    final boolean isLandscapeInit = (swInit > shInit && swInit >= dp(440));

    final PickerMaxHeightScrollView scroll = new PickerMaxHeightScrollView(getContext, (int) (shInit * 0.88f));

    final LinearLayout box = new LinearLayout(getContext);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setGravity(Gravity.CENTER_HORIZONTAL);
    box.setPadding(dp(16), dp(14), dp(16), dp(14));
    // Prevent Hex EditText from auto-opening keyboard on dialog launch
    box.setFocusable(true);
    box.setFocusableInTouchMode(true);
    box.requestFocus();

    final GradientDrawable boxBg = ModxTheme.roundRect(
            getContext, ModxTheme.CARD, ModxTheme.LINE, 1f, ModxTheme.RADIUS_DP);
    box.setBackground(boxBg);

    // ---- 1. Top Header Bar (Title + Reset button) ----
    LinearLayout headerRow = new LinearLayout(getContext);
    headerRow.setOrientation(LinearLayout.HORIZONTAL);
    headerRow.setGravity(Gravity.CENTER_VERTICAL);
    headerRow.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

    View titleDot = new View(getContext);
    LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
    dotLp.setMargins(0, 0, dp(8), 0);
    titleDot.setLayoutParams(dotLp);
    final GradientDrawable titleDotBg = new GradientDrawable();
    titleDotBg.setShape(GradientDrawable.OVAL);
    titleDotBg.setColor(COLOR_ACCENT);
    titleDot.setBackground(titleDotBg);

    final TextView title = new TextView(getContext);
    title.setText("Menu Color Palette");
    title.setTextColor(ModxTheme.FG);
    title.setTypeface(fontBold);
    title.setTextSize(15f);
    title.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

    Button resetBtn = new Button(getContext);
    resetBtn.setText("Reset");
    resetBtn.setAllCaps(false);
    resetBtn.setTypeface(fontMedium);
    resetBtn.setTextSize(11f);
    resetBtn.setTextColor(ModxTheme.FG);
    resetBtn.setMinHeight(dp(34));
    resetBtn.setMinimumHeight(dp(34));
    resetBtn.setMinWidth(0);
    resetBtn.setMinimumWidth(0);
    resetBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
    resetBtn.setBackground(ModxTheme.secondaryButtonBg(getContext));
    flatten(resetBtn);
    addPressAnim(resetBtn);

    headerRow.addView(titleDot);
    headerRow.addView(title);
    headerRow.addView(resetBtn);
    box.addView(headerRow);

    // ---- 2. Large Preview Swatch Banner ----
    LinearLayout previewBanner = new LinearLayout(getContext);
    previewBanner.setOrientation(LinearLayout.HORIZONTAL);
    previewBanner.setGravity(Gravity.CENTER_VERTICAL);
    previewBanner.setPadding(dp(12), dp(10), dp(12), dp(10));
    LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    prevLp.setMargins(0, dp(10), 0, dp(10));
    previewBanner.setLayoutParams(prevLp);

    final GradientDrawable previewBg = new GradientDrawable();
    previewBg.setCornerRadius(dpf(ModxTheme.RADIUS_DP));
    previewBg.setColor(rawPickedHolder[0]);
    previewBg.setStroke(dp(1), ModxTheme.LINE_STRONG);
    previewBanner.setBackground(previewBg);

    LinearLayout previewTextCol = new LinearLayout(getContext);
    previewTextCol.setOrientation(LinearLayout.VERTICAL);
    previewTextCol.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

    final TextView previewHexTv = new TextView(getContext);
    previewHexTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    previewHexTv.setTextSize(15f);
    previewHexTv.setText(hex(rawPickedHolder[0]));
    previewHexTv.setTextColor(ModxTheme.onAccent(rawPickedHolder[0]));

    final TextView previewSubTv = new TextView(getContext);
    previewSubTv.setTypeface(fontRegular);
    previewSubTv.setTextSize(10f);
    previewSubTv.setText("Live Preview");
    previewSubTv.setTextColor(ModxTheme.withAlpha(ModxTheme.onAccent(rawPickedHolder[0]), 0xCC));

    previewTextCol.addView(previewHexTv);
    previewTextCol.addView(previewSubTv);

    // Original color compare chip (tap to jump back to original without closing)
    LinearLayout origChip = new LinearLayout(getContext);
    origChip.setOrientation(LinearLayout.HORIZONTAL);
    origChip.setGravity(Gravity.CENTER_VERTICAL);
    origChip.setPadding(dp(8), dp(5), dp(8), dp(5));
    origChip.setBackground(ModxTheme.withInteractiveRipple(
            getContext,
            ModxTheme.roundRect(getContext,
                    ModxTheme.withAlpha(ModxTheme.SURFACE, 0xCC),
                    ModxTheme.withAlpha(ModxTheme.FG, 0x44),
                    1f, ModxTheme.RADIUS_DP),
            0x26FFFFFF,
            ModxTheme.RADIUS_DP));
    origChip.setClickable(true);
    origChip.setFocusable(true);

    View origDot = new View(getContext);
    origDot.setLayoutParams(new LinearLayout.LayoutParams(dp(14), dp(14)));
    GradientDrawable origDotBg = new GradientDrawable();
    origDotBg.setShape(GradientDrawable.OVAL);
    origDotBg.setColor(mColorPickerOriginalColor);
    origDotBg.setStroke(dp(1), ModxTheme.FG);
    origDot.setBackground(origDotBg);

    TextView origLbl = new TextView(getContext);
    origLbl.setText("Orig");
    origLbl.setTextColor(ModxTheme.FG);
    origLbl.setTypeface(fontMedium);
    origLbl.setTextSize(10f);
    LinearLayout.LayoutParams origLblLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
    origLblLp.setMargins(dp(5), 0, 0, 0);
    origLbl.setLayoutParams(origLblLp);

    origChip.addView(origDot);
    origChip.addView(origLbl);
    addPressAnim(origChip);

    previewBanner.addView(previewTextCol);
    previewBanner.addView(origChip);
    box.addView(previewBanner);

    // ---- 3. Hue Ring + S/V Square ----
    int wheelSize = isLandscapeInit
            ? Math.min(dp(168), Math.max(dp(136), shInit - dp(180)))
            : Math.min(dp(196), Math.max(dp(150), Math.min(swInit - dp(100), (int) (shInit * 0.30f))));

    final ColorWheelView wheel = new ColorWheelView(getContext, curHsv[0], rawPickedHolder[0]);
    LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(wheelSize, wheelSize);
    wlp.gravity = Gravity.CENTER;
    wheel.setLayoutParams(wlp);

    final SatValSquareView svSquare = new SatValSquareView(getContext, curHsv[0], curHsv[1], curHsv[2]);
    int svHeight = isLandscapeInit ? dp(116) : Math.min(dp(116), Math.max(dp(88), (int) (shInit * 0.16f)));
    LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(MATCH_PARENT, svHeight);
    if (!isLandscapeInit) {
        svLp.setMargins(0, dp(6), 0, 0);
    }
    svSquare.setLayoutParams(svLp);

    // ---- 4. Hex Input + Validation ----
    LinearLayout hexContainer = new LinearLayout(getContext);
    hexContainer.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams hexContLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    hexContLp.setMargins(0, dp(8), 0, 0);
    hexContainer.setLayoutParams(hexContLp);

    LinearLayout hexRow = new LinearLayout(getContext);
    hexRow.setOrientation(LinearLayout.HORIZONTAL);
    hexRow.setGravity(Gravity.CENTER_VERTICAL);

    TextView hexLabel = new TextView(getContext);
    hexLabel.setText("HEX");
    hexLabel.setTextColor(ModxTheme.MUTED);
    hexLabel.setTypeface(fontBold);
    hexLabel.setTextSize(11f);
    hexLabel.setPadding(0, 0, dp(10), 0);

    final EditText hexInput = new EditText(getContext);
    hexInput.setTextColor(ModxTheme.FG);
    hexInput.setHintTextColor(ModxTheme.SUBTLE);
    hexInput.setHint("#20C997");
    hexInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    hexInput.setTextSize(13f);
    hexInput.setSingleLine(true);
    hexInput.setMinHeight(dp(42));
    hexInput.setPadding(dp(12), dp(8), dp(12), dp(8));
    hexInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    hexInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
    hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
    hexInput.setHighlightColor(ModxTheme.accentFocusRing(COLOR_ACCENT));
    final GradientDrawable hexInputBg = ModxTheme.roundRect(
            getContext, ModxTheme.INPUT, ModxTheme.LINE_STRONG, 1f, ModxTheme.RADIUS_DP);
    hexInput.setBackground(hexInputBg);
    hexInput.setText(hex(rawPickedHolder[0]));
    hexInput.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

    final TextView hexErrorTv = new TextView(getContext);
    hexErrorTv.setTextColor(ModxTheme.DANGER);
    hexErrorTv.setTypeface(fontRegular);
    hexErrorTv.setTextSize(10f);
    hexErrorTv.setPadding(dp(2), dp(3), 0, 0);
    hexErrorTv.setVisibility(View.GONE);

    hexRow.addView(hexLabel);
    hexRow.addView(hexInput);
    hexContainer.addView(hexRow);
    hexContainer.addView(hexErrorTv);

    if (isLandscapeInit) {
        LinearLayout pickerArea = new LinearLayout(getContext);
        pickerArea.setOrientation(LinearLayout.HORIZONTAL);
        pickerArea.setGravity(Gravity.CENTER_VERTICAL);
        pickerArea.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        LinearLayout rightCol = new LinearLayout(getContext);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rcLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        rcLp.setMargins(dp(12), 0, 0, 0);
        rightCol.setLayoutParams(rcLp);
        rightCol.addView(svSquare);
        rightCol.addView(hexContainer);

        pickerArea.addView(wheel);
        pickerArea.addView(rightCol);
        box.addView(pickerArea);
    } else {
        box.addView(wheel);
        box.addView(svSquare);
        box.addView(hexContainer);
    }

    // Track swatch rings so active color highlights immediately
    final ArrayList<SwatchRingHolder> allSwatchRings = new ArrayList<SwatchRingHolder>();
    final Button okBtn = dialogButton("Apply", true);

    final Runnable[] syncUiRunnable = new Runnable[1];
    syncUiRunnable[0] = new Runnable() {
        @Override
        public void run() {
            int raw = rawPickedHolder[0];
            int safe = ensureReadableAccent(raw);
            int onPreview = ModxTheme.onAccent(raw);

            previewBg.setColor(raw);
            previewHexTv.setText(hex(raw));
            previewHexTv.setTextColor(onPreview);
            if (safe != raw) {
                previewSubTv.setText("Adjusted for readability: " + hex(safe));
            } else {
                previewSubTv.setText("Live Preview");
            }
            previewSubTv.setTextColor(ModxTheme.withAlpha(onPreview, 0xCC));

            boxBg.setStroke(dp(1), ModxTheme.accentLine(safe));
            titleDotBg.setColor(safe);

            okBtn.setBackground(ModxTheme.primaryButtonBg(getContext, safe));
            okBtn.setTextColor(ModxTheme.onAccent(safe));

            if (!hexInput.hasFocus()) {
                updatingHex[0] = true;
                hexInput.setText(hex(raw));
                hexInput.setSelection(hexInput.getText().length());
                updatingHex[0] = false;
                hexErrorTv.setVisibility(View.GONE);
                hexInputBg.setStroke(dp(1), ModxTheme.LINE_STRONG);
            }

            for (int i = 0; i < allSwatchRings.size(); i++) {
                SwatchRingHolder h = allSwatchRings.get(i);
                boolean match = (h.color == raw || h.color == safe);
                h.bg.setStroke(dp(match ? 2 : 1), match ? ModxTheme.FG : ModxTheme.LINE_STRONG);
            }

            // Live preview across the menu (never persists)
            mCurrentHueF = curHsv[0];
            applyMenuColorLive(safe);
        }
    };

    // Wheel & S/V square listeners
    wheel.setListener(new ColorWheelView.Listener() {
        @Override
        public void onHueChanged(float hueDegrees, boolean fromCancel) {
            curHsv[0] = hueDegrees;
            rawPickedHolder[0] = 0xFF000000 | Color.HSVToColor(curHsv);
            svSquare.setHSV(curHsv[0], curHsv[1], curHsv[2]);
            wheel.setState(curHsv[0], rawPickedHolder[0]);
            if (hexInput.hasFocus()) hexInput.clearFocus();
            syncUiRunnable[0].run();
        }
    });

    svSquare.setListener(new SatValSquareView.Listener() {
        @Override
        public void onSatValChanged(float sat, float val, boolean fromCancel) {
            curHsv[1] = sat;
            curHsv[2] = val;
            rawPickedHolder[0] = 0xFF000000 | Color.HSVToColor(curHsv);
            wheel.setState(curHsv[0], rawPickedHolder[0]);
            if (hexInput.hasFocus()) hexInput.clearFocus();
            syncUiRunnable[0].run();
        }
    });

    // Hex input click/focus/watcher
    hexInput.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            hexInput.requestFocus();
            try {
                InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(hexInput, InputMethodManager.SHOW_IMPLICIT);
            } catch (Throwable ignored) { }
        }
    });

    hexInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                Integer parsed = parseHexColorStrict(hexInput.getText().toString());
                if (parsed == null) {
                    hexErrorTv.setText("Enter 6 hex digits (e.g. #20C997)");
                    hexErrorTv.setVisibility(View.VISIBLE);
                    hexInputBg.setStroke(dp(1), ModxTheme.DANGER);
                } else {
                    hexInput.clearFocus();
                    box.requestFocus();
                    syncUiRunnable[0].run();
                    try {
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.hideSoftInputFromWindow(hexInput.getWindowToken(), 0);
                    } catch (Throwable ignored) { }
                }
                return true;
            }
            return false;
        }
    });

    hexInput.addTextChangedListener(new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override
        public void afterTextChanged(Editable s) {
            if (updatingHex[0]) return;
            String txt = s.toString().trim();
            Integer parsed = parseHexColorStrict(txt);
            if (parsed != null) {
                hexErrorTv.setVisibility(View.GONE);
                hexInputBg.setStroke(dp(1), COLOR_ACCENT);
                rawPickedHolder[0] = parsed;
                Color.colorToHSV(parsed, curHsv);
                wheel.setState(curHsv[0], parsed);
                svSquare.setHSV(curHsv[0], curHsv[1], curHsv[2]);
                syncUiRunnable[0].run();
            } else {
                hexErrorTv.setText("Enter 6 hex digits (e.g. #20C997)");
                hexErrorTv.setVisibility(View.VISIBLE);
                hexInputBg.setStroke(dp(1), ModxTheme.DANGER);
            }
        }
    });

    // Helper to jump to a picked preset/recent/reset color (live preview only)
    final android.view.View.OnClickListener[] selectColorAction = new android.view.View.OnClickListener[1];
    final java.util.HashMap<View, Integer> viewColorMap = new java.util.HashMap<View, Integer>();
    selectColorAction[0] = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Integer cObj = viewColorMap.get(v);
            if (cObj == null) return;
            ModxTheme.hapticTap(v);
            int c = cObj;
            if (hexInput.hasFocus()) {
                hexInput.clearFocus();
                box.requestFocus();
            }
            rawPickedHolder[0] = c;
            Color.colorToHSV(c, curHsv);
            wheel.setState(curHsv[0], c);
            svSquare.setHSV(curHsv[0], curHsv[1], curHsv[2]);
            syncUiRunnable[0].run();
        }
    };

    // Original chip click jumps back to original
    viewColorMap.put(origChip, mColorPickerOriginalColor);
    origChip.setOnClickListener(selectColorAction[0]);

    // Reset button resets picker to default accent #20C997 (still Cancelable!)
    resetBtn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ModxTheme.hapticTap(v);
            int def = FeatureStateStore.DEFAULT_MENU_COLOR;
            if (hexInput.hasFocus()) {
                hexInput.clearFocus();
                box.requestFocus();
            }
            rawPickedHolder[0] = def;
            Color.colorToHSV(def, curHsv);
            wheel.setState(curHsv[0], def);
            svSquare.setHSV(curHsv[0], curHsv[1], curHsv[2]);
            syncUiRunnable[0].run();
        }
    });

    // ---- 5. Curated Presets (12 swatches: 2 rows of 6) ----
    TextView presetsLbl = new TextView(getContext);
    presetsLbl.setText("Presets");
    presetsLbl.setTextColor(ModxTheme.MUTED);
    presetsLbl.setTypeface(fontBold);
    presetsLbl.setTextSize(11f);
    LinearLayout.LayoutParams pLblLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    pLblLp.setMargins(0, dp(10), 0, dp(4));
    presetsLbl.setLayoutParams(pLblLp);
    box.addView(presetsLbl);

    for (int rowStart = 0; rowStart < CURATED_ACCENT_PRESETS.length; rowStart += 6) {
        LinearLayout swatchRow = new LinearLayout(getContext);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(Gravity.CENTER_VERTICAL);
        swatchRow.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        for (int j = 0; j < 6 && rowStart + j < CURATED_ACCENT_PRESETS.length; j++) {
            int pc = CURATED_ACCENT_PRESETS[rowStart + j];
            View sw = makeSwatchItem(pc, selectColorAction[0], allSwatchRings);
            viewColorMap.put(sw, pc);
            swatchRow.addView(sw);
        }
        box.addView(swatchRow);
    }

    // ---- 6. Recent Used Colors (last 5) ----
    int[] recentColors = Preferences.getRecentMenuColors();
    if (recentColors != null && recentColors.length > 0) {
        TextView recentLbl = new TextView(getContext);
        recentLbl.setText("Recent");
        recentLbl.setTextColor(ModxTheme.MUTED);
        recentLbl.setTypeface(fontBold);
        recentLbl.setTextSize(11f);
        LinearLayout.LayoutParams rLblLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        rLblLp.setMargins(0, dp(6), 0, dp(2));
        recentLbl.setLayoutParams(rLblLp);
        box.addView(recentLbl);

        LinearLayout recentRow = new LinearLayout(getContext);
        recentRow.setOrientation(LinearLayout.HORIZONTAL);
        recentRow.setGravity(Gravity.CENTER_VERTICAL);
        recentRow.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        for (int i = 0; i < 5; i++) {
            if (i < recentColors.length) {
                int rc = recentColors[i];
                View sw = makeSwatchItem(rc, selectColorAction[0], allSwatchRings);
                viewColorMap.put(sw, rc);
                recentRow.addView(sw);
            } else {
                View spacer = new View(getContext);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
                recentRow.addView(spacer);
            }
        }
        box.addView(recentRow);
    }

    // ---- 7. Cancel & Apply Buttons ----
    LinearLayout btnRow = new LinearLayout(getContext);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    btnRowLp.setMargins(0, dp(12), 0, 0);
    btnRow.setLayoutParams(btnRowLp);

    Button cancelBtn = dialogButton("Cancel", false);
    cancelBtn.setMinimumHeight(dp(46));
    LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
    cLp.setMargins(0, 0, dp(6), 0);
    cancelBtn.setLayoutParams(cLp);
    cancelBtn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ModxTheme.hapticTap(v);
            if (dialogRef[0] != null) dialogRef[0].cancel();
        }
    });

    okBtn.setMinimumHeight(dp(46));
    LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
    oLp.setMargins(dp(6), 0, 0, 0);
    okBtn.setLayoutParams(oLp);
    okBtn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ModxTheme.hapticTap(v);
            if (hexInput.hasFocus()) {
                Integer typed = parseHexColorStrict(hexInput.getText().toString());
                if (typed == null) {
                    hexErrorTv.setText("Enter 6 hex digits (e.g. #20C997)");
                    hexErrorTv.setVisibility(View.VISIBLE);
                    hexInputBg.setStroke(dp(1), ModxTheme.DANGER);
                    return;
                }
                rawPickedHolder[0] = typed;
            }
            try {
                InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(hexInput.getWindowToken(), 0);
            } catch (Throwable ignored) { }

            if (dialogRef[0] != null) dialogRef[0].dismiss();
            applyMenuColor(rawPickedHolder[0]);
        }
    });

    btnRow.addView(cancelBtn);
    btnRow.addView(okBtn);
    box.addView(btnRow);

    scroll.addView(box, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

    // Sync initial visual state (including initial swatch ring & contrast)
    syncUiRunnable[0].run();

    // ---- Build & Show Dialog (safe for both Activity & Service overlay) ----
    Context dialogCtx = (getContext instanceof Activity)
            ? getContext
            : new ContextThemeWrapper(getContext,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            ? android.R.style.Theme_Material_Dialog_Alert
                            : android.R.style.Theme_Holo_Dialog);
    AlertDialog.Builder builder = new AlertDialog.Builder(dialogCtx);
    builder.setView(scroll);
    final AlertDialog dialog = builder.create();
    dialogRef[0] = dialog;
    activeColorPickerDialog = dialog;
    dialog.setCancelable(true);
    dialog.setCanceledOnTouchOutside(true);

    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
        @Override
        public void onCancel(DialogInterface d) {
            mCurrentHueF = getHueFromColor(mColorPickerOriginalColor);
            applyMenuColorLive(mColorPickerOriginalColor);
        }
    });
    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface d) {
            if (activeColorPickerDialog == dialog) {
                activeColorPickerDialog = null;
            }
            try {
                InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
            } catch (Throwable ignored) { }
        }
    });

    Window w = dialog.getWindow();
    if (w != null) {
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (overlayRequired) {
            w.setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 2038 : 2002);
        }
        w.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    dialog.show();

    final Window shownW = dialog.getWindow();
    if (shownW != null) {
        shownW.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        shownW.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        int capW = isLandscapeInit
                ? Math.min(dp(500), (int) (swInit * 0.88f))
                : Math.min(dp(360), (int) (swInit * 0.92f));
        WindowManager.LayoutParams lp = shownW.getAttributes();
        lp.width = capW;
        lp.height = WRAP_CONTENT;
        shownW.setAttributes(lp);

        final int[] lastDims = new int[]{ swInit, shInit };
        shownW.getDecorView().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int sw = screenW();
                int sh = screenH();
                if (sw != lastDims[0] || sh != lastDims[1]) {
                    lastDims[0] = sw;
                    lastDims[1] = sh;
                    boolean land = (sw > sh && sw >= dp(440));
                    scroll.setMaxHeightPx((int) (sh * 0.88f));
                    WindowManager.LayoutParams nlp = shownW.getAttributes();
                    nlp.width = land
                            ? Math.min(dp(500), (int) (sw * 0.88f))
                            : Math.min(dp(360), (int) (sw * 0.92f));
                    shownW.setAttributes(nlp);
                }
            }
        });
    }
}

/** Hex color থেকে hue বের করে (0..360) */
private static float getHueFromColor(int color) {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    return hsv[0];
}
// ==================================================================
// Teleport Pad Touch Overlay
// ---------------------------------------------------------------
// Pad-টি native ESP overlay-এ আঁকা হয় (bottom-left corner)।
// এই View টি ঠিক সেই position/size এ বসে এবং touches গুলো native
// এর TeleportPadTouch() এ forward করে। Pad-এর বাইরের কোনো touch
// consume করে না (FLAG_NOT_TOUCH_MODAL)।
// ==================================================================
// ==================================================================
// Floating Teleport Pad — rectangular, draggable, safe center
// ==================================================================
private static class TeleportPadTouchView extends View {
    private static final int PAD_W_DP = 165;    // ★ 25% ছোট
private static final int PAD_H_DP = 100;    // ★ 23% ছোট
    private static final int DRAG_BAR_DP = 14;

    private final Paint bgPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coordPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float density;
    private final int slop;

    private float markerNX = -1f;
    private float markerNY = -1f;

    private boolean dragging;
    private float dragStartX, dragStartY;
    private int winStartX, winStartY;

    private boolean padTouched;

    TeleportPadTouchView(Context ctx) {
        super(ctx);
        density = safeDisplayMetrics(ctx).density;
        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(ModxTheme.withAlpha(ModxTheme.SURFACE, 0xE6));

        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(1.5f));
        framePaint.setColor(ModxTheme.ACCENT);

        crossPaint.setStyle(Paint.Style.STROKE);
        crossPaint.setStrokeWidth(dpf(1f));
        crossPaint.setColor(ModxTheme.withAlpha(ModxTheme.ACCENT, 0x66));

        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(ModxTheme.ACCENT);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dpf(2f));
        glowPaint.setColor(ModxTheme.withAlpha(ModxTheme.ACCENT, 0x55));

        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(ModxTheme.FG);

        labelPaint.setColor(ModxTheme.SUBTLE);
        labelPaint.setTextSize(dp(9));
        labelPaint.setAntiAlias(true);
        labelPaint.setFakeBoldText(true);

        coordPaint.setColor(ModxTheme.FG);
        coordPaint.setTextSize(dp(10));
        coordPaint.setTextAlign(Paint.Align.CENTER);
        coordPaint.setAntiAlias(true);
        coordPaint.setFakeBoldText(true);

        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(ModxTheme.withAlpha(ModxTheme.ACCENT, 0x80));

        setClickable(true);
        setFocusable(false);
    }

    private float dp(float v)  { return v * density; }
    private float dpf(float v) { return v * density; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension((int)dp(PAD_W_DP), (int)dp(PAD_H_DP));
    }

    // Content area (excludes drag bar at top)
    private float contentTop()    { return dp(DRAG_BAR_DP + 6); }
    private float contentBottom() { return getHeight() - dp(6); }
    private float contentLeft()   { return dp(6); }
    private float contentRight()  { return getWidth() - dp(6); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Rounded background
        RectF bg = new RectF(0, 0, w, h);
        canvas.drawRoundRect(bg, dp(ModxTheme.RADIUS_DP), dp(ModxTheme.RADIUS_DP), bgPaint);

        // Drag bar at top (visual only)
        RectF handle = new RectF(dp(50), dp(3), w - dp(50), dp(8));
        canvas.drawRoundRect(handle, dp(3), dp(3), handlePaint);

        // Content area
        float l = contentLeft(),  t = contentTop();
        float r = contentRight(), b = contentBottom();
        RectF content = new RectF(l, t, r, b);

        // Frame
        canvas.drawRoundRect(content, dp(ModxTheme.RADIUS_DP), dp(ModxTheme.RADIUS_DP), framePaint);

        // Crosshair
        float cx = (l + r) / 2f;
        float cy = (t + b) / 2f;
        canvas.drawLine(l, cy, r, cy, crossPaint);
        canvas.drawLine(cx, t, cx, b, crossPaint);

        // Tiny corner labels
        canvas.drawText("NW", l + dp(4), t + dp(11), labelPaint);
        canvas.drawText("NE", r - dp(4) - labelPaint.measureText("NE"),
                        t + dp(11), labelPaint);
        canvas.drawText("SW", l + dp(4), b - dp(3), labelPaint);
        canvas.drawText("SE", r - dp(4) - labelPaint.measureText("SE"),
                        b - dp(3), labelPaint);

        // Marker
        if (markerNX >= 0f && markerNY >= 0f) {
            float mx = l + markerNX * (r - l);
            float my = t + markerNY * (b - t);

            canvas.drawCircle(mx, my, dp(15), glowPaint);
            canvas.drawCircle(mx, my, dp(9), markerPaint);
            canvas.drawCircle(mx, my, dp(3.5f), whitePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();

        boolean onDragBar = (y < dp(DRAG_BAR_DP));

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (onDragBar) {
                    dragging = true;
                    padTouched = false;
                    dragStartX = e.getRawX();
                    dragStartY = e.getRawY();
                    if (Menu.instance != null && Menu.instance.tpPadParams != null) {
                        winStartX = Menu.instance.tpPadParams.x;
                        winStartY = Menu.instance.tpPadParams.y;
                    }
                } else {
                    dragging = false;
                    padTouched = true;
                    handlePadTouch(x, y);
                }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    int dx = (int)(e.getRawX() - dragStartX);
                    int dy = (int)(e.getRawY() - dragStartY);
                    if (Menu.instance != null
                            && Menu.instance.tpPadWindowManager != null
                            && Menu.instance.tpPadParams != null) {
                        Menu.instance.tpPadParams.x = winStartX + dx;
                        Menu.instance.tpPadParams.y = winStartY + dy;
                        try {
                            Menu.instance.tpPadWindowManager
                                .updateViewLayout(this, Menu.instance.tpPadParams);
                        } catch (Exception ignored) { }
                    }
                } else if (padTouched) {
                    handlePadTouch(x, y);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                padTouched = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(e);
    }

    void clearMarker() {
        markerNX = -1f;
        markerNY = -1f;
        invalidate();
    }

    void setMarkerNorm(float nx, float ny) {
        markerNX = nx;
        markerNY = ny;
        invalidate();
    }

    private void handlePadTouch(float x, float y) {
    float l = contentLeft(),  t = contentTop();
    float r = contentRight(), b = contentBottom();
    if (r <= l || b <= t) return;

    if (x < l) x = l; if (x > r) x = r;
    if (y < t) y = t; if (y > b) y = b;

    float nx = (x - l) / (r - l);
    float ny = (y - t) / (b - t);

    // ★ NEW: visual marker-ও safe range-এ clamp করি
    final float MARGIN = 0.18f;
    if (nx < MARGIN)          nx = MARGIN;
    if (nx > 1.0f - MARGIN)   nx = 1.0f - MARGIN;
    if (ny < MARGIN)          ny = MARGIN;
    if (ny > 1.0f - MARGIN)   ny = 1.0f - MARGIN;

    markerNX = nx; markerNY = ny;
    invalidate();

    if (Menu.instance != null) {
        Menu.instance.syncTeleportMarkersFromFloating(nx, ny);
    }
    try { Menu.SetTeleportTargetNorm(nx, ny); }
    catch (Throwable ignored) { }
}
}
private void TeleportPad(LinearLayout linLayout) {
    // Helper text
    TextView hint = new TextView(getContext);
    hint.setText("Tap inside -> teleport to safe spot\nDrag top bar to move floating pad");
    hint.setTextColor(ModxTheme.MUTED);
    hint.setTextSize(10f);
    hint.setTypeface(fontRegular);
    hint.setPadding(dp(8), dp(2), dp(8), dp(6));
    linLayout.addView(hint);

    // Menu version
    TeleportPadView pad = new TeleportPadView(getContext);
    LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    lp.setMargins(dp(6), dp(4), dp(6), dp(6));
    pad.setLayoutParams(lp);
    mMenuTeleportPad = pad;
    linLayout.addView(pad);
}

private class TeleportPadView extends View {
    private final Paint bgPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float markerNX = -1f;
    private float markerNY = -1f;

    TeleportPadView(Context ctx) {
        super(ctx);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(ModxTheme.SURFACE);

        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dpf(1.5f));
        framePaint.setColor(ModxTheme.withAlpha(COLOR_ACCENT, 0xCC));

        crossPaint.setStyle(Paint.Style.STROKE);
        crossPaint.setStrokeWidth(dpf(1f));
        crossPaint.setColor(ModxTheme.withAlpha(COLOR_ACCENT, 0x55));

        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(COLOR_ACCENT);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(2));
        glowPaint.setColor(ModxTheme.withAlpha(COLOR_ACCENT, 0x55));

        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(ModxTheme.FG);

        labelPaint.setColor(ModxTheme.SUBTLE);
        labelPaint.setTextSize(dp(9));
        labelPaint.setAntiAlias(true);
        labelPaint.setFakeBoldText(true);

        setClickable(true);
    }

    void setAccentColor(int c) {
        markerPaint.setColor(c);
        framePaint.setColor(ModxTheme.withAlpha(c, 0xCC));
        crossPaint.setColor(ModxTheme.withAlpha(c, 0x55));
        glowPaint.setColor(ModxTheme.withAlpha(c, 0x55));
        invalidate();
    }

    void clearMarker() {
        markerNX = -1f;
        markerNY = -1f;
        invalidate();
    }

    void setMarkerNorm(float nx, float ny) {
        markerNX = nx;
        markerNY = ny;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        if (w <= 0) w = dp(220);
        int h = (int)(w * 0.55f);      // rectangular
        if (h > dp(130)) h = dp(130);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = dp(4);
        RectF rect = new RectF(pad, pad, w - pad, h - pad);

        canvas.drawRoundRect(new RectF(0, 0, w, h), dpf(ModxTheme.RADIUS_DP), dpf(ModxTheme.RADIUS_DP), bgPaint);
        canvas.drawRoundRect(rect, dpf(ModxTheme.RADIUS_DP), dpf(ModxTheme.RADIUS_DP), framePaint);

        float cx = w / 2f;
        float cy = h / 2f;
        canvas.drawLine(rect.left, cy, rect.right, cy, crossPaint);
        canvas.drawLine(cx, rect.top, cx, rect.bottom, crossPaint);

        canvas.drawText("NW", rect.left + dp(4), rect.top + dp(11), labelPaint);
        canvas.drawText("NE", rect.right - dp(4) - labelPaint.measureText("NE"),
                        rect.top + dp(11), labelPaint);
        canvas.drawText("SW", rect.left + dp(4), rect.bottom - dp(3), labelPaint);
        canvas.drawText("SE", rect.right - dp(4) - labelPaint.measureText("SE"),
                        rect.bottom - dp(3), labelPaint);

        if (markerNX >= 0f && markerNY >= 0f) {
            float mx = rect.left + markerNX * rect.width();
            float my = rect.top  + markerNY * rect.height();
            canvas.drawCircle(mx, my, dp(14), glowPaint);
            canvas.drawCircle(mx, my, dp(8), markerPaint);
            canvas.drawCircle(mx, my, dp(3), whitePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return false;

        boolean enabled = false;
        try { enabled = Menu.GetTeleportEnabled(); }
        catch (Throwable ignored) { }
        if (!enabled) return false;

        float pad = dp(4);
        float innerW = w - 2f * pad;
        float innerH = h - 2f * pad;
        if (innerW <= 0f || innerH <= 0f) return false;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);
                handleTouch(e.getX(), e.getY(), pad, innerW, innerH);
                return true;
            case MotionEvent.ACTION_MOVE:
                handleTouch(e.getX(), e.getY(), pad, innerW, innerH);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void handleTouch(float x, float y, float pad,
                         float innerW, float innerH) {
    if (x < pad) x = pad;
    if (x > pad + innerW) x = pad + innerW;
    if (y < pad) y = pad;
    if (y > pad + innerH) y = pad + innerH;

    float nx = (x - pad) / innerW;
    float ny = (y - pad) / innerH;

    // ★ NEW: same safe clamp
    final float MARGIN = 0.18f;
    if (nx < MARGIN)          nx = MARGIN;
    if (nx > 1.0f - MARGIN)   nx = 1.0f - MARGIN;
    if (ny < MARGIN)          ny = MARGIN;
    if (ny > 1.0f - MARGIN)   ny = 1.0f - MARGIN;

    markerNX = nx; markerNY = ny;
    invalidate();

    syncTeleportMarkersFromMenu(nx, ny);
    try { Menu.SetTeleportTargetNorm(nx, ny); }
    catch (Throwable ignored) { }
}
}

    // ================================================================
    // SEC_DIAG-only live self-test inspection & control hooks
    // Compiled out cleanly when BuildConfig.SEC_DIAG == false.
    // ================================================================
    boolean diagIsLoggedIn() {
        return BuildConfig.SEC_DIAG && isLoggedIn;
    }

    String diagGetActiveUser() {
        return BuildConfig.SEC_DIAG ? activeSessionUser : "";
    }

    void diagTriggerResetAll() {
        if (!BuildConfig.SEC_DIAG) return;
        resetAllFeaturesAction(null, null, null, null, "Reset All Features", null);
    }

    void diagClickMode(int modeFeatNum) {
        if (!BuildConfig.SEC_DIAG) return;
        ModeButtonHolder h = mModeButtons.get(modeFeatNum);
        if (h != null) {
            handleModeButtonToggle(h);
        }
    }

    void diagSetToggleUiAndSync(int featNum, boolean val) {
        if (!BuildConfig.SEC_DIAG) return;
        setToggleState(featNum, val, false, true);
    }

    void diagSetSeekBarUiAndSync(int featNum, int val) {
        if (!BuildConfig.SEC_DIAG) return;
        setSeekBarValue(featNum, val, false, true);
    }

    void diagSetSpinnerUiAndSync(int featNum, int idx) {
        if (!BuildConfig.SEC_DIAG) return;
        setSpinnerValue(featNum, idx, false, true);
    }

    boolean diagIsModeUiActive(int modeFeatNum) {
        if (!BuildConfig.SEC_DIAG) return false;
        ModeButtonHolder h = mModeButtons.get(modeFeatNum);
        return h != null && h.state != null && h.state[0];
    }

    boolean diagGetToggleUi(int featNum) {
        if (!BuildConfig.SEC_DIAG) return false;
        ToggleHolder h = mToggles.get(featNum);
        return h != null && h.state != null && h.state[0];
    }

    int diagGetSeekBarUi(int featNum) {
        if (!BuildConfig.SEC_DIAG) return -1;
        SeekBarHolder h = mSeekBars.get(featNum);
        return h != null && h.seekBar != null ? h.seekBar.getProgress() : -1;
    }

    String diagGetSeekBarChipText(int featNum) {
        if (!BuildConfig.SEC_DIAG) return "";
        SeekBarHolder h = mSeekBars.get(featNum);
        return h != null && h.chip != null && h.chip.getText() != null
                ? h.chip.getText().toString() : "";
    }

    int diagGetSpinnerUi(int featNum) {
        if (!BuildConfig.SEC_DIAG) return -1;
        SpinnerHolder h = mSpinners.get(featNum);
        return h != null ? h.currentIdx : -1;
    }

    String diagGetSpinnerText(int featNum) {
        if (!BuildConfig.SEC_DIAG) return "";
        SpinnerHolder h = mSpinners.get(featNum);
        return h != null && h.nameView != null && h.nameView.getText() != null
                ? h.nameView.getText().toString() : "";
    }

    void diagSelectTab(String tabName) {
        if (!BuildConfig.SEC_DIAG) return;
        selectTabByName(tabName);
    }

    String diagGetSelectedTabName() {
        if (!BuildConfig.SEC_DIAG) return "";
        String t = getCurrentSelectedTabName();
        return t != null ? t : "";
    }

    void diagRebuildMenusPreservingTab() {
        if (!BuildConfig.SEC_DIAG) return;
        buildFeaturesAndCategories(GetFeatureList());
    }

    void diagRestorePostTestUiAndNative(int restoredAccent) {
        if (!BuildConfig.SEC_DIAG) return;
        applyMenuColorLive(restoredAccent);
        FeatureStateStore.onSessionLockedOut();
        buildFeaturesAndCategories(GetFeatureList());
    }
}