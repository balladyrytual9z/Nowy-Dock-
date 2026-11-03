package pl.dock.widget;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;

// Night Shift: ciepły filtr barwy ekranu (nakładka). Wymaga zgody "Wyświetlaj nad innymi aplikacjami".
// Tryby: "off" | "on" | "sunset" (od zachodu do wschodu słońca wg SolarTimes).
public class NightShift {

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
