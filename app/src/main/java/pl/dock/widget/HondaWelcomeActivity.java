package pl.dock.widget;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * Honda Premium Startup - elegancka animacja powitalna przy wlaczeniu ekranu
 * radia ESSGOO (po przekreceniu kluczyka). NIE jest to animacja bootowania
 * Androida ani startu silnika - dziala jako niezalezna animacja powitalna,
 * uruchamiana z ScreenOnReceiver (rejestracja w DockApp), i plynnie
 * odsłania launcher.
 *
 * Storyboard 2 s (1280x720, poziomo):
 *   0.00-0.35  blackout (tlo, bez elementow UI)
 *   0.35-0.85  reveal - logo plynnie sie pojawia (decelerate)
 *   0.85-1.40  light sweep - jeden chromowany refleks przechodzi przez logo
 *   1.40-1.75  hold - logo stabilizuje sie
 *   1.75-2.00  fade out - logo znika, okno zanika i odsłania launcher
 *
 * Dwa warianty kolorystyczne zwiazane z trybem dnia/nocy launchera (DarkMode):
 *   NIGHT - grafitowe tlo, srebrny metal (logo 1:1)
 *   DAY   - jasne tlo, przyciemnione aluminium (tint na wektorze)
 *
 * Logo: zasob wektorowy drawable/honda_welcome_logo.xml (pelny lockup
 * H + HONDA + CIVIC HATCHBACK, dokladnie wg grafiki uzytkownika).
 * Brak zasobu = ciche zakonczenie (build nie zalezy od grafiki).
 *
 * Czego celowo NIE ma: obrotow, zoomu kamery, czasteczek, sci-fi,
 * agresywnych blyskow (ustalenia projektowe).
 */
public class HondaWelcomeActivity extends Activity {

    // Klatki kluczowe storyboardu (ms od startu).
    static final long T_BLACKOUT = 350L;
    static final long T_REVEAL   = 850L;
    static final long T_SWEEP    = 1400L;
    static final long T_HOLD     = 1750L;
    static final long T_END      = 2000L;

    // Logo zajmuje ~62% szerokosci ekranu (pelny lockup jest szeroki;
    // napis HONDA wypada wtedy ok. 30-35% szerokosci - jak w ustaleniach).
    private static final float LOGO_WIDTH_FRAC = 0.62f;

    // DAY: przyciemniony metal (aluminium/chlodny chrom na jasnym tle).
    private static final int DAY_TINT = 0xFF6E6E73;

    private WelcomeView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Wejscie bez animacji - ekran ma byc "czarny" od razu (blackout).
        overridePendingTransition(0, 0);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Drawable logo = loadLogoDrawable();
        if (logo == null) {
            // Brak grafiki w repo - nie blokujemy launchera.
            finish();
            overridePendingTransition(0, 0);
            return;
        }

        boolean night = DarkMode.isNight(this);
        view = new WelcomeView(this, logo, night);
        setContentView(view, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        // Dotkniecie ekranu = natychmiastowe pominiecie animacji.
        view.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finishWithFade(); }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } catch (Throwable ignored) { }
    }

    @Override
    public void onBackPressed() {
        finishWithFade();
    }

    /** Zakonczenie z plynnym zanikiem okna - pod spodem zostaje launcher. */
    void finishWithFade() {
        finish();
        try {
            overridePendingTransition(0, android.R.anim.fade_out);
        } catch (Throwable ignored) { }
    }

    /** Wczytuje logo z drawable "honda_welcome_logo" (wektor w repo). */
    private Drawable loadLogoDrawable() {
        try {
            int id = getResources().getIdentifier("honda_welcome_logo", "drawable", getPackageName());
            if (id == 0) return null;
            return getDrawable(id);
        } catch (Throwable t) {
            LogDiagnostics.e("HondaWelcome", "loadLogoDrawable: " + t.getMessage(), t);
            return null;
        }
    }

    /** Widok rysujacy caly storyboard na Canvas - bez XML i bez AndroidX. */
    static class WelcomeView extends View {
        private final Drawable logo;
        private final boolean night;
        private final Paint bgPaint = new Paint();
        private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private long start = -1L;
        private boolean finished = false;
        private LinearGradient sweepShader;
        private float bandWidth;

        WelcomeView(android.content.Context c, Drawable logo, boolean night) {
            super(c);
            this.logo = logo;
            this.night = night;
            // NIGHT: grafit (odrobine jasniejszy niz czern, elegancko);
            // DAY: jasne, chlodne tlo.
            bgPaint.setColor(night ? 0xFF0B0B0D : 0xFFF2F2F5);
            if (!night) {
                // Aluminium zamiast srebra na jasnym tle.
                logo.setColorFilter(new PorterDuffColorFilter(DAY_TINT, PorterDuff.Mode.SRC_ATOP));
            }
            sweepPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long now = SystemClock.uptimeMillis();
            if (start < 0) start = now;
            long t = now - start;
            final int w = getWidth(), h = getHeight();

            // 1) Tlo.
            canvas.drawRect(0, 0, w, h, bgPaint);

            // Globalny fade-out calosci (1.75-2.00 s).
            float global = 1f;
            if (t > T_HOLD) {
                float f = (t - T_HOLD) / (float) (T_END - T_HOLD);
                global = 1f - accel(f);
            }

            // 2) Reveal (0.35-0.85 s).
            float reveal = 0f;
            if (t >= T_BLACKOUT) {
                float f = (t - T_BLACKOUT) / (float) (T_REVEAL - T_BLACKOUT);
                reveal = decel(Math.min(1f, f));
            }

            if (reveal > 0f && global > 0f) {
                // Proporcje z intrinsicRatio wektora (2528x1696).
                float ratio = logo.getIntrinsicWidth() > 0 && logo.getIntrinsicHeight() > 0
                        ? logo.getIntrinsicWidth() / (float) logo.getIntrinsicHeight()
                        : 2528f / 1696f;
                float dw = w * LOGO_WIDTH_FRAC;
                float dh = dw / ratio;
                if (dh > h * 0.80f) {          // bezpiecznik na inne ekranie
                    dh = h * 0.80f;
                    dw = dh * ratio;
                }
                float left = (w - dw) / 2f, top = (h - dh) / 2f;

                // Subtelne "osadzenie" 0.985 -> 1.0 (nie zoom kamery).
                float settle = 0.985f + 0.015f * reveal;
                canvas.save();
                canvas.scale(settle, settle, w / 2f, h / 2f);

                RectF bounds = new RectF(left, top, left + dw, top + dh);
                logo.setBounds((int) left, (int) top, (int) (left + dw), (int) (top + dh));
                logo.setAlpha((int) (255 * reveal * global));
                logo.draw(canvas);

                // 3) Light sweep (0.85-1.40 s): jeden delikatny refleks,
                //    klipowany do pikseli logo (SRC_ATOP), bez agresywnego
                //    blysku.
                if (t >= T_REVEAL && t < T_SWEEP + 120L) {
                    float p = (t - T_REVEAL) / (float) (T_SWEEP - T_REVEAL);
                    if (p > 0f && p < 1f) {
                        if (sweepShader == null) {
                            bandWidth = Math.max(dw, 1f) * 0.45f;
                            sweepShader = new LinearGradient(0, 0, bandWidth, bandWidth * 0.25f,
                                    new int[]{0x00000000, 0x59FFFFFF, 0x00000000},
                                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                            sweepPaint.setShader(sweepShader);
                            sweepPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                        }
                        int sc = canvas.saveLayer(bounds.left, bounds.top,
                                bounds.right, bounds.bottom, null);
                        logo.draw(canvas);
                        Matrix m = new Matrix();
                        float tx = bounds.left - bandWidth + p * (dw + 2f * bandWidth);
                        m.setTranslate(tx, 0f);
                        sweepShader.setLocalMatrix(m);
                        sweepPaint.setAlpha((int) (255 * 0.9f * global));
                        canvas.drawRect(bounds, sweepPaint);
                        canvas.restoreToCount(sc);
                    }
                }
                canvas.restore();
            }

            // 4) Koniec - plynnie odsłaniamy launcher.
            if (t >= T_END) {
                if (!finished) {
                    finished = true;
                    setOnClickListener(null);
                    post(new Runnable() {
                        @Override public void run() {
                            if (getContext() instanceof HondaWelcomeActivity) {
                                ((HondaWelcomeActivity) getContext()).finishWithFade();
                            }
                        }
                    });
                }
                return;
            }
            postInvalidateOnAnimation();
        }

        private static float decel(float x) {
            return 1f - (1f - x) * (1f - x);
        }

        private static float accel(float x) {
            return x * x;
        }
    }
}
