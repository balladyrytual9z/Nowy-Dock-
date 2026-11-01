
package pl.dock.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

public class Look {

    public static volatile Boolean wasNight;

    // Czy teraz jest tryb nocny (rÄcznie, wg godzin albo wg systemu)
    public static boolean night(Context c) {
        boolean result;
        if (Prefs.followSystem(c)) {
            int m = Resources.getSystem().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            result = m == Configuration.UI_MODE_NIGHT_YES;
        } else {
            String m = Prefs.mode(c);
            if ("time".equals(m)) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                int now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
                int st = Prefs.nightStart(c);
                int en = Prefs.nightEnd(c);
                if (st > en) result = now >= st || now < en;
                else result = now >= st && now < en;
            } else {
                result = "night".equals(m);
            }
        }
        wasNight = result;
        return result;
    }

    public static int bg(boolean glass, boolean night) {
        if (glass) return night ? R.drawable.bg_glass_night : R.drawable.bg_glass_day;
        return night ? R.drawable.bg_classic_night : R.drawable.bg_classic_day;
    }

    // Kolory ekranÃ³w Docka (tryb nocny: czerÅ jak w CarPlay)
    public static class Pal {
        public int bg, card, text, sub, accent, sep;
    }

    public static Pal pal(boolean night) {
        Pal p = new Pal();
        if (night) {
            p.bg = 0xFF0B0C0E; // LCD car tablet, not AMOLED pure black
            p.card = 0xFF1C1C1E;
            p.text = 0xFFFFFFFF;
            p.sub = 0xFF8E8E93;
            p.accent = 0xFF0A84FF;
            p.sep = 0xFF2C2C2E;
        } else {
            p.bg = 0xFFE8E8ED;
            p.card = 0xFFFFFFFF;
            p.text = 0xFF10131A;
            p.sub = 0xFF6B7686;
            p.accent = 0xFF0A6CFF;
            p.sep = 0xFFE1E6EE;
        }
        return p;
    }
}





