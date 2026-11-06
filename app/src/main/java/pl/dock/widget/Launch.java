
package pl.dock.widget;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

// Uruchamianie aplikacji; CarPlay otwiera od razu okno z obrazem z iPhone'a (jeśli da się je wskazać)
public class Launch {

    // Szukamy aktywności wyświetlającej obraz CarPlay: najpierw "CarPlayHost", potem inne "CarPlay".
    private static String autoActivity(Context c, String pkg) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            if (pi.activities == null) return "";
            String second = "";
            for (ActivityInfo ai : pi.activities) {
                if (!ai.exported || !ai.enabled) continue;
                String n = ai.name.toLowerCase();
                if (n.contains("carplayhost")) return ai.name;
                if (second.length() == 0 && (n.contains("carplay") || n.contains("projection"))) {
                    second = ai.name;
                }
            }
            return second;
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static Intent carplayIntent(Context c) {
        String pkg = Prefs.diplayPkg(c);
        String act = Prefs.cpActivity(c);
        if ("-".equals(act)) act = "";
        else if (act.length() == 0) act = autoActivity(c, pkg);
        if (act.length() > 0) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(pkg, act);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return i;
        }
        Intent l = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (l != null) l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return l;
    }

    public static boolean start(Context c, Intent i) {
        try {
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Narzedzia Docka (pseudo-pakiety "dock:...") uzywane w 4 ikonach dok-a.
     * Zwraca true, gdy pakiet byl narzedziem Docka i zostalo otwarte.
     */
    public static boolean openDockTool(Context c, String pkg) {
        if (pkg == null || !pkg.startsWith("dock:")) return false;
        if ("dock:settings".equals(pkg)) {
            Intent i = new Intent(c, ConfigActivity.class);
            i.putExtra("origin_page", Prefs.lastPage(c));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return start(c, i);
        }
        if (pkg.equals("dock:carplay")) {
            carplay(c);
            return true;
        }
        // dock:obd oraz nieznane "dock:" -> centrum OBD
        Intent i = new Intent(c, ObdDiscoveryActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return start(c, i);
    }

    public static void carplay(Context c) {
        Intent i = carplayIntent(c);
        if (i != null && start(c, i)) return;
        Intent l = c.getPackageManager().getLaunchIntentForPackage(Prefs.diplayPkg(c));
        if (l != null) {
            l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            start(c, l);
        }
    }

    // do wyboru w ustawieniach: {nazwa klasy, "eksportowana"/"wewnętrzna"}
    public static List<String[]> activities(Context c) {
        List<String[]> out = new ArrayList<String[]>();
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(Prefs.diplayPkg(c),
                    PackageManager.GET_ACTIVITIES);
            if (pi.activities != null) {
                for (ActivityInfo ai : pi.activities) {
                    out.add(new String[]{ai.name, ai.exported ? "eksportowana" : "wewnętrzna"});
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    // przeniesienie do aplikacji, z której idzie dźwięk (CarPlay -> okno CarPlay w DiPlay)
    public static void openSource(Context c, String pkg) {
        if (pkg == null || pkg.length() == 0 || pkg.equals(Prefs.diplayPkg(c))) {
            carplay(c);
            return;
        }
        Intent l = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (l != null) {
            l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            start(c, l);
        } else {
            carplay(c);
        }
    }
}







