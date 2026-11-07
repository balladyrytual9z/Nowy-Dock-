package pl.dock.widget.maps.elevation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Testy profilu wysokosci (JVM, bez Androida).
 * Punkty z NaN (brak danych pokrycia) sa odrzucane.
 */
public class ElevationProfileTest {

    private static final double STEP = 0.001d;   // ~111.3 m na punkt

    @Test
    public void profilZBudowanyZPunktow() {
        // 4 punkty, pierwszy bez danych wysokosciowych
        double[] lats = {52.0d, 52.0d + STEP, 52.0d + 2 * STEP, 52.0d + 3 * STEP};
        double[] lons = {21.0d, 21.0d, 21.0d, 21.0d};
        double[] elevs = {Double.NaN, 100d, 120d, 110d};
        ElevationProfile p = ElevationProfile.of(lats, lons, elevs);
        assertEquals(3, p.distM.length);
        assertEquals(3, p.elevM.length);
        assertEquals(100d, p.minElevM, 1e-9);
        assertEquals(120d, p.maxElevM, 1e-9);
        // podjazd tylko w gore: 100->120 (+20), 120->110 liczy sie 0
        assertEquals(20d, p.gainM, 1e-9);
        // dystans rosnie: 0, ~111, ~223
        assertEquals(0d, p.distM[0], 1e-9);
        assertEquals(111.3d, p.distM[1], 2d);
        assertEquals(222.7d, p.distM[2], 3d);
        assertEquals(222.7d, p.totalM, 3d);
    }

    @Test
    public void brakWszystkichDanych_dajePustyProfil() {
        double[] lats = {52.0d, 52.1d};
        double[] lons = {21.0d, 21.1d};
        double[] elevs = {Double.NaN, Double.NaN};
        ElevationProfile p = ElevationProfile.of(lats, lons, elevs);
        assertEquals(0, p.distM.length);
        assertEquals(0, p.elevM.length);
        assertEquals(0d, p.totalM, 1e-9);
    }

    @Test
    public void rozneDlugosciTablicNieRzucaja() {
        double[] lats = {52.0d, 52.001d, 52.002d};
        double[] lons = {21.0d, 21.0d};
        double[] elevs = {10d};
        ElevationProfile p = ElevationProfile.of(lats, lons, elevs);
        assertEquals(1, p.distM.length);
        assertEquals(10d, p.maxElevM, 1e-9);
        assertEquals(0d, p.gainM, 1e-9);
    }

    @Test
    public void samaGora_dajeGainRownyRoznicy() {
        double[] lats = {52.0d, 52.001d, 52.002d};
        double[] lons = {21.0d, 21.0d, 21.0d};
        double[] elevs = {100d, 150d, 220d};
        ElevationProfile p = ElevationProfile.of(lats, lons, elevs);
        assertEquals(120d, p.gainM, 1e-9);
        assertEquals(100d, p.minElevM, 1e-9);
        assertEquals(220d, p.maxElevM, 1e-9);
        assertTrue(p.totalM > 200d);
    }
}
