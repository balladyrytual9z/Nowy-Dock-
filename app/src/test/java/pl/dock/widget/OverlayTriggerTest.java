package pl.dock.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Testy czystej logiki gestu otwarcia centrum łączności (root cause:
 * poprzednia implementacja w LauncherActivity zwracała false w ACTION_DOWN,
 * więc ACTION_UP nigdy nie przychodził i panel nie otwierał się gestem).
 */
public class OverlayTriggerTest {

    // ---------------------------------------------------------------- strefa gestu

    @Test
    public void downInBottomZone_isSwipeZone() {
        // ekran 800px, strefa 120px -> dotyk od 681 wzwyż jest w strefie
        assertTrue(OverlayTrigger.inSwipeZone(800, 800, 120));
        assertTrue(OverlayTrigger.inSwipeZone(681, 800, 120));
        assertFalse(OverlayTrigger.inSwipeZone(679, 800, 120));
        assertFalse(OverlayTrigger.inSwipeZone(0, 800, 120));
    }

    @Test
    public void degenerateInputs_areNeverInZone() {
        assertFalse(OverlayTrigger.inSwipeZone(500, 0, 120));
        assertFalse(OverlayTrigger.inSwipeZone(500, -1, 120));
        assertFalse(OverlayTrigger.inSwipeZone(500, 800, -5));
    }

    // ---------------------------------------------------------------- rozpoznanie swipe UP

    @Test
    public void fastUpwardSwipe_isSwipeUp() {
        // 150px w górę w 200 ms
        assertTrue(OverlayTrigger.isSwipeUp(700, 550, 200, 80, 500));
    }

    @Test
    public void tooShortSwipe_isNotSwipeUp() {
        // tylko 50px w górę - poniżej progu 80px
        assertFalse(OverlayTrigger.isSwipeUp(700, 650, 200, 80, 500));
    }

    @Test
    public void slowSwipe_isNotSwipeUp() {
        // 200px w górę, ale za wolno (limit 500 ms)
        assertFalse(OverlayTrigger.isSwipeUp(700, 500, 600, 80, 500));
    }

    @Test
    public void downwardSwipe_isNotSwipeUp() {
        // palec przesunięty w DÓŁ - dy ujemne
     
   assertFalse(OverlayTrigger.isSwipeUp(500, 700, 200, 80, 500));
    }

    @Test
    public void boundarySwipe_isNotSwipeUp() {
        // dokładnie na granicy (dy == min, dt == max) - bez marginesu błędu
        assertFalse(OverlayTrigger.isSwipeUp(700, 620, 200, 80, 500));
        assertFalse(OverlayTrigger.isSwipeUp(700, 550, 500, 80, 500));
    }

    // ---------------------------------------------------------------- pozycja panelu

    @Test
    public void followGesture_centersPanelOverFinger() {
        // ekran 1200, panel 500, dok 120 po lewej, pad 8
        assertEquals(350, OverlayTrigger.panelX(600, 500, 1200, 120, true, true, 8));
    }

    @Test
    public void followGesture_clampsToScreen() {
        // palec przy lewej krawędzi, dok po PRAWEJ -> clamp do pad.
        // gestureX=0 to brak gestu (otwarcie przyciskiem doka) - ten przypadek
        // pokrywa zeroGesture_fallsBackToDefault, tam pozycja jest domyślna.
        assertEquals(8, OverlayTrigger.panelX(10, 500, 1200, 120, false, true, 8));
        // palec przy prawej krawędzi, dok po LEWEJ -> max = 1200 - 500 - 8
        assertEquals(692, OverlayTrigger.panelX(1200, 500, 1200, 120, true, true, 8));
    }

    @Test
    public void followGesture_avoidsLeftDock() {
        // wycentrowany panel nachodziłby na dok po lewej -> dok + pad
        assertEquals(128, OverlayTrigger.panelX(60, 500, 1200, 120, true, true, 8));
    }

    @Test
    public void followGesture_avoidsRightDock() {
        // dok po prawej (1200-120=1080): panel nie może sięgnąć 1080
        // wycentrowany: 1100-250=850; 850+500=1350 > 1080 -> przesunięty w lewo
        assertEquals(1080 - 500 - 8, OverlayTrigger.panelX(1100, 500, 1200, 120, false, true, 8));
    }

    @Test
    public void noFollow_placesPanelBesideDock() {
        assertEquals(128, OverlayTrigger.panelX(600, 500, 1200, 120, true, false, 8));
        // dok po prawej -> panel przy lewej krawędzi
        assertEquals(8, OverlayTrigger.panelX(600, 500, 1
200, 120, false, false, 8));
    }

    @Test
    public void zeroGesture_fallsBackToDefault() {
        // gestureX = 0 (np. otwarcie przyciskiem nagłówka doka) -> pozycja domyślna
        assertEquals(128, OverlayTrigger.panelX(0, 500, 1200, 120, true, true, 8));
        assertEquals(8, OverlayTrigger.panelX(0, 500, 1200, 120, false, true, 8));
    }
}
