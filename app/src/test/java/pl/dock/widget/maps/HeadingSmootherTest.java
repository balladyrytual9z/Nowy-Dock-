package pl.dock.widget.maps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Testy wygladzania ruchu i obrotu mapy (JVM, bez Androida).
 * HeadingSmoother nie dotyka API Androida, wiec dziala w czystym JUnit.
 */
public class HeadingSmootherTest {

    private static final double EPS = 1e-6;

    @Test
    public void normalize_wrapujeZakres() {
        assertEquals(0f, HeadingSmoother.normalize(0f), 1e-4f);
        assertEquals(90f, HeadingSmoother.normalize(450f), 1e-4f);
        assertEquals(270f, HeadingSmoother.normalize(-90f), 1e-4f);
        assertEquals(359f, HeadingSmoother.normalize(-1f), 1e-4f);
    }

    @Test
    public void shortestDelta_idzieNajkrotszaDroga() {
        // 359 -> 1 stopien: najkrotsza droga to +2, a nie -358
        assertEquals(2f, HeadingSmoother.shortestDelta(1f, 359f), 1e-3f);
        // 350 -> 10: najkrotsza droga to -20 (w lewo)
        assertEquals(-20f, HeadingSmoother.shortestDelta(350f, 10f), 1e-3f);
        // obrot o 180 nie gubi sie
        assertEquals(180f, HeadingSmoother.shortestDelta(180f, 0f), 1e-3f);
    }

    @Test
    public void pierwszyFix_ustawiaPozycjeOdRazu() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(52.5d, 21.0d, 90f, 10f, 8f, 1000L);
        assertEquals(52.5d, s.lat(), EPS);
        assertEquals(21.0d, s.lon(), EPS);
        assertEquals(90f, s.markerBearing(), 1e-3f);
        assertEquals(270f, s.mapOrientation(), 1e-3f);   // heading-up: -kurs
        assertTrue(s.hasFix());
    }

    @Test
    public void pozycja_idzieLiniowoDoNowegoFixu() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(0d, 0d, 0f, 10f, 5f, 1000L);
        // fix oddalony o 0.001 stopnia, po 1000 ms od poprzedniego
        s.onFix(0.001d, 0d, 0f, 10f, 5f, 2000L);

        s.advance(2500L);   // polowa drogi (fix o 2000, czas przejscia 1000 ms)
        assertEquals(0.0005d, s.lat(), 1e-9);

        s.advance(3000L);   // koniec drogi
        assertEquals(0.001d, s.lat(), 1e-9);
    }

    @Test
    public void nowyFixWTrakcieRuchu_nieRobikSkoku() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(0d, 0d, 0f, 10f, 5f, 1000L);
        s.onFix(0.01d, 0d, 0f, 10f, 5f, 2000L);
        s.advance(2500L);
        double mid = s.lat();
        assertTrue("w polowie ruchu pozycja jest miedzy fixami", mid > 0d && mid < 0.01d);

        // kolejny fix w polowie ruchu: wartosc ma byc ciagla (zero skoku)
        s.onFix(0.02d, 0d, 0f, 10f, 5f, 2500L);
        assertEquals(mid, s.lat(), EPS);

        s.advance(3000L);
        assertTrue("po nowym fixie pozycja idzie dalej", s.lat() > mid);
    }

    @Test
    public void postoj_wracaDoPolnocy() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 90f, 20f, 5f, 1000L);
        assertEquals(270f, s.mapOrientation(), 1e-3f);

        // zatrzymanie: predkosc 0 -> cel to polnoc (orientacja 0)
        s.onFix(50d, 20d, 90f, 0f, 5f, 2000L);
        s.advance(3000L);
        assertEquals(0f, s.mapOrientation(), 1e-3f);
        assertFalse("na postoju nie rysujemy strzalki", s.isMoving());
    }

    @Test
    public void obrotPrzezZero_nieKreciSieWDrugaStrone() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 350f, 20f, 5f, 1000L);
        float before = s.markerBearing();
        // kurs 10 stopni: droga przez 0/360 (2 stopnie), a nie 340 w tyl
        s.onFix(50d, 20d, 10f, 20f, 5f, 2000L);
        s.advance(2500L);   // polowa obrotu: 350 -> 370, czyli 360 == polnoc
        float mid = s.markerBearing();
        assertEquals("w polowie obrotu jestesmy na polnocy", 0f, mid, 1e-3f);
        assertTrue("obrot idzie przez polnoc, nie 340 stopni w tyl", before > mid);
        s.advance(3000L);
        assertEquals(10f, s.markerBearing(), 1e-3f);
    }

    @Test
    public void moving_falseGdyFixJestStary() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 90f, 20f, 5f, 1000L);
        assertTrue(s.moving(1500L));
        assertFalse("po 5 s bez fix'a petla ma odpoczac", s.moving(10000L));
    }

    @Test
    public void reset_czysciStan() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 90f, 20f, 5f, 1000L);
        s.reset();
        assertFalse(s.hasFix());
        assertEquals(0f, s.speedMs(), 1e-4f);
    }
}
