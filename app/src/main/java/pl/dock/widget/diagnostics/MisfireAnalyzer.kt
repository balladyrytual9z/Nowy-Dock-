package pl.dock.widget.diagnostics

/**
 * Mode $06 (On-Board Monitoring Test Results) – Misfire Lab
 * for Honda Civic VIII 1.4 i-VTEC (L13Z1, Keihin ECU).
 *
 * Raw frames come from ELM327 with headers ON (ATH1), so each line
 * contains an ISO 15765-4 (ISO-TP single frame) header before the payload:
 *
 *   7E8 06 46 01 00 00 00 06 00 00 00 06
 *   │   │  │  │
 *   │   │  │  └── MID ($01..$0A = misfire counters per cylinder)
 *   │   │  └───── Mode $06 response service byte ($46)
 *   │   └──────── optional service echo
 *   └──────────── CAN header (11-bit: 7E8 = engine ECU)
 *
 * The parser strips the header, keeps only the payload and extracts
 * MID / TID / UASID / test-value / min / max for misfire MIDs.
 */
class MisfireAnalyzer {

    data class MisfireResult(
        val mid: Int,          // MID == cylinder index (01..0A) on Keihin ECU
        val tid: Int,
        val value: Int,        // misfire count reported by the monitor
        val min: Int,
        val max: Int
    ) {
        val failed: Boolean get() = max > 0 && value > max
    }

    /** latest per-cylinder results, key = cylinder 1..10 */
    @Volatile var lastResults: List<MisfireResult> = emptyList()
        private set

    @Volatile var lastPollMs: Long = 0L
        private set

    /**
     * Poll Mode $06 through the session (request/response only).
     * [request] must forward a raw ELM command and return the raw answer
     * (headers included). Returns parsed misfire counters.
     */
    suspend fun poll(request: suspend (String) -> String?): List<MisfireResult> {
        val raw = request("06") ?: return lastResults
        val parsed = parseMode06(raw)
        if (parsed.isNotEmpty()) {
            lastResults = parsed
            lastPollMs = System.currentTimeMillis()
        }
        return parsed
    }

    /** True when any cylinder exceeded its Mode $06 test limit. */
    fun hasFailingCylinder(): Boolean = lastResults.any { it.failed }

    companion object {

        private val HEX = Regex("^[0-9A-Fa-f]+$")

        /** ISO-TP header lengths for 11-bit CAN with ATH1. */
        private const val HDR_NORMAL = "7E8"

        /**
         * Parse a raw multi-line Mode $06 answer.
         * Separates the ISO 15765-4 header from the payload and keeps only
         * misfire MIDs ($01..$0A).
         */
        fun parseMode06(raw: String): List<MisfireResult> {
            val out = ArrayList<MisfireResult>(16)
            // ELM may return several lines; lines are separated by '\n'
            raw.split('\n').forEach { line ->
                val toks = tokenize(line)
                var i = 0
                // skip ISO-TP header: CAN id (7E8 / 18DA10F1) + PCI byte (06)
                while (i < toks.size && !toks[i].equals("46", ignoreCase = true)) i++
                if (i == 0 || i >= toks.size) {
                    // no 46 found -> not a $06 data line
                    if (toks.size >= 6 && i < toks.size) return@forEach
                }
                if (i >= toks.size - 4) return@forEach

                val mid = toks[i + 1].toIntOrNull(16) ?: return@forEach
                val tid = toks[i + 2].toIntOrNull(16) ?: return@forEach
                // UASID (test id) – Keihin keeps misfire tests on TID 00/01
                val value = toks[i + 3].toIntOrNull(16) ?: return@forEach
                val min = toks.getOrElse(i + 4) { "0" }.toIntOrNull(16) ?: 0
                val max = toks.getOrElse(i + 5) { "0" }.toIntOrNull(16) ?: 0

                if (mid in 1..0x0A) {
                    out += MisfireResult(mid, tid, value, min, max)
                }
            }
            return out
        }

        private fun tokenize(line: String): List<String> =
            line.trim()
                .split(' ', '\t', '\r')
                .filter { it.isNotEmpty() && it.length <= 2 && HEX.matches(it) }
    }
}
