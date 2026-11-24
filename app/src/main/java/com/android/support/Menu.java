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

    // ---- "Obsidian & Gilt" palette : graphite-navy surfaces, champagne-gold accent ----
    // ==== DEFAULT ACCENT = SKY BLUE (changeable via Settings) ====
// ==== DEFAULT = login menu এর green ====
int COLOR_ACCENT      = Color.parseColor("#3DDB87");
int COLOR_ACCENT_2    = Color.parseColor("#2BB673");
int COLOR_ACCENT_3    = Color.parseColor("#6EE7A9");
int COLOR_SUCCESS     = Color.parseColor("#4FBA82");
int COLOR_DANGER      = Color.parseColor("#EF4444");

// ==== FIXED GREEN GLOW (both menus) ====
final int GLOW_COLOR_1 = Color.parseColor("#3DDB87");
final int GLOW_COLOR_2 = Color.parseColor("#2BB673");

int COLOR_BG_TOP      = Color.parseColor("#12141C");
int COLOR_BG_BOTTOM   = Color.parseColor("#08090D");
int COLOR_CARD        = Color.parseColor("#181B24");
int COLOR_CARD_HI     = Color.parseColor("#20242F");
int COLOR_CARD_BORDER = Color.parseColor("#2E313C");
int COLOR_TRACK       = Color.parseColor("#282B35");
int COLOR_TEXT_MUTED  = Color.parseColor("#8D8F99");
int BTN_GRAD_1        = Color.parseColor("#2E3550");
int BTN_GRAD_2        = Color.parseColor("#4C5C8C");

// Legacy refs
// Legacy refs
int TEXT_COLOR            = Color.parseColor("#3DDB87");
int TEXT_COLOR_2          = Color.parseColor("#ECE8DF");
int BTN_COLOR             = Color.parseColor("#3DDB87");
int MENU_BG_COLOR         = Color.parseColor("#0C0E14");
int MENU_FEATURE_BG_COLOR = Color.parseColor("#181B24");
int BORDER_COLOR          = Color.parseColor("#3DDB87");

int ToggleON  = Color.parseColor("#4FBA82");
int ToggleOFF = Color.parseColor("#C25C56");
int BtnON     = Color.parseColor("#4FBA82");
int BtnOFF    = Color.parseColor("#C25C56");
int CategoryBG    = Color.parseColor("#151822");
int SeekBarColor  = Color.parseColor("#3DDB87");
int SeekBarProgressColor = Color.parseColor("#3DDB87");
int CheckBoxColor = Color.parseColor("#3DDB87");
int RadioColor    = Color.parseColor("#3DDB87");
int CollapseColor = Color.parseColor("#171A24");
String NumberTxtColor = "#3DDB87";

    // ---- Default menu / window dimensions ----
    private static final int   POS_X       = 5;
    private static final int   POS_Y       = 100;
    private static final int   MENU_WIDTH  = 272;
    private static final int   MENU_HEIGHT = 352;
    private static final float MENU_CORNER = 10f;
    private static final float ICON_SIZE   = 65f;
    private static final float ICON_ALPHA  = 1f;

    // ---- Glow / frame ----
    private static final int   GLOW_DP   = 6;     // subtler ambient edge-light, not a neon halo
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
    
    // Header / shimmer — login screen এ লুকানো থাকবে, login হলে দেখানো হবে
private View mHeaderView = null;
private View mShimmerView = null;
// Header views যাতে color change এ update করা যায়
private TitanicTextView mTitleView    = null;
private TitanicTextView mProView      = null;
private ImageView       mSettingsIcon = null;
private View            mShimmerViewRef = null;
private float           mCurrentHueF    = 145f;
private TextView mSubTitleView = null;
// ---- NEW: additional view references for live theming ----
private Button          mMinimizeBtn    = null;
private Button          mHideBtn        = null;
private TextView        mProBadgeText   = null;
private int             mColorPickerOriginalColor = 0;

// ===== Teleport Pad touch overlay =====
private View tpPadView = null;
private WindowManager tpPadWindowManager = null;
private WindowManager.LayoutParams tpPadParams = null;

public static Menu instance = null;
public static native boolean IsSmoothTeleportActive();

    // Typography (loaded from assets/fonts if present, otherwise clean system sans)
    private Typeface fontRegular;
    private Typeface fontMedium;
    private Typeface fontBold;

    // Glow animation
    private GlowFrameDrawable glowDrawable;
    private ValueAnimator glowAnimator;
    private final ArgbEvaluator argb = new ArgbEvaluator();

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

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean mProgrammaticModeUpdate = false;

    // ===== Mode buttons mutual exclusion =====
    private static class ModeButtonHolder {
        int featNum;
        String name;
        Button button;
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
        GradientDrawable thumbGlow;
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
        TabIcon chevIcon;
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
    }
    private final HashMap<Integer, RadioGroupHolder> mRadioGroups =
            new HashMap<Integer, RadioGroupHolder>();

    private static class ColorPickerHolder {
        int featNum;
        String featName;
        Button button;
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
        TabIcon chevIcon;
    }
    private final ArrayList<TextView> mCategoryViews = new ArrayList<TextView>();
    private final ArrayList<CollapseHolder> mCollapseHolders = new ArrayList<CollapseHolder>();
    private final ArrayList<Button> mLinkButtons = new ArrayList<Button>();
    private Button mMenuColorBtn = null;
    private View mBottomDivider = null;
    private TeleportPadView mMenuTeleportPad = null;
    private AlertDialog activeColorPickerDialog = null;

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
private PopupWindow activeDropdownPopup = null;
private View sidebarDivider = null;
private int effectivePosY = POS_Y;
private static final long SESSION_REVERIFY_INTERVAL_MS = 30L * 60L * 1000L;
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
                COLOR_ACCENT_2 = darken(safe, 0.65f);
                COLOR_ACCENT_3 = lighten(safe, 0.35f);
                TEXT_COLOR = safe;
                BTN_COLOR  = safe;
                BORDER_COLOR = safe;
                SeekBarColor = safe;
                SeekBarProgressColor = safe;
                CheckBoxColor = safe;
                RadioColor    = safe;
                NumberTxtColor = String.format("#%06X", (0xFFFFFF & safe));
            }
            mCurrentHueF = getHueFromColor(COLOR_ACCENT);
        } catch (Exception ignored) { }

        // Drop Poppins / Inter files into assets/fonts to upgrade the typography.
        fontRegular = loadFont(new String[]{"fonts/poppins_regular.ttf", "fonts/inter_regular.ttf"}, "sans-serif", Typeface.NORMAL);
        fontMedium  = loadFont(new String[]{"fonts/poppins_medium.ttf", "fonts/inter_medium.ttf"}, "sans-serif-medium", Typeface.NORMAL);
        fontBold    = loadFont(new String[]{"fonts/poppins_semibold.ttf", "fonts/poppins_bold.ttf", "fonts/inter_bold.ttf"}, "sans-serif", Typeface.BOLD);

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
        // ---------------- Frame: animated glow + gradient border ----------------
        menuFrame = new FrameLayout(context);
        menuFrame.setVisibility(View.GONE);
        menuFrame.setLayoutParams(new RelativeLayout.LayoutParams(initW, initH));
        menuFrame.setClipChildren(false);
        menuFrame.setClipToPadding(false);
        final int inset = dp(GLOW_DP) + dp(BORDER_DP);
        menuFrame.setPadding(inset, inset, inset, inset);
        glowDrawable = new GlowFrameDrawable(dpf(MENU_CORNER), dp(GLOW_DP), dp(BORDER_DP), GLOW_COLOR_1,
                                     new int[]{GLOW_COLOR_1, GLOW_COLOR_2, GLOW_COLOR_1, GLOW_COLOR_2});
        menuFrame.setBackground(glowDrawable);

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
        GradientDrawable menuBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                                                       new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        final float innerRadius = dpf(MENU_CORNER) - dp(BORDER_DP);
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
        header.setPadding(dp(12), dp(9), dp(8), dp(7));
        header.setOnTouchListener(onTouchListener());

        LinearLayout brand = new LinearLayout(context);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

        final TitanicTextView title = new TitanicTextView(context);
        title.setTextSize(19);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setTextColor(COLOR_ACCENT);
        title.setTypeface(fontBold);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) title.setLetterSpacing(0.03f);
        title.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView subTitle = new TextView(context);
        subTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        subTitle.setMarqueeRepeatLimit(-1);
        subTitle.setSingleLine(true);
        subTitle.setSelected(true);
        subTitle.setTextColor(Color.WHITE);
        subTitle.setTypeface(fontRegular);
        subTitle.setTextSize(9.5f);
        subTitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        subTitle.setPadding(0, dp(1), 0, 0);
        subTitle.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        brand.addView(title);
        brand.addView(subTitle);

        final TitanicTextView proTitle = new TitanicTextView(context);
        proTitle.setText("PRO");
        proTitle.setTextSize(10);
        proTitle.setGravity(Gravity.CENTER);
        proTitle.setTextColor(Color.WHITE);
        proTitle.setTypeface(fontBold);
        proTitle.setPadding(dp(8), dp(2), dp(8), dp(2));
        proTitle.setBackground(cardBg(withAlpha(COLOR_SUCCESS, 0x1A), withAlpha(COLOR_SUCCESS, 0x99), 8));
        LinearLayout.LayoutParams proLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        proLp.setMargins(dp(6), 0, dp(6), 0);
        proTitle.setLayoutParams(proLp);

        final ImageView settings = new ImageView(context);
        TabIcon gearIcon = new TabIcon(TabIcon.GEAR);
        gearIcon.setColor(COLOR_ACCENT);
        settings.setImageDrawable(gearIcon);
        settings.setScaleType(ImageView.ScaleType.FIT_CENTER);
        settings.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable gearBg = new GradientDrawable();
        gearBg.setShape(GradientDrawable.OVAL);
        gearBg.setColor(withAlpha(COLOR_ACCENT, 0x14));
        gearBg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0x55));
        settings.setBackground(gearBg);
        settings.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
        settings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.animate().rotationBy(120f).setDuration(320).setInterpolator(new OvershootInterpolator(1.4f)).start();
                    selectTabByName("Settings");
                }
            });

        header.addView(brand);
        header.addView(proTitle);
        header.addView(settings);

        LinearLayout.LayoutParams shimmerLp = new LinearLayout.LayoutParams(MATCH_PARENT, dp(2));
        shimmerLp.setMargins(dp(12), 0, dp(12), 0);
        ShimmerLine shimmer = new ShimmerLine(context, COLOR_ACCENT, COLOR_ACCENT_2);
        shimmer.setLayoutParams(shimmerLp);
        // ==== Save header views for color theming ====
mTitleView = title;
mSubTitleView = subTitle;
mProView = proTitle;
mSettingsIcon = settings;
mShimmerViewRef = shimmer;


        // ---------------- Body: sidebar + content (both natively drag-scrollable) ----------------
        mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.HORIZONTAL);
        mainContainer.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f));

        sidebarScroll = new SlimScrollView(context);
        sidebarScroll.setLayoutParams(new LinearLayout.LayoutParams(dp(96), MATCH_PARENT));
        sidebarScroll.setBackgroundColor(0x33000000);

        sidebarLayout = new LinearLayout(context);
        sidebarLayout.setOrientation(LinearLayout.VERTICAL);
        sidebarLayout.setPadding(dp(6), dp(6), dp(8), dp(6));
        sidebarScroll.addView(sidebarLayout);

        contentScrollView = new SlimScrollView(context);
        contentScrollView.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
        contentScrollView.setFillViewport(true);

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(0), dp(4), dp(6), dp(6));
        contentScrollView.addView(contentLayout);

        mainContainer.addView(sidebarScroll);
        sidebarDivider = makeDivider(true);
        mainContainer.addView(sidebarDivider);
        mainContainer.addView(contentScrollView);

        // ---------------- Bottom bar (HIDE/KILL + MINIMIZE) ----------------
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        bottomBar.setPadding(dp(8), dp(6), dp(26), dp(7));   // right space for resize handle

                Button hideBtn = new Button(context);
        LinearLayout.LayoutParams hideLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
        hideLp.setMargins(0, 0, dp(6), 0);
        hideBtn.setLayoutParams(hideLp);
        // ★ Bright crisp red — border + bg + text
        int DANGER_BRIGHT = Color.parseColor("#FF3B30");     // iOS-style vivid red
        hideBtn.setBackground(cardBg(
                withAlpha(DANGER_BRIGHT, 0x1F),              // soft tint bg
                withAlpha(DANGER_BRIGHT, 0x99),              // strong border
                6));                                          // ★ same radius as MINIMIZE
        hideBtn.setText("HIDE/KILL (Hold)");
        hideBtn.setAllCaps(false);
        hideBtn.setTextColor(DANGER_BRIGHT);                  // ★ pure red text (no lighten)
        hideBtn.setTextSize(11f);
        hideBtn.setTypeface(fontMedium);
        hideBtn.setSingleLine(true);
        hideBtn.setEllipsize(TextUtils.TruncateAt.END);
        hideBtn.setGravity(Gravity.CENTER);
        hideBtn.setMinHeight(0);
        hideBtn.setMinimumHeight(0);
        hideBtn.setMinWidth(0);
        hideBtn.setMinimumWidth(0);
        hideBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        flatten(hideBtn);
        addPressAnim(hideBtn);
        hideBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    collapseMenu(0f);
                    Toast.makeText(view.getContext(), "Icon hidden", Toast.LENGTH_SHORT).show();
                }
            });
        hideBtn.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View view) {
                    Preferences.flush("menu_hide_kill");
                    Toast.makeText(view.getContext(), "Menu killed", Toast.LENGTH_LONG).show();
                    stopChecking = true;
                    stopPeriodicSessionCheck();
                    activeSessionToken = "";
                    activeSessionUser = "";
                    activeSessionDeviceId = "";
                    detachLoginScrollListeners();
                    if (activeLoginHelper != null) {
                        activeLoginHelper.cleanup();
                        activeLoginHelper = null;
                    }
                    stopGlowAnimator();
                    rootFrame.removeView(mRootContainer);
                    mWindowManager.removeView(rootFrame);
                    return false;
                }
            });

        Button closeBtn = new Button(context);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        closeBtn.setBackground(cardBg(withAlpha(COLOR_ACCENT, 0x1A), withAlpha(COLOR_ACCENT, 0x77), 6));
        closeBtn.setText("MINIMIZE");
        closeBtn.setAllCaps(false);
        closeBtn.setTextColor(COLOR_ACCENT);
        closeBtn.setTextSize(11f);
        closeBtn.setTypeface(fontMedium);
        closeBtn.setSingleLine(true);
        closeBtn.setMinHeight(0);
        closeBtn.setMinimumHeight(0);
        closeBtn.setMinWidth(0);
        closeBtn.setMinimumWidth(0);
        closeBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        flatten(closeBtn);
        addPressAnim(closeBtn);
        closeBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    collapseMenu(ICON_ALPHA);
                }
            });

        bottomBar.addView(hideBtn);
        bottomBar.addView(closeBtn);
                // ==== NEW: save references for live theming ====
        mHideBtn        = hideBtn;
        mMinimizeBtn    = closeBtn;
        mProBadgeText   = proTitle;
        mHeaderView  = header;
mShimmerView = shimmer;
        mExpanded.addView(header);
mExpanded.addView(shimmer);

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

        // ---------------- Resize handle (diagonal grip) ----------------
        resizeHandle = new View(context);
        resizeHandle.setBackground(makeGripDrawable(withAlpha(COLOR_ACCENT, 0xE0), dpf(1.7f)));
        int hSize = dp(22);
        FrameLayout.LayoutParams rhLp = new FrameLayout.LayoutParams(hSize, hSize);
        rhLp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        rhLp.setMargins(0, 0, inset + dp(3), inset + dp(3));
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

        // Default position: bottom-right
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

// ★ Native Init "Waiting for game lib..." সেট করতে পারে → সাথে সাথে override
title.setText("ModX Lab");
subTitle.setText("Subway Surf V3.70.2");

// ★ Periodically re-check — Init async হলে তখনও override হবে
final TextView fSub = subTitle;
final TextView fTitle = title;
final Handler subGuard = new Handler();
subGuard.postDelayed(new Runnable() {
    @Override
    public void run() {
        try {
            CharSequence s = fSub.getText();
            if (s != null && s.toString().contains("Waiting")) {
                fSub.setText("Subway Surf V3.70.2");
            }
        } catch (Exception ignored) { }
        subGuard.postDelayed(this, 1000);
    }
}, 1000);

new Titanic().start(title);
new Titanic().start(proTitle);
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
        int darkInk = Color.parseColor("#0B0D12");
        return (contrastRatio(bgColor, darkInk) >= contrastRatio(bgColor, Color.WHITE))
                ? darkInk
                : Color.WHITE;
    }

    private int ensureReadableAccent(int rawColor) {
        int opaque = 0xFF000000 | rawColor;
        double minContrast = 3.5;
        if (contrastRatio(opaque, COLOR_CARD) >= minContrast) {
            return opaque;
        }
        float[] hsv = new float[3];
        Color.colorToHSV(opaque, hsv);

        // Step 1: raise brightness (V) up to 1.0 preserving hue & saturation
        float[] test = new float[]{ hsv[0], hsv[1], 1.0f };
        int fullV = 0xFF000000 | Color.HSVToColor(test);
        if (contrastRatio(fullV, COLOR_CARD) >= minContrast) {
            float lo = hsv[2];
            float hi = 1.0f;
            for (int i = 0; i < 18; i++) {
                float mid = (lo + hi) * 0.5f;
                test[2] = mid;
                int c = 0xFF000000 | Color.HSVToColor(test);
                if (contrastRatio(c, COLOR_CARD) >= minContrast) {
                    hi = mid;
                } else {
                    lo = mid;
                }
            }
            test[2] = hi;
            return 0xFF000000 | Color.HSVToColor(test);
        }

        // Step 2: for very deep pure blues/violets where V=1 is still < 3.5:1,
        // slightly reduce saturation toward white until contrast >= 3.5
        test[2] = 1.0f;
        float lo = 0f;
        float hi = hsv[1];
        for (int i = 0; i < 18; i++) {
            float mid = (lo + hi) * 0.5f;
            test[1] = mid;
            int c = 0xFF000000 | Color.HSVToColor(test);
            if (contrastRatio(c, COLOR_CARD) >= minContrast) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        test[1] = lo;
        return 0xFF000000 | Color.HSVToColor(test);
    }

    private static boolean isLight(int color) {
        return bestTextOnColor(color) != Color.WHITE;
    }

    private static String hex(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
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
        g.setStroke(dp(1), stroke);
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

    // Soft "press in / bounce out" feedback. Returns false so click listeners still fire.
    private void addPressAnim(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            view.animate().scaleX(0.975f).scaleY(0.975f).alpha(0.92f).setDuration(100).setInterpolator(new DecelerateInterpolator()).start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(new OvershootInterpolator(1.3f)).start();
                            break;
                    }
                    return false;
                }
            });
    }

    private void animateStroke(final GradientDrawable g, int from, int to) {
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        va.setDuration(240);
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
            v.setBackgroundColor(withAlpha(COLOR_ACCENT, 0x24));
        } else {
            v.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, dp(1)));
            GradientDrawable line = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                                         new int[]{withAlpha(COLOR_ACCENT, 0x00), withAlpha(COLOR_ACCENT, 0x70), withAlpha(COLOR_ACCENT_2, 0x70), withAlpha(COLOR_ACCENT_2, 0x00)});
            v.setBackground(line);
        }
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
    menuFrame.setScaleX(0.86f);
    menuFrame.setScaleY(0.86f);
    menuFrame.animate()
        .alpha(1f).scaleX(1f).scaleY(1f)
        .setDuration(280)
        .setInterpolator(new DecelerateInterpolator(1.8f))
        .start();
    startGlowAnimator();
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
        stopGlowAnimator();
        mCollapsed.setVisibility(View.VISIBLE);
        mCollapsed.setAlpha(iconAlpha);
        mCollapsed.setScaleX(0.6f);
        mCollapsed.setScaleY(0.6f);
        mCollapsed.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.4f)).start();

        menuFrame.animate().cancel();
        menuFrame.animate()
            .alpha(0f).scaleX(0.9f).scaleY(0.9f)
            .setDuration(170)
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

    // Border light rotates slowly and the glow "breathes" between cyan and violet
    private void startGlowAnimator() {
        if (glowDrawable == null) return;
        if (glowAnimator != null && glowAnimator.isRunning()) return;
        glowAnimator = ValueAnimator.ofFloat(0f, 1f);
        glowAnimator.setDuration(9000);
        glowAnimator.setInterpolator(new LinearInterpolator());
        glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        glowAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    float p = (Float) a.getAnimatedValue();
                    double s = Math.sin(p * Math.PI * 2.0 * 4.0);
                    float intensity = (float) (0.78 + 0.22 * s);
                    int color = (Integer) argb.evaluate((float) ((s + 1.0) / 2.0), GLOW_COLOR_1, GLOW_COLOR_2);
                    glowDrawable.update(p, intensity, color);
                }
            });
        glowAnimator.start();
    }

    private void stopGlowAnimator() {
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
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
    // Animated glow + gradient border frame (drawn behind the menu surface)
    // ================================================================
    private static class GlowFrameDrawable extends Drawable {
        private static final int STEPS = 10;

        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF base = new RectF();
        private final RectF tmp = new RectF();
        private final Matrix matrix = new Matrix();
        private final float radius, glowSize, borderWidth;
        private final int[] sweepColors;
        private SweepGradient sweep;
        private float phase = 0f;
        private float intensity = 1f;
        private int glowColor;

        GlowFrameDrawable(float radius, float glowSize, float borderWidth, int glowColor, int[] sweepColors) {
            this.radius = radius;
            this.glowSize = glowSize;
            this.borderWidth = borderWidth;
            this.glowColor = glowColor;
            this.sweepColors = sweepColors;
            glowPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(borderWidth);
        }

        void update(float phase, float intensity, int glowColor) {
            this.phase = phase;
            this.intensity = intensity;
            this.glowColor = glowColor;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect b) {
            super.onBoundsChange(b);
            if (b.width() > 0 && b.height() > 0) {
                sweep = new SweepGradient(b.exactCenterX(), b.exactCenterY(), sweepColors, null);
                borderPaint.setShader(sweep);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0 || sweep == null) return;

            base.set(b.left + glowSize, b.top + glowSize, b.right - glowSize, b.bottom - glowSize);
            float step = glowSize / STEPS;
            glowPaint.setStrokeWidth(step + 1f);

            for (int i = STEPS; i >= 1; i--) {
                float d = (i - 0.5f) / STEPS;
                float a = 0.22f * (1f - d) * (1f - d) * intensity;
                glowPaint.setColor(glowColor);
                glowPaint.setAlpha(Math.max(0, Math.min(255, (int) (255f * a))));
                float inflate = (i - 0.5f) * step;
                tmp.set(base.left - inflate, base.top - inflate, base.right + inflate, base.bottom + inflate);
                canvas.drawRoundRect(tmp, radius + inflate, radius + inflate, glowPaint);
            }

            matrix.setRotate(phase * 360f, b.exactCenterX(), b.exactCenterY());
            sweep.setLocalMatrix(matrix);
            float half = borderWidth / 2f;
            tmp.set(base.left + half, base.top + half, base.right - half, base.bottom - half);
            canvas.drawRoundRect(tmp, radius - half, radius - half, borderPaint);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================
    // Looping animated views (auto start / stop with visibility)
    // ================================================================
    private static abstract class LoopView extends View {
        protected float progress;
        private final long duration;
        private ValueAnimator animator;
        private boolean attached;

        LoopView(Context context, long duration) {
            super(context);
            this.duration = duration;
        }

        private void sync() {
            boolean run = attached && isShown() && getWindowVisibility() == View.VISIBLE;
            if (run) {
                if (animator == null) {
                    animator = ValueAnimator.ofFloat(0f, 1f);
                    animator.setDuration(duration);
                    animator.setInterpolator(new LinearInterpolator());
                    animator.setRepeatCount(ValueAnimator.INFINITE);
                    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator a) {
                                progress = (Float) a.getAnimatedValue();
                                invalidate();
                            }
                        });
                }
                if (!animator.isStarted()) animator.start();
            } else if (animator != null && animator.isStarted()) {
                animator.cancel();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            sync();
        }

        @Override
        protected void onDetachedFromWindow() {
            attached = false;
            sync();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onVisibilityChanged(View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            sync();
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            sync();
        }
    }

    // Rotating neon ring + pulsing glow around the collapsed icon
    private static class GlowRingView extends LoopView {
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ringRect = new RectF();
        private final RectF tmp = new RectF();
        private final Matrix matrix = new Matrix();
        private final float density;
        private final int c1, c2;
        private SweepGradient sweep;

        GlowRingView(Context context, int c1, int c2) {
            super(context, 3200);
            this.density = safeDisplayMetrics(context).density;
            this.c1 = c1;
            this.c2 = c2;
            glowPaint.setStyle(Paint.Style.STROKE);
            basePaint.setStyle(Paint.Style.STROKE);
            basePaint.setStrokeWidth(1.1f * density);
            basePaint.setColor(withAlpha(c1, 0x38));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(1.8f * density);
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w > 0 && h > 0) {
                sweep = new SweepGradient(w / 2f, h / 2f,
                                          new int[]{withAlpha(c1, 0), c1, c2, withAlpha(c2, 0)},
                                          new float[]{0f, 0.45f, 0.8f, 1f});
                ringPaint.setShader(sweep);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0 || sweep == null) return;

            float pad = 11f * density;
            float gap = 3f * density;
            float radius = (w - 2f * pad) * ICON_CORNER_FRACTION + gap;
            ringRect.set(pad - gap, pad - gap, w - pad + gap, h - pad + gap);

            float glowSize = pad - gap - density;
            int steps = 7;
            float step = glowSize / steps;
            float pulse = 0.65f + 0.35f * (float) Math.sin(progress * 2.0 * Math.PI);
            glowPaint.setStrokeWidth(step + 1f);
            for (int i = steps; i >= 1; i--) {
                float d = (i - 0.5f) / steps;
                float a = 0.28f * (1f - d) * (1f - d) * pulse;
                glowPaint.setColor(c1);
                glowPaint.setAlpha(Math.max(0, Math.min(255, (int) (255f * a))));
                float inflate = (i - 0.5f) * step;
                tmp.set(ringRect.left - inflate, ringRect.top - inflate, ringRect.right + inflate, ringRect.bottom + inflate);
                canvas.drawRoundRect(tmp, radius + inflate, radius + inflate, glowPaint);
            }

            canvas.drawRoundRect(ringRect, radius, radius, basePaint);
            matrix.setRotate(progress * 360f, w / 2f, h / 2f);
            sweep.setLocalMatrix(matrix);
            canvas.drawRoundRect(ringRect, radius, radius, ringPaint);
        }
    }

    // Thin gradient line with a light sweeping across (header accent)
    private static class ShimmerLine extends LoopView {
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private int c1, c2;
        private LinearGradient hiShader;

        ShimmerLine(Context context, int c1, int c2) {
            super(context, 2600);
            this.c1 = c1;
            this.c2 = c2;
        }

        void setColors(int c1, int c2) {
            this.c1 = c1;
            this.c2 = c2;
            int w = getWidth();
            if (w > 0) {
                basePaint.setShader(new LinearGradient(0, 0, w, 0,
                        new int[]{withAlpha(c1, 0), c1, c2, withAlpha(c2, 0)},
                        new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            }
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0) return;
            basePaint.setShader(new LinearGradient(0, 0, w, 0,
                                                   new int[]{withAlpha(c1, 0), c1, c2, withAlpha(c2, 0)},
                                                   new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            hiShader = new LinearGradient(0, 0, w * 0.28f, 0,
                                          new int[]{0x00FFFFFF, 0xDDFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            hiPaint.setShader(hiShader);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || hiShader == null) return;
            canvas.drawRect(0, 0, w, h, basePaint);
            matrix.setTranslate(-w * 0.28f + progress * (w * 1.28f), 0f);
            hiShader.setLocalMatrix(matrix);
            canvas.drawRect(0, 0, w, h, hiPaint);
        }
    }

    // ================================================================
    // Premium toggle switch (animated thumb + glow)
    // ================================================================
    private static class ToggleView extends View {
        interface Listener { void onChanged(boolean checked); }

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final ArgbEvaluator argb = new ArgbEvaluator();
        private final float density;
        private int onColor, onColor2, offColor;
        private boolean checked;
        private float t = 0f;
        private float scale = 1f;
        private float shaderScale = -1f;
        private LinearGradient onShader;
        private ValueAnimator anim;
        private Listener listener;

        ToggleView(Context context, int onColor, int onColor2, int offColor) {
            super(context);
            this.density = safeDisplayMetrics(context).density;
            this.onColor = onColor;
            this.onColor2 = onColor2;
            this.offColor = offColor;
            glowPaint.setStyle(Paint.Style.FILL);
            thumbPaint.setStyle(Paint.Style.FILL);
            trackPaint.setStyle(Paint.Style.FILL);
        }

        void setColors(int onColor, int onColor2, int offColor) {
            this.onColor = onColor;
            this.onColor2 = onColor2;
            this.offColor = offColor;
            this.onShader = null;
            this.shaderScale = -1f;
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
            anim.setDuration(260);
            anim.setInterpolator(new OvershootInterpolator(1.6f));
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
            setMeasuredDimension(Math.round(51f * scale * density), Math.round(28f * scale * density));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = scale * density;
            float trackW = 38f * s;
            float trackH = 20f * s;
            float left = 3f * s;
            float top = (getHeight() - trackH) / 2f;
            float r = trackH / 2f;
            float tc = Math.max(0f, Math.min(1f, t));

            trackPaint.setShader(null);
            trackPaint.setColor(offColor);
            rect.set(left, top, left + trackW, top + trackH);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            if (tc > 0.01f) {
                if (onShader == null || shaderScale != s) {
                    onShader = new LinearGradient(left, 0, left + trackW, 0, onColor, onColor2, Shader.TileMode.CLAMP);
                    shaderScale = s;
                }
                trackPaint.setShader(onShader);
                trackPaint.setAlpha((int) (255f * tc));
                canvas.drawRoundRect(rect, r, r, trackPaint);
                trackPaint.setShader(null);
            }

            float thumbR = r - 2.6f * s;
            float pos = Math.max(-0.1f, Math.min(1.1f, t));
            float cx = left + r + (trackW - 2f * r) * pos;
            float cy = top + r;

            if (tc > 0.01f) {
                for (int k = 3; k >= 1; k--) {
                    glowPaint.setColor(onColor);
                    glowPaint.setAlpha((int) (255f * 0.15f * tc * (4 - k) / 3f));
                    canvas.drawCircle(cx, cy, thumbR + k * 1.7f * s, glowPaint);
                }
            }
            thumbPaint.setColor((Integer) argb.evaluate(tc, 0xFFB7B5AC, 0xFFF6F2E8));
            canvas.drawCircle(cx, cy, thumbR, thumbPaint);
        }
    }

    // ================================================================
    // Seek bar track: rounded gradient bar with a soft glow
    // ================================================================
    private static class SeekTrackDrawable extends Drawable {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float barH;
        private int c1;
        private int c2;

        SeekTrackDrawable(float barH, int trackColor, int c1, int c2) {
            this.barH = barH;
            this.c1 = c1;
            this.c2 = c2;
            trackPaint.setColor(trackColor);
            glowPaint.setColor(c1);
            glowPaint.setAlpha(46);
        }

        void setColors(int c1, int c2) {
            this.c1 = c1;
            this.c2 = c2;
            glowPaint.setColor(c1);
            glowPaint.setAlpha(46);
            Rect b = getBounds();
            if (b.width() > 0) {
                progPaint.setShader(new LinearGradient(b.left, 0, Math.max(b.right, b.left + 1), 0, c1, c2, Shader.TileMode.CLAMP));
            }
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect b) {
            super.onBoundsChange(b);
            progPaint.setShader(new LinearGradient(b.left, 0, Math.max(b.right, b.left + 1), 0, c1, c2, Shader.TileMode.CLAMP));
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
                float gh = barH * 0.4f;
                rect.set(b.left, top - gh, Math.max(right, b.left + barH), bottom + gh);
                canvas.drawRoundRect(rect, r + gh, r + gh, glowPaint);
                rect.set(b.left, top, Math.max(right, b.left + barH), bottom);
                canvas.drawRoundRect(rect, r, r, progPaint);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================
    // Slim, themed scroll view (custom drawn scrollbar, native drag + fling)
    // ================================================================
    private static class SlimScrollView extends ScrollView {
        private static final int ACTIVE_ALPHA = 255;
        private static final int IDLE_ALPHA = 0;

        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private int accentColor = 0xFF3DDB87;   // dynamic accent

    void setAccentColor(int c) {
        this.accentColor = c;
        invalidate();
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
            return super.onTouchEvent(ev);
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

                        trackPaint.setColor(accentColor);
            trackPaint.setAlpha(barAlpha * 26 / 255);
            rect.set(left, trackTop, right, trackTop + trackH);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            thumbPaint.setColor(accentColor);
            thumbPaint.setAlpha(barAlpha);
            rect.set(left, thumbTop, right, thumbTop + thumbH);
            canvas.drawRoundRect(rect, r, r, thumbPaint);
        }
    }

    private static class TabIcon extends Drawable {
                static final int HOME = 0, USER = 1, RUN = 2, BOLT = 3, EYE = 4,
        GEAR = 5, PULSE = 6, TARGET = 7, GRID = 8, CHEVRON = 9,
        LOCATION = 10, CAMERA = 11, ROBOT = 12;

        private final int type;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        TabIcon(int type) {
            this.type = type;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(2f);
            fill.setStyle(Paint.Style.FILL);
            setColor(Color.WHITE);
        }

        void setColor(int color) {
            stroke.setColor(color);
            fill.setColor(color);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float size = Math.min(b.width(), b.height());
            float s = size / 24f;

            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);
            path.reset();

            switch (type) {
                case HOME:
                    path.moveTo(3f, 11f); path.lineTo(12f, 3f); path.lineTo(21f, 11f);
                    canvas.drawPath(path, stroke);
                    path.reset();
                    path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 20f); path.lineTo(18.5f, 20f); path.lineTo(18.5f, 9.5f);
                    canvas.drawPath(path, stroke);
                    path.reset();
                    path.moveTo(10f, 20f); path.lineTo(10f, 14f); path.lineTo(14f, 14f); path.lineTo(14f, 20f);
                    canvas.drawPath(path, stroke);
                    break;

                case USER:
                    canvas.drawCircle(12f, 7.5f, 3.8f, stroke);
                    path.moveTo(4.5f, 21f);
                    path.cubicTo(4.5f, 15.5f, 8f, 13.5f, 12f, 13.5f);
                    path.cubicTo(16f, 13.5f, 19.5f, 15.5f, 19.5f, 21f);
                    canvas.drawPath(path, stroke);
                    break;

                case RUN:
                    path.moveTo(5f, 5f); path.lineTo(12f, 12f); path.lineTo(5f, 19f);
                    canvas.drawPath(path, stroke);
                    path.reset();
                    path.moveTo(12f, 5f); path.lineTo(19f, 12f); path.lineTo(12f, 19f);
                    canvas.drawPath(path, stroke);
                    break;

                case BOLT:
                    path.moveTo(13f, 2f); path.lineTo(4.5f, 13.5f); path.lineTo(11f, 13.5f);
                    path.lineTo(10f, 22f); path.lineTo(19.5f, 9.5f); path.lineTo(13f, 9.5f);
                    path.close();
                    canvas.drawPath(path, stroke);
                    break;

                case EYE:
                    path.moveTo(1.5f, 12f);
                    path.quadTo(12f, 0.5f, 22.5f, 12f);
                    path.quadTo(12f, 23.5f, 1.5f, 12f);
                    path.close();
                    canvas.drawPath(path, stroke);
                    canvas.drawCircle(12f, 12f, 3.2f, stroke);
                    break;

                case GEAR: {
                        final int teeth = 8;
                        final float rIn = 7.2f, rOut = 10f;
                        final double[] offs = {-17, -9, 9, 17};
                        final float[] radii = {rIn, rOut, rOut, rIn};
                        for (int i = 0; i < teeth; i++) {
                            double c = Math.toRadians(i * 360.0 / teeth);
                            for (int k = 0; k < 4; k++) {
                                double a = c + Math.toRadians(offs[k]);
                                float x = 12f + (float) (radii[k] * Math.cos(a));
                                float y = 12f + (float) (radii[k] * Math.sin(a));
                                if (i == 0 && k == 0) path.moveTo(x, y);
                                else path.lineTo(x, y);
                            }
                        }
                        path.close();
                        canvas.drawPath(path, stroke);
                        canvas.drawCircle(12f, 12f, 3f, stroke);
                        break;
                    }

                case PULSE:
                    path.moveTo(1.5f, 12f); path.lineTo(7f, 12f); path.lineTo(10f, 4.5f);
                    path.lineTo(14f, 19.5f); path.lineTo(17f, 12f); path.lineTo(22.5f, 12f);
                    canvas.drawPath(path, stroke);
                    break;

                case TARGET:
                    canvas.drawCircle(12f, 12f, 7f, stroke);
                    canvas.drawCircle(12f, 12f, 1.8f, fill);
                    canvas.drawLine(12f, 2f, 12f, 5f, stroke);
                    canvas.drawLine(12f, 19f, 12f, 22f, stroke);
                    canvas.drawLine(2f, 12f, 5f, 12f, stroke);
                    canvas.drawLine(19f, 12f, 22f, 12f, stroke);
                    break;
                    
                                case LOCATION:
                    // Map pin — perfect for Teleport
                    path.moveTo(12f, 21.5f);
                    path.cubicTo(12f, 21.5f, 4.5f, 13.5f, 4.5f, 9.5f);
                    path.cubicTo(4.5f, 5.2f, 8.0f, 2.5f, 12f, 2.5f);
                    path.cubicTo(16.0f, 2.5f, 19.5f, 5.2f, 19.5f, 9.5f);
                    path.cubicTo(19.5f, 13.5f, 12f, 21.5f, 12f, 21.5f);
                    path.close();
                    canvas.drawPath(path, stroke);
                    canvas.drawCircle(12f, 9.5f, 3f, stroke);
                    break;

                case CAMERA:
                    // Camera body + lens
                    rect.set(3f, 7.5f, 21f, 19f);
                    canvas.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                    canvas.drawCircle(12f, 13.2f, 3.6f, stroke);
                    path.moveTo(8f, 7.5f);
                    path.lineTo(9.2f, 4.5f);
                    path.lineTo(14.8f, 4.5f);
                    path.lineTo(16f, 7.5f);
                    canvas.drawPath(path, stroke);
                    break;

                case ROBOT:
                    // Robot head
                    rect.set(5f, 8.5f, 19f, 19f);
                    canvas.drawRoundRect(rect, 2.2f, 2.2f, stroke);
                    canvas.drawCircle(9f, 13.5f, 1.4f, fill);
                    canvas.drawCircle(15f, 13.5f, 1.4f, fill);
                    canvas.drawLine(12f, 8.5f, 12f, 5f, stroke);
                    canvas.drawCircle(12f, 4f, 1.1f, fill);
                    canvas.drawLine(3f, 13.5f, 5f, 13.5f, stroke);
                    canvas.drawLine(19f, 13.5f, 21f, 13.5f, stroke);
                    break;

                case CHEVRON:
                    path.moveTo(5.5f, 9f); path.lineTo(12f, 15.5f); path.lineTo(18.5f, 9f);
                    canvas.drawPath(path, stroke);
                    break;

                default:
                    rect.set(4f, 4f, 10.5f, 10.5f);   canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                    rect.set(13.5f, 4f, 20f, 10.5f);  canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                    rect.set(4f, 13.5f, 10.5f, 20f);  canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                    rect.set(13.5f, 13.5f, 20f, 20f); canvas.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                    break;
            }
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { stroke.setAlpha(alpha); fill.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter cf) { stroke.setColorFilter(cf); fill.setColorFilter(cf); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

        private static int iconTypeFor(String name) {
    String n = name == null ? "" : name.toLowerCase();
    if (n.contains("setting"))                            return TabIcon.GEAR;
    if (n.contains("player"))                             return TabIcon.USER;
    if (n.contains("power"))                              return TabIcon.BOLT;
    if (n.contains("score"))                              return TabIcon.TARGET;
    if (n.contains("safety") || n.contains("guard"))      return TabIcon.EYE;
    if (n.contains("shop") || n.contains("cart"))         return TabIcon.GRID;
    if (n.contains("camera"))                             return TabIcon.CAMERA;
    if (n.contains("world") || n.contains("home"))        return TabIcon.HOME;
    if (n.contains("chams") || n.contains("visual"))      return TabIcon.PULSE;
    if (n.contains("main"))                               return TabIcon.HOME;
    return TabIcon.GRID;
}

    private static class TabHolder {
        String name;
        LinearLayout root;
        ImageView icon;
        TabIcon iconDrawable;
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
                        }
                        v.setAlpha(0.6f);
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
                        v.setAlpha(1f);
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
    // Telegram paper-plane icon
    // ================================================================
        private static class TelegramIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int sizePx;

        TelegramIcon(int color, float density) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            this.sizePx = (int) (18 * density);   // 18dp icon
        }

        @Override public int getIntrinsicWidth()  { return sizePx; }
        @Override public int getIntrinsicHeight() { return sizePx; }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;

            float size = Math.min(b.width(), b.height());
            float s = size / 24f;

            canvas.save();
            canvas.translate(b.left + (b.width() - size) / 2f,
                             b.top + (b.height() - size) / 2f);
            canvas.scale(s, s);

            // Telegram paper-plane
            path.reset();
            path.moveTo(22.0f, 3.0f);
            path.lineTo(2.0f, 11.5f);
            path.lineTo(9.5f, 14.0f);
            path.lineTo(11.5f, 20.0f);
            path.lineTo(14.5f, 15.0f);
            path.lineTo(21.0f, 19.5f);
            path.close();
            canvas.drawPath(path, paint);

            // Fold line — subtle darker overlay (safe)
            Paint strokeP = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokeP.setStyle(Paint.Style.STROKE);
            strokeP.setStrokeWidth(1.2f);
            strokeP.setColor(0x33000000);
            path.reset();
            path.moveTo(9.5f, 14.0f);
            path.lineTo(21.0f, 6.5f);
            canvas.drawPath(path, strokeP);

            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

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
        int iconSize = Math.max(dp(12), (int) (dp(18) * s));
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
            h.root.setPadding((int) (dp(iconOnly ? 2 : 9) * s), (int) (dp(10) * s),
                              (int) (dp(iconOnly ? 2 : 9) * s), (int) (dp(10) * s));
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
                            if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) moved = true;
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
    private void buildFeaturesAndCategories(String[] features) {
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
        mCollapseHolders.clear();
        mLinkButtons.clear();
        mMenuColorBtn = null;
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

        // ===== Pick Menu Color (shows current color + hex) =====
        settingsLay.addView(makeMenuColorButton(
                COLOR_ACCENT, "Menu Color", -29));
        settingsLay.addView(makeMenuColorButton(
                FeatureStateStore.DEFAULT_MENU_COLOR, "Reset to Default", -28));

        Category(settingsLay, "Community");

        // ===== Telegram button (sky-blue, paper-plane icon) =====
        ButtonTelegram(settingsLay, "Join our Telegram",
                "https://t.me/kayesahmmedpro");

        Button(settingsLay, -6, "Close Settings");
    }
        setupSidebarTabs();
        rescaleAll();
        applyTabMetrics();
        if (isLoggedIn) {
            Preferences.pushRestoredSnapshotToNativeOnce();
        }
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
        tabHolders.clear();
        sidebarLayout.removeAllViews();

        for (final String catName : categoryNames) {
            final TabHolder h = new TabHolder();
            h.name = catName;

            LinearLayout tab = new LinearLayout(getContext);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            lp.setMargins(0, dp(3), 0, dp(3));
            tab.setLayoutParams(lp);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(dp(9), dp(10), dp(9), dp(10));
            tab.setTag(h);
            h.root = tab;

            if (SHOW_TAB_ICONS) {
                h.iconDrawable = new TabIcon(iconTypeFor(catName));
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
            tabText.setTextSize(12);
            tabText.setTypeface(fontMedium);
            tabText.setSingleLine(true);
            tabText.setEllipsize(TextUtils.TruncateAt.END);
            tabText.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
            lpText.setMargins(SHOW_TAB_ICONS ? dp(7) : 0, 0, 0, 0);
            tabText.setLayoutParams(lpText);
            h.label = tabText;
            tab.addView(tabText);

            tab.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectTab(h);
                    }
                });
            addPressAnim(tab);

            tabHolders.add(h);
            sidebarLayout.addView(tab);
            styleTab(h, false);
        }

        if (tabHolders.size() > 0) selectTab(tabHolders.get(0));
    }

    private void styleTab(TabHolder h, boolean selected) {
        GradientDrawable bg;
        if (selected) {
            bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                      new int[]{withAlpha(COLOR_ACCENT, 0x38), withAlpha(COLOR_ACCENT_2, 0x1C)});
            bg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0xAA));
        } else {
            bg = new GradientDrawable();
            bg.setColor(0x0DFFFFFF);
        }
        bg.setCornerRadius(dp(5));
        h.root.setBackground(bg);
        h.label.setTextColor(selected ? Color.WHITE : COLOR_TEXT_MUTED);
        h.label.setTypeface(selected ? fontBold : fontMedium);
        if (h.iconDrawable != null) {
            h.iconDrawable.setColor(selected ? COLOR_ACCENT : COLOR_TEXT_MUTED);
        }
    }

    private void selectTab(TabHolder selected) {
        // ★ FIX: Close any open dropdown to prevent it from overlaying the new tab
        
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

        // content glides in, selected tab pops
        contentLayout.animate().cancel();
        contentLayout.setAlpha(0f);
        contentLayout.setTranslationY(dp(10));
        contentLayout.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(new DecelerateInterpolator(1.6f)).start();

        selected.root.setScaleX(0.93f);
        selected.root.setScaleY(0.93f);
        selected.root.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new OvershootInterpolator(1.3f)).start();
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
    final Handler handler = new Handler();
    handler.postDelayed(new Runnable() {
        @Override
        public void run() {
            // ★ Game lib-এর জন্য অপেক্ষা না — সরাসরি login বা main menu দেখাই
            if (!isLoggedIn) {
                showLoginScreen();
            } else {
                if (contentScrollView instanceof SlimScrollView) {
                    ((SlimScrollView) contentScrollView).setScrollingEnabled(true);
                }
                buildFeaturesAndCategories(GetFeatureList());
            }
        }
    }, 300);
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
    stopPeriodicSessionCheck();
    if (!isLoggedIn || stopChecking) return;
    sessionReverifyRunnable = new Runnable() {
        @Override
        public void run() {
            runPeriodicSessionCheck();
        }
    };
    sessionVerifyHandler.postDelayed(sessionReverifyRunnable, SESSION_REVERIFY_INTERVAL_MS);
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
                        startPeriodicSessionCheck();
                    } else if (isSessionRevokedReason(fReason)) {
                        forceLogoutToLogin(fReason);
                    } else {
                        // Offline or transient network error: keep active session and recheck next cycle
                        startPeriodicSessionCheck();
                    }
                }
            });
        }
    }).start();
}

private void forceLogoutToLogin(final String reason) {
    SecureTokenStore.clearToken(getContext);
    SecurityNative.clearVerifiedSession();
    Runnable task = new Runnable() {
        @Override
        public void run() {
            stopPeriodicSessionCheck();
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
            setWindowFocusable(false);
            isLoggedIn = true;

            if (mHeaderView  != null) mHeaderView.setVisibility(View.VISIBLE);
            if (mShimmerView != null) mShimmerView.setVisibility(View.VISIBLE);
            if (sidebarScroll != null) sidebarScroll.setVisibility(View.VISIBLE);
            if (sidebarDivider != null) sidebarDivider.setVisibility(View.VISIBLE);

            if (contentScrollView instanceof SlimScrollView) {
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
                            loginHelper.dismissTextActionMode();
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
            if (sw != lastLoginScreenW || sh != lastLoginScreenH) {
                lastLoginScreenW = sw;
                lastLoginScreenH = sh;
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
            if (vh != lastLoginViewportH || ch != lastLoginContentH || visBottom != lastLoginVisBottom) {
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
    if (tpPadView == null) return;
    tpPadView.setVisibility(visible ? View.VISIBLE : View.GONE);
    // (optional) hide করলে marker reset
    if (!visible) tpPadView.invalidate();
}
        public void onDestroy() {
    Preferences.flush("menu_onDestroy");
    stopChecking = true;
    stopPeriodicSessionCheck();
    activeSessionToken = "";
    activeSessionUser = "";
    activeSessionDeviceId = "";
    detachLoginScrollListeners();
    if (activeLoginHelper != null) {
        activeLoginHelper.cleanup();
        activeLoginHelper = null;
    }
    stopGlowAnimator();
    if (rootFrame != null && mWindowManager != null) {
        try { mWindowManager.removeView(rootFrame); } catch (Exception e) {}
    }
    if (tpPadView != null && tpPadWindowManager != null) {
        try { tpPadWindowManager.removeView(tpPadView); } catch (Exception e) {}
    }
    instance = null;
}
    private Button dialogButton(String text, boolean primary) {
        Button b = new Button(getContext);
        b.setText(text);
        b.setAllCaps(false);
        b.setTypeface(fontBold);
        b.setTextSize(12f);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        if (primary) {
            b.setTextColor(Color.WHITE);
            b.setBackground(gradBg(BTN_GRAD_1, BTN_GRAD_2, 8));
        } else {
            b.setTextColor(COLOR_TEXT_MUTED);
            b.setBackground(cardBg(0x0FFFFFFF, COLOR_CARD_BORDER, 6));
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
        GradientDrawable boxBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                                                      new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
        boxBg.setCornerRadius(dp(18));
        boxBg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0xAA));
        box.setBackground(boxBg);

        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(COLOR_ACCENT);
        t.setTypeface(fontBold);
        t.setTextSize(15f);
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
                        if (onPos != null) onPos.run();
                        if (ref[0] != null) ref[0].dismiss();
                    }
                });
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            plp.setMargins(dp(8), 0, 0, 0);
            row.addView(pos, plp);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setView(box);
        AlertDialog dialog = builder.create();
        ref[0] = dialog;
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (overlayRequired) {
                w.setType(Build.VERSION.SDK_INT >= 26 ? 2038 : 2002);
            }
        }
        dialog.show();
        return dialog;
    }

    private EditText makeEditText() {
        EditText e = new EditText(getContext);
        e.setTextColor(TEXT_COLOR_2);
        e.setHintTextColor(COLOR_TEXT_MUTED);
        e.setTypeface(fontMedium);
        e.setTextSize(14f);
        e.setSingleLine(true);
        e.setBackground(cardBg(COLOR_CARD, withAlpha(COLOR_ACCENT, 0x88), 10));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }

    // Shared keyboard behaviour for all input dialogs
    private void showInputDialog(String title, final EditText editText, final Runnable onOk) {
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (hasFocus) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                    else imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                }
            });
        editText.requestFocus();

        showStyledDialog(title, editText,
            "OK", new Runnable() {
                @Override
                public void run() {
                    onOk.run();
                    editText.setFocusable(false);
                }
            },
            "Cancel", new Runnable() {
                @Override
                public void run() {
                    InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                }
            });
    }

    // ================================================================
    // ColorPicker — visual color chart dialog (feature #108)
    // ================================================================
    private void ColorPicker(LinearLayout linLayout, final int featNum, final String featName, final String defaultHex) {
        final int[] PRESET_COLORS = {
            0xFF00FF88, 0xFFFF00FF, 0xFF00FFFF, 0xFFFF0000,
            0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFFFF8000,
            0xFFFF00AA, 0xFF9C27B0, 0xFFFFFFFF, 0xFF000000,
            0xFFFFD700, 0xFF00BFFF, 0xFF32CD32, 0xFFFF4500,
            0xFF00CED1, 0xFF7FFF00, 0xFFDC143C, 0xFF8A2BE2,
            0xFF20B2AA, 0xFFF0E68C, 0xFFFF69B4, 0xFFADFF2F
        };
        final String[] COLOR_NAMES = {
            "Default Green", "Magenta", "Cyan", "Red", "Green", "Blue",
            "Yellow", "Orange", "Pink", "Purple", "White", "Black",
            "Gold", "Sky Blue", "Lime", "Orange Red",
            "Turquoise", "Chartreuse", "Crimson", "Violet",
            "Sea Green", "Khaki", "Hot Pink", "Green Yellow"
        };

        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setAllCaps(false);
        button.setTypeface(fontBold);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(13), dp(10), dp(13));
        flatten(button);
        addPressAnim(button);

        int defaultColor = 0xFF00FF88;
        try { defaultColor = Color.parseColor(defaultHex); }
        catch (Exception ignored) { }
        final int defaultColorFinal = defaultColor;
        int savedColor = Preferences.readInitialColor(featName, featNum, defaultColorFinal);

        final ColorPickerHolder cpHolder = new ColorPickerHolder();
        cpHolder.featNum      = featNum;
        cpHolder.featName     = featName;
        cpHolder.button       = button;
        cpHolder.defaultColor = defaultColorFinal;
        cpHolder.currentColor = savedColor;
        mColorPickers.put(featNum, cpHolder);

        applyColorButton(button, featName, savedColor);


        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ScrollView scroll = new ScrollView(getContext);
                    LinearLayout grid = new LinearLayout(getContext);
                    grid.setOrientation(LinearLayout.VERTICAL);
                    grid.setPadding(dp(2), dp(2), dp(2), dp(2));

                    final AlertDialog[] dialogRef = new AlertDialog[1];

                    final int cols = 4;
                    for (int i = 0; i < PRESET_COLORS.length; i += cols) {
                        LinearLayout row = new LinearLayout(getContext);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

                        int added = 0;
                        for (int j = 0; j < cols && i + j < PRESET_COLORS.length; j++) {
                            final int color = PRESET_COLORS[i + j];
                            final String cname = COLOR_NAMES[i + j];

                            LinearLayout cell = new LinearLayout(getContext);
                            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f);
                            cellLp.setMargins(dp(3), dp(5), dp(3), dp(5));
                            cell.setLayoutParams(cellLp);
                            cell.setOrientation(LinearLayout.VERTICAL);
                            cell.setGravity(Gravity.CENTER);

                            View swatch = new View(getContext);
                            swatch.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
                            GradientDrawable sw = new GradientDrawable();
                            sw.setShape(GradientDrawable.OVAL);
                            sw.setColor(color);
                            sw.setStroke(dp(2), withAlpha(0xFFFFFF, 0xCC));
                            swatch.setBackground(sw);
                            addPressAnim(swatch);

                            TextView label = new TextView(getContext);
                            label.setText(cname);
                            label.setTextColor(COLOR_TEXT_MUTED);
                            label.setTypeface(fontRegular);
                            label.setTextSize(9f);
                            label.setGravity(Gravity.CENTER);
                            label.setMaxLines(1);
                            label.setEllipsize(TextUtils.TruncateAt.END);
                            LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                            lblLp.setMargins(0, dp(4), 0, 0);
                            label.setLayoutParams(lblLp);

                            cell.addView(swatch);
                            cell.addView(label);

                            swatch.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v2) {
                                        cpHolder.currentColor = color;
                                        applyColorButton(button, featName, color);
                                        Preferences.changeFeatureColor(featName, featNum, color);

                                        Toast.makeText(getContext, "Color: " + cname, Toast.LENGTH_SHORT).show();
                                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                                    }
                                });

                            row.addView(cell);
                            added++;
                        }
                        for (int k = added; k < cols; k++) {
                            View empty = new View(getContext);
                            empty.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
                            row.addView(empty);
                        }
                        grid.addView(row);
                    }

                    scroll.addView(grid);
                    dialogRef[0] = showStyledDialog("Pick ESP Color", scroll, null, null, "Cancel", null);
                }
            });

        linLayout.addView(button);
    }

    private void applyColorButton(Button button, String featName, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
        bg.setColor(color);
        bg.setStroke(dp(2), withAlpha(0xFFFFFF, 0xB0));
        button.setBackground(bg);
        int fg = isLight(color) ? Color.BLACK : Color.WHITE;
        button.setTextColor(fg);
        button.setText(Html.fromHtml("<b>" + featName + "</b>  " + hex(color)));
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
    row.setLayoutParams(rowLp(6, 3, 6, 3));
    final GradientDrawable rowBg = cardBg(COLOR_CARD, COLOR_CARD_BORDER, 6);
    row.setBackground(rowBg);

    TextView label = new TextView(getContext);
    label.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
    label.setText(featName);
    label.setTextColor(TEXT_COLOR_2);
    label.setTypeface(fontMedium);
    label.setTextSize(12f);
    label.setPadding(dp(12), dp(10), dp(6), dp(10));

    final ToggleView toggle = new ToggleView(getContext, COLOR_ACCENT, COLOR_ACCENT_3, COLOR_TRACK);
    boolean initial = Preferences.readInitialBool(featName, featNum, swiOn);
    toggle.setChecked(initial, false);
    rowBg.setStroke(dp(1), initial ? withAlpha(ToggleON, 0x99) : COLOR_CARD_BORDER);
    if (featNum == 710) {
        setTeleportPadVisible(initial);
    }

    // ★ Toggle state tracking
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
                          bool ? COLOR_CARD_BORDER : withAlpha(ToggleON, 0x99),
                          bool ? withAlpha(ToggleON, 0x99) : COLOR_CARD_BORDER);

            if (!mProgrammaticModeUpdate) {
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
    int defaultVal = (def != null) ? def.intValue() : min;
    if (defaultVal < min) defaultVal = min;
    if (defaultVal > max) defaultVal = max;

    int startVal = Preferences.readInitialInt(featName, featNum, defaultVal);
    if (startVal < min) startVal = min;
    if (startVal > max) startVal = max;

        LinearLayout card = new LinearLayout(getContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(rowLp(6, 3, 6, 3));
        card.setBackground(cardBg(COLOR_CARD, COLOR_CARD_BORDER, 6));

        LinearLayout head = new LinearLayout(getContext);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(getContext);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        label.setText(featName);
        label.setTextColor(TEXT_COLOR_2);
        label.setTypeface(fontMedium);
        label.setTextSize(12f);
        label.setPadding(dp(12), dp(10), dp(6), dp(2));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        

        final TextView chip = new TextView(getContext);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        chipLp.setMargins(0, dp(8), dp(10), 0);
        chip.setLayoutParams(chipLp);
        int displayStart = (featNum == 103) ? (startVal + 30) : startVal;
chip.setText(String.valueOf(displayStart));
        chip.setTextColor(Color.parseColor(NumberTxtColor));
        chip.setTypeface(fontBold);
        chip.setTextSize(11f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(2), dp(8), dp(2));
        chip.setBackground(cardBg(withAlpha(Color.parseColor(NumberTxtColor), 0x18), withAlpha(Color.parseColor(NumberTxtColor), 0x66), 8));

        head.addView(label);
        head.addView(chip);

                final SeekBar seekBar = new SeekBar(getContext);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        seekBar.setPadding(dp(16), dp(16), dp(16), dp(18));   // ★ thumb না কাটার জন্য বেশি padding
        
        seekBar.setMax(max);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) seekBar.setMin(min);
        SeekTrackDrawable trackDrawable = new SeekTrackDrawable(dpf(6f), COLOR_TRACK, COLOR_ACCENT, COLOR_SUCCESS);
        seekBar.setProgressDrawable(trackDrawable);

        GradientDrawable thumbGlow = new GradientDrawable();
        thumbGlow.setShape(GradientDrawable.OVAL);
        thumbGlow.setColor(withAlpha(COLOR_ACCENT, 0x44));
        thumbGlow.setSize(dp(22), dp(22));

        GradientDrawable thumbCore = new GradientDrawable();
        thumbCore.setShape(GradientDrawable.OVAL);
        thumbCore.setColor(0xFFFFFFFF);
        thumbCore.setStroke(dp(3), COLOR_ACCENT);
        thumbCore.setSize(dp(14), dp(14));

        LayerDrawable thumbLd = new LayerDrawable(new Drawable[]{thumbGlow, thumbCore});
        thumbLd.setLayerInset(1, dp(4), dp(4), dp(4), dp(4));
        seekBar.setThumb(thumbLd);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) seekBar.setSplitTrack(false);
        seekBar.setProgress(startVal);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onStartTrackingTouch(SeekBar s) { }
                @Override public void onStopTrackingTouch(SeekBar s)  { }
                @Override
                public void onProgressChanged(SeekBar s, int i, boolean z) {
                    int val = i < min ? min : i;
                    if (val != i) {
                        seekBar.setProgress(val);
                        return;
                    }
                    int disp = (featNum == 103) ? (val + 30) : val;
                    chip.setText(String.valueOf(disp));
                    if (!mProgrammaticModeUpdate) {
                        Preferences.changeFeatureInt(featName, featNum, val);
                    }
                }
            });
            
        SeekBarHolder sbHolder = new SeekBarHolder();
        sbHolder.featNum       = featNum;
        sbHolder.featName      = featName;
        sbHolder.seekBar       = seekBar;
        sbHolder.chip          = chip;
        sbHolder.trackDrawable = trackDrawable;
        sbHolder.thumbGlow     = thumbGlow;
        sbHolder.thumbCore     = thumbCore;
        sbHolder.min           = min;
        sbHolder.max           = max;
        sbHolder.defaultVal    = defaultVal;
        mSeekBars.put(featNum, sbHolder);

        card.addView(head);
        card.addView(seekBar);
        linLayout.addView(card);
    }

    private Drawable makeSeekThumb() {
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setColor(withAlpha(COLOR_ACCENT, 0x44));
        glow.setSize(dp(22), dp(22));

        GradientDrawable core = new GradientDrawable();
        core.setShape(GradientDrawable.OVAL);
        core.setColor(0xFFFFFFFF);
        core.setStroke(dp(3), COLOR_ACCENT);
        core.setSize(dp(14), dp(14));

        LayerDrawable ld = new LayerDrawable(new Drawable[]{glow, core});
        ld.setLayerInset(1, dp(4), dp(4), dp(4), dp(4));
        return ld;
    }

        private void Button(LinearLayout linLayout, final int featNum, final String featName) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setAllCaps(false);
        button.setText(Html.fromHtml(featName));
        button.setTypeface(fontBold);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));

        // ★ Special style for Close button (-6) — distinct danger color
        if (featNum == -6) {
            button.setTextColor(lighten(COLOR_DANGER, 0.45f));
            button.setBackground(cardBg(
                    withAlpha(COLOR_DANGER, 0x1F),
                    withAlpha(COLOR_DANGER, 0xCC), 8));
        } else {
            button.setTextColor(Color.WHITE);
            GradientDrawable btnBg = gradBg(BTN_GRAD_1, BTN_GRAD_2, 8);
            btnBg.setStroke(dp(1), 0x55FFFFFF);
            button.setBackground(btnBg);
        }

        flatten(button);
        addPressAnim(button);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
         
                // ★ Camera Tab Auto Open
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
private Button makeMenuColorButton(final int color, final String label,
                                    final int featNum) {
    final Button button = new Button(getContext);
    button.setMinHeight(0);
    button.setMinimumHeight(0);
    button.setLayoutParams(rowLp(6, 4, 6, 4));
    button.setAllCaps(false);
    button.setTypeface(fontBold);
    button.setTextSize(12f);
    button.setPadding(dp(10), dp(13), dp(10), dp(13));
    flatten(button);
    addPressAnim(button);

    applyMenuColorButtonStyle(button, label, color);
    if (featNum == -29) {
        mMenuColorBtn = button;
    }

    button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (featNum == -29) {
                showMenuColorPickerDialog();
            } else if (featNum == -28) {
                resetMenuColorToDefaultInPlace();
            }
        }
    });
    return button;
}

private void applyMenuColorButtonStyle(Button button, String label, int color) {
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(6));
    bg.setColor(color);
    bg.setStroke(dp(2), withAlpha(0xFFFFFF, 0xB0));
    button.setBackground(bg);
    int fg = bestTextOnColor(color);
    button.setTextColor(fg);
    button.setText(Html.fromHtml(
        "<b>" + label + "</b>  " + hex(color)));
}

    private void ButtonLink(LinearLayout linLayout, final String featName, final String url) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setAllCaps(false);
        button.setTextColor(COLOR_ACCENT);
        button.setText(Html.fromHtml(featName + " &#8599;"));
        button.setTypeface(fontBold);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        button.setBackground(cardBg(withAlpha(COLOR_ACCENT, 0x14), withAlpha(COLOR_ACCENT, 0x88), 6));
        flatten(button);
        addPressAnim(button);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
            button.setBackground(cardBg(withAlpha(ToggleON, 0x1F), withAlpha(ToggleON, 0xCC), 6));
            button.setText(Html.fromHtml(name + "  <font color='" + hex(ToggleON) + "'><b>ON</b></font>"));
        } else {
            button.setBackground(cardBg(withAlpha(ToggleOFF, 0x14), withAlpha(ToggleOFF, 0xBB), 6));
            button.setText(Html.fromHtml(name + "  <font color='" + hex(ToggleOFF) + "'><b>OFF</b></font>"));
        }
    }

    private void ButtonOnOff(LinearLayout linLayout, final int featNum, String featName, final boolean switchedOn) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTextColor(TEXT_COLOR_2);
        button.setAllCaps(false);
        button.setTypeface(fontMedium);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        flatten(button);
        addPressAnim(button);

        final String finalfeatName = featName.replace("OnOff_", "");
        final boolean isMode = (featNum == MODE_SIMPLE || featNum == MODE_MAX || featNum == MODE_ULTRA);
        final boolean isResetAll = (featNum == MODE_RESET);

        final boolean initialState = isResetAll
                ? false
                : Preferences.readInitialBool(finalfeatName, featNum, switchedOn);
        final boolean[] state = new boolean[]{ initialState };
        applyOnOffStyle(button, finalfeatName, state[0]);

        if (isMode) {
            ModeButtonHolder h = new ModeButtonHolder();
            h.featNum = featNum;
            h.name    = finalfeatName;
            h.button  = button;
            h.state   = state;
            mModeButtons.put(featNum, h);
        } else if (!isResetAll) {
            OtherButtonOnOffHolder oh = new OtherButtonOnOffHolder();
            oh.featNum    = featNum;
            oh.name       = finalfeatName;
            oh.button     = button;
            oh.state      = state;
            oh.defaultVal = switchedOn;
            mOtherButtonOnOffs.put(featNum, oh);
        }

        final Runnable resetDoneRevert = isResetAll ? new Runnable() {
            @Override
            public void run() {
                if (button.getParent() != null) {
                    applyOnOffStyle(button, finalfeatName, false);
                }
            }
        } : null;

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isResetAll) {
                    resetAllFeaturesAction(button, finalfeatName, resetDoneRevert);
                    return;
                }
                if (isMode) {
                    handleModeButtonToggle(featNum, finalfeatName, button, state);
                    return;
                }
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

    private void handleModeButtonToggle(final int featNum, final String featName,
                                        final Button button, final boolean[] state) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (mProgrammaticModeUpdate) return;
                mProgrammaticModeUpdate = true;
                Preferences.beginBatch();
                try {
                    boolean newState = !state[0];

                    // Step 1: When turning a Mode ON, turn any other active Mode OFF first
                    if (newState) {
                        for (int fn : MODE_FEATNUMS) {
                            if (fn == featNum) continue;
                            ModeButtonHolder other = mModeButtons.get(fn);
                            if (other != null && other.state[0]) {
                                other.state[0] = false;
                                applyOnOffStyle(other.button, other.name, false);
                                Preferences.forceSyncBool(other.name, other.featNum, false, true);
                                applyModeToMenuInternal(fn, false);
                            }
                        }
                    }

                    // Step 2: Update the clicked Mode button (UI + Store/Disk + Native)
                    state[0] = newState;
                    applyOnOffStyle(button, featName, newState);
                    Preferences.forceSyncBool(featName, featNum, newState, true);

                    // Step 3: Apply this Mode's full preset (leaves non-mode manual choices alone)
                    applyModeToMenuInternal(featNum, newState);
                } finally {
                    Preferences.endBatch(true);
                    mProgrammaticModeUpdate = false;
                }
            }
        });
    }

    private void resetAllFeaturesAction(final Button resetButton, final String resetName,
                                        final Runnable revertRunnable) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (mProgrammaticModeUpdate) return;
                mProgrammaticModeUpdate = true;
                Preferences.beginBatch();
                try {
                    // 1. Turn all Mode buttons OFF (UI + Store/Disk; native 803 resets all game globals)
                    for (int fn : MODE_FEATNUMS) {
                        ModeButtonHolder mh = mModeButtons.get(fn);
                        if (mh != null) {
                            if (mh.state[0]) {
                                mh.state[0] = false;
                                applyOnOffStyle(mh.button, mh.name, false);
                            }
                            Preferences.forceSyncBool(mh.name, mh.featNum, false, false);
                        } else {
                            Preferences.forceSyncBool("", fn, false, false);
                        }
                    }

                    // 2. Reset any non-mode ButtonOnOff controls
                    for (OtherButtonOnOffHolder oh : mOtherButtonOnOffs.values()) {
                        boolean def = oh.defaultVal;
                        if (oh.state[0] != def) {
                            oh.state[0] = def;
                            applyOnOffStyle(oh.button, oh.name, def);
                        }
                        Preferences.forceSyncBool(oh.name, oh.featNum, def, true);
                    }

                    // 3. Reset all Toggles (NEVER touch featNum -1 "Save features preference")
                    for (ToggleHolder th : mToggles.values()) {
                        if (th.featNum == -1) continue;
                        setToggleState(th.featNum, th.defaultVal, true);
                    }

                    // 4. Reset all SeekBars to default
                    for (SeekBarHolder sb : mSeekBars.values()) {
                        setSeekBarValue(sb.featNum, sb.defaultVal, true);
                    }

                    // 5. Reset all Spinners to index 0 (includes Camera Mode, Shader Target, Color Preset)
                    for (SpinnerHolder sp : mSpinners.values()) {
                        setSpinnerValue(sp.featNum, 0, true);
                    }

                    // 6. Reset all CheckBoxes to default
                    for (CheckBoxHolder cb : mCheckBoxes.values()) {
                        setCheckBoxState(cb.featNum, cb.defaultVal, true);
                    }

                    // 7. Reset all RadioGroups to 0 (unselected)
                    for (RadioGroupHolder rg : mRadioGroups.values()) {
                        rg.radioGroup.clearCheck();
                        rg.selectedName = null;
                        rg.titleView.setText(rg.featName + ":");
                        Preferences.forceSyncInt(rg.featName, rg.featNum, 0, true);
                    }

                    // 8. Reset all ColorPickers to defaultColor
                    for (ColorPickerHolder cp : mColorPickers.values()) {
                        setColorPickerValue(cp.featNum, cp.defaultColor, true);
                    }

                    // 9. Reset all InputNum / InputLNum / InputText controls
                    for (InputHolder ih : mInputNums.values()) {
                        ih.currentValueText = "0";
                        ih.button.setText(Html.fromHtml(ih.featName + ": <font color='" + NumberTxtColor + "'>0</font>"));
                        Preferences.forceSyncInt(ih.featName, ih.featNum, 0, true);
                    }
                    for (InputHolder ih : mInputLNums.values()) {
                        ih.currentValueText = "0";
                        ih.button.setText(Html.fromHtml(ih.featName + ": <font color='" + NumberTxtColor + "'>0</font>"));
                        Preferences.forceSyncLong(ih.featName, ih.featNum, 0L, true);
                    }
                    for (InputHolder ih : mInputTexts.values()) {
                        ih.currentValueText = "";
                        ih.button.setText(Html.fromHtml(ih.featName + ": <font color='" + NumberTxtColor + "'></font>"));
                        Preferences.forceSyncString(ih.featName, ih.featNum, "", true);
                    }

                    // 10. Trigger native Reset All (803) and keep 803 itself unlatched (OFF)
                    Preferences.triggerResetAllButton(resetName, MODE_RESET);
                } finally {
                    Preferences.endBatch(true);
                    mProgrammaticModeUpdate = false;
                }

                // Momentary "Done" flash, then return to OFF
                if (revertRunnable != null) {
                    resetButton.removeCallbacks(revertRunnable);
                    resetButton.setBackground(cardBg(withAlpha(ToggleON, 0x1F), withAlpha(ToggleON, 0xCC), 6));
                    resetButton.setText(Html.fromHtml(resetName + "  <font color='" + hex(ToggleON) + "'><b>Done</b></font>"));
                    resetButton.postDelayed(revertRunnable, 900);
                } else {
                    applyOnOffStyle(resetButton, resetName, false);
                }
            }
        });
    }
    
    // ================================================================
    // Telegram button — Sky blue + Telegram paper-plane icon
    // ================================================================
    private void ButtonTelegram(LinearLayout linLayout, final String label, final String url) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(fontBold);
        button.setTextSize(12f);
        button.setPadding(dp(12), dp(11), dp(12), dp(11));
        button.setText(label);
        button.setGravity(Gravity.CENTER);

        // Telegram blue background (#229ED9)
        int TG_BLUE = Color.parseColor("#229ED9");
        int TG_BLUE_DEEP = Color.parseColor("#1A7EB0");
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{TG_BLUE, TG_BLUE_DEEP});
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), 0x55FFFFFF);
        button.setBackground(bg);

        // Telegram paper-plane icon (left of text)
        float density = safeDisplayMetrics(getContext).density;
        TelegramIcon icon = new TelegramIcon(Color.WHITE, density);
        button.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(8));

        flatten(button);
        addPressAnim(button);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
        setToggleState(featNum, newState, false);
    }

    private void setToggleState(int featNum, boolean newState, boolean forceSync) {
        ToggleHolder h = mToggles.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncBool("", featNum, newState, true);
            }
            return;
        }
        if (h.state[0] != newState) {
            h.state[0] = newState;
            h.toggle.setChecked(newState, true);
            animateStroke(h.rowBg,
                          newState ? COLOR_CARD_BORDER : withAlpha(ToggleON, 0x99),
                          newState ? withAlpha(ToggleON, 0x99) : COLOR_CARD_BORDER);
            if (featNum == 710) setTeleportPadVisible(newState);
        }
        if (forceSync) {
            Preferences.forceSyncBool(h.featName, featNum, newState, true);
        } else {
            Preferences.changeFeatureBool(h.featName, featNum, newState);
        }
    }

    private void setCheckBoxState(int featNum, boolean newState, boolean forceSync) {
        CheckBoxHolder h = mCheckBoxes.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncBool("", featNum, newState, true);
            }
            return;
        }
        if (h.checkBox.isChecked() != newState) {
            h.checkBox.setChecked(newState);
            animateStroke(h.bg,
                          newState ? COLOR_CARD_BORDER : withAlpha(CheckBoxColor, 0x99),
                          newState ? withAlpha(CheckBoxColor, 0x99) : COLOR_CARD_BORDER);
        }
        if (forceSync) {
            Preferences.forceSyncBool(h.featName, featNum, newState, true);
        } else {
            Preferences.changeFeatureBool(h.featName, featNum, newState);
        }
    }

    private void setSeekBarValue(int featNum, int value) {
        setSeekBarValue(featNum, value, false);
    }

    private void setSeekBarValue(int featNum, int value, boolean forceSync) {
        SeekBarHolder h = mSeekBars.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncInt("", featNum, value, true);
            }
            return;
        }
        int clamped = value;
        if (clamped < h.min) clamped = h.min;
        if (clamped > h.max) clamped = h.max;
        if (h.seekBar.getProgress() != clamped) {
            h.seekBar.setProgress(clamped);
            int disp = (featNum == 103) ? (clamped + 30) : clamped;
            h.chip.setText(String.valueOf(disp));
        }
        if (forceSync) {
            Preferences.forceSyncInt(h.featName, featNum, clamped, true);
        } else {
            Preferences.changeFeatureInt(h.featName, featNum, clamped);
        }
    }

    private void setSpinnerValue(int featNum, int index) {
        setSpinnerValue(featNum, index, false);
    }

    private void setSpinnerValue(int featNum, int index, boolean forceSync) {
        SpinnerHolder h = mSpinners.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncInt("", featNum, index, true);
            }
            return;
        }
        if (index < 0 || index >= h.items.length) return;

        if (h.currentIdx != index) {
            h.currentIdx = index;
            h.nameView.setText(h.featName + ": " + h.items[index]);
            for (int j = 0; j < h.dropdownList.getChildCount(); j++) {
                View child = h.dropdownList.getChildAt(j);
                if (child instanceof TextView) {
                    TextView t = (TextView) child;
                    if (j == index) {
                        t.setTextColor(COLOR_ACCENT);
                        t.setBackground(cardBg(withAlpha(COLOR_ACCENT, 0x1A), 0, 4));
                    } else {
                        t.setTextColor(TEXT_COLOR_2);
                        t.setBackground(null);
                    }
                }
            }
        }
        if (forceSync) {
            Preferences.forceSyncInt(h.featName, featNum, index, true);
        } else {
            Preferences.changeFeatureInt(h.featName, featNum, index);
        }
    }

    private void setColorPickerValue(int featNum, int color, boolean forceSync) {
        ColorPickerHolder h = mColorPickers.get(featNum);
        if (h == null) {
            if (forceSync) {
                Preferences.forceSyncColor("", featNum, color, true);
            }
            return;
        }
        if (h.currentColor != color) {
            h.currentColor = color;
            applyColorButton(h.button, h.featName, color);
        }
        if (forceSync) {
            Preferences.forceSyncColor(h.featName, featNum, color, true);
        } else {
            Preferences.changeFeatureColor(h.featName, featNum, color);
        }
    }

    private void applyModeToMenu(final int modeFeatNum, final boolean enabled) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (mProgrammaticModeUpdate) return;
                mProgrammaticModeUpdate = true;
                Preferences.beginBatch();
                try {
                    applyModeToMenuInternal(modeFeatNum, enabled);
                } finally {
                    Preferences.endBatch(true);
                    mProgrammaticModeUpdate = false;
                }
            }
        });
    }

    private void applyModeToMenuInternal(int modeFeatNum, boolean enabled) {
        PresetItem[] items = MODE_PRESETS.get(modeFeatNum);
        if (items == null) return;
        for (int i = 0; i < items.length; i++) {
            PresetItem item = items[i];
            int targetVal = enabled ? item.onValue : item.offValue;
            switch (item.type) {
                case PRESET_TOGGLE:
                    setToggleState(item.featNum, targetVal != 0, true);
                    break;
                case PRESET_SEEKBAR:
                    setSeekBarValue(item.featNum, targetVal, true);
                    break;
                case PRESET_SPINNER:
                    setSpinnerValue(item.featNum, targetVal, true);
                    break;
                case PRESET_COLOR:
                    setColorPickerValue(item.featNum, targetVal, true);
                    break;
            }
        }
    }
         // ================================================================
    // Perfect Inline Dropdown (No PopupWindow, No Bugs)
    // ================================================================
            private void Spinner(LinearLayout linLayout, final int featNum, final String featName, final String list) {
    final Context ctx = getContext;
    final List<String> lists = new LinkedList<String>(Arrays.asList(list.split(",")));

    // ১. মেইন কন্টেইনার (যা spinner row এবং dropdown list দুটোই ধরে রাখবে)
    final LinearLayout container = new LinearLayout(ctx);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setLayoutParams(rowLp(6, 3, 6, 3));

    // ২. Spinner Row (যেটি সবসময় দেখা যাবে)
    final LinearLayout spinnerRow = new LinearLayout(ctx);
    spinnerRow.setBackground(cardBg(COLOR_CARD, COLOR_CARD_BORDER, 6));
    spinnerRow.setGravity(Gravity.CENTER_VERTICAL);
    spinnerRow.setPadding(dp(12), dp(11), dp(12), dp(11));
    spinnerRow.setOrientation(LinearLayout.HORIZONTAL);

    final TextView tvName = new TextView(ctx);
    tvName.setTextColor(TEXT_COLOR_2);
    tvName.setTypeface(fontMedium);
    tvName.setTextSize(12f);
    tvName.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

    final ImageView arrowIv = new ImageView(ctx);
    TabIcon chevIcon = new TabIcon(TabIcon.CHEVRON);
    chevIcon.setColor(COLOR_ACCENT);
    arrowIv.setImageDrawable(chevIcon);
    arrowIv.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));

    spinnerRow.addView(tvName);
    spinnerRow.addView(arrowIv);

    final int savedIdx = Preferences.readInitialInt(featName, featNum, 0);
    final int initIdx = (savedIdx >= 0 && savedIdx < lists.size()) ? savedIdx : 0;
    tvName.setText(featName + ": " + lists.get(initIdx));

    // ৩. Dropdown List (যা ক্লিক করলে ওপেন হবে)
    final LinearLayout dropdownList = new LinearLayout(ctx);
    dropdownList.setOrientation(LinearLayout.VERTICAL);
    dropdownList.setVisibility(View.GONE);
    dropdownList.setBackground(cardBg(COLOR_BG_TOP, withAlpha(COLOR_ACCENT, 0xAA), 8));
    dropdownList.setPadding(dp(4), dp(4), dp(4), dp(4));
    
    LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    listLp.setMargins(0, dp(4), 0, 0);
    dropdownList.setLayoutParams(listLp);

    for (int i = 0; i < lists.size(); i++) {
        final int index = i;
        TextView tv = new TextView(ctx);
        tv.setText(lists.get(i));
        tv.setTextColor(TEXT_COLOR_2);
        tv.setTypeface(fontMedium);
        tv.setTextSize(12f);
        tv.setPadding(dp(12), dp(12), dp(12), dp(12));
        
        if (index == initIdx) {
            tv.setTextColor(COLOR_ACCENT);
            tv.setBackground(cardBg(withAlpha(COLOR_ACCENT, 0x1A), 0, 4));
        }

        tv.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        setSpinnerValue(featNum, index);   // ★ highlight + nameView + prefs — সব এক জায়গায়
        dropdownList.setVisibility(View.GONE);
        arrowIv.animate().rotation(0f).setDuration(200).start();
    }
});
        dropdownList.addView(tv);
    }

    // ৪. Spinner Row-তে ক্লিক করলে Toggle হবে
    spinnerRow.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (dropdownList.getVisibility() == View.VISIBLE) {
                dropdownList.setVisibility(View.GONE);
                arrowIv.animate().rotation(0f).setDuration(200).start();
            } else {
                dropdownList.setVisibility(View.VISIBLE);
                arrowIv.animate().rotation(180f).setDuration(200).start();
            }
        }
    });
    
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
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        int num = Preferences.readInitialInt(featName, featNum, 0);
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
        button.setAllCaps(false);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTypeface(fontMedium);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        button.setBackground(cardBg(COLOR_CARD, withAlpha(COLOR_ACCENT, 0x66), 6));
        button.setTextColor(TEXT_COLOR_2);
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
                    final EditText editText = makeEditText();
                    if (maxValue != 0) editText.setHint("Max: " + maxValue);
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setKeyListener(DigitsKeyListener.getInstance("0123456789-"));
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
                    showInputDialog("Input number", editText, new Runnable() {
                            @Override
                            public void run() {
                                int num;
                                try {
                                    String inp = editText.getText().toString();
                                    num = Integer.parseInt(inp.isEmpty() ? "0" : inp);
                                    if (maxValue != 0 && num >= maxValue) num = maxValue;
                                } catch (NumberFormatException ex) { num = maxValue != 0 ? maxValue : Integer.MAX_VALUE; }
                                inHolder.currentValueText = String.valueOf(num);
                                button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
                                Preferences.changeFeatureInt(featName, featNum, num);
                            }
                        });
                }
            });
        linLayout.addView(button);
    }

    private void InputLNum(LinearLayout linLayout, final int featNum, final String featName, final long maxValue) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        long num = Preferences.readInitialLong(featName, featNum, 0L);
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
        button.setAllCaps(false);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTypeface(fontMedium);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        button.setBackground(cardBg(COLOR_CARD, withAlpha(COLOR_ACCENT, 0x66), 6));
        button.setTextColor(TEXT_COLOR_2);
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
                    final EditText editText = makeEditText();
                    if (maxValue != 0) editText.setHint("Max: " + maxValue);
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setKeyListener(DigitsKeyListener.getInstance("0123456789-"));
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
                    showInputDialog("Input number", editText, new Runnable() {
                            @Override
                            public void run() {
                                long num;
                                try {
                                    String inp = editText.getText().toString();
                                    num = Long.parseLong(inp.isEmpty() ? "0" : inp);
                                    if (maxValue != 0 && num >= maxValue) num = maxValue;
                                } catch (NumberFormatException ex) { num = maxValue != 0 ? maxValue : Long.MAX_VALUE; }
                                ilHolder.currentValueText = String.valueOf(num);
                                button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
                                Preferences.changeFeatureLong(featName, featNum, num);
                            }
                        });
                }
            });
        linLayout.addView(button);
    }

    private void InputText(LinearLayout linLayout, final int featNum, final String featName) {
        final Button button = new Button(getContext);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        String string = Preferences.readInitialString(featName, featNum, "");
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + string + "</font>"));
        button.setAllCaps(false);
        button.setLayoutParams(rowLp(6, 4, 6, 4));
        button.setTypeface(fontMedium);
        button.setTextSize(12f);
        button.setPadding(dp(10), dp(11), dp(10), dp(11));
        button.setBackground(cardBg(COLOR_CARD, withAlpha(COLOR_ACCENT, 0x66), 6));
        button.setTextColor(TEXT_COLOR_2);
        flatten(button);
        addPressAnim(button);
        final InputHolder itHolder = new InputHolder();
        itHolder.featNum          = featNum;
        itHolder.featName         = featName;
        itHolder.button           = button;
        itHolder.currentValueText = (string != null) ? string : "";
        mInputTexts.put(featNum, itHolder);
        button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final EditText editText = makeEditText();
                    showInputDialog("Input text", editText, new Runnable() {
                            @Override
                            public void run() {
                                String str = editText.getText().toString();
                                itHolder.currentValueText = str;
                                button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + str + "</font>"));
                                Preferences.changeFeatureString(featName, featNum, str);
                            }
                        });
                }
            });
        linLayout.addView(button);
    }

    private void CheckBox(LinearLayout linLayout, final int featNum, final String featName, boolean switchedOn) {
        final CheckBox checkBox = new CheckBox(getContext);
        final GradientDrawable cbBg = cardBg(COLOR_CARD, COLOR_CARD_BORDER, 6);
        checkBox.setLayoutParams(rowLp(6, 3, 6, 3));
        checkBox.setBackground(cbBg);
        checkBox.setText(featName);
        checkBox.setTextColor(TEXT_COLOR_2);
        checkBox.setTypeface(fontMedium);
        checkBox.setTextSize(12f);
        checkBox.setPadding(dp(10), dp(9), dp(10), dp(9));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) checkBox.setButtonTintList(ColorStateList.valueOf(CheckBoxColor));
        boolean initial = Preferences.readInitialBool(featName, featNum, switchedOn);
        checkBox.setChecked(initial);

        if (initial) cbBg.setStroke(dp(1), withAlpha(CheckBoxColor, 0x99));
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton bv, boolean isChecked) {
                    animateStroke(cbBg,
                                  isChecked ? COLOR_CARD_BORDER : withAlpha(CheckBoxColor, 0x99),
                                  isChecked ? withAlpha(CheckBoxColor, 0x99) : COLOR_CARD_BORDER);
                    if (!mProgrammaticModeUpdate) {
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
        textView.setTextColor(TEXT_COLOR_2);
        textView.setTypeface(fontBold);
        textView.setTextSize(12f);
        textView.setPadding(0, dp(2), 0, dp(4));
        final RadioGroup radioGroup = new RadioGroup(getContext);
        radioGroup.setLayoutParams(rowLp(6, 3, 6, 3));
        radioGroup.setBackground(cardBg(COLOR_CARD, COLOR_CARD_BORDER, 6));
        radioGroup.setPadding(dp(12), dp(8), dp(12), dp(8));
        radioGroup.setOrientation(LinearLayout.VERTICAL);
        radioGroup.addView(textView);
        final RadioGroupHolder rgHolder = new RadioGroupHolder();
        rgHolder.featNum    = featNum;
        rgHolder.featName   = featName;
        rgHolder.radioGroup = radioGroup;
        rgHolder.titleView  = textView;
        for (int i = 0; i < lists.size(); i++) {
            final RadioButton Radioo = new RadioButton(getContext);
            final String finalfeatName = featName, radioName = lists.get(i);
            View.OnClickListener rl = new View.OnClickListener() {
                public void onClick(View v) {
                    rgHolder.selectedName = radioName;
                    textView.setText(Html.fromHtml(finalfeatName + ": <font color='" + NumberTxtColor + "'>" + radioName));
                    Preferences.changeFeatureInt(finalfeatName, featNum, radioGroup.indexOfChild(Radioo));
                }
            };
            Radioo.setText(lists.get(i));
            Radioo.setTextColor(Color.parseColor("#C6C8D0"));
            Radioo.setTypeface(fontRegular);
            Radioo.setTextSize(12f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) Radioo.setButtonTintList(ColorStateList.valueOf(RadioColor));
            Radioo.setOnClickListener(rl);
            radioGroup.addView(Radioo);
        }
        int index = Preferences.readInitialInt(featName, featNum, 0);
        if (index > 0 && index <= lists.size() && index < radioGroup.getChildCount()) {
            rgHolder.selectedName = lists.get(index - 1);
            textView.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + lists.get(index - 1)));
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

        // header row: title + rotating chevron
        LinearLayout headRow = new LinearLayout(getContext);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable colBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                                      new int[]{CollapseColor, withAlpha(COLOR_ACCENT_2, 0x30)});
        colBg.setCornerRadius(dp(6));
        colBg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0x77));
        headRow.setBackground(colBg);

        final TextView textView = new TextView(getContext);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        textView.setText(text);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextColor(COLOR_ACCENT);
        textView.setTypeface(fontBold);
        textView.setTextSize(12f);
        textView.setPadding(dp(12), dp(12), dp(6), dp(12));

        final ImageView chevron = new ImageView(getContext);
        TabIcon chevIcon = new TabIcon(TabIcon.CHEVRON);
        chevIcon.setColor(COLOR_ACCENT);
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
        TextView textView = new TextView(getContext);
        GradientDrawable catBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                                                      new int[]{withAlpha(COLOR_ACCENT, 0x00), withAlpha(COLOR_ACCENT, 0x2A), withAlpha(COLOR_ACCENT_2, 0x00)});
        textView.setBackground(catBg);
        textView.setText(Html.fromHtml(text));
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(COLOR_ACCENT);
        textView.setTypeface(fontBold);
        textView.setTextSize(12f);
        textView.setPadding(0, dp(7), 0, dp(7));
        mCategoryViews.add(textView);
        linLayout.addView(textView);
    }

    
    
    private void TextView(LinearLayout linLayout, String text) {
    // এখানে featNum হিসেবে -1 পাস করা হলো, কারণ এটি সাধারণ টেক্সট
    TextView(linLayout, -1, text, false);
}

private void TextView(LinearLayout linLayout, int featNum, String text, boolean small) {
    TextView textView = new TextView(getContext);
    textView.setText(Html.fromHtml(text));
    textView.setTextColor(Color.parseColor("#C6C8D0"));
    textView.setTypeface(fontRegular);
    textView.setTextSize(small ? 10f : 12f);
    textView.setPadding(dp(12), dp(5), dp(12), dp(5));

    // ★ Camera Tab Auto Open on clicking "Click here"
    if (featNum == 993) {
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTabByName("Camera");
            }
        });
    }
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
 * Live preview — palette থেকে drag করার সময় সাথে সাথে in-place apply,
 * কিন্তু rebuild বা disk write হয় না।
 */
private void applyMenuColorLive(int baseColor) {
    int safeColor = ensureReadableAccent(baseColor);
    COLOR_ACCENT   = safeColor;
    COLOR_ACCENT_2 = darken(safeColor, 0.65f);
    COLOR_ACCENT_3 = lighten(safeColor, 0.35f);
    TEXT_COLOR = safeColor;
    BTN_COLOR  = safeColor;
    BORDER_COLOR = safeColor;
    SeekBarColor = safeColor;
    SeekBarProgressColor = safeColor;
    CheckBoxColor = safeColor;
    RadioColor    = safeColor;
    NumberTxtColor = String.format("#%06X", (0xFFFFFF & safeColor));

    // Header & chrome live update
    updateHeaderColors();

    // Resize handle grip
    if (resizeHandle != null) {
        resizeHandle.setBackground(makeGripDrawable(withAlpha(COLOR_ACCENT, 0xE0), dpf(1.7f)));
    }

    // Sidebar tab colors update
    for (TabHolder h : tabHolders) styleTab(h, isTabSelected(h));

    // Update all content controls in-place (zero rebuild, zero scroll jump, zero native call)
    updateThemedControlsInPlace();
}

private void updateThemedControlsInPlace() {
    if (mMenuColorBtn != null) {
        applyMenuColorButtonStyle(mMenuColorBtn, "Menu Color", COLOR_ACCENT);
    }
    for (ToggleHolder th : mToggles.values()) {
        if (th.toggle != null) {
            th.toggle.setColors(COLOR_ACCENT, COLOR_ACCENT_3, COLOR_TRACK);
        }
    }
    for (SeekBarHolder sb : mSeekBars.values()) {
        if (sb.trackDrawable != null) {
            sb.trackDrawable.setColors(COLOR_ACCENT, COLOR_SUCCESS);
        }
        if (sb.thumbGlow != null) {
            sb.thumbGlow.setColor(withAlpha(COLOR_ACCENT, 0x44));
        }
        if (sb.thumbCore != null) {
            sb.thumbCore.setStroke(dp(3), COLOR_ACCENT);
        }
        if (sb.seekBar != null) {
            sb.seekBar.invalidate();
        }
        if (sb.chip != null) {
            sb.chip.setTextColor(COLOR_ACCENT);
            sb.chip.setBackground(cardBg(withAlpha(COLOR_ACCENT, 0x18), withAlpha(COLOR_ACCENT, 0x66), 8));
        }
    }
    for (SpinnerHolder sp : mSpinners.values()) {
        if (sp.chevIcon != null) {
            sp.chevIcon.setColor(COLOR_ACCENT);
        }
        if (sp.dropdownList != null) {
            sp.dropdownList.setBackground(cardBg(COLOR_BG_TOP, withAlpha(COLOR_ACCENT, 0xAA), 8));
            for (int j = 0; j < sp.dropdownList.getChildCount(); j++) {
                View child = sp.dropdownList.getChildAt(j);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    if (j == sp.currentIdx) {
                        tv.setTextColor(COLOR_ACCENT);
                        tv.setBackground(cardBg(withAlpha(COLOR_ACCENT, 0x1A), 0, 4));
                    } else {
                        tv.setTextColor(TEXT_COLOR_2);
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
                cb.bg.setStroke(dp(1), withAlpha(CheckBoxColor, 0x99));
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
            rg.titleView.setText(Html.fromHtml(rg.featName + ": <font color='" + NumberTxtColor + "'>" + rg.selectedName));
        }
    }
    updateInputHoldersTheme(mInputNums);
    updateInputHoldersTheme(mInputLNums);
    updateInputHoldersTheme(mInputTexts);

    for (TextView catTv : mCategoryViews) {
        if (catTv != null) {
            GradientDrawable catBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{withAlpha(COLOR_ACCENT, 0x00), withAlpha(COLOR_ACCENT, 0x2A), withAlpha(COLOR_ACCENT_2, 0x00)});
            catTv.setBackground(catBg);
            catTv.setTextColor(COLOR_ACCENT);
        }
    }
    for (CollapseHolder ch : mCollapseHolders) {
        if (ch.headRow != null) {
            GradientDrawable colBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{CollapseColor, withAlpha(COLOR_ACCENT_2, 0x30)});
            colBg.setCornerRadius(dp(6));
            colBg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0x77));
            ch.headRow.setBackground(colBg);
        }
        if (ch.titleView != null) {
            ch.titleView.setTextColor(COLOR_ACCENT);
        }
        if (ch.chevIcon != null) {
            ch.chevIcon.setColor(COLOR_ACCENT);
        }
    }
    for (Button lb : mLinkButtons) {
        if (lb != null) {
            lb.setTextColor(COLOR_ACCENT);
            lb.setBackground(cardBg(withAlpha(COLOR_ACCENT, 0x14), withAlpha(COLOR_ACCENT, 0x88), 6));
        }
    }
    if (mMenuTeleportPad != null) {
        mMenuTeleportPad.setAccentColor(COLOR_ACCENT);
    }
}

private void updateInputHoldersTheme(HashMap<Integer, InputHolder> map) {
    for (InputHolder ih : map.values()) {
        if (ih.button != null) {
            ih.button.setBackground(cardBg(COLOR_CARD, withAlpha(COLOR_ACCENT, 0x66), 6));
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
    Toast.makeText(getContext,
            Preferences.isSaveEnabled() ? "Menu color reset & saved" : "Menu color reset",
            Toast.LENGTH_SHORT).show();
}

/**
 * Commit — Apply ট্যাপ করলে theme in-place apply ও (Save ON থাকলে) persist হয়।
 * কোনো rebuild, tab jump, scroll reset বা native call হয় না।
 */
private void applyMenuColor(final int baseColor) {
    int safeAccent = ensureReadableAccent(baseColor);
    mCurrentHueF = getHueFromColor(safeAccent);
    applyMenuColorLive(safeAccent);
    Preferences.recordMenuColorUsed(safeAccent);
    Preferences.setMenuAccentColor(safeAccent);

    Toast.makeText(getContext,
            Preferences.isSaveEnabled() ? "Menu color saved" : "Menu color applied",
            Toast.LENGTH_SHORT).show();
}

private boolean isTabSelected(TabHolder h) {
    // কোনো tab selected কিনা চেক — TabHolder এ tag দিয়ে track করা হয়
    for (TabHolder t : tabHolders) {
        LinearLayout catView = categoryViewsMap.get(t.name);
        if (catView != null && catView.getParent() == contentLayout) {
            return t == h;
        }
    }
    return false;
}

/**
 * Header (ModX Lab / PRO / gear) colors update।
 */
private void updateHeaderColors() {
    // ---- Title (ModX Lab) ----
    if (mTitleView != null) {
        mTitleView.setTextColor(COLOR_ACCENT);
    }
    if (mSubTitleView != null) {
    mSubTitleView.setTextColor(Color.WHITE);
}

    // ---- PRO badge ----
    if (mProView != null) {
    mProView.setBackground(cardBg(
        withAlpha(COLOR_ACCENT, 0x1A),
        withAlpha(COLOR_ACCENT, 0x99), 8));
    mProView.setTextColor(Color.WHITE);   // ← white text
}

    // ---- Settings gear icon ----
    if (mSettingsIcon != null) {
        TabIcon icon = new TabIcon(TabIcon.GEAR);
        icon.setColor(COLOR_ACCENT);
        mSettingsIcon.setImageDrawable(icon);
        GradientDrawable gearBg = new GradientDrawable();
        gearBg.setShape(GradientDrawable.OVAL);
        gearBg.setColor(withAlpha(COLOR_ACCENT, 0x14));
        gearBg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0x55));
        mSettingsIcon.setBackground(gearBg);
    }

    // ---- MINIMIZE button ----
    if (mMinimizeBtn != null) {
        mMinimizeBtn.setBackground(cardBg(
            withAlpha(COLOR_ACCENT, 0x1A),
            withAlpha(COLOR_ACCENT, 0x77), 6));
        mMinimizeBtn.setTextColor(COLOR_ACCENT);
    }

    // ---- HIDE/KILL button (bright red, always) ----
    if (mHideBtn != null) {
        int DANGER_BRIGHT = Color.parseColor("#FF3B30");
        mHideBtn.setBackground(cardBg(
                withAlpha(DANGER_BRIGHT, 0x1F),
                withAlpha(DANGER_BRIGHT, 0x99), 6));
        mHideBtn.setTextColor(DANGER_BRIGHT);
    }

    // ---- Shimmer line (update colors in-place, zero view churn) ----
    if (mShimmerViewRef instanceof ShimmerLine) {
        ((ShimmerLine) mShimmerViewRef).setColors(COLOR_ACCENT, COLOR_ACCENT_2);
    }

    // ---- Dividers ----
    if (sidebarDivider != null) {
        sidebarDivider.setBackgroundColor(withAlpha(COLOR_ACCENT, 0x24));
    }
    if (mBottomDivider != null) {
        GradientDrawable line = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{withAlpha(COLOR_ACCENT, 0x00), withAlpha(COLOR_ACCENT, 0x70), withAlpha(COLOR_ACCENT_2, 0x70), withAlpha(COLOR_ACCENT_2, 0x00)});
        mBottomDivider.setBackground(line);
    }
        // ---- Scrollbar colors (both sidebar & content) ----
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
    private int previewColor = 0xFF3DDB87;
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
        borderPaint.setColor(0x66000000);

        centerPaint.setStyle(Paint.Style.FILL);
        centerRingPaint.setStyle(Paint.Style.STROKE);
        centerRingPaint.setStrokeWidth(1.5f * density);
        centerRingPaint.setColor(0x55FFFFFF);

        markerFillPaint.setStyle(Paint.Style.FILL);
        markerWhitePaint.setStyle(Paint.Style.STROKE);
        markerWhitePaint.setStrokeWidth(2.6f * density);
        markerWhitePaint.setColor(0xFFFFFFFF);

        markerDarkPaint.setStyle(Paint.Style.STROKE);
        markerDarkPaint.setStrokeWidth(1.4f * density);
        markerDarkPaint.setColor(0xCC000000);
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
        this.cornerR = 10f * density;
        this.pad = 10f * density;
        this.hue = hue;
        this.sat = clamp01(sat);
        this.val = clamp01(val);
        setHapticFeedbackEnabled(true);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.2f * density);
        borderPaint.setColor(0x55FFFFFF);

        markerFillPaint.setStyle(Paint.Style.FILL);
        markerWhitePaint.setStyle(Paint.Style.STROKE);
        markerWhitePaint.setStrokeWidth(2.5f * density);
        markerWhitePaint.setColor(0xFFFFFFFF);

        markerDarkPaint.setStyle(Paint.Style.STROKE);
        markerDarkPaint.setStrokeWidth(1.4f * density);
        markerDarkPaint.setColor(0xCC000000);
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
    0xFF3DDB87, // ModX Green (Default)
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
    gd.setStroke(dp(1), withAlpha(0xFFFFFF, 0x55));
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

    final GradientDrawable boxBg = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM});
    boxBg.setCornerRadius(dp(16));
    boxBg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0xAA));
    box.setBackground(boxBg);

    // ---- 1. Top Header Bar (Title + Reset button) ----
    LinearLayout headerRow = new LinearLayout(getContext);
    headerRow.setOrientation(LinearLayout.HORIZONTAL);
    headerRow.setGravity(Gravity.CENTER_VERTICAL);
    headerRow.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

    final TextView title = new TextView(getContext);
    title.setText("Menu Color Palette");
    title.setTextColor(COLOR_ACCENT);
    title.setTypeface(fontBold);
    title.setTextSize(15f);
    title.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

    Button resetBtn = new Button(getContext);
    resetBtn.setText("Reset");
    resetBtn.setAllCaps(false);
    resetBtn.setTypeface(fontMedium);
    resetBtn.setTextSize(11f);
    resetBtn.setTextColor(TEXT_COLOR_2);
    resetBtn.setMinHeight(dp(34));
    resetBtn.setMinimumHeight(dp(34));
    resetBtn.setMinWidth(0);
    resetBtn.setMinimumWidth(0);
    resetBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
    resetBtn.setBackground(cardBg(COLOR_CARD, COLOR_CARD_BORDER, 8));
    flatten(resetBtn);
    addPressAnim(resetBtn);

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
    previewBg.setCornerRadius(dp(10));
    previewBg.setColor(rawPickedHolder[0]);
    previewBg.setStroke(dp(1), withAlpha(0xFFFFFF, 0x66));
    previewBanner.setBackground(previewBg);

    LinearLayout previewTextCol = new LinearLayout(getContext);
    previewTextCol.setOrientation(LinearLayout.VERTICAL);
    previewTextCol.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

    final TextView previewHexTv = new TextView(getContext);
    previewHexTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    previewHexTv.setTextSize(15f);
    previewHexTv.setText(hex(rawPickedHolder[0]));
    previewHexTv.setTextColor(bestTextOnColor(rawPickedHolder[0]));

    final TextView previewSubTv = new TextView(getContext);
    previewSubTv.setTypeface(fontRegular);
    previewSubTv.setTextSize(10f);
    previewSubTv.setText("Live Preview");
    previewSubTv.setTextColor(withAlpha(bestTextOnColor(rawPickedHolder[0]), 0xCC));

    previewTextCol.addView(previewHexTv);
    previewTextCol.addView(previewSubTv);

    // Original color compare chip (tap to jump back to original without closing)
    LinearLayout origChip = new LinearLayout(getContext);
    origChip.setOrientation(LinearLayout.HORIZONTAL);
    origChip.setGravity(Gravity.CENTER_VERTICAL);
    origChip.setPadding(dp(8), dp(5), dp(8), dp(5));
    origChip.setBackground(cardBg(0x66000000, 0x55FFFFFF, 8));
    origChip.setClickable(true);
    origChip.setFocusable(true);

    View origDot = new View(getContext);
    origDot.setLayoutParams(new LinearLayout.LayoutParams(dp(14), dp(14)));
    GradientDrawable origDotBg = new GradientDrawable();
    origDotBg.setShape(GradientDrawable.OVAL);
    origDotBg.setColor(mColorPickerOriginalColor);
    origDotBg.setStroke(dp(1), Color.WHITE);
    origDot.setBackground(origDotBg);

    TextView origLbl = new TextView(getContext);
    origLbl.setText("Orig");
    origLbl.setTextColor(Color.WHITE);
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
    hexLabel.setTextColor(COLOR_TEXT_MUTED);
    hexLabel.setTypeface(fontBold);
    hexLabel.setTextSize(11f);
    hexLabel.setPadding(0, 0, dp(10), 0);

    final EditText hexInput = new EditText(getContext);
    hexInput.setTextColor(Color.WHITE);
    hexInput.setHintTextColor(COLOR_TEXT_MUTED);
    hexInput.setHint("#3DDB87");
    hexInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    hexInput.setTextSize(13f);
    hexInput.setSingleLine(true);
    hexInput.setMinHeight(dp(42));
    hexInput.setPadding(dp(12), dp(8), dp(12), dp(8));
    hexInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
    hexInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
    hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
    hexInput.setHighlightColor(withAlpha(COLOR_ACCENT, 0x55));
    final GradientDrawable hexInputBg = cardBg(COLOR_CARD, withAlpha(COLOR_ACCENT, 0x66), 8);
    hexInput.setBackground(hexInputBg);
    hexInput.setText(hex(rawPickedHolder[0]));
    hexInput.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));

    final TextView hexErrorTv = new TextView(getContext);
    hexErrorTv.setTextColor(lighten(COLOR_DANGER, 0.25f));
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
            int onPreview = bestTextOnColor(raw);

            previewBg.setColor(raw);
            previewHexTv.setText(hex(raw));
            previewHexTv.setTextColor(onPreview);
            if (safe != raw) {
                previewSubTv.setText("Adjusted for readability: " + hex(safe));
            } else {
                previewSubTv.setText("Live Preview");
            }
            previewSubTv.setTextColor(withAlpha(onPreview, 0xCC));

            boxBg.setStroke(dp(1), withAlpha(safe, 0xAA));
            title.setTextColor(safe);

            GradientDrawable applyBg = new GradientDrawable();
            applyBg.setCornerRadius(dp(8));
            applyBg.setColor(safe);
            okBtn.setBackground(applyBg);
            okBtn.setTextColor(bestTextOnColor(safe));

            if (!hexInput.hasFocus()) {
                updatingHex[0] = true;
                hexInput.setText(hex(raw));
                hexInput.setSelection(hexInput.getText().length());
                updatingHex[0] = false;
                hexErrorTv.setVisibility(View.GONE);
                hexInputBg.setStroke(dp(1), withAlpha(safe, 0x66));
            }

            for (int i = 0; i < allSwatchRings.size(); i++) {
                SwatchRingHolder h = allSwatchRings.get(i);
                boolean match = (h.color == raw || h.color == safe);
                h.bg.setStroke(dp(match ? 2 : 1), match ? Color.WHITE : withAlpha(0xFFFFFF, 0x55));
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
                    hexErrorTv.setText("Enter 6 hex digits (e.g. #3DDB87)");
                    hexErrorTv.setVisibility(View.VISIBLE);
                    hexInputBg.setStroke(dp(1), COLOR_DANGER);
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
                hexInputBg.setStroke(dp(1), withAlpha(COLOR_ACCENT, 0x88));
                rawPickedHolder[0] = parsed;
                Color.colorToHSV(parsed, curHsv);
                wheel.setState(curHsv[0], parsed);
                svSquare.setHSV(curHsv[0], curHsv[1], curHsv[2]);
                syncUiRunnable[0].run();
            } else {
                hexErrorTv.setText("Enter 6 hex digits (e.g. #3DDB87)");
                hexErrorTv.setVisibility(View.VISIBLE);
                hexInputBg.setStroke(dp(1), COLOR_DANGER);
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

    // Reset button resets picker to default green #3DDB87 (still Cancelable!)
    resetBtn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
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
    presetsLbl.setTextColor(COLOR_TEXT_MUTED);
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
        recentLbl.setTextColor(COLOR_TEXT_MUTED);
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
            if (hexInput.hasFocus()) {
                Integer typed = parseHexColorStrict(hexInput.getText().toString());
                if (typed == null) {
                    hexErrorTv.setText("Enter 6 hex digits (e.g. #3DDB87)");
                    hexErrorTv.setVisibility(View.VISIBLE);
                    hexInputBg.setStroke(dp(1), COLOR_DANGER);
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
        bgPaint.setColor(0x88121A1E);           // dark semi-transparent

        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(2));
        framePaint.setColor(0xFF3DDB87);

        crossPaint.setStyle(Paint.Style.STROKE);
        crossPaint.setStrokeWidth(dpf(1f));
        crossPaint.setColor(0x803DDB87);

        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(0xFF3DDB87);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dpf(2f));
        glowPaint.setColor(0x663DDB87);

        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(0xFFFFFFFF);

        labelPaint.setColor(0x998D8F99);
        labelPaint.setTextSize(dp(9));
        labelPaint.setAntiAlias(true);
        labelPaint.setFakeBoldText(true);

        coordPaint.setColor(0xFFFFFFFF);
        coordPaint.setTextSize(dp(10));
        coordPaint.setTextAlign(Paint.Align.CENTER);
        coordPaint.setAntiAlias(true);
        coordPaint.setFakeBoldText(true);

        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(0x703DDB87);

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
        canvas.drawRoundRect(bg, dp(10), dp(10), bgPaint);

        // Drag bar at top (visual only)
        RectF handle = new RectF(dp(50), dp(3), w - dp(50), dp(8));
        canvas.drawRoundRect(handle, dp(3), dp(3), handlePaint);

        // Content area
        float l = contentLeft(),  t = contentTop();
        float r = contentRight(), b = contentBottom();
        RectF content = new RectF(l, t, r, b);

        // Frame
        canvas.drawRoundRect(content, dp(6), dp(6), framePaint);

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

    try { Menu.SetTeleportTargetNorm(nx, ny); }
    catch (Throwable ignored) { }
}
}
private void TeleportPad(LinearLayout linLayout) {
    // Helper text
    TextView hint = new TextView(getContext);
    hint.setText("Tap inside → teleport to safe spot\nDrag top bar to move floating pad");
    hint.setTextColor(COLOR_TEXT_MUTED);
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
        bgPaint.setColor(0xE6121A1E);

        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dpf(1.5f));         // ★ 2dp → 1.5dp
framePaint.setColor(0xCC3DDB87);

        crossPaint.setStyle(Paint.Style.STROKE);
        crossPaint.setStrokeWidth(dpf(1f));
        crossPaint.setColor(0x603DDB87);

        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(COLOR_ACCENT);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(2));
        glowPaint.setColor(0x553DDB87);

        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setColor(0xFFFFFFFF);

        labelPaint.setColor(COLOR_TEXT_MUTED);
        labelPaint.setTextSize(dp(9));
        labelPaint.setAntiAlias(true);
        labelPaint.setFakeBoldText(true);

        setClickable(true);
    }

    void setAccentColor(int c) {
        markerPaint.setColor(c);
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

        canvas.drawRoundRect(new RectF(0, 0, w, h), dp(8), dp(8), bgPaint);
        canvas.drawRoundRect(rect, dp(6), dp(6), framePaint);

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

    try { Menu.SetTeleportTargetNorm(nx, ny); }
    catch (Throwable ignored) { }
}
}
}