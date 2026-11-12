package pl.dock.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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

    // ikona dolnego przycisku w ekranach wewnetrznych = strona, na ktora przycisk wroci
    public static final int BOTTOM_GRID = 0;     // siatka 3x3 (strony aplikacji)
    public static final int BOTTOM_HOME = 1;    // jeden kwadrat (ekran glowny)
    public static final int BOTTOM_WIDGETS = 2; // trzy kwadraciki (strona widzetow)
    public static final int BOTTOM_CC = 3;      // otwarte centrum lacznosci: ikona zamkniecia (suwaki w squircle)

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
    private long lastGlassAt;        // throttle snapshotow (~50 ms) dla plynnosci gestu
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
    private int bottomMode = -1;   // -1 = crossfade launchera; >=0 = staly glif (ekrany wewnetrzne)
    private float ccT;             // 0..1: płynne pojawianie/zanikanie glifu CC (panel łączności)
    private boolean ccShowing;     // kierunek animacji glifu CC
    private Drawable bg;
    private int bgRes = -1;
    private boolean showCp = true;
    // ostatni tap w naglowek doka (wspolrzedne lokalne) - punkt startu animacji panelu lacznosci
    private float lastTapX, lastTapY;

    private final Paint clockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint voltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint homePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path glassClip = new Path();   // clip tla szkla do zaokraglonych rogow

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

    /** Ostatni tap w naglowek doka (wspolrzedne lokalne) - start animacji panelu lacznosci. */
    public float lastTapX() {
        return lastTapX;
    }

    public float lastTapY() {
        return lastTapY;
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
        // Ikony pod zegarem = TE SAME co w panelu łączności: ic_wifi/ic_bt przez
        // Icons.vector + Icons.tether (identyczna ścieżka kodu jak kafelki panelu,
        // które użytkownik zatwierdził na tablecie). Wcześniejsze glify z Glyphs
        // miały inne kształty; pełne wektory z opaque fill renderują się na
        // MT8163 poprawnie (zawodne były tylko wektory obrysowe z pustym fill).
        bWifi = Icons.wifi(c, s.wifi, fg);
        bTether = Icons.tether(96, s.tether ? fg : dimmer, false);
        bBt = Icons.vector(c, R.drawable.ic_bt, 96, s.bt ? fg : dimmer);
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
        bottomMode = -1;   // launcher: wroc do trybu crossfade
        if (ccT > 0.001f) ccShowing = false;   // dokończ płynne wygaszanie glifu CC
        t = Math.max(0f, Math.min(1f, t));
        if (Math.abs(t - bottomT) < 0.004f) return;
        bottomT = t;
        invalidate();
    }

    /** Ekrany wewnetrzne: staly glif dolnego przycisku = strona, na ktora wroci.
     *  Przejście z/na glif CC (panel łączności) to PŁYNNY crossfade
     *  (jak przejście ekran główny ↔ siatka aplikacji). */
    public void setBottomMode(int mode) {
        boolean wasCc = ccShowing || ccT > 0.001f;
        bottomMode = mode;
        if (mode == BOTTOM_CC) {
            ccShowing = true;            // płynne pojawienie glifu "zamknij"
        } else if (wasCc) {
            ccShowing = false;           // płynne wygaszenie na tle nowego trybu
        } else {
            ccT = 0f;
        }
        invalidate();
    }

    /** Tryb ikony dla strony launchera (null = ekran glowny). */
    public static int bottomModeFor(String page) {
        if (Prefs.PAGE_APPS.equals(page)) return BOTTOM_GRID;
        if (Prefs.PAGE_WIDGETS.equals(page)) return BOTTOM_WIDGETS;
        return BOTTOM_HOME;
    }

    // Liquid Glass ma "zyc": gdy cokolwiek na ekranie sie rusza (przewijanie stron,
    // gest, animacja), tresc przesuwa sie POD szklem i musi byc widoczna.
    // Snapshot odswiezamy throttlowany (min 50 ms) przez OnPreDraw - czysto
    // stagnujacy ekran nie robi zadnych dodatkowych snapshotow. Bufor w LiveBlur
    // jest reuzywany (zero alokacji), wiec gest nie tnie na slabym GPU.
    private void maybeRefreshGlass() {
        if (!glass || glassSource == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastGlassAt < 50L) return;
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
    // Paddingi wewnetrzne Docka (wg specyfikacji: 16px gora/dol, 12px boki).
    // W Androidzie uzywajemy dp (d = gęstość ekranu); 16px ~ 8dp, 12px ~ 6dp na 2x dpi.
    private final float topPad = 8f * d;    // 16px -> zegar nie dotyka krawedzi gorniej
    private final float bottomPad = 8f * d; // 16px -> ostatnia ikona nie dotyka krawedzi dolnej
    private final float sidePad = 6f * d;   // 12px -> ikony nie dotykaja scian bocznych

    private float panelL() {
        return sidePad;
    }

    private float panelT() {
        return topPad;
    }

    private float panelR() {
        return panelL() + 65f * d;
    }

    private float panelB() {
        return getHeight() - bottomPad;
    }

    private float cx() {
        return panelL() + 32.5f * d;
    }

    /** Wysokosc dostepna na zawartosc (pomiedzy paddingami gora/dol). */
    private float contentH() {
        return panelB() - panelT();
    }

    /** Oblicza pionowe pozycje wszystkich sekcji Docka z uzyciem space-between.
     *  Zwraca tablice y-srodkow kazdej sekcji w kolejnosci od gory. */
    private float[] calcSectionYs() {
        // Wysokosci poszczegolnych sekcji (w dp, przemnozone przez d):
        // 1. Zegar
        // 2. Wiersz sieci Wi-Fi/tether/BT (19dp)
        // 3. Napiecie (opcjonalnie, 20dp)
        // 4. Bateria telefonu (opcjonalnie, 12dp)
        // 5. CarPlay (opcjonalnie, 20dp)
        // 6-9. Cztery ikony aplikacji (po 42dp)
        // 10. Przycisk dolny (20dp glif)
        float clockH = 19f * d;
        float netRowH = 19f * d;
        float voltH = showVolt ? 20f * d : 0f;
        float phoneH = showPhone ? 12f * d : 0f;
        float cpH = showCp ? 20f * d : 0f;
        float iconH = 42f * d;
        float bottomH = 20f * d;

        // Buduj liste sekcji
        java.util.ArrayList<Float> heights = new java.util.ArrayList<>();
        heights.add(clockH);           // zegar
        heights.add(netRowH);          // Wi-Fi/tether/BT
        if (showVolt) heights.add(voltH);
        if (showPhone) heights.add(phoneH);
        if (showCp) heights.add(cpH);
        for (int i = 0; i < 4; i++) {
            if (icons[i] != null && !(i == 3 && cpReconnecting)) heights.add(iconH);
        }
        heights.add(bottomH);          // przycisk dolny

        int n = heights.size();
        float totalH = 0f;
        for (float h : heights) totalH += h;

        float avail = contentH();
        float gap = n > 1 ? Math.max(0f, (avail - totalH) / (n - 1)) : 0f;

        float[] ys = new float[n];
        float y = panelT();
        for (int i = 0; i < n; i++) {
            ys[i] = y + heights.get(i) / 2f;  // srodek sekcji
            y += heights.get(i) + gap;
        }
        return ys;
    }

    /** Srodek sekcji o danym indeksie (z calcSectionYs). */
    private float secY(float[] ys, int idx) {
        return idx >= 0 && idx < ys.length ? ys[idx] : panelT();
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
            // NAPRAWA: tlo szkla (snapshot i zapasowy drawable) przycinamy
            // do zaokraglonych rogow doka (rad 22 dp) - wczesniej rysowalo sie
            // w kwadracie i w naroznikach wystawaly kwadratowe plamy tla
            // poza obwodka szkla.
            rect.set(l, t, rr, bt);
            glassClip.reset();
            glassClip.addRoundRect(rect, rad, rad, Path.Direction.CW);
            cv.save();
            cv.clipPath(glassClip);
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
            cv.restore();
            // 1) korpus szkla: rozblysk u gory, wyrazny poblask na dole (glebia)
            Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
            body.setStyle(Paint.Style.FILL);
            body.setShader(new LinearGradient(l, t, l, bt,
                    new int[]{0x24FFFFFF, 0x08FFFFFF, 0x05FFFFFF, 0x1AFFFFFF},
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
        float top = panelT();

        // Oblicz pionowe pozycje sekcji z space-between (równe odstępy).
        float[] ys = calcSectionYs();
        int si = 0; // indeks sekcji

        // zegar (sekcja 0)
        float cy = secY(ys, si++);
        cv.drawText(clock, cx, cy - (clockPaint.descent() + clockPaint.ascent()) / 2f, clockPaint);

        // WiFi, tethering, Bluetooth (telefon) - sekcja 1
        float sy = secY(ys, si++);
        float is = 19f * d;   // ikona
        float igp = 3f * d;   // rowny odstep miedzy ikonami
        float x0 = cx - (3f * is + 2f * igp) / 2f;
        drawIcon(cv, bWifi, x0, sy - is / 2f, is, is, 255);
        drawIcon(cv, bTether, x0 + is + igp, sy - is / 2f, is, is, 255);
        drawIcon(cv, bBt, x0 + 2f * (is + igp), sy - is / 2f, is, is, 255);

        // Napięcie / ładowanie (sekcja 2, opcjonalnie)
        if (showVolt) {
            float vy = secY(ys, si++);
            if (charging) {
                drawIcon(cv, bCharge, cx - 12f * d, vy - 10f * d, 24f * d, 20f * d, 255);
            } else {
                float tw = voltPaint.measureText(volt);
                float start = cx - (24f * d + 3f * d + tw) / 2f;
                drawIcon(cv, bBattery, start + 2f * d, vy - 10f * d, 20f * d, 20f * d, 255);
                cv.drawText(volt, start + 27f * d,
                        vy - (voltPaint.descent() + voltPaint.ascent()) / 2f, voltPaint);
            }
        }
        // Bateria telefonu (sekcja 3, opcjonalnie)
        if (showPhone && bPhone != null) {
            float py = secY(ys, si++);
            String pct = phone + "%";
            float tw = voltPaint.measureText(pct);
            float start = cx - (24f * d + 3f * d + tw) / 2f;
            drawIcon(cv, bPhone, start, py - 6f * d, 24f * d, 12f * d, 255);
            cv.drawText(pct, start + 27f * d, py - (voltPaint.descent() + voltPaint.ascent()) / 2f,
                    voltPaint);
        }
        // CarPlay (sekcja 4, opcjonalnie)
        if (showCp) {
            float cpY = secY(ys, si++);
            drawIcon(cv, bCp, cx - 10f * d, cpY - 10f * d, 20f * d, 20f * d, 255);
        }

        // ikony: 3 górne + CarPlay (sekcje 5-8, opcjonalnie)
        float sz = 42f * d;
        for (int i = 0; i < 4; i++) {
            Bitmap b = icons[i];
            if (b == null) continue;
            if (i == 3 && cpReconnecting) continue;   // spinner zastępuje ikonę CP
            float iy = secY(ys, si++);
            drawIcon(cv, b, cx - sz / 2f, iy - sz / 2f, sz, sz, pressed == i ? 150 : 255);
        }
        if (cpReconnecting) {
            // CP spinner - używamy ostatniej pozycji ikony (sekcja 8)
            int lastIconIdx = 4; // index ikony CP w sekcjach (po zegar, sieć, opcjonalne...)
            // liczymy ile ikon aplikacji przed CP
            int appIconsBeforeCp = 0;
            for (int j = 0; j < 3; j++) {
                if (icons[j] != null) appIconsBeforeCp++;
            }
            // Pozycja CP to sekcja: 0 zegar, 1 sieć, 2 volt?, 3 phone?, 4 cp?, 5-8 ikony
            // Prostszym rozwiązaniem: użyj ostatniej ikony (przed przyciskiem dolnym)
            float cpSpinY = secY(ys, ys.length - 2);
            drawSpinnerRing(cv, cx, cpSpinY + sz / 2f, sz * 0.35f);
            postInvalidateOnAnimation();   // wiruje ~1 obrót/s, dopóki PHONE
        }

        // przycisk dolny - ostatnia sekcja
        float by = secY(ys, ys.length - 1);
        int pa = pressed == BOTTOM ? 150 : 255;
        float g = 20f * d;
        // płynna animacja glifu CC (tickCc prosi o kolejne klatki w trakcie animacji)
        tickCc();
        if (bottomMode == BOTTOM_CC) {
            // otwarte centrum łączności: PŁYNNY crossfade (jak między stronami):
            // pod spodem glif launchera (cel powrotu) wygaszany z ccT,
            // na wierzchu glif "zamknij" narastający z ccT.
            drawLauncherBottom(cv, cx, by, g, pa, Math.round(255 * (1f - ccT)));
            drawCcGlyph(cv, cx, by, g, Math.round(pa * ccT));
        } else if (bottomMode >= 0) {
            // EKRANY WEWNETRZNE (Ustawienia, Media Center, Odtwarzane): glif
            // pokazuje strone, na ktora przycisk WROCI (origin page), a nie cel
            // przelaczenia jak w launcherze. Wygasający glif CC dolewa się płynnie.
            if (ccT > 0.001f) drawCcGlyph(cv, cx, by, g, Math.round(pa * ccT));
            int mul = Math.round(255 * (1f - ccT));
            homePaint.setAlpha((pa * mul) / 255);
            if (bottomMode == BOTTOM_WIDGETS) {
                // strona widzetow: trzy kwadraciki w rzedzie (nowa ikona)
                homePaint.setStyle(Paint.Style.FILL);
                float gap = g / 3f;
                float q = gap * 0.66f;
                float rr2 = q * 0.28f;
                for (int gx = 0; gx < 3; gx++) {
                    float qx = cx - g / 2f + gx * gap + (gap - q) / 2f;
                    rect.set(qx, by - q / 2f, qx + q, by + q / 2f);
                    cv.drawRoundRect(rect, rr2, rr2, homePaint);
                }
                homePaint.setStyle(Paint.Style.STROKE);
            } else if (bottomMode == BOTTOM_HOME) {
                // ekran glowny: jeden kwadrat z zaokraglonymi rogami
                rect.set(cx - g / 2f, by - g / 2f, cx + g / 2f, by + g / 2f);
                cv.drawRoundRect(rect, 6f * d, 6f * d, homePaint);
            } else {
                // strony aplikacji / Biblioteka: siatka 3x3
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
            homePaint.setAlpha(255);
        } else {
            // launcher: crossfade siatka/kwadrat wg bottomT + wygasający glif CC
            // po zamknięciu panelu łączności (płynny powrót ikony).
            int mul = Math.round(255 * (1f - ccT));
            if (bottomT < 0.999f) {
                // nie jestesmy jeszcze na liscie aplikacji: siatka 9 kwadratow (3x3)
                homePaint.setAlpha(((int) (pa * (1f - bottomT)) * mul) / 255);
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
                homePaint.setAlpha(((int) (pa * bottomT) * mul) / 255);
                rect.set(cx - g / 2f, by - g / 2f, cx + g / 2f, by + g / 2f);
                cv.drawRoundRect(rect, 6f * d, 6f * d, homePaint);
            }
            if (ccT > 0.001f) drawCcGlyph(cv, cx, by, g, Math.round(pa * ccT));
            homePaint.setAlpha(255);
        }
    }

    /** Glif "zamknij centrum łączności": ramka IDENTYCZNA jak przycisk "ekran
     *  główny" (BOTTOM_HOME): bok g, promień 6 dp, stroke 3.2 dp. Zawartość
     *  (dwa pionowe suwaki z kółkiem, obrócona o 90 stopni) odchudzona
     *  proporcjonalnie: suwaki 1.4 dp, kółka 1.5k (było 1.8 dp / 2k). */
    private void drawCcGlyph(Canvas cv, float cx, float by, float g, int alpha) {
        if (alpha <= 0) return;
        homePaint.setStyle(Paint.Style.STROKE);
        homePaint.setStrokeWidth(3.2f * d);
        homePaint.setAlpha(alpha);
        rect.set(cx - g / 2f, by - g / 2f, cx + g / 2f, by + g / 2f);
        cv.drawRoundRect(rect, 6f * d, 6f * d, homePaint);
        cv.save();
        cv.rotate(90f, cx, by);
        homePaint.setStrokeWidth(1.4f * d);
        float k = g / 24f;
        cv.drawLine(cx - g / 2f + 5f * k, by - g / 2f + 9.5f * k,
                cx - g / 2f + 19f * k, by - g / 2f + 9.5f * k, homePaint);
        cv.drawLine(cx - g / 2f + 5f * k, by - g / 2f + 14.5f * k,
                cx - g / 2f + 19f * k, by - g / 2f + 14.5f * k, homePaint);
        homePaint.setStyle(Paint.Style.FILL);
        cv.drawCircle(cx - g / 2f + 16f * k, by - g / 2f + 9.5f * k, 1.5f * k, homePaint);
        cv.drawCircle(cx - g / 2f + 8f * k, by - g / 2f + 14.5f * k, 1.5f * k, homePaint);
        cv.restore();
        homePaint.setStyle(Paint.Style.STROKE);
        homePaint.setStrokeWidth(3.2f * d);
        homePaint.setAlpha(255);
    }

    /** Glif dolnego przycisku launchera (crossfade siatka 3x3 / kwadrat wg bottomT).
     *  alphaMul 0..255 mnoży alfy - używany przy płynnym crossfade z glifem CC. */
    private void drawLauncherBottom(Canvas cv, float cx, float by, float g, int pa, int alphaMul) {
        if (alphaMul <= 0) return;
        if (bottomT < 0.999f) {
            homePaint.setAlpha(((int) (pa * (1f - bottomT)) * alphaMul) / 255);
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
            homePaint.setAlpha(((int) (pa * bottomT) * alphaMul) / 255);
            rect.set(cx - g / 2f, by - g / 2f, cx + g / 2f, by + g / 2f);
            cv.drawRoundRect(rect, 6f * d, 6f * d, homePaint);
        }
        homePaint.setAlpha(255);
    }

    /** Wygładzona animacja glifu CC (ccT 0..1). Wywoływana przy rysowaniu
     *  dolnego przycisku; w trakcie animacji prosi o kolejną klatkę. */
    private void tickCc() {
        float target = ccShowing ? 1f : 0f;
        if (Math.abs(ccT - target) < 0.02f) {
            ccT = target;
            return;
        }
        ccT += (target - ccT) * 0.30f;   // ~200 ms do pełna przy 60 fps
        postInvalidateOnAnimation();
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

        // Zegar - sekcja 0
        float[] ys = calcSectionYs();
        float cy = secY(ys, 0);
        if (y >= cy - 12f * d && y <= cy + 12f * d) return HEADER;

        // Ikony aplikacji - szukamy po pozycjach z calcSectionYs.
        // Kolejnosc sekcji w calcSectionYs(): zegar(0), siec(1), volt?(2), phone?(3), cp?(4),
        // potem tylko nie-null ikony aplikacji (pomijamy CP gdy cpReconnecting), na koncu przycisk dolny.
        int si = 1; // sekcja po zegarze = siec
        if (showVolt) si++;
        if (showPhone) si++;
        if (showCp && !cpReconnecting) si++;  // CP jest w sekcjach tylko gdy nie cpReconnecting
        float sz = 42f * d;
        float m = 6f * d;
        int iconIdx = 0; // rzeczywisty indeks ikony (0..3)
        for (int i = 0; i < 4; i++) {
            Bitmap b = icons[i];
            if (b == null) continue;
            if (i == 3 && cpReconnecting) continue;
            float iy = secY(ys, si + iconIdx);
            if (y >= iy - sz / 2f - m && y <= iy + sz / 2f + m) return i;
            iconIdx++;
        }
        float by = secY(ys, ys.length - 1);
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
                        // zapamietaj miejsce tapniecia (pod zegarem) - panel
                        // lacznosci rosnie/zanika od tego punktu (jak w iOS)
                        lastTapX = downX;
                        lastTapY = downY;
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


