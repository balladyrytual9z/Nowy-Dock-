package pl.dock.widget.maps;

/**
 * Kontrola sensownosci pozycji mapy.
 *
 * Powstalo po zgloszeniu: "po kazdej nowej wersji mapa szuka miejsca, gdzie
 * kiedys bylem (jakies gory), a na pelnym ekranie mapa w ogole sie nie wyswietla".
 *
 * Przyczyna byla po stronie ZAPISANEJ pozycji, nie GPS:
 *  - niezainicjalizowana mapa osmdroid oddaje srodek (0,0) - "null wyspa" na
 *    Atlantyku, gdzie nie ma zadnych kafli (stad pusty ekran),
 *  - zapis przyjmowal wszystko, takze NaN, nieskonczonosc i zoom 0,
 *  - zapisana pozycja (np. z wyjazdu w gory) wygrywala z zywym GPS, wiec po
 *    kazdym restarcie - a kazda aktualizacja restartuje aplikacje - mapa
 *    otwierala sie w starym miejscu.
 *
 * Dlatego: zapisujemy tylko sensowne wartosci (patrz Prefs.setMapLast),
 * a przy starcie GPS ma pierwszenstwo nad pamiecia (patrz restoreCenter).
 */
public final class MapPlace {

    /** Najnizszy sensowny zoom mapy (swiat). */
    public static final float MIN_ZOOM = 2f;
    /** Najwyzszy sensowny zoom mapy (pojedyncze budynki). */
    public static final float MAX_ZOOM = 19f;
    /** Poniżej tego promienia od (0,0) uznajemy, ze mapa nie byla ustawiona. */
    private static final double NULL_ISLAND = 0.01d;

    private MapPlace() {
    }

    /** Czy taka pozycja ma w ogole sens (odrzuca smieci, (0,0) i wartosci spoza zakresu). */
    public static boolean usable(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) return false;
        if (Double.isInfinite(lat) || Double.isInfinite(lon)) return false;
        if (lat < -85d || lat > 85d) return false;
        if (lon < -180d || lon > 180d) return false;
        // (0,0) = "null wyspa" - tak wraca niezainicjalizowana mapa osmdroid
        return Math.abs(lat) > NULL_ISLAND || Math.abs(lon) > NULL_ISLAND;
    }

    /**
     * Co pokazac na starcie mapy. Kolejnosc waznosci:
     *   1) swiezy fix GPS (wiemy na pewno, gdzie jestesmy),
     *   2) ostatnia DOBRA pozycja GPS - "gdzie zaparkowalem" (uzytkownik: "jak wsiadam
     *      do auta, to nie moze od nowa szukac miejsca, tylko ma pamietac, gdzie
     *      zostawilem auto i od razu pokazac wlasciwa mape"),
     *   3) ostatni widok mapy (gdzie uzytkownik patrzyl),
     *   4) null - czyli widok calej Polski.
     * Dla pozycji "brak" wystarczy podac (0,0) - {@link #usable} je odrzuca.
     */
    public static double[] startupPlace(double fixLat, double fixLon,
                                        double goodLat, double goodLon,
                                        double viewLat, double viewLon) {
        if (usable(fixLat, fixLon)) return new double[]{fixLat, fixLon};
        if (usable(goodLat, goodLon)) return new double[]{goodLat, goodLon};
        if (usable(viewLat, viewLon)) return new double[]{viewLat, viewLon};
        return null;
    }

    /** Zoom z zapisu, a gdy jest bez sensu - wartosc zapasowa. */
    public static float zoom(float z, float fallback) {
        if (Float.isNaN(z) || z < MIN_ZOOM || z > MAX_ZOOM) return fallback;
        return z;
    }
}
