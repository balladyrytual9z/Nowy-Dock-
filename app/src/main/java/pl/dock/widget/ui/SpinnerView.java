
package pl.dock.widget.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/** ObracajÄcy siÄ pierÅcieÅ z kreskami â jak iOS "reconnecting". */
public class SpinnerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float angle = 0f;
    private int color = 0xFFFF9F0A;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            angle = (angle + 14f) % 360f;
            invalidate();
            if (isAttachedToWindow()) h.postDelayed(this, 16);
        }
    };

    public SpinnerView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.5f * c.getResources().getDisplayMetrics().density);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        h.post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        h.removeCallbacks(tick);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float pad = paint.getStrokeWidth() * 2;
        rect.set(pad, pad, getWidth() - pad, getHeight() - pad);
        cv.save();
        cv.rotate(angle, getWidth() / 2f, getHeight() / 2f);
        for (int i = 0; i < 12; i++) {
            paint.setColor((color & 0x00FFFFFF) | ((40 + i * 18) << 24));
            cv.drawArc(rect, i * 30f, 22f, false, paint);
        }
        cv.restore();
    }
}





