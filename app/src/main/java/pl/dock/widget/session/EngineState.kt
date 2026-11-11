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
    /**
     * NAPRAWA zawieszania zegarow (2026-11-09): Hondy spokojnie stoja na
     * 12.4-12.6 V, wiec dokumentowy prog OFF (< 12.4 V) nie opadal po
     * zgaszeniu silnika. Fallback: samo RPM == 0 utrzymane przez
     * zeroRpmHoldMs (ECU raportuje zero obrotow = silnik nie pracuje).
     */
    private val zeroRpmHoldMs: Long = 10_000L,
    /**
     * NAPRAWA zawieszania zegarow: ELM zywy, ECU nie odpowiada ("NO DATA") -
     * po noDataHoldMs bez WAZNEGO odczytu RPM label RUNNING spada do UNKNOWN
     * zamiast trzymac zamrozony stan (brak fabrykacji danych).
     */
    private val noDataHoldMs: Long = 15_000L,
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
    /** Start ciaglego RPM == 0 (0 = nie dotyczy). */
    private var zeroRpmSince: Long = 0L
    /** Czas ostatniego WAZNEGO odczytu RPM (0 = brak odczytu). */
    private var lastValidRpmMs: Long = 0L
    /** Start biezacej blokady PID (ochrona) - zawor bezpieczenstwa. */
    private var protectedSince: Long = 0L

    fun updateVoltage(v: Float) {
        voltage = v
        recompute()
    }

    fun updateRpm(r: Int) {
        rpm = r
        lastValidRpmMs = clock()
        recompute()
    }

    /**
     * NAPRAWA zawieszania zegarow (2026-11-09): ELM odpowiada, ale ECU milczy
     * ("NO DATA" na 010C). Po noDataHoldMs bez waznego RPM label RUNNING
     * spada do UNKNOWN - zegary dostaja "brak danych", nie zamrozona wartosc.
     * Wywolane z ConnectionManager przy 3x nieparsowalnym 010C i przy stale.
     */
    fun onRpmNoData() {
        val now = clock()
        if (lastValidRpmMs != 0L &&
            now - lastValidRpmMs >= noDataHoldMs &&
            (label == EngineStateLabel.RUNNING || label == EngineStateLabel.CRANKING)
        ) {
            label = EngineStateLabel.UNKNOWN
            rpm = -1
            protected = false
            offSince = 0L
            zeroRpmSince = 0L
            lastValidRpmMs = 0L
        }
    }

    private fun recompute() {
        val lowVolt = !voltage.isNaN() && voltage < 11.0f
        val cranking = rpm in 1..450
        val running = rpm > runningMinRpm && !voltage.isNaN() && voltage > runningMinVoltage
        val offConditions = rpm == 0 && !voltage.isNaN() && voltage < offMaxVoltage
        val now = clock()

        // Zawor bezpieczenstwa (2026-11-11): blokada PID (rozruch/niskie
        // napiecie) nie moze trwac w nieskonczonosc - bez odczytow nie ma
        // czym jej zdjac. Po 20 s odpuszczamy automatycznie.
        if (protected && protectedSince != 0L && now - protectedSince > 20_000L) {
            protected = false
            protectedSince = 0L
        }

        // hold: OFF dopiero po offHoldMs ciaglych warunkow (pkt 35)
        if (offConditions) {
            if (offSince == 0L) offSince = now
        } else {
            offSince = 0L
        }
        val docOff = offConditions && offSince != 0L && now - offSince >= offHoldMs

        // NAPRAWA zawieszania zegarow: fallback RPM == 0 bez wzgledu na
        // napiecie (dokumentowy prog 12.4 V nie lapal Hondy stojacej na 12.5 V)
        if (rpm == 0) {
            if (zeroRpmSince == 0L) zeroRpmSince = now
        } else {
            zeroRpmSince = 0L
        }
        val zeroRpmOff = zeroRpmSince != 0L && now - zeroRpmSince >= zeroRpmHoldMs
        val off = docOff || zeroRpmOff

        when {
            cranking -> {
                if (!protected) protectedSince = now
                protected = crankingProtect
                label = EngineStateLabel.CRANKING
            }
            lowVolt -> {
                if (!protected) protectedSince = now
                protected = true
                label = EngineStateLabel.PROTECTED
            }
            running -> {
                protected = false
                protectedSince = 0L
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
        protectedSince = 0L
        offSince = 0L
        zeroRpmSince = 0L
        lastValidRpmMs = 0L
    }
}
