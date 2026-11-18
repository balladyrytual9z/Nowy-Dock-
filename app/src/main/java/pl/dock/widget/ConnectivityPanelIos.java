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
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;

// Centrum łacznosci w stylu iOS - port 1:1 z Liquid-Glass-Diplay---Launcher-Switcher-.
// Ikony Wi-Fi/Bluetooth/Hotspot DOKŁADNIE jak w oryginale (Icons.vector na
// ic_wifi/ic_wifi_off/ic_bt + Icons.tether). Wymiary oryginalne: kafelki 150/76dp,
// suwaki 70x238dp, przyciski 88x96dp, lewa kolumna 400dp.
// Zmiany na życzenie (i NIC więcej):
// 1. Jasny/Ciemny/Auto w JEDNYM przycisku (klik = cykl trybów).
// 2. Night Shift: klik = wl/wyl, PRZYTRZYMANIE = suwak ciepła (jak w iOS).
// 3. Tło panelu = PanelGlassFrame (ramka szkła doka 1:1, jak w nakładce).
// 4. NAPRAWA UCIĘCIA: szerokość okna MATCH_PARENT (launcher 0838be52,
//    ConnectivityActivity 5c03c92a) - WRAP_CONTENT zawala łańcuch MATCH_PARENT.
// 5. SZKŁO (jak w iOS): kafelki, przyciski i suwaki półprzezroczyste,
//    światło TYLKO Z GÓRY (pionowe gradienty).
// 6. IKONY SUWAKÓW 1:1 ze zdjęcia iOS: słońce #FFD700 (koło + 8 cienkich
//    promieni), głośnik #1EA1FF (korpus z "uszami" + 2 łuki fal konturem).
// 7. "TRYB CIEMNY" JAK W iOS: przełącznik wł/wył zamiast cyklu Jasny/Ciemny/Auto.
//    Klik = zmiana do najbliższego wschodu/zachodu słońca (DarkMode + SolarTimes).
//    Ikona "kontrast" 1:1 z iOS. Podpis 3-liniowy: nazwa + stan w 2 wierszach.
// 8. IKONA WIĘKSZA + ODSTĘPY TEKSTU: wewnętrzny element kontrastu ~1/2 średnicy
//    białego koła, przycisk wyższy (112dp), linie podpisu rozsunięte.
// 9. NIGHT SHIFT JAK W iOS: ta sama logika co "Tryb ciemny" (harmonogram
//    zachód -> wschód słońca, klik = zmiana do najbliższego wydarzenia,
//    NightShift.effectiveOn/toggle). AUTO-przejścia trwają 30 s - NightShiftService.
//    Suwak ciepła (przytrzymanie) jak na zdjęciu iOS, ZAKRES 0-50.
// 10. IKONA NIGHT SHIFT = STYL SF SYMBOLS (wzorzec IMG_9097).
// 11. JEDEN PANEL WSZĘDZIE: ta klasa jest JEDYNYM panelem łączności -
//    LauncherActivity też jej używa (zamiast starego ConnectivityPanel).
//    Warstwa SOFTWARE na API < 30 (Mali-T720 / Android 9): bez niej w
//    animowanej overlay tresc panelu potrafi sie nie wyrenderowac
//    (lekcja z ConnectivityPanel, PR #14). Warstwa TYLKO na panelu -
//    NIGDY na overlayu (dwie zagniezdzone warstwy = pusty panel, PR #46).
// 12. UKŁAD KOMPAKTOWY (14.11/15.11.2026, commity c5da85a..): kolejność pasków
//    Wi-Fi -> Hotspot -> Bluetooth. Kafelki STAŁA szerokość 196dp (naturalne
//    ~150dp z oryginału iOS + ~30%, zyczenie "30 procent szersze"), rogi 16dp
//    (jak karty launchera, Ui.cardBg), wysokosc 56dp = srednica kola przyciskow
//    trybow. Suwaki głośności/jasności 56dp SZEROKIE (= srednica kola
//    przyciskow, zyczenie usera) x 188dp wysokie, BEZ przerwy zaraz za
//    kafelkami (margines 10dp jak miedzy paskami). Caly wiersz content jest
//    WRAP_CONTENT i okno panelu tez WRAP_CONTENT (oba hosty) - szklo obejmuje
//    DOKLADNIE tresc, bez pustego pola po prawej i na dole (zyczenie usera).
//    UWAGA: wczesniejszy MATCH_PARENT okna (0838be52/5c03c92a) byl potrzebny,
//    bo kafelki byly MATCH_PARENT - ten lancuch juz nie istnieje, wiec
//    WRAP_CONTENT nie zapada okna.
// 13. PRZERWA SUWAKI<->KAFELKI USUNIĘTA (15.11.2026): wiersz przycisków trybów
//    (Tryb ciemny / Night Shift, 2x88dp) rozpychał lewą kolumnę do 188dp i
//    tworzył ~66dp pustej przerwy między suwakami a kafelkami. Przyciski idą
//    teraz OSOBNO pod cały wiersz content (wyrównane do lewej - wizualnie w
//    tym samym miejscu), lewa kolumna = tylko suwaki (122dp), kafelki zaraz
//    za nimi (10dp), szerokość panelu wraca do ~352dp.
public class ConnectivityPanelIos extends LinearLayout {

    public interface Host {
        void close();

        void modeChanged();
    }

    /** Jedno obcinanie tekstu do szerokosci kafelka/przycisku (TASK-1):
     *  dlugi SSID, "Hotspot osobisty" czy "Night Shift" nie moga wychodzic
     *  poza swoj prostokat w siatce 120dp. */
    private static String fitText(Paint paint, String s, float maxW) {
        if (s == null) return "";
        if (maxW <= 0f) return "";
        try {
            if (paint.measureText(s) <= maxW) return s;
        } catch (Throwable ignored) {
            return s;
        }
        final String ell = "…";
        int n = s.length();
        while (n > 0) {
            n--;
            String cut = s.substring(0, n) + ell;
            try {
                if (paint.measureText(cut) <= maxW) return cut;
            } catch (Throwable ignored) {
                return s;
            }
        }
        return "";
    }

    private final Activity act;
    private boolean night;   // nie-final: panel reaguje na zmiane trybu w otwartej nakladce
    private PillSlider briSlider; // suwak jasnosci - do wymuszenia minimum przy Night Shift
    private final Host host;
    private Tile tWifi, tBt, tHot;
    private RoundBtn bMode, bShift;
    private TextView note;
    private BroadcastReceiver rc;
    private LinearLayout warmthRow;
    private WarmthSlider sb;
    private boolean buttonsVisible = false;   // przyciski ukryte domyslnie, pojawiaja sie po long-press na suwaku jasnosci
    private LinearLayout controls;            // wiersz przyciskow trybow (dostepny z toggleButtons)
    private final Runnable hideWarmth = new Runnable() {
        @Override
        public void run() {
            if (warmthRow != null && sb != null && !sb.isPressed()) {
                warmthRow.setVisibility(GONE);
            } else {
                scheduleWarmthHide();
            }
        }
    };

    private int dp(float v) {
        // Cały panel powiększony o 20% (1.2x) na życzenie użytkownika.
        return Ui.dp(act, v * 1.2f);
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
        // Warstwa SOFTWARE na starym API (Mali-T720 / Android 9) - patrz nagłówek (11).
        if (android.os.Build.VERSION.SDK_INT < 30) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        setElevation(dp(12));
        // Klik w puste miejsce panelu: zamyka regulację Night Shift (warmth slider).
        // DOTYK SUWAKÓW NIE JEST BLOKOWANY - PillSlider ma wlasny onTouchEvent z
        // requestDisallowInterceptTouchEvent, wiec zdarzenia docieraja do dzieci.
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN && warmthRow != null
                        && warmthRow.getVisibility() == VISIBLE) {
                    warmthRow.setVisibility(GONE);
                    removeCallbacks(hideWarmth);
                    return true;
                }
                return false;
            }
        });
        build();
    }

    /** Zmiana trybu dzien/noc w OTWARTYM panelu (host wywoluje z modeChanged):
     *  nowa ramka szkla + pelna przebudowa tresci w nowych kolorach, potem
     *  odswiezenie stanow radii/przyciskow. Dzieki temu panel zmienia sie
     *  razem z tlem i dokiem, a nie tylko tlo pod nim. */
    public void setNight(boolean n) {
        if (this.night == n) return;
        this.night = n;
        try {
            setBackground(new PanelGlassFrame(act, night));
        } catch (Throwable ignored) {}
        removeAllViews();
        warmthRow = null;
        sb = null;
        build();
        update();
    }

    private void build() {
        // LEWA kolumna: 3 paski lacznosci + wiersz przyciskow trybow.
        // PRAWA kolumna: pionowe suwaki glosnosci i jasnosci.
        // Kazdy pasek: STALA szerokosc 196dp (oryginalne ~150dp kafelkow iOS
        // + ~30% - zyczenie usera) x 56dp. WYSOKOSC 56dp = srednica kola
        // przyciskow trybow (RoundBtn: promien 28dp). Odstep 10dp.
        // KOLEJNOSC: Wi-Fi, Hotspot (tethering), Bluetooth.
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(VERTICAL);

        tWifi = new Tile(act);
        LayoutParams l1 = new LayoutParams(dp(196), dp(56));
        l1.bottomMargin = dp(10);
        top.addView(tWifi, l1);

        tHot = new Tile(act);
        LayoutParams l2 = new LayoutParams(dp(196), dp(56));
        l2.bottomMargin = dp(10);
        top.addView(tHot, l2);

        tBt = new Tile(act);
        LayoutParams l3 = new LayoutParams(dp(196), dp(56));
        l3.bottomMargin = dp(10);
        top.addView(tBt, l3);

        // Wiersz przyciskow trybow na dole panelu (wycentrowane wzgledem calej szerokosci).
        // Przyciski sa UKRYTE domyslnie - pojawiaja sie dopiero po long-press na suwaku jasnosci.
        boolean showMode = Prefs.showModeButtons(act);
        boolean showNs = Prefs.showNightShift(act);
        LinearLayout controls = null;
        LayoutParams rp = null;
        if (showMode || showNs) {
            this.controls = new LinearLayout(act);
            this.controls.setGravity(Gravity.CENTER);
            this.controls.setVisibility(GONE);
            rp = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(10);
            if (showMode) {
                bMode = new RoundBtn(act, 4, "Tryb ciemny");
                LayoutParams mp = new LayoutParams(dp(88), dp(96));
                if (showNs) mp.rightMargin = dp(12);
                this.controls.addView(bMode, mp);
                bMode.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DarkMode.toggle(act);
                        applyMode();
                    }
                });
            }
            if (showNs) {
                bShift = new RoundBtn(act, 3, "Night Shift");
                this.controls.addView(bShift, new LayoutParams(dp(88), dp(96)));
                bShift.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean ok = NightShift.toggle(act);
                        if (!ok) host.close();
                        update();
                    }
                });
                bShift.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showWarmthSlider();
                        return true;
                    }
                });
            }
        }

        // Zewnetrzny wiersz: SUWAKI po lewej, KAFELKI po prawej - zaraz za
        // suwakami, bez pustej przerwy (10dp między kolumnami, jak między paskami).
        LinearLayout content = new LinearLayout(act);
        content.setOrientation(HORIZONTAL);

        // Lewa kolumna: tylko suwaki (przyciski trybów idą osobno pod cały wiersz)
        LinearLayout leftColumn = new LinearLayout(act);
        leftColumn.setOrientation(VERTICAL);

        if (Prefs.customBars(act)) {
            LinearLayout bars = new LinearLayout(act);
            bars.setOrientation(HORIZONTAL);
            PillSlider vol = new PillSlider(act, false, volumeValue());
            PillSlider bri = new PillSlider(act, true, brightnessValue());
            briSlider = bri; // zapisz ref. do wymuszenia minimum przy Night Shift
            LinearLayout.LayoutParams vbp = new LinearLayout.LayoutParams(dp(56), dp(188));
            bars.addView(vol, vbp);
            LinearLayout.LayoutParams bbp = new LinearLayout.LayoutParams(dp(56), dp(188));
            bbp.leftMargin = dp(10);
            bars.addView(bri, bbp);
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
            LinearLayout.LayoutParams lpBars = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lpBars.bottomMargin = dp(10);
            leftColumn.addView(bars, lpBars);
        }

        LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lpLeft.rightMargin = dp(10);   // przerwa miedzy suwakami a kafelkami lacznosci (jak miedzy kafelkami Wi-Fi/hotspot)
        content.addView(leftColumn, lpLeft);

        // Prawa kolumna: kafelki lacznosci (pionowo)
        content.addView(top, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(content, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Przyciski trybów POD CAŁYM wierszem content (wyrównane do lewej):
        // wizualnie w tym samym miejscu co wcześniej (pod suwakami), ale ich
        // szerokość (2x88dp) nie rozpycha lewej kolumny i nie tworzy pustej
        // przerwy między suwakami a kafelkami.
        if (controls != null && rp != null) {
            addView(controls, rp);
        }

        // akcje kafelków (przywrócone ze starego panelu): tap = przełącz / ekran systemowy
        tWifi.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean before = Net.wifiEnabled(act);
                if (!Net.setWifi(act, !before)) {
                    // radio producenta potrafi blokować przełączanie: wtedy ustawienia
                    if (Net.openWifiSettings(act)) host.close();
                    else Toast.makeText(act, "Ta funkcja jest niedostepna na tym radiu",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                update();   // stan przyjdzie też broadcastem WIFI_STATE_CHANGED
            }
        });
        tBt.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleBt();
            }
        });
        tHot.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Android 9 nie pozwala aplikacji przełączać hotspotu: tap prowadzi
                // wprost do ekranu „Hotspot Wi-Fi" z systemowym przełącznikiem
                if (Net.openTetherSettings(act)) host.close();
                else Toast.makeText(act, "Ta funkcja jest niedostepna na tym radiu",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Pokazuje/ukrywa przyciski trybow (Tryb ciemny / Night Shift) na dole panelu
     *  z płynną animacją alfa i skali. Wywoływane po long-press na suwaku jasności. */
    private void toggleButtons() {
        if (controls == null) return;
        buttonsVisible = !buttonsVisible;
        controls.setVisibility(VISIBLE);
        float startAlpha = buttonsVisible ? 0f : 1f;
        float endAlpha = buttonsVisible ? 1f : 0f;
        controls.setAlpha(startAlpha);
        controls.setScaleX(0.85f);
        controls.setScaleY(0.85f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(controls, View.ALPHA, startAlpha, endAlpha);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(controls, View.SCALE_X, 0.85f, buttonsVisible ? 1f : 0.85f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(controls, View.SCALE_Y, 0.85f, buttonsVisible ? 1f : 0.85f);
        alpha.setDuration(220);
        scaleX.setDuration(220);
        scaleY.setDuration(220);
        alpha.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
        alpha.start();
        scaleX.start();
        scaleY.start();
        alpha.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!buttonsVisible) controls.setVisibility(GONE);
            }
        });
    }

    /** Suwak ciepła Night Shift (przytrzymanie przycisku, jak w iOS).
     *  Ustawia filtr NA BIEŻĄCO, chowa się po 6 s bez dotyku. */
    private void showWarmthSlider() {
        if (warmthRow != null) {
            warmthRow.setVisibility(VISIBLE);
            sb.setProgress(Math.max(5, Math.min(80, Prefs.nightShiftStrength(act))));
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
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(6);

        TextView cool = new TextView(act);
        cool.setText("Chłodniejsze");
        cool.setTextSize(15);
        cool.setTextColor(night ? 0xFFFFFFFF : 0xFF111114);
        row.addView(cool, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        sb = new WarmthSlider(act);
        sb.setProgress(Math.max(5, Math.min(80, Prefs.nightShiftStrength(act))));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(150), dp(46));
        bp.leftMargin = dp(10);
        bp.rightMargin = dp(10);
        row.addView(sb, bp);

        TextView warm = new TextView(act);
        warm.setText("Cieplejsze");
        warm.setTextSize(15);
        warm.setTextColor(night ? 0xFFFFFFFF : 0xFF111114);
        row.addView(warm, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        wrap.addView(row, rp);
        warmthRow = wrap;
        sb.setListener(new WarmthSlider.Listener() {
            @Override
            public void on(int strength) {
                Prefs.setNightShiftStrength(act, strength);
                NightShift.refresh(act);
            }
        });

        addView(wrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        } catch (Throwable t) {
            Log.e("ConnectivityPanelIos", "setVolume failed: " + t, t);
        }
    }

    private float brightnessValue() {
        try {
            // Gdy Night Shift wlaczony - zawsze obniz jasnosc do minimum.
            if (NightShift.isOn(act)) return 0.0f;
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
            // Gdy Night Shift wlaczony - zawsze minimalna jasnosc (niezaleznie od suwaka).
            int level = NightShift.isOn(act) ? 10 : Math.max(10, Math.round(v * 255));
            Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, level);
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
        // Kolor aktywnego stanu: iOS #007AFF dla wszystkich 3 paskow.
        String ssid = Net.wifiSsid(act);
        tWifi.set(wEn, 0xFF007AFF, Icons.vector(act, wEn ? R.drawable.ic_wifi : R.drawable.ic_wifi_off,
                128, white), "Wi-Fi",
                (ws == 3 || ws == 2) ? (ssid.length() > 0 ? ssid : "Połączono")
                        : (ws == 1 ? "Włączone" : "Wyłączone"));
        // podpis Bluetooth: nazwy podłączonych urządzeń, po przecinku
        StringBuilder btSub = new StringBuilder();
        for (String n : BtDevice.connectedNames()) {
            if (btSub.length() > 0) btSub.append(", ");
            btSub.append(n);
        }
        tBt.set(bt, 0xFF007AFF, Icons.vector(act, R.drawable.ic_bt, 128, white), "Bluetooth",
                btSub.length() > 0 ? btSub.toString() : (bt ? "Włączony" : "Wyłączony"));
        tHot.set(th, 0xFF007AFF, Icons.tether(128, white, false), "Hotspot osobisty",
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
        // Gdy Night Shift wlaczony - suwak jasnosci zawsze na minimum (0.0).
        // Jasnosc systemowa jest obnizana przez NightShiftService, suwak tylko
        // odzwierciedla ten stan wizualnie.
        if (briSlider != null && NightShift.isOn(act)) {
            briSlider.setValue(0.0f);
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
        // Uwaga: AudioManager.VOLUME_CHANGED_ACTION nie istnieje na Androidzie 9 (API 28),
        // dodano je w API 31. Suwak aktualizuje sie przez update() wywolywane z innych
        // broadcastow (Bluetooth, Wi-Fi itp.).
        try {
            act.registerReceiver(rc, f);
        } catch (Throwable t) {
            Log.e("ConnectivityPanelIos", "registerReceiver failed: " + t, t);
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

    // ---------------------------------------------------------------- pasek lacznosci (szklana pigułka iOS 26)
    // Jednolity poziomy wiersz (pill) dla Wi-Fi, Bluetooth i Hotspotu:
    // szklane tło jak suwaki PillSlider, światło z góry, gradientowa obwódka,
    // pełne zaokrąglenie (pigułka, cornerRadius = h/2). Ikona 40dp po lewej,
    // tytuł bold 15sp i podtytuł 12sp po prawej. Wszystkie 3 paski mają
    // identyczną wysokość (56dp) i pełną szerokość kontenera.
    private class Tile extends View {
        private boolean active;
        private int color;
        private Bitmap icon;
        private String title;
        private String sub;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d = getResources().getDisplayMetrics().density;

        Tile(Context c) {
            super(c);
            this.active = false;
            this.color = 0xFF007AFF;
        }

        void set(boolean a, int col, Bitmap ic, String t, String s) {
            active = a;
            color = col;
            icon = ic;
            title = t;
            sub = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float rad = h / 2f;  // pełna pigułka

            // SZKŁO: półprzezroczysty tor + pas światła Z GÓRY (jak PillSlider)
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
            cv.restoreToCount(save);

            // obwódka gradientowa (jak PillSlider)
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f * d);
            p.setShader(new LinearGradient(0, 0, 0, h,
                    night ? new int[]{0x99FFFFFF, 0x26FFFFFF}
                          : new int[]{0x66808090, 0x1A808090},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            r.set(d * 0.7f, d * 0.7f, w - d * 0.7f, h - d * 0.7f);
            cv.drawRoundRect(r, rad - d * 0.7f, rad - d * 0.7f, p);
            p.setShader(null);

            // bąbel ikony (40dp) po lewej
            float cx = dp(12) + dp(20);  // 12dp margin + 20dp (pół z 40dp)
            float cy = h / 2f;
            p.setColor(active ? color : 0xFF7E8686);
            p.setStyle(Paint.Style.FILL);
            cv.drawCircle(cx, cy, dp(20), p);  // bąbel 40dp = promień 20dp

            // ikona wewnątrz bąbla (24dp)
            if (icon != null) {
                cv.drawBitmap(icon, null,
                        new android.graphics.RectF(cx - dp(12), cy - dp(12), cx + dp(12), cy + dp(12)), p);
            }

            // tekst po prawej (margin 8dp od bąbla)
            float textX = cx + dp(20) + dp(8);
            p.setColor(night ? 0xFFFFFFFF : 0xFF111114);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(15f * getResources().getDisplayMetrics().scaledDensity);
            p.setTextAlign(Paint.Align.LEFT);
            cv.drawText(fitText(p, title, w - textX - dp(8)), textX, cy - dp(4), p);

            p.setColor(night ? 0xFFC9CFCF : 0xFF8E8E93);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
            cv.drawText(fitText(p, sub, w - textX - dp(8)), textX, cy + dp(10), p);
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
            // KOLUMNA IKON KAFELKOW: srodek kola = 18dp (margines) + 25dp (polowa
            // kola) = 43dp od lewej krawedzi - uzyte tez przez "Tryb ciemny",
            // dzieki czemu ikony Wi-Fi / Hotspot / Tryb ciemny stoja w jednej linii.
            // UWAGA: to NIE zalezy od szerokosci kontenera (kiedyś cx = w/2 przy
            // kontenerze 194dp dawal 97dp - koło bylo 54dp w prawo od sasiadow).
            float cx = 43f * d;
            float cy = 28f * d;
            float rad = 28f * d;
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            // DZIEN (nieaktywny): szarawe szklo (0x3D787880, jak nieaktywne przyciski
            // iOS w jasnym motywie) - biale kolo znikalo na jasnym tle panelu
            p.setColor(active ? (kind == 3 ? 0xFFFF9F0A
                            : (kind == 4 ? 0xFFFFFFFF : 0xFF1C94FF))
                    : (night ? 0x9E4E4E53 : 0x3D787880));
            cv.drawCircle(cx, cy, rad, p);
            if (!active) {
                int saveG = cv.saveLayer(cx - rad - d, cy - rad - d, cx + rad + d, cy + rad + d, null);
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
            // NAPRAWA: w trybie dziennym ikona "kontrast" byla BIALA na bialym kole
            // (niewidoczna - wygladalo to jak pusty bialy przelacznik). Nieaktywny
            // kind 4 ma teraz ikone ciemna w dzien, biala w nocy - jak pozostale.
            int ink = (kind == 4 && active) ? 0xFF1C1C1E
                    : active ? 0xFFFFFFFF
                    : (night ? 0xFFFFFFFF : 0xFF3C3C43);
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
            // Podpis w stalej szerokosci przycisku (120dp): nazwa i kazda linia
            // stanu obcinane do szerokosci - dlugi "Night Shift" nie wychodzi
            // poza sasiada (TASK-1).
            float labW = Math.max(dp(20), w - 12f * d);
            if (sub != null) {
                // trzy linie jak w iOS: nazwa (bold) + stan w dwóch wierszach;
                // odstęp nazwy od koła WIĘKSZY - dokładnie jak na wzorcu
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(12f * sd);
                p.setColor(night ? 0xFFFFFFFF : 0xFF111114);
                cv.drawText(fitText(p, label, labW), cx, 72f * d, p);
                p.setTypeface(Typeface.DEFAULT);
                p.setTextSize(10.5f * sd);
                p.setColor(night ? 0xFFC9CFCF : 0xFF6C6C70);
                int di = sub.indexOf(" do ");
                if (di > 0) {
                    cv.drawText(fitText(p, sub.substring(0, di + 3), labW), cx, 84f * d, p);
                    cv.drawText(fitText(p, sub.substring(di + 4), labW), cx, 94f * d, p);
                } else {
                    cv.drawText(fitText(p, sub, labW), cx, 84f * d, p);
                }
            } else {
                p.setTypeface(Typeface.DEFAULT);
                p.setColor(night ? 0xFFFFFFFF : 0xFF111114);
                p.setTextSize(13f * sd);
                cv.drawText(fitText(p, label, labW), cx, 80f * d, p);
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
        private final Path pillPath = new Path();
        private final Path levelPath = new Path();
        private final Path speakerPath = new Path();
        private final float d = getResources().getDisplayMetrics().density;

        public PillSlider(Context c, boolean sun, float v) {
            super(c);
            this.sun = sun;
            this.value = Math.max(0f, Math.min(1f, v));
            this.night = Look.night(c);
            setContentDescription(sun ? "Jasność ekranu" : "Głośność");
        }

        /** Ustaw wartość suwaka z zewnątrz (np. wymuś minimum przy Night Shift). */
        public void setValue(float v) {
            this.value = Math.max(0f, Math.min(1f, v));
            invalidate();
        }

        public void setListener(Listener l) {
            this.l = l;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float rad = w / 2f;

            // 1. SZKŁO: półprzezroczysty tor + pionowy rant
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(night ? 0x9E4E4E53 : 0xCCD1D1D6);
            r.set(0f, 0f, w, h);
            cv.drawRoundRect(r, rad, rad, p);

            // 2. WYPEŁNIENIE POZIOMU: biały prostokład przycięty do kształtu pigułki
            //    przez Path.op(INTERSECT) — gładkie łuki w LAYER_TYPE_SOFTWARE,
            //    górna krawędź idealnie płaska na wysokości value.
            float fillTop = h * (1f - value);
            pillPath.reset();
            pillPath.addRoundRect(r, rad, rad, Path.Direction.CW);
            levelPath.reset();
            levelPath.addRect(0f, fillTop, w, h, Path.Direction.CW);
            pillPath.op(levelPath, Path.Op.INTERSECT);
            p.setShader(null);
            p.setColor(0xE6FAFAFC);
            cv.drawPath(pillPath, p);

            // 3. PAS ŚWIATŁA Z GÓRY na wierzchu wypełnienia (delikatny gradient)
            p.setShader(new LinearGradient(0f, 0f, 0f, h * 0.35f,
                    new int[]{0x3DFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            cv.drawPath(pillPath, p);
            p.setShader(null);

            // 4. Obrys (ramka) pigułki
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f * d);
            p.setShader(new LinearGradient(0f, 0f, 0f, h,
                    night ? new int[]{0x99FFFFFF, 0x26FFFFFF}
                          : new int[]{0x66808090, 0x1A808090},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            r.set(d * 0.7f, d * 0.7f, w - d * 0.7f, h - d * 0.7f);
            cv.drawRoundRect(r, rad - d * 0.7f, rad - d * 0.7f, p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);

            // IKONY 1:1 z iOS (zdjęcie usera): słońce #FFD700, głośnik #1EA1FF
            float cx = w / 2f;
            float cy = h - 34f * d;
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
                speakerPath.reset();
                speakerPath.moveTo(cx - 11f * d, cy - 4.5f * d);
                speakerPath.lineTo(cx - 4f * d, cy - 4.5f * d);
                speakerPath.lineTo(cx + 3f * d, cy - 10.5f * d);
                speakerPath.lineTo(cx + 3f * d, cy + 10.5f * d);
                speakerPath.lineTo(cx - 4f * d, cy + 4.5f * d);
                speakerPath.lineTo(cx - 11f * d, cy + 4.5f * d);
                speakerPath.close();
                cv.drawPath(speakerPath, p);
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
                return true;
            }
            // WPUST NA KOŃCACH: palec fizycznie nie wejdzie na samą krawędź
            // widoku, więc wcześniej zakres 0..1 liczono 1:1 z całej wysokości
            // i końców (0% / 100%) nie dało się osiągnąć. Teraz zakres dotyku
            // jest zwężony o wpust - dotknięcie BLISKO krawędzi daje od razu
            // wartość skrajną.
            if (a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
                float inset = 30f * d;
                float usable = getHeight() - inset * 2f;
                float frac = usable <= 0 ? 0.5f
                        : (getHeight() - inset - e.getY()) / usable;
                value = Math.max(0f, Math.min(1f, frac));
                invalidate();
                if (l != null) {
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

        void setProgress(int v) {
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
            float trackY = h * 0.42f;
            float trackH = 7f * d;
            float pad = 20f * d;   // miejsce na pigułkę na końcach toru
            float trackW = w - pad * 2f;
            // tor: niewypełniona część (ciemna)
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(night ? 0x2EFFFFFF : 0x22000000);
            r.set(pad, trackY - trackH / 2f, pad + trackW, trackY + trackH / 2f);
            cv.drawRoundRect(r, trackH / 2f, trackH / 2f, p);
            // wypełnienie chłodne→ciepłe do uchwytu (jak w iOS: niebieski -> pomarańcz)
            float frac = value / 50f;
            if (frac > 0f) {
                p.setShader(new LinearGradient(pad, 0f, pad + trackW, 0f,
                        0xFF64D2FF, 0xFFFF9F0A, Shader.TileMode.CLAMP));
                r.set(pad, trackY - trackH / 2f, pad + trackW * frac, trackY + trackH / 2f);
                cv.drawRoundRect(r, trackH / 2f, trackH / 2f, p);
                p.setShader(null);
            }
            // dwa punkty-pozycjonery pod torem (jak w iOS)
            p.setColor(night ? 0x55FFFFFF : 0x44000000);
            cv.drawCircle(pad + trackW * 0.10f, trackY + 9f * d, 1.6f * d, p);
            cv.drawCircle(pad + trackW * 0.50f, trackY + 9f * d, 1.6f * d, p);
            // biały pigułkowy uchwyt — obniżony o połowę wysokości (12 dp w dół),
            // żeby górna krawędź była na poziomie palca/toru, a nie wyżej.
            // Usunięto zaokrąglenie (drawRect zamiast drawRoundRect) — bez efektu
            // „rozmycia” pod paskiem.
            float tx = pad + trackW * frac;
            p.setColor(0xFFFFFFFF);
            r.set(tx - 17f * d, trackY, tx + 17f * d, trackY + 24f * d);
            cv.drawRect(r, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
                float pad = 20f * d;
                float trackW = getWidth() - pad * 2f;
                float frac = trackW <= 0 ? 0f : (e.getX() - pad) / trackW;
                int v = Math.round(Math.max(0f, Math.min(1f, frac)) * 50f);
                if (v != value) {
                    value = v;
                    invalidate();
                }
                if (l != null) {
                    l.on(value);
                }
            }
            return true;
        }
    }
}
