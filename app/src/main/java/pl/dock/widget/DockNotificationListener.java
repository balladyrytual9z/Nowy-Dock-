
package pl.dock.widget;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

// Daje dostęp do sesji multimedialnych innych aplikacji (np. DiPlay) i ich powiadomień.
// Wymaga jednorazowego włączenia "Dostępu do powiadomień" dla Docka w ustawieniach Androida.
public class DockNotificationListener extends NotificationListenerService {

    private static volatile DockNotificationListener instance;
    private static volatile String removedKey = "";
    private static volatile long removedTs;

    public static boolean connected() {
        return instance != null;
    }

    public static boolean hasNotificationFrom(String pkg) {
        return notificationText(pkg) != null;
    }

    /** Number of active notifications for a package (for iOS-style badges). */
    public static int notificationCount(String pkg) {
        DockNotificationListener l = instance;
        if (l == null || pkg == null) return 0;
        try {
            StatusBarNotification[] all = l.getActiveNotifications();
            if (all == null) return 0;
            int count = 0;
            for (StatusBarNotification n : all) {
                if (pkg.equals(n.getPackageName())) {
                    // skip ongoing / group summaries if possible
                    if ((n.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                    count++;
                }
            }
            return count;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // tytuł i tekst pierwszego aktywnego powiadomienia aplikacji (null = brak)
    public static String[] notificationText(String pkg) {
        DockNotificationListener l = instance;
        if (l == null || pkg == null) return null;
        try {
            if (removedKey.length() > 0
                    && System.currentTimeMillis() - removedTs > 2000) {
                removedKey = "";
            }
            StatusBarNotification[] all = l.getActiveNotifications();
            if (all == null) return null;
            for (StatusBarNotification n : all) {
                if (!pkg.equals(n.getPackageName())) continue;
                if (n.getKey() != null && n.getKey().equals(removedKey)) continue;
                android.os.Bundle ex = n.getNotification().extras;
                CharSequence t = ex == null ? null : ex.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence x = ex == null ? null : ex.getCharSequence(Notification.EXTRA_TEXT);
                return new String[]{t == null ? "" : t.toString(), x == null ? "" : x.toString()};
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        removedKey = "";
        MediaHub.get().onListenerEvent();
        notifyStatus();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn != null && sbn.getKey() != null && sbn.getKey().equals(removedKey)) removedKey = "";
        MediaHub.get().onListenerEvent();
        notifyStatus();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // w chwili zdarzenia lista aktywnych może jeszcze zawierać usuwane powiadomienie
        removedKey = sbn == null || sbn.getKey() == null ? "" : sbn.getKey();
        removedTs = System.currentTimeMillis();
        MediaHub.get().onListenerEvent();
        notifyStatus();
    }

    // zmiana stanu CarPlay jest zgłaszana od razu, bez czekania na okresowe odświeżenie
    private void notifyStatus() {
        final android.content.Context ctx = getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    StatusCenter.refreshCp(ctx);
                    DockWidget.updateAll(ctx);
                } catch (Throwable ignored) {
                }
            }
        });
    }
}







