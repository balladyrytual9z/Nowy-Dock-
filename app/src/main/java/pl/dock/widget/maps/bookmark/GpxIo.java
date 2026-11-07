package pl.dock.widget.maps.bookmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zapis/odczyt GPX i KML dla zakladek i sladow. Czysta logika bez Androida
 * (testy JVM: GpxIoTest) - parsowanie idzie po wyrazeniach regularnych,
 * bo org.xmlpull w testach JVM nie istnieje, a struktura GPX/KML jest na tyle
 * regularna, ze pelny parser XML nie jest potrzebny.
 *
 * Obslugiwane formaty jak w Organic Maps: GPX (wpt + trk), KML (Placemark/Point).
 */
public final class GpxIo {

    private static final Pattern ATTR = Pattern.compile(
            "lat=\"([-0-9.]+)\"\\s+lon=\"([-0-9.]+)\"");
    private static final Pattern NAME = Pattern.compile(
            "<name>([^<]*)</name>");
    private static final Pattern WPT = Pattern.compile(
            "<wpt[^>]*>(.*?)</wpt>", Pattern.DOTALL);
    private static final Pattern WPT_EMPTY = Pattern.compile(
            "<wpt\\b([^>]*)/>");
    private static final Pattern TRKPT = Pattern.compile(
            "<trkpt\\b[^>]*lat=\"([-0-9.]+)\"[^>]*lon=\"([-0-9.]+)\"[^>]*>");
    private static final Pattern PLACEMARK = Pattern.compile(
            "<Placemark>(.*?)</Placemark>", Pattern.DOTALL);
    private static final Pattern COORDS = Pattern.compile(
            "<coordinates>\\s*([-0-9.]+),([-0-9.]+)");

    private GpxIo() {
    }

    // ---------------- zapis ----------------

    /** GPX ze wszystkimi zakladkami (wpt). */
    public static String writeBookmarks(List<Bookmark> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"Dock Launcher\" ")
                .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        for (int i = 0; i < list.size(); i++) {
            Bookmark b = list.get(i);
            sb.append("<wpt lat=\"").append(fmt(b.lat)).append("\" lon=\"")
                    .append(fmt(b.lon)).append("\">\n");
            sb.append("<name>").append(xml(b.name)).append("</name>\n");
            sb.append("</wpt>\n");
        }
        sb.append("</gpx>\n");
        return sb.toString();
    }

    /** GPX ze sladem (trk/trkseg/trkpt). Czasy w UTC, jak nakazuje specyfikacja GPX. */
    public static String writeTrack(String name, double[] lats, double[] lons, long[] times) {
        java.text.SimpleDateFormat df =
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        df.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"Dock Launcher\" ")
                .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("<trk>\n<name>").append(xml(name)).append("</name>\n<trkseg>\n");
        int n = Math.min(lats.length, lons.length);
        for (int i = 0; i < n; i++) {
            sb.append("<trkpt lat=\"").append(fmt(lats[i])).append("\" lon=\"")
                    .append(fmt(lons[i])).append("\">");
            if (times != null && i < times.length) {
                sb.append("<time>").append(df.format(new java.util.Date(times[i])))
                        .append("</time>");
            }
            sb.append("</trkpt>\n");
        }
        sb.append("</trkseg>\n</trk>\n</gpx>\n");
        return sb.toString();
    }

    /** KML ze zakladkami (Placemark/Point). */
    public static String writeBookmarksKml(List<Bookmark> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>\n");
        for (int i = 0; i < list.size(); i++) {
            Bookmark b = list.get(i);
            sb.append("<Placemark>\n<name>").append(xml(b.name)).append("</name>\n");
            sb.append("<Point><coordinates>").append(fmt(b.lon)).append(",")
                    .append(fmt(b.lat)).append(",0</coordinates></Point>\n");
            sb.append("</Placemark>\n");
        }
        sb.append("</Document></kml>\n");
        return sb.toString();
    }

    // ---------------- odczyt ----------------

    /** Parsuje zakladki z GPX (wpt). */
    public static List<Bookmark> parseWaypoints(String gpx) {
        List<Bookmark> out = new ArrayList<Bookmark>();
        if (gpx == null) return out;
        Matcher m = WPT.matcher(gpx);
        while (m.find()) {
            String block = m.group(1);
            double[] ll = attr(block);
            if (ll == null) continue;
            String name = first(NAME, block);
            out.add(new Bookmark(0L, name, ll[0], ll[1], 0L));
        }
        // wpt bez zawartosci (samosiezajace)
        if (out.isEmpty()) {
            Matcher m2 = WPT_EMPTY.matcher(gpx);
            while (m2.find()) {
                double[] ll = attr(m2.group(1));
                if (ll != null) out.add(new Bookmark(0L, "Punkt", ll[0], ll[1], 0L));
            }
        }
        return out;
    }

    /** Parsuje punkty sladu z GPX (trkpt lub rtept) -> [lat, lon, timeMs(0 gdy brak)]. */
    public static List<double[]> parseTrack(String gpx) {
        List<double[]> out = new ArrayList<double[]>();
        if (gpx == null) return out;
        Matcher m = TRKPT.matcher(gpx);
        while (m.find()) {
            out.add(new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), 0d});
        }
        return out;
    }

    /** Parsuje zakladki z KML (Placemark z Point/coordinates: lon,lat). */
    public static List<Bookmark> parseKmlWaypoints(String kml) {
        List<Bookmark> out = new ArrayList<Bookmark>();
        if (kml == null) return out;
        Matcher m = PLACEMARK.matcher(kml);
        while (m.find()) {
            String block = m.group(1);
            Matcher c = COORDS.matcher(block);
            if (!c.find()) continue;
            double lon = Double.parseDouble(c.group(1));
            double lat = Double.parseDouble(c.group(2));
            String name = first(NAME, block);
            out.add(new Bookmark(0L, name, lat, lon, 0L));
        }
        return out;
    }

    // ---------------- pomocnicze ----------------

    private static double[] attr(String block) {
        Matcher a = ATTR.matcher(block);
        if (!a.find()) return null;
        return new double[]{Double.parseDouble(a.group(1)), Double.parseDouble(a.group(2))};
    }

    private static String first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        if (m.find()) return m.group(1).trim();
        return "";
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
