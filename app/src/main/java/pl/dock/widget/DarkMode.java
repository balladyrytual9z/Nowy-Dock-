package pl.dock.widget;

import android.content.Context;

import java.util.Calendar;

// Tryb ciemny jak w iOS (przycisk w panelu łączności):
// - przełącznik wł/wył,
// - automat: ciemny od zachodu do wschodu słońca (SolarTimes/NOAA, offline;
//   współrzędne z SolarCalculator - te same co Night Shift),
// - ręczny klik zmienia stan "do najbliższego wydarzenia słońca" - dokładnie
//   jak podpis w iOS: "Włączony do wsch. słońca" / "Wyłączony do zach. słońca".
//
// NOWY MODEL STANÓW:
//   AUTO_DAY   – automatycznie wyłączony (dzień)
//   AUTO_NIGHT – automatycznie włączony (noc)
//   MANUAL_ON  – ręcznie włączony do najbliższego wydarzenia
//   MANUAL_OFF – ręcznie wyłączony do najbliższego wydarzenia
public class DarkMode {

    // Czy teraz tryb ciemny (efektywny stan wykonawczy).
    public static boolean isNight(Context c) {
        return state(c) == DarkState.AUTO_NIGHT || state(c) == DarkState.MANUAL_ON;
    }

    // Zwraca aktualny stan wynikowy (AUTO_DAY / AUTO_NIGHT / MANUAL_ON / MANUAL_OFF).
    public static SolarCalculator.DarkState state(Context c) {
        long now = System.currentTimeMillis();
        int ovr = Prefs.darkOverride(c);
        long ovrUntil = Prefs.darkOverrideUntil(c);

        // Ręczne nadpisanie ma pierwszeństwo, ale tylko do czasu wygaśnięcia.
        if (ovr != 0 && ovrUntil > 0) {
            if (now < ovrUntil) {
                return ovr == 1 ? SolarCalculator.DarkState.MANUAL_ON : SolarCalculator.DarkState.MANUAL_OFF;
            } else {
                // Nadpisanie wygasło – przejmuje automatyka słoneczna.
                Prefs.clearDarkOverride(c);
                LogDiagnostics.stateChange("DarkMode", "MANUAL_" + (ovr == 1 ? "ON" : "OFF"), "AUTO", "nadpisanie wygasło");
            }
        }

        // Jeśli użytkownik nie użył jeszcze nowego przełącznika, zachowaj stare zachowanie.
        if (!Prefs.darkUsed(c)) {
            return nightBySun(c) ? SolarCalculator.DarkState.AUTO_NIGHT : SolarCalculator.DarkState.AUTO_DAY;
        }

        // Automatyka słoneczna.
        return nightBySun(c) ? SolarCalculator.DarkState.AUTO_NIGHT : SolarCalculator.DarkState.AUTO_DAY;
    }

    // Klik: przełącz i utrzymaj do najbliższego wschodu/zachodu słońca.
    public static void toggle(Context c) {
        boolean now = isNight(c);
        Prefs.setDarkUsed(c, true);
        long until = SolarCalculator.nextSunEvent(c, now);
        if (until < 0L) {
            // Nie da się obliczyć wydarzenia – zachowaj ostatni bezpieczny stan.
            LogDiagnostics.e("DarkMode", "toggle: nie można obliczyć następnego wydarzenia słonecznego");
            return;
        }
        Prefs.setDarkOverride(c, now ? 2 : 1, until);
        SolarCalculator.DarkState from = state(c);
        SolarCalculator.DarkState to = now ? SolarCalculator.DarkState.MANUAL_OFF : SolarCalculator.DarkState.MANUAL_ON;
        LogDiagnostics.stateChange("DarkMode", from.name(), to.name(), "ręczne kliknięcie");

        // Natychmiastowa aktualizacja interfejsu.
        Look.refreshTheme(c);

        // Uruchom usługę Night Shift jeśli potrzebna (niezależny mechanizm).
        NightShift.syncService(c);
    }

    // Podpis pod przyciskiem (jak w iOS).
    public static String label(Context c) {
        SolarCalculator.DarkState st = state(c);
        switch (st) {
            case MANUAL_ON:
            case AUTO_NIGHT:
                return "Włączony do wsch. słońca";
            case MANUAL_OFF:
            case AUTO_DAY:
                return "Wyłączony do zach. słońca";
        }
        return "Wyłączony do zach. słońca";
    }

    private static boolean nightBySun(Context c) {
        try {
            return SolarCalculator.isNight(c);
        } catch (Throwable t) {
            LogDiagnostics.e("DarkMode", "nightBySun failed: " + t.getMessage(), t);
            return false;
        }
    }
}
