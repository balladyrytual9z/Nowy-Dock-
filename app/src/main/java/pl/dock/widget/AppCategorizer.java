
package pl.dock.widget;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Automatic App Library categories (iOS-style) based on package + label heuristics. */
public final class AppCategorizer {

    public static final String CAT_SUGGESTIONS = "Sugestie";
    public static final String CAT_RECENT = "Ostatnio dodane";
    public static final String CAT_SOCIAL = "SpoÅecznoÅci";
    public static final String CAT_UTILITIES = "NarzÄdzia";
    public static final String CAT_ENTERTAINMENT = "Rozrywka";
    public static final String CAT_PRODUCTIVITY = "Biurowe i finansowe";
    public static final String CAT_NAVIGATION = "Nawigacja";
    public static final String CAT_OTHER = "Inne";

    private static final Set<String> SOCIAL_PKGS = new HashSet<String>(Arrays.asList(
            "com.facebook.katana", "com.instagram.android", "com.whatsapp", "org.telegram.messenger",
            "com.snapchat.android", "com.twitter.android", "com.zhiliaoapp.musically", "com.discord",
            "com.viber.voip", "com.skype.raider", "com.linkedin.android", "com.reddit.frontpage"
    ));

    private static final Set<String> UTILITIES_PKGS = new HashSet<String>(Arrays.asList(
            "com.android.settings", "com.android.vending", "com.google.android.apps.photos",
            "com.android.chrome", "com.google.android.calendar", "com.android.calculator2",
            "com.google.android.apps.maps", "com.google.android.gm", "com.android.documentsui",
            "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles"
    ));

    private static final Set<String> ENTERTAINMENT_PKGS = new HashSet<String>(Arrays.asList(
            "com.google.android.youtube", "com.spotify.music", "com.netflix.mediaclient",
            "com.google.android.apps.youtube.music", "tv.twitch.android.app",
            "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.hbo.hbonow"
    ));

    private static final Set<String> NAV_PKGS = new HashSet<String>(Arrays.asList(
            "com.google.android.apps.maps", "com.waze", "com.tomtom.gplay.navapp",
            "com.mapfactor.android", "com.here.app.maps", "com.sygic.aura"
    ));

    public static String getCategory(AppEntry e, String label) {
        if (e == null) return CAT_OTHER;
        String pkg = e.pkg == null ? "" : e.pkg.toLowerCase(Locale.US);
        String l = label == null ? "" : label.toLowerCase(Locale.US);

        if (SOCIAL_PKGS.contains(pkg) || l.contains("social") || l.contains("chat")
                || l.contains("messenger") || l.contains("whatsapp") || l.contains("telegram")
                || l.contains("facebook") || l.contains("instagram") || l.contains("discord")
                || l.contains("snapchat") || l.contains("viber") || l.contains("skype")
                || l.contains("spoÅecz") || l.contains("komunikator")) {
            return CAT_SOCIAL;
        }
        if (NAV_PKGS.contains(pkg) || l.contains("map") || l.contains("nav") || l.contains("gps")
                || l.contains("waze") || l.contains("nawig") || l.contains("mapa")
                || l.contains("tomtom") || l.contains("sygic")) {
            return CAT_NAVIGATION;
        }
        if (ENTERTAINMENT_PKGS.contains(pkg) || l.contains("tv") || l.contains("video")
                || l.contains("music") || l.contains("player") || l.contains("youtube")
                || l.contains("spotify") || l.contains("netflix") || l.contains("muzyka")
                || l.contains("rozryw") || l.contains("film") || l.contains("gra")
                || l.contains("game") || l.contains("play") || l.contains("stream")) {
            return CAT_ENTERTAINMENT;
        }
        if (UTILITIES_PKGS.contains(pkg) || l.contains("setting") || l.contains("tool")
                || l.contains("file") || l.contains("camera") || l.contains("galeri")
                || l.contains("kalkulator") || l.contains("calculator") || l.contains("narzÄd")
                || l.contains("ustawienia") || l.contains("galeria") || l.contains("aparat")
                || l.contains("latarka") || l.contains("flashlight") || l.contains("timer")
                || l.contains("zegar") || l.contains("clock")) {
            return CAT_UTILITIES;
        }
        if (l.contains("bank") || l.contains("office") || l.contains("pdf") || l.contains("mail")
                || l.contains("notes") || l.contains("notat") || l.contains("excel")
                || l.contains("word") || l.contains("docs") || l.contains("pÅatno")
                || l.contains("finance") || l.contains("finanse") || l.contains("pÅatnoÅÄ")
                || l.contains("przelew") || l.contains("wallet") || l.contains("portfel")) {
            return CAT_PRODUCTIVITY;
        }
        return CAT_OTHER;
    }

    private AppCategorizer() {}
}


