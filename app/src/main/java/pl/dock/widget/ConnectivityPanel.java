package pl.dock.widget;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
    private TextView btnEdit;
    private TextView btnAdd;
    private TextView note;
    // narzedzia stalego modulu bazowego - nie moga sie powtarzac w edytowalnej siatce
    static final String[] BASE_IDS =
            {"bri", "vol", "nightshift", "mode", "wifi", "bt", "hot", "conn"};
    private NightShiftRow nightRow;
    private ModeSegmentView modeSeg;
    private final List<View> railTiles = new ArrayList<View>();
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
        // iOS Control Center: brak karty - tresc lezy bezposrednio na rozmytym
        // tle ekranu (migawka + Blur w LauncherActivity). Klikalne, by tap w tlo
        // panelu nie zamykal nakladki.
        setBackground(null);
        setPadding(dp(16), dp(14), dp(16), dp(12));
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
        g.setColor(night ? 0x24FFFFFF : 0x14000000);
        g.setCornerRadius(dp(28));
        g.setStroke(Math.max(1, dp(1)), night ? 0x33FFFFFF : 0x1E787880);
        return g;
    }

    private void build() {
        // naglowek: tytul + przycisk Edytuj / Gotowe
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(act);
        title.setText("Łączność");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        head.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnEdit = pill("Edytuj");
        btnEdit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editMode) exitEditMode();
                else enterEditMode();
            }
        });
        head.addView(btnEdit, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        LayoutParams hp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.leftMargin = dp(6);
        hp.bottomMargin = dp(10);
        addView(head, hp);

        // ===== STALY MODUL BAZOWY (zawsze widoczny po otwarciu) =====
        // Jak w iOS: karta lacznosci po LEWEJ, suwaki po PRAWEJ.
        // Stale dp zamiast weight (Mali-T720): 126 + 10 + 190 + 12 + 190 = 528 dp
        // = panel 560 dp minus padding 2 x 16 dp; wysokosc modulow jednakowa.
        LinearLayout main = new LinearLayout(act);
        main.setOrientation(HORIZONTAL);

        // LEWA kolumna: szklana karta lacznosci jak w iOS - trzy okragle
        // PRZELACZNIKI Wi-Fi / Bluetooth / Hotspot (kolo + etykieta pod spodem)
        LinearLayout rail = new LinearLayout(act);
        rail.setOrientation(VERTICAL);
        rail.setPadding(dp(10), 0, dp(10), 0);
        rail.setBackground(glassBg());
        buildRail(rail);
        LayoutParams rlp = new LayoutParams(dp(126), dp(240));
        rlp.rightMargin = dp(10);
        main.addView(rail, rlp);

        // suwak jasnosci | suwak glosnosci (PillSlider - tylko widoki frameworka)
        LinearLayout sliders = new LinearLayout(act);
        sliders.setOrientation(HORIZONTAL);
        briSlider = new PillSlider(act, true, brightnessValue());
        briSlider.setListener(new PillSlider.Listener() {
            @Override
            public void on(float v) {
                setBrightness(v);
            }

            @Override
            public void onLong() {
                showWarmthSlider();   // jak w iOS: przytrzymanie jasnosci = cieplo ekranu
            }
        });
        // stale dp zamiast weight: na tym GPU (Mali-T720) dzieci z weight potrafia
        // wyjsc zerowe - caly uklad panelu uzywa wylacznie stalych dp.
        // Suwaki maja 240 dp wysokosci - tyle samo, co karta lacznosci obok
        // (jak w iOS: suwaki i modul lacznosci sa jednej wysokosci).
        LayoutParams blp = new LayoutParams(dp(190), dp(240));
        blp.rightMargin = dp(6);
        sliders.addView(briSlider, blp);
        volSlider = new PillSlider(act, false, volumeValue());
        volSlider.setListener(new PillSlider.Listener() {
            @Override
            public void on(float v) {
                setVolume(v);
            }

            @Override
            public void onLong() {
                openAndClose("android.settings.SOUND_SETTINGS");
            }
        });
        LayoutParams vlp = new LayoutParams(dp(190), dp(240));
        vlp.leftMargin = dp(6);
        sliders.addView(volSlider, vlp);
        main.addView(sliders, new LayoutParams(dp(392), dp(240)));

        addView(main, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ===== MODUL WYSWIETLACZA (jak w iOS) =====
        // Pelnoszerokosciowa karta pod modulem glownym - jak modul z okraglymi
        // przyciskami Tryb ciemny / Night Shift / True Tone w Control Center.
        // NightShiftRow to okragly przycisk z podpisem i podpodpisem stanu,
        // ModeSegmentView to trzy okragle przyciski trybu wygladu.
        if (Prefs.showNightShift(act) || Prefs.showModeButtons(act)) {
            LinearLayout dcard = new LinearLayout(act);
            dcard.setOrientation(HORIZONTAL);
            dcard.setGravity(Gravity.CENTER);
            dcard.setPadding(dp(8), dp(8), dp(8), dp(8));
            dcard.setBackground(glassBg());
            if (Prefs.showNightShift(act)) {
                nightRow = new NightShiftRow(act);
                LayoutParams nlp = new LayoutParams(dp(124), dp(88));
                nlp.rightMargin = dp(8);
                dcard.addView(nightRow, nlp);
            }
            if (Prefs.showModeButtons(act)) {
                modeSeg = new ModeSegmentView(act);
                dcard.addView(modeSeg, new LayoutParams(dp(252), dp(88)));
            }
            LayoutParams dlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104));
            dlp.topMargin = dp(12);
            addView(dcard, dlp);
        }

        // podpis trybu automatycznego (od zachodu do wschodu slonca)
        note = new TextView(act);
        note.setTextSize(13);
        note.setTextColor(night ? 0xFF9A9AA0 : 0xFF6C6C70);
        note.setPadding(dp(6), dp(6), 0, dp(4));
        note.setVisibility(GONE);
        addView(note);

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
        // narzedzia ze stalego modulu bazowego nigdy nie duplikuja sie w siatce
        toolIds = withoutBaseTools(toolIds);
        grid.setTools(toolIds);
        LayoutParams gp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = dp(10);
        addView(grid, gp);

        // dlugie przytrzymanie tla = tryb edycji
        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                enterEditMode();
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
    }

    // ---------------------------------------------------------------- kafelki Wi-Fi / BT / Hotspot
    private void buildRail(LinearLayout rail) {
        String[] railIds = {"wifi", "bt", "hot"};
        for (String rid : railIds) {
            final PanelTool.Def def = PanelTool.find(rid);
            if (def == null) continue;
            final View tile = ToolGrid.createTile(act, night, def);
            tile.setTag(def);
            tile.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onRailTap(def);
                }
            });
            tile.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    onRailLong(def);
                    return true;
                }
            });
            // 3 x (72 + 8) = 240 dp = wysokosc karty (marginesy pionowe 4 dp)
            LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
            tlp.topMargin = dp(4);
            tlp.bottomMargin = dp(4);
            rail.addView(tile, tlp);
            railTiles.add(tile);
        }
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
        btnEdit.setText("Gotowe");
        try {
            if (grid != null) grid.setEditing(true);
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
        btnEdit.setText("Edytuj");
        try {
            if (grid != null) grid.setEditing(false);
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
            Settings.Global.putInt(act.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, next ? 1 : 0);
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
            Settings.System.putInt(act.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, cur == 1 ? 0 : 1);
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
        if (modeSeg != null) modeSeg.refreshSelection();
        if (nightRow != null) nightRow.refresh();
        for (int i = 0; i < railTiles.size(); i++) {
            PanelTool.Def d = (PanelTool.Def) railTiles.get(i).getTag();
            if (d != null) ToolGrid.refreshTile(act, night, railTiles.get(i), d);
        }
        if (note != null) {
            boolean nsSun = "sunset".equals(NightShift.mode(act));
            boolean follow = Prefs.followSystem(act);
            note.setText(nsSun ? "Night Shift automatyczny: od zachodu do wschodu slonca"
                    : (follow ? "Wyglad automatyczny: od zachodu do wschodu slonca" : ""));
            note.setVisibility(note.getText().length() > 0 ? VISIBLE : GONE);
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
        exitEditMode();
        try {
            if (rc != null) act.unregisterReceiver(rc);
        } catch (Throwable ignored) {
        }
        rc = null;
    }

    // ---------------------------------------------------------------- Night Shift (framework-only)
    // Okragly przycisk jak w Control Center (screen "Night Shift / wl."):
    // kolko z ikona slonca, pod nim pogrubiony podpis "Night Shift"
    // i drobny podpodpis ze stanem. Tap = cykl trybow, przytrzymanie = cieplo.
    private class NightShiftRow extends LinearLayout {
        private final View circleBg;
        private final TextView label;
        private final TextView sub;
        private String mode = "off";
        private boolean on;

        NightShiftRow(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);

            FrameLayout circ = new FrameLayout(c);
            circleBg = new View(c);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(48), dp(48),
                    Gravity.CENTER);
            circ.addView(circleBg, clp);
            ImageView icon = new ImageView(c);
            icon.setImageBitmap(glyphBitmap("sun", dp(24), 0xFFFFFFFF));
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(26), dp(26),
                    Gravity.CENTER);
            circ.addView(icon, ilp);
            addView(circ, new LayoutParams(dp(48), dp(48)));

            label = new TextView(c);
            label.setText("Night Shift");
            label.setTextSize(12);
            label.setTypeface(null, Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(night ? 0xFFFFFFFF : 0xFF111114);
            addView(label, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            sub = new TextView(c);
            sub.setTextSize(10);
            sub.setGravity(Gravity.CENTER);
            sub.setTextColor(night ? 0x99EBEBF5 : 0x993C3C43);
            addView(sub, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            refresh();
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycle();
                }
            });
            setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showWarmthSlider();
                    return true;
                }
            });
        }

        void refresh() {
            mode = NightShift.mode(act);
            on = NightShift.isOn(act);
            // kolko: pomaranczowe jak w iOS gdy wlaczone, inaczej systemowa szarosc
            circleBg.setBackground(Ui.round(on ? 0xFFFF9F0A
                    : (night ? 0x4D787880 : 0xFFE4E4EA), 999, act));
            sub.setText("sunset".equals(mode) ? "Automatyczny"
                    : ("on".equals(mode) ? "Włączony" : "Wyłączony"));
        }

        private void cycle() {
            String next = NightShift.nextMode(mode);
            boolean ok = NightShift.setMode(act, next);
            if (!ok) {
                host.close();
                return;
            }
            refresh();
            update();
        }
    }

    // ---------------------------------------------------------------- tryb wygladu (framework-only)
    // Trzy okragle przyciski jak w module wyswietlacza iOS (screeny "Tryb ciemny"):
    // slonce = Dzienny, ksiiezyc = Nocny, pol-kolo = Auto. Wybrany ma niebieskie
    // kolko i pogrubiony podpis. Logika wyboru bez zmian (Prefs.setMode/followSystem).
    private class ModeSegmentView extends LinearLayout {
        private final View[] bgs = new View[3];
        private final TextView[] labels = new TextView[3];
        private final String[] glyphs = {"sun", "moon", "half"};
        private final String[] texts = {"Dzienny", "Nocny", "Auto"};

        ModeSegmentView(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                LinearLayout btn = new LinearLayout(c);
                btn.setOrientation(VERTICAL);
                btn.setGravity(Gravity.CENTER_HORIZONTAL);

                FrameLayout circ = new FrameLayout(c);
                bgs[i] = new View(c);
                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(48), dp(48),
                        Gravity.CENTER);
                circ.addView(bgs[i], clp);
                ImageView icon = new ImageView(c);
                icon.setImageBitmap(glyphBitmap(glyphs[i], dp(24), 0xFFFFFFFF));
                FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(26), dp(26),
                        Gravity.CENTER);
                circ.addView(icon, ilp);
                btn.addView(circ, new LayoutParams(dp(48), dp(48)));

                labels[i] = new TextView(c);
                labels[i].setText(texts[i]);
                labels[i].setTextSize(11);
                labels[i].setGravity(Gravity.CENTER);
                btn.addView(labels[i], new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                btn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        select(idx);
                    }
                });
                LayoutParams blp = new LayoutParams(dp(84),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                addView(btn, blp);
            }
            refreshSelection();
        }

        void refreshSelection() {
            int sel = Prefs.followSystem(act) ? 2 : ("night".equals(Prefs.mode(act)) ? 1 : 0);
            for (int i = 0; i < 3; i++) {
                boolean s = i == sel;
                bgs[i].setBackground(Ui.round(s ? 0xFF0A84FF
                        : (night ? 0x4D787880 : 0xFFE4E4EA), 999, act));
                labels[i].setTextColor(s ? 0xFFFFFFFF
                        : (night ? 0x99EBEBF5 : 0x993C3C43));
                labels[i].setTypeface(null, s ? Typeface.BOLD : Typeface.NORMAL);
            }
        }

        private void select(int idx) {
            if (idx == 0) {
                Prefs.setFollowSystem(act, false);
                Prefs.setMode(act, "day");
            } else if (idx == 1) {
                Prefs.setFollowSystem(act, false);
                Prefs.setMode(act, "night");
            } else {
                Prefs.setFollowSystem(act, true);
            }
            refreshSelection();
            applyMode();
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
    public static class PillSlider extends LinearLayout {
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
        private final ImageView icon;
        private Listener l;

        private int dp(float v) {
            return Math.round(v * d);
        }

        public PillSlider(Context c, boolean sun, float v) {
            super(c);
            setOrientation(VERTICAL);
            // tor (track) rysuje GradientDrawable - framework
            // tor = ciemne szklo przepuszczajace rozmyte tlo (pigulka jak w iOS)
            GradientDrawable track = new GradientDrawable();
            track.setColor(Look.night(c) ? 0x993A3A3C : 0xCCE1E1E7);
            track.setCornerRadius(999f);
            setBackground(track);
            value = Math.max(0f, Math.min(1f, v));

            // wypelnienie od dolu - zwykly View z bialym GradientDrawable
            fill = new View(c);
            fill.setBackground(Ui.round(0xF5FFFFFF, 999, c));
            FrameLayout froot = new FrameLayout(c);
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.BOTTOM);
            flp.setMargins(dp(2), dp(2), dp(2), dp(2));
            froot.addView(fill, flp);
            addView(froot, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            // ikona na dole (Bitmapa -> ImageView, framework)
            icon = new ImageView(c);
            int ink = sun ? 0xFFF2C94C : 0xFF8E8E93;
            icon.setImageBitmap(makeSliderIcon(c, sun, ink));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(38), dp(38));
            ilp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            ilp.bottomMargin = dp(6);
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
            int h = getHeight() - dp(4);
            if (h <= 0) return;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
            lp.height = Math.max(1, Math.round(h * value));
            fill.setLayoutParams(lp);
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
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE
                    || a == MotionEvent.ACTION_UP) {
                int h = getHeight();
                if (h > 0) {
                    value = Math.max(0f, Math.min(1f, 1f - e.getY() / h));
                    applyFill();
                    icon.setAlpha(value < 0.05f ? 120 : 255);
                    if (l != null && a != MotionEvent.ACTION_DOWN) {
                        l.on(value);
                    }
                }
            }
            return true;
        }

        private static Bitmap makeSliderIcon(Context c, boolean sun, int ink) {
            float den = c.getResources().getDisplayMetrics().density;
            int px = (int) Math.ceil(38f * den);
            Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            PanelTool.glyph(cv, sun ? "sun" : "speaker", px / 2f, px / 2f, 32f * den, ink);
            return b;
        }
    }
}
