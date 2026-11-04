
package pl.dock.widget;

import java.util.Locale;

public class Overrides {
    public static class O {
        public final String label;
        public final String icon;
        O(String label, String icon) { this.label = label; this.icon = icon; }
    }

    public static O match(String label) {
        if (label == null) return null;
        String l = label.trim().toLowerCase(Locale.US);

        if (l.equals("diplay") || l.equals("zlink") || l.contains("carplay"))
            return new O("CarPlay", "carplay");

        if (l.equals("localsend")) return new O("AirDrop", "airdrop");

        if (l.contains("f-droid") || l.equals("fdroid") || l.equals("droidify")
                || l.contains("aurora store") || l.equals("aurora"))
            return new O("App Store", "appstore");

        if (l.equals("material files") || l.equals("pliki") || l.equals("x-plore")
                || l.equals("mi explorer") || l.contains("file manager")
                || l.contains("menedżer plików") || l.contains("menedzer plikow"))
            return new O("Pliki", "files");

        if (l.equals("obd") || l.contains("obd2") || l.contains("torque")
                || l.contains("car scanner") || l.contains("carinfo"))
            return new O(label, "honda");

        if (l.equals("chrome") || l.equals("brave") || l.contains("przeglądark")
                || l.contains("przegladark") || l.equals("browser") || l.equals("firefox"))
            return new O("Przeglądarka", "browser");

        if (l.contains("organic") || l.contains("waze") || l.contains("maps")
                || l.contains("mapy") || l.contains("here wego") || l.contains("sygic"))
            return new O("Nawigacja", "nav");

        if (l.contains("spotify") || l.contains("youtube music") || l.contains("tidal"))
            return new O("Muzyka", "music");
        if (l.contains("netflix") || l.contains("youtube") || l.contains("disney"))
            return new O("Wideo", "video");

        if (l.contains("camera") || l.contains("kamera") || l.contains("dashcam"))
            return new O("Kamera", "camera");

        return null;
    }
}





