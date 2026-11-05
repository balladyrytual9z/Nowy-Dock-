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
        // fix 11 m dalej, po 1000 ms (10 m/s - tyle da sie przejechac)
        s.onFix(0.0001d, 0d, 0f, 10f, 5f, 2000L);

        s.advance(2500L);   // polowa drogi (fix o 2000, czas przejscia 1000 ms)
        assertEquals(0.00005d, s.lat(), 1e-9);

        s.advance(3000L);   // koniec drogi
        assertEquals(0.0001d, s.lat(), 1e-9);
    }

    @Test
    public void nowyFixWTrakcieRuchu_nieRobikSkoku() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(0d, 0d, 0f, 10f, 5f, 1000L);
        s.onFix(0.0002d, 0d, 0f, 10f, 5f, 2000L);   // 22 m - realny ruch przy 10 m/s
        s.advance(2500L);
        double mid = s.lat();
        assertTrue("w polowie ruchu pozycja jest miedzy fixami",
                mid > 0d && mid < 0.0002d);

        // kolejny fix w polowie ruchu: wartosc ma byc ciagla (zero skoku)
        s.onFix(0.0004d, 0d, 0f, 10f, 5f, 2500L);
        assertEquals(mid, s.lat(), EPS);

        s.advance(3000L);
        assertTrue("po nowym fixie pozycja idzie dalej", s.lat() > mid);
    }

    @Test
    public void postojNaSwiatlachTrzymaKursZJazdy() {
        // Wczesniej zatrzymanie oznaczalo powrot mapy na polnoc (orientacja 0).
        // Uzytkownik zglosil, ze nie chce takiego przestawiania sie widoku na
        // swiatlach - widok ma zostac taki, jak w czasie jazdy. Kurs NIE jest
        // brany z szumu GPS (predkosc 0), tylko trzymany z ostatniej jazdy.
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 90f, 20f, 5f, 1000L);
        assertEquals(270f, s.mapOrientation(), 1e-3f);

        s.onFix(50d, 20d, 90f, 0f, 5f, 2000L);
        s.advance(3000L);
        assertEquals("widok ma zostac z jazdy", 270f, s.mapOrientation(), 1f);
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
        // fix'a i mapa obracala sie na boki. Teraz ponizej 10 km/h kurs bierzemy
        // z ostatniej JAZDY (a gdy jej nie bylo - polnoc), nigdy z takiego fix'a.
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 0f, 20f, 5f, 1000L);
        s.advance(2000L);
        s.onFix(50d, 20d, 90f, 0.6f, 5f, 2000L);
        s.advance(4000L);
        assertTrue("kurs 90 stopni z fix'a 0.6 m/s nie moze obracac mapy (orientacja "
                        + s.mapOrientation() + ")",
                Math.abs(s.mapOrientation()) < 1e-3f);
    }

    // ---------------- skoki GPS: bledny fix nie moze przestawic mapy ----------------

    @Test
    public void blednyFixZKlamliwaPredkosciaJestOdrzucany() {
        // Dokladnie to, co robilo sie na urzadzeniu: stoimy, a GPS raz podaje
        // pozycje 5 km dalej i twierdzi, ze jedziemy 12 m/s.
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(52.0000d, 19.0000d, 0f, 0f, 6f, 1000L);
        s.advance(3000L);
        double lat0 = s.lat(), lon0 = s.lon();
        s.onFix(52.0450d, 19.0000d, 90f, 12f, 8f, 2000L);
        s.advance(4000L);
        assertEquals("pozycja nie moze skoczyc 5 km",
                0d, HeadingSmoother.metersBetween(lat0, lon0, s.lat(), s.lon()), 0.01d);
        assertTrue("licznik nie moze pokazac 43 km/h z blednego fixa, jest "
                + s.speedMs(), s.speedMs() < 0.01f);
        assertTrue("nastepny prawdziwy fix ma byc przyjety", s.hasFix());
    }

    @Test
    public void powtarzajacySieSkokToPrawdziwaZmianaMiejsca() {
        // Prawdziwa zmiana miejsca (np. laweta): GPS uparcie pokazuje 5 km dalej.
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(52.0d, 19.0d, 0f, 0f, 6f, 1000L);
        long t = 2000L;
        for (int i = 0; i < 12; i++) {
            s.advance(t);                    // domknij ruch do biezacej chwili
            s.onFix(52.045d, 19.0d, 0f, 0f, 6f, t);
            t += 1000L;
        }
        s.advance(t);
        double dist = HeadingSmoother.metersBetween(52.0d, 19.0d, s.lat(), s.lon());
        assertTrue("prawdziwa zmiana miejsca ma byc sledzona (przebyte " + dist + " m)",
                dist > 1000d);
    }

    @Test
    public void skokPoDluzszejPrzerwieNaPostojuWymagaPotwierdzenia() {
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(52.0d, 19.0d, 0f, 0f, 6f, 1000L);
        s.advance(3000L);
        // 60 s przerwy w fixach i nagle 3 km dalej - to jeszcze nie dowod
        s.onFix(52.027d, 19.0d, 0f, 0f, 6f, 61000L);
        s.advance(62000L);
        assertEquals("jeden fix po przerwie to za malo",
                0d, HeadingSmoother.metersBetween(52.0d, 19.0d, s.lat(), s.lon()), 0.01d);
        long t = 62000L;
        // GPS powtarza to samo miejsce: najpierw potwierdzenie, potem dojscie
        for (int i = 0; i < 14; i++) {
            t += 1000L;
            s.advance(t);
            s.onFix(52.027d, 19.0d, 0f, 0f, 6f, t);
        }
        s.advance(t + 1000L);
        double dist = HeadingSmoother.metersBetween(52.0d, 19.0d, s.lat(), s.lon());
        assertTrue("po potwierdzeniu pozycja ma dojsc na miejsce (jest " + dist + " m)",
                dist > 1000d);
    }

    @Test
    public void szumNaPostojuNiePrzesuwaNawetPozaPromieniem() {
        // Szum o rozrzucie wiekszym niz promien postoju (12 m) - nadal zero ruchu,
        // bo pojedyncze fixy nie moga sie zsumowac w "dryf".
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 0f, 0.2f, 10f, 1000L);
        double[] offs = {14d, -16d, 18d, -13d, 15d, -17d, 19d, -15d, 16d, -18d};
        double lat0 = s.lat(), lon0 = s.lon();
        for (int i = 0; i < offs.length; i++) {
            long t = 2000L + i * 1000L;
            s.advance(t);
            s.onFix(50d, 20d + mLon(offs[i], 50d), 0f, 0.2f, 10f, t);
        }
        s.advance(20000L);
        // Od 2026-11-05 dziala jeszcze glosowanie: jesli GPS UPARCIE pokazuje ten sam
        // punkt obok (stale przesuniecie), to po kilku fixach idziemy do jego srodka.
        // Efekt jest maly (metry), a losowy szum nadal nie rusza znacznikiem.
        double drift = HeadingSmoother.metersBetween(lat0, lon0, s.lat(), s.lon());
        assertTrue("postoj ma byc praktycznie nieruchomy (dryf " + drift + " m)", drift < 5d);
    }

    // ---------------- widok przy zatrzymaniu: nie przestawiamy na polnoc ----------------

    @Test
    public void zatrzymanieNiePrzestawiaWidokuNaPolnoc() {
        // Jedziemy na wschod (kurs 90 -> mapa 270), stajemy na swiatlach.
        // Widok ma zostac taki, jak w czasie jazdy - a nie wskoczyc na polnoc.
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 90f, 20f, 5f, 1000L);
        s.advance(2000L);
        assertEquals(270f, s.mapOrientation(), 1e-3f);

        s.onFix(50d, 20d, 90f, 0f, 5f, 2000L);            // stop na swiatlach
        s.advance(3000L);
        assertEquals("widok ma zostac z jazdy", 270f, s.mapOrientation(), 1f);

        // szum kursu na postoju (GPS podaje byle co) nie moze krecic mapa
        s.onFix(50d, 20d, 10f, 0f, 5f, 3000L);
        s.advance(4000L);
        s.onFix(50d, 20d, 300f, 0f, 5f, 4000L);
        s.advance(5000L);
        assertEquals("szum kursu nie kreci mapa", 270f, s.mapOrientation(), 1f);
    }

    @Test
    public void bezJazdyZostajePolnoc() {
        // Stojac od startu nie mamy kursu z jazdy - wtedy zostaje polnoc.
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(50d, 20d, 123f, 0f, 5f, 1000L);
        s.advance(2000L);
        assertEquals(0f, s.mapOrientation(), 1e-3f);
    }

    // ---------------- postoj: pozycja wraca do prawdy (glosowanie) ----------------

    @Test
    public void poPostojuWZlymMiejscuPozycjaWracaDoPrawdziwej() {
        // Zly punkt startowy (np. stara pozycja z AGPS), a GPS uparcie pokazuje
        // prawdziwe miejsce 5 km dalej. Stoimy - i mimo to mamy tam wrocic.
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(52.0d, 19.0d, 0f, 0f, 8f, 1000L);
        double[] noise = {0d, 12d, -9d, 15d, -14d, 6d, -11d, 13d, -7d, 9d, -13d, 11d,
                -8d, 14d, -12d, 7d, -10d, 10d, -6d, 8d};
        long t = 2000L;
        double maxStep = 0d, prevLat = s.lat(), prevLon = s.lon();
        for (int i = 0; i < 40; i++) {
            t += 1000L;
            s.advance(t);
            double off = noise[i % noise.length];
            s.onFix(52.045d + off / 111320d, 19.0d, 0f, 0f, 8f, t);
            maxStep = Math.max(maxStep,
                    HeadingSmoother.metersBetween(prevLat, prevLon, s.lat(), s.lon()));
            prevLat = s.lat();
            prevLon = s.lon();
        }
        double left = HeadingSmoother.metersBetween(52.045d, 19.0d, s.lat(), s.lon());
        assertTrue("ma dojsc do prawdziwego miejsca (zostalo " + left + " m)", left < 400d);
        assertTrue("korekta bez teleportu (max krok " + maxStep + " m)", maxStep < 600d);
    }

    @Test
    public void losoweSkokiPrzyPostojuNieRuszajaPozycji() {
        // GPS co sekunde rzuca nas w inne miejsce po kilka km - nie ma z czego
        // zrobic grupy, wiec mamy stac w miejscu.
        HeadingSmoother s = new HeadingSmoother();
        s.onFix(52.0d, 19.0d, 0f, 0f, 8f, 1000L);
        double[] dirs = {1d, -1d, 3d, -3d, 5d, -5d, 7d, -7d, 2d, -2d, 4d, -4d};
        long t = 2000L;
        for (int i = 0; i < dirs.length; i++) {
            t += 1000L;
            s.advance(t);
            s.onFix(52.0d + dirs[i] * 0.03d, 19.0d + dirs[i] * 0.02d, 0f, 0f, 8f, t);
        }
        s.advance(t + 1000L);
        assertEquals("pozycja ma stac w miejscu",
                0d, HeadingSmoother.metersBetween(52.0d, 19.0d, s.lat(), s.lon()), 1d);
    }

    // ---------------- start z pamieci: "gdzie zaparkowalem" ----------------

    @Test
    public void startZZapamietanejPozycjiDoprecyzowujeBezSzukania() {
        // Wsiadamy do auta: mamy zapamietane miejsce, a GPS po chwili podaje prawie
        // to samo (pare metrow obok). Znacznik ma byc od razu na miejscu.
        HeadingSmoother s = new HeadingSmoother();
        s.seed(52.0d, 19.0d, 1000L);
        assertEquals(52.0d, s.lat(), 1e-9);
        s.advance(1500L);
        s.onFix(52.00005d, 19.0d, 0f, 0f, 8f, 2000L);      // ~5,5 m dalej
        s.advance(3000L);
        double off = HeadingSmoother.metersBetween(52.00005d, 19.0d, s.lat(), s.lon());
        assertTrue("ma byc od razu na miejscu (jest " + off + " m)", off < 8d);
    }

    @Test
    public void startZZapamietanejPozycjiNieSzukaOdZera() {
        // Zly pierwszy fix (np. stara pozycja z AGPS 5 km dalej) NIE moze nas
        // przeniesc - startujemy z zaparkowanego miejsca, GPS tylko doprecyzowuje.
        HeadingSmoother s = new HeadingSmoother();
        s.seed(52.0d, 19.0d, 1000L);
        s.advance(1500L);
        s.onFix(52.045d, 19.0d, 0f, 0f, 8f, 2000L);
        s.advance(3000L);
        double moved = HeadingSmoother.metersBetween(52.0d, 19.0d, s.lat(), s.lon());
        assertTrue("zly pierwszy fix nie przenosi nas (ruszylismy sie " + moved + " m)",
                moved < 40d);
        // a prawdziwe fixy (w miejscu zaparkowania) dochodza normalnie
        long t = 3000L;
        for (int i = 0; i < 6; i++) {
            t += 1000L;
            s.advance(t);
            s.onFix(52.00002d, 19.0d, 0f, 0f, 6f, t);
        }
        double left = HeadingSmoother.metersBetween(52.00002d, 19.0d, s.lat(), s.lon());
        assertTrue("po prawdziwych fixach jestesmy na miejscu (jest " + left + " m)",
                left < 20d);
    }
}