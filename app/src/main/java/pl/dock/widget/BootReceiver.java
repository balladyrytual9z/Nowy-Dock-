
package pl.dock.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        StatusService.start(context);
        // OBD core (czarna skrzynka + analityka) – usługa pierwszoplanowa IMPORTANCE_LOW
        pl.dock.widget.ObdCoreService.start(context);
    }
}







