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
}
