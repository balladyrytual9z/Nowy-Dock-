package pl.dock.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
//
// Na czas systemowego okna instalacji APK nakladka schodzi z ekranu ("pause")
// i wraca po powrocie do launchera ("resume"): okno instalatora i Play Protect
// odcinaja dotyk, gdy cokolwiek rysuje sie nad nimi (ochrona przed tapjackingiem).
public class NightShiftService extends Service {

    private static final long FADE_MS = 800L;        // plynne przejscie przy recznym on/off
    private static final long SUNSET_RAMP_MS = 120000L; // narastanie po zachodzie (~2 min)
    private static final long SUN_CHECK_MS = 60000L;  // sprawdzenie zachodu co minute

    private WindowManager wm;
    private FilterView overlay;
    private final Handler h = new Handler(Looper.getMainLooper());
    private android.animation.ValueAnimator anim;
    private float currentAlpha = 0f;   // aktualna widoczna sila filtru (0..1)
    private boolean sunsetMode = false;
    private boolean paused = false;    // pauza na czas okna instalatora APK

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

        // PAUZA: okno instalatora APK (potwierdzenie instalacji / Play Protect
        // "Zainstaluj bez skanowania") odcina dotyk, gdy cokolwiek rysuje sie
        // nad nim - nawet przezroczysta nakladka nie-lapiaca dotyku (ochrona
        // przed tapjackingiem). Na czas tego okna nakladka schodzi z ekranu
        // CALA (usuwamy okno), a wraca po powrocie do launchera (resume).
        if (intent != null && intent.getBooleanExtra("pause", false)) {
            paused = true;
            if (anim != null) anim.cancel();
            removeOverlayNow();
            h.removeCallbacksAndMessages(null);   // petla sloncka milczy podczas pauzy
            return START_NOT_STICKY;
        }
        if (intent != null && intent.getBooleanExtra("resume", false)) {
            boolean wasPaused = paused;
            paused = false;
            if ("off".equals(mode)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            showOverlay();
            boolean night = !sunsetMode || NightShift.isNightBySun(this);
            float target = night ? targetStrengthFraction() : 0f;
            // po powrocie z instalatora plynny powrot filtra; bez pauzy nic
            // nierestartujemy (np. rampa zachodu ma prawo trwac dalej)
            if (wasPaused || Math.abs(target - currentAlpha) > 0.001f) {
                animateTo(target, FADE_MS);
            }
            scheduleSunCheck();
            return START_NOT_STICKY;
        }

        paused = false;
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

    /**
     * Wartosc 5-80 z suwaka w Ustawieniach/Panelu -> glebia ciepla 0..1.
     * 5  -> 0.06  (subtelne cieplo)
     * 40 -> 0.50  (domyslne, punkt bieli ok. 4200 K)
     * 80 -> 1.00  (maksimum jak w iOS: punkt bieli 2700 K)
     */
    private float targetStrengthFraction() {
        int strength = Math.max(5, Math.min(80, Prefs.nightShiftStrength(this)));
        return strength / 80f;
    }

    private void applyFraction(float f) {
        currentAlpha = f;
        if (overlay != null) {
            overlay.setWarmthFraction(f);
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

    /**
     * Nagle zdejmuje nakladke z ekranu. Wydarza sie to tylko przy pauzie na
     * czas instalatora: USUWAMY okno z WindowManager, a nie tylko zerujemy
     * alphe - nawet w pelni przezroczyste okno nad instalatorem ustawia mu
     * flage "obscured" i ten odcina dotyk.
     */
    private void removeOverlayNow() {
        if (overlay != null) {
            try {
                if (wm != null) wm.removeView(overlay);
            } catch (Throwable ignored) {
            }
            overlay = null;
        }
        currentAlpha = 0f;
    }

    private void scheduleSunCheck() {
        h.removeCallbacksAndMessages(null);
        if (!sunsetMode) return;
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!sunsetMode || paused) return;
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
                overlay = new FilterView(this);
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

    /**
     * Nakladka jak iOS Night Shift: SAMA barwa, ZERO przyciemniania.
     * (Wersja z 3.11 rysowala dwa prostokaty - kolor z kelwinow i czarny dim -
     * przez co przy typowym suwaku ekran byl blady zamiast zolty.)
     *
     * Matematyka w ColorTemp.overlayTint(): nasycony kolor liczony tak, aby
     * BIALY trafial dokladnie w punkt bieli celu (biel -> 2700 K), a alpha
     * rosnie liniowo z glebia ciepla (przy 0 nakladka zanika do zera).
     * Alokacja tylko przy zmianie barwy (suwak/animacja), w onDraw zero.
     */
    private static final class FilterView extends View {

        private float warmth = 0f;
        private final Paint warmPaint = new Paint();

        FilterView(Context c) {
            super(c);
            warmPaint.setColor(0x00FFFFFF);
            setWillNotDraw(false);
        }

        void setWarmthFraction(float f) {
            f = Math.max(0f, Math.min(1f, f));
            if (Math.abs(f - warmth) < 0.0005f) return;
            warmth = f;

            int[] t = ColorTemp.overlayTint(f);
            warmPaint.setColor(Color.argb(t[0], t[1], t[2], t[3]));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (warmth <= 0f) return;
            c.drawRect(0f, 0f, getWidth(), getHeight(), warmPaint);
        }
    }
}
