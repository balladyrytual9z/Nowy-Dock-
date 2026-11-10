package pl.dock.widget;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

// =====================================================================
// Centrum laczonosci w stylu iOS - PRZEBUDOWANE OD ZERA (2026-11-06).
//
// DLACZEGO OD ZERA: na tablecie (ESSGOO / MT8163 / Mali-T720 / API 28)
// w nakladce launchera NIE rysowaly sie widoki z wlasnym onDraw()
// (stary PillSlider, NightShiftButton, ModeSegment, kafelki ToolGrid) -
// widac bylo tylko tlo panelu i TextView; po wielu iteracjach
// (warstwy SOFTWARE, fade, animacje, re-layout - v1.0.244...256)
// ustalono, ze w dynamicznie doczepianym poddrzewie renderuja sie
// WLACZNIE widoki frameworka. Dlatego caly panel to LinearLayout /
// FrameLayout / TextView / ImageView / View z tlem GradientDrawable /
// SeekBar. Zaden widok nie ma wlasnego onDraw(): zaokraglenia to
// GradientDrawable, a ikony to Bitmapy generowane raz (Canvas na
// bitmapie = CPU) i wyswietlane przez ImageView.
// =====================================================================
public class ConnectivityPanel extends LinearLayout {

    public interface Host {
        void close();

        void modeChanged();
    }

    private final Activity act;
    private final boolean night;
    private final Host host;
    private ToolGrid grid;
    private TextView btnAdd;
    // narzedzia stalego modulu bazowego - nie moga sie powtarzac w edytowalnej siatce
    static final String[] BASE_IDS =
            {"bri", "vol", "nightshift", "mode", "wifi", "bt", "hot", "conn"};
    private Tile tWifi, tBt, tHot;
    private RoundBtn bMode, bShift;
    private LinearLayout warmthRow;
    private android.widget.SeekBar warmthBar;
    private PillSlider volSlider;
    private PillSlider briSlider;
    private BroadcastReceiver rc;
    private boolean editMode = false;
    private final Runnable refreshLater = new Runnable() {
        @Override
        public void run() {
            update();
        }
    };

    private int dp(float v) {
        return Ui.dp(act, v);
    }

    /** Czysta logika (testowalna na JVM): wyczysc z listy narzedzi te z modulu bazowego. */
    static List<String> withoutBaseTools(List<String> ids) {
        List<String> out = new ArrayList<String>();
        if (ids == null) return out;
        for (String id : ids) {
            boolean base = false;
            for (String bid : BASE_IDS) {
                if (bid.equals(id)) {
                    base = true;
                    break;
                }
            }
            if (!base) out.add(id);
        }
        return out;
    }

    public ConnectivityPanel(Activity a, boolean night, Host host) {
        super(a);
        this.act = a;
        this.night = night;
        this.host = host;
        PanelTool.init(a);
        setOrientation(VERTICAL);
        // Babel w stylu macOS: kwadratowy z zaokraglonymi rogami, szklany
        // (tresc modulow lezy na nim jak na popoverze). Klikalne, by tap w tlo
        // panelu nie zamykal nakladki.
        // tlo jak w reszcie aplikacji (popupBg - wspolne tlo popupow launchera)
        setBackground(Ui.popupBg(act, night));
        setPadding(dp(12), dp(12), dp(12), dp(10));
        setClickable(true);
        // Warstwa SOFTWARE na API < 30 zostaje (potwierdzona w PR #14): dla widokow
        // frameworka jest nieszkodliwa, a chroni, gdyby ktos dozyl widok z onDraw.
        if (android.os.Build.VERSION.SDK_INT < 30) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        build();
    }

    private TextView pill(String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(14);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(0xFF0A84FF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        // pigułka jak w iOS: szarość systemowa 787880, nie zwykła biel/czerń
        t.setBackground(Ui.round(night ? 0x33787880 : 0x1F787880, 18, act));
        return t;
    }

    /** Szklana karta modulu jak w iOS Control Center: lekkie wypelnienie
     *  przepuszczajace rozmyte tlo ekranu + subtelna obwodka.
     *  GradientDrawable = czysty framework (zero onDraw, patrz naglowek klasy). */
    private GradientDrawable glassBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(night ? 0x2EFFFFFF : 0x17000000);
        g.setCornerRadius(dp(28));
        g.setStroke(Math.max(1, dp(1)), night ? 0x33FFFFFF : 0x1E787880);
        return g;
    }

    private void build() {
        // ===== modul bazowy 1:1 jak w Liquid-Glass =====
        // lewa kolumna 400dp: kafelki Wi-Fi/Bluetooth (150dp) + szeroki Hotspot (76dp);
        // prawa kolumna: dwa pionowe suwaki 70x238dp. BEZ naglowka, BEZ "Edytuj"
        // (edycja = przytrzymanie tla panelu), BEZ podpisow dodatkowych.
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(HORIZONTAL);

        LinearLayout left = new LinearLayout(act);
        left.setOrientation(VERTICAL);
        LinearLayout row1 = new LinearLayout(act);
        tWifi = new Tile(act, false);
        tBt = new Tile(act, false);
        // kafelki prostokatne jak w iOS (szersze niz wyzsze)
        LayoutParams l1 = new LayoutParams(dp(170), dp(128));
        l1.rightMargin = dp(6);
        LayoutParams l2 = new LayoutParams(dp(170), dp(128));
        l2.leftMargin = dp(6);
        row1.addView(tWifi, l1);
        row1.addView(tBt, l2);
        left.addView(row1);
        tHot = new Tile(act, true);
        LayoutParams l3 = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76));
        l3.topMargin = dp(12);
        left.addView(tHot, l3);
        top.addView(left, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

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

                @Override
                public void onLong() {
                    openAndClose("android.settings.SOUND_SETTINGS");
                }
            });
            bri.setListener(new PillSlider.Listener() {
                @Override
                public void on(float v) {
                    setBrightness(v);
                }

                @Override
                public void onLong() {
                    showWarmthSlider();
                }
            });
            volSlider = vol;
            briSlider = bri;
            top.addView(bars);
        }
        addView(top);

        // dolny rzad: JEDEN przycisk trybu (klik = cykl Jasny -> Ciemny -> Auto)
        // + Night Shift (klik = wl/wyl, przytrzymanie = suwak ciepla)
        boolean showMode = Prefs.showModeButtons(act);
        boolean showNs = Prefs.showNightShift(act);
        if (showMode || showNs) {
            LinearLayout row = new LinearLayout(act);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LayoutParams rp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            // Odstęp wizualny od kafelka Hotspot = 12dp (6 tu + 6 wewnętrznego
            // marginesu górnego koła w RoundBtn) - czyli TYLE SAMA co przerwa
            // między kafelkami Wi-Fi/Bluetooth a Hotspotem (topMargin 12dp).
            rp.topMargin = dp(6);
            if (showMode) {
                bMode = new RoundBtn(act);
                bMode.setKind(0, "Jasny");
                row.addView(bMode, new LayoutParams(dp(88), dp(96)));
                bMode.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Prefs.followSystem(act)) {
                            Prefs.setFollowSystem(act, false);
                            Prefs.setMode(act, "day");
                        } else if ("day".equals(Prefs.mode(act))) {
                            Prefs.setMode(act, "night");
                        } else if ("night".equals(Prefs.mode(act))) {
                            Prefs.setFollowSystem(act, true);
                        } else {
                            Prefs.setMode(act, "day");
                        }
                        applyMode();
                    }
                });
            }
            if (showNs) {
                // Night Shift bezposrednio PO PRAWEJ przycisku trybu
                bShift = new RoundBtn(act);
                bShift.setKind(3, "Night Shift");
                LayoutParams shp = new LayoutParams(dp(110), dp(96));
                shp.leftMargin = dp(12);
                row.addView(bShift, shp);
                bShift.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean on = !NightShift.isOn(act);
                        boolean ok = NightShift.set(act, on);
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
            addView(row, rp);
        }

        // ===== siatka dodatkowych narzedzi (edytowalna) =====
        grid = new ToolGrid(act, night, new ToolGrid.Host() {
            @Override
            public void onTool(PanelTool.Def d, boolean longPress) {
                runTool(d, longPress);
            }

            @Override
            public void onChanged(List<String> ids) {
                PanelTool.save(act, ids);
                update();
            }
        });
        List<String> toolIds = PanelTool.load(act);
        toolIds = withoutBaseTools(toolIds);
        grid.setTools(toolIds);
        grid.setVisibility(GONE);
        LayoutParams gp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = dp(10);
        addView(grid, gp);

        // dlugie przytrzymanie tla = przelacznik trybu edycji: pierwsze przytrzymanie
        // otwiera (siatka narzedzi + "Dodaj narzedzie"), nastepne zamyka
        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (editMode) exitEditMode();
                else enterEditMode();
                return true;
            }
        });

        // przycisk "Dodaj narzedzie" (widoczny tylko w trybie edycji)
        btnAdd = pill("+  Dodaj narzedzie");
        btnAdd.setTextSize(15);
        btnAdd.setVisibility(GONE);
        btnAdd.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openPicker();
            }
        });
        LayoutParams ap = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        ap.gravity = Gravity.CENTER_HORIZONTAL;
        ap.topMargin = dp(12);
        addView(btnAdd, ap);

        // akcje kafelkow lacznosci (tap/przytrzymanie jak dotychczas)
        wireTile(tWifi, "wifi");
        wireTile(tBt, "bt");
        wireTile(tHot, "hot");
    }

    private void wireTile(Tile t, String id) {
        t.setTag(PanelTool.find(id));
        t.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onRailTap((PanelTool.Def) v.getTag());
            }
        });
        t.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onRailLong((PanelTool.Def) v.getTag());
                return true;
            }
        });
    }

    private void onRailTap(PanelTool.Def d) {
        String id = d.id;
        if ("wifi".equals(id)) {
            // iOS: tap = przelacz Wi-Fi bezposrednio (Android 9 pozwala przez
            // WifiManager; CHANGE_WIFI_STATE jest w manifieście).
            boolean before = Net.wifiEnabled(act);
            if (!Net.setWifi(act, !before)) {
                // radio producenta potrafi blokowac przelaczanie: wtedy ustawienia
                if (Net.openWifiSettings(act)) host.close();
                else unavailable();
                return;
            }
            update();   // stan przyjdzie tez broadcastem WIFI_STATE_CHANGED
        } else if ("hot".equals(id)) {
            // Android 9 nie pozwala aplikacji przelaczac hotspotu: tap prowadzi
            // wprost do ekranu z androidowym przelacznikiem hotspotu
            if (Net.openTetherSettings(act)) host.close();
            else unavailable();
        } else if ("bt".equals(id)) {
            // dzialanie wg ustawienia btAction (aplikacja / przelacz / auto)
            toggleBt();
        } else {
            runTool(d, false);
        }
    }

    /** Przytrzymanie kafelka lacznosci: ekran ustawien danej funkcji. */
    private void onRailLong(PanelTool.Def d) {
        String id = d.id;
        if ("wifi".equals(id)) {
            if (Net.openWifiSettings(act)) host.close();
            else unavailable();
        } else if ("bt".equals(id)) {
            if (Net.openBluetoothSettings(act)) host.close();
            else unavailable();
        } else if ("hot".equals(id)) {
            if (Net.openTetherSettings(act)) host.close();
            else unavailable();
        } else {
            enterEditMode();
        }
    }

    // ---------------------------------------------------------------- tryb edycji
    public void enterEditMode() {
        if (editMode) return;
        editMode = true;
        try {
            if (grid != null) grid.setEditing(true);
            if (grid != null) grid.setVisibility(VISIBLE);
        } catch (Throwable ignored) {
        }
        btnAdd.setAlpha(0f);
        btnAdd.setScaleX(0.9f);
        btnAdd.setScaleY(0.9f);
        btnAdd.setVisibility(VISIBLE);
        try {
            btnAdd.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(pl.dock.widget.ui.DockMotion.IOS_SPRING).start();
        } catch (Throwable tr) {
            btnAdd.setAlpha(1f);
            btnAdd.setScaleX(1f);
            btnAdd.setScaleY(1f);
        }
    }

    public void exitEditMode() {
        if (!editMode) return;
        editMode = false;
        try {
            if (grid != null) grid.setEditing(false);
            if (grid != null) grid.setVisibility(GONE);
        } catch (Throwable ignored) {
        }
        btnAdd.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(180)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        btnAdd.setVisibility(GONE);
                    }
                }).start();
    }

    private void openPicker() {
        // narzedzia stalego modulu bazowego pokaz jako juz dodane (wyszarzone)
        List<String> have = grid.ids();
        for (String id : BASE_IDS) {
            if (!have.contains(id)) have.add(id);
        }
        ToolPicker.show(act, night, have, new ToolPicker.OnPick() {
            @Override
            public void pick(String id) {
                grid.addTool(id);
            }
        });
    }

    // ---------------------------------------------------------------- dzialanie narzedzi
    private boolean startAction(String action) {
        try {
            Intent i = new Intent(action);
            if (i.resolveActivity(act.getPackageManager()) == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void openAndClose(String action) {
        if (startAction(action)) {
            host.close();
        } else {
            unavailable();
        }
    }

    private void unavailable() {
        Toast.makeText(act, "Ta funkcja jest niedostepna na tym radiu", Toast.LENGTH_SHORT).show();
    }

    private void openShortcut(PanelTool.Def d) {
        String a = d.action;
        boolean ok;
        if ("@tether".equals(a)) {
            ok = Net.openTetherSettings(act);
        } else if ("@btapp".equals(a)) {
            ok = Net.openBluetoothApp(act);
        } else if ("@dock".equals(a)) {
            try {
                Intent i = new Intent(act, ConfigActivity.class);
                i.putExtra("origin_page", Prefs.lastPage(act));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(i);
                ok = true;
            } catch (Throwable t) {
                ok = false;
            }
        } else {
            ok = startAction(a);
        }
        if (ok) host.close();
        else unavailable();
    }

    private void runTool(PanelTool.Def d, boolean longPress) {
        if (d.kind == PanelTool.K_APP) {
            if (longPress) enterEditMode();
            else if (PanelTool.launchApp(act, d)) host.close();
            else unavailable();
            return;
        }
        if (d.kind == PanelTool.K_SHORTCUT) {
            if (longPress) enterEditMode();
            else openShortcut(d);
            return;
        }
        String id = d.id;
        if (!longPress) {
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
        if ("air".equals(id)) {
            if (longPress) openAndClose("android.settings.AIRPLANE_MODE_SETTINGS");
            else toggleAirplane();
        } else if ("hot".equals(id)) {
            boolean before = Net.tethering(act);
            String res = Net.setTethering(act, !before);
            if ("ok".equals(res)) {
                refreshSoon();
            } else if ("settings".equals(res)) {
                host.close();
            } else {
                unavailable();
            }
        } else if ("wifi".equals(id)) {
            if (!Net.openWifiSettings(act)) unavailable();
            else host.close();
        } else if ("bt".equals(id)) {
            if (longPress) {
                Net.openBluetoothSettings(act);
                host.close();
            } else {
                toggleBt();
            }
        } else if ("rotate".equals(id)) {
            if (longPress) openAndClose("android.settings.DISPLAY_SETTINGS");
            else toggleRotate();
        } else if ("mute".equals(id)) {
            if (longPress) openAndClose("android.settings.SOUND_SETTINGS");
            else toggleMute();
        } else if ("flash".equals(id)) {
            if (longPress) enterEditMode();
            else if (!PanelTool.toggleTorch(act)) unavailable();
        } else if ("loc".equals(id)) {
            openAndClose("android.settings.LOCATION_SOURCE_SETTINGS");
        } else if ("dark".equals(id)) {
            if (longPress) {
                enterEditMode();
            } else {
                boolean toNight = !Look.night(act);
                Prefs.setFollowSystem(act, false);
                Prefs.setMode(act, toNight ? "night" : "day");
                applyMode();
            }
        } else if ("nightshift".equals(id)) {
            if (longPress) {
                showWarmthSlider();
            } else {
                String next = NightShift.nextMode(NightShift.mode(act));
                boolean ok = NightShift.setMode(act, next);
                if (!ok) host.close();
            }
        } else if (longPress) {
            enterEditMode();
        }
        refreshSoon();
    }

    private void refreshSoon() {
        update();
        removeCallbacks(refreshLater);
        postDelayed(refreshLater, 400);
    }

    private boolean askWriteSettings() {
        try {
            if (!Settings.System.canWrite(act)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:" + act.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(i);
                host.close();
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void toggleAirplane() {
        try {
            boolean air = Settings.Global.getInt(act.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
            boolean next = !air;
            Settings.Global.putInt(act.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, next ? 1 : 0);
            Intent i = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            i.putExtra("state", next);
            act.sendBroadcast(i);
        } catch (Throwable t) {
            openAndClose("android.settings.AIRPLANE_MODE_SETTINGS");
        }
    }

    private void toggleRotate() {
        if (askWriteSettings()) return;
        try {
            int cur = Settings.System.getInt(act.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 0);
            Settings.System.putInt(act.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, cur == 1 ? 0 : 1);
        } catch (Throwable t) {
            openAndClose("android.settings.DISPLAY_SETTINGS");
        }
    }

    private void toggleMute() {
        try {
            AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            if (am.isStreamMute(AudioManager.STREAM_MUSIC)
                    || am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC,
                            Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 3), 0);
                }
            } else {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Inline suwak ciepla Night Shift - pojawia sie pod siatka narzedzi i ustawia
     *  filtr NA BIEZACO (bez przycisku "Zapisz"), jak przytrzymanie przycisku w iPhonie. */
    private void showWarmthSlider() {
        if (warmthRow != null) {
            warmthRow.setVisibility(VISIBLE);
            warmthBar.setProgress(Math.max(5, Math.min(80, Prefs.nightShiftStrength(act))));
            scheduleWarmthHide();
            return;
        }
        warmthRow = new LinearLayout(act);
        warmthRow.setOrientation(HORIZONTAL);
        warmthRow.setGravity(Gravity.CENTER_VERTICAL);
        warmthRow.setPadding(dp(6), dp(2), dp(6), dp(2));
        warmthRow.setBackground(Ui.round(night ? 0x33787880 : 0x1F787880, 20, act));

        TextView cool = new TextView(act);
        cool.setText("Chlodniejsze");
        cool.setTextSize(13);
        cool.setTypeface(null, Typeface.BOLD);
        cool.setTextColor(night ? 0xB3FFFFFF : 0xFF3C3C43);
        warmthRow.addView(cool, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        warmthBar = new android.widget.SeekBar(act);
        warmthBar.setMax(80);
        warmthBar.setProgress(Math.max(5, Math.min(80, Prefs.nightShiftStrength(act))));
        LayoutParams bp = new LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.leftMargin = dp(12);
        bp.rightMargin = dp(12);
        warmthRow.addView(warmthBar, bp);

        TextView warm = new TextView(act);
        warm.setText("Cieplejsze");
        warm.setTextSize(13);
        warm.setTypeface(null, Typeface.BOLD);
        warm.setTextColor(night ? 0xB3FFFFFF : 0xFF3C3C43);
        warmthRow.addView(warm, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        warmthBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar sb, int prog, boolean fromUser) {
                if (!fromUser) return;
                int strength = Math.max(5, Math.min(80, prog));
                Prefs.setNightShiftStrength(act, strength);
                NightShift.refresh(act);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar sb) {
                removeCallbacks(hideWarmth);
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar sb) {
                // Kciuk SeekBara moze zajechac ponizej 5 (SeekBar nie ma min):
                // pref zapisal juz zclampowana wartosc (>= 5), wiec przyklej
                // kciuk do realnie zapisanej wartosci - panel musi pokazywac
                // to samo, co Ustawienia (ten sam klucz nsval).
                sb.setProgress(Math.max(5, Math.min(80, Prefs.nightShiftStrength(act))));
                scheduleWarmthHide();
            }
        });

        LayoutParams rp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(10);
        addView(warmthRow, rp);
        warmthRow.setAlpha(0f);
        warmthRow.setTranslationY(dp(8));
        warmthRow.animate().alpha(1f).translationY(0f).setDuration(220).start();
        scheduleWarmthHide();
    }

    private void scheduleWarmthHide() {
        removeCallbacks(hideWarmth);
        postDelayed(hideWarmth, 6000);
    }

    private final Runnable hideWarmth = new Runnable() {
        @Override
        public void run() {
            if (warmthRow != null && warmthBar != null && !warmthBar.isPressed()) {
                warmthRow.animate().alpha(0f).setDuration(250)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (warmthRow != null) warmthRow.setVisibility(GONE);
                            }
                        }).start();
            } else {
                scheduleWarmthHide();
            }
        }
    };

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

    // ---------------------------------------------------------------- glosnosc / jasnosc
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
            if (askWriteSettings()) return;
            Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(act.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    Math.max(10, Math.round(v * 255)));
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- stan
    public void update() {
        if (grid != null) grid.refreshTiles();
        if (volSlider != null) volSlider.setValue(volumeValue());
        if (briSlider != null) briSlider.setValue(brightnessValue());
        int white = 0xFFFFFFFF;
        boolean wEn = Net.wifiEnabled(act);
        int ws = Net.wifiState(act);
        boolean bt = Net.bluetoothOn();
        boolean th = Net.tethering(act);
        // ikony 1:1 z Liquid-Glass (te same drawables i tether co w oryginale)
        tWifi.set(wEn, 0xFF1C94FF, Icons.vector(act, wEn ? R.drawable.ic_wifi : R.drawable.ic_wifi_off,
                128, white), "Wi-Fi", ws == 3 ? "Połączono" : (ws == 2 ? "Brak internetu"
                : (ws == 1 ? "Włączone" : "Wyłączone")));
        tBt.set(bt, 0xFF1C94FF, Icons.vector(act, R.drawable.ic_bt, 128, white), "Bluetooth",
                BtDevice.isConnected(act) && Prefs.btDevice(act).length() > 0
                        ? "Telefon połączony" : (bt ? "Włączony" : "Wyłączony"));
        tHot.set(th, 0xFF30D158, Icons.tether(128, white, false), "Hotspot osobisty",
                th ? "Włączony" : "Wyłączony");
        if (bMode != null) {
            boolean follow = Prefs.followSystem(act);
            String m = Prefs.mode(act);
            if (follow) {
                bMode.setKind(2, "Auto");
            } else if ("night".equals(m)) {
                bMode.setKind(1, "Ciemny");
            } else {
                bMode.setKind(0, "Jasny");
            }
        }
        if (bShift != null) bShift.setActive(NightShift.isOn(act));
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
        f.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        f.addAction("android.location.MODE_CHANGED");
        f.addAction("android.media.VOLUME_CHANGED_ACTION");
        try {
            act.registerReceiver(rc, f);
        } catch (Throwable ignored) {
        }
        try {
            update();
        } catch (Throwable tr) {
            android.util.Log.w("Dock", "panel update", tr);
        }

        // Siatka bezpieczenstwa na Mali-T720: po doszlifowaniu ukladu wymuszamy
        // pelny pomiar i przerysowanie - tanio, a odsiewa przypadki wyscigu.
        postDelayed(new Runnable() {
            @Override
            public void run() {
                requestLayout();
                invalidate();
            }
        }, 260);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(refreshLater);
        removeCallbacks(hideWarmth);
        exitEditMode();
        try {
            if (rc != null) act.unregisterReceiver(rc);
        } catch (Throwable ignored) {
        }
        rc = null;
    }

    // ---------------------------------------------------------------- kafelek (framework-only)
    // Wyglad 1:1 z Liquid-Glass: zaokraglony prostokat (34dp / polowa wysokosci
    // dla szerokiego), kolo 50dp z ikona, bold tytul + podpis. Zero onDraw:
    // GradientDrawable + ImageView + TextView.
    private class Tile extends FrameLayout {
        private final boolean wide;
        private final View circle;
        private final ImageView icon;
        private final TextView title;
        private final TextView sub;

        Tile(Context c, boolean wide) {
            super(c);
            this.wide = wide;
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(night ? 0xF046464B : 0xFFCACACF);
            bg.setCornerRadius(wide ? dp(38) : dp(34));
            bg.setStroke(Math.max(1, dp(1)), 0x55FFFFFF);
            setBackground(bg);

            FrameLayout circ = new FrameLayout(c);
            circle = new View(c);
            circle.setBackground(Ui.round(0xFF7E8686, 999, act));
            circ.addView(circle, new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER));
            icon = new ImageView(c);
            icon.setLayoutParams(new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            circ.addView(icon);
            LayoutParams clp = new LayoutParams(dp(50), dp(50));
            if (wide) {
                clp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                // leftMargin 16dp = IDENTYCZNIE jak w kafelkach Wi-Fi/Bluetooth:
                // srodki kol (16 + 25 = 41dp od lewej) leza w jednej pionowej linii
                clp.leftMargin = dp(16);
            } else {
                // ikona w LEWYM GÓRNYM rogu kafelka (jak w iOS Control Center)
                clp.gravity = Gravity.TOP | Gravity.START;
                clp.leftMargin = dp(16);
                clp.topMargin = dp(14);
            }
            addView(circ, clp);

            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(VERTICAL);
            title = new TextView(c);
            title.setTextSize(19);
            title.setTypeface(null, Typeface.BOLD);
            title.setTextColor(night ? 0xFFFFFFFF : 0xFF111114);
            texts.addView(title);
            sub = new TextView(c);
            sub.setTextSize(wide ? 16 : 15);
            sub.setTextColor(night ? 0xFFC9CFCF : 0xFF6C6C70);
            texts.addView(sub);
            LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (wide) {
                tlp.gravity = Gravity.CENTER_VERTICAL;
                tlp.leftMargin = dp(84);
            } else {
                tlp.gravity = Gravity.BOTTOM;
                tlp.leftMargin = dp(18);
                tlp.bottomMargin = dp(12);
            }
            addView(texts, tlp);
        }

        void set(boolean active, int color, Bitmap bmp, String t, String s) {
            circle.setBackground(Ui.round(active ? color : 0xFF7E8686, 999, act));
            icon.setImageBitmap(bmp);
            title.setText(t);
            sub.setText(s);
            setContentDescription(t + ", " + s);
        }
    }

    // ---------------------------------------------------------------- okragly przycisk z podpisem (framework-only)
    // Wyglad 1:1 z Liquid-Glass: kolo 56dp z ikona, pod spodem podpis 13sp.
    // kind: 0 jasny (slonce), 1 ciemny (ksiezyc), 2 auto (A), 3 Night Shift.
    private class RoundBtn extends LinearLayout {
        private final View circle;
        private final ImageView icon;
        private final TextView letter;
        private final TextView label;
        private int kind = -1;
        private boolean active = true;

        RoundBtn(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);

            FrameLayout circ = new FrameLayout(c);
            circle = new View(c);
            circle.setBackground(Ui.round(0xFF1C94FF, 999, act));
            circ.addView(circle, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
            icon = new ImageView(c);
            icon.setLayoutParams(new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
            circ.addView(icon);
            letter = new TextView(c);
            letter.setText("A");
            letter.setTextSize(22);
            letter.setTypeface(null, Typeface.BOLD);
            letter.setTextColor(0xFFFFFFFF);
            letter.setGravity(Gravity.CENTER);
            letter.setLayoutParams(new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
            circ.addView(letter);
            LayoutParams clp = new LayoutParams(dp(56), dp(56));
            clp.topMargin = dp(6);
            addView(circ, clp);

            label = new TextView(c);
            label.setTextSize(13);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(night ? 0xFFFFFFFF : 0xFF111114);
            LayoutParams llp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(2);
            addView(label, llp);
        }

        void setKind(int k, String lbl) {
            if (k == kind && lbl.contentEquals(label.getText())) return;
            kind = k;
            label.setText(lbl);
            setContentDescription(lbl);
            boolean showLetter = (k == 2);
            letter.setVisibility(showLetter ? VISIBLE : GONE);
            icon.setVisibility(showLetter ? GONE : VISIBLE);
            icon.setImageBitmap(glyphBitmap(k == 1 ? "moon" : "sun", dp(30), 0xFFFFFFFF));
            refreshColor();
        }

        void setActive(boolean a) {
            if (a == active) return;
            active = a;
            refreshColor();
        }

        private void refreshColor() {
            circle.setBackground(Ui.round(active ? (kind == 3 ? 0xFFFF9F0A : 0xFF1C94FF)
                    : (night ? 0xFF4A4F50 : 0xFFD1D1D6), 999, act));
        }
    }

    // ---------------------------------------------------------------- ikony (Bitmapa -> ImageView)
    // sizePx juz w pikselach ekranu (wolane z dp(24)); glif wypelnia ~85% ikony,
    // dzieki czemu ikony w kafelkach maja proporcje jak w iOS Control Center.
    private Bitmap glyphBitmap(String glyph, int sizePx, int color) {
        Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        PanelTool.glyph(cv, glyph, sizePx / 2f, sizePx / 2f, sizePx * 0.85f, color);
        return b;
    }

    // ---------------------------------------------------------------- pionowy suwak (framework-only)
    public static class PillSlider extends FrameLayout {
        public interface Listener {
            void on(float v);

            /** iOS: dlugie przytrzymanie suwaka = dodatkowe opcje. */
            void onLong();
        }

        private final float d = getResources().getDisplayMetrics().density;
        private float value;
        private boolean dragging;
        private float downY;
        private boolean longFired;
        private Runnable longPress;
        private final View fill;
        private FillDrawable fillBg;
        private final ImageView icon;
        private Listener l;

        private int dp(float v) {
            return Math.round(v * d);
        }

        // NAPRAWA (ESSGOO/MT8163, API 28): stary suwak uzywal potomka z weight
        // ( wysokosc 0 + waga 1 ) - na tym GPU weight potrafil wyjsc zerowy i
        // suwak splaszcza sie do poziomej pigulki z wielka ikona. Teraz czysty
        // FrameLayout. Smuklejszy tor jak w iOS Control Center, mniejsza ikona
        // przy dolnej krawedzi.
        public PillSlider(Context c, boolean sun, float v) {
            super(c);
            // tor = ciemne szklo przepuszczajace rozmyte tlo (pigulka jak w iOS)
            GradientDrawable track = new GradientDrawable();
            track.setColor(Look.night(c) ? 0xFF4A4F50 : 0xFFD1D1D6);
            track.setCornerRadius(999f);
            setBackground(track);
            value = Math.max(0f, Math.min(1f, v));

            // Wypelnienie od dolu jako pelna pigulka przycieta prosta pozioma do
            // poziomu wypelnienia (FillDrawable ponizej). Dzieki temu:
            //  - dol ZAWSZE dokladnie wypelnia polokragly dol toru,
            //  - gorna krawedz jest CALY CZAS PROSTA (zadnej kopulki),
            //  - a przy poziomie dochodzacym do gory krawedz chowa sie w
            //    polokragly gor toru (boki podazaja za okregiem).
            // Dlaczego nie clipToOutline: przy warstwie SOFTWARE (API < 30,
            // Mali-T720) przycinanie do obrysu NIE dziala - stad wlasny Drawable
            // z clipRect, rysowany ta sama sciezka co backgroundy GradientDrawable
            // (czyli renderujace sie poprawnie na tym GPU).
            fill = new View(c);
            fillBg = new FillDrawable(0xFFFAFAFC, dp(4));
            fill.setBackground(fillBg);
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            addView(fill, flp);

            // przyciecie zawartosci do pigulki (odpowiednik clipPath w oryginale)
            android.view.ViewOutlineProvider op = new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            view.getWidth() / 2f);
                }
            };
            setOutlineProvider(op);
            setClipToOutline(true);

            // ikona przy dolnej krawedzi (Bitmapa -> ImageView, framework)
            icon = new ImageView(c);
            icon.setImageBitmap(makeSliderIcon(c, sun));
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(34), dp(34),
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            ilp.bottomMargin = dp(17);
            addView(icon, ilp);

            setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    return handleTouch(e);
                }
            });
        }

        public void setListener(Listener l) {
            this.l = l;
        }

        // ustawia wartosc z zewnatrz (nie w trakcie przesuwania palcem)
        public void setValue(float v) {
            if (dragging) return;
            value = Math.max(0f, Math.min(1f, v));
            applyFill();
            icon.setAlpha(value < 0.05f ? 120 : 255);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            applyFill();
        }

        private void applyFill() {
            if (fillBg != null) fillBg.setFrac(value);
        }

        /** Tlo wypelnienia: pelna pigulka (drawRoundRect o promieniu pol szerokosci)
         *  przycieta clipRect do poziomu wypelnienia. Widoczny ksztalt = przeciecie
         *  pigulki toru z prosta pozioma: dol dokladnie polokragly, gorna krawedz
         *  prosta i wpasowana w polokragly gor toru przy kazdym poziomie. */
        private static class FillDrawable extends android.graphics.drawable.Drawable {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final int insetPx;
            private float frac = 0f;

            FillDrawable(int color, int insetPx) {
                p.setColor(color);
                this.insetPx = insetPx;
            }

            void setFrac(float f) {
                float nf = Math.max(0f, Math.min(1f, f));
                if (nf != frac) {
                    frac = nf;
                    invalidateSelf();
                }
            }

            @Override
            public void draw(Canvas cv) {
                RectF b = new RectF(getBounds());
                float w = b.width();
                float h = b.height();
                if (w <= 0 || h <= 0) return;
                float r = w / 2f;
                cv.save();
                cv.clipRect(0f, h - frac * (h - insetPx), w, h);
                cv.drawRoundRect(b, r, r, p);
                cv.restore();
            }

            @Override
            public void setAlpha(int alpha) {
                p.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter cf) {
                p.setColorFilter(cf);
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        }

        private boolean handleTouch(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                dragging = true;
                longFired = false;
                downY = e.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                if (longPress != null) removeCallbacks(longPress);
                longPress = new Runnable() {
                    @Override
                    public void run() {
                        longFired = true;
                        dragging = false;
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        if (l != null) l.onLong();
                    }
                };
                postDelayed(longPress, 550);
            }
            if (a == MotionEvent.ACTION_MOVE && longPress != null
                    && Math.abs(e.getY() - downY) > 8f * d) {
                removeCallbacks(longPress);
                longPress = null;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (longPress != null) {
                    removeCallbacks(longPress);
                    longPress = null;
                }
                dragging = false;
            }
            if (longFired && (a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP)) {
                return true;   // po dlugim przytrzymaniu nie przestawiaj wartosci
            }
            // Wartosc ustawiaja dopiero MOVE/UP, nie DOWN: samo przytrzymanie
            // pigulki (long-press 550 ms -> suwak ciepla Night Shift) nie moze
            // przeskakiwac wypelnienia do palca - inaczej pigulka pokazuje inna
            // wartosc niz faktycznie jest (desync do najblizszego update()).
            // Tap-to-set obsluguje UP, drag - MOVE.
            if (a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
                int h = getHeight();
                if (h > 0) {
                    value = Math.max(0f, Math.min(1f, 1f - e.getY() / h));
                    applyFill();
                    icon.setAlpha(value < 0.05f ? 120 : 255);
                    if (l != null) {
                        l.on(value);
                    }
                }
            }
            return true;
        }

        // ikony suwakow 1:1 z Liquid-Glass: zolte slonce (kolo + promienie),
        // niebieski glosnik (path + luk) - rysowane raz na bitmapie CPU
        private static Bitmap makeSliderIcon(Context c, boolean sun) {
            float den = c.getResources().getDisplayMetrics().density;
            int px = (int) Math.ceil(34f * den);
            Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            float cx = px / 2f;
            float cy = px / 2f;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (sun) {
                p.setColor(0xFFF2C94C);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy, 7f * den, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.4f * den);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    cv.drawLine(cx + (float) Math.cos(a) * 11f * den, cy + (float) Math.sin(a) * 11f * den,
                            cx + (float) Math.cos(a) * 15f * den, cy + (float) Math.sin(a) * 15f * den, p);
                }
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF5AC8FA);
                Path sp = new Path();
                sp.moveTo(cx - 12f * den, cy - 5f * den);
                sp.lineTo(cx - 6f * den, cy - 5f * den);
                sp.lineTo(cx + 1f * den, cy - 11f * den);
                sp.lineTo(cx + 1f * den, cy + 11f * den);
                sp.lineTo(cx - 6f * den, cy + 5f * den);
                sp.lineTo(cx - 12f * den, cy + 5f * den);
                sp.close();
                cv.drawPath(sp, p);
                p.setStyle(Paint.Style.STROKE);
                cv.drawArc(new RectF(cx - 2f * den, cy - 7f * den, cx + 10f * den, cy + 7f * den),
                        -50, 100, false, p);
            }
            return b;
        }
    }
}
