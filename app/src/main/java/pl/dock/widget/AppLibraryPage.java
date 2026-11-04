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
public class AppLibraryPage extends FrameLayout {

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
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    private final LinkedHashMap<String, Integer> letterY = new LinkedHashMap<String, Integer>();
    private TextView stickyHeader;      // przyklejony naglowek aktualnej kategorii
    private String lastStickySection;
    private final List<View> sectionHeaders = new ArrayList<View>();

    public AppLibraryPage(Context c) {
        super(c);
        this.ctx = c;
        this.d = c.getResources().getDisplayMetrics().density;
        this.night = Look.night(c);
        setClickable(true);
        buildChrome();
        // DLUZE przytrzymanie na tle listy wlacza tryb z minusami (zadnego trzesienia)
        final OnLongClickListener bgLong = new OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                setMinusMode(true);
                return true;
            }
        };
        setOnLongClickListener(bgLong);
        if (scrollView != null) scrollView.setOnLongClickListener(bgLong);
        if (grid != null) grid.setOnLongClickListener(bgLong);
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (editing) setMinusMode(false);
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

        // jak w Bibliotece aplikacji iOS: na gorze sam pasek wyszukiwania
        // (tytul strony jest teraz placeholderem tego pola)
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16) + extraL, dp(14), dp(16) + extraR, dp(6));

        // Search bar â opens inline filter
        final android.widget.EditText searchEt = new android.widget.EditText(ctx);
        searchEt.setHint("Biblioteka aplikacji");
        searchEt.setTextSize(17);
        searchEt.setTypeface(pl.dock.widget.ui.DockTheme.interRegular());
        searchEt.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        searchEt.setHintTextColor(night ? 0x99FFFFFF : 0x996C6C70);
        searchEt.setSingleLine(true);
        searchEt.setBackground(new android.graphics.drawable.GradientDrawable() {{
            setCornerRadius(dp(22));
            setColor(night ? 0x33FFFFFF : 0xCCFFFFFF);
            setStroke(Math.max(1, (int) (0.5f * d)), night ? 0x33FFFFFF : 0x2E000000);
        }});
        // ikona lupy po lewej stronie pola (rysowana recznie, zero zasobow)
        int msz = dp(20);
        android.graphics.Bitmap mb = android.graphics.Bitmap.createBitmap(
                msz, msz, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas mc = new android.graphics.Canvas(mb);
        android.graphics.Paint mp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        mp.setStyle(android.graphics.Paint.Style.STROKE);
        mp.setStrokeWidth(1.8f * d);
        mp.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        mp.setColor(night ? 0x99FFFFFF : 0x993C3C43);
        mc.drawCircle(msz * 0.40f, msz * 0.40f, msz * 0.26f, mp);
        mc.drawLine(msz * 0.60f, msz * 0.60f, msz * 0.80f, msz * 0.80f, mp);
        searchEt.setCompoundDrawablesWithIntrinsicBounds(
                new android.graphics.drawable.BitmapDrawable(ctx.getResources(), mb), null, null, null);
        searchEt.setCompoundDrawablePadding(dp(10));
        searchEt.setPadding(dp(14), dp(10), dp(14), dp(10));
        searchEt.setMinimumHeight(dp(46));
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
        grid.setPadding(dp(14) + extraL, dp(4), dp(40) + extraR, dp(24)); // right pad for letter bar
        scrollView.addView(grid, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LayoutParams scLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        scLp.topMargin = dp(72);
        addView(scrollView, scLp);

        // AâZ letter index (iOS style)
        letterBar = new LinearLayout(ctx);
        letterBar.setOrientation(LinearLayout.VERTICAL);
        letterBar.setGravity(Gravity.CENTER_HORIZONTAL);
        letterBar.setPadding(0, dp(8), dp(4), dp(8));
        for (int i = 0; i < LETTERS.length(); i++) {
            final String letter = String.valueOf(LETTERS.charAt(i));
            TextView tv = new TextView(ctx);
            tv.setText(letter);
            tv.setTextSize(13);
            tv.setTypeface(pl.dock.widget.ui.DockTheme.interMedium());
            tv.setTextColor(night ? 0xFF0A84FF : 0xFF007AFF);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(6), dp(1), dp(6), dp(1));
            tv.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    scrollToLetter(letter);
                }
            });
            letterBar.addView(tv);
        }
        // iOS: przeciganie palcem po pasku = zywe przewijanie + efekt magnesu
        // (wybrana litera powieksza sie i wysuwa w lewo)
        letterBar.setOnTouchListener(new View.OnTouchListener() {
            private int selected = -1;

            @Override public boolean onTouch(View v, android.view.MotionEvent ev) {
                int a = ev.getActionMasked();
                int idx = -1;
                float y = ev.getY();
                for (int i = 0; i < letterBar.getChildCount(); i++) {
                    View ch = letterBar.getChildAt(i);
                    if (y >= ch.getTop() && y <= ch.getBottom()) { idx = i; break; }
                }
                if (a == android.view.MotionEvent.ACTION_DOWN
                        || a == android.view.MotionEvent.ACTION_MOVE) {
                    if (idx >= 0 && idx != selected) {
                        if (selected >= 0) resetLetter(selected);
                        selected = idx;
                        TextView tv = (TextView) letterBar.getChildAt(idx);
                        tv.setPivotX(tv.getWidth());
                        tv.animate().scaleX(1.7f).scaleY(1.7f).translationX(-dp(12))
                                .setDuration(90).start();
                        tv.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        scrollToLetter(String.valueOf(LETTERS.charAt(idx)));
                    }
                    return true;
                }
                if (a == android.view.MotionEvent.ACTION_UP
                        || a == android.view.MotionEvent.ACTION_CANCEL) {
                    if (selected >= 0) resetLetter(selected);
                    selected = -1;
                    return true;
                }
                return false;
            }
        });
        LayoutParams lbLp = new LayoutParams(dp(34), ViewGroup.LayoutParams.MATCH_PARENT);
        lbLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lbLp.topMargin = dp(72);
        lbLp.rightMargin = extraR;
        lbLp.leftMargin = extraL;
        addView(letterBar, lbLp);

        // przyklejony (sticky) naglowek aktualnej kategorii - widoczny przy scrollu
        stickyHeader = new TextView(ctx);
        stickyHeader.setTextSize(20);
        stickyHeader.setTypeface(pl.dock.widget.ui.DockTheme.interSemiBold());
        stickyHeader.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        stickyHeader.setBackgroundColor(night ? 0xF20E0E0F : 0xF2F5F5F7);
        stickyHeader.setPadding(dp(16) + extraL, dp(10), dp(54) + extraR, dp(10));
        stickyHeader.setVisibility(GONE);
        LayoutParams shLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        shLp.topMargin = dp(72);
        addView(stickyHeader, shLp);

        // aktualizacja sticky przy przewijaniu listy
        scrollView.getViewTreeObserver().addOnScrollChangedListener(
                new android.view.ViewTreeObserver.OnScrollChangedListener() {
                    @Override public void onScrollChanged() {
                        updateSticky();
                    }
                });
    }

    private void resetLetter(int i) {
        if (letterBar == null || i < 0 || i >= letterBar.getChildCount()) return;
        TextView tv = (TextView) letterBar.getChildAt(i);
        tv.animate().scaleX(1f).scaleY(1f).translationX(0f).setDuration(130).start();
    }

    private void scrollToLetter(String letter) {
        if (scrollView == null || grid == null) return;
        // najpierw lista: dokladna pozycja pierwszej aplikacji na dana litere
        for (Map.Entry<String, Integer> e : letterY.entrySet()) {
            if (letter.equals(e.getKey())) {
                scrollView.smoothScrollTo(0, Math.max(0, e.getValue() - dp(24)));
                return;
            }
        }
        // fallback: kategorie
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
        order.add("Narzędzia");
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
                if (it == null) continue;
                // iOS 26 CarPlay: stale, przewidywalne kategorie - narzedzia systemowe
                // (Ustawienia / CarPlay / OBD) zawsze w tym samym miejscu Biblioteki
                if (it.settings || (it.action != null && it.action.length() > 0)) {
                    AppEntry tool = new AppEntry();
                    boolean isObd = "obd".equals(it.action);
                    tool.pkg = it.settings ? "dock:settings" : (isObd ? "dock:obd" : "dock:carplay");
                    tool.mode = "builtin";
                    // OBD w launcherze nosi ikone Hondy (zgodnie ze specyfikacja)
                    tool.iconPkg = it.settings ? "settings" : (isObd ? "honda" : "carplay");
                    labels.put(tool.pkg, it.label);
                    categories.get("Narzędzia").add(tool);
                    continue;
                }
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
                showLibraryAppMenuNear(iconView, app);
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
        sectionHeaders.clear();
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
            // LISTA aplikacji w kategorii: naglowek + wiersze ikona+nazwa
            categoryY.put(e.getKey(), grid.getChildCount() * dp(60));
            TextView head = new TextView(ctx);
            head.setText(e.getKey());
            head.setTextSize(20);
            head.setTypeface(pl.dock.widget.ui.DockTheme.interSemiBold());
            head.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
            head.setPadding(dp(16), dp(16), dp(16), dp(6));
            head.setTag(e.getKey());
            sectionHeaders.add(head);
            grid.addView(head, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            for (final AppEntry app : e.getValue()) {
                grid.addView(makeAppRow(app, fl), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        if (categories.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("Brak aplikacji w bibliotece");
            empty.setTextColor(night ? 0x99FFFFFF : 0x99666666);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            grid.addView(empty);
        }
        // pozycje liter dla paska A-Z (dopiero po policzeniu layoutu)
        post(measureLetters);
        updateSticky();
    }

    // wiersz aplikacji: zaokraglona ikona po lewej + nazwa po prawej (jak w Ustawieniach iOS)
    // wiersz aplikacji: zaokraglona ikona po lewej + nazwa po prawej (jak w Ustawieniach iOS)
    private View makeAppRow(final AppEntry app, final LibraryFolderView.Listener fl) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        row.setTag(app);
        // rozjasnienie przy dotknieciu (lekkie, bez ciezkiego ripple)
        android.graphics.drawable.GradientDrawable rowMask = new android.graphics.drawable.GradientDrawable();
        rowMask.setCornerRadius(dp(14));
        rowMask.setColor(0x00000000);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(night ? 0x26FFFFFF : 0x14000000),
                null, rowMask));

        FrameLayout iconBox = new FrameLayout(ctx);
        android.widget.ImageView iv = new android.widget.ImageView(ctx);
        android.graphics.Bitmap bmp = Icons.forEntry(ctx, app);
        if (bmp != null) {
            android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(dp(48), dp(48), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(out);
            android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
            android.graphics.RectF rect = new android.graphics.RectF(0, 0, dp(48), dp(48));
            cv.drawRoundRect(rect, dp(12), dp(12), pt);
            pt.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            android.graphics.Rect src = new android.graphics.Rect(0, 0, bmp.getWidth(), bmp.getHeight());
            cv.drawBitmap(bmp, src, rect, pt);
            iv.setImageBitmap(out);
        }
        iconBox.addView(iv, new FrameLayout.LayoutParams(dp(48), dp(48)));

        if (editing) {
            // minus w LEWYM GORNYM rogu ikony (jak w iOS)
            View badge = new View(ctx) {
                @Override protected void onDraw(android.graphics.Canvas c) {
                    float s = getWidth();
                    android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    p.setStyle(android.graphics.Paint.Style.FILL);
                    p.setColor(0xFFFF3B30);
                    c.drawCircle(s / 2f, s / 2f, s / 2f, p);
                    p.setColor(0xFFFFFFFF);
                    p.setStyle(android.graphics.Paint.Style.STROKE);
                    p.setStrokeWidth(2.2f * d);
                    p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                    float m = s * 0.28f;
                    c.drawLine(m, s / 2f, s - m, s / 2f, p);
                }
            };
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(22), dp(22),
                    Gravity.TOP | Gravity.START);
            blp.leftMargin = -dp(8);
            blp.topMargin = -dp(8);
            iconBox.addView(badge, blp);
            badge.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    fl.onRemoveApp(app);
                }
            });
        }
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView name = new TextView(ctx);
        name.setText(appLabel(app));
        name.setTextSize(17);
        name.setTypeface(pl.dock.widget.ui.DockTheme.interRegular());
        name.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(14);
        row.addView(name, nlp);

        row.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                fl.onAppTap(app);
            }
        });
        row.setOnLongClickListener(new OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showLibraryAppMenuNear(v, app);
                return true;
            }
        });
        return row;
    }

    // przyklejony naglowek: pokazuje kategorie, ktora jest aktualnie na gorze listy
    private void updateSticky() {
        if (stickyHeader == null) return;
        if (sectionHeaders.isEmpty() || scrollView == null) {
            stickyHeader.setVisibility(GONE);
            lastStickySection = null;
            return;
        }
        int y = scrollView.getScrollY();
        String cur = null;
        for (View h : sectionHeaders) {
            if (h.getTop() <= y + dp(4)) cur = (String) h.getTag();
        }
        if (cur == null) {
            stickyHeader.setVisibility(GONE);
            lastStickySection = null;
            return;
        }
        if (!cur.equals(lastStickySection)) {
            lastStickySection = cur;
            stickyHeader.setText(cur);
        }
        stickyHeader.setVisibility(VISIBLE);
    }

    // komorka aplikacji na liscie biblioteki: ikona + podpis (+ usuwanie w trybie edycji)
    private View makeAppCell(final AppEntry app, final LibraryFolderView.Listener fl) {
        FrameLayout cell = new FrameLayout(ctx);
        cell.setTag(app);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(4), dp(4), dp(4), dp(4));
        android.widget.ImageView iv = new android.widget.ImageView(ctx);
        iv.setImageBitmap(Icons.forEntry(ctx, app));
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        col.addView(iv, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView tv = new TextView(ctx);
        tv.setText(appLabel(app));
        tv.setTextSize(11);
        tv.setMaxLines(1);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        col.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cell.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (editing) {
            // badge usuwania: bialy minus na czerwonym kole (jak w iOS)
            TextView badge = new TextView(ctx);
            badge.setText("\u2212");
            badge.setTextSize(15);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(0xFFFFFFFF);
            badge.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable bgd = new android.graphics.drawable.GradientDrawable();
            bgd.setColor(0xFFFF3B30);
            bgd.setCornerRadius(dp(12));
            badge.setBackground(bgd);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(24), dp(24),
                    Gravity.TOP | Gravity.START);
            cell.addView(badge, blp);
            badge.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    fl.onRemoveApp(app);
                }
            });
        }

        cell.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                fl.onAppTap(app);
            }
        });
        cell.setOnLongClickListener(new OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                fl.onAppLongPress(v, app);
                return true;
            }
        });
        return cell;
    }

    // po layout: zapisz Y pierwszej aplikacji kazdej litery (pasek A-Z trafia dokladnie)
    private final Runnable measureLetters = new Runnable() {
        @Override public void run() {
            letterY.clear();
            if (grid == null) return;
            int n = grid.getChildCount();
            for (int i = 0; i < n; i++) {
                View row = grid.getChildAt(i);
                if (!(row instanceof LinearLayout)) continue;
                LinearLayout rw = (LinearLayout) row;
                for (int j = 0; j < rw.getChildCount(); j++) {
                    View cell = rw.getChildAt(j);
                    Object tag = cell.getTag();
                    if (!(tag instanceof AppEntry)) continue;
                    String lb = appLabel((AppEntry) tag);
                    if (lb == null || lb.length() == 0) continue;
                    char c0 = Character.toUpperCase(lb.charAt(0));
                    String key = Character.isLetter(c0) ? String.valueOf(c0) : "#";
                    if (!letterY.containsKey(key)) {
                        letterY.put(key, row.getTop() + cell.getTop());
                    }
                }
            }
        }
    };

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

    // menu przy aplikacji (nie na srodku ekranu): usuwa z listy / informacje / odinstaluj
    private void showLibraryAppMenuNear(final View anchor, final AppEntry app) {
        if (app == null || app.pkg == null || app.pkg.length() == 0
                || !(ctx instanceof android.app.Activity)) {
            return;
        }
        final android.app.Activity a = (android.app.Activity) ctx;
        Popups.menuNear(a, anchor, appLabel(app),
                new String[]{"Usuń z listy aplikacji", "Informacje o aplikacji", "Odinstaluj"},
                new Popups.OnSel() {
                    @Override public void on(int idx) {
                        if (idx == 0) {
                            Prefs.setHiddenInLibrary(ctx, app.pkg, true);
                            if (host != null) host.onLibraryChanged();
                            for (List<AppEntry> lst : categories.values()) {
                                for (int i = lst.size() - 1; i >= 0; i--) {
                                    if (app.pkg.equals(lst.get(i).pkg)) lst.remove(i);
                                }
                            }
                            rebuildFolders();
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
                    showLibraryAppMenuNear(v, app);
                    return true;
                }
            });
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(cell, cellLp);
            col++;
        }
    }
    // tryb z minusami (bez trzesienia): wlaczany dlugim przytrzymaniem tla listy
    public void setMinusMode(boolean on) {
        editing = on;
        if (grid != null) rebuildFolders();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        editing = false;
    }

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }
}
