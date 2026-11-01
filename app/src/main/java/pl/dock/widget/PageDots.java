
package pl.dock.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

// Kropki stron. Na stronie widÅ¼etÃ³w i ekranie gÅÃ³wnym leÅ¼Ä w szarej kapsule, na liÅcie aplikacji bez niej.
public class PageDots extends View {

    private int count;
    private float active;
    private boolean capsule;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private int base = 0x00FFFFFF;

    public PageDots(Context c, boolean capsule) {
        super(c);
        this.capsule = capsule;
        d = c.getResources().getDisplayMetrics().density;
    }

    public void setLight(boolean light) {
        base = light ? 0x00000000 : 0x00FFFFFF;
        invalidate();
    }

    public void set(int count, float active) {
        this.count = count;
        this.active = active;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (count <= 1) return;
        float rr = 5f * d;
        float pitch = 20f * d;
        float total = pitch * (count - 1);
        float x0 = getWidth() / 2f - total / 2f;
        float cy = getHeight() / 2f;
        if (capsule) {
            p.setColor(0x66808088);
            r.set(x0 - 16f * d, cy - 11f * d, x0 + total + 16f * d, cy + 11f * d);
            cv.drawRoundRect(r, 11f * d, 11f * d, p);
        }
        for (int i = 0; i < count; i++) {
            float near = Math.max(0f, 1f - Math.abs(i - active));
            int a = 90 + (int) (150 * near);
            int col = capsule ? ((near > 0.5f) ? 0xFF000000 | base : 0x99FFFFFF)
                    : ((a << 24) | base);
            if (capsule && base == 0x00000000) col = near > 0.5f ? 0xFF000000 : 0x66000000;
            p.setColor(col);
            cv.drawCircle(x0 + i * pitch, cy, rr, p);
        }
    }
}







