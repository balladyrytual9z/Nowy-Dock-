package pl.dock.widget.maps.route;

/**
 * Trasa z silnika routingu (OSRM): punkty, kroki (manewry) i statystyki.
 * Czysta logika bez Androida - Guidance i testy JVM zyja na tej klasie.
 */
public final class Route {

    /** Krok/manewr na trasie. pointIndex wskazuje punkt w lats/lons,
     *  w ktorym manewr sie wykonuje. */
    public static final class Step {
        public final int pointIndex;
        public final String instruction;
        public final String road;
        public final double distanceM;
        public final String type;       // "turn", "roundabout", "arrive", ...
        public final String modifier;   // "left", "right", "uturn", ...
        public final String laneHint;   // podpowiedz pasow ruchu ("" gdy brak)

        public Step(int pointIndex, String instruction, String road,
                    double distanceM, String type, String modifier) {
            this(pointIndex, instruction, road, distanceM, type, modifier, "");
        }

        public Step(int pointIndex, String instruction, String road,
                    double distanceM, String type, String modifier, String laneHint) {
            this.pointIndex = pointIndex;
            this.instruction = instruction;
            this.road = road;
            this.distanceM = distanceM;
            this.type = type;
            this.modifier = modifier;
            this.laneHint = laneHint == null ? "" : laneHint;
        }
    }

    public final double[] lats;
    public final double[] lons;
    public final double distanceM;
    public final long durationS;
    public final Step[] steps;
    /** Ograniczenia predkosci [km/h] per SEGMENT (lats.length-1); 0/brak = nieznane. */
    public final int[] maxKmh;

    public Route(double[] lats, double[] lons, double distanceM, long durationS, Step[] steps) {
        this(lats, lons, distanceM, durationS, steps, null);
    }

    public Route(double[] lats, double[] lons, double distanceM, long durationS,
                 Step[] steps, int[] maxKmh) {
        this.lats = lats;
        this.lons = lons;
        this.distanceM = distanceM;
        this.durationS = durationS;
        this.steps = steps == null ? new Step[0] : steps;
        this.maxKmh = maxKmh;
    }

    public int pointCount() {
        return lats == null ? 0 : lats.length;
    }
}
