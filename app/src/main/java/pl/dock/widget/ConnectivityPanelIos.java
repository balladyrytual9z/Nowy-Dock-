package pl.dock.widget;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

// Centrum łacznosci w stylu iOS - port 1:1 z Liquid-Glass-Diplay---Launcher-Switcher-.
// Ikony Wi-Fi/Bluetooth/Hotspot DOKŁADNIE jak w oryginale (Icons.vector na
// ic_wifi/ic_wifi_off/ic_bt + Icons.tether). Wymiary oryginalne: kafelki 150/76dp,
// suwaki 70x238dp, przyciski 88x96dp, lewa kolumna 400dp.
// Zmiany na życzenie (i NIC więcej):
// 1. Jasny/Ciemny/Auto w JEDNYM przycisku (klik = cykl trybów).
// 2. Night Shift: klik = wl/wyl, PRZYTRZYMANIE = suwak ciepła (jak w iOS).
// 3. Tło panelu = PanelGlassFrame (ramka szkła doka 1:1, jak w nakładce).
// 4. NAPRAWA UCIĘCIA: zawartość panelu ma 574dp (400+10+70+70+24), a okno
//    było 560dp - prawy suwak (jasność) wystawał poza tło. Panel ma teraz
//    szerokość WRAP_CONTENT, więc tło obejmuje całą zawartość.
// 5. SZKŁO (jak w iOS): kafelki, przyciski i suwaki półprzezroczyste,
//    światło TYLKO Z GÓRY (pionowe gradienty) - bez ukośnego światła z lewej,
//    które jest w dokku/ramce okna.
// 6. IKONY SUWAKÓW 1:1 ze zdjęcia iOS: słońce #FFD700 (koło + 8 cienkich
//    promieni), głośnik #1EA1FF (korpus z "uszami" + 2 łuki fal konturem).
// 7. "TRYB CIEMNY" JAK W iOS: przełącznik wł/wył zamiast cyklu Jasny/Ciemny/Auto.
//    Klik = zmiana do najbliższego wschodu/zachodu słońca (DarkMode + SolarTimes).
//    Ikona "kontrast" 1:1 z iOS: koło podzielone pionowo - jedna połowa pełna,
//    druga tylko gruby obrys, oczko na środku z odwróconymi połówkami
//    (wycinane na przezroczysto). Wł = białe koło/ciemna ikona,
//    wył = ciemne szkło/biała ikona. Podpis 3-liniowy: nazwa + stan w 2 wierszach.
// 8. IKONA WIĘKSZA + ODSTĘPY TEKSTU: wewnętrzny element kontrastu ~1/2 średnicy
//    białego koła (jak na zdjęciach źródłowych), przycisk wyższy (112dp),
//    linie podpisu rozsunięte - żadna nie wchodzi na drugą ani na koło.
// 9. NIGHT SHIFT JAK W iOS: ta sama logika co "Tryb ciemny" (harmonogram
//    zachód -> wschód słońca, klik = zmiana do najbliższego wydarzenia,
//    NightShift.effectiveOn/toggle). AUTO-przejścia trwają 30 s: filtr
//    narasta/wygasa, a podświetlenie płynnie spada do minimum (włączanie)
//    albo wraca do zapamiętanej wartości (wyłączanie) - NightShiftService.
//    Suwak ciepła (przytrzymanie) jak na zdjęciu iOS: wycentrowany podpis
//    "Temperatura kolorów", niebieski tor, biały pigułkowy uchwyt,
//    punkty-pozycjonery, ZAKRES 0-50 (max = połowa obecnego maksimum ciepła).
// 10. IKONA NIGHT SHIFT = STYL SF SYMBOLS (wzorzec IMG_9097): pełne koło z
//    CIENKIM obrysem dookoła (lewa strona pusta), po PRAWEJ stronie GRUBY,
//    całkowicie wypełniony biały sierp księżyca - rogi przy godzinach 12 i 6,
//    wewnętrzna krawędź sierpa = gładkie, okrągłe wycięcie (rysowany jako
//    precyzyjna ścieżka z dwóch łuków, bez wycinania). Promienie: 8 szt.
//    co 45 stopni, CIEŃKIE (grubość = grubość obrysu koła), dłuższe,
//    smukle, z zaokrąglonymi końcami i większym marginesem od koła.
//    Odstęp podpisu od koła jak na wzorcu - baseline 78/91/104.
public class ConnectivityPanelIos extends LinearLayout {

    public interface Host {
        void close();

        void modeChanged();
    }

    private final Activity act;
    private final boolean night;
    private final Host host;
    private Tile tWifi, tBt, tHot;
    private RoundBtn bMode, bShift;
    private TextView note;
    private BroadcastReceiver rc;
    private LinearLayout warmthRow;
    private WarmthSlider warmthBar;
    private final Runnable hideWarmth = new Runnable() {
        @Override
        public void run() {
            if (warmthRow != null && warmthBar != null && !warmthBar.isPressed()) {
                warmthRow.setVisibility(GONE);
            } else {
                scheduleWarmthHide();
            }
        }
    };

    private int dp(float v) {
        return Ui.dp(act, v);
    }

    public ConnectivityPanelIos(Activity a, boolean night, Host host) {
        super(a);
        this.act = a;
        this.night = night;
        this.host = host;
        setOrientation(VERTICAL);
        // tło okna = ramka szkła doka 1:1 (rozbłysk w lewym górnym rogu, jasny
        // rim u góry, ukośna obwódka) - to samo co nakładka launchera.
        // Tylko OKNO panelu; elementy wewnątrz bez zmian.
        setBackground(new PanelGlassFrame(a, night));
        setPadding(dp(12), dp(12), dp(12), dp(10));
        setClickable(true);
        setElevation(dp(12));
        build();
    }

    private void build() {
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(HORIZONTAL);

        LinearLayout left = new LinearLayout(act);
        left.setOrientation(VERTICAL);
        LinearLayout row1 = new LinearLayout(act);
        tWifi = new Tile(act, false);
        tBt = new Tile(act, false);
        LayoutParams l1 = new LayoutParams(0, dp(150), 1f);
        l1.rightMargin = dp(6);
        LayoutParams l2 = new LayoutParams(0, dp(150), 1f);
        l2.leftMargin = dp(6);
        row1.addView(tWifi, l1);
        row1.addView(tBt, l2);
        left.addView(row1);
        tHot = new Tile(act, true);
        LayoutParams l3 = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76));
        l3.topMargin = dp(12);
        left.addView(tHot, l3);
        top.addView(left, new LayoutParams(dp(400), ViewGroup.LayoutParams.WRAP_CONTENT));

        if (Prefs.customBars(act)) {
            LinearLayout bars = new LinearLayout(act);
            bars.setOrientation(HORIZONTAL);
            PillSlider vol = new PillSlider(act, false, volumeValue());
            PillSlider bri = new PillSlider(act, true, brightnessValue());
            LayoutParams bp = new LayoutParams(dp(70), dp(238));
            bp.leftMargin = dp(10);
            bars.addView(vol, bp);
            bars.addView(bri, bp);
            vol.setListener(new PillSlider.Listener() {
                @Override
                public void on(float v) {
                    setVolume(v);
                }
            });
            bri.setListener(new PillSlider.Listener() {
                @Override
                public void on(float v) {
                    setBrightness(v);
                }
            });
            top.addView(bars);
        }
        addView(top);

        boolean showMode = Prefs.showModeButtons(act);
        boolean showNs = Prefs.showNightShift(act);
        if (showMode || showNs) {
            LinearLayout row = new LinearLayout(act);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LayoutParams rp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(12);
            if (showMode) {
                // "Tryb ciemny" jak w iOS: klik = przełącz do najbliższego
                // wschodu/zachodu słońca (potem znów rządzi słońce)
                bMode = new RoundBtn(act, 4, "Tryb ciemny");
                row.addView(bMode, new LayoutParams(dp(150), dp(112)));
                bMode.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DarkMode.toggle(act);
                        applyMode();
                    }
                });
            }
            if (showNs) {
                // Night Shift OBOK przycisku trybu (bez elasticznej przerwy)
                bShift = new RoundBtn(act, 3, "Night Shift");
                LayoutParams sp = new LayoutParams(dp(110), dp(112));
                sp.leftMargin = dp(12);
                row.addView(bShift, sp);
                bShift.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // jak "Tryb ciemny": klik = przełącz do najbliższego
                        // wschodu/zachodu słońca (potem znów rządzi słońce)
                        boolean ok = NightShift.toggle(act);
                        if (!ok) host.close();
                        update();
                    }
                });
                // PRZYTRZYMANIE Night Shift = suwak ciepła (jak w iOS)
                bShift.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showWarmthSlider();
                        return true;
                    }
                });
            }
            addView(row, rp);
            note = new TextView(act);
            note.setTextSize(13);
            note.setTextColor(night ? 0xFF9A9AA0 : 0xFF6C6C70);
            note.setPadding(dp(6), dp(2), 0, dp(4));
            addView(note);
        }
    }

    /** Suwak ciepła Night Shift (przytrzymanie przycisku, jak w iOS).
     *  Ustawia filtr NA BIEŻĄCO, chowa się po 6 s bez dotyku. */
    private void showWarmthSlider() {
        if (warmthRow != null) {
            warmthRow.setVisibility(VISIBLE);
            warmthBar.setValue(Math.max(0, Math.min(50, Prefs.nightShiftStrength(act))));
            scheduleWarmthHide();
            return;
        }
        // karta jak na zdjęciu iOS: wycentrowany podpis "Temperatura kolorów",
        // w środku "Chłodniejsze" / suwak / "Cieplejsze"
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(VERTICAL);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.setPadding(dp(14), dp(10), dp(14), dp(12));
        wrap.setBackground(Ui.round(night ? 0xFF202024 : 0x33FFFFFF, 24, act));

        TextView title = new TextView(act);
        title.setText("Temperatura kolorów");
        title.setTextSize(14);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(night ? 0xFF9A9AA0 : 0xFF6C6C70);
        wrap.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(6);

        TextView cool = new TextView(act);
        cool.setText("Chłodniejsze");
        cool.setTextSize(15);
        cool.setTextColor(night ? 0xFFFFFFFF : 0xFF111114);
        row.addView(cool, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        warmthBar = new WarmthSlider(act);
        warmthBar.setValue(Math.max(0, Math.min(50, Prefs.nightShiftStrength(act))));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        bp.leftMargin = dp(10);
        bp.rightMargin = dp(10);
        row.addView(warmthBar, bp);

        TextView warm = new TextView(act);
        warm.setText("Cieplejsze");
        warm.setTextSize(15);
        warm.setTextColor(night ? 0xFFFFFFFF : 0xFF111114);
        row.addView(warm, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        wrap.addView(row, rp);
        warmthRow = wrap;
        warmthBar.setListener(new WarmthSlider.Listener() {
            @Override
            public void on(int v) {
                Prefs.setNightShiftStrength(act, Math.max(0, Math.min(50, v)));
                NightShift.refresh(act);
            }
        });

        addView(wrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scheduleWarmthHide();
    }

    private void scheduleWarmthHide() {
        removeCallbacks(hideWarmth);
        postDelayed(hideWarmth, 6000);
    }

    private void applyMode() {
        host.modeChanged();
        update();
    }

    private void toggleBt() {
        String mode = Prefs.btAction(act);
        if ("app".equals(mode)) {
            Net.openBluetoothApp(act);
            host.close();
            return;
        }
        final boolean before = Net.bluetoothOn();
        boolean ok = Net.setBluetooth(!before);
        if (!ok) {
            if ("auto".equals(mode)) {
                Net.openBluetoothApp(act);
            } else {
                Net.openBluetoothSettings(act);
            }
            host.close();
            return;
        }
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (Net.bluetoothOn() == before && isAttachedToWindow()) {
                    Net.openBluetoothApp(act);   // radio przejmuje Bluetooth: otworz jego aplikacje
                    host.close();
                }
            }
        }, 2500);
        update();
    }

    // ---------------------------------------------------------------- głośność / jasność
    private float volumeValue() {
        try {
            AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            return max <= 0 ? 0f : am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
        } catch (Throwable t) {
            return 0.5f;
        }
    }

    private void setVolume(float v) {
        try {
            AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max), 0);
        } catch (Throwable ignored) {
        }
    }

    private float brightnessValue() {
        try {
            return Settings.System.getInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
        } catch (Throwable t) {
            return 0.7f;
        }
    }

    private void setBrightness(float v) {
        try {
            if (!Settings.System.canWrite(act)) {
                // jednorazowa zgoda na zmianę ustawień systemu
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:" + act.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(i);
                host.close();
                return;
            }
            Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    Math.max(10, Math.round(v * 255)));
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- stan
    public void update() {
        boolean wEn = Net.wifiEnabled(act);
        int ws = Net.wifiState(act);
        boolean bt = Net.bluetoothOn();
        boolean th = Net.tethering(act);
        int white = 0xFFFFFFFF;
        // ikony 1:1 jak w oryginale (wektory ic_wifi / ic_wifi_off / ic_bt)
        // podpis Wi-Fi: sama nazwa sieci (SSID); gdy system jej nie da - "Połączono"
        String ssid = Net.wifiSsid(act);
        tWifi.set(wEn, 0xFF1C94FF, Icons.vector(act, wEn ? R.drawable.ic_wifi : R.drawable.ic_wifi_off,
                128, white), "Wi-Fi",
                (ws == 3 || ws == 2) ? (ssid.length() > 0 ? ssid : "Połączono")
                        : (ws == 1 ? "Włączone" : "Wyłączone"));
        // podpis Bluetooth: nazwy podłączonych urządzeń, po przecinku
        StringBuilder btSub = new StringBuilder();
        for (String n : BtDevice.connectedNames()) {
            if (btSub.length() > 0) btSub.append(", ");
            btSub.append(n);
        }
        tBt.set(bt, 0xFF1C94FF, Icons.vector(act, R.drawable.ic_bt, 128, white), "Bluetooth",
                btSub.length() > 0 ? btSub.toString() : (bt ? "Włączony" : "Wyłączony"));
        tHot.set(th, 0xFF30D158, Icons.tether(128, white, false), "Hotspot osobisty",
                th ? "Włączony" : "Wyłączony");
        if (bMode != null) {
            // "Tryb ciemny" jak w iOS: stan + podpis "do wsch./zach. słońca"
            boolean on = DarkMode.isNight(act);
            bMode.setKind(4, "Tryb ciemny");
            bMode.setSub(DarkMode.label(act));
            bMode.setActive(on);
        }
        if (bShift != null) {
            // Night Shift jak w iOS: stan + podpis "do wsch./zach. słońca"
            bShift.setActive(NightShift.effectiveOn(act));
            bShift.setSub(NightShift.label(act));
        }
        if (note != null) {
            // stan trybu jest teraz w podpisie przycisku "Tryb ciemny"
            note.setVisibility(GONE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        rc = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                update();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        f.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        f.addAction("android.net.conn.TETHER_STATE_CHANGED");
        try {
            act.registerReceiver(rc, f);
        } catch (Throwable ignored) {
        }
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            if (rc != null) act.unregisterReceiver(rc);
        } catch (Throwable ignored) {
        }
        rc = null;
        removeCallbacks(hideWarmth);
    }

    // ---------------------------------------------------------------- kafelek
    private class Tile extends View {
        private final boolean wide;
        private boolean active;
        private int color = 0xFF1C94FF;
        private Bitmap icon;
        private String title = "";
        private String sub = "";
        private boolean pressed;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d = getResources().getDisplayMetrics().density;

        Tile(Context c, boolean wide) {
            super(c);
            this.wide = wide;
        }

        void set(boolean active, int color, Bitmap icon, String title, String sub) {
            this.active = active;
            this.color = color;
            this.icon = icon;
            this.title = title;
            this.sub = sub;
            setContentDescription(title + ", " + sub);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float hh = getHeight();
            float rad = wide ? hh / 2f : 34f * d;
            // SZKŁO: półprzezroczysty korpus (tło prześwituje) + światło Z GÓRY
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(night ? 0x9E4E4E53 : 0xCCFFFFFF);
            r.set(0, 0, w, hh);
            cv.drawRoundRect(r, rad, rad, p);
            int saveG = cv.save();
            Path clipG = new Path();
            clipG.addRoundRect(r, rad, rad, Path.Direction.CW);
            cv.clipPath(clipG);
            p.setShader(new LinearGradient(0, 0, 0, hh * 0.45f,
                    new int[]{0x3DFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(r, p);
            cv.restoreToCount(saveG);
            p.setShader(null);
            if (pressed) {
                p.setColor(0x22FFFFFF);
                cv.drawRoundRect(r, rad, rad, p);
            }
            // rant: pionowy gradient (jasny u gory, wygaszony u dolu)
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f * d);
            p.setShader(new LinearGradient(0, 0, 0, hh,
                    night ? new int[]{0x99FFFFFF, 0x26FFFFFF}
                          : new int[]{0x66808090, 0x1A808090},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            r.set(d, d, w - d, hh - d);
            cv.drawRoundRect(r, rad - d, rad - d, p);
            p.setShader(null);

            float cd = 50f * d;
            float cx = wide ? 20f * d + cd / 2f : 18f * d + cd / 2f;
            float cy = wide ? hh / 2f : 18f * d + cd / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(active ? color : 0xFF7E8686);
            cv.drawCircle(cx, cy, cd / 2f, p);
            if (icon != null) {
                float is = 28f * d;
                r.set(cx - is / 2f, cy - is / 2f, cx + is / 2f, cy + is / 2f);
                cv.drawBitmap(icon, null, r, p);
            }
            float sd = getResources().getDisplayMetrics().scaledDensity;
            int tc = night ? 0xFFFFFFFF : 0xFF111114;
            int sc = night ? 0xFFC9CFCF : 0xFF6C6C70;
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.LEFT);
            if (wide) {
                float tx = 20f * d + cd + 14f * d;
                p.setColor(tc);
                p.setTextSize(19f * sd);
                cv.drawText(title, tx, hh / 2f - 4f * d, p);
                p.setTypeface(Typeface.DEFAULT);
                p.setColor(sc);
                p.setTextSize(16f * sd);
                cv.drawText(sub, tx, hh / 2f + 20f * d, p);
            } else {
                p.setColor(tc);
                p.setTextSize(19f * sd);
                cv.drawText(title, 18f * d, hh - 40f * d, p);
                p.setTypeface(Typeface.DEFAULT);
                p.setColor(sc);
                p.setTextSize(15f * sd);
                cv.drawText(sub, 18f * d, hh - 16f * d, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                pressed = true;
                invalidate();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                pressed = false;
                invalidate();
            }
            return super.onTouchEvent(e);
        }
    }

    // ---------------------------------------------------------------- okrągły przycisk z podpisem
    // kind: 0 jasny (słońce), 1 ciemny (księżyc), 2 auto (A), 3 Night Shift,
    //       4 tryb ciemny (ikona "kontrast", 2 linie podpisu)
    private class RoundBtn extends View {
        private int kind;
        private String label;
        private String sub;
        private boolean active;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d = getResources().getDisplayMetrics().density;

        RoundBtn(Context c, int kind, String label) {
            super(c);
            this.kind = kind;
            this.label = label;
            setContentDescription(label);
            this.active = true;
        }

        void setActive(boolean a) {
            active = a;
            invalidate();
        }

        void setKind(int k, String lbl) {
            kind = k;
            label = lbl;
            setContentDescription(lbl);
            invalidate();
        }

        void setSub(String s) {
            sub = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float cx = w / 2f;
            float cy = 34f * d;
            float rad = 28f * d;
            // SZKŁO: nieaktywny = półprzezroczysty + światło Z GÓRY; aktywny = pełny kolor
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(active ? (kind == 3 ? 0xFFFF9F0A
                            : (kind == 4 ? 0xFFFFFFFF : 0xFF1C94FF))
                    : (night ? 0x9E4E4E53 : 0xCCFFFFFF));
            cv.drawCircle(cx, cy, rad, p);
            if (!active) {
                int saveG = cv.save();
                Path clipG = new Path();
                clipG.addCircle(cx, cy, rad, Path.Direction.CW);
                cv.clipPath(clipG);
                p.setShader(new LinearGradient(0, cy - rad, 0, cy + rad * 0.2f,
                        new int[]{0x3DFFFFFF, 0x00FFFFFF},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                cv.drawRect(cx - rad, cy - rad, cx + rad, cy + rad, p);
                cv.restoreToCount(saveG);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.4f * d);
                p.setShader(new LinearGradient(0, cy - rad, 0, cy + rad,
                        night ? new int[]{0x99FFFFFF, 0x26FFFFFF}
                              : new int[]{0x66808090, 0x1A808090},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, cy, rad - d * 0.7f, p);
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
            }
            int ink = kind == 4 ? (active ? 0xFF1C1C1E : 0xFFFFFFFF)
                    : (active ? 0xFFFFFFFF : (night ? 0xFFFFFFFF : 0xFF3C3C43));
            p.setColor(ink);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.6f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            if (kind == 4) {
                // "kontrast" 1:1 z iOS: koło podzielone PIONOWO na pół -
                // jedna połowa pełna, druga tylko gruby obrys; oczko na środku
                // z połówkami ODWRÓCONYMI względem koła (wycinane na przezroczysto).
                // Wewnętrzny element ~1/2 średnicy białego koła (zdjęcia źródłowe):
                // koło ma 56dp, element 28dp (R=14d).
                float R = 14f * d;
                float Ro = R - 1.3f * d;      // obrys mieści się w tym samym kole
                float rd = 6.2f * d;          // oczko (~45% elementu, jak w iOS)
                boolean fullLeft = active;    // wył: pełna prawa; wł: pełna lewa
                int sc4 = cv.saveLayer(cx - R - d, cy - R - d, cx + R + d, cy + R + d, null);
                p.setXfermode(null);
                // pełna połówka
                Path full = new Path();
                full.addArc(cx - R, cy - R, cx + R, cy + R, fullLeft ? 90f : -90f, 180f);
                full.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(full, p);
                // gruby obrys drugiej połówki
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.6f * d);
                cv.drawArc(cx - Ro, cy - Ro, cx + Ro, cy + Ro, fullLeft ? -90f : 90f, 180f,
                        false, p);
                // oczko: połówka wypełniona (odwrotna niż pełna połowa koła)
                Path dotF = new Path();
                dotF.addArc(cx - rd, cy - rd, cx + rd, cy + rd, fullLeft ? -90f : 90f, 180f);
                dotF.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(dotF, p);
                // oczko: połówka wycinana na przezroczysto (prześwituje tło przycisku)
                Paint clearP = new Paint(Paint.ANTI_ALIAS_FLAG);
                clearP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                Path dotC = new Path();
                dotC.addArc(cx - rd, cy - rd, cx + rd, cy + rd, fullLeft ? 90f : -90f, 180f);
                dotC.close();
                cv.drawPath(dotC, clearP);
                cv.restoreToCount(sc4);
            } else if (kind == 3) {
                // Night Shift 1:1 z iOS (SF Symbols, wzorzec IMG_9097):
                // pełne koło = CIENKI obrys dookoła (lewa strona pusta);
                // po PRAWEJ stronie GRUBY, całkowicie wypełniony biały
                // sierp księżyca - rogi przy godzinach 12 i 6, wewnętrzna
                // krawędź = gładkie, okrągłe wycięcie (ścieżka z dwóch łuków,
                // bez wycinania - koniec ze "zlaną plamą"). Promienie: 8 szt.
                // co 45 stopni, CIEŃKIE (grubosć obrysu), dłuższe, smukłe,
                // zaokrąglone końce, większy margines od koła.
                float R = 11f * d;
                float lw = 1.6f * d;          // grubość cienkiej linii (obrys = promienie)
                // cienki obrys całego koła
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(lw);
                cv.drawCircle(cx, cy, R - lw / 2f, p);
                // sierp: łuk zewnętrzny po prawej (12 -> 6) + łuk wewnętrzny
                // (gładkie okrągłe wycięcie) z powrotem do góry
                Path crescent = new Path();
                crescent.moveTo(cx, cy - R);
                crescent.arcTo(new RectF(cx - R, cy - R, cx + R, cy + R), -90f, 180f);
                float xb = -5.14f * d;        // środek koła wycięcia (na osi poziomej)
                float rb = 12.14f * d;        // promień wycięcia - gładka krawędź
                crescent.arcTo(new RectF(cx + xb - rb, cy - rb, cx + xb + rb, cy + rb),
                        65f, -130f);
                crescent.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(crescent, p);
                // promienie: cienkie jak obrys, dalej od koła, zaokrąglone końce
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(lw);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    cv.drawLine(cx + (float) Math.cos(a) * 13.5f * d,
                            cy + (float) Math.sin(a) * 13.5f * d,
                            cx + (float) Math.cos(a) * 18f * d,
                            cy + (float) Math.sin(a) * 18f * d, p);
                }
            } else if (kind == 0) {
                cv.drawCircle(cx, cy, 6f * d, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    cv.drawLine(cx + (float) Math.cos(a) * 10f * d, cy + (float) Math.sin(a) * 10f * d,
                            cx + (float) Math.cos(a) * 14f * d, cy + (float) Math.sin(a) * 14f * d, p);
                }
            } else if (kind == 1) {
                p.setStyle(Paint.Style.FILL);
                Path moon = new Path();
                moon.addCircle(cx, cy, 11f * d, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(cx + 6f * d, cy - 4f * d, 10f * d, Path.Direction.CW);
                moon.op(cut, Path.Op.DIFFERENCE);
                cv.drawPath(moon, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(22f * getResources().getDisplayMetrics().scaledDensity);
                cv.drawText("A", cx, cy + 8f * d, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            float sd = getResources().getDisplayMetrics().scaledDensity;
            if (sub != null) {
                // trzy linie jak w iOS: nazwa (bold) + stan w dwóch wierszach;
                // odstęp nazwy od koła WIĘKSZY - dokładnie jak na wzorcu
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(12f * sd);
                p.setColor(night ? 0xFFFFFFFF : 0xFF111114);
                cv.drawText(label, cx, 78f * d, p);
                p.setTypeface(Typeface.DEFAULT);
                p.setTextSize(10.5f * sd);
                p.setColor(night ? 0xFFC9CFCF : 0xFF6C6C70);
                int di = sub.indexOf(" do ");
                if (di > 0) {
                    cv.drawText(sub.substring(0, di + 3), cx, 91f * d, p);
                    cv.drawText(sub.substring(di + 4), cx, 104f * d, p);
                } else {
                    cv.drawText(sub, cx, 91f * d, p);
                }
            } else {
                p.setTypeface(Typeface.DEFAULT);
                p.setColor(night ? 0xFFFFFFFF : 0xFF111114);
                p.setTextSize(13f * sd);
                cv.drawText(label, cx, 84f * d, p);
            }
        }
    }

    // ---------------------------------------------------------------- pionowy suwak
    public static class PillSlider extends View {
        public interface Listener {
            void on(float v);
        }

        private float value;
        private final boolean sun;
        private final boolean night;
        private Listener l;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d = getResources().getDisplayMetrics().density;

        public PillSlider(Context c, boolean sun, float v) {
            super(c);
            this.sun = sun;
            this.value = Math.max(0f, Math.min(1f, v));
            this.night = Look.night(c);
            setContentDescription(sun ? "Jasność ekranu" : "Głośność");
        }

        public void setListener(Listener l) {
            this.l = l;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float rad = w / 2f;
            // SZKŁO: półprzezroczysty tor + pas światła Z GÓRY + pionowy rant
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(night ? 0x9E4E4E53 : 0xCCD1D1D6);
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, rad, rad, p);
            int save = cv.save();
            Path clip = new Path();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            cv.clipPath(clip);
            p.setShader(new LinearGradient(0, 0, 0, h * 0.35f,
                    new int[]{0x3DFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(r, p);
            p.setShader(null);
            p.setColor(0xE6FAFAFC);
            cv.drawRect(0, h * (1f - value), w, h, p);
            cv.restoreToCount(save);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f * d);
            p.setShader(new LinearGradient(0, 0, 0, h,
                    night ? new int[]{0x99FFFFFF, 0x26FFFFFF}
                          : new int[]{0x66808090, 0x1A808090},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            r.set(d * 0.7f, d * 0.7f, w - d * 0.7f, h - d * 0.7f);
            cv.drawRoundRect(r, rad - d * 0.7f, rad - d * 0.7f, p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            float cx = w / 2f;
            float cy = h - 34f * d;
            // IKONY 1:1 z iOS (zdjęcie usera): słońce #FFD700, głośnik #1EA1FF
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            if (sun) {
                // słońce: wypełnione koło + 8 cienkich prostych promieni
                p.setColor(0xFFFFD700);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy, 6.5f * d, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.2f * d);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    cv.drawLine(cx + (float) Math.cos(a) * 10.5f * d, cy + (float) Math.sin(a) * 10.5f * d,
                            cx + (float) Math.cos(a) * 14.5f * d, cy + (float) Math.sin(a) * 14.5f * d, p);
                }
            } else {
                // głośnik: wypełniony korpus z "uszami" po lewej + 2 łuki fal (kontur)
                p.setColor(0xFF1EA1FF);
                p.setStyle(Paint.Style.FILL_AND_STROKE);
                p.setStrokeWidth(2f * d);
                Path sp = new Path();
                sp.moveTo(cx - 11f * d, cy - 4.5f * d);
                sp.lineTo(cx - 4f * d, cy - 4.5f * d);
                sp.lineTo(cx + 3f * d, cy - 10.5f * d);
                sp.lineTo(cx + 3f * d, cy + 10.5f * d);
                sp.lineTo(cx - 4f * d, cy + 4.5f * d);
                sp.lineTo(cx - 11f * d, cy + 4.5f * d);
                sp.close();
                cv.drawPath(sp, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.2f * d);
                cv.drawArc(new RectF(cx - 1f * d, cy - 5f * d, cx + 9f * d, cy + 5f * d), -48, 96,
                        false, p);
                cv.drawArc(new RectF(cx - 5.5f * d, cy - 9.5f * d, cx + 13.5f * d, cy + 9.5f * d), -48, 96,
                        false, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE
                    || a == MotionEvent.ACTION_UP) {
                value = Math.max(0f, Math.min(1f, 1f - e.getY() / getHeight()));
                invalidate();
                if (l != null && (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_MOVE)) {
                    l.on(value);
                }
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- suwak ciepła (poziomy)
    // 1:1 ze zdjęcia iOS ("Temperatura kolorów"): niebieski tor wypełnienia,
    // biały pigułkowy uchwyt, dwa małe punkty-pozycjonery pod torem.
    // ZAKRES 0-50: maksimum = połowa obecnego maksimum ciepła (decyzja usera).
    // Klasa STATYCZNA (jak PillSlider): w niestatycznej klasie wewnętrznej
    // nie wolno deklarować interfejsu (javac: "Illegal static declaration").
    private static class WarmthSlider extends View {
        interface Listener {
            void on(int v);
        }

        private int value;
        private final boolean night;
        private Listener l;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d = getResources().getDisplayMetrics().density;

        WarmthSlider(Context c) {
            super(c);
            this.night = Look.night(c);
            setContentDescription("Temperatura kolorów");
        }

        void setValue(int v) {
            value = Math.max(0, Math.min(50, v));
            invalidate();
        }

        void setListener(Listener l) {
            this.l = l;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float trackY = h * 0.40f;
            float trackH = 7f * d;
            float pad = 20f * d;   // miejsce na pigułkę na końcach toru
            float trackW = w - pad * 2f;
            // tor: niewypełniona część (ciemna)
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(night ? 0x2EFFFFFF : 0x22000000);
            r.set(pad, trackY - trackH / 2f, pad + trackW, trackY + trackH / 2f);
            cv.drawRoundRect(r, trackH / 2f, trackH / 2f, p);
            // wypełnienie niebieskie do uchwytu
            float frac = value / 50f;
            if (frac > 0f) {
                p.setColor(0xFF0A84FF);
                r.set(pad, trackY - trackH / 2f, pad + trackW * frac, trackY + trackH / 2f);
                cv.drawRoundRect(r, trackH / 2f, trackH / 2f, p);
            }
            // dwa punkty-pozycjonery pod torem (jak w iOS)
            p.setColor(night ? 0x55FFFFFF : 0x44000000);
            cv.drawCircle(pad + trackW * 0.10f, trackY + 9f * d, 1.6f * d, p);
            cv.drawCircle(pad + trackW * 0.50f, trackY + 9f * d, 1.6f * d, p);
            // biały pigułkowy uchwyt
            float tx = pad + trackW * frac;
            p.setColor(0xFFFFFFFF);
            r.set(tx - 17f * d, trackY - 12f * d, tx + 17f * d, trackY + 12f * d);
            cv.drawRoundRect(r, 12f * d, 12f * d, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE
                    || a == MotionEvent.ACTION_UP) {
                float pad = 20f * d;
                float trackW = getWidth() - pad * 2f;
                float frac = trackW <= 0 ? 0f : (e.getX() - pad) / trackW;
                int v = Math.round(Math.max(0f, Math.min(1f, frac)) * 50f);
                if (v != value) {
                    value = v;
                    invalidate();
                }
                if (l != null && (a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP)) {
                    l.on(value);
                }
            }
            return true;
        }
    }
}
        private final float d = getResources().getDisplayMetrics().density;

        WarmthSlider(Context c) {
            super(c);
            this.night = Look.night(c);
            setContentDescription("Temperatura kolorów");
        }

        void setValue(int v) {
            value = Math.max(0, Math.min(50, v));
            invalidate();
        }

        void setListener(Listener l) {
            this.l = l;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float trackY = h * 0.40f;
            float trackH = 7f * d;
            float pad = 20f * d;   // miejsce na pigułkę na końcach toru
            float trackW = w - pad * 2f;
            // tor: niewypełniona część (ciemna)
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(night ? 0x2EFFFFFF : 0x22000000);
            r.set(pad, trackY - trackH / 2f, pad + trackW, trackY + trackH / 2f);
            cv.drawRoundRect(r, trackH / 2f, trackH / 2f, p);
            // wypełnienie niebieskie do uchwytu
            float frac = value / 50f;
            if (frac > 0f) {
                p.setColor(0xFF0A84FF);
                r.set(pad, trackY - trackH / 2f, pad + trackW * frac, trackY + trackH / 2f);
                cv.drawRoundRect(r, trackH / 2f, trackH / 2f, p);
            }
            // dwa punkty-pozycjonery pod torem (jak w iOS)
            p.setColor(night ? 0x55FFFFFF : 0x44000000);
            cv.drawCircle(pad + trackW * 0.10f, trackY + 9f * d, 1.6f * d, p);
            cv.drawCircle(pad + trackW * 0.50f, trackY + 9f * d, 1.6f * d, p);
            // biały pigułkowy uchwyt
            float tx = pad + trackW * frac;
            p.setColor(0xFFFFFFFF);
            r.set(tx - 17f * d, trackY - 12f * d, tx + 17f * d, trackY + 12f * d);
            cv.drawRoundRect(r, 12f * d, 12f * d, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE
                    || a == MotionEvent.ACTION_UP) {
                float pad = 20f * d;
                float trackW = getWidth() - pad * 2f;
                float frac = trackW <= 0 ? 0f : (e.getX() - pad) / trackW;
                int v = Math.round(Math.max(0f, Math.min(1f, frac)) * 50f);
                if (v != value) {
                    value = v;
                    invalidate();
                }
                if (l != null && (a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP)) {
                    l.on(value);
                }
            }
            return true;
        }
    }
}
