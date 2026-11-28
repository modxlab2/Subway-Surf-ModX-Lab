package com.android.support;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;

/**
 * Centralized ModX Lab website dark design system tokens & drawable factories.
 * Zero R.* / res/ / assets/ dependency — safe for DEX + .so injection.
 */
public final class ModxTheme {

    // =========================================================================
    // Website Dark Palette Tokens (modxlab.vercel.app theme.css)
    // =========================================================================
    public static final int PAGE            = 0xFF131312; // #131312
    public static final int SURFACE         = 0xFF0E0E0D; // #0E0E0D (header/footer/sidebar band)
    public static final int CARD            = 0xFF222220; // #222220
    public static final int CARD_HOVER      = 0xFF292927; // #292927
    public static final int RAISED          = 0xFF2F2F2D; // #2F2F2D
    public static final int INPUT           = 0xFF1A1A19; // #1A1A19

    public static final int LINE            = 0xFF31302E; // #31302E
    public static final int LINE_STRONG     = 0xFF464541; // #464541
    public static final int LINE_HOVER      = 0xFF5A5955; // #5A5955
    public static final int HAIRLINE        = 0x17DEDCD1; // rgba(222,220,209,0.09)
    public static final int INNER_HIGHLIGHT = 0x0AFFFFFF; // rgba(255,255,255,0.04)

    public static final int FG              = 0xFFFAF9F5; // #FAF9F5
    public static final int MUTED           = 0xFFBAB8AE; // #BAB8AE
    public static final int SUBTLE          = 0xFF93918A; // #93918A

    public static final int ACCENT          = 0xFF20C997; // #20C997 (Emerald brand accent)
    public static final int ACCENT_HOVER    = 0xFF34D8A8; // #34D8A8
    public static final int ACCENT_PRESS    = 0xFF1AAE82; // #1AAE82
    public static final int ON_ACCENT       = 0xFF04231B; // #04231B
    public static final int ACCENT_SOFT     = 0x1F20C997; // rgba(32,201,151,0.12)
    public static final int ACCENT_LINE     = 0x5220C997; // rgba(32,201,151,0.32)

    // Type tokens
    public static final int TYPE_GAME       = 0xFF20C997;
    public static final int TYPE_RESOURCE   = 0xFF38BDF8;
    public static final int TYPE_TOOL       = 0xFFA78BFA;

    // Status tokens
    public static final int SUCCESS         = 0xFF22C55E; // #22C55E
    public static final int WARNING         = 0xFFFBBF24; // #FBBF24
    public static final int DANGER          = 0xFFF87171; // #F87171
    public static final int INFO            = 0xFF38BDF8; // #38BDF8

    // Brand tokens
    public static final int TELEGRAM        = 0xFF229ED9; // #229ED9
    public static final int TELEGRAM_DEEP   = 0xFF1A7EB0; // #1A7EB0

    // System FloatingToolbar fallback tokens (used only when device theme attr is absent)
    public static final int TOOLBAR_DARK_BG         = 0xFF2D2E30;
    public static final int TOOLBAR_LIGHT_BG        = 0xFFFFFFFF;
    public static final int TOOLBAR_DARK_FG         = 0xFFF1F3F4;
    public static final int TOOLBAR_LIGHT_FG        = 0xFF1F1F1F;
    public static final int TOOLBAR_DARK_HIGHLIGHT  = 0x33FFFFFF;
    public static final int TOOLBAR_LIGHT_HIGHLIGHT = 0x1F000000;
    public static final int TOOLBAR_RIPPLE_MASK     = 0xFFFFFFFF;

    // Geometry
    public static final float RADIUS_DP     = 6f;

    // Color picker preset palettes (centralized here; zero hardcoded colors in UI classes)
    public static final int[] MENU_ACCENT_PRESETS = {
        ACCENT,      // ModX Emerald
        0xFF22D3EE,  // Cyan
        0xFF38BDF8,  // Sky Blue
        0xFF60A5FA,  // Azure
        0xFF818CF8,  // Indigo
        0xFFA855F7,  // Electric Purple
        0xFFEC4899,  // Neon Pink
        0xFFF43F5E,  // Crimson Rose
        0xFFFB923C,  // Amber Orange
        0xFFFACC15,  // Gold
        0xFFA3E635,  // Lime
        0xFFE2E8F0   // Silver Frost
    };

    public static final int[] ESP_COLOR_PRESETS = {
        ACCENT,     0xFFFF00FF, 0xFF00FFFF, 0xFFFF4D4F,
        0xFF22C55E, 0xFF38BDF8, 0xFFFACC15, 0xFFFB923C,
        0xFFEC4899, 0xFFA855F7, FG,         PAGE,
        0xFFFFD700, 0xFF60A5FA, 0xFFA3E635, 0xFFFF6B35,
        0xFF22D3EE, 0xFF4ADE80, 0xFFF43F5E, 0xFF818CF8,
        0xFF2DD4BF, 0xFFFDE047, 0xFFF472B6, 0xFFE2E8F0
    };

    // Typography
    public static final Typeface FONT_REGULAR  = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface FONT_MEDIUM   = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface FONT_SEMIBOLD = Typeface.create("sans-serif", Typeface.BOLD);

    private ModxTheme() { }

    // =========================================================================
    // Color Math & Accessibility Contrast Helpers
    // =========================================================================
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int withAlphaFraction(int color, float fraction) {
        int a = Math.max(0, Math.min(255, Math.round(fraction * 255f)));
        return withAlpha(color, a);
    }

    public static int accentSoft(int accent) {
        return withAlpha(accent, 0x1F); // ~12%
    }

    public static int accentLine(int accent) {
        return withAlpha(accent, 0x52); // ~32%
    }

    public static int accentFocusRing(int accent) {
        return withAlpha(accent, 0x26); // ~15%
    }

    public static int badgeBg(int statusColor) {
        return withAlpha(statusColor, 0x26); // 15%
    }

    public static int badgeBorder(int statusColor) {
        return withAlpha(statusColor, 0x66); // 40%
    }

    public static int accentHover(int accent) {
        return blend(accent, 0xFFFFFFFF, 0.18f);
    }

    public static int accentPress(int accent) {
        if ((accent & 0x00FFFFFF) == (ACCENT & 0x00FFFFFF)) {
            return ACCENT_PRESS;
        }
        return blend(accent, 0xFF000000, 0.14f);
    }

    public static int blend(int c1, int c2, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int r = Math.round(Color.red(c1) + (Color.red(c2) - Color.red(c1)) * clamped);
        int g = Math.round(Color.green(c1) + (Color.green(c2) - Color.green(c1)) * clamped);
        int b = Math.round(Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * clamped);
        return Color.argb(255, clamp255(r), clamp255(g), clamp255(b));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static double linearizeChannel(double c) {
        return (c <= 0.04045) ? (c / 12.92) : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double relativeLuminance(int color) {
        double r = linearizeChannel(Color.red(color) / 255.0);
        double g = linearizeChannel(Color.green(color) / 255.0);
        double b = linearizeChannel(Color.blue(color) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    public static double contrastRatio(int c1, int c2) {
        double l1 = relativeLuminance(c1);
        double l2 = relativeLuminance(c2);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    public static int onAccent(int bgColor) {
        return (contrastRatio(bgColor, ON_ACCENT) >= contrastRatio(bgColor, FG))
                ? ON_ACCENT
                : FG;
    }

    public static boolean isLight(int color) {
        return onAccent(color) == ON_ACCENT;
    }

    public static int ensureReadableAccent(int rawColor) {
        int opaque = 0xFF000000 | rawColor;
        double minContrast = 3.5;
        if (contrastRatio(opaque, CARD) >= minContrast) {
            return opaque;
        }
        float[] hsv = new float[3];
        Color.colorToHSV(opaque, hsv);

        float[] test = new float[]{ hsv[0], hsv[1], 1.0f };
        int fullV = 0xFF000000 | Color.HSVToColor(test);
        if (contrastRatio(fullV, CARD) >= minContrast) {
            float lo = hsv[2];
            float hi = 1.0f;
            for (int i = 0; i < 18; i++) {
                float mid = (lo + hi) * 0.5f;
                test[2] = mid;
                int c = 0xFF000000 | Color.HSVToColor(test);
                if (contrastRatio(c, CARD) >= minContrast) {
                    hi = mid;
                } else {
                    lo = mid;
                }
            }
            test[2] = hi;
            return 0xFF000000 | Color.HSVToColor(test);
        }

        test[2] = 1.0f;
        float lo = 0f;
        float hi = hsv[1];
        for (int i = 0; i < 18; i++) {
            float mid = (lo + hi) * 0.5f;
            test[1] = mid;
            int c = 0xFF000000 | Color.HSVToColor(test);
            if (contrastRatio(c, CARD) >= minContrast) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        test[1] = lo;
        return 0xFF000000 | Color.HSVToColor(test);
    }

    public static String hex6(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    // =========================================================================
    // Dimension & Haptic Helpers
    // =========================================================================
    public static DisplayMetrics metrics(Context ctx) {
        DisplayMetrics dm = new DisplayMetrics();
        try {
            if (ctx != null) {
                WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null && wm.getDefaultDisplay() != null) {
                    wm.getDefaultDisplay().getMetrics(dm);
                    if (dm.widthPixels > 0 && dm.heightPixels > 0 && dm.density > 0f) {
                        return dm;
                    }
                }
            }
        } catch (Throwable ignored) { }
        return android.content.res.Resources.getSystem().getDisplayMetrics();
    }

    public static int dp(Context ctx, float valueDp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, valueDp, metrics(ctx)));
    }

    public static float dpf(Context ctx, float valueDp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, valueDp, metrics(ctx));
    }

    public static void hapticTap(View v) {
        if (v == null) return;
        try {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Throwable ignored) { }
    }

    public static void hapticTick(View v) {
        if (v == null) return;
        try {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        } catch (Throwable ignored) { }
    }

    // =========================================================================
    // Drawable Factories (6dp radius, subtle 1dp highlight, crisp borders)
    // =========================================================================
    public static GradientDrawable roundRect(Context ctx, int fillColor, int strokeColor,
                                             float strokeDp, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fillColor);
        g.setCornerRadius(dpf(ctx, radiusDp));
        if (strokeDp > 0f && Color.alpha(strokeColor) > 0) {
            g.setStroke(Math.max(1, dp(ctx, strokeDp)), strokeColor);
        }
        return g;
    }

    /**
     * Standard card surface with 1dp neutral border + subtle top highlight (rgba 255,255,255,0.04).
     */
    public static Drawable cardSurface(Context ctx, int strokeColor) {
        float r = dpf(ctx, RADIUS_DP);
        int borderPx = Math.max(1, dp(ctx, 1f));

        GradientDrawable base = new GradientDrawable();
        base.setColor(CARD);
        base.setCornerRadius(r);
        base.setStroke(borderPx, strokeColor);

        GradientDrawable topHighlight = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{INNER_HIGHLIGHT, 0x00FFFFFF});
        topHighlight.setCornerRadius(r);

        LayerDrawable ld = new LayerDrawable(new Drawable[]{base, topHighlight});
        ld.setLayerInset(1, borderPx, borderPx, borderPx, borderPx);
        return ld;
    }

    /**
     * Interactive ripple wrapper on API 21+ with pressed fallback on older builds.
     */
    public static Drawable withInteractiveRipple(Context ctx, Drawable content,
                                                 int rippleColor, float radiusDp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE);
            mask.setCornerRadius(dpf(ctx, radiusDp));
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
        }
        return content;
    }

    public static Drawable primaryButtonBg(Context ctx, int accent) {
        float r = dpf(ctx, RADIUS_DP);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(accent);
        normal.setCornerRadius(r);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(accentPress(accent));
        pressed.setCornerRadius(r);

        GradientDrawable disabled = new GradientDrawable();
        disabled.setColor(RAISED);
        disabled.setStroke(Math.max(1, dp(ctx, 1f)), LINE);
        disabled.setCornerRadius(r);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], normal);

        return withInteractiveRipple(ctx, states, withAlpha(onAccent(accent), 0x24), RADIUS_DP);
    }

    public static Drawable loginPrimaryButtonBg(Context ctx, int accent) {
        float r = dpf(ctx, RADIUS_DP);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(accent);
        normal.setCornerRadius(r);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(accentPress(accent));
        pressed.setCornerRadius(r);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], normal);

        return withInteractiveRipple(ctx, states, withAlpha(onAccent(accent), 0x1A), RADIUS_DP);
    }

    public static Drawable secondaryButtonBg(Context ctx) {
        float r = dpf(ctx, RADIUS_DP);
        int b = Math.max(1, dp(ctx, 1f));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(RAISED);
        normal.setStroke(b, LINE_STRONG);
        normal.setCornerRadius(r);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(CARD_HOVER);
        pressed.setStroke(b, LINE_HOVER);
        pressed.setCornerRadius(r);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], normal);

        return withInteractiveRipple(ctx, states, 0x1AFFFFFF, RADIUS_DP);
    }

    public static Drawable ghostButtonBg(Context ctx) {
        float r = dpf(ctx, RADIUS_DP);
        int b = Math.max(1, dp(ctx, 1f));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);
        normal.setStroke(b, LINE_STRONG);
        normal.setCornerRadius(r);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(CARD);
        pressed.setStroke(b, LINE_HOVER);
        pressed.setCornerRadius(r);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], normal);

        return withInteractiveRipple(ctx, states, withAlpha(FG, 0x16), RADIUS_DP);
    }

    public static Drawable telegramButtonBg(Context ctx) {
        float r = dpf(ctx, RADIUS_DP);
        int b = Math.max(1, dp(ctx, 1f));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(TELEGRAM);
        normal.setStroke(b, TELEGRAM_DEEP);
        normal.setCornerRadius(r);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(TELEGRAM_DEEP);
        pressed.setStroke(b, TELEGRAM_DEEP);
        pressed.setCornerRadius(r);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], normal);

        return withInteractiveRipple(ctx, states, withAlpha(FG, 0x26), RADIUS_DP);
    }

    public static Drawable ghostIconBg(Context ctx, int accent) {
        float r = dpf(ctx, RADIUS_DP);
        int b = Math.max(1, dp(ctx, 1f));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(CARD);
        normal.setStroke(b, LINE);
        normal.setCornerRadius(r);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(CARD_HOVER);
        pressed.setStroke(b, LINE_HOVER);
        pressed.setCornerRadius(r);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], normal);

        return withInteractiveRipple(ctx, states, withAlpha(accent, 0x24), RADIUS_DP);
    }

    public static Drawable floatingIconBg(Context ctx, int accent) {
        float r = dpf(ctx, RADIUS_DP);
        int b = Math.max(1, dp(ctx, 1f));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(withAlphaFraction(SURFACE, 0.85f));
        normal.setStroke(b, LINE);
        normal.setCornerRadius(r);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(withAlphaFraction(CARD_HOVER, 0.92f));
        pressed.setStroke(b, LINE_HOVER);
        pressed.setCornerRadius(r);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], normal);

        return withInteractiveRipple(ctx, states, withAlpha(accent, 0x24), RADIUS_DP);
    }

    public static Drawable fieldBoxBg(Context ctx, int accent, boolean focused) {
        return roundRect(
                ctx,
                INPUT,
                focused ? accent : LINE_STRONG,
                focused ? 1.5f : 1f,
                RADIUS_DP);
    }

    public static Drawable dialogCardBg(Context ctx) {
        return cardSurface(ctx, LINE);
    }

    public static Drawable pillBadgeBg(Context ctx, int bgColor, int borderColor) {
        return roundRect(ctx, bgColor, borderColor, 1f, RADIUS_DP);
    }

    public static Drawable transparentBg() {
        return new ColorDrawable(Color.TRANSPARENT);
    }

    // =========================================================================
    // Unified Responsive Scale Model & Explicit Size-Class Token Tables
    // =========================================================================
    public enum SizeClass {
        REGULAR, // >= 240dp wide
        COMPACT, // 190dp .. < 240dp wide
        MINI     // < 190dp wide
    }

    public static final class MenuScaleTokens {
        public final SizeClass sizeClass;
        public final float scale;

        // Header
        public final int headerMinHeightPx;
        public final int headerPadHPx;
        public final int headerPadVPx;
        public final float titleSizeSp;
        public final int titleDotPx;
        public final boolean subtitleVisible;
        public final float subtitleSizeSp;
        public final int gearBoxPx;
        public final int gearIconPx;

        // Sidebar tabs
        public final boolean sidebarIconOnly;
        public final int sidebarWidthPx;
        public final int tabMinHeightPx;
        public final int tabPadHPx;
        public final int tabPadVPx;
        public final int tabIconPx;
        public final float tabFontSp;
        public final int tabIndicatorWpx;
        public final int tabIndicatorHpx;

        // Mode cards
        public final int modeCardMinHeightPx;
        public final int modeCardPadHPx;
        public final int modeCardPadVPx;
        public final int modeCheckSizePx;
        public final int modeCheckIconPx;
        public final float modeTitleMaxSp;
        public final float modeTitleMinSp;
        public final float modeDescSp;
        public final boolean modeAllowActiveTag;

        // Footer
        public final boolean footerIconOnly;
        public final int footerPadStartPx;
        public final int footerPadVPx;
        public final int footerPadEndPx;
        public final int footerBtnHeightPx;
        public final float footerBtnFontSp;
        public final int footerBtnIconPx;
        public final int footerGapPx;

        // Resize grip & snackbar
        public final int gripTouchPx;
        public final float snackbarFontSp;
        public final int snackbarPadHPx;
        public final int snackbarPadVPx;
        public final int snackbarBottomMarginPx;

        private MenuScaleTokens(Context ctx, SizeClass sizeClass, float s, float widthDp) {
            this.sizeClass = sizeClass;
            this.scale = s;

            int hairPx = Math.max(1, dp(ctx, 1f));
            int innerWpx = Math.max(1, dp(ctx, widthDp) - hairPx * 2);

            if (sizeClass == SizeClass.MINI) {
                this.headerMinHeightPx = dp(ctx, 40f);
                this.headerPadHPx = dp(ctx, 8f);
                this.headerPadVPx = dp(ctx, 4f);
                this.titleSizeSp = 13.5f;
                this.titleDotPx = dp(ctx, 5f);
                this.subtitleVisible = false;
                this.subtitleSizeSp = 0f;
                this.gearBoxPx = dp(ctx, 36f);
                this.gearIconPx = dp(ctx, 16f);

                this.sidebarIconOnly = true;
                this.sidebarWidthPx = dp(ctx, 44f);
                this.tabMinHeightPx = dp(ctx, 36f);
                this.tabPadHPx = dp(ctx, 4f);
                this.tabPadVPx = dp(ctx, 6f);
                this.tabIconPx = dp(ctx, 16f);
                this.tabFontSp = 10f;
                this.tabIndicatorWpx = dp(ctx, 2.5f);
                this.tabIndicatorHpx = dp(ctx, 16f);

                this.modeCardMinHeightPx = dp(ctx, 44f);
                this.modeCardPadHPx = dp(ctx, 8f);
                this.modeCardPadVPx = dp(ctx, 8f);
                this.modeCheckSizePx = dp(ctx, 16f);
                this.modeCheckIconPx = dp(ctx, 10f);
                this.modeTitleMaxSp = 12f;
                this.modeTitleMinSp = 10f;
                this.modeDescSp = 10f;
                this.modeAllowActiveTag = false;

                this.gripTouchPx = dp(ctx, 20f);
                this.footerIconOnly = true;
                this.footerPadStartPx = dp(ctx, 6f);
                this.footerPadVPx = dp(ctx, 5f);
                this.footerPadEndPx = this.footerPadStartPx;
                this.footerBtnHeightPx = dp(ctx, 36f);
                this.footerBtnFontSp = 0f;
                this.footerBtnIconPx = dp(ctx, 16f);
                this.footerGapPx = dp(ctx, 6f);

                this.snackbarFontSp = 10f;
                this.snackbarPadHPx = dp(ctx, 10f);
                this.snackbarPadVPx = dp(ctx, 6f);
                this.snackbarBottomMarginPx = this.footerBtnHeightPx + this.footerPadVPx * 2 + dp(ctx, 8f);
            } else if (sizeClass == SizeClass.COMPACT) {
                this.headerMinHeightPx = Math.max(dp(ctx, 42f), Math.round(dp(ctx, 46f) * s));
                this.headerPadHPx = dp(ctx, Math.max(8f, 10f * s));
                this.headerPadVPx = dp(ctx, Math.max(5f, 7f * s));
                this.titleSizeSp = clamp(16f * s, 13.5f, 16f);
                this.titleDotPx = dp(ctx, clamp(5.5f * s, 5f, 6f));
                this.subtitleVisible = true;
                this.subtitleSizeSp = clamp(10f * s, 8.5f, 10f);
                this.gearBoxPx = dp(ctx, 36f);
                this.gearIconPx = dp(ctx, 17f);

                boolean iconOnly = widthDp < 210f;
                this.sidebarIconOnly = iconOnly;
                this.sidebarWidthPx = iconOnly
                        ? dp(ctx, 46f)
                        : Math.max(dp(ctx, 74f), Math.min(dp(ctx, 90f), Math.round(innerWpx * 0.35f)));
                this.tabMinHeightPx = dp(ctx, 38f);
                this.tabPadHPx = dp(ctx, iconOnly ? 4f : 7f);
                this.tabPadVPx = dp(ctx, 7f);
                this.tabIconPx = dp(ctx, 16f);
                this.tabFontSp = clamp(11.5f * s, 10f, 11.5f);
                this.tabIndicatorWpx = dp(ctx, 2.5f);
                this.tabIndicatorHpx = dp(ctx, 16f);

                this.modeCardMinHeightPx = dp(ctx, 48f);
                this.modeCardPadHPx = dp(ctx, 10f);
                this.modeCardPadVPx = dp(ctx, 10f);
                this.modeCheckSizePx = dp(ctx, 18f);
                this.modeCheckIconPx = dp(ctx, 12f);
                this.modeTitleMaxSp = clamp(13.5f * s, 11.5f, 13.5f);
                this.modeTitleMinSp = 10.5f;
                this.modeDescSp = clamp(11.5f * s, 10f, 11.5f);
                this.modeAllowActiveTag = true;

                this.gripTouchPx = dp(ctx, 22f);
                this.footerIconOnly = false;
                this.footerPadStartPx = dp(ctx, 7f);
                this.footerPadVPx = dp(ctx, 6f);
                this.footerPadEndPx = this.footerPadStartPx;
                this.footerBtnHeightPx = dp(ctx, 36f);
                this.footerBtnFontSp = clamp(11f * s, 9.5f, 11f);
                this.footerBtnIconPx = dp(ctx, 13f);
                this.footerGapPx = dp(ctx, 6f);

                this.snackbarFontSp = clamp(11f * s, 10f, 11f);
                this.snackbarPadHPx = dp(ctx, 10f);
                this.snackbarPadVPx = dp(ctx, 6f);
                this.snackbarBottomMarginPx = this.footerBtnHeightPx + this.footerPadVPx * 2 + dp(ctx, 8f);
            } else {
                this.headerMinHeightPx = Math.max(dp(ctx, 46f), Math.round(dp(ctx, 54f) * s));
                this.headerPadHPx = dp(ctx, clamp(12f * s, 10f, 16f));
                this.headerPadVPx = dp(ctx, clamp(9f * s, 7f, 12f));
                this.titleSizeSp = clamp(17f * s, 14f, 19.5f);
                this.titleDotPx = dp(ctx, clamp(6f * s, 5f, 7.5f));
                this.subtitleVisible = true;
                this.subtitleSizeSp = clamp(10f * s, 9f, 11.5f);
                this.gearBoxPx = Math.max(dp(ctx, 38f), Math.round(dp(ctx, 40f) * s));
                this.gearIconPx = Math.max(dp(ctx, 16f), Math.round(dp(ctx, 19f) * s));

                this.sidebarIconOnly = false;
                this.sidebarWidthPx = Math.max(dp(ctx, 86f), Math.min(dp(ctx, 136f), Math.round(innerWpx * 0.36f)));
                this.tabMinHeightPx = Math.max(dp(ctx, 40f), Math.round(dp(ctx, 42f) * s));
                this.tabPadHPx = dp(ctx, clamp(9f * s, 7f, 12f));
                this.tabPadVPx = dp(ctx, clamp(8f * s, 6f, 11f));
                this.tabIconPx = Math.max(dp(ctx, 15f), Math.round(dp(ctx, 18f) * s));
                this.tabFontSp = clamp(12f * s, 10.5f, 13.5f);
                this.tabIndicatorWpx = dp(ctx, 3f);
                this.tabIndicatorHpx = Math.max(dp(ctx, 16f), Math.round(dp(ctx, 18f) * s));

                this.modeCardMinHeightPx = Math.max(dp(ctx, 52f), Math.round(dp(ctx, 56f) * s));
                this.modeCardPadHPx = dp(ctx, clamp(12f * s, 10f, 14f));
                this.modeCardPadVPx = dp(ctx, clamp(12f * s, 9f, 14f));
                this.modeCheckSizePx = Math.max(dp(ctx, 18f), Math.round(dp(ctx, 20f) * s));
                this.modeCheckIconPx = Math.max(dp(ctx, 11f), Math.round(dp(ctx, 13f) * s));
                this.modeTitleMaxSp = clamp(14f * s, 12f, 15f);
                this.modeTitleMinSp = 11f;
                this.modeDescSp = clamp(12f * s, 10.5f, 13f);
                this.modeAllowActiveTag = true;

                this.gripTouchPx = dp(ctx, 22f);
                this.footerIconOnly = false;
                this.footerPadStartPx = dp(ctx, clamp(8f * s, 6f, 12f));
                this.footerPadVPx = dp(ctx, clamp(7f * s, 5f, 10f));
                this.footerPadEndPx = this.footerPadStartPx;
                this.footerBtnHeightPx = Math.max(dp(ctx, 36f), Math.round(dp(ctx, 38f) * s));
                this.footerBtnFontSp = clamp(11f * s, 9.5f, 12.5f);
                this.footerBtnIconPx = Math.max(dp(ctx, 13f), Math.round(dp(ctx, 14f) * s));
                this.footerGapPx = dp(ctx, clamp(6f * s, 4f, 8f));

                this.snackbarFontSp = clamp(11f * s, 10f, 12.5f);
                this.snackbarPadHPx = dp(ctx, clamp(12f * s, 10f, 16f));
                this.snackbarPadVPx = dp(ctx, clamp(7f * s, 6f, 9f));
                this.snackbarBottomMarginPx = this.footerBtnHeightPx + this.footerPadVPx * 2 + dp(ctx, 10f);
            }
        }
    }

    public static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    public static SizeClass resolveSizeClass(float widthDp) {
        if (widthDp < 190f) return SizeClass.MINI;
        if (widthDp < 240f) return SizeClass.COMPACT;
        return SizeClass.REGULAR;
    }

    public static MenuScaleTokens resolveMenuTokens(Context ctx, int widthPx, int heightPx,
                                                      float designWidthDp, float designHeightDp) {
        DisplayMetrics dm = metrics(ctx);
        float dens = dm.density > 0f ? dm.density : 1f;
        float wDp = (widthPx > 0) ? (widthPx / dens) : designWidthDp;
        float hDp = (heightPx > 0) ? (heightPx / dens) : designHeightDp;
        float rawS = Math.min(wDp / Math.max(1f, designWidthDp), hDp / Math.max(1f, designHeightDp));
        float s = clamp(rawS, 0.62f, 1.15f);
        return new MenuScaleTokens(ctx, resolveSizeClass(wDp), s, wDp);
    }
}
