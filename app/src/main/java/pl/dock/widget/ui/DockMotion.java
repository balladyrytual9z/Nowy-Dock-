
package pl.dock.widget.ui;

import android.animation.TimeInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Shared interpolators for Dock 2.0 – iOS-inspired spring & standard curves.
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

    private DockMotion() {}
}


