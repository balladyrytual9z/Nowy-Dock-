
package pl.dock.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

// Pogoda i godziny słońca z Open-Meteo (bez klucza). Odświeżane najwyżej co 30 minut.
public class WeatherData {
    public static String city = "";
    public static double temp, feels, wind, uv, tMax, tMin;
    public static int pop, code;
    public static String sunrise = "--:--", sunset = "--:--";
    public static boolean ok;
    private static long ts;
    private static boolean loading;
    private static final Handler main = new Handler(Looper.getMainLooper());

    public static void refresh(final Context c, final Runnable done) {
        final long now = System.currentTimeMillis();
        if (loading || (ok && now - ts < 30 * 60 * 1000L)) {
            if (done != null) done.run();
            return;
        }
        loading = true;
        final String name = Prefs.weatherCity(c);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String g = get("https://geocoding-api.open-meteo.com/v1/search?name="
                            + URLEncoder.encode(name, "UTF-8") + "&count=1&language=pl&format=json");
                    JSONObject r0 = new JSONObject(g).getJSONArray("results").getJSONObject(0);
                    String url = "https://api.open-meteo.com/v1/forecast?latitude=" + r0.getDouble("latitude")
                            + "&longitude=" + r0.getDouble("longitude")
                            + "&current=temperature_2m,apparent_temperature,wind_speed_10m,weather_code"
                            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,"
                            + "uv_index_max,sunrise,sunset&timezone=auto&wind_speed_unit=ms&forecast_days=1";
                    JSONObject j = new JSONObject(get(url));
                    JSONObject cur = j.getJSONObject("current");
                    JSONObject day = j.getJSONObject("daily");
                    city = r0.optString("name", name);
                    temp = cur.getDouble("temperature_2m");
                    feels = cur.getDouble("apparent_temperature");
                    wind = cur.getDouble("wind_speed_10m");
                    code = cur.optInt("weather_code", 0);
                    tMax = first(day.getJSONArray("temperature_2m_max"));
                    tMin = first(day.getJSONArray("temperature_2m_min"));
                    pop = (int) first(day.getJSONArray("precipitation_probability_max"));
                    uv = first(day.getJSONArray("uv_index_max"));
                    sunrise = day.getJSONArray("sunrise").getString(0).substring(11);
                    sunset = day.getJSONArray("sunset").getString(0).substring(11);
                    ok = true;
                    ts = System.currentTimeMillis();
                } catch (Throwable t) {
                    // zostają poprzednie dane (albo "brak danych")
                }
                loading = false;
                if (done != null) main.post(done);
            }
        }).start();
    }

    private static double first(JSONArray a) throws Exception {
        return a.getDouble(0);
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) b.append(line);
        r.close();
        c.disconnect();
        return b.toString();
    }

    public static String uvLabel() {
        if (uv < 3) return "Niski";
        if (uv < 6) return "Umiarkowany";
        if (uv < 8) return "Wysoki";
        if (uv < 11) return "Bardzo wysoki";
        return "Ekstremalny";
    }
}







