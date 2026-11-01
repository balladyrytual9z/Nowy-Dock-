
package pl.dock.widget;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Okno âDodaj narzÄdzieâ: lista narzÄdzi i skrÃ³tÃ³w do ustawieÅ dostÄpnych na tym tablecie,
// z wyszukiwarkÄ. JuÅ¼ dodane pozycje sÄ wyszarzone.
public final class ToolPicker {

    public interface OnPick {
        void pick(String id);
    }

    private static final int PER_ROW = 5;

    private ToolPicker() {
    }

    public static void show(final Activity a, final boolean night, final List<String> have,
                            final OnPick cb) {
        final Dialog[] ref = new Dialog[1];
        final LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 16);
        c.setPadding(pad, Ui.dp(a, 14), pad, Ui.dp(a, 12));

        TextView title = Popups.text(a, "Dodaj narzÄdzie", 17, Popups.txt(night), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, Ui.dp(a, 10));
        c.addView(title);

        final EditText search = new EditText(a);
        search.setHint("Szukaj narzÄdzi");
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextSize(16);
        search.setTextColor(Popups.txt(night));
        search.setHintTextColor(Popups.sub(night));
        search.setBackground(Ui.round(night ? 0x33FFFFFF : 0x14000000, 18, a));
        search.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 42));
        sp.bottomMargin = Ui.dp(a, 8);
        c.addView(search, sp);

        final int maxH = Math.max(Ui.dp(a, 180),
                a.getResources().getDisplayMetrics().heightPixels - Ui.dp(a, 230));
        ScrollView sv = new ScrollView(a) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
            }
        };
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        c.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = new Runnable() {
            @Override
            public void run() {
                fill(a, night, list, search.getText().toString(), have, new OnPick() {
                    @Override
                    public void pick(String id) {
                        if (ref[0] != null) ref[0].dismiss();
                        cb.pick(id);
                    }
                });
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c1, int a1) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c1) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                rebuild[0].run();
            }
        });
        rebuild[0].run();

        ref[0] = Popups.panel(a, c, 580, Gravity.CENTER);
        try {
            ref[0].getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        } catch (Throwable ignored) {
        }
        ref[0].show();
    }

    private static void fill(Activity a, boolean night, LinearLayout list, String query,
                             List<String> have, OnPick cb) {
        list.removeAllViews();
        List<PanelTool.Def> apps = PanelTool.installedApps(a);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        String[] cats = {PanelTool.CAT_TOOLS, PanelTool.CAT_SETTINGS, PanelTool.CAT_APPS};
        boolean any = false;
        for (String cat : cats) {
            List<PanelTool.Def> defs = new ArrayList<PanelTool.Def>();
            List<PanelTool.Def> src = PanelTool.CAT_APPS.equals(cat) ? apps : PanelTool.all();
            for (PanelTool.Def d : src) {
                if (!d.cat.equals(cat)) continue;
                if (!PanelTool.available(a, d)) continue;
                if (q.length() > 0 && !d.label.toLowerCase(Locale.getDefault()).contains(q)) continue;
                defs.add(d);
            }
            if (defs.isEmpty()) continue;
            any = true;
            TextView h = Popups.text(a, cat, 13, Popups.sub(night), true);
            h.setPadding(Ui.dp(a, 4), Ui.dp(a, 10), 0, Ui.dp(a, 6));
            list.addView(h);
            for (int i = 0; i < defs.size(); i += PER_ROW) {
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int j = 0; j < PER_ROW; j++) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 100), 1f);
                    if (i + j < defs.size()) {
                        final PanelTool.Def def = defs.get(i + j);
                        final boolean added = have.contains(def.id);
                        Cell cell = new Cell(a, def, night, added);
                        if (!added) {
                            cell.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    cb.pick(def.id);
                                }
                            });
                        }
                        row.addView(cell, lp);
                    } else {
                        row.addView(new View(a), lp);
                    }
                }
                list.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        if (!any) {
            TextView t = Popups.text(a, "Nic nie pasuje do wyszukiwania", 15, Popups.sub(night), false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(a, 30), 0, Ui.dp(a, 30));
            list.addView(t);
        }
    }

    // pojedyncza pozycja w oknie wyboru
    private static class Cell extends View {
        private final PanelTool.Def def;
        private final boolean night;
        private final boolean added;
        private final float d;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Cell(Context c, PanelTool.Def def, boolean night, boolean added) {
            super(c);
            this.def = def;
            this.night = night;
            this.added = added;
            this.d = c.getResources().getDisplayMetrics().density;
            setClickable(!added);
            setContentDescription(def.label);
        }

        private String fit(String s, float maxW) {
            if (p.measureText(s) <= maxW) return s;
            while (s.length() > 1 && p.measureText(s + "â¦") > maxW) {
                s = s.substring(0, s.length() - 1);
            }
            return s + "â¦";
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float dia = 54f * d;
            float cx = w / 2f;
            float cy = 4f * d + dia / 2f;
            p.reset();
            p.setAntiAlias(true);
            p.setStyle(Paint.Style.FILL);
            p.setColor(night ? 0xFF3A3A3C : 0xFF8E8E93);
            p.setAlpha(added ? 90 : 255);
            if (def.kind != PanelTool.K_APP) cv.drawCircle(cx, cy, dia / 2f, p);
            if (def.kind == PanelTool.K_APP) {
                android.graphics.Bitmap ic = null;
                try {
                    ic = Icons.forPackage(getContext(), def.action);
                } catch (Throwable ignored) {
                }
                if (ic != null) {
                    android.graphics.RectF dst = new android.graphics.RectF(cx - dia / 2f, cy - dia / 2f,
                            cx + dia / 2f, cy + dia / 2f);
                    Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    bp.setAlpha(added ? 90 : 255);
                    cv.drawBitmap(ic, null, dst, bp);
                }
            } else {
                PanelTool.glyph(cv, def.glyph, cx, cy, dia / 2f, added ? 0x80FFFFFF : 0xFFFFFFFF);
            }

            p.reset();
            p.setAntiAlias(true);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity);
            p.setColor(night ? 0xFFFFFFFF : 0xFF111114);
            if (added) p.setAlpha(110);
            // podpis w jednej lub dwÃ³ch liniach
            String s = def.label;
            float maxW = w - 6f * d;
            float base = 4f * d + dia + 15f * d;
            if (p.measureText(s) <= maxW) {
                cv.drawText(s, cx, base, p);
            } else {
                int mid = s.length() / 2;
                int best = -1;
                for (int i = 0; i < s.length(); i++) {
                    if (s.charAt(i) == ' ' && (best < 0 || Math.abs(i - mid) < Math.abs(best - mid))) best = i;
                }
                if (best < 0) {
                    cv.drawText(fit(s, maxW), cx, base, p);
                } else {
                    cv.drawText(fit(s.substring(0, best), maxW), cx, base - 6f * d, p);
                    cv.drawText(fit(s.substring(best + 1), maxW), cx, base + 7f * d, p);
                }
            }
            if (added) {
                // znacznik âdodanoâ
                float bx = cx + dia / 2f - 4f * d;
                float by = cy - dia / 2f + 6f * d;
                p.setAlpha(255);
                p.setColor(0xFF30D158);
                cv.drawCircle(bx, by, 8f * d, p);
                p.setColor(0xFFFFFFFF);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f * d);
                p.setStrokeCap(Paint.Cap.ROUND);
                cv.drawLine(bx - 3.5f * d, by, bx - 1f * d, by + 3f * d, p);
                cv.drawLine(bx - 1f * d, by + 3f * d, bx + 4f * d, by - 3f * d, p);
            }
        }
    }
}


