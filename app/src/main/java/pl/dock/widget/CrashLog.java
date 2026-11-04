package pl.dock.widget;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Zapis ostatniego crasha, zeby po awarii dalo sie odczytac stos wywolan
 * bez adb (raport w diagnostyce: sekcja "Ostatni crash").
 *
 * Handler tylko ZAPISUJE (synchronicznie, commit) i przekazuje wyjatek dalej
 * do domyslnego handlera systemu - zachowanie awarii sie nie zmienia.
 */
public final class CrashLog {

    private static final String KEY = "lastcrash";
    private static final int MAX_CHARS = 6000;

    private CrashLog() {
    }

    public static void install(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String st = sw.toString();
                    if (st.length() > MAX_CHARS) st = st.substring(0, MAX_CHARS) + "\n...";
                    String head = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .format(new Date()) + "  watek: " + t.getName() + "\n";
                    SharedPreferences sp = Prefs.sp(app);
                    sp.edit().putString(KEY, head + st).commit();
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    public static String last(Context c) {
        try {
            return Prefs.sp(c).getString(KEY, "brak");
        } catch (Throwable t) {
            return "brak";
        }
    }
}
