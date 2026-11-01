
package pl.dock.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

// Skala interfejsu zaleÅ¼na tylko od rozmiaru ekranu i ustawienia w Docku,
// a nie od systemowego ustawienia "Rozmiar interfejsu" / "Rozmiar czcionki".
public class Scale {
    // 214 dpi na ekranie 1280x720 = wyglÄd zaprojektowany w Docku
    private static final float BASE_DPI = 214f;

    public static float user(Context base) {
        try {
            return base.getSharedPreferences("dock", Context.MODE_PRIVATE).getFloat("uiscale", 1f);
        } catch (Throwable t) {
            return 1f;
        }
    }

    public static Context wrap(Context base) {
        try {
            DisplayMetrics dm = base.getResources().getDisplayMetrics();
            int big = Math.max(dm.widthPixels, dm.heightPixels);
            int small = Math.min(dm.widthPixels, dm.heightPixels);
            float screen = Math.min(big / 1280f, small / 720f);
            int dpi = Math.max(100, Math.round(BASE_DPI * screen * user(base)));
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            cfg.densityDpi = dpi;
            cfg.fontScale = 1f;
            return base.createConfigurationContext(cfg);
        } catch (Throwable t) {
            return base;
        }
    }
}







