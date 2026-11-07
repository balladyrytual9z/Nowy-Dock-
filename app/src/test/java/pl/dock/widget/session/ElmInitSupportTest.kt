package pl.dock.widget.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Straznik czystej logiki initu ELM327 (JVM, bez Androida).
 * CI: krok "Run Unit Tests" (gradle test). Dane deterministyczne -
 * zero Random (konwencja repo).
 */
class ElmInitSupportTest {

    // --- stripNoise ------------------------------------------------------

    @Test
    fun stripNoiseUsuwaSearching() {
        assertNull(ElmInitSupport.stripNoise("SEARCHING..."))
        assertNull(ElmInitSupport.stripNoise("searching"))
        assertNull(ElmInitSupport.stripNoise("SEARCHING........"))
        assertNull(ElmInitSupport.stripNoise("   "))
    }

    @Test
    fun stripNoiseUsuwaBusInit() {
        assertNull(ElmInitSupport.stripNoise("BUS INIT"))
        assertNull(ElmInitSupport.stripNoise("BUS INIT: ..."))
        assertNull(ElmInitSupport.stripNoise("STOPPED"))
    }

    @Test
    fun stripNoiseZachowujeRamke() {
        assertEquals("7E8 03 41 0C 1A F8", ElmInitSupport.stripNoise("7E8 03 41 0C 1A F8"))
        assertEquals("OK", ElmInitSupport.stripNoise("OK"))
        assertEquals("12.6V", ElmInitSupport.stripNoise("12.6V"))
        assertEquals("ELM327 v2.1", ElmInitSupport.stripNoise("ELM327 v2.1"))
    }

    // --- isEcuSilence / probeOk ------------------------------------------

    @Test
    fun ecuSilenceRozpoznajeBledy() {
        assertTrue(ElmInitSupport.isEcuSilence(null))
        assertTrue(ElmInitSupport.isEcuSilence(""))
        assertTrue(ElmInitSupport.isEcuSilence("?"))
        assertTrue(ElmInitSupport.isEcuSilence("NO DATA"))
        assertTrue(ElmInitSupport.isEcuSilence("UNABLE TO CONNECT"))
        assertTrue(ElmInitSupport.isEcuSilence("CAN ERROR"))
        assertTrue(ElmInitSupport.isEcuSilence("BUS ERROR"))
        assertTrue(ElmInitSupport.isEcuSilence("BUFFER FULL"))
    }

    @Test
    fun ecuSilenceNieKlasyfikujeDanych() {
        assertFalse(ElmInitSupport.isEcuSilence("7E8 06 41 00 BE 3E B8 11"))
        assertFalse(ElmInitSupport.isEcuSilence("OK"))
    }

    @Test
    fun probeOkWymagaMaskiPid00() {
        // poprawna odpowiedz Hondy na 0100 (ATH1, CAN 500k)
        assertTrue(ElmInitSupport.probeOk("7E8 06 41 00 BE 3E B8 11"))
        // bez spacji (ATL0/parsery czyszacza) tez musi przejsc
        assertTrue(ElmInitSupport.probeOk("4100BE3EB811"))
        // porazki
        assertFalse(ElmInitSupport.probeOk("NO DATA"))
        assertFalse(ElmInitSupport.probeOk("UNABLE TO CONNECT"))
        assertFalse(ElmInitSupport.probeOk(null))
        // ramka z innym PID nie jest odpowiedzia na sonde
        assertFalse(ElmInitSupport.probeOk("7E8 03 41 0C 1A F8"))
    }

    // --- parseDpn / protocolName -----------------------------------------

    @Test
    fun dpnParsujeCyfreIAuto() {
        assertEquals("6" to false, ElmInitSupport.parseDpn("6"))
        assertEquals("6" to true, ElmInitSupport.parseDpn("A6"))
        assertEquals("3" to true, ElmInitSupport.parseDpn("a3"))
        assertEquals("9" to false, ElmInitSupport.parseDpn("9"))
        assertNull(ElmInitSupport.parseDpn(null))
        assertNull(ElmInitSupport.parseDpn(""))
    }

    @Test
    fun protocolNameZwracaNazweCan500k() {
        assertEquals("ISO 15765-4 CAN 11-bit 500k", ElmInitSupport.protocolName("6"))
        assertEquals("ISO 9141-2 (K-Line)", ElmInitSupport.protocolName("3"))
        assertEquals("auto", ElmInitSupport.protocolName("0"))
    }

    // --- parseVoltage ------------------------------------------------------

    @Test
    fun voltageParsujeAtrv() {
        assertEquals(12.6f, ElmInitSupport.parseVoltage("12.6V"), 0.001f)
        assertEquals(14.2f, ElmInitSupport.parseVoltage("14.2V"), 0.001f)
        // banner adapteru (ATI) nie moze byc wzienty jako napiecie
        assertTrue(ElmInitSupport.parseVoltage("ELM327 v2.1").isNaN())
        assertTrue(ElmInitSupport.parseVoltage(null).isNaN())
        assertTrue(ElmInitSupport.parseVoltage("OK").isNaN())
    }

    // --- isSlowDiagnostic ---------------------------------------------------

    @Test
    fun slowDiagnosticRozdzielaRureOdDiagnostyki() {
        // rura PID - szybka
        assertFalse(ElmInitSupport.isSlowDiagnostic("010C"))
        assertFalse(ElmInitSupport.isSlowDiagnostic("010D"))
        assertFalse(ElmInitSupport.isSlowDiagnostic("0101"))
        // wolne tryby Hondy (Keihin)
        assertTrue(ElmInitSupport.isSlowDiagnostic("03"))
        assertTrue(ElmInitSupport.isSlowDiagnostic("04"))
        assertTrue(ElmInitSupport.isSlowDiagnostic("06"))
        assertTrue(ElmInitSupport.isSlowDiagnostic("07"))
        assertTrue(ElmInitSupport.isSlowDiagnostic("0902"))
        assertTrue(ElmInitSupport.isSlowDiagnostic("0A"))
        assertTrue(ElmInitSupport.isSlowDiagnostic("2101"))
        // AT-zlecenia nigdy
        assertFalse(ElmInitSupport.isSlowDiagnostic("ATRV"))
        assertFalse(ElmInitSupport.isSlowDiagnostic("ATZ"))
    }
}
