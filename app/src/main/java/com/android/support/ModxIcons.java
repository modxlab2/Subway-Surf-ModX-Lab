package com.android.support;

import android.content.Context;
import android.graphics.drawable.Drawable;

/**
 * Crisp 24x24 SVG vector icons backed by SvgIcon (zero R.* / res/ / assets/).
 * Line icons use Lucide (ISC) paths with 1.75 stroke weight on a 24x24 grid;
 * paste uses Material Symbols content_paste (Apache-2.0).
 */
public final class ModxIcons {

    public static final float STROKE = 1.75f;

    private ModxIcons() { }

    private static SvgIcon icon(int color, int sizePx) {
        return new SvgIcon(24f, color, STROKE).setIntrinsicSizePx(sizePx);
    }

    public static SvgIcon home(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M3 9 L12 2 L21 9 V20 A2 2 0 0 1 19 22 H5 A2 2 0 0 1 3 20 Z")
                .addPath("M9 22 V12 H15 V22");
    }

    public static SvgIcon shoppingBag(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M6 2 L3 6 V20 A2 2 0 0 0 5 22 H19 A2 2 0 0 0 21 20 V6 L18 2 Z")
                .addPath("M3 6 H21")
                .addPath("M16 10 A4 4 0 0 1 8 10");
    }

    public static SvgIcon shieldCheck(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M12 22 S20 18 20 12 V5 L12 2 L4 5 V12 C4 18 12 22 12 22 Z")
                .addPath("M9 12 L11 14 L15 10");
    }

    public static SvgIcon user(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M19 21 V19 A4 4 0 0 0 15 15 H9 A4 4 0 0 0 5 19 V21")
                .addCircle(12f, 7f, 4f);
    }

    public static SvgIcon zap(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M13 2 L4 14 H12 L11 22 L20 10 H12 L13 2 Z");
    }

    public static SvgIcon target(int color, int sizePx) {
        return icon(color, sizePx)
                .addCircle(12f, 12f, 10f)
                .addCircle(12f, 12f, 6f)
                .addCircle(12f, 12f, 2f);
    }

    public static SvgIcon camera(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M14.5 4 H9.5 L7 7 H4 A2 2 0 0 0 2 9 V18 A2 2 0 0 0 4 20 H20 A2 2 0 0 0 22 18 V9 A2 2 0 0 0 20 7 H17 L14.5 4 Z")
                .addCircle(12f, 13f, 3f);
    }

    public static SvgIcon globe(int color, int sizePx) {
        return icon(color, sizePx)
                .addCircle(12f, 12f, 10f)
                .addPath("M12 2 A14.5 14.5 0 0 0 12 22 A14.5 14.5 0 0 0 12 2")
                .addPath("M2 12 H22");
    }

    public static SvgIcon eye(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M2 12 S5.5 5 12 5 S22 12 22 12 S18.5 19 12 19 S2 12 2 12 Z")
                .addCircle(12f, 12f, 3f);
    }

    public static SvgIcon settings(int color, int sizePx) {
        return icon(color, sizePx)
                .addCircle(12f, 12f, 3f)
                .addPath("M19.4 15 A1.65 1.65 0 0 0 19.73 16.82 L19.79 16.88 A2 2 0 0 1 16.96 19.71 L16.9 19.65 A1.65 1.65 0 0 0 15 19.98 A1.65 1.65 0 0 0 14 21.5 V21.66 A2 2 0 0 1 10 21.66 V21.57 A1.65 1.65 0 0 0 8.92 20.06 A1.65 1.65 0 0 0 7.1 20.39 L7.04 20.45 A2 2 0 0 1 4.21 17.62 L4.27 17.56 A1.65 1.65 0 0 0 4.6 15.74 A1.65 1.65 0 0 0 3.09 14.74 H3 A2 2 0 0 1 3 10.74 H3.09 A1.65 1.65 0 0 0 4.6 9.66 A1.65 1.65 0 0 0 4.27 7.84 L4.21 7.78 A2 2 0 0 1 7.04 4.95 L7.1 5.01 A1.65 1.65 0 0 0 8.92 5.34 H9 A1.65 1.65 0 0 0 10 3.83 V3.66 A2 2 0 0 1 14 3.66 V3.75 A1.65 1.65 0 0 0 15 5.26 A1.65 1.65 0 0 0 16.82 4.93 L16.88 4.87 A2 2 0 0 1 19.71 7.7 L19.65 7.76 A1.65 1.65 0 0 0 19.32 9.58 V9.66 A1.65 1.65 0 0 0 20.83 10.66 H21 A2 2 0 0 1 21 14.66 H20.91 A1.65 1.65 0 0 0 19.4 15 Z");
    }

    public static SvgIcon grid(int color, int sizePx) {
        return icon(color, sizePx)
                .addRect(3f, 3f, 7.5f, 7.5f, 1.5f, 1.5f)
                .addRect(13.5f, 3f, 7.5f, 7.5f, 1.5f, 1.5f)
                .addRect(3f, 13.5f, 7.5f, 7.5f, 1.5f, 1.5f)
                .addRect(13.5f, 13.5f, 7.5f, 7.5f, 1.5f, 1.5f);
    }

    public static SvgIcon lock(int color, int sizePx) {
        return icon(color, sizePx)
                .addRect(3f, 11f, 18f, 11f, 2f, 2f)
                .addPath("M7 11 V7 A5 5 0 0 1 17 7 V11");
    }

    /** Material Symbols content_paste (clean clipboard paste, never looks like export). */
    public static SvgIcon clipboardPaste(int color, int sizePx) {
        return icon(color, sizePx)
                .addFillPath("M19 2 H14.82 C14.4 0.84 13.3 0 12 0 C10.7 0 9.6 0.84 9.18 2 H5 C3.9 2 3 2.9 3 4 V20 C3 21.1 3.9 22 5 22 H19 C20.1 22 21 21.1 21 20 V4 C21 2.9 20.1 2 19 2 Z M12 2 C12.55 2 13 2.45 13 3 C13 3.55 12.55 4 12 4 C11.45 4 11 3.55 11 3 C11 2.45 11.45 2 12 2 Z M19 20 H5 V4 H7 V7 H17 V4 H19 V20 Z")
                .addFillPath("M8 10 H16 V12 H8 Z")
                .addFillPath("M8 14 H14 V16 H8 Z");
    }

    public static SvgIcon wifiOff(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M8.5 16.43 A5 5 0 0 1 15.5 16.43")
                .addPath("M5 12.86 A10 10 0 0 1 10.17 10.17")
                .addPath("M19 12.86 A10 10 0 0 0 17 11.34")
                .addPath("M2 8.82 A15 15 0 0 1 6.18 6.18")
                .addPath("M22 8.82 A15 15 0 0 0 10.71 5.06")
                .addPath("M2 2 L22 22")
                .addFillCircle(12f, 20f, 1.1f);
    }

    public static SvgIcon alertCircle(int color, int sizePx) {
        return icon(color, sizePx)
                .addCircle(12f, 12f, 10f)
                .addPath("M12 8 V12.5")
                .addFillCircle(12f, 16.2f, 1.1f);
    }

    public static SvgIcon checkCircle(int color, int sizePx) {
        return icon(color, sizePx)
                .addCircle(12f, 12f, 10f)
                .addPath("M9 12 L11 14 L15 10");
    }

    public static SvgIcon circle(int color, int sizePx) {
        return icon(color, sizePx)
                .addCircle(12f, 12f, 10f);
    }

    public static SvgIcon loader(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M21 12 A9 9 0 1 1 14.78 3.44");
    }

    public static SvgIcon refreshCw(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M3 12 A9 9 0 0 1 18.74 5.74 L21 8")
                .addPath("M21 3 V8 H16")
                .addPath("M21 12 A9 9 0 0 1 5.26 18.26 L3 16")
                .addPath("M8 16 H3 V21");
    }

    public static SvgIcon chevronDown(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M6 9 L12 15 L18 9");
    }

    public static SvgIcon download(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M21 15 V19 A2 2 0 0 1 19 21 H5 A2 2 0 0 1 3 19 V15")
                .addPath("M7 10 L12 15 L17 10")
                .addPath("M12 15 V3");
    }

    public static SvgIcon minimize(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M4 14 H10 V20")
                .addPath("M20 10 H14 V4")
                .addPath("M14 10 L21 3")
                .addPath("M3 21 L10 14");
    }

    public static SvgIcon slidersHorizontal(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M21 4 H14 M10 4 H3")
                .addPath("M21 12 H12 M8 12 H3")
                .addPath("M21 20 H16 M12 20 H3")
                .addPath("M14 2 V6 M8 10 V14 M16 18 V22");
    }

    public static SvgIcon info(int color, int sizePx) {
        return icon(color, sizePx)
                .addCircle(12f, 12f, 10f)
                .addPath("M12 16 V12")
                .addFillCircle(12f, 8f, 1.1f);
    }

    public static SvgIcon externalLink(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M15 3 H21 V9")
                .addPath("M10 14 L21 3")
                .addPath("M18 13 V19 A2 2 0 0 1 16 21 H5 A2 2 0 0 1 3 19 V8 A2 2 0 0 1 5 6 H11");
    }

    public static SvgIcon arrowLeft(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M12 19 L5 12 L12 5")
                .addPath("M19 12 H5");
    }

    public static SvgIcon moreVertical(int color, int sizePx) {
        return icon(color, sizePx)
                .addFillCircle(12f, 5f, 1.45f)
                .addFillCircle(12f, 12f, 1.45f)
                .addFillCircle(12f, 19f, 1.45f);
    }

    public static SvgIcon copy(int color, int sizePx) {
        return icon(color, sizePx)
                .addRect(9f, 9f, 13f, 13f, 2f, 2f)
                .addPath("M5 15 H4 A2 2 0 0 1 2 13 V4 A2 2 0 0 1 4 2 H13 A2 2 0 0 1 15 4 V5");
    }

    public static SvgIcon close(int color, int sizePx) {
        return icon(color, sizePx)
                .addPath("M18 6 L6 18")
                .addPath("M6 6 L18 18");
    }

    public static SvgIcon telegramPlane(int color, int sizePx) {
        return icon(color, sizePx)
                .addFillPath("M21.8 3.6 L2.4 11.1 C1.1 11.6 1.1 12.3 2.2 12.6 L7.1 14.1 L18.4 7.0 C18.9 6.7 19.4 6.9 19.0 7.2 L9.9 15.5 L9.5 20.6 C10.0 20.6 10.2 20.4 10.5 20.1 L12.9 17.8 L17.8 21.4 C18.7 21.9 19.4 21.6 19.6 20.6 L22.9 5.0 C23.2 3.7 22.4 3.2 21.8 3.6 Z");
    }

    public static Drawable tabIconFor(Context ctx, String name, int color, int sizeDp) {
        int px = ModxTheme.dp(ctx, sizeDp);
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.US);
        if (n.contains("main"))                          return home(color, px);
        if (n.contains("shop") || n.contains("cart"))    return shoppingBag(color, px);
        if (n.contains("safety") || n.contains("guard")) return shieldCheck(color, px);
        if (n.contains("player"))                        return user(color, px);
        if (n.contains("power"))                         return zap(color, px);
        if (n.contains("score"))                         return target(color, px);
        if (n.contains("camera"))                        return camera(color, px);
        if (n.contains("world"))                         return globe(color, px);
        if (n.contains("chams") || n.contains("visual")) return eye(color, px);
        if (n.contains("setting"))                       return settings(color, px);
        return grid(color, px);
    }
}
