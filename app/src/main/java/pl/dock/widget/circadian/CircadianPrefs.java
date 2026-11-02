package pl.dock.widget.circadian;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

// Wbudowany magazyn krzywej dobowej (odpowiednik Room z Circadian Display,
// ale w SharedPreferences - launcher nie uzywa AndroidX/Room).
// Krzywa zapisana jako ciag "t,w,d;t,w,d;..." (t = minuty od polnocy 0..1439).
public final class CircadianPrefs {

    private static final String FILE = "circadian_dock";

    private CircadianPrefs() {}

    public static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) {
        return sp(c).getBoolean("enabled", false);
    }

    public static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("enabled", on).apply();
    }

    // Punkty [timeMinutes, warmth, dimming], posortowane po czasie.
    public static List<float[]> points(Context c) {
        String s = sp(c).getString("curve", null);
        if (s == null) {
            seed(c);
            s = sp(c).getString("curve", "");
        }
        List<float[]> out = new ArrayList<>();
        if (s != null) {
            for (String part : s.split(";")) {
                String[] f = part.split(",");
                if (f.length < 3) continue;
                try {
                    int t = clamp(Math.round(Float.parseFloat(f[0].trim())), 0, 1439);
                    float w = clamp01(Float.parseFloat(f[1].trim()));
                    float d = clamp01(Float.parseFloat(f[2].trim()));
                    out.add(new float[]{t, w, d});
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    public static void setPoints(Context c, List<float[]> pts) {
        StringBuilder sb = new StringBuilder();
        for (float[] p : pts) {
            if (p == null || p.length < 3) continue;
            int t = clamp(Math.round(p[0]), 0, 1439);
            float w = clamp01(p[1]);
            float d = clamp01(p[2]);
            if (sb.length() > 0) sb.append(';');
            sb.append(t).append(',').append(w).append(',').append(d);
        }
        sp(c).edit().putString("curve", sb.toString()).apply();
    }

    // Domyslny profil "Wieczor" - 1:1 z SeedData.kt z Circadian Display:
    // 18:00 neutral, 20:00 subtelne cieplo, 22:00 mocne cieplo + przyciemnienie,
    // 23:00 maksimum.
    public static void seed(Context c) {
        if (sp(c).contains("curve")) return;
        List<float[]> evening = new ArrayList<>();
        evening.add(new float[]{1080, 0f, 0f});
        evening.add(new float[]{1200, 0.4f, 0.1f});
        evening.add(new float[]{1320, 0.8f, 0.3f});
        evening.add(new float[]{1380, 1f, 0.5f});
        setPoints(c, evening);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
