
package pl.dock.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Calendar;

// Zegar analogowy (wbudowany widżet jak w panelu widżetów CarPlay).
// Odświeża się raz na minutę i tylko, gdy jest widoczny.
public class ClockCard extends View implements NightAware {

    private final float d;
    private boolean night = true;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            schedule();
        }
    };

    public ClockCard(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setContentDescription("Zegar analogowy");
    }

    @Override
    public void setNight(boolean n) {
        if (n == night) return;
        night = n;
        invalidate();
    }

    private void schedule() {
        long now = System.currentTimeMillis();
        h.postDelayed(tick, 60000L - (now % 60000L) + 50L);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        h.removeCallbacks(tick);
        schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        h.removeCallbacks(tick);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f - 14f * d;
        if (r <= 0) return;
        int col = night ? 0xFFFFFFFF : 0xFF111114;
        p.setColor(col);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);

        p.setStrokeWidth(3.2f * d);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12.0;
            float c = (float) Math.cos(a);
            float s = (float) Math.sin(a);
            float r0 = (i % 3 == 0) ? r * 0.80f : r * 0.86f;
            cv.drawLine(cx + c * r0, cy + s * r0, cx + c * r * 0.98f, cy + s * r * 0.98f, p);
        }

        Calendar cal = Calendar.getInstance();
        float min = cal.get(Calendar.MINUTE);
        float hr = (cal.get(Calendar.HOUR) % 12) + min / 60f;
        double ha = Math.PI * 2 * hr / 12.0 - Math.PI / 2;
        double ma = Math.PI * 2 * min / 60.0 - Math.PI / 2;
        p.setStrokeWidth(5.5f * d);
        cv.drawLine(cx, cy, cx + (float) Math.cos(ha) * r * 0.50f,
                cy + (float) Math.sin(ha) * r * 0.50f, p);
        p.setStrokeWidth(4f * d);
        cv.drawLine(cx, cy, cx + (float) Math.cos(ma) * r * 0.78f,
                cy + (float) Math.sin(ma) * r * 0.78f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF9F0A);
        cv.drawCircle(cx, cy, 4.5f * d, p);
    }
}







