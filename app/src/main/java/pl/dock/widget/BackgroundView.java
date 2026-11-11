
package pl.dock.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

// Tło launchera. Rysowane raz do małych bitmap (pół rozdzielczości); w trybie dziennym
// strona widżetów płynnie ciemnieje do wersji nocnej (jak w CarPlay).
public class BackgroundView extends View {

    private Bitmap bmpDay;
    private Bitmap bmpNight;
    private String style = "graphite";
    private boolean night = true;
    private float parallax;
    private float blend;     // 0..1: ile nocnej wersji nałożyć na dzienną
    // płynne przejście dzienny/nocny: renderowany udział wersji nocnej (crossfade)
    private float nightT = 1f;
    private float nightFrom, nightTo;
    private long nightStart;
    private boolean nightAnim;
    private static final long NIGHT_MS = 320L;   // rytm scrima motywu w LauncherActivity
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();

    public BackgroundView(Context c) {
        super(c);
    }

    public void setStyle(String style, boolean night) {
        boolean styleChanged = style != null && !style.equals(this.style);
        if (!styleChanged && night == this.night
                && (bmpNight != null || "system".equals(style))) {
            return;
        }
        if (!styleChanged && night != this.night && bmpNight != null && bmpDay != null) {
            // tylko zmiana trybu: płynny crossfade bitmap (bez przebudowy) -
            // zblurowane tło płynnie ciemnieje/jaśnieje zamiast skoku
            this.night = night;
            animateNight(night ? 1f : 0f);
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

    // 0 = tło wg trybu, 1 = zawsze nocne (strona widżetów w trybie dziennym)
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
        nightAnim = false;
        nightT = night ? 1f : 0f;
        if (getWidth() <= 0 || getHeight() <= 0 || "system".equals(style)) return;
        // obie wersje zawsze pod ręką - crossfade w obie strony
        bmpNight = make(true);
        bmpDay = make(false);
    }

    /** Płynny crossfade dzienny<->nocny (lerp klatka po klatce, jak tickCc w doku). */
    private void animateNight(float target) {
        nightFrom = nightT;
        nightTo = target;
        nightStart = android.os.SystemClock.uptimeMillis();
        if (!nightAnim) {
            nightAnim = true;
            postOnAnimation(nightTick);
        }
    }

    private final Runnable nightTick = new Runnable() {
        @Override
        public void run() {
            if (!nightAnim) return;
            float t = Math.min(1f, (android.os.SystemClock.uptimeMillis() - nightStart)
                    / (float) NIGHT_MS);
            nightT = nightFrom + (nightTo - nightFrom) * t;
            invalidate();
            if (t < 1f) postOnAnimation(nightTick);
            else nightAnim = false;
        }
    };

    @Override
    protected void onDraw(Canvas cv) {
        if (bmpNight == null) return;
        float extra = getWidth() * 0.06f;
        dst.set(-extra * parallax, 0, getWidth() + extra * (1f - parallax), getHeight());
        float t = Math.max(nightT, blend);   // blend = strona widżetów "zawsze nocna"
        if (t >= 0.999f || bmpDay == null) {
            paint.setAlpha(255);
            cv.drawBitmap(bmpNight, null, dst, paint);
            return;
        }
        paint.setAlpha(255);
        cv.drawBitmap(bmpDay, null, dst, paint);
        if (t > 0.001f) {
            paint.setAlpha((int) (255 * t));
            cv.drawBitmap(bmpNight, null, dst, paint);
            paint.setAlpha(255);
        }
    }

    /** Rysuje tło w WYMUSZONEJ wersji (dzień/noc), ignorując trwający crossfade -
     *  używane do migawki tła overlayu centrum łączności. */
    public void drawVersion(Canvas cv, boolean n) {
        Bitmap b = n ? bmpNight : (bmpDay != null ? bmpDay : bmpNight);
        if (b == null) return;
        float extra = getWidth() * 0.06f;
        dst.set(-extra * parallax, 0, getWidth() + extra * (1f - parallax), getHeight());
        paint.setAlpha(255);
        cv.drawBitmap(b, null, dst, paint);
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








