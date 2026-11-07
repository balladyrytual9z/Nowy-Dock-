package pl.dock.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Matematyka nakladki Night Shift (JVM, bez Androida).
 * Kluczowa wlasnosc: nasycony kolor overlayTint() trafia BIALYM dokladnie
 * w punkt bieli celu warmthWhitePoint() - dlatego ekran zolci sie jak
 * w iOS, a nie bladnie jak po refactorze z 3.11 (Kelvin liniowo + dim).
 *
 * UWAGA na arytmetyke FLOAT: asercje liczone sa tak, jak je liczy Java,
 * czyli w float (Math.round(float)), a nie w double. Przy przepisywaniu
 * tych asercji "z glowy" w double latwo o +-1 (patrz test alphy nizej).
 */
public class ColorTempTest {

    @Test
    public void kotwica2700KToBursztyn() {
        int[] rgb = ColorTemp.kelvinToRgb(ColorTemp.WARM_K);
        assertEquals(255, rgb[0]);
        assertEquals(167, rgb[1]);
        assertEquals(87, rgb[2]);
    }

    @Test
    public void punktBieliOdNeutraluDoBursztynu() {
        int[] neutral = ColorTemp.warmthWhitePoint(0f);
        assertEquals(255, neutral[0]);
        assertEquals(255, neutral[1]);
        assertEquals(255, neutral[2]);

        int[] full = ColorTemp.warmthWhitePoint(1f);
        int[] amber = ColorTemp.kelvinToRgb(ColorTemp.WARM_K);
        assertEquals(amber[0], full[0]);
        assertEquals(amber[1], full[1]);
        assertEquals(amber[2], full[2]);
    }

    @Test
    public void punktBieliMonotonicznyZGlebia() {
        int prevG = 256, prevB = 256;
        for (int i = 0; i <= 20; i++) {
            int[] w = ColorTemp.warmthWhitePoint(i / 20f);
            assertTrue("kanal G nie rosnie", w[1] <= prevG);
            assertTrue("kanal B nie rosnie", w[2] <= prevB);
            assertTrue("kanal G w zakresie", w[1] >= 167);
            assertTrue("kanal B w zakresie", w[2] >= 87);
            prevG = w[1];
            prevB = w[2];
        }
    }

    @Test
    public void bialyTrafiaWPunktBieliCelu() {
        // Caly zakres suwaka, gesto: zaokraglenia nie moga nikogo zdradzic
        // przy zadnej pozycji (glebia = sila/80, sila 5..80).
        for (int i = 1; i <= 80; i++) {
            float f = i / 80f;
            int[] t = ColorTemp.overlayTint(f);
            int[] w = ColorTemp.warmthWhitePoint(f);
            assertTrue("alpha > 0 dla f=" + f, t[0] >= 1);
            assertEquals("kanal R nasycony", 255, t[1]);
            int rg = Math.round((255 * (255 - t[0]) + t[2] * t[0]) / 255f);
            int rb = Math.round((255 * (255 - t[0]) + t[3] * t[0]) / 255f);
            assertTrue("bialy G=" + rg + " ma trafic w " + w[1] + " (f=" + f + ")",
                    Math.abs(rg - w[1]) <= 1);
            assertTrue("bialy B=" + rb + " ma trafic w " + w[2] + " (f=" + f + ")",
                    Math.abs(rb - w[2]) <= 1);
        }
    }

    @Test
    public void alphaZanikaDoZerIJestMonotoniczna() {
        assertEquals(0, ColorTemp.overlayTint(0f)[0]);
        int prev = -1;
        for (int i = 0; i <= 20; i++) {
            int[] t = ColorTemp.overlayTint(i / 20f);
            assertTrue("alpha monotoniczna", t[0] >= prev);
            assertTrue("alpha w 0..255", t[0] >= 0 && t[0] <= 255);
            prev = t[0];
        }
        // maksimum: 0.70f * 255f LICZONE W FLOAT zaokragla sie do DOKLADNIE
        // 178.5f (float nie przechowuje 0.70 dokladnie), a Math.round(float)
        // liczy half-up -> 179. W double wyszloby 0.70*255=178.5 -> tez 179.
        // Nie "poprawiac" tej asercji na 178 po przeliczeniu w glowie w double.
        assertEquals(179, ColorTemp.overlayTint(1f)[0]);
    }

    @Test
    public void kolorNakladkiJestNasyconty() {
        // To jest roznica miedzy "zolty jak iOS" a "blady": przy typowym
        // ustawieniu (sila 40 -> f=0.5) kanaly koloru maja byc gleboko
        // sciete, a nie ledwo odjete jak przy liniowym Kelvinie.
        int[] t = ColorTemp.overlayTint(0.5f);
        assertEquals(255, t[1]);
        assertTrue("kanal G nasycony: " + t[2], t[2] > 120 && t[2] < 145);
        assertTrue("kanal B gleboko sciete: " + t[3], t[3] < 40);
    }
}
