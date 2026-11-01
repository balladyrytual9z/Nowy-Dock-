package pl.dock.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * iOS App Library folder (liquid glass): 3 duze ikony + mini-klaster 2x2
 * w prawym dolnym rogu; nazwe kategorii rysuje AppLibraryPage pod plyta.
 */
public class LibraryFolderView extends FrameLayout {

    public interface Listener {
        void onFolderTap(String category);
        void onAppTap(AppEntry app);
        void onAppLongPress(View iconView, AppEntry app);
        void onRemoveApp(AppEntry app);
    }

    private final String categoryName;
    private final List<AppEntry> apps;
    private final boolean night;
    private final Listener listener;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float d;
    private boolean editing;
    private final List<ImageView> iconViews = new ArrayList<ImageView>();
    private final List<View> badgeViews = new ArrayList<View>();
    private int bigCount = 0;
    private int miniCount = 0;

    public LibraryFolderView(Context c, String category, List<AppEntry> apps,
                             boolean night, Listener listener) {
        super(c);
        this.categoryName = category;
        this.apps = apps != null ? apps : new ArrayList<AppEntry>();
        this.night = night;
        this.listener = listener;
        this.d = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(true);
        buildIcons();
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (editing) return;
                if (LibraryFolderView.this.listener != null) {
                    LibraryFolderView.this.listener.onFolderTap(categoryName);
                }
            }
        });
    }

    private void buildIcons() {
        removeAllViews();
        iconViews.clear();
        badgeViews.clear();
        bigCount = Math.min(apps.size(), 3);
        miniCount = Math.min(4, Math.max(0, apps.size() - bigCount));

        for (int i = 0; i < bigCount; i++) {
            final AppEntry app = apps.get(i);
            ImageView iv = new ImageView(getContext());
            iv.setImageBitmap(Icons.forEntry(getContext(), app));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(iv);
            iconViews.add(iv);
            iv.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    if (listener != null) listener.onAppTap(app);
                }
            });
            iv.setOnLongClickListener(new OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    if (listener != null) listener.onAppLongPress(v, app);
                    return true;
                }
            });
            // plakietka usuwania (widoczna tylko w trybie edycji)
            View badge = new View(getContext()) {
                private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas c) {
                    float s = getWidth();
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFF3B30);
                    c.drawCircle(s / 2f, s / 2f, s / 2f, p);
                    p.setColor(0xFFFFFFFF);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2f * d);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    float m = s * 0.28f;
                    c.drawLine(m, m, s - m, s - m, p);
                    c.drawLine(m, s - m, s - m, m, p);
                }
            };
            badge.setVisibility(GONE);
            addView(badge);
            badgeViews.add(badge);
            final AppEntry appRef = app;
            badge.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    if (listener != null) listener.onRemoveApp(appRef);
                }
            });
        }
        for (int i = 0; i < miniCount; i++) {
            final AppEntry app = apps.get(bigCount + i);
            ImageView iv = new ImageView(getContext());
            iv.setImageBitmap(Icons.forEntry(getContext(), app));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(iv);
            iconViews.add(iv);
            iv.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    if (editing) return;
                    // iOS: dotkniecie mini-klasterka rozwija cala kategorie
                    if (listener != null) listener.onFolderTap(categoryName);
                }
            });
        }
    }

    public void setEditing(boolean on) {
        editing = on;
        for (int i = 0; i < iconViews.size(); i++) {
            View iv = iconViews.get(i);
            if (on) {
                EditModeController.get().startWobble(iv, i * 0.7f);
            } else {
                EditModeController.get().stopWobble(iv);
            }
        }
        for (View badge : badgeViews) {
            badge.setVisibility(on ? VISIBLE : GONE);
        }
        invalidate();
    }

    public String getCategoryName() {
        return categoryName;
    }

    public List<AppEntry> getApps() {
        return apps;
    }

    // geometria duzych ikon (trojkat: dwie gorne + jedna dolna po lewej)
    private int[] bigPos(int w, int idx) {
        float p = w * 0.10f;
        int bigSz = (int) (w * 0.30f);
        int bigGap = (int) (w * 0.04f);
        int x, y;
        if (idx == 0) { x = (int) p; y = (int) p; }
        else if (idx == 1) { x = (int) (w - p - bigSz); y = (int) p; }
        else { x = (int) p; y = (int) (p + bigSz + bigGap); }
        return new int[]{x, y, bigSz};
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth();
        float h = getHeight();
        float rad = 22f * d;

        // iOS-style glass fill with Bezier squircle
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(night ? 0xCC1C1C1E : 0xE6F2F2F7);
        rect.set(0, 0, w, h);
        Ui.drawSquircle(cv, rect, bgPaint, 0.42f);

        // soft top highlight (glassmorphism)
        bgPaint.setColor(night ? 0x28FFFFFF : 0x0A000000);
        rect.set(1, 1, w - 1, h * 0.38f);
        Ui.drawSquircle(cv, rect, bgPaint, 0.42f);

        // subtle white border
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.0f * d);
        strokePaint.setColor(night ? 0x33FFFFFF : 0x14000000);
        rect.set(0.5f * d, 0.5f * d, w - 0.5f * d, h - 0.5f * d);
        Ui.drawSquircle(cv, rect, strokePaint, 0.42f);

        // szklana pigulka pod mini-klasterem w prawym dolnym rogu
        if (miniCount > 0) {
            float p = w * 0.10f;
            float miniSz = w * 0.13f;
            float miniGap = w * 0.03f;
            float miniPad = w * 0.045f;
            float cl = w - p - miniPad - (2 * miniSz + miniGap);
            float cb = h - p - miniPad - (2 * miniSz + miniGap);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(night ? 0x4DFFFFFF : 0x14000000);
            rect.set(cl, cb, cl + 2 * miniSz + miniGap, cb + 2 * miniSz + miniGap);
            Ui.drawSquircle(cv, rect, bgPaint, 0.42f);
            strokePaint.setStrokeWidth(1.0f * d);
            strokePaint.setColor(night ? 0x33FFFFFF : 0x14000000);
            rect.set(cl + 0.5f * d, cb + 0.5f * d,
                    cl + 2 * miniSz + miniGap - 0.5f * d, cb + 2 * miniSz + miniGap - 0.5f * d);
            Ui.drawSquircle(cv, rect, strokePaint, 0.42f);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = (int) (w * 0.88f);
        setMeasuredDimension(w, h);
        int bigSz = (int) (w * 0.30f);
        int miniSz = (int) (w * 0.13f);
        int badgeSize = (int) (20 * d);
        int iconIdx = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ImageView) {
                int sz = iconIdx < bigCount ? bigSz : miniSz;
                child.measure(
                        MeasureSpec.makeMeasureSpec(sz, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(sz, MeasureSpec.EXACTLY));
                iconIdx++;
            } else {
                child.measure(
                        MeasureSpec.makeMeasureSpec(badgeSize, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(badgeSize, MeasureSpec.EXACTLY));
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        int iconIdx = 0;
        int badgeIdx = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ImageView) {
                if (iconIdx < bigCount) {
                    int[] bp = bigPos(w, iconIdx);
                    child.layout(bp[0], bp[1], bp[0] + bp[2], bp[1] + bp[2]);
                } else {
                    // mini-klaster 2x2 w prawym dolnym rogu
                    int mi = iconIdx - bigCount;
                    int col = mi % 2;
                    int row = mi / 2;
                    float p = w * 0.10f;
                    int miniSz = (int) (w * 0.13f);
                    int miniGap = (int) (w * 0.03f);
                    int miniPad = (int) (w * 0.045f);
                    int bx = (int) (w - p - miniPad - (2 * miniSz + miniGap)) + col * (miniSz + miniGap);
                    int by = (int) (h - p - miniPad - (2 * miniSz + miniGap)) + row * (miniSz + miniGap);
                    child.layout(bx, by, bx + miniSz, by + miniSz);
                }
                iconIdx++;
            } else {
                int[] bp = bigPos(w, badgeIdx);
                int badgeSize = (int) (20 * d);
                child.layout(bp[0] + bp[2] - (int) (10 * d), bp[1] - (int) (6 * d),
                        bp[0] + bp[2] - (int) (10 * d) + badgeSize,
                        bp[1] - (int) (6 * d) + badgeSize);
                badgeIdx++;
            }
        }
    }
}
