package pl.dock.widget;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// Wszystkie ustawienia. Zapisy zwiększają `version`, dzięki temu widżet i launcher
// mogą pominąć odświeżenie, gdy nic się nie zmieniło.
public class Prefs {

    // zwiększane przy każdym zapisie
    public static volatile int version = 0;

    private static List<AppEntry> appsCache;
    private static AppEntry cpCache;
    private static List<WidgetEntry> widgetsCache;

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("dock", Context.MODE_PRIVATE);
    }

    static SharedPreferences.Editor ed(Context c) {
        version++;
        return sp(c).edit();
    }

    // ---------- lista aplikacji (dowolna liczba) ----------
    public static synchronized List<AppEntry> apps(Context c) {
        if (appsCache == null) appsCache = loadApps(c);
        List<AppEntry> out = new ArrayList<AppEntry>(appsCache.size());
        for (AppEntry e : appsCache) out.add(e.copy());
        return out;
    }

    private static List<AppEntry> loadApps(Context c) {
        SharedPreferences s = sp(c);
        List<AppEntry> out = new ArrayList<AppEntry>();
        if (!s.contains("apps")) {
            out = defaultApps(c);
            writeApps(c, out);
            return out;
        }
        try {
            JSONArray a = new JSONArray(s.getString("apps", "[]"));
            for (int i = 0; i < a.length(); i++) {
                out.add(AppEntry.fromJson(a.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void writeApps(Context c, List<AppEntry> list) {
        JSONArray a = new JSONArray();
        for (AppEntry e : list) a.put(e.toJson());
        ed(c).putString("apps", a.toString()).apply();
    }

    public static synchronized void saveApps(Context c, List<AppEntry> list) {
        List<AppEntry> copy = new ArrayList<AppEntry>(list.size());
        for (AppEntry e : list) copy.add(e.copy());
        appsCache = copy;
        writeApps(c, copy);
    }

    public static AppEntry add(Context c, String pkg) {
        List<AppEntry> l = apps(c);
        AppEntry e = new AppEntry();
        e.id = System.currentTimeMillis();
        e.pkg = pkg;
        l.add(e);
        saveApps(c, l);
        return e;
    }

    public static void remove(Context c, long id) {
        List<AppEntry> l = apps(c);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id == id) {
                Icons.file(c, l.get(i)).delete();
                l.remove(i);
                break;
            }
        }
        saveApps(c, l);
    }

    // przesuwa wpis o jedną pozycję (-1 w górę, +1 w dół)
    public static void move(Context c, long id, int delta) {
        List<AppEntry> l = apps(c);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id == id) {
                int j = i + delta;
                if (j < 0 || j >= l.size()) return;
                AppEntry t = l.get(i);
                l.set(i, l.get(j));
                l.set(j, t);
                saveApps(c, l);
                return;
            }
        }
    }

    // zapis zmienionego wpisu (zwykłego albo CarPlay)
    public static void persist(Context c, AppEntry e) {
        if (e.id == 0) {
            saveCarplay(c, e);
            return;
        }
        List<AppEntry> l = apps(c);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id == e.id) {
                l.set(i, e);
                break;
            }
        }
        saveApps(c, l);
    }

    // ---------- CarPlay (zawsze na dole) ----------
    public static synchronized AppEntry carplay(Context c) {
        if (cpCache == null) {
            try {
                String s = sp(c).getString("cp", null);
                if (s != null) {
                    cpCache = AppEntry.fromJson(new JSONObject(s));
                    cpCache.id = 0;
                }
            } catch (Exception ignored) {
            }
            if (cpCache == null) {
                cpCache = defaultCarplay(c);
                ed(c).putString("cp", cpCache.toJson().toString()).apply();
            }
        }
        return cpCache.copy();
    }

    public static synchronized void saveCarplay(Context c, AppEntry e) {
        e.id = 0;
        cpCache = e.copy();
        ed(c).putString("cp", e.toJson().toString()).apply();
    }

    // ---------- ustawienia domyślne (wg nazw aplikacji z listy tabletu) ----------
    // Nie zgaduję nazw pakietów: aplikacje są szukane po etykiecie widocznej na liście aplikacji.
    static String pkgByLabel(Context c, String want) {
        try {
            PackageManager pm = c.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                if (ri.loadLabel(pm).toString().trim().equalsIgnoreCase(want)) {
                    return ri.activityInfo.packageName;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static List<AppEntry> defaultApps(Context c) {
        // pasek: equalizer, Bluetooth, OBD; lista dodatkowo: X-plore, Material Files
        String[][] spec = {{"DSP", "eq"}, {"Bluetooth", "bt"}, {"OBD2 Dashboard", "obdgear"},
                {"X-plore", "files"}, {"Material Files", "files"}};
        List<AppEntry> out = new ArrayList<AppEntry>();
        long t = System.currentTimeMillis();
        for (String[] s : spec) {
            String pkg = pkgByLabel(c, s[0]);
            if (pkg == null) continue;
            AppEntry e = new AppEntry();
            e.id = t++;
            e.pkg = pkg;
            e.mode = "builtin";
            e.iconPkg = s[1];
            out.add(e);
        }
        return out;
    }

    static AppEntry defaultCarplay(Context c) {
        AppEntry cp = new AppEntry();
        String pkg = pkgByLabel(c, "DiPlay");
        if (pkg != null) cp.pkg = pkg;
        cp.mode = "builtin";
        cp.iconPkg = "carplay";
        return cp;
    }

    public static void resetToDefaults(Context c) {
        saveApps(c, defaultApps(c));
        saveCarplay(c, defaultCarplay(c));
        ed(c).putString("sort", "last").apply();
    }

    // ---------- użycie ----------
    public static void recordUse(Context c, long id) {
        List<AppEntry> l = apps(c);
        for (AppEntry e : l) {
            if (e.id == id) {
                e.count++;
                e.last = System.currentTimeMillis();
                break;
            }
        }
        saveApps(c, l);
    }

    public static void resetStats(Context c) {
        List<AppEntry> l = apps(c);
        for (AppEntry e : l) {
            e.count = 0;
            e.last = 0;
        }
        saveApps(c, l);
    }

    // 3 górne aplikacje wg użycia w całym launcherze (ostatnio / najczęściej).
    // Wliczamy tez narzedzia Docka (OBD, Ustawienia) jako pseudo-pakiety, dzieki
    // czemu pojawiaja sie w dok-u, gdy kierowca czesto z nich korzysta.
    public static List<AppEntry> top(Context c, int n) {
        PackageManager pm = c.getPackageManager();
        String cp = diplayPkg(c);
        String me = c.getPackageName();
        List<AppEntry> ok = new ArrayList<AppEntry>();
        for (AppEntry e : apps(c)) {
            if (e.pkg.length() == 0 || e.pkg.equals(cp) || e.pkg.equals(me)
                    || e.pkg.equals("com.android.settings")) {
                continue;
            }
            if (!isVirtual(e.pkg) && !isAppSelected(c, e.pkg)) continue;
            if (isVirtual(e.pkg) || pm.getLaunchIntentForPackage(e.pkg) != null) ok.add(e);
        }
        final boolean byCount = "count".equals(sortMode(c));
        Collections.sort(ok, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                if (byCount) {
                    int x = Integer.compare(b.count, a.count);
                    if (x != 0) return x;
                }
                return Long.compare(b.last, a.last);
            }
        });
        return ok.size() > n ? new ArrayList<AppEntry>(ok.subList(0, n)) : ok;
    }

    // ostatnio użyte aplikacje (do przełącznika po długim dotknięciu siatki)
    public static List<AppEntry> recents(Context c, int n) {
        PackageManager pm = c.getPackageManager();
        String me = c.getPackageName();
        List<AppEntry> ok = new ArrayList<AppEntry>();
        for (AppEntry e : apps(c)) {
            if (e.pkg.length() == 0 || e.pkg.equals(me) || e.last == 0) continue;
            if (pm.getLaunchIntentForPackage(e.pkg) != null) ok.add(e);
        }
        Collections.sort(ok, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return Long.compare(b.last, a.last);
            }
        });
        return ok.size() > n ? new ArrayList<AppEntry>(ok.subList(0, n)) : ok;
    }

    // narzedzia Docka udawane jako aplikacje (OBD / Ustawienia) - brak pakietu systemowego
    public static boolean isVirtual(String pkg) {
        return "dock:obd".equals(pkg) || "dock:settings".equals(pkg);
    }

    private static void describeVirtual(AppEntry e, String pkg) {
        e.pkg = pkg;
        e.mode = "builtin";
        if ("dock:obd".equals(pkg)) {
            e.iconPkg = "honda";
        } else {
            e.iconPkg = "settings";
        }
    }

    // każde uruchomienie aplikacji z launchera trafia do statystyk (wpis powstaje sam).
    // Dla narzedzi Docka (dock:obd / dock:settings) wpis jest odtwarzany z ikona/nazwa,
    // gdyz nie maja pakietu systemowego do odczytania.
    public static synchronized void recordPkg(Context c, String pkg) {
        if (pkg == null || pkg.length() == 0) return;
        List<AppEntry> l = apps(c);
        long now = System.currentTimeMillis();
        for (AppEntry e : l) {
            if (e.pkg.equals(pkg)) {
                e.count++;
                e.last = now;
                if (isVirtual(pkg)) describeVirtual(e, pkg);
                saveApps(c, l);
                return;
            }
        }
        AppEntry e = new AppEntry();
        e.id = now + l.size();
        e.pkg = pkg;
        e.count = 1;
        e.last = now;
        if (isVirtual(pkg)) {
            describeVirtual(e, pkg);
        } else {
            try {
                PackageManager pm = c.getPackageManager();
                String label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                Overrides.O o = Overrides.match(label);
                if (o != null) {
                    e.mode = "builtin";
                    e.iconPkg = o.icon;
                }
            } catch (Throwable ignored) {
            }
        }
        l.add(e);
        saveApps(c, l);
    }

    public static String labelFor(Context c, String pkg) {
        if ("dock:obd".equals(pkg)) return "Honda OBD";
        if ("dock:settings".equals(pkg)) return "Ustawienia";
        if ("dock:carplay".equals(pkg)) return "CarPlay";
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return pkg != null ? pkg : "";
        }
    }

    // ---------- widżety launchera ----------
    public static synchronized List<WidgetEntry> widgets(Context c) {
        if (widgetsCache == null) {
            widgetsCache = new ArrayList<WidgetEntry>();
            try {
                JSONArray a = new JSONArray(sp(c).getString("widgets", "[]"));
                for (int i = 0; i < a.length(); i++) {
                    widgetsCache.add(WidgetEntry.fromJson(a.getJSONObject(i)));
                }
            } catch (Exception ignored) {
            }
        }
        List<WidgetEntry> out = new ArrayList<WidgetEntry>(widgetsCache.size());
        for (WidgetEntry e : widgetsCache) out.add(e.copy());
        return out;
    }

    public static synchronized void saveWidgets(Context c, List<WidgetEntry> list) {
        List<WidgetEntry> copy = new ArrayList<WidgetEntry>(list.size());
        JSONArray a = new JSONArray();
        for (WidgetEntry e : list) {
            copy.add(e.copy());
            a.put(e.toJson());
        }
        widgetsCache = copy;
        ed(c).putString("widgets", a.toString()).apply();
    }

    // ---------- ustawienia ----------
    public static String sortMode(Context c) {
        return sp(c).getString("sort", "last");   // last | count
    }

    public static void setSortMode(Context c, String v) {
        ed(c).putString("sort", v).apply();
    }

    public static String style(Context c) {
        return sp(c).getString("style", "classic");   // classic | glass
    }

    public static void setStyle(Context c, String v) {
        ed(c).putString("style", v).apply();
    }

    public static boolean followSystem(Context c) {
        return sp(c).getBoolean("follow", false);
    }

    public static void setFollowSystem(Context c, boolean v) {
        ed(c).putBoolean("follow", v).apply();
    }

    public static boolean night(Context c) {
        return sp(c).getBoolean("night", true);
    }

    public static void setNight(Context c, boolean v) {
        ed(c).putBoolean("night", v).apply();
    }

    public static boolean showVoltage(Context c) {
        return sp(c).getBoolean("voltage", false);
    }

    public static void setShowVoltage(Context c, boolean v) {
        ed(c).putBoolean("voltage", v).apply();
    }

    // ---------- przycisk siatki (tylko widżet) ----------
    public static String gridMode(Context c) {
        return sp(c).getString("grid", "dock");   // dock | system
    }

    public static void setGridMode(Context c, String v) {
        ed(c).putString("grid", v).apply();
    }

    // ---------- źródło napięcia ----------
    public static String voltSource(Context c) {
        return sp(c).getString("vsrc", "battery");   // battery | file | bcast | setting
    }

    public static void setVoltSource(Context c, String v) {
        ed(c).putString("vsrc", v).apply();
    }

    public static String voltPath(Context c) {
        return sp(c).getString("vpath", "/sys/class/power_supply/battery/voltage_now");
    }

    public static void setVoltPath(Context c, String v) {
        ed(c).putString("vpath", v).apply();
    }

    public static String bcastAction(Context c) {
        return sp(c).getString("bact", "");
    }

    public static String bcastExtra(Context c) {
        return sp(c).getString("bext", "");
    }

    public static void setBcast(Context c, String action, String extra) {
        ed(c).putString("bact", action).putString("bext", extra).apply();
    }

    public static float bcastVal(Context c) {
        return sp(c).getFloat("bval", 0f);
    }

    public static void setBcastVal(Context c, float v) {
        // wartość z broadcastu zmienia się często: nie zwiększamy `version`
        sp(c).edit().putFloat("bval", v).putLong("bts", System.currentTimeMillis()).apply();
    }

    // kiedy ostatnio przyszedł broadcast z napięciem (ms)
    public static long bcastTs(Context c) {
        return sp(c).getLong("bts", 0L);
    }

    public static String settingKey(Context c) {
        return sp(c).getString("skey", "");
    }

    public static void setSettingKey(Context c, String v) {
        ed(c).putString("skey", v).apply();
    }

    // ---------- tryb dzienny / nocny ----------
    // day | night | time
    public static String mode(Context c) {
        SharedPreferences s = sp(c);
        if (s.contains("mode")) return s.getString("mode", "night");
        return s.getBoolean("night", true) ? "night" : "day";
    }

    public static void setMode(Context c, String v) {
        ed(c).putString("mode", v).apply();
    }

    public static int nightStart(Context c) {
        return sp(c).getInt("nstart", 19 * 60);
    }

    public static int nightEnd(Context c) {
        return sp(c).getInt("nend", 7 * 60);
    }

    public static void setNightStart(Context c, int m) {
        ed(c).putInt("nstart", m).apply();
    }

    public static void setNightEnd(Context c, int m) {
        ed(c).putInt("nend", m).apply();
    }

    // ---------- ramka ikon ----------
    // none | ring | carplay
    public static String frame(Context c) {
        return sp(c).getString("frame", "carplay");
    }

    public static void setFrame(Context c, String v) {
        ed(c).putString("frame", v).apply();
    }

    // ---------- launcher ----------
    public static String dockSide(Context c) {
        return sp(c).getString("dockside", "left");   // left | right
    }

    public static void setDockSide(Context c, String v) {
        ed(c).putString("dockside", v).apply();
    }

    public static String wallpaper(Context c) {
        String w = sp(c).getString("wall", "graphite");
        return "navy".equals(w) ? "graphite" : w;
    }

    public static void setWallpaper(Context c, String v) {
        ed(c).putString("wall", v).apply();
    }

    public static boolean parallax(Context c) {
        return sp(c).getBoolean("parallax", true);
    }

    public static void setParallax(Context c, boolean v) {
        ed(c).putBoolean("parallax", v).apply();
    }

    // ograniczenie ruchu (bez animacji przewijania)
    public static boolean reduceMotion(Context c) {
        return sp(c).getBoolean("reduce", false);
    }

    public static void setReduceMotion(Context c, boolean v) {
        ed(c).putBoolean("reduce", v).apply();
    }

    public static boolean hideBars(Context c) {
        return sp(c).getBoolean("hidebars", true);
    }

    public static void setHideBars(Context c, boolean v) {
        ed(c).putBoolean("hidebars", v).apply();
    }

    public static boolean widgetsPage(Context c) {
        return sp(c).getBoolean("widgetspage", true);
    }

    public static void setWidgetsPage(Context c, boolean v) {
        ed(c).putBoolean("widgetspage", v).apply();
    }

    public static boolean gridOnlySelected(Context c) {
        return sp(c).getBoolean("gridsel", false);
    }

    public static void setGridOnlySelected(Context c, boolean v) {
        ed(c).putBoolean("gridsel", v).apply();
    }

    // ---------- Bluetooth w panelu łączności ----------
    // auto (przełącz, a jeśli się nie da – aplikacja) | app | toggle
    public static String btAction(Context c) {
        return sp(c).getString("btaction", "auto");
    }

    public static void setBtAction(Context c, String v) {
        ed(c).putString("btaction", v).apply();
    }

    public static String btPkg(Context c) {
        return sp(c).getString("btpkg", "com.autochips.bluetooth");
    }

    public static void setBtPkg(Context c, String v) {
        ed(c).putString("btpkg", v).apply();
    }

    // ---------- status CarPlay (aplikacja DiPlay) ----------
    private static volatile String diplayResolved;

    public static boolean cpShow(Context c) {
        return sp(c).getBoolean("cpshow", true);
    }

    public static void setCpShow(Context c, boolean v) {
        ed(c).putBoolean("cpshow", v).apply();
    }

    // pakiet aplikacji CarPlay: ustawiony ręcznie, albo znaleziony po etykiecie "DiPlay",
    // albo pakiet z dokumentacji projektu DiPlay
    public static String diplayPkg(Context c) {
        String s = sp(c).getString("cppkg", "");
        if (s.length() > 0) return s;
        String r = diplayResolved;
        if (r != null) return r;
        synchronized (Prefs.class) {
            if (diplayResolved != null) return diplayResolved;
            String f = pkgByLabel(c, "DiPlay");
            diplayResolved = f != null ? f : "com.shihab.diplay";
            return diplayResolved;
        }
    }

    public static void setDiplayPkg(Context c, String v) {
        ed(c).putString("cppkg", v).apply();
        diplayResolved = null;
    }

    /** Wywoływane z AppListWatcher, gdy lista pakietów się zmieniła. */
    public static void invalidateDiplayPkg() {
        diplayResolved = null;
    }

    public static boolean cpUseSession(Context c) {
        return sp(c).getBoolean("cpsession", true);
    }

    public static void setCpUseSession(Context c, boolean v) {
        ed(c).putBoolean("cpsession", v).apply();
    }

    public static boolean cpUseNotif(Context c) {
        return sp(c).getBoolean("cpnotif", true);
    }

    public static void setCpUseNotif(Context c, boolean v) {
        ed(c).putBoolean("cpnotif", v).apply();
    }

    public static boolean cpUseUsb(Context c) {
        return sp(c).getBoolean("cpusb", true);
    }

    public static void setCpUseUsb(Context c, boolean v) {
        ed(c).putBoolean("cpusb", v).apply();
    }

    // opcjonalny broadcast ze stanem CarPlay (jeśli DiPlay go wysyła)
    public static String cpAction(Context c) {
        return sp(c).getString("cpact", "");
    }

    public static String cpExtra(Context c) {
        return sp(c).getString("cpext", "");
    }

    public static void setCpBcast(Context c, String action, String extra) {
        ed(c).putString("cpact", action).putString("cpext", extra).apply();
    }

    public static String cpbVal(Context c) {
        return sp(c).getString("cpbval", "");
    }

    public static void setCpbVal(Context c, String v) {
        sp(c).edit().putString("cpbval", v).apply();
    }

    // ---------- źródło odtwarzania (widżet muzyki) ----------
    // pusty = aplikacja CarPlay (DiPlay), a gdy jej sesji nie ma – dowolna aktywna
    public static String mediaPkg(Context c) {
        return sp(c).getString("mediapkg", "");
    }

    public static void setMediaPkg(Context c, String v) {
        ed(c).putString("mediapkg", v).apply();
    }

    public static boolean widgetsSeeded(Context c) {
        return sp(c).getBoolean("wseeded", false);
    }

    public static void setWidgetsSeeded(Context c) {
        ed(c).putBoolean("wseeded", true).apply();
    }

    // ---------- skala interfejsu (niezależna od ustawień Androida) ----------
    public static float uiScale(Context c) {
        return sp(c).getFloat("uiscale", 1f);
    }

    public static void setUiScale(Context c, float v) {
        ed(c).putFloat("uiscale", v).apply();
    }

    // własne paski głośności i jasności oraz skróty trybu w panelu łączności
    public static boolean customBars(Context c) {
        return sp(c).getBoolean("custombars", true);
    }

    public static void setCustomBars(Context c, boolean v) {
        ed(c).putBoolean("custombars", v).apply();
    }

    public static boolean showModeButtons(Context c) {
        return sp(c).getBoolean("modebtns", true);
    }

    public static void setShowModeButtons(Context c, boolean v) {
        ed(c).putBoolean("modebtns", v).apply();
    }

    public static boolean showNightShift(Context c) {
        return sp(c).getBoolean("nightshift", true);
    }

    public static void setShowNightShift(Context c, boolean v) {
        ed(c).putBoolean("nightshift", v).apply();
    }

    // aktywność DiPlay wyświetlająca CarPlay ("" = automatycznie, "-" = domyślne uruchomienie)
    public static String cpActivity(Context c) {
        return sp(c).getString("cpact2", "");
    }

    public static void setCpActivity(Context c, String v) {
        ed(c).putString("cpact2", v).apply();
    }

    // miasto do pogody (Open-Meteo)
    public static String weatherCity(Context c) {
        return sp(c).getString("wcity", "Kraków");
    }

    public static void setWeatherCity(Context c, String v) {
        ed(c).putString("wcity", v).apply();
    }

    // ---------- wskaźnik Bluetooth: konkretny telefon ----------
    public static String btDevice(Context c) {
        return sp(c).getString("btdev", "");
    }

    public static String btDeviceName(Context c) {
        return sp(c).getString("btdevname", "");
    }

    public static void setBtDevice(Context c, String mac, String name) {
        ed(c).putString("btdev", mac).putString("btdevname", name).apply();
    }

    // ---------- Night Shift (filtr barwy ekranu wbudowany w launcher) ----------
    public static boolean nightShiftOn(Context c) {
        return sp(c).getBoolean("nson", false);
    }

    public static void setNightShiftOn(Context c, boolean v) {
        ed(c).putBoolean("nson", v).apply();
    }

    public static int nightShiftStrength(Context c) {
        return sp(c).getInt("nsval", 40);
    }

    public static void setNightShiftStrength(Context c, int v) {
        ed(c).putInt("nsval", v).apply();
    }

    // filtr włącza się sam razem z trybem nocnym
    public static boolean nightShiftAuto(Context c) {
        return sp(c).getBoolean("nsauto", false);
    }

    public static void setNightShiftAuto(Context c, boolean v) {
        ed(c).putBoolean("nsauto", v).apply();
    }

    // Tryb Night Shift: "off" | "on" | "sunset" (od zachodu do wschodu słońca).
    // Sunset korzysta z SolarTimes (lat/lon z "obd_settings" - te same co modul OBD).
    public static String nightShiftMode(Context c) {
        String m = sp(c).getString("nsmode", null);
        if (m == null) {
            m = nightShiftOn(c) ? (nightShiftAuto(c) ? "sunset" : "on") : "off";
        }
        return m;
    }

    public static void setNightShiftMode(Context c, String v) {
        boolean on = !"off".equals(v);
        ed(c).putString("nsmode", v)
                .putBoolean("nson", on)
                .putBoolean("nsauto", "sunset".equals(v))
                .apply();
    }

    // Centrum sterowania – gest od dołu i pozycja panelu
    public static boolean overlayEnabled(Context c) {
        return sp(c).getBoolean("cc_overlay", true);
    }
    public static void setOverlayEnabled(Context c, boolean v) {
        ed(c).putBoolean("cc_overlay", v).apply();
    }
    public static boolean overlayFollowGesture(Context c) {
        return sp(c).getBoolean("cc_follow", true);
    }
    public static void setOverlayFollowGesture(Context c, boolean v) {
        ed(c).putBoolean("cc_follow", v).apply();
    }

    // Animacja bounce przy wybudzeniu / otwarciu launchera (iOS-like)
    public static boolean wakeBounce(Context c) {
        return sp(c).getBoolean("wake_bounce", true);
    }
    public static void setWakeBounce(Context c, boolean v) {
        ed(c).putBoolean("wake_bounce", v).apply();
    }

    // ---------- przycisk Home na ekranie głównym launchera ----------
    // carplay | app | none
    public static String homeAction(Context c) {
        return sp(c).getString("homeact", "carplay");
    }

    public static void setHomeAction(Context c, String v) {
        ed(c).putString("homeact", v).apply();
    }

    public static String homeAppPkg(Context c) {
        return sp(c).getString("homeapp", "");
    }

    public static void setHomeAppPkg(Context c, String v) {
        ed(c).putString("homeapp", v).apply();
    }


    // ---------- WYBRANE aplikacje do siatki ----------
    // Format: JSON array pakietów. Pusta lista = wszystkie widoczne.
    private static List<String> selectedCache;

    public static synchronized List<String> selectedPkgs(Context c) {
        if (selectedCache != null) return new ArrayList<String>(selectedCache);
        String raw = sp(c).getString("selectedpkgs", null);
        if (raw == null) {
            selectedCache = new ArrayList<String>();
            return new ArrayList<String>();
        }
        List<String> out = new ArrayList<String>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {}
        selectedCache = out;
        return new ArrayList<String>(out);
    }

    public static synchronized void setSelectedPkgs(Context c, List<String> pkgs) {
        List<String> copy = new ArrayList<String>(pkgs);
        selectedCache = copy;
        JSONArray a = new JSONArray();
        for (String p : copy) a.put(p);
        ed(c).putString("selectedpkgs", a.toString()).apply();
    }

    public static boolean isAppSelected(Context c, String pkg) {
        List<String> sel = selectedPkgs(c);
        if (sel.isEmpty()) return true;
        return sel.contains(pkg);
    }

    // ---------- OSTATNIA STRONA ----------
    public static final String PAGE_WIDGETS = "widgets";
    public static final String PAGE_HOME = "home";
    public static final String PAGE_APPS = "apps";

    public static String lastPage(Context c) {
        return sp(c).getString("lastpage", PAGE_HOME);
    }

    public static void setLastPage(Context c, String page) {
        if (PAGE_WIDGETS.equals(page) || PAGE_HOME.equals(page) || PAGE_APPS.equals(page)) {
            sp(c).edit().putString("lastpage", page).apply();
        }
    }

    public static int lastPageIndex(Context c, int widgetsIdx, int homeIdx, int appsIdx) {
        String p = lastPage(c);
        if (PAGE_WIDGETS.equals(p) && widgetsIdx >= 0) return widgetsIdx;
        if (PAGE_APPS.equals(p) && appsIdx >= 0) return appsIdx;
        return homeIdx;
    }

    public static String externalCaller(Context c) {
        return sp(c).getString("extcaller", "");
    }

    public static void setExternalCaller(Context c, String pkg) {
        sp(c).edit().putString("extcaller", pkg == null ? "" : pkg).apply();
    }

    public static List<String> trustedCallers(Context c) {
        List<String> out = new ArrayList<String>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("trusted", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    public static void setTrustedCallers(Context c, List<String> pkgs) {
        JSONArray a = new JSONArray();
        for (String p : pkgs) a.put(p);
        ed(c).putString("trusted", a.toString()).apply();
    }

    public static String dspPkg(Context c) {
        String s = sp(c).getString("dsppkg", "");
        if (s.length() > 0) return s;
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            android.content.Intent main = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER);
            for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                String label = ri.loadLabel(pm).toString().toLowerCase();
                if (label.contains("dsp") || label.contains("equalizer") || label.contains("korektor")) {
                    return ri.activityInfo.packageName;
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public static void setDspPkg(Context c, String v) {
        ed(c).putString("dsppkg", v).apply();
    }


    // ---- App Library (Biblioteka aplikacji) ----
    public static boolean isHiddenInLibrary(Context c, String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        return sp(c).getBoolean("lib_hidden_" + pkg, false);
    }

    public static void setHiddenInLibrary(Context c, String pkg, boolean hidden) {
        if (pkg == null || pkg.length() == 0) return;
        ed(c).putBoolean("lib_hidden_" + pkg, hidden).apply();
    }


    // pakiety ukryte w Bibliotece aplikacji (do przywracania w Ustawieniach)
    public static java.util.List<String> hiddenInLibrary(Context c) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        java.util.Map<String, ?> all = sp(c).getAll();
        for (String k : all.keySet()) {
            if (k != null && k.startsWith("lib_hidden_")) {
                Object v = all.get(k);
                if (Boolean.TRUE.equals(v)) out.add(k.substring("lib_hidden_".length()));
            }
        }
        java.util.Collections.sort(out);
        return out;
    }
    // ---------- narzędzia w panelu łączności ----------
    // identyfikatory oddzielone przecinkami; null = układ domyślny, "" = pusty panel
    public static String panelTools(Context c) {
        return sp(c).getString("paneltools", null);
    }

    public static void setPanelTools(Context c, String v) {
        ed(c).putString("paneltools", v).apply();
    }

    // ---------- mapy offline (osmdroid) ----------
    // "map_online" = dokladanie kafli z internetu (gdy tablet jest online).
    // Domyslnie WLACZONE, zeby mapa od razu cos pokazywala. Mozna wylaczyc
    // w Ustawieniach -> Mapy i wtedy dziala tylko z pobranego obszaru (offline).
    public static boolean mapOnline(Context c) {
        return sp(c).getBoolean("map_online", true);
    }

    public static void setMapOnline(Context c, boolean v) {
        ed(c).putBoolean("map_online", v).apply();
    }

    // ostatnia pozycja/zoom mapy - zeby po powrocie mapa wracala w to samo miejsce
    public static String mapLastLat(Context c) {
        return sp(c).getString("map_lat", "");
    }

    public static String mapLastLon(Context c) {
        return sp(c).getString("map_lon", "");
    }

    public static float mapLastZoom(Context c) {
        return sp(c).getFloat("map_zoom", 16f);
    }

    public static void setMapLast(Context c, double lat, double lon, float zoom) {
        // Nie zapisujemy smieci: niezainicjalizowana mapa osmdroid oddaje srodek
        // (0,0) - "null wyspa" na Atlantyku, gdzie nie ma kafli (pusty ekran).
        if (!pl.dock.widget.maps.MapPlace.usable(lat, lon)) return;
        ed(c).putString("map_lat", String.valueOf(lat))
                .putString("map_lon", String.valueOf(lon))
                .putFloat("map_zoom", pl.dock.widget.maps.MapPlace.zoom(zoom, 16f))
                .apply();
    }

    /** Kasuje zapamietana pozycje mapy (Ustawienia -> Mapy). */
    public static void clearMapLast(Context c) {
        ed(c).remove("map_lat").remove("map_lon").remove("map_zoom").apply();
    }

    // wybrany styl kafli: "mapnik" (ulice) albo "topo" (teren)
    public static String mapStyle(Context c) {
        return sp(c).getString("map_style", "mapnik");
    }

    public static void setMapStyle(Context c, String v) {
        ed(c).putString("map_style", v).apply();
    }

    // auto-zoom i podazanie za pozycja GPS (jak w CarPlay)
    public static boolean mapFollow(Context c) {
        return sp(c).getBoolean("map_follow", true);
    }

    public static void setMapFollow(Context c, boolean v) {
        ed(c).putBoolean("map_follow", v).apply();
    }

    // "map_poland" = po wejsciu w mape pokazuj od razu cala Polske (gdy nie ma
    // zapisanej ostatniej pozycji). Domyslnie WLACZONE, mozna wylaczyc w Ustawieniach.
    public static boolean mapPoland(Context c) {
        return sp(c).getBoolean("map_poland", true);
    }

    public static void setMapPoland(Context c, boolean v) {
        ed(c).putBoolean("map_poland", v).apply();
    }

    // ---------- samoaktualizacja ----------
    /** Domyslny odstep sprawdzania aktualizacji w tle [min]. Tryb testowy = 1 min. */
    public static final int UPDATE_MIN_DEFAULT = 1;

    /** Co ile minut sprawdzac aktualizacje w tle (1 = tryb testowy). */
    public static int updateCheckMin(Context c) {
        int v = sp(c).getInt("update_check_min", UPDATE_MIN_DEFAULT);
        return v < 1 ? UPDATE_MIN_DEFAULT : v;
    }

    public static void setUpdateCheckMin(Context c, int min) {
        ed(c).putInt("update_check_min", min < 1 ? 1 : min).apply();
    }
}
