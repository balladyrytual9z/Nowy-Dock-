package pl.dock.widget.maps;

import java.util.Locale;

/**
 * Kategorie POI dla wyszukiwania (jak przyciski kategorii w Organic Maps).
 * Polska fraza z "Szukaj" (np. "apteka", "stacje benzynowe") mapuje sie na
 * angielski termin OSM/Nominatim, ktory z viewbox wokol pozycji znajduje
 * obiekty w okolicy. Czysta logika bez Androida (testy: PoiCategoriesTest).
 */
public final class PoiCategories {

    private PoiCategories() {
    }

    /** [fragment frazy, termin OSM]. Pierwsze dopasowanie wygrywa. */
    private static final String[][] MAP = {
            {"benzyn", "fuel"},
            {"paliw", "fuel"},
            {"tankuj", "fuel"},
            {"lpg", "fuel"},
            {"myjn", "car wash"},
            {"parking", "parking"},
            {"restaurac", "restaurant"},
            {"pizzer", "pizza"},
            {"kebab", "kebab"},
            {"burger", "burger"},
            {"sushi", "sushi"},
            {"obiad", "restaurant"},
            {"knajp", "restaurant"},
            {"jedzen", "restaurant"},
            {"kuchni", "restaurant"},
            {"bankomat", "atm"},
            {"kant", "bureau de change"},
            {"aptek", "pharmacy"},
            {"kawiarn", "cafe"},
            {"cafe", "cafe"},
            {"cukiern", "pastry"},
            {"lodziar", "ice cream"},
            {"hotel", "hotel"},
            {"motel", "motel"},
            {"hostel", "hostel"},
            {"nocleg", "hotel"},
            {"szpital", "hospital"},
            {"leczni", "clinic"},
            {"weterynar", "veterinary"},
            {"sklep", "supermarket"},
            {"market", "supermarket"},
            {"spozyw", "supermarket"},
            {"biedron", "supermarket"},
            {"zabaw", "supermarket"},
            {"piekarn", "bakery"},
            {"kwiat", "florist"},
            {"poczt", "post office"},
            {"policj", "police"},
            {"toalet", "toilets"},
            {"ladown", "charging station"},
            {"ladowark", "charging station"},
            {"pradow", "charging station"},
            {"stacja ladowan", "charging station"},
    };

    /** Znosi polskie diakrytyki (ł->l, ż->z itd.), zeby "ładowania" trafialo w "ladowan". */
    private static String fold(String s) {
        if (s == null) return "";
        return s.replace("ł", "l").replace("Ł", "l")
                .replace("ą", "a").replace("ę", "e").replace("ś", "s")
                .replace("ż", "z").replace("ź", "z").replace("ó", "o")
                .replace("ć", "c").replace("ń", "n");
    }

    /** Termin OSM dla frazy-kategorii albo null (zwykle wyszukiwanie). */
    public static String lookup(String query) {
        if (query == null) return null;
        String q = fold(query.toLowerCase(Locale.ROOT).trim());
        if (q.length() == 0) return null;
        for (int i = 0; i < MAP.length; i++) {
            String frag = fold(MAP[i][0]);
            if (frag.length() > 0 && q.contains(frag)) return MAP[i][1];
        }
        return null;
    }
}
