package pl.dock.widget.maps.route;

/**
 * Maszyna stanow prowadzenia po trasie: najblizszy punkt, dystans do manewru,
 * prompty glosowe, zjazd z trasy, dojazd do celu.
 *
 * Czysta logika bez Androida (testy JVM: GuidanceTest). Odleglosci liczmy
 * przyrozeniem equirectangular - dla okolic miasta wystarcza, a na MT8163
 * nie ma mowy o haversine per punkt na kazdym ticku.
 */
public final class Guidance {

    public static final int PROMPT_NONE = 0;
    public static final int PROMPT_FAR = 1;        // zapowiedz manewru
    public static final int PROMPT_NOW = 2;        // "teraz"
    public static final int PROMPT_ARRIVED = 3;    // cel osiagniety
    public static final int PROMPT_OFF_ROUTE = 4;  // zjazd z trasy -> przelicz

    /** Stan kazdego manewru: 0 = nie podany, 1 = podana zapowiedz, 2 = podane "teraz". */
    public static final class Update {
        public final double remainingM;
        public final double etaS;
        public final double distToManeuverM;
        public final String instruction;
        public final int prompt;

        Update(double remainingM, double etaS, double distToManeuverM,
               String instruction, int prompt) {
            this.remainingM = remainingM;
            this.etaS = etaS;
            this.distToManeuverM = distToManeuverM;
            this.instruction = instruction;
            this.prompt = prompt;
        }
    }

    private static final double OFF_ROUTE_M = 55d;
    private static final double ARRIVE_M = 40d;
    private static final double MIN_ETA_SPEED = 2.5d;   // m/s - ETA na postoju nie wyjdzie absurd

    private final Route route;
    private final double[] cum;      // cum[i] = dystans od punktu i do KONCA trasy [m]
    private final int[] stepState;   // per krok: 0/1/2 (co juz powiedziane)
    private int nearest = 0;
    private int prevNearest = 0;

    public Guidance(Route r) {
        route = r;
        int n = r.lats.length;
        cum = new double[n];
        cum[n - 1] = 0d;
        for (int i = n - 2; i >= 0; i--) {
            cum[i] = cum[i + 1]
                    + distM(r.lats[i], r.lons[i], r.lats[i + 1], r.lons[i + 1]);
        }
        stepState = new int[r.steps.length];
    }

    /** Calkowita dlugosc trasy [m]. */
    public double totalM() {
        return cum.length == 0 ? 0d : cum[0];
    }

    public Update update(double lat, double lon, float bearing, float speedMs, long nowMs) {
        int n = route.lats.length;

        // 1) najblizszy punkt w oknie (trasa sie nie "przewija" w nieskonczonosc)
        int from = Math.max(0, nearest - 8);
        int to = Math.min(n - 1, nearest + 60);
        double best = Double.MAX_VALUE;
        int bi = nearest;
        for (int i = from; i <= to; i++) {
            double d = distM(lat, lon, route.lats[i], route.lons[i]);
            if (d < best) {
                best = d;
                bi = i;
            }
        }
        prevNearest = nearest;
        nearest = bi;

        double remaining = cum[bi];
        double speed = speedMs < MIN_ETA_SPEED ? MIN_ETA_SPEED : (double) speedMs;
        double etaS = remaining / speed;

        // 2) najblizszy PRZYSZLY manewr (pierwszy krok za najblizszym punktem, bez "depart")
        int next = -1;
        for (int k = 0; k < route.steps.length; k++) {
            Route.Step st = route.steps[k];
            if (st.pointIndex > nearest && !"depart".equals(st.type)) {
                next = k;
                break;
            }
        }
        double distToMan = 0d;
        String instruction = "";
        if (next >= 0) {
            Route.Step st = route.steps[next];
            distToMan = remaining - cum[st.pointIndex];
            if (distToMan < 0d) distToMan = 0d;
            instruction = st.instruction;
        }

        // progi promptow zalezne od predkosci (szybciej = wczesniej)
        double farM = Math.min(1500d, Math.max(400d, (double) speedMs * 20d));
        double nowM = Math.min(250d, Math.max(60d, (double) speedMs * 4d));
        if (speedMs < 2f) {
            farM = 300d;
            nowM = 50d;
        }

        int prompt = PROMPT_NONE;

        // 3) dojazd / zjazd z trasy
        boolean arrived = remaining <= ARRIVE_M;
        if (arrived) {
            prompt = PROMPT_ARRIVED;
        } else if (best > OFF_ROUTE_M && speedMs > 1.5f && distToMan > 120d) {
            prompt = PROMPT_OFF_ROUTE;
        } else {
            // 4) przeskoczylismy manewr miedzy fixami -> "teraz" dla ostatniego przekrozonego
            for (int k = 0; k < route.steps.length; k++) {
                Route.Step st = route.steps[k];
                if ("depart".equals(st.type) || "arrive".equals(st.type)) continue;
                if (st.pointIndex > prevNearest && st.pointIndex <= nearest && stepState[k] < 2) {
                    stepState[k] = 2;
                    prompt = PROMPT_NOW;
                    instruction = st.instruction;
                    distToMan = 0d;
                }
            }
            // 5) normalne prompty dla nastepnego manewru
            if (prompt == PROMPT_NONE && next >= 0 && !"arrive".equals(route.steps[next].type)) {
                if (stepState[next] == 0 && distToMan <= farM) {
                    stepState[next] = 1;
                    prompt = PROMPT_FAR;
                }
                if (stepState[next] < 2 && distToMan <= nowM) {
                    stepState[next] = 2;
                    prompt = PROMPT_NOW;
                }
            }
        }

        return new Update(remaining, etaS, distToMan, instruction, prompt);
    }

    /** Odleglosc przyrozeniem equirectangular [m] - wspolna dla modulu route. */
    public static double distM(double lat1, double lon1, double lat2, double lon2) {
        double mlat = (lat1 + lat2) * 0.5d * Math.PI / 180d;
        double dx = (lon2 - lon1) * Math.PI / 180d * Math.cos(mlat) * 6371000d;
        double dy = (lat2 - lat1) * Math.PI / 180d * 6371000d;
        double d = Math.sqrt(dx * dx + dy * dy);
        return d;
    }
}
