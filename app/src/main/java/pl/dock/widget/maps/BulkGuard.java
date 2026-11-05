package pl.dock.widget.maps;

import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.views.MapView;

/**
 * Straznik masowego pobierania kafli.
 *
 * Zrodlo kafli "Mapnik" (tile.openstreetmap.org) ma w osmdroid flage
 * FLAG_NO_BULK - regulamin serwerow OSM zabrania masowego pobierania.
 * CacheManager sprawdza to dopiero w watku w tle (AsyncTask.doInBackground)
 * i rzuca wtedy TileSourcePolicyException (RuntimeException). Takiego wyjatku
 * NIE da sie zlapac w miejscu wywolania, wiec zabijal caly proces aplikacji.
 *
 * Dlatego sprawdzamy to PRZED startem pobierania i pokazujemy komunikat.
 */
public final class BulkGuard {

    private BulkGuard() {
    }

    /** Czy aktualne zrodlo kafli mapy dopuszcza masowe pobieranie. */
    public static boolean allowed(MapView map) {
        if (map == null) return false;
        try {
            ITileSource ts = map.getTileProvider().getTileSource();
            if (!(ts instanceof OnlineTileSourceBase)) return false;
            return ((OnlineTileSourceBase) ts).getTileSourcePolicy().acceptsBulkDownload();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Komunikat dla uzytkownika, gdy pobieranie jest niedozwolone. */
    public static final String DENIED_MSG =
            "To źródło map nie pozwala na masowe pobieranie (np. styl Topo). "
                    + "Zmień styl map w Ustawieniach na podstawowy albo pobieraj okolicę mniejszymi obszarami.";
}
