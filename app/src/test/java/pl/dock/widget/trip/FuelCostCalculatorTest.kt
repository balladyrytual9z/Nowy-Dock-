package pl.dock.widget.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy szacunkowego kalkulatora paliwa (MAF -> L/h -> L/100 km -> PLN).
 * Dokumenty OBD: FAZA 4 "Trip Financial & Fuel Calculator",
 * obd part 2 #13 (stechiometria 14.7, gestosc 745 g/l) i #31 (hybryda
 * L/100 km w jezdzie / L/h na postoju). Uruchamiane w CI (gradle test).
 */
class FuelCostCalculatorTest {

    @Test
    fun litersPerHourZeroOnNoData() {
        val c = FuelCostCalculator()
        assertEquals(0.0, c.litersPerHour(Float.NaN), 1e-9)
        assertEquals(0.0, c.litersPerHour(0f), 1e-9)
        assertEquals(0.0, c.litersPerHour(-3f), 1e-9)
    }

    @Test
    fun litersPerHourFromMaf() {
        val c = FuelCostCalculator()
        // 2 g/s -> 2*3600/(14.7*745) = okolo 0.657 L/h (bieg jalowy)
        assertEquals(0.65746, c.litersPerHour(2f), 0.001)
        // 10 g/s -> okolo 3.287 L/h
        assertEquals(3.28732, c.litersPerHour(10f), 0.001)
    }

    @Test
    fun instantIsIdleBelowMovingThreshold() {
        val c = FuelCostCalculator()
        val i = c.instant(3f, 0)
        assertTrue(i.idle)
        assertEquals(c.litersPerHour(3f), i.lPerHour, 1e-9)
        assertTrue(c.instant(3f, 2).idle)   // 2 km/h to nadal postoj
        assertTrue(!c.instant(3f, 3).idle)   // prog = 3 km/h
    }

    @Test
    fun instantIsLPer100kmWhenMoving() {
        val c = FuelCostCalculator()
        val i = c.instant(10f, 50)
        assertTrue(!i.idle)
        assertEquals(c.litersPerHour(10f) * 100.0 / 50.0, i.lPer100km, 1e-9)
    }

    @Test
    fun integrateAccumulatesFuel() {
        val c = FuelCostCalculator()
        c.integrate(2f, 1_000)
        c.integrate(2f, 2_000)   // dt = 1 s
        assertEquals(c.litersPerHour(2f) / 3600.0, c.totalLiters, 1e-12)
    }

    @Test
    fun integrateIgnoresLongGaps() {
        val c = FuelCostCalculator()
        c.integrate(2f, 1_000)
        c.integrate(2f, 10_000)   // gap 9 s > 3 s - bez doliczania
        assertEquals(0.0, c.totalLiters, 1e-12)
    }

    @Test
    fun integrateSkipsNaNMaf() {
        val c = FuelCostCalculator()
        c.integrate(2f, 1_000)
        c.integrate(Float.NaN, 2_000)   // brak danych - bez fabrykacji
        assertEquals(0.0, c.totalLiters, 1e-12)
    }

    @Test
    fun avgLitersPer100kmOnDistance() {
        val c = FuelCostCalculator()
        c.integrate(2f, 1_000)
        c.integrate(2f, 2_000)
        val expected = c.litersPerHour(2f) / 3600.0 * 100_000.0 / 1_000.0
        assertEquals(expected, c.avgLitersPer100km(1_000.0), 1e-12)
        assertTrue(c.avgLitersPer100km(99.0).isNaN())   // < 100 m - brak sredniej
    }

    @Test
    fun costPlnFromPriceGrPerLiter() {
        val c = FuelCostCalculator()
        assertEquals(0.0, c.costPln(0), 1e-12)   // cena nieustawiona
        c.integrate(2f, 1_000)
        c.integrate(2f, 2_000)
        assertEquals(c.totalLiters * 6.5, c.costPln(650), 1e-12)
    }

    @Test
    fun resetClearsState() {
        val c = FuelCostCalculator()
        c.integrate(2f, 1_000)
        c.reset()
        assertEquals(0.0, c.totalLiters, 0.0)
        assertEquals(0L, c.lastSampleMs)
    }
}
