package pl.dock.widget.session

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.dock.widget.repository.EngineStateLabel

/**
 * Testy deterministyczne maszyny stanow silnika (pkt 35):
 * hold 2 s, progi 12.4 V / 500 RPM / 13.2 V, brak fabrykacji stanu.
 */
class EngineStateTest {

    @Test
    fun offDopieroPoDwochSekundachHoldu() {
        var now = 10_000L
        val es = EngineState(clock = { now })
        es.updateVoltage(12.0f)
        es.updateRpm(0)
        // hold 2 s nie minal - UI nie zgaduje stanu
        assertEquals(EngineStateLabel.UNKNOWN, es.label)
        now += 2_500L
        es.updateRpm(0)
        assertEquals(EngineStateLabel.OFF, es.label)
    }

    @Test
    fun runningWgProgowZDokumentu() {
        var now = 1_000L
        val es = EngineState(clock = { now })
        es.updateVoltage(13.5f)
        es.updateRpm(550)
        assertEquals(EngineStateLabel.RUNNING, es.label)
        es.updateRpm(600)
        assertEquals(EngineStateLabel.RUNNING, es.label)
    }

    @Test
    fun crankingIRozruchNieSaOFF() {
        var now = 1_000L
        val es = EngineState(clock = { now })
        es.updateVoltage(12.0f)
        es.updateRpm(300)
        assertEquals(EngineStateLabel.CRANKING, es.label)
        now += 5_000L
        es.updateRpm(300)
        assertEquals(EngineStateLabel.CRANKING, es.label)
    }

    @Test
    fun niskieNapiecieDajePROTECTED() {
        val es = EngineState()
        es.updateVoltage(10.5f)
        es.updateRpm(800)
        assertEquals(EngineStateLabel.PROTECTED, es.label)
        assertEquals(true, es.protected)
    }

    @Test
    fun holdPrzerwanyRozruchemStartujeOdNowa() {
        var now = 10_000L
        val es = EngineState(clock = { now })
        es.updateVoltage(12.0f)
        es.updateRpm(0)
        now += 1_000L
        es.updateRpm(900)   // warunki OFF przerwane - licznik holdu zerowany
        now += 500L
        es.updateRpm(0)
        now += 1_500L       // tylko 1.5 s od restartu - jeszcze nie OFF
        es.updateRpm(0)
        assertEquals(EngineStateLabel.UNKNOWN, es.label)
        now += 600L          // 2.1 s - teraz OFF
        es.updateRpm(0)
        assertEquals(EngineStateLabel.OFF, es.label)
    }

    @Test
    fun runningPoOFFZerujeHold() {
        var now = 10_000L
        val es = EngineState(clock = { now })
        es.updateVoltage(12.0f)
        es.updateRpm(0)
        now += 3_000L
        es.updateRpm(0)
        assertEquals(EngineStateLabel.OFF, es.label)
        es.updateVoltage(13.8f)
        es.updateRpm(900)
        assertEquals(EngineStateLabel.RUNNING, es.label)
    }

    // --- NAPRAWA zawieszania zegarow (2026-11-09) ---

    @Test
    fun zeroRpmFallbackOFFBezWzgleduNaNapiecie() {
        var now = 10_000L
        val es = EngineState(clock = { now })
        es.updateVoltage(12.8f)   // powyzej progu 12.4 V - dokumentowy OFF nie zadziala
        es.updateRpm(0)
        now += 5_000L
        es.updateRpm(0)
        assertEquals(EngineStateLabel.UNKNOWN, es.label)   // hold 10 s nie minal
        now += 6_000L
        es.updateRpm(0)
        assertEquals(EngineStateLabel.OFF, es.label)       // 11 s RPM == 0 -> OFF
    }

    @Test
    fun brakDanychRPMZdejmujeRUNNINGPoHoldzie() {
        var now = 1_000L
        val es = EngineState(clock = { now })
        es.updateVoltage(13.6f)
        es.updateRpm(900)
        assertEquals(EngineStateLabel.RUNNING, es.label)
        now += 14_000L
        es.onRpmNoData()
        assertEquals(EngineStateLabel.RUNNING, es.label)  // 14 s < 15 s holdu
        now += 2_000L
        es.onRpmNoData()
        assertEquals(EngineStateLabel.UNKNOWN, es.label)   // 16 s bez waznego RPM
        assertEquals(-1, es.rpm)
        assertEquals(false, es.protected)
    }

    @Test
    fun brakDanychZdejmujePROTECTEDCrankingu() {
        var now = 1_000L
        val es = EngineState(clock = { now })
        es.crankingProtect = true
        es.updateVoltage(12.0f)
        es.updateRpm(300)
        assertEquals(EngineStateLabel.CRANKING, es.label)
        assertEquals(true, es.protected)
        now += 16_000L
        es.onRpmNoData()
        assertEquals(EngineStateLabel.UNKNOWN, es.label)
        assertEquals(false, es.protected)
    }

    @Test
    fun rozruchPrzerywaLicznikZeroRpm() {
        var now = 10_000L
        val es = EngineState(clock = { now })
        es.updateVoltage(12.8f)
        es.updateRpm(0)
        now += 8_000L
        es.updateRpm(0)
        assertEquals(EngineStateLabel.UNKNOWN, es.label)
        es.updateRpm(900)   // start silnika - licznik 10 s przerwany
        now += 2_000L
        es.updateRpm(0)     // znowu stoi - licznik od zera
        now += 9_000L
        es.updateRpm(0)
        assertEquals(EngineStateLabel.UNKNOWN, es.label)   // 9 s < 10 s
        now += 2_000L
        es.updateRpm(0)
        assertEquals(EngineStateLabel.OFF, es.label)
    }
}
