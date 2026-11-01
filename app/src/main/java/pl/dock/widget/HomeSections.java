
package pl.dock.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;

// Ekran gÅÃ³wny zawsze w trzech sekcjach jak w CarPlay:
// po lewej duÅ¼y widÅ¼et (docelowo mapa), po prawej u gÃ³ry 1/3 (widÅ¼et), po prawej na dole 2/3 (odtwarzanie).
public class HomeSections extends ViewGroup implements NightAware {

    public interface Callbacks {
        void onSlot(String where);
    }

    private View left, rightTop, rightBottom;
    private boolean night = true;
    private final float d;

    public HomeSections(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(true);
    }

    public void setViews(View l, View rt, View rb) {
        removeAllViews();
        left = l;
        rightTop = rt;
        rightBottom = rb;
        if (l != null) addView(l);
        if (rt != null) addView(rt);
        if (rb != null) addView(rb);
        requestLayout();
    }

    @Override
    public void setNight(boolean n) {
        night = n;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof NightAware) ((NightAware) v).setNight(n);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
    }

    private void place(View v, float l, float t, float r, float b) {
        if (v == null) return;
        int w = (int) (r - l);
        int h = (int) (b - t);
        v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        v.layout((int) l, (int) t, (int) l + w, (int) t + h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int rr, int b) {
        float w = rr - l;
        float h = b - t;
        // Inset for dock so content is not fully covered; content still can scroll under glass dock
        float dockInset = Ui.dp(getContext(), Ui.DOCK_OFFSET_DP);
        boolean dockLeft = Ui.dockLeft(getContext());
        float padL = 12f * d + (dockLeft ? dockInset : 0);
        float padR = 12f * d + (dockLeft ? 0 : dockInset);
        float pad = 12f * d;
        float gap = 10f * d;
        float usableW = w - padL - padR - gap;
        float leftW = usableW * 0.60f;
        float rx = padL + leftW + gap;
        float topH = (h - 2 * pad - gap) / 3f;
        place(left, padL, pad, padL + leftW, h - pad);
        place(rightTop, rx, pad, w - padR, pad + topH);
        place(rightBottom, rx, pad + topH + gap, w - padR, h - pad);
    }

    // pusty slot (miejsce na widÅ¼et) w stylu kart CarPlay
    public static class Slot extends View implements NightAware {
        private final String where;
        private boolean night = true;
        private final float d;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public Slot(Context c, String where, final Callbacks cb) {
            super(c);
            this.where = where;
            d = c.getResources().getDisplayMetrics().density;
            setContentDescription("Miejsce na widÅ¼et");
            setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (cb != null) cb.onSlot(Slot.this.where);
                }
            });
        }

        @Override
        public void setNight(boolean n) {
            night = n;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            r.set(0, 0, getWidth(), getHeight());
            Ui.ringCard(cv, r, 30f * d, 1.5f * d, night, true);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(night ? 0x66FFFFFF : 0x66000000);
            cv.drawLine(cx - 12f * d, cy - 8f * d, cx + 12f * d, cy - 8f * d, p);
            cv.drawLine(cx, cy - 20f * d, cx, cy + 4f * d, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(15f * getResources().getDisplayMetrics().scaledDensity);
            cv.drawText("Dodaj widÅ¼et", cx, cy + 28f * d, p);
        }
    }
}







