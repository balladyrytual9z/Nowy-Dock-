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
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ELM327 session: init sequence + request/response only (AT MA blocked).
 *
 * NAPRAWA 1 (race subskrypcji) i NAPRAWA 2 (serializacja) - bez zmian,
 * patrz AGENTS.md / historia w git.
 *
 * NAPRAWA 3 (2026-11-07, "laczy sie, ale nic sie nie dzieje"):
 * stary init zakladal idealny adapter (ATZ -> 300 ms -> ATE0 jedna
 * proba -> ATSP6 NA SZTYWNO -> ATSTFF) i NIGDY nie sprawdzal, czy ECU
 * odpowiada - ConnectionManager publikowal "polaczono" po samym
 * sockecie SPP. Tani klon ELM327 po ATZ polyka pierwsza komende
 * i odpowiada "?" na legalne AT-zlecenia - a zegary pokazywaly "-" .
 *
 * Nowy init (wzorce z miskibin/obd2-dashboard, dostosowane do
 * Honda Civic VIII 2008-11 / ECU Keihin / CAN 11-bit 500k):
 *  1. ATWS (warm start, nie zrywa linku BT) -> fallback ATZ + 3 s
 *     (klon po ATZ restartuje wewnetrzny stos i gubi komendy),
 *  2. ATE0 - 3 proby po 3 s: jedyna komenda, ktora MUSI wejsc
 *     (cale ramkowanie zalezy od wylaczonego echa),
 *  3. komendy ustawien OPCJONALNE (klon odpowiada "?" i to nie
 *     porazka): ATL0, ATH1, ATAT1, ATST32 - przy 60 ms odstepu
 *     (klony gubia komendy wyslane zaraz po poprzedniej odpowiedzi),
 *  4. protokol: preferowany z prefs (obd_protocol, Civic = "6")
 *     albo ATSP0 (auto-detekcja wszystkich protokolow ELM),
 *  5. SONDA "0100" - pierwszy RAZ init sprawdza, ze ECU odpowiada
 *     (8 s gdy znamy protokol / 15 s przy auto-detekcji),
 *  6. ATDPN (potwierdzenie protokolu) + ATI (id) + ATRV (napiecie).
 *
 * HYBRYDOWY ATST (decyzja uzytkownika 2026-11-07, pod Keihin):
 *  - ATST32 (~200 ms) - rura PID: NO DATA konczy sie po 200 ms,
 *    Fast tier 10 Hz nie zapycha kolejki (wczesniej ~1 s blokady),
 *  - ATSTFF (~1020 ms) - przed wolnymi trybami diagnostycznymi Hondy
 *    (03/04/06/07/09/0A/21xx/22xx: DTC, kasowanie, Mode06, pending,
 *    VIN, wtrysk 2101) sesja sama podbija timeout i wraca do ATST32
 *    po odpowiedzi - cierpliwosc tam gdzie potrzebna, responsywnosc
 *    dla zegarow.
 *  - ATS0 celowo NIE wysylane: parsery (ElmHex.scanAb, parseByte)
 *    licza na spacje w ramkach.
 *
 * Filtr szumu (SEARCHING.../BUS INIT/STOPPED) - [ElmInitSupport.stripNoise];
 * bajty 0x00 odfiltrowane w czytnikach transportow (dokumentowany quirk ELM327).
 *
 * Read-Only Safety Lock (2E/27/10/30/31) - bez zmian.
 */
class ElmSession(
    private val transport: ElmTransport,
    serviceMode: Boolean = false,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    /**
     * Wynik initu - jedyny kontrakt ConnectionManagera. Failure niesie
     * nazwe kroku i czytelny blad (trafia do lastError w UI).
     */
    sealed class InitResult {
        data class Success(
            val protocolDigit: String,
            val protocolName: String,
            val autoDetected: Boolean,
            val adapterId: String,
            val voltageV: Float,
            val unsupported: List<String>
        ) : InitResult()

        data class Failure(val step: String, val error: String) : InitResult()
    }

    companion object {
        private const val TAG = "ElmSession"

        /** Timeout promptu dla zapytan PID / trybow diagnostycznych. */
        private const val PROMPT_TIMEOUT_MS = 8_000L

        /** AT-zlecenia: klony potrafia odpowiedziec dopiero po ~2-3 s. */
        private const val AT_TIMEOUT_MS = 3_000L

        /** Sonda 0100 na znanym protokole. */
        private const val PROBE_KNOWN_MS = 8_000L

        /** Sonda 0100 przy ATSP0: ELM przeszukuje wszystkie protokoly. */
        private const val PROBE_SEARCH_MS = 15_000L

        /** ATZ = pelny self-test chipu; klon wstaje dopiero po ~3 s. */
        private const val COLD_RESET_WAIT_MS = 3_000L

        /** ATWS = warm start (szybszy, nie zrywa linku BT). */
        private const val WARM_RESET_WAIT_MS = 1_500L

        /** Klony gubia komendy wyslane bezposrednio po poprzedniej odpowiedzi. */
        private const val INTER_CMD_MS = 60L

        /** ATE0 - jedyne AT-zlecenie, bez ktorego ramkowanie pada. */
        private const val ECHO_OFF_ATTEMPTS = 3

        /** Monitor All - jedyna komenda ELM blokowana twardo. */
        private const val AT_MA = "ATMA"

        /** Prefiksy komend modyfikujacych ECU (Read-Only Safety Lock). */
        private val MUTATING = setOf("2E", "27", "10", "30", "31")

        /** Hybrydowy timeout ELM: ~200 ms (0x32 x 4 ms) dla rury PID. */
        private const val ST_FAST = "ATST32"

        /** Hybrydowy timeout ELM: ~1020 ms (0xFF x 4 ms) dla wolnych trybow. */
        private const val ST_SLOW = "ATSTFF"
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

    /** Jedna komenda ELM naraz (patrz NAPRAWA 2). */
    private val requestMutex = Mutex()

    private val initialized = AtomicBoolean(false)
    private var collectorJob: Job? = null

    /**
     * Start trwalego kolektora linii (NAPRAWA 1 - subskrypcja PRZED
     * pierwszym zapisem, inaczej odpowiedz initu przepadla).
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
     * Pelna sekwencja initu (opis w naglowku klasy). Zwraca [InitResult]:
     * Success z POTWIERDZONYM protokolem i napieciem albo Failure z nazwa
     * kroku i czytelnym bledem - koniec cichego "polaczono" przy milczacym
     * ECU. [onStep] raportuje aktualny krok do UI (VehicleUiState.initStep).
     *
     * @param preferredProtocol cyfra protokolu z prefs ("6" = ISO 15765-4
     *        CAN 11-bit 500k dla Civic VIII 2008-11) albo null -> ATSP0.
     */
    suspend fun initialize(
        preferredProtocol: String? = null,
        onStep: (String) -> Unit = {}
    ): InitResult = withContext(Dispatchers.IO) {
        requestMutex.withLock {
            startCollecting()
            val unsupported = mutableListOf<String>()

            // 1) reset: warm start; gdy klon nie zna ATWS ("?") -> ATZ + 3 s
            onStep("ATWS")
            val warm = sendAndWaitPrompt("ATWS", AT_TIMEOUT_MS)
            if (warm == null || warm.trim() == "?") {
                onStep("ATZ")
                sendAndWaitPrompt("ATZ", PROMPT_TIMEOUT_MS)
                delay(COLD_RESET_WAIT_MS)
            } else {
                delay(WARM_RESET_WAIT_MS)
            }

            // 2) ATE0 x3 - jedyna komenda, ktora MUSI wejsc
            var echoOk = false
            for (i in 1..ECHO_OFF_ATTEMPTS) {
                onStep("ATE0")
                val r = sendAndWaitPrompt("ATE0", AT_TIMEOUT_MS)
                delay(INTER_CMD_MS)
                if (r != null && r.uppercase().contains("OK")) { echoOk = true; break }
            }
            if (!echoOk) {
                initialized.set(false)
                Log.w(TAG, "init FAILED przy ATE0: adapter nie potwierdza echo off")
                return@withLock InitResult.Failure(
                    "ATE0", "adapter nie potwierdza echo off (3 proby) - kabel/adapter")
            }

            // 3) ustawienia opcjonalne - klon moze odpowiedziec "?" i to OK
            for (cmd in listOf("ATL0", "ATH1", "ATAT1", ST_FAST)) {
                onStep(cmd)
                val r = sendAndWaitPrompt(cmd, AT_TIMEOUT_MS)
                if (r == null || r.trim() == "?") unsupported.add(cmd)
                delay(INTER_CMD_MS)
            }

            // 4) protokol: preferowany (Honda Civic VIII 2008-11 = "6") / auto
            val preferred = preferredProtocol?.trim()?.uppercase()
                ?.takeIf { it.isNotEmpty() && it != "0" }
            val probeTimeout = if (preferred != null) PROBE_KNOWN_MS else PROBE_SEARCH_MS
            onStep("ATSP" + (preferred ?: "0"))
            sendAndWaitPrompt("ATSP" + (preferred ?: "0"), AT_TIMEOUT_MS)
            delay(INTER_CMD_MS)

            // 5) SONDA 0100 - pierwszy test, ze ECU NAPRAWDE odpowiada
            onStep("0100")
            var probe = sendAndWaitPrompt("0100", probeTimeout)
            if (!ElmInitSupport.probeOk(probe) && preferred != null) {
                // znany protokol nie zadzialal -> auto-detekcja (K-Line itp.)
                onStep("ATSP0")
                sendAndWaitPrompt("ATSP0", AT_TIMEOUT_MS)
                delay(INTER_CMD_MS)
                onStep("0100 (auto)")
                probe = sendAndWaitPrompt("0100", PROBE_SEARCH_MS)
            }
            if (!ElmInitSupport.probeOk(probe)) {
                initialized.set(false)
                val err = ElmInitSupport.errorName(probe)
                Log.w(TAG, "init FAILED przy 0100: " + err)
                return@withLock InitResult.Failure("0100", err)
            }

            // 6) potwierdzenie protokolu + identyfikator + napiecie
            onStep("ATDPN")
            val dpn = ElmInitSupport.parseDpn(sendAndWaitPrompt("ATDPN", AT_TIMEOUT_MS))
            var digit = dpn?.first ?: preferred ?: "0"
            val auto = dpn?.second ?: (preferred == null)
            if (dpn != null && dpn.second && digit != "0") {
                // utrwal wykryty protokol, zeby ELM nie szukal ponownie
                sendAndWaitPrompt("ATSP" + digit, AT_TIMEOUT_MS)
            }
            delay(INTER_CMD_MS)
            onStep("ATI")
            val id = sendAndWaitPrompt("ATI", AT_TIMEOUT_MS)
                ?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim() ?: ""
            delay(INTER_CMD_MS)
            onStep("ATRV")
            val voltage = ElmInitSupport.parseVoltage(sendAndWaitPrompt("ATRV", AT_TIMEOUT_MS))
            delay(INTER_CMD_MS)

            initialized.set(true)
            Log.i(TAG, "ELM init OK: " + ElmInitSupport.protocolName(digit) +
                " (digit=" + digit + " auto=" + auto + " id=" + id +
                " U=" + voltage + "V odrzucone=" + unsupported + ")")
            InitResult.Success(
                protocolDigit = digit,
                protocolName = ElmInitSupport.protocolName(digit),
                autoDetected = auto,
                adapterId = id,
                voltageV = voltage,
                unsupported = unsupported
            )
        }
    }

    /**
     * Send a single command and collect response until prompt.
     * Blocks AT MA (Monitor All). Blocks mutating ECU commands unless
     * serviceMode is enabled. HYBRYDA ATST: przed wolnymi trybami
     * diagnostycznymi ([ElmInitSupport.isSlowDiagnostic]) sesja sama
     * podbija timeout do ~1 s i wraca do ~200 ms po odpowiedzi.
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
            val slow = ElmInitSupport.isSlowDiagnostic(noSpace)
            if (slow) {
                // cierpliwosc Keihina dla DTC / VIN / Mode06 / wtrysku 2101
                try { sendAndWaitPrompt(ST_SLOW, AT_TIMEOUT_MS) } catch (_: Exception) {}
            }
            try {
                sendAndWaitPrompt(cmd, PROMPT_TIMEOUT_MS)
                    ?: throw IOException("timeout: brak promptu '>' na komendzie " + cmd)
            } finally {
                if (slow) {
                    try { sendAndWaitPrompt(ST_FAST, AT_TIMEOUT_MS) } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Wysyla komende i zbiera linie do promptu '>'.
     * Zwraca null po timeoutie (request rzuca IOException, init liczy
     * porazke kroku). Filtr szumu: SEARCHING.../BUS INIT/STOPPED
     * (patrz [ElmInitSupport.stripNoise]); echo komendy odfiltrowane.
     */
    private suspend fun sendAndWaitPrompt(
        command: String,
        timeoutMs: Long = PROMPT_TIMEOUT_MS
    ): String? {
        // drain odpowiedzi z poprzedniego zadania - nie sklejamy ramek
        while (lineChannel.tryReceive().isSuccess) { /* drain */ }
        transport.write(command)
        return withTimeoutOrNull(timeoutMs) {
            val buffer = StringBuilder()
            while (true) {
                val line = lineChannel.receive()
                if (line.contains('>')) break
                val meaningful = ElmInitSupport.stripNoise(line) ?: continue
                if (meaningful.equals(command.trim(), ignoreCase = true)) continue // echo
                if (buffer.isNotEmpty()) buffer.append('\n')
                buffer.append(meaningful)
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
