package pl.dock.widget.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testy skanera ELM327 wspoldzielonego przez PidScheduler i ConnectionManager.
 * Uruchamiane w CI krokiem "Run Unit Tests" (gradle test).
 */
class ElmHexTest {

    @Test
    fun nibParsesLowerCaseAndUpperCaseHex() {
        assertEquals(0, ElmHex.nib('0'))
        assertEquals(9, ElmHex.nib('9'))
        assertEquals(10, ElmHex.nib('A'))
        assertEquals(15, ElmHex.nib('F'))
        assertEquals(10, ElmHex.nib('a'))
        assertEquals(15, ElmHex.nib('f'))
    }

    @Test
    fun nibRejectsNonHex() {
        assertEquals(-1, ElmHex.nib('G'))
        assertEquals(-1, ElmHex.nib(' '))
        assertEquals(-1, ElmHex.nib('>'))
    }

    @Test
    fun scanAbReadsCanFrameWithHeader() {
        // 7E8 03 41 0C 1A F8 -> ((0x1A*256)+0xF8) = 6904
        assertEquals(6904, ElmHex.scanAb("7E8 03 41 0C 1A F8", 4, 1, 0, 0xC))
    }

    @Test
    fun scanAbIgnoresWhitespaceAndPrompt() {
        assertEquals(6904, ElmHex.scanAb("7E8 04 41 0C 1A F8\r\n>", 4, 1, 0, 0xC))
    }

    @Test
    fun scanAbReturnsMinusOneWhenPidMissing() {
        assertEquals(-1, ElmHex.scanAb("7E8 03 41 0D 00 00", 4, 1, 0, 0xC))
        assertEquals(-1, ElmHex.scanAb("", 4, 1, 0, 0xC))
    }

    @Test
    fun scanAbReturnsMinusOneOnTruncatedPayload() {
        // naglowek obecny, ale tylko 1 bajt danych po nim
        assertEquals(-1, ElmHex.scanAb("7E8 03 41 0C 1A", 4, 1, 0, 0xC))
    }

    @Test
    fun scanAbReturnsMinusOneOnNonHexAfterHeader() {
        assertEquals(-1, ElmHex.scanAb("7E8 03 41 0C 1A Z8", 4, 1, 0, 0xC))
    }

    @Test
    fun scanAbReadsSecondFrameWhenFirstIsNoise() {
        // pierwsza ramka inny PID, druga szukany RPM
        assertEquals(6904, ElmHex.scanAb("7E8 03 41 0D 00 00\n7E8 03 41 0C 1A F8", 4, 1, 0, 0xC))
    }
}
