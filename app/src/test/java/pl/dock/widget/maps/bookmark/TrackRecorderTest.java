package pl.dock.widget.maps.bookmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Testy rejestratora sladu (JVM, bez Androida).
 * Szum GPS na postoju nie moze budowac "sladu".
 */
public class TrackRecorderTest {

    private static final double M_LAT = 7d / 111320d;   // 7 m w szerokosci

    @Test
    public void pierwszyFixJestPrzyjety() {
        TrackRecorder r = new TrackRecorder();
        assertTrue(r.add(52.0d, 21.0d, 1000L));
        assertEquals(1, r.size());
        assertEquals(0d, r.totalM(), 1e-9);
    }

    @Test
    public void szumNaStojakoNieLiczysie() {
        TrackRecorder r = new TrackRecorder();
        assertTrue(r.add(52.0d, 21.0d, 1000L));
        // 2 m obok po sekundzie: za blisko i za szybko -> odrzucone
        assertFalse(r.add(52.0d + M_LAT * 2d / 7d, 21.0d, 2000L));
        assertEquals(1, r.size());
        // drugi taki sam szum tez odpada
        assertFalse(r.add(52.0d - M_LAT * 0.4d, 21.0d, 3000L));
        assertEquals(1, r.size());
    }

    @Test
    public void prawdziwyRuchJestRejestrowany() {
        TrackRecorder r = new TrackRecorder();
        assertTrue(r.add(52.0d, 21.0d, 1000L));
        assertFalse(r.add(52.0d + M_LAT * 0.3d, 21.0d, 2000L));  // szum
        // 10 m dalej po 4 s od ostatniego PRZYJETEGO punktu -> OK
        assertTrue(r.add(52.0d + M_LAT * 10d / 7d, 21.0d, 5000L));
        assertEquals(2, r.size());
        assertEquals(10d, r.totalM(), 0.5d);
        // kolejne 8 m po 3 s -> OK
        assertTrue(r.add(52.0d + M_LAT * 18d / 7d, 21.0d, 8000L));
        assertEquals(3, r.size());
        assertEquals(18d, r.totalM(), 1d);
    }

    @Test
    public void tabliceOddajaZapisanePunkty() {
        TrackRecorder r = new TrackRecorder();
        r.add(52.0d, 21.0d, 1000L);
        r.add(52.0d + M_LAT, 21.0d, 5000L);
        double[] lats = r.lats();
        double[] lons = r.lons();
        long[] times = r.times();
        assertEquals(2, lats.length);
        assertEquals(2, lons.length);
        assertEquals(2, times.length);
        assertEquals(52.0d, lats[0], 1e-9);
        assertEquals(52.0d + M_LAT, lats[1], 1e-9);
        assertEquals(21.0d, lons[0], 1e-9);
        assertEquals(1000L, times[0]);
        assertEquals(5000L, times[1]);
    }

    @Test
    public void resetCzysciSlad() {
        TrackRecorder r = new TrackRecorder();
        r.add(52.0d, 21.0d, 1000L);
        r.add(52.0d + M_LAT, 21.0d, 5000L);
        r.reset();
        assertEquals(0, r.size());
        assertEquals(0d, r.totalM(), 1e-9);
        assertEquals(0, r.lats().length);
        // po resetcie da sie nagrywa od nowa
        assertTrue(r.add(52.1d, 21.1d, 9000L));
        assertEquals(1, r.size());
    }
}
