package pl.dock.widget;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;

import java.util.Calendar;

// Night Shift: ciepły filtr barwy ekranu (nakładka). Wymaga zgody "Wyświetlaj nad innymi aplikacjami".
// Tryby: "off" | "on" | "sunset" (od zachodu do wschodu słońca wg SolarTimes).
//
// NOWE (jak w iOS, na wzór Trybu ciemnego): po pierwszym kliknięciu nowego
// przycisku (nsUsed) Night Shift działa na harmonogramie słońca
// zachód -> wschód, a klik zmienia stan "do najbliższego wydarzenia":
// wł. do wsch. słońca / wył. do zach. słońca. Po tym wydarzeniu znów
// rządzi słońce. Własne klucze w osobnym pliku prefs "dock_ns" (nie ruszamy
// Prefs.java - te same wzorce co DarkMode w Prefs).
public class NightShift {

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("dock_ns", Context.MODE_PRIVATE);
    }

    public static boolean canDraw(Context c) {
        try {
            return Settings.canDrawOverlays(c);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isOn(Context c) {
        return Prefs.nightShiftOn(c);
    }

    public static String mode(Context c) {
        return Prefs.nightShiftMode(c);
    }

    // ---------- nowy model (jak Tryb ciemny) ----------

    // Flaga: user użył nowego przycisku - od tego momentu harmonogram słońca
    // przejmuje sterowanie (przed tym: stary model off/on/sunset).
    public static boolean nsUsed(Context c) {
        return sp(c).getBoolean("nsused", false);
    }

    public static void setNsUsed(Context c, boolean v) {
        sp(c).edit().putBoolean("nsused", v).apply();
    }

    // Ręczna zmiana: 0 = brak, 1 = włączony, 2 = wyłączony (do "nsovr_until")
    public static int nsOverride(Context c) {
        return sp(c).getInt("nsovr", 0);
    }

    public static long nsOverrideUntil(Context c) {
        return sp(c).getLong("nsovr_until", 0L);
    }

    public static void setNsOverride(Context c, int v, long until) {
        sp(c).edit().putInt("nsovr", v).putLong("nsovr_until", until).apply();
    }

    public static void clearNsOverride(Context c) {
        sp(c).edit().remove("nsovr").remove("nsovr_until").apply();
    }

    // Jasność zapamiętana przed płynnym schodzeniem do minimum (auto-włączenie);
    // zapisy bez `Prefs.version` - to nie rysunek launchera.
    public static int nsBrightPrev(Context c) {
        return sp(c).getInt("ns_bright_prev", 0);
    }

    public static void setNsBrightPrev(Context c, int v) {
        sp(c).edit().putInt("ns_bright_prev", v).apply();
    }

    public static boolean nsBrightSaved(Context c) {
        return sp(c).getBoolean("ns_bright_saved", false);
    }

    public static void setNsBrightSaved(Context c, boolean v) {
        sp(c).edit().putBoolean("ns_bright_saved", v).apply();
    }

    // Czy filtr MA teraz działać: najpierw ręczna zmiana (do najbliższego
    // wydarzenia), po jej wygaśnięciu - harmonogram słońca (zachód -> wschód).
    public static boolean effectiveOn(Context c) {
        if (!nsUsed(c)) return Prefs.nightShiftOn(c);
        int o = nsOverride(c);
        if (o != 0) {
            if (System.currentTimeMillis() < nsOverrideUntil(c)) return o == 1;
            clearNsOverride(c);   // ręczna zmiana wygasła - przejmuje słońce
        }
        return isNightBySun(c);
    }

    // Klik: przełącz i utrzymaj do najbliższego wschodu/zachodu słońca.
    // Zwraca false, gdy trzeba najpierw dać zgodę (otwiera się ekran Androida).
    public static boolean toggle(Context c) {
        if (!canDraw(c)) {
            requestPermission(c);
            return false;
        }
        boolean now = effectiveOn(c);
        setNsUsed(c, true);
        Prefs.setNightShiftMode(c, "sunset");   // baza = harmonogram słońca
        setNsOverride(c, now ? 2 : 1, nextEvent(c, now));
        try {
            Intent i = new Intent(c, NightShiftService.class);
            i.putExtra("manual", true);   // krótkie przejście (bez 30 s i bez jasności)
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    // Podpis pod przyciskiem (jak w iOS / Trybie ciemnym).
    public static String label(Context c) {
        return effectiveOn(c) ? "Włączony do wsch. słońca" : "Wyłączony do zach. słońca";
    }

    // Następne wydarzenie: gdy teraz działa -> wschód, gdy nie działa -> zachód.
    private static long nextEvent(Context c, boolean nowOn) {
        try {
            Calendar cal = Calendar.getInstance();
            double[] ss = pl.dock.widget.util.SolarTimes.INSTANCE.sunriseSunset(
                    readCoord(c, "lat", 52.23), readCoord(c, "lon", 21.01), cal);
            if (ss != null) {
                double target = nowOn ? ss[0] : ss[1];   // działa -> wschód; nie -> zachód
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

    // Czy wg słońca jest teraz noc (lat/lon z ustawień OBD)
    public static boolean isNightBySun(Context c) {
        double lat = readCoord(c, "lat", 52.23);
        double lon = readCoord(c, "lon", 21.01);
        try {
            return pl.dock.widget.util.SolarTimes.INSTANCE.isNight(
                    lat, lon, java.util.Calendar.getInstance());
        } catch (Throwable t) {
            return false;
        }
    }

    // modul OBD moze zapisywac wspolrzedne jako float, string albo raw long bits
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
                try {
                    return Double.longBitsToDouble(s.getLong(key, 0L));
                } catch (Throwable ignored) {
                }
                try {
                    return (double) s.getFloat(key, (float) def);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    // Ustawia tryb: "off" | "on" | "sunset".
    // Zwraca false, gdy trzeba najpierw dać zgodę (otwiera się ekran Androida).
    public static boolean setMode(Context c, String mode) {
        boolean on = !"off".equals(mode);
        if (on && !canDraw(c)) {
            requestPermission(c);
            return false;
        }
        Prefs.setNightShiftMode(c, mode);
        startStop(c, mode);
        return true;
    }

    // kompatybilność wstecz: on/off
    public static boolean set(Context c, boolean on) {
        return setMode(c, on ? "on" : "off");
    }

    // następny tryb w cyklu: off -> on -> sunset -> off
    public static String nextMode(String cur) {
        if ("on".equals(cur)) return "sunset";
        if ("sunset".equals(cur)) return "off";
        return "on";
    }

    // natychmiastowe odświeżenie koloru nakładki (np. po zmianie ciepła suwakiem)
    public static void refresh(Context c) {
        if (!Prefs.nightShiftOn(c) || !canDraw(c)) return;
        try {
            Intent i = new Intent(c, NightShiftService.class);
            i.putExtra("refresh", true);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Pauza nakladki na czas systemowego okna instalacji APK (patrz Updater.install).
     * Okno instalatora / Play Protect ("Zainstaluj bez skanowania") odcina dotyk,
     * gdy jakiekolwiek okno jest nad nim - nawet przezroczysta nakladka
     * nie-lapiaca dotyku (ochrona przed tapjackingiem). Filtr wraca po
     * powrocie do launchera (BaseActivity.onResume -> resume).
     */
    public static void pause(Context c) {
        if (!Prefs.nightShiftOn(c)) return;
        try {
            Intent i = new Intent(c, NightShiftService.class);
            i.putExtra("pause", true);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Powrot nakladki po pauzie (wolane z BaseActivity.onResume). */
    public static void resume(Context c) {
        if (!Prefs.nightShiftOn(c) || !canDraw(c)) return;
        try {
            Intent i = new Intent(c, NightShiftService.class);
            i.putExtra("resume", true);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void startStop(Context c, String mode) {
        try {
            Intent i = new Intent(c, NightShiftService.class);
            if ("off".equals(mode)) {
                c.stopService(i);
            } else {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    c.startForegroundService(i);
                } else {
                    c.startService(i);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void restore(Context c) {
        String m = Prefs.nightShiftMode(c);
        if (!"off".equals(m) && canDraw(c)) {
            startStop(c, m);
        }
    }
}
