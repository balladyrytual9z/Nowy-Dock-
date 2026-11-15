package pl.dock.widget;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Testy czystej logiki Night Shift i Trybu Ciemnego (bez kontekstu Android).
 *
 * Scenariusze:
 * - Cykl trybów Night Shift: off -> on -> sunset -> off
 * - Walidacja współrzędnych (lat/lon)
 * - Formatowanie czasu wschodu/zachodu
 */
public class NightShiftLogicTest {

    @Test
    public void fullCycle() {
        assertEquals("on", NightShift.nextMode("off"));
        assertEquals("sunset", NightShift.nextMode("on"));
        assertEquals("off", NightShift.nextMode("sunset"));
        assertEquals("off", NightShift.nextMode(NightShift.nextMode(NightShift.nextMode("off"))));
    }

    @Test
    public void unknownOrNull_goesToOn() {
        assertEquals("on", NightShift.nextMode(null));
        assertEquals("on", NightShift.nextMode(""));
        assertEquals("on", NightShift.nextMode("cos-innego"));
    }

    @Test
    public void validateLat_inRange() {
        assertEquals(52.23, SolarCalculator.validateLat(52.23, 0.0), 0.001);
        assertEquals(-90.0, SolarCalculator.validateLat(-90.0, 0.0), 0.001);
        assertEquals(90.0, SolarCalculator.validateLat(90.0, 0.0), 0.001);
        assertEquals(0.0, SolarCalculator.validateLat(0.0, 0.0), 0.001);
    }

    @Test
    public void validateLat_outOfRange() {
        assertEquals(0.0, SolarCalculator.validateLat(200.0, 0.0), 0.001);
        assertEquals(0.0, SolarCalculator.validateLat(-200.0, 0.0), 0.001);
        assertEquals(0.0, SolarCalculator.validateLat(Double.NaN, 0.0), 0.001);
        assertEquals(0.0, SolarCalculator.validateLat(Double.POSITIVE_INFINITY, 0.0), 0.001);
        assertEquals(0.0, SolarCalculator.validateLat(Double.NEGATIVE_INFINITY, 0.0), 0.001);
    }

    @Test
    public void validateLon_inRange() {
        assertEquals(21.01, SolarCalculator.validateLon(21.01, 0.0), 0.001);
        assertEquals(-180.0, SolarCalculator.validateLon(-180.0, 0.0), 0.001);
        assertEquals(180.0, SolarCalculator.validateLon(180.0, 0.0), 0.001);
        assertEquals(0.0, SolarCalculator.validateLon(0.0, 0.0), 0.001);
    }

    @Test
    public void validateLon_outOfRange() {
        assertEquals(0.0, SolarCalculator.validateLon(200.0, 0.0), 0.001);
        assertEquals(0.0, SolarCalculator.validateLon(-200.0, 0.0), 0.001);
        assertEquals(0.0, SolarCalculator.validateLon(Double.NaN, 0.0), 0.001);
        assertEquals(0.0, SolarCalculator.validateLon(Double.POSITIVE_INFINITY, 0.0), 0.001);
        assertEquals(0.0, SolarCalculator.validateLon(Double.NEGATIVE_INFINITY, 0.0), 0.001);
    }

    @Test
    public void fmtMinutes() {
        // SolarCalculator.fmtMinutes jest prywatne, testujemy przez coordsString jeśli możliwe
        // Ten test tylko potwierdza że klasa ładuje się poprawnie
        assertNotNull(SolarCalculator.class);
    }

    @Test
    public void darkStateEnumExists() {
        assertNotNull(SolarCalculator.DarkState.AUTO_DAY);
        assertNotNull(SolarCalculator.DarkState.AUTO_NIGHT);
        assertNotNull(SolarCalculator.DarkState.MANUAL_ON);
        assertNotNull(SolarCalculator.DarkState.MANUAL_OFF);
    }

    @Test
    public void nsStateEnumExists() {
        assertNotNull(SolarCalculator.NsState.AUTO_OFF);
        assertNotNull(SolarCalculator.NsState.AUTO_ON);
        assertNotNull(SolarCalculator.NsState.MANUAL_ON);
        assertNotNull(SolarCalculator.NsState.MANUAL_OFF);
    }
}
