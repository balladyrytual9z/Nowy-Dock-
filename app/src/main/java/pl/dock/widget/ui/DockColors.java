
package pl.dock.widget.ui;

/**
 * Central colour palette for Dock 2.0 – Graphite / Night and Day.
 * Accent is a muted green, not neon.
 */
public final class DockColors {

    // Night / Graphite – soft dark for LCD car tablets (NOT pure AMOLED black)
    public static final int NIGHT_BASE = 0xFF0B0C0E;
    public static final int NIGHT_CARD = 0xFF16181C;
    public static final int NIGHT_RAISED = 0xFF22252A;
    public static final int NIGHT_ACCENT = 0xFF8D9994;
    public static final int NIGHT_TEXT = 0xFFF2F3F5;
    public static final int NIGHT_TEXT_SEC = 0xFFA0A4AB;
    public static final int NIGHT_OUTLINE = 0x33FFFFFF;

    // Day
    public static final int DAY_BASE = 0xFFF2F3F5;
    public static final int DAY_CARD = 0xFFFFFFFF;
    public static final int DAY_RAISED = 0xFFE6E8EB;
    public static final int DAY_ACCENT = 0xFF607D70;
    public static final int DAY_TEXT = 0xFF121417;
    public static final int DAY_TEXT_SEC = 0xFF5C636A;
    public static final int DAY_OUTLINE = 0x22000000;

    // Semantic
    public static final int SUCCESS = 0xFF3D9A6A;
    public static final int ERROR = 0xFFC44B4B;
    public static final int WARNING = 0xFFD4922A;

    public static final int CIRCADIAN_WARM = 0xFFFF9F0A;
    public static final int CIRCADIAN_DIM  = 0xFF6C6C70;

    public static int base(boolean night) { return night ? NIGHT_BASE : DAY_BASE; }
    public static int card(boolean night) { return night ? NIGHT_CARD : DAY_CARD; }
    public static int raised(boolean night) { return night ? NIGHT_RAISED : DAY_RAISED; }
    public static int accent(boolean night) { return night ? NIGHT_ACCENT : DAY_ACCENT; }
    public static int text(boolean night) { return night ? NIGHT_TEXT : DAY_TEXT; }
    public static int textSecondary(boolean night) { return night ? NIGHT_TEXT_SEC : DAY_TEXT_SEC; }
    public static int outline(boolean night) { return night ? NIGHT_OUTLINE : DAY_OUTLINE; }

    private DockColors() {}
}






