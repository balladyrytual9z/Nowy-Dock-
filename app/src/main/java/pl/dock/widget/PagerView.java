
package pl.dock.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

// Poziomy pager: strony przesuwajÄ siÄ pÅynnie za palcem, a po puszczeniu dochodzÄ do celu.
public class PagerView extends FrameLayout {

    public interface Listener {
        void onScroll(float position);

        void onSettled(int index);
    }

    private float pos;
    private Listener listener;
    private ValueAnimator anim;
    private float downX, downY, lastX;
    private boolean dragging;
    private final int slop;
    private final int minFling;
    private VelocityTracker vt;
    private boolean reduceMotion;

    public PagerView(Context c) {
        super(c);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = Math.max(vc.getScaledMinimumFlingVelocity() * 4, 700);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setReduceMotion(boolean r) {
        reduceMotion = r;
    }

    public float position() {
        return pos;
    }

    public int count() {
        return getChildCount();
    }

    private float clamp(float p) {
        float max = Math.max(0, getChildCount() - 1);
        return Math.max(0, Math.min(max, p));
    }

    public void setPosition(float p) {
        pos = clamp(p);
        apply();
        if (listener != null) listener.onScroll(pos);
    }

    private void apply() {
        int w = getWidth();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            float d = i - pos;
            v.setTranslationX(d * w);
            int want = Math.abs(d) < 1f ? VISIBLE : INVISIBLE;
            if (v.getVisibility() != want) v.setVisibility(want);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        apply();
    }

    private void cancelAnim() {
        if (anim != null) {
            anim.cancel();
            anim = null;
            if (listener != null) listener.onSettled(Math.round(pos));
        }
    }

    public void goTo(int index, boolean animate) {
        cancelAnim();
        final float target = clamp(index);
        if (!animate || reduceMotion || getWidth() == 0 || Math.abs(target - pos) < 0.001f) {
            setPosition(target);
            if (listener != null) listener.onSettled((int) target);
            return;
        }
        anim = ValueAnimator.ofFloat(pos, target);
        anim.setDuration(260 + (long) (70 * Math.min(3f, Math.abs(target - pos))));
        anim.setInterpolator(new DecelerateInterpolator(1.7f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                setPosition((Float) a.getAnimatedValue());
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (!cancelled && listener != null) listener.onSettled((int) target);
            }
        });
        anim.start();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = false;
                if (anim != null && anim.isRunning()) {
                    cancelAnim();
                    startTracking(e);
                    dragging = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                    dragging = true;
                    startTracking(e);
                    lastX = e.getX();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            default:
                break;
        }
        return dragging;
    }

    private void startTracking(MotionEvent e) {
        if (vt != null) vt.recycle();
        vt = VelocityTracker.obtain();
        vt.addMovement(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            downX = lastX = e.getX();
            downY = e.getY();
            startTracking(e);
            return true;
        }
        if (vt == null) startTracking(e);
        vt.addMovement(e);
        if (a == MotionEvent.ACTION_MOVE) {
            if (!dragging && Math.abs(e.getX() - downX) > slop) {
                dragging = true;
                lastX = e.getX();
            }
            if (dragging && getWidth() > 0) {
                float dx = e.getX() - lastX;
                lastX = e.getX();
                setPosition(pos - dx / getWidth());
            }
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (dragging) {
                vt.computeCurrentVelocity(1000);
                float vx = vt.getXVelocity();
                int target;
                if (vx < -minFling) {
                    target = (int) Math.floor(pos) + 1;
                } else if (vx > minFling) {
                    target = (int) Math.ceil(pos) - 1;
                } else {
                    target = Math.round(pos);
                }
                goTo(Math.max(0, Math.min(getChildCount() - 1, target)), true);
            }
            dragging = false;
            vt.recycle();
            vt = null;
            return true;
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAnim();
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
    }
}







