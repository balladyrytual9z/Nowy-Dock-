
package pl.dock.widget.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;

/**
 * Central design system entry point for Dock 2.0.
 * All screens should obtain colours, radii and motion from here.
 */
public final class DockTheme {

    public static final float RADIUS_SM = 12f;
    public static final float RADIUS_MD = 18f;
    public static final float RADIUS_LG = 24f;
    public static final float RADIUS_PANEL = 28f;
    public static final float OUTLINE_DP = 1f;
    public static final float MARGIN_BASE = 8f;
    public static final float CARD_GAP = 14f;

    public static final long ANIM_QUICK = 140L;
    public static final long ANIM_STANDARD = 280L;

    private final boolean night;
    private final float density;

    public DockTheme(Context ctx, boolean night) {
        this.night = night;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    public boolean isNight() { return night; }
    public float dp(float v) { return v * density; }

    public int base() { return DockColors.base(night); }
    public int card() { return DockColors.card(night); }
    public int raised() { return DockColors.raised(night); }
    public int accent() { return DockColors.accent(night); }
    public int text() { return DockColors.text(night); }
    public int textSec() { return DockColors.textSecondary(night); }
    public int outline() { return DockColors.outline(night); }

    public float radiusSm() { return dp(RADIUS_SM); }
    public float radiusMd() { return dp(RADIUS_MD); }
    public float radiusLg() { return dp(RADIUS_LG); }
    public float radiusPanel() { return dp(RADIUS_PANEL); }

    public Paint cardPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(card());
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private static Typeface interRegular;
    private static Typeface interMedium;
    private static Typeface interSemiBold;
    private static boolean fontsLoaded;

    /** Call once from Application / first Activity with app Context. */
    public static void loadFonts(Context ctx) {
        if (fontsLoaded) return;
        try {
            interRegular = Typeface.createFromAsset(ctx.getAssets(), "fonts/Inter-Regular.ttf");
            interMedium = Typeface.createFromAsset(ctx.getAssets(), "fonts/Inter-Medium.ttf");
            interSemiBold = Typeface.createFromAsset(ctx.getAssets(), "fonts/Inter-SemiBold.ttf");
            fontsLoaded = true;
        } catch (Throwable t) {
            // fallback silently
            fontsLoaded = false;
        }
    }

    public Typeface mono() {
        return Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);
    }

    public Typeface regular() {
        if (interRegular != null) return interRegular;
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }

    public Typeface medium() {
        if (interMedium != null) return interMedium;
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public Typeface semiBold() {
        if (interSemiBold != null) return interSemiBold;
        return Typeface.create("sans-serif-medium", Typeface.BOLD);
    }

    /** Static helpers when no DockTheme instance is available. */
    public static Typeface interRegular() {
        return interRegular != null ? interRegular : Typeface.create("sans-serif", Typeface.NORMAL);
    }
    public static Typeface interMedium() {
        return interMedium != null ? interMedium : Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }
    public static Typeface interSemiBold() {
        return interSemiBold != null ? interSemiBold : Typeface.create("sans-serif-medium", Typeface.BOLD);
    }

    public float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                android.content.res.Resources.getSystem().getDisplayMetrics());
    }
}






