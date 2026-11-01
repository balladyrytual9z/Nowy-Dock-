
package pl.dock.widget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import pl.dock.widget.ui.DockColors;
import pl.dock.widget.ui.DockTheme;

/**
 * Media Center – full-screen Now Playing for Dock 2.0.
 * Uses the same MediaHub as the home widget (single source of truth).
 * Installed in the same APK for now; architecture allows split to pl.dock.media later.
 */
public class MediaCenterActivity extends Activity implements MediaHub.Listener {

    private MediaHub.Track track = new MediaHub.Track();
    private MediaView view;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (view != null) view.invalidate();
            if (track.playing()) h.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new MediaView(this);
        setContentView(view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MediaHub.get().attach(this, this);
        h.post(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MediaHub.get().detach(this);
        h.removeCallbacks(tick);
    }

    @Override
    public void onTrack(MediaHub.Track t) {
        track = t == null ? new MediaHub.Track() : t;
        if (view != null) view.invalidate();
        h.removeCallbacks(tick);
        if (track.playing()) h.post(tick);
    }

    private class MediaView extends View {
        private final DockTheme theme;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int pressed = -1; // 0 prev, 1 play, 2 next, 3 back

        MediaView(MediaCenterActivity a) {
            super(a);
            theme = new DockTheme(a, true);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int hgt = getHeight();
            boolean night = true;
            c.drawColor(DockColors.base(night));

            float artSize = Math.min(w, hgt) * 0.42f;
            float artX = (w - artSize) / 2f;
            float artY = hgt * 0.12f;

            // artwork
            if (track.art != null && !track.art.isRecycled()) {
                r.set(artX, artY, artX + artSize, artY + artSize);
                paint.setStyle(Paint.Style.FILL);
                c.drawRoundRect(r, theme.radiusLg(), theme.radiusLg(), paint);
                // simple clip via round rect mask would need more work; draw scaled
                float scale = Math.min(artSize / track.art.getWidth(), artSize / track.art.getHeight());
                float dw = track.art.getWidth() * scale;
                float dh = track.art.getHeight() * scale;
                float dx = artX + (artSize - dw) / 2f;
                float dy = artY + (artSize - dh) / 2f;
                c.drawBitmap(track.art, null, new RectF(dx, dy, dx + dw, dy + dh), null);
            } else {
                paint.setColor(DockColors.raised(night));
                r.set(artX, artY, artX + artSize, artY + artSize);
                c.drawRoundRect(r, theme.radiusLg(), theme.radiusLg(), paint);
                text.setColor(DockColors.textSecondary(night));
                text.setTextSize(theme.sp(18));
                text.setTextAlign(Paint.Align.CENTER);
                c.drawText("♪", artX + artSize / 2f, artY + artSize / 2f + theme.sp(6), text);
            }

            float ty = artY + artSize + theme.dp(28);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(DockColors.text(night));
            text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            text.setTextSize(theme.sp(26));
            String title = track.title.length() > 0 ? track.title : "Brak utworu";
            c.drawText(TextUtils.ellipsize(title, text, w * 0.85f, TextUtils.TruncateAt.END).toString(),
                    w / 2f, ty, text);

            ty += theme.dp(28);
            text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            text.setTextSize(theme.sp(16));
            text.setColor(DockColors.textSecondary(night));
            String sub = track.artist;
            if (track.album.length() > 0) {
                if (sub.length() > 0) sub += " · ";
                sub += track.album;
            }
            if (sub.length() == 0) sub = track.pkg.length() > 0 ? track.pkg : "—";
            c.drawText(TextUtils.ellipsize(sub, text, w * 0.85f, TextUtils.TruncateAt.END).toString(),
                    w / 2f, ty, text);

            // progress
            float barY = hgt * 0.72f;
            float barL = w * 0.12f;
            float barR = w * 0.88f;
            float barH = theme.dp(4);
            paint.setColor(DockColors.raised(night));
            r.set(barL, barY, barR, barY + barH);
            c.drawRoundRect(r, barH / 2, barH / 2, paint);
            float frac = 0f;
            if (track.duration > 0) {
                frac = Math.min(1f, Math.max(0f, track.position() / (float) track.duration));
            }
            paint.setColor(DockColors.accent(night));
            r.set(barL, barY, barL + (barR - barL) * frac, barY + barH);
            c.drawRoundRect(r, barH / 2, barH / 2, paint);

            text.setTextSize(theme.sp(12));
            text.setColor(DockColors.textSecondary(night));
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText(fmt(track.position()), barL, barY - theme.dp(8), text);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(track.duration > 0 ? fmt(track.duration) : "--:--", barR, barY - theme.dp(8), text);

            // controls
            float cy = hgt * 0.84f;
            float btn = theme.dp(56);
            float gap = theme.dp(72);
            drawCtrl(c, w / 2f - gap, cy, btn, 0, "◀◀", night);
            drawCtrl(c, w / 2f, cy, btn * 1.15f, 1, track.playing() ? "❚❚" : "▶", night);
            drawCtrl(c, w / 2f + gap, cy, btn, 2, "▶▶", night);

            // back
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(theme.sp(14));
            text.setColor(DockColors.accent(night));
            c.drawText("← Dock", theme.dp(20), theme.dp(36), text);

            if (track.source.length() > 0) {
                text.setTextAlign(Paint.Align.RIGHT);
                text.setColor(DockColors.textSecondary(night));
                c.drawText(track.source, w - theme.dp(20), theme.dp(36), text);
            }
        }

        private void drawCtrl(Canvas c, float cx, float cy, float size, int id, String glyph, boolean night) {
            // efekt CarPlay: dotkniety przycisk rozswietla sie pod spodem
            if (pressed == id) {
                float rad = size * 0.95f;
                Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
                gp.setShader(new RadialGradient(cx, cy, rad, 0x660A84FF, 0x000A84FF,
                        Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, rad, gp);
            }
            paint.setColor(pressed == id ? DockColors.accent(night) : DockColors.raised(night));
            c.drawCircle(cx, cy, size / 2f, paint);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(theme.sp(id == 1 ? 22 : 16));
            text.setColor(DockColors.text(night));
            c.drawText(glyph, cx, cy + theme.sp(id == 1 ? 8 : 6), text);
        }

        private String fmt(long ms) {
            if (ms < 0) return "0:00";
            long s = ms / 1000;
            return (s / 60) + ":" + String.format("%02d", s % 60);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX();
            float y = e.getY();
            float w = getWidth();
            float hgt = getHeight();
            float cy = hgt * 0.84f;
            float gap = theme.dp(72);
            float btn = theme.dp(56);

            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                if (y < theme.dp(50) && x < theme.dp(100)) {
                    pressed = 3;
                    invalidate();
                    return true;
                }
                if (dist(x, y, w / 2f - gap, cy) < btn) { pressed = 0; invalidate(); return true; }
                if (dist(x, y, w / 2f, cy) < btn * 1.2f) { pressed = 1; invalidate(); return true; }
                if (dist(x, y, w / 2f + gap, cy) < btn) { pressed = 2; invalidate(); return true; }
                // seek on bar
                float barY = hgt * 0.72f;
                if (y > barY - theme.dp(20) && y < barY + theme.dp(20) && track.duration > 0) {
                    float barL = w * 0.12f;
                    float barR = w * 0.88f;
                    float frac = Math.max(0, Math.min(1, (x - barL) / (barR - barL)));
                    MediaHub.get().seekTo((long) (frac * track.duration));
                    return true;
                }
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                int p = pressed;
                pressed = -1;
                invalidate();
                if (p == 3) {
                    finish();
                    return true;
                }
                if (p == 0) MediaHub.get().prev();
                else if (p == 1) MediaHub.get().toggle();
                else if (p == 2) MediaHub.get().next();
                return true;
            }
            return super.onTouchEvent(e);
        }

        private float dist(float x, float y, float cx, float cy) {
            float dx = x - cx, dy = y - cy;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }

    public static void open(android.content.Context c) {
        Intent i = new Intent(c, MediaCenterActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        c.startActivity(i);
    }
}






