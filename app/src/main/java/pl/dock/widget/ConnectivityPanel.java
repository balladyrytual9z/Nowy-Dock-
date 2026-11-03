package pl.dock.widget;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

// Centrum łączności w stylu iOS. Zawartość to siatka narzędzi (ToolGrid), którą można
// układać po swojemu: przycisk „Edytuj” (albo długie przytrzymanie tła) włącza tryb edycji,
// w którym kafelki można przeciągać, usuwać i dodawać nowe z listy narzędzi i skrótów do ustawień.
// Używane w launcherze (nakładka) i jako okno zapasowe.
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
    private ModeSegment modeSeg;
    private LinearLayout warmthRow;
    private android.widget.SeekBar warmthBar;
    private PillSlider volSlider;
    private PillSlider briSlider;
    private ConnGroup connGroup;
    private BroadcastReceiver rc;
    private boolean editMode = false;
    private final List<View> staggerViews = new ArrayList<View>();
    private final Runnable refreshLater = new Runnable() {
        @Override
        public void run() {
            update();
        }
    };

    private int dp(float v) {
        return Ui.dp(act, v);
    }

    public ConnectivityPanel(Activity a, boolean night, Host host) {
        super(a);
        this.act = a;
        this.night = night;
        this.host = host;
        PanelTool.init(a);
        setOrientation(VERTICAL);
        setBackground(Ui.popupBg(a, night));
        setPadding(dp(12), dp(12), dp(12), dp(10));
        setClickable(true);
        setElevation(dp(12));
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
        t.setBackground(Ui.round(night ? 0x33FFFFFF : 0x14000000, 18, act));
        return t;
    }

    private void build() {
        // nagłówek: tytuł + przycisk Edytuj / Gotowe
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View titleSpacer = new View(act);
        head.addView(titleSpacer, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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
        staggerViews.add(head);

        // siatka narzędzi
        grid = new ToolGrid(act, night, new ToolGrid.Host() {
            @Override
            public View create(PanelTool.Def d) {
                return makeView(d);
            }

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
        grid.setTools(PanelTool.load(act));
        addView(grid, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        staggerViews.add(grid);

        // długie przytrzymanie tła = tryb edycji
        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                enterEditMode();
                return true;
            }
        });

        // przycisk „Dodaj narzędzie” (widoczny tylko w trybie edycji)
        btnAdd = pill("＋  Dodaj narzędzie");
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

        note = new TextView(act);
        note.setTextSize(13);
        note.setTextColor(night ? 0xFF9A9AA0 : 0xFF6C6C70);
        note.setPadding(dp(6), dp(6), 0, dp(4));
        note.setVisibility(GONE);
        addView(note);

        post(new Runnable() {
            @Override
            public void run() {
                playStaggerIn();
            }
        });
    }

    // własne widoki dla suwaków i przełącznika wyglądu; reszta to zwykłe kafelki
    private View makeView(PanelTool.Def d) {
        if (d.kind == PanelTool.K_VOLUME) {
            PillSlider s = new PillSlider(act, false, volumeValue());
            s.setListener(new PillSlider.Listener() {
                @Override
                public void on(float v) {
                    setVolume(v);
                }

                @Override
                public void onLong() {
                    openAndClose("android.settings.SOUND_SETTINGS");
                }
            });
            volSlider = s;
            return s;
        }
        if (d.kind == PanelTool.K_BRIGHT) {
            PillSlider s = new PillSlider(act, true, brightnessValue());
            s.setListener(new PillSlider.Listener() {
                @Override
                public void on(float v) {
                    setBrightness(v);
                }

                @Override
                public void onLong() {
                    showWarmthSlider();   // jak w iOS: przytrzymanie jasnosci = cieplo ekranu
                }
            });
            briSlider = s;
            return s;
        }
        if (d.kind == PanelTool.K_MODE) {
            modeSeg = new ModeSegment(act);
            return modeSeg;
        }
        if (d.kind == PanelTool.K_GROUP) {
            // grupa lacznosci 2x2 jak w iOS: samolotowy / hotspot / Wi-Fi / Bluetooth
            String[] gids = {"air", "hot", "wifi", "bt"};
            int n = 0;
            for (String gid : gids) if (PanelTool.find(gid) != null) n++;
            if (n == 0) return null;
            PanelTool.Def[] defs = new PanelTool.Def[n];
            int k = 0;
            for (String gid : gids) {
                PanelTool.Def gd = PanelTool.find(gid);
                if (gd != null) defs[k++] = gd;
            }
            connGroup = new ConnGroup(act, defs);
            return connGroup;
        }
        return null;
    }

    /** iOS-style staggered spring entrance for modules. */
    private void playStaggerIn() {
        long delay = 0;
        for (View v : staggerViews) {
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(dp(18));
            final View fv = v;
            final long dl = delay;
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    fv.animate()
                            .alpha(1f).translationY(0f)
                            .setDuration(320)
                            .setInterpolator(pl.dock.widget.ui.DockMotion.IOS_SPRING)
                            .start();
                }
            }, dl);
            delay += 55;
        }
    }

    // ---------------------------------------------------------------- tryb edycji

    public void enterEditMode() {
        if (editMode) return;
        editMode = true;
        grid.setEditing(true);
        btnEdit.setText("Gotowe");
        btnAdd.setAlpha(0f);
        btnAdd.setScaleX(0.9f);
        btnAdd.setScaleY(0.9f);
        btnAdd.setVisibility(VISIBLE);
        btnAdd.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(pl.dock.widget.ui.DockMotion.IOS_SPRING).start();
    }

    public void exitEditMode() {
        if (!editMode) return;
        editMode = false;
        grid.setEditing(false);
        btnEdit.setText("Edytuj");
        btnAdd.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(180)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        btnAdd.setVisibility(GONE);
                    }
                }).start();
    }

    private void openPicker() {
        ToolPicker.show(act, night, grid.ids(), new ToolPicker.OnPick() {
            @Override
            public void pick(String id) {
                grid.addTool(id);
            }
        });
    }

    // ---------------------------------------------------------------- działanie narzędzi

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
        Toast.makeText(act, "Ta funkcja jest niedostępna na tym radiu", Toast.LENGTH_SHORT).show();
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
            if (!Net.openTetherSettings(act)) unavailable();
            else host.close();
        } else if ("wifi".equals(id)) {
            if (longPress) {
                if (!Net.openWifiSettings(act)) unavailable();
                else host.close();
            } else {
                // najpierw sprobuj przeplaczyc Wi-Fi bezposrednio (Android 9 pozwala);
                // jesli sie nie da - otworz ustawienia Wi-Fi jak wroadziej
                boolean before = Net.wifiEnabled(act);
                if (Net.setWifi(act, !before)) {
                    refreshSoon();
                } else if (!Net.openWifiSettings(act)) {
                    unavailable();
                } else {
                    host.close();
                }
            }
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
                // cykl jak w iPhonie: wylaczony -> wlaczony -> od zachodu do wschodu
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
                // jednorazowa zgoda na zmianę ustawień systemu
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
            // zwykła aplikacja nie może tego zmienić – otwórz ustawienia
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
        warmthRow.setBackground(Ui.round(night ? 0x33FFFFFF : 0x14000000, 18, act));

        // Ten sam suwak co w Ustawienia > Ekran > Night Shift:
        // Chlodniejsze <-> Cieplejsze (jak temperatury kolorow w iOS)
        TextView cool = new TextView(act);
        cool.setText("Chłodniejsze");
        cool.setTextSize(13);
        cool.setTypeface(null, Typeface.BOLD);
        cool.setTextColor(night ? 0xB3FFFFFF : 0xFF3C3C43);
        warmthRow.addView(cool, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        warmthBar = new android.widget.SeekBar(act);
        warmthBar.setMax(80);
        warmthBar.setProgress(Math.max(5, Math.min(80, Prefs.nightShiftStrength(act))));
        LayoutParams bp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
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
                NightShift.refresh(act);   // nakladka zmienia kolor na zywo
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar sb) {
                removeCallbacks(hideWarmth);
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar sb) {
                // Caly mechanizm jest wbudowany w Docka - nowa sila zostala juz
                // ustawiona na zywo w onProgressChanged (NightShift.refresh).
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
                    Net.openBluetoothApp(act);   // radio przejmuje Bluetooth: otwórz jego aplikację
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
        if (connGroup != null) connGroup.refresh();
        if (volSlider != null) volSlider.setValue(volumeValue());
        if (briSlider != null) briSlider.setValue(brightnessValue());
        if (modeSeg != null) modeSeg.refreshSelection();
        if (note != null) {
            boolean follow = Prefs.followSystem(act) && grid != null && grid.has("mode");
            note.setText(follow ? "Wygląd automatyczny: od zachodu do wschodu słońca" : "");
            note.setVisibility(follow ? VISIBLE : GONE);
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
        update();
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

    // ------------------------------------------------- grupa lacznosci 2x2 jak w Control Center iOS
    // (tryb samolotowy / hotspot / Wi-Fi / Bluetooth w jednym zaokraglonym module)
    private class ConnGroup extends View {
        private final PanelTool.Def[] defs;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d = getResources().getDisplayMetrics().density;
        private final boolean[] on = new boolean[4];
        private int pressed = -1;
        private boolean longFired;
        private float downX, downY;
        private Runnable longPress;

        ConnGroup(Context c, PanelTool.Def[] defs) {
            super(c);
            this.defs = defs;
            setClickable(true);
            setContentDescription("Łączność");
        }

        void refresh() {
            for (int i = 0; i < defs.length; i++) on[i] = PanelTool.isOn(act, defs[i]);
            invalidate();
        }

        private int cols() {
            return defs.length <= 2 ? 1 : 2;
        }

        private int hit(float x, float y) {
            int n = defs.length;
            if (n <= 1) return 0;
            int c = cols();
            int rows = (n + c - 1) / c;
            int col = (int) Math.max(0, Math.min(c - 1, x / (getWidth() / (float) c)));
            int row = (int) Math.max(0, Math.min(rows - 1, y / (getHeight() / (float) rows)));
            return Math.min(n - 1, row * c + col);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            int n = defs.length;
            // modul w tle: polprzezroczysty zaokraglony prostokat jak w iOS
            float mod = 22f * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(night ? 0x2E787880 : 0x1E787880);
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, mod, mod, p);

            int c = cols();
            int rows = (n + c - 1) / c;
            float cw = w / c;
            float ch = h / rows;
            float dia = Math.min(cw, ch) - 12f * d;
            if (dia < 20f * d) dia = Math.max(10f * d, Math.min(cw, ch) * 0.8f);
            for (int i = 0; i < n; i++) {
                int col = i % c;
                int row = i / c;
                float cx = cw * col + cw / 2f;
                float cy = ch * row + ch / 2f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(on[i] ? PanelTool.onColor(defs[i]) : (night ? 0x33FFFFFF : 0x2E3C3C43));
                cv.drawCircle(cx, cy, dia / 2f, p);
                if (i == pressed) {
                    p.setColor(0x22000000);
                    cv.drawCircle(cx, cy, dia / 2f, p);
                }
                int ink = on[i] ? 0xFFFFFFFF : (night ? 0xFFFFFFFF : 0xFF3C3C43);
                PanelTool.glyph(cv, defs[i].glyph, cx, cy, dia * 0.32f, ink);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                pressed = hit(downX, downY);
                longFired = false;
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                if (longPress != null) removeCallbacks(longPress);
                // dlugie przytrzymanie = tryb edycji, jak przy pozostalych kafelkach
                longPress = new Runnable() {
                    @Override
                    public void run() {
                        longFired = true;
                        pressed = -1;
                        invalidate();
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        enterEditMode();
                    }
                };
                postDelayed(longPress, 550);
                return true;
            }
            if (a == MotionEvent.ACTION_MOVE && longPress != null
                    && (Math.abs(e.getX() - downX) > 10f * d || Math.abs(e.getY() - downY) > 10f * d)) {
                removeCallbacks(longPress);
                longPress = null;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (longPress != null) {
                    removeCallbacks(longPress);
                    longPress = null;
                }
                int pr = pressed;
                int hitIdx = hit(e.getX(), e.getY());
                pressed = -1;
                invalidate();
                if (!longFired && a == MotionEvent.ACTION_UP
                        && pr >= 0 && pr == hitIdx && pr < defs.length) {
                    runTool(defs[pr], false);
                }
                longFired = false;
                return true;
            }
            return true;
        }
    }

    // ------------------------------------------------- jeden przycisk wygladu: klik = nastepny stan
    // (Jasny -> Ciemny -> Auto -> Jasny), jak przycisk jasnosci w iPhonie
    private class ModeSegment extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d = getResources().getDisplayMetrics().density;
        private final String[] labels = {"Jasny", "Ciemny", "Auto"};
        private int selected; // 0 day, 1 night, 2 auto
        private boolean pressed = false;

        ModeSegment(Context c) {
            super(c);
            refreshSelection();
            setClickable(true);
        }

        void refreshSelection() {
            boolean follow = Prefs.followSystem(act);
            String m = Prefs.mode(act);
            if (follow) selected = 2;
            else if ("night".equals(m)) selected = 1;
            else selected = 0;
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float rad = 18f * d;
            RectF track = new RectF(0, 0, w, h);

            // tl segmentu (jak w iOS)
            p.setStyle(Paint.Style.FILL);
            p.setColor(night ? 0xF0606767 : 0xFFE4E4EA);
            cv.drawRoundRect(track, rad, rad, p);

            // wybrana pigulka na cala szerokosc - podswietlona jak aktywny przycisk
            float pad = 3f * d;
            RectF pill = new RectF(pad, pad, w - pad, h - pad);
            p.setColor(selected == 1 ? 0xFF0A84FF   // Ciemny - niebieski akcent
                            : selected == 2 ? (night ? 0xFF48484A : 0xFFC7C7CC)
                            : (night ? 0xFF3A3A3C : 0xFFFFFFFF));
            cv.drawRoundRect(pill, rad - 2f, rad - 2f, p);
            if (pressed) {
                p.setColor(0x22000000);
                cv.drawRoundRect(pill, rad - 2f, rad - 2f, p);
            }

            // subtelna ramka
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * d);
            p.setColor(0x44FFFFFF);
            cv.drawRoundRect(track, rad, rad, p);

            // etykieta aktualnego stanu + wskazowka kolejnego stanu
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float sd = getResources().getDisplayMetrics().scaledDensity;
            p.setTextSize(15f * sd);
            int ink = selected == 1 ? 0xFFFFFFFF : (night ? 0xFFFFFFFF : 0xFF1C1C1E);
            p.setColor(ink);
            cv.drawText(labels[selected], w / 2f, h / 2f + 5.5f * d, p);

            // male kropki stanow pod etykieta (1 z 3)
            float dotR = 2.2f * d;
            float gap = 9f * d;
            float x0 = w / 2f - gap;
            for (int i = 0; i < 3; i++) {
                p.setColor(i == selected ? (selected == 1 ? 0xFFFFFFFF : 0xFF0A84FF)
                        : (night ? 0x66FFFFFF : 0x33000000));
                cv.drawCircle(x0 + i * gap, h - 7f * d, dotR, p);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                pressed = true;
                invalidate();
                animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
                return true;
            }
            if (a == MotionEvent.ACTION_UP) {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                pressed = false;
                invalidate();
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                // cykl stanow: Jasny -> Ciemny -> Auto -> Jasny
                int next = (selected + 1) % 3;
                if (next == 0) {
                    Prefs.setFollowSystem(act, false);
                    Prefs.setMode(act, "day");
                } else if (next == 1) {
                    Prefs.setFollowSystem(act, false);
                    Prefs.setMode(act, "night");
                } else {
                    Prefs.setFollowSystem(act, true);
                }
                refreshSelection();
                applyMode();
                if (host != null) host.modeChanged();
                return true;
            }
            if (a == MotionEvent.ACTION_CANCEL) {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                pressed = false;
                invalidate();
            }
            return true;
        }
    }



    // ---------------------------------------------------------------- pionowy suwak
    public static class PillSlider extends View {
        public interface Listener {
            void on(float v);

            /** iOS: dlugie przytrzymanie suwaka = dodatkowe opcje. */
            void onLong();
        }

        private float value;
        private boolean dragging;
        private float downY;
        private boolean longFired;
        private Runnable longPress;
        private final boolean sun;
        private final boolean night;
        private final int tint;
        private Listener l;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d = getResources().getDisplayMetrics().density;

        public PillSlider(Context c, boolean sun, float v) {
            super(c);
            this.sun = sun;
            this.value = Math.max(0f, Math.min(1f, v));
            this.night = Look.night(c);
            this.tint = 0xFFFFFFFF;   // jak w iOS: biale wypelnienie obu suwakow
            setContentDescription(sun ? "Jasność ekranu" : "Głośność");
        }

        public void setListener(Listener l) {
            this.l = l;
        }

        // ustawia wartość z zewnątrz (nie w trakcie przesuwania palcem)
        public void setValue(float v) {
            if (dragging) return;
            value = Math.max(0f, Math.min(1f, v));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float rad = w / 2f;
            // Track (dark gray)
            p.setStyle(Paint.Style.FILL);
            p.setColor(night ? 0xFF3A3A3C : 0xFFD1D1D6);
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, rad, rad, p);
            // iOS 26 Liquid Glass: subtelny szklany obrys suwaka
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * d);
            p.setColor(night ? 0x33FFFFFF : 0x14000000);
            cv.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
            // White fill from bottom
            int save = cv.save();
            Path clip = new Path();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            cv.clipPath(clip);
            p.setColor(tint);
            cv.drawRect(0, h * (1f - value), w, h, p);
            cv.restoreToCount(save);

            // Icon at bottom (iOS style) – gray when value ~0
            float cx = w / 2f;
            float cy = h - 28f * d;
            boolean dim = value < 0.05f;
            int ink = dim ? 0xFF8E8E93 : (sun ? 0xFF1C1C1E : 0xFF8E8E93);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(2.6f * d);
            if (sun) {
                // Sun icon – size scales slightly with brightness
                float scale = 0.85f + value * 0.25f;
                p.setColor(dim ? 0xFF8E8E93 : 0xFFF2C94C);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy, 6.5f * d * scale, p);
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    float inner = 10f * d * scale;
                    float outer = 14.5f * d * scale;
                    cv.drawLine(cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner,
                            cx + (float) Math.cos(a) * outer, cy + (float) Math.sin(a) * outer, p);
                }
            } else {
                // Speaker icon
                p.setStyle(Paint.Style.FILL);
                p.setColor(ink);
                Path sp = new Path();
                sp.moveTo(cx - 11f * d, cy - 5f * d);
                sp.lineTo(cx - 5f * d, cy - 5f * d);
                sp.lineTo(cx + 2f * d, cy - 11f * d);
                sp.lineTo(cx + 2f * d, cy + 11f * d);
                sp.lineTo(cx - 5f * d, cy + 5f * d);
                sp.lineTo(cx - 11f * d, cy + 5f * d);
                sp.close();
                cv.drawPath(sp, p);
                if (value > 0.15f) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(ink);
                    cv.drawArc(new RectF(cx - 1f * d, cy - 7f * d, cx + 11f * d, cy + 7f * d), -50, 100, false, p);
                }
                if (value > 0.5f) {
                    cv.drawArc(new RectF(cx + 1f * d, cy - 11f * d, cx + 16f * d, cy + 11f * d), -55, 110, false, p);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                dragging = true;
                longFired = false;
                downY = e.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                animate().scaleX(0.97f).scaleY(0.97f).setDuration(90)
                        .setInterpolator(pl.dock.widget.ui.DockMotion.IOS_SPRING).start();
                if (longPress != null) removeCallbacks(longPress);
                // iOS: dlugie przytrzymanie suwaka otwiera opcje (cisza / cieplo ekranu)
                longPress = new Runnable() {
                    @Override public void run() {
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
                animate().scaleX(1f).scaleY(1f).setDuration(180)
                        .setInterpolator(pl.dock.widget.ui.DockMotion.IOS_SPRING).start();
            }
            if (longFired && (a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP)) {
                return true;   // po dlugim przytrzymaniu nie przestawiaj wartosci
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
}


