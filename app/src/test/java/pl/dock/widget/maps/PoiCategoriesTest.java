package pl.dock.widget.maps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Testy mapowania polskich fraz na kategorie POI (JVM, bez Androida).
 */
public class PoiCategoriesTest {

    @Test
    public void znaneKategorie() {
        assertEquals("pharmacy", PoiCategories.lookup("apteka"));
        assertEquals("fuel", PoiCategories.lookup("Stacje benzynowe"));
        assertEquals("parking", PoiCategories.lookup("parking"));
        assertEquals("atm", PoiCategories.lookup("bankomaty"));
        assertEquals("charging station", PoiCategories.lookup("stacja ładowania"));
        assertEquals("restaurant", PoiCategories.lookup("restauracja"));
        assertEquals("hotel", PoiCategories.lookup("hotele"));
    }

    @Test
    public void kategorieZeOdmiennaForma() {
        // dopasowanie po fragmencie: odmiany tez lapia
        assertEquals("fuel", PoiCategories.lookup(" tankuję - gdzie paliwo?"));
        assertEquals("pharmacy", PoiCategories.lookup("najbliższa apteka"));
        assertEquals("supermarket", PoiCategories.lookup("sklepy spożywcze"));
    }

    @Test
    public void zwykleZapytaniaToNieKategorie() {
        assertNull(PoiCategories.lookup("ul. Marszałkowska"));
        assertNull(PoiCategories.lookup("Warszawa"));
        assertNull(PoiCategories.lookup("Kraków, Rynek Główny"));
        assertNull(PoiCategories.lookup(""));
        assertNull(PoiCategories.lookup(null));
    }
}
