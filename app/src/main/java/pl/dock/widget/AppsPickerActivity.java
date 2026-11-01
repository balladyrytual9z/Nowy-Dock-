
package pl.dock.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ekran wyboru aplikacji widocznych w siatce launchera. */
public class AppsPickerActivity extends BaseActivity {

    private Look.Pal p;
    private boolean night;

    private static class Row {
        String label;
        String pkg;
        boolean included;
    }

    private final List<Row> rows = new ArrayList<Row>();
    private ListView list;
    private RowAdapter adapter;

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        night = Look.night(this);
        setTheme(night ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(b);
        p = Look.pal(night);
        loadRows();
        build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppListWatcher.register(this);
        AppListWatcher.get().addListener(appsListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        AppListWatcher.get().removeListener(appsListener);
    }

    private final AppListWatcher.Listener appsListener = new AppListWatcher.Listener() {
        @Override
        public void onAppsChanged() {
            loadRows();
            if (adapter != null) adapter.notifyDataSetChanged();
        }
    };

    private void loadRows() {
        rows.clear();
        Set<String> selected = new HashSet<String>(Prefs.selectedPkgs(this));
        boolean emptyMeansAll = selected.isEmpty();

        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        Set<String> seen = new HashSet<String>();
        List<Row> all = new ArrayList<Row>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            if (!seen.add(pkg)) continue;
            Row r = new Row();
            r.pkg = pkg;
            r.label = ri.loadLabel(pm).toString();
            Overrides.O o = Overrides.match(r.label);
            if (o != null) r.label = o.label;
            r.included = emptyMeansAll || selected.contains(pkg);
            all.add(r);
        }
        List<String> order = Prefs.selectedPkgs(this);
        if (!order.isEmpty()) {
            List<Row> ordered = new ArrayList<Row>();
            for (String pkg : order) {
                for (Row r : all) {
                    if (r.pkg.equals(pkg)) {
                        ordered.add(r);
                        break;
                    }
                }
            }
            List<Row> rest = new ArrayList<Row>();
            for (Row r : all) if (!order.contains(r.pkg)) rest.add(r);
            Collections.sort(rest, new Comparator<Row>() {
                @Override
                public int compare(Row a, Row b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
            ordered.addAll(rest);
            all = ordered;
        } else {
            Collections.sort(all, new Comparator<Row>() {
                @Override
                public int compare(Row a, Row b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
        }
        rows.addAll(all);
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(night ? 0xFF000000 : 0xFFF2F2F7);

        FrameLayout titleBar = new FrameLayout(this);
        titleBar.setPadding(dp(12), dp(10), dp(12), dp(6));

        TextView back = new TextView(this);
        back.setText("â¹");
        back.setTextSize(34);
        back.setTextColor(p.text);
        back.setGravity(Gravity.CENTER);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(night ? 0xFF1C1C1E : 0xFFE5E5EA);
        backBg.setCornerRadius(dp(24));
        back.setBackground(backBg);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        titleBar.addView(back, new FrameLayout.LayoutParams(dp(48), dp(48),
                Gravity.START | Gravity.CENTER_VERTICAL));

        TextView title = new TextView(this);
        title.setText("Aplikacje");
        title.setTextSize(22);
        title.setTextColor(p.text);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        titleBar.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        TextView reset = new TextView(this);
        reset.setText("Wyzeruj");
        reset.setTextSize(16);
        reset.setTextColor(p.text);
        reset.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable rb = new GradientDrawable();
        rb.setColor(night ? 0xFF1C1C1E : 0xFFE5E5EA);
        rb.setCornerRadius(dp(22));
        reset.setBackground(rb);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetDefaults();
            }
        });
        titleBar.addView(reset, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL));

        root.addView(titleBar);

        TextView sub = new TextView(this);
        sub.setText("ObjÄte aplikacje");
        sub.setTextSize(15);
        sub.setTextColor(0xFF8E8E93);
        sub.setPadding(dp(24), dp(10), dp(24), dp(6));
        root.addView(sub);

        FrameLayout card = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(night ? 0xFF1C1C1E : 0xFFFFFFFF);
        cardBg.setCornerRadius(dp(28));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        clp.setMargins(dp(12), 0, dp(12), dp(12));
        root.addView(card, clp);

        list = new ListView(this);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(0, dp(6), 0, dp(6));
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        card.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        persistOrder();
    }

    private void persistOrder() {
        List<String> inc = new ArrayList<String>();
        for (Row r : rows) if (r.included) inc.add(r.pkg);
        Prefs.setSelectedPkgs(this, inc);
        AppListWatcher.notifyChanged(this);
    }

    private void toggle(Row r) {
        r.included = !r.included;
        adapter.notifyDataSetChanged();
        persistOrder();
    }

    private void resetDefaults() {
        new AlertDialog.Builder(this)
                .setTitle("WyzerowaÄ wybÃ³r?")
                .setMessage("Wszystkie aplikacje stanÄ siÄ widoczne w siatce, w kolejnoÅci alfabetycznej.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Wyzeruj", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Prefs.setSelectedPkgs(AppsPickerActivity.this, new ArrayList<String>());
                        loadRows();
                        adapter.notifyDataSetChanged();
                        AppListWatcher.notifyChanged(AppsPickerActivity.this);
                    }
                }).show();
    }

    private class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int i) {
            return rows.get(i);
        }

        @Override
        public long getItemId(int i) {
            return rows.get(i).pkg.hashCode();
        }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            ViewHolder vh;
            if (cv == null) {
                cv = buildRow();
                vh = (ViewHolder) cv.getTag();
            } else {
                vh = (ViewHolder) cv.getTag();
            }
            final Row r = rows.get(pos);
            if (!r.included) {
                vh.label.setTextColor(0xFF8E8E93);
                vh.icon.setAlpha(0.5f);
            } else {
                vh.label.setTextColor(p.text);
                vh.icon.setAlpha(1f);
            }
            vh.label.setText(r.label);
            vh.icon.setImageBitmap(Icons.forPackage(AppsPickerActivity.this, r.pkg));
            vh.minus.setVisibility(r.included ? View.VISIBLE : View.GONE);
            return cv;
        }

        private View buildRow() {
            LinearLayout row = new LinearLayout(AppsPickerActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(64));
            row.setPadding(dp(12), dp(6), dp(12), dp(6));

            TextView minus = new TextView(AppsPickerActivity.this);
            minus.setText("â");
            minus.setTextSize(26);
            minus.setTextColor(Color.WHITE);
            minus.setGravity(Gravity.CENTER);
            GradientDrawable mBg = new GradientDrawable();
            mBg.setColor(0xFFFF453A);
            mBg.setShape(GradientDrawable.OVAL);
            minus.setBackground(mBg);
            row.addView(minus, new LinearLayout.LayoutParams(dp(28), dp(28)));

            View gap = new View(AppsPickerActivity.this);
            row.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));

            ImageView icon = new ImageView(AppsPickerActivity.this);
            row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

            View gap2 = new View(AppsPickerActivity.this);
            row.addView(gap2, new LinearLayout.LayoutParams(dp(14), 1));

            TextView label = new TextView(AppsPickerActivity.this);
            label.setTextSize(19);
            label.setTextColor(p.text);
            label.setSingleLine(true);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView handle = new ImageView(AppsPickerActivity.this);
            handle.setImageBitmap(handleBitmap());
            row.addView(handle, new LinearLayout.LayoutParams(dp(36), dp(36)));

            ViewHolder vh = new ViewHolder();
            vh.minus = minus;
            vh.icon = icon;
            vh.label = label;
            vh.handle = handle;
            row.setTag(vh);

            minus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    View parent = (View) v.getParent();
                    int p = list.getPositionForView(parent);
                    if (p >= 0 && p < rows.size()) {
                        rows.get(p).included = false;
                        adapter.notifyDataSetChanged();
                        persistOrder();
                    }
                }
            });

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int p = list.getPositionForView(v);
                    if (p >= 0 && p < rows.size()) toggle(rows.get(p));
                }
            });

            final DragHelper drag = new DragHelper();
            handle.setOnTouchListener(drag);
            drag.row = row;
            return row;
        }

        private Bitmap handleBitmap() {
            int s = dp(36);
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
            pt.setColor(0x66FFFFFF);
            pt.setStrokeWidth(dp(2));
            pt.setStrokeCap(Paint.Cap.ROUND);
            float y0 = s * 0.36f, y1 = s * 0.50f, y2 = s * 0.64f;
            float x0 = s * 0.30f, x1 = s * 0.70f;
            c.drawLine(x0, y0, x1, y0, pt);
            c.drawLine(x0, y1, x1, y1, pt);
            c.drawLine(x0, y2, x1, y2, pt);
            return b;
        }
    }

    private static class ViewHolder {
        TextView minus;
        ImageView icon;
        TextView label;
        ImageView handle;
    }

    private class DragHelper implements View.OnTouchListener {
        View row;
        int startPos = -1;
        float downY;
        int slop = ViewConfiguration.get(AppsPickerActivity.this).getScaledTouchSlop();
        boolean dragging;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startPos = list.getPositionForView(row);
                    downY = e.getRawY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (startPos < 0) return false;
                    float dy = e.getRawY() - downY;
                    if (!dragging && Math.abs(dy) > slop) dragging = true;
                    if (dragging) {
                        int target = list.pointToPosition(
                                (int) (v.getX() + row.getLeft()),
                                (int) e.getRawY() - list.getTop() - list.getPaddingTop());
                        if (target < 0) {
                            int first = list.getFirstVisiblePosition();
                            View firstV = list.getChildAt(0);
                            if (firstV != null) {
                                int h = Math.max(1, firstV.getHeight());
                                target = first + Math.max(0, Math.min(list.getCount() - 1,
                                        Math.round((e.getRawY() - list.getTop() - firstV.getTop()) / h)));
                            }
                        }
                        if (target >= 0 && target < rows.size() && target != startPos) {
                            Row r = rows.remove(startPos);
                            rows.add(target, r);
                            startPos = target;
                            adapter.notifyDataSetChanged();
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) persistOrder();
                    dragging = false;
                    startPos = -1;
                    return true;
                default:
                    return false;
            }
        }
    }
}






