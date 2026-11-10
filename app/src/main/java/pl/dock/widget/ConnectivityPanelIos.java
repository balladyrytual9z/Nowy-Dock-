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
import android.widget.SeekBar;
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
    private SeekBar warmthBar;
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
                // JEDEN przycisk trybu: klik cykli Jasny -> Ciemny -> Auto -> Jasny
                bMode = new RoundBtn(act, 0, "Jasny");
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
                View gap = new View(act);
                row.addView(gap, new LayoutParams(0, 1, 1f));
                bShift = new RoundBtn(act, 3, "Night Shift");
                row.addView(bShift, new LayoutParams(dp(110), dp(96)));
                bShift.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean on = !NightShift.isOn(act);
                        boolean ok = NightShift.set(act, on);
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
        cool.setText("Chłodniejsze");
        cool.setTextSize(13);
        cool.setTypeface(null, Typeface.BOLD);
        cool.setTextColor(night ? 0xB3FFFFFF : 0xFF3C3C43);
        warmthRow.addView(cool, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        warmthBar = new SeekBar(act);
        warmthBar.setMax(80);
        warmthBar.setProgress(Math.max(5, Math.min(80, Prefs.nightShiftStrength(act))));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(220),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.leftMargin = dp(12);
        bp.rightMargin = dp(12);
        warmthRow.addView(warmthBar, bp);

        TextView warm = new TextView(act);
        warm.setText("Cieplejsze");
        warm.setTextSize(13);
        warm.setTypeface(null, Typeface.BOLD);
        warm.setTextColor(night ? 0xB3FFFFFF : 0xFF3C3C43);
        warmthRow.addView(warm, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        warmthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
                if (!fromUser) return;
                Prefs.setNightShiftStrength(act, Math.max(5, Math.min(80, prog)));
                NightShift.refresh(act);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                removeCallbacks(hideWarmth);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                sb.setProgress(Math.max(5, Math.min(80, Prefs.nightShiftStrength(act))));
                scheduleWarmthHide();
            }
        });

        addView(warmthRow, new LinearLayout.LayoutParams(
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
        if (note != null) {
            boolean follow = Prefs.followSystem(act);
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
            p.setStyle(Paint.Style.FILL);
            p.setColor(night ? 0xF0606767 : 0xFFE4E4EA);
            r.set(0, 0, w, hh);
            cv.drawRoundRect(r, rad, rad, p);
            if (pressed) {
                p.setColor(0x22FFFFFF);
                cv.drawRoundRect(r, rad, rad, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f * d);
            p.setShader(new LinearGradient(0, 0, w, hh,
                    new int[]{0xCCFFFFFF, 0x55FFFFFF, 0x22FFFFFF, 0x44FFFFFF},
                    new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
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
    // kind: 0 jasny (słońce), 1 ciemny (księżyc), 2 auto (A), 3 Night Shift
    private class RoundBtn extends View {
        private int kind;
        private String label;
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

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float cx = w / 2f;
            float cy = 34f * d;
            float rad = 28f * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(active ? (kind == 3 ? 0xFFFF9F0A : 0xFF1C94FF)
                    : (night ? 0xFF4A4F50 : 0xFFD1D1D6));
            cv.drawCircle(cx, cy, rad, p);
            int ink = active ? 0xFFFFFFFF : (night ? 0xFFFFFFFF : 0xFF3C3C43);
            p.setColor(ink);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.6f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            if (kind == 0 || kind == 3) {
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
            p.setTypeface(Typeface.DEFAULT);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(night ? 0xFFFFFFFF : 0xFF111114);
            p.setTextSize(13f * getResources().getDisplayMetrics().scaledDensity);
            cv.drawText(label, cx, 84f * d, p);
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
            p.setStyle(Paint.Style.FILL);
            p.setColor(night ? 0xFF4A4F50 : 0xFFD1D1D6);
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, rad, rad, p);
            int save = cv.save();
            Path clip = new Path();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            cv.clipPath(clip);
            p.setColor(0xFFFAFAFC);
            cv.drawRect(0, h * (1f - value), w, h, p);
            cv.restoreToCount(save);
            float cx = w / 2f;
            float cy = h - 34f * d;
            int ink = 0xFF8E8E93;
            p.setColor(ink);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.4f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            if (sun) {
                p.setColor(0xFFF2C94C);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy, 7f * d, p);
                p.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    cv.drawLine(cx + (float) Math.cos(a) * 11f * d, cy + (float) Math.sin(a) * 11f * d,
                            cx + (float) Math.cos(a) * 15f * d, cy + (float) Math.sin(a) * 15f * d, p);
                }
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF5AC8FA);
                Path sp = new Path();
                sp.moveTo(cx - 12f * d, cy - 5f * d);
                sp.lineTo(cx - 6f * d, cy - 5f * d);
                sp.lineTo(cx + 1f * d, cy - 11f * d);
                sp.lineTo(cx + 1f * d, cy + 11f * d);
                sp.lineTo(cx - 6f * d, cy + 5f * d);
                sp.lineTo(cx - 12f * d, cy + 5f * d);
                sp.close();
                cv.drawPath(sp, p);
                p.setStyle(Paint.Style.STROKE);
                cv.drawArc(new RectF(cx - 2f * d, cy - 7f * d, cx + 10f * d, cy + 7f * d), -50, 100,
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
}
