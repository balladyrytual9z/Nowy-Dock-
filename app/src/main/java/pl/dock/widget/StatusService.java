package pl.dock.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** Obsluguje widzet Dock: reaguje na zdarzenia systemowe i odswieza widzet tylko przy zmianie. */
public class StatusService extends Service {

    private static final String CHANNEL = "dock_status";
    private static final int NOTIF_ID = 43;
    private BroadcastReceiver receiver;
    private BroadcastReceiver bcastReceiver;
    private long lastBattery = 0;
    private Handler handler;
    private Runnable tick;

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, StatusService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, StatusService.class));
        } catch (Exception ignored) {
        }
    }

    public static void restart(Context c) {
        stop(c);
        start(c);
    }

    // okresowa kontrola to tylko zabezpieczenie; napiecie z pliku / ustawienia nie ma broadcastu
    private long interval() {
        boolean poll = Prefs.showVoltage(this) && !"battery".equals(Prefs.voltSource(this));
        return poll ? 20000L : 60000L;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                    // napiecie baterii potrzebne tylko, gdy jest pokazywane
                    if (!Prefs.showVoltage(context)) return;
                    long now = System.currentTimeMillis();
                    if (now - lastBattery < 5000) return;
                    lastBattery = now;
                }
                BtDevice.handle(intent);
                PhoneBattery.handle(intent);
                StatusCenter.refresh(context);
                DockWidget.updateAll(context);
            }
        };
        BtDevice.init(this);
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        f.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        f.addAction("android.net.conn.TETHER_STATE_CHANGED");
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(PhoneBattery.ACTION_DEV);
        f.addAction(PhoneBattery.ACTION_VENDOR);
        f.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(receiver, f);

        // wlasny broadcast z napieciem (jesli wybrany w ustawieniach)
        String action = Prefs.bcastAction(this);
        if (action.length() > 0) {
            bcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    try {
                        Bundle ex = intent.getExtras();
                        String name = Prefs.bcastExtra(ctx);
                        if (ex != null && name.length() > 0 && ex.containsKey(name)) {
                            @SuppressWarnings("deprecation")
                            Object o = ex.get(name);
                            Double d = Volt.toDouble(o);
                            if (d != null) Prefs.setBcastVal(ctx, d.floatValue());
                        }
                    } catch (Throwable ignored) {
                    }
                    StatusCenter.refresh(ctx);
                    DockWidget.updateAll(ctx);
                }
            };
            registerReceiver(bcastReceiver, new IntentFilter(action));
        }

        handler = new Handler(Looper.getMainLooper());
        tick = new Runnable() {
            @Override
            public void run() {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                boolean screenOn = pm == null || pm.isInteractive();
                // gdy launcher jest na wierzchu, sam dba o odswiezanie
                if (screenOn && !LauncherActivity.visible) {
                    StatusCenter.refresh(StatusService.this);
                    DockWidget.updateAll(StatusService.this);
                }
                handler.postDelayed(this, interval());
            }
        };
        handler.postDelayed(tick, interval());
        AppListWatcher.register(this);
        AppListWatcher.get().addListener(new AppListWatcher.Listener() {
            @Override
            public void onAppsChanged() {
                DockWidget.updateAll(StatusService.this);
            }
        });

    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Dock status", NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Utrzymuje odswiezanie widzetu Dock");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification n;
        if (Build.VERSION.SDK_INT >= 26) {
            n = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentTitle("Dock")
                    .setContentText("Monitor stanu systemu")
                    .setOngoing(true)
                    .build();
        } else {
            n = new Notification.Builder(this)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentTitle("Dock")
                    .setContentText("Monitor stanu systemu")
                    .setOngoing(true)
                    .build();
        }
        startForeground(NOTIF_ID, n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (handler != null && tick != null) {
            handler.removeCallbacks(tick);
        }
        if (receiver != null) {
            try { unregisterReceiver(receiver); } catch (Throwable ignored) {}
            receiver = null;
        }
        if (bcastReceiver != null) {
            try { unregisterReceiver(bcastReceiver); } catch (Throwable ignored) {}
            bcastReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
