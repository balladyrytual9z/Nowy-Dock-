
package pl.dock.widget;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Global Edit Mode + Wobble animation (iOS-like) for home icons and Control Center tiles.
 * Singleton-style controller: long-press enters edit, "Done" or outside tap exits.
 */
public final class EditModeController {

    private static final EditModeController INSTANCE = new EditModeController();

    public static EditModeController get() {
        return INSTANCE;
    }

    public interface Listener {
        void onEditModeChanged(boolean editing);
    }

    private boolean editing;
    private final List<Listener> listeners = new ArrayList<Listener>();
    private final WeakHashMap<View, ValueAnimator> wobbles = new WeakHashMap<View, ValueAnimator>();

    private EditModeController() {
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean on) {
        if (editing == on) return;
        editing = on;
        for (Listener l : listeners) {
            try {
                l.onEditModeChanged(on);
            } catch (Throwable ignored) {
            }
        }
        if (!on) {
            // stop all wobbles
            for (ValueAnimator a : wobbles.values()) {
                if (a != null) a.cancel();
            }
            wobbles.clear();
        }
    }

    public void toggle() {
        setEditing(!editing);
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /**
     * Attach continuous small rotation "wobble" while in edit mode.
     * Phase offset avoids synchronized motion across icons.
     */
    public void startWobble(final View v, float phase) {
        if (v == null) return;
        stopWobble(v);
        if (!editing) return;
        final float amp = 3.5f; // degrees
        ValueAnimator anim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        anim.setDuration(320 + (long) (phase * 40));
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue() + phase;
                v.setRotation((float) (Math.sin(t) * amp));
            }
        });
        anim.start();
        wobbles.put(v, anim);
    }

    public void stopWobble(View v) {
        ValueAnimator a = wobbles.remove(v);
        if (a != null) {
            a.cancel();
        }
        if (v != null) {
            v.setRotation(0f);
        }
    }
}



