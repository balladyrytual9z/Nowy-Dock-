package pl.dock.widget;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

public class BaseActivity extends Activity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Scale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // pelny ekran jak CarPlay - pasek nawigacji Androida nigdy nie wyskakuje
        try {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Powrot do launchera (Home albo koniec okna instalatora APK) - przywroc
        // nakladke Night Shift, jesli Updater pauzowal ja na czas instalacji.
        // Gdy filtra nie ma, wolanie nic nie robi. Wszystkie ekrany dziedzicza
        // po BaseActivity, wiec jedno miejsce obsluguje caly launcher.
        try {
            NightShift.resume(this);
        } catch (Throwable ignored) {
        }
    }
}
