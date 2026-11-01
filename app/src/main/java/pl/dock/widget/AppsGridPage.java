package pl.dock.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
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
    private float sc = 1f, iconSz, colPitch, rowPitch, left, top, pillH, labelMax;
    private int pressed = -1;
    private float downX, downY;
    private boolean longFired;
    private final Handler h = new Handler();
    private final int slop;

    // Edit mode / wobble / drag-reorder
    private boolean editMode = false;
    private final float[] wobblePhase = new float[PER_PAGE];
    private int dragIndex = -1;
    private float dragX, dragY;
    private boolean dragActive = false;
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
        h.post(wobbleTick);
        invalidate();
    }

    public void exitEditMode() {
        if (!editMode) return;
        editMode = false;
        h.removeCallbacks(wobbleTick);
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
            float s = (pressed == i && !dragActive) ? 0.93f : 1f;
            float inset = iconSz * (1f - s) / 2f;

            if (editMode && !dragActive) {
                float angle = (float) Math.sin(wobblePhase[i]) * 1.6f;
                cv.save();
                cv.rotate(angle, x + iconSz / 2f, y + iconSz / 2f);
            }

            bmp.setAlpha(pressed == i && !dragActive ? 190 : 255);
            r.set(x + inset, y + inset, x + iconSz - inset, y + iconSz - inset);
            cv.drawBitmap(b, null, r, bmp);

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
            cv.drawBitmap(b, null, r, bmp);
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

    private void showRemoveDialog(final int index) {
        if (index < 0 || index >= items.size()) return;
        final Item it = items.get(index);
        if (it.settings || it.action.length() > 0) return;
        final String[] options = {"Usuń z ekranu głównego", "Odinstaluj aplikację"};
        new AlertDialog.Builder(getContext())
                .setTitle(it.label)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        // remove from Dock home list
                        if (it.entry != null) {
                            Prefs.remove(getContext(), it.entry.id);
                        } else if (it.pkg != null && it.pkg.length() > 0) {
                            // fallback: remove by package if no entry id
                            List<AppEntry> list = Prefs.apps(getContext());
                            for (AppEntry e : list) {
                                if (it.pkg.equals(e.pkg)) {
                                    Prefs.remove(getContext(), e.id);
                                    break;
                                }
                            }
                        }
                        if (listener != null) listener.onAppsChanged();
                    } else {
                        uninstallPackage(it.pkg);
                    }
                }).show();
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
                if (pressed < 0) {
                    // long press empty space also enters edit
                    if (!editMode) {
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                enterEditMode();
                            }
                        }, ViewConfiguration.getLongPressTimeout());
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
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(longPress);
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
        h.removeCallbacks(wobbleTick);
        EditModeController.get().removeListener(this);
    }
}
