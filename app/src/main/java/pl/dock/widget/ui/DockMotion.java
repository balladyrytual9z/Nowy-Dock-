package pl.dock.widget.ui;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

import java.util.WeakHashMap;

/**
 * Shared interpolators for Dock 2.0 – iOS-inspired spring & standard curves.
 * Plus real spring physics (Newtonian, like iOS DynamicAnimation):
 * values are integrated every frame - position, velocity, stiffness, damping.
 */
public final class DockMotion {

    /** Smooth Bezier with slight overshoot (iOS Quick / springy open). */
    public static final TimeInterpolator IOS_SPRING =
            new PathInterpolator(0.25f, 1.1f, 0.5f, 1.0f);

    /** Standard iOS ease. */
    public static final TimeInterpolator IOS_STANDARD =
            new PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f);

    public static final TimeInterpolator STANDARD = new AccelerateDecelerateInterpolator();
    public static final TimeInterpolator DECELERATE = new DecelerateInterpolator(1.5f);
    public static final TimeInterpolator EMPHASIZED = new DecelerateInterpolator(2f);
    public static final TimeInterpolator QUICK = new LinearInterpolator();
    public static final TimeInterpolator SOFT_OVERSHOOT = new OvershootInterpolator(0.6f);

    // ------------------------------------------------------------ spring physics

    public static final float STIFFNESS_HIGH = 800f;
    public static final float STIFFNESS_MEDIUM = 400f;
    public static final float STIFFNESS_LOW = 200f;

    /** animatory sprężyn per widok: 0 = skala, 1 = translationX, 2 = translationY */
    private static final WeakHashMap<View, ValueAnimator[]> SPRINGS =
            new WeakHashMap<View, ValueAnimator[]>();

    /** Sprężyna na skali (docisk/odbicie kafelka jak w Control Center). */
    public static void springScale(View v, float target, float stiffness, float dampingRatio) {
        start(v, 0, v.getScaleX(), target, stiffness, dampingRatio);
    }

    public static void springX(View v, float target, float stiffness, float dampingRatio) {
        start(v, 1, v.getTranslationX(), target, stiffness, dampingRatio);
    }

    public static void springY(View v, float target, float stiffness, float dampingRatio) {
        start(v, 2, v.getTranslationY(), target, stiffness, dampingRatio);
    }

    /** Standardowy sprężysty docisk ikony (iOS tap). */
    public static void press(View v) {
        springScale(v, 0.94f, STIFFNESS_HIGH, 0.62f);
    }

    /** Sprężysty powrót ikony po puszczeniu (z lekkim odbiciem). */
    public static void release(View v) {
        springScale(v, 1f, STIFFNESS_MEDIUM, 0.66f);
    }

    private static synchronized void start(final View v, final int prop, float from,
                                          final float target, float k, float zeta) {
        if (v == null) return;
        ValueAnimator[] arr = SPRINGS.get(v);
        if (arr == null) {
            arr = new ValueAnimator[3];
            SPRINGS.put(v, arr);
        }
        if (arr[prop] != null) arr[prop].cancel();
        final float[] s = {from, 0f};   // pozycja, prędkość
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(3000);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator an) {
                float dt = 1f / 60f;
                float c = 2f * zeta * (float) Math.sqrt(k);
                s[1] += (-k * (s[0] - target) - c * s[1]) * dt;
                s[0] += s[1] * dt;
                apply(v, prop, s[0]);
                if (Math.abs(s[0] - target) < 0.0015f && Math.abs(s[1]) < 0.004f) {
                    apply(v, prop, target);
                    an.cancel();
                }
            }
        });
        arr[prop] = a;
        a.start();
    }

    private static void apply(View v, int prop, float val) {
        if (prop == 0) {
            v.setScaleX(val);
            v.setScaleY(val);
        } else if (prop == 1) {
            v.setTranslationX(val);
        } else {
            v.setTranslationY(val);
        }
    }

    private DockMotion() {}
}
