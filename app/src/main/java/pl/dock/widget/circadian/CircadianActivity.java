package pl.dock.widget.circadian;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import pl.dock.widget.NightShift;

// Wbudowany edytor Circadian Display (styl Docka: czysty framework, grafitowa paleta).
// Otwierany z ikony "Circadian" lub przez CircadianBridge, gdy zewnetrzna
// aplikacja Circadian Display nie jest zainstalowana.
public class CircadianActivity extends Activity {

    private LinearLayout list;
    private TextView status;
    private Button permBtn;
    private Switch sw;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CircadianPrefs.seed(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF11141A);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("Circadian Display");
        title.setTextColor(0xFFE8ECF2);
        title.setTextSize(24f);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Krzywa ciepła i przyciemnienia w ciągu dnia. Wbudowane — bez instalacji osobnej aplikacji.");
        sub.setTextColor(0xFF9AA4B2);
        sub.setTextSize(13f);
        root.addView(sub);

        status = new TextView(this);
        status.setTextColor(0xFFC7F0D8);
        status.setTextSize(15f);
        status.setPadding(0, dp(12), 0, dp(8));
        root.addView(status);

        permBtn = new Button(this);
        permBtn.setText("Daj zgodę: wyświetlanie nad innymi aplikacjami");
        permBtn.setOnClickListener(v -> NightShift.requestPermission(CircadianActivity.this));
        root.addView(permBtn);

        sw = new Switch(this);
        sw.setText("Włącz krzywą dobową");
        sw.setTextColor(0xFFE8ECF2);
        sw.setTextSize(16f);
        sw.setPadding(0, dp(12), 0, dp(4));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                CircadianPrefs.setEnabled(CircadianActivity.this, on);
                if (on && !CircadianService.canDraw(CircadianActivity.this)) {
                    NightShift.requestPermission(CircadianActivity.this);
                }
                CircadianService.refresh(CircadianActivity.this);
                rebuild();
            }
        });
        root.addView(sw);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(8), 0, 0);
        root.addView(list);

        Button add = new Button(this);
        add.setText("Dodaj punkt krzywej");
        add.setOnClickListener(v -> editPoint(-1));
        root.addView(add);

        setContentView(sv);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        boolean canDraw = CircadianService.canDraw(this);
        permBtn.setVisibility(canDraw ? View.GONE : View.VISIBLE);
        sw.setChecked(CircadianPrefs.enabled(this));

        List<float[]> pts = CircadianPrefs.points(this);
        float[] st = CircadianCurve.evaluate(pts, CircadianService.nowMinutes());
        int now = CircadianService.nowMinutes();
        status.setText(String.format(Locale.US,
                "Teraz %02d:%02d — ciepło %d%%, przyciemnienie %d%%",
                now / 60, now % 60,
                Math.round(st[0] * 100f), Math.round(st[1] * 100f)));

        list.removeAllViews();
        for (int i = 0; i < pts.size(); i++) {
            final int idx = i;
            float[] p = pts.get(i);
            TextView row = new TextView(this);
            row.setTextColor(0xFFE8ECF2);
            row.setTextSize(15f);
            row.setPadding(dp(4), dp(10), dp(4), dp(10));
            row.setText(String.format(Locale.US, "%02d:%02d   ciepło %d%%   przyciemnienie %d%%",
                    (int) p[0] / 60, (int) p[0] % 60,
                    Math.round(p[1] * 100f), Math.round(p[2] * 100f)));
            row.setOnClickListener(v -> editPoint(idx));
            row.setOnLongClickListener(v -> {
                confirmDelete(idx);
                return true;
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void editPoint(final int index) {
        List<float[]> pts = CircadianPrefs.points(this);
        float[] cur = index >= 0 && index < pts.size() ? pts.get(index) : new float[]{nowHm(), 0f, 0f};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);

        final EditText time = new EditText(this);
        time.setInputType(InputType.TYPE_CLASS_TEXT);
        time.setText(String.format(Locale.US, "%02d:%02d", (int) cur[0] / 60, (int) cur[0] % 60));
        time.setHint("HH:MM");
        box.addView(time);

        final TextView wLabel = new TextView(this);
        wLabel.setText("Ciepło");
        wLabel.setTextColor(0xFFE8ECF2);
        box.addView(wLabel);
        final SeekBar warm = new SeekBar(this);
        warm.setMax(100);
        warm.setProgress(Math.round(cur[1] * 100f));
        box.addView(warm);

        final TextView dLabel = new TextView(this);
        dLabel.setText("Przyciemnienie");
        dLabel.setTextColor(0xFFE8ECF2);
        box.addView(dLabel);
        final SeekBar dim = new SeekBar(this);
        dim.setMax(100);
        dim.setProgress(Math.round(cur[2] * 100f));
        box.addView(dim);

        final float[] in = cur;
        final int idx = index;
        new AlertDialog.Builder(this)
                .setTitle(idx >= 0 ? "Edytuj punkt" : "Nowy punkt")
                .setView(box)
                .setPositiveButton("Zapisz", (d, w) -> {
                    int t = parseHm(time.getText().toString(), (int) in[0]);
                    List<float[]> updated = new ArrayList<>(CircadianPrefs.points(CircadianActivity.this));
                    if (idx >= 0 && idx < updated.size()) {
                        updated.set(idx, new float[]{t, warm.getProgress() / 100f, dim.getProgress() / 100f});
                    } else {
                        updated.add(new float[]{t, warm.getProgress() / 100f, dim.getProgress() / 100f});
                    }
                    CircadianPrefs.setPoints(CircadianActivity.this, updated);
                    CircadianService.refresh(CircadianActivity.this);
                    rebuild();
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void confirmDelete(final int index) {
        new AlertDialog.Builder(this)
                .setTitle("Usunąć punkt?")
                .setPositiveButton("Usuń", (d, w) -> {
                    List<float[]> updated = new ArrayList<>(CircadianPrefs.points(CircadianActivity.this));
                    if (index >= 0 && index < updated.size()) {
                        updated.remove(index);
                    }
                    CircadianPrefs.setPoints(CircadianActivity.this, updated);
                    CircadianService.refresh(CircadianActivity.this);
                    rebuild();
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private int nowHm() {
        return CircadianService.nowMinutes();
    }

    private static int parseHm(String s, int fallback) {
        try {
            String[] f = s.trim().split(":");
            int h = Integer.parseInt(f[0].trim());
            int m = f.length > 1 ? Integer.parseInt(f[1].trim()) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) return fallback;
            return h * 60 + m;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
