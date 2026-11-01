
package pl.dock.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skaner źródeł napięcia. Bez roota, bez GMS, bez natywnego kodu.
 * Każdy test jest osobno zabezpieczony; błędy trafiają do tabeli jako wiersze.
 * Nie zakłada żadnych nazw kluczy producenta.
 */
public class DiagScan {

    public static class Row {
        public String source = "";
        public String name = "";
        public String value = "";
        public String unit = "";
        public String status = "";
        public boolean probable;       // wynik 12–15 V (niepotwierdzony)
        public String useType;         // "file" | "setting" (można użyć w widżecie)
        public String useRef;

        Row(String s, String n, String v, String u, String st) {
            source = s;
            name = n;
            value = v;
            unit = u;
            status = st;
        }
    }

    private static final double MIN_V = 12.0;
    private static final double MAX_V = 15.0;
    private static final int MAX_SETTINGS_PER_SCOPE = 200;
    private static final int MAX_FILES = 700;
    private static final int MAX_PACKAGES = 400;
    private static final int MAX_SERVICES = 400;
    private static final int MAX_PROPS = 150;

    // ---------------------------------------------------------------- pomocnicze

    static String cut(String s, int n) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ');
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    static Row err(String source, String name, Throwable t) {
        String m = t.getMessage();
        return new Row(source, name, "", "",
                "BŁĄD: " + t.getClass().getSimpleName() + (m == null ? "" : ": " + cut(m, 140)));
    }

    static Double num(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(s);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean inRange(double v) {
        return v >= MIN_V && v <= MAX_V;
    }

    // jednostka znana: raw / divisor = wolty
    static void assessKnown(Row r, double raw, double divisor) {
        double v = raw / divisor;
        String vs = String.format(Locale.US, "%.2f V", v);
        if (inRange(v)) {
            r.probable = true;
            r.status = "= " + vs + " → PRAWDOPODOBNE napięcie instalacji (niepotwierdzone)";
        } else {
            r.status = "= " + vs + " → poza 12–15 V, brak podstaw by uznać za napięcie instalacji";
        }
    }

    // jednostka nieznana: sprawdź V, mV, µV
    static void assessUnknown(Row r, String value) {
        Double n = num(value);
        if (n == null) {
            r.status = "wartość bez liczby";
            return;
        }
        double[] div = {1.0, 1000.0, 1000000.0};
        String[] un = {"V", "mV", "µV"};
        StringBuilder hit = new StringBuilder();
        for (int i = 0; i < div.length; i++) {
            double v = n / div[i];
            if (inRange(v)) {
                hit.append("jeśli ").append(un[i]).append(" → ")
                        .append(String.format(Locale.US, "%.2f V", v)).append("; ");
            }
        }
        if (hit.length() > 0) {
            r.probable = true;
            r.status = hit + "PRAWDOPODOBNE napięcie instalacji (niepotwierdzone, jednostka nieznana)";
        } else {
            r.status = "żadna jednostka (V/mV/µV) nie daje 12–15 V";
        }
    }

    // ---------------------------------------------------------------- całość

    public static List<Row> run(Context c) {
        List<Row> out = new ArrayList<Row>();
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            try {
                battery(c, out);
            } catch (Throwable t) {
                out.add(err("ACTION_BATTERY_CHANGED", "(test)", t));
            }
            try {
                settings(c, out);
            } catch (Throwable t) {
                out.add(err("Settings", "(test)", t));
            }
            int[] budget = {MAX_FILES};
            try {
                files(out, "/sys/class/power_supply", "power_supply", pool, budget);
            } catch (Throwable t) {
                out.add(err("sysfs/power_supply", "(test)", t));
            }
            try {
                files(out, "/sys/class/hwmon", "hwmon", pool, budget);
            } catch (Throwable t) {
                out.add(err("sysfs/hwmon", "(test)", t));
            }
            try {
                props(out);
            } catch (Throwable t) {
                out.add(err("getprop", "(test)", t));
            }
            try {
                packages(c, out);
            } catch (Throwable t) {
                out.add(err("Pakiety", "(test)", t));
            }
            try {
                services(out);
            } catch (Throwable t) {
                out.add(err("ServiceManager", "(test)", t));
            }
        } finally {
            pool.shutdownNow();
        }
        return out;
    }

    // ---------------------------------------------------------------- 1. bateria

    private static void battery(Context c, List<Row> out) {
        String src = "ACTION_BATTERY_CHANGED";
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) {
            out.add(new Row(src, "(sticky broadcast)", "", "", "brak danych (null)"));
            return;
        }
        int mv = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        Row r = new Row(src, "EXTRA_VOLTAGE", String.valueOf(mv), "mV", "");
        if (mv > 0) {
            assessKnown(r, mv, 1000.0);
            r.status += " [to napięcie zgłasza system jako baterii]";
        } else {
            r.status = "brak odczytu (wartość " + mv + ")";
        }
        out.add(r);
        out.add(new Row(src, "EXTRA_LEVEL", String.valueOf(b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)),
                "%", "informacja"));
        out.add(new Row(src, "EXTRA_PLUGGED", String.valueOf(b.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)),
                "kod", "informacja"));
        out.add(new Row(src, "EXTRA_STATUS", String.valueOf(b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)),
                "kod", "informacja"));
        out.add(new Row(src, "EXTRA_TEMPERATURE",
                String.valueOf(b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)), "0,1 °C", "informacja"));
        out.add(new Row(src, "EXTRA_PRESENT", String.valueOf(b.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)),
                "", "informacja"));
    }

    // ---------------------------------------------------------------- 2. Settings

    private static boolean matches(String ln) {
        return ln.contains("voltage") || ln.contains("volt") || ln.contains("battery")
                || ln.contains("power") || ln.contains("car") || ln.contains("acc");
    }

    private static int prio(String ln) {
        if (ln.contains("volt")) return 0;
        if (ln.contains("battery") || ln.contains("power")) return 1;
        if (ln.contains("car")) return 2;
        return 3;
    }

    private static void settings(Context c, List<Row> out) {
        ContentResolver cr = c.getContentResolver();
        Uri[] uris = {Settings.System.CONTENT_URI, Settings.Global.CONTENT_URI,
                Settings.Secure.CONTENT_URI};
        String[] names = {"Settings.System", "Settings.Global", "Settings.Secure"};
        String[] scopes = {"system", "global", "secure"};
        for (int i = 0; i < uris.length; i++) {
            Cursor cur = null;
            try {
                cur = cr.query(uris[i], new String[]{"name", "value"}, null, null, null);
                if (cur == null) {
                    out.add(new Row(names[i], "(zapytanie)", "", "", "dostawca zwrócił null"));
                    continue;
                }
                List<String[]> hits = new ArrayList<String[]>();
                int total = 0;
                while (cur.moveToNext()) {
                    total++;
                    String n = cur.getString(0);
                    if (n == null) continue;
                    if (matches(n.toLowerCase(Locale.US))) {
                        String v = cur.getString(1);
                        hits.add(new String[]{n, v == null ? "" : cut(v, 160)});
                    }
                }
                Collections.sort(hits, new Comparator<String[]>() {
                    @Override
                    public int compare(String[] a, String[] b) {
                        int pa = prio(a[0].toLowerCase(Locale.US));
                        int pb = prio(b[0].toLowerCase(Locale.US));
                        if (pa != pb) return pa < pb ? -1 : 1;
                        return a[0].compareToIgnoreCase(b[0]);
                    }
                });
                out.add(new Row(names[i], "(podsumowanie)", "", "",
                        "przeskanowano " + total + " kluczy, pasuje " + hits.size()
                                + (hits.size() > MAX_SETTINGS_PER_SCOPE
                                ? " (pokazano " + MAX_SETTINGS_PER_SCOPE + ")" : "")));
                int shown = 0;
                for (String[] h : hits) {
                    if (shown++ >= MAX_SETTINGS_PER_SCOPE) break;
                    Row r = new Row(names[i], h[0], h[1], "?", "");
                    assessUnknown(r, h[1]);
                    r.useType = "setting";
                    r.useRef = scopes[i] + ":" + h[0];
                    out.add(r);
                }
            } catch (Throwable t) {
                out.add(err(names[i], "(zapytanie)", t));
            } finally {
                if (cur != null) {
                    try {
                        cur.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    // ręczny odczyt klucza (pkt 5)
    public static Row readSetting(ContentResolver cr, int scope, String key) {
        String[] names = {"Settings.System", "Settings.Global", "Settings.Secure"};
        String[] scopes = {"system", "global", "secure"};
        Row r = new Row("Ręcznie: " + names[scope], key, "", "?", "");
        try {
            String v;
            if (scope == 0) {
                v = Settings.System.getString(cr, key);
            } else if (scope == 1) {
                v = Settings.Global.getString(cr, key);
            } else {
                v = Settings.Secure.getString(cr, key);
            }
            if (v == null) {
                r.status = "klucz nie istnieje albo nie ma wartości";
            } else {
                r.value = cut(v, 160);
                assessUnknown(r, v);
                r.useType = "setting";
                r.useRef = scopes[scope] + ":" + key;
            }
        } catch (Throwable t) {
            String m = t.getMessage();
            r.status = "BŁĄD: " + t.getClass().getSimpleName() + (m == null ? "" : ": " + cut(m, 140));
        }
        return r;
    }

    // ---------------------------------------------------------------- 3. sysfs

    private static String unitFor(String kind, String ln) {
        if ("power_supply".equals(kind)) {
            if (ln.startsWith("voltage")) return "µV";
            if (ln.startsWith("current")) return "µA";
            if (ln.startsWith("temp")) return "0,1 °C";
            if (ln.equals("capacity")) return "%";
            if (ln.startsWith("charge")) return "µAh";
            if (ln.startsWith("energy")) return "µWh";
            if (ln.startsWith("power")) return "µW";
        } else {
            if (ln.matches("in\\d+_.*")) return "mV";
            if (ln.matches("temp\\d+_.*")) return "m°C";
            if (ln.matches("curr\\d+_.*")) return "mA";
            if (ln.matches("power\\d+_.*")) return "µW";
            if (ln.matches("fan\\d+_.*")) return "RPM";
        }
        return "";
    }

    private static String readFile(final File f, ExecutorService pool, String[] err) {
        Future<String> fu = pool.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                FileInputStream in = new FileInputStream(f);
                try {
                    byte[] buf = new byte[256];
                    int n = in.read(buf);
                    return n <= 0 ? "" : new String(buf, 0, n, "UTF-8");
                } finally {
                    in.close();
                }
            }
        });
        try {
            return fu.get(1500, TimeUnit.MILLISECONDS).trim().replace("\n", " | ");
        } catch (TimeoutException e) {
            fu.cancel(true);
            err[0] = "przekroczono czas odczytu (1,5 s)";
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            err[0] = cut(String.valueOf(c.getMessage() != null ? c.getMessage() : c.toString()), 140);
        } catch (Exception e) {
            err[0] = cut(e.toString(), 140);
        }
        return null;
    }

    private static void files(List<Row> out, String basePath, String kind, ExecutorService pool,
                              int[] budget) {
        String src = "sysfs/" + kind;
        File base = new File(basePath);
        File[] nodes = base.listFiles();
        if (nodes == null) {
            out.add(new Row(src, basePath, "", "",
                    base.exists() ? "nie można wylistować (uprawnienia / SELinux)" : "nie istnieje"));
            return;
        }
        Arrays.sort(nodes, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });
        for (File node : nodes) {
            if (node.isFile()) {
                readOne(out, src, kind, node.getName(), node, pool, budget);
                continue;
            }
            out.add(new Row(src, node.getName(), "", "", "katalog"));
            File[] fs = node.listFiles();
            if (fs == null) {
                out.add(new Row(src, node.getName() + "/", "", "",
                        "nie można wylistować (uprawnienia / SELinux)"));
                continue;
            }
            Arrays.sort(fs, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return a.getName().compareTo(b.getName());
                }
            });
            for (File f : fs) {
                String label = node.getName() + "/" + f.getName();
                if (f.isDirectory()) {
                    out.add(new Row(src, label, "", "", "podkatalog (nie schodzę głębiej)"));
                } else {
                    readOne(out, src, kind, label, f, pool, budget);
                }
            }
        }
    }

    private static void readOne(List<Row> out, String src, String kind, String label, File f,
                                ExecutorService pool, int[] budget) {
        String ln = f.getName().toLowerCase(Locale.US);
        String unit = unitFor(kind, ln);
        if (budget[0] <= 0) {
            out.add(new Row(src, label, "", unit, "pominięto (limit odczytów)"));
            return;
        }
        if (!f.canRead()) {
            out.add(new Row(src, label, "", unit, "brak prawa odczytu (uprawnienia)"));
            return;
        }
        budget[0]--;
        String[] err = new String[1];
        String v = readFile(f, pool, err);
        if (v == null) {
            out.add(new Row(src, label, "", unit, "BŁĄD: " + err[0]));
            return;
        }
        Row r = new Row(src, label, cut(v, 160), unit, "OK");
        boolean voltName = ln.contains("volt") || ln.contains("vbat") || ln.equals("batt_vol")
                || ("hwmon".equals(kind) && ln.matches("in\\d+_input"));
        Double n = num(v);
        if (voltName && n != null) {
            r.useType = "file";
            r.useRef = f.getAbsolutePath();
            if ("µV".equals(unit)) {
                assessKnown(r, n, 1000000.0);
            } else if ("mV".equals(unit)) {
                assessKnown(r, n, 1000.0);
            } else {
                assessUnknown(r, v);
            }
        }
        out.add(r);
    }

    // ---------------------------------------------------------------- właściwości systemu

    private static boolean propIdent(String lk) {
        return lk.equals("ro.product.manufacturer") || lk.equals("ro.product.brand")
                || lk.equals("ro.product.model") || lk.equals("ro.product.device")
                || lk.equals("ro.hardware") || lk.equals("ro.board.platform")
                || lk.equals("ro.build.display.id") || lk.equals("ro.build.fingerprint");
    }

    private static boolean propHit(String lk) {
        return lk.contains("volt") || lk.contains("batt") || lk.contains("power")
                || lk.contains("acc") || lk.contains("car") || lk.contains("mcu");
    }

    private static void props(List<Row> out) {
        Process p = null;
        BufferedReader r = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"getprop"});
            r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"), 8192);
            Pattern pat = Pattern.compile("^\\[(.*?)\\]: \\[(.*)\\]$");
            int hits = 0;
            int lines = 0;
            String line;
            while ((line = r.readLine()) != null && lines++ < 4000) {
                Matcher m = pat.matcher(line);
                if (!m.find()) continue;
                String k = m.group(1);
                String v = m.group(2);
                String lk = k.toLowerCase(Locale.US);
                if (propIdent(lk)) {
                    out.add(new Row("getprop (identyfikacja)", k, cut(v, 160), "", "informacja"));
                } else if (hits < MAX_PROPS && propHit(lk)) {
                    hits++;
                    Row row = new Row("getprop", k, cut(v, 160), "?", "");
                    if (lk.contains("volt")) {
                        assessUnknown(row, v);
                    } else {
                        row.status = "informacja";
                    }
                    out.add(row);
                }
            }
        } catch (Throwable t) {
            out.add(err("getprop", "(test)", t));
        } finally {
            try {
                if (r != null) r.close();
            } catch (Throwable ignored) {
            }
            if (p != null) p.destroy();
        }
    }

    // ---------------------------------------------------------------- 4. pakiety i usługi

    // Odfiltrowane są tylko standardowe pakiety Androida/Google; nazw producenta nie zakładam.
    private static boolean standardPackage(String pkg) {
        return pkg.equals("android") || pkg.startsWith("android.") || pkg.startsWith("com.android.")
                || pkg.startsWith("com.google.") || pkg.startsWith("org.chromium.");
    }

    private static String shortPath(String path) {
        if (path == null) return "?";
        String[] seg = path.split("/");
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String s : seg) {
            if (s.length() == 0) continue;
            b.append('/').append(s);
            if (++n >= 3) break;
        }
        return b.toString();
    }

    private static String shortName(String cls) {
        int i = cls.lastIndexOf('.');
        return i >= 0 ? cls.substring(i + 1) : cls;
    }

    private static void packages(Context c, List<Row> out) {
        PackageManager pm = c.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        Collections.sort(apps, new Comparator<ApplicationInfo>() {
            @Override
            public int compare(ApplicationInfo a, ApplicationInfo b) {
                return a.packageName.compareTo(b.packageName);
            }
        });
        int n = 0;
        for (ApplicationInfo ai : apps) {
            int sys = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
            if ((ai.flags & sys) == 0) continue;
            String pkg = ai.packageName;
            if (standardPackage(pkg) || pkg.equals(c.getPackageName())) continue;
            if (n >= MAX_PACKAGES) {
                out.add(new Row("Pakiet (systemowy)", "(limit)", "", "", "pominięto resztę – limit " + MAX_PACKAGES));
                break;
            }
            n++;
            String label = "";
            try {
                label = String.valueOf(pm.getApplicationLabel(ai));
            } catch (Throwable ignored) {
            }
            StringBuilder d = new StringBuilder();
            d.append(shortPath(ai.sourceDir));
            try {
                PackageInfo pi = pm.getPackageInfo(pkg,
                        PackageManager.GET_SERVICES | PackageManager.GET_PROVIDERS);
                if (pi.versionName != null) label = label + " " + pi.versionName;
                if (pi.sharedUserId != null) d.append("; uid=").append(pi.sharedUserId);
                if (pi.services != null && pi.services.length > 0) {
                    d.append("; usł.: ");
                    int k = 0;
                    for (ServiceInfo si : pi.services) {
                        if (k >= 4) break;
                        if (k > 0) d.append(", ");
                        d.append(shortName(si.name)).append(si.exported ? "*" : "");
                        k++;
                    }
                    if (pi.services.length > 4) d.append(" +").append(pi.services.length - 4);
                }
                if (pi.providers != null && pi.providers.length > 0) {
                    d.append("; dostawcy: ");
                    int k = 0;
                    for (ProviderInfo pr : pi.providers) {
                        if (k >= 3) break;
                        if (k > 0) d.append(", ");
                        d.append(pr.authority).append(pr.exported ? "*" : "");
                        k++;
                    }
                    if (pi.providers.length > 3) d.append(" +").append(pi.providers.length - 3);
                }
            } catch (Throwable t) {
                d.append("; (brak szczegółów: ").append(t.getClass().getSimpleName()).append(")");
            }
            out.add(new Row("Pakiet (systemowy)", pkg, cut(label, 80), "", cut(d.toString(), 320)));
        }
        out.add(new Row("Pakiet (systemowy)", "(podsumowanie)", "", "",
                "pokazano " + n + " pakietów spoza android/com.android/com.google; * = eksportowane"));
    }

    private static void services(List<Row> out) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method m = sm.getMethod("listServices");
            Object res = m.invoke(null);
            if (!(res instanceof String[])) {
                out.add(new Row("ServiceManager", "listServices", "", "", "brak listy"));
                return;
            }
            String[] names = (String[]) res;
            Arrays.sort(names);
            int n = 0;
            for (String s : names) {
                if (n++ >= MAX_SERVICES) break;
                out.add(new Row("ServiceManager", s, "", "", "zarejestrowana (bez możliwości zapytania)"));
            }
        } catch (Throwable t) {
            out.add(new Row("ServiceManager", "listServices", "", "",
                    "niedostępne dla zwykłej aplikacji na API 28 (" + t.getClass().getSimpleName() + ")"));
        }
    }
}







