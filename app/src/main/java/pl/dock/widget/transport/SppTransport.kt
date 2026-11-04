package pl.dock.widget.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth Classic (SPP) transport for ELM327 / vGate clones.
 *
 * Three-stage connection heuristic for UUID 00001101-0000-1000-8000-00805F9B34FB:
 *  1. Insecure RFCOMM (no authentication key) - primary, works with most clones
 *  2. Secure RFCOMM - fallback
 *  3. Reflection channel-1 bypass - last resort for stubborn adapters
 *
 * NAPRAWA (prompt '>'): ELM327 konczy kazda odpowiedz znakiem zachety '>'
 * BEZ zadnego terminatora linii (CR/LF). Wczesniej BufferedReader.readLine()
 * czekal na terminator, wiec linia z promptem NIGDY nie byla emitowana -
 * kazda komenda (zaczynajac od ATZ w initialize()) konczyla sie timeoutem
 * 8 s i polaczenie padalo mimo otwartego socketa ("adapter jest znajdowany,
 * ale sie nie laczy"). Czytamy teraz surowe bajty i traktujemy CR, LF ORAZ '>'
 * jako koniec linii; prompt '>' emitujemy jako osobna linie, ktora ElmSession
 * (sendAndWaitPrompt) wykrywa i konczy oczekiwanie na odpowiedz.
 */
class SppTransport : ElmTransport {

    companion object {
        private const val TAG = "SppTransport"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val connectionState: StateFlow<TransportState> = _state.asStateFlow()

    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingLines: SharedFlow<String> = _lines.asSharedFlow()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var input: InputStream? = null
    @Volatile private var readThread: Thread? = null

    override fun isConnected(): Boolean = _state.value == TransportState.CONNECTED

    override suspend fun connect(address: String) = withContext(Dispatchers.IO) {
        if (_state.value == TransportState.CONNECTED) return@withContext
        _state.value = TransportState.CONNECTING
        disconnectInternal()

        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw IllegalStateException("BluetoothAdapter unavailable")
        val device = adapter.getRemoteDevice(address)

        var lastError: Exception? = null
        val socket = tryInsecure(device)
            ?: trySecure(device).also { if (it == null) lastError = Exception("secure failed") }
            ?: tryReflectionChannel1(device).also { if (it == null) lastError = Exception("reflection failed") }
            ?: throw (lastError ?: Exception("All SPP connect methods failed for $address"))

        this@SppTransport.socket = socket
        output = socket.outputStream
        input = socket.inputStream
        startReader()
        _state.value = TransportState.CONNECTED
        Log.i(TAG, "SPP connected to $address")
    }

    private fun tryInsecure(device: BluetoothDevice): BluetoothSocket? {
        return try {
            val s = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            s.connect()
            Log.i(TAG, "Insecure RFCOMM OK")
            s
        } catch (e: Exception) {
            Log.w(TAG, "Insecure RFCOMM failed: " + e.message)
            null
        }
    }

    private fun trySecure(device: BluetoothDevice): BluetoothSocket? {
        return try {
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            s.connect()
            Log.i(TAG, "Secure RFCOMM OK")
            s
        } catch (e: Exception) {
            Log.w(TAG, "Secure RFCOMM failed: " + e.message)
            null
        }
    }

    /**
     * Fallback via reflection: createRfcommSocket(int channel) with channel 1.
     * Bypasses SDP lookup which often fails on ELM clones.
     */
    private fun tryReflectionChannel1(device: BluetoothDevice): BluetoothSocket? {
        return try {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val s = method.invoke(device, 1) as BluetoothSocket
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            s.connect()
            Log.i(TAG, "Reflection channel-1 OK")
            s
        } catch (e: Exception) {
            Log.w(TAG, "Reflection channel-1 failed: " + e.message)
            null
        }
    }

    /**
     * Czytnik socketa: surowe bajty, koncem linii jest CR (0x0D), LF (0x0A)
     * LUB znak zachety '>' (0x3E). NAPRAWA: prompt '>' nie ma za soba
     * terminatora - readLine() wieszal sie na nim i ATZ zawsze timeoutowal,
     * przez co polaczenie z adapterem nigdy nie dochodzilo do stanu OK.
     */
    private fun startReader() {
        readThread = Thread({
            val inp = input ?: return@Thread
            val sb = StringBuilder()
            val buf = ByteArray(256)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    for (i in 0 until n) {
                        when (val c = buf[i].toInt() and 0xFF) {
                            0x0D, 0x0A -> {
                                val line = sb.toString().trim()
                                sb.setLength(0)
                                if (line.isNotEmpty()) _lines.tryEmit(line)
                            }
                            0x3E -> {
                                val line = sb.toString().trim()
                                sb.setLength(0)
                                if (line.isNotEmpty()) _lines.tryEmit(line)
                                _lines.tryEmit(">")   // prompt = koniec odpowiedzi ELM
                            }
                            else -> sb.append(c.toChar())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reader stopped: " + e.message)
            } finally {
                if (_state.value == TransportState.CONNECTED) {
                    _state.value = TransportState.DISCONNECTED
                }
            }
        }, "spp-reader").also { it.isDaemon = true; it.start() }
    }

    override suspend fun write(command: String) = withContext(Dispatchers.IO) {
        val out = output ?: throw IllegalStateException("Not connected")
        val payload = if (command.endsWith("\r")) command else "$command\r"
        out.write(payload.toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
        _state.value = TransportState.DISCONNECTED
    }

    private fun disconnectInternal() {
        try { readThread?.interrupt() } catch (_: Exception) {}
        readThread = null
        try { input?.close() } catch (_: Exception) {}
        input = null
        try { output?.close() } catch (_: Exception) {}
        output = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
