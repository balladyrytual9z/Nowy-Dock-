package pl.dock.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Testy logiki samoaktualizacji, ktora nie dotyka Androida: rozpoznanie
 * najnowszego taga z kanalu Atom i skladanie linkow do plikow release.
 *
 * Cala samoaktualizacja stoi na tych dwoch krokach - gdyby przestaly dzialac,
 * launcher przestalby sie aktualizowac i nikt by tego nie zauwazyl.
 */
public class UpdaterTest {

    /** Fragment prawdziwego releases.atom - wpisy ida od najnowszego (2026-11-05). */
    private static final String FEED =
            "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n"
                    + "  <entry>\n"
                    + "    <link rel=\"alternate\" type=\"text/html\""
                    + " href=\"https://github.com/balladyrytual9z/Nowy-Dock-/releases/tag/v1.0.199\"/>\n"
                    + "    <title>CarPlay Launcher v1.0.199</title>\n"
                    + "  </entry>\n"
                    + "  <entry>\n"
                    + "    <link rel=\"alternate\" type=\"text/html\""
                    + " href=\"https://github.com/balladyrytual9z/Nowy-Dock-/releases/tag/v1.0.197\"/>\n"
                    + "  </entry>\n"
                    + "</feed>";

    @Test
    public void firstTag_bierzeNajnowszyWpis() {
        assertEquals("v1.0.199", Updater.firstTag(FEED));
    }

    @Test
    public void firstTag_odpornyNaSmieci() {
        assertNull(Updater.firstTag(null));
        assertNull(Updater.firstTag(""));
        assertNull(Updater.firstTag("<feed></feed>"));
        // brak wpisow, ale jest link do listy releasow - nie moze zwrocic smiecia
        assertNull(Updater.firstTag("https://github.com/b/R/releases/tag/"));
    }

    @Test
    public void parseTag_czytaNumerWersji() {
        assertEquals(199, Updater.parseTag("v1.0.199"));
        assertEquals(1, Updater.parseTag("v1.0.1"));
        assertEquals(-1, Updater.parseTag(""));
        assertEquals(-1, Updater.parseTag("bez-numeru"));
    }

    @Test
    public void linkiDoPlikowRelease() {
        assertEquals("CarPlay-Launcher-v1.0.199.apk", Updater.apkName("v1.0.199"));
        assertEquals("https://github.com/balladyrytual9z/Nowy-Dock-/releases/download/"
                        + "v1.0.199/CarPlay-Launcher-v1.0.199.apk",
                Updater.assetUrl("v1.0.199", Updater.apkName("v1.0.199")));
        assertEquals("https://github.com/balladyrytual9z/Nowy-Dock-/releases/download/"
                        + "v1.0.199/SHA256SUMS.txt",
                Updater.assetUrl("v1.0.199", "SHA256SUMS.txt"));
    }

    @Test
    public void decyzjaOAktualizacji() {
        int local = 199;
        assertTrue("nowszy release ma byc widoczny", Updater.parseTag("v1.0.204") > local);
        assertTrue("ten sam release to brak aktualizacji",
                Updater.parseTag("v1.0.199") <= local);
        assertTrue("starszy release to brak aktualizacji",
                Updater.parseTag("v1.0.150") <= local);
    }

    @Test
    public void domyslnyInterwalJestTestowy() {
        // Tryb testowy: sprawdzanie co minute. Gdyby ktos zmienil domyslna wartosc,
        // ten test przypomni, ze uzytkownik chce na razie 1 min.
        assertEquals(1, Prefs.UPDATE_MIN_DEFAULT);
    }
}
