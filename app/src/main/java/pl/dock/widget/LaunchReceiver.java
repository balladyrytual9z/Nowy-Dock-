
package pl.dock.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// ObsÅuguje klikniÄcie ikony na widÅ¼ecie: zlicza uÅ¼ycie i uruchamia aplikacjÄ
public class LaunchReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        long id = i.getLongExtra("id", 0);
        if (id == 0) {
            Launch.carplay(c);
            return;
        }
        String pkg = "";
        for (AppEntry e : Prefs.apps(c)) {
            if (e.id == id) {
                pkg = e.pkg;
                break;
            }
        }
        Prefs.recordPkg(c, pkg);
        Intent launch = pkg.length() > 0
                ? c.getPackageManager().getLaunchIntentForPackage(pkg) : null;
        if (launch == null) {
            launch = new Intent(c, ConfigActivity.class);
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(launch);
        } catch (Exception ignored) {
        }
        DockWidget.updateAll(c);
    }
}







