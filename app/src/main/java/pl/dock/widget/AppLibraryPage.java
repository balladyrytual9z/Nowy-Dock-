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
        letterBar.setPadding(0, dp(8), 0, dp(8));
        for (int i = 0; i < LETTERS.length(); i++) {
            TextView tv = new TextView(ctx);
            tv.setText(String.valueOf(LETTERS.charAt(i)));
            tv.setTextSize(13);
            tv.setTypeface(pl.dock.widget.ui.DockTheme.interMedium());
            tv.setTextColor(night ? 0xFF0A84FF : 0xFF007AFF);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(1), 0, dp(1));
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
                    if (selected >= 0) magnifyLetter(selected, false);
                    selected = -1; return true;
                }
                return false;
            }
            private void dragSelect(float y) {
                int idx = letterIndexAt(y);
                if (idx >= 0 && idx != selected) {
                    if (selected >= 0) magnifyLetter(selected, false);
                    selected = idx;
                    magnifyLetter(idx, true);
                    letterBar.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    scrollToLetter(String.valueOf(LETTERS.charAt(idx)), true);
                }
            }
        });

        LinearLayout leftCol = new LinearLayout(ctx);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setClipChildren(false);
        leftCol.setClipToPadding(false);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT);
        if (dockLeft) colLp.leftMargin = dockInset;
        headerGap = new View(ctx);
        leftCol.addView(headerGap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        LinearLayout.LayoutParams lbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lbLp.bottomMargin = dp(28);
        leftCol.addView(letterBar, lbLp);
        page.addView(leftCol, colLp);

        LinearLayout contentPane = new LinearLayout(ctx);
        contentPane.setOrientation(LinearLayout.VERTICAL);
        contentPane.setClipChildren(false);
        contentPane.setClipToPadding(false);

        searchHeader = new LinearLayout(ctx);
        searchHeader.setOrientation(LinearLayout.HORIZONTAL);
        searchHeader.setGravity(Gravity.CENTER_VERTICAL);
        searchHeader.setClipChildren(false);
        searchHeader.setClipToPadding(false);
        searchHeader.setPadding(dp(12), dp(10), dp(12) + extraR, dp(10));
        searchHeader.setBackground(null);
        searchHeader.setElevation(0f);

        searchEt = new EditText(ctx);
        searchEt.setHint("Biblioteka aplikacji");
        searchEt.setTextSize(17);
        searchEt.setTypeface(pl.dock.widget.ui.DockTheme.interRegular());
        searchEt.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        searchEt.setHintTextColor(night ? 0x99FFFFFF : 0x996C6C70);
        searchEt.setSingleLine(true);
        GradientDrawable field = new GradientDrawable();
        field.setCornerRadius(dp(22));
        field.setColor(night ? 0x28FFFFFF : 0x55FFFFFF);
        field.setStroke(Math.max(1, (int) (0.8f * d)), night ? 0x55FFFFFF : 0x33808090);
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
        contentPane.addView(searchHeader, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView = new ScrollView(ctx);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listCol = new LinearLayout(ctx);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(dp(14), dp(4), dp(14) + extraR, dp(28));
        scrollView.addView(listCol, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentPane.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(contentPane, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        addView(page, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        if (it.settings) return Prefs.PKG_SETTINGS;
        if (it.action != null && it.action.length() > 0) return "dock:" + it.action;
        return it.pkg;
    }

    private void showRowMenu(View anchor, final AppsGridPage.Item it) {
        final String pkg = packageOf(it);
        Popups.menu(ctx, it.label, new String[]{"Usuń z siatki aplikacji", "Informacje", "Odinstaluj"}, new Popups.OnSel() {
            @Override public void on(int i) {
                if (i == 0 && host != null) host.setOnGrid(pkg, false);
                else if (i == 1 && host != null) host.showAppInfo(pkg);
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

    private void magnifyLetter(int idx, boolean on) {
        if (letterBar == null || idx < 0 || idx >= letterBar.getChildCount()) return;
        TextView tv = (TextView) letterBar.getChildAt(idx);
        tv.setTextSize(on ? 22 : 13);
        tv.setScaleX(on ? 1.35f : 1f);
        tv.setScaleY(on ? 1.35f : 1f);
        tv.setTranslationX(on ? dp(8) : 0);
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

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }
}
