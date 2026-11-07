package pl.dock.widget.maps.bookmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Testy zapisu/odczytu GPX i KML (JVM, bez Androida).
 * Roundtrip: zapis -> odczyt ma oddac te same punkty.
 */
public class GpxIoTest {

    @Test
    public void zakladki_gpxRoundtrip() {
        List<Bookmark> in = new ArrayList<Bookmark>();
        in.add(new Bookmark(1L, "Dom", 52.160000d, 21.050000d, 1000L));
        in.add(new Bookmark(2L, "Praca \"Biuro\"", 51.910000d, 20.700000d, 2000L));
        String gpx = GpxIo.writeBookmarks(in);
        List<Bookmark> out = GpxIo.parseWaypoints(gpx);
        assertEquals(2, out.size());
        assertEquals("Dom", out.get(0).name);
        assertEquals(52.16d, out.get(0).lat, 1e-6);
        assertEquals(21.05d, out.get(0).lon, 1e-6);
        assertEquals("Praca \"Biuro\"", out.get(1).name);
        assertEquals(51.91d, out.get(1).lat, 1e-6);
        assertEquals(20.7d, out.get(1).lon, 1e-6);
    }

    @Test
    public void zakladki_kmlRoundtrip() {
        List<Bookmark> in = new ArrayList<Bookmark>();
        in.add(new Bookmark(1L, "Stacja <Paliw>", 52.5d, 19.5d, 0L));
        String kml = GpxIo.writeBookmarksKml(in);
        // KML zapisuje lon,lat - odczyt ma to odkrecic
        List<Bookmark> out = GpxIo.parseKmlWaypoints(kml);
        assertEquals(1, out.size());
        assertEquals("Stacja <Paliw>", out.get(0).name);
        assertEquals(52.5d, out.get(0).lat, 1e-6);
        assertEquals(19.5d, out.get(0).lon, 1e-6);
    }

    @Test
    public void slad_gpxRoundtrip() {
        double[] lats = {52.0d, 52.001d, 52.002d};
        double[] lons = {21.0d, 21.001d, 21.0025d};
        long[] times = {1000L, 2000L, 3000L};
        String gpx = GpxIo.writeTrack("slad-testowy", lats, lons, times);
        List<double[]> back = GpxIo.parseTrack(gpx);
        assertEquals(3, back.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(lats[i], back.get(i)[0], 1e-6);
            assertEquals(lons[i], back.get(i)[1], 1e-6);
        }
        assertTrue(gpx.contains("slad-testowy"));
        assertTrue("czasy w UTC zgodnie z GPX", gpx.contains("<time>"));
    }

    @Test
    public void slad_bezCzasowTezSieZapisuje() {
        double[] lats = {52.0d, 52.01d};
        double[] lons = {21.0d, 21.01d};
        String gpx = GpxIo.writeTrack("bez-czasow", lats, lons, null);
        List<double[]> back = GpxIo.parseTrack(gpx);
        assertEquals(2, back.size());
        assertEquals(52.01d, back.get(1)[0], 1e-6);
    }

    @Test
    public void parsowanie_obcychPlikowNieRzuca() {
        assertEquals(0, GpxIo.parseWaypoints(null).size());
        assertEquals(0, GpxIo.parseWaypoints("to nie jest gpx").size());
        assertEquals(0, GpxIo.parseTrack("").size());
        assertEquals(0, GpxIo.parseKmlWaypoints("<kml/>").size());
        // samozamykajace wpt (bez nazwy)
        List<Bookmark> w = GpxIo.parseWaypoints(
                "<gpx><wpt lat=\"52.1\" lon=\"21.2\"/></gpx>");
        assertEquals(1, w.size());
        assertEquals(52.1d, w.get(0).lat, 1e-6);
        assertEquals("Punkt", w.get(0).name);
    }
}
