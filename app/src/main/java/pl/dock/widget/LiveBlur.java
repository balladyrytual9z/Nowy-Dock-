package pl.dock.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

/** Wysokowydajny blur dla Androida 9 (bez RenderScript – brak w API 28 na części OEM). */
public class LiveBlur {

    private static final int SCALE = 6;
    private static final int RADIUS = 6;
    private static final int PASSES = 3;

    private static Bitmap buf;

    public static volatile View snapshotExclude;

    private static final Paint regionPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final Paint tintPaint = new Paint();
    private static final Paint legacyPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    static {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(1.45f);
        regionPaint.setColorFilter(new ColorMatrixColorFilter(m));
    }

    public static Bitmap captureAndBlur(ViewGroup root, int srcW, int srcH) {
        try {
            int w = Math.max(1, srcW / SCALE);
            int h = Math.max(1, srcH / SCALE);
            if (buf == null || buf.getWidth() != w || buf.getHeight() != h) {
                buf = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            buf.eraseColor(0);
            Canvas cv = new Canvas(buf);
            cv.scale(1f / SCALE, 1f / SCALE);
            View excl = snapshotExclude;
            boolean restore = excl != null && excl.getVisibility() == View.VISIBLE;
            if (restore) excl.setVisibility(View.INVISIBLE);
            root.draw(cv);
            if (restore) excl.setVisibility(View.VISIBLE);
            Blur.blur(buf, RADIUS, PASSES);
            return buf;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void draw(Canvas cv, Bitmap b, int left, int top, int right, int bottom,
                            boolean night) {
        if (b == null) return;
        android.graphics.Rect src = new android.graphics.Rect(0, 0, b.getWidth(), b.getHeight());
        android.graphics.RectF dst = new android.graphics.RectF(left, top, right, bottom);
        cv.drawBitmap(b, src, dst, legacyPaint);
        tintPaint.setColor(night ? 0x48000000 : 0x38FFFFFF);
        cv.drawRect(dst, tintPaint);
    }

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
        android.graphics.Rect src = new android.graphics.Rect(sx, sy, ex, ey);
        android.graphics.RectF dst = new android.graphics.RectF(dstL, dstT, dstR, dstB);
        cv.drawBitmap(b, src, dst, regionPaint);
        tintPaint.setColor(night ? 0x22000000 : 0x12FFFFFF);
        cv.drawRect(dst, tintPaint);
    }
}
