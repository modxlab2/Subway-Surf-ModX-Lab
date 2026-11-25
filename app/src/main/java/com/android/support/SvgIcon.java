package com.android.support;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Minimal self-contained vector icon Drawable that parses SVG path data
 * (M m L l H h V v C c S s Q q T t A a Z z, including SVG elliptical arc-to-cubic
 * Bezier conversion) plus <rect> and <circle> primitives on a square viewBox grid.
 * Renders stroke-only with round caps and joins, scaled cleanly to bounds.
 */
final class SvgIcon extends Drawable {

    private final Path sourcePath = new Path();
    private final Path renderPath = new Path();
    private final Path fillSourcePath = new Path();
    private final Path fillRenderPath = new Path();
    private boolean hasFill = false;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float viewBoxSize;
    private final float strokeWidthGrid;
    private int currentColor;
    private int intrinsicSizePx = -1;
    private int cachedW = -1;
    private int cachedH = -1;
    private int cachedL = Integer.MIN_VALUE;
    private int cachedT = Integer.MIN_VALUE;
    private float cachedScale = 1f;
    private ValueAnimator colorAnimator;

    SvgIcon(int color, float strokeWidthGrid) {
        this(24f, color, strokeWidthGrid);
    }

    SvgIcon(float viewBoxSize, int color, float strokeWidthGrid) {
        this.viewBoxSize = viewBoxSize > 0f ? viewBoxSize : 24f;
        this.strokeWidthGrid = strokeWidthGrid;
        this.currentColor = color;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setColor(color);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    SvgIcon setIntrinsicSizePx(int sizePx) {
        this.intrinsicSizePx = sizePx;
        return this;
    }

    SvgIcon addPath(String d) {
        if (d != null && !d.isEmpty()) {
            parsePathInto(d, sourcePath);
            invalidateRenderCache();
        }
        return this;
    }

    SvgIcon addFillPath(String d) {
        if (d != null && !d.isEmpty()) {
            parsePathInto(d, fillSourcePath);
            hasFill = true;
            invalidateRenderCache();
        }
        return this;
    }

    SvgIcon addRect(float x, float y, float width, float height, float rx, float ry) {
        float arx = Math.max(0f, rx);
        float ary = Math.max(0f, ry);
        if (arx > 0f && ary == 0f) ary = arx;
        if (ary > 0f && arx == 0f) arx = ary;
        arx = Math.min(arx, width * 0.5f);
        ary = Math.min(ary, height * 0.5f);
        RectF r = new RectF(x, y, x + width, y + height);
        if (arx > 0f || ary > 0f) {
            sourcePath.addRoundRect(r, arx, ary, Path.Direction.CW);
        } else {
            sourcePath.addRect(r, Path.Direction.CW);
        }
        invalidateRenderCache();
        return this;
    }

    SvgIcon addCircle(float cx, float cy, float r) {
        if (r > 0f) {
            sourcePath.addCircle(cx, cy, r, Path.Direction.CW);
            invalidateRenderCache();
        }
        return this;
    }

    SvgIcon addFillCircle(float cx, float cy, float r) {
        if (r > 0f) {
            fillSourcePath.addCircle(cx, cy, r, Path.Direction.CW);
            hasFill = true;
            invalidateRenderCache();
        }
        return this;
    }

    void setColor(int color) {
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        if (this.currentColor != color) {
            this.currentColor = color;
            paint.setColor(color);
            fillPaint.setColor(color);
            invalidateSelf();
        }
    }

    void animateColor(int toColor, int durationMs) {
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        if (this.currentColor == toColor) {
            return;
        }
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), currentColor, toColor);
        va.setDuration(durationMs);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                currentColor = (Integer) a.getAnimatedValue();
                paint.setColor(currentColor);
                fillPaint.setColor(currentColor);
                invalidateSelf();
            }
        });
        colorAnimator = va;
        va.start();
    }

    private void invalidateRenderCache() {
        cachedW = -1;
        cachedH = -1;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        cachedW = -1;
        cachedH = -1;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicSizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicSizePx;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        int w = b.width();
        int h = b.height();
        if (w <= 0 || h <= 0) return;

        if (w != cachedW || h != cachedH || b.left != cachedL || b.top != cachedT) {
            float size = Math.min(w, h);
            cachedScale = size / viewBoxSize;
            float dx = b.left + (w - size) * 0.5f;
            float dy = b.top + (h - size) * 0.5f;
            matrix.reset();
            matrix.setScale(cachedScale, cachedScale);
            matrix.postTranslate(dx, dy);
            sourcePath.transform(matrix, renderPath);
            if (hasFill) {
                fillSourcePath.transform(matrix, fillRenderPath);
            }
            cachedW = w;
            cachedH = h;
            cachedL = b.left;
            cachedT = b.top;
        }

        if (hasFill) {
            canvas.drawPath(fillRenderPath, fillPaint);
        }
        if (!sourcePath.isEmpty()) {
            paint.setStrokeWidth(strokeWidthGrid * cachedScale);
            canvas.drawPath(renderPath, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        fillPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        fillPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    // =========================================================================
    // SVG Path Data Parser (M/m L/l H/h V/v C/c S/s Q/q T/t A/a Z/z)
    // =========================================================================
    private static void parsePathInto(String d, Path out) {
        PathScanner sc = new PathScanner(d);
        float curX = 0f, curY = 0f;
        float subX = 0f, subY = 0f;
        float lastCtrlX = 0f, lastCtrlY = 0f;
        char lastCmd = 0;
        boolean hasAnyCmd = false;

        while (sc.skipSeparators()) {
            char cmd;
            if (sc.isCommand()) {
                cmd = sc.nextChar();
            } else if (lastCmd != 0) {
                if (lastCmd == 'M') cmd = 'L';
                else if (lastCmd == 'm') cmd = 'l';
                else cmd = lastCmd;
            } else {
                break;
            }

            switch (cmd) {
                case 'M':
                case 'm': {
                    float x = sc.nextFloat();
                    float y = sc.nextFloat();
                    if (cmd == 'm' && hasAnyCmd) {
                        x += curX;
                        y += curY;
                    }
                    out.moveTo(x, y);
                    curX = x;
                    curY = y;
                    subX = x;
                    subY = y;
                    lastCtrlX = curX;
                    lastCtrlY = curY;
                    lastCmd = cmd;
                    hasAnyCmd = true;
                    break;
                }
                case 'L':
                case 'l': {
                    float x = sc.nextFloat();
                    float y = sc.nextFloat();
                    if (cmd == 'l') {
                        x += curX;
                        y += curY;
                    }
                    out.lineTo(x, y);
                    curX = x;
                    curY = y;
                    lastCtrlX = curX;
                    lastCtrlY = curY;
                    lastCmd = cmd;
                    hasAnyCmd = true;
                    break;
                }
                case 'H':
                case 'h': {
                    float x = sc.nextFloat();
                    if (cmd == 'h') x += curX;
                    out.lineTo(x, curY);
                    curX = x;
                    lastCtrlX = curX;
                    lastCtrlY = curY;
                    lastCmd = cmd;
                    hasAnyCmd = true;
                    break;
                }
                case 'V':
                case 'v': {
                    float y = sc.nextFloat();
                    if (cmd == 'v') y += curY;
                    out.lineTo(curX, y);
                    curY = y;
                    lastCtrlX = curX;
                    lastCtrlY = curY;
                    lastCmd = cmd;
                    hasAnyCmd = true;
                    break;
                }
                case 'C':
                case 'c': {
                    float x1 = sc.nextFloat(), y1 = sc.nextFloat();
                    float x2 = sc.nextFloat(), y2 = sc.nextFloat();
                    float x = sc.nextFloat(), y = sc.nextFloat();
                    if (cmd == 'c') {
                        x1 += curX; y1 += curY;
                        x2 += curX; y2 += curY;
                        x += curX; y += curY;
                    }
                    out.cubicTo(x1, y1, x2, y2, x, y);
                    lastCtrlX = x2;
                    lastCtrlY = y2;
                    curX = x;
                    curY = y;
                    lastCmd = cmd;
                    hasAnyCmd = true;
                    break;
                }
                case 'S':
                case 's': {
                    float x1 = curX, y1 = curY;
                    if (lastCmd == 'C' || lastCmd == 'c' || lastCmd == 'S' || lastCmd == 's') {
                        x1 = 2f * curX - lastCtrlX;
                        y1 = 2f * curY - lastCtrlY;
                    }
                    float x2 = sc.nextFloat(), y2 = sc.nextFloat();
                    float x = sc.nextFloat(), y = sc.nextFloat();
                    if (cmd == 's') {
                        x2 += curX; y2 += curY;
                        x += curX; y += curY;
                    }
                    out.cubicTo(x1, y1, x2, y2, x, y);
                    lastCtrlX = x2;
                    lastCtrlY = y2;
                    curX = x;
                    curY = y;
                    lastCmd = cmd;
                    hasAnyCmd = true;
                    break;
                }
                case 'Q':
                case 'q': {
                    float x1 = sc.nextFloat(), y1 = sc.nextFloat();
                    float x = sc.nextFloat(), y = sc.nextFloat();
                    if (cmd == 'q') {
                        x1 += curX; y1 += curY;
                        x += curX; y += curY;
                    }
                    out.quadTo(x1, y1, x, y);
                    lastCtrlX = x1;
                    lastCtrlY = y1;
                    curX = x;
                    curY = y;
                    lastCmd = cmd;
                    hasAnyCmd = true;
                    break;
                }
                case 'T':
                case 't': {
                    float x1 = curX, y1 = curY;
                    if (lastCmd == 'Q' || lastCmd == 'q' || lastCmd == 'T' || lastCmd == 't') {
                        x1 = 2f * curX - lastCtrlX;
                        y1 = 2f * curY - lastCtrlY;
                    }
                    float x = sc.nextFloat(), y = sc.nextFloat();
                    if (cmd == 't') {
                        x += curX; y += curY;
                    }
                    out.quadTo(x1, y1, x, y);
                    lastCtrlX = x1;
                    lastCtrlY = y1;
                    curX = x;
                    curY = y;
                    lastCmd = cmd;
                    hasAnyCmd = true;
                    break;
                }
                case 'A':
                case 'a': {
                    float rx = sc.nextFloat();
                    float ry = sc.nextFloat();
                    float xAxisRot = sc.nextFloat();
                    boolean largeArc = sc.nextFlag();
                    boolean sweep = sc.nextFlag();
                    float x = sc.nextFloat();
                    float y = sc.nextFloat();
                    if (cmd == 'a') {
                        x += curX;
                        y += curY;
                    }
                    arcToCubicBeziers(out, curX, curY, x, y, rx, ry, xAxisRot, largeArc, sweep);
                    curX = x;
                    curY = y;
                    lastCtrlX = curX;
                    lastCtrlY = curY;
                    lastCmd = cmd;
                    hasAnyCmd = true;
                    break;
                }
                case 'Z':
                case 'z': {
                    out.close();
                    curX = subX;
                    curY = subY;
                    lastCtrlX = curX;
                    lastCtrlY = curY;
                    lastCmd = cmd;
                    hasAnyCmd = true;
                    break;
                }
                default:
                    lastCmd = 0;
                    break;
            }
        }
    }

    /**
     * Converts an SVG elliptical arc (W3C SVG 1.1 Appendix F.6.5 / F.6.6)
     * into 1..N cubic Bezier segments (each <= 90 degrees).
     */
    private static void arcToCubicBeziers(Path out,
                                            double x1, double y1,
                                            double x2, double y2,
                                            double rx, double ry,
                                            double phiDeg,
                                            boolean largeArcFlag,
                                            boolean sweepFlag) {
        if (x1 == x2 && y1 == y2) {
            return;
        }
        if (rx == 0.0 || ry == 0.0) {
            out.lineTo((float) x2, (float) y2);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);

        double phi = Math.toRadians(phiDeg % 360.0);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        double dx2 = (x1 - x2) * 0.5;
        double dy2 = (y1 - y2) * 0.5;
        double x1p = cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;

        double rx2 = rx * rx;
        double ry2 = ry * ry;
        double x1p2 = x1p * x1p;
        double y1p2 = y1p * y1p;

        double lambda = (x1p2 / rx2) + (y1p2 / ry2);
        if (lambda > 1.0) {
            double sqrtLambda = Math.sqrt(lambda);
            rx *= sqrtLambda;
            ry *= sqrtLambda;
            rx2 = rx * rx;
            ry2 = ry * ry;
        }

        double num = Math.max(0.0, rx2 * ry2 - rx2 * y1p2 - ry2 * x1p2);
        double den = rx2 * y1p2 + ry2 * x1p2;
        double factor = (den == 0.0) ? 0.0 : Math.sqrt(num / den);
        if (largeArcFlag == sweepFlag) {
            factor = -factor;
        }
        double cxp = factor * ((rx * y1p) / ry);
        double cyp = factor * (-(ry * x1p) / rx);

        double cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) * 0.5;
        double cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) * 0.5;

        double ux = (x1p - cxp) / rx;
        double uy = (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx;
        double vy = (-y1p - cyp) / ry;

        double theta1 = vectorAngle(1.0, 0.0, ux, uy);
        double dTheta = vectorAngle(ux, uy, vx, vy);
        if (!sweepFlag && dTheta > 0.0) {
            dTheta -= 2.0 * Math.PI;
        } else if (sweepFlag && dTheta < 0.0) {
            dTheta += 2.0 * Math.PI;
        }

        int numSegs = Math.max(1, (int) Math.ceil(Math.abs(dTheta) / (Math.PI * 0.5)));
        double segAngle = dTheta / numSegs;
        for (int i = 0; i < numSegs; i++) {
            double t1 = theta1 + i * segAngle;
            double t2 = theta1 + (i + 1) * segAngle;
            double alpha = (4.0 / 3.0) * Math.tan((t2 - t1) * 0.25);

            double cos1 = Math.cos(t1), sin1 = Math.sin(t1);
            double cos2 = Math.cos(t2), sin2 = Math.sin(t2);

            double ep1x = cos1 - alpha * sin1;
            double ep1y = sin1 + alpha * cos1;
            double ep2x = cos2 + alpha * sin2;
            double ep2y = sin2 - alpha * cos2;

            double c1x = cx + cosPhi * (rx * ep1x) - sinPhi * (ry * ep1y);
            double c1y = cy + sinPhi * (rx * ep1x) + cosPhi * (ry * ep1y);
            double c2x = cx + cosPhi * (rx * ep2x) - sinPhi * (ry * ep2y);
            double c2y = cy + sinPhi * (rx * ep2x) + cosPhi * (ry * ep2y);
            double ex = (i == numSegs - 1) ? x2 : (cx + cosPhi * (rx * cos2) - sinPhi * (ry * sin2));
            double ey = (i == numSegs - 1) ? y2 : (cy + sinPhi * (rx * cos2) + cosPhi * (ry * sin2));

            out.cubicTo((float) c1x, (float) c1y, (float) c2x, (float) c2y, (float) ex, (float) ey);
        }
    }

    private static double vectorAngle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double mag = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
        if (mag == 0.0) return 0.0;
        double ratio = Math.max(-1.0, Math.min(1.0, dot / mag));
        double angle = Math.acos(ratio);
        if (ux * vy - uy * vx < 0.0) {
            angle = -angle;
        }
        return angle;
    }

    private static final class PathScanner {
        private final String s;
        private final int len;
        private int pos = 0;

        PathScanner(String s) {
            this.s = s;
            this.len = s.length();
        }

        boolean skipSeparators() {
            while (pos < len) {
                char c = s.charAt(pos);
                if (c == ' ' || c == ',' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
            return pos < len;
        }

        boolean isCommand() {
            if (pos >= len) return false;
            char c = s.charAt(pos);
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
        }

        char nextChar() {
            return s.charAt(pos++);
        }

        boolean nextFlag() {
            if (!skipSeparators()) return false;
            char c = s.charAt(pos);
            if (c == '0' || c == '1') {
                pos++;
                return c == '1';
            }
            return nextFloat() != 0f;
        }

        float nextFloat() {
            if (!skipSeparators()) return 0f;
            int start = pos;
            if (pos < len && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                pos++;
            }
            boolean seenDot = false;
            boolean seenExp = false;
            while (pos < len) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' && !seenDot && !seenExp) {
                    seenDot = true;
                    pos++;
                } else if ((c == 'e' || c == 'E') && !seenExp && pos > start) {
                    seenExp = true;
                    pos++;
                    if (pos < len && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                        pos++;
                    }
                } else {
                    break;
                }
            }
            if (pos == start) return 0f;
            try {
                return Float.parseFloat(s.substring(start, pos));
            } catch (NumberFormatException e) {
                return 0f;
            }
        }
    }
}
