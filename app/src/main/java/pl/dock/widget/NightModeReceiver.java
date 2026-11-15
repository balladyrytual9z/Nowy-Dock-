package pl.dock.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Broadcast receiver dla zdarzeń czasu i systemu.
 * Przy zmianie godziny, strefy czasowej, daty lub po restarcie urządzenia
 * przelicza harmonogram słoneczny i odświeża stan trybu ciemnego / Night Shift.
 *
 * Rejestrowany w AndroidManifest.xml (nie dynamicznie), więc działa nawet
 * gdy aplikacja nie jest uruchomiona.
 */
public class NightModeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        LogDiagnostics.d("NightModeReceiver", "otrzymano: " + action);

        if (Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)) {
            // Zmiana czasu / strefy / daty – przeliczenie harmonogramu.
            // Odświeżamy stan obu mechanizmów.
            boolean night = DarkMode.isNight(c);
            Look.refreshTheme(c);

            // Zrestartuj usługę Night Shift jeśli jest aktywna.
            if (NightShift.nsUsed(c)) {
                NightShift.syncService(c);
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            // Po restarcie lub aktualizacji: odtwórz ostatni stan.
            // Nie wymuszaj niczego – po prostu zsynchronizuj usługę.
            if (NightShift.nsUsed(c)) {
                NightShift.restore(c);
            }
        }
    }
}
