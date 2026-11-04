package pl.dock.widget.session

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.dock.widget.transport.ElmTransport
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ELM327 session: init sequence + request/response only (AT MA blocked).
 *
 * Init: ATZ -> ATE0 -> ATSP6 -> ATH1 -> ATAT1 -> ATSTFF
 * (AT ST FF = maksymalny timeout na odpowiedz ECU Keihin)
 * Waits for prompt '>' (0x3E) via coroutine flow - no Thread.sleep.
 *
 * NAPRAWA 1 (race subskrypcji): wczesniej kazde zapytanie startowalo
 * kolektor `incomingLines.first()` dopiero OBOK zapisu komendy. SharedFlow
 * nie ma replay - jesli odpowiedz przyszla szybciej, niz korutyna zdazyla
 * sie zasubskrybowac, linie przepadly i zapytanie wieszalo sie do timeoutu
 * (8 s). Teraz JEDEN trwaly kolektor subskrybuje transport przez caly
 * cykl zycia sesji (onSubscription + CompletableDeferred gwarantuja
 * subskrypcje PRZED pierwszym zapisem) i przekazuje linie do kanalu.
 *
 * NAPRAWA 2 (serializacja): ELM327 to protokol szeregowy - dokladnie
 * jedna komenda w locie. Wczesniej PidScheduler (3 rownolegle tier-y),
 * keep-alive ATRV i rawRequest z UI (DTC 03/07/04, Mode 06, terminal,
 * e-raport) mogly pisac do adaptera jednoczesnie; prompt '>' z cudzej
 * odpowiedzi konczyl cudze zadanie -> desync danych. Teraz [requestMutex]
 * wymusza jedna komende naraz; odpowiedzi zbierane z [lineChannel].
 *
 * Read-Only Safety Lock: komendy modyfikujace ECU (2E WriteData,
 * 10 xx Session Control, 27 SecurityAccess, 30/31 aktywne testy)
 * sa zablokowane domyslnie. Odblokowanie w locie (bez reconnectu):
 * [allowMutating] ustawiane przez ConnectionManager.setServiceMode().
 *
 * Timer S3 (UDS): sesja rozszerzona wymaga podtrzymania 3E 00 co <5 s,
 * inaczej ECU degraduje sesje i resetuje autoryzacje Security Access
 * do LOCKED (wg raportu inzynierii wstecznej Keihin).
 */
class ElmSession(
    private val transport: ElmTransport,
    serviceMode: Boolean = false,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "ElmSession"
        private const val PROMPT_TIMEOUT_MS = 8_000L

        /** Monitor All – jedyna komenda ELM, którą blokujemy twardo (request/response only). */
        private const val AT_MA = "ATMA"

        /** Prefiksy komend modyfikujacych ECU (Read-Only Safety Lock). */
        private val MUTATING = setOf("2E", "27", "10", "30", "31")
    }

    /** Flaga trybu serwisowego - wlaczana np. z Terminala (bez reconnectu). */
    @Volatile var allowMutating: Boolean = serviceMode

    private val _responses = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val responses: SharedFlow<String> = _responses.asSharedFlow()

    /**
     * Kolejka linii z transportu dla zadania bedacego w locie.
     * UNLIMITED = zaden odczyt nie blokuje watku czytajacego socket/GATT.
     */
    private val lineChannel = Channel<String>(Channel.UNLIMITED)

    /** Jedna komenda ELM naraz (patrz NAPRAWA 2 wyzej). */
    private val requestMutex = Mutex()

    private val initialized = AtomicBoolean(false)
    private var collectorJob: Job? = null

    /**
     * Start trwalego kolektora linii. Wracaja DOPIERO po faktycznej
     * subskrypcji `incomingLines` (onSubscription + Deferred) - bez tego
     * pierwszy zapis ("ATZ") moglby wyprzedzic subskrypcje i odpowiedz
     * inicjalizacyjna by przepadla.
     */
    private suspend fun startCollecting() {
        if (collectorJob?.isActive == true) return
        val subscribed = CompletableDeferred<Unit>()
        collectorJob = scope.launch {
            transport.incomingLines
                .onSubscription { subscribed.complete(Unit) }
                .collect { line ->
                    _responses.emit(line)
                    lineChannel.send(line)
                }
        }
        subscribed.await()
    }

    fun stopCollecting() {
        collectorJob?.cancel()
        collectorJob = null
    }

    /**
     * Asynchronous init sequence. Suspends until each command receives prompt '>'.
     * Cala sekwencja pod [requestMutex] - nikt nie wtyka komend w trakcie init.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        requestMutex.withLock {
            startCollecting()
            sendAndWaitPrompt("ATZ")
            delay(300) // ATZ soft reset - brief settle only (not busy-wait on bus)
            sendAndWaitPrompt("ATE0")
            sendAndWaitPrompt("ATSP6")   // ISO 15765-4 CAN 11-bit 500 kbaud
            sendAndWaitPrompt("ATH1")    // headers on
            sendAndWaitPrompt("ATAT1")   // adaptive timing
            sendAndWaitPrompt("ATSTFF")  // max timeout odpowiedzi ECU (spec Honda Keihin)
            initialized.set(true)
            Log.i(TAG, "ELM327 initialized (SP6 / CAN 11-bit 500k / STFF)")
        }
    }

    /**
     * Send a single command and collect response until prompt.
     * Blocks AT MA (Monitor All) - forced request/response only.
     * Blocks mutating ECU commands unless serviceMode is enabled.
     * Serializacja: [requestMutex] pilnuje, ze w adapterze jest maksymalnie
     * jedna komenda - klientow (scheduler/terminal/DTC) moze byc wielu.
     */
    suspend fun request(command: String): String = withContext(Dispatchers.IO) {
        val cmd = command.trim().uppercase()
        val noSpace = cmd.replace(" ", "")
        if (noSpace == AT_MA) {
            throw SecurityException("AT MA (Monitor All) is blocked - request/response only")
        }
        if (!allowMutating && MUTATING.any { noSpace.startsWith(it) }) {
            throw SecurityException(
                "Read-Only Safety Lock: komenda '" + cmd +
                "' modyfikuje ECU - wlacz TRYB SERWISOWY w Terminalu"
            )
        }
        requestMutex.withLock {
            if (!transport.isConnected()) throw IllegalStateException("Transport not connected")
            sendAndWaitPrompt(command)
        }
    }

    private suspend fun sendAndWaitPrompt(command: String): String {
        // Wszystko, co jest w kolejce PRZED wyslaniem naszej komendy, to
        // odpowiedzi pozostale po poprzednim zadaniu (np. linie przyszle
        // po ostatnim promptcie) - czyscimy, zeby nie sklecic dwoch odpowiedzi.
        while (lineChannel.tryReceive().isSuccess) { /* drain */ }
        val buffer = StringBuilder()
        transport.write(command)
        return withTimeout(PROMPT_TIMEOUT_MS) {
            while (true) {
                val line = lineChannel.receive()
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

    fun isInitialized(): Boolean = initialized.get()

    suspend fun close() {
        stopCollecting()
        lineChannel.close()
        initialized.set(false)
        try { transport.disconnect() } catch (_: Exception) {}
    }
}
