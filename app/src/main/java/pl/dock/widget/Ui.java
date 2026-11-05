package pl.dock.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

// Wspólne elementy interfejsu w stylu CarPlay / Apple
public class Ui {

    // Okna ustawień i podręczne zaczynają się obok docka, więc dock zostaje widoczny i klikalny
    public static final int DOCK_OFFSET_DP = 77;

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static boolean dockLeft(Context c) {
        return !"right".equals(Prefs.dockSide(c));
    }

    public static int windowWidth(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return dm.widthPixels - dp(c, DOCK_OFFSET_DP);
    }

    public static void dockWindow(Window w, Context c) {
        try {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(windowWidth(c), ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity((dockLeft(c) ? Gravity.END : Gravity.START) | Gravity.TOP);
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        } catch (Throwable ignored) {
        }
    }

    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    // tło ekranu ustawień – Apple-like soft wallpaper gradient with glass feel
    public static GradientDrawable pageBg(Context c, boolean night) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                night ? new int[]{0xFF0B0B12, 0xFF1A1A2E, 0xFF12121A, 0xFF000000}
                      : new int[]{0xFFE8EEF8, 0xFFF5F7FB, 0xFFE2E8F0, 0xFFDDE4F0});
        float r = dp(c, 26);
        g.setCornerRadii(dockLeft(c) ? new float[]{r, r, 0, 0, 0, 0, r, r}
                : new float[]{0, 0, r, r, r, r, 0, 0});
        return g;
    }

    // karta z listą (jak w ustawieniach CarPlay / Apple)
    public static GradientDrawable cardBg(Context c, boolean night) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                night ? new int[]{0xE0282830, 0xE01E1E24}
                        : new int[]{0xF0FFFFFF, 0xF0F7F8FC});
        g.setCornerRadius(dp(c, 16));
        return g;
    }

    // okno podręczne (jak wyskakujące menu Apple)
    public static GradientDrawable popupBg(Context c, boolean night) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(night ? 0xF20E0E0F : 0xF2F5F5F7);
        g.setCornerRadius(dp(c, 34));
        g.setStroke(Math.max(1, dp(c, 1)), night ? 0x33FFFFFF : 0x22000000);
        return g;
    }

    // karta w kolorze docka (noc: #131418) z ramką jaśniejszą od lewego górnego rogu
    public static void ringCard(Canvas cv, RectF r, float radius, float stroke, boolean night,
                                boolean ring) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(night ? 0xFF131418 : 0xFFF2F2F5);
        // Use squircle for more iOS-like corners when radius is large relative to size
        float side = Math.min(r.width(), r.height());
        if (radius > side * 0.18f) {
            Path sq = squirclePath(r, Math.min(radius / side, 0.45f));
            cv.drawPath(sq, p);
        } else {
            cv.drawRoundRect(r, radius, radius, p);
        }
        if (ring) ringStroke(cv, r, radius, stroke, night);
    }

    /**
     * Sama obwodka karty (bez wypelnienia).
     *
     * Dla widzetow, ktore nie moga zostac zamalowane kolorem karty, bo maja pod
     * obwodka "zywa" tresc - np. kafelek mapy, gdzie pod obwodka jest MapView
     * z mapa. Kolory i geometria sa te same, co w {@link #ringCard}.
     */
    public static void ringStroke(Canvas cv, RectF r, float radius, float stroke, boolean night) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                night ? new int[]{0xCCFFFFFF, 0x4DFFFFFF, 0x14FFFFFF, 0x33FFFFFF}
                        : new int[]{0x99FFFFFF, 0x55808090, 0x22808090, 0x40808090},
                new float[]{0f, 0.3f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        RectF in = new RectF(r.left + stroke / 2, r.top + stroke / 2, r.right - stroke / 2,
                r.bottom - stroke / 2);
        float side = Math.min(r.width(), r.height());
        if (radius > side * 0.18f) {
            Path sqIn = squirclePath(in, Math.min(radius / side, 0.45f));
            cv.drawPath(sqIn, p);
        } else {
            cv.drawRoundRect(in, radius - stroke / 2, radius - stroke / 2, p);
        }
        p.setShader(null);
    }

    /**
     * Approximate iOS squircle (superellipse) using cubic Bezier control points.
     * @param curvature 0.2–0.45 (higher = closer to circle)
     */
    public static Path squirclePath(RectF r, float curvature) {
        Path path = new Path();
        float left = r.left, top = r.top, right = r.right, bottom = r.bottom;
        float w = right - left, h = bottom - top;
        // Control offset proportional to curvature (classic Apple-like ~0.45 for smooth corners)
        float k = 0.5522847498f * (0.5f + curvature); // blend circle factor with extra roundness
        float ox = w * k * 0.5f;
        float oy = h * k * 0.5f;
        // Start at top-middle
        path.moveTo(left + w * 0.5f, top);
        // Top-right corner
        path.cubicTo(right - ox, top, right, top + oy, right, top + h * 0.5f);
        // Bottom-right
        path.cubicTo(right, bottom - oy, right - ox, bottom, left + w * 0.5f, bottom);
        // Bottom-left
        path.cubicTo(left + ox, bottom, left, bottom - oy, left, top + h * 0.5f);
        // Top-left
        path.cubicTo(left, top + oy, left + ox, top, left + w * 0.5f, top);
        path.close();
        return path;
    }

    public static void drawSquircle(Canvas cv, RectF r, Paint p, float curvature) {
        cv.drawPath(squirclePath(r, curvature), p);
    }

    // ------------------------------------------------------------ strzałka / znacznik
    public static class Chev extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int dir;   // 0 >, 1 ^, 2 v, 3 <, 4 ✓

        public Chev(Context c, int dir, int color) {
            super(c);
            this.dir = dir;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(dp(c, 2.4f));
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            float s = p.getStrokeWidth() / 2f + 1f;
            Path path = new Path();
            if (dir == 0) {
                path.moveTo(s, s);
                path.lineTo(w - s, h / 2f);
                path.lineTo(s, h - s);
            } else if (dir == 1) {
                path.moveTo(s, h - s);
                path.lineTo(w / 2f, s);
                path.lineTo(w - s, h - s);
            } else if (dir == 2) {
                path.moveTo(s, s);
                path.lineTo(w / 2f, h - s);
                path.lineTo(w - s, s);
            } else if (dir == 3) {
                path.moveTo(w - s, s);
                path.lineTo(s, h / 2f);
                path.lineTo(w - s, h - s);
            } else {
                path.moveTo(s, h * 0.55f);
                path.lineTo(w * 0.38f, h - s);
                path.lineTo(w - s, s);
            }
            cv.drawPath(path, p);
        }
    }

    // ------------------------------------------------------------ przełącznik jak w iOS
    public static class IosSwitch extends View {
        public interface Listener {
            void onChanged(boolean on);
        }

        private boolean on;
        private final int offColor;
        private final Listener listener;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public IosSwitch(Context c, boolean on, int offColor, Listener l) {
            super(c);
            this.on = on;
            this.offColor = offColor;
            this.listener = l;
            setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    IosSwitch.this.on = !IosSwitch.this.on;
                    invalidate();
                    if (listener != null) listener.onChanged(IosSwitch.this.on);
                }
            });
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? 0xFF30D158 : offColor);
            cv.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, p);
            float m = h * 0.08f;
            float r = h / 2f - m;
            float cx = on ? w - h / 2f : h / 2f;
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, h / 2f, r, p);
        }
    }
}







