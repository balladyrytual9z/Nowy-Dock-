package pl.dock.widget;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * Honda Premium Startup - elegancka animacja powitalna przy wlaczeniu ekranu
 * radia ESSGOO (po przekreceniu kluczyka). NIE jest to animacja bootowania
 * Androida ani startu silnika - dziala jako niezalezna animacja powitalna
 * nad launcherem, uruchamiana z ScreenOnReceiver (DockApp).
 *
 * Storyboard 2 s (1280x720, poziomo):
 *   0.00-0.35  blackout (czarne tlo, nic wiecej)
 *   0.35-0.85  reveal - logo plynnie sie pojawia
 *   0.85-1.40  light sweep - jeden chromowany refleks przechodzi przez logo
 *   1.40-1.75  hold - logo stabilizuje sie
 *   1.75-2.00  fade out - logo znika i okno plynnie odsłania launcher
 *
 * Dwa warianty kolorystyczne zwiazane z trybem dnia/nocy launchera (DarkMode):
 *   NIGHT - grafitowe tlo, srebrny metal (logo 1:1)
 *   DAY   - jasne tlo, sciemnione aluminium (logo przyciemniane ~2.4x)
 *
 * Logo wczytywane z zasobu drawable "honda_welcome_logo" (dokladnie obraz
 * dostarczony przez uzytkownika). Brak zasobu = brak animacji (ciche
 * zakonczenie), build nigdy nie zalezy od tej grafiki.
 *
 * Czego celowo NIE ma: obrotow, zoomu kamery, czasteczek, sci-fi, agresywnych
 * blyskow (ustalenia projektowe).
 */
public class HondaWelcomeActivity extends Activity {

    // Klatki kluczowe storyboardu (ms od startu).
    static final long T_BLACKOUT = 350L;
    static final long T_REVEAL   = 850L;
    static final long T_SWEEP    = 1400L;
    static final long T_HOLD     = 1750L;
    static final long T_END      = 2000L;

    // Logo zajmuje ~62% szerokosci ekranu (bylo: 25-35% dla samego napisu;
    // pelny lockup H+HONDA+CIVIC HATCHBACK jest szerszy, wiec 62% daje
    // napis HONDA w okolicy dawnych 30-35%).
    private static final float LOGO_WIDTH_FRAC = 0.62f;

    private WelcomeView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Wejscie bez animacji - ekran ma byc czarny od razu (blackout).
        overridePendingTransition(0, 0);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Bitmap src = loadLogoBitmap();
        if (src == null) {
            // Brak grafiki w repo - nie blokujemy launchera.
            finish();
            overridePendingTransition(0, 0);
            return;
        }

        boolean night = DarkMode.isNight(this);
        // DAY: aluminium/chlodny chrom na jasnym tle - przyciemniamy metal,
        // NIGHT: oryginalne srebrne chromy na graficie.
        Bitmap logo = keyed(src, night ? 1.0f : 0.42f);
        if (src != logo) src.recycle();

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

    private void finishWithFade() {
        finish();
        // Wyjscie plynnym zanikiem calego okna - pod spodem zostaje launcher.
        try {
            overridePendingTransition(0, android.R.anim.fade_out);
        } catch (Throwable ignored) { }
    }

    /** Wczytuje logo z drawable "honda_welcome_logo" (plik dodany do repo). */
    private Bitmap loadLogoBitmap() {
        try {
            int id = getResources().getIdentifier("honda_welcome_logo", "drawable", getPackageName());
            if (id == 0) return null;
            Bitmap b = android.graphics.BitmapFactory.decodeResource(getResources(), id);
            return (b == null || b.isRecycled() || b.getWidth() == 0) ? null : b;
        } catch (Throwable t) {
            LogDiagnostics.e("HondaWelcome", "loadLogoBitmap: " + t.getMessage(), t);
            return null;
        }
    }

    /**
     * Usuwa czarne tlo obrazu (kluczowanie po luminancji) i opcjonalnie
     * przyciemnia metal. Dzieki temu logo stoi na tle animacji, a nie
     * w czarnym prostokacie (wazne dla wariantu dziennego).
     */
    static Bitmap keyed(Bitmap src, float darken) {
        try {
            int w = src.getWidth(), h = src.getHeight();
            int[] px = new int[w * h];
            src.getPixels(px, 0, w, 0, 0, w, h);
            for (int i = 0; i < px.length; i++) {
                int p = px[i];
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                int lum = Math.max(r, Math.max(g, b));
                int a;
                if (lum <= 14) a = 0;
                else if (lum >= 235) a = 255;
                else a = (int) ((lum - 14) * 255f / (235 - 14));
                r = (int) (r * darken); g = (int) (g * darken); b = (int) (b * darken);
                px[i] = (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            LogDiagnostics.e("HondaWelcome", "keyed: " + t.getMessage(), t);
            return src;
        }
    }

    /** Widok rysujacy caly storyboard na Canvas - bez XML i bez AndroidX. */
    static class WelcomeView extends View {
        private final Bitmap logo;
        private final boolean night;
        private final Paint logoPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint();

        private long start = -1L;
        private boolean finished = false;
        private LinearGradient sweepShader;
        private float bandWidth;

        WelcomeView(android.content.Context c, Bitmap logo, boolean night) {
            super(c);
            this.logo = logo;
            this.night = night;
            // NIGHT: grafit (lekko jasniejszy niz czyste czarne tlo logo,
            // zeby nie bylo widocznej krawedzi obrazu); DAY: jasne, chlodne.
            bgPaint.setColor(night ? 0xFF0B0B0D : 0xFFF2F2F5);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            long now = SystemClock.uptimeMillis();
            if (start < 0) start = now;
            long t = now - start;
            final int w = getWidth(), h = getHeight();

            // 1) Tlo (blackout w nocy = prawie czarne; w dzien jasne).
            canvas.drawRect(0, 0, w, h, bgPaint);

            // Globalny fade-out calosci (1.75-2.00 s).
            float global = 1f;
            if (t > T_HOLD) {
                float f = (t - T_HOLD) / (float) (T_END - T_HOLD);
                global = 1f - accel(f);
            }

            // 2) Reveal (0.35-0.85 s): decelerate alpha + subtelne 0.985->1.0.
            float reveal = 0f;
            if (t >= T_BLACKOUT) {
                float f = (t - T_BLACKOUT) / (float) (T_REVEAL - T_BLACKOUT);
                reveal = decel(Math.min(1f, f));
            }

            if (reveal > 0f && global > 0f) {
                // Skalowanie: logo dopasowane do LOGO_WIDTH_FRAC szerokosci,
                // wyśrodkowane w poziomie i pionie.
                float scale = Math.min((w * LOGO_WIDTH_FRAC) / logo.getWidth(),
                        (h * 0.80f) / logo.getHeight());
                float dw = logo.getWidth() * scale, dh = logo.getHeight() * scale;
                float left = (w - dw) / 2f, top = (h - dh) / 2f;

                // Subtelne "osadzenie" 0.985 -> 1.0 (nie zoom kamery).
                float settle = 0.985f + 0.015f * reveal;
                canvas.save();
                canvas.scale(settle, settle, w / 2f, h / 2f);

                logoPaint.setAlpha((int) (255 * reveal * global));
                android.graphics.RectF dst = new android.graphics.RectF(left, top, left + dw, top + dh);
                canvas.drawBitmap(logo, null, dst, logoPaint);

                // 3) Light sweep (0.85-1.40 s): jeden refleks, bez przesadnego
                //    blysku - delikatny pas swiatla klipowany do pikseli logo.
                if (t >= T_REVEAL && t < T_SWEEP + 120L) {
                    float p = (t - T_REVEAL) / (float) (T_SWEEP - T_REVEAL);
                    if (p > 0f && p < 1f) {
                        if (sweepShader == null) {
                            bandWidth = Math.max(dw, 1f) * 0.45f;
                            sweepShader = new LinearGradient(0, 0, bandWidth, bandWidth * 0.25f,
                                    new int[]{0x00000000, 0x59FFFFFF, 0x00000000},
                                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                        }
                        float pad = bandWidth;
                        Paint sweep = new Paint(Paint.ANTI_ALIAS_FLAG);
                        sweep.setShader(sweepShader);
                        sweep.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                        sweep.setAlpha((int) (255 * 0.9f * global));
                        int sc = canvas.saveLayer(left - pad, top - pad, left + dw + pad, top + dh + pad, null);
                        canvas.drawBitmap(logo, null, dst, logoPaint);
                        Matrix m = new Matrix();
                        float tx = left - bandWidth + p * (dw + 2f * bandWidth);
                        m.setTranslate(tx, 0f);
                        sweepShader.setLocalMatrix(m);
                        canvas.drawRect(left - pad, top - pad, left + dw + pad, top + dh + pad, sweep);
                        canvas.restoreToCount(sc);
                    }
                }
                canvas.restore();
            }

            // 4) Koniec - plynnie odsłaniamy launcher (fade out okna).
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
