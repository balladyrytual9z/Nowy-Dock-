package pl.dock.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Samoaktualizacja launchera z GitHub Releases - TYLKO przez WiFi.
 *
 * Sprawdza w tle CICHO (bez powiadomien i okien), domyslnie co minute
 * (Prefs.updateCheckMin; w Ustawieniach mozna przestawic na 10 min / 1 h / 6 h).
 * Gdy jest nowsza wersja: pobiera APK, weryfikuje SHA256 z SHA256SUMS.txt
 * i PROBUJE zainstalowac bez udzialu uzytkownika:
 *
 *   1. root (jesli urzadzenie ma "su"): "pm install -r" - calkowicie cicho,
 *   2. PackageInstaller: jesli system pozada potwierdzenia, sami otwieramy to okno
 *      (zostaje jedno dotkniecie); na czesci radiow OEM sesja idzie bez okna,
 *   3. klasyczny instalator systemowy (ACTION_VIEW) - ostatnia deska ratunku.
 *
 * Bez roota Android NIE pozwala zwyklej aplikacji instalowac pakietow po cichu -
 * jedno systemowe potwierdzenie jest wtedy nie do ominiecia.
 *
 * "Najnowsza wersja" idzie z kanalu Atom (releases.atom): GitHub API ma limit
 * 60 zapytan/h na IP, wiec przy sprawdzaniu co minute padlby po godzinie. API
 * pytamy tylko awaryjnie.
 */
public class Updater {

    private static final String FEED =
            "https://github.com/balladyrytual9z/Nowy-Dock-/releases.atom";
    private static final String LATEST =
            "https://api.github.com/repos/balladyrytual9z/Nowy-Dock-/releases/latest";
    /** Z jakim odstepem petla w tle pyta o nowa wersje (throttle w scheduleCheck). */
    private static final long POLL_MS = 60L * 1000L;
    /** Wynik instalacji wraca tym broadcastem (patrz installWithSession). */
    private static final String RESULT_ACTION = "pl.dock.widget.INSTALL_RESULT";
    private static volatile boolean busy = false;
    private static volatile boolean polling = false;
    /** Czy "su" dziala - sprawdzamy raz, bo proba kosztuje. */
    private static Boolean rootOk;

    /** true, gdy aktywna siec to WiFi (nie dane komorkowe). */
    public static boolean onWifi(Context c) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected()
                    && ni.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Odstep sprawdzania z Ustawien (domyslnie 1 min - tryb testowy). */
    private static long intervalMs(Context c) {
        return Prefs.updateCheckMin(c) * 60L * 1000L;
    }

    /**
     * Petla sprawdzania w tle. Launcher jest ekranem glownym, wiec zyje caly czas -
     * jeden lekki Runnable na minute wystarcza (bez AlarmManagera i budzenia tabletu).
     * Wolane raz z LauncherActivity.
     */
    public static void startPolling(Context c) {
        if (polling) return;
        polling = true;
        final Context app = c.getApplicationContext();
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    scheduleCheck(app);
                } catch (Throwable t) {
                    android.util.Log.w("Dock", "update poll", t);
                }
                h.postDelayed(this, POLL_MS);
            }
        }, POLL_MS);
    }

    /**
     * Wywolywane przy starcie i przy zmianie polaczenia (oraz z petli w tle).
     * Samo sprawdza WiFi i throttle, wiec mozna wolac czesto i bez skutkow ubocznych.
     */
    public static void scheduleCheck(Context c) {
        if (busy || !onWifi(c)) return;
        final Context app = c.getApplicationContext();
        SharedPreferences sp = app.getSharedPreferences("dock.update", 0);
        long now = System.currentTimeMillis();
        if (now - sp.getLong("last_check", 0) < intervalMs(app)) return;
        sp.edit().putLong("last_check", now).apply();
        busy = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    check(app, false);
                } catch (Throwable t) {
                    android.util.Log.w("Dock", "update", t);
                } finally {
                    busy = false;
                }
            }
        }).start();
    }

    /**
     * Reczne sprawdzenie aktualizacji z Ustawien - ignoruje przerwe.
     * Tylko tutaj dajemy komunikaty zwrotne; sprawdzanie w tle jest ciche.
     */
    public static void checkNow(Context c) {
        if (busy) {
            toast(c, "Sprawdzanie aktualizacji już trwa…");
            return;
        }
        if (!onWifi(c)) {
            toast(c, "Sprawdzanie tylko przez WiFi – podłącz WiFi");
            return;
        }
        final Context app = c.getApplicationContext();
        app.getSharedPreferences("dock.update", 0).edit()
                .putLong("last_check", System.currentTimeMillis()).apply();
        busy = true;
        toast(app, "Sprawdzam aktualizacje…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    check(app, true);
                } catch (Throwable t) {
                    android.util.Log.w("Dock", "update", t);
                    toast(app, "Nie udało się sprawdzić aktualizacji");
                } finally {
                    busy = false;
                }
            }
        }).start();
    }

    private static void check(Context c, boolean manual) throws Exception {
        Release rel = latest();
        if (rel == null) {
            if (manual) toast(c, "Nie udało się pobrać informacji o aktualizacji");
            return;
        }
        int local = localVersion(c);
        if (rel.version <= 0 || rel.version <= local) {
            if (manual) toast(c, "Masz najnowszą wersję Docka (" + localTag(c) + ")");
            return;   // brak nowszej wersji
        }
        String tag = rel.tag;
        String apkUrl = rel.apkUrl, shaUrl = rel.shaUrl;
        if (apkUrl == null) {
            if (manual) toast(c, "Release " + tag + " nie ma jeszcze pliku APK");
            return;
        }

        // oczekiwana suma kontrolna z SHA256SUMS.txt (pierwszy token w pliku)
        String expected = null;
        if (shaUrl != null) {
            try {
                String sums = new String(httpGet(shaUrl, 15000, 15000), "UTF-8");
                String[] tok = sums.trim().split("\\s+");
                if (tok.length > 0 && tok[0].length() >= 32) expected = tok[0].toLowerCase();
            } catch (Throwable ignored) {
            }
        }

        if (manual) toast(c, "Nowa wersja " + tag + " - pobieram przez WiFi...");
        byte[] apk = httpGet(apkUrl, 15000, 600000);
        String actual = sha256(apk);
        if (expected != null && !expected.equals(actual)) {
            if (manual) toast(c, "Aktualizacja: niezgodna suma SHA256, pobieranie anulowane");
            return;
        }

        // katalog pod serwowanie APK instalatorowi systemowemu (ApkProvider)
        File dir = new File(c.getCacheDir(), "updates");
        dir.mkdirs();
        File out = new File(dir, "update.apk");
        try {
            OutputStream os = new FileOutputStream(out);
            try {
                os.write(apk);
                os.flush();
            } finally {
                os.close();
            }
        } catch (Throwable t) {
            android.util.Log.w("Dock", "update write", t);
            return;
        }
        install(c, out);
    }

    /**
     * Najnowszy release: najpierw kanal Atom (bez limitu zapytan), awaryjnie API.
     * Zwraca null, gdy nie da sie ustalic wersji.
     */
    static Release latest() {
        try {
            String feed = new String(httpGet(FEED, 8000, 12000), "UTF-8");
            String tag = firstTag(feed);
            if (tag != null) {
                Release r = new Release();
                r.tag = tag;
                r.version = parseTag(tag);
                r.apkUrl = assetUrl(tag, apkName(tag));
                r.shaUrl = assetUrl(tag, "SHA256SUMS.txt");
                if (r.version > 0) return r;
            }
        } catch (Throwable t) {
            android.util.Log.w("Dock", "update feed", t);
        }
        // Awaryjnie GitHub API (limit 60 zapytan/h, dlatego tak rzadko jak sie da).
        try {
            JSONObject rel = httpJson(LATEST);
            if (rel == null) return null;
            Release r = new Release();
            r.tag = rel.optString("tag_name", "");
            r.version = parseTag(r.tag);
            JSONArray assets = rel.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String name = a.optString("name", "");
                if (name.endsWith(".apk")) r.apkUrl = a.optString("browser_download_url");
                if ("SHA256SUMS.txt".equals(name)) r.shaUrl = a.optString("browser_download_url");
            }
            return r;
        } catch (Throwable t) {
            android.util.Log.w("Dock", "update api", t);
            return null;
        }
    }

    /** Pierwszy tag z kanalu Atom - wpisy ida od najnowszego. */
    static String firstTag(String feed) {
        if (feed == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("releases/tag/([0-9A-Za-z._-]+)").matcher(feed);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Nazwa pliku APK w releasech - konwencja z CI: CarPlay-Launcher-<tag>.apk. */
    static String apkName(String tag) {
        return "CarPlay-Launcher-" + tag + ".apk";
    }

    static String assetUrl(String tag, String name) {
        return "https://github.com/balladyrytual9z/Nowy-Dock-/releases/download/"
                + tag + "/" + name;
    }

    /** Rozpoznany najnowszy release. */
    static final class Release {
        int version;
        String tag;
        String apkUrl;
        String shaUrl;
    }

    /**
     * Instalacja bez udzialu uzytkownika - kolejnosc od najlepszej do ostatniej.
     * Bez roota system ZAWSZE pokaze swoje potwierdzenie (tego nie da sie ominac).
     */
    private static void install(Context c, File apk) {
        // Android 9: raz trzeba zezwolic na "nieznane zrodla" dla tej aplikacji.
        // Bez tego zadna droga nie przejdzie - otwieramy Ustawienia (cicho) i
        // nastepnym razem pojdzie juz samo.
        try {
            if (!c.getPackageManager().canRequestPackageInstalls()) {
                c.getSharedPreferences("dock.update", 0).edit()
                        .putLong("last_check", 0L).apply();
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                i.setData(Uri.parse("package:" + c.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return;
            }
        } catch (Throwable ignored) {
        }
        if (installWithRoot(apk)) return;              // 1) root - calkiem cicho
        if (installWithSession(c, apk)) return;        // 2) PackageInstaller
        installViaView(c, apk);                        // 3) instalator systemowy
    }

    /** Instalacja przez root ("su -c pm install -r"). Bez roota zwraca false. */
    private static boolean installWithRoot(File apk) {
        if (rootOk == null) rootOk = Boolean.valueOf(probeRoot());
        if (!rootOk.booleanValue()) return false;
        try {
            Process p = new ProcessBuilder("su", "-c",
                    "pm install -r '" + apk.getAbsolutePath() + "'")
                    .redirectErrorStream(true).start();
            p.getOutputStream().close();
            String out = readAll(p, 90000L);
            p.waitFor();
            boolean ok = out != null
                    && out.toLowerCase(java.util.Locale.US).contains("success");
            if (!ok) android.util.Log.w("Dock", "root install: " + out);
            return ok;
        } catch (Throwable t) {
            android.util.Log.w("Dock", "root install", t);
            return false;
        }
    }

    /** Czy na urzadzeniu dziala "su" (root). Probujemy raz - wynik zapamietujemy. */
    private static boolean probeRoot() {
        try {
            Process p = new ProcessBuilder("su", "-c", "id")
                    .redirectErrorStream(true).start();
            p.getOutputStream().close();
            String out = readAll(p, 4000L);
            p.destroy();
            return out != null && out.contains("uid=0");
        } catch (Throwable t) {
            return false;
        }
    }

    /** Czyta wyjscie procesu z limitem czasu, zeby "su"/"pm" nie zawiesil watku. */
    private static String readAll(final Process p, long timeoutMs) {
        final StringBuilder sb = new StringBuilder();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream in = p.getInputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
                } catch (Throwable ignored) {
                }
            }
        });
        t.setDaemon(true);
        t.start();
        try {
            t.join(timeoutMs);
        } catch (InterruptedException ignored) {
        }
        return sb.toString();
    }

    /**
     * Instalacja przez PackageInstaller. Gdy system pozada potwierdzenia, dostajemy
     * STATUS_PENDING_USER_ACTION i sami otwieramy to okno - uzytkownik ma tylko jedno
     * dotkniecie. Gdy sesja przejdzie bez okna (czesc radiow OEM), instaluje sie
     * calkiem cicho. Przy bledzie ratujemy sie klasycznym instalatorem.
     */
    private static boolean installWithSession(final Context c, final File apk) {
        try {
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(c.getPackageName());
            // Uwaga: nie ma publicznego API "zainstaluj bez okna" - o tym, czy system
            // pokaze potwierdzenie, decyduje platforma (na czesci radiow OEM sesja
            // przechodzi bez okna, na czystym Androidzie nie). Dlatego NIE zakladamy
            // z gory, jaki bedzie wynik: obsluga STATUS_PENDING_USER_ACTION nizej
            // otwiera okno systemu sama, a STATUS_SUCCESS konczy sie po cichu.
            int sid = pi.createSession(p);
            PackageInstaller.Session s = pi.openSession(sid);
            OutputStream os = s.openWrite("dock", 0, apk.length());
            FileInputStream in = new FileInputStream(apk);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            in.close();
            os.flush();
            s.fsync(os);
            os.close();

            android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context ctx, Intent it) {
                    try {
                        ctx.unregisterReceiver(this);
                    } catch (Throwable ignored) {
                    }
                    int status = it.getIntExtra(PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE);
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        Intent pend = (Intent) it.getParcelableExtra(Intent.EXTRA_INTENT);
                        if (pend == null) return;
                        pend.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            ctx.startActivity(pend);
                        } catch (Throwable t) {
                            android.util.Log.w("Dock", "install confirm", t);
                            installViaView(ctx, apk);
                        }
                        return;
                    }
                    if (status != PackageInstaller.STATUS_SUCCESS) {
                        android.util.Log.w("Dock", "install status " + status);
                        installViaView(ctx, apk);
                    }
                    // STATUS_SUCCESS: system sam podmieni aplikacje - nic nie robimy
                }
            };
            c.registerReceiver(r, new android.content.IntentFilter(RESULT_ACTION));
            PendingIntent pd = PendingIntent.getBroadcast(c, 0,
                    new Intent(RESULT_ACTION).setPackage(c.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(pd.getIntentSender());
            s.close();
            return true;
        } catch (Throwable t) {
            android.util.Log.w("Dock", "install session", t);
            return false;
        }
    }

    /** Klasyczny instalator systemowy (jedno potwierdzenie) - ostatnia deska ratunku. */
    private static void installViaView(Context c, File apk) {
        try {
            Uri uri = Uri.parse("content://pl.dock.widget.apk/update.apk");
            Intent vi = new Intent(Intent.ACTION_VIEW);
            vi.setDataAndType(uri, "application/vnd.android.package-archive");
            vi.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(vi);
        } catch (Throwable t) {
            android.util.Log.w("Dock", "install view", t);
        }
    }

    // "v1.0.50" -> 50 ; versionCode CI = numer runu, wiek porownanie wystarczy
    static int parseTag(String tag) {
        try {
            String[] parts = tag.split("\\.");
            return Integer.parseInt(parts[parts.length - 1].replaceAll("[^0-9]", ""));
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String localTag(Context c) {
        try {
            return "v1.0." + c.getPackageManager()
                    .getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Throwable t) {
            return "";
        }
    }

    private static int localVersion(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static byte[] httpGet(String url, int connectMs, int readMs) throws Exception {
        HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
        cn.setInstanceFollowRedirects(true);
        cn.setConnectTimeout(connectMs);
        cn.setReadTimeout(readMs);
        cn.setRequestProperty("User-Agent", "Dock-Updater");
        try {
            InputStream in = cn.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toByteArray();
        } finally {
            cn.disconnect();
        }
    }

    private static JSONObject httpJson(String url) {
        try {
            return new JSONObject(new String(httpGet(url, 10000, 15000), "UTF-8"));
        } catch (Throwable t) {
            android.util.Log.w("Dock", "update api", t);
            return null;
        }
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void toast(final Context c, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
