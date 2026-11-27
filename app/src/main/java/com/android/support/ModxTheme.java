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

    public static Drawable dangerOutlineButtonBg(Context ctx, boolean armed) {
        float r = dpf(ctx, RADIUS_DP);
        int b = Math.max(1, dp(ctx, 1f));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(armed ? badgeBg(DANGER) : withAlpha(DANGER, 0x14));
        normal.setStroke(b, armed ? withAlpha(DANGER, 0xAA) : badgeBorder(DANGER));
        normal.setCornerRadius(r);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(badgeBg(DANGER));
        pressed.setStroke(b, DANGER);
        pressed.setCornerRadius(r);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[0], normal);

        return withInteractiveRipple(ctx, states, withAlpha(DANGER, 0x26), RADIUS_DP);
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
}
