
package pl.dock.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewConfiguration;
import pl.dock.widget.ui.SpinnerView;

// Dock w launcherze: te same wymiary co widżet (zegar, statusy, napięcie, bateria telefonu, ikony, przycisk dolny).
// Rysowany jednym widokiem; odświeżany tylko przy zmianie danych.
public class DockView extends View {

    public interface Listener {
        void onHeader();

        void onApp(int index);   // 0..2 górne aplikacje, 3 = CarPlay

        void onBottom();

        void onBottomLong();
    }

    private static final int HEADER = 5;
    private static final int BOTTOM = 4;

    private final float d;
    private Listener listener;
    private boolean night = true, glass, showVolt;
    private Bitmap glassBmp;
    private float blend;            // 0..1: strona widżetów w trybie dziennym robi dock nocny
    private String clock = "--:--";
    private String volt = "";
    private boolean charging;
    private int phone = -1;
    private boolean showPhone;
    private StatusCenter.Snap snap;
    private final Bitmap[] icons = new Bitmap[4];
    private Bitmap bWifi, bTether, bBt, bBattery, bCharge, bGrid, bCp, bPhone;
    private float bottomT;
    private Drawable bg;
    private int bgRes = -1;
    private boolean showCp = true;

    private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint voltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint homePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int pressed = -1;
    private float downX, downY;
    private final int slop;
    private final Handler longHandler = new Handler(Looper.getMainLooper());
    private boolean bottomLongFired;
    private final Runnable bottomLong = new Runnable() {
        @Override public void run() {
            bottomLongFired = true;
            if (listener != null) listener.onBottomLong();
        }
    };
    private SpinnerView spinner;
    private boolean cpReconnecting;

    public DockView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        float sd = c.getResources().getDisplayMetrics().scaledDensity;
        clockPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clockPaint.setTextSize(19f * sd);
        clockPaint.setTextAlign(Paint.Align.CENTER);
        voltPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        voltPaint.setTextSize(12f * sd);
        homePaint.setStyle(Paint.Style.STROKE);
        homePaint.setStrokeWidth(3.2f * d);
        setContentDescription("Dock");
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setCarplayState(int cpState) {
        ensureSpinner();
        boolean reconnecting = (cpState == CarplayState.PHONE);
        cpReconnecting = reconnecting;
        if (spinner != null) {
            spinner.setVisibility(reconnecting ? VISIBLE : GONE);
            int fg = effNight() ? 0xFFFFFFFF : 0xFF111111;
            spinner.setColor(CarplayState.color(cpState, fg));
            if (reconnecting) spinner.bringToFront();
        }
        invalidate();
    }

    private void ensureSpinner() {
        if (spinner == null) {
            spinner = new SpinnerView(getContext());
            // SpinnerView is drawn manually in onDraw when needed; keep as helper only
        }
    }

    private void layoutSpinner() {
        if (spinner == null) return;
        float sz = 42f * d;
        int l = (int) (cx() - sz / 2);
        int t = (int) iconTop(3);
        spinner.layout(l, t, (int) (l + sz), (int) (t + sz));
    }

    private boolean effNight() {
        return night || blend >= 0.5f;
    }

    public void setData(boolean night, boolean glass, String clock, StatusCenter.Snap s,
                        boolean showVolt, Bitmap[] ic) {
        this.night = night;
        this.glass = glass;
        this.clock = clock;
        this.showVolt = showVolt;
        this.snap = s;
        this.volt = s.volt.length() > 0 ? s.volt : "-- V";
        this.charging = s.charging;
        this.phone = s.phone;
        this.showPhone = s.phone >= 0 && s.cp == CarplayState.CONNECTED;
        for (int i = 0; i < 4; i++) icons[i] = ic != null && i < ic.length ? ic[i] : null;
        showCp = Prefs.cpShow(getContext());
        rebuild();
        invalidate();
    }

    public void setGlassBitmap(Bitmap b) {
        this.glassBmp = b;
        invalidate();
    }

    // strona widżetów: dock płynnie przechodzi w wersję nocną
    public void setNightBlend(float b) {
        b = Math.max(0f, Math.min(1f, b));
        if (Math.abs(b - blend) < 0.004f) return;
        boolean before = effNight();
        blend = b;
        if (before != effNight() && snap != null) rebuild();
        invalidate();
    }

    private void rebuild() {
        if (snap == null) return;
        boolean en = effNight();
        int fg = en ? 0xFFFFFFFF : 0xFF111111;
        int dimmer = (fg & 0x00FFFFFF) | 0x40000000;
        Context c = getContext();
        StatusCenter.Snap s = snap;
        bWifi = Icons.wifi(c, s.wifi, fg);
        bTether = Icons.tether(96, s.tether ? fg : dimmer, false);
        bBt = Icons.vector(c, R.drawable.ic_bt, 96, s.bt ? fg : dimmer);
        bBattery = Icons.vector(c, R.drawable.ic_battery, 96, fg);
        bCharge = Icons.batteryCharging(c, 96, fg);
        bGrid = Icons.vector(c, R.drawable.ic_grid, 128, fg);
        bCp = Icons.carplayMark(96, CarplayState.color(s.cp, fg));
        bPhone = phone >= 0 ? Icons.phoneBattery(phone, fg) : null;
        int res = Look.bg(glass, night);
        if (res != bgRes) {
            bgRes = res;
            bg = c.getDrawable(res).mutate();
        }
        clockPaint.setColor(fg);
        voltPaint.setColor(fg);
        homePaint.setColor(fg);
    }

    // 0 = siatka, 1 = symbol ekranu głównego
    public void setBottomProgress(float t) {
        t = Math.max(0f, Math.min(1f, t));
        if (Math.abs(t - bottomT) < 0.004f) return;
        bottomT = t;
        invalidate();
    }

    // ------------------------------------------------------------ geometria (dp -> px)
    private float panelL() {
        return 6f * d;
    }

    private float panelT() {
        return 6f * d;
    }

    private float panelR() {
        return panelL() + 65f * d;
    }

    private float panelB() {
        return getHeight() - 6f * d;
    }

    private float cx() {
        return panelL() + 32.5f * d;
    }

    private float iconTop(int i) {
        return panelT() + (164f + 54f * i) * d;
    }

    private float bottomCy() {
        return panelB() - 32f * d;
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float l = panelL();
        float t = panelT();
        float rr = panelR();
        float bt = panelB();
        float rad = 22f * d;

        if (glass) {
            // Prawdziwy live blur tła + warstwa połysku Liquid Glass
            if (glassBmp != null) {
                LiveBlur.draw(cv, glassBmp, (int) l, (int) t, (int) rr, (int) bt, effNight());
            } else if (bg != null) {
                bg.setBounds((int) l, (int) t, (int) rr, (int) bt);
                bg.draw(cv);
            }
            Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            glassPaint.setStyle(Paint.Style.FILL);
            glassPaint.setShader(new LinearGradient(l, t, rr, bt,
                    new int[]{0x44FFFFFF, 0x10FFFFFF, 0x04FFFFFF, 0x22FFFFFF},
                    new float[]{0f, 0.25f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            rect.set(l, t, rr, bt);
            cv.drawRoundRect(rect, rad, rad, glassPaint);
            Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1.4f * d);
            edge.setShader(new LinearGradient(l, t, rr, bt,
                    new int[]{0xCCFFFFFF, 0x44FFFFFF, 0x14FFFFFF, 0x55FFFFFF},
                    new float[]{0f, 0.3f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            rect.set(l + d * 0.7f, t + d * 0.7f, rr - d * 0.7f, bt - d * 0.7f);
            cv.drawRoundRect(rect, rad - d * 0.7f, rad - d * 0.7f, edge);
        } else {
            // kolor docka w nocy: #131418; ramka jaśniejsza od lewego górnego rogu
            float nb = night ? 1f : blend;
            card.setStyle(Paint.Style.FILL);
            card.setShader(null);
            card.setColor(lerp(0xFFF2F2F5, 0xFF131418, nb));
            rect.set(l, t, rr, bt);
            cv.drawRoundRect(rect, rad, rad, card);
            card.setStyle(Paint.Style.STROKE);
            card.setStrokeWidth(1.8f * d);
            boolean en = effNight();
            card.setShader(new LinearGradient(l, t, rr, bt,
                    en ? new int[]{0xCCFFFFFF, 0x4DFFFFFF, 0x14FFFFFF, 0x33FFFFFF}
                            : new int[]{0x99FFFFFF, 0x55808090, 0x22808090, 0x40808090},
                    new float[]{0f, 0.3f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            rect.set(l + d, t + d, rr - d, bt - d);
            cv.drawRoundRect(rect, rad - d, rad - d, card);
            card.setShader(null);
        }

        float cx = cx();
        float top = t;

        // zegar
        float cy = top + 24f * d;
        cv.drawText(clock, cx, cy - (clockPaint.descent() + clockPaint.ascent()) / 2f, clockPaint);

        // WiFi, tethering, Bluetooth (telefon)
        float sy = top + 50f * d;
        float is = 16f * d;
        float x0 = cx - 27f * d;
        drawIcon(cv, bWifi, x0, sy - is / 2f, is, is, 255);
        drawIcon(cv, bTether, x0 + 19f * d, sy - is / 2f, is, is, 255);
        drawIcon(cv, bBt, x0 + 38f * d, sy - is / 2f, is, is, 255);

        // kolejne wiersze: napięcie / ładowanie, bateria telefonu, status CarPlay
        float y = top + 76f * d;
        if (showVolt) {
            if (charging) {
                drawIcon(cv, bCharge, cx - 12f * d, y - 10f * d, 24f * d, 20f * d, 255);
            } else {
                float tw = voltPaint.measureText(volt);
                float start = cx - (24f * d + 3f * d + tw) / 2f;
                drawIcon(cv, bBattery, start + 2f * d, y - 10f * d, 20f * d, 20f * d, 255);
                cv.drawText(volt, start + 27f * d,
                        y - (voltPaint.descent() + voltPaint.ascent()) / 2f, voltPaint);
            }
            y += 26f * d;
        }
        if (showPhone && bPhone != null) {
            String pct = phone + "%";
            float tw = voltPaint.measureText(pct);
            float start = cx - (24f * d + 3f * d + tw) / 2f;
            drawIcon(cv, bPhone, start, y - 6f * d, 24f * d, 12f * d, 255);
            cv.drawText(pct, start + 27f * d, y - (voltPaint.descent() + voltPaint.ascent()) / 2f,
                    voltPaint);
            y += 26f * d;
        }
        if (showCp) {
            drawIcon(cv, bCp, cx - 10f * d, y - 10f * d, 20f * d, 20f * d, 255);
        }

        // ikony: 3 górne + CarPlay
        float sz = 42f * d;
        for (int i = 0; i < 4; i++) {
            Bitmap b = icons[i];
            if (b == null) continue;
            drawIcon(cv, b, cx - sz / 2f, iconTop(i), sz, sz, pressed == i ? 150 : 255);
        }

        // przycisk dolny: siatka -> symbol ekranu głównego
        float by = bottomCy();
        int pa = pressed == BOTTOM ? 150 : 255;
        float g = 20f * d;
        if (bottomT < 0.999f) {
            drawIcon(cv, bGrid, cx - g / 2f, by - g / 2f, g, g, (int) (pa * (1f - bottomT)));
        }
        if (bottomT > 0.001f) {
            homePaint.setAlpha((int) (pa * bottomT));
            rect.set(cx - g / 2f, by - g / 2f, cx + g / 2f, by + g / 2f);
            cv.drawRoundRect(rect, 6f * d, 6f * d, homePaint);
        }
    }

    private void drawIcon(Canvas cv, Bitmap b, float l, float t, float w, float h, int alpha) {
        if (b == null || alpha <= 0) return;
        bmpPaint.setAlpha(alpha);
        rect.set(l, t, l + w, t + h);
        cv.drawBitmap(b, null, rect, bmpPaint);
    }

    // ------------------------------------------------------------ dotyk
    private int hit(float x, float y) {
        if (x < panelL() || x > panelR()) return -1;
        float top = panelT();
        if (y >= top && y < top + 164f * d) return HEADER;
        float sz = 42f * d;
        float m = 6f * d;
        for (int i = 0; i < 4; i++) {
            float t = iconTop(i);
            if (y >= t - m && y <= t + sz + m) return i;
        }
        float by = bottomCy();
        if (Math.abs(y - by) <= 28f * d) return BOTTOM;
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                pressed = hit(downX, downY);
                if (pressed < 0) return false;
                if (pressed < 4 && icons[pressed] == null && pressed != 3) {
                    pressed = -1;
                    return false;
                }
                if (pressed == BOTTOM) {
                    bottomLongFired = false;
                    longHandler.postDelayed(bottomLong, ViewConfiguration.getLongPressTimeout());
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed >= 0 && (Math.abs(e.getX() - downX) > slop
                        || Math.abs(e.getY() - downY) > slop)) {
                    longHandler.removeCallbacks(bottomLong);
                    pressed = -1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                longHandler.removeCallbacks(bottomLong);
                int p = pressed;
                pressed = -1;
                invalidate();
                if (p >= 0 && !bottomLongFired && listener != null) {
                    if (p == HEADER) {
                        listener.onHeader();
                    } else if (p == BOTTOM) {
                        listener.onBottom();
                    } else {
                        listener.onApp(p);
                    }
                }
                bottomLongFired = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                longHandler.removeCallbacks(bottomLong);
                pressed = -1;
                bottomLongFired = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}







