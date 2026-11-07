package pl.dock.widget.maps.elevation;

import pl.dock.widget.maps.route.Guidance;

/**
 * Profil wysokosci sladu/trasy: dystanse przyrojone po punktach, min/max,
 * suma podejsc. Czysta logika bez Androida (testy JVM: ElevationProfileTest).
 */
public final class ElevationProfile {

    public final double[] distM;
    public final double[] elevM;
    public final double minElevM;
    public final double maxElevM;
    public final double gainM;
    public final double totalM;

    private ElevationProfile(double[] distM, double[] elevM, double minElevM,
                             double maxElevM, double gainM, double totalM) {
        this.distM = distM;
        this.elevM = elevM;
        this.minElevM = minElevM;
        this.maxElevM = maxElevM;
        this.gainM = gainM;
        this.totalM = totalM;
    }

    /** Buduje profil z punktow i wysokosci; pary z NaN (ocean/brak danych) sa odrzucane. */
    public static ElevationProfile of(double[] lats, double[] lons, double[] elevs) {
        int n = Math.min(lats.length, Math.min(lons.length, elevs.length));
        double[] dist = new double[n];
        double[] el = new double[n];
        int c = 0;
        double cum = 0d;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double gain = 0d;
        double prevElev = Double.NaN;
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(elevs[i])) continue;
            if (c > 0) {
                cum += Guidance.distM(lats[i - 1], lons[i - 1], lats[i], lons[i]);
            }
            if (!Double.isNaN(prevElev) && elevs[i] > prevElev) {
                gain += elevs[i] - prevElev;
            }
            prevElev = elevs[i];
            dist[c] = cum;
            el[c] = elevs[i];
            if (elevs[i] < min) min = elevs[i];
            if (elevs[i] > max) max = elevs[i];
            c++;
        }
        if (c == 0) {
            return new ElevationProfile(new double[0], new double[0], 0d, 0d, 0d, 0d);
        }
        double[] dist2 = new double[c];
        double[] el2 = new double[c];
        for (int i = 0; i < c; i++) {
            dist2[i] = dist[i];
            el2[i] = el[i];
        }
        return new ElevationProfile(dist2, el2, min, max, gain, cum);
    }
}
