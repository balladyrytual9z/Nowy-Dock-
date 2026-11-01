package pl.dock.widget.repository

/**
 * Immutable snapshot of vehicle telemetry exposed to UI via StateFlow.
 * UI layer must never touch Bluetooth / ELM transport directly.
 */
data class VehicleUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val adapterName: String = "",
    val voltage: Float = Float.NaN,
    val rpm: Int = -1,
    val speedKmh: Int = -1,
    val engineLoadPct: Float = Float.NaN,
    val tpsPct: Float = Float.NaN,
    val mapKpa: Float = Float.NaN,
    val ectC: Float = Float.NaN,
    val iatC: Float = Float.NaN,
    val stftPct: Float = Float.NaN,
    val ltftPct: Float = Float.NaN,
    /** PID 0101: kontrolka MIL (Check Engine) zapalona. */
    val milOn: Boolean = false,
    /** PID 0101: liczba potwierdzonych kodow DTC zgloszona przez ECU (-1 = brak danych). */
    val dtcCount: Int = -1,
    /** PID 0101 (bajt C): ile monitorow nieciaglych jest wspieranych przez ECU (-1 = brak). */
    val readinessTotal: Int = -1,
    /** PID 0101 (bajt D, bit 0 = test zakonczony): ile monitorow gotowych (Ready). */
    val readinessReady: Int = -1,
    /** PID 0133: cisnienie barometryczne [kPa]. */
    val baroKpa: Float = Float.NaN,
    /** PID 0134: stosunek lambda przeliczony na AFR (x14.7); wartosc -1 = brak. */
    val lambdaAfr: Float = Float.NaN,
    /** Mode 21 (KWP2000 ReadDataByIdentifier, "2101"): czas wtrysku [ms] (skala x0.001, do kalibracji). */
    val injectionMs: Float = Float.NaN,
    /** True when last response for a PID is older than stale threshold (UI greys out). */
    val staleFlags: StaleFlags = StaleFlags(),
    val engineState: EngineStateLabel = EngineStateLabel.UNKNOWN,
    val lastError: String? = null,
    val protocol: String = ""
)

data class StaleFlags(
    val rpm: Boolean = false,
    val speed: Boolean = false,
    val load: Boolean = false,
    val tps: Boolean = false,
    val map: Boolean = false,
    val ect: Boolean = false,
    val iat: Boolean = false,
    val stft: Boolean = false,
    val ltft: Boolean = false
)

enum class EngineStateLabel {
    UNKNOWN,
    OFF,
    CRANKING,
    RUNNING,
    PROTECTED   // voltage/RPM guard active - PIDs blocked
}
