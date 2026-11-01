
package pl.dock.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Pojedyncze dotknięcie zegara / statusów otwiera panel łączności
public class HeaderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Intent open = new Intent(c, ConnectivityActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(open);
        } catch (Throwable ignored) {
        }
    }
}







