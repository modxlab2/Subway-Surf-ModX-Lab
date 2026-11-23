package com.android.support;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.TextView;

public class TitanicTextView extends TextView {

    private static final int WAVE_WIDTH = 200;
    private static final int WAVE_HEIGHT = 100;

    private AnimationSetupCallback animationSetupCallback;
    private float maskX, maskY;
    private boolean sinking;
    private boolean setUp;
    private BitmapShader shader;
    private Matrix shaderMatrix;
    private float offsetY;

    public TitanicTextView(Context context) {
        super(context);
        init();
    }

    public TitanicTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TitanicTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        shaderMatrix = new Matrix();
    }

    public AnimationSetupCallback getAnimationSetupCallback() {
        return animationSetupCallback;
    }

    public void setAnimationSetupCallback(AnimationSetupCallback animationSetupCallback) {
        this.animationSetupCallback = animationSetupCallback;
    }

    public float getMaskX() {
        return maskX;
    }

    public void setMaskX(float maskX) {
        this.maskX = maskX;
        invalidate();
    }

    public float getMaskY() {
        return maskY;
    }

    public void setMaskY(float maskY) {
        this.maskY = maskY;
        invalidate();
    }

    public boolean isSinking() {
        return sinking;
    }

    public void setSinking(boolean sinking) {
        this.sinking = sinking;
    }

    public boolean isSetUp() {
        return setUp;
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        createShader();
    }

    @Override
    public void setTextColor(android.content.res.ColorStateList colors) {
        super.setTextColor(colors);
        createShader();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        createShader();
        if (!setUp) {
            setUp = true;
            if (animationSetupCallback != null) {
                animationSetupCallback.onSetupAnimation(TitanicTextView.this);
            }
        }
    }

    private void createShader() {
        try {
            int textColor = getCurrentTextColor();
            int baseColor = Color.argb(
                    135,
                    Color.red(textColor),
                    Color.green(textColor),
                    Color.blue(textColor));

            Bitmap b = Bitmap.createBitmap(WAVE_WIDTH, WAVE_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(baseColor);

            Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            wavePaint.setStyle(Paint.Style.FILL);
            wavePaint.setColor(textColor);

            Path path = new Path();
            float midY = WAVE_HEIGHT * 0.50f;
            float amp = WAVE_HEIGHT * 0.14f;
            path.moveTo(0f, WAVE_HEIGHT);
            path.lineTo(0f, midY);
            for (int x = 0; x <= WAVE_WIDTH; x += 4) {
                float y = midY + (float) Math.sin((x / (double) WAVE_WIDTH) * 2.0 * Math.PI) * amp;
                path.lineTo(x, y);
            }
            path.lineTo(WAVE_WIDTH, WAVE_HEIGHT);
            path.close();
            c.drawPath(path, wavePaint);

            shader = new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
            getPaint().setShader(shader);
            offsetY = (getHeight() - WAVE_HEIGHT) / 2f;
        } catch (Exception e) {
            getPaint().setShader(null);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (sinking && shader != null) {
            if (getPaint().getShader() == null) {
                getPaint().setShader(shader);
            }
            shaderMatrix.setTranslate(maskX, maskY + offsetY);
            shader.setLocalMatrix(shaderMatrix);
        } else {
            getPaint().setShader(null);
        }
        super.onDraw(canvas);
    }
}