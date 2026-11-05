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

    /** Szybciej niz to nie jezdzimy [m/s] (216 km/h) - "szybszy" fix to blad GPS. */
    private static final float MAX_PLAUSIBLE_SPEED_MS = 60f;

    /** Zapas na niepewnosc GPS [m] - tyle wolno odjechac bez potwierdzenia. */
    private static final float JUMP_SLACK_M = 30f;

    /** Ile razy z rzedu GPS musi pokazac to samo miejsce, by uznac skok za prawdziwy. */
    private static final int JUMP_CONFIRM = 3;

    /** Na postoju wymagamy wiekszego dowodu - szum nie moze nas przesuwac. */
    private static final int JUMP_CONFIRM_STILL = 5;

    /** Dwa fixy to "to samo miejsce", gdy dzieli je tyle metrow. */
    private static final float JUMP_AGREE_M = 25f;

    /** Ile ostatnich fixow bierzemy do glosowania przy postoju. */
    private static final int VOTE_SLOTS = 8;

    /** Ile z nich musi wskazywac to samo miejsce, by uznac je za prawdziwe. */
    private static final int VOTE_NEEDED = 5;

    /** Jak blisko siebie musza byc fixy, by uznac je za to samo miejsce [m]. */
    private static final float VOTE_CLUSTER_M = 150f;

    /** Najwiekszy krok korekty pozycji na jeden fix [m] - zeby nie bylo teleportu. */
    private static final double MAX_CORRECT_STEP_M = 400d;

    /** Najmniejszy krok korekty (zeby dojsc do celu, nawet gdy blad jest maly) [1]. */
    private static final float MIN_CORRECT_TRUST = 0.06f;

    /** Na postoju ignorujemy rozrzut do tyle razy wiekszy od deklarowanej dokladnosci. */
    private static final float STILL_ACCURACY_SLACK = 1.5f;

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

    /** Czy mamy pozycje z pamieci ("gdzie zaparkowalem"), zanim przyszly fixy. */
    private boolean seeded;

    /** Ostatni kurs z realnej jazdy - trzymamy go po zatrzymaniu. */
    private float holdHeading;
    private boolean holdHeadingSet;

    /** Ile fixow z rzedu GPS pokazywal ten sam skok (patrz {@link #plausible}). */
    private int pendingHits;
    private double pendingLat, pendingLon;
    /** Cel przyjety przez glosowanie (srodek grupy) - zamiast pojedynczego fixa. */
    private boolean accSet;
    private double accLat, accLon;

    /** Glosowanie: ostatnie fixy (pierscien) - patrz {@link #voteWinner}. */
    private final double[] voteLat = new double[VOTE_SLOTS];
    private final double[] voteLon = new double[VOTE_SLOTS];
    private final double[] voteOut = new double[3];
    private int voteCount;
    private int voteNext;

    /** Miejsce, ktore GPS potwierdzil jako nowa pozycje - dochodzimy do niego. */
    private boolean confirmedSet;
    private double confirmedLat, confirmedLon;

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
        seeded = false;
        holdHeading = 0f;
        holdHeadingSet = false;
        pendingHits = 0;
        pendingLat = 0d;
        pendingLon = 0d;
        confirmedSet = false;
        confirmedLat = 0d;
        confirmedLon = 0d;
        accSet = false;
        accLat = 0d;
        accLon = 0d;
        voteCount = 0;
        voteNext = 0;
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
        // 0.5 -> 0.6 m/s: raz kurs, raz polnoc). - przy wolnej jazdzie i na postoju GPS
        // podaje kierunek z szumu i mapa krecilaby sie na boki.
        // Ale zatrzymujac sie (np. na swiatlach) NIE przestawiamy widoku na polnoc:
        // trzymamy ostatni kurs z jazdy, zeby widok byl ciagle ten sam. Zmiana na
        // polnoc tylko wtedy, gdy jeszcze nigdy nie jechalismy (brak kursu).
        float target = fixSpeedMs >= NORTH_UP_SPEED_MS ? normalize(fixBearing) : holdingTarget();
        if (fixSpeedMs >= NORTH_UP_SPEED_MS) {      // jedziemy - zapamietujemy kurs
            holdHeading = normalize(fixBearing);
            holdHeadingSet = true;
        }
        if (!hasFix) {
            hasFix = true;
            if (seeded) {
                // Pozycje mamy z pamieci, wiec pierwszy fix NIE ustawia nas od zera -
                // tylko ja doprecyzowuje (lecimy dalej nizej, zwykla sciezka).
            } else {
            // pierwszy fix - nie ma z czego interpolowac, ustawiamy od razu
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
        }
        if (fixAccuracy > MAX_USABLE_ACCURACY_M) {
            // Fix bezuzyteczny (np. zimny start) - nie psujemy nim pozycji, ale
            // odnotowujemy, ze GPS zyje (inaczej pozycja uznana za nieaktualna).
            lastFixAt = nowMs;
            return;
        }
        // Bramka na skoki GPS. Pojedynczy bledny fix (zimny start, tunel, miasto miedzy
        // budynkami) potrafi wskazac pozycje kilometry dalej i jeszcze sklamac, ze
        // jedziemy. Taki fix nie moze przestawic mapy ani zapisac sie jako "ostatnia
        // pozycja" w Ustawieniach.
        long gap = nowMs - lastFixAt;
        if (!plausible(fixLat, fixLon, fixSpeedMs, fixAccuracy, gap)) {
            lastFixAt = nowMs;      // wiemy, ze GPS zyje, ale pozycji nie ruszamy
            return;
        }

        // Glosowanie moglo wskazac srodek grupy fixow - wtedy to on jest celem.
        double tLat = accSet ? accLat : fixLat;
        double tLon = accSet ? accLon : fixLon;
        accSet = false;
        // Ile z tego fix'a bierzemy na wiare (0 = stoimy, 1 = jedziemy).
        float trust = trustFor(tLat, tLon, fixSpeedMs, fixAccuracy);

        durMs = gap < MIN_DUR_MS ? MIN_DUR_MS : (gap > MAX_DUR_MS ? MAX_DUR_MS : gap);
        lastFixAt = nowMs;
        t0 = nowMs;
        // start z biezacej (wygladzonej) wartosci - nowy fix nie robi skoku
        fromLat = lat;
        fromLon = lon;
        // Cel to punkt o "trust" blizej fix'a. Przy postoju (trust = 0) celem jest
        // biezaca pozycja, czyli znacznik stoi w miejscu mimo szumu GPS.
        toLat = lat + (tLat - lat) * trust;
        toLon = lon + (tLon - lon) * trust;
        speedMs = trust <= 0f ? 0f : fixSpeedMs;
        if (trust > 0f) accuracy = fixAccuracy;
        fromBearing = bearing;
        toBearing = bearing + shortestDelta(target, bearing);
        dirty = true;
    }

    /**
     * Pozycja z pamieci ("gdzie zaparkowalem"), zanim przyjdzie pierwszy fix.
     * Dzieki temu po wejsciu do auta mapa jest od razu w dobrym miejscu, a GPS
     * tylko ja doprecyzowuje - bez szukania miejsca od zera. Ignorowane, gdy
     * mamy juz prawdziwy fix.
     */
    public void seed(double sLat, double sLon, long nowMs) {
        if (hasFix) return;
        lat = sLat;
        lon = sLon;
        fromLat = sLat;
        fromLon = sLon;
        toLat = sLat;
        toLon = sLon;
        seeded = true;
        lastFixAt = nowMs;      // pierwszy fix oceniamy normalnie (na postoju: zapas 12 m)
        dirty = true;
    }

    /** Czy pozycja pochodzi z pamieci ("gdzie zaparkowalem"), a fix jeszcze nie przyszedl. */
    public boolean seededKnown() {
        return seeded && !hasFix;
    }

    /** Kurs do trzymania na postoju: ostatni z jazdy, a bez jazdy - polnoc. */
    private float holdingTarget() {
        return holdHeadingSet ? holdHeading : 0f;
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
        // Ten sam promien co w bramce (plausible): jesli fix przeszedl bramke,
        // to znaczy, ze jest w granicach szumu - i wtedy NIE ruszamy znacznika.
        float radius = Math.max(STILL_RADIUS_M, fixAccuracy * STILL_ACCURACY_SLACK);
        if (dist <= radius) return 0f;
        // Potwierdzona korekta pozycji: dochodzimy malymi krokami (max ~400 m na fix),
        // zeby nawet 5 km bledu nadrobic plynnie w kilkanascie sekund, bez teleportu.
        double step = MAX_CORRECT_STEP_M / Math.max(1d, dist);
        return (float) Math.max(MIN_CORRECT_TRUST, Math.min(CREEP_TRUST, step));
    }

    /**
     * Czy temu fix'owi wierzymy. Odrzucamy skoki, ktorych nie da sie przejechac
     * (GPS klamie), a duzy skok przyjmujemy dopiero, gdy powtorzy sie kilka razy
     * z rzedu w tym samym miejscu - to odroznia prawdziwa zmiane miejsca od bledu.
     * Odrzucony fix nie rusza ani pozycji, ani predkosci, wiec licznik nie pokazuje
     * na postoju 40 km/h po blednym fixie.
     */
    private boolean plausible(double fixLat, double fixLon, float fixSpeedMs,
                              float fixAccuracy, long gapMs) {
        double dist = metersBetween(lat, lon, fixLat, fixLon);
        double dt = Math.max(0.5d, gapMs / 1000d);
        boolean still = fixSpeedMs < STILL_SPEED_MS && speedMs < STILL_SPEED_MS;
        addVote(fixLat, fixLon);          // kazdy fix jest glosem (patrz voteWinner)
        double allowed;
        if (still) {
            allowed = Math.max(STILL_RADIUS_M, fixAccuracy * STILL_ACCURACY_SLACK);
        } else {
            double v = Math.min(MAX_PLAUSIBLE_SPEED_MS, Math.max(fixSpeedMs, speedMs));
            allowed = v * dt * 1.6d + JUMP_SLACK_M;
        }
        if (dist <= allowed) {
            // Jestesmy na miejscu - konczymy "dochodzenie" do zatwierdzonego punktu.
            pendingHits = 0;
            confirmedSet = false;
            return true;
        }
        // Skok potwierdzony: kolejne fixy z tego samego miejsca przyjmujemy od razu,
        // inaczej dochodzenie do nowej pozycji rozciagalo sie na minuty (kazdy krok
        // musialby znow czekac na potwierdzenie).
        if (confirmedSet
                && metersBetween(confirmedLat, confirmedLon, fixLat, fixLon) <= JUMP_AGREE_M) {
            return true;
        }
        if (pendingHits > 0
                && metersBetween(pendingLat, pendingLon, fixLat, fixLon) <= JUMP_AGREE_M) {
            pendingHits++;
        } else {
            pendingLat = fixLat;
            pendingLon = fixLon;
            pendingHits = 1;
        }
        if (still && voteWinner()) {
            // Postoj: glosowanie. Pojedyncze fixy skacza, ale prawdziwe miejsce
            // powtarza sie najczesciej - jesli najliczniejsza grupa ostatnich fixow
            // trzyma sie razem, to wlasnie tam jestesmy. Celem jest SRODEK tej grupy
            // (a nie pojedynczy fix), dzieki czemu szum nie wozil znacznikiem.
            // Bez tego stalismy w blednym miejscu az do ruszenia (zgloszenie 2026-11-05).
            confirmedSet = true;
            confirmedLat = voteOut[0];
            confirmedLon = voteOut[1];
            pendingHits = 0;
            accSet = true;
            accLat = voteOut[0];
            accLon = voteOut[1];
            return true;
        }
        if (pendingHits >= (still ? JUMP_CONFIRM_STILL : JUMP_CONFIRM)) {
            pendingHits = 0;         // powtarza sie - to prawda, nie blad
            confirmedSet = true;
            confirmedLat = fixLat;
            confirmedLon = fixLon;
            return true;
        }
        return false;
    }

    /** Dopisz fix do glosowania (pierscien ostatnich {@link #VOTE_SLOTS} fixow). */
    private void addVote(double fixLat, double fixLon) {
        voteLat[voteNext] = fixLat;
        voteLon[voteNext] = fixLon;
        voteNext = (voteNext + 1) % VOTE_SLOTS;
        if (voteCount < VOTE_SLOTS) voteCount++;
    }

    /**
     * Czy najliczniejsza grupa ostatnich fixow wskazuje jedno miejsce? Jesli tak,
     * wpisuje jego srodek do {@link #voteOut} (lat, lon, ile glosow) i zwraca true.
     * Smieci w nieuzywanych slotach pierscienia nie biora udzialu w glosowaniu.
     */
    private boolean voteWinner() {
        if (voteCount < VOTE_NEEDED) return false;
        int best = 0;
        double bestLat = 0d, bestLon = 0d;
        for (int i = 0; i < voteCount; i++) {
            int n = 0;
            double sLat = 0d, sLon = 0d;
            for (int j = 0; j < voteCount; j++) {
                if (metersBetween(voteLat[i], voteLon[i], voteLat[j], voteLon[j])
                        <= VOTE_CLUSTER_M) {
                    n++;
                    sLat += voteLat[j];
                    sLon += voteLon[j];
                }
            }
            if (n > best) {
                best = n;
                bestLat = sLat / n;
                bestLon = sLon / n;
            }
        }
        if (best < VOTE_NEEDED) return false;
        voteOut[0] = bestLat;
        voteOut[1] = bestLon;
        voteOut[2] = best;
        return true;
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
