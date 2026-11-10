
package pl.dock.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;

public class Icons {
    static final int S = 128;

    private static int ver = 0;

    // Gotowe ikony trzymamy w pamięci (ok. 64 KB sztuka), żeby nie składać ich przy każdym odświeżeniu.
    private static final LruCache<String, Bitmap> cache =
            new LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap b) {
                    return b.getByteCount();
                }
            };

    public static int version() {
        return ver;
    }

    // po instalacji/odinstalowaniu aplikacji lub zmianie motywu ikon
    public static void invalidate() {
        ver++;
        cache.evictAll();
    }

    static File file(Context c, AppEntry e) {
        return new File(c.getFilesDir(), "icon_" + e.key() + ".png");
    }

    // ---------------------------------------------------------------- ikony aplikacji

    public static Bitmap forEntry(Context c, AppEntry e) {
        BuiltinIcons.init(c);
        String fm = Prefs.frame(c);
        String key = e.key() + "|" + e.pkg + "|" + e.mode + "|" + e.iconPkg + "|" + e.orig + "|"
                + fm + "|" + ver + ("file".equals(e.mode) ? "|" + file(c, e).lastModified() : "");
        Bitmap b = cache.get(key);
        if (b == null) {
            b = buildEntry(c, e, fm);
            cache.put(key, b);
        }
        return b;
    }

    private static Bitmap buildEntry(Context c, AppEntry e, String fm) {
        try {
            // narzedzia Docka (dock:obd / dock:settings) nie maja pakietu systemowego -
            // zawsze rysujemy wbudowana ikone, nawet dla wpisu bez mode="builtin"
            if (Prefs.isVirtual(e.pkg)) {
                String id = "dock:obd".equals(e.pkg) ? "honda" : "settings";
                return compose(BuiltinIcons.render(id, S), true, fm);
            }
            if ("builtin".equals(e.mode) && e.iconPkg.length() > 0) {
                return compose(BuiltinIcons.render(e.iconPkg, S), true, fm);
            }
            if (e.id == 0 && "default".equals(e.mode) && e.pkg.length() == 0) {
                return compose(BuiltinIcons.render("carplay", S), true, fm);
            }
            if ("file".equals(e.mode)) {
                Bitmap f = BitmapFactory.decodeFile(file(c, e).getAbsolutePath());
                if (f != null) return compose(f, true, fm);
            } else if ("app".equals(e.mode) && e.iconPkg.length() > 0) {
                Bitmap a = drawableToBitmap(c.getPackageManager().getApplicationIcon(e.iconPkg));
                return e.orig ? rounded(a) : compose(a, false, fm);
            }
            if (e.pkg.length() > 0) {
                Bitmap a = drawableToBitmap(c.getPackageManager().getApplicationIcon(e.pkg));
                return e.orig ? rounded(a) : compose(a, false, fm);
            }
        } catch (Throwable ignored) {
        }
        return compose(null, true, fm);
    }

    public static Bitmap forPackage(Context c, String pkg) {
        BuiltinIcons.init(c);
        String fm = Prefs.frame(c);
        String key = "p|" + pkg + "|" + fm + "|" + ver;
        Bitmap b = cache.get(key);
        if (b == null) {
            String label = "";
            try {
                android.content.pm.PackageManager pm = c.getPackageManager();
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (Throwable ignored) {}
            Overrides.O o = Overrides.match(label);
            if (o != null) {
                b = compose(BuiltinIcons.render(o.icon, S), true, fm);
            } else {
                try {
                    b = compose(drawableToBitmap(c.getPackageManager().getApplicationIcon(pkg)),
                            false, fm);
                } catch (Throwable e) {
                    b = compose(null, true, fm);
                }
            }
            cache.put(key, b);
        }
        return b;
    }

    public static Bitmap reconnectingRing(int size, int color) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(size * 0.09f);
        p.setStrokeCap(Paint.Cap.ROUND);
        RectF r = new RectF(size * 0.15f, size * 0.15f, size * 0.85f, size * 0.85f);
        for (int i = 0; i < 8; i++) {
            p.setColor((color & 0x00FFFFFF) | ((40 + i * 25) << 24));
            cv.drawArc(r, i * 45f, 32f, false, p);
        }
        return b;
    }

    // wbudowana ikona z ramką (do wyboru w ustawieniach i na listach)
    public static Bitmap builtin(Context c, String id) {
        BuiltinIcons.init(c);
        String fm = Prefs.frame(c);
        String key = "b|" + id + "|" + fm + "|" + ver;
        Bitmap b = cache.get(key);
        if (b == null) {
            b = compose(BuiltinIcons.render(id, S), true, fm);
            cache.put(key, b);
        }
        return b;
    }

    // Ikona DOKLADNIE taka jak w systemie - bez podmian Overrides (AirDrop, Pliki,
    // App Store...). Uzywana przez liste aplikacji, ktora pokazuje oryginalne nazwy.
    public static Bitmap forPackageRaw(Context c, String pkg) {
        BuiltinIcons.init(c);
        String fm = Prefs.frame(c);
        String key = "r|" + pkg + "|" + fm + "|" + ver;
        Bitmap b = cache.get(key);
        if (b == null) {
            try {
                b = compose(drawableToBitmap(c.getPackageManager().getApplicationIcon(pkg)),
                        false, fm);
            } catch (Throwable t) {
                b = compose(null, true, fm);
            }
            cache.put(key, b);
        }
        return b;
    }

    // podgląd ikony w wybranym trybie ramki
    public static Bitmap preview(Context c, Bitmap raw, String fm) {
        return compose(raw, false, fm);
    }

    static Bitmap drawableToBitmap(Drawable d) {
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        d.setBounds(0, 0, S, S);
        d.draw(cv);
        return b;
    }

    // zaokrąglona ikona bez ramki
    static Bitmap rounded(Bitmap raw) {
        Bitmap out = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = S * 0.225f;
        if (raw != null) {
            Bitmap sc = Bitmap.createScaledBitmap(raw, S, S, true);
            p.setShader(new BitmapShader(sc, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        } else {
            p.setColor(0xFF2A3344);
        }
        oc.drawRoundRect(new RectF(0, 0, S, S), r, r, p);
        return out;
    }

    // Ramka jak w CarPlay: ciemny kafelek, poświata od lewego górnego rogu
    // i pierścień, który jest najjaśniejszy w lewym górnym rogu.
    // tile = true: obraz jest już kafelkiem (wbudowany / własny z pliku).
    // tile = false: ikona aplikacji; w trybie "carplay" ląduje wcięta w ciemnym kafelku.
    static Bitmap compose(Bitmap raw, boolean tile, String fm) {
        Bitmap out = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float r = S * 0.225f;
        RectF full = new RectF(0, 0, S, S);
        if (raw != null && "carplay".equals(fm) && !tile) {
            p.setShader(new LinearGradient(0, 0, S * 0.7f, S, 0xFF343437, 0xFF18181A,
                    Shader.TileMode.CLAMP));
            cv.drawRoundRect(full, r, r, p);
            p.setShader(null);
            float pad = S * 0.11f;
            cv.drawBitmap(rounded(raw), null, new RectF(pad, pad, S - pad, S - pad), p);
        } else {
            cv.drawBitmap(rounded(raw), 0, 0, null);
        }
        if (!"none".equals(fm)) {
            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setShader(new RadialGradient(S * 0.14f, S * 0.08f, S * 1.0f,
                    0x34FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawRoundRect(full, r, r, glow);

            float w = S * 0.03f;
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(w);
            ring.setShader(new LinearGradient(0, 0, S, S,
                    new int[]{0xCCFFFFFF, 0x55FFFFFF, 0x18FFFFFF, 0x40FFFFFF},
                    new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRoundRect(new RectF(w / 2, w / 2, S - w / 2, S - w / 2),
                    r - w / 2, r - w / 2, ring);
        }
        return out;
    }

    // ---------------------------------------------------------------- ikony statusu

    // Ikona wektorowa w podanym kolorze, wyśrodkowana pod względem widocznej zawartości
    public static Bitmap vector(Context c, int resId, int size, int color) {
        String key = "v|" + resId + "|" + size + "|" + color;
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        try {
            Drawable d = c.getDrawable(resId).mutate();
            d.setBounds(0, 0, size, size);
            d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            d.draw(new Canvas(b));
        } catch (Throwable ignored) {
        }
        Bitmap out = centered(b);
        cache.put(key, out);
        return out;
    }

    // Akumulator z zielonym piorunem (silnik pracuje, ładowanie z alternatora)
    public static Bitmap batteryCharging(Context c, int size, int color) {
        String key = "bc|" + size + "|" + color;
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        try {
            Canvas cv = new Canvas(b);
            Drawable d = c.getDrawable(R.drawable.ic_battery_outline).mutate();
            d.setBounds(0, 0, size, size);
            d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            d.draw(cv);
            float s = size;
            Path bolt = new Path();
            bolt.moveTo(s * 0.55f, s * 0.26f);
            bolt.lineTo(s * 0.37f, s * 0.55f);
            bolt.lineTo(s * 0.49f, s * 0.55f);
            bolt.lineTo(s * 0.45f, s * 0.78f);
            bolt.lineTo(s * 0.64f, s * 0.47f);
            bolt.lineTo(s * 0.52f, s * 0.47f);
            bolt.close();
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setColor(0xFF30D158);
            cv.drawPath(bolt, g);
        } catch (Throwable ignored) {
        }
        Bitmap out = centered(b);
        cache.put(key, out);
        return out;
    }

    // Bateria telefonu: kontur z wypełnieniem proporcjonalnym do poziomu (jak w iOS)
    public static Bitmap phoneBattery(int level, int color) {
        String key = "pb|" + level + "|" + color;
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        int w = 96;
        int h = 48;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor((color & 0x00FFFFFF) | 0x99000000);
        cv.drawRoundRect(new RectF(3, 6, 82, 42), 11, 11, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor((color & 0x00FFFFFF) | 0x99000000);
        cv.drawRoundRect(new RectF(86, 19, 92, 29), 3, 3, p);
        int fill = (level <= 20) ? 0xFFFF453A : color;
        p.setColor(fill);
        float fw = Math.max(6f, 68f * Math.max(0, Math.min(100, level)) / 100f);
        cv.drawRoundRect(new RectF(8, 11, 8 + fw, 37), 7, 7, p);
        cache.put(key, b);
        return b;
    }

    // Wi-Fi w czterech stanach: wyłączone (przekreślone), włączone bez sieci (przygaszone),
    // połączone bez internetu (z pomarańczowym znakiem), połączone
    //
    // Kształt ikony bierze się z Glyphs - to samo źródło prawdy, z którego rysuje
    // panel łączności i ikony pod zegarem w doku. Wcześniej był tu wektor z
    // res/drawable/ic_wifi barwiony filtrem PorterDuff.SRC_IN; na docelowym sprzęcie
    // (ESSGOO MT8163 / Mali-T720 / Android 9) obrysowy wektor z przezroczystym
    // wypełnieniem po takim filtrze wychodził pusty i ikona Wi-Fi nie pojawiała się.
    public static Bitmap wifi(Context c, int state, int fg) {
        int dim = (fg & 0x00FFFFFF) | 0x66000000;
        int dimmer = (fg & 0x00FFFFFF) | 0x40000000;
        if (state == 0) return Glyphs.bitmapFull("wifi_off", 96, dimmer);   // nie XML - na tym GPU ginie
        if (state == 1) return Glyphs.bitmapFull("wifi", 96, dim);
        if (state == 3) return Glyphs.bitmapFull("wifi", 96, fg);
        String key = "wf2|" + fg;
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        Bitmap base = Glyphs.bitmapFull("wifi", 96, fg);
        Bitmap out = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawBitmap(base, 0, 0, null);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFF9F0A);
        cv.drawCircle(78, 78, 15, p);
        p.setColor(0xFF000000);
        cv.drawRect(75.5f, 68f, 80.5f, 80f, p);
        cv.drawCircle(78, 85f, 3f, p);
        cache.put(key, out);
        return out;
    }

    // Znak CarPlay (łuk i trójkąt) do paska statusu
    public static Bitmap carplayMark(int size, int color) {
        String key = "cm|" + size + "|" + color;
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float s = size;
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeCap(Paint.Cap.ROUND);
        st.setStrokeWidth(s * 0.10f);
        st.setColor(color);
        cv.drawArc(new RectF(s * 0.12f, s * 0.12f, s * 0.88f, s * 0.88f), 40, 280, false, st);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        Path t = new Path();
        t.moveTo(s * 0.40f, s * 0.30f);
        t.lineTo(s * 0.74f, s * 0.50f);
        t.lineTo(s * 0.40f, s * 0.70f);
        t.close();
        cv.drawPath(t, fill);
        Bitmap out = centered(b);
        cache.put(key, out);
        return out;
    }

    // Tethering (hotspot) jak w iPhonie: dwa splecione ogniwa łańcucha; slash = wyłączony
    public static Bitmap tether(int size, int color, boolean slash) {
        String key = "t|" + size + "|" + color + "|" + slash;
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        Bitmap out = drawTether(size, color, slash);
        cache.put(key, out);
        return out;
    }

    private static Bitmap drawTether(int size, int color, boolean slash) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float s = size;
        float sw = s * 0.085f;
        float gap = s * 0.05f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setColor(color);
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setStyle(Paint.Style.STROKE);
        clear.setStrokeWidth(sw + 2 * gap);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        RectF a = new RectF(s * 0.08f, s * 0.16f, s * 0.68f, s * 0.50f);
        RectF d = new RectF(s * 0.32f, s * 0.38f, s * 0.92f, s * 0.72f);
        float r = a.height() / 2f;

        cv.drawRoundRect(a, r, r, p);

        Path c1 = new Path();
        c1.addCircle(s * 0.33f, s * 0.50f, s * 0.11f, Path.Direction.CW);
        cv.save();
        cv.clipPath(c1);
        cv.drawRoundRect(d, r, r, clear);
        cv.restore();
        cv.drawRoundRect(d, r, r, p);

        Path c2 = new Path();
        c2.addCircle(s * 0.67f, s * 0.38f, s * 0.11f, Path.Direction.CW);
        cv.save();
        cv.clipPath(c2);
        cv.drawRoundRect(a, r, r, clear);
        cv.drawRoundRect(a, r, r, p);
        cv.restore();

        if (slash) {
            Paint cl = new Paint(Paint.ANTI_ALIAS_FLAG);
            cl.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            cl.setStyle(Paint.Style.STROKE);
            cl.setStrokeWidth(s * 0.20f);
            cv.drawLine(s * 0.16f, s * 0.10f, s * 0.84f, s * 0.78f, cl);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeWidth(sw);
            line.setColor(color);
            cv.drawLine(s * 0.16f, s * 0.10f, s * 0.84f, s * 0.78f, line);
        }
        return centered(b);
    }

    // Przesuwa zawartość tak, by jej środek leżał dokładnie w środku bitmapy
    static Bitmap centered(Bitmap b) {
        try {
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
            if (maxX < 0) return b;
            float dx = w / 2f - (minX + maxX + 1) / 2f;
            float dy = h / 2f - (minY + maxY + 1) / 2f;
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            new Canvas(out).drawBitmap(b, dx, dy, null);
            return out;
        } catch (Throwable t) {
            return b;
        }
    }

    // ---------------------------------------------------------------- własny obraz z pliku

    public static boolean saveFromStream(Context c, AppEntry e, java.io.InputStream in1,
                                         java.io.InputStream in2) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in1, null, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 512 && o.outHeight / (sample * 2) >= 512) {
                sample *= 2;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeStream(in2, null, o2);
            if (bmp == null) return false;
            int side = Math.min(bmp.getWidth(), bmp.getHeight());
            Bitmap sq = Bitmap.createBitmap(bmp,
                    (bmp.getWidth() - side) / 2, (bmp.getHeight() - side) / 2, side, side);
            Bitmap fin = Bitmap.createScaledBitmap(sq, 256, 256, true);
            FileOutputStream fo = new FileOutputStream(file(c, e));
            fin.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
            invalidate();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}







