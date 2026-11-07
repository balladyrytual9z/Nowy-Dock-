package pl.dock.widget.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy deterministyczne modulu analitycznego (pkt 19 + zasady pkt 45):
 * brak fabrykacji - za malo danych to status BRAK, nie zmyslony raport.
 */
class CorrelationAnalyzerTest {

    @Test
    fun dodatniaKorelacjaDajeRaportInfo() {
        val load = ArrayList<Pair<Long, Float>>()
        val ltft = ArrayList<Pair<Long, Float>>()
        for (i in 0 until 40) {
            val t = 1000L * i
            load.add(Pair(t, i.toFloat()))
            ltft.add(Pair(t, i.toFloat() * 0.5f))
        }
        val rep = CorrelationAnalyzer.analyze(mapOf("LOAD" to load, "LTFT" to ltft))
        assertEquals(CorrelationAnalyzer.STATUS_INFO, rep.status)
        assertTrue(rep.text.contains("dodatnia"))
        assertTrue(rep.text.contains("r=1.00"))
    }

    @Test
    fun ujemnaKorelacjaJestOpisana() {
        val a = ArrayList<Pair<Long, Float>>()
        val b = ArrayList<Pair<Long, Float>>()
        for (i in 0 until 30) {
            val t = 100L * i
            a.add(Pair(t, i.toFloat()))
            b.add(Pair(t, -i.toFloat()))
        }
        val rep = CorrelationAnalyzer.analyze(mapOf("LOAD" to a, "LTFT" to b))
        assertTrue(rep.text.contains("ujemna"))
        assertTrue(rep.text.contains("r=-1.00"))
    }

    @Test
    fun zaMaloProbekDajeStatusBrak() {
        val a = ArrayList<Pair<Long, Float>>()
        for (i in 0 until 5) a.add(Pair(100L * i, i.toFloat()))
        val rep = CorrelationAnalyzer.analyze(mapOf("LOAD" to a))
        assertEquals(CorrelationAnalyzer.STATUS_NO_DATA, rep.status)
        assertFalse(rep.text.contains("Korelacja"))
    }

    @Test
    fun seriaStalaNieDajeKorelacji() {
        val a = ArrayList<Pair<Long, Float>>()
        val b = ArrayList<Pair<Long, Float>>()
        for (i in 0 until 30) {
            val t = 100L * i
            a.add(Pair(t, 5f))
            b.add(Pair(t, i.toFloat()))
        }
        val rep = CorrelationAnalyzer.analyze(mapOf("LOAD" to a, "LTFT" to b))
        assertEquals(CorrelationAnalyzer.STATUS_NO_DATA, rep.status)
    }

    @Test
    fun pearsonZerowaWariancjaToNaN() {
        val r = CorrelationAnalyzer.pearson(
            floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 2f, 3f))
        assertTrue(r.isNaN())
    }

    @Test
    fun polskieNazwyParametrowIstnieja() {
        assertTrue(CorrelationAnalyzer.plName("LOAD").contains("obciazenie"))
        assertEquals("XYZ", CorrelationAnalyzer.plName("XYZ"))
    }
}
