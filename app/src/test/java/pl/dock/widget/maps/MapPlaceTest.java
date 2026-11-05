package pl.dock.widget.maps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Kontrola zapisanej pozycji mapy. Te testy pilnuja, zeby do Ustawien nie
 * trafialy smieci (m.in. (0,0) z niezainicjalizowanej mapy) i zeby po
 * restarcie nie otwieralo sie puste morze.
 */
public class MapPlaceTest {

    @Test
    public void normalnePozycjeSaDobre() {
        assertTrue(MapPlace.usable(52.2297d, 21.0122d));      // Warszawa
        assertTrue(MapPlace.usable(49.2992d, 19.9496d));      // Zakopane
        assertTrue(MapPlace.usable(-33.86d, 151.21d));        // Sydney
        assertTrue(MapPlace.usable(0d, 19.94d));              // rownik, ale na lądzie
    }

    @Test
    public void nullWyspaJestOdrzucana() {
        // Dokladnie to potrafi oddac niezainicjalizowana mapa osmdroid
        assertFalse(MapPlace.usable(0d, 0d));
        assertFalse(MapPlace.usable(0.0001d, -0.0001d));
    }

    @Test
    public void smieciSaOdrzucane() {
        assertFalse(MapPlace.usable(Double.NaN, 19d));
        assertFalse(MapPlace.usable(52d, Double.NaN));
        assertFalse(MapPlace.usable(Double.POSITIVE_INFINITY, 19d));
        assertFalse(MapPlace.usable(52d, Double.NEGATIVE_INFINITY));
        assertFalse(MapPlace.usable(91d, 19d));               // za daleko na polnoc
        assertFalse(MapPlace.usable(52d, 200d));              // za daleko na wschod
    }

    @Test
    public void zoomJestOgraniczany() {
        assertEquals(16f, MapPlace.zoom(16f, 16f), 1e-3f);
        assertEquals(16f, MapPlace.zoom(0f, 16f), 1e-3f);     // zoom 0 = pusto
        assertEquals(16f, MapPlace.zoom(Float.NaN, 16f), 1e-3f);
        assertEquals(16f, MapPlace.zoom(99f, 16f), 1e-3f);
        assertEquals(MapPlace.MAX_ZOOM, MapPlace.zoom(MapPlace.MAX_ZOOM, 16f), 1e-3f);
    }

    @Test
    public void naStarcieFixPrzedZaparkowanymMiejscem() {
        // 1) swiezy fix GPS wygrywa ze wszystkim
        double[] p = MapPlace.startupPlace(52.2d, 21.0d, 50.0d, 19.9d, 49.0d, 22.0d);
        assertEquals(52.2d, p[0], 1e-9);
        assertEquals(21.0d, p[1], 1e-9);
        // 2) bez fixa - "gdzie zaparkowalem"
        p = MapPlace.startupPlace(0d, 0d, 50.0d, 19.9d, 49.0d, 22.0d);
        assertEquals(50.0d, p[0], 1e-9);
        // 3) bez fixa i bez zaparkowanego - ostatni widok mapy
        p = MapPlace.startupPlace(0d, 0d, 0d, 0d, 49.0d, 22.0d);
        assertEquals(49.0d, p[0], 1e-9);
        // 4) nic nie ma - null, czyli widok calej Polski
        assertNull(MapPlace.startupPlace(0d, 0d, 0d, 0d, 0d, 0d));
        // smieci tez nie przejda
        assertNull(MapPlace.startupPlace(Double.NaN, 5d, Double.POSITIVE_INFINITY, 5d, 0d, 0d));
    }
}