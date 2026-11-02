package pl.dock.widget.circadian;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import pl.dock.widget.DockWidget;
import pl.dock.widget.Look;
import pl.dock.widget.Prefs;
import pl.dock.widget.StatusCenter;

/**
 * Dwukierunkowa synchronizacja z Circadian Display:
 *  - pl.circadiandisplay.STATE_CHANGED  (z nakładki, extra isNight)
 *  - pl.dock.widget.NIGHT_MODE_CHANGED   (z Docka / wewnętrzne)
 */
public class NightModeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null) return;
        String a = i.getAction();
        if (a == null) return;

        boolean isNight = i.getBooleanExtra("isNight", false);

        if (CircadianBridge.ACTION_STATE_CHANGED.equals(a)) {
            // Circadian → Dock: ustaw tryb i odśwież UI
            if (!Prefs.circadianAuto(c)) return;
            String want = isNight ? "night" : "day";
            if (!Prefs.followSystem(c)) {
                if (!want.equals(Prefs.mode(c))) Prefs.setMode(c, want);
            }
            Look.wasNight = isNight;
            try {
                StatusCenter.refresh(c);
                DockWidget.updateAll(c);
            } catch (Throwable ignored) {
            }
            // Przekaż do launchera / StatusService (ten sam pakiet)
            c.sendBroadcast(new Intent(CircadianBridge.ACTION_NIGHT)
                    .setPackage(c.getPackageName())
                    .putExtra("isNight", isNight)
                    .putExtra("from_circadian", true));
            return;
        }

        if (CircadianBridge.ACTION_NIGHT.equals(a)
                || "pl.dock.widget.NIGHT_MODE_CHANGED".equals(a)) {
            // Filtr Night Shift wlacza sie WYLACZNIE kafelkiem w Centrum sterowania
            // (recznie albo "od zachodu do wschodu"). Zmiana motywu nie wymusza
            // nakladki - tutaj tylko odswiezamy krzywa, jesli uzytkownik ja
            // wlaczyl w Ustawieniach. Caly mechanizm jest wbudowany w Docka.
            if (Prefs.circadianAuto(c)) {
                CircadianService.refresh(c);
            }
        }
    }
}





