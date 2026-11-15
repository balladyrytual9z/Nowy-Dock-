package pl.dock.widget;

import android.util.Log;

/**
 * Czytałne logowanie diagnostyczne dla trybu ciemnego i Night Shift.
 * Wszystkie błędy i ważne zdarzenia są zapisywane w logu - nic nie jest ukrywane.
 */
public final class LogDiagnostics {

    private static final String TAG = "DockNight";

    private LogDiagnostics() {}

    public static void v(String tag, String msg) {
        Log.v(TAG, "[" + tag + "] " + msg);
    }

    public static void d(String tag, String msg) {
        Log.d(TAG, "[" + tag + "] " + msg);
    }

    public static void i(String tag, String msg) {
        Log.i(TAG, "[" + tag + "] " + msg);
    }

    public static void w(String tag, String msg) {
        Log.w(TAG, "[" + tag + "] " + msg);
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(TAG, "[" + tag + "] " + msg, t);
    }

    public static void e(String tag, String msg) {
        Log.e(TAG, "[" + tag + "] " + msg);
    }

    /** Loguj zmianę stanu (tryb ciemny / Night Shift). */
    public static void stateChange(String component, String from, String to, String reason) {
        Log.i(TAG, "[" + component + "] STATE: " + from + " -> " + to + " | reason=" + reason);
    }

    /** Loguj współrzędne lokalizacji. */
    public static void location(String source, double lat, double lon, boolean valid) {
        Log.i(TAG, "LOCATION: source=" + source + " lat=" + lat + " lon=" + lon + " valid=" + valid);
    }

    /** Loguj obliczenia słoneczne. */
    public static void solar(String event, double lat, double lon, double sunriseMin, double sunsetMin) {
        Log.i(TAG, "SOLAR: " + event + " lat=" + lat + " lon=" + lon +
                " sunrise=" + fmtMinutes(sunriseMin) + " sunset=" + fmtMinutes(sunsetMin));
    }

    /** Loguj harmonogram. */
    public static void schedule(String component, long nextEventMillis, String description) {
        Log.i(TAG, "[" + component + "] SCHEDULE: nextEvent=" + nextEventMillis + " (" + description + ")");
    }

    /** Loguj usługę. */
    public static void service(String action, String detail) {
        Log.i(TAG, "SERVICE: " + action + " | " + detail);
    }

    /** Loguj błąd nakładki. */
    public static void overlayError(String operation, Throwable t) {
        Log.e(TAG, "OVERLAY: " + operation + " failed", t);
    }

    private static String fmtMinutes(double min) {
        int h = (int) (min / 60.0);
        int m = (int) (min - h * 60);
        return String.format("%02d:%02d", h, m);
    }
}
