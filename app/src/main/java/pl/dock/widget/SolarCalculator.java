package pl.dock.widget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

/**
 * Wspólne źródło prawdy dla lokalizacji i obliczeń słonecznych.
 * Używane przez DarkMode i NightShift - oba mechanizmy korzystają z
 * identycznych współrzędnych i tej samej implementacji SolarTimes.
 *
 * Obsługiwane formaty współrzędnych w obd_settings:
 *   - Float
 *   - Double
 *   - String
 *   - surowe long bits (Double.doubleToRawLongBits)
 *
 * Walidacja: szerokość -90..90, długość -180..180.
 * Nieprawidłowe wartości -> fallback (Warszawa) + log diagnostyczny.
 */
public final class SolarCalculator {

    private SolarCalculator() {}

    /** Klucz flagi "użyto już nowego modelu" dla trybu ciemnego. */
    public static final String PREF_DARK_USED = "darkused";
    /** Klucz ręcznego nadpisania trybu ciemnego: 0=brak, 1=ON, 2=OFF. */
    public static final String PREF_DARK_OVERRIDE = "darkovr";
    /** Klucz czasu wygaśnięcia nadpisania trybu ciemnego. */
    public static final String PREF_DARK_OVERRIDE_UNTIL = "darkovr_until";

    /** Stan wynikowy trybu ciemnego. */
    public enum DarkState {
        AUTO_DAY,    // automatycznie wyłączony (dzień)
        AUTO_NIGHT,  // automatycznie włączony (noc)
        MANUAL_ON,   // ręcznie włączony do najbliższego wydarzenia
        MANUAL_OFF   // ręcznie wyłączony do najbliższego wydarzenia
    }

    /** Stan wynikowy Night Shift. */
    public enum NsState {
        AUTO_OFF,    // automatycznie wyłączony (dzień)
        AUTO_ON,     // automatycznie włączony (noc)
        MANUAL_ON,   // ręcznie włączony do najbliższego wydarzenia
        MANUAL_OFF   // ręcznie wyłączony do najbliższego wydarzenia
    }

    /** Domyślne współrzędne: Warszawa. */
    private static final double DEFAULT_LAT = 52.23;
    private static final double DEFAULT_LON = 21.01;

    /** Pobierz wspólne współrzędne dla obu mechanizmów. */
    public static double[] coords(Context c) {
        return new double[]{readCoord(c, "lat", DEFAULT_LAT), readCoord(c, "lon", DEFAULT_LON)};
    }

    /** Odczytaj jedną współrzędną z obd_settings z obsługą wszystkich formatów. */
    public static double readCoord(Context c, String key, double def) {
        try {
            SharedPreferences s = c.getApplicationContext()
                    .getSharedPreferences("obd_settings", Context.MODE_PRIVATE);
            if (!s.contains(key)) return def;

            Object raw = null;
            try {
                raw = s.getAll().get(key);
            } catch (Throwable ignored) {}

            if (raw instanceof Float) {
                return validate((float) raw, def);
            }
            if (raw instanceof Double) {
                return validate((double) raw, def);
            }
            if (raw instanceof Long) {
                // surowe long bits
                try {
                    return validate(Double.longBitsToDouble((Long) raw), def);
                } catch (Throwable ignored) {}
            }
            if (raw instanceof Integer) {
                return validate((double) (Integer) raw, def);
            }

            String str = s.getString(key, null);
            if (str != null) {
                try {
                    return validate(Double.parseDouble(str.trim()), def);
                } catch (Throwable ignored) {}
            }

            // spróbuj jako long bits z getLong
            try {
                long bits = s.getLong(key, 0L);
                if (bits != 0L) {
                    return validate(Double.longBitsToDouble(bits), def);
                }
            } catch (Throwable ignored) {}

            // spróbuj jako float z getFloat
            try {
                return validate((double) s.getFloat(key, (float) def), def);
            } catch (Throwable ignored) {}

            return def;
        } catch (Throwable t) {
            LogDiagnostics.e("SolarCalculator", "readCoord(" + key + ") failed: " + t.getMessage(), t);
            return def;
        }
    }

    private static double validate(double v, double def) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return def;
        // szerokość: -90..90, długość: -180..180 (walidacja chiamana przez wywołującego dla konkretnego klucza)
        return v;
    }

    /** Waliduj szerokość geograficzną (-90..90). */
    public static double validateLat(double v, double def) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < -90.0 || v > 90.0) return def;
        return v;
    }

    /** Waliduj długość geograficzną (-180..180). */
    public static double validateLon(double v, double def) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < -180.0 || v > 180.0) return def;
        return v;
    }

    /**
     * Oblicz wschód i zachód słońca w minutach od północy.
     * @return null gdy doba polarna lub błąd obliczeń.
     */
    public static Double[] sunriseSunset(Context c, Calendar day) {
        double[] ll = coords(c);
        double lat = validateLat(ll[0], DEFAULT_LAT);
        double lon = validateLon(ll[1], DEFAULT_LON);
        try {
            double[] ss = pl.dock.widget.util.SolarTimes.INSTANCE.sunriseSunset(lat, lon, day);
            if (ss == null) {
                LogDiagnostics.w("SolarCalculator", "doba polarna dla lat=" + lat + " lon=" + lon);
                return null;
            }
            return new Double[]{ss[0], ss[1]};
        } catch (Throwable t) {
            LogDiagnostics.e("SolarCalculator", "sunriseSunset failed: " + t.getMessage(), t);
            return null;
        }
    }

    /** Czy teraz jest noc (wg słońca). */
    public static boolean isNight(Context c) {
        double[] ll = coords(c);
        double lat = validateLat(ll[0], DEFAULT_LAT);
        double lon = validateLon(ll[1], DEFAULT_LON);
        try {
            return pl.dock.widget.util.SolarTimes.INSTANCE.isNight(lat, lon, Calendar.getInstance());
        } catch (Throwable t) {
            LogDiagnostics.e("SolarCalculator", "isNight failed: " + t.getMessage(), t);
            return false;
        }
    }

    /**
     * Oblicz czas następnego wydarzenia słonecznego.
     * @param nowNight jeśli teraz noc -> szukaj wschodu, jeśli dzień -> szukaj zachodu.
     * @return czas w millis lub -1 gdy nie da się obliczyć.
     */
    public static long nextSunEvent(Context c, boolean nowNight) {
        Calendar cal = Calendar.getInstance();
        Double[] ss = sunriseSunset(c, cal);
        if (ss == null) {
            LogDiagnostics.e("SolarCalculator", "nextSunEvent: nie da się obliczyć wschodu/zachodu");
            return -1L;
        }
        double targetMinutes = nowNight ? ss[0] : ss[1]; // noc -> wschód; dzień -> zachód
        long t = midnight(cal) + (long) (targetMinutes * 60000.0);
        long now = System.currentTimeMillis();
        if (t <= now) t += 24L * 3600L * 1000L; // już było - jutro
        return t;
    }

    private static long midnight(Calendar cal) {
        Calendar m = (Calendar) cal.clone();
        m.set(Calendar.HOUR_OF_DAY, 0);
        m.set(Calendar.MINUTE, 0);
        m.set(Calendar.SECOND, 0);
        m.set(Calendar.MILLISECOND, 0);
        return m.getTimeInMillis();
    }

    /** Formatuj współrzędne do logów diagnostycznych. */
    public static String coordsString(Context c) {
        double[] ll = coords(c);
        return String.format(Locale.getDefault(), "lat=%.4f lon=%.4f", ll[0], ll[1]);
    }

    /** Sprawdź czy czas letni/zimowy może wpłynąć na obliczenia (Calendar uwzględnia to automatycznie). */
    public static boolean isDST(Context c) {
        return Calendar.getInstance().getTimeZone().inDaylightTime(Calendar.getInstance().getTime());
    }
}
