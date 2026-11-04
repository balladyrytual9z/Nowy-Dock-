package pl.dock.widget.maps;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;

import pl.dock.widget.Prefs;

/**
 * Samo-pobieranie mapy CALEJ POLSKI, gdy tablet jest podlaczony do Wi-Fi.
 *
 * Zasada: gdy uzytkownik ma wlaczone "Pobieraj mape Polski przez Wi-Fi"
 * (Ustawienia -> Mapy, domyslnie WLACZONE) i tablet jest na Wi-Fi, a mapa
 * Polski nie byla jeszcze pobrana - sciagamy ja raz w tle (zoom 7-10, jak
 * preset "Cala Polska (niska jakosc)" w MapDownloadActivity). Potem mapa
 * dziala bez internetu.
 */
public final class PolandAutoDownload {

    /** Prostokat calej Polski: N/E/S/W. */
    private static final BoundingBox POLAND = new BoundingBox(54.95, 24.20, 48.95, 14.05);
    private static final int MIN_ZOOM = 7;
    private static final int MAX_ZOOM = 10;

    private static volatile boolean running;

    private PolandAutoDownload() {
    }

    /** Czy tablet jest teraz na Wi-Fi? */
    public static boolean onWifi(Context c) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isDone(Context c) {
        return Prefs.mapPolandDone(c);
    }

    /**
     * Sprawdza warunki i (jesli trzeba) startuje pobieranie w tle.
     * Wywolywane przy wejsciu w mape - nie blokuje interfejsu.
     */
    public static void maybeStart(Context context, final MapView map) {
        if (map == null || context == null) return;
        if (!Prefs.mapWifiAuto(context)) return;
        if (Prefs.mapPolandDone(context)) return;
        if (running) return;
        if (!onWifi(context)) return;

        running = true;
        try {
            CacheManager cm = new CacheManager(map);
            cm.downloadAreaAsyncNoUI(context, POLAND, MIN_ZOOM, MAX_ZOOM,
                    new CacheManager.CacheManagerCallback() {
                        @Override
                        public void onTaskComplete() {
                            running = false;
                            Prefs.setMapPolandDone(context, true);
                            toast(context, "Mapa Polski pobrana - dziala offline");
                        }

                        @Override
                        public void updateProgress(int progress, int currentZoomLevel,
                                                   int zoomMin, int zoomMax) {
                        }

                        @Override
                        public void downloadStarted() {
                            toast(context, "Pobieram mape Polski przez Wi-Fi...");
                        }

                        @Override
                        public void setPossibleTilesInArea(int total) {
                        }

                        @Override
                        public void onTaskFailed(int errors) {
                            running = false;
                        }
                    });
        } catch (Throwable t) {
            running = false;
        }
    }

    private static void toast(final Context context, final String msg) {
        try {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
