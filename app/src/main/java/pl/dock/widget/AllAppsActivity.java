package pl.dock.widget;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista wszystkich aplikacji podzielona na kategorie (iOS-style App Library).
 * Używa istniejącego AppCategorizer do automatycznej kategoryzacji.
 */
public class AllAppsActivity extends BaseActivity {

    private static class CategorizedApp {
        String label;
        String pkg;
        String category;
    }

    private boolean night;
    private Look.Pal p;

    // TASK-1: wszystkie aplikacje + aktywny filtr wyszukiwania. Lista jest
    // przebudowywana w rebuildList(), wiec trzymamy dane, nie widoki.
    private final List<CategorizedApp> allApps = new ArrayList<CategorizedApp>();
    private String query = "";
    private LinearLayout col;
    private TextView count;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        night = Look.night(this);
        setTheme(night ? R.style.Dock_Night : R.style.Dock_Day);
        super.onCreate(b);
        p = Look.pal(night);

        try {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        } catch (Throwable ignored) {}
        applyImmersive();

        build();
    }

    private void applyImmersive() {
        try {
            int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            if (!night) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    private void build() {
        // Zebranie wszystkich aplikacji
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);

        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            CategorizedApp ca = new CategorizedApp();
            ca.label = ri.loadLabel(pm).toString();
            ca.pkg = ri.activityInfo.packageName;
            AppEntry ae = new AppEntry();
            ae.pkg = ca.pkg;
            ca.category = AppCategorizer.getCategory(ae, ca.label);
            allApps.add(ca);
        }

        // Sortowanie alfabetyczne w obrębie kategorii
        Collections.sort(allApps, new Comparator<CategorizedApp>() {
            @Override
            public int compare(CategorizedApp a, CategorizedApp b) {
                int catCmp = a.category.compareTo(b.category);
                if (catCmp != 0) return catCmp;
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        // Budowa widoku
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(p.bg);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);

        // Naglowek i pasek szukania poza przewijaniem (TASK-1): lista przewija
        // sie POD nimi, a szukanie jest zawsze pod palcem.
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(20), dp(16), 0);

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(10), dp(16), dp(40));
        sv.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Pasek wstecz + tytuł
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(8), dp(8));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(30);
        back.setTypeface(null, Typeface.BOLD);
        back.setTextColor(p.text);
        back.setGravity(Gravity.CENTER);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(p.card);
        backBg.setCornerRadius(dp(22));
        back.setBackground(backBg);
        back.setContentDescription("Wstecz");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText("Wszystkie aplikacje");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(p.text);
        title.setPadding(dp(14), 0, 0, 0);
        bar.addView(title);

        // Licznik aplikacji
        count = new TextView(this);
        count.setText(allApps.size() + " aplikacji");
        count.setTextSize(14);
        count.setTextColor(p.sub);
        count.setPadding(dp(14), 0, 0, 0);
        bar.addView(count, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        head.addView(bar);

        // WYSZUKIWARKA (TASK-1): szklana pigułka pod nagłówkiem, ta sama estetyka
        // co pasek szukania w Bibliotece. Filtruje po nazwie z polskimi ogonkami
        // i wielkosci liter; przy pustym wyniku pokazuje "Brak wyników".
        EditText searchEt = new EditText(this);
        searchEt.setTextSize(16);
        searchEt.setTypeface(pl.dock.widget.ui.DockTheme.interRegular());
        searchEt.setTextColor(p.text);
        searchEt.setHintTextColor(p.sub);
        searchEt.setHint("Szukaj aplikacji");
        searchEt.setSingleLine(true);
        searchEt.setPadding(dp(16), dp(12), dp(16), dp(12));
        searchEt.setMinHeight(dp(46));
        searchEt.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        searchEt.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchEt.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent ev) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                        || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                        || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                    InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (im != null) {
                        try {
                            im.hideSoftInputFromWindow(v.getWindowToken(), 0);
                        } catch (Throwable ignored) {
                        }
                    }
                    v.clearFocus();
                    return true;
                }
                return false;
            }
        });
        GradientDrawable field = new GradientDrawable();
        field.setColor(night ? 0x33FFFFFF : 0xFFFFFFFF);
        field.setCornerRadius(dp(23));
        field.setStroke(Math.max(1, dp(1) / 2), night ? 0x33FFFFFF : 0x1F808090);
        searchEt.setBackground(field);
        searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s == null ? "" : s.toString();
                rebuildList();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sepLp.topMargin = dp(12);
        head.addView(searchEt, sepLp);

        // Ostatni element: lista pod nagłówkiem. Szukanie poza przewijaniem.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rebuildList();
        setContentView(root);
    }

    /** Buduje sekcje kategorii z aktualnego filtra (TASK-1). Wywolywane przy
     *  kazdej zmianie tekstu - nie odtwarza naglowka ani paska szukania. */
    private void rebuildList() {
        if (col == null) return;

        // Usun poprzednie sekcje - lista trzyma juz tylko kategorie i wiersze.
        col.removeAllViews();

        final String q = norm(query);

        // Grupowanie: kategoria → lista aplikacji
        LinkedHashMap<String, List<CategorizedApp>> grouped = new LinkedHashMap<String, List<CategorizedApp>>();
        String[] catOrder = {
                AppCategorizer.CAT_SUGGESTIONS,
                AppCategorizer.CAT_RECENT,
                AppCategorizer.CAT_SOCIAL,
                AppCategorizer.CAT_UTILITIES,
                AppCategorizer.CAT_ENTERTAINMENT,
                AppCategorizer.CAT_PRODUCTIVITY,
                AppCategorizer.CAT_NAVIGATION,
                AppCategorizer.CAT_OTHER
        };
        for (String cat : catOrder) {
            grouped.put(cat, new ArrayList<CategorizedApp>());
        }
        int shown = 0;
        for (CategorizedApp ca : allApps) {
            if (q.length() > 0 && !matches(ca.label, q)) continue;
            shown++;
            String cat = ca.category;
            if (!grouped.containsKey(cat)) {
                grouped.put(cat, new ArrayList<CategorizedApp>());
            }
            grouped.get(cat).add(ca);
        }
        if (count != null) {
            count.setText(q.length() > 0
                    ? shown + " z " + allApps.size()
                    : allApps.size() + " aplikacji");
        }

        // Usuwamy puste kategorie
        ArrayList<String> toRemove = new ArrayList<String>();
        for (Map.Entry<String, List<CategorizedApp>> e : grouped.entrySet()) {
            if (e.getValue().isEmpty()) toRemove.add(e.getKey());
        }
        for (String k : toRemove) {
            grouped.remove(k);
        }

        if (grouped.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Brak wyników");
            empty.setTextSize(16);
            empty.setTextColor(p.sub);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(48), 0, 0);
            col.addView(empty);
            return;
        }

        // Kategorie z aplikacjami
        for (Map.Entry<String, List<CategorizedApp>> entry : grouped.entrySet()) {
            String catName = entry.getKey();
            List<CategorizedApp> catApps = entry.getValue();

            // Nagłówek kategorii
            TextView catTitle = new TextView(this);
            catTitle.setText(catName.toUpperCase());
            catTitle.setTextSize(13);
            catTitle.setTypeface(null, Typeface.BOLD);
            catTitle.setTextColor(p.sub);
            catTitle.setPadding(dp(8), dp(22), 0, dp(6));
            col.addView(catTitle);

            // Karta z aplikacjami w kategorii
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(p.card);
            cardBg.setCornerRadius(dp(22));
            card.setBackground(cardBg);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = dp(6);
            col.addView(card, cardLp);

            boolean first = true;
            for (final CategorizedApp ca : catApps) {
                if (!first) {
                    View sep = new View(this);
                    sep.setBackgroundColor(p.sep);
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
                    slp.leftMargin = dp(16);
                    card.addView(sep, slp);
                }
                first = false;

                // Wiersz aplikacji
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(68));
                row.setPadding(dp(16), dp(8), dp(16), dp(8));
                row.setClickable(true);

                // Ikona
                ImageView iv = new ImageView(this);
                Bitmap icon = Icons.forPackage(this, ca.pkg);
                iv.setImageBitmap(icon);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(48), dp(48));
                ilp.rightMargin = dp(14);
                row.addView(iv, ilp);

                // Nazwa
                TextView name = new TextView(this);
                name.setText(ca.label);
                name.setTextSize(17);
                name.setTextColor(p.text);
                name.setMaxLines(1);
                row.addView(name, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                // Chevron
                TextView chevron = new TextView(this);
                chevron.setText("  ›");
                chevron.setTextSize(26);
                chevron.setTextColor(p.sub);
                row.addView(chevron);

                final String pkg = ca.pkg;
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(launch);
                        } else {
                            Toast.makeText(AllAppsActivity.this,
                                    "Nie można otworzyć aplikacji", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                card.addView(row);
            }
        }
    }

    /** Normalizacja do porownywania - patrz {@link AppLibraryPage#fold(String)}.
     *  Wspolna z szuflada aplikacji, wiec "Ładowanie" i "ladowanie" dzialaja
     *  tak samo w obu miejscach (TASK-1). */
    private static String norm(String s) {
        return AppLibraryPage.fold(s);
    }

    private static boolean matches(String label, String q) {
        String n = norm(label);
        if (n.contains(q)) return true;
        // dopasowanie po aliasie Docka (jak w Bibliotece): "AirDrop" -> "LocalSend"
        try {
            Overrides.O o = Overrides.match(label);
            if (o != null && o.label != null && norm(o.label).contains(q)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
