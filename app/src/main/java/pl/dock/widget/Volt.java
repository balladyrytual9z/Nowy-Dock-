
package pl.dock.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// NapiÄcie tabletu z wybranego ÅºrÃ³dÅa
public class Volt {

    static Intent batt(Context c) {
        return c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    public static String readRaw(String path) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(path));
            String s = r.readLine();
            return s == null ? "?" : s.trim();
        } catch (Throwable t) {
            return "brak dostÄpu";
        } finally {
            try {
                if (r != null) r.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static Double parse(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(s);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static Double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) return parse((String) o);
        return null;
    }

    // mikrowolty -> V, miliwolty -> V, wolty bez zmian
    static double autoScale(double v) {
        if (v >= 100000) return v / 1000000.0;
        if (v >= 100) return v / 1000.0;
        return v;
    }

    static String readSetting(Context c, String spec) {
        String scope = "system";
        String key = spec;
        int i = spec.indexOf(':');
        if (i > 0) {
            scope = spec.substring(0, i).toLowerCase(Locale.US);
            key = spec.substring(i + 1);
        }
        ContentResolver cr = c.getContentResolver();
        if ("global".equals(scope)) return Settings.Global.getString(cr, key);
        if ("secure".equals(scope)) return Settings.Secure.getString(cr, key);
        return Settings.System.getString(cr, key);
    }

    public static Float read(Context c) {
        String src = Prefs.voltSource(c);
        Double raw = null;
        try {
            if ("file".equals(src)) {
                raw = parse(readRaw(Prefs.voltPath(c)));
            } else if ("bcast".equals(src)) {
                // wartoÅÄ starsza niÅ¼ 2 min = ÅºrÃ³dÅo milczy (np. aplikacja OBD zamkniÄta)
                float v = Prefs.bcastVal(c);
                boolean fresh = System.currentTimeMillis() - Prefs.bcastTs(c) < 120000L;
                if (v > 0 && fresh) raw = (double) v;
            } else if ("setting".equals(src)) {
                raw = parse(readSetting(c, Prefs.settingKey(c)));
            } else {
                Intent b = batt(c);
                int mv = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                if (mv > 0) raw = (double) mv;
            }
        } catch (Throwable ignored) {
        }
        if (raw == null || raw <= 0) return null;
        return (float) autoScale(raw);
    }

    public static String text(Context c) {
        Float v = read(c);
        if (v == null) return "-- V";
        String f = v >= 10 ? "%.1f V" : "%.2f V";
        return String.format(new Locale("pl"), f, v);
    }

    public static String sourceLabel(Context c) {
        String s = Prefs.voltSource(c);
        if ("file".equals(s)) return "Plik: " + Prefs.voltPath(c);
        if ("bcast".equals(s)) return "Broadcast: " + Prefs.bcastAction(c);
        if ("setting".equals(s)) return "Ustawienie: " + Prefs.settingKey(c);
        return "Bateria Androida";
    }

    // pliki z napiÄciem w /sys/class/power_supply
    public static List<String> discoverFiles() {
        List<String> out = new ArrayList<String>();
        try {
            File[] nodes = new File("/sys/class/power_supply").listFiles();
            if (nodes == null) return out;
            for (File n : nodes) {
                File[] fs = n.listFiles();
                if (fs == null) continue;
                for (File f : fs) {
                    String nm = f.getName().toLowerCase(Locale.US);
                    if ((nm.contains("vol") || nm.contains("vbat")) && f.isFile() && f.canRead()) {
                        out.add(f.getAbsolutePath());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (out.size() > 25) return new ArrayList<String>(out.subList(0, 25));
        return out;
    }
}







