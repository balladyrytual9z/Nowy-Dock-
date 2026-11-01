
package pl.dock.widget;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import java.util.Locale;

/**
 * Sensor Fusion for CarPlay / DiPlay projection state (Android 9 / API 28).
 *
 * Deterministic state machine aggregating independent vectors:
 *  1. NotificationListenerService – DiPlaySessionService foreground notifications
 *  2. MediaSessionManager – audio session STATE_PLAYING / PAUSED for DiPlay package
 *  3. UsageStatsManager – recent ACTIVITY_RESUMED of CarPlayHostActivity (polling)
 *  4. USB – Apple VID 0x05AC present
 *  5. Optional broadcast / Prefs.cpbVal override
 *
 * States: OFF → PHONE (connecting / detected) → CONNECTED (active session / foreground)
 */
public class CarplayState {
    public static final int OFF = 0;
    public static final int PHONE = 1;       // connecting / iPhone detected
    public static final int CONNECTED = 2;   // active projection

    public static int read(Context c) {
        Context app = c.getApplicationContext();
        if (!Prefs.cpShow(app)) return OFF;
        int st = OFF;
        String pkg = Prefs.diplayPkg(app);

        // 5. Explicit override from broadcast / prefs
        String b = Prefs.cpbVal(app).toLowerCase(Locale.US);
        if (b.length() > 0) {
            if (b.contains("connected") || b.contains("streaming") || b.contains("active")
                    || b.equals("true") || b.equals("1")) {
                st = Math.max(st, CONNECTED);
            } else if (b.contains("connecting") || b.contains("waiting") || b.contains("phone")) {
                st = Math.max(st, PHONE);
            }
        }

        // 2. Media session
        try {
            if (Prefs.cpUseSession(app) && MediaHub.hasSession(app, pkg)) {
                st = Math.max(st, CONNECTED);
            }
        } catch (Throwable ignored) {
        }

        // 1. Notification
        try {
            if (Prefs.cpUseNotif(app)) {
                String[] n = DockNotificationListener.notificationText(pkg);
                if (n != null) {
                    String all = (n[0] + " " + n[1]).toLowerCase(Locale.US);
                    if (all.contains("error") || all.contains("fail") || all.contains("błąd")) {
                        // leave as is or force lower; treat as not connected
                    } else if (all.contains("connecting") || all.contains("waiting")
                            || all.contains("searching") || all.contains("łączenie")
                            || all.contains("pairing")) {
                        st = Math.max(st, PHONE);
                    } else {
                        st = Math.max(st, CONNECTED);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 4. USB Apple
        try {
            if (Prefs.cpUseUsb(app) && appleUsbPresent(app)) {
                st = Math.max(st, PHONE);
            }
        } catch (Throwable ignored) {
        }

        // 3. UsageStats – recent CarPlay host activity (requires PACKAGE_USAGE_STATS)
        try {
            if (st < CONNECTED && recentCarPlayHost(app, pkg)) {
                st = Math.max(st, CONNECTED);
            }
        } catch (Throwable ignored) {
        }

        return st;
    }

    /** True if a CarPlay host activity was resumed in the last ~8 seconds. */
    private static boolean recentCarPlayHost(Context c, String diplayPkg) {
        if (Build.VERSION.SDK_INT < 22) return false;
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return false;
        long now = System.currentTimeMillis();
        long begin = now - 8000;
        UsageEvents events = usm.queryEvents(begin, now);
        if (events == null) return false;
        UsageEvents.Event ev = new UsageEvents.Event();
        String[] hosts = {
                diplayPkg,
                "com.shilapi.xcertplay",
                "com.carplay.dipower",
                "com.shihab.diplay"
        };
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            if (ev.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND
                    && ev.getEventType() != 1 /* ACTIVITY_RESUMED legacy */) {
                continue;
            }
            String cn = ev.getClassName();
            String pn = ev.getPackageName();
            if (cn == null) cn = "";
            if (pn == null) continue;
            boolean pkgMatch = false;
            for (String h : hosts) {
                if (pn.equals(h) || pn.startsWith(h)) {
                    pkgMatch = true;
                    break;
                }
            }
            if (!pkgMatch) continue;
            if (cn.contains("CarPlayHost") || cn.contains("CarPlay") || cn.contains("Projection")
                    || cn.contains("HostActivity") || cn.contains("SameTaskCarPlay")) {
                return true;
            }
        }
        return false;
    }

    public static boolean appleUsbPresent(Context c) {
        UsbManager um = (UsbManager) c.getSystemService(Context.USB_SERVICE);
        if (um == null || um.getDeviceList() == null) return false;
        for (UsbDevice d : um.getDeviceList().values()) {
            if (d.getVendorId() == 0x05AC) return true;
        }
        return false;
    }

    public static int color(int state, int fg) {
        if (state == CONNECTED) return 0xFF30D158; // iOS green
        if (state == PHONE) return 0xFFFF9F0A;     // amber / connecting
        return (fg & 0x00FFFFFF) | 0x40000000;
    }

    public static String label(int state) {
        if (state == CONNECTED) return "CarPlay połączony";
        if (state == PHONE) return "iPhone wykryty / łączenie";
        return "CarPlay nieaktywny";
    }
}



