package pl.dock.widget.orchestration

import android.util.Log
import kotlinx.coroutines.*
import pl.dock.widget.session.ElmSession
import pl.dock.widget.session.EngineState
import pl.dock.widget.util.ElmHex
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Adaptive polling scheduler with three tiers:
 *  Fast   100 ms  - PID 010C (RPM), 010D (VSS)
 *  Medium 500 ms  - Engine Load, TPS, MAP
 *  Slow   2000 ms  - ECT, IAT, STFT, LTFT + 0101 (MIL/readiness A-D),
 *                    0133 (BARO), 0134 (Lambda/AFR), 2101 (czas wtrysku,
 *                    KWP2000 ReadDataByIdentifier - ukryty PID Hondy)
 *
 * Stale-data marking if no response > 3 s.
 * Keep-alive in background: only AT RV (voltage), no PID traffic.
 * Cranking protection via [EngineState].
 *
 * Rate Limiting: minimum 80 ms przerwy miedzy ukonczeniem ramki (prompt '>')
 * a wyslaniem kolejnej komendy - tanie klony ELM327 sie dlawia bez tego.
 */
class PidScheduler(
    private val session: ElmSession,
    private val engineState: EngineState,
    private val onPidResult: (pid: String, raw: String, timestampMs: Long) -> Unit,
    private val onStale: (pid: String) -> Unit,
    private val onVoltage: (Float) -> Unit
) {
    companion object {
        private const val TAG = "PidScheduler"
        private const val STALE_MS = 3_000L
        private const val MIN_GAP_MS = 80L
        val FAST_PIDS = listOf("010C", "010D")          // RPM, VSS
        val MEDIUM_PIDS = listOf("0104", "0111", "010B") // Load, TPS, MAP
        val SLOW_PIDS = listOf("0105", "010F", "0106", "0107",
            "0101", "0133", "0134", "2101", "0110", "010E") // ECT, IAT, STFT, LTFT, MIL, BARO, LAMBDA, INJ, MAF, IGN
    }

    private val executor: ScheduledExecutorService =
        Executors.newScheduledThreadPool(3) { r ->
            Thread(r, "pid-sched").also { it.isDaemon = true }
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val backgroundMode = AtomicBoolean(false)
    /** BT adapter zabity (Zlink przejal stos): zero odpytywania i keep-alive. */
    private val parked = AtomicBoolean(false)

    private val lastResponse = mutableMapOf<String, AtomicLong>()
    /** Czas ukonczenia poprzedniej komendy (odebrania promptu '>'). */
    private val lastDoneMs = AtomicLong(0L)
    private var fastFuture: ScheduledFuture<*>? = null
    private var mediumFuture: ScheduledFuture<*>? = null
    private var slowFuture: ScheduledFuture<*>? = null
    private var keepAliveFuture: ScheduledFuture<*>? = null
    private var staleChecker: ScheduledFuture<*>? = null

    init {
        (FAST_PIDS + MEDIUM_PIDS + SLOW_PIDS).forEach {
            lastResponse[it] = AtomicLong(0L)
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        backgroundMode.set(false)
        scheduleTiers()
        staleChecker = executor.scheduleAtFixedRate({
            val now = System.currentTimeMillis()
            lastResponse.forEach { (pid, ts) ->
                val t = ts.get()
                if (t > 0 && now - t > STALE_MS) {
                    onStale(pid)
                }
            }
        }, 1, 1, TimeUnit.SECONDS)
        Log.i(TAG, "PidScheduler started")
    }

    fun stop() {
        running.set(false)
        cancelAll()
        Log.i(TAG, "PidScheduler stopped")
    }

    /**
     * App moved to background: stop PID polling, keep only AT RV locally
     * so we don't wake the ECU.
     */
    fun enterBackground() {
        backgroundMode.set(true)
        cancelPidTiers()
        keepAliveFuture?.cancel(false)
        keepAliveFuture = executor.scheduleAtFixedRate({
            if (!running.get() || !backgroundMode.get() || parked.get()) return@scheduleAtFixedRate
            if (engineState.protected) return@scheduleAtFixedRate
            scope.launch {
                try {
                    val raw = session.request("ATRV")
                    parseVoltage(raw)?.let {
                        engineState.updateVoltage(it)
                        onVoltage(it)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Keep-alive ATRV failed: ${e.message}")
                }
            }
        }, 0, 5, TimeUnit.SECONDS)
        Log.i(TAG, "Background keep-alive (ATRV only)")
    }

    fun enterForeground() {
        backgroundMode.set(false)
        keepAliveFuture?.cancel(false)
        keepAliveFuture = null
        if (running.get()) scheduleTiers()
        Log.i(TAG, "Foreground - full PID tiers restored")
    }

    /**
     * Parkowanie przy padzie adaptera BT (Zlink/CarPlay przejmuje stos):
     * stop PID i keep-alive, zero ruchu na martwym porcie.
     */
    fun park() {
        if (!parked.compareAndSet(false, true)) return
        cancelPidTiers()
        keepAliveFuture?.cancel(false)
        keepAliveFuture = null
        Log.i(TAG, "Parked - BT adapter OFF (Zlink takeover)")
    }

    /** Adapter BT wstal - wznow odpytywanie zgodnie z trybem (fg/bg). */
    fun unpark() {
        if (!parked.compareAndSet(true, false)) return
        if (!running.get()) return
        if (backgroundMode.get()) {
            enterBackground()
        } else {
            scheduleTiers()
        }
        Log.i(TAG, "Unparked - PID polling resumed")
    }

    private fun scheduleTiers() {
        cancelPidTiers()
        fastFuture = executor.scheduleAtFixedRate(
            { poll(FAST_PIDS) }, 0, 100, TimeUnit.MILLISECONDS
        )
        mediumFuture = executor.scheduleAtFixedRate(
            { poll(MEDIUM_PIDS) }, 50, 500, TimeUnit.MILLISECONDS
        )
        slowFuture = executor.scheduleAtFixedRate(
            { poll(SLOW_PIDS) }, 100, 2000, TimeUnit.MILLISECONDS
        )
    }

    private fun poll(pids: List<String>) {
        if (!running.get() || backgroundMode.get() || parked.get()) return
        if (engineState.protected) return
        if (!session.isInitialized()) return
        scope.launch {
            for (pid in pids) {
                if (engineState.protected || backgroundMode.get()) break
                try {
                    // Rate limiting: wymuszenie min. 80 ms od ukonczenia
                    // poprzedniej ramki (odbior promptu '>') do nowej komendy
                    val gap = MIN_GAP_MS - (System.currentTimeMillis() - lastDoneMs.get())
                    if (gap > 0) delay(gap)
                    val raw = session.request(pid)
                    lastDoneMs.set(System.currentTimeMillis())
                    val now = System.currentTimeMillis()
                    lastResponse[pid]?.set(now)
                    onPidResult(pid, raw, now)
                    // feed engine state for protection
                    when (pid) {
                        "010C" -> parseRpm(raw)?.let { engineState.updateRpm(it) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "PID $pid failed: ${e.message}")
                }
            }
        }
    }

    private fun parseRpm(raw: String): Int? {
        // typical: 41 0C A B -> RPM = ((A*256)+B)/4
        // hot path 10 Hz: skaner bez alokacji (zero replace/uppercase/substring)
        val ab = ElmHex.scanAb(raw, 4, 1, 0, 0xC)
        return if (ab < 0) null else ab / 4
    }

    private fun parseVoltage(raw: String): Float? {
        // ATRV -> "12.6V" or similar
        val cleaned = raw.replace("V", "", ignoreCase = true).trim()
        return cleaned.toFloatOrNull()
    }

    private fun cancelPidTiers() {
        fastFuture?.cancel(false)
        mediumFuture?.cancel(false)
        slowFuture?.cancel(false)
        fastFuture = null
        mediumFuture = null
        slowFuture = null
    }

    private fun cancelAll() {
        cancelPidTiers()
        keepAliveFuture?.cancel(false)
        staleChecker?.cancel(false)
        keepAliveFuture = null
        staleChecker = null
    }

    fun shutdown() {
        stop()
        executor.shutdownNow()
        scope.cancel()
    }
}
