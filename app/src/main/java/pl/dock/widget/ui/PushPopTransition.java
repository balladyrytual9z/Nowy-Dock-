
package pl.dock.widget.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/** Apple-like push/pop: nowa plansza wjeżdża od prawej, stara lekko odjeżdża w lewo i przygasa. */
public final class PushPopTransition {

    public static void push(final View incoming, final View outgoing, boolean reduceMotion) {
        int w = incoming.getWidth() > 0 ? incoming.getWidth() : 1080;
        incoming.setTranslationX(w);
        incoming.setAlpha(1f);
        incoming.setVisibility(View.VISIBLE);

        if (reduceMotion) {
            incoming.setTranslationX(0);
            return;
        }

        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(320);
        a.setInterpolator(new DecelerateInterpolator(1.8f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float t = (Float) va.getAnimatedValue();
                incoming.setTranslationX(w * (1f - t));
                if (outgoing != null) {
                    outgoing.setTranslationX(-w * 0.25f * t);
                    outgoing.setAlpha(1f - 0.3f * t);
                    outgoing.setScaleX(1f - 0.04f * t);
                    outgoing.setScaleY(1f - 0.04f * t);
                }
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (outgoing != null) {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setTranslationX(0);
                    outgoing.setScaleX(1f);
                    outgoing.setScaleY(1f);
                    outgoing.setAlpha(1f);
                }
            }
        });
        a.start();
    }

    public static void pop(final View current, final View target, boolean reduceMotion) {
        int w = current.getWidth() > 0 ? current.getWidth() : 1080;
        current.setVisibility(View.VISIBLE);
        target.setVisibility(View.VISIBLE);
        if (reduceMotion) {
            current.setVisibility(View.GONE);
            return;
        }

        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(280);
        a.setInterpolator(new AccelerateInterpolator(1.5f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float t = (Float) va.getAnimatedValue();
                current.setTranslationX(w * t);
                current.setAlpha(1f - 0.4f * t);
                target.setTranslationX(-w * 0.25f * (1f - t));
                target.setAlpha(0.7f + 0.3f * t);
                target.setScaleX(0.96f + 0.04f * t);
                target.setScaleY(0.96f + 0.04f * t);
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                current.setVisibility(View.GONE);
                current.setTranslationX(0);
                target.setTranslationX(0);
                target.setAlpha(1f);
                target.setScaleX(1f);
                target.setScaleY(1f);
            }
        });
        a.start();
    }

    private PushPopTransition() {}
}





