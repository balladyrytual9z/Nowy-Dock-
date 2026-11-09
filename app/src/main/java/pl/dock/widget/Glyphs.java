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
        // WZORZEC 1:1 z SVG (viewBox 0 0 100 100): kropka w (50,85), trzy luki
        // r=20/40/60 o srodku w kropce, KAZDY rozwarty 90 stopni (+/-45 od
        // pionu), stroke 8, round caps. Przeliczenie: przy bitmapFull(96)
        // pol = s = 37.5px, 1 jedn. viewBox = 0.96px, wiec t = 8*0.96 =
        // 0.2048*pol, ay = 85*0.96 -> cy + 0.896*pol, promienie 2.5/5/7.5*t.
        Paint p = GP;
        RectF rc = RC;
        float t = pol * 0.2048f;                       // grubosc pasa (stroke 8 wzorca)
        float ay = cy + pol * 0.896f;                  // wierzcholek wachlarza (50,85 wzorca)
        float[] rr = {2.5f * t, 5f * t, 7.5f * t};     // promienie 20/40/60 wzorca
        float h = 45f;                                 // polowa rozwarcia KAZDEGO pasma (90 st.)
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(t);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 3; i++) {
            rc.set(cx - rr[i], ay - rr[i], cx + rr[i], ay + rr[i]);
            cv.drawArc(rc, 270f - h, 2f * h, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(cx, ay, t / 2f, p);
        if (off) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(2.2f, pol * 0.16f));
            float dd = pol * 0.95f;
            cv.drawLine(cx - dd * 0.72f, cy - dd * 0.72f, cx + dd * 0.72f, cy + dd * 0.72f, p);
        }
    }

    private static void btIos(Canvas cv, float cx, float cy, float s, Paint p, float sw) {
        // WZORZEC 1:1 z SVG (viewBox 0 0 100 100): JEDNA otwarta polilinia
        // M 30,70 L 70,30 L 50,10 L 50,90 L 70,70 L 30,30, stroke 6,
        // round cap + round join. To oficjalna (asymetryczna) runa Bluetooth:
        // X krzyzujacy sie na srodku (dosieg +/-20 jedn. = 0.5s), pion na
        // cala wysokosc, a PRAWE koncy X lacza sie z koncami pionu; lewe
        // koncy X sa wolne. Grubosc: 6/80 wysokosci = 0.15*s.
        Paint.Style oldStyle = p.getStyle();
        float oldW = p.getStrokeWidth();
        Paint.Cap oldCap = p.getStrokeCap();
        Paint.Join oldJoin = p.getStrokeJoin();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, s * 0.15f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path pa = PA;
        pa.reset();
        pa.moveTo(cx - 0.5f * s, cy + 0.5f * s);       // (30,70) dolny-lewy koniec X
        pa.lineTo(cx + 0.5f * s, cy - 0.5f * s);       // (70,30) gorny-prawy koniec X
        pa.lineTo(cx, cy - s);                         // (50,10) gora pionu
        pa.lineTo(cx, cy + s);                         // (50,90) dol pionu
        pa.lineTo(cx + 0.5f * s, cy + 0.5f * s);       // (70,70) dolny-prawy koniec X
        pa.lineTo(cx - 0.5f * s, cy - 0.5f * s);       // (30,30) gorny-lewy koniec X
        cv.drawPath(pa, p);
        pa.reset();
        p.setStrokeJoin(oldJoin);
        p.setStrokeCap(oldCap);
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
                // Tethering - WZORZEC zmierzony maszynowo z bitmapowego trace
                // wzorca (1106x521): dwa ogniwa-stadium (kapsuly) rownej
                // wielkosci, kontur zewnetrzny 701x391 jedn., grubosc pasa 85
                // (~22% wysokosci ogniwa). Srodki ogniw co dx=398.5, dy=57
                // (prawe ogniwo NIZEJ, jak w prawdziwym lancuchu). Lewe ogniwo
                // na wierzchu w calosci; prawe przeciete wzdluz konturu lewego
                // (clipOutPath, szczelina gap ~ 0.03H) - tip prawego wchodzi w
                // otwor lewego ogniwa. Jednostka H = wysokosc ogniwa = 0.48*s.
                float H = s * 0.48f;                  // wysokosc ogniwa
                float st = H * 0.2174f;               // grubosc pasa ogniwa
                float L = H * 1.7923f;                // dlugosc ogniwa
                float dx = H * 1.0192f;               // rozstaw srodkow X
                float dy = H * 0.1458f;               // rozstaw srodkow Y (prawe nizej)
                float gap = H * 0.03f;                  // szczelina przy przecieciu
                float cw = L - st, chh = H - st;      // wymiary srodnicy (linii konturu)
                float cr = chh * 0.5f;                // promien kapsuly srodnicy
                float ax = cx - dx * 0.5f, ayc = cy - dy * 0.5f;   // ogniwo A (lewe, wyzej)
                float bx = cx + dx * 0.5f, byc = cy + dy * 0.5f;   // ogniwo B (prawe, nizej)
                RectF rA = new RectF(ax - cw * 0.5f, ayc - chh * 0.5f, ax + cw * 0.5f, ayc + chh * 0.5f);
                RectF rB = new RectF(bx - cw * 0.5f, byc - chh * 0.5f, bx + cw * 0.5f, byc + chh * 0.5f);
                // obszar pasa A powiekszony o gap (do wyciecia w B): brzeg
                // zewnetrzny = srodnica + (st/2 + gap), brzeg otworu = srodnica - (st/2 - gap)
                RectF oA = new RectF(rA);
                oA.inset(-(st * 0.5f + gap), -(st * 0.5f + gap));
                RectF iA = new RectF(rA);
                iA.inset(st * 0.5f - gap, st * 0.5f - gap);
                Path bandA = new Path();
                bandA.addRoundRect(oA, chh * 0.5f + st * 0.5f + gap, chh * 0.5f + st * 0.5f + gap, Path.Direction.CW);
                Path holeA = new Path();
                holeA.addRoundRect(iA, chh * 0.5f - st * 0.5f + gap, chh * 0.5f - st * 0.5f + gap, Path.Direction.CW);
                bandA.op(holeA, Path.Op.DIFFERENCE);
                float oldSw = p.getStrokeWidth();
                Paint.Cap oldCap = p.getStrokeCap();
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(Math.max(2f, st));
                Path pB = new Path();
                pB.addRoundRect(rB, cr, cr, Path.Direction.CW);
                cv.save();
                cv.clipOutPath(bandA);                // minSdk 26
                cv.drawPath(pB, p);
                cv.restore();
                Path pA = new Path();
                pA.addRoundRect(rA, cr, cr, Path.Direction.CW);
                cv.drawPath(pA, p);
                p.setStrokeCap(oldCap);
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
