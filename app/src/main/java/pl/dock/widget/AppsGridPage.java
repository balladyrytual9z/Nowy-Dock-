package pl.dock.widget;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

// Jedna strona siatki aplikacji (5 x 3) w układzie jak w CarPlay + iOS edit mode / wobble
public class AppsGridPage extends View implements EditModeController.Listener {

    public static final int COLS = 5;
    public static final int ROWS = 3;
    public static final int PER_PAGE = COLS * ROWS;
    public static final int REQ_UNINSTALL = 1001;

    public static class Item {
        public String label = "";
        public String pkg = "";
        public AppEntry entry;       // aplikacja z listy Docka (ikona wg ustawień)
        public boolean settings;     // skrót do ustawień Docka
        public String builtinId = "";   // wbudowana ikona (CarPlay, Telefon, Ustawienia)
        public String action = "";      // carplay | phone
        String shown = "";
        float textW;
    }

    public interface Listener {
        void onTap(Item it);

        void onLong(Item it);

        void onAppsChanged();

        // przeciagniecie ikony do krawedzi strony: przenies na sasiednia strone
        void onEdgeDrag(Item it, int direction);
    }

    private final float d;
    private final List<Item> items = new ArrayList<Item>();
    private Listener listener;
    private boolean night = true;
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clipPath = new Path();
    private float sc = 1f, iconSz, colPitch, rowPitch, left, top, pillH, labelMax;
    private int pressed = -1;
    private float downX, downY;
    private boolean longFired;
    private final Handler h = new Handler();
    private final int slop;
    // dlugie przytrzymanie PUSTEGO tla = tryb edycji; anulowane po zdjeciu palca,
    // zeby byle dotkniecie tla (jak teraz) nie wlaczalo trzesienia po 0,5 s
    private final Runnable bgLongPress = new Runnable() {
        @Override
        public void run() {
            if (!editMode && pressed < 0) enterEditMode();
        }
    };

    // Edit mode / wobble / drag-reorder
    private boolean editMode = false;
    private final float[] wobblePhase = new float[PER_PAGE];
    private int dragIndex = -1;
    private float dragX, dragY;
    private boolean dragActive = false;
    private long lastEdge = 0;   // debounce zmiany strony przy dragowaniu do krawedzi
    private final Runnable wobbleTick = new Runnable() {
        @Override
        public void run() {
            if (editMode) {
                for (int i = 0; i < wobblePhase.length; i++) {
                    wobblePhase[i] += 0.18f;
                }
                invalidate();
                h.postDelayed(this, 16);
            }
        }
    };

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (pressed >= 0 && pressed < items.size()) {
                longFired = true;
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                enterEditMode();
                // still notify listener so context menu can appear if desired
                if (listener != null) {
                    listener.onLong(items.get(pressed));
                }
                pressed = -1;
                invalidate();
            }
        }
    };

    public AppsGridPage(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        text.setTextAlign(Paint.Align.CENTER);
        // random initial phases
        for (int i = 0; i < wobblePhase.length; i++) {
            wobblePhase[i] = (float) (Math.random() * Math.PI * 2);
        }
        EditModeController.get().addListener(this);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setItems(List<Item> list, boolean night) {
        items.clear();
        items.addAll(list);
        this.night = night;
        measureLabels();
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

    public void enterEditMode() {
        if (editMode) return;
        editMode = true;
        EditModeController.get().setEditing(true);
        // sprzetowa warstwa na czas drzenia: ostre, gladkie krawedzie ikon (jak w iOS)
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        h.post(wobbleTick);
        invalidate();
    }

    public void exitEditMode() {
        if (!editMode) return;
        editMode = false;
        h.removeCallbacks(wobbleTick);
        setLayerType(View.LAYER_TYPE_NONE, null);
        invalidate();
    }

    public boolean isEditMode() {
        return editMode;
    }

    @Override
    protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        // Full dock inset – grid must not sit under the dock
        float dockInset = Ui.dp(getContext(), Ui.DOCK_OFFSET_DP);
        boolean dockLeft = Ui.dockLeft(getContext());
        float usableW = Math.max(1f, w - dockInset);
        sc = Math.min(1f, Math.min(usableW / (773f * d), hh / (520f * d)));
        iconSz = 85f * d * sc;
        colPitch = 162f * d * sc;
        rowPitch = 160f * d * sc;
        float gridW = 4 * colPitch + iconSz;
        left = (dockLeft ? dockInset : 0) + (usableW - gridW) / 2f;
        top = 34f * d * sc;
        pillH = 29f * d * sc;
        labelMax = colPitch - 14f * d * sc;
        text.setTextSize(18f * getResources().getDisplayMetrics().scaledDensity * sc);
        measureLabels();
    }

    private void measureLabels() {
        if (labelMax <= 0) return;
        for (Item it : items) {
            it.shown = TextUtils.ellipsize(it.label, text, labelMax - 20f * d * sc,
                    TextUtils.TruncateAt.END).toString();
            it.textW = text.measureText(it.shown);
        }
    }

    private Bitmap bitmapFor(Item it) {
        Context c = getContext();
        if (it.builtinId.length() > 0) return Icons.builtin(c, it.builtinId);
        if (it.settings) return Icons.builtin(c, "settings");
        if (it.entry != null) return Icons.forEntry(c, it.entry);
        return Icons.forPackage(c, it.pkg);
    }

    private void drawRemoveBadge(Canvas cv, float x, float y) {
        float r = 10f * d * sc;
        float cx = x + r;
        float cy = y + r;
        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(0xFFFF3B30); // iOS red
        cv.drawCircle(cx, cy, r, badgePaint);
        badgePaint.setColor(0xFFFFFFFF);
        badgePaint.setStyle(Paint.Style.STROKE);
        badgePaint.setStrokeWidth(2.2f * d * sc);
        float arm = r * 0.45f;
        cv.drawLine(cx - arm, cy, cx + arm, cy, badgePaint);
        cv.drawLine(cx, cy - arm, cx, cy + arm, badgePaint);
    }

    private int hitRemoveBadge(float x, float y) {
        float badgeR = 14f * d * sc; // hit area slightly larger
        for (int i = 0; i < items.size(); i++) {
            int row = i / COLS;
            int col = i % COLS;
            float ix = left + col * colPitch;
            float iy = top + row * rowPitch;
            float cx = ix + 10f * d * sc;
            float cy = iy + 10f * d * sc;
            float dx = x - cx;
            float dy = y - cy;
            if (dx * dx + dy * dy <= badgeR * badgeR) return i;
        }
        return -1;
    }

    // ikona przycieta do squircle z pelnym antyaliasingiem (BitmapShader);
    // clipPath nie wygladza krawedzi na Androidzie 9, ikony mialy poszarpane brzegi
    private void drawRoundedIcon(Canvas cv, Bitmap b, RectF rr) {
        if (b == null || b.getWidth() <= 0 || b.getHeight() <= 0) return;
        float rad = rr.width() * 0.225f;
        android.graphics.BitmapShader sh = new android.graphics.BitmapShader(b,
                android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(rr.width() / b.getWidth(), rr.height() / b.getHeight());
        sh.setLocalMatrix(m);
        bmp.setShader(sh);
        cv.drawRoundRect(rr, rad, rad, bmp);
        bmp.setShader(null);
    }

    @Override
    protected void onDraw(Canvas cv) {
        pill.setColor(night ? 0xB31E1E20 : 0xCCFFFFFF);
        text.setColor(night ? 0xFFFFFFFF : 0xFF111114);
        for (int i = 0; i < items.size(); i++) {
            if (dragActive && i == dragIndex) continue; // drawn floating later
            Item it = items.get(i);
            int row = i / COLS;
            int col = i % COLS;
            float x = left + col * colPitch;
            float y = top + row * rowPitch;
            Bitmap b = bitmapFor(it);
            float s = (pressed == i && !dragActive) ? 0.90f : 1f;
            float inset = iconSz * (1f - s) / 2f;

            if (editMode && !dragActive) {
                float angle = (float) Math.sin(wobblePhase[i]) * 1.6f;
                cv.save();
                cv.rotate(angle, x + iconSz / 2f, y + iconSz / 2f);
            }

            bmp.setAlpha(pressed == i && !dragActive ? 190 : 255);
            r.set(x + inset, y + inset, x + iconSz - inset, y + iconSz - inset);
            // squircle z pelnym antyaliasingiem (BitmapShader, nie clipPath)
            drawRoundedIcon(cv, b, r);

            float pw = it.textW + 24f * d * sc;
            float px = x + iconSz / 2f - pw / 2f;
            float py = y + iconSz + 7f * d * sc;
            r.set(px, py, px + pw, py + pillH);
            cv.drawRoundRect(r, pillH / 2f, pillH / 2f, pill);
            cv.drawText(it.shown, x + iconSz / 2f,
                    py + pillH / 2f - (text.descent() + text.ascent()) / 2f, text);

            if (editMode && !dragActive) {
                cv.restore();
                if (!it.settings && it.action.length() == 0 && it.pkg != null && it.pkg.length() > 0) {
                    drawRemoveBadge(cv, x, y);
                }
            } else if (!editMode && it.pkg != null && it.pkg.length() > 0) {
                int count = DockNotificationListener.notificationCount(it.pkg);
                if (count > 0) {
                    drawNotifBadge(cv, x + iconSz, y, count);
                }
            }
        }
        // Floating dragged icon
        if (dragActive && dragIndex >= 0 && dragIndex < items.size()) {
            Item it = items.get(dragIndex);
            Bitmap b = bitmapFor(it);
            float x = dragX - iconSz / 2f;
            float y = dragY - iconSz / 2f;
            bmp.setAlpha(230);
            r.set(x, y, x + iconSz, y + iconSz);
            drawRoundedIcon(cv, b, r);
            // highlight target slot
            int target = hit(dragX, dragY);
            if (target >= 0 && target != dragIndex) {
                int row = target / COLS;
                int col = target % COLS;
                float tx = left + col * colPitch;
                float ty = top + row * rowPitch;
                badgePaint.setStyle(Paint.Style.STROKE);
                badgePaint.setStrokeWidth(2f * d * sc);
                badgePaint.setColor(0xFF0A84FF);
                r.set(tx - 4, ty - 4, tx + iconSz + 4, ty + iconSz + 4);
                cv.drawRoundRect(r, 12f * d * sc, 12f * d * sc, badgePaint);
            }
        }
    }

    private void drawNotifBadge(Canvas cv, float right, float top, int count) {
        float r = 11f * d * sc;
        float cx = right - r * 0.3f;
        float cy = top + r * 0.3f;
        // red pill / circle
        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(0xFFFF3B30);
        String t = count > 99 ? "99+" : String.valueOf(count);
        badgePaint.setTextSize(11f * d * sc);
        badgePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        badgePaint.setTextAlign(Paint.Align.CENTER);
        float tw = badgePaint.measureText(t);
        float bw = Math.max(r * 2f, tw + 10f * d * sc);
        float left = cx - bw / 2f;
        RectF br = new RectF(left, cy - r, left + bw, cy + r);
        cv.drawRoundRect(br, r, r, badgePaint);
        // white number
        badgePaint.setColor(0xFFFFFFFF);
        float ty = cy - (badgePaint.descent() + badgePaint.ascent()) / 2f;
        cv.drawText(t, cx, ty, badgePaint);
    }

    private int hit(float x, float y) {
        for (int i = 0; i < items.size(); i++) {
            int row = i / COLS;
            int col = i % COLS;
            float cx = left + col * colPitch - 10f * d * sc;
            float cy = top + row * rowPitch - 6f * d * sc;
            float cw = iconSz + 20f * d * sc;
            float ch = iconSz + 7f * d * sc + pillH + 12f * d * sc;
            if (x >= cx && x <= cx + cw && y >= cy && y <= cy + ch) return i;
        }
        return -1;
    }

    // Menu kontekstowe w stylu iOS: ciemne szklo, zaokraglenia,
    // opcja destrukcyjna ("Usun aplikacje") w kolorze czerwonym #FF3B30.
    private void showRemoveDialog(final int index) {
        if (index < 0 || index >= items.size()) return;
        final Item it = items.get(index);
        if (it.settings || it.action.length() > 0) return;
        Context ctx = getContext();
        final Dialog dlg = new Dialog(ctx);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(night ? 0xD91C1C1E : 0xF6F2F2F7);
        bg.setCornerRadius(20f * d);
        bg.setStroke(Math.max(1, (int) (0.5f * d)), night ? 0x33FFFFFF : 0x1F000000);
        c.setBackground(bg);

        boolean canHide = it.entry != null;
        // tytul aplikacji na gorze menu
        addIosMenuRow(c, dlg, it.label, night ? 0xFF9A9AA0 : 0xFF6C6C70, Typeface.DEFAULT_BOLD, new Runnable() {
            @Override public void run() { }
        }, false);
        int rowInk = night ? 0xFFFFFFFF : 0xFF1C1C1E;
        addIosMenuRow(c, dlg, "Informacje o aplikacji", rowInk, Typeface.DEFAULT, new Runnable() {
            @Override public void run() {
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + it.pkg));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(i);
                } catch (Throwable ignored) { }
            }
        }, true);
        if (canHide) {
            addIosMenuRow(c, dlg, "Usuń z ekranu głównego", rowInk, Typeface.DEFAULT, new Runnable() {
                @Override public void run() {
                    Prefs.remove(getContext(), it.entry.id);
                    if (listener != null) listener.onAppsChanged();
                }
            }, true);
        }
        addIosMenuRow(c, dlg, "Usuń aplikację", 0xFFFF3B30, Typeface.DEFAULT, new Runnable() {
            @Override public void run() { uninstallPackage(it.pkg); }
        }, true);

        dlg.setContentView(c);
        dlg.setCanceledOnTouchOutside(true);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout((int) (280f * d), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dlg.show();
    }

    // jeden wiersz menu: 44dp wysokosci, ikona po lewej nie jest potrzebna w CarPlay
    private void addIosMenuRow(LinearLayout parent, final Dialog dlg, String label, int color,
                                Typeface tf, final Runnable act, boolean closeOnClick) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setTextColor(color);
        tv.setTypeface(tf);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding((int) (16f * d), 0, (int) (16f * d), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) (44f * d));
        parent.addView(tv, lp);
        if (closeOnClick) {
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try { dlg.dismiss(); } catch (Throwable ignored) { }
                    act.run();
                }
            });
        }
        if (parent.getChildCount() > 1) {
            // separator jak w iOS
            android.view.View sep = new android.view.View(getContext());
            sep.setBackgroundColor(night ? 0x33FFFFFF : 0x1F000000);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) (0.5f * d)));
            sp.leftMargin = (int) (16f * d);
            sp.rightMargin = (int) (16f * d);
            parent.addView(sep, parent.getChildCount() - 1, sp);
        }
    }

    private void uninstallPackage(String pkg) {
        if (pkg == null || pkg.length() == 0) return;
        try {
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(Uri.parse("package:" + pkg));
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            Context ctx = getContext();
            if (ctx instanceof Activity) {
                ((Activity) ctx).startActivityForResult(intent, REQ_UNINSTALL);
            } else {
                ctx.startActivity(intent);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Swap/reorder icons in edit mode and persist via Prefs.apps order. */
    private void reorderItems(int from, int to) {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size() || from == to) return;
        Item moved = items.remove(from);
        items.add(to, moved);
        // Persist: rebuild Prefs order from items that have AppEntry
        try {
            Context c = getContext();
            List<AppEntry> current = Prefs.apps(c);
            // Build new order: entries matching our grid items in new sequence first
            java.util.LinkedHashSet<Integer> used = new java.util.LinkedHashSet<Integer>();
            List<AppEntry> ordered = new ArrayList<AppEntry>();
            for (Item it : items) {
                if (it.entry != null) {
                    ordered.add(it.entry);
                    used.add((int) it.entry.id);
                }
            }
            // Append any remaining apps not on this page
            for (AppEntry e : current) {
                if (!used.contains((int) e.id)) ordered.add(e);
            }
            Prefs.saveApps(c, ordered);
            if (listener != null) listener.onAppsChanged();
        } catch (Throwable ignored) {
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                longFired = false;
                if (editMode) {
                    int badge = hitRemoveBadge(downX, downY);
                    if (badge >= 0) {
                        pressed = badge;
                        invalidate();
                        return true;
                    }
                }
                pressed = hit(downX, downY);
                if (pressed >= 0) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                }
                if (pressed < 0) {
                    // dlugie przytrzymanie pustego tla = tryb edycji (jak w Apple)
                    if (!editMode) {
                        h.postDelayed(bgLongPress, ViewConfiguration.getLongPressTimeout());
                    }
                    return true;
                }
                invalidate();
                if (!editMode) {
                    h.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (editMode && pressed >= 0) {
                    // Start / continue drag-reorder
                    if (!dragActive && (Math.abs(e.getX() - downX) > slop
                            || Math.abs(e.getY() - downY) > slop)) {
                        dragActive = true;
                        dragIndex = pressed;
                        h.removeCallbacks(longPress);
                    }
                    if (dragActive) {
                        dragX = e.getX();
                        dragY = e.getY();
                        // edge scrolling: drag do krawedzi przenosi ikone na sasiednia strone
                        long now = System.currentTimeMillis();
                        if (now - lastEdge > 600 && dragIndex >= 0 && dragIndex < items.size()
                                && listener != null) {
                            float edge = 56f * d;
                            int dir = 0;
                            if (dragX < edge) dir = -1;
                            else if (dragX > getWidth() - edge) dir = 1;
                            if (dir != 0) {
                                lastEdge = now;
                                Item dragIt = items.get(dragIndex);
                                dragActive = false;
                                dragIndex = -1;
                                pressed = -1;
                                invalidate();
                                listener.onEdgeDrag(dragIt, dir);
                                return true;
                            }
                        }
                        invalidate();
                    }
                    return true;
                }
                if (pressed >= 0 && (Math.abs(e.getX() - downX) > slop
                        || Math.abs(e.getY() - downY) > slop)) {
                    h.removeCallbacks(longPress);
                    pressed = -1;
                    invalidate();
                }
                h.removeCallbacks(bgLongPress);
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(longPress);
                h.removeCallbacks(bgLongPress);
                if (editMode && dragActive && dragIndex >= 0) {
                    int target = hit(e.getX(), e.getY());
                    if (target >= 0 && target != dragIndex && target < items.size()) {
                        reorderItems(dragIndex, target);
                    }
                    dragActive = false;
                    dragIndex = -1;
                    pressed = -1;
                    invalidate();
                    return true;
                }
                int p = pressed;
                pressed = -1;
                dragActive = false;
                dragIndex = -1;
                invalidate();
                if (editMode) {
                    int badgeIndex = hitRemoveBadge(e.getX(), e.getY());
                    if (badgeIndex >= 0) {
                        showRemoveDialog(badgeIndex);
                        return true;
                    }
                    if (p < 0) {
                        EditModeController.get().setEditing(false);
                    }
                    return true;
                }
                if (p >= 0 && !longFired && p < items.size() && listener != null) {
                    listener.onTap(items.get(p));
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(longPress);
                h.removeCallbacks(bgLongPress);
                pressed = -1;
                dragActive = false;
                dragIndex = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        h.removeCallbacks(longPress);
        h.removeCallbacks(bgLongPress);
        h.removeCallbacks(wobbleTick);
        EditModeController.get().removeListener(this);
    }
}
