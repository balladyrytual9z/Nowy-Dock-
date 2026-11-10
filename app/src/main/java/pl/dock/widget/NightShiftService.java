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
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

// Nakladka Night Shift. Kolor narasta plynnie:
// - reczne wlaczenie / wylaczenie (klik w przycisk): krotkie przejscie ok. 800 ms,
// - AUTO (harmonogram slonca, jak w iOS): PO 30 S - filtr narasta/wygasa przez 30 s,
//   a w tym samym czasie podswietlenie PLYNNIE spada do najnizszej wartosci
//   (auto-wlaczenie) albo wraca do zapamietanej (auto-wylaczenie),
// - suwak ciepla w panelu lacznosci zmienia kolor na zywo.
//
// Na czas systemowego okna instalacji APK nakladka schodzi z ekranu ("pause")
// i wraca po powrocie do launchera ("resume"): okno instalatora i Play Protect
// odcinaja dotyk, gdy cokolwiek rysuje sie nad nimi (ochrona przed tapjackingiem).
public class NightShiftService extends Service {

    private static final long FADE_MS = 800L;         // plynne przejscie przy recznym on/off
    private static final long RAMP_MS = 30000L;       // AUTO: 30 s (filtr + jasnost, jak w iOS)
    private static final long SUN_CHECK_MS = 60000L;  // sprawdzenie slonca co minute
    private static final int BRIGHT_MIN = 10;         // najnizsza wartosc podswietlenia

    private WindowManager wm;
    private FilterView overlay;
    private final Handler h = new Handler(Looper.getMainLooper());
    private android.animation.ValueAnimator anim;
    private android.animation.ValueAnimator bAnim;    // rampa jasnosci (auto 30 s)
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
        boolean manual = intent != null && intent.getBooleanExtra("manual", false);
        String mode = Prefs.nightShiftMode(this);
        // nowy model (nsUsed): baza = harmonogram slonca, nawet gdy pref mowi "off"
        sunsetMode = "sunset".equals(mode) || NightShift.nsUsed(this);

        // PAUZA: okno instalatora APK (potwierdzenie instalacji / Play Protect
        // "Zainstaluj bez skanowania") odcina dotyk, gdy cokolwiek rysuje sie
        // nad nim - nawet przezroczysta nakladka nie-lapiaca dotyku (ochrona
        // przed tapjackingiem). Na czas tego okna nakladka schodzi z ekranu
        // CALA (usuwamy okno), a wraca po powrocie do launchera (resume).
        if (intent != null && intent.getBooleanExtra("pause", false)) {
            paused = true;
            if (anim != null) anim.cancel();
            if (bAnim != null) bAnim.cancel();
            removeOverlayNow();
            h.removeCallbacksAndMessages(null);   // petla sloncka milczy podczas pauzy
            return START_NOT_STICKY;
        }
        if (intent != null && intent.getBooleanExtra("resume", false)) {
            boolean wasPaused = paused;
            paused = false;
            if ("off".equals(mode) && !NightShift.nsUsed(this)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            showOverlay();
            boolean night = currentNight(mode);
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
        if ("off".equals(mode) && !NightShift.nsUsed(this)) {
            hideOverlayAnimated();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (refresh) {
            // zmiana ciepla suwakiem: plytka korekta do nowej sily
            showOverlay();
            animateTo(currentNight(mode) ? targetStrengthFraction() : 0f, 250L);
            scheduleSunCheck();
            return START_NOT_STICKY;
        }
        showOverlay();
        boolean night = currentNight(mode);
        // reczny klik = krotkie przejscie; auto = 30 s (bez rampy jasnosci przy
        // starcie uslugi - ta odpala sie tylko z petli slonckiej, na realnym
        // przejsciu dzien/noc)
        animateTo(night ? targetStrengthFraction() : 0f, manual ? FADE_MS : RAMP_MS);
        if (manual && bAnim != null) bAnim.cancel();
        scheduleSunCheck();
        return START_NOT_STICKY;
    }

    /** Czy filtr ma teraz dzialac (nowy model: reczna zmiana -> potem slonce). */
    private boolean currentNight(String mode) {
        if (NightShift.nsUsed(this)) return NightShift.effectiveOn(this);
        if ("on".equals(mode)) return true;
        if ("sunset".equals(mode)) return NightShift.isNightBySun(this);
        return false;
    }

    /**
     * Wartosc 0-50 z suwaka ciepla (max = POLOWA obecnego maksimum - decyzja
     * uzytkownika) -> glebia ciepla 0..1:
     *   0  -> 0.00  (brak filtra)
     *  40  -> 0.40  (domyslne)
     *  50  -> 0.50  (nowe maksimum = polowa dawnego 80 -> 1.00)
     * Stare zapisy > 50 sa przycinane do 50.
     */
    private float targetStrengthFraction() {
        int strength = Math.max(0, Math.min(50, Prefs.nightShiftStrength(this)));
        return strength / 100f;
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
     * AUTO-przejscie (z petli slonckiej): w tle plynnie prowadzimy tez
     * podswietlenie - przy wlaczaniu spada do najnizszej wartosci (30 s),
     * przy wylaczaniu wraca do zapamietanej (30 s). Wymaga zgody na zmiane
     * ustawien systemowych; bez niej tylko filtr sie animuje.
     */
    private void startBrightnessRamp(boolean down) {
        if (bAnim != null) bAnim.cancel();
        try {
            if (!Settings.System.canWrite(this)) return;
            int from = currentBrightness();
            int to;
            if (down) {
                if (!NightShift.nsBrightSaved(this)) {
                    NightShift.setNsBrightPrev(this, from);
                    NightShift.setNsBrightSaved(this, true);
                }
                to = BRIGHT_MIN;
            } else {
                to = NightShift.nsBrightPrev(this);
                if (to <= BRIGHT_MIN) to = Math.max(from, 100);
                NightShift.setNsBrightSaved(this, false);
            }
            if (to == from) return;
            bAnim = android.animation.ValueAnimator.ofInt(from, to);
            bAnim.setDuration(RAMP_MS);
            bAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                private int last = -1;

                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    int v = (Integer) a.getAnimatedValue();
                    if (v == last) return;
                    last = v;
                    try {
                        Settings.System.putInt(getContentResolver(),
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                        Settings.System.putInt(getContentResolver(),
                                Settings.System.SCREEN_BRIGHTNESS, v);
                    } catch (Throwable ignored) {
                    }
                }
            });
            bAnim.start();
        } catch (Throwable ignored) {
        }
    }

    private int currentBrightness() {
        try {
            return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Throwable t) {
            return 100;
        }
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
                boolean night = currentNight(Prefs.nightShiftMode(NightShiftService.this));
                float target = night ? targetStrengthFraction() : 0f;
                if (Math.abs(target - currentAlpha) > 0.001f) {
                    // AUTO: filtr 30 s + podswietlenie plynnie do minimum / powrot
                    animateTo(target, RAMP_MS);
                    startBrightnessRamp(night);
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
        if (bAnim != null) bAnim.cancel();
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
