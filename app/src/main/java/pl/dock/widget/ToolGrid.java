package pl.dock.widget;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Siatka narzędzi panelu łączności. Układa kafelki jak Centrum sterowania w iPhonie,
// a w trybie edycji pozwala je przeciągać (zmiana kolejności) i usuwać.
public class ToolGrid extends ViewGroup {

    public interface Host {
        /** Własny widok dla suwaków i przełącznika wyglądu; null = zwykły kafelek. */
        View create(PanelTool.Def d);

        /** Kliknięcie (longPress = false) albo długie przytrzymanie kafelka. */
        void onTool(PanelTool.Def d, boolean longPress);

        /** Zmieniła się lista narzędzi (kolejność, dodanie, usunięcie). */
        void onChanged(List<String> ids);
    }

    private static class Item {
        PanelTool.Def def;
        View view;
        int col;
        int row;
    }

    private final Host host;
    private final boolean night;
    private final float d;
    private final List<Item> items = new ArrayList<Item>();
    private final Map<View, ObjectAnimator> wobbles = new HashMap<View, ObjectAnimator>();
    private final Random rnd = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int slop;

    private boolean editing = false;
    private int cols = 6;
    private int cell;
    private int gap;
    private int offX;

    // przeciąganie
    private Item downItem;
    private boolean badgeHit;
    private float downX;
    private float downY;
    private Item dragItem;
    private float grabX;
    private float grabY;
    private long lastSwap;

    public ToolGrid(Context c, boolean night, Host host) {
        super(c);
        this.night = night;
        this.host = host;
        this.d = c.getResources().getDisplayMetrics().density;
        this.slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClipChildren(true);
        setClipToPadding(true);
    }

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }

    // ---------------------------------------------------------------- lista narzędzi

    public void setTools(List<String> ids) {
        for (Item it : items) {
            stopWobble(it.view);
        }
        items.clear();
        removeAllViews();
        for (String id : ids) {
            PanelTool.Def def = PanelTool.find(id);
            if (def != null) addItem(def);
        }
        if (editing) startAllWobbles();
        requestLayout();
    }

    private Item addItem(PanelTool.Def def) {
        Item it = new Item();
        it.def = def;
        View v = host.create(def);
        if (v == null) v = new Tile(getContext(), def, night, host, this);
        it.view = v;
        items.add(it);
        addView(v);
        return it;
    }

    public boolean has(String id) {
        for (Item it : items) {
            if (it.def.id.equals(id)) return true;
        }
        return false;
    }

    public List<String> ids() {
        List<String> l = new ArrayList<String>();
        for (Item it : items) {
            l.add(it.def.id);
        }
        return l;
    }

    public void addTool(String id) {
        PanelTool.Def def = PanelTool.find(id);
        if (def == null || has(id)) return;
        Item it = addItem(def);
        if (editing) startWobble(it.view);
        it.view.setAlpha(0f);
        it.view.setScaleX(0.6f);
        it.view.setScaleY(0.6f);
        it.view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();
        requestLayout();
        host.onChanged(ids());
    }

    public void refreshTiles() {
        for (Item it : items) {
            if (it.view instanceof Tile) ((Tile) it.view).refresh();
        }
    }

    public void setEditing(boolean on) {
        if (editing == on) return;
        editing = on;
        if (on) {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            setClipChildren(false);
            setClipToPadding(false);
            startAllWobbles();
        } else {
            for (Item it : items) {
                stopWobble(it.view);
            }
            dragItem = null;
            setClipChildren(true);
            setClipToPadding(true);
            downItem = null;
        }
        invalidate();
    }

    // ---------------------------------------------------------------- drżenie kafelków

    private void startAllWobbles() {
        for (Item it : items) {
            startWobble(it.view);
        }
    }

    private void startWobble(View v) {
        stopWobble(v);
        // pivot na srodku kafelka - bez tego obrot wokol rogu (0,0) psul siatke
        if (v.getWidth() > 0 && v.getHeight() > 0) {
            v.setPivotX(v.getWidth() / 2f);
            v.setPivotY(v.getHeight() / 2f);
            startWobbleAnim(v);
        } else {
            final View fv = v;
            fv.post(new Runnable() {
                @Override
                public void run() {
                    if (!editing || fv.getWidth() <= 0) return;
                    fv.setPivotX(fv.getWidth() / 2f);
                    fv.setPivotY(fv.getHeight() / 2f);
                    startWobbleAnim(fv);
                }
            });
        }
    }

    private void startWobbleAnim(View v) {
        ObjectAnimator oa = ObjectAnimator.ofFloat(v, View.ROTATION, -1.7f, 1.7f);
        oa.setDuration(110 + rnd.nextInt(60));
        oa.setRepeatMode(ValueAnimator.REVERSE);
        oa.setRepeatCount(ValueAnimator.INFINITE);
        oa.start();
        wobbles.put(v, oa);
    }

    private void stopWobble(View v) {
        ObjectAnimator oa = wobbles.remove(v);
        if (oa != null) oa.cancel();
        if (v != null) v.setRotation(0f);
    }

    // ---------------------------------------------------------------- układ

    private boolean fits(List<boolean[]> occ, int r, int c, int w, int h) {
        for (int i = r; i < r + h; i++) {
            while (occ.size() <= i) occ.add(new boolean[cols]);
            boolean[] row = occ.get(i);
            for (int j = c; j < c + w; j++) {
                if (row[j]) return false;
            }
        }
        return true;
    }

    // ustawia kafelki w pierwszych wolnych miejscach; zwraca liczbę wierszy
    private int pack() {
        List<boolean[]> occ = new ArrayList<boolean[]>();
        int rows = 0;
        for (Item it : items) {
            int w = Math.min(it.def.w, cols);
            int h = it.def.h;
            boolean placed = false;
            for (int r = 0; !placed; r++) {
                for (int c = 0; c + w <= cols && !placed; c++) {
                    if (fits(occ, r, c, w, h)) {
                        for (int i = r; i < r + h; i++) {
                            for (int j = c; j < c + w; j++) {
                                occ.get(i)[j] = true;
                            }
                        }
                        it.col = c;
                        it.row = r;
                        placed = true;
                        rows = Math.max(rows, r + h);
                    }
                }
            }
        }
        return rows;
    }

    private int itemW(Item it) {
        int w = Math.min(it.def.w, cols);
        return w * cell + (w - 1) * gap;
    }

    private int itemH(Item it) {
        return it.def.h * cell + (it.def.h - 1) * gap;
    }

    private int itemL(Item it) {
        return offX + it.col * (cell + gap);
    }

    private int itemT(Item it) {
        return it.row * (cell + gap);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        if (MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED || w <= 0) w = dp(520);
        gap = dp(10);
        cols = w >= dp(430) ? 6 : 4;
        cell = Math.min(dp(96), (w - (cols - 1) * gap) / cols);
        int rows = pack();
        int availH = getResources().getDisplayMetrics().heightPixels - dp(210);
        if (rows > 0) {
            int need = rows * cell + (rows - 1) * gap;
            if (need > availH) {
                cell = Math.max(dp(56), (availH - (rows - 1) * gap) / rows);
            }
        }
        offX = Math.max(0, (w - (cols * cell + (cols - 1) * gap)) / 2);
        int h = rows == 0 ? dp(64) : rows * cell + (rows - 1) * gap;
        for (Item it : items) {
            it.view.measure(MeasureSpec.makeMeasureSpec(itemW(it), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(itemH(it), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (Item it : items) {
            int x = itemL(it);
            int y = itemT(it);
            it.view.layout(x, y, x + itemW(it), y + itemH(it));
        }
    }

    // przestawia kafelki po zmianie kolejności i płynnie przesuwa je na nowe miejsca
    private void reflow() {
        int n = items.size();
        int[] ol = new int[n];
        int[] ot = new int[n];
        for (int i = 0; i < n; i++) {
            ol[i] = items.get(i).view.getLeft();
            ot[i] = items.get(i).view.getTop();
        }
        pack();
        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            int x = itemL(it);
            int y = itemT(it);
            View v = it.view;
            v.layout(x, y, x + itemW(it), y + itemH(it));
            float dx = ol[i] - x;
            float dy = ot[i] - y;
            if (it == dragItem) {
                v.setTranslationX(v.getTranslationX() + dx);
                v.setTranslationY(v.getTranslationY() + dy);
            } else if (dx != 0f || dy != 0f) {
                v.setTranslationX(v.getTranslationX() + dx);
                v.setTranslationY(v.getTranslationY() + dy);
                v.animate().translationX(0f).translationY(0f).setDuration(200).start();
            }
        }
        requestLayout();
    }

    // ---------------------------------------------------------------- rysowanie plakietek usuwania

    private float badgeX(Item it) {
        return it.view.getLeft() + it.view.getTranslationX() + dp(12);
    }

    private float badgeY(Item it) {
        return it.view.getTop() + it.view.getTranslationY() + dp(12);
    }

    @Override
    protected void dispatchDraw(Canvas cv) {
        super.dispatchDraw(cv);
        if (items.isEmpty() && !editing) {
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(14f * getResources().getDisplayMetrics().scaledDensity);
            p.setColor(night ? 0xFFEBEBF0 : 0xFF3C3C43);
            cv.drawText("Brak narzędzi. Naciśnij Edytuj i dodaj własne.",
                    getWidth() / 2f, getHeight() / 2f + dp(5), p);
        }
        if (!editing) return;
        for (Item it : items) {
            if (it.view.getAlpha() < 0.5f) continue;
            float bx = badgeX(it);
            float by = badgeY(it);
            float br = dp(11);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFF3B30);
            cv.drawCircle(bx, by, br, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f * d);
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(bx, by, br, p);
            p.setStrokeWidth(2.4f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            float arm = br * 0.45f;
            cv.drawLine(bx - arm, by, bx + arm, by, p);
        }
    }

    // ---------------------------------------------------------------- dotyk w trybie edycji

    private Item itemAt(float x, float y) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            View v = it.view;
            float l = v.getLeft() + v.getTranslationX();
            float t = v.getTop() + v.getTranslationY();
            if (x >= l && x <= l + v.getWidth() && y >= t && y <= t + v.getHeight()) return it;
        }
        return null;
    }

    private boolean inBadge(Item it, float x, float y) {
        float dx = x - badgeX(it);
        float dy = y - badgeY(it);
        float r = dp(20);
        return dx * dx + dy * dy <= r * r;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return editing;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!editing) return false;
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                downItem = itemAt(x, y);
                badgeHit = downItem != null && inBadge(downItem, x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (downItem == null) return true;
                if (dragItem == null && !badgeHit
                        && (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop)) {
                    startDrag(downItem, x, y);
                }
                if (dragItem != null) updateDrag(x, y);
                return true;
            case MotionEvent.ACTION_UP:
                if (dragItem != null) {
                    endDrag();
                } else if (downItem != null && badgeHit && inBadge(downItem, x, y)) {
                    removeItem(downItem);
                }
                downItem = null;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragItem != null) endDrag();
                downItem = null;
                return true;
            default:
                return true;
        }
    }

    private void startDrag(Item it, float x, float y) {
        dragItem = it;
        View v = it.view;
        stopWobble(v);
        v.bringToFront();
        grabX = x - v.getLeft();
        grabY = y - v.getTop();
        v.animate().cancel();
        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
        v.setElevation(dp(6));   // iOS: kafelek unosci sie nad reszte
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    private void updateDrag(float x, float y) {
        View v = dragItem.view;
        v.setTranslationX(x - grabX - v.getLeft());
        v.setTranslationY(y - grabY - v.getTop());
        long now = System.currentTimeMillis();
        if (now - lastSwap < 180) return;
        float cx = v.getLeft() + v.getTranslationX() + v.getWidth() / 2f;
        float cy = v.getTop() + v.getTranslationY() + v.getHeight() / 2f;
        for (Item other : items) {
            if (other == dragItem) continue;
            View ov = other.view;
            if (cx >= ov.getLeft() && cx <= ov.getRight() && cy >= ov.getTop() && cy <= ov.getBottom()) {
                int from = items.indexOf(dragItem);
                int to = items.indexOf(other);
                items.remove(from);
                items.add(to, dragItem);
                lastSwap = now;
                reflow();
                break;
            }
        }
    }

    private void endDrag() {
        final Item it = dragItem;
        dragItem = null;
        if (it == null) return;
        final View v = it.view;
        v.setElevation(0f);
        v.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(180).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (editing && items.contains(it)) startWobble(v);
                    }
                }).start();
        host.onChanged(ids());
    }

    private void removeItem(Item it) {
        final View v = it.view;
        items.remove(it);
        stopWobble(v);
        reflow();
        v.animate().alpha(0f).scaleX(0.4f).scaleY(0.4f).setDuration(150)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        removeView(v);
                        requestLayout();
                    }
                }).start();
        host.onChanged(ids());
    }

    // ---------------------------------------------------------------- kafelek (okrągły przycisk z podpisem)

    public static class Tile extends View {
        private final PanelTool.Def def;
        private final boolean night;
        private final float d;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private boolean on;
        private String[] lines;

        Tile(Context c, final PanelTool.Def def, boolean night, final Host host, ToolGrid grid) {
            super(c);
            this.def = def;
            this.night = night;
            this.d = c.getResources().getDisplayMetrics().density;
            setContentDescription(def.label);
            setClickable(true);
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.onTool(def, false);
                }
            });
            setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    host.onTool(def, true);
                    return true;
                }
            });
            refresh();
        }

        void refresh() {
            on = PanelTool.isOn(getContext(), def);
            invalidate();
        }

        private float textSize() {
            return 11f * getResources().getDisplayMetrics().scaledDensity;
        }

        private String fit(String s, float maxW) {
            if (p.measureText(s) <= maxW) return s;
            while (s.length() > 1 && p.measureText(s + "…") > maxW) {
                s = s.substring(0, s.length() - 1);
            }
            return s + "…";
        }

        private String[] split(String s, float maxW) {
            if (p.measureText(s) <= maxW) return new String[]{s};
            int mid = s.length() / 2;
            int best = -1;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == ' ' && (best < 0 || Math.abs(i - mid) < Math.abs(best - mid))) best = i;
            }
            if (best < 0) return new String[]{fit(s, maxW)};
            return new String[]{fit(s.substring(0, best), maxW), fit(s.substring(best + 1), maxW)};
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            p.setTextSize(textSize());
            lines = split(def.label, w - 4 * d);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float dia = Math.min(w - 6f * d, h - 6f * d);
            if (dia < 24f * d) dia = Math.min(w, h) * 0.7f;
            float cx = w / 2f;
            float cy = h / 2f;
            // styl iOS 18 Control Center: aktywne = bialy kafelek + kolorowy glyph,
            // nieaktywne = polprzezroczyste tlo + bialy glyph
            int fill = def.kind == PanelTool.K_APP ? 0x00000000
                    : on ? PanelTool.onColor(def)
                    : (night ? 0x33FFFFFF : 0xFFE4E4EA);
            int ink = def.kind == PanelTool.K_APP ? 0xFFFFFFFF
                    : (on ? 0xFFFFFFFF : (night ? 0xFFFFFFFF : 0xFF3C3C43));
            // iOS 18: kafelek to squircle (zaokraglony prostokat), nie kolo
            float rad = dia * 0.32f;
            android.graphics.RectF body = new android.graphics.RectF(
                    cx - dia / 2f, cy - dia / 2f, cx + dia / 2f, cy + dia / 2f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(fill);
            cv.drawRoundRect(body, rad, rad, p);
            // iOS 26 Liquid Glass: szklana krawedz (rim) dookola kafelka
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * d);
            p.setColor(night ? 0x40FFFFFF : 0x80FFFFFF);
            cv.drawRoundRect(body, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
            if (def.kind == PanelTool.K_APP) {
                android.graphics.Bitmap ic = null;
                try {
                    ic = Icons.forPackage(getContext(), def.action);
                } catch (Throwable ignored) {
                }
                if (ic != null) {
                    android.graphics.RectF dst = new android.graphics.RectF(cx - dia / 2f, cy - dia / 2f,
                            cx + dia / 2f, cy + dia / 2f);
                    cv.drawBitmap(ic, null, dst, bmp);
                } else {
                    PanelTool.glyph(cv, "apps", cx, cy, dia / 2f, 0xFFFFFFFF);
                }
            } else {
                PanelTool.glyph(cv, def.glyph, cx, cy, dia / 2f, ink);
            }

        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return super.onTouchEvent(e);
        }
    }
}


