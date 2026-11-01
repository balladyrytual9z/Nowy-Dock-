
package pl.dock.widget.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import pl.dock.widget.Prefs;
import pl.dock.widget.circadian.CircadianBridge;

public final class BackupManager {
    public static final String VERSION = "1";

    public static String exportJson(Context c) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        root.put("package", c.getPackageName());
        root.put("timestamp", System.currentTimeMillis());
        SharedPreferences sp = c.getSharedPreferences("dock", Context.MODE_PRIVATE);
        JSONObject prefs = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            prefs.put(e.getKey(), String.valueOf(e.getValue()));
        }
        root.put("prefs", prefs);
        if (CircadianBridge.isInstalled(c)) {
            try {
                android.os.Bundle b = c.getContentResolver().call(
                        android.net.Uri.parse("content://" + CircadianBridge.AUTHORITY),
                        "get_profile_start", null, null);
                if (b != null) {
                    JSONObject circ = new JSONObject();
                    circ.put("start_minutes", b.getInt("start_minutes", 0));
                    circ.put("auto_night", b.getBoolean("auto_night", false));
                    circ.put("profile_name", b.getString("profile_name", ""));
                    root.put("circadian", circ);
                }
            } catch (Throwable ignored) {}
        }
        return root.toString(2);
    }

    public static boolean writeTo(Context c, Uri uri) {
        try (OutputStream os = c.getContentResolver().openOutputStream(uri)) {
            if (os == null) return false;
            os.write(exportJson(c).getBytes("UTF-8"));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean importFrom(Context c, Uri uri) {
        try (InputStream is = c.getContentResolver().openInputStream(uri)) {
            if (is == null) return false;
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line);
            JSONObject root = new JSONObject(b.toString());
            if (!VERSION.equals(root.optString("version"))) return false;
            JSONObject prefs = root.getJSONObject("prefs");
            SharedPreferences.Editor ed = c.getSharedPreferences("dock", Context.MODE_PRIVATE).edit();
            ed.clear();
            for (Iterator<String> it = prefs.keys(); it.hasNext(); ) {
                String k = it.next();
                String v = prefs.getString(k);
                if ("true".equals(v) || "false".equals(v)) {
                    ed.putBoolean(k, Boolean.parseBoolean(v));
                } else {
                    try {
                        ed.putInt(k, Integer.parseInt(v));
                    } catch (NumberFormatException nfe) {
                        try {
                            ed.putFloat(k, Float.parseFloat(v));
                        } catch (NumberFormatException nfe2) {
                            ed.putString(k, v);
                        }
                    }
                }
            }
            ed.apply();
            Prefs.version++;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private BackupManager() {}
}





