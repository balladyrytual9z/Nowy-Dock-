package pl.dock.widget.maps.bookmark;

import java.util.ArrayList;

import pl.dock.widget.maps.route.Guidance;

/**
 * Rejestrator sladu jazdy. Odrzuca "szum" GPS (fixy rozmieszczone po kilku
 * metrow na postoju) - ta sama filozofia co HeadingSmoother, tylko dla punktow.
 *
 * Czysta logika bez Androida (testy JVM: TrackRecorderTest).
 */
public final class TrackRecorder {

    private static final double MIN_STEP_M = 7d;
    private static final long MIN_STEP_MS = 3000L;

    private final ArrayList<double[]> pts = new ArrayList<double[]>();  // lat, lon, timeMs
    private double totalM;
    private boolean has;
    private double lastLat;
    private double lastLon;
    private long lastAt;

    /**
     * Dodaje fix. Zwraca true, gdy punkt zostal przyjety; false gdy odrzucony
     * jako szum (za blisko i za szybko po poprzednim).
     */
    public boolean add(double lat, double lon, long timeMs) {
        if (has) {
            double d = Guidance.distM(lastLat, lastLon, lat, lon);
            if (d < MIN_STEP_M && timeMs - lastAt < MIN_STEP_MS) {
                return false;
            }
            totalM += d;
        }
        has = true;
        lastLat = lat;
        lastLon = lon;
        lastAt = timeMs;
        pts.add(new double[]{lat, lon, (double) timeMs});
        return true;
    }

    public int size() {
        return pts.size();
    }

    /** Przebyty dystans [m] (liczony po przyjetych punktach). */
    public double totalM() {
        return totalM;
    }

    public double[] lats() {
        double[] out = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) out[i] = pts.get(i)[0];
        return out;
    }

    public double[] lons() {
        double[] out = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) out[i] = pts.get(i)[1];
        return out;
    }

    public long[] times() {
        long[] out = new long[pts.size()];
        for (int i = 0; i < pts.size(); i++) out[i] = (long) pts.get(i)[2];
        return out;
    }

    public void reset() {
        pts.clear();
        totalM = 0d;
        has = false;
        lastLat = 0d;
        lastLon = 0d;
        lastAt = 0L;
    }
}
