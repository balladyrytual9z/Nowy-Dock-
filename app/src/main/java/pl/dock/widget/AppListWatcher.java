
package pl.dock.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.concurrent.CopyOnWriteArrayList;

/** Centralne ÅºrÃ³dÅo informacji o zmianie listy aplikacji. */
public final class AppListWatcher extends BroadcastReceiver {

    public interface Listener {
        void onAppsChanged();
    }

    private static AppListWatcher instance;
    private static boolean registered;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public static synchronized AppListWatcher get() {
        if (instance == null) instance = new AppListWatcher();
        return instance;
    }

    private AppListWatcher() {}

    public static void register(Context c) {
        if (registered) return;
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addAction(Intent.ACTION_PACKAGE_REPLACED);
            f.addDataScheme("package");
            c.getApplicationContext().registerReceiver(get(), f);
            registered = true;
        } catch (Throwable ignored) {}
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public static void notifyChanged(Context c) {
        Icons.invalidate();
        try {
            DockWidget.updateAll(c);
        } catch (Throwable ignored) {}
        for (Listener l : get().listeners) {
            try {
                l.onAppsChanged();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (Intent.ACTION_PACKAGE_REPLACED.equals(action)
                && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            return;
        }
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            return;
        }
        if (Intent.ACTION_PACKAGE_ADDED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            Icons.invalidate();
            try {
                DockWidget.updateAll(context);
            } catch (Throwable ignored) {}
            for (Listener l : listeners) {
                try {
                    l.onAppsChanged();
                } catch (Throwable ignored) {}
            }
        }
    }
}






