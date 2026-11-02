package pl.dock.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.ViewGroup;

/** Wysokowydajny blur dla Androida 9 (bez RenderScript – brak w API 28 na części OEM). */
public class LiveBlur {

    private static final int SCALE = 6;     // downsampling 1/6
    private static final int RADIUS = 4;    // box blur pass
    private static final int PASSES = 3;

    /** Zrzuca widok (root) do bitmapy i rozmywa. Zwraca bitmapę do narysowania. */
    public static Bitmap captureAndBlur(ViewGroup root, int srcW, int srcH) {
        try {
            int w = Math.max(1, srcW / SCALE);
            int h = Math.max(1, srcH / SCALE);
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.scale(1f / SCALE, 1f / SCALE);
            root.draw(cv);
            Blur.blur(b, RADIUS, PASSES);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Rysuje rozmyte tło z lekkim przyciemnieniem/rozjaśnieniem w zależności od motywu. */
    public static void draw(Canvas cv, Bitmap b, int left, int top, int right, int bottom,
                            boolean night) {
        if (b == null) return;
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        android.graphics.Rect src = new android.graphics.Rect(0, 0, b.getWidth(), b.getHeight());
        android.graphics.RectF dst = new android.graphics.RectF(left, top, right, bottom);
        cv.drawBitmap(b, src, dst, p);
        p.setColor(night ? 0x55000000 : 0x44FFFFFF);
        cv.drawRect(dst, p);
    }

    /**
     * Rysuje WYCINEK pelnoekranowego snapshota odpowiadajacy prostokatowi doka.
     * Wcześniej caly ekran byl rozciagany na pasek doka (efekt miniatury zbiorczej).
     * src* = wspolrzedne w root (pelny ekran), dst* = wspolrzedne widoku doka.
     */
    public static void drawRegion(Canvas cv, Bitmap b, int rootW, int rootH,
                                  int srcX0, int srcY0, int srcX1, int srcY1,
                                  float dstL, float dstT, float dstR, float dstB,
                                  boolean night) {
        if (b == null || b.isRecycled() || rootW <= 0 || rootH <= 0) return;
        float kx = b.getWidth() / (float) rootW;
        float ky = b.getHeight() / (float) rootH;
        int sx = Math.max(0, (int) (srcX0 * kx));
        int sy = Math.max(0, (int) (srcY0 * ky));
        int ex = Math.min(b.getWidth(), Math.max(1, (int) (srcX1 * kx)));
        int ey = Math.min(b.getHeight(), Math.max(1, (int) (srcY1 * ky)));
        if (ex <= sx || ey <= sy) return;
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        android.graphics.Rect src = new android.graphics.Rect(sx, sy, ex, ey);
        android.graphics.RectF dst = new android.graphics.RectF(dstL, dstT, dstR, dstB);
        cv.drawBitmap(b, src, dst, p);
        p.setColor(night ? 0x55000000 : 0x44FFFFFF);
        cv.drawRect(dst, p);
    }
}


