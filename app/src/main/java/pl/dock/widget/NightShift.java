package pl.dock.widget;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

// Night Shift: ciepły filtr barwy ekranu (nakładka). Wymaga zgody "Wyświetlaj nad innymi aplikacjami".
//
// NOWY MODEL STANÓW (jak w iOS, na wzór Trybu ciemnego):
//   AUTO_OFF   – automatycznie wyłączony (dzień)
//   AUTO_ON    – automatycznie włączony (noc, zachód → wschód)
//   MANUAL_ON  – ręcznie włączony do najbliższego wydarzenia
//   MANUAL_OFF – ręcznie wyłączony do najbliższego wydarzenia
//
// Po pierwszym kliknięciu nowego przycisku (nsUsed) Night Shift działa na
// harmonogramie słońca, a klik zmienia stan "do najbliższego wydarzenia".
// Własne klucze w osobnym pliku prefs "dock_ns" (nie ruszamy Prefs.java).
public class NightShift {

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("dock_ns", Context.MODE_PRIVATE);
    }

    public static boolean canDraw(Context c) {
        try {
            return Settings.canDrawOverlays(c);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    // ---------- stare API (kompatybilność wsteczna) ----------

    public static boolean isOn(Context c) {
        return state(c) == SolarCalculator.NsState.AUTO_ON || state(c) == SolarCalculator.NsState.MANUAL_ON;
    }

    public static String mode(Context c) {
        return Prefs.nightShiftMode(c);
    }

    // Flaga: user użył nowego przycisku - od tego momentu harmonogram słońca
    // przejmuje sterowanie (przed tym: stary model off/on/sunset).
    public static boolean nsUsed(Context c) {
        return sp(c).getBoolean("nsused", false);
    }

    public static void setNsUsed(Context c, boolean v) {
        sp(c).edit().putBoolean("nsused", v).apply();
    }

    // Ręczna zmiana: 0 = brak, 1 = włączony, 2 = wyłączony (do "nsovr_until")
    public static int nsOverride(Context c) {
        return sp(c).getInt("nsovr", 0);
    }

    public static long nsOverrideUntil(Context c) {
        return sp(c).getLong("nsovr_until", 0L);
    }

    public static void setNsOverride(Context c, int v, long until) {
        sp(c).edit().putInt("nsovr", v).putLong("nsovr_until", until).apply();
    }

    public static void clearNsOverride(Context c) {
        sp(c).edit().remove("nsovr").remove("nsovr_until").apply();
    }

    // Jasność zapamiętana przed płynnym schodzeniem do minimum (auto-włączenie);
    // zapisy bez `Prefs.version` - to nie rysunek launchera.
    public static int nsBrightPrev(Context c) {
        return sp(c).getInt("ns_bright_prev", 0);
    }

    public static void setNsBrightPrev(Context c, int v) {
        sp(c).edit().putInt("ns_bright_prev", v).apply();
    }

    public static boolean nsBrightSaved(Context c) {
        return sp(c).getBoolean("ns_bright_saved", false);
    }

    public static void setNsBrightSaved(Context c, boolean v) {
        sp(c).edit().putBoolean("ns_bright_saved", v).apply();
    }

    // Czy filtr MA teraz działać (efektywny stan wykonawczy).
    public static boolean effectiveOn(Context c) {
        return state(c) == SolarCalculator.NsState.AUTO_ON || state(c) == SolarCalculator.NsState.MANUAL_ON;
    }

    // Zwraca aktualny stan wynikowy (AUTO_OFF / AUTO_ON / MANUAL_ON / MANUAL_OFF).
    public static SolarCalculator.NsState state(Context c) {
        long now = System.currentTimeMillis();
        int ovr = nsOverride(c);
        long ovrUntil = nsOverrideUntil(c);

        // Ręczne nadpisanie ma pierwszeństwo, ale tylko do czasu wygaśnięcia.
        if (ovr != 0 && ovrUntil > 0) {
            if (now < ovrUntil) {
                return ovr == 1 ? SolarCalculator.NsState.MANUAL_ON : SolarCalculator.NsState.MANUAL_OFF;
            } else {
                // Nadpisanie wygasło – przejmuje automatyka słoneczna.
                clearNsOverride(c);
                LogDiagnostics.stateChange("NightShift", "MANUAL_" + (ovr == 1 ? "ON" : "OFF"), "AUTO", "nadpisanie wygasło");
            }
        }

        // Jeśli użytkownik nie użył jeszcze nowego przełącznika, zachowaj stare zachowanie.
        if (!nsUsed(c)) {
            boolean on = Prefs.nightShiftOn(c);
            boolean auto = Prefs.nightShiftAuto(c);
            return (on && auto) ? SolarCalculator.NsState.AUTO_ON :
                   (on) ? SolarCalculator.NsState.AUTO_ON : SolarCalculator.NsState.AUTO_OFF;
        }

        // Automatyka słoneczna (zachód → wschód).
        return isNightBySun(c) ? SolarCalculator.NsState.AUTO_ON : SolarCalculator.NsState.AUTO_OFF;
    }

    // Klik: przełącz i utrzymaj do najbliższego wschodu/zachodu słońca.
    // Zwraca false, gdy trzeba najpierw dać zgodę (otwiera się ekran Androida).
    public static boolean toggle(Context c) {
        if (!canDraw(c)) {
            requestPermission(c);
            return false;
        }
        boolean now = effectiveOn(c);
        setNsUsed(c, true);
        Prefs.setNightShiftMode(c, "sunset");   // baza = harmonogram słońca
        long until = SolarCalculator.nextSunEvent(c, now);
        if (until < 0L) {
            LogDiagnostics.e("NightShift", "toggle: nie można obliczyć następnego wydarzenia słonecznego");
            return false;
        }
        setNsOverride(c, now ? 2 : 1, until);
        SolarCalculator.NsState from = state(c);
        SolarCalculator.NsState to = now ? SolarCalculator.NsState.MANUAL_OFF : SolarCalculator.NsState.MANUAL_ON;
        LogDiagnostics.stateChange("NightShift", from.name(), to.name(), "ręczne kliknięcie");

        // Uruchom usługę z natychmiastowym przejściem.
        try {
            Intent i = new Intent(c, NightShiftService.class);
            i.putExtra("manual", true);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    // Podpis pod przyciskiem (jak w iOS / Trybie ciemnym).
    public static String label(Context c) {
        SolarCalculator.NsState st = state(c);
        switch (st) {
            case MANUAL_ON:
            case AUTO_ON:
                return "Włączony do wsch. słońca";
            case MANUAL_OFF:
            case AUTO_OFF:
                return "Wyłączony do zach. słońca";
        }
        return "Wyłączony do zach. słońca";
    }

    // Czy wg słońca jest teraz noc (lat/lon z SolarCalculator)
    public static boolean isNightBySun(Context c) {
        try {
            return SolarCalculator.isNight(c);
        } catch (Throwable t) {
            LogDiagnostics.e("NightShift", "isNightBySun failed: " + t.getMessage(), t);
            return false;
        }
    }

    // Ustawia tryb: "off" | "on" | "sunset".
    // Zwraca false, gdy trzeba najpierw dać zgodę (otwiera się ekran Androida).
    public static boolean setMode(Context c, String mode) {
        boolean on = !"off".equals(mode);
        if (on && !canDraw(c)) {
            requestPermission(c);
            return false;
        }
        Prefs.setNightShiftMode(c, mode);
        startStop(c, mode);
        return true;
    }

    // kompatybilność wstecz: on/off
    public static boolean set(Context c, boolean on) {
        return setMode(c, on ? "on" : "off");
    }

    // następny tryb w cyklu: off -> on -> sunset -> off
    public static String nextMode(String cur) {
        if ("on".equals(cur)) return "sunset";
        if ("sunset".equals(cur)) return "off";
        return "on";
    }

    // natychmiastowe odświeżenie koloru nakładki (np. po zmianie ciepła suwakiem)
    public static void refresh(Context c) {
        if (!effectiveOn(c) || !canDraw(c)) return;
        try {
            Intent i = new Intent(c, NightShiftService.class);
            i.putExtra("refresh", true);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Pauza nakładki na czas systemowego okna instalacji APK (patrz Updater.install).
     * Okno instalatora / Play Protect ("Zainstaluj bez skanowania") odcina dotyk,
     * gdy cokolwiek rysuje się nad nim - nawet przezroczysta nakładka
     * nie-łapiąca dotyku (ochrona przed tapjackingiem). Filtr wraca po
     * powrocie do launchera (BaseActivity.onResume -> resume).
     */
    public static void pause(Context c) {
        if (!effectiveOn(c)) return;
        try {
            Intent i = new Intent(c, NightShiftService.class);
            i.putExtra("pause", true);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Powrót nakładki po pauzie (wolane z BaseActivity.onResume). */
    public static void resume(Context c) {
        if (!effectiveOn(c) || !canDraw(c)) return;
        try {
            Intent i = new Intent(c, NightShiftService.class);
            i.putExtra("resume", true);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Synchronizuj usługę z aktualnym stanem (wywoływane po zmianach). */
    public static void syncService(Context c) {
        if (!effectiveOn(c) || !canDraw(c)) {
            // Filtr powinien być wyłączony.
            try {
                Intent i = new Intent(c, NightShiftService.class);
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    c.startForegroundService(i);
                } else {
                    c.startService(i);
                }
                i.putExtra("refresh", true);
                // Usługa sama określi czy filtr ma być aktywny.
            } catch (Throwable ignored) {}
        } else {
            refresh(c);
        }
    }

    private static void startStop(Context c, String mode) {
        try {
            Intent i = new Intent(c, NightShiftService.class);
            if ("off".equals(mode)) {
                c.stopService(i);
            } else {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    c.startForegroundService(i);
                } else {
                    c.startService(i);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void restore(Context c) {
        String m = Prefs.nightShiftMode(c);
        if (!"off".equals(m) && canDraw(c)) {
            startStop(c, m);
        }
    }
}
