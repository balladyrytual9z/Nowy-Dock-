package pl.dock.widget.maps.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Testy kodowania polyline (JVM, bez Androida).
 * Przyklad kanoniczny z dokumentacji algorytmu Google.
 */
public class PolylineCodecTest {

    /** (38.5, -120.2), (40.7, -120.95), (43.252, -126.453) */
    private static final String CANONICAL = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

    @Test
    public void encode_przykladZDokumentacjiGoogle() {
        List<double[]> pts = new ArrayList<double[]>();
        pts.add(new double[]{38.5d, -120.2d});
        pts.add(new double[]{40.7d, -120.95d});
        pts.add(new double[]{43.252d, -126.453d});
        assertEquals(CANONICAL, PolylineCodec.encode(pts, 5));
    }

    @Test
    public void decode_przykladZDokumentacjiGoogle() {
        List<double[]> pts = PolylineCodec.decode(CANONICAL, 5);
        assertEquals(3, pts.size());
        assertEquals(38.5d, pts.get(0)[0], 1e-5);
        assertEquals(-120.2d, pts.get(0)[1], 1e-5);
        assertEquals(40.7d, pts.get(1)[0], 1e-5);
        assertEquals(-120.95d, pts.get(1)[1], 1e-5);
        assertEquals(43.252d, pts.get(2)[0], 1e-5);
        assertEquals(-126.453d, pts.get(2)[1], 1e-5);
    }

    @Test
    public void roundtrip_punktyNaMokotwie() {
        List<double[]> pts = new ArrayList<double[]>();
        pts.add(new double[]{52.16d, 21.05d});
        pts.add(new double[]{52.17d, 21.06d});
        pts.add(new double[]{52.16123d, 21.04456d});
        pts.add(new double[]{52.15d, 21.0d});
        String enc = PolylineCodec.encode(pts, 5);
        List<double[]> back = PolylineCodec.decode(enc, 5);
        assertEquals(pts.size(), back.size());
        for (int i = 0; i < pts.size(); i++) {
            assertEquals(pts.get(i)[0], back.get(i)[0], 1e-5);
            assertEquals(pts.get(i)[1], back.get(i)[1], 1e-5);
        }
    }

    @Test
    public void decodeArrays_oddajeTeSamePunkty() {
        double[][] a = PolylineCodec.decodeArrays(CANONICAL, 5);
        assertEquals(3, a[0].length);
        assertEquals(3, a[1].length);
        assertEquals(43.252d, a[0][2], 1e-5);
        assertEquals(-126.453d, a[1][2], 1e-5);
    }

    @Test
    public void decode_nullIGarbageNieRzucaja() {
        assertEquals(0, PolylineCodec.decode(null, 5).size());
        assertEquals(0, PolylineCodec.decode("", 5).size());
        // pusty string to zero punktow, nie wyjatek
        assertTrue(PolylineCodec.decode("?????", 5).size() >= 0);
    }
}
