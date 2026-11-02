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
        // Wbudowany Circadian (krzywa dobowe) - bezposrednio z magazynu Docka,
        // bez zadnej zewnetrznej aplikacji ani provider'a.
        try {
            SharedPreferences csp = pl.dock.widget.circadian.CircadianPrefs.sp(c);
            JSONObject circ = new JSONObject();
            for (Map.Entry<String, ?> e : csp.getAll().entrySet()) {
                circ.put(e.getKey(), String.valueOf(e.getValue()));
            }
            root.put("circadian", circ);
        } catch (Throwable ignored) {}
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
            JSONObject circ = root.optJSONObject("circadian");
            if (circ != null) {
                SharedPreferences.Editor ce = pl.dock.widget.circadian.CircadianPrefs.sp(c).edit();
                ce.clear();
                for (Iterator<String> it = circ.keys(); it.hasNext(); ) {
                    String k = it.next();
                    ce.putString(k, circ.getString(k));
                }
                ce.apply();
            }
            Prefs.version++;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private BackupManager() {}
}





