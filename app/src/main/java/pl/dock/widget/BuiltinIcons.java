
package pl.dock.widget;

import android.graphics.Bitmap;
import android.content.Context;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff;
import android.graphics.BlurMaskFilter;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Locale;

// Wbudowane ikony w stylu CarPlay: ciemny kafelek + kolorowy symbol.
// Wszystkie rysowane kodem, bez plikÃ³w graficznych.
public class BuiltinIcons {

    public static class Def {
        public final String id;
        public final String label;

        Def(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    public static final Def[] ALL = {
            new Def("carplay", "CarPlay"),
            new Def("eq", "Equalizer / DSP"),
            new Def("bt", "Bluetooth"),
            new Def("files", "Pliki"),
            new Def("car_settings", "Ustawienia auta"),
            new Def("settings", "Ustawienia"),
            new Def("obd1", "OBD 1"),
            new Def("obd2", "OBD 2"),
            new Def("obd3", "OBD 3"),
            new Def("car", "SamochÃ³d"),
            new Def("phone", "Telefon"),
            new Def("music", "Muzyka"),
            new Def("radio", "Radio"),
            new Def("dab", "DAB+"),
            new Def("nav", "Nawigacja"),
            new Def("camera", "Kamera"),
            new Def("video", "Wideo"),
            new Def("update", "Aktualizacja"),
            new Def("mcu", "MCU / CAN"),
            new Def("swc", "Kierownica"),
            new Def("av", "WejÅcie AV"),
            new Def("clock", "Zegar"),
            new Def("browser", "PrzeglÄdarka"),
            new Def("info", "Informacje"),
            new Def("airdrop", "AirDrop"),
            new Def("appstore", "Sklep z aplikacjami"),
            new Def("obdgear", "OBD (zÄbatka z autem)"),
            new Def("honda", "Honda OBD")
    };

    private static Context appCtx;

    public static void init(Context c) {
        if (appCtx == null) appCtx = c.getApplicationContext();
    }

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLUE = 0xFF0A84FF;
    private static final int LBLUE = 0xFF5AC8FA;
    private static final int GREEN = 0xFF30D158;
    private static final int PINK = 0xFFFF375F;
    private static final int ORANGE = 0xFFFF9F0A;
    private static final int RED = 0xFFFF453A;
    private static final int PURPLE = 0xFFBF5AF2;
    private static final int GRAY = 0xFF8E8E93;
    private static final int DARK = 0xFF1F1F22;

    // ---------------------------------------------------------------- dobÃ³r po nazwie

    private static boolean has(String l, String... keys) {
        for (String k : keys) {
            if (l.contains(k)) return true;
        }
        return false;
    }

    // Zwraca id ikony pasujÄcej do nazwy aplikacji albo null ("obd" = do ponumerowania)
    public static String suggest(String label) {
        if (label == null) return null;
        String l = label.toLowerCase(Locale.US);
        if (has(l, "obd", "scanner", "carinfo", "torque")) return "obd";
        if (has(l, "diplay", "carplay", "zlink")) return "carplay";
        if (has(l, "localsend")) return "airdrop";
        if (has(l, "f-droid", "fdroid")) return "appstore";
        if (has(l, "mcu") || l.endsWith(" can")) return "mcu";
        if (has(l, "bluetooth")) return "bt";
        if (has(l, "dsp", "equalizer", "korektor")) return "eq";
        if (has(l, "ustawienia samochodu", "ustawienia auta")) return "car_settings";
        if (has(l, "pliki", "plikÃ³w", "files", "menedÅ¼er", "manager", "x-plore", "explorer"))
            return "files";
        if (has(l, "ustawienia", "settings")) return "settings";
        if (has(l, "aktualiz", "update")) return "update";
        if (has(l, "dab")) return "dab";
        if (has(l, "radio")) return "radio";
        if (has(l, "swc")) return "swc";
        if (has(l, "wejÅcie av", "av in")) return "av";
        if (has(l, "wygaszacz", "zegar", "clock")) return "clock";
        if (has(l, "telefon", "phone")) return "phone";
        if (has(l, "muzyka", "music", "spotify")) return "music";
        if (has(l, "waze", "mapy", "maps", "here", "organic", "nawigac")) return "nav";
        if (has(l, "wideo", "video", "netflix", "youtube")) return "video";
        if (has(l, "chrome", "brave", "przeglÄdark", "browser")) return "browser";
        if (has(l, "instruction", "instrukc", "info")) return "info";
        if (has(l, "kamera", "camera", "dashcam")) return "camera";
        if (has(l, "samoch", "auto", "car")) return "car";
        return null;
    }

    // ---------------------------------------------------------------- rysowanie

    private static Paint f(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private static Paint s(int color, float w) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    private static RectF rc(float u, float l, float t, float r, float b) {
        return new RectF(l * u, t * u, r * u, b * u);
    }

    private static void text(Canvas c, String s, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText(s, cx, cy - (p.descent() + p.ascent()) / 2f, p);
    }

    private static void gear(Canvas c, float u, int color, float holeR) {
        Paint g = f(color);
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45, 50 * u, 50 * u);
            c.drawRoundRect(rc(u, 44, 15, 56, 31), 2.5f * u, 2.5f * u, g);
            c.restore();
        }
        c.drawCircle(50 * u, 50 * u, 27 * u, g);
        c.drawCircle(50 * u, 50 * u, holeR * u, f(DARK));
    }

    private static void engine(Canvas c, float u, int color) {
        Paint e = f(color);
        c.drawRoundRect(rc(u, 30, 42, 68, 70), 5 * u, 5 * u, e);
        c.drawRoundRect(rc(u, 38, 34, 60, 43), 3 * u, 3 * u, e);
        c.drawRect(rc(u, 46, 27, 52, 35), e);
        c.drawRoundRect(rc(u, 20, 48, 31, 64), 2 * u, 2 * u, e);
        c.drawRect(rc(u, 13, 53, 20, 59), e);
        c.drawRoundRect(rc(u, 68, 46, 77, 66), 2 * u, 2 * u, e);
        c.drawRect(rc(u, 77, 52, 82, 60), e);
    }

    private static void glyph(Canvas c, String id, float u) {
        if ("carplay".equals(id)) {
            c.drawArc(rc(u, 24, 24, 76, 76), 40, 280, false, s(WHITE, 6.5f * u));
            Path t = new Path();
            t.moveTo(44 * u, 37 * u);
            t.lineTo(68 * u, 50 * u);
            t.lineTo(44 * u, 63 * u);
            t.close();
            c.drawPath(t, f(WHITE));
        } else if ("eq".equals(id)) {
            for (int i = 0; i < 3; i++) {
                float x = (30 + 20 * i) * u;
                c.drawLine(x, 24 * u, x, 76 * u, s(PINK, 5 * u));
                float ky = (i == 0 ? 40 : (i == 1 ? 60 : 34)) * u;
                c.drawRoundRect(new RectF(x - 9 * u, ky - 5.5f * u, x + 9 * u, ky + 5.5f * u),
                        4 * u, 4 * u, f(WHITE));
            }
        } else if ("bt".equals(id)) {
            Path p = new Path();
            p.moveTo(36 * u, 35 * u);
            p.lineTo(64 * u, 62 * u);
            p.lineTo(50 * u, 75 * u);
            p.lineTo(50 * u, 25 * u);
            p.lineTo(64 * u, 38 * u);
            p.lineTo(36 * u, 65 * u);
            c.drawPath(p, s(BLUE, 6 * u));
        } else if ("files".equals(id)) {
            c.drawRoundRect(rc(u, 20, 26, 46, 44), 5 * u, 5 * u, f(LBLUE));
            c.drawRoundRect(rc(u, 20, 34, 80, 72), 7 * u, 7 * u, f(BLUE));
        } else if ("settings".equals(id)) {
            gear(c, u, GRAY, 12);
            c.drawCircle(50 * u, 50 * u, 4 * u, f(GRAY));
        } else if ("car_settings".equals(id)) {
            gear(c, u, GRAY, 19);
            Path roof = new Path();
            roof.moveTo(38 * u, 52 * u);
            roof.lineTo(43 * u, 43 * u);
            roof.lineTo(57 * u, 43 * u);
            roof.lineTo(62 * u, 52 * u);
            roof.close();
            c.drawPath(roof, f(WHITE));
            c.drawRoundRect(rc(u, 34, 50, 66, 60), 3 * u, 3 * u, f(WHITE));
            c.drawCircle(42 * u, 60 * u, 3.2f * u, f(WHITE));
            c.drawCircle(58 * u, 60 * u, 3.2f * u, f(WHITE));
        } else if (id.startsWith("obd")) {
            int n = id.length() > 3 ? id.charAt(3) - '0' : 1;
            int col = n == 1 ? BLUE : (n == 2 ? ORANGE : GREEN);
            engine(c, u, col);
            c.drawCircle(76 * u, 27 * u, 15 * u, f(WHITE));
            text(c, String.valueOf(n), 76 * u, 27 * u, 22 * u, 0xFF1C1C1E);
        } else if ("car".equals(id)) {
            Paint e = f(BLUE);
            c.drawRoundRect(rc(u, 20, 52, 80, 70), 7 * u, 7 * u, e);
            Path cab = new Path();
            cab.moveTo(30 * u, 52 * u);
            cab.lineTo(38 * u, 36 * u);
            cab.lineTo(62 * u, 36 * u);
            cab.lineTo(70 * u, 52 * u);
            cab.close();
            c.drawPath(cab, e);
            Paint w = f(DARK);
            Path w1 = new Path();
            w1.moveTo(35 * u, 50 * u);
            w1.lineTo(41 * u, 40 * u);
            w1.lineTo(48 * u, 40 * u);
            w1.lineTo(48 * u, 50 * u);
            w1.close();
            c.drawPath(w1, w);
            Path w2 = new Path();
            w2.moveTo(52 * u, 50 * u);
            w2.lineTo(52 * u, 40 * u);
            w2.lineTo(59 * u, 40 * u);
            w2.lineTo(65 * u, 50 * u);
            w2.close();
            c.drawPath(w2, w);
            c.drawCircle(34 * u, 70 * u, 7 * u, e);
            c.drawCircle(66 * u, 70 * u, 7 * u, e);
            c.drawCircle(34 * u, 70 * u, 3 * u, w);
            c.drawCircle(66 * u, 70 * u, 3 * u, w);
        } else if ("phone".equals(id)) {
            c.save();
            c.rotate(-45, 50 * u, 50 * u);
            Paint g = f(GREEN);
            // Klasyczna sÅuchawka: korpus + dwie muszle
            c.drawRoundRect(rc(u, 44, 22, 56, 78), 6 * u, 6 * u, g);
            c.drawRoundRect(rc(u, 32, 16, 68, 34), 9 * u, 9 * u, g);
            c.drawRoundRect(rc(u, 32, 66, 68, 84), 9 * u, 9 * u, g);
            // WgÅÄbienie na gÅoÅnik (Årodkowa listwa)
            c.drawRect(rc(u, 46, 34, 54, 40), f(DARK));
            c.restore();
        } else if ("music".equals(id)) {
            Paint pk = f(PINK);
            c.drawOval(rc(u, 26, 60, 48, 76), pk);
            c.drawOval(rc(u, 54, 54, 76, 70), pk);
            Paint st = s(PINK, 5 * u);
            c.drawLine(46 * u, 68 * u, 46 * u, 28 * u, st);
            c.drawLine(74 * u, 62 * u, 74 * u, 22 * u, st);
            Path bm = new Path();
            bm.moveTo(46 * u, 28 * u);
            bm.lineTo(74 * u, 22 * u);
            bm.lineTo(74 * u, 33 * u);
            bm.lineTo(46 * u, 39 * u);
            bm.close();
            c.drawPath(bm, pk);
        } else if ("radio".equals(id)) {
            Paint st = s(BLUE, 5 * u);
            c.drawRoundRect(rc(u, 20, 38, 80, 74), 8 * u, 8 * u, st);
            c.drawCircle(37 * u, 56 * u, 9 * u, st);
            c.drawLine(56 * u, 50 * u, 70 * u, 50 * u, st);
            c.drawLine(56 * u, 61 * u, 70 * u, 61 * u, st);
            c.drawLine(30 * u, 38 * u, 66 * u, 24 * u, s(BLUE, 4 * u));
        } else if ("dab".equals(id)) {
            text(c, "DAB+", 50 * u, 50 * u, 26 * u, GREEN);
        } else if ("nav".equals(id)) {
            Path a = new Path();
            a.moveTo(50 * u, 20 * u);
            a.lineTo(73 * u, 77 * u);
            a.lineTo(50 * u, 65 * u);
            a.lineTo(27 * u, 77 * u);
            a.close();
            c.drawPath(a, f(BLUE));
        } else if ("camera".equals(id)) {
            c.drawRoundRect(rc(u, 40, 27, 60, 36), 3 * u, 3 * u, f(LBLUE));
            c.drawRoundRect(rc(u, 20, 33, 80, 71), 9 * u, 9 * u, f(LBLUE));
            c.drawCircle(50 * u, 52 * u, 13 * u, f(DARK));
            c.drawCircle(50 * u, 52 * u, 6 * u, f(LBLUE));
        } else if ("video".equals(id)) {
            c.drawRoundRect(rc(u, 20, 30, 80, 70), 9 * u, 9 * u, f(RED));
            Path t = new Path();
            t.moveTo(44 * u, 40 * u);
            t.lineTo(63 * u, 50 * u);
            t.lineTo(44 * u, 60 * u);
            t.close();
            c.drawPath(t, f(WHITE));
        } else if ("update".equals(id)) {
            Paint e = f(BLUE);
            c.drawCircle(36 * u, 58 * u, 11 * u, e);
            c.drawCircle(52 * u, 50 * u, 16 * u, e);
            c.drawCircle(68 * u, 58 * u, 11 * u, e);
            c.drawRect(rc(u, 36, 58, 68, 69), e);
            Paint st = s(WHITE, 5 * u);
            c.drawLine(52 * u, 66 * u, 52 * u, 46 * u, st);
            Path h = new Path();
            h.moveTo(43 * u, 54 * u);
            h.lineTo(52 * u, 45 * u);
            h.lineTo(61 * u, 54 * u);
            c.drawPath(h, st);
        } else if ("mcu".equals(id)) {
            Paint st = s(GREEN, 5 * u);
            c.drawRoundRect(rc(u, 30, 30, 70, 70), 5 * u, 5 * u, st);
            c.drawRect(rc(u, 42, 42, 58, 58), f(GREEN));
            for (int i = 0; i < 3; i++) {
                float p = (38 + 12 * i) * u;
                c.drawLine(p, 20 * u, p, 29 * u, st);
                c.drawLine(p, 71 * u, p, 80 * u, st);
                c.drawLine(20 * u, p, 29 * u, p, st);
                c.drawLine(71 * u, p, 80 * u, p, st);
            }
        } else if ("swc".equals(id)) {
            Paint st = s(GRAY, 6 * u);
            c.drawCircle(50 * u, 50 * u, 28 * u, st);
            c.drawCircle(50 * u, 50 * u, 8 * u, f(GRAY));
            c.drawLine(22 * u, 50 * u, 42 * u, 50 * u, st);
            c.drawLine(58 * u, 50 * u, 78 * u, 50 * u, st);
            c.drawLine(50 * u, 58 * u, 50 * u, 78 * u, st);
        } else if ("av".equals(id)) {
            text(c, "AV", 50 * u, 50 * u, 32 * u, ORANGE);
        } else if ("clock".equals(id)) {
            c.drawCircle(50 * u, 50 * u, 28 * u, s(LBLUE, 5 * u));
            Paint st = s(WHITE, 4.5f * u);
            c.drawLine(50 * u, 50 * u, 50 * u, 32 * u, st);
            c.drawLine(50 * u, 50 * u, 62 * u, 57 * u, st);
        } else if ("browser".equals(id)) {
            Paint st = s(BLUE, 5 * u);
            c.drawCircle(50 * u, 50 * u, 28 * u, st);
            c.drawOval(rc(u, 38, 22, 62, 78), s(BLUE, 4 * u));
            c.drawLine(22 * u, 50 * u, 78 * u, 50 * u, s(BLUE, 4 * u));
        } else if ("airdrop".equals(id)) {
            Paint ar = s(BLUE, 5.5f * u);
            c.drawCircle(50 * u, 66 * u, 4.5f * u, f(BLUE));
            // Symetryczne Åuki wzglÄdem osi X=50
            c.drawArc(rc(u, 38, 54, 62, 78), 218, 104, false, ar);
            c.drawArc(rc(u, 28, 44, 72, 88), 218, 104, false, ar);
            c.drawArc(rc(u, 18, 34, 82, 98), 218, 104, false, ar);
        } else if ("appstore".equals(id)) {
            Paint st = s(BLUE, 6.5f * u);
            // Litery "A" jak w logo App Store (trÃ³jkÄt z poprzeczkÄ)
            c.drawLine(50 * u, 22 * u, 26 * u, 72 * u, st);
            c.drawLine(50 * u, 22 * u, 74 * u, 72 * u, st);
            c.drawLine(36 * u, 55 * u, 64 * u, 55 * u, st);
            // Dodatkowe kreski charakterystyczne dla logo App Store
            c.drawLine(28 * u, 78 * u, 72 * u, 78 * u, s(BLUE, 5f * u));
        } else if ("appearance".equals(id)) {
            c.drawCircle(50 * u, 50 * u, 26 * u, s(WHITE, 5 * u));
            c.drawArc(rc(u, 24, 24, 76, 76), 90, 180, true, f(WHITE));
        } else if ("grid".equals(id)) {
            int[] cols = {BLUE, GREEN, ORANGE, PINK};
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    c.drawRoundRect(rc(u, 24 + i * 28, 24 + j * 28, 46 + i * 28, 46 + j * 28),
                            5 * u, 5 * u, f(cols[i * 2 + j]));
                }
            }
        } else if ("apps".equals(id)) {
            for (int i = 0; i < 3; i++) {
                float y = (30 + 20 * i) * u;
                c.drawCircle(30 * u, y, 4 * u, f(BLUE));
                c.drawLine(42 * u, y, 74 * u, y, s(BLUE, 5 * u));
            }
        } else if ("star".equals(id)) {
            Path star = new Path();
            for (int i = 0; i < 10; i++) {
                double ang = -Math.PI / 2 + i * Math.PI / 5;
                float rr = (i % 2 == 0 ? 30 : 13) * u;
                float x = 50 * u + (float) (Math.cos(ang) * rr);
                float y = 52 * u + (float) (Math.sin(ang) * rr);
                if (i == 0) {
                    star.moveTo(x, y);
                } else {
                    star.lineTo(x, y);
                }
            }
            star.close();
            c.drawPath(star, f(ORANGE));
        } else if ("status".equals(id)) {
            c.drawRoundRect(rc(u, 18, 36, 76, 64), 8 * u, 8 * u, s(GREEN, 5 * u));
            c.drawRect(rc(u, 78, 46, 84, 54), f(GREEN));
            c.drawRoundRect(rc(u, 25, 43, 55, 57), 3 * u, 3 * u, f(GREEN));
        } else if ("link".equals(id)) {
            Bitmap lk = Icons.tether(Math.round(70 * u), GREEN, false);
            c.drawBitmap(lk, 15 * u, 15 * u, null);
        } else if ("reset".equals(id)) {
            c.drawArc(rc(u, 26, 28, 74, 76), -50, 280, false, s(ORANGE, 6 * u));
            Path ah = new Path();
            ah.moveTo(58 * u, 16 * u);
            ah.lineTo(76 * u, 28 * u);
            ah.lineTo(56 * u, 38 * u);
            ah.close();
            c.drawPath(ah, f(ORANGE));
        } else if ("info".equals(id)) {
            c.drawCircle(50 * u, 50 * u, 28 * u, s(PURPLE, 5 * u));
            c.drawCircle(50 * u, 37 * u, 3.8f * u, f(PURPLE));
            c.drawLine(50 * u, 47 * u, 50 * u, 66 * u, s(PURPLE, 6 * u));
        } else if ("honda".equals(id)) {
            // Emblemat Hondy: zaokraglona tarcza + zwezone "H" (biale, z czerwonym
            // akcentem) - czytelne na ciemnym kaflu, w stylu reszty ikon Docka.
            Paint red = f(0xFFE60012);
            c.drawRoundRect(rc(u, 27, 19, 73, 81), 20 * u, 20 * u, red);
            Paint white = f(0xFFFFFFFF);
            c.drawRoundRect(rc(u, 32, 24, 68, 76), 16 * u, 16 * u, white);
            Paint bar = s(0xFFE60012, 8f * u);
            c.drawLine(41 * u, 33 * u, 41 * u, 67 * u, bar);
            c.drawLine(59 * u, 33 * u, 59 * u, 67 * u, bar);
            c.drawLine(41 * u, 50 * u, 59 * u, 50 * u, bar);
        }
    }

    public static Bitmap render(String id, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float u = size / 100f;
        float r = size * 0.225f;

        if ("obdgear".equals(id) && appCtx != null) {
            try {
                Bitmap src = BitmapFactory.decodeResource(appCtx.getResources(), R.drawable.obd_gear);
                if (src != null) {
                    Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    c.drawBitmap(src, null, new RectF(0, 0, size, size), pp);
                    return b;
                }
            } catch (Throwable ignored) {
            }
        }

        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        if ("carplay".equals(id)) {
            t.setShader(new LinearGradient(0, 0, 0, size, 0xFF4BE878, 0xFF1FA84A,
                    Shader.TileMode.CLAMP));
        } else {
            t.setShader(new LinearGradient(0, 0, size * 0.6f, size, 0xFF34353A, 0xFF121316,
                    Shader.TileMode.CLAMP));
        }
        c.drawRoundRect(new RectF(0, 0, size, size), r, r, t);

        // symbol rysujemy na osobnej warstwie: cieÅ + ÅwiatÅo od gÃ³ry i cieÅ od doÅu
        Bitmap layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);
        try {
            glyph(lc, id, u);
        } catch (Throwable ignored) {
        }
        try {
            Paint shp = new Paint(Paint.ANTI_ALIAS_FLAG);
            shp.setMaskFilter(new BlurMaskFilter(Math.max(1f, 2.4f * u), BlurMaskFilter.Blur.NORMAL));
            int[] off = new int[2];
            Bitmap alpha = layer.extractAlpha(shp, off);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setColor(0xAA000000);
            c.drawBitmap(alpha, off[0], off[1] + 2.4f * u, sp);

            Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
            gp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            gp.setShader(new LinearGradient(0, 0, 0, size,
                    new int[]{0x66FFFFFF, 0x00FFFFFF, 0x4A000000},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            lc.drawRect(0, 0, size, size, gp);
        } catch (Throwable ignored) {
        }
        c.drawBitmap(layer, 0, 0, null);
        return b;
    }
}







