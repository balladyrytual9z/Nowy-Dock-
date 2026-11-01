
package pl.dock.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.RadialGradient;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

// Widżet "Odtwarzane" jak w CarPlay: okładka, tytuł, wykonawca, pasek postępu i sterowanie.
// Dotknięcie okładki lub tytułu otwiera pełny ekran odtwarzania; przytrzymanie ◀◀ / ▶▶ przewija.
public class NowPlayingCard extends View implements MediaHub.Listener, NightAware {

    private final float d;
    private MediaHub.Track t = new MediaHub.Track();
    private boolean night = true;
    private boolean cardBg = true;
    private boolean ring = false;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (t.playing() && isAttachedToWindow()) h.postDelayed(this, 1000);
        }
    };
    private int pressed = -1;   // 0 prev, 1 play, 2 next
    // efekt CarPlay: rozswietlenie przycisku przy dotknieciu (glow 0..1)
    private int glowBtn = -1;
    private float glow;
    private android.animation.ObjectAnimator glowAnim;

    public float getGlow() {
        return glow;
    }

    public void setGlow(float v) {
        glow = v;
        if (glow <= 0.01f) glowBtn = -1;
        invalidate();
    }

    private void glowTo(int btn, float target, long ms) {
        glowBtn = btn;
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = android.animation.ObjectAnimator.ofFloat(this, "glow", glow, target);
        glowAnim.setDuration(ms);
        glowAnim.start();
    }
    private boolean scanning;
    private long scanStart;
    private final Runnable scan = new Runnable() {
        @Override
        public void run() {
            if (pressed != 0 && pressed != 2) return;
            scanning = true;
            long el = System.currentTimeMillis() - scanStart;
            long step = 4000 + Math.min(14000, el / 2);
            MediaHub.get().seekBy(pressed == 2 ? step : -step);
            h.postDelayed(this, 260);
        }
    };

    public NowPlayingCard(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setContentDescription("Odtwarzane");
    }

    // karta w kolorze docka; ring = ramka jaśniejsza od lewego górnego rogu (ekran główny)
    public void setFrame(boolean card, boolean ring) {
        cardBg = card;
        this.ring = ring;
        invalidate();
    }

    @Override
    public void setNight(boolean n) {
        if (n == night) return;
        night = n;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MediaHub.get().attach(getContext(), this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        h.removeCallbacks(tick);
        h.removeCallbacks(scan);
        MediaHub.get().detach(this);
    }

    @Override
    public void onTrack(MediaHub.Track track) {
        t = track;
        h.removeCallbacks(tick);
        if (t.playing()) h.postDelayed(tick, 1000);
        invalidate();
    }

    private float scaledSp(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth();
        float hgt = getHeight();
        float pad = 16f * d;
        int fg = night ? 0xFFFFFFFF : 0xFF111114;
        int sub = night ? 0xFF9A9AA0 : 0xFF6C6C70;
        boolean access = MediaHub.accessGranted(getContext());
        boolean data = t.hasData() && access;
        if (cardBg) {
            r.set(0, 0, w, hgt);
            Ui.ringCard(cv, r, 30f * d, 1.5f * d, night, ring);
        }

        float y = pad;
        text.setTextAlign(Paint.Align.CENTER);
        if (!data) {
            // stan wylaczony jak w CarPlay: ikona aplikacji i napis wysrodkowane w pionie,
            // przyciski sterowania ukryte (sesja martwa -> brak asymetrycznej pustki po prawej)
            float s = Math.min(w * 0.42f, hgt * 0.38f);
            float block = s + 34f * d;
            float top = (hgt - block) / 2f;
            r.set(w / 2f - s / 2f, top, w / 2f + s / 2f, top + s);
            PlayGlyphs.artPlaceholder(cv, r, s * 0.22f, true);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(scaledSp(access ? 20 : 17));
            text.setColor(fg);
            String msg = access ? "Odtwarzanie wyłączone" : "Włącz dostęp do powiadomień";
            cv.drawText(TextUtils.ellipsize(msg, text, w - 2 * pad, TextUtils.TruncateAt.END).toString(),
                    w / 2f, top + block, text);
            return;
        }

        float artSide = Math.min(w - 2 * pad, hgt - 196f * d);
        if (artSide >= 56f * d) {
            float ax = w / 2f - artSide / 2f;
            r.set(ax, y, ax + artSide, y + artSide);
            if (t.art != null) {
                Bitmap b = t.art;
                BitmapShader sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                Matrix m = new Matrix();
                float sc = Math.max(artSide / b.getWidth(), artSide / b.getHeight());
                m.setScale(sc, sc);
                m.postTranslate(ax - (b.getWidth() * sc - artSide) / 2f,
                        y - (b.getHeight() * sc - artSide) / 2f);
                sh.setLocalMatrix(m);
                paint.setShader(sh);
                cv.drawRoundRect(r, 14f * d, 14f * d, paint);
                paint.setShader(null);
            } else {
                PlayGlyphs.artPlaceholder(cv, r, 14f * d, night);
            }
            y += artSide + 14f * d;
        }
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(scaledSp(20));
        text.setColor(fg);
        String title = t.title.length() > 0 ? t.title : "Odtwarzane";
        cv.drawText(TextUtils.ellipsize(title, text, w - 2 * pad, TextUtils.TruncateAt.END).toString(),
                w / 2f, y + 22f * d, text);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(scaledSp(16));
        text.setColor(sub);
        cv.drawText(TextUtils.ellipsize(t.artist, text, w - 2 * pad, TextUtils.TruncateAt.END).toString(),
                w / 2f, y + 46f * d, text);

        float py = hgt - 76f * d;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(night ? 0x33FFFFFF : 0x22000000);
        r.set(pad, py, w - pad, py + 5f * d);
        cv.drawRoundRect(r, 3f * d, 3f * d, paint);
        if (t.duration > 0) {
            float f = Math.max(0f, Math.min(1f, t.position() / (float) t.duration));
            paint.setColor(night ? 0xCCFFFFFF : 0xCC111114);
            r.set(pad, py, pad + (w - 2 * pad) * f, py + 5f * d);
            cv.drawRoundRect(r, 3f * d, 3f * d, paint);
        }
        drawControls(cv, w, hgt, fg, true);
    }

    // rysuje tylko działające przyciski (gdy odtwarzacz je udostępnia)
    private void drawControls(Canvas cv, float w, float hgt, int fg, boolean on) {
        MediaHub hub = MediaHub.get();
        float cy = hgt - 34f * d;
        float step = w / 4f;
        float[] xs = {w / 2f - step, w / 2f, w / 2f + step};
        if (!on) {
            paint.setColor(fg);
            paint.setAlpha(70);
            PlayGlyphs.skip(cv, w / 2f - step, cy, 30f * d, false, paint);
            PlayGlyphs.skip(cv, w / 2f + step, cy, 30f * d, true, paint);
            PlayGlyphs.play(cv, w / 2f, cy, 34f * d, paint);
            paint.setAlpha(255);
            return;
        }
        // efekt CarPlay: dotkniety przycisk rozswietla sie pod spodem
        if (glow > 0.01f && glowBtn >= 0 && glowBtn <= 2) {
            float gx = xs[glowBtn];
            float rad = 40f * d;
            int col = night ? 0x99FFFFFF : 0x990A84FF;
            int a = Math.round((col >>> 24) * glow);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(gx, cy, rad,
                    (a << 24) | (col & 0x00FFFFFF), col & 0x00FFFFFF, Shader.TileMode.CLAMP));
            cv.drawCircle(gx, cy, rad, paint);
            paint.setShader(null);
        }
        if (hub.canPrev()) {
            paint.setColor(fg);
            paint.setAlpha(255);
            PlayGlyphs.skip(cv, w / 2f - step, cy, 30f * d, false, paint);
        }
        if (hub.canNext()) {
            paint.setColor(fg);
            paint.setAlpha(255);
            PlayGlyphs.skip(cv, w / 2f + step, cy, 30f * d, true, paint);
        }
        if (hub.canToggle()) {
            paint.setColor(fg);
            paint.setAlpha(255);
            if (t.playing()) {
                PlayGlyphs.pause(cv, w / 2f, cy, 34f * d, paint);
            } else {
                PlayGlyphs.play(cv, w / 2f, cy, 34f * d, paint);
            }
        }
        paint.setAlpha(255);
    }

    private int region(float x, float y) {
        float hgt = getHeight();
        float w = getWidth();
        if (y > hgt - 66f * d) {
            if (x < w * 0.38f) return 0;
            if (x > w * 0.62f) return 2;
            return 1;
        }
        return 3;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = region(e.getX(), e.getY());
                scanning = false;
                if (pressed == 0 || pressed == 2) {
                    scanStart = System.currentTimeMillis();
                    h.postDelayed(scan, ViewConfiguration.getLongPressTimeout());
                }
                if (pressed >= 0 && pressed <= 2) glowTo(pressed, 1f, 110);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(scan);
                int p = pressed;
                boolean wasScan = scanning;
                pressed = -1;
                scanning = false;
                if (glow > 0.01f && glowBtn >= 0) glowTo(glowBtn, 0f, 320);
                invalidate();
                if (!wasScan && p >= 0 && p == region(e.getX(), e.getY())) act(p);
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(scan);
                pressed = -1;
                scanning = false;
                if (glow > 0.01f && glowBtn >= 0) glowTo(glowBtn, 0f, 320);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void act(int p) {
        Context c = getContext();
        if (!MediaHub.accessGranted(c)) {
            openAccessSettings(c);
            return;
        }
        if (p == 0) {
            MediaHub.get().prev();
        } else if (p == 1) {
            MediaHub.get().toggle();
        } else if (p == 2) {
            MediaHub.get().next();
        } else if (t.hasData()) {
            Intent i = new Intent(c, MediaCenterActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        }
    }

    public static void openAccessSettings(Context c) {
        try {
            Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable t) {
            try {
                Intent i = new Intent(Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Throwable ignored) {
            }
        }
    }
}







