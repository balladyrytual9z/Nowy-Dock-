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
    PROTECTED   // voltage/RPM guard active â PIDs blocked
}

