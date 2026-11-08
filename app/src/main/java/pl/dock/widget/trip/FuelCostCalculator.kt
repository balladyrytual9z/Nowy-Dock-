package pl.dock.widget.trip

/**
 * Szacunkowy kalkulator zuzycia paliwa i kosztu podrozy.
 * Zrodlo danych: PID 0110 (MAF, g/s) z telemetrii OBD.
 * Zuzycie liczzone ze stechiometrii benzyny: 14.7 g powietrza
 * na 1 g paliwa, gestosc benzyny 745 g/l (dokumenty OBD:
 * FAZA 4 "Trip Financial & Fuel Calculator", obd part 2 #13 i #31).
 *
 * Wszystkie wyniki sa ESTYMACJA (ECU L13Z1 nie udostepnia
 * wtrysku paliwa jako PID) - UI oznacza je "(szac.)".
 *
 * Tryb hybrydowy (#31): w jezdzie L/100 km, na postoju L/h
 * (dzielenie przez zero przy speed = 0 wykluczone z definicji).
 */
class FuelCostCalculator {

    data class Instant(
        /** L/100 km - sensowne tylko w ruchu (idle = false). */
        val lPer100km: Double,
        /** L/h - aktualne na postoju, w ruchu pomocnicze. */
        val lPerHour: Double,
        /** true = pojazd stoi, UI pokazuje L/h. */
        val idle: Boolean
    )

    companion object {
        /** Stechiometria benzyny [g powietrza / g paliwa]. */
        const val AIR_FUEL_RATIO = 14.7
        /** Gestosc benzyny [g/l]. */
        const val FUEL_DENSITY_G_PER_L = 745.0
        /** Powyzej tej predkosci UI pokazuje L/100 km. */
        const val MIN_MOVING_KMH = 3
        /** Przerwa w telemetrii powyzej tego progu nie sumuje paliwa. */
        const val MAX_SAMPLE_GAP_MS = 3000L
    }

    var totalLiters = 0.0
        private set
    var lastSampleMs = 0L
        private set

    /** Przelicz przeplyw powietrza [g/s] na litry paliwa na godzine. */
    fun litersPerHour(mafGs: Float): Double {
        if (mafGs.isNaN() || mafGs <= 0f) return 0.0
        return mafGs * 3600.0 / (AIR_FUEL_RATIO * FUEL_DENSITY_G_PER_L)
    }

    /** Chwilowe zuzycie: L/100 km w jezdzie, L/h na postoju. */
    fun instant(mafGs: Float, speedKmh: Int): Instant {
        val lph = litersPerHour(mafGs)
        return if (speedKmh >= MIN_MOVING_KMH) {
            Instant(lph * 100.0 / speedKmh, lph, false)
        } else {
            Instant(0.0, lph, true)
        }
    }

    /**
     * Doloz jedna probke telemetrii do calki zuzycia.
     * dt liczone od poprzedniej probki; przerwy dluzsze niz
     * MAX_SAMPLE_GAP_MS (gap w telemetrii) nie dodaja paliwa,
     * zeby nie liczyc zuzycia po utracie polaczenia.
     * MAF = NaN (PID nieodpowiedziany) - bez fabrykacji.
     */
    fun integrate(mafGs: Float, nowMs: Long) {
        if (mafGs.isNaN()) {
            lastSampleMs = nowMs
            return
        }
        val prev = lastSampleMs
        if (prev != 0L) {
            val dt = nowMs - prev
            if (dt in 1..MAX_SAMPLE_GAP_MS) {
                totalLiters += litersPerHour(mafGs) * dt / 3_600_000.0
            }
        }
        lastSampleMs = nowMs
    }

    /** Srednie zuzycie [L/100 km] na dystansie [m]; NaN przy < 100 m. */
    fun avgLitersPer100km(distanceM: Double): Double {
        if (distanceM < 100.0) return Double.NaN
        return totalLiters * 100_000.0 / distanceM
    }

    /** Koszt [PLN] przy cenie [gr/l]; 0.0 gdy cena nieustawiona. */
    fun costPln(priceGrPerL: Int): Double {
        if (priceGrPerL <= 0) return 0.0
        return totalLiters * priceGrPerL / 100.0
    }

    fun reset() {
        totalLiters = 0.0
        lastSampleMs = 0L
    }
}
