package pl.dock.widget;

import android.app.Application;

/** Klasa aplikacji: instaluje zapis crashy jak najwczesniej (przed jakakolwiek aktywnoscia). */
public class DockApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
    }
}
