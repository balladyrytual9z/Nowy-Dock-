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

    // ------------------------------------------------------------------
    // Postoj: GPS szumi w miejscu (zgloszenie uzytkownika 2026-11-05:
    // "jak stoje w miejscu i GPS lapie fix, to pozycja skacze bez sensu").
    // Fixy na postoju wypadaja o kilka metrow od siebie w losowych kierunkach.
    // ------------------------------------------------------------------

    /** Te same "szumy" co w symulacji: +-5 m, kierunki rozne. */
    private static final double[][] NOISE_5M = {
            {3.2d, 1.1d}, {-4.5d, -2.0d}, {1.0d, 2.5d}, {-2.2d, -1.5d},
            {4.0d, 4.1d}, {-3.1d, 0.3d}, {2.4d, -4.2d}, {-0.5d, -1.1d},
            {4.7d, 2.0d}, {-4.9d, 3.3d}, {0.9d, -3.7d}, {3.6d, -0.9d},
    };

    private static double mLat(double m) {
        return m / 111320d;
    }

    private static double mLon(double m, double latDeg) {
        return m / (111320d * Math.cos(Math.toRadians(latDeg)));
    }

    @Test
    public void postoj_szumNieRuszaZnacznika() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 0f, 0.2f, 6f, 1000L);
        double lat0 = s.lat();
        double lon0 = s.lon();
        float bearing0 = s.markerBearing();
        double maxDrift = 0d;
        for (int i = 0; i < 24; i++) {
            long t = 2000L + i * 1000L;
            s.advance(t);
            maxDrift = Math.max(maxDrift, HeadingSmoother.metersBetween(lat0, lon0, s.lat(), s.lon()));
            double[] n = NOISE_5M[i % NOISE_5M.length];
            // predkosc 0.0-0.5 m/s: ponizej progu jazdy, tak wlasnie szumi GPS na postoju
            s.onFix(50d + mLat(n[0]), 20d + mLon(n[1], 50d),
                    (i * 37) % 360, i % 2 == 0 ? 0f : 0.5f, 6f + (i % 3), t);
        }
        assertTrue("znacznik nie moze chodzic na postoju (dryf " + maxDrift + " m)",
                maxDrift < 0.5d);
        assertTrue("na postoju licznik ma pokazywac 0, jest " + s.speedMs(), s.speedMs() < 1e-4f);
        assertTrue("kurs nie moze sie zmieniac od szumu (" + bearing0 + " -> " + s.markerBearing() + ") "
                + "(dryf kursu " + Math.abs(s.markerBearing() - bearing0) + ") ",
                Math.abs(s.markerBearing() - bearing0) < 1e-3f);
    }

    @Test
    public void postoj_slabyGpsTezNieRusza() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 0f, 0.2f, 25f, 1000L);
        double lat0 = s.lat();
        double lon0 = s.lon();
        double maxDrift = 0d;
        for (int i = 0; i < 24; i++) {
            long t = 2000L + i * 1000L;
            s.advance(t);
            maxDrift = Math.max(maxDrift, HeadingSmoother.metersBetween(lat0, lon0, s.lat(), s.lon()));
            double[] n = NOISE_5M[i % NOISE_5M.length];
            // dokladnosc 25 m: promien "stania" rosnie razem z nia
            s.onFix(50d + mLat(n[0] * 4d), 20d + mLon(n[1] * 4d, 50d),
                    0f, 0.2f, 25f, t);
        }
        assertTrue("przy slaby GPS tez nie skaczemy (dryf " + maxDrift + " m)", maxDrift < 0.5d);
    }

    @Test
    public void postoj_realnePrzesuniecieNieSkacze() {
        // GPS po zimnym starcie "poprawia sie" o 40 m. Wtedy trzeba dojsc do nowej
        // pozycji, ale bez skoku o 40 m (wlasnie taki skok widac na urzadzeniu).
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 0f, 0.2f, 6f, 1000L);
        double latFix = 50d;
        double lonFix = 20d + mLon(40d, 50d);
        double prevLat = s.lat();
        double prevLon = s.lon();
        double maxStep = 0d;
        for (int i = 0; i < 12; i++) {
            long t = 2000L + i * 1000L;
            s.advance(t);
            maxStep = Math.max(maxStep, HeadingSmoother.metersBetween(prevLat, prevLon, s.lat(), s.lon()));
            prevLat = s.lat();
            prevLon = s.lon();
            s.onFix(latFix, lonFix, 0f, 0.2f, 6f, t);
        }
        assertTrue("zadnego skoku o 40 m (max skok " + maxStep + " m)", maxStep < 12d);
        double left = HeadingSmoother.metersBetween(latFix, lonFix, s.lat(), s.lon());
        assertTrue("ma dojsc do nowej pozycji (zostalo " + left + " m)", left < 14d);
    }

    @Test
    public void pieszo_szumJestTlumionyAleIdzie() {
        HeadingSmoother s = new HeadingSmoother();
        double lat = 50d;
        s.onFix(lat, 20d, 0f, 1.4f, 6f, 1000L);
        double prevLat = s.lat();
        double prevLon = s.lon();
        double maxStep = 0d;
        for (int i = 0; i < 20; i++) {
            long t = 2000L + i * 1000L;
            s.advance(t);
            maxStep = Math.max(maxStep, HeadingSmoother.metersBetween(prevLat, prevLon, s.lat(), s.lon()));
            prevLat = s.lat();
            prevLon = s.lon();
            lat += mLat(1.4d);                       // idzie 1.4 m/s
            double[] n = NOISE_5M[i % NOISE_5M.length];
            s.onFix(lat + mLat(n[0]), 20d + mLon(n[1], 50d), 0f, 1.4f, 6f, t);
        }
        double travelled = HeadingSmoother.metersBetween(50d, 20d, s.lat(), s.lon());
        assertTrue("idzie za uzytkownikiem (przebyte " + travelled + " m)", travelled > 20d);
        assertTrue("szum stlumiony, max skok " + maxStep + " m", maxStep < 5d);
    }

    @Test
    public void fixBezuzytecznyJestOdrzucany() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 0f, 0.2f, 6f, 1000L);
        double lat0 = s.lat();
        double lon0 = s.lon();
        // fix 300 m dalej z dokladnoscia 200 m - to smiec, nie pozycja
        s.onFix(50d + mLat(300d), 20d, 0f, 0.2f, 200f, 2000L);
        s.advance(3000L);
        assertEquals(0d, HeadingSmoother.metersBetween(lat0, lon0, s.lat(), s.lon()), 0.01d);
    }

    @Test
    public void jazda_idzieDokladnieZaFixem() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 90f, 14f, 5f, 1000L);
        double lon = 20d + mLon(14d, 50d);
        s.onFix(50d, lon, 90f, 14f, 5f, 2000L);
        s.advance(3000L);
        assertEquals("w jazdie (14 m/s) nie tlumimy pozycji",
                0d, HeadingSmoother.metersBetween(50d, lon, s.lat(), s.lon()), 0.01d);
        assertEquals(270f, s.mapOrientation(), 1e-3f);
    }

    @Test
    public void malaPredkoscNieKreciMapa() {
        // Predkosc 0.6 m/s (2 km/h) to jeszcze szum: wczesniej kurs bral sie z takiego
        // fix'a i mapa obracala sie na boki. Teraz ponizej 10 km/h mapa idzie na polnoc.
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 0f, 20f, 5f, 1000L);
        s.advance(2000L);
        s.onFix(50d, 20d, 90f, 0.6f, 5f, 2000L);
        s.advance(4000L);
        assertTrue("kurs 90 stopni z fix'a 0.6 m/s nie moze obracac mapy (orientacja "
                        + s.mapOrientation() + ")",
                Math.abs(s.mapOrientation()) < 1e-3f);
    }
}
