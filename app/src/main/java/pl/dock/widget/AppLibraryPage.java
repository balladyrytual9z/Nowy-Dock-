package pl.dock.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
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

public class AppLibraryPage extends FrameLayout {

    public interface Host {
        void openApp(String pkg);
        void onLibraryChanged();
        void setOnGrid(String pkg, boolean onGrid);
        void showAppInfo(String pkg);
        void uninstall(String pkg);
    }

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";

    private final Context ctx;
    private final float d;
    private boolean night;
    private Host host;
    private final List<AppsGridPage.Item> apps = new ArrayList<AppsGridPage.Item>();
    private EditText searchEt;
    private boolean clearVisible;
    private int clearSize;
    private LinearLayout letterBar;
    private LinearLayout searchHeader;
    private View headerGap;
    private ScrollView scrollView;
    private TextView letterBubble;   // iOS: duza litera nad palcem na pasku liter
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

    public void setHost(Host h) { this.host = h; }

    public void setNight(boolean n) {
        if (this.night == n && listCol != null) return;
        this.night = n;
        buildChrome();
        if (rawText.length() > 0) searchEt.setText(rawText);
        else rebuild();
    }

    public void setApps(List<AppsGridPage.Item> list) {
        apps.clear();
        if (list != null) for (AppsGridPage.Item it : list) if (it != null) apps.add(it);
        Collections.sort(apps, new Comparator<AppsGridPage.Item>() {
            @Override public int compare(AppsGridPage.Item a, AppsGridPage.Item b) {
                return norm(a.label).compareTo(norm(b.label));
            }
        });
        rebuild();
    }

    public void dismissSearch() {
        if (searchEt == null) return;
        try {
            searchEt.clearFocus();
            InputMethodManager im = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(searchEt.getWindowToken(), 0);
        } catch (Throwable ignored) {}
    }

    private void buildChrome() {
        removeAllViews();
        int dockInset = Ui.dp(ctx, Ui.DOCK_OFFSET_DP);
        boolean dockLeft = Ui.dockLeft(ctx);
        int extraR = dockLeft ? 0 : dockInset;

        LinearLayout page = new LinearLayout(ctx);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);

        letterBar = new LinearLayout(ctx);
        letterBar.setOrientation(LinearLayout.VERTICAL);
        letterBar.setGravity(Gravity.CENTER);
        letterBar.setClipChildren(false);
        letterBar.setClipToPadding(false);
        letterBar.setPadding(0, dp(4), 0, dp(4));
        for (int i = 0; i < LETTERS.length(); i++) {
            TextView tv = new TextView(ctx);
            tv.setText(String.valueOf(LETTERS.charAt(i)));
            tv.setTextSize(11);
            tv.setTypeface(pl.dock.widget.ui.DockTheme.interMedium());
            tv.setTextColor(night ? 0xFF0A84FF : 0xFF007AFF);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, 0, 0, 0);
            letterBar.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        letterBar.setOnTouchListener(new View.OnTouchListener() {
            private int selected = -1;
            private float downY;
            private boolean dragged;
            @Override public boolean onTouch(View v, MotionEvent ev) {
                int a = ev.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) { downY = ev.getY(); dragged = false; dragSelect(ev.getY()); return true; }
                if (a == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(ev.getY() - downY) > dp(4)) dragged = true;
                    dragSelect(ev.getY()); return true;
                }
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    if (a == MotionEvent.ACTION_UP && !dragged && selected >= 0)
                        scrollToLetter(String.valueOf(LETTERS.charAt(selected)), true);
                    hideLetterBubble();
                    selected = -1; return true;
                }
                // Pozostale akcje (np. multi-touch ACTION_POINTER_DOWN) zostaja
                // przy naszym gesture. Bez tego return javac odmawia kompilacji
                // ("missing return statement") - ten blad psul CI od ef018565.
                return true;
            }
            private void dragSelect(float y) {
                int idx = letterIndexAt(y);
                if (idx >= 0) {
                    if (idx != selected) {
                        selected = idx;
                        letterBar.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        scrollToLetter(String.valueOf(LETTERS.charAt(idx)), true);
                    }
                    showLetterBubble(String.valueOf(LETTERS.charAt(idx)), y);
                }
            }
        });

        LinearLayout leftCol = new LinearLayout(ctx);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setClipChildren(false);
        leftCol.setClipToPadding(false);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT);
        // pasek liter wycentrowany miedzy prawa krawedzia doka a lewa krawedzia
        // ikon aplikacji (ikony = 14dp padding listy + 16dp padding wiersza)
        if (dockLeft) colLp.leftMargin = dockInset + dp(15);
        headerGap = new View(ctx);
        leftCol.addView(headerGap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        LinearLayout.LayoutParams lbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lbLp.bottomMargin = dp(24);
        leftCol.addView(letterBar, lbLp);
        page.addView(leftCol, colLp);

        // Pasek szukania plywa NAD lista jak w iOS: aplikacje miekko zanikaja
        // (maska alpha) zanim dotra pod pigulke - pigulka wyglada jak wczesniej,
        // bez zadnego widocznego pasma, a pod nia zostaje wylacznie tapeta.
        android.widget.FrameLayout contentPane = new android.widget.FrameLayout(ctx);
        contentPane.setClipChildren(false);
        contentPane.setClipToPadding(false);

        searchHeader = new LinearLayout(ctx);
        searchHeader.setOrientation(LinearLayout.HORIZONTAL);
        searchHeader.setGravity(Gravity.CENTER_VERTICAL);
        searchHeader.setClipChildren(false);
        searchHeader.setClipToPadding(false);
        searchHeader.setPadding(dp(12), dp(10), dp(12) + extraR, dp(10));
        // szklana pigulka paska szukania - polprzezroczysta, lista widoczna pod spodem
        GradientDrawable glassPill = new GradientDrawable();
        glassPill.setCornerRadius(dp(26));
        glassPill.setColor(night ? 0x30FFFFFF : 0x4DF2F2F7);
        glassPill.setStroke(Math.max(1, (int) (0.9f * d)), night ? 0x3DFFFFFF : 0x2E808090);
        searchHeader.setBackground(glassPill);
        searchHeader.setElevation(0f);

        searchEt = new EditText(ctx);
        searchEt.setHint("Biblioteka aplikacji");
        searchEt.setTextSize(17);
        searchEt.setTypeface(pl.dock.widget.ui.DockTheme.interRegular());
        searchEt.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        searchEt.setHintTextColor(night ? 0x99FFFFFF : 0x996C6C70);
        searchEt.setSingleLine(true);
        // tlo pola = szklana pigulka naglowka (przezroczyste pole wewnatrz szkla)
        GradientDrawable field = new GradientDrawable();
        field.setCornerRadius(dp(22));
        field.setColor(0x00000000);
        field.setStroke(0, 0x00000000);
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
        searchHeader.addView(searchEt, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.FrameLayout.LayoutParams shLp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        shLp.topMargin = dp(8);
        contentPane.addView(searchHeader, shLp);

        scrollView = new FadeScrollView(ctx);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listCol = new LinearLayout(ctx);
        listCol.setOrientation(LinearLayout.VERTICAL);
        // lista przewija sie pod paskiem, ale wiersze miekko zanikaja (maska
        // alpha w FadeScrollView) zanim dotra pod pigulke - pod pigulka tylko tapeta
        listCol.setPadding(dp(14), dp(70), dp(14) + extraR, dp(28));
        scrollView.addView(listCol, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentPane.addView(scrollView, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // pasek szukania ZAWSZE nad lista (z-order): bringChildToFront po
        // dodaniu scrollView - w przeciwnym razie lista rysowala sie na pasku
        contentPane.bringChildToFront(searchHeader);
        page.addView(contentPane, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        addView(page, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // babel z aktualna litera NAD palcem (jak w iOS w selektorze liter)
        letterBubble = new TextView(ctx);
        letterBubble.setTextSize(46);
        letterBubble.setTypeface(null, android.graphics.Typeface.BOLD);
        letterBubble.setTextColor(0xFFFFFFFF);
        letterBubble.setGravity(Gravity.CENTER);
        letterBubble.setMinWidth(dp(84));
        letterBubble.setPadding(dp(10), dp(4), dp(10), dp(8));
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(GradientDrawable.RECTANGLE);
        bubbleBg.setCornerRadius(dp(26));
        bubbleBg.setColor(night ? 0xCC1C1C22 : 0xE61C1C22);
        bubbleBg.setStroke(Math.max(1, (int) (1f * d)), night ? 0x4DFFFFFF : 0x22808090);
        letterBubble.setBackground(bubbleBg);
        letterBubble.setVisibility(GONE);
        addView(letterBubble, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

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

    private void rebuild() {
        if (listCol == null) return;
        listCol.removeAllViews();
        letterY.clear();
        List<AppsGridPage.Item> shown = new ArrayList<AppsGridPage.Item>();
        for (AppsGridPage.Item it : apps) {
            if (it == null) continue;
            String label = it.label == null ? "" : it.label;
            if (query != null && query.length() > 0) {
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
            listCol.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (AppsGridPage.Item it : shown) {
            listCol.addView(makeRow(it), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        post(measureLetters);
    }

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
        mask.setColor(0xFFFFFFFF);
        row.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(night ? 0x26FFFFFF : 0x14000000), null, mask));
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
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(14);
        row.addView(name, nlp);
        row.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                dismissSearch();
                if (host != null) host.openApp(packageOf(it));
            }
        });
        if (!builtin) {
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
            if ("carplay".equals(it.action)) return Icons.builtin(ctx, "carplay");
            if ("obd".equals(it.action)) return Icons.builtin(ctx, "honda");
        }
        return Icons.forPackageRaw(ctx, it.pkg != null ? it.pkg : "");
    }

    private String packageOf(AppsGridPage.Item it) {
        if (it.settings) return "dock:settings";
        if (it.action != null && it.action.length() > 0) {
            return "obd".equals(it.action) ? "dock:obd" : "dock:carplay";
        }
        return it.pkg;
    }

    private void showRowMenu(final View anchor, final AppsGridPage.Item it) {
        if (!(ctx instanceof android.app.Activity)) return;
        final android.app.Activity a = (android.app.Activity) ctx;
        final String pkg = packageOf(it);
        final boolean onGrid = Prefs.isAppSelected(ctx, it.pkg);
        String[] items = new String[]{
                "Informacje o aplikacji",
                onGrid ? "Usuń z siatki aplikacji" : "Dodaj do siatki aplikacji",
                "Odinstaluj"
        };
        Popups.menu(a, it.label, items, new Popups.OnSel() {
            @Override public void on(int i) {
                if (i == 0 && host != null) host.showAppInfo(pkg);
                else if (i == 1 && host != null) host.setOnGrid(pkg, !onGrid);
                else if (i == 2 && host != null) host.uninstall(pkg);
            }
        });
    }

    private final Runnable measureLetters = new Runnable() {
        @Override public void run() {
            letterY.clear();
            if (listCol == null) return;
            for (int i = 0; i < listCol.getChildCount(); i++) {
                View v = listCol.getChildAt(i);
                Object tag = v.getTag();
                if (!(tag instanceof AppsGridPage.Item)) continue;
                AppsGridPage.Item it = (AppsGridPage.Item) tag;
                String label = it.label == null ? "" : it.label;
                String L = letterOf(label);
                if (!letterY.containsKey(L)) letterY.put(L, v.getTop());
            }
        }
    };

    private int letterIndexAt(float y) {
        if (letterBar == null || letterBar.getChildCount() == 0) return -1;
        int n = letterBar.getChildCount();
        float h = letterBar.getHeight() / (float) n;
        int idx = (int) (y / h);
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return idx;
    }

    /** iOS: babel z aktualna litera NAD palcem, sledzi pozycje palca na pasku liter. */
    private void showLetterBubble(String letter, float yInBar) {
        if (letterBubble == null || letterBar == null) return;
        letterBubble.setText(letter);
        letterBubble.setVisibility(VISIBLE);
        int[] lb = new int[2];
        letterBar.getLocationOnScreen(lb);
        int[] pg = new int[2];
        getLocationOnScreen(pg);
        float fingerY = lb[1] - pg[1] + yInBar;
        float bx = lb[0] - pg[0] + letterBar.getWidth() / 2f - dp(42);
        float by = Math.max(dp(70), fingerY - dp(90));
        LayoutParams lp = (LayoutParams) letterBubble.getLayoutParams();
        lp.leftMargin = (int) bx;
        lp.topMargin = (int) by;
        letterBubble.setLayoutParams(lp);
    }

    private void hideLetterBubble() {
        if (letterBubble != null) letterBubble.setVisibility(GONE);
    }

    private void scrollToLetter(String letter, boolean smooth) {
        Integer y = letterY.get(letter);
        if (y == null || scrollView == null) return;
        if (smooth) scrollView.smoothScrollTo(0, y);
        else scrollView.scrollTo(0, y);
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT);
    }

    private static String letterOf(String label) {
        String n = norm(label);
        if (n.length() == 0) return "#";
        char c = n.charAt(0);
        if (c >= 'a' && c <= 'z') return String.valueOf(Character.toUpperCase(c));
        return "#";
    }

    /** ScrollView z miekkim zanikiem tresci pod paskiem szukania: maska alpha
     *  (DST_IN) wygasza wiersze listy zanim dotra pod polprzezroczysta pigulke.
     *  Dzieki temu pod pigulka widac wylacznie tapete, a sam pasek wyglada jak
     *  wczesniej - bez zadnego dodatkowego pasma na tle tapety. */
    private class FadeScrollView extends ScrollView {
        private Paint mask;
        FadeScrollView(Context c) { super(c); }
        @Override public void draw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            int sc = c.saveLayer(0f, 0f, w, h, null, Canvas.ALL_SAVE_FLAG);
            super.draw(c);
            if (mask == null) {
                mask = new Paint(Paint.ANTI_ALIAS_FLAG);
                // alpha 0 nad dolna krawedzia paska (74dp), pelna widocznosc 26dp nizej;
                // shader z CLAMP wygasza tez caly obszar ukryty pod pigulka
                mask.setShader(new LinearGradient(0f, dp(74), 0f, dp(74) + dp(26),
                        0x00000000, 0xFFFFFFFF, Shader.TileMode.CLAMP));
                mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            }
            c.drawRect(0f, 0f, w, h, mask);
            c.restoreToCount(sc);
        }
    }

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }
}


