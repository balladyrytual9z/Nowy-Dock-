package pl.dock.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Wyzwalacz animacji powitalnej Honda: ACTION_SCREEN_ON (wlaczenie ekranu
 * po przekreceniu kluczyka). Rejestrowany dynamicznie w DockApp - manifestowy
 * receiver nie dostaje SCREEN_ON na Androidzie 9.
 *
 * Zabezpieczenie przed wielokrotnym wyswietleniem (np. szybkie gaszenie/
 * zapalanie ekranu): MIN_GAP miedzy pokazaniami.
 */
public class ScreenOnReceiver extends BroadcastReceiver {

    static final String PREFS = "honda_welcome";
    static final String KEY_LAST = "last_show";
    static final long MIN_GAP_MS = 60_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_SCREEN_ON.equals(intent.getAction())) return;
        try {
            android.content.SharedPreferences p =
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long now = SystemClock.elapsedRealtime();
            long last = p.getLong(KEY_LAST, -MIN_GAP_MS);
            if (now - last < MIN_GAP_MS) return;
            p.edit().putLong(KEY_LAST, now).apply();

            Intent it = new Intent(context, HondaWelcomeActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            context.startActivity(it);
        } catch (Throwable t) {
            LogDiagnostics.e("ScreenOnReceiver", "start animacji: " + t.getMessage(), t);
        }
    }
}
