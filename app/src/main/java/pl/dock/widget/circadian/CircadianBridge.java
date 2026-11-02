package pl.dock.widget.circadian;

import android.content.Context;
import android.content.Intent;

/**
 * Wewnetrzny mostek Circadian - caly silnik (krzywa, nakladka, edytor) jest
 * WBUDOWANY w Docka. Nie jest wymagana zadna zewnetrzna aplikacja.
 * Kafel Night Shift w Centrum sterowania pozostaje jedynym "lataczem" filtra;
 * krzywa Circadian z ustawien dziala rownolegle, ale ustapuje kafelkowi.
 */
public final class CircadianBridge {

    /** Dock -> Circadian (wewnatrz pakietu): zlecenie trybu nocnego. */
    public static final String ACTION_NIGHT = "pl.dock.widget.NIGHT_MODE_CHANGED";
    /** Circadian -> Dock: stan nakladki (extra isNight). */
    public static final String ACTION_STATE_CHANGED = "pl.circadiandisplay.STATE_CHANGED";

    // Silnik wbudowany - "zainstalowany" zawsze.
    public static boolean isInstalled(Context c) {
        return true;
    }

    // Zapewnia spujnosc z poprzednia wersja (zewnetrzna aplikacja): wlacza
    // wbudowany silnik krzywej. Nie dotyka kafelka Night Shift.
    public static boolean activateForNight(Context c) {
        try {
            CircadianService.activateForNight(c);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ustawia tryb nocny: u nas to wewnetrzny broadcast (bez zewnetrznego provider'a). */
    public static boolean setNightMode(Context c, boolean isNight) {
        try {
            c.sendBroadcast(new Intent(ACTION_NIGHT)
                    .setPackage(c.getPackageName())
                    .putExtra("isNight", isNight));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Otwiera wbudowany edytor krzywej (dawne "Circadian Display"). */
    public static Intent openApp(Context c) {
        Intent i = new Intent(c, CircadianActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    public static Intent openSettingsIntent(Context c) {
        return openApp(c);
    }

    private CircadianBridge() {
    }
}
