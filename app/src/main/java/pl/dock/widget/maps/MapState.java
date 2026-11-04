package pl.dock.widget.maps;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Lekki dostawca pozycji GPS dla modulu map.
 *
 * ESSGOO (MT8163, Android 9) ma dzialajacy GPS. Uzywamy wbudowanego
 * LocationManager (provider GPS) - bez Google Play Services, bez AndroidX.
 * Gdy nie ma jeszcze fiksa, zwracamy ostatnia znana pozycje.
 */
public final class MapState {

    public interface Listener {
        void onLocation(double lat, double lon, float bearing, float speedMs, boolean hasFix);
    }

    private static MapState instance;

    private final Context app;
    private final LocationManager lm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();

    private Location last;
    private boolean listening;

    private final LocationListener gpsListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            if (l != null) {
                last = l;
                dispatch();
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    private MapState(Context c) {
        app = c.getApplicationContext();
        lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
    }

    public static synchronized MapState get(Context c) {
        if (instance == null) instance = new MapState(c);
        return instance;
    }

    public static boolean hasPermission(Context c) {
        try {
            return c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public void addListener(Listener l) {
        if (l == null) return;
        synchronized (listeners) {
            if (!listeners.contains(l)) listeners.add(l);
        }
        l.onLocation(lastLat(), lastLon(), lastBearing(), lastSpeed(), hasFix());
        start();
    }

    public void removeListener(Listener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
        if (listeners.isEmpty()) stop();
    }

    private void start() {
        if (listening || lm == null) return;
        if (!hasPermission(app)) return;
        listening = true;
        try {
            if (last == null) last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, gpsListener,
                        Looper.getMainLooper());
            }
        } catch (Throwable ignored) {
        }
        dispatchSoon();
    }

    private void stop() {
        if (!listening || lm == null) return;
        listening = false;
        try {
            lm.removeUpdates(gpsListener);
        } catch (Throwable ignored) {
        }
    }

    private void dispatchSoon() {
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (listening) dispatch();
            }
        }, 1500L);
    }

    private void dispatch() {
        List<Listener> copy;
        synchronized (listeners) {
            copy = new ArrayList<Listener>(listeners);
        }
        for (Listener l : copy) {
            l.onLocation(lastLat(), lastLon(), lastBearing(), lastSpeed(), hasFix());
        }
    }

    public boolean hasFix() {
        return last != null;
    }

    public double lastLat() {
        return last != null ? last.getLatitude() : 0d;
    }

    public double lastLon() {
        return last != null ? last.getLongitude() : 0d;
    }

    public float lastBearing() {
        return last != null && last.hasBearing() ? last.getBearing() : 0f;
    }

    public float lastSpeed() {
        return last != null && last.hasSpeed() ? last.getSpeed() : 0f;
    }
}
