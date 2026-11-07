package pl.dock.widget.maps.route;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Klient OSRM (routing.openstreetmap.de - te same serwery, z ktorych korzysta
 * strona openstreetmap.org). Profile: auto / rower / pieszo.
 *
 * Wyzwanie offline: bez internetu zwraca error i NavigatorActivity zostaje
 * przy lini prostej do celu (dotychczasowe zachowanie). Trasa nie jest
 * zapisywana na dysk - cel i tak zyje w pamieci aktywnosci.
 */
public final class RouteClient {

    public interface Callback {
        void onRoute(Route route);

        void onError(String message);
    }

    private static final String HOST = "https://routing.openstreetmap.de/";

    private RouteClient() {
    }

    /** Asynchroniczny request trasy; callback zawsze na watku UI. */
    public static void route(String profile, double fromLat, double fromLon,
                             double toLat, double toLon, final Callback cb) {
        final String url = buildUrl(profile, fromLat, fromLon, toLat, toLon);
        if (url == null) {
            postError(cb, "Zly profil ruchu");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String json = httpGet(url);
                    if (json == null) {
                        postError(cb, "Brak odpowiedzi serwera");
                        return;
                    }
                    JSONObject root = new JSONObject(json);
                    String err = root.optString("message", "");
                    if (err.length() > 0) {
                        postError(cb, err);
                        return;
                    }
                    JSONArray routes = root.optJSONArray("routes");
                    if (routes == null || routes.length() == 0) {
                        postError(cb, "Brak trasy");
                        return;
                    }
                    final Route r = parse(routes.getJSONObject(0));
                    if (r == null || r.lats.length < 2) {
                        postError(cb, "Trasa uszkodzona");
                        return;
                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onRoute(r);
                        }
                    });
                } catch (Throwable t) {
                    postError(cb, t.getMessage() == null ? "Brak internetu" : t.getMessage());
                }
            }
        }).start();
    }

    static String buildUrl(String profile, double fromLat, double fromLon,
                           double toLat, double toLon) {
        String path;
        if ("rower".equals(profile)) path = HOST + "routed-bike/route/v1/driving/";
        else if ("pieszo".equals(profile)) path = HOST + "routed-foot/route/v1/driving/";
        else if ("auto".equals(profile) || profile == null || profile.length() == 0)
            path = HOST + "routed-car/route/v1/driving/";
        else return null;
        // OSRM chce kolejnosc lon,lat;lon,lat
        String coords = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f",
                fromLon, fromLat, toLon, toLat);
        return path + coords + "?overview=full&geometries=polyline&steps=true&alternatives=false";
    }

    private static Route parse(JSONObject rt) throws Exception {
        double[][] ll = PolylineCodec.decodeArrays(rt.getString("geometry"), 5);
        double[] lats = ll[0];
        double[] lons = ll[1];
        double distanceM = rt.optDouble("distance", 0d);
        long durationS = (long) rt.optDouble("duration", 0d);

        JSONArray steps = rt.optJSONArray("legs");
        Route.Step[] ss = new Route.Step[0];
        if (steps != null && steps.length() > 0) {
            JSONArray ssArr = steps.getJSONObject(0).optJSONArray("steps");
            if (ssArr != null) {
                ss = new Route.Step[ssArr.length()];
                for (int i = 0; i < ssArr.length(); i++) {
                    JSONObject st = ssArr.getJSONObject(i);
                    JSONObject man = st.optJSONObject("maneuver");
                    String type = "";
                    String modifier = "";
                    double mLat = 0d;
                    double mLon = 0d;
                    if (man != null) {
                        type = man.optString("type", "");
                        modifier = man.optString("modifier", "");
                        JSONArray loc = man.optJSONArray("location");
                        if (loc != null && loc.length() >= 2) {
                            mLon = loc.optDouble(0, 0d);
                            mLat = loc.optDouble(1, 0d);
                        }
                    }
                    String road = st.optString("name", "");
                    double stepDist = st.optDouble("distance", 0d);
                    int anchor = nearestIndex(lats, lons, mLat, mLon);
                    ss[i] = new Route.Step(anchor,
                            Maneuvers.polish(type, modifier, road),
                            road, stepDist, type, modifier);
                }
            }
        }
        return new Route(lats, lons, distanceM, durationS, ss);
    }

    /** Punkt trasy najblizszy lokalizacji manewru (manewry "przypinamy" do geometrii). */
    static int nearestIndex(double[] lats, double[] lons, double lat, double lon) {
        int bi = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < lats.length; i++) {
            double d = Guidance.distM(lat, lon, lats[i], lons[i]);
            if (d < best) {
                best = d;
                bi = i;
            }
        }
        return bi;
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

    private static void postError(final Callback cb, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                cb.onError(msg);
            }
        });
    }
}
