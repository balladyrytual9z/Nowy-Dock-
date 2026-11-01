package pl.dock.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

// Nakladka Night Shift. Kolor narasta plynnie:
// - reczne wlaczenie / wylaczenie: krotkie przejscie ok. 800 ms,
// - tryb "od zachodu do wschodu": po zachodzie sila narasta od 0 do celu w ~2 min,
// - suwak ciepla w panelu lacznosci zmienia kolor na zywo.
public class NightShiftService extends Service {

    private static final long FADE_MS = 800L;        // plynne przejscie przy recznym on/off
    private static final long SUNSET_RAMP_MS = 120000L; // narastanie po zachodzie (~2 min)
    private static final long SUN_CHECK_MS = 60000L;  // sprawdzenie zachodu co minute

    private WindowManager wm;
    private View overlay;
    private final Handler h = new Handler(Looper.getMainLooper());
    private android.animation.ValueAnimator anim;
    private float currentAlpha = 0f;   // aktualna widoczna sila filtru (0..1)
    private boolean sunsetMode = false;

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean refresh = intent != null && intent.getBooleanExtra("refresh", false);
        String mode = Prefs.nightShiftMode(this);
        sunsetMode = "sunset".equals(mode);
        if ("off".equals(mode)) {
            hideOverlayAnimated();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (refresh) {
            // zmiana ciepla suwakiem: plytka korekta do nowej sily
            showOverlay();
            animateTo(targetStrengthFraction(), 250L);
            scheduleSunCheck();
            return START_NOT_STICKY;
        }
        showOverlay();
        boolean night = !sunsetMode || NightShift.isNightBySun(this);
        if (night) {
            // po zachodzie filtr narasta powoli; przy recznym wlaczeniu krotko
            animateTo(targetStrengthFraction(), sunsetMode ? SUNSET_RAMP_MS : FADE_MS);
        } else {
            animateTo(0f, FADE_MS);
        }
        scheduleSunCheck();
        return START_NOT_STICKY;
    }

    // docelowa widocznosc filtru jako ulamek 0..1
    private float targetStrengthFraction() {
        int strength = Math.max(5, Math.min(80, Prefs.nightShiftStrength(this)));
        return (strength / 100f) * 0.55f;
    }

    private void applyFraction(float f) {
        currentAlpha = f;
        if (overlay != null) {
            int a = Math.round(255f * Math.max(0f, Math.min(1f, f)));
            overlay.setBackgroundColor(Color.argb(a, 255, 140, 40));
        }
    }

    private void animateTo(float target, long duration) {
        if (anim != null) anim.cancel();
        if (overlay == null) return;
        if (duration <= 0 || Prefs.reduceMotion(this)) {
            applyFraction(target);
            return;
        }
        anim = android.animation.ValueAnimator.ofFloat(currentAlpha, target);
        anim.setDuration(duration);
        anim.setInterpolator(new DecelerateInterpolator(1.2f));
        anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                applyFraction((Float) a.getAnimatedValue());
            }
        });
        anim.start();
    }

    private void scheduleSunCheck() {
        h.removeCallbacksAndMessages(null);
        if (!sunsetMode) return;
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!sunsetMode) return;
                boolean night = NightShift.isNightBySun(NightShiftService.this);
                float target = night ? targetStrengthFraction() : 0f;
                if (Math.abs(target - currentAlpha) > 0.001f) {
                    animateTo(target, night ? SUNSET_RAMP_MS : FADE_MS);
                }
                h.postDelayed(this, SUN_CHECK_MS);
            }
        }, SUN_CHECK_MS);
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("dock_ns", "Night Shift",
                    NotificationManager.IMPORTANCE_MIN));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "dock_ns") : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Night Shift");
        startForeground(2, b.build());
    }

    private void showOverlay() {
        try {
            if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (overlay == null) {
                overlay = new View(this);
                int type = Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT, type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                applyFraction(currentAlpha);
                wm.addView(overlay, lp);
            }
        } catch (Throwable ignored) {
        }
    }

    private void hideOverlayAnimated() {
        // usluga zaraz umiera - szybkie zanikanie wystarczy
        try {
            if (overlay != null && wm != null) {
                overlay.animate().alpha(0f).setDuration(FADE_MS).start();
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (anim != null) anim.cancel();
        try {
            if (wm != null && overlay != null) wm.removeView(overlay);
        } catch (Throwable ignored) {
        }
        overlay = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
