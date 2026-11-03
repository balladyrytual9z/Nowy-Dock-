package pl.dock.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

// Strona z widżetami ułożonymi w kolumnach (stosach). W każdej kolumnie widać jeden widżet;
// kolejne można przełączać kropkami po prawej stronie kolumny. + iOS edit / wobble
public class WidgetPage extends ViewGroup implements EditModeController.Listener {

    public interface Callbacks {
        void onAdd(String where);

        void onMenu(WidgetEntry e);

        void onEmptyLong();
    }

    private final float d;
    private final String[] wheres;
    private final boolean cards;
    private Callbacks cb;
    private boolean night = true;
    private final int[] sel;
    private final RectF r = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler h = new Handler();
    private final int slop;
    private float downX, downY;
    private boolean longFired;
    private WidgetEntry pressEntry;
    private boolean editMode = false;
    private final List<View> widgetViews = new ArrayList<View>();
    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            longFired = true;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            if (cb == null) return;
            if (pressEntry != null) {
                cb.onMenu(pressEntry);
            } else {
                enterEditMode();
                cb.onEmptyLong();
            }
        }
    };

    public WidgetPage(Context c, String[] wheres, boolean cards) {
        super(c);
        this.wheres = wheres;
        this.cards = cards;
        this.sel = new int[wheres.length];
        d = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setWillNotDraw(false);
        setClipChildren(false);
        EditModeController.get().addListener(this);
    }

    public void setCallbacks(Callbacks c) {
        cb = c;
    }

    public void setNight(boolean n) {
        night = n;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof NightAware) ((NightAware) v).setNight(n);
        }
        invalidate();
    }

    public void clearWidgets() {
        removeAllViews();
        widgetViews.clear();
        for (int i = 0; i < sel.length; i++) sel[i] = 0;
    }

    public void addWidget(WidgetEntry e, View v) {
        v.setTag(e);
        addView(v);
        widgetViews.add(v);
        if (editMode) {
            startWobble(v);
            // badge handled in draw or overlay if needed
        }
    }

    public void removeWidget(int appWidgetId) {
        for (int i = 0; i < getChildCount(); i++) {
            Object t = getChildAt(i).getTag();
            if (t instanceof WidgetEntry && ((WidgetEntry) t).id == appWidgetId) {
                View child = getChildAt(i);
                stopWobble(child);
                widgetViews.remove(child);
                removeViewAt(i);
                break;
            }
        }
        applySelection();
    }

    public void enterEditMode() {
        if (editMode) return;
        editMode = true;
        EditModeController.get().setEditing(true);
        for (View v : widgetViews) {
            startWobble(v);
        }
        invalidate();
    }

    public void exitEditMode() {
        if (!editMode) return;
        editMode = false;
        for (View v : widgetViews) {
            stopWobble(v);
        }
        invalidate();
    }

    @Override
    public void onEditModeChanged(boolean editing) {
        if (editing) {
            if (!editMode) enterEditMode();
        } else {
            if (editMode) exitEditMode();
        }
    }

    private void startWobble(View v) {
        // trzesienie usuniete
        if (v == null) return;
    }

    private void stopWobble(View v) {
    }

    private int colOf(String where) {
        for (int i = 0; i < wheres.length; i++) if (wheres[i].equals(where)) return i;
        return -1;
    }

    private List<View> inCol(int col) {
        List<View> out = new ArrayList<View>();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            Object t = v.getTag();
            if (t instanceof WidgetEntry && colOf(((WidgetEntry) t).where) == col) out.add(v);
        }
        return out;
    }

    // pokazuje wybrany widżet każdej kolumny i dodaje puste kafelki "+" tam, gdzie nic nie ma
    public void applySelection() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            if (getChildAt(i) instanceof EmptyTile) removeViewAt(i);
        }
        for (int c = 0; c < wheres.length; c++) {
            List<View> l = inCol(c);
            if (sel[c] >= l.size()) sel[c] = 0;
            for (int k = 0; k < l.size(); k++) {
                l.get(k).setVisibility(k == sel[c] ? VISIBLE : GONE);
            }
            if (l.isEmpty() && cards) {
                EmptyTile t = new EmptyTile(getContext(), wheres[c]);
                addView(t);
            }
        }
        requestLayout();
        invalidate();
    }

    public void cycle(String where) {
        int c = colOf(where);
        if (c < 0) return;
        sel[c]++;
        applySelection();
    }

    public int count(String where) {
        int c = colOf(where);
        return c < 0 ? 0 : inCol(c).size();
    }

    // ---------------------------------------------------------------- układ
    private float padL() {
        return 16f * d;
    }

    // kwadratowe widżety jak w CarPlay, wyśrodkowane w pionie – z insetem na dock
    private RectF colRect(int c, int w, int hh) {
        int n = wheres.length;
        float dockInset = Ui.dp(getContext(), Ui.DOCK_OFFSET_DP);
        boolean dockLeft = Ui.dockLeft(getContext());
        float padL = 20f * d + (dockLeft ? dockInset : 0);
        float padR = 20f * d + (dockLeft ? 0 : dockInset);
        float gap = 34f * d;
        float usable = Math.max(1f, w - padL - padR - (n - 1) * gap);
        float cw = usable / n;
        float side = Math.min(cw, hh * 0.62f);
        float x = padL + c * (cw + gap) + (cw - side) / 2f;
        float y = (hh - side) / 2f - 8f * d;
        r.set(x, y, x + side, y + side);
        return r;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int rr, int b) {
        int w = rr - l;
        int hh = b - t;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int col = -1;
            if (v instanceof EmptyTile) {
                col = colOf(((EmptyTile) v).where);
            } else if (v.getTag() instanceof WidgetEntry) {
                col = colOf(((WidgetEntry) v.getTag()).where);
            }
            if (col < 0 || v.getVisibility() == GONE) continue;
            RectF c = colRect(col, w, hh);
            int cw = (int) c.width();
            int ch = (int) c.height();
            v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
            v.layout((int) c.left, (int) c.top, (int) c.left + cw, (int) c.top + ch);
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
    }

    @Override
    protected void dispatchDraw(Canvas cv) {
        int w = getWidth();
        int hh = getHeight();
        super.dispatchDraw(cv);
        // kropki przełączania widżetów w kolumnie
        for (int c = 0; c < wheres.length; c++) {
            int n = inCol(c).size();
            if (n < 2) continue;
            RectF rc = colRect(c, w, hh);
            float x = rc.right + 14f * d;
            float y0 = rc.centerY() - (n - 1) * 8f * d;
            for (int k = 0; k < n; k++) {
                paint.setColor(k == sel[c] ? 0xFFFFFFFF : 0x66FFFFFF);
                cv.drawCircle(x, y0 + k * 16f * d, 3.6f * d, paint);
            }
        }
    }

    // ---------------------------------------------------------------- dotyk
    private WidgetEntry entryAt(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View v = getChildAt(i);
            if (v.getVisibility() != VISIBLE || !(v.getTag() instanceof WidgetEntry)) continue;
            if (x >= v.getLeft() && x <= v.getRight() && y >= v.getTop() && y <= v.getBottom()) {
                return (WidgetEntry) v.getTag();
            }
        }
        return null;
    }

    private String dotsAt(float x, float y) {
        int w = getWidth();
        int hh = getHeight();
        for (int c = 0; c < wheres.length; c++) {
            if (inCol(c).size() < 2) continue;
            RectF rc = colRect(c, w, hh);
            if (x >= rc.right + 2f * d && x <= rc.right + 28f * d && y >= rc.top && y <= rc.bottom) {
                return wheres[c];
            }
        }
        return null;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                longFired = false;
                pressEntry = entryAt(downX, downY);
                h.postDelayed(longPress, ViewConfiguration.getLongPressTimeout() + 150);
                if (dotsAt(downX, downY) != null) return true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop) {
                    h.removeCallbacks(longPress);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(longPress);
                break;
            default:
                break;
        }
        return longFired;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!cards && pressEntry == null) return true;
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(longPress);
                if (!longFired) {
                    String w = dotsAt(e.getX(), e.getY());
                    if (w != null) cycle(w);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(longPress);
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // po odbudowie pagera strona wraca do drzewa widokow: odzyskaj rejestracje
        // w globalnym trybie edycji, zeby strony widzetow i aplikacji wskazywaly ten sam stan
        EditModeController.get().addListener(this);
        if (EditModeController.get().isEditing() && !editMode) enterEditMode();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        h.removeCallbacks(longPress);
        exitEditMode();
        EditModeController.get().removeListener(this);
    }

    // ---------------------------------------------------------------- pusty kafelek "+"
    private class EmptyTile extends View {
        final String where;

        EmptyTile(Context c, String where) {
            super(c);
            this.where = where;
            setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (cb != null) cb.onAdd(EmptyTile.this.where);
                }
            });
            setContentDescription("Dodaj widżet");
        }

        @Override
        protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(night ? 0x88FFFFFF : 0x88000000);
            cv.drawLine(cx - 14 * d, cy - 20 * d, cx + 14 * d, cy - 20 * d, p);
            cv.drawLine(cx, cy - 34 * d, cx, cy - 6 * d, p);
            text.setColor(night ? 0xAAFFFFFF : 0xAA000000);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(16f * getResources().getDisplayMetrics().scaledDensity);
            cv.drawText("Dodaj widżet", cx, cy + 22 * d, text);
        }
    }
}







