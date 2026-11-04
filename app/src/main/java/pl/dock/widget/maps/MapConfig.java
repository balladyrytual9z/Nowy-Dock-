package pl.dock.widget.maps;

import android.content.Context;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

/**
 * Jednorazowa konfiguracja silnika map (osmdroid) dla calej aplikacji.
 *
 * Mapy dzialaja OFFLINE: kafle trzymamy w pamieci aplikacji i domyslnie
 * NIE pobieramy nic z sieci. Gdy tablet jest online, wlaczamy dokladanie
 * nowych obszarow (patrz Prefs "map_online" i przycisk pobierania w Nawigacji).
 */
public final class MapConfig {

    private static volatile boolean inited;

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
}
