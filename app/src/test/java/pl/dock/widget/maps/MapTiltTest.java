package pl.dock.widget.maps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Geometria pochylenia mapy ("3D"). Test liczy rzut NIEZALEZNIE od MapTilt
 * (skala, obrot wokol dolnej krawedzi, perspektywa, przesuniecie) i sprawdza,
 * ze pochylona mapa nadal zakrywa caly widok, a nasza pozycja (srodek mapy)
 * wypada tam, gdzie w nawigacjach.
 *
 * Gdyby ktos zmienil kat, poziom albo zapas zakrycia i zepsul widok
 * (przeswit u gory albo znacznik uciekajacy pod pasek), ten test to zlapie.
 */
public class MapTiltTest {

    private static final float[] SIZES = {120f, 360f, 480f, 1080f};   // kafelek i ekrany 9"

    /** Rzut punktu y (0 = gora widoku, h = dol) - tak, jak zrobi to Android. */
    private static double project(double y, double h) {
        double a = Math.toRadians(MapTilt.ANGLE);
        double k = MapTilt.scaleFor((float) h);
        double d = MapTilt.cameraDist((float) h);
        double shift = MapTilt.shiftFor((float) h);
        double y1 = h + (y - h) * k;                 // skala wokol dolnej krawedzi
        double depth = (h - y1) * Math.sin(a);       // odleglosc od ekranu = perspektywa
        double persp = d / (d + depth);
        return h + (y1 - h) * Math.cos(a) * persp + shift;
    }

    @Test
    public void pochylenieZakrywaCalyWidok() {
        for (float h : SIZES) {
            double top = project(0, h);
            double bottom = project(h, h);
            assertTrue("gora odslonieta dla h=" + h + " (top=" + top + ")", top <= 0.0);
            assertTrue("dol odsloniety dla h=" + h + " (bottom=" + bottom + ")", bottom >= h);
        }
    }

    @Test
    public void znacznikWypadaWNawigacyjnymMiejscu() {
        for (float h : SIZES) {
            double marker = project(h / 2.0, h) / h;
            assertEquals("znacznik dla h=" + h, MapTilt.MARKER_ANCHOR, marker, 0.03);
        }
    }

    @Test
    public void pochylenieNieJestPrzesadne() {
        assertTrue("kat poza sensownymi granicami: " + MapTilt.ANGLE,
                MapTilt.ANGLE >= 30f && MapTilt.ANGLE <= 70f);
        float k = MapTilt.scaleFor(480f);
        assertTrue("przyblizenie poza rozsadkiem: " + k, k >= 1.2f && k <= 3.5f);
        assertTrue("kamera musi byc dalej niz wysokosc widoku",
                MapTilt.cameraDist(480f) > 480f);
    }

    @Test
    public void rozmiarNieZmieniaWygladu() {
        // ten sam kat i ten sam efekt na malym kafelku i na pelnym ekranie
        assertEquals(MapTilt.scaleFor(120f), MapTilt.scaleFor(1080f), 0.01);
        assertEquals(MapTilt.shiftFor(120f) / 120.0, MapTilt.shiftFor(1080f) / 1080.0, 0.01);
    }
}
