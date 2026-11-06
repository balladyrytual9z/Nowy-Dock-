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
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewConfiguration;

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
    private int glassX;      // polozenie doka w root: wycinek pelnoekranowego snapshota
    private int rootW, rootH;
    private ViewGroup glassSource;   // root: zywe odswiezanie szkla w trakcie przewijania
    private long lastGlassAt;        // throttle snapshotow (~150 ms) dla plynnosci gestu
    private float blend;            // 0..1: strona widżetów w trybie dziennym robi dock nocny
    private String clock = "--:--";
    private String volt = "";
    private boolean charging;
    private int phone = -1;
    private boolean showPhone;
    private StatusCenter.Snap snap;
    private final Bitmap[] icons = new Bitmap[4];
    private Bitmap bWifi, bTether, bBt, bBattery, bCharge, bCp, bPhone;
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
    private boolean cpReconnecting;
    private long cpSpinnerStart = 0L;
    private final Paint spinnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF spinnerRect = new RectF();

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
        boolean reconnecting = (cpState == CarplayState.PHONE);
        if (reconnecting && !cpReconnecting) {
            cpSpinnerStart = System.currentTimeMillis();
        }
        cpReconnecting = reconnecting;
        invalidate();
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

    // polozenie doka na ekranie + rozmiar roota: glass rysuje WYCINEK snapshota
    // caloekranowego (dokladnie to, co jest za dokia), a nie rozciaga calej bitmapy
    public void setGlassOrigin(int x) {
        this.glassX = x;
    }

    public void setRootSize(int w, int h) {
        this.rootW = w;
        this.rootH = h;
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
        // Ikony pod zegarem (Wi-Fi, hotspot, Bluetooth) rysowane kodem przez Glyphs -
        // to samo źródło prawdy, z którego korzysta panel łączności. Wcześniej hotspot
        // i Bluetooth szły z res/drawable/ic_*.xml przez Icons.vector() z filtrem
        // PorterDuff.SRC_IN, który na docelowym sprzęcie (ESSGOO MT8163 / Mali-T720 /
        // Android 9) gubił obrysowe wektory z przezroczystym wypełnieniem i ikony
        // w doku nie pojawiały się.
        bWifi = Icons.wifi(c, s.wifi, fg);
        bTether = Glyphs.bitmap("hot", 96, s.tether ? fg : dimmer);
        bBt = Glyphs.bitmap("bt", 96, s.bt ? fg : dimmer);
        bBattery = Icons.vector(c, R.drawable.ic_battery, 96, fg);
        bCharge = Icons.batteryCharging(c, 96, fg);
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

    // 0 = ekran glowny (siatka 9 kwadratow), 1 = lista aplikacji (jeden kwadrat z zaokraglonymi rogami)
    public void setBottomProgress(float t) {
        t = Math.max(0f, Math.min(1f, t));
        if (Math.abs(t - bottomT) < 0.004f) return;
        bottomT = t;
        invalidate();
    }

    // Liquid Glass ma "zyc": gdy cokolwiek na ekranie sie rusza (przewijanie stron,
    // gest, animacja), tresc przesuwa sie POD szklem i musi byc widoczna.
    // Snapshot odswiezamy throttlowany (min 150 ms) przez OnPreDraw - czysto
    // stagnujacy ekran nie robi zadnych dodatkowych snapshotow. Bufor w LiveBlur
    // jest reuzywany (zero alokacji), wiec gest nie tnie na slabym GPU.
    private void maybeRefreshGlass() {
        if (!glass || glassSource == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastGlassAt < 150L) return;
        lastGlassAt = now;
        post(glassCapture);
    }

    private final Runnable glassCapture = new Runnable() {
        @Override public void run() {
            if (!glass || glassSource == null) return;
            Bitmap b = LiveBlur.captureAndBlur(glassSource,
                    glassSource.getWidth(), glassSource.getHeight());
            if (b != null) {
                glassBmp = b;
                rootW = glassSource.getWidth();
                rootH = glassSource.getHeight();
                invalidate();
            }
        }
    };

    private final ViewTreeObserver.OnPreDrawListener glassPreDraw =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    maybeRefreshGlass();
                    return true;
                }
            };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() instanceof ViewGroup) glassSource = (ViewGroup) getParent();
        // szkło nie moze pokazywac wlasnego ducha: snapshot tla pomija docka
        LiveBlur.snapshotExclude = this;
        getViewTreeObserver().addOnPreDrawListener(glassPreDraw);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (LiveBlur.snapshotExclude == this) LiveBlur.snapshotExclude = null;
        try {
            getViewTreeObserver().removeOnPreDrawListener(glassPreDraw);
        } catch (Throwable ignored) {
        }
        super.onDetachedFromWindow();
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
            // Liquid Glass: rozmyte + rozjasnione tlo pod dokia, a na nim warstwy
            // swiatel - jasny rim u gory, wewnetrzny cien na dole, korpus szkla
            if (glassBmp != null) {
                // wycinek pelnoekranowego snapshota spod doka (1:1 z tlem ekranu),
                // nie miniatura calego ekranu scisnieta do paska doka
                LiveBlur.drawRegion(cv, glassBmp, rootW, rootH,
                        glassX + (int) l, (int) t, glassX + (int) rr, (int) bt,
                        l, t, rr, bt, effNight());
            } else if (bg != null) {
                bg.setBounds((int) l, (int) t, (int) rr, (int) bt);
                bg.draw(cv);
            }
            // 1) korpus szkla: rozblysk u gory, wyrazny poblask na dole (glebia)
            Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
            body.setStyle(Paint.Style.FILL);
            body.setShader(new LinearGradient(l, t, l, bt,
                    new int[]{0x2EFFFFFF, 0x0DFFFFFF, 0x08FFFFFF, 0x24FFFFFF},
                    new float[]{0f, 0.16f, 0.62f, 1f}, Shader.TileMode.CLAMP));
            rect.set(l, t, rr, bt);
            cv.drawRoundRect(rect, rad, rad, body);
            // 2) wewnetrzny cien na dole - szklo "unosi sie" nad tlem
            Paint bottomShade = new Paint(Paint.ANTI_ALIAS_FLAG);
            bottomShade.setStyle(Paint.Style.FILL);
            bottomShade.setShader(new LinearGradient(l, bt - 14f * d, l, bt,
                    new int[]{0x00000000, 0x30000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            rect.set(l + 2f * d, bt - 14f * d, rr - 2f * d, bt - 2f * d);
            cv.drawRoundRect(rect, rad * 0.5f, rad * 0.5f, bottomShade);
            // 3) jasny rim u gory - refrakcja swiatla na krawedzi szkla
            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(2f * d);
            rim.setShader(new LinearGradient(l, t, l, t + 22f * d,
                    new int[]{0xE6FFFFFF, 0x4DFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            rect.set(l + 1f * d, t + 1f * d, rr - 1f * d, bt - 1f * d);
            cv.drawRoundRect(rect, rad - 1f * d, rad - 1f * d, rim);
            // 4) obwodka - plynnie przejscie szkla w otoczenie
            Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1.4f * d);
            edge.setShader(new LinearGradient(l, t, rr, bt,
                    new int[]{0xB3FFFFFF, 0x40FFFFFF, 0x14FFFFFF, 0x40FFFFFF},
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
            if (i == 3 && cpReconnecting) continue;   // spinner zastępuje ikonę CP
            drawIcon(cv, b, cx - sz / 2f, iconTop(i), sz, sz, pressed == i ? 150 : 255);
        }
        if (cpReconnecting) {
            drawSpinnerRing(cv, cx, iconTop(3) + sz / 2f, sz * 0.35f);
            postInvalidateOnAnimation();   // wiruje ~1 obrót/s, dopóki PHONE
        }

        // przycisk dolny - kontrakt (zgodny z LauncherActivity.onBottom()):
        //   ekran glowny / strony widzetow -> siatka 9 kwadratow (3x3) = "lista aplikacji",
        //   strony aplikacji i Biblioteka -> jeden kwadrat z zaokraglonymi rogami = "ekran glowny".
        // Czyli glif pokazuje CEL dotkniecia, a nie miejsce, w ktorym jestesmy.
        // Crossfade w czasie przejazdu miedzy stronami (bottomT 0..1).
        float by = bottomCy();
        int pa = pressed == BOTTOM ? 150 : 255;
        float g = 20f * d;
        if (bottomT < 0.999f) {
            // nie jestesmy jeszcze na liscie aplikacji: siatka 9 kwadratow (3x3)
            homePaint.setAlpha((int) (pa * (1f - bottomT)));
            homePaint.setStyle(Paint.Style.FILL);
            float gap = g / 3f;
            float q = gap * 0.62f;
            float rr2 = q * 0.28f;
            for (int gy = 0; gy < 3; gy++) {
                for (int gx = 0; gx < 3; gx++) {
                    float qx = cx - g / 2f + gx * gap + (gap - q) / 2f;
                    float qy = by - g / 2f + gy * gap + (gap - q) / 2f;
                    rect.set(qx, qy, qx + q, qy + q);
                    cv.drawRoundRect(rect, rr2, rr2, homePaint);
                }
            }
            homePaint.setStyle(Paint.Style.STROKE);
        }
        if (bottomT > 0.001f) {
            // jestesmy na liscie aplikacji: jeden kwadrat z zaokraglonymi rogami
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

    /** Wirujący pierścień (iOS "reconnecting") rysowany inline zamiast dziecka View. */
    private void drawSpinnerRing(Canvas cv, float cx, float cy, float r) {
        int fg = effNight() ? 0xFFFFFFFF : 0xFF111111;
        float ang = ((System.currentTimeMillis() - cpSpinnerStart) % 1000L) * 0.36f;
        spinnerPaint.setStyle(Paint.Style.STROKE);
        spinnerPaint.setStrokeWidth(3.0f * d);
        spinnerPaint.setStrokeCap(Paint.Cap.ROUND);
        spinnerRect.set(cx - r, cy - r, cx + r, cy + r);
        for (int i = 0; i < 8; i++) {
            int alpha = Math.min(255, 40 + i * 25);
            spinnerPaint.setColor((fg & 0x00FFFFFF) | (alpha << 24));
            cv.save();
            cv.rotate(ang + i * 45f, cx, cy);
            cv.drawArc(spinnerRect, -18f, 22f, false, spinnerPaint);
            cv.restore();
        }
        spinnerPaint.setStyle(Paint.Style.FILL);
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
