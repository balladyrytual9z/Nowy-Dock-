package pl.dock.widget.maps.elevation;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Wysokosci terenu z opentopodata (SRTM 90 m). Publiczny serwer, bez klucza:
 * limit 100 punktow na zapytanie i 1 zapytanie/sekunde - dlugie slady
 * probkujemy po stronie wywolujacej i liczymy sie z pauza miedzy paczkami.
 *
 * Online only - offline profil po prostu nie powstaje (toast z powodem).
 */
public final class ElevationClient {

    public interface Callback {
        void onResult(double[] elevations, String error);
    }

    private static final String BASE =
            "https://api.opentopodata.org/v1/srtm90m?locations=";

    private ElevationClient() {
    }

    /** Asynchroniczne pobranie wysokosci; callback na watku UI. */
    public static void fetch(double[] lats, double[] lons, final Callback cb) {
        final double[] la = lats;
        final double[] lo = lons;
        new Thread(new Runnable() {
            @Override
            public void run() {
                int n = Math.min(la.length, lo.length);
                double[] out = new double[n];
                int i = 0;
                try {
                    while (i < n) {
                        int end = Math.min(n, i + 100);
                        StringBuilder sb = new StringBuilder();
                        for (int k = i; k < end; k++) {
                            if (k > i) sb.append('|');
                            sb.append(String.format(Locale.US, "%.5f,%.5f", la[k], lo[k]));
                        }
                        String url = BASE + URLEncoder.encode(sb.toString(), "UTF-8");
                        String json = httpGet(url);
                        if (json == null) {
                            post(cb, null, "brak internetu");
                            return;
                        }
                        JSONObject root = new JSONObject(json);
                        if (root.has("error")) {
                            post(cb, null, root.optString("error"));
                            return;
                        }
                        JSONArray results = root.optJSONArray("results");
                        if (results == null || results.length() < end - i) {
                            post(cb, null, "serwer zwrocil za malo danych");
                            return;
                        }
                        for (int k = 0; k < end - i; k++) {
                            JSONObject o = results.getJSONObject(k);
                            out[i + k] = o.optDouble("elevation", Double.NaN);
                        }
                        i = end;
                        if (i < n) Thread.sleep(1100L);
                    }
                    post(cb, out, null);
                } catch (Throwable t) {
                    post(cb, null, t.getMessage() == null ? "blad pobierania" : t.getMessage());
                }
            }
        }).start();
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(urlStr);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestProperty("User-Agent", "pl.dock.widget/1.0 (car launcher)");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            InputStream is = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static void post(final Callback cb, final double[] elev, final String err) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                cb.onResult(elev, err);
            }
        });
    }
}
