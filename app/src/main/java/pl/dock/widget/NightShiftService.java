
package pl.dock.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;

public class NightShiftService extends Service {

    private WindowManager wm;
    private View overlay;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        show();
        return START_NOT_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("dock_ns", "Night Shift",
                    NotificationManager.IMPORTANCE_MIN));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "dock_ns") : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Night Shift");
        startForeground(2, b.build());
    }

    private void show() {
        try {
            if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (overlay == null) {
                overlay = new View(this);
                int type = Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT, type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                wm.addView(overlay, lp);
            }
            int strength = Math.max(5, Math.min(80, Prefs.nightShiftStrength(this)));
            overlay.setBackgroundColor(Color.argb((int) (255 * strength / 100f * 0.55f), 255, 140, 40));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (wm != null && overlay != null) wm.removeView(overlay);
        } catch (Throwable ignored) {
        }
        overlay = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}







