package pl.dock.widget.circadian;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import java.lang.reflect.Method;
import java.util.List;

// Wbudowany Circadian Display: SchedulerWorker + OverlayDisplayController
// w jednym, w procesie Docka. NIE wymaga instalacji osobnej aplikacji.
//
// Co minute evaluuje krzywa dobowe (CircadianCurve) i naklada JEDNO pelnoekranowe
// okno nakladki: najpierw czarna warstwa przyciemnienia, na niej bursztynowa
// warstwa ciepla (#FFB300, max alpha 0.5 - jak w oryginale). Okno jest
// FLAG_NOT_TOUCHABLE, a na Androidzie 12+ jego alpha jest ograniczona do
// maksymalnej "obscuring opacity for touch", zeby gesty i dotyki przechodzily
// pod nakladke (port tego samego rozwiazania co w Circadian Display).
//
// Gdy noc (cieplo >= 0.35) sie wlacza/wylacza, nadaje broadcast
// pl.circadiandisplay.STATE_CHANGED z extra isNight - dzieki temu istniejacy
// NightModeReceiver dalej synchronizuje motyw nocy Docka.
public class CircadianService extends Service {

    public static final String ACTION_STATE_CHANGED = "pl.circadiandisplay.STATE_CHANGED";

    private static final long TICK_MS = 60000L;
    private static final float MAX_WARMTH_ALPHA = 0.5f;
    private static final float DEFAULT_MAX_OBSCURING_OPACITY = 0.8f;
    private static final int NOTIF_ID = 3;

    private WindowManager wm;
    private FilterView overlay;
    private float windowAlpha = 1f;
    private boolean lastNight = false;
    private android.animation.ValueAnimator glide;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                evaluate();
            } catch (Throwable ignored) {
            }
            h.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // JEDEN filtr naraz: kafel Night Shift w Centrum sterowania ma pierwszenstwo
        if (!CircadianPrefs.enabled(this) || !canDraw(this)
                || !"off".equals(pl.dock.widget.Prefs.nightShiftMode(this))) {
            clearOverlay();
            stopSelf();
            return START_NOT_STICKY;
        }
        h.removeCallbacks(tick);
        evaluate();
        h.postDelayed(tick, TICK_MS);
        return START_STICKY;
    }

    public static boolean canDraw(Context c) {
        try {
            return Settings.canDrawOverlays(c);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int nowMinutes() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
    }

    private void evaluate() {
        List<float[]> pts = CircadianPrefs.points(this);
        float[] st = CircadianCurve.evaluate(pts, nowMinutes());
        applyState(st[0], st[1]);

        boolean night = st[0] >= 0.35f;
        if (night != lastNight) {
            lastNight = night;
            try {
                Intent i = new Intent(ACTION_STATE_CHANGED)
                        .setPackage(getPackageName())
                        .putExtra("isNight", night)
                        .putExtra("warmth", st[0])
                        .putExtra("dimming", st[1]);
                sendBroadcast(i);
            } catch (Throwable ignored) {
            }
        }
    }

    // ── nakladka (port OverlayDisplayController) ──────────────────────────

    private float obscuringOpacity() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                Object im = getSystemService(Context.INPUT_SERVICE);
                Method m = im.getClass().getMethod("getMaximumObscuringOpacityForTouch");
                Object v = m.invoke(im);
                if (v instanceof Float) {
                    return Math.max(0.05f, Math.min(1f, (Float) v));
                }
            } catch (Throwable ignored) {
            }
            return DEFAULT_MAX_OBSCURING_OPACITY;
        }
        return 1f;
    }

    private void applyState(float warmth, float dimming) {
        warmth = Math.max(0f, Math.min(1f, warmth));
        dimming = Math.max(0f, Math.min(1f, dimming));
        if (overlay == null) addOverlay();
        if (overlay == null) return;

        // kompensacja alpha okna, by netto na ekranie bylo dokladnie tyle,
        // ile zada krzywa (do sufitu 0.8 na Androidzie 12+)
                float comp = 1f / windowAlpha;
        final float tDim = Math.max(0f, Math.min(1f, dimming * comp));
        final float tWarm = Math.max(0f, Math.min(1f, warmth * MAX_WARMTH_ALPHA * comp));
        glideTo(tWarm, tDim);
    }

    // Plynne nakladanie krzywej: nowe wartosci narastaja gladko w ~2,5 s, wiez
    // 1-minutowe ticki sa niewyczuwalne, a wlaczenie od zera to miekkie pojawienie.
    private void glideTo(final float tWarm, final float tDim) {
        if (overlay == null) return;
        if (glide != null) glide.cancel();
        final float w0 = overlay.warmthAlpha;
        final float d0 = overlay.dimmingAlpha;
        if (Math.abs(tWarm - w0) < 0.002f && Math.abs(tDim - d0) < 0.002f) return;
        if (pl.dock.widget.Prefs.reduceMotion(this)) {
            overlay.warmthAlpha = tWarm;
            overlay.dimmingAlpha = tDim;
            overlay.invalidate();
            return;
        }
        glide = android.animation.ValueAnimator.ofFloat(0f, 1f);
        glide.setDuration(2500L);
        glide.setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f));
        glide.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                if (overlay != null) {
                    overlay.warmthAlpha = w0 + (tWarm - w0) * t;
                    overlay.dimmingAlpha = d0 + (tDim - d0) * t;
                    overlay.invalidate();
                }
            }
        });
        glide.start();
    }

    private void addOverlay() {
        try {
            if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (overlay != null) return;
            FilterView v = new FilterView(this);
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = 0;
            lp.y = 0;
            windowAlpha = obscuringOpacity();
            lp.alpha = windowAlpha;
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            wm.addView(v, lp);
            overlay = v;
        } catch (Throwable ignored) {
        }
    }

    private void clearOverlay() {
        try {
            if (wm != null && overlay != null) wm.removeView(overlay);
        } catch (Throwable ignored) {
        }
        overlay = null;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("dock_circadian",
                    "Circadian Display", NotificationManager.IMPORTANCE_MIN));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "dock_circadian")
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Circadian Display");
        startForeground(NOTIF_ID, b.build());
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (glide != null) glide.cancel();
        clearOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ── API statyczne dla reszty Docka ───────────────────────────────────

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, CircadianService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, CircadianService.class));
        } catch (Throwable ignored) {
        }
    }

    // wlacz/wylacz + natychmiastowe odswiezenie
    public static void refresh(Context c) {
        if (CircadianPrefs.enabled(c) && canDraw(c)) {
            start(c);
        } else {
            stop(c);
        }
    }

    // po restarcie urzadzenia
    public static void restore(Context c) {
        if (CircadianPrefs.enabled(c)) {
            CircadianPrefs.seed(c);
            start(c);
        }
    }

    // "aktywuj na noc" - odpowiednik activate_for_night z zewnetrznej aplikacji
    public static void activateForNight(Context c) {
        CircadianPrefs.seed(c);
        CircadianPrefs.setEnabled(c, true);
        refresh(c);
    }

    // Jedno okno, dwa layery rysowane w jednym przebiegu onDraw.
    private static final class FilterView extends View {
        float dimmingAlpha = 0f;
        float warmthAlpha = 0f;

        FilterView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (dimmingAlpha > 0f) {
                canvas.drawARGB((int) (dimmingAlpha * 255f), 0, 0, 0);
            }
            if (warmthAlpha > 0f) {
                canvas.drawARGB((int) (warmthAlpha * 255f), 0xFF, 0xB3, 0x00);
            }
        }
    }
}
