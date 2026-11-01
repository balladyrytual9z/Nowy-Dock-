package pl.dock.widget.blackbox

/**
 * One telemetry frame as stored by the Black Box.
 * NaN / -1 marks "not available at sample time".
 */
data class TelemetrySnapshot(
    val timestampMs: Long,
    val voltage: Float = Float.NaN,
    val rpm: Int = -1,
    val speedKmh: Int = -1,
    val loadPct: Float = Float.NaN,
    val tpsPct: Float = Float.NaN,
    val mapKpa: Float = Float.NaN,
    val ectC: Float = Float.NaN,
    val iatC: Float = Float.NaN,
    val stftPct: Float = Float.NaN,
    val ltftPct: Float = Float.NaN,
    /** optional event marker ("HARSH_DECEL", "ALERT_ECT", ...) */
    val event: String = ""
) {
    fun csvHeaderRow(): String = HEADER

    fun toCsvRow(): String = buildString {
        append(timestampMs); append(',')
        append(nan(voltage)); append(',')
        append(rpm); append(',')
        append(speedKmh); append(',')
        append(nan(loadPct)); append(',')
        append(nan(tpsPct)); append(',')
        append(nan(mapKpa)); append(',')
        append(nan(ectC)); append(',')
        append(nan(iatC)); append(',')
        append(nan(stftPct)); append(',')
        append(nan(ltftPct)); append(',')
        append(if (event.isEmpty()) "" else event.replace(',', ';'))
    }

    private fun nan(v: Float): String = if (v.isNaN()) "" else v.toString()

    companion object {
        const val HEADER =
            "timestamp_ms,voltage,rpm,speed_kmh,load_pct,tps_pct,map_kpa,ect_c,iat_c,stft_pct,ltft_pct,event"
    }
}
