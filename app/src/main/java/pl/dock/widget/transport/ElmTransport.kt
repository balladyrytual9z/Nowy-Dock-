package pl.dock.widget.transport

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over SPP / BLE ELM327 transports.
 * UI and session layers depend only on this interface.
 */
interface ElmTransport {
    val connectionState: StateFlow<TransportState>
    val incomingLines: SharedFlow<String>

    suspend fun connect(address: String)
    suspend fun disconnect()
    suspend fun write(command: String)
    fun isConnected(): Boolean
}

enum class TransportState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

/**
 * Events emitted by transport for diagnostics / UI badges.
 */
sealed class TransportEvent {
    data class Connected(val address: String, val type: String) : TransportEvent()
    data class Disconnected(val reason: String) : TransportEvent()
    data class Error(val message: String) : TransportEvent()
    data class BytesWritten(val count: Int) : TransportEvent()
}

