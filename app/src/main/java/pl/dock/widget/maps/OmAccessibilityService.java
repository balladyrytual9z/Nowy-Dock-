package pl.dock.widget.maps;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Usluga dostepnosci Docka: czyta dane nawigacji z ekranu Organic Maps.
 *
 * Organic Maps rysuje panel nawigacji zwyklymi widokami Androida o stalych
 * identyfikatorach, wiec odczytujemy je po nazwie (wymaga flagReportViewIds,
 * patrz res/xml/om_accessibility.xml):
 *
 *   street, turn, distance            - gorny panel: ulica, manewr, dystans do manewru
 *   speed_value / speed_dimen         - aktualna predkosc
 *   time_hour_value/time_minute_value - pozostaly czas
 *   time_estimate                     - godzina dojazdu (ETA)
 *   distance_value / distance_dimen   - pozostaly dystans
 *   nav_speed_limit                   - limit predkosci (jesli droga go ma)
 *
 * Usluga nasluchuje wylacznie pakietu app.organicmaps (patrz packageNames).
 * Dane zostaja na urzadzeniu - nic nie jest wysylane na zewnatrz.
 */
public class OmAccessibilityService extends AccessibilityService {

    public static final String OM_PACKAGE = "app.organicmaps";

    private static volatile boolean connected;

    public static boolean isConnected() {
        return connected;
    }

    /** Tekst z pierwszego widoku o danym identyfikatorze (albo pusty). */
    private static String nodeText(AccessibilityNodeInfo root, String idName) {
        if (root == null) return "";
        try {
            List<AccessibilityNodeInfo> nodes =
                    root.findAccessibilityNodeInfosByViewId(OM_PACKAGE + ":id/" + idName);
            if (nodes == null || nodes.isEmpty()) return "";
            for (AccessibilityNodeInfo n : nodes) {
                if (n == null) continue;
                CharSequence text = n.getText();
                if (text != null && text.length() > 0) return text.toString().trim();
                CharSequence desc = n.getContentDescription();
                if (desc != null && desc.length() > 0) return desc.toString().trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String join(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        if (x.isEmpty()) return y;
        if (y.isEmpty()) return x;
        return x + " " + y;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        NavData.get().update(false, "", "", "", "", "", "", "", "", "");
        return super.onUnbind(intent);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !OM_PACKAGE.contentEquals(pkg)) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            String street = nodeText(root, "street");
            String turn = nodeText(root, "turn");
            String turnDistance = nodeText(root, "distance");

            String speed = join(nodeText(root, "speed_value"), "");
            String speedUnit = nodeText(root, "speed_dimen");

            String remainTime = join(nodeText(root, "time_hour_value"),
                    nodeText(root, "time_minute_value"));
            String eta = nodeText(root, "time_estimate");
            String remainDistance = join(nodeText(root, "distance_value"),
                    nodeText(root, "distance_dimen"));
            String speedLimit = nodeText(root, "nav_speed_limit");

            // Nawigacja trwa, gdy widac panel trasy (dystans/czas/manewr).
            boolean active = !remainDistance.isEmpty()
                    || !remainTime.isEmpty()
                    || !turnDistance.isEmpty();

            NavData.get().update(active, turn, turnDistance, street, eta, remainTime,
                    remainDistance, speed, speedUnit, speedLimit);
        } catch (Throwable ignored) {
        }
    }
}
