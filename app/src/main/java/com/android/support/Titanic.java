package com.android.support;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.view.animation.LinearInterpolator;

public class Titanic {
    private AnimatorSet animatorSet;
    private Animator.AnimatorListener animatorListener;
    private TitanicTextView boundView;
    private boolean paused = false;

    public Animator.AnimatorListener getAnimatorListener() {
        return animatorListener;
    }

    public void setAnimatorListener(Animator.AnimatorListener animatorListener) {
        this.animatorListener = animatorListener;
    }

    public boolean isRunning() {
        return animatorSet != null && animatorSet.isRunning();
    }

    public void setPaused(boolean shouldPause) {
        if (this.paused == shouldPause && (!shouldPause || animatorSet == null)) {
            if (!shouldPause && boundView != null && !isRunning()) {
                start(boundView);
            }
            return;
        }
        this.paused = shouldPause;
        if (shouldPause) {
            cancel();
        } else if (boundView != null) {
            start(boundView);
        }
    }

    public void start(final TitanicTextView textView) {
        if (textView == null) return;
        boundView = textView;
        paused = false;
        if (isRunning()) {
            return;
        }

        final Runnable animate = new Runnable() {
            @Override
            public void run() {
                if (paused || boundView != textView) {
                    return;
                }
                if (animatorSet != null) {
                    animatorSet.cancel();
                }

                textView.setSinking(true);

                // Horizontal wave scroll (200px = WAVE_WIDTH)
                ObjectAnimator maskXAnimator = ObjectAnimator.ofFloat(textView, "maskX", 0f, 200f);
                maskXAnimator.setRepeatCount(ValueAnimator.INFINITE);
                maskXAnimator.setDuration(1400L);
                maskXAnimator.setStartDelay(0L);

                // Gentle vertical bob centered on the text height
                int h = Math.max(12, textView.getHeight());
                float swing = h * 0.38f;
                ObjectAnimator maskYAnimator = ObjectAnimator.ofFloat(textView, "maskY", swing, -swing);
                maskYAnimator.setRepeatCount(ValueAnimator.INFINITE);
                maskYAnimator.setRepeatMode(ValueAnimator.REVERSE);
                maskYAnimator.setDuration(8000L);
                maskYAnimator.setStartDelay(0L);

                final AnimatorSet set = new AnimatorSet();
                set.playTogether(maskXAnimator, maskYAnimator);
                set.setInterpolator(new LinearInterpolator());
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) { }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        textView.setSinking(false);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            textView.postInvalidate();
                        } else {
                            textView.postInvalidateOnAnimation();
                        }
                        if (animatorSet == set) {
                            animatorSet = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) { }

                    @Override
                    public void onAnimationRepeat(Animator animation) { }
                });
                if (animatorListener != null) {
                    set.addListener(animatorListener);
                }
                animatorSet = set;
                set.start();
            }
        };

        if (!textView.isSetUp()) {
            textView.setAnimationSetupCallback(new AnimationSetupCallback() {
                @Override
                public void onSetupAnimation(final TitanicTextView target) {
                    if (!paused && boundView == target) {
                        animate.run();
                    }
                }
            });
        } else {
            animate.run();
        }
    }

    public void cancel() {
        if (animatorSet != null) {
            AnimatorSet running = animatorSet;
            animatorSet = null;
            running.cancel();
        }
        if (boundView != null) {
            boundView.setSinking(false);
        }
    }
}