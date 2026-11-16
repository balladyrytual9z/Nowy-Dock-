package pl.dock.widget;

import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;

/** Klasa aplikacji: instaluje zapis crashy jak najwczesniej (przed jakakolwiek aktywnoscia). */
public class DockApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        // Honda Premium Startup: wyzwalacz wlaczenia ekranu (SCREEN_ON).
        // Rejestracja dynamiczna - manifestowy receiver nie dostaje
        // ACTION_SCREEN_ON na Androidzie 9. Receiver jest cichy, gdy
        // animacja zostala pokazana niedawno (debounce w ScreenOnReceiver).
        try {
            registerReceiver(new ScreenOnReceiver(), new IntentFilter(Intent.ACTION_SCREEN_ON));
        } catch (Throwable t) {
            LogDiagnostics.e("DockApp", "registerReceiver SCREEN_ON: " + t.getMessage(), t);
        }
    }
}
