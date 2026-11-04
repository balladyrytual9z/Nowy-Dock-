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
 * Po lacznym starcie i po kazdym podlaczeniu WiFi sprawdza najnowszy release
 * (co najmniej 6 h przerwy miedzy sprawdzaniami). Gdy jest nowa wersja:
 * pobiera APK, weryfikuje sume SHA256 z SHA256SUMS.txt i instaluje przez
 * PackageInstaller (system pokazuje jedno potwierdzenie instalacji).
 */
public class Updater {

    private static final String LATEST =
            "https://api.github.com/repos/balladyrytual9z/Nowy-Dock-/releases/latest";
    private static final long CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000;   // 6 h
    private static volatile boolean busy = false;

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

    /**
     * Wywolywane przy starcie i przy zmianie polaczenia. Samo sprawdza WiFi
     * i throttle, wiec mozna wolac czesto i bez skutkow ubocznych.
     */
    public static void scheduleCheck(Context c) {
        if (busy || !onWifi(c)) return;
        final Context app = c.getApplicationContext();
        SharedPreferences sp = app.getSharedPreferences("dock.update", 0);
        long now = System.currentTimeMillis();
        if (now - sp.getLong("last_check", 0) < CHECK_INTERVAL_MS) return;
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
     * Reczne sprawdzenie aktualizacji z Ustawien - ignoruje przerwe 6 h.
     * Daje komunikaty zwrotne: najnowsza wersja / pobieram / blad.
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
        JSONObject rel = httpJson(LATEST);
        if (rel == null) {
            if (manual) toast(c, "Nie udało się pobrać informacji o aktualizacji");
            return;
        }
        String tag = rel.optString("tag_name", "");
        int remote = parseTag(tag);
        int local = localVersion(c);
        if (remote <= 0 || remote <= local) {
            if (manual) toast(c, "Masz najnowszą wersję Docka (" + localTag(c) + ")");
            return;   // brak nowszej wersji
        }

        String apkUrl = null, shaUrl = null;
        JSONArray assets = rel.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "");
            if (name.endsWith(".apk")) apkUrl = a.optString("browser_download_url");
            if ("SHA256SUMS.txt".equals(name)) shaUrl = a.optString("browser_download_url");
        }
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

        toast(c, "Nowa wersja " + tag + " - pobieram przez WiFi...");
        byte[] apk = httpGet(apkUrl, 15000, 600000);
        String actual = sha256(apk);
        if (expected != null && !expected.equals(actual)) {
            toast(c, "Aktualizacja: niezgodna suma SHA256, pobieranie anulowane");
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

    private static void install(Context c, File apk) {
        try {
            PackageManager pm = c.getPackageManager();
            // Android 9: raz trzeba zezwolic na "nieznane zrodla" dla tej aplikacji
            if (!pm.canRequestPackageInstalls()) {
                // NIE zuzyczaj przerwy 6 h: gdy uzytkownik cofnie sie bez nadania
                // zgody, przy nastepnym starcie/WiFi spróbuj od razu ponownie
                c.getSharedPreferences("dock.update", 0).edit().putLong("last_check", 0L).apply();
                toast(c, "Aktualizacja gotowa - zezwol na instalacje aplikacji Dock");
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    i.setData(Uri.parse("package:" + c.getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(i);
                } catch (Throwable ignored) {
                    android.util.Log.w("Dock", "unknown sources", ignored);
                }
                return;
            }
            // 1) Klasyczny instalator systemowy (ACTION_VIEW przez wlasny ApkProvider).
            // Na radiach OEM z Androidem 9 okno potwierdzenia PackageInstallera czesto
            // w ogole sie nie pokazuje; pelny instalator systemowy otwiera sie pewnie.
            try {
                Uri uri = Uri.parse("content://pl.dock.widget.apk/update.apk");
                Intent vi = new Intent(Intent.ACTION_VIEW);
                vi.setDataAndType(uri, "application/vnd.android.package-archive");
                vi.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(vi);
                toast(c, "Aktualizacja pobrana - potwierdz instalacje");
                return;
            } catch (Throwable t) {
                android.util.Log.w("Dock", "install view", t);
            }
            // 2) Awaryjnie: PackageInstaller (jak dotychczas)
            PackageInstaller pi = pm.getPackageInstaller();
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
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
            // po instalacji wracamy do launchera
            Intent done = new Intent(c, LauncherActivity.class);
            PendingIntent pd = PendingIntent.getActivity(c, 0, done,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(pd.getIntentSender());
            s.close();
            toast(c, "Aktualizacja pobrana - potwierdz instalacje");
        } catch (Throwable t) {
            android.util.Log.w("Dock", "install", t);
            toast(c, "Nie udalo sie uruchomic instalacji aktualizacji");
        }
    }

    // "v1.0.50" -> 50 ; versionCode CI = numer runu, wiek porownanie wystarczy
    private static int parseTag(String tag) {
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
