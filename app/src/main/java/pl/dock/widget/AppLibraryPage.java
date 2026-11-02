package pl.dock.widget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * iOS App Library page: searchable title + 2-column grid of category folders.
 */
public class AppLibraryPage extends FrameLayout implements EditModeController.Listener {

    public interface Host {
        void openApp(String pkg);
        void onLibraryChanged();
        void showCategoryFolder(String category, List<AppEntry> apps);
    }

    private final Context ctx;
    private final float d;
    private boolean night;
    private Host host;
    private final Map<String, List<AppEntry>> categories = new LinkedHashMap<String, List<AppEntry>>();
    private final List<LibraryFolderView> folders = new ArrayList<LibraryFolderView>();
    private LinearLayout grid;
    private boolean editing;
    private TextView title;
    private View searchBar;
    private ScrollView scrollView;
    private LinearLayout letterBar;
    private String filterQuery = "";
    private boolean searchFocused = false;
    private final Map<String, Integer> categoryY = new LinkedHashMap<String, Integer>();
    private final Map<String, String> labels = new LinkedHashMap<String, String>();

    public AppLibraryPage(Context c) {
        super(c);
        this.ctx = c;
        this.d = c.getResources().getDisplayMetrics().density;
        this.night = Look.night(c);
        setClickable(true);
        buildChrome();
        EditModeController.get().addListener(this);
        setOnLongClickListener(new OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                EditModeController.get().setEditing(true);
                return true;
            }
        });
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (editing) EditModeController.get().setEditing(false);
            }
        });
    }

    public void setHost(Host h) {
        this.host = h;
    }

    public void setNight(boolean n) {
        this.night = n;
        if (title != null) title.setTextColor(n ? 0xFFFFFFFF : 0xFF1C1C1E);
        rebuildFolders();
    }

    private void buildChrome() {
        removeAllViews();
        folders.clear();

        // inset na dock: zawartosc biblioteki nie moze wchodzic pod dok
        int dockInset = Ui.dp(ctx, Ui.DOCK_OFFSET_DP);
        boolean dockLeft = Ui.dockLeft(ctx);
        int extraL = dockLeft ? dockInset : 0;
        int extraR = dockLeft ? 0 : dockInset;

        // Title + iOS-style search bar
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16) + extraL, dp(12), dp(16) + extraR, dp(4));

        title = new TextView(ctx);
        title.setText("Biblioteka aplikacji");
        title.setTextSize(28);
        title.setTypeface(pl.dock.widget.ui.DockTheme.interSemiBold());
        title.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, dp(10));
        header.addView(title);

        // Search bar â opens inline filter
        final android.widget.EditText searchEt = new android.widget.EditText(ctx);
        searchEt.setHint("Szukaj");
        searchEt.setTextSize(16);
        searchEt.setTypeface(pl.dock.widget.ui.DockTheme.interRegular());
        searchEt.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        searchEt.setHintTextColor(night ? 0x99FFFFFF : 0x99666666);
        searchEt.setSingleLine(true);
        searchEt.setBackground(new android.graphics.drawable.GradientDrawable() {{
            setCornerRadius(dp(24));
            setColor(night ? 0x4DFFFFFF : 0xCCFFFFFF);
            setStroke(Math.max(1, (int) (0.5f * d)), 0x33FFFFFF);
        }});
        searchEt.setPadding(dp(14), dp(10), dp(14), dp(10));
        searchEt.setMinimumHeight(dp(40));
        searchBar = searchEt;
        header.addView(searchEt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        searchEt.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                filterQuery = s == null ? "" : s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                rebuildFolders();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        // iOS: dotkniecie paska szukania (bez tekstu) = pelna lista alfabetyczna
        searchEt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                searchFocused = hasFocus;
                rebuildFolders();
            }
        });

        addView(header, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView = new ScrollView(ctx);
                scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(14) + extraL, dp(4), dp(28) + extraR, dp(24)); // right pad for letter bar
        scrollView.addView(grid, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LayoutParams scLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        scLp.topMargin = dp(100);
        addView(scrollView, scLp);

        // AâZ letter index (iOS style)
        letterBar = new LinearLayout(ctx);
        letterBar.setOrientation(LinearLayout.VERTICAL);
        letterBar.setGravity(Gravity.CENTER_HORIZONTAL);
        letterBar.setPadding(0, dp(8), dp(4), dp(8));
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
        for (int i = 0; i < letters.length(); i++) {
            final String letter = String.valueOf(letters.charAt(i));
            TextView tv = new TextView(ctx);
            tv.setText(letter);
            tv.setTextSize(10);
            tv.setTypeface(pl.dock.widget.ui.DockTheme.interMedium());
            tv.setTextColor(night ? 0xFF0A84FF : 0xFF007AFF);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(4), dp(1), dp(4), dp(1));
            tv.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    scrollToLetter(letter);
                }
            });
            letterBar.addView(tv);
        }
        LayoutParams lbLp = new LayoutParams(dp(22), ViewGroup.LayoutParams.MATCH_PARENT);
        lbLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lbLp.topMargin = dp(100);
        lbLp.rightMargin = extraR;
        lbLp.leftMargin = extraL;
        addView(letterBar, lbLp);
    }

    private void scrollToLetter(String letter) {
        if (scrollView == null || grid == null) return;
        // Find first category starting with letter
        for (Map.Entry<String, Integer> e : categoryY.entrySet()) {
            String name = e.getKey();
            if (name == null || name.isEmpty()) continue;
            char c = Character.toUpperCase(name.charAt(0));
            if (letter.equals("#")) {
                if (!Character.isLetter(c)) {
                    scrollView.smoothScrollTo(0, e.getValue());
                    return;
                }
            } else if (c == letter.charAt(0)) {
                scrollView.smoothScrollTo(0, e.getValue());
                return;
            }
        }
    }

    /** Rebuild from full launcher items list (selected apps). */
    public void setApps(List<AppsGridPage.Item> items) {
        categories.clear();
        labels.clear();
        // order: Suggestions/Recent first if we want â for now auto categories
        List<String> order = new ArrayList<String>();
        order.add(AppCategorizer.CAT_SOCIAL);
        order.add(AppCategorizer.CAT_ENTERTAINMENT);
        order.add(AppCategorizer.CAT_NAVIGATION);
        order.add(AppCategorizer.CAT_UTILITIES);
        order.add(AppCategorizer.CAT_PRODUCTIVITY);
        order.add(AppCategorizer.CAT_OTHER);

        for (String k : order) categories.put(k, new ArrayList<AppEntry>());

        if (items != null) {
            PackageManager pm = ctx.getPackageManager();
            for (AppsGridPage.Item it : items) {
                if (it == null || it.settings || (it.action != null && it.action.length() > 0)) continue;
                String pkg = it.pkg;
                if (pkg == null || pkg.length() == 0) continue;
                if (Prefs.isHiddenInLibrary(ctx, pkg)) continue;

                AppEntry e = it.entry != null ? it.entry : new AppEntry();
                if (e.pkg == null || e.pkg.length() == 0) e.pkg = pkg;

                String label = it.label != null ? it.label : "";
                if (label.length() == 0) {
                    try {
                        label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                    } catch (Throwable ignored) {
                        label = pkg;
                    }
                }
                labels.put(pkg, label);
                String cat = AppCategorizer.getCategory(e, label);
                if (!categories.containsKey(cat)) {
                    categories.put(cat, new ArrayList<AppEntry>());
                }
                categories.get(cat).add(e);
            }
        }
        // CarPlay iOS 26: ZERO personalizacji - statyczne, przewidywalne kategorie
        // (kierowca musi wiedziec, ze aplikacje sa zawsze w tym samym miejscu)

        // drop empty
        List<String> empty = new ArrayList<String>();
        for (Map.Entry<String, List<AppEntry>> e : categories.entrySet()) {
            if (e.getValue().isEmpty()) empty.add(e.getKey());
        }
        for (String k : empty) categories.remove(k);

        rebuildFolders();
    }

    private void rebuildFolders() {
        if (grid == null) return;
        grid.removeAllViews();
        folders.clear();
        if (searchFocused && (filterQuery == null || filterQuery.length() == 0)) {
            buildAlphabeticalList();
            return;
        }

        LibraryFolderView.Listener fl = new LibraryFolderView.Listener() {
            @Override public void onFolderTap(String category) {
                if (editing) return;
                List<AppEntry> apps = categories.get(category);
                if (host != null && apps != null) host.showCategoryFolder(category, apps);
            }

            @Override public void onAppTap(AppEntry app) {
                if (editing) return;
                if (host != null && app != null) host.openApp(app.pkg);
            }

            @Override public void onAppLongPress(View iconView, AppEntry app) {
                showLibraryAppMenu(app);
            }

            @Override public void onRemoveApp(AppEntry app) {
                if (app == null) return;
                Prefs.setHiddenInLibrary(ctx, app.pkg, true);
                if (host != null) host.onLibraryChanged();
                // local refresh
                List<AppEntry> list = categories.get(
                        AppCategorizer.getCategory(app, ""));
                // rebuild from current map
                for (List<AppEntry> lst : categories.values()) {
                    for (int i = lst.size() - 1; i >= 0; i--) {
                        if (app.pkg.equals(lst.get(i).pkg)) lst.remove(i);
                    }
                }
                rebuildFolders();
            }
        };

        categoryY.clear();
        LinearLayout row = null;
        int col = 0;
        for (Map.Entry<String, List<AppEntry>> e : categories.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            // Search filter: match category name or any app label
            if (filterQuery != null && filterQuery.length() > 0) {
                boolean match = e.getKey().toLowerCase(java.util.Locale.ROOT).contains(filterQuery);
                if (!match) {
                    for (AppEntry app : e.getValue()) {
                        if (app == null) continue;
                        String pkg = app.pkg != null ? app.pkg.toLowerCase(java.util.Locale.ROOT) : "";
                        if (pkg.contains(filterQuery)) {
                            match = true;
                            break;
                        }
                    }
                }
                if (!match) continue;
            }
            // Record approximate Y for letter index (after layout would be better; use index for now)
            categoryY.put(e.getKey(), grid.getChildCount() * dp(140));
            if (col == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = dp(16);
                grid.addView(row, rlp);
            }
            // Vertical wrapper: folder tile + label underneath (iOS style)
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            LibraryFolderView folder = new LibraryFolderView(ctx, e.getKey(), e.getValue(), night, fl);
            folders.add(folder);
            LinearLayout.LayoutParams folderLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cell.addView(folder, folderLp);

            TextView label = new TextView(ctx);
            label.setText(e.getKey());
            label.setTextSize(13);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setPadding(0, dp(6), 0, 0);
            cell.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (col == 0) cellLp.rightMargin = dp(12);
            row.addView(cell, cellLp);
            if (editing) folder.setEditing(true);
            col++;
            if (col >= 2) col = 0;
        }
        if (categories.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("Brak aplikacji w bibliotece");
            empty.setTextColor(night ? 0x99FFFFFF : 0x99666666);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            grid.addView(empty);
        }
    }

    // Sugestie: do 4 najczesciej uzywanych aplikacji z ostatniego tygodnia
    private List<AppEntry> suggestions(List<AppEntry> all) {
        List<AppEntry> out = new ArrayList<AppEntry>();
        Map<String, Long> last = new LinkedHashMap<String, Long>();
        try {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                    ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm != null) {
                List<android.app.usage.UsageStats> stats = usm.queryUsageStats(
                        android.app.usage.UsageStatsManager.INTERVAL_WEEKLY,
                        System.currentTimeMillis() - 7L * 24 * 3600 * 1000L,
                        System.currentTimeMillis());
                if (stats != null) {
                    for (android.app.usage.UsageStats s : stats) {
                        if (s == null) continue;
                        Long cur = last.get(s.getPackageName());
                        if (cur == null || s.getLastTimeUsed() > cur) {
                            last.put(s.getPackageName(), s.getLastTimeUsed());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (last.isEmpty()) return out;
        final Map<String, Long> usage = last;
        List<AppEntry> cands = new ArrayList<AppEntry>(all);
        Collections.sort(cands, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                Long ta = usage.get(a.pkg), tb = usage.get(b.pkg);
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            }
        });
        for (AppEntry e : cands) {
            if (out.size() >= 4) break;
            if (usage.containsKey(e.pkg)) out.add(e);
        }
        return out;
    }

    // Ostatnio dodane: instalacje/aktualizacje z ostatnich 7 dni
    private List<AppEntry> recentlyAdded(List<AppEntry> all) {
        List<AppEntry> out = new ArrayList<AppEntry>();
        long week = 7L * 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();
        try {
            PackageManager pm = ctx.getPackageManager();
            for (AppEntry e : all) {
                try {
                    long t = pm.getPackageInfo(e.pkg, 0).lastUpdateTime;
                    if (t > 0 && now - t <= week) out.add(e);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    // iOS: menu kontekstowe aplikacji w Bibliotece
    private void showLibraryAppMenu(final AppEntry app) {
        if (app == null || app.pkg == null || app.pkg.length() == 0
                || !(ctx instanceof android.app.Activity)) {
            EditModeController.get().setEditing(true);
            return;
        }
        final android.app.Activity a = (android.app.Activity) ctx;
        Popups.menu(a, appLabel(app),
                new String[]{"Dodaj do ekranu głównego", "Informacje o aplikacji", "Usuń aplikację"},
                new Popups.OnSel() {
                    @Override public void on(int idx) {
                        if (idx == 0) {
                            Prefs.add(ctx, app.pkg);
                            android.widget.Toast.makeText(ctx, "Dodano do ekranu głównego",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        } else if (idx == 1) {
                            try {
                                a.startActivity(new android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:" + app.pkg)));
                            } catch (Throwable ignored) {
                            }
                        } else if (idx == 2) {
                            try {
                                android.content.Intent i = new android.content.Intent(
                                        android.content.Intent.ACTION_UNINSTALL_PACKAGE,
                                        android.net.Uri.parse("package:" + app.pkg));
                                i.putExtra(android.content.Intent.EXTRA_RETURN_RESULT, true);
                                a.startActivity(i);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });
    }

    private String appLabel(AppEntry e) {
        if (e == null) return "";
        String l = labels.get(e.pkg);
        if (l != null && l.length() > 0) return l;
        return e.pkg != null ? e.pkg : "";
    }

    // pelna lista alfabetyczna (dotkniecie paska szukania bez tekstu, jak w iOS)
    private void buildAlphabeticalList() {
        List<AppEntry> all = new ArrayList<AppEntry>();
        HashSet<String> seen = new HashSet<String>();
        for (List<AppEntry> lst : categories.values()) {
            for (AppEntry e : lst) {
                if (e != null && e.pkg != null && seen.add(e.pkg)) all.add(e);
            }
        }
        Collections.sort(all, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return appLabel(a).compareToIgnoreCase(appLabel(b));
            }
        });
        if (all.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("Brak aplikacji");
            empty.setTextColor(night ? 0x99FFFFFF : 0x99666666);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            grid.addView(empty);
            return;
        }
        LinearLayout row = null;
        int col = 0;
        for (final AppEntry app : all) {
            if (row == null || col >= 4) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
                col = 0;
            }
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(6), dp(6), dp(6), dp(6));
            android.widget.ImageView iv = new android.widget.ImageView(ctx);
            iv.setImageBitmap(Icons.forEntry(ctx, app));
            int isz = dp(52);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            cell.addView(iv, new LinearLayout.LayoutParams(isz, isz));
            TextView tv = new TextView(ctx);
            tv.setText(appLabel(app));
            tv.setTextSize(11);
            tv.setMaxLines(1);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
            cell.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    if (editing) return;
                    if (host != null) host.openApp(app.pkg);
                }
            });
            cell.setOnLongClickListener(new OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    showLibraryAppMenu(app);
                    return true;
                }
            });
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(cell, cellLp);
            col++;
        }
    }

    public void setEditing(boolean on) {
        editing = on;
        for (LibraryFolderView f : folders) f.setEditing(on);
    }

    @Override
    public void onEditModeChanged(boolean editing) {
        setEditing(editing);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        EditModeController.get().removeListener(this);
        setEditing(false);
    }

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }
}


