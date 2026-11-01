package pl.dock.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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

    public static List<String> defaults(Context c) {
        List<String> l = new ArrayList<String>();
        l.add("air");
        l.add("hot");
        l.add("wifi");
        l.add("bt");
        if (Prefs.customBars(c)) {
            l.add("vol");
            l.add("bri");
        }
        l.add("rotate");
        l.add("mute");
        l.add("loc");
        if (Prefs.showNightShift(c)) l.add("nightshift");
        if (Prefs.showModeButtons(c)) l.add("mode");
        return l;
    }

    public static List<String> load(Context c) {
        init(c);
        String s = Prefs.panelTools(c);
        if (s == null) return defaults(c);
        List<String> out = new ArrayList<String>();
        if (s.length() == 0) return out;
        for (String id : s.split(",")) {
            id = id.trim();
            if (find(id) != null && !out.contains(id)) out.add(id);
        }
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

    private static final Paint GP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PA = new Path();
    private static final RectF RC = new RectF();

    private static void head(Canvas cv, float x, float y, float dirDeg, float len, Paint p) {
        double a = Math.toRadians(dirDeg);
        for (int k = -1; k <= 1; k += 2) {
            double b = a + Math.PI + k * 0.6;
            cv.drawLine(x, y, x + (float) (Math.cos(b) * len), y + (float) (Math.sin(b) * len), p);
        }
    }

    private static void speaker(Canvas cv, float cx, float cy, float s, float off, Paint p) {
        Path pa = PA;
        pa.reset();
        pa.moveTo(cx + off - s * 0.9f, cy - s * 0.4f);
        pa.lineTo(cx + off - s * 0.35f, cy - s * 0.4f);
        pa.lineTo(cx + off + s * 0.2f, cy - s * 0.9f);
        pa.lineTo(cx + off + s * 0.2f, cy + s * 0.9f);
        pa.lineTo(cx + off - s * 0.35f, cy + s * 0.4f);
        pa.lineTo(cx + off - s * 0.9f, cy + s * 0.4f);
        pa.close();
        Paint.Style old = p.getStyle();
        p.setStyle(Paint.Style.FILL);
        cv.drawPath(pa, p);
        p.setStyle(old);
    }

    /** Rysuje ikonę o danym kodzie w kółku o promieniu r wokół punktu (cx, cy). */
    public static void glyph(Canvas cv, String g, float cx, float cy, float r, int color) {
        Paint p = GP;
        RectF rc = RC;
        Path pa = PA;
        pa.reset();
        float s = r * 0.46f;
        float sw = Math.max(2f, r * 0.095f);
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        if (g == null) g = "gear";

        switch (g) {
            case "wifi":
                rc.set(cx - s, cy - s * 0.55f, cx + s, cy + s * 0.85f);
                cv.drawArc(rc, 210, 120, false, p);
                rc.set(cx - s * 0.62f, cy - s * 0.3f, cx + s * 0.62f, cy + s * 0.65f);
                cv.drawArc(rc, 210, 120, false, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy + s * 0.3f, sw * 0.9f, p);
                break;
            case "bt":
                pa.moveTo(cx, cy - s);
                pa.lineTo(cx, cy + s);
                pa.moveTo(cx, cy - s);
                pa.lineTo(cx + s * 0.7f, cy - s * 0.35f);
                pa.lineTo(cx, cy);
                pa.lineTo(cx + s * 0.7f, cy + s * 0.35f);
                pa.lineTo(cx, cy + s);
                pa.moveTo(cx - s * 0.55f, cy - s * 0.4f);
                pa.lineTo(cx, cy);
                pa.lineTo(cx - s * 0.55f, cy + s * 0.4f);
                cv.drawPath(pa, p);
                break;
            case "air":
                pa.moveTo(cx - s, cy);
                pa.lineTo(cx + s * 0.2f, cy - s * 0.15f);
                pa.lineTo(cx + s, cy);
                pa.lineTo(cx + s * 0.2f, cy + s * 0.15f);
                pa.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(pa, p);
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(cx - s * 0.6f, cy - s * 0.7f, cx + s * 0.1f, cy - s * 0.1f, p);
                cv.drawLine(cx - s * 0.6f, cy + s * 0.7f, cx + s * 0.1f, cy + s * 0.1f, p);
                break;
            case "hot":
                cv.drawCircle(cx, cy, s * 0.3f, p);
                rc.set(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f);
                cv.drawArc(rc, 200, 140, false, p);
                rc.set(cx - s, cy - s, cx + s, cy + s);
                cv.drawArc(rc, 200, 140, false, p);
                rc.set(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f);
                cv.drawArc(rc, 20, 140, false, p);
                break;
            case "rotate": {
                float R = s * 0.95f;
                rc.set(cx - R, cy - R, cx + R, cy + R);
                cv.drawArc(rc, -50, 270, false, p);
                double e = Math.toRadians(220);
                head(cv, cx + R * (float) Math.cos(e), cy + R * (float) Math.sin(e), 310, s * 0.5f, p);
                rc.set(cx - s * 0.32f, cy - s * 0.02f, cx + s * 0.32f, cy + s * 0.5f);
                cv.drawRoundRect(rc, s * 0.1f, s * 0.1f, p);
                rc.set(cx - s * 0.2f, cy - s * 0.4f, cx + s * 0.2f, cy + s * 0.1f);
                cv.drawArc(rc, 180, 180, false, p);
                break;
            }
            case "speaker":
                speaker(cv, cx, cy, s, -s * 0.25f, p);
                rc.set(cx - s * 0.35f, cy - s * 0.55f, cx + s * 0.45f, cy + s * 0.55f);
                cv.drawArc(rc, -50, 100, false, p);
                rc.set(cx - s * 0.2f, cy - s * 0.95f, cx + s * 0.85f, cy + s * 0.95f);
                cv.drawArc(rc, -50, 100, false, p);
                break;
            case "mute":
                speaker(cv, cx, cy, s, -s * 0.35f, p);
                cv.drawLine(cx + s * 0.3f, cy - s * 0.35f, cx + s * 0.95f, cy + s * 0.35f, p);
                cv.drawLine(cx + s * 0.3f, cy + s * 0.35f, cx + s * 0.95f, cy - s * 0.35f, p);
                break;
            case "loc": {
                float R = s * 0.62f;
                float py = cy - s * 0.3f;
                pa.moveTo(cx + R * 0.87f, py + R * 0.5f);
                pa.lineTo(cx, cy + s);
                pa.lineTo(cx - R * 0.87f, py + R * 0.5f);
                cv.drawPath(pa, p);
                rc.set(cx - R, py - R, cx + R, py + R);
                cv.drawArc(rc, 150, 240, false, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, py, sw * 0.8f, p);
                break;
            }
            case "flash":
                rc.set(cx - s * 0.55f, cy - s, cx + s * 0.55f, cy - s * 0.35f);
                cv.drawRoundRect(rc, s * 0.15f, s * 0.15f, p);
                rc.set(cx - s * 0.35f, cy - s * 0.35f, cx + s * 0.35f, cy + s);
                cv.drawRoundRect(rc, s * 0.15f, s * 0.15f, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy + s * 0.1f, sw * 0.7f, p);
                break;
            case "sun":
                cv.drawCircle(cx, cy, s * 0.42f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    cv.drawLine(cx + (float) Math.cos(a) * s * 0.72f, cy + (float) Math.sin(a) * s * 0.72f,
                            cx + (float) Math.cos(a) * s * 1.02f, cy + (float) Math.sin(a) * s * 1.02f, p);
                }
                break;
            case "moon": {
                Path m = new Path();
                m.addCircle(cx, cy, s, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(cx + s * 0.55f, cy - s * 0.4f, s * 0.85f, Path.Direction.CW);
                m.op(cut, Path.Op.DIFFERENCE);
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(m, p);
                break;
            }
            case "half":
                rc.set(cx - s, cy - s, cx + s, cy + s);
                cv.drawCircle(cx, cy, s, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawArc(rc, 90, 180, true, p);
                break;
            case "apps":
                p.setStyle(Paint.Style.FILL);
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        cv.drawCircle(cx + i * s * 0.62f, cy + j * s * 0.62f, sw * 0.75f, p);
                    }
                }
                break;
            case "storage":
                rc.set(cx - s, cy - s * 0.55f, cx + s, cy + s * 0.55f);
                cv.drawRoundRect(rc, s * 0.25f, s * 0.25f, p);
                cv.drawLine(cx - s, cy + s * 0.1f, cx + s, cy + s * 0.1f, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx + s * 0.6f, cy + s * 0.33f, sw * 0.6f, p);
                break;
            case "battery":
                rc.set(cx - s, cy - s * 0.5f, cx + s * 0.8f, cy + s * 0.5f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                cv.drawLine(cx + s, cy - s * 0.2f, cx + s, cy + s * 0.2f, p);
                p.setStyle(Paint.Style.FILL);
                rc.set(cx - s * 0.75f, cy - s * 0.25f, cx - s * 0.1f, cy + s * 0.25f);
                cv.drawRect(rc, p);
                break;
            case "clock":
                cv.drawCircle(cx, cy, s, p);
                cv.drawLine(cx, cy, cx, cy - s * 0.6f, p);
                cv.drawLine(cx, cy, cx + s * 0.45f, cy + s * 0.2f, p);
                break;
            case "globe":
                cv.drawCircle(cx, cy, s, p);
                rc.set(cx - s * 0.45f, cy - s, cx + s * 0.45f, cy + s);
                cv.drawOval(rc, p);
                cv.drawLine(cx - s, cy, cx + s, cy, p);
                break;
            case "keyboard":
                rc.set(cx - s, cy - s * 0.6f, cx + s, cy + s * 0.6f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                p.setStyle(Paint.Style.FILL);
                for (int i = -2; i <= 2; i++) {
                    cv.drawCircle(cx + i * s * 0.38f, cy - s * 0.25f, sw * 0.45f, p);
                }
                for (int i = -1; i <= 1; i++) {
                    cv.drawCircle(cx + i * s * 0.38f, cy + s * 0.02f, sw * 0.45f, p);
                }
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(cx - s * 0.5f, cy + s * 0.3f, cx + s * 0.5f, cy + s * 0.3f, p);
                break;
            case "access":
                cv.drawCircle(cx, cy - s * 0.75f, s * 0.25f, p);
                cv.drawLine(cx - s * 0.8f, cy - s * 0.2f, cx + s * 0.8f, cy - s * 0.2f, p);
                cv.drawLine(cx, cy - s * 0.2f, cx, cy + s * 0.3f, p);
                cv.drawLine(cx, cy + s * 0.3f, cx - s * 0.5f, cy + s, p);
                cv.drawLine(cx, cy + s * 0.3f, cx + s * 0.5f, cy + s, p);
                break;
            case "nfc": {
                float ox = cx - s * 0.7f;
                for (int i = 1; i <= 3; i++) {
                    float R = s * 0.5f * i;
                    rc.set(ox - R, cy - R, ox + R, cy + R);
                    cv.drawArc(rc, -40, 80, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(ox, cy, sw * 0.7f, p);
                break;
            }
            case "cast":
                rc.set(cx - s, cy - s * 0.8f, cx + s, cy + s * 0.8f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                for (int i = 1; i <= 3; i++) {
                    float R = s * 0.4f * i;
                    rc.set(cx - s - R, cy + s * 0.8f - R, cx - s + R, cy + s * 0.8f + R);
                    cv.drawArc(rc, -90, 90, false, p);
                }
                break;
            case "data":
                cv.drawLine(cx - s * 0.45f, cy + s * 0.8f, cx - s * 0.45f, cy - s * 0.8f, p);
                head(cv, cx - s * 0.45f, cy - s * 0.8f, 270, s * 0.45f, p);
                cv.drawLine(cx + s * 0.45f, cy - s * 0.8f, cx + s * 0.45f, cy + s * 0.8f, p);
                head(cv, cx + s * 0.45f, cy + s * 0.8f, 90, s * 0.45f, p);
                break;
            case "lock":
                rc.set(cx - s * 0.7f, cy - s * 0.1f, cx + s * 0.7f, cy + s * 0.9f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                rc.set(cx - s * 0.45f, cy - s * 0.95f, cx + s * 0.45f, cy + s * 0.15f);
                cv.drawArc(rc, 180, 180, false, p);
                break;
            case "code":
                pa.moveTo(cx - s * 0.3f, cy - s * 0.6f);
                pa.lineTo(cx - s * 0.9f, cy);
                pa.lineTo(cx - s * 0.3f, cy + s * 0.6f);
                pa.moveTo(cx + s * 0.3f, cy - s * 0.6f);
                pa.lineTo(cx + s * 0.9f, cy);
                pa.lineTo(cx + s * 0.3f, cy + s * 0.6f);
                pa.moveTo(cx + s * 0.15f, cy - s * 0.7f);
                pa.lineTo(cx - s * 0.15f, cy + s * 0.7f);
                cv.drawPath(pa, p);
                break;
            case "info":
                cv.drawCircle(cx, cy, s, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy - s * 0.42f, sw * 0.7f, p);
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(cx, cy - s * 0.05f, cx, cy + s * 0.55f, p);
                break;
            case "sync": {
                float R = s * 0.9f;
                rc.set(cx - R, cy - R, cx + R, cy + R);
                cv.drawArc(rc, 200, 140, false, p);
                cv.drawArc(rc, 20, 140, false, p);
                double e1 = Math.toRadians(340);
                head(cv, cx + R * (float) Math.cos(e1), cy + R * (float) Math.sin(e1), 70, s * 0.45f, p);
                double e2 = Math.toRadians(160);
                head(cv, cx + R * (float) Math.cos(e2), cy + R * (float) Math.sin(e2), 250, s * 0.45f, p);
                break;
            }
            case "user":
                cv.drawCircle(cx, cy - s * 0.45f, s * 0.38f, p);
                rc.set(cx - s * 0.8f, cy + s * 0.1f, cx + s * 0.8f, cy + s * 1.5f);
                cv.drawArc(rc, 180, 180, false, p);
                break;
            case "antenna": {
                float dy = cy + s * 0.6f;
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, dy, sw * 0.9f, p);
                p.setStyle(Paint.Style.STROKE);
                for (int i = 1; i <= 2; i++) {
                    float R = s * 0.65f * i;
                    rc.set(cx - R, dy - R, cx + R, dy + R);
                    cv.drawArc(rc, 230, 80, false, p);
                }
                break;
            }
            case "bell":
                rc.set(cx - s * 0.75f, cy - s * 0.95f, cx + s * 0.75f, cy + s * 0.55f);
                cv.drawArc(rc, 180, 180, false, p);
                cv.drawLine(cx - s * 0.75f, cy - s * 0.2f, cx - s * 0.95f, cy + s * 0.5f, p);
                cv.drawLine(cx + s * 0.75f, cy - s * 0.2f, cx + s * 0.95f, cy + s * 0.5f, p);
                cv.drawLine(cx - s * 0.95f, cy + s * 0.5f, cx + s * 0.95f, cy + s * 0.5f, p);
                rc.set(cx - s * 0.2f, cy + s * 0.6f, cx + s * 0.2f, cy + s * 0.95f);
                cv.drawArc(rc, 0, 180, false, p);
                break;
            case "star":
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + 36 * i);
                    float rad = (i % 2 == 0) ? s * 1.05f : s * 0.45f;
                    float x = cx + (float) Math.cos(a) * rad;
                    float y = cy + (float) Math.sin(a) * rad;
                    if (i == 0) pa.moveTo(x, y);
                    else pa.lineTo(x, y);
                }
                pa.close();
                cv.drawPath(pa, p);
                break;
            case "home":
                pa.moveTo(cx - s, cy);
                pa.lineTo(cx, cy - s);
                pa.lineTo(cx + s, cy);
                pa.moveTo(cx - s * 0.7f, cy - s * 0.1f);
                pa.lineTo(cx - s * 0.7f, cy + s * 0.9f);
                pa.lineTo(cx + s * 0.7f, cy + s * 0.9f);
                pa.lineTo(cx + s * 0.7f, cy - s * 0.1f);
                cv.drawPath(pa, p);
                break;
            case "image":
                rc.set(cx - s, cy - s * 0.8f, cx + s, cy + s * 0.8f);
                cv.drawRoundRect(rc, s * 0.2f, s * 0.2f, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx + s * 0.45f, cy - s * 0.3f, s * 0.18f, p);
                p.setStyle(Paint.Style.STROKE);
                pa.moveTo(cx - s * 0.8f, cy + s * 0.6f);
                pa.lineTo(cx - s * 0.2f, cy);
                pa.lineTo(cx + s * 0.2f, cy + s * 0.4f);
                pa.lineTo(cx + s * 0.5f, cy + s * 0.15f);
                pa.lineTo(cx + s * 0.8f, cy + s * 0.6f);
                cv.drawPath(pa, p);
                break;
            default: // "gear" i nieznane
                cv.drawCircle(cx, cy, s * 0.3f, p);
                cv.drawCircle(cx, cy, s * 0.62f, p);
                p.setStrokeWidth(sw * 1.5f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    cv.drawLine(cx + (float) Math.cos(a) * s * 0.7f, cy + (float) Math.sin(a) * s * 0.7f,
                            cx + (float) Math.cos(a) * s * 1.0f, cy + (float) Math.sin(a) * s * 1.0f, p);
                }
                break;
        }
    }
}
