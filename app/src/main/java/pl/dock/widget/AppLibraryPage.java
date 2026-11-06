package pl.dock.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Ostatnia strona launchera: PLASKA lista alfabetyczna wszystkich aplikacji
 * zainstalowanych w tablecie (+ 3 narzedzia launchera wmieszane alfabetycznie).
 *
 * Widok jest celowo pozbawiony kategorii - silnik kategoryzowania
 * (AppCategorizer) zostaje w kodzie na pozniej, ale nie jest tu uzywany.
 * Na gorze pole wyszukiwania, pod nim poziomy pasek liter A-Z w stylu iOS
 * (litery nie sa wygaszane; litera pod palcem powieksza sie z animacja).
 */
public class AppLibraryPage extends FrameLayout {

    public interface Host {
        void openApp(String pkg);
        void onLibraryChanged();
        // onGrid=false -> usun z siatki aplikacji, onGrid=true -> dodaj do siatki
        void setOnGrid(String pkg, boolean onGrid);
        void showAppInfo(String pkg);
        void uninstall(String pkg);
    }

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";

    private final Context ctx;
    private final float d;
    private boolean night;
    private Host host;

    // pelna lista z launchera (kolejnosc ustawiamy sami po oryginalnej nazwie)
    private final List<AppsGridPage.Item> apps = new ArrayList<AppsGridPage.Item>();

    private EditText searchEt;
    private boolean clearVisible;
    private int clearSize;
    private LinearLayout letterBar;
    private ScrollView scrollView;
    private LinearLayout listCol;

    private String query = "";
    private String rawText = "";
    private final LinkedHashMap<String, Integer> letterY = new LinkedHashMap<String, Integer>();

    public AppLibraryPage(Context c) {
        super(c);
        this.ctx = c;
        this.d = c.getResources().getDisplayMetrics().density;
        this.night = Look.night(c);
        setClickable(true);
        setClipChildren(false);
        setClipToPadding(false);
        buildChrome();
    }

    public void setHost(Host h) {
        this.host = h;
    }

    public void setNight(boolean n) {
        if (this.night == n && listCol != null) return;
        this.night = n;
        buildChrome();
        if (rawText.length() > 0) {
            searchEt.setText(rawText);   // TextWatcher sam odbuduje przefiltrowana liste
        } else {
            rebuild();
        }
    }

    // ---------------------------------------------------------------- dane

    /** Ustawia pelna liste aplikacji z launchera i odbudowuje widok. */
    public void setApps(List<AppsGridPage.Item> list) {
        apps.clear();
        if (list != null) {
            for (AppsGridPage.Item it : list) {
                if (it != null) apps.add(it);
            }
        }
        Collections.sort(apps, new Comparator<AppsGridPage.Item>() {
            @Override public int compare(AppsGridPage.Item a, AppsGridPage.Item b) {
                return norm(a.label).compareTo(norm(b.label));
            }
        });
        rebuild();
    }

    /** Zamyka fokus i klawiature (wyjscie ze strony / otwarcie aplikacji). */
    public void dismissSearch() {
        if (searchEt == null) return;
        try {
            searchEt.clearFocus();
            InputMethodManager im = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(searchEt.getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- chrome (gora strony)

    private void buildChrome() {
        removeAllViews();

        int dockInset = Ui.dp(ctx, Ui.DOCK_OFFSET_DP);
        boolean dockLeft = Ui.dockLeft(ctx);
        int extraL = dockLeft ? dockInset : 0;
        int extraR = dockLeft ? 0 : dockInset;

        // Glowny kontener: POZIOMO – alfabet z lewej, tresc z prawej
        LinearLayout page = new LinearLayout(ctx);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);

        // ---- LEWY PANEL: pionowy pasek liter A-Z (bezposrednio w page) ----
        letterBar = new LinearLayout(ctx);
        letterBar.setOrientation(LinearLayout.VERTICAL);
        letterBar.setGravity(Gravity.CENTER);
        letterBar.setClipChildren(false);
        letterBar.setClipToPadding(false);
        letterBar.setPadding(0, dp(8), 0, dp(8));
        // polprzezroczyste tlo + elevacja zeby bylo NAD lista
        letterBar.setBackgroundColor(night ? 0xCC1C1C1E : 0xCCFFFFFF);
        letterBar.setElevation(dp(8));
        for (int i = 0; i < LETTERS.length(); i++) {
            final String letter = String.valueOf(LETTERS.charAt(i));
            TextView tv = new TextView(ctx);
            tv.setText(letter);
            tv.setTextSize(13);
            tv.setTypeface(pl.dock.widget.ui.DockTheme.interMedium());
            tv.setTextColor(night ? 0xFF0A84FF : 0xFF007AFF);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(1), 0, dp(1));
            tv.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    scrollToLetter(letter, true);
                }
            });
            letterBar.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        // przesuwanie palcem (pionowo) po pasku liter
        letterBar.setOnTouchListener(new View.OnTouchListener() {
            private int selected = -1;

            @Override public boolean onTouch(View v, MotionEvent ev) {
                int a = ev.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                    int idx = letterIndexAt(ev.getY());   // pionowy pasek → wspolrzedna Y
                    if (idx >= 0 && idx != selected) {
                        if (selected >= 0) magnifyLetter(selected, false);
                        selected = idx;
                        magnifyLetter(idx, true);
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        scrollToLetter(String.valueOf(LETTERS.charAt(idx)), false);
                    }
                    return true;
                }
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    if (selected >= 0) magnifyLetter(selected, false);
                    selected = -1;
                    return true;
                }
                return false;
            }
        });

        // letterBar dostaje stala szerokosc + offset docka po lewej
        page.addView(letterBar, new LinearLayout.LayoutParams(
                dp(38) + extraL, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- PRAWY PANEL: search bar + lista ----
        LinearLayout contentPane = new LinearLayout(ctx);
        contentPane.setOrientation(LinearLayout.VERTICAL);
        contentPane.setClipChildren(false);
        contentPane.setClipToPadding(false);

        // Search bar z efektem "frosted glass" (polprzezroczyste tlo + elevacja)
        LinearLayout searchHeader = new LinearLayout(ctx);
        searchHeader.setOrientation(LinearLayout.HORIZONTAL);
        searchHeader.setGravity(Gravity.CENTER_VERTICAL);
        searchHeader.setClipChildren(false);
        searchHeader.setClipToPadding(false);
        searchHeader.setPadding(dp(12), dp(10), dp(12) + extraR, dp(10));
        searchHeader.setBackgroundColor(night ? 0xCC1C1C1E : 0xCCFFFFFF);
        searchHeader.setElevation(dp(8));

        searchEt = new EditText(ctx);
        searchEt.setHint("Biblioteka aplikacji");
        searchEt.setTextSize(17);
        searchEt.setTypeface(pl.dock.widget.ui.DockTheme.interRegular());
        searchEt.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        searchEt.setHintTextColor(night ? 0x99FFFFFF : 0x996C6C70);
        searchEt.setSingleLine(true);
        GradientDrawable field = new GradientDrawable();
        field.setCornerRadius(dp(22));
        field.setColor(night ? 0x33FFFFFF : 0xCCFFFFFF);
        field.setStroke(Math.max(1, (int) (0.5f * d)), night ? 0x33FFFFFF : 0x2E000000);
        searchEt.setBackground(field);
        clearSize = dp(20);
        searchEt.setCompoundDrawablePadding(dp(10));
        searchEt.setPadding(dp(14), dp(10), dp(14), dp(10));
        searchEt.setMinimumHeight(dp(46));
        searchEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                rawText = s == null ? "" : s.toString();
                query = norm(rawText);
                updateClear();
                rebuild();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchEt.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP && clearVisible
                        && e.getX() >= v.getWidth() - v.getPaddingRight() - clearSize) {
                    searchEt.setText("");
                    return true;
                }
                return false;
            }
        });
        updateClear();
        searchHeader.addView(searchEt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentPane.addView(searchHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView = new ScrollView(ctx);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        listCol = new LinearLayout(ctx);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(dp(14), dp(4), dp(14) + extraR, dp(28));
        scrollView.addView(listCol, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentPane.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        page.addView(contentPane, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        addView(page, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // lupa rysowana recznie (zero zasobow)
    private Bitmap magnifier() {
        int sz = dp(20);
        Bitmap b = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(night ? 0x99FFFFFF : 0x993C3C43);
        c.drawCircle(sz * 0.40f, sz * 0.40f, sz * 0.26f, p);
        c.drawLine(sz * 0.60f, sz * 0.60f, sz * 0.80f, sz * 0.80f, p);
        return b;
    }

    // szary okrag z bialym X - przycisk czyszczenia pola (jak w iOS)
    private Bitmap clearGlyph() {
        int sz = dp(20);
        Bitmap b = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF8E8E93);
        c.drawCircle(sz / 2f, sz / 2f, sz * 0.42f, p);
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        float a = sz * 0.24f;
        c.drawLine(sz / 2f - a, sz / 2f - a, sz / 2f + a, sz / 2f + a, p);
        c.drawLine(sz / 2f - a, sz / 2f + a, sz / 2f + a, sz / 2f - a, p);
        return b;
    }

    private void updateClear() {
        clearVisible = query != null && query.length() > 0;
        if (searchEt == null) return;
        searchEt.setCompoundDrawablesWithIntrinsicBounds(
                new BitmapDrawable(ctx.getResources(), magnifier()), null,
                clearVisible ? new BitmapDrawable(ctx.getResources(), clearGlyph()) : null, null);
    }

    // ---------------------------------------------------------------- lista

    private void rebuild() {
        if (listCol == null) return;
        listCol.removeAllViews();
        letterY.clear();

        List<AppsGridPage.Item> shown = new ArrayList<AppsGridPage.Item>();
        for (AppsGridPage.Item it : apps) {
            if (it == null) continue;
            String label = it.label == null ? "" : it.label;
            if (query != null && query.length() > 0) {
                // szukamy po ORYGINALNEJ nazwie oraz po nazwie podmienionej w Docku
                // (np. wpisane "AirDrop" ma pokazac "LocalSend")
                String alias = "";
                Overrides.O o = Overrides.match(label);
                if (o != null && o.label != null) alias = o.label;
                if (!norm(label).contains(query) && !norm(alias).contains(query)) continue;
            }
            shown.add(it);
        }

        if (shown.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText(query != null && query.length() > 0 ? "Brak wyników" : "Brak aplikacji");
            empty.setTextColor(night ? 0x99FFFFFF : 0x99666666);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            listCol.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        for (AppsGridPage.Item it : shown) {
            listCol.addView(makeRow(it), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        post(measureLetters);
    }

    // wiersz: zaokraglona ikona po lewej + nazwa po prawej (jak w Ustawieniach iOS)
    private View makeRow(final AppsGridPage.Item it) {
        final boolean builtin = it.settings || (it.action != null && it.action.length() > 0);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        row.setTag(it);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(14));
        mask.setColor(0xFFFFFFFF);   // maska ripple: liczy sie tylko alfa
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(night ? 0x26FFFFFF : 0x14000000),
                null, mask));

        ImageView iv = new ImageView(ctx);
        iv.setImageBitmap(iconFor(it));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(iv, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView name = new TextView(ctx);
        name.setText(it.label == null ? "" : it.label);
        name.setTextSize(17);
        name.setTypeface(pl.dock.widget.ui.DockTheme.interRegular());
        name.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(14);
        row.addView(name, nlp);

        row.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                dismissSearch();
                if (host != null) host.openApp(packageOf(it));
            }
        });
        if (!builtin) {
            // narzedzia launchera (Ustawienia / CarPlay / Honda OBD) nie dostaja menu
            row.setOnLongClickListener(new OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    showRowMenu(v, it);
                    return true;
                }
            });
        }
        return row;
    }

    private Bitmap iconFor(AppsGridPage.Item it) {
        if (it.settings) return Icons.builtin(ctx, "settings");
        if (it.action != null && it.action.length() > 0) {
            return Icons.builtin(ctx, "obd".equals(it.action) ? "honda" : "carplay");
        }
        if (it.builtinId != null && it.builtinId.length() > 0) {
            return Icons.builtin(ctx, it.builtinId);
        }
        if (it.entry != null) return Icons.forEntry(ctx, it.entry);
        // oryginalna ikona aplikacji - lista pokazuje oryginalne nazwy
        return Icons.forPackageRaw(ctx, it.pkg);
    }

    private String packageOf(AppsGridPage.Item it) {
        if (it.settings) return "dock:settings";
        if (it.action != null && it.action.length() > 0) {
            return "obd".equals(it.action) ? "dock:obd" : "dock:carplay";
        }
        return it.pkg;
    }

    // menu dlugiego przytrzymania: informacje / dodaj-usun z siatki / odinstaluj
    private void showRowMenu(final View anchor, final AppsGridPage.Item it) {
        if (!(ctx instanceof android.app.Activity)) return;
        final android.app.Activity a = (android.app.Activity) ctx;
        final boolean onGrid = Prefs.isAppSelected(ctx, it.pkg);
        String[] items = new String[]{
                "Informacje o aplikacji",
                onGrid ? "Usuń z siatki aplikacji" : "Dodaj do siatki aplikacji",
                "Odinstaluj"
        };
        Popups.menuNear(a, anchor, it.label, items, new Popups.OnSel() {
            @Override public void on(int idx) {
                if (idx == 0) {
                    if (host != null) host.showAppInfo(it.pkg);
                } else if (idx == 1) {
                    if (host != null) host.setOnGrid(it.pkg, !onGrid);
                } else if (idx == 2) {
                    if (host != null) host.uninstall(it.pkg);
                }
            }
        });
    }

    /** Wymusza odswiezenie listy po zmianie stanu siatki. */
    public void refreshFromHost() {
        if (host != null) host.onLibraryChanged();
    }

    // ---------------------------------------------------------------- pasek liter

    // pozycje liter liczone z tagow WIERSZY (wczesniej tag byl na dzieciach i pasek nie dzialal)
    private final Runnable measureLetters = new Runnable() {
        @Override public void run() {
            letterY.clear();
            if (listCol == null) return;
            for (int i = 0; i < listCol.getChildCount(); i++) {
                View row = listCol.getChildAt(i);
                Object tag = row.getTag();
                if (!(tag instanceof AppsGridPage.Item)) continue;
                String key = letterOf(((AppsGridPage.Item) tag).label);
                if (!letterY.containsKey(key)) letterY.put(key, row.getTop());
            }
        }
    };

    private String letterOf(String label) {
        if (label == null || label.length() == 0) return "#";
        String s = stripDiacritics(label.trim().toLowerCase(Locale.ROOT));
        if (s.length() == 0) return "#";
        char c = Character.toUpperCase(s.charAt(0));
        return c >= 'A' && c <= 'Z' ? String.valueOf(c) : "#";
    }

    private void scrollToLetter(String letter, boolean smooth) {
        if (scrollView == null) return;
        Integer y = letterY.get(letter);
        if (y == null) {
            // layout mogl jeszcze nie byc policzony - zmierz i sprobuj ponownie
            measureLetters.run();
            y = letterY.get(letter);
        }
        if (y == null) return;   // brak aplikacji na te litere
        int to = Math.max(0, y - dp(8));
        if (smooth) {
            scrollView.smoothScrollTo(0, to);
        } else {
            scrollView.scrollTo(0, to);
        }
    }

    private int letterIndexAt(float y) {
        if (letterBar == null) return -1;
        for (int i = 0; i < letterBar.getChildCount(); i++) {
            View ch = letterBar.getChildAt(i);
            if (y >= ch.getTop() && y <= ch.getBottom()) return i;
        }
        return -1;
    }

    // powiekszenie litery pod palcem; litera rosnie W PRAWO (pivot na lewej krawedzi)
    private void magnifyLetter(int idx, boolean on) {
        if (letterBar == null || idx < 0 || idx >= letterBar.getChildCount()) return;
        View tv = letterBar.getChildAt(idx);
        // pivot na LEWEJ krawedzi – litera rosnie w prawo, nie nachodzi na ikony
        tv.setPivotX(0f);
        tv.setPivotY(tv.getHeight() / 2f);
        float s = on ? 1.5f : 1f;
        tv.animate().scaleX(s).scaleY(s).translationX(on ? dp(6) : 0f)
                .setDuration(on ? 180 : 260).start();
    }

    // ---------------------------------------------------------------- narzedzia

    // male litery + bez polskich ogonkow (L->l, S->s, Z->z)
    static String norm(String s) {
        if (s == null) return "";
        return stripDiacritics(s.trim().toLowerCase(Locale.ROOT));
    }

    static String stripDiacritics(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'ą': b.append('a'); break;
                case 'ć': b.append('c'); break;
                case 'ę': b.append('e'); break;
                case 'ł': b.append('l'); break;
                case 'ń': b.append('n'); break;
                case 'ó': b.append('o'); break;
                case 'ś': b.append('s'); break;
                case 'ź': b.append('z'); break;
                case 'ż': b.append('z'); break;
                case 'Ą': b.append('A'); break;
                case 'Ć': b.append('C'); break;
                case 'Ę': b.append('E'); break;
                case 'Ł': b.append('L'); break;
                case 'Ń': b.append('N'); break;
                case 'Ó': b.append('O'); break;
                case 'Ś': b.append('S'); break;
                case 'Ź': b.append('Z'); break;
                case 'Ż': b.append('Z'); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissSearch();
    }

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }
}
