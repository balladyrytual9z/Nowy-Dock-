package pl.dock.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.LruCache;

public final class Glyphs {

    private Glyphs() {}

    private static final Paint GP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PA = new Path();
    private static final RectF RC = new RectF();
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(64);

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
        // Referencja uzytkownika (zdjecia): klasyczny wypelniony "pie" iOS -
        // trzy pasma narastajace od dolu + pelne kolko u dolu. Nie konturowe.
        Paint p = GP;
        Path pa = PA;
        RectF rc = RC;
        float ax = cx;
        float ay = cy + pol * 0.62f;                                  // wierzcholek wachlarza
        float dot = pol * 0.16f;                                       // promien kolka u dolu
        float g = pol * 0.08f;                                         // szczelina miedzy pasmami
        float t = pol * 0.42f;                                         // grubosc pasma (wypelniony pie jak na referencji)
        float[] rr = {dot + g + t / 2f, dot + 2f * g + 3f * t / 2f, dot + 3f * g + 5f * t / 2f};
        float[] hh = {22f, 40f, 58f};                                 // polowy rozwarcia pasm
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) {
            float rin = rr[i] - t / 2f, rout = rr[i] + t / 2f;
            float h = hh[i];
            double a0 = Math.toRadians(270f - h), a1 = Math.toRadians(270f + h);
            pa.reset();
            pa.moveTo(ax + (float) (rin * Math.cos(a0)), ay + (float) (rin * Math.sin(a0)));
            pa.lineTo(ax + (float) (rout * Math.cos(a0)), ay + (float) (rout * Math.sin(a0)));
            rc.set(ax - rout, ay - rout, ax + rout, ay + rout);
            pa.arcTo(rc, 270f - h, 2f * h, false);
            pa.lineTo(ax + (float) (rin * Math.cos(a1)), ay + (float) (rin * Math.sin(a1)));
            rc.set(ax - rin, ay - rin, ax + rin, ay + rin);
            pa.arcTo(rc, 270f + h, -2f * h, false);
            pa.close();
            cv.drawPath(pa, p);
        }
        cv.drawCircle(ax, ay, dot, p);
        if (off) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(2.2f, pol * 0.16f));
            float dd = pol * 0.95f;
            cv.drawLine(cx - dd * 0.72f, cy - dd * 0.72f, cx + dd * 0.72f, cy + dd * 0.72f, p);
        }
    }

    private static void btIos(Canvas cv, float cx, float cy, float s, Paint p, float sw) {
        // Referencja: gorny trojkat wezszy (mniejszy), dolny szerszy (wiekszy),
        // ramiona X po lewej wystaja poza oba trojkaty.
        Paint.Style oldStyle = p.getStyle();
        float oldW = p.getStrokeWidth();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, sw * 1.3f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path pa = PA;
        pa.reset();
        pa.moveTo(cx, cy - s);                        // pion
        pa.lineTo(cx, cy + s);
        pa.moveTo(cx - s * 0.66f, cy - s * 0.34f);     // ramiona X (wystaja w lewo)
        pa.lineTo(cx, cy);
        pa.lineTo(cx - s * 0.66f, cy + s * 0.34f);
        pa.moveTo(cx, cy - s);                         // trojkat gorny - mniejszy
        pa.lineTo(cx + s * 0.40f, cy - s * 0.62f);
        pa.lineTo(cx, cy - s * 0.04f);
        pa.moveTo(cx, cy + s * 0.04f);                 // trojkat dolny - wiekszy
        pa.lineTo(cx + s * 0.62f, cy + s * 0.40f);
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
                // Referencja: ogniwo lancucha - dwa zaczepione PIONOWE owale;
                // lewy przebiega przez srodek prawego (widoczne "S" jak na zdjeciu)
                float lh = s * 0.62f;      // pol-wysokosc ogniwa
                float lw = s * 0.42f;      // pol-szerokosc ogniwa
                float off = s * 0.30f;     // wzajemne przesuniecie
                float oldSw = p.getStrokeWidth();
                p.setStrokeWidth(Math.max(2f, sw * 1.1f));
                rc.set(cx + off - lw, cy - lh, cx + off + lw, cy + lh);
                cv.drawRoundRect(rc, lw, lw, p);
                rc.set(cx - off - lw, cy - lh, cx - off + lw, cy + lh);
                cv.drawRoundRect(rc, lw, lw, p);
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
            case "rotate": {
                float R = s * 0.95f;
                rc.set(cx - R, cy - R, cx + R, cy + R);
                cv.drawArc(rc, -50, 270, false, p);
                double e = Math.toRadians(220);
                head(cv, cx + R * (float) Math.cos(e), cy + R * (float) Math.sin(e), 310, s * 0.5f, p);
                rc.set(cx - s * 0.32f, cy - s * 0.02f, cx + s * 0.32f, cy + s * 0.5f);
                cv.drawRoundRect(rc, s * 0.1f, s * 0.1f, p);
                rc.set(cx - s * 0.2f, cy - s * 0.4f, cx + s * 0.2f, cy + s * 0.1f);
                cv.drawArc(rc, 180, 180, false, p);
                break;
            }
            case "speaker":
                speaker(cv, cx, cy, s, -s * 0.25f, p);
                rc.set(cx - s * 0.45f, cy - s * 0.28f, cx + s * 0.35f, cy + s * 0.28f);
                cv.drawArc(rc, -50, 100, false, p);
                rc.set(cx - s * 0.35f, cy - s * 0.55f, cx + s * 0.45f, cy + s * 0.55f);
                cv.drawArc(rc, -50, 100, false, p);
                break;
            case "mute":
                speaker(cv, cx, cy, s, -s * 0.35f, p);
                cv.drawLine(cx + s * 0.3f, cy - s * 0.35f, cx + s * 0.95f, cy + s * 0.35f, p);
                cv.drawLine(cx + s * 0.3f, cy + s * 0.35f, cx + s * 0.95f, cy - s * 0.35f, p);
                break;
            case "loc": {
                float R = s * 0.62f;
                float py = cy - s * 0.3f;
                pa.moveTo(cx + R * 0.87f, py + R * 0.5f);
                pa.lineTo(cx, cy + s);
                pa.lineTo(cx - R * 0.87f, py + R * 0.5f);
                cv.drawPath(pa, p);
                rc.set(cx - R, py - R, cx + R, py + R);
                cv.drawArc(rc, 150, 240, false, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, py, sw * 0.8f, p);
                break;
            }
            case "flash":
                rc.set(cx - s * 0.55f, cy - s, cx + s * 0.55f, cy - s * 0.35f);
                cv.drawRoundRect(rc, s * 0.15f, s * 0.15f, p);
                rc.set(cx - s * 0.35f, cy - s * 0.35f, cx + s * 0.35f, cy + s);
                cv.drawRoundRect(rc, s * 0.15f, s * 0.15f, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy + s * 0.1f, sw * 0.7f, p);
                break;
            case "sun":
                cv.drawCircle(cx, cy, s * 0.42f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    cv.drawLine(cx + (float) Math.cos(a) * s * 0.72f, cy + (float) Math.sin(a) * s * 0.72f,
                            cx + (float) Math.cos(a) * s * 1.02f, cy + (float) Math.sin(a) * s * 1.02f, p);
                }
                break;
            case "moon": {
                Path m = new Path();
                m.addCircle(cx, cy, s, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(cx + s * 0.55f, cy - s * 0.4f, s * 0.85f, Path.Direction.CW);
                m.op(cut, Path.Op.DIFFERENCE);
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(m, p);
                break;
            }
            case "half":
                rc.set(cx - s, cy - s, cx + s, cy + s);
                cv.drawCircle(cx, cy, s, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawArc(rc, 90, 180, true, p);
                break;
            case "apps":
                p.setStyle(Paint.Style.FILL);
                for (int i = -1; i <= 1; i++)
                    for (int j = -1; j <= 1; j++)
                        cv.drawCircle(cx + i * s * 0.62f, cy + j * s * 0.62f, sw * 0.75f, p);
                break;
            case "storage":
                rc.set(cx - s, cy - s * 0.55f, cx + s, cy + s * 0.55f);
                cv.drawRoundRect(rc, s * 0.25f, s * 0.25f, p);
                cv.drawLine(cx - s, cy + s * 0.1f, cx + s, cy + s * 0.1f, p);
                break;
            case "battery":
                rc.set(cx - s, cy - s * 0.5f, cx + s * 0.8f, cy + s * 0.5f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                cv.drawLine(cx + s, cy - s * 0.2f, cx + s, cy + s * 0.2f, p);
                break;
            case "clock":
                cv.drawCircle(cx, cy, s, p);
                cv.drawLine(cx, cy, cx, cy - s * 0.6f, p);
                cv.drawLine(cx, cy, cx + s * 0.45f, cy + s * 0.2f, p);
                break;
            case "globe":
                cv.drawCircle(cx, cy, s, p);
                rc.set(cx - s * 0.45f, cy - s, cx + s * 0.45f, cy + s);
                cv.drawOval(rc, p);
                cv.drawLine(cx - s, cy, cx + s, cy, p);
                break;
            case "keyboard":
                rc.set(cx - s, cy - s * 0.6f, cx + s, cy + s * 0.6f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                break;
            case "access":
                cv.drawCircle(cx, cy - s * 0.75f, s * 0.25f, p);
                cv.drawLine(cx - s * 0.8f, cy - s * 0.2f, cx + s * 0.8f, cy - s * 0.2f, p);
                cv.drawLine(cx, cy - s * 0.2f, cx, cy + s * 0.3f, p);
                cv.drawLine(cx, cy + s * 0.3f, cx - s * 0.5f, cy + s, p);
                cv.drawLine(cx, cy + s * 0.3f, cx + s * 0.5f, cy + s, p);
                break;
            case "nfc": {
                float ox = cx - s * 0.7f;
                for (int i = 1; i <= 3; i++) {
                    float R = s * 0.5f * i;
                    rc.set(ox - R, cy - R, ox + R, cy + R);
                    cv.drawArc(rc, -40, 80, false, p);
                }
                break;
            }
            case "cast":
                rc.set(cx - s, cy - s * 0.8f, cx + s, cy + s * 0.8f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                break;
            case "data":
                cv.drawLine(cx - s * 0.45f, cy + s * 0.8f, cx - s * 0.45f, cy - s * 0.8f, p);
                head(cv, cx - s * 0.45f, cy - s * 0.8f, 270, s * 0.45f, p);
                cv.drawLine(cx + s * 0.45f, cy - s * 0.8f, cx + s * 0.45f, cy + s * 0.8f, p);
                head(cv, cx + s * 0.45f, cy + s * 0.8f, 90, s * 0.45f, p);
                break;
            case "lock":
                rc.set(cx - s * 0.7f, cy - s * 0.1f, cx + s * 0.7f, cy + s * 0.9f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                rc.set(cx - s * 0.45f, cy - s * 0.95f, cx + s * 0.45f, cy + s * 0.15f);
                cv.drawArc(rc, 180, 180, false, p);
                break;
            case "code":
                pa.moveTo(cx - s * 0.3f, cy - s * 0.6f);
                pa.lineTo(cx - s * 0.9f, cy);
                pa.lineTo(cx - s * 0.3f, cy + s * 0.6f);
                pa.moveTo(cx + s * 0.3f, cy - s * 0.6f);
                pa.lineTo(cx + s * 0.9f, cy);
                pa.lineTo(cx + s * 0.3f, cy + s * 0.6f);
                cv.drawPath(pa, p);
                break;
            case "info":
                cv.drawCircle(cx, cy, s, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy - s * 0.42f, sw * 0.7f, p);
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(cx, cy - s * 0.05f, cx, cy + s * 0.55f, p);
                break;
            case "sync": {
                float R = s * 0.9f;
                rc.set(cx - R, cy - R, cx + R, cy + R);
                cv.drawArc(rc, 200, 140, false, p);
                cv.drawArc(rc, 20, 140, false, p);
                break;
            }
            case "user":
                cv.drawCircle(cx, cy - s * 0.45f, s * 0.38f, p);
                rc.set(cx - s * 0.8f, cy + s * 0.1f, cx + s * 0.8f, cy + s * 1.5f);
                cv.drawArc(rc, 180, 180, false, p);
                break;
            case "antenna": {
                float dy = cy + s * 0.6f;
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, dy, sw * 0.9f, p);
                p.setStyle(Paint.Style.STROKE);
                for (int i = 1; i <= 2; i++) {
                    float R = s * 0.65f * i;
                    rc.set(cx - R, dy - R, cx + R, dy + R);
                    cv.drawArc(rc, 230, 80, false, p);
                }
                break;
            }
            case "bell":
                rc.set(cx - s * 0.75f, cy - s * 0.95f, cx + s * 0.75f, cy + s * 0.55f);
                cv.drawArc(rc, 180, 180, false, p);
                cv.drawLine(cx - s * 0.95f, cy + s * 0.5f, cx + s * 0.95f, cy + s * 0.5f, p);
                break;
            case "star":
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + 36 * i);
                    float rad = (i % 2 == 0) ? s * 1.05f : s * 0.45f;
                    float x = cx + (float) Math.cos(a) * rad;
                    float y = cy + (float) Math.sin(a) * rad;
                    if (i == 0) pa.moveTo(x, y); else pa.lineTo(x, y);
                }
                pa.close();
                cv.drawPath(pa, p);
                break;
            case "home":
                pa.moveTo(cx - s, cy);
                pa.lineTo(cx, cy - s);
                pa.lineTo(cx + s, cy);
                pa.moveTo(cx - s * 0.7f, cy - s * 0.1f);
                pa.lineTo(cx - s * 0.7f, cy + s * 0.9f);
                pa.lineTo(cx + s * 0.7f, cy + s * 0.9f);
                pa.lineTo(cx + s * 0.7f, cy - s * 0.1f);
                cv.drawPath(pa, p);
                break;
            case "gear":
            default:
                cv.drawCircle(cx, cy, s * 0.55f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    cv.drawLine(cx + (float) Math.cos(a) * s * 0.7f, cy + (float) Math.sin(a) * s * 0.7f,
                            cx + (float) Math.cos(a) * s, cy + (float) Math.sin(a) * s, p);
                }
                break;
        }
    }

    public static Bitmap bitmap(String g, int size, int color) {
        String key = g + "|" + size + "|" + color;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        draw(cv, g, size / 2f, size / 2f, size * 0.42f, color);
        CACHE.put(key, b);
        return b;
    }

    /** Jak bitmap(), ale glif wypelnia kratke tak jak w panelu lacznosci
     *  (r = 0.85 * size, patrz ConnectivityPanel.glyphBitmap). Ikony pod zegarem
     *  w doku i w widzecie sa male (19 dp): przy r = 0.42 * size glif WiFi
     *  zajmowal ~35% kratki, pasma "pie" schodzily do ~1,5 dp i wygladaly jak
     *  cienkie linie zamiast grubego wypelnionego wachlarza jak na referencji.
     *  bitmapFull daje DOKLADNIE te same proporcje glifu co panel lacznosci -
     *  jedno zrodlo prawdy rowniez dla skali wypelnienia. */
    public static Bitmap bitmapFull(String g, int size, int color) {
        String key = "F|" + g + "|" + size + "|" + color;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        draw(cv, g, size / 2f, size / 2f, size * 0.85f, color);
        CACHE.put(key, b);
        return b;
    }
}
