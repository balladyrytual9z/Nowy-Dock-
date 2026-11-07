package pl.dock.widget.maps.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Testy polskich opisow manewrow i formatow dystansu/czasu (JVM).
 */
public class ManeuversTest {

    @Test
    public void turn_rightZNazwaUlicy() {
        assertEquals("Skręć w prawo na Marszałkowska",
                Maneuvers.polish("turn", "right", "Marszałkowska"));
    }

    @Test
    public void turn_leftBezUlicy() {
        assertEquals("Skręć w lewo", Maneuvers.polish("turn", "left", null));
        assertEquals("Skręć w lewo", Maneuvers.polish("turn", "left", "  "));
    }

    @Test
    public void departIArrive() {
        assertEquals("Wyjazd na Ulica Wyjazdowa",
                Maneuvers.polish("depart", null, "Ulica Wyjazdowa"));
        assertEquals("Dojazd do celu",
                Maneuvers.polish("arrive", null, null));
    }

    @Test
    public void uturnIRozwidlenia() {
        assertEquals("Zawróć na Aleje Jerozolimskie",
                Maneuvers.polish("turn", "uturn", "Aleje Jerozolimskie"));
        assertEquals("Na rozwidleniu trzymaj się lekko w prawo",
                Maneuvers.polish("fork", "slight right", null));
        assertEquals("Na końcu drogi skręć w prawo",
                Maneuvers.polish("end of road", "right", null));
        assertEquals("Na rondzie skręć w lewo",
                Maneuvers.polish("roundabout", "left", null));
        assertEquals("Zjedź z ronda",
                Maneuvers.polish("exit roundabout", null, null));
    }

    @Test
    public void nieznanyManewrNieRzuca() {
        assertTrue(Maneuvers.polish(null, null, null).length() > 0);
        assertTrue(Maneuvers.polish("bzdura", "bzdura", "X").length() > 0);
    }

    @Test
    public void distText_metryIKilometry() {
        assertEquals("300 m", Maneuvers.distText(300d));
        assertEquals("0 m", Maneuvers.distText(0d));
        assertEquals("999 m", Maneuvers.distText(999.4d));
        assertEquals("1,0 km", Maneuvers.distText(1000d));
        assertEquals("1,2 km", Maneuvers.distText(1234d));
        assertEquals("12,5 km", Maneuvers.distText(12450d));
        assertEquals("?", Maneuvers.distText(-1d));
    }

    @Test
    public void minText_zaokraglaWGore() {
        assertEquals("1 min", Maneuvers.minText(30d));
        assertEquals("1 min", Maneuvers.minText(60d));
        assertEquals("4 min", Maneuvers.minText(240d));
        assertEquals("4 min", Maneuvers.minText(210d));
        assertEquals("17 min", Maneuvers.minText(1000d));
    }
}
