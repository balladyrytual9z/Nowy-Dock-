
package pl.dock.widget;

import org.json.JSONObject;

public class AppEntry {
    public long id;            // 0 = aplikacja CarPlay (przypięta na dole)
    public String pkg = "";
    public String mode = "default";   // default | file | app | builtin
    public String iconPkg = "";       // dla "app": pakiet ikony, dla "builtin": id ikony
    public int count;
    public long last;
    public boolean orig;              // ikona dokładnie taka jak oryginalna (bez ramki)

    public String key() {
        return id == 0 ? "cp" : "e" + id;
    }

    public AppEntry copy() {
        AppEntry e = new AppEntry();
        e.id = id;
        e.pkg = pkg;
        e.mode = mode;
        e.iconPkg = iconPkg;
        e.count = count;
        e.last = last;
        e.orig = orig;
        return e;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("pkg", pkg);
            o.put("mode", mode);
            o.put("iconPkg", iconPkg);
            o.put("count", count);
            o.put("last", last);
            o.put("orig", orig);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static AppEntry fromJson(JSONObject o) {
        AppEntry e = new AppEntry();
        e.id = o.optLong("id", 0);
        e.pkg = o.optString("pkg", "");
        e.mode = o.optString("mode", "default");
        e.iconPkg = o.optString("iconPkg", "");
        e.count = o.optInt("count", 0);
        e.last = o.optLong("last", 0);
        e.orig = o.optBoolean("orig", false);
        return e;
    }
}







