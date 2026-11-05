package pl.dock.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Lista narzędzi i skrótów do ustawień, które można dodać do panelu łączności,
// oraz rysowanie ich ikon.
public final class PanelTool {

    public static final int K_TOGGLE = 0;    // przycisk ze stanem (włączony / wyłączony)
    public static final int K_SHORTCUT = 1;  // skrót do ekranu ustawień
    public static final int K_VOLUME = 2;    // suwak głośności (1 x 2)
    public static final int K_BRIGHT = 3;    // suwak jasności (1 x 2)
    public static final int K_MODE = 4;      // Jasny / Ciemny / Auto (3 x 1)
    public static final int K_APP = 5;       // skrót do aplikacji (id = "app:pakiet")
    public static final int K_GROUP = 6;     // grupa łączności 2x2 jak w iOS (samolotowy/hotspot/Wi-Fi/BT)

    public static final String CAT_TOOLS = "Narzędzia";
    public static final String CAT_SETTINGS = "Skróty do ustawień";
    public static final String CAT_APPS = "Aplikacje";

    public static final class Def {
        public final String id;
        public final String label;
        public final String glyph;
        public final String cat;
        public final String action;   // akcja ustawień; "@..." = skrót specjalny
        public final int kind;
        public final int w;
        public final int h;

        Def(String id, String label, String glyph, String cat, int kind, int w, int h, String action) {
            this.id = id;
            this.label = label;
            this.glyph = glyph;
            this.cat = cat;
            this.kind = kind;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }

    private static final List<Def> ALL = new ArrayList<Def>();
    private static boolean torchOn = false;

    private static void tool(String id, String label, String glyph, int kind, int w, int h) {
        ALL.add(new Def(id, label, glyph, CAT_TOOLS, kind, w, h, null));
    }

    private static void sc(String id, String label, String glyph, String action) {
        ALL.add(new Def(id, label, glyph, CAT_SETTINGS, K_SHORTCUT, 1, 1, action));
    }

    static {
        tool("air", "Tryb samolotowy", "air", K_TOGGLE, 1, 1);
        tool("hot", "Hotspot", "hot", K_TOGGLE, 1, 1);
        tool("wifi", "Wi-Fi", "wifi", K_TOGGLE, 1, 1);
        tool("bt", "Bluetooth", "bt", K_TOGGLE, 1, 1);
        tool("rotate", "Obrót ekranu", "rotate", K_TOGGLE, 1, 1);
        tool("mute", "Wyciszenie", "mute", K_TOGGLE, 1, 1);
        tool("flash", "Latarka", "flash", K_TOGGLE, 1, 1);
        tool("loc", "Lokalizacja", "loc", K_TOGGLE, 1, 1);
        tool("dark", "Tryb ciemny", "moon", K_TOGGLE, 1, 1);
        tool("nightshift", "Night Shift", "sun", K_TOGGLE, 1, 1);
        tool("vol", "Głośność", "speaker", K_VOLUME, 1, 2);
        tool("bri", "Jasność", "sun", K_BRIGHT, 1, 2);
        tool("mode", "Wygląd", "half", K_MODE, 3, 1);
        tool("conn", "Łączność", "wifi", K_GROUP, 2, 2);

        sc("s_settings", "Ustawienia", "gear", "android.settings.SETTINGS");
        sc("s_wifi", "Ustawienia Wi-Fi", "wifi", "android.settings.WIFI_SETTINGS");
        sc("s_bt", "Ustawienia Bluetooth", "bt", "android.settings.BLUETOOTH_SETTINGS");
        sc("s_btapp", "Aplikacja Bluetooth", "bt", "@btapp");
        sc("s_tether", "Ustawienia hotspotu", "hot", "@tether");
        sc("s_wireless", "Sieci bezprzewodowe", "antenna", "android.settings.WIRELESS_SETTINGS");
        sc("s_data", "Użycie danych", "data", "android.settings.DATA_USAGE_SETTINGS");
        sc("s_vpn", "VPN", "lock", "android.settings.VPN_SETTINGS");
        sc("s_cast", "Przesyłanie ekranu", "cast", "android.settings.CAST_SETTINGS");
        sc("s_nfc", "NFC", "nfc", "android.settings.NFC_SETTINGS");
        sc("s_loc", "Ustawienia lokalizacji", "loc", "android.settings.LOCATION_SOURCE_SETTINGS");
        sc("s_display", "Ekran", "sun", "android.settings.DISPLAY_SETTINGS");
        sc("s_night", "Podświetlenie nocne", "moon", "android.settings.NIGHT_DISPLAY_SETTINGS");
        sc("s_dream", "Wygaszacz ekranu", "image", "android.settings.DREAM_SETTINGS");
        sc("s_wall", "Tapeta", "image", "android.intent.action.SET_WALLPAPER");
        sc("s_sound", "Dźwięk", "speaker", "android.settings.SOUND_SETTINGS");
        sc("s_dnd", "Nie przeszkadzać", "bell", "android.settings.ZEN_MODE_SETTINGS");
        sc("s_apps", "Aplikacje", "apps", "android.settings.APPLICATION_SETTINGS");
        sc("s_allapps", "Wszystkie aplikacje", "apps", "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS");
        sc("s_defaults", "Aplikacje domyślne", "star", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS");
        sc("s_homeset", "Ekran główny", "home", "android.settings.HOME_SETTINGS");
        sc("s_storage", "Pamięć", "storage", "android.settings.INTERNAL_STORAGE_SETTINGS");
        sc("s_card", "Karta pamięci", "storage", "android.settings.MEMORY_CARD_SETTINGS");
        sc("s_battery", "Oszczędzanie energii", "battery", "android.settings.BATTERY_SAVER_SETTINGS");
        sc("s_date", "Data i godzina", "clock", "android.settings.DATE_SETTINGS");
        sc("s_lang", "Język", "globe", "android.settings.LOCALE_SETTINGS");
        sc("s_keyboard", "Klawiatura", "keyboard", "android.settings.INPUT_METHOD_SETTINGS");
        sc("s_access", "Ułatwienia dostępu", "access", "android.settings.ACCESSIBILITY_SETTINGS");
        sc("s_security", "Bezpieczeństwo", "lock", "android.settings.SECURITY_SETTINGS");
        sc("s_privacy", "Prywatność", "lock", "android.settings.PRIVACY_SETTINGS");
        sc("s_accounts", "Konta i synchronizacja", "user", "android.settings.SYNC_SETTINGS");
        sc("s_notif", "Dostęp do powiadomień", "bell", "android.settings.NOTIFICATION_LISTENER_SETTINGS");
        sc("s_usage", "Dostęp do użycia", "info", "android.settings.USAGE_ACCESS_SETTINGS");
        sc("s_dev", "Opcje programisty", "code", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS");
        sc("s_about", "Informacje o urządzeniu", "info", "android.settings.DEVICE_INFO_SETTINGS");
        sc("s_dock", "Ustawienia Docka", "gear", "@dock");
    }

    private PanelTool() {
    }

    public static List<Def> all() {
        return ALL;
    }

    private static Context appCtx;
    private static final Map<String, Def> APP_DEFS = new HashMap<String, Def>();

    public static void init(Context c) {
        if (c != null) appCtx = c.getApplicationContext();
    }

    public static Def find(String id) {
        if (id == null) return null;
        if (id.startsWith("app:")) return appDef(id.substring(4));
        for (Def d : ALL) {
            if (d.id.equals(id)) return d;
        }
        return null;
    }

    // skrót do zainstalowanej aplikacji; null, gdy jej nie ma albo nie da się jej uruchomić
    public static Def appDef(String pkg) {
        if (pkg == null || pkg.length() == 0 || appCtx == null) return null;
        Def d = APP_DEFS.get(pkg);
        if (d != null) return d;
        try {
            PackageManager pm = appCtx.getPackageManager();
            if (pm.getLaunchIntentForPackage(pkg) == null) return null;
            String label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            d = new Def("app:" + pkg, label, "app", CAT_APPS, K_APP, 1, 1, pkg);
            APP_DEFS.put(pkg, d);
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    // wszystkie aplikacje z ikoną w launcherze, alfabetycznie
    public static List<Def> installedApps(Context c) {
        init(c);
        List<Def> out = new ArrayList<Def>();
        try {
            PackageManager pm = c.getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
            List<String> seen = new ArrayList<String>();
            for (ResolveInfo ri : ris) {
                String pkg = ri.activityInfo.packageName;
                if (seen.contains(pkg) || pkg.equals(c.getPackageName())) continue;
                seen.add(pkg);
                Def d = appDef(pkg);
                if (d != null) out.add(d);
            }
        } catch (Throwable ignored) {
        }
        Collections.sort(out, new Comparator<Def>() {
            @Override
            public int compare(Def a, Def b) {
                return a.label.toLowerCase(Locale.getDefault()).compareTo(b.label.toLowerCase(Locale.getDefault()));
            }
        });
        return out;
    }

    // uruchamia aplikację ze skrótu
    public static boolean launchApp(Context c, Def d) {
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(d.action);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // czy to urządzenie ma taką funkcję / taki ekran ustawień
    public static boolean available(Context c, Def d) {
        try {
            PackageManager pm = c.getPackageManager();
            if (d.kind == K_SHORTCUT) {
                if (d.action.startsWith("@")) return true;
                Intent i = new Intent(d.action);
                return i.resolveActivity(pm) != null;
            }
            if ("flash".equals(d.id)) {
                return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    // ---------------------------------------------------------------- zapis układu

    // Panel ma staly modul bazowy (suwaki, Night Shift, tryb wygladu, Wi-Fi/BT/hotspot),
    // wiec domyslne dodatkowe narzedzia w siatce to tylko pozostale przelaczniki.
    public static List<String> defaults(Context c) {
        List<String> l = new ArrayList<String>();
        l.add("rotate");
        l.add("mute");
        l.add("loc");
        return l;
    }

    public static List<String> load(Context c) {
        init(c);
        String s = Prefs.panelTools(c);
        if (s == null) return defaults(c);
        s = s.trim();
        // pusty zapis = przywroc domyslne narzedzia; panel nigdy nie jest calkiem pusty
        if (s.length() == 0) return defaults(c);
        List<String> out = new ArrayList<String>();
        for (String id : s.split(",")) {
            id = id.trim();
            if (find(id) != null && !out.contains(id)) out.add(id);
        }
        // stary/uszkodzony zapis bez kafelkow = przywroc domyslne narzedzia
        if (out.isEmpty()) return defaults(c);
        return out;
    }

    public static void save(Context c, List<String> ids) {
        StringBuilder b = new StringBuilder();
        for (String id : ids) {
            if (b.length() > 0) b.append(',');
            b.append(id);
        }
        Prefs.setPanelTools(c, b.toString());
    }

    // ---------------------------------------------------------------- stan

    public static boolean isOn(Context c, Def d) {
        try {
            ContentResolver cr = c.getContentResolver();
            String id = d.id;
            if ("air".equals(id)) {
                return Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
            } else if ("hot".equals(id)) {
                return Net.tethering(c);
            } else if ("wifi".equals(id)) {
                return Net.wifiEnabled(c);
            } else if ("bt".equals(id)) {
                return Net.bluetoothOn();
            } else if ("rotate".equals(id)) {
                return Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
            } else if ("mute".equals(id)) {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                return am.isStreamMute(AudioManager.STREAM_MUSIC)
                        || am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
            } else if ("flash".equals(id)) {
                return torchOn;
            } else if ("loc".equals(id)) {
                return Settings.Secure.getInt(cr, Settings.Secure.LOCATION_MODE, 0) != 0;
            } else if ("dark".equals(id)) {
                return Look.night(c);
            } else if ("nightshift".equals(id)) {
                return NightShift.isOn(c);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static int onColor(Def d) {
        if ("hot".equals(d.id)) return 0xFF30D158;
        if ("nightshift".equals(d.id)) return 0xFFFF9F0A;
        if ("mute".equals(d.id)) return 0xFFFF453A;
        return 0xFF0A84FF;
    }

    // włącza / wyłącza latarkę (bez zgody na aparat); false = nie udało się
    public static boolean toggleTorch(Context c) {
        try {
            CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                Boolean f = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) {
                    boolean next = !torchOn;
                    cm.setTorchMode(id, next);
                    torchOn = next;
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ---------------------------------------------------------------- ikony

    /**
     * Rysuje ikone o danym kodzie w okregu o promieniu r wokol punktu (cx, cy).
     *
     * Rysowanie mieszka w Glyphs - to samo zrodlo prawdy obsluguje panel lacznosci
     * i ikony pod zegarem w doku. Wczesniej dok bral ikony z res/drawable/ic_*.xml
     * przez Icons.vector() z filtrem PorterDuff.SRC_IN, ktory na docelowym sprzecie
     * (Android 9 / Mali-T720) gubil obrysowe wektory i ikony nie pojawialy sie.
     */
    public static void glyph(Canvas cv, String g, float cx, float cy, float r, int color) {
        Glyphs.draw(cv, g, cx, cy, r, color);
    }
}
