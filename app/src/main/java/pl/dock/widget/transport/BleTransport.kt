package pl.dock.widget.transport

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * BLE GATT transport for ELM327 / vGate BLE adapters.
 *
 * - Single-operation mutex (only one async GATT op at a time)
 * - Pairing timeout 12 s, transfer timeout 6 s
 * - MTU forced to max 185; writes chunked â¤ 20 bytes, WRITE_TYPE_NO_RESPONSE
 * - CCCD (0x2902) subscription; missing descriptor is ignored, notify continues
 * - GATT cache clear via reflection on allocation errors
 */
@SuppressLint("MissingPermission")
class BleTransport(private val appContext: Context) : ElmTransport {

    companion object {
        private const val TAG = "BleTransport"
        // Common ELM BLE service / characteristic UUIDs (vGate / similar)
        val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val PAIR_TIMEOUT_MS = 12_000L
        private const val TX_TIMEOUT_MS = 6_000L
        private const val MAX_CHUNK = 20
        private const val TARGET_MTU = 185
    }

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val connectionState: StateFlow<TransportState> = _state.asStateFlow()

    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingLines: SharedFlow<String> = _lines.asSharedFlow()

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile private var notifyChar: BluetoothGattCharacteristic? = null
    private val lineBuffer = StringBuilder()
    private val connected = AtomicBoolean(false)

    override fun isConnected(): Boolean = connected.get() && _state.value == TransportState.CONNECTED

    override suspend fun connect(address: String) = withContext(Dispatchers.Main) {
        if (isConnected()) return@withContext
        _state.value = TransportState.CONNECTING
        disconnectInternal()

        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw IllegalStateException("BluetoothAdapter unavailable")
        val device = adapter.getRemoteDevice(address)

        withTimeout(PAIR_TIMEOUT_MS) {
            suspendCancellableCoroutine<Unit> { cont ->
                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.i(TAG, "GATT connected, discovering services")
                            g.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            connected.set(false)
                            _state.value = TransportState.DISCONNECTED
                            if (cont.isActive) cont.resumeWithException(Exception("Disconnected status=$status"))
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            if (cont.isActive) cont.resumeWithException(Exception("Service discovery failed: $status"))
                            return
                        }
                        val service = g.getService(SERVICE_UUID)
                        if (service == null) {
                            // try first available service with write+notify
                            val fallback = g.services.firstOrNull { svc ->
                                svc.characteristics.any { it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ||
                                        it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 }
                            }
                            if (fallback == null) {
                                if (cont.isActive) cont.resumeWithException(Exception("No suitable GATT service"))
                                return
                            }
                            writeChar = fallback.characteristics.firstOrNull {
                                it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ||
                                        it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
                            }
                            notifyChar = fallback.characteristics.firstOrNull {
                                it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                            }
                        } else {
                            writeChar = service.getCharacteristic(WRITE_UUID)
                            notifyChar = service.getCharacteristic(NOTIFY_UUID)
                        }
                        // Request larger MTU
                        if (Build.VERSION.SDK_INT >= 21) {
                            g.requestMtu(TARGET_MTU)
                        } else {
                            enableNotifyAndFinish(g, cont)
                        }
                    }

                    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                        Log.i(TAG, "MTU changed to $mtu status=$status")
                        enableNotifyAndFinish(g, cont)
                    }

                    override fun onCharacteristicChanged(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic
                    ) {
                        val data = characteristic.value ?: return
                        val text = String(data, Charsets.US_ASCII)
                        synchronized(lineBuffer) {
                            lineBuffer.append(text)
                            var idx: Int
                            while (lineBuffer.indexOf('\r').also { idx = it } >= 0 ||
                                lineBuffer.indexOf('\n').also { idx = it } >= 0) {
                                val line = lineBuffer.substring(0, idx).trim()
                                lineBuffer.delete(0, idx + 1)
                                if (line.isNotEmpty()) _lines.tryEmit(line)
                            }
                        }
                    }

                    override fun onCharacteristicWrite(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        // signal completion for mutex-gated writes if needed
                    }
                }

                gatt = if (Build.VERSION.SDK_INT >= 23) {
                    device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(appContext, false, callback)
                }
                cont.invokeOnCancellation { disconnectInternal() }
            }
        }
        connected.set(true)
        _state.value = TransportState.CONNECTED
        Log.i(TAG, "BLE connected to $address")
    }

    private fun enableNotifyAndFinish(g: BluetoothGatt, cont: CancellableContinuation<Unit>) {
        val nc = notifyChar
        if (nc != null) {
            g.setCharacteristicNotification(nc, true)
            val cccd = nc.getDescriptor(CCCD_UUID)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(cccd)
            } else {
                // Missing CCCD â ignore and continue listening (some clones omit it)
                Log.w(TAG, "CCCD 0x2902 missing â continuing without descriptor write")
            }
        }
        if (cont.isActive) cont.resume(Unit)
    }

    override suspend fun write(command: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            withTimeout(TX_TIMEOUT_MS) {
                val g = gatt ?: throw IllegalStateException("Not connected")
                val ch = writeChar ?: throw IllegalStateException("No write characteristic")
                val payload = if (command.endsWith("\r")) command else "$command\r"
                val bytes = payload.toByteArray(Charsets.US_ASCII)
                var offset = 0
                while (offset < bytes.size) {
                    val end = minOf(offset + MAX_CHUNK, bytes.size)
                    val chunk = bytes.copyOfRange(offset, end)
                    ch.value = chunk
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    val ok = g.writeCharacteristic(ch)
                    if (!ok) throw Exception("writeCharacteristic returned false")
                    offset = end
                    // small yield so stack can process
                    delay(8)
                }
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.Main) {
        disconnectInternal()
        _state.value = TransportState.DISCONNECTED
    }

    private fun disconnectInternal() {
        connected.set(false)
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        gatt = null
        writeChar = null
        notifyChar = null
        synchronized(lineBuffer) { lineBuffer.setLength(0) }
    }

    /**
     * Clear GATT cache via reflection â useful after allocation / status 133 errors.
     */
    fun clearGattCache() {
        val g = gatt ?: return
        try {
            val method = g.javaClass.getMethod("refresh")
            method.invoke(g)
            Log.i(TAG, "GATT cache refreshed via reflection")
        } catch (e: Exception) {
            Log.w(TAG, "GATT refresh failed: ${e.message}")
        }
    }
}

