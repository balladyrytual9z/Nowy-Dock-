
package pl.dock.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * iOS App Library folder card: glass squircle with up to 4 mini icons + category label.
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
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float d;
    private boolean editing;
    private final List<ImageView> iconViews = new ArrayList<ImageView>();
    private final List<View> badgeViews = new ArrayList<View>();

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
        int limit = Math.min(apps.size(), 4);
        int iconSize = (int) (38 * d);
        int pad = (int) (14 * d);
        int gap = (int) (8 * d);

        for (int i = 0; i < limit; i++) {
            final AppEntry app = apps.get(i);
            ImageView iv = new ImageView(getContext());
            Bitmap bmp = Icons.forEntry(getContext(), app);
            iv.setImageBitmap(bmp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            // soft squircle via outline is limited on API 26; round via padding ok

            LayoutParams lp = new LayoutParams(iconSize, iconSize);
            lp.leftMargin = pad + (i % 2) * (iconSize + gap);
            lp.topMargin = pad + (i / 2) * (iconSize + gap);
            addView(iv, lp);
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

            // remove badge (hidden until edit)
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
            LayoutParams blp = new LayoutParams((int) (20 * d), (int) (20 * d));
            blp.leftMargin = lp.leftMargin + iconSize - (int) (10 * d);
            blp.topMargin = lp.topMargin - (int) (6 * d);
            addView(badge, blp);
            badgeViews.add(badge);
            final AppEntry appRef = app;
            badge.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    if (listener != null) listener.onRemoveApp(appRef);
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
            if (i < badgeViews.size()) {
                badgeViews.get(i).setVisibility(on ? VISIBLE : GONE);
            }
        }
        invalidate();
    }

    public String getCategoryName() {
        return categoryName;
    }

    public List<AppEntry> getApps() {
        return apps;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth();
        float h = getHeight();
        float rad = 22f * d;

        // iOS-style glass fill with Bezier squircle
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(night ? 0xCC1C1C1E : 0xCCF2F2F7);
        rect.set(0, 0, w, h);
        Ui.drawSquircle(cv, rect, bgPaint, 0.42f);

        // soft top highlight (glassmorphism)
        bgPaint.setColor(night ? 0x28FFFFFF : 0x40FFFFFF);
        rect.set(1, 1, w - 1, h * 0.38f);
        Ui.drawSquircle(cv, rect, bgPaint, 0.42f);

        // subtle white border
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.0f * d);
        strokePaint.setColor(0x33FFFFFF);
        rect.set(0.5f * d, 0.5f * d, w - 0.5f * d, h - 0.5f * d);
        Ui.drawSquircle(cv, rect, strokePaint, 0.42f);

        // Label is now drawn outside by AppLibraryPage (under the tile)
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = (int) (w * 0.88f);
        setMeasuredDimension(w, h);
        // measure children
        int iconSize = (int) (38 * d);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ImageView) {
                child.measure(
                        MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY));
            } else {
                int bs = (int) (20 * d);
                child.measure(
                        MeasureSpec.makeMeasureSpec(bs, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(bs, MeasureSpec.EXACTLY));
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int iconSize = (int) (38 * d);
        int pad = (int) (14 * d);
        int gap = (int) (8 * d);
        int iconIdx = 0;
        int badgeIdx = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ImageView) {
                int col = iconIdx % 2;
                int row = iconIdx / 2;
                int left = pad + col * (iconSize + gap);
                int top = pad + row * (iconSize + gap);
                child.layout(left, top, left + iconSize, top + iconSize);
                iconIdx++;
            } else {
                // badge
                int bi = badgeIdx;
                int col = bi % 2;
                int row = bi / 2;
                int left = pad + col * (iconSize + gap) + iconSize - (int) (10 * d);
                int top = pad + row * (iconSize + gap) - (int) (6 * d);
                int bs = (int) (20 * d);
                child.layout(left, top, left + bs, top + bs);
                badgeIdx++;
            }
        }
    }
}


