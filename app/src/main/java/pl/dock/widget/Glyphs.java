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
 *
 * Wczesniej te same ikony istnialy w dwoch rozjechanych wersjach: panel lacznosci
 * rysowal je tutaj (glyph), a dok pod zegarem bral je z res/drawable/ic_*.xml przez
 * Icons.vector(), ktory barwil je filtrem PorterDuff.SRC_IN. Na docelowym sprzecie
 * (ESSGOO MT8163 / Mali-T720 / Android 9) obrysowe wektory z przezroczystym
 * wypelnieniem po takim filtrze wychodzily puste - ikony znikaly.
 *
 * Teraz oba miejsca korzystaja z tej samej metody: draw() maluje glif na canvasie,
 * bitmap() robi z niego gotowa ikone (bez XML i bez filtrow koloru).
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

    /**
     * Wi-Fi w stylu iOS: trzy wspolsrodkowe luki z ZAOKRAGLONYMI koncami i kropka,
     * ciasny wachlarz (~130 stopni). Rysowane kodem, bo na tym GPU wektory XML
     * z przezroczystym wypelnieniem wychodza puste. "off" doklada przekreslenie.
     */
    private static void wifiIos(Canvas cv, float cx, float cy, float pol, int color, boolean off) {
        Paint p = GP;
        RectF rc = RC;
        float w = Math.max(2f, pol * 0.23f);            // grubosc luku
        float cyA = cy - pol * 0.10f;                   // srodek wachlarza (kropka nizej)
        float r0 = pol * 0.30f, r1 = pol * 0.58f, r2 = pol * 0.88f;
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w);
        float[] rs = {r2, r1, r0};
        for (float R : rs) {
            rc.set(cx - R, cyA - R, cx + R, cyA + R);
            cv.drawArc(rc, 205f, 130f, false, p);       // gorny wachlarz, symetrycznie do pionu
        }
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(cx, cyA + r0 * 1.05f, w * 0.92f, p);
        if (off) {
            p.setStyle(Paint.Style.STROKE);
            float d = pol * 0.94f;
            cv.drawLine(cx - d * 0.70f, cyA - d * 0.70f, cx + d * 0.70f, cyA + d * 0.70f, p);
        }
    }

    /**
     * Bluetooth w stylu iOS (SF Symbols): runo rysowane OBRYSAMI o zaokraglonych
     * koncowkach - pionowa linia i dwa zygzaki (lewy stromiejszy, prawy plaski),
     * dokladnie jak na referencyjnych screenach panelu lacznosci. Poprzednia
     * wersja to wypelniona sciezka z Material - wygladala nie na miejscu
     * miedzy obrysowymi glifami Wi-Fi i hotspotu.
     */
    private static void btIos(Canvas cv, float cx, float cy, float s, Paint p, float sw) {
        Paint.Style oldStyle = p.getStyle();
        float oldW = p.getStrokeWidth();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw * 1.15f);
        Path pa = PA;
        pa.reset();
        pa.moveTo(cx, cy - s);
        pa.lineTo(cx, cy + s);
        pa.moveTo(cx, cy - s * 0.24f);
        pa.lineTo(cx - s * 0.50f, cy - s * 0.63f);
        pa.lineTo(cx, cy);
        pa.lineTo(cx - s * 0.50f, cy + s * 0.63f);
        pa.lineTo(cx, cy + s * 0.24f);
        pa.moveTo(cx, cy - s);
        pa.lineTo(cx + s * 0.54f, cy - s * 0.43f);
        pa.lineTo(cx + s * 0.17f, cy);
        pa.lineTo(cx + s * 0.54f, cy + s * 0.43f);
        pa.lineTo(cx, cy + s);
        cv.drawPath(pa, p);
        pa.reset();
        p.setStrokeWidth(oldW);
        p.setStyle(oldStyle);
    }

    /** Rysuje ikone o danym kodzie w okregu o promieniu r wokol punktu (cx, cy). */
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

        // Ikony lacznosci maja jeden wzorzec dla calej aplikacji (panel, dok, widzet):
        // Wi-Fi towachlarz z zaokraglonymi lukami, Bluetooth to obrysowe runo iOS.
        if ("wifi".equals(g) || "wifi_off".equals(g)) {
            wifiIos(cv, cx, cy, s, color, "wifi_off".equals(g));
            return;
        }
        if ("bt".equals(g)) {
            btIos(cv, cx, cy, s, p, sw);
            return;
        }

        switch (g) {
            case "air":
                // wypelniona sylwetka samolotu (jak w referencji): nos, skrzydla, stateczniki
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
            case "hot": {
                // ogniwa lancucha jak w ikonie tetheringu: dwie poziome kapsulki
                // zachodzace na siebie, prawa na wierzchu, obrysy bez przerw
                float hh = s * 0.3f;      // pol wysokosci ogniwa (dl. do wys. ~2:1)
                float aa = s * 0.62f;     // pol dlugosci ogniwa
                float off = aa * 0.6f;   // przesuniecie srodkow (zachodza na srodku)
                float oldSw = p.getStrokeWidth();
                p.setStrokeWidth(sw * 0.72f);
                rc.set(cx - off - aa, cy - hh, cx - off + aa, cy + hh);
                cv.drawRoundRect(rc, hh, hh, p);   // lewe ogniwo
                rc.set(cx + off - aa, cy - hh, cx + off + aa, cy + hh);
                cv.drawRoundRect(rc, hh, hh, p);   // prawe ogniwo na wierzchu
                p.setStrokeWidth(oldSw);
                break;
            }
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
                rc.set(cx - s * 0.2f, cy - s * 0.95f, cx + s * 0.85f, cy + s * 0.95f);
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
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        cv.drawCircle(cx + i * s * 0.62f, cy + j * s * 0.62f, sw * 0.75f, p);
                    }
                }
                break;
            case "storage":
                rc.set(cx - s, cy - s * 0.55f, cx + s, cy + s * 0.55f);
                cv.drawRoundRect(rc, s * 0.25f, s * 0.25f, p);
                cv.drawLine(cx - s, cy + s * 0.1f, cx + s, cy + s * 0.1f, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx + s * 0.6f, cy + s * 0.33f, sw * 0.6f, p);
                break;
            case "battery":
                rc.set(cx - s, cy - s * 0.5f, cx + s * 0.8f, cy + s * 0.5f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                cv.drawLine(cx + s, cy - s * 0.2f, cx + s, cy + s * 0.2f, p);
                p.setStyle(Paint.Style.FILL);
                rc.set(cx - s * 0.75f, cy - s * 0.25f, cx - s * 0.1f, cy + s * 0.25f);
                cv.drawRect(rc, p);
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
                p.setStyle(Paint.Style.FILL);
                for (int i = -2; i <= 2; i++) {
                    cv.drawCircle(cx + i * s * 0.38f, cy - s * 0.25f, sw * 0.45f, p);
                }
                for (int i = -1; i <= 1; i++) {
                    cv.drawCircle(cx + i * s * 0.38f, cy + s * 0.02f, sw * 0.45f, p);
                }
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(cx - s * 0.5f, cy + s * 0.3f, cx + s * 0.5f, cy + s * 0.3f, p);
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
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(ox, cy, sw * 0.7f, p);
                break;
            }
            case "cast":
                rc.set(cx - s, cy - s * 0.8f, cx + s, cy + s * 0.8f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                for (int i = 1; i <= 3; i++) {
                    float R = s * 0.4f * i;
                    rc.set(cx - s - R, cy + s * 0.8f - R, cx - s + R, cy + s * 0.8f + R);
                    cv.drawArc(rc, -90, 90, false, p);
                }
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
                pa.moveTo(cx + s * 0.15f, cy - s * 0.7f);
                pa.lineTo(cx - s * 0.15f, cy + s * 0.7f);
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
                double e1 = Math.toRadians(340);
                head(cv, cx + R * (float) Math.cos(e1), cy + R * (float) Math.sin(e1), 70, s * 0.45f, p);
                double e2 = Math.toRadians(160);
                head(cv, cx + R * (float) Math.cos(e2), cy + R * (float) Math.sin(e2), 250, s * 0.45f, p);
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
                cv.drawLine(cx - s * 0.75f, cy - s * 0.2f, cx - s * 0.95f, cy + s * 0.5f, p);
                cv.drawLine(cx + s * 0.75f, cy - s * 0.2f, cx + s * 0.95f, cy + s * 0.5f, p);
                cv.drawLine(cx - s * 0.95f, cy + s * 0.5f, cx + s * 0.95f, cy + s * 0.5f, p);
                rc.set(cx - s * 0.2f, cy + s * 0.6f, cx + s * 0.2f, cy + s * 0.95f);
                cv.drawArc(rc, 0, 180, false, p);
                break;
            case "star":
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + 36 * i);
                    float rad = (i % 2 == 0) ? s * 1.05f : s * 0.45f;
                    float x = cx + (float) Math.cos(a) * rad;
                    float y = cy + (float) Math.sin(a) * rad;
                    if (i == 0) pa.moveTo(x, y);
                    else pa.lineTo(x, y);
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
            case "image":
                rc.set(cx - s, cy - s * 0.8f, cx + s, cy + s * 0.8f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx + s * 0.45f, cy - s * 0.3f, s * 0.18f, p);
                p.setStyle(Paint.Style.STROKE);
                pa.moveTo(cx - s * 0.8f, cy + s * 0.6f);
                pa.lineTo(cx - s * 0.2f, cy);
                pa.lineTo(cx + s * 0.2f, cy + s * 0.4f);
                pa.lineTo(cx + s * 0.5f, cy + s * 0.15f);
                pa.lineTo(cx + s * 0.8f, cy + s * 0.6f);
                cv.drawPath(pa, p);
                break;
            default: // "gear" i nieznane
                cv.drawCircle(cx, cy, s * 0.3f, p);
                cv.drawCircle(cx, cy, s * 0.62f, p);
                p.setStrokeWidth(sw * 1.5f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    cv.drawLine(cx + (float) Math.cos(a) * s * 0.7f, cy + (float) Math.sin(a) * s * 0.7f,
                            cx + (float) Math.cos(a) * s * 1.0f, cy + (float) Math.sin(a) * s * 1.0f, p);
                }
                break;
        }
    }
    // ---------------------------------------------------------------- glif jako bitmapa

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(4 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap b) {
                    return b.getByteCount();
                }
            };

    private static final Paint FILTER =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    /**
     * Glif jako bitmapa size x size w podanym kolorze, przycieta do widocznej
     * zawartosci i wysrodkowana - dzieki temu kazdy glif wypelnia pole tak samo,
     * niezaleznie od tego, jak duzo miejsca zajmuje w swoim ukladzie wspolrzednych.
     */
    public static Bitmap bitmap(String glyph, int size, int color) {
        String key = glyph + "|" + size + "|" + color;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Bitmap out = null;
        try {
            // rysujemy z zapasem (3x), potem przycinamy i skalujemy - krawedzie
            // sa wygladzone, a ksztalt nie jest obcinany
            int big = Math.max(size * 3, 96);
            Bitmap tmp = Bitmap.createBitmap(big, big, Bitmap.Config.ARGB_8888);
            draw(new Canvas(tmp), glyph, big / 2f, big / 2f, big / 2f, color);
            int[] b = bounds(tmp);
            if (b != null) {
                int w = Math.min(b[2] - b[0] + 1, big - b[0]);
                int h = Math.min(b[3] - b[1] + 1, big - b[1]);
                Bitmap crop = Bitmap.createBitmap(tmp, b[0], b[1], w, h);
                out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                float k = size / (float) Math.max(w, h);
                Matrix m = new Matrix();
                m.postScale(k, k);
                m.postTranslate((size - w * k) / 2f, (size - h * k) / 2f);
                new Canvas(out).drawBitmap(crop, m, FILTER);
            }
        } catch (Throwable ignored) {
        }
        if (out == null) out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        CACHE.put(key, out);
        return out;
    }

    // widoczna zawartosc bitmapy: {minX, minY, maxX, maxY} albo null gdy pusta
    private static int[] bounds(Bitmap b) {
        int w = b.getWidth();
        int h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 16) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        return maxX < 0 ? null : new int[]{minX, minY, maxX, maxY};
    }
}
