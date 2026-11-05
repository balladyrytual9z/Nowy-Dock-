package pl.dock.widget.maps;

import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;

import java.io.File;

/**
 * Jednorazowa konfiguracja silnika map (osmdroid) dla calej aplikacji.
 *
 * Mapy dzialaja OFFLINE: kafle trzymamy w pamieci aplikacji i domyslnie
 * NIE pobieramy nic z sieci. Gdy tablet jest online, wlaczamy dokladanie
 * nowych obszarow (patrz Prefs "map_online" i przycisk pobierania w Nawigacji).
 */
public final class MapConfig {

    /** Tlo mapy poza kaflami - zgodne z motywem launchera (noc / dzien). */
    public static final int NIGHT_BG = 0xFF0B0C0E;
    public static final int DAY_BG = 0xFFE8E8ED;

    private static volatile boolean inited;
    private static volatile ColorFilter nightTiles;

    private MapConfig() {
    }

    public static synchronized void init(Context ctx) {
        if (inited) return;
        Context app = ctx.getApplicationContext();
        try {
            IConfigurationProvider cfg = Configuration.getInstance();
            cfg.setUserAgentValue(app.getPackageName() + "/maps");
            File base = new File(app.getFilesDir(), "osmdroid");
            File tiles = new File(base, "tiles");
            cfg.setOsmdroidBasePath(base);
            cfg.setOsmdroidTileCache(tiles);
            // Lekki cache pod ograniczony RAM tabletu (MT8163 / 128 MB heap).
            cfg.setCacheMapTileCount((short) 9);
            cfg.setTileDownloadThreads((short) 2);
            cfg.setTileFileSystemThreads((short) 4);
            cfg.setTileFileSystemCacheMaxBytes(350L * 1024 * 1024);
            cfg.setTileFileSystemCacheTrimBytes(280L * 1024 * 1024);
        } catch (Throwable ignored) {
        }
        inited = true;
    }

    /**
     * Filtr kafli dla trybu nocnego: odbarwienie + przyciemnienie.
     *
     * Kafle OpenStreetMap sa jasne i nie ma dla nich offline ciemnego stylu
     * (pobieramy je wczesniej, wiec nie mozemy przelaczac zrodla na noc).
     * Dlatego przygaszamy je na GPU, tak samo jak reszta launchera w trybie
     * nocnym - tworzymy filtr raz i trzymamy w statyku.
     */
    public static ColorFilter nightTilesFilter() {
        ColorFilter f = nightTiles;
        if (f == null) {
            ColorMatrix desat = new ColorMatrix();
            desat.setSaturation(0.25f);
            // przyciemnienie kolorow + delikatne podbicie, zeby ciemne drogi
            // nie zlewaly sie z tlem
            ColorMatrix dim = new ColorMatrix(new float[]{
                    0.55f, 0f, 0f, 0f, 14f,
                    0f, 0.58f, 0f, 0f, 16f,
                    0f, 0f, 0.66f, 0f, 22f,
                    0f, 0f, 0f, 1f, 0f});
            desat.postConcat(dim);
            f = new ColorMatrixColorFilter(desat);
            nightTiles = f;
        }
        return f;
    }

    /**
     * Ustawia motyw mapy: tlo + filtr kafli. Filtr dotyczy WYLACZNIE kafli,
     * wiec znacznik pozycji, cel i linia trasy pozostaja pelnymi kolorami.
     */
    public static void applyNight(MapView map, boolean night) {
        if (map == null) return;
        try {
            map.setBackgroundColor(night ? NIGHT_BG : DAY_BG);
            TilesOverlay tiles = map.getOverlayManager().getTilesOverlay();
            if (tiles != null) tiles.setColorFilter(night ? nightTilesFilter() : null);
            map.invalidate();
        } catch (Throwable ignored) {
        }
    }
}
