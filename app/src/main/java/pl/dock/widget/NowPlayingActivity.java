
package pl.dock.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

// Pełnoekranowe "Odtwarzane" w obszarze obok docka (dock zostaje widoczny i działa).
// Pokazuje wyłącznie przyciski, które odtwarzacz faktycznie obsługuje.
public class NowPlayingActivity extends BaseActivity implements MediaHub.Listener {

    private PlayerView pv;
    private FrameLayout root;
    private boolean night;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (pv != null) pv.invalidate();
            if (pv != null && pv.t.playing()) h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        night = Look.night(this);
        setTheme(android.R.style.Theme_Translucent_NoTitleBar);
        super.onCreate(b);
        // NAPRAWA (ESSGOO): pelne okno z dokiem rysowanym wewnatrz (wczesniej
        // Ui.dockWindow zawezalo okno o 77 dp - pasek po boku pokazywal tlo
        // systemu). Pasek statusu normalnie wysuwalny (bez immersive).
        try {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } catch (Throwable ignored) {
        }
        root = new FrameLayout(this);
        root.setBackground(Ui.pageBg(this, night));
        pv = new PlayerView(this);
        FrameLayout.LayoutParams pvLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int dockPx = Ui.dp(this, Ui.DOCK_OFFSET_DP);
        if (Ui.dockLeft(this)) pvLp.leftMargin = dockPx;
        else pvLp.rightMargin = dockPx;
        root.addView(pv, pvLp);
        root.addView(buildDock(), new FrameLayout.LayoutParams(dockPx,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dockLeft(this) ? Gravity.START : Gravity.END));
        setContentView(root);
    }

    private DockView dock;

    private DockView buildDock() {
        dock = new DockView(this);
        dock.setListener(new DockView.Listener() {
            @Override public void onHeader() {
                // panel lacznosci jako osobne okno (overlay dziala tylko w launcherze)
                startActivity(new Intent(NowPlayingActivity.this, ConnectivityActivity.class));
            }
            @Override public void onApp(int index) {
                java.util.List<AppEntry> top = Prefs.top(NowPlayingActivity.this, 3);
                if (index == 3) {
                    Launch.carplay(NowPlayingActivity.this);
                    finish();
                } else if (index < top.size()) {
                    Intent i = getPackageManager()
                            .getLaunchIntentForPackage(top.get(index).pkg);
                    if (i != null) {
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        finish();
                    }
                }
            }
            @Override public void onBottom() {
                finish();
            }
            @Override public void onBottomLong() {
            }
        });
        return dock;
    }

    private void refreshDockData() {
        if (dock == null) return;
        java.util.List<AppEntry> top = Prefs.top(this, 3);
        android.graphics.Bitmap[] ic = new android.graphics.Bitmap[4];
        for (int i = 0; i < 3 && i < top.size(); i++) ic[i] = Icons.forEntry(this, top.get(i));
        ic[3] = Icons.forEntry(this, Prefs.carplay(this));
        java.util.Calendar cal = java.util.Calendar.getInstance();
        String clock = String.format(java.util.Locale.US, "%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
        StatusCenter.Snap s = StatusCenter.get(this);
        dock.setData(night, "glass".equals(Prefs.style(this)), clock, s,
                Prefs.showVoltage(this), ic);
        dock.setCarplayState(s.cp);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MediaHub.get().attach(this, this);
        refreshDockData();
    }

    @Override
    protected void onStop() {
        super.onStop();
        h.removeCallbacks(tick);
        MediaHub.get().detach(this);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_slide_out);
    }

    @Override
    public void onTrack(MediaHub.Track t) {
        pv.t = t;
        h.removeCallbacks(tick);
        if (t.playing()) h.postDelayed(tick, 1000);
        if (t.art != null) {
            int c = avg(t.art);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{c, 0xFF050506});
            float r = Ui.dp(this, 26);
            g.setCornerRadii(Ui.dockLeft(this) ? new float[]{r, r, 0, 0, 0, 0, r, r}
                    : new float[]{0, 0, r, r, r, r, 0, 0});
            root.setBackground(g);
        } else {
            root.setBackground(Ui.pageBg(this, night));
        }
        pv.invalidate();
    }

    private static int avg(Bitmap b) {
        try {
            Bitmap s = Bitmap.createScaledBitmap(b, 8, 8, true);
            long r = 0, g = 0, bl = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int p = s.getPixel(x, y);
                    r += Color.red(p);
                    g += Color.green(p);
                    bl += Color.blue(p);
                }
            }
            return Color.rgb((int) (r / 64 * 0.62), (int) (g / 64 * 0.62), (int) (bl / 64 * 0.62));
        } catch (Throwable t) {
            return 0xFF1C1C1E;
        }
    }

    // ------------------------------------------------------------ widok odtwarzacza
    private class PlayerView extends View {
        MediaHub.Track t = new MediaHub.Track();
        private final float d = getResources().getDisplayMetrics().density;
        private final float sd = getResources().getDisplayMetrics().scaledDensity;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tx = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final RectF[] hit = new RectF[12];
        private int pressed = -1;
        private boolean seeking;
        private float seekFrac;
        private float barL, barR;
        private boolean scanning;
        private long scanStart;
        private final Runnable scan = new Runnable() {
            @Override
            public void run() {
                if (pressed != PREV && pressed != NEXT) return;
                scanning = true;
                long step = 4000 + Math.min(14000, (System.currentTimeMillis() - scanStart) / 2);
                MediaHub.get().seekBy(pressed == NEXT ? step : -step);
                h.postDelayed(this, 260);
            }
        };

        static final int BACK = 0, SRC = 1, PREV = 2, PLAY = 3, NEXT = 4, BAR = 5, SHUF = 6,
                REP = 7, STAR = 8;

        PlayerView(Context c) {
            super(c);
            for (int i = 0; i < hit.length; i++) hit[i] = new RectF();
        }

        private void box(int id, float cx, float cy, float rad) {
            hit[id].set(cx - rad, cy - rad, cx + rad, cy + rad);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float hh = getHeight();
            for (RectF q : hit) q.setEmpty();
            MediaHub hub = MediaHub.get();
            boolean access = MediaHub.accessGranted(getContext());
            boolean data = t.hasData() && access;
            int fg = 0xFFFFFFFF;
            int sub = 0xCCFFFFFF;

            // przyciski górne: wstecz i JEDNA ikona źródła dźwięku
            circle(cv, 14 * d + 26 * d, 12 * d + 26 * d, 26 * d, pressed == BACK);
            box(BACK, 40 * d, 38 * d, 30 * d);
            p.setColor(fg);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            Path chev = new Path();
            chev.moveTo(46 * d, 28 * d);
            chev.lineTo(36 * d, 38 * d);
            chev.lineTo(46 * d, 48 * d);
            cv.drawPath(chev, p);
            p.setStyle(Paint.Style.FILL);

            if (data || (access && t.pkg.length() > 0)) {
                float sx = w - 40 * d;
                circle(cv, sx, 38 * d, 26 * d, pressed == SRC);
                box(SRC, sx, 38 * d, 30 * d);
                Bitmap ic = t.pkg.equals(Prefs.diplayPkg(getContext()))
                        ? Icons.carplayMark(64, 0xFFFFFFFF)
                        : Icons.forPackage(getContext(), t.pkg);
                r.set(sx - 14 * d, 38 * d - 14 * d, sx + 14 * d, 38 * d + 14 * d);
                cv.drawBitmap(ic, null, r, p);
            }

            float pad = 28 * d;
            if (!data) {
                tx.setTextAlign(Paint.Align.CENTER);
                tx.setTypeface(Typeface.DEFAULT_BOLD);
                tx.setTextSize(28 * sd);
                tx.setColor(fg);
                float s = Math.min(hh * 0.30f, 150 * d);
                r.set(w / 2f - s / 2f, hh * 0.28f, w / 2f + s / 2f, hh * 0.28f + s);
                PlayGlyphs.artPlaceholder(cv, r, s * 0.22f, true);
                cv.drawText(access ? "Odtwarzanie wyłączone" : "Włącz dostęp do powiadomień",
                        w / 2f, hh * 0.28f + s + 48 * d, tx);
                if (!access) addTap(99, w / 2f, hh * 0.5f, 300 * d);
                return;
            }

            // okładka po prawej
            float artSide = Math.min(hh * 0.66f, w * 0.40f);
            float artL = w - pad - artSide;
            float artT = (hh - artSide) / 2f;
            r.set(artL, artT, artL + artSide, artT + artSide);
            if (t.art != null) {
                BitmapShader sh = new BitmapShader(t.art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                Matrix m = new Matrix();
                float sc = Math.max(artSide / t.art.getWidth(), artSide / t.art.getHeight());
                m.setScale(sc, sc);
                m.postTranslate(artL - (t.art.getWidth() * sc - artSide) / 2f,
                        artT - (t.art.getHeight() * sc - artSide) / 2f);
                sh.setLocalMatrix(m);
                p.setShader(sh);
                cv.drawRoundRect(r, 22 * d, 22 * d, p);
                p.setShader(null);
            } else {
                PlayGlyphs.artPlaceholder(cv, r, 22 * d, true);
            }

            // lewa kolumna wyśrodkowana pionowo
            float leftW = artL - 2 * pad;
            boolean sh1 = hub.findCustom("shuffle") != null;
            boolean rp1 = hub.findCustom("repeat") != null;
            boolean st1 = hub.findCustom("like") != null || hub.findCustom("favorite") != null
                    || hub.findCustom("star") != null;
            boolean pills = sh1 || rp1 || st1;
            float blockH = (40 + 34 + 28 + 30 + 76 + 26 + 30 + (pills ? 66 : 0)) * d;
            float y0 = Math.max(70 * d, (hh - blockH) / 2f);
            tx.setTextAlign(Paint.Align.LEFT);
            tx.setTypeface(Typeface.DEFAULT_BOLD);
            tx.setTextSize(30 * sd);
            tx.setColor(fg);
            cv.drawText(TextUtils.ellipsize(t.title.length() > 0 ? t.title : "Odtwarzane", tx, leftW,
                    TextUtils.TruncateAt.END).toString(), pad, y0 + 30 * d, tx);
            tx.setTypeface(Typeface.DEFAULT);
            tx.setTextSize(22 * sd);
            tx.setColor(sub);
            cv.drawText(TextUtils.ellipsize(t.artist, tx, leftW, TextUtils.TruncateAt.END).toString(),
                    pad, y0 + 64 * d, tx);
            tx.setTextSize(18 * sd);
            tx.setColor(0x99FFFFFF);
            cv.drawText(TextUtils.ellipsize(t.album, tx, leftW, TextUtils.TruncateAt.END).toString(),
                    pad, y0 + 92 * d, tx);

            // sterowanie: tylko obsługiwane
            float cxCtl = pad + leftW / 2f;
            float ctlY = y0 + (40 + 34 + 28 + 30) * d + 38 * d;
            if (hub.canPrev()) {
                p.setColor(fg);
                p.setAlpha(pressed == PREV ? 140 : 255);
                PlayGlyphs.skip(cv, cxCtl - 98 * d, ctlY, 46 * d, false, p);
                box(PREV, cxCtl - 98 * d, ctlY, 40 * d);
            }
            if (hub.canNext()) {
                p.setColor(fg);
                p.setAlpha(pressed == NEXT ? 140 : 255);
                PlayGlyphs.skip(cv, cxCtl + 98 * d, ctlY, 46 * d, true, p);
                box(NEXT, cxCtl + 98 * d, ctlY, 40 * d);
            }
            if (hub.canToggle()) {
                p.setColor(fg);
                p.setAlpha(pressed == PLAY ? 140 : 255);
                if (t.playing()) {
                    PlayGlyphs.pause(cv, cxCtl, ctlY, 66 * d, p);
                } else {
                    PlayGlyphs.play(cv, cxCtl, ctlY, 66 * d, p);
                }
                box(PLAY, cxCtl, ctlY, 48 * d);
            }
            p.setAlpha(255);

            // postęp z czasem
            float barY = ctlY + 38 * d + 38 * d;
            barL = pad + 56 * d;
            barR = pad + leftW - 64 * d;
            long dur = t.duration;
            long pos = seeking && dur > 0 ? (long) (seekFrac * dur) : t.position();
            tx.setTextSize(17 * sd);
            tx.setColor(0xCCFFFFFF);
            tx.setTextAlign(Paint.Align.LEFT);
            if (dur > 0) {
                cv.drawText(PlayGlyphs.time(pos), pad, barY + 6 * d, tx);
                tx.setTextAlign(Paint.Align.RIGHT);
                cv.drawText("-" + PlayGlyphs.time(dur - pos), pad + leftW, barY + 6 * d, tx);
                p.setColor(0x44FFFFFF);
                r.set(barL, barY - 4 * d, barR, barY + 4 * d);
                cv.drawRoundRect(r, 4 * d, 4 * d, p);
                float f = Math.max(0f, Math.min(1f, pos / (float) dur));
                p.setColor(0xEEFFFFFF);
                r.set(barL, barY - 4 * d, barL + (barR - barL) * f, barY + 4 * d);
                cv.drawRoundRect(r, 4 * d, 4 * d, p);
                hit[BAR].set(barL - 10 * d, barY - 22 * d, barR + 10 * d, barY + 22 * d);
            }

            // dodatkowe przyciski tylko, jeśli odtwarzacz je udostępnia
            if (pills) {
                float py = barY + 54 * d;
                float x = cxCtl - ((sh1 ? 1 : 0) + (rp1 ? 1 : 0) + (st1 ? 1 : 0)) * 60 * d / 2f;
                if (sh1) {
                    pill(cv, SHUF, x + 30 * d, py);
                    x += 60 * d;
                }
                if (rp1) {
                    pill(cv, REP, x + 30 * d, py);
                    x += 60 * d;
                }
                if (st1) {
                    pill(cv, STAR, x + 30 * d, py);
                }
            }
        }

        private void addTap(int id, float cx, float cy, float rad) {
            if (id < hit.length) hit[id].set(cx - rad, cy - rad, cx + rad, cy + rad);
        }

        private void pill(Canvas cv, int id, float cx, float cy) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(pressed == id ? 0x88FFFFFF : 0x55FFFFFF);
            cv.drawCircle(cx, cy, 26 * d, p);
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.6f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            if (id == SHUF) {
                cv.drawLine(cx - 12 * d, cy - 7 * d, cx + 12 * d, cy + 7 * d, p);
                cv.drawLine(cx - 12 * d, cy + 7 * d, cx + 12 * d, cy - 7 * d, p);
            } else if (id == REP) {
                r.set(cx - 12 * d, cy - 8 * d, cx + 12 * d, cy + 8 * d);
                cv.drawRoundRect(r, 8 * d, 8 * d, p);
            } else {
                Path s = new Path();
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = (i % 2 == 0 ? 13 : 5.5f) * d;
                    float px = cx + (float) (Math.cos(a) * rr);
                    float py = cy + (float) (Math.sin(a) * rr);
                    if (i == 0) s.moveTo(px, py);
                    else s.lineTo(px, py);
                }
                s.close();
                cv.drawPath(s, p);
            }
            p.setStyle(Paint.Style.FILL);
            box(id, cx, cy, 30 * d);
        }

        private void circle(Canvas cv, float cx, float cy, float rad, boolean press) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(press ? 0x88000000 : 0x55000000);
            cv.drawCircle(cx, cy, rad, p);
        }

        private int hitAt(float x, float y) {
            for (int i = 0; i < hit.length; i++) {
                if (!hit[i].isEmpty() && hit[i].contains(x, y)) return i;
            }
            return -1;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX();
            float y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed = hitAt(x, y);
                    scanning = false;
                    if (pressed == BAR && MediaHub.get().canSeek() && t.duration > 0) {
                        seeking = true;
                        seekFrac = frac(x);
                    }
                    if (pressed == PREV || pressed == NEXT) {
                        scanStart = System.currentTimeMillis();
                        if (MediaHub.get().canScan()) {
                            h.postDelayed(scan, ViewConfiguration.getLongPressTimeout());
                        }
                    }
                    invalidate();
                    return pressed >= 0;
                case MotionEvent.ACTION_MOVE:
                    if (seeking) {
                        seekFrac = frac(x);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    h.removeCallbacks(scan);
                    int id = pressed;
                    boolean wasScan = scanning;
                    pressed = -1;
                    scanning = false;
                    if (seeking) {
                        seeking = false;
                        MediaHub.get().seekTo((long) (seekFrac * t.duration));
                    } else if (!wasScan && id >= 0 && id == hitAt(x, y)) {
                        act(id);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    h.removeCallbacks(scan);
                    pressed = -1;
                    seeking = false;
                    scanning = false;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        private float frac(float x) {
            return Math.max(0f, Math.min(1f, (x - barL) / Math.max(1f, barR - barL)));
        }

        private void act(int id) {
            Context c = getContext();
            if (id == BACK) {
                finish();
            } else if (id == SRC) {
                Launch.openSource(c, t.pkg);
            } else if (id == 99) {
                NowPlayingCard.openAccessSettings(c);
            } else if (id == PREV) {
                MediaHub.get().prev();
            } else if (id == PLAY) {
                MediaHub.get().toggle();
            } else if (id == NEXT) {
                MediaHub.get().next();
            } else if (id == SHUF) {
                MediaHub.get().sendCustom(MediaHub.get().findCustom("shuffle"));
            } else if (id == REP) {
                MediaHub.get().sendCustom(MediaHub.get().findCustom("repeat"));
            } else if (id == STAR) {
                PlaybackState.CustomAction a = MediaHub.get().findCustom("like");
                if (a == null) a = MediaHub.get().findCustom("favorite");
                if (a == null) a = MediaHub.get().findCustom("star");
                MediaHub.get().sendCustom(a);
            }
        }
    }
}







