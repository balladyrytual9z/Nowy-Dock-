package pl.dock.widget.maps;

/**
 * Wygladzanie pozycji i obrotu mapy - czysta logika, bez Androida (testowalna na JVM).
 *
 * GPS w tym urzadzeniu daje fix raz na sekunde (MapState: 1000 ms / 1 m), a znacznik
 * i obrot mapy byly ustawiane dokladnie na ostatnim fixie. Efekt: strzalka skakala
 * co sekunde, a mapa obracala sie "skokowo" (animacja 250 ms na fix, potem bezruch).
 *
 * Tutaj ruch jest CIAGLY: miedzy poprzednim a nowym fixem pozycja i kierunek ida
 * liniowo, a czas przejscia jest rowny odstepowi miedzy fixami (300-1500 ms).
 * Nowy fix re-bazuje poczatek ruchu na biezaca, wygladzona wartosc, wiec nie ma
 * szarpnieca. Obrot idzie zawsze NAJKROTSZA droga (przez 0/360), a ponizej
 * {@link #NORTH_UP_SPEED_MS} mapa wyrownuje sie do polnocy.
 *
 * POSTOJ (2026-11-05): na postoju GPS "szumi" - kolejne fixy wypadaja o kilka
 * metrow od siebie w losowych kierunkach, a kurs jest czystym szumem. Wtedy
 * znacznik chodzil bez sensu (i kazdy taki fix przesuwal go o kilka metrow), a
 * mapa krecila sie od losowego kursu. Dlatego fixy sa teraz wazone ZAUFANIEM:
 *
 *  - predkosc >= {@link #NORTH_UP_SPEED_MS} (10 km/h): zaufanie 1 - jedziemy,
 *    pozycja i kurs ida dokladnie za fixem (jak dotad),
 *  - predkosc {@link #STILL_SPEED_MS}..{@link #NORTH_UP_SPEED_MS}: zaufanie
 *    proporcjonalne do predkosci (min. {@link #CREEP_TRUST}) - szum jest tlumiony,
 *    ale wolny ruch nadal jest sledzony,
 *  - predkosc < {@link #STILL_SPEED_MS} (3.6 km/h): postoj. Dopoki fix jest blizej
 *    niz {@link #STILL_RADIUS_M} (albo niz dokladnosc GPS), znacznik NIE rusza sie
 *    w ogole, predkosc na liczniku to 0, a kurs jest trzymany (zero krecenia mapa).
 *    Jesli fix wypadnie dalej (np. GPS po zimnym starcie sie poprawil), dochodzimy
 *    do niego po {@link #CREEP_TRUST} na fix - bez skoku.
 *
 * Fix gorszy niz {@link #MAX_USABLE_ACCURACY_M} jest odrzucany w calosci.
 */
public final class HeadingSmoother {

    /** 10 km/h. Ponizej tego GPS podaje kierunek z szumu, wiec mapa wraca do polnocy. */
    public static final float NORTH_UP_SPEED_MS = 2.78f;

    /** Ponizej tej predkosci nie ma kursu - rysujemy ikone osoby, nie strzalke. */
    private static final float MIN_MOVE_SPEED_MS = 0.5f;

    // ---- postoj: nie ruszamy znacznika, gdy GPS szumi w miejscu ----
    /** Ponizej 3.6 km/h uznajemy, ze stoimy - to szum, nie jazda. */
    private static final float STILL_SPEED_MS = 1.0f;
    /** Promien "stania w miejscu" [m]. GPS na postoju chodzi o kilka metrow. */
    private static final float STILL_RADIUS_M = 12f;
    /** Krok dochodzenia do fix'a poza promieniem szumu (na fix) - bez skoku. */
    private static final float CREEP_TRUST = 0.25f;
    /** Fix z gorsza dokladnoscia niz tyle metrow jest bezuzyteczny. */
    private static final float MAX_USABLE_ACCURACY_M = 100f;

    /** Ponizej takiego ruchu/obrotu nie ma po co przerysowywac klatki (postoj = zero pracy). */
    private static final double MIN_MOVE_DEG = 1e-7;
    private static final float MIN_TURN_DEG = 0.02f;

    private static final long MIN_DUR_MS = 300L;
    private static final long MAX_DUR_MS = 1500L;
    private static final long DEFAULT_DUR_MS = 1000L;
    /** Po tylu ms bez fix'a pozycja jest nieaktualna - petla animacji moze odpoczac. */
    private static final long STALE_MS = 5000L;

    private boolean hasFix;
    private long lastFixAt;
    private long t0;
    private long durMs = DEFAULT_DUR_MS;

    // przejscie (from -> to) w czasie [t0, t0 + durMs]
    private double fromLat, fromLon, toLat, toLon;
    private float fromBearing, toBearing;

    // stan biezacy (wygladzony) - to wystawiamy na zewnatrz
    private double lat, lon;
    private float bearing;
    private float speedMs;
    private float accuracy;
    private boolean dirty;

    /** Zeruje stan (np. po utracie fix'a). */
    public void reset() {
        hasFix = false;
        lastFixAt = 0L;
        t0 = 0L;
        durMs = DEFAULT_DUR_MS;
        fromLat = 0d;
        fromLon = 0d;
        toLat = 0d;
        toLon = 0d;
        fromBearing = 0f;
        toBearing = 0f;
        lat = 0d;
        lon = 0d;
        bearing = 0f;
        speedMs = 0f;
        accuracy = 0f;
        dirty = true;
    }

    /**
     * Nowy fix z GPS. {@code nowMs} musi byc mierzone tym samym zegarem co w
     * {@link #advance(long)} (w aplikacji: SystemClock.uptimeMillis()).
     */
    public void onFix(double fixLat, double fixLon, float fixBearing, float fixSpeedMs,
                      float fixAccuracy, long nowMs) {
        // Kurs bierzemy tylko z realnej jazdy. Prog jest tu 10 km/h, a nie 1.8 km/h:
        // przy wolnej jazdzie i na postoju GPS podaje kierunek z szumu, a wtedy mapa
        // kreci sie na boki (to bylo widac na urzadzeniu przy zmianie predkosci
        // 0.5 -> 0.6 m/s: raz kurs, raz polnoc).
        float target = fixSpeedMs >= NORTH_UP_SPEED_MS ? normalize(fixBearing) : 0f;
        if (!hasFix) {
            // pierwszy fix - nie ma z czego interpolowac, ustawiamy od razu
            hasFix = true;
            lat = fixLat;
            lon = fixLon;
            bearing = target;
            fromLat = fixLat;
            fromLon = fixLon;
            toLat = fixLat;
            toLon = fixLon;
            fromBearing = target;
            toBearing = target;
            t0 = nowMs;
            durMs = DEFAULT_DUR_MS;
            lastFixAt = nowMs;
            speedMs = fixSpeedMs >= STILL_SPEED_MS ? fixSpeedMs : 0f;
            accuracy = fixAccuracy;
            dirty = true;
            return;
        }
        if (fixAccuracy > MAX_USABLE_ACCURACY_M) {
            // Fix bezuzyteczny (np. zimny start) - nie psujemy nim pozycji, ale
            // odnotowujemy, ze GPS zyje (inaczej pozycja uznana za nieaktualna).
            lastFixAt = nowMs;
            return;
        }
        // Ile z tego fix'a bierzemy na wiare (0 = stoimy, 1 = jedziemy).
        float trust = trustFor(fixLat, fixLon, fixSpeedMs, fixAccuracy);

        long gap = nowMs - lastFixAt;
        durMs = gap < MIN_DUR_MS ? MIN_DUR_MS : (gap > MAX_DUR_MS ? MAX_DUR_MS : gap);
        lastFixAt = nowMs;
        t0 = nowMs;
        // start z biezacej (wygladzonej) wartosci - nowy fix nie robi skoku
        fromLat = lat;
        fromLon = lon;
        // Cel to punkt o "trust" blizej fix'a. Przy postoju (trust = 0) celem jest
        // biezaca pozycja, czyli znacznik stoi w miejscu mimo szumu GPS.
        toLat = lat + (fixLat - lat) * trust;
        toLon = lon + (fixLon - lon) * trust;
        speedMs = trust <= 0f ? 0f : fixSpeedMs;
        if (trust > 0f) accuracy = fixAccuracy;
        fromBearing = bearing;
        toBearing = bearing + shortestDelta(target, bearing);
        dirty = true;
    }

    /**
     * Ile zaufania dac temu fix'owi: 0 = postoj (nie ruszamy znacznika),
     * 1 = pelna jazda (pozycja idzie dokladnie za fixem).
     */
    private float trustFor(double fixLat, double fixLon, float fixSpeedMs, float fixAccuracy) {
        if (fixSpeedMs >= NORTH_UP_SPEED_MS) return 1f;                  // jedziemy
        if (fixSpeedMs >= STILL_SPEED_MS) {                              // wolno, ale w ruchu
            return Math.max(CREEP_TRUST, fixSpeedMs / NORTH_UP_SPEED_MS);
        }
        // Postoj: promien szumu rosnie z (nie)dokladnoscia GPS.
        double dist = metersBetween(lat, lon, fixLat, fixLon);
        float radius = Math.max(STILL_RADIUS_M, fixAccuracy);
        return dist <= radius ? 0f : CREEP_TRUST;
    }

    /** Odleglosc w metrach (haversine). */
    static double metersBetween(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371000d;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2d) * Math.sin(dp / 2d)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2d) * Math.sin(dl / 2d);
        return r * 2d * Math.asin(Math.min(1d, Math.sqrt(a)));
    }

    /** Przelicza stan na chwile {@code nowMs}. Wolane w kazdej klatce animacji. */
    public void advance(long nowMs) {
        if (!hasFix) return;
        float f = 1f;
        if (durMs > 0L) {
            long dt = nowMs - t0;
            f = dt <= 0L ? 0f : (dt >= durMs ? 1f : (float) dt / (float) durMs);
        }
        double nLat = fromLat + (toLat - fromLat) * f;
        double nLon = fromLon + (toLon - fromLon) * f;
        float nBearing = fromBearing + (toBearing - fromBearing) * f;
        dirty = Math.abs(nLat - lat) > MIN_MOVE_DEG
                || Math.abs(nLon - lon) > MIN_MOVE_DEG
                || Math.abs(shortestDelta(nBearing, bearing)) > MIN_TURN_DEG;
        lat = nLat;
        lon = nLon;
        bearing = normalize(nBearing);
    }

    /**
     * Czy trwa ruch. Petla rysuje czesto (~30 fps) tylko wtedy, gdy jest co pokazywac;
     * na postoju wystarcza rzadkie klatki (MT8163 / Mali-T720 - malo animacji).
     */
    public boolean moving(long nowMs) {
        if (!hasFix) return false;
        if (nowMs - lastFixAt > STALE_MS) return false;
        if (nowMs - t0 < durMs) return true;          // trwa przejscie do nowego fix'a
        return speedMs >= MIN_MOVE_SPEED_MS;          // jedziemy - czekamy na kolejny fix
    }

    /** Czy od ostatniego {@link #advance(long)} cokolwiek sie zmienilo. */
    public boolean dirty() {
        return dirty;
    }

    public boolean hasFix() {
        return hasFix;
    }

    public double lat() {
        return lat;
    }

    public double lon() {
        return lon;
    }

    /** Wygladzony kurs jazdy (0 = polnoc). Tym obracamy strzalke i kompas. */
    public float markerBearing() {
        return bearing;
    }

    /**
     * Orientacja mapy dla MapView.setMapOrientation: heading-up = kierunek jazdy
     * na gorze, czyli obrot przeciwny do kursu (ustalone na urzadzeniu w PR #26).
     */
    public float mapOrientation() {
        return normalize(-bearing);
    }

    /** Wygladzona predkosc (m/s) - do wskaznika predkosci. */
    public float speedMs() {
        return speedMs;
    }

    /** Dokladnosc ostatniego fix'a (m) - do kola dokladnosci. */
    public float accuracy() {
        return accuracy;
    }

    /**
     * Czy jedziemy - decyduje, czy rysowac strzalke (a nie ikone osoby).
     * Na postoju speedMs jest zerowane, wiec nie ma migania strzalka/osoba.
     */
    public boolean isMoving() {
        return speedMs >= MIN_MOVE_SPEED_MS;
    }

    /** Roznica katow w zakresie (-180, 180] - zawsze najkrotsza droga. */
    public static float shortestDelta(float target, float current) {
        float dlt = normalize(target) - normalize(current);
        while (dlt > 180f) dlt -= 360f;
        while (dlt <= -180f) dlt += 360f;
        return dlt;
    }

    /** Normalizacja kata do [0, 360). */
    public static float normalize(float deg) {
        float v = deg % 360f;
        if (v < 0f) v += 360f;
        return v;
    }
}
