package pl.dock.widget.maps.route;

import java.util.ArrayList;
import java.util.List;

/**
 * Kodowanie/dekodowanie "encoded polyline" (algorytm Google/OSRM).
 * Czysta logika bez Androida - testowalna na JVM (gradle test).
 */
public final class PolylineCodec {

    private PolylineCodec() {
    }

    /**
     * Dekoduje polyline do par [lat, lon] (kolejnosc jak w OSRM).
     *
     * @param precision 5 dla standardowego polyline OSRM ("geometries=polyline")
     */
    public static List<double[]> decode(String encoded, int precision) {
        List<double[]> out = new ArrayList<double[]>();
        if (encoded == null) return out;
        double factor = Math.pow(10d, (double) precision);
        int idx = 0;
        int len = encoded.length();
        int lat = 0;
        int lon = 0;
        while (idx < len) {
            int[] r1 = readVarint(encoded, idx);
            lat += r1[0];
            idx = r1[1];
            if (idx > len) break;
            int[] r2 = readVarint(encoded, idx);
            lon += r2[0];
            idx = r2[1];
            out.add(new double[]{lat / factor, lon / factor});
        }
        return out;
    }

    /** Dekoduje polyline do tablic lat[] i lon[] (jedna alokacja, mniej GC). */
    public static double[][] decodeArrays(String encoded, int precision) {
        List<double[]> pts = decode(encoded, precision);
        double[] lats = new double[pts.size()];
        double[] lons = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            lats[i] = pts.get(i)[0];
            lons[i] = pts.get(i)[1];
        }
        return new double[][]{lats, lons};
    }

    /** Koduje punkty [lat, lon] do postaci polyline (precision 5). */
    public static String encode(List<double[]> points, int precision) {
        StringBuilder sb = new StringBuilder();
        double factor = Math.pow(10d, (double) precision);
        long prevLat = 0L;
        long prevLon = 0L;
        for (int i = 0; i < points.size(); i++) {
            long la = Math.round(points.get(i)[0] * factor);
            long lo = Math.round(points.get(i)[1] * factor);
            writeVarint(sb, (int) (la - prevLat));
            writeVarint(sb, (int) (lo - prevLon));
            prevLat = la;
            prevLon = lo;
        }
        return sb.toString();
    }

    private static int[] readVarint(String s, int idx) {
        int shift = 0;
        int result = 0;
        int b;
        int len = s.length();
        do {
            if (idx >= len) return new int[]{result, idx};
            b = s.charAt(idx) - 63;
            idx++;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        boolean neg = (result & 1) == 1;
        result >>>= 1;
        if (neg) result = ~result;
        return new int[]{result, idx};
    }

    private static void writeVarint(StringBuilder sb, int v) {
        int vv = v < 0 ? ~(v << 1) : (v << 1);
        while (vv >= 0x20) {
            sb.append((char) ((0x20 | (vv & 0x1f)) + 63));
            vv >>>= 5;
        }
        sb.append((char) (vv + 63));
    }
}
