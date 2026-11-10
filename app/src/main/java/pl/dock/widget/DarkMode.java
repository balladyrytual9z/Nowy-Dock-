package pl.dock.widget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

// Tryb ciemny jak w iOS (przycisk w panelu łączności):
// - przełącznik wł/wył,
// - automat: ciemny od zachodu do wschodu słońca (SolarTimes/NOAA, offline;
//   współrzędne jak w Night Shift - z modułu OBD, fallback Warszawa),
// - ręczny klik zmienia stan "do najbliższego wydarzenia słońca" - dokładnie
//   jak podpis w iOS: "Włączony do wsch. słońca" / "Wyłączony do zach. słońca".
public class DarkMode {

    // Czy teraz tryb ciemny: najpierw ręczna zmiana (do najbliższego wydarzenia),
    // po jej wygaśnięciu - harmonogram słońca.
    public static boolean isNight(Context c) {
        int o = Prefs.darkOverride(c);
        if (o != 0) {
            if (System.currentTimeMillis() < Prefs.darkOverrideUntil(c)) return o == 1;
            Prefs.clearDarkOverride(c);   // ręczna zmiana wygasła - przejmuje słońce
        }
        return nightBySun(c);
    }

    // Klik: przełącz i utrzymaj do najbliższego wschodu/zachodu słońca.
    public static void toggle(Context c) {
        boolean now = isNight(c);
        Prefs.setDarkUsed(c, true);
        Prefs.setDarkOverride(c, now ? 2 : 1, nextEvent(c, now));
    }

    // Podpis pod przyciskiem (jak w iOS).
    public static String label(Context c) {
        return isNight(c) ? "Włączony do wsch. słońca" : "Wyłączony do zach. słońca";
    }

    private static boolean nightBySun(Context c) {
        try {
            double[] ll = coords(c);
            return pl.dock.widget.util.SolarTimes.INSTANCE.isNight(
                    ll[0], ll[1], Calendar.getInstance());
        } catch (Throwable t) {
            return false;
        }
    }

    // Następne wydarzenie: gdy teraz noc -> wschód, gdy dzień -> zachód.
    private static long nextEvent(Context c, boolean nowNight) {
        try {
            double[] ll = coords(c);
            Calendar cal = Calendar.getInstance();
            double[] ss = pl.dock.widget.util.SolarTimes.INSTANCE.sunriseSunset(ll[0], ll[1], cal);
            if (ss != null) {
                double target = nowNight ? ss[0] : ss[1];   // noc -> wschód; dzień -> zachód
                long t = midnight(cal) + (long) (target * 60000.0);
                if (t <= System.currentTimeMillis()) t += 24L * 3600L * 1000L;   // już było - jutro
                return t;
            }
        } catch (Throwable ignored) {
        }
        return System.currentTimeMillis() + 6L * 3600L * 1000L;   // bezpiecznik: 6 h
    }

    private static long midnight(Calendar cal) {
        Calendar m = (Calendar) cal.clone();
        m.set(Calendar.HOUR_OF_DAY, 0);
        m.set(Calendar.MINUTE, 0);
        m.set(Calendar.SECOND, 0);
        m.set(Calendar.MILLISECOND, 0);
        return m.getTimeInMillis();
    }

    // współrzędne jak w Night Shift (moduł OBD zapisuje lat/lon w "obd_settings")
    private static double[] coords(Context c) {
        return new double[]{readCoord(c, "lat", 52.23), readCoord(c, "lon", 21.01)};
    }

    private static double readCoord(Context c, String key, double def) {
        try {
            SharedPreferences s = c.getApplicationContext()
                    .getSharedPreferences("obd_settings", Context.MODE_PRIVATE);
            if (s.contains(key)) {
                try {
                    Object v = s.getAll().get(key);
                    if (v instanceof Float) return (Float) v;
                    if (v instanceof Double) return (Double) v;
                } catch (Throwable ignored) {
                }
                String str = s.getString(key, null);
                if (str != null) {
                    try {
                        return Double.parseDouble(str);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return def;
    }
}
