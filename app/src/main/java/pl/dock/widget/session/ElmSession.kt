package pl.dock.widget.session

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import pl.dock.widget.transport.ElmTransport
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ELM327 session: init sequence + request/response only (AT MA blocked).
 *
 * Init: ATZ â ATE0 â ATSP6 â ATH1 â ATAT1
 * Waits for prompt '>' (0x3E) via coroutine flow â no Thread.sleep.
 */
class ElmSession(
    private val transport: ElmTransport,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "ElmSession"
        private const val PROMPT_TIMEOUT_MS = 8_000L
        private val FORBIDDEN = setOf("ATMA", "AT MA", "ATMA\r", "AT MA\r")
    }

    private val _responses = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val responses: SharedFlow<String> = _responses.asSharedFlow()

    private val initialized = AtomicBoolean(false)
    private var collectorJob: Job? = null

    fun startCollecting() {
        if (collectorJob?.isActive == true) return
        collectorJob = scope.launch {
            transport.incomingLines.collect { line ->
                _responses.emit(line)
            }
        }
    }

    fun stopCollecting() {
        collectorJob?.cancel()
        collectorJob = null
    }

    /**
     * Asynchronous init sequence. Suspends until each command receives prompt '>'.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        startCollecting()
        sendAndWaitPrompt("ATZ")
        delay(300) // ATZ soft reset â brief settle only (not busy-wait on bus)
        sendAndWaitPrompt("ATE0")
        sendAndWaitPrompt("ATSP6")   // ISO 15765-4 CAN 11-bit 500 kbaud
        sendAndWaitPrompt("ATH1")    // headers on
        sendAndWaitPrompt("ATAT1")   // adaptive timing
        initialized.set(true)
        Log.i(TAG, "ELM327 initialized (SP6 / CAN 11-bit 500k)")
    }

    /**
     * Send a single command and collect response until prompt.
     * Blocks AT MA (Monitor All) â forced request/response only.
     */
    suspend fun request(command: String): String = withContext(Dispatchers.IO) {
        val cmd = command.trim().uppercase()
        if (FORBIDDEN.any { cmd.startsWith(it.replace(" ", "")) || cmd == it }) {
            throw SecurityException("AT MA (Monitor All) is blocked â request/response only")
        }
        if (!transport.isConnected()) throw IllegalStateException("Transport not connected")
        sendAndWaitPrompt(command)
    }

    private suspend fun sendAndWaitPrompt(command: String): String {
        val buffer = StringBuilder()
        val waitJob = scope.async {
            withTimeout(PROMPT_TIMEOUT_MS) {
                while (true) {
                    val line = transport.incomingLines.first()
                    if (line.contains('>')) {
                        buffer.append(line.replace(">", "").trim())
                        break
                    }
                    if (line.isNotBlank() && !line.equals(command.trim(), ignoreCase = true)) {
                        if (buffer.isNotEmpty()) buffer.append('\n')
                        buffer.append(line)
                    }
                }
                buffer.toString().trim()
            }
        }
        transport.write(command)
        return waitJob.await()
    }

    fun isInitialized(): Boolean = initialized.get()

    suspend fun close() {
        stopCollecting()
        initialized.set(false)
        try { transport.disconnect() } catch (_: Exception) {}
    }
}

