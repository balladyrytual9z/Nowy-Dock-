package pl.dock.widget.circadian;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Port CurveEngine z Circadian Display (core/curve) - czysta logika interpolacji.
// Punkty krzywej to tablice [timeMinutes, warmth, dimming]; warmth/dimming w 0..1.
// Zachowane wszystkie przypadki brzegowe oryginalu:
//  - pusta lista -> DisplayState(0,0)
//  - jeden punkt -> zawsze jego wartosci
//  - czas przed/po punktach skrajnych -> clamp
//  - punkt dokladnie na czasie -> dokladne wartosci
//  - duplikaty czasu -> po sortowaniu pierwszy wystapienie
//  - punkty nieposortowane -> sortowanie przed przetwarzaniem
public final class CircadianCurve {

    private CircadianCurve() {}

    // Zwraca {warmth, dimming} dla podanych punktow i czasu w minutach od polnocy.
    public static float[] evaluate(List<float[]> pts, int timeMinutes) {
        if (pts == null || pts.isEmpty()) return new float[]{0f, 0f};

        List<float[]> sorted = new ArrayList<>();
        for (float[] p : pts) {
            if (p != null && p.length >= 3) sorted.add(p);
        }
        if (sorted.isEmpty()) return new float[]{0f, 0f};

        Collections.sort(sorted, (a, b) -> Float.compare(a[0], b[0]));

        // deduplikacja po timeMinutes - pierwszy wystapien wygrywa
        List<float[]> uniq = new ArrayList<>();
        for (float[] p : sorted) {
            if (uniq.isEmpty() || (int) uniq.get(uniq.size() - 1)[0] != (int) p[0]) {
                uniq.add(p);
            }
        }

        float[] first = uniq.get(0);
        float[] last = uniq.get(uniq.size() - 1);
        if (timeMinutes <= (int) first[0]) return new float[]{first[1], first[2]};
        if (timeMinutes >= (int) last[0]) return new float[]{last[1], last[2]};

        for (int i = 1; i < uniq.size(); i++) {
            float[] a = uniq.get(i - 1);
            float[] b = uniq.get(i);
            if ((int) b[0] > timeMinutes) {
                float range = b[0] - a[0];
                float t = range == 0f ? 0f : (timeMinutes - a[0]) / range;
                return new float[]{lerp(a[1], b[1], t), lerp(a[2], b[2], t)};
            }
        }
        return new float[]{last[1], last[2]};
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
