package pl.dock.widget.maps.wiki;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Wikipedia w okolicy" - miejsca z artykulami w polskiej Wikipedii
 * wokol biezacej pozycji (geosearch). Jak warstwa Wikipedia w Organic Maps.
 *
 * Online only - offline dialog po prostu nie pokazuje wynikow.
 */
public final class WikiNearby {

    public interface Callback {
        void onResults(List<String> names, List<double[]> latlon);

        void onError(String message);
    }

    private WikiNearby() {
    }

    /** Szuka miejsc z Wikipedii w promieniu do 10 km; callback na watku UI. */
    public static void search(double lat, double lon, double radiusM, final Callback cb) {
        final String url;
        try {
            double r = Math.min(10000d, Math.max(100d, radiusM));
            url = "https://pl.wikipedia.org/w/api.php?action=query&format=json&list=geosearch"
                    + "&gscoord=" + URLEncoder.encode(
                    String.format(Locale.US, "%.6f|%0.6f", lat, lon), "UTF-8")
                    + "&gsradius=" + (int) r
                    + "&gslimit=30";
        } catch (Throwable t) {
            postError(cb, "blad adresu");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String json = httpGet(url);
                    if (json == null) {
                        postError(cb, "brak internetu");
                        return;
                    }
                    JSONObject root = new JSONObject(json);
                    JSONArray arr = root.optJSONObject("query")
                            .optJSONArray("geosearch");
                    final List<String> names = new ArrayList<String>();
                    final List<double[]> pts = new ArrayList<double[]>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            names.add(o.optString("title", "?"));
                            pts.add(new double[]{o.optDouble("lat", 0d), o.optDouble("lon", 0d)});
                        }
                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResults(names, pts);
                        }
                    });
                } catch (Throwable t) {
                    postError(cb, t.getMessage() == null ? "blad zapytania" : t.getMessage());
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
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
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

    private static void postError(final Callback cb, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                cb.onError(msg);
            }
        });
    }
}
