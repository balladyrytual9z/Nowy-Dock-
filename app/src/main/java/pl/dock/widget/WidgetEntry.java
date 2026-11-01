
package pl.dock.widget;

import org.json.JSONObject;

// WidÅ¼et systemowy Androida umieszczony w launcherze
public class WidgetEntry {
    public int id;                 // appWidgetId
    public String where = "home";  // home | s1 | s2 | s3
    public float x = 24, y = 24, w = 220, h = 160;   // dp, tylko dla "home"

    public boolean isStack() {
        return where.length() == 2 && where.charAt(0) == 's';
    }

    public WidgetEntry copy() {
        WidgetEntry e = new WidgetEntry();
        e.id = id;
        e.where = where;
        e.x = x;
        e.y = y;
        e.w = w;
        e.h = h;
        return e;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("where", where);
            o.put("x", x);
            o.put("y", y);
            o.put("w", w);
            o.put("h", h);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static WidgetEntry fromJson(JSONObject o) {
        WidgetEntry e = new WidgetEntry();
        e.id = o.optInt("id", -1);
        e.where = o.optString("where", "home");
        e.x = (float) o.optDouble("x", 24);
        e.y = (float) o.optDouble("y", 24);
        e.w = (float) o.optDouble("w", 220);
        e.h = (float) o.optDouble("h", 160);
        return e;
    }
}







