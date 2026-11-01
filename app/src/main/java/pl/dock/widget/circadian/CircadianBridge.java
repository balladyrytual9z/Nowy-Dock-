
package pl.dock.widget.circadian;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

/** Most do Circadian Display. DziaÅa tylko gdy pakiet jest zainstalowany. */
public final class CircadianBridge {
    public static final String PKG = "com.circadiandisplay.app";
    public static final String AUTHORITY = "com.circadiandisplay.app.state";
    /** Dock â Circadian (or internal): night mode changed. */
    public static final String ACTION_NIGHT = "pl.dock.widget.NIGHT_MODE_CHANGED";
    /** Circadian Display â Dock: overlay / profile state flipped (extra isNight). */
    public static final String ACTION_STATE_CHANGED = "pl.circadiandisplay.STATE_CHANGED";

    public static boolean isInstalled(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean activateForNight(Context c) {
        if (!isInstalled(c)) return false;
        try {
            Bundle b = c.getContentResolver().call(
                    Uri.parse("content://" + AUTHORITY), "activate_for_night", null, null);
            return b != null && b.getBoolean("ok", false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean setNightMode(Context c, boolean isNight) {
        if (!isInstalled(c)) return false;
        try {
            Bundle in = new Bundle();
            in.putBoolean("isNight", isNight);
            c.getContentResolver().call(Uri.parse("content://" + AUTHORITY),
                    "set_night_mode", null, in);
            Intent i = new Intent(ACTION_NIGHT).setPackage(PKG).putExtra("isNight", isNight);
            c.sendBroadcast(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Intent openApp(Context c) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(PKG);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    public static Intent openSettingsIntent(Context c) {
        return openApp(c);
    }

    private CircadianBridge() {}
}





