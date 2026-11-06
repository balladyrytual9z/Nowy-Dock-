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

    @Test
    public void overlayNieMaWlasnejWarstwy() throws Exception {
        File f = launcherSource();
        String src = new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));

        // Warstwa SOFTWARE moze byc TYLKO na ConnectivityPanel (potwierdzona przez
        // uzytkownika, PR #14). Warstwa dodana na overlayu (PR #46, commit 2907c5e)
        // tworzyla DWIe zagniezdzone warstwy - tresc panelu przestala sie rysowac.
        assertFalse("overlay.setLayerType na OVERLAYU psuje panel: dwie zagniezdzone"
                        + " warstwy na Mali-T720 nie renderuja tresci - warstwa tylko"
                        + " na ConnectivityPanel (PR #14)",
                src.contains("overlay.setLayerType"));
    }

    @Test
    public void overlayZachowujePotwierdzonaSekwencjeWejscia() throws Exception {
        File f = launcherSource();
        String src = new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));

        // Potwierdzona na urzadzeniu sekwencja (PR #14, commit b21e953): fade
        // overlayu + animacja wejscia panelu (skala/pivot/translate/Overshoot)
        // + withEndAction z wymuszonym requestLayout()+invalidate(). Komentarz
        // z tamtej poprawki: "pierwszy pomiar panelu w animowanej overlay potrafil
        // wyjsc 0x0 i tresc nigdy sie nie rysowala" - bez tego panel jest pusty.
        assertTrue("Brak wymuszonego re-layoutu panelu (panel.requestLayout() w"
                        + " withEndAction) - to mechanizm naprawczy z PR #14",
                src.contains("panel.requestLayout()"));
        assertTrue("Brak overlay.invalidate() po animacji wejscia (PR #14)",
                src.contains("overlay.invalidate()"));
        assertTrue("Brak animacji wejscia panelu (panel.setScaleX(0.92f)) -"
                        + " bez niej nie ma withEndAction i tresc nie odtwarza sie",
                src.contains("panel.setScaleX(0.92f)"));
        assertTrue("Brak withEndAction po animacji wejscia panelu (PR #14)",
                src.contains("withEndAction"));
    }

    @Test
    public void naStarymApiCentrumOtwieraSieWOsobnymOknie() throws Exception {
        File f = launcherSource();
        String src = new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));

        // OSTATECZNY fix (v1.0.257): na API < 30 zaden overlay w oknie launchera
        // nie renderuje custom Canvas views (testowane w 252/254/256 - zawsze
        // puste okno z samym „Edytuj”). Na starych API centrum lacznosci musi
        // otwierac sie w OSOBNYM OKNIE (ConnectivityActivity) - swiezy kontekst,
        // ta sama sciezka co z widzetu. Overlay (blur) wolno tylko na SDK >= 30.
        assertTrue("Brak przekierowania na API < 30 do ConnectivityActivity -"
                        + " bez tego centrum lacznosci jest puste na ESSGOO",
                src.contains("SDK_INT < 30")
                        && src.contains("startActivity(new Intent(this, ConnectivityActivity.class))"));
    }

    @Test
    public void gestSwipeUpMieszkaWPagerze() throws Exception {
        File f = launcherSource();
        String src = new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));

        // Gest swipe-up NIE moze wrocic na root.setOnTouchListener: pelnoekranowy
        // pager konsumuje kazdy ACTION_DOWN u dzieci, wiec listener na rootcie
        // nigdy nie dostaje zdarzenia i gest jest martwym kodem. Gest musi byc
        // wykrywany w PagerView, ktore naprawde widzi wszystkie dotyki.
        assertFalse("root.setOnTouchListener = martwy gest (pager zjada DOWN) -"
                        + " wykrywanie musi byc w PagerView",
                src.contains("root.setOnTouchListener"));

        assertTrue("Brak pager.setSwipeEnabled(...) - LauncherActivity musi wlaczyc"
                        + " gest swipe-up w PagerView",
                src.contains("pager.setSwipeEnabled("));

        File pf = new File("src/main/java/pl/dock/widget/PagerView.java");
        if (!pf.isFile()) pf = new File("app/" + pf.getPath());
        String psrc = new String(Files.readAllBytes(pf.toPath()), Charset.forName("UTF-8"));
        assertTrue("PagerView.Listener musi miec onSwipeUp(float) - na tym wisi gest",
                psrc.contains("void onSwipeUp(float x)"));
        assertTrue("Brak wlacznika setSwipeEnabled w PagerView",
                psrc.contains("setSwipeEnabled"));
    }

    @Test
    public void widzetyNaStronieSaPrzezroczyste() throws Exception {
        File f = launcherSource();
        String src = new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));

        // Widzety na skrajnie lewej stronie (WidgetPage) maja przezroczyste tlo
        // ("szklo") zamiast nieprzezroczystej karty; ekran glowny bez zmian.
        assertTrue("Brak markGlass(...) w LauncherActivity - widzety na stronie"
                        + " widzetow nie sa przezroczyste",
                src.contains("markGlass(v)"));

        File uf = new File("src/main/java/pl/dock/widget/Ui.java");
        if (!uf.isFile()) uf = new File("app/" + uf.getPath());
        String usrc = new String(Files.readAllBytes(uf.toPath()), Charset.forName("UTF-8"));
        assertTrue("Brak Ui.glassCard(...) - przezroczysta karta widzetu nie istnieje",
                usrc.contains("public static void glassCard("));
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

    private static File launcherSource() {
        String rel = "src/main/java/pl/dock/widget/LauncherActivity.java";
        File f = new File(rel);
        if (f.isFile()) return f;
        f = new File("app/" + rel);
        if (f.isFile()) return f;
        throw new IllegalStateException("Nie znalazlem LauncherActivity.java (cwd="
                + new File(".").getAbsolutePath() + ")");
    }
}
