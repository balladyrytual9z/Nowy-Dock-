package pl.dock.widget.maps.route;

import java.util.Locale;

/**
 * Polskie opisy manewrow OSRM + formaty dystansu/czasu dla UI i syntezatora mowy.
 * Czysta logika bez Androida. Teksty ida na banner nawigacji i do TTS.
 */
public final class Maneuvers {

    private Maneuvers() {
    }

    /** Opis manewru po polsku, np. "Skręć w prawo na Marszałkowska". */
    public static String polish(String type, String modifier, String road) {
        String t = type == null ? "" : type;
        String m = modifier == null ? "" : modifier;
        String r = road == null ? "" : road.trim();

        if ("depart".equals(t)) return "Wyjazd" + on(r);
        if ("arrive".equals(t)) return "Dojazd do celu" + on(r);
        if ("turn".equals(t)) return cap(dir(m)) + on(r);
        if ("new name".equals(t) || "continue".equals(t)) return "Kontynuuj jazdę" + on(r);
        if ("merge".equals(t)) return "Włącz się" + on(r);
        if ("on ramp".equals(t)) return "Wjazd na drogę szybkiego ruchu" + on(r);
        if ("off ramp".equals(t)) return "Zjazd" + on(r);
        if ("fork".equals(t)) return "Na rozwidleniu " + dir(m);
        if ("end of road".equals(t)) return "Na końcu drogi " + dir(m);
        if ("roundabout".equals(t) || "rotary".equals(t) || "roundabout turn".equals(t)) {
            return "Na rondzie " + dir(m);
        }
        if ("exit roundabout".equals(t) || "exit rotary".equals(t)) return "Zjedź z ronda";
        if ("use lane".equals(t)) return "Ustaw się na pasie ruchu" + on(r);
        if ("notification".equals(t)) return "Uwaga" + on(r);
        return cap(dir(m)) + on(r);
    }

    private static String dir(String modifier) {
        if ("uturn".equals(modifier)) return "zawróć";
        if ("sharp right".equals(modifier)) return "skręć ostro w prawo";
        if ("right".equals(modifier)) return "skręć w prawo";
        if ("slight right".equals(modifier)) return "trzymaj się lekko w prawo";
        if ("slight left".equals(modifier)) return "trzymaj się lekko w lewo";
        if ("left".equals(modifier)) return "skręć w lewo";
        if ("sharp left".equals(modifier)) return "skręć ostro w lewo";
        return "jedź prosto";
    }

    private static String on(String road) {
        if (road.length() == 0) return "";
        return " na " + road;
    }

    private static String cap(String s) {
        if (s.length() == 0) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "300 m" / "1,2 km" - polski przecinek dziesietny. */
    public static String distText(double meters) {
        if (meters < 0d) return "?";
        if (meters >= 1000d) {
            String s = String.format(Locale.US, "%.1f", meters / 1000d);
            return s.replace('.', ',') + " km";
        }
        return String.valueOf(Math.round(meters)) + " m";
    }

    /** "4 min" (zaokraglenie, min. 1 min). */
    public static String minText(double seconds) {
        long m = Math.round(seconds / 60d);
        if (m < 1L) m = 1L;
        return m + " min";
    }
}
