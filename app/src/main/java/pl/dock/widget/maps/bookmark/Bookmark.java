package pl.dock.widget.maps.bookmark;

/**
 * Zakladka (zapisane miejsce). Czysta logika bez Androida - GPX/KML
 * parser i testy JVM korzystaja z tej klasy.
 */
public final class Bookmark {

    public final long id;
    public final String name;
    public final double lat;
    public final double lon;
    public final long addedAt;

    public Bookmark(long id, String name, double lat, double lon, long addedAt) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.lat = lat;
        this.lon = lon;
        this.addedAt = addedAt;
    }
}
