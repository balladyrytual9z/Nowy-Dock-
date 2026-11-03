package pl.dock.widget.transport

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Auto-reconnect daemon with exponential backoff (delay doubles, max 30 s).
 * On allocation / GATT errors attempts cache clear via reflection.
 */
class ConnectionDaemon(
    private val transport: ElmTransport,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "ConnectionDaemon"
        private const val INITIAL_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 30_000L
    }

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    @Volatile private var targetAddress: String? = null
    @Volatile private var job: Job? = null
    @Volatile private var delayMs = INITIAL_DELAY_MS

    fun start(address: String) {
        targetAddress = address
        if (job?.isActive == true) return
        _active.value = true
        delayMs = INITIAL_DELAY_MS
        job = scope.launch {
            while (isActive && _active.value) {
                val addr = targetAddress ?: break
                if (transport.isConnected()) {
                    delayMs = INITIAL_DELAY_MS
                    delay(2_000)
                    continue
                }
                try {
                    Log.i(TAG, "Reconnect attempt to $addr (backoff ${delayMs}ms)")
                    transport.connect(addr)
                    delayMs = INITIAL_DELAY_MS
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect failed: ${e.message}")
                    // GATT allocation / status 133 style errors â clear cache
                    if (transport is BleTransport &&
                        (e.message?.contains("133") == true ||
                                e.message?.contains("allocation", ignoreCase = true) == true ||
                                e.message?.contains("GATT", ignoreCase = true) == true)
                    ) {
                        transport.clearGattCache()
                    }
                    delay(delayMs)
                    delayMs = minOf(delayMs * 2, MAX_DELAY_MS)
                }
            }
        }
    }

    /**
     * Adapter BT wstal (STATE_ON po przejeciu stosu przez Zlink/CarPlay):
     * reset backoffu + natychmiastowa proba reconnectu (zamiast czekac do 30 s).
     */
    fun kick() {
        val addr = targetAddress ?: return
        delayMs = INITIAL_DELAY_MS
        if (job?.isActive == true) job?.cancel()
        start(addr)
    }

    fun stop() {
        _active.value = false
        job?.cancel()
        job = null
        targetAddress = null
        delayMs = INITIAL_DELAY_MS
    }
}

