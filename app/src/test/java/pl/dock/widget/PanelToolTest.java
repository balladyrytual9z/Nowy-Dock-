package pl.dock.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Testy spójności definicji narzędzi panelu łączności (JVM, bez Androida).
 * PanelTool.defaults/find nie dotykają Context, więc są testowalne w czystym
 * JUnit. (W app/build.gradle ustawione testOptions.unitTests
 * .returnDefaultValues = true, bo klasa PanelTool ma statyczne pola Paint.)
 */
public class PanelToolTest {

    @Test
    public void find_returnsKnownTool() {
        PanelTool.Def wifi = PanelTool.find("wifi");
        assertNotNull("wifi musi istnieć - to kafelek modulu bazowego", wifi);
        assertEquals("wifi", wifi.id);
        assertEquals("Wi-Fi", wifi.label);
        assertEquals(PanelTool.K_TOGGLE, wifi.kind);
    }

    @Test
    public void find_unknownOrNull_returnsNull() {
        assertNull(PanelTool.find("nie-ma-takiego"));
        assertNull(PanelTool.find(null));
    }

    @Test
    public void baseModuleTools_existAsToggleOrSlider() {
        // staly modul bazowy panelu: suwaki, Night Shift, tryb wygladu, łączność
        assertEquals(PanelTool.K_BRIGHT, PanelTool.find("bri").kind);
        assertEquals(PanelTool.K_VOLUME, PanelTool.find("vol").kind);
        assertEquals(PanelTool.K_MODE, PanelTool.find("mode").kind);
        assertEquals(PanelTool.K_GROUP, PanelTool.find("conn").kind);
        assertEquals(PanelTool.K_TOGGLE, PanelTool.find("nightshift").kind);
        assertEquals(PanelTool.K_TOGGLE, PanelTool.find("bt").kind);
        assertEquals(PanelTool.K_TOGGLE, PanelTool.find("hot").kind);
    }

    @Test
    public void defaults_areEditableGridTools() {
        // Context nieuzywany w defaults - null jest OK na JVM
        List<String> d = PanelTool.defaults(null);
        assertEquals(Arrays.asList("rotate", "mute", "loc"), d);
        // domyslne narzedzia nigdy nie pokrywaja sie z modulem bazowym
        for (String id : d) {
            for (String base : ConnectivityPanel.BASE_IDS) {
                assertTrue("domyslne narzedzie nie moze byc z module bazowego: " + id,
                        !id.equals(base));
            }
        }
    }

    @Test
    public void withoutBaseTools_removesBaseTools() {
        List<String> mixed = new ArrayList<String>(
                Arrays.asList("rotate", "wifi", "mute", "bri", "flash", "conn"));
        assertEquals(Arrays.asList("rotate", "mute", "flash"),
                ConnectivityPanel.withoutBaseTools(mixed));
        // lista wejsciowa niezmieniona (czysta funkcja)
        assertEquals(6, mixed.size());
    }

    @Test
    public void withoutBaseTools_nullAndEmpty() {
        assertTrue(ConnectivityPanel.withoutBaseTools(null).isEmpty());
        assertTrue(ConnectivityPanel.withoutBaseTools(new ArrayList<String>()).isEmpty());
    }

    @Test
    public void defaultsAreConsistentWithWithoutBaseTools() {
        // to samo, co robi ConnectivityPanel.build(): domyslne -> siatka bez bazowych
        List<String> grid = ConnectivityPanel.withoutBaseTools(PanelTool.defaults(null));
        assertEquals(PanelTool.defaults(null).size(), grid.size());
    }
}
