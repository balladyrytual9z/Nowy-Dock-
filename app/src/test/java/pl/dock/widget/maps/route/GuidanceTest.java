package pl.dock.widget.maps.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Testy maszyny prowadzenia po trasie (JVM, bez Androida).
 * Trasa syntetyczna: 52.0 -> 52.1 (krok 0.001), manewr w polowie.
 */
public class GuidanceTest {

    private static final double LAT0 = 52.0d;
    private static final double LON0 = 21.0d;
    private static final double STEP = 0.001d;          // ~111.3 m na segment
    private static final int N = 101;                   // punkty 0..100
    private static final float SPEED = 14f;              // ~50 km/h

    private static Route route() {
        double[] lats = new double[N];
        double[] lons = new double[N];
        for (int i = 0; i < N; i++) {
            lats[i] = LAT0 + STEP * i;
            lons[i] = LON0;
        }
        Route.Step[] steps = new Route.Step[]{
                new Route.Step(0, Maneuvers.polish("depart", null, "Ulica Wyjazdowa"),
                        "Ulica Wyjazdowa", 300d, "depart", null),
                new Route.Step(50, Maneuvers.polish("turn", "right", "Marszałkowska"),
                        "Marszałkowska", 400d, "turn", "right"),
                new Route.Step(100, Maneuvers.polish("arrive", null, null),
                        "", 0d, "arrive", null),
        };
        return new Route(lats, lons, 11132d, 900L, steps);
    }

    private static Guidance.Update drive(Guidance g, int i) {
        return g.update(LAT0 + STEP * i, LON0, 0f, SPEED, 1000L + i * 1000L);
    }

    @Test
    public void totalM_zgadzaSieZDlugosciaTrasy() {
        Guidance g = new Guidance(route());
        // 100 segmentow po ~111.32 m
        assertEquals(11132d, g.totalM(), 30d);
    }

    @Test
    public void zapowiedzManewru_wchodzi400mPrzed() {
        Guidance g = new Guidance(route());
        Guidance.Update u = null;
        for (int i = 0; i <= 46; i++) {
            u = drive(g, i);
            assertEquals("przed zapowiedzia cisza (i=" + i + ")",
                    Guidance.PROMPT_NONE, u.prompt);
        }
        // i=47: do manewru ~334 m -> zapowiedz (prog 400 m przy 14 m/s)
        u = drive(g, 47);
        assertEquals(Guidance.PROMPT_FAR, u.prompt);
        assertTrue(u.instruction, u.instruction.contains("prawo"));
        assertTrue(u.instruction, u.instruction.contains("Marszałkowska"));
        // pozostala trasa do konca (segment 0.001 stopnia = ~111.195 m przy R=6371 km)
        assertEquals(53 * 111.195d, u.remainingM, 5d);
        assertTrue(u.etaS > 0d);
    }

    @Test
    public void przekroczonyManewr_mowiTeraz() {
        Guidance g = new Guidance(route());
        Guidance.Update u = null;
        for (int i = 0; i <= 50; i++) {
            u = drive(g, i);
        }
        // najechalismy na punkt manewru (50): "teraz", dystans 0
        assertEquals(Guidance.PROMPT_NOW, u.prompt);
        assertEquals(0d, u.distToManeuverM, 1e-6);
        assertTrue(u.instruction.contains("prawo"));
    }

    @Test
    public void dojazdDoCelu_raportujeArrived() {
        Guidance g = new Guidance(route());
        Guidance.Update u = null;
        for (int i = 0; i <= 100; i++) {
            u = drive(g, i);
        }
        assertEquals(Guidance.PROMPT_ARRIVED, u.prompt);
        assertTrue("na celu zostalo " + u.remainingM + " m", u.remainingM <= 40d);
    }

    @Test
    public void zjazdZTrasy_raportujeOffRoute() {
        Guidance g = new Guidance(route());
        for (int i = 0; i <= 30; i++) {
            drive(g, i);
        }
        // 400 m na wschod od trasy, jedziemy 14 m/s -> zjazd z trasy
        double lon = LON0 + 400d / (111320d * Math.cos(Math.toRadians(LAT0 + STEP * 30)));
        Guidance.Update u = g.update(LAT0 + STEP * 30, lon, 90f, SPEED, 31000L);
        assertEquals(Guidance.PROMPT_OFF_ROUTE, u.prompt);
    }

    @Test
    public void postojNieKrzyknieZjazdZTrasy() {
        Guidance g = new Guidance(route());
        for (int i = 0; i <= 30; i++) {
            drive(g, i);
        }
        // stoimy obok trasy (predkosc 0) - nie przeliczamy trasy z postoju
        double lon = LON0 + 400d / (111320d * Math.cos(Math.toRadians(LAT0 + STEP * 30)));
        Guidance.Update u = g.update(LAT0 + STEP * 30, lon, 90f, 0f, 31000L);
        assertTrue(u.prompt == Guidance.PROMPT_NONE || u.prompt == Guidance.PROMPT_FAR);
    }

    @Test
    public void distM_znaneOdleglosci() {
        // 1 stopien szerokosci ~111.2 km
        assertEquals(111195d, Guidance.distM(52.0, 21.0, 53.0, 21.0), 500d);
        // zero na tym samym punkcie
        assertEquals(0d, Guidance.distM(52.0, 21.0, 52.0, 21.0), 1e-9);
        // 0.001 stopnia ~111.3 m
        assertEquals(111.3d, Guidance.distM(52.0, 21.0, 52.001, 21.0), 1d);
    }

    @Test
    public void maxKmh_iLaneHint_przekazywaneDoUpdate() {
        // trasa z limitami: 50 na pierwszej polowie, 90 na drugiej
        double[] lats = new double[N];
        double[] lons = new double[N];
        for (int i = 0; i < N; i++) {
            lats[i] = LAT0 + STEP * i;
            lons[i] = LON0;
        }
        int[] maxKmh = new int[N - 1];
        for (int i = 0; i < N - 1; i++) maxKmh[i] = i < 50 ? 50 : 90;
        Route.Step[] steps = new Route.Step[]{
                new Route.Step(50, "Skręć w prawo na Marszałkowska",
                        "Marszałkowska", 400d, "turn", "right", "prawy pas"),
                new Route.Step(100, "Dojazd do celu", "", 0d, "arrive", null, ""),
        };
        Route r = new Route(lats, lons, 11132d, 900L, steps, maxKmh);
        Guidance g = new Guidance(r);

        // jedziemy PRZED manewrem: limit 50, podpowiedz pasow nadchodzacego manewru
        Guidance.Update u = drive(g, 30);
        assertEquals(50, u.maxKmh);
        assertEquals("prawy pas", u.laneHint);

        // druga polowa: limit 90, po manewrze brak podpowiedzi pasow
        u = drive(g, 80);
        assertEquals(90, u.maxKmh);
        assertEquals("", u.laneHint);
    }

    @Test
    public void maxKmh_brakDanychZwracaZero() {
        // trasa bez limitow (stary 5-arg konstruktor): maxKmh = 0
        Guidance g = new Guidance(route());
        Guidance.Update u = drive(g, 30);
        assertEquals(0, u.maxKmh);
        // laneHint pusty, gdy step bez podpowiedzi (6-arg konstruktor)
        assertEquals("", u.laneHint);
    }
}
