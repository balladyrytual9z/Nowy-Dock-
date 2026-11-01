
package pl.dock.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

// TÅo launchera. Rysowane raz do maÅych bitmap (pÃ³Å rozdzielczoÅci); w trybie dziennym
// strona widÅ¼etÃ³w pÅynnie ciemnieje do wersji nocnej (jak w CarPlay).
public class BackgroundView extends View {

    private Bitmap bmpDay;
    private Bitmap bmpNight;
    private String style = "graphite";
    private boolean night = true;
    private float parallax;
    private float blend;     // 0..1: ile nocnej wersji naÅoÅ¼yÄ na dziennÄ
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();

    public BackgroundView(Context c) {
        super(c);
    }

    public void setStyle(String style, boolean night) {
        if (style != null && style.equals(this.style) && night == this.night
                && (bmpNight != null || "system".equals(style))) {
            return;
        }
        this.style = style;
        this.night = night;
        rebuild();
        invalidate();
    }

    public void setParallax(float p) {
        if (Math.abs(p - parallax) < 0.003f) return;
        parallax = p;
        invalidate();
    }

    // 0 = tÅo wg trybu, 1 = zawsze nocne (strona widÅ¼etÃ³w w trybie dziennym)
    public void setNightBlend(float b) {
        b = Math.max(0f, Math.min(1f, b));
        if (Math.abs(b - blend) < 0.004f) return;
        blend = b;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rebuild();
    }

    private Bitmap make(boolean n) {
        int w = getWidth();
        int h = getHeight();
        int bw = Math.max(64, w / 2);
        int bh = Math.max(64, h / 2);
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Wallpapers.paint(new Canvas(b), bw, bh, Wallpapers.byId(style), n);
        return b;
    }

    private void rebuild() {
        if (bmpDay != null) bmpDay.recycle();
        if (bmpNight != null) bmpNight.recycle();
        bmpDay = null;
        bmpNight = null;
        if (getWidth() <= 0 || getHeight() <= 0 || "system".equals(style)) return;
        bmpNight = make(true);
        if (!night) bmpDay = make(false);
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (bmpNight == null) return;
        float extra = getWidth() * 0.06f;
        dst.set(-extra * parallax, 0, getWidth() + extra * (1f - parallax), getHeight());
        if (night || bmpDay == null) {
            paint.setAlpha(255);
            cv.drawBitmap(bmpNight, null, dst, paint);
            return;
        }
        paint.setAlpha(255);
        cv.drawBitmap(bmpDay, null, dst, paint);
        if (blend > 0.001f) {
            paint.setAlpha((int) (255 * blend));
            cv.drawBitmap(bmpNight, null, dst, paint);
            paint.setAlpha(255);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (bmpDay != null) bmpDay.recycle();
        if (bmpNight != null) bmpNight.recycle();
        bmpDay = null;
        bmpNight = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (bmpNight == null && getWidth() > 0) rebuild();
    }
}







