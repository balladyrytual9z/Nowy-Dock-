package pl.dock.widget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Cykl trybów Night Shift w panelu łączności: off -> on -> sunset -> off. */
public class NightShiftTest {

    @Test
    public void fullCycle() {
        assertEquals("on", NightShift.nextMode("off"));
        assertEquals("sunset", NightShift.nextMode("on"));
        assertEquals("off", NightShift.nextMode("sunset"));
        // po pelnym cyklu wracamy na "off" (stan spojnny z poczatkowym)
        assertEquals("off", NightShift.nextMode(NightShift.nextMode(NightShift.nextMode("off"))));
    }

    @Test
    public void unknownOrNull_goesToOn() {
        assertEquals("on", NightShift.nextMode(null));
        assertEquals("on", NightShift.nextMode(""));
        assertEquals("on", NightShift.nextMode("cos-innego"));
    }
}
