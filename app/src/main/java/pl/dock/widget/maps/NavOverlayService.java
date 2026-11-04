package pl.dock.widget.maps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import pl.dock.widget.AppEntry;
import pl.dock.widget.DockView;
import pl.dock.widget.Icons;
import pl.dock.widget.Launch;
import pl.dock.widget.Look;
import pl.dock.widget.Prefs;
import pl.dock.widget.StatusCenter;
import pl.dock.widget.Ui;

/**
 * Nakladka CarPlay nad Organic Maps.
 *
 * Dwa okna systemowe (SYSTEM_ALERT_WINDOW):
 *  1) chrome - pelnoekranowe, NIEKLIKALNE (FLAG_NOT_TOUCHABLE): karta manewru,
 *     pasek ETA i kolo predkosci z tarcza limitu. Dotkniecia przechodza do mapy.
 *  2) dock - waski pasek z boku, KLIKALNY: te same 4 ikony co w launcherze.
 *
 * Nakladka pokazuje sie tylko wtedy, gdy trwa nawigacja (NavData.isFresh())
 * i Organic Maps jest na wierzchu. Gdy uzytkownik wyjdzie z mapy, znika.
 */
public class NavOverlayService extends Service implements NavData.Listener {

    private static final String CHANNEL = "nav_overlay";
    private static final int NOTIF_ID = 0x4E41;
    private static final long OM_GRACE_MS = 4000L;

    private WindowManager wm;
    private NavChromeView chrome;
    private DockView dock;
    private boolean chromeAdded;
    private boolean dockAdded;
    private boolean visible;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshDock();
            h.postDelayed(this, 30000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        NavData.get().addListener(this);
        startForegroundSafe();
        h.postDelayed(tick, 30000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        update();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        NavData.get().removeListener(this);
        h.removeCallbacks(tick);
        hideOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onNavChanged(NavData data) {
        update();
    }

    // ---------------------------------------------------------------- widocznosc

    private void update() {
        NavData nd = NavData.get();
        boolean show = nd.isFresh()
                && (nd.isOmForeground() || nd.msSinceOmEvent() < OM_GRACE_MS);

        if (show == visible) {
            if (show && chrome != null) chrome.invalidate();
            return;
        }
        visible = show;
        if (show) showOverlay();
        else hideOverlay();
    }

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) return;

        if (!chromeAdded) {
            try {
                chrome = new NavChromeView(this);
                chrome.setNight(Look.night(this));
                wm.addView(chrome, chromeParams());
                chromeAdded = true;
            } catch (Throwable t) {
                chrome = null;
                chromeAdded = false;
            }
        }
        if (!dockAdded) {
            try {
                dock = new DockView(this);
                wm.addView(dock, dockParams());
                dockAdded = true;
                wireDock();
            } catch (Throwable t) {
                dock = null;
                dockAdded = false;
            }
        }
        if (chrome != null) chrome.invalidate();
    }

    private void hideOverlay() {
        if (chromeAdded && chrome != null) {
            try {
                wm.removeView(chrome);
            } catch (Throwable ignored) {
            }
        }
        chrome = null;
        chromeAdded = false;

        if (dockAdded && dock != null) {
            try {
                wm.removeView(dock);
            } catch (Throwable ignored) {
            }
        }
        dock = null;
        dockAdded = false;
    }

    // ---------------------------------------------------------------- okna

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private WindowManager.LayoutParams chromeParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    private WindowManager.LayoutParams dockParams() {
        int dockPx = Ui.dp(this, Ui.DOCK_OFFSET_DP);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dockPx,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Ui.dockLeft(this)
                ? (Gravity.TOP | Gravity.START)
                : (Gravity.TOP | Gravity.END);
        return lp;
    }

    // ---------------------------------------------------------------- dok

    private void wireDock() {
        if (dock == null) return;
        dock.setListener(new DockView.Listener() {
            @Override
            public void onHeader() {
                openLauncher();
            }

            @Override
            public void onApp(int index) {
                openApp(index);
            }

            @Override
            public void onBottom() {
            }

            @Override
            public void onBottomLong() {
            }
        });
        refreshDock();
    }

    private void refreshDock() {
        if (dock == null) return;
        try {
            Bitmap[] icons = new Bitmap[4];
            List<AppEntry> top = Prefs.top(this, 3);
            for (int i = 0; i < 3 && i < top.size(); i++) {
                icons[i] = Icons.forEntry(this, top.get(i));
            }
            icons[3] = Icons.forEntry(this, Prefs.carplay(this));

            StatusCenter.Snap s = StatusCenter.get(this);
            Calendar cal = Calendar.getInstance();
            String clock = String.format(Locale.US, "%02d:%02d",
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));

            dock.setData(Look.night(this), false, clock, s, Prefs.showVoltage(this), icons);
            dock.setCarplayState(s.cp);
        } catch (Throwable ignored) {
        }
    }

    private void openApp(int index) {
        try {
            if (index == 3) {
                Launch.carplay(this);
                return;
            }
            List<AppEntry> top = Prefs.top(this, 3);
            if (index < top.size()) {
                String pkg = top.get(index).pkg;
                if (Launch.openDockTool(this, pkg)) return;
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void openLauncher() {
        try {
            Intent i = new Intent(this, pl.dock.widget.LauncherActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- foreground

    private void startForegroundSafe() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL,
                        "Nakladka nawigacji", NotificationManager.IMPORTANCE_MIN);
                ch.setShowBadge(false);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
            }
            Notification n = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentTitle("Dock")
                    .setContentText("Nakladka nawigacji gotowa")
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_MIN)
                    .build();
            startForeground(NOTIF_ID, n);
        } catch (Throwable ignored) {
        }
    }
}
