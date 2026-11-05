package pl.dock.widget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Alarm regresyjny dla panelu lacznosci (JVM, bez Androida).
 *
 * Panel byl naprawiany kilka razy i DWA razy zniknal (pusty panel na ESSGOO
 * MT8163 / Mali-T720, realny Android 9 = API 28) po tym, jak przepisano ten plik
 * i usunieto warstwe SOFTWARE. Raz zrobil to PR #17, drugi raz omal nie zrobil
 * tego PR #21 (bazowal na stanie sprzed naprawy).
 *
 * Samo rysowanie da sie sprawdzic tylko na urzadzeniu, wiec ten test pilnuje
 * ZRODLA: warstwa SOFTWARE na API < 30 musi zostac. To nie test logiki - to alarm
 * dla kolejnych osob i agentow, zeby nie "posprzatali" tej jednej linii.
 */
public class ConnectivityPanelGuardTest {

    @Test
    public void panelMaWarstweSoftwareNaStarymApi() throws Exception {
        File f = panelSource();
        String src = new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));

        assertTrue("ConnectivityPanel.java wyglada na obciety (" + src.length()
                        + " B) - ten plik byl juz raz wyzerowany do 0 B",
                src.length() > 4000);

        assertTrue("Brak guardu 'SDK_INT < 30' - na API 28 trzeba wlasnej warstwy,"
                        + " inaczej panel jest pusty",
                src.contains("SDK_INT < 30"));

        assertTrue("Brak setLayerType(View.LAYER_TYPE_SOFTWARE, null) - to jest linia,"
                        + " ktora naprawia pusty panel na Mali-T720",
                src.contains("LAYER_TYPE_SOFTWARE"));
    }

    @Test
    public void panelNiePrzelaczaWarstwyZPowrotem() throws Exception {
        File f = panelSource();
        String src = new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));

        // Przestawienie warstwy na NONE czysci bufor w trakcie animacji otwarcia
        // i panel gasnie (PR #9). W komentarzach wolno o tym pisac - ale nie w kodzie.
        assertFalse("setLayerType(View.LAYER_TYPE_NONE, ...) gasi panel (PR #9)",
                src.contains("setLayerType(View.LAYER_TYPE_NONE"));
    }

    private static File panelSource() {
        String rel = "src/main/java/pl/dock/widget/ConnectivityPanel.java";
        File f = new File(rel);
        if (f.isFile()) return f;
        f = new File("app/" + rel);
        if (f.isFile()) return f;
        throw new IllegalStateException("Nie znalazlem ConnectivityPanel.java (cwd="
                + new File(".").getAbsolutePath() + ")");
    }
}
