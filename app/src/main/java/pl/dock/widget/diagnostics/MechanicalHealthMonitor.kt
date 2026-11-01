package pl.dock.widget.diagnostics

import android.content.SharedPreferences
import android.util.Log

/**
 * Mechanical health heuristics for Honda Civic VIII 1.4 i-VTEC:
 *
 *  1. Clutch Slip Detector - expected RPM = VSS * learned ratio;
 *     TPS >= 80 % and (real - expected) >= 450 RPM -> warning.
 *  2. Tight intake valves - idle (RPM 600-850), ECT > 80 C and
 *     LTFT >= 7.5 % sustained -> warning.
 *  3. ELD / alternator degeneration - multi-day average system voltage
 *     drop > 0.4 V -> warning.
 *  4. Eco-Driving Smoothness Index - pochodna polozenia przepustnicy
 *     (dThrottle/dt); gwaltowne szarpniecia (> 60 %/s przez 3 probki)
 *     -> ostrzezenie o nierownej jezdzie.
 */
class MechanicalHealthMonitor(private val prefs: SharedPreferences) {

    companion object {
        private const val TAG = "MechHealth"
        private const val KEY_ELD_BASE = "eld_base_avg"
        private const val KEY_ELD_DAY = "eld_day"
        private const val KEY_ELD_SUM = "eld_sum"
        private const val KEY_ELD_N = "eld_n"

        private const val SLIP_TPS_PCT = 80f
        private const val SLIP_RPM_DELTA = 450
        private const val VALVE_LTFT_PCT = 7.5f
        private const val ELD_DROP_V = 0.4f

        /** Eco smoothness: prog pochodnej TPS [%/s]. */
        private const val SMOOTH_JERK_PPS = 60f
        private const val SMOOTH_HITS = 3
        private const val SMOOTH_RATELIMIT_MS = 60_000L
    }

    data class Warning(val kind: Kind, val message: String)

    enum class Kind { CLUTCH_SLIP, TIGHT_VALVES, ALTERNATOR, ECO_SMOOTHNESS }

    // --- clutch slip -------------------------------------------------------
    @Volatile private var learnedRatio: Float = -1f   // RPM per km/h (highest gear)

    // --- tight valves ------------------------------------------------------
    private var valveHits = 0
    private var valveWarnedMs = 0L

    // --- ELD ---------------------------------------------------------------
    private var eldDay = -1
    private var eldSum = 0.0
    private var eldCount = 0
    private var eldBaseAvg = 0.0

    // --- eco smoothness ----------------------------------------------------
    private var prevTps = Float.NaN
    private var prevMs = 0L
    private var jerkHits = 0
    private var smoothWarnedMs = 0L

    init {
        eldBaseAvg = prefs.getFloat(KEY_ELD_BASE, 0f).toDouble()
        eldDay = prefs.getInt(KEY_ELD_DAY, -1)
        eldSum = prefs.getFloat(KEY_ELD_SUM, 0f).toDouble()
        eldCount = prefs.getInt(KEY_ELD_N, 0)
    }

    /**
     * Feed one telemetry sample. Returns warnings raised by this sample
     * (each warning kind is rate-limited internally).
     */
    @Synchronized
    fun onTelemetry(
        rpm: Int, vssKmh: Int, tpsPct: Float,
        ectC: Float, ltftPct: Float, voltage: Float,
        nowMs: Long = System.currentTimeMillis()
    ): List<Warning> {
        val out = ArrayList<Warning>(2)

        // 1) learn RPM/VSS ratio while coasting (light throttle, moving)
        if (vssKmh > 10 && rpm in 800..3200 && tpsPct in 0f..20f) {
            val r = rpm.toFloat() / vssKmh
            if (r > 10f && r < 120f) {
                learnedRatio = if (learnedRatio < 0) r else learnedRatio * 0.9f + r * 0.1f
            }
        }

        // clutch slip check - full throttle, real RPM far above expected
        if (tpsPct >= SLIP_TPS_PCT && learnedRatio > 0 && vssKmh > 15) {
            val expectedRpm = (vssKmh * learnedRatio).toInt()
            if (rpm - expectedRpm >= SLIP_RPM_DELTA) {
                out += Warning(
                    Kind.CLUTCH_SLIP,
                    "Slizganie sprzegla: RPM $rpm vs oczekiwane ~$expectedRpm (TPS ${tpsPct.toInt()}%)"
                )
            }
        }

        // 2) tight intake valves at hot idle with rich correction
        if (rpm in 600..850 && ectC > 80f && ltftPct >= VALVE_LTFT_PCT) {
            valveHits++
            if (valveHits >= 30 && nowMs - valveWarnedMs > 5 * 60_000L) {
                valveWarnedMs = nowMs
                out += Warning(
                    Kind.TIGHT_VALVES,
                    "Podejrzenie ciasnych zaworow: LTFT ${ltftPct}% przy goracym biegu jalowym"
                )
            }
        } else {
            valveHits = 0
        }

        // 3) ELD / alternator daily-average tracking
        if (!voltage.isNaN() && voltage > 8f) {
            trackEld(voltage, nowMs)
            if (eldBaseAvg > 0 && eldCount >= 50) {
                val avg = eldSum / eldCount
                if (eldBaseAvg - avg > ELD_DROP_V) {
                    out += Warning(
                        Kind.ALTERNATOR,
                        "Degeneracja alternatora: srednia $avg V vs baza ${eldBaseAvg} V"
                    )
                }
            }
        }

        // 4) Eco-Driving Smoothness Index (dThrottle/dt)
        if (!tpsPct.isNaN()) {
            if (!prevTps.isNaN() && nowMs > prevMs) {
                val jerk = (tpsPct - prevTps) * 1000f / (nowMs - prevMs)   // [%/s]
                if (Math.abs(jerk) > SMOOTH_JERK_PPS) {
                    jerkHits++
                    if (jerkHits >= SMOOTH_HITS && nowMs - smoothWarnedMs > SMOOTH_RATELIMIT_MS) {
                        smoothWarnedMs = nowMs
                        out += Warning(
                            Kind.ECO_SMOOTHNESS,
                            "Nierowna jazda: gwaltowne zmiany przepustnicy (" +
                                jerk.toInt() + " %/s) - Eco Index: slizganie po Gazie"
                        )
                    }
                } else {
                    jerkHits = 0
                }
            }
            prevTps = tpsPct
            prevMs = nowMs
        }
        return out
    }

    private fun trackEld(v: Float, nowMs: Long) {
        val day = (nowMs / 86_400_000L).toInt()
        if (day != eldDay) {
            if (eldCount > 0 && eldBaseAvg <= 0.0) {
                eldBaseAvg = eldSum / eldCount
                prefs.edit().putFloat(KEY_ELD_BASE, eldBaseAvg.toFloat()).apply()
            }
            eldDay = day
            eldSum = 0.0
            eldCount = 0
            prefs.edit().putInt(KEY_ELD_DAY, day)
                .putFloat(KEY_ELD_SUM, 0f).putInt(KEY_ELD_N, 0).apply()
        }
        eldSum += v
        eldCount++
        if (eldCount % 20 == 0) {
            prefs.edit().putFloat(KEY_ELD_SUM, eldSum.toFloat())
                .putInt(KEY_ELD_N, eldCount).apply()
        }
        if (eldBaseAvg <= 0.0 && eldCount == 50) {
            // first full day becomes the baseline
            eldBaseAvg = eldSum / eldCount
            prefs.edit().putFloat(KEY_ELD_BASE, eldBaseAvg.toFloat()).apply()
            Log.i(TAG, "ELD baseline: ${"%.2f".format(eldBaseAvg)} V")
        }
    }

    /** Force a new alternator baseline (after replacing ELD/regulator). */
    fun resetEldBaseline() {
        eldBaseAvg = if (eldCount > 0) eldSum / eldCount else 0.0
        prefs.edit().putFloat(KEY_ELD_BASE, eldBaseAvg.toFloat()).apply()
    }
}
