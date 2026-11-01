
package pl.dock.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Ekran „Diagnostyka źródła napięcia”
public class VoltDiagActivity extends BaseActivity {

    private static final int GREEN = 0xFF2ECC71;
    private static final float[] W = {1.1f, 2.3f, 1.7f, 0.7f, 2.8f};

    private Look.Pal p;
    private final List<DiagScan.Row> all = new ArrayList<DiagScan.Row>();
    private final List<DiagScan.Row> shown = new ArrayList<DiagScan.Row>();
    private RowAdapter adapter;
    private TextView statusView;
    private EditText filterBox;
    private EditText keyBox;
    private Spinner scopeSpinner;
    private ListView list;
    private boolean scanning;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    @Override
    protected void onCreate(Bundle b) {
        boolean night = Look.night(this);
        setTheme(night ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(b);
        p = Look.pal(night);
        build();
        startScan();
    }

    // ------------------------------------------------------------ UI
    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.setPadding(dp(16), dp(10), dp(16), dp(8));

        root.addView(tv("Diagnostyka źródła napięcia", 22, p.text, true));

        TextView note = tv("Wynik 12–15 V jest tylko PRAWDOPODOBNYM napięciem instalacji i wymaga "
                + "potwierdzenia (porównaj z paskiem statusu radia). Jeśli napięcie jest dostępne "
                + "wyłącznie wewnątrz zmodyfikowanego frameworka lub prywatnego interfejsu MCU, "
                + "zwykła aplikacja może nie mieć do niego dostępu. Dotknij wiersza, by zobaczyć "
                + "szczegóły lub użyć go jako źródła w widżecie.", 12, p.sub, false);
        note.setPadding(0, dp(4), 0, dp(6));
        root.addView(note);

        // przyciski
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button bScan = new Button(this);
        bScan.setText("Skanuj ponownie");
        bScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScan();
            }
        });
        Button bCopy = new Button(this);
        bCopy.setText("Kopiuj");
        bCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyReport();
            }
        });
        Button bSave = new Button(this);
        bSave.setText("Zapisz do pliku");
        bSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveReport();
            }
        });
        bar.addView(bScan);
        bar.addView(bCopy);
        bar.addView(bSave);
        root.addView(bar);

        // ręczny odczyt klucza Settings
        LinearLayout man = new LinearLayout(this);
        man.setOrientation(LinearLayout.HORIZONTAL);
        man.setGravity(Gravity.CENTER_VERTICAL);
        scopeSpinner = new Spinner(this);
        ArrayAdapter<String> sa = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"System", "Global", "Secure"});
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scopeSpinner.setAdapter(sa);
        man.addView(scopeSpinner);
        keyBox = new EditText(this);
        keyBox.setHint("Klucz Settings do odczytu");
        keyBox.setSingleLine(true);
        keyBox.setTextColor(p.text);
        keyBox.setHintTextColor(p.sub);
        man.addView(keyBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button bRead = new Button(this);
        bRead.setText("Odczytaj");
        bRead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manualRead();
            }
        });
        man.addView(bRead);
        root.addView(man);

        // filtr
        filterBox = new EditText(this);
        filterBox.setHint("Filtr tabeli (np. volt, hwmon, battery)");
        filterBox.setSingleLine(true);
        filterBox.setTextColor(p.text);
        filterBox.setHintTextColor(p.sub);
        filterBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });
        root.addView(filterBox);

        statusView = tv("", 12, p.sub, false);
        statusView.setPadding(0, dp(2), 0, dp(4));
        root.addView(statusView);

        // nagłówek tabeli
        LinearLayout head = rowContainer();
        String[] hs = {"Źródło", "Klucz / plik", "Wartość", "Jedn.", "Status / błąd"};
        for (int i = 0; i < hs.length; i++) {
            TextView h = tv(hs[i], 12, p.text, true);
            head.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, W[i]));
        }
        head.setBackgroundColor(p.sep);
        root.addView(head);

        list = new ListView(this);
        list.setDivider(null);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                if (pos >= 0 && pos < shown.size()) showDetail(shown.get(pos));
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private LinearLayout rowContainer() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(dp(6), dp(5), dp(6), dp(5));
        return r;
    }

    private class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout r;
            TextView[] cells;
            if (cv == null) {
                r = rowContainer();
                cells = new TextView[5];
                for (int i = 0; i < 5; i++) {
                    TextView t = new TextView(VoltDiagActivity.this);
                    t.setTextSize(12);
                    t.setMaxLines(4);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    t.setPadding(0, 0, dp(4), 0);
                    r.addView(t, new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, W[i]));
                    cells[i] = t;
                }
                r.setTag(cells);
            } else {
                r = (LinearLayout) cv;
                cells = (TextView[]) r.getTag();
            }
            DiagScan.Row row = shown.get(pos);
            cells[0].setText(row.source);
            cells[1].setText(row.name);
            cells[2].setText(row.value);
            cells[3].setText(row.unit);
            cells[4].setText(row.status);
            cells[0].setTextColor(p.sub);
            cells[1].setTextColor(p.text);
            cells[2].setTextColor(p.text);
            cells[3].setTextColor(p.sub);
            cells[4].setTextColor(row.probable ? GREEN : p.sub);
            cells[4].setTypeface(null, row.probable ? Typeface.BOLD : Typeface.NORMAL);
            if (row.probable) {
                r.setBackgroundColor(0x3328C76F);
            } else {
                r.setBackgroundColor(pos % 2 == 0 ? p.card : Color.TRANSPARENT);
            }
            return r;
        }
    }

    // ------------------------------------------------------------ skan
    private void startScan() {
        if (scanning) return;
        scanning = true;
        statusView.setText("Skanowanie… (może potrwać kilka sekund)");
        all.clear();
        applyFilter();
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<DiagScan.Row> res;
                try {
                    res = DiagScan.run(app);
                } catch (Throwable t) {
                    res = new ArrayList<DiagScan.Row>();
                    res.add(DiagScan.err("Skan", "(test)", t));
                }
                final List<DiagScan.Row> fin = res;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        if (isFinishing()) return;
                        int hot = 0;
                        for (DiagScan.Row r : fin) if (r.probable) hot++;
                        all.addAll(fin);
                        applyFilter();
                        statusView.setText("Gotowe: " + fin.size() + " wierszy, w tym "
                                + hot + " z wynikiem 12–15 V (prawdopodobne, niepotwierdzone)");
                    }
                });
            }
        }).start();
    }

    private void applyFilter() {
        String f = filterBox == null ? "" : filterBox.getText().toString().trim().toLowerCase(Locale.US);
        shown.clear();
        for (DiagScan.Row r : all) {
            if (f.length() == 0) {
                shown.add(r);
            } else {
                String s = (r.source + " " + r.name + " " + r.value + " " + r.unit + " " + r.status)
                        .toLowerCase(Locale.US);
                if (s.contains(f)) shown.add(r);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void manualRead() {
        String key = keyBox.getText().toString().trim();
        if (key.length() == 0) {
            Toast.makeText(this, "Wpisz nazwę klucza", Toast.LENGTH_SHORT).show();
            return;
        }
        DiagScan.Row r = DiagScan.readSetting(getContentResolver(),
                scopeSpinner.getSelectedItemPosition(), key);
        all.add(0, r);
        applyFilter();
        list.setSelection(0);
        showDetail(r);
    }

    // ------------------------------------------------------------ szczegóły
    private void showDetail(final DiagScan.Row r) {
        String msg = "Źródło: " + r.source + "\nKlucz/plik: " + r.name + "\nWartość: " + r.value
                + "\nJednostka: " + r.unit + "\nStatus: " + r.status;
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Szczegóły")
                .setMessage(msg)
                .setNegativeButton("Zamknij", null);
        if (r.useType != null) {
            b.setPositiveButton("Użyj w widżecie", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    useInWidget(r);
                }
            });
        }
        b.show();
    }

    private void useInWidget(DiagScan.Row r) {
        if ("file".equals(r.useType)) {
            Prefs.setVoltSource(this, "file");
            Prefs.setVoltPath(this, r.useRef);
        } else {
            Prefs.setVoltSource(this, "setting");
            Prefs.setSettingKey(this, r.useRef);
        }
        StatusService.restart(this);
        DockWidget.updateAll(this);
        Toast.makeText(this, "Źródło napięcia widżetu ustawione: " + r.useRef,
                Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------ eksport
    private String report() {
        StringBuilder b = new StringBuilder();
        b.append("źródło\tklucz/plik\twartość\tjednostka\tstatus\n");
        for (DiagScan.Row r : all) {
            b.append(r.source).append('\t').append(r.name).append('\t').append(r.value)
                    .append('\t').append(r.unit).append('\t').append(r.status).append('\n');
            if (b.length() > 300000) {
                b.append("… (ucięto – zbyt długi raport)\n");
                break;
            }
        }
        return b.toString();
    }

    private void copyReport() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("diagnostyka napięcia", report()));
            Toast.makeText(this, "Skopiowano do schowka", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Nie udało się skopiować: " + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void saveReport() {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            File f = new File(dir, "diagnostyka_napiecia.txt");
            FileWriter w = new FileWriter(f);
            try {
                w.write(report());
            } finally {
                w.close();
            }
            Toast.makeText(this, "Zapisano: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Nie udało się zapisać: " + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }
}







