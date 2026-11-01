
package pl.dock.widget;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

// Night Shift: ciepÅy filtr barwy ekranu (nakÅadka). Wymaga zgody "WyÅwietlaj nad innymi aplikacjami".
public class NightShift {

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

    public static boolean isOn(Context c) {
        return Prefs.nightShiftOn(c);
    }

    // zwraca false, gdy trzeba najpierw daÄ zgodÄ (otwiera siÄ odpowiedni ekran Androida)
    public static boolean set(Context c, boolean on) {
        if (on && !canDraw(c)) {
            requestPermission(c);
            return false;
        }
        Prefs.setNightShiftOn(c, on);
        try {
            Intent i = new Intent(c, NightShiftService.class);
            if (on) {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    c.startForegroundService(i);
                } else {
                    c.startService(i);
                }
            } else {
                c.stopService(i);
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    public static void restore(Context c) {
        if (Prefs.nightShiftOn(c) && canDraw(c)) {
            set(c, true);
        }
    }
}







