
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

// Centrum ÅÄcznoÅci w stylu iOS. ZawartoÅÄ to siatka narzÄdzi (ToolGrid), ktÃ³rÄ moÅ¼na
// ukÅadaÄ po swojemu: przycisk âEdytujâ (albo dÅugie przytrzymanie tÅa) wÅÄcza tryb edycji,
// w ktÃ³rym kafelki moÅ¼na przeciÄgaÄ, usuwaÄ i dodawaÄ nowe z listy narzÄdzi i skrÃ³tÃ³w do ustawieÅ.
// UÅ¼ywane w launcherze (nakÅadka) i jako okno zapasowe.
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
    private PillSlider volSlider;
    private PillSlider briSlider;
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
        // nagÅÃ³wek: tytuÅ + przycisk Edytuj / Gotowe
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Popups.text(act, "Centrum ÅÄcznoÅci", 16, Popups.txt(night), true);
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
        staggerViews.add(head);

        // siatka narzÄdzi
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

        // dÅugie przytrzymanie tÅa = tryb edycji
        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                enterEditMode();
                return true;
            }
        });

        // przycisk âDodaj narzÄdzieâ (widoczny tylko w trybie edycji)
        btnAdd = pill("ï¼  Dodaj narzÄdzie");
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

    // wÅasne widoki dla suwakÃ³w i przeÅÄcznika wyglÄdu; reszta to zwykÅe kafelki
    private View makeView(PanelTool.Def d) {
        if (d.kind == PanelTool.K_VOLUME) {
            PillSlider s = new PillSlider(act, false, volumeValue());
            s.setListener(new PillSlider.Listener() {
                @Override
                public void on(float v) {
                    setVolume(v);
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
            });
            briSlider = s;
            return s;
        }
        if (d.kind == PanelTool.K_MODE) {
            modeSeg = new ModeSegment(act);
            return modeSeg;
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
        btnAdd.setVisibility(VISIBLE);
    }

    public void exitEditMode() {
        if (!editMode) return;
        editMode = false;
        grid.setEditing(false);
        btnEdit.setText("Edytuj");
        btnAdd.setVisibility(GONE);
    }

    private void openPicker() {
        ToolPicker.show(act, night, grid.ids(), new ToolPicker.OnPick() {
            @Override
            public void pick(String id) {
                grid.addTool(id);
            }
        });
    }

    // ---------------------------------------------------------------- dziaÅanie narzÄdzi

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
        Toast.makeText(act, "Ta funkcja jest niedostÄpna na tym tablecie", Toast.LENGTH_SHORT).show();
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
        if ("air".equals(id)) {
            if (longPress) openAndClose("android.settings.AIRPLANE_MODE_SETTINGS");
            else toggleAirplane();
        } else if ("hot".equals(id)) {
            if (!Net.openTetherSettings(act)) unavailable();
            else host.close();
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
                boolean on = !NightShift.isOn(act);
                boolean ok = NightShift.set(act, on);
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
                // jednorazowa zgoda na zmianÄ ustawieÅ systemu
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
            // zwykÅa aplikacja nie moÅ¼e tego zmieniÄ â otwÃ³rz ustawienia
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

    /** Suwak warmth Night Shift â zapis do Prefs i prÃ³ba sync z Circadian Display. */
    private void showWarmthSlider() {
        final android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(act);
        b.setTitle("Night Shift â ciepÅo");
        final android.widget.SeekBar sb = new android.widget.SeekBar(act);
        sb.setMax(80);
        sb.setProgress(Math.max(5, Math.min(80, Prefs.nightShiftStrength(act))));
        int pad = dp(16);
        sb.setPadding(pad, pad, pad, pad);
        b.setView(sb);
        b.setPositiveButton("Zapisz", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int w) {
                int strength = Math.max(5, sb.getProgress());
                Prefs.setNightShiftStrength(act, strength);
                if (NightShift.isOn(act)) {
                    NightShift.set(act, false);
                    NightShift.set(act, true);
                }
                try {
                    if (pl.dock.widget.circadian.CircadianBridge.isInstalled(act)) {
                        android.os.Bundle in = new android.os.Bundle();
                        in.putInt("warmth", strength);
                        act.getContentResolver().call(
                                android.net.Uri.parse("content://com.circadiandisplay.app.state"),
                                "set_warmth", null, in);
                    }
                } catch (Throwable ignored) {
                }
                update();
            }
        });
        b.setNegativeButton("Anuluj", null);
        b.show();
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
                    Net.openBluetoothApp(act);   // radio przejmuje Bluetooth: otwÃ³rz jego aplikacjÄ
                    host.close();
                }
            }
        }, 2500);
        update();
    }

    // ---------------------------------------------------------------- gÅoÅnoÅÄ / jasnoÅÄ
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
        if (note != null) {
            boolean follow = Prefs.followSystem(act) && grid != null && grid.has("mode");
            note.setText(follow ? "WyglÄd automatyczny: od zachodu do wschodu sÅoÅca" : "");
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

    // ---------------------------------------------------------------- segment: Jasny / Ciemny / Auto (jeden kafelek)
    private class ModeSegment extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d = getResources().getDisplayMetrics().density;
        private final String[] labels = {"Jasny", "Ciemny", "Auto"};
        private int selected; // 0 day, 1 night, 2 auto
        private int pressedSeg = -1;

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
            // background track
            p.setStyle(Paint.Style.FILL);
            p.setColor(night ? 0xF0606767 : 0xFFE4E4EA);
            RectF track = new RectF(0, 0, w, h);
            cv.drawRoundRect(track, rad, rad, p);

            float segW = w / 3f;
            // selected pill
            float pad = 3f * d;
            RectF pill = new RectF(selected * segW + pad, pad, (selected + 1) * segW - pad, h - pad);
            p.setColor(night ? 0xFF3A3A3C : 0xFFFFFFFF);
            cv.drawRoundRect(pill, rad - 2f, rad - 2f, p);

            // subtle border
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * d);
            p.setColor(0x44FFFFFF);
            cv.drawRoundRect(track, rad, rad, p);

            // labels
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float sd = getResources().getDisplayMetrics().scaledDensity;
            p.setTextSize(14f * sd);
            for (int i = 0; i < 3; i++) {
                p.setColor(i == selected ? (night ? 0xFFFFFFFF : 0xFF1C1C1E) : (night ? 0xFFC9CFCF : 0xFF6C6C70));
                cv.drawText(labels[i], i * segW + segW / 2f, h / 2f + 5f * d, p);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float segW = getWidth() / 3f;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                pressedSeg = Math.min(2, Math.max(0, (int) (e.getX() / segW)));
                invalidate();
                return true;
            }
            if (a == MotionEvent.ACTION_UP) {
                int seg = Math.min(2, Math.max(0, (int) (e.getX() / segW)));
                pressedSeg = -1;
                if (seg == 0) {
                    Prefs.setFollowSystem(act, false);
                    Prefs.setMode(act, "day");
                } else if (seg == 1) {
                    Prefs.setFollowSystem(act, false);
                    Prefs.setMode(act, "night");
                } else {
                    Prefs.setFollowSystem(act, true);
                }
                applyMode();
                refreshSelection();
                if (host != null) host.modeChanged();
                return true;
            }
            if (a == MotionEvent.ACTION_CANCEL) {
                pressedSeg = -1;
                invalidate();
            }
            return true;
        }
    }


    // ---------------------------------------------------------------- pionowy suwak
    public static class PillSlider extends View {
        public interface Listener {
            void on(float v);
        }

        private float value;
        private boolean dragging;
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
            setContentDescription(sun ? "JasnoÅÄ ekranu" : "GÅoÅnoÅÄ");
        }

        public void setListener(Listener l) {
            this.l = l;
        }

        // ustawia wartoÅÄ z zewnÄtrz (nie w trakcie przesuwania palcem)
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
            // White fill from bottom
            int save = cv.save();
            Path clip = new Path();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            cv.clipPath(clip);
            p.setColor(0xFFFFFFFF);
            cv.drawRect(0, h * (1f - value), w, h, p);
            cv.restoreToCount(save);

            // Icon at bottom (iOS style) â gray when value ~0
            float cx = w / 2f;
            float cy = h - 28f * d;
            boolean dim = value < 0.05f;
            int ink = dim ? 0xFF8E8E93 : 0xFF1C1C1E;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(2.6f * d);
            if (sun) {
                // Sun icon â size scales slightly with brightness
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
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                dragging = false;
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


