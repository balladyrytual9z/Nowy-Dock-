package pl.dock.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.LruCache;

/**
 * Jedno zrodlo prawdy dla ikon rysowanych kodem (Wi-Fi, hotspot, Bluetooth i reszta).
 */
public final class Glyphs {

    private Glyphs() {
    }

    private static final Paint GP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PA = new Path();
    private static final RectF RC = new RectF();

    private static void head(Canvas cv, float x, float y, float dirDeg, float len, Paint p) {
        double a = Math.toRadians(dirDeg);
        for (int k = -1; k <= 1; k += 2) {
            double b = a + Math.PI + k * 0.6;
            cv.drawLine(x, y, x + (float) (Math.cos(b) * len), y + (float) (Math.sin(b) * len), p);
        }
    }

    private static void speaker(Canvas cv, float cx, float cy, float s, float off, Paint p) {
        Path pa = PA;
        pa.reset();
        pa.moveTo(cx + off - s * 0.9f, cy - s * 0.4f);
        pa.lineTo(cx + off - s * 0.35f, cy - s * 0.4f);
        pa.lineTo(cx + off + s * 0.2f, cy - s * 0.9f);
        pa.lineTo(cx + off + s * 0.2f, cy + s * 0.9f);
        pa.lineTo(cx + off - s * 0.35f, cy + s * 0.4f);
        pa.lineTo(cx + off - s * 0.9f, cy + s * 0.4f);
        pa.close();
        Paint.Style old = p.getStyle();
        p.setStyle(Paint.Style.FILL);
        cv.drawPath(pa, p);
        p.setStyle(old);
    }

    private static void wifiIos(Canvas cv, float cx, float cy, float pol, int color, boolean off) {
        Paint p = GP;
        RectF rc = RC;
        float w = Math.max(2.2f, pol * 0.22f);
        float cyA = cy + pol * 0.02f;
        float r0 = pol * 0.28f, r1 = pol * 0.55f, r2 = pol * 0.86f;
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(w);
        float start = 210f, sweep = 120f;
        for (float R : new float[]{r2, r1, r0}) {
            rc.set(cx - R, cyA - R, cx + R, cyA + R);
            cv.drawArc(rc, start, sweep, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(cx, cyA + r0 * 0.95f, w * 0.95f, p);
        if (off) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w);
            float d = pol * 0.95f;
            cv.drawLine(cx - d * 0.72f, cyA - d * 0.72f, cx + d * 0.72f, cyA + d * 0.72f, p);
        }
    }

    private static void btIos(Canvas cv, float cx, float cy, float s, Paint p, float sw) {
        Paint.Style oldStyle = p.getStyle();
        float oldW = p.getStrokeWidth();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw * 1.25f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path pa = PA;
        pa.reset();
        pa.moveTo(cx, cy - s);
        pa.lineTo(cx, cy + s);
        pa.moveTo(cx - s * 0.52f, cy - s * 0.55f);
        pa.lineTo(cx, cy);
        pa.lineTo(cx - s * 0.52f, cy + s * 0.55f);
        pa.moveTo(cx, cy - s);
        pa.lineTo(cx + s * 0.58f, cy - s * 0.38f);
        pa.lineTo(cx, cy);
        pa.moveTo(cx, cy);
        pa.lineTo(cx + s * 0.58f, cy + s * 0.38f);
        pa.lineTo(cx, cy + s);
        cv.drawPath(pa, p);
        pa.reset();
        p.setStrokeWidth(oldW);
        p.setStyle(oldStyle);
    }

    public static void draw(Canvas cv, String g, float cx, float cy, float r, int color) {
        Paint p = GP;
        RectF rc = RC;
        Path pa = PA;
        pa.reset();
        float s = r * 0.46f;
        float sw = Math.max(2f, r * 0.095f);
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        if (g == null) g = "gear";

        if ("wifi".equals(g) || "wifi_off".equals(g)) {
            wifiIos(cv, cx, cy, s, color, "wifi_off".equals(g));
            return;
        }
        if ("bt".equals(g)) {
            btIos(cv, cx, cy, s, p, sw);
            return;
        }

        switch (g) {
            case "hot": {
                float hh = s * 0.34f;
                float aa = s * 0.58f;
                float off = aa * 0.72f;
                float oldSw = p.getStrokeWidth();
                p.setStrokeWidth(Math.max(2f, sw * 0.85f));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                rc.set(cx - off - aa, cy - hh, cx - off + aa, cy + hh);
                cv.drawRoundRect(rc, hh, hh, p);
                rc.set(cx + off - aa, cy - hh, cx + off + aa, cy + hh);
                cv.drawRoundRect(rc, hh, hh, p);
                p.setStrokeWidth(oldSw);
                break;
            }
            case "air":
                pa.moveTo(cx, cy - s);
                pa.lineTo(cx + s * 0.15f, cy - s * 0.45f);
                pa.lineTo(cx + s * 0.95f, cy + s * 0.1f);
                pa.lineTo(cx + s * 0.95f, cy + s * 0.32f);
                pa.lineTo(cx + s * 0.15f, cy + s * 0.02f);
                pa.lineTo(cx + s * 0.28f, cy + s * 0.68f);
                pa.lineTo(cx + s * 0.28f, cy + s * 0.88f);
                pa.lineTo(cx, cy + s * 0.6f);
                pa.lineTo(cx - s * 0.28f, cy + s * 0.88f);
                pa.lineTo(cx - s * 0.28f, cy + s * 0.68f);
                pa.lineTo(cx - s * 0.15f, cy + s * 0.02f);
                pa.lineTo(cx - s * 0.95f, cy + s * 0.32f);
                pa.lineTo(cx - s * 0.95f, cy + s * 0.1f);
                pa.lineTo(cx - s * 0.15f, cy - s * 0.45f);
                pa.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(pa, p);
                break;
            default:
                cv.drawCircle(cx, cy, s * 0.5f, p);
                break;
        }
    }

    public static Bitmap bitmap(String g, int size, int color) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        draw(cv, g, size / 2f, size / 2f, size * 0.42f, color);
        return b;
    }
}
