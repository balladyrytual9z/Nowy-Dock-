package pl.dock.widget.diagnostics

/**
 * Virtual Dyno – WHP estimation from VSS derivative.
 *
 *   P_WHP = (m * (dv/dt) * v) / 745.7
 *
 * m      – vehicle mass [kg] (default 1250 kg + payload)
 * dv/dt  – acceleration [m/s^2] (deltas < 50 ms are rejected)
 * v      – speed [m/s]
 *
 * Environmental compensation: barometric pressure (PID 0133) and IAT
 * are used to scale air density:
 *
 *   corr = (baro_kPa / 101.325) * (288.15 / IAT_K)
 */
class VirtualDyno(
    private val massKg: Float = DEFAULT_MASS_KG
) {

    companion object {
        const val DEFAULT_MASS_KG = 1250f
        private const val MIN_DELTA_MS = 50L
    }

    data class DynoSample(
        val timestampMs: Long,
        val whp: Float,
        val accelMs2: Float,
        val speedKmh: Float,
        val corrected: Boolean
    )

    @Volatile var peakWhp: Float = 0f
        private set
    @Volatile var lastSample: DynoSample? = null
        private set

    @Volatile private var baroKpa: Float = 101.325f
    @Volatile private var iatC: Float = 15f

    /** extra mass (passengers, luggage) on top of the base mass */
    fun setPayloadKg(extra: Float) { payloadKg = extra.coerceAtLeast(0f) }
    @Volatile private var payloadKg = 0f

    /** PID 0133 – barometric pressure [kPa] */
    fun setBaroKpa(kpa: Float) { if (kpa > 0f) baroKpa = kpa }

    /** PID 010F – intake air temperature [°C] */
    fun setIatC(c: Float) { if (c > -60f && c < 150f) iatC = c }

    /** End current dyno run (call when decelerating / clutch in). */
    fun resetRun() { peakWhp = 0f }

    /**
     * Feed a VSS sample. Returns an estimated WHP when the sample pair is
     * usable (dt >= 50 ms, accelerating), otherwise null.
     */
    fun onSpeed(speedKmh: Int, timestampMs: Long): DynoSample? {
        val prev = lastSample
        val sample = DynoSample(timestampMs, 0f, 0f, speedKmh.toFloat(), false)
        if (prev == null) {
            lastSample = sample
            return null
        }

        val dt = timestampMs - prev.timestampMs
        if (dt < MIN_DELTA_MS) return null          // reject jitter deltas
        if (speedKmh < 0) return null

        val v0 = prev.speedKmh / 3.6f                // m/s
        val v1 = speedKmh / 3.6f
        val a = (v1 - v0) / (dt / 1000f)             // m/s^2

        if (a <= 0.05f) {                            // only accelerating runs
            lastSample = sample
            return null
        }

        val corrected = v1 * (baroKpa / 101.325f) * (288.15f / (iatC + 273.15f))
        val m = massKg + payloadKg
        val whp = (m * a * corrected) / 745.7f

        val out = DynoSample(timestampMs, whp, a, speedKmh.toFloat(), true)
        lastSample = sample
        if (whp > peakWhp) peakWhp = whp
        return out
    }
}
