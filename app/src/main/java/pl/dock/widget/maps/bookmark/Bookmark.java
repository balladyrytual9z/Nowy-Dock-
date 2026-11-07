package pl.dock.widget.maps.bookmark;

/**
 * Zakladka (zapisane miejsce). Czysta logika bez Androida - GPX/KML
 * parser i testy JVM korzystaja z tej klasy.
 */
public final class Bookmark {

    /** Paleta kolorow zakladek (jak kolory pinow w Organic Maps). */
    public static final int[] COLORS = {
            0xFF0A84FF, 0xFF30D158, 0xFFFF9500, 0xFFFF3B30,
            0xFFAF52DE, 0xFFFFD60A, 0xFF64D2FF, 0xFFFF2D55};

    /** Kolor z palety po numerze porzadkowym (cyklicznie, ujemne tez OK). */
    public static int colorAt(int index) {
        int i = index % COLORS.length;
        if (i < 0) i += COLORS.length;
        return COLORS[i];
    }

    public final long id;
    public final String name;
    public final double lat;
    public final double lon;
    public final long addedAt;
    public final String folder;   // grupa zakladek ("" = bez folderu)
    public final int color;       // kolor z palety (0 = domyslny/szare)

    public Bookmark(long id, String name, double lat, double lon, long addedAt) {
        this(id, name, lat, lon, addedAt, "", 0);
    }

    public Bookmark(long id, String name, double lat, double lon, long addedAt,
                    String folder, int color) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.lat = lat;
        this.lon = lon;
        this.addedAt = addedAt;
        this.folder = folder == null ? "" : folder;
        this.color = color;
    }
}
