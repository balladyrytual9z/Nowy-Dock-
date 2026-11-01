package pl.dock.widget.orchestration

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.dock.widget.repository.EngineStateLabel
import pl.dock.widget.repository.StaleFlags
import pl.dock.widget.repository.VehicleUiState
import pl.dock.widget.session.ElmSession
import pl.dock.widget.session.EngineState
import pl.dock.widget.transport.*

/**
 * Orchestrates transport selection, session init, PID scheduler and
 * publishes a single [VehicleUiState] StateFlow for the UI layer.
 * UI never talks to Bluetooth directly.
 *
 * Nowe PID-y (raport inzynierii wstecznej Keihin):
 *  - 0101: MIL + DTC count + pelne dekodowanie monitorow gotowosci
 *    (bajty A/B/C/D wg SAE J1979; bajt C = supported, bajt D: bit 0
 *    oznacza test ZAKONCZONY pomyślnie - Ready).
 *  - 0133 (BARO), 0134 (Lambda -> AFR)
 *  - Mode 21 "2101" (KWP2000 ReadDataByIdentifier): czas wtrysku [ms].
 *
 * Tryb serwisowy (odblokowanie komend modyfikujacych 10/27/30/31/2E):
 * [setServiceMode] - zmiana w locie, bez reconnectu.
 */
class ConnectionManager(
    private val appContext: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    companion object {
        private const val TAG = "ConnectionManager"

        @Volatile private var instance: ConnectionManager? = null

        /** Process-wide singleton - UI, widget and ObdCoreService share it. */
        fun get(appContext: Context): ConnectionManager =
            instance ?: synchronized(this) {
                instance ?: ConnectionManager(appContext.applicationContext).also { instance = it }
            }
    }

    /**
     * Raw ELM request (request/response only, AT MA blocked inside the session).
     * Used by Mode $06 misfire polling, DTC read/clear, freeze frame, VIN,
     * tryb serwisowy (1003 / 2701 / 3E00 / rutyny 31 / IO 30).
     */
    suspend fun rawRequest(command: String): String? = try {
        session?.request(command)
    } catch (e: Exception) {
        Log.w(TAG, "rawRequest '$command' failed: ${e.message}")
        null
    }

    private val _uiState = MutableStateFlow(VehicleUiState())
    val uiState: StateFlow<VehicleUiState> = _uiState.asStateFlow()

    private var transport: ElmTransport? = null
    private var session: ElmSession? = null
    private var scheduler: PidScheduler? = null
    private var daemon: ConnectionDaemon? = null
    private val engineState = EngineState()

    private val stale = mutableMapOf<String, Boolean>()

    /**
     * Tryb serwisowy ON/OFF - odblokowuje/zablokowuje komendy modyfikujace
     * ECU w locie (bez reconnectu) i utrwala w prefs "obd_settings".
     */
    fun setServiceMode(enabled: Boolean) {
        session?.allowMutating = enabled
        appContext.getSharedPreferences("obd_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("service_mode", enabled).apply()
        Log.w(TAG, "Service mode " + (if (enabled) "ENABLED (komendy 10/27/30/31/2E odblokowane)" else "DISABLED (Read-Only Lock)"))
    }

    fun isServiceMode(): Boolean = session?.allowMutating
        ?: appContext.getSharedPreferences("obd_settings", Context.MODE_PRIVATE)
            .getBoolean("service_mode", false)

    /**
     * @param address MAC address
     * @param preferBle true -> BleTransport, false -> SppTransport
     */
    fun connect(address: String, preferBle: Boolean = false) {
        scope.launch {
            _uiState.value = _uiState.value.copy(
                connecting = true,
                connected = false,
                lastError = null
            )
            try {
                val t: ElmTransport = if (preferBle) {
                    BleTransport(appContext)
                } else {
                    SppTransport()
                }
                transport = t
                t.connect(address)

                // Read-Only Safety Lock: przy restarcie aplikacji przywracamy
                // stan flagi z prefs (domyslnie wylaczony).
                val serviceMode = appContext
                    .getSharedPreferences("obd_settings", Context.MODE_PRIVATE)
                    .getBoolean("service_mode", false)
                val s = ElmSession(t, serviceMode = serviceMode)
                session = s
                s.initialize()

                val sched = PidScheduler(
                    session = s,
                    engineState = engineState,
                    onPidResult = { pid, raw, _ -> handlePid(pid, raw) },
                    onStale = { pid -> markStale(pid) },
                    onVoltage = { v ->
                        engineState.updateVoltage(v)
                        publish()
                    }
                )
                scheduler = sched
                sched.start()

                val d = ConnectionDaemon(t, scope)
                daemon = d
                d.start(address)

                _uiState.value = _uiState.value.copy(
                    connected = true,
                    connecting = false,
                    adapterName = address,
                    protocol = "ISO 15765-4 CAN 11-bit 500k",
                    engineState = engineState.label
                )
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed", e)
                _uiState.value = _uiState.value.copy(
                    connected = false,
                    connecting = false,
                    lastError = e.message ?: "unknown"
                )
            }
        }
    }

    fun disconnect() {
        scope.launch {
            scheduler?.shutdown()
            scheduler = null
            daemon?.stop()
            daemon = null
            session?.close()
            session = null
            transport = null
            engineState.reset()
            stale.clear()
            _uiState.value = VehicleUiState()
        }
    }

    fun onAppBackground() {
        scheduler?.enterBackground()
    }

    fun onAppForeground() {
        scheduler?.enterForeground()
    }

    private fun handlePid(pid: String, raw: String) {
        stale[pid] = false
        val cur = _uiState.value
        val next = when (pid) {
            "010C" -> { // RPM
                val rpm = parseRpm(raw)
                if (rpm != null) engineState.updateRpm(rpm)
                cur.copy(rpm = rpm ?: cur.rpm)
            }
            "010D" -> cur.copy(speedKmh = parseByte(raw, "410D") ?: cur.speedKmh)
            "0104" -> cur.copy(engineLoadPct = parsePct(raw, "4104") ?: cur.engineLoadPct)
            "0111" -> cur.copy(tpsPct = parsePct(raw, "4111") ?: cur.tpsPct)
            "010B" -> cur.copy(mapKpa = parseByte(raw, "410B")?.toFloat() ?: cur.mapKpa)
            "0105" -> cur.copy(ectC = parseTemp(raw, "4105") ?: cur.ectC)
            "010F" -> cur.copy(iatC = parseTemp(raw, "410F") ?: cur.iatC)
            "0106" -> cur.copy(stftPct = parseFuelTrim(raw, "4106") ?: cur.stftPct)
            "0107" -> cur.copy(ltftPct = parseFuelTrim(raw, "4107") ?: cur.ltftPct)
            "0101" -> { // MIL + DTC count + readiness (A/B/C/D)
                val r = parseReadiness(raw)
                if (r != null) cur.copy(milOn = r.milOn, dtcCount = r.dtcCount,
                    readinessTotal = r.total, readinessReady = r.ready)
                else cur
            }
            "0133" -> cur.copy(baroKpa = parseByte(raw, "4133")?.toFloat() ?: cur.baroKpa)
            "0134" -> { // Lambda (SAE): ((A*256)+B)/32768 -> AFR przy 14.7
                val afr = parseLambdaAfr(raw)
                if (afr != null) cur.copy(lambdaAfr = afr) else cur
            }
            "2101" -> { // KWP2000 ReadDataByIdentifier: czas wtrysku (16-bit BE, skala x0.001 ms)
                val ms = parseInjectionMs(raw)
                if (ms != null) cur.copy(injectionMs = ms) else cur
            }
            else -> cur
        }
        _uiState.value = next.copy(
            engineState = engineState.label,
            staleFlags = buildStaleFlags()
        )
    }

    private fun markStale(pid: String) {
        stale[pid] = true
        _uiState.value = _uiState.value.copy(staleFlags = buildStaleFlags())
    }

    private fun buildStaleFlags() = StaleFlags(
        rpm = stale["010C"] == true,
        speed = stale["010D"] == true,
        load = stale["0104"] == true,
        tps = stale["0111"] == true,
        map = stale["010B"] == true,
        ect = stale["0105"] == true,
        iat = stale["010F"] == true,
        stft = stale["0106"] == true,
        ltft = stale["0107"] == true
    )

    private fun publish() {
        _uiState.value = _uiState.value.copy(
            voltage = engineState.voltage,
            engineState = engineState.label
        )
    }

    // --- minimal PID parsers ---
    private fun parseRpm(raw: String): Int? {
        val hex = raw.replace(" ", "").uppercase()
        val i = hex.indexOf("410C")
        if (i < 0 || hex.length < i + 8) return null
        return try {
            val a = hex.substring(i + 4, i + 6).toInt(16)
            val b = hex.substring(i + 6, i + 8).toInt(16)
            ((a * 256) + b) / 4
        } catch (_: Exception) { null }
    }

    private fun parseByte(raw: String, header: String): Int? {
        val hex = raw.replace(" ", "").uppercase()
        val i = hex.indexOf(header)
        if (i < 0 || hex.length < i + 6) return null
        return try { hex.substring(i + 4, i + 6).toInt(16) } catch (_: Exception) { null }
    }

    private fun parsePct(raw: String, header: String): Float? {
        val b = parseByte(raw, header) ?: return null
        return b * 100f / 255f
    }

    private fun parseTemp(raw: String, header: String): Float? {
        val b = parseByte(raw, header) ?: return null
        return b - 40f
    }

    private fun parseFuelTrim(raw: String, header: String): Float? {
        val b = parseByte(raw, header) ?: return null
        return (b - 128) * 100f / 128f
    }

    private data class Readiness(val milOn: Boolean, val dtcCount: Int,
                                  val total: Int, val ready: Int)

    /**
     * PID 0101 - pelne dekodowanie 4 bajtow (A, B, C, D) wg SAE J1979:
     *  A: bit0 = MIL, bity 1-7 = liczba DTC
     *  B: monitory ciagle (misfire / fuel system)
     *  C: monitory nieciagle - SUPPORTED (bit = 1)
     *  D: monitory nieciagle - status testu; bit = 0 oznacza
     *     "Test ZAKONCZONY (Ready)", bit = 1 "Not Ready / Incomplete"
     * Zwraca [Readiness] lub null (brak fabrykacji).
     */
    private fun parseReadiness(raw: String): Readiness? {
        val hex = raw.replace(" ", "").uppercase()
        val i = hex.indexOf("4101")
        if (i < 0 || hex.length < i + 14) return null
        return try {
            val a = hex.substring(i + 4, i + 6).toInt(16)
            val c = hex.substring(i + 8, i + 10).toInt(16)   // supported
            val d = hex.substring(i + 10, i + 12).toInt(16)  // test status
            val mil = (a and 0x01) != 0
            val dtc = a shr 1
            var total = 0
            var ready = 0
            for (bit in 0..7) {
                if ((c shr bit) and 1 == 1) {
                    total++
                    if ((d shr bit) and 1 == 0) ready++   // bit 0 = zakonczony (Ready)
                }
            }
            Readiness(mil, dtc, total, ready)
        } catch (_: Exception) { null }
    }

    /** PID 0134: lambda = ((A*256)+B)/32768; AFR = lambda * 14.7 (benzyna). */
    private fun parseLambdaAfr(raw: String): Float? {
        val hex = raw.replace(" ", "").uppercase()
        val i = hex.indexOf("4134")
        if (i < 0 || hex.length < i + 8) return null
        return try {
            val a = hex.substring(i + 4, i + 6).toInt(16)
            val b = hex.substring(i + 6, i + 8).toInt(16)
            val lambda = ((a * 256) + b) / 32768f
            lambda * 14.7f
        } catch (_: Exception) { null }
    }

    /**
     * Mode 21 (KWP2000 ReadDataByIdentifier), zapytanie "2101":
     * odpowiedz 61 01 XX XX. Czas wtrysku = pierwsze 2 bajty danych
     * (16-bit Big Endian) x 0.001 [ms] - skala wg raportu wymaga kalibracji
     * wzgledem HDS (mnoznik 0.001 / 0.01).
     */
    private fun parseInjectionMs(raw: String): Float? {
        val hex = raw.replace(" ", "").uppercase()
        val i = hex.indexOf("6101")
        if (i < 0 || hex.length < i + 8) return null
        return try {
            val a = hex.substring(i + 4, i + 6).toInt(16)
            val b = hex.substring(i + 6, i + 8).toInt(16)
            ((a * 256) + b) * 0.001f
        } catch (_: Exception) { null }
    }
}
