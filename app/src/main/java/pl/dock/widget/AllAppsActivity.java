package pl.dock.widget;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
        List<CategorizedApp> allApps = new ArrayList<CategorizedApp>();

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

        // Grupowanie: kategoria → lista aplikacji
        LinkedHashMap<String, List<CategorizedApp>> grouped = new LinkedHashMap<String, List<CategorizedApp>>();
        // Zachowujemy kolejność kategorii z AppCategorizer
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
        for (CategorizedApp ca : allApps) {
            String cat = ca.category;
            if (!grouped.containsKey(cat)) {
                grouped.put(cat, new ArrayList<CategorizedApp>());
            }
            grouped.get(cat).add(ca);
        }

        // Usuwamy puste kategorie
        ArrayList<String> toRemove = new ArrayList<String>();
        for (Map.Entry<String, List<CategorizedApp>> e : grouped.entrySet()) {
            if (e.getValue().isEmpty()) toRemove.add(e.getKey());
        }
        for (String k : toRemove) {
            grouped.remove(k);
        }

        // Budowa widoku
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(p.bg);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(20), dp(16), dp(40));
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
        TextView count = new TextView(this);
        count.setText(allApps.size() + " aplikacji");
        count.setTextSize(14);
        count.setTextColor(p.sub);
        count.setPadding(dp(14), 0, 0, 0);
        bar.addView(count, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        col.addView(bar);

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

        setContentView(sv);
    }
}
