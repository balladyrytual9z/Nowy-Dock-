package pl.dock.widget.maps.route;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Klient OSRM (routing.openstreetmap.de - te same serwery, z ktorych korzysta
 * strona openstreetmap.org). Profile: auto / rower / pieszo.
 *
 * Pobiera warianty trasy (alternatives=true), limity predkosci per segment
 * (annotations=true) i wskazania pasow ruchu na manewrach (intersections.lanes).
 * Bez internetu zwraca error i NavigatorActivity zostaje przy linii prostej
 * do celu (dotychczasowe zachowanie).
 */
public final class RouteClient {

    public interface Callback {
        void onRoute(Route route);

        void onError(String message);
    }

    /** Wiele wariantow trasy (pierwszy = glowny). */
    public interface RoutesCallback {
        void onRoutes(List<Route> routes);

        void onError(String message);
    }

    private static final String HOST = "https://routing.openstreetmap.de/";

    private RouteClient() {
    }

    /** Asynchroniczny request trasy; callback zawsze na watku UI. */
    public static void route(String profile, double fromLat, double fromLon,
                             double toLat, double toLon, final Callback cb) {
        routes(profile, fromLat, fromLon, toLat, toLon, new RoutesCallback() {
            @Override
            public void onRoutes(List<Route> rs) {
                cb.onRoute(rs.get(0));
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    /** Wszystkie warianty trasy (alternatives=true). */
    public static void routes(String profile, double fromLat, double fromLon,
                              double toLat, double toLon, final RoutesCallback cb) {
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
                    JSONArray routesArr = root.optJSONArray("routes");
                    if (routesArr == null || routesArr.length() == 0) {
                        postError(cb, "Brak trasy");
                        return;
                    }
                    final List<Route> rs = new ArrayList<Route>();
                    for (int i = 0; i < routesArr.length(); i++) {
                        Route r = parse(routesArr.getJSONObject(i));
                        if (r != null && r.lats.length >= 2) rs.add(r);
                    }
                    if (rs.isEmpty()) {
                        postError(cb, "Trasa uszkodzona");
                        return;
                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onRoutes(rs);
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
        return path + coords
                + "?overview=full&geometries=polyline&steps=true"
                + "&alternatives=true&annotations=true";
    }

    private static Route parse(JSONObject rt) throws Exception {
        double[][] ll = PolylineCodec.decodeArrays(rt.getString("geometry"), 5);
        double[] lats = ll[0];
        double[] lons = ll[1];
        double distanceM = rt.optDouble("distance", 0d);
        long durationS = (long) rt.optDouble("duration", 0d);

        JSONArray legs = rt.optJSONArray("legs");
        Route.Step[] ss = new Route.Step[0];
        int[] maxKmh = null;
        if (legs != null && legs.length() > 0) {
            JSONArray ssArr = legs.getJSONObject(0).optJSONArray("steps");
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
                    // wskazania pasow z pierwszego skrzyzowania manewru (valid=true)
                    String laneHint = "";
                    try {
                        JSONArray inters = st.optJSONArray("intersections");
                        if (inters != null && inters.length() > 0) {
                            JSONArray lanes = inters.getJSONObject(0).optJSONArray("lanes");
                            if (lanes != null && lanes.length() > 0) {
                                List<String> inds = new ArrayList<String>();
                                for (int k = 0; k < lanes.length(); k++) {
                                    JSONObject ln = lanes.getJSONObject(k);
                                    if (!ln.optBoolean("valid", false)) continue;
                                    inds.add(ln.optString("indications", "[]")
                                            .replace("[", "").replace("]", "")
                                            .replace("\"", "").trim());
                                }
                                String[] arr = new String[inds.size()];
                                // wskazanie moze byc lista, np. ["through","right"] -
                                // rozbijamy na pojedyncze slowa
                                List<String> single = new ArrayList<String>();
                                for (int k = 0; k < arr.length; k++) {
                                    String[] parts = arr[k].length() == 0
                                            ? new String[0] : arr[k].split(",");
                                    for (int p = 0; p < parts.length; p++) {
                                        String w = parts[p].trim();
                                        if (w.length() > 0) single.add(w);
                                    }
                                }
                                String[] finalInds = new String[single.size()];
                                laneHint = Maneuvers.laneHint(single.toArray(finalInds));
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    ss[i] = new Route.Step(anchor,
                            Maneuvers.polish(type, modifier, road),
                            road, stepDist, type, modifier, laneHint);
                }
            }
            // limity predkosci per segment: legs[].annotation.maxspeed
            int segments = lats.length - 1;
            if (segments > 0) {
                maxKmh = new int[segments];
                int ofs = 0;
                for (int li = 0; li < legs.length() && ofs < segments; li++) {
                    JSONObject ann = legs.getJSONObject(li).optJSONObject("annotation");
                    if (ann == null) continue;
                    JSONArray ms = ann.optJSONArray("maxspeed");
                    if (ms == null) continue;
                    int take = Math.min(ms.length(), segments - ofs);
                    for (int k = 0; k < take; k++) {
                        maxKmh[ofs + k] = parseMaxspeed(ms.get(k));
                    }
                    ofs += ms.length();
                }
            }
        }
        return new Route(lats, lons, distanceM, durationS, ss, maxKmphOrNull(maxKmh));
    }

    /** maxspeed moze byc obiektem {speed, unit}, liczba [km/h] lub null/mph. */
    private static int parseMaxspeed(Object v) {
        try {
            if (v == null || JSONObject.NULL.equals(v)) return 0;
            if (v instanceof JSONObject) {
                JSONObject o = (JSONObject) v;
                double s = o.optDouble("speed", 0d);
                String unit = o.optString("unit", "km/h");
                if (s <= 0d) return 0;
                if ("mph".equals(unit)) return (int) Math.round(s * 1.609d);
                return (int) Math.round(s);
            }
            if (v instanceof Number) {
                double s = ((Number) v).doubleValue();
                return s <= 0d ? 0 : (int) Math.round(s);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /** Tablica w sama zer (brak danych o limitach) = null - Guidance nie pokaze chipa. */
    private static int[] maxKmphOrNull(int[] arr) {
        if (arr == null) return null;
        boolean any = false;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] > 0) {
                any = true;
                break;
            }
        }
        return any ? arr : null;
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

    private static void postError(final RoutesCallback cb, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                cb.onError(msg);
            }
        });
    }
}
