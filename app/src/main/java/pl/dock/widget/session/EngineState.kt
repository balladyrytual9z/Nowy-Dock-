package pl.dock.widget.session

import pl.dock.widget.repository.EngineStateLabel

/**
 * Maszyna stanow silnika (pkt 35 + 44.3 dokumentu "ui obd.txt").
 *
 * Reguly zrodlowe z dokumentu (pkt 35) - to NIE uniwersalny standard
 * wykrywania pracy silnika, dlatego progi sa parametrami konstruktora
 * (domyslnie wartosci z dokumentu):
 *  - Engine OFF: RPM == 0 oraz napiecie < 12.4 V, utrzymane > 2 s;
 *  - Engine RUNNING: RPM > 500 oraz napiecie > 13.2 V.
 *
 * Dodatkowo (wczesniejsze zachowania, uzywane przez PidScheduler
 * i watchdog ACC w ObdCoreService):
 *  - CRANKING: RPM 1..450 (rozruch) - blokada PID przy crankingProtect;
 *  - PROTECTED: napiecie < 11.0 V (ochrona rozladowanego akumulatora).
 *
 * Hold 2 s zapobiega falszywemu OFF przy chwilowym zerwaniu odpowiedzi
 * ECU - bez fabrykacji stanu (pkt 45).
 */
class EngineState(
    /** Doc pkt 35: OFF przy napieciu < offMaxVoltage. */
    private val offMaxVoltage: Float = 12.4f,
    /** Doc pkt 35: OFF wymaga utrzymania warunkow przez offHoldMs. */
    private val offHoldMs: Long = 2_000L,
    /** Doc pkt 35: RUNNING przy RPM > runningMinRpm. */
    private val runningMinRpm: Int = 500,
    /** Doc pkt 35: RUNNING przy napieciu > runningMinVoltage. */
    private val runningMinVoltage: Float = 13.2f,
    /** Zegar wstrzykiwany - deterministyczne testy JVM. */
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    @Volatile var voltage: Float = Float.NaN
        private set
    @Volatile var rpm: Int = -1
        private set
    @Volatile var label: EngineStateLabel = EngineStateLabel.UNKNOWN
        private set

    /** True when PID polling must be suppressed. */
    @Volatile var protected: Boolean = false
        private set

    /**
     * Ustawienie "cranking_protect": true = blokada PID w trakcie rozruchu
     * (odczyt z prefs "obd_settings" przy polaczeniu).
     */
    @Volatile var crankingProtect: Boolean = true

    /** Start ciaglego spelniania warunkow OFF (0 = niespelnione). */
    private var offSince: Long = 0L

    fun updateVoltage(v: Float) {
        voltage = v
        recompute()
    }

    fun updateRpm(r: Int) {
        rpm = r
        recompute()
    }

    private fun recompute() {
        val lowVolt = !voltage.isNaN() && voltage < 11.0f
        val cranking = rpm in 1..450
        val running = rpm > runningMinRpm && !voltage.isNaN() && voltage > runningMinVoltage
        val offConditions = rpm == 0 && !voltage.isNaN() && voltage < offMaxVoltage
        val now = clock()

        // hold: OFF dopiero po offHoldMs ciaglych warunkow (pkt 35)
        if (offConditions) {
            if (offSince == 0L) offSince = now
        } else {
            offSince = 0L
        }
        val off = offConditions && offSince != 0L && now - offSince >= offHoldMs

        when {
            cranking -> {
                protected = crankingProtect
                label = EngineStateLabel.CRANKING
            }
            lowVolt -> {
                protected = true
                label = EngineStateLabel.PROTECTED
            }
            running -> {
                protected = false
                label = EngineStateLabel.RUNNING
            }
            off -> {
                protected = false
                label = EngineStateLabel.OFF
            }
            else -> {
                // przejsciowe / hold w trakcie: bez zmiany etykiety
                // (UI nie zgaduje stanu silnika)
            }
        }
    }

    fun reset() {
        voltage = Float.NaN
        rpm = -1
        label = EngineStateLabel.UNKNOWN
        protected = false
        offSince = 0L
    }
}
