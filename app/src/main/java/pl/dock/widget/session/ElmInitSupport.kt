package pl.dock.widget.session

/**
 * Czysta logika initu ELM327 - zero android.*, w pelni testowalna na JVM
 * (straznik: ElmInitSupportTest, CI krok "Run Unit Tests").
 *
 * Wzorce z miskibin/obd2-dashboard (ElmInitializer/ElmSession) dostosowane
 * do tanich klonow ELM327 (AliExpress) i Hondy Civic VIII (ECU Keihin).
 */
object ElmInitSupport {

    /** "SEARCHING..." - ELM szuka protokolu (ATSP0, 5-baud init). */
    private val SEARCHING = Regex("SEARCHING\\.*", RegexOption.IGNORE_CASE)

    /**
     * Prefiksy trybow, ktore na Hondzie (Keihin) odpowiadaja wolniej
     * niz rura PID: 03 DTC, 04 kasowanie, 06 Mode06, 07 pending,
     * 09 VIN, 0A stare DTC, 21xx wtrysk (KWP), 22xx UDS.
     */
    private val SLOW_PREFIXES = listOf("03", "04", "06", "07", "09", "0A", "21", "22")

    /** Komunikaty bledow magistrali/ECU zwracane przez ELM327 (bez spacji). */
    private val ECU_ERRORS = listOf(
        "NODATA", "UNABLETOCONNECT", "CANERROR", "BUSERROR",
        "FBERROR", "DATAERROR", "BUFFERFULL"
    )

    /** Czy komenda (bez spacji, uppercase) to wolny tryb diagnostyczny. */
    fun isSlowDiagnostic(cmdNoSpaces: String): Boolean {
        if (cmdNoSpaces.startsWith("AT")) return false
        return SLOW_PREFIXES.any { cmdNoSpaces.startsWith(it) }
    }

    /**
     * Zwraca istotna czesc linii albo null, gdy cala linia to szum
     * (SEARCHING... / BUS INIT... / STOPPED / pusta).
     */
    fun stripNoise(line: String): String? {
        val t = line.trim()
        if (t.isEmpty()) return null
        val noSearching = SEARCHING.replace(t, "").trim()
        if (noSearching.isEmpty()) return null
        val up = noSearching.uppercase()
        if (up.startsWith("BUS INIT") || up.startsWith("BUSINIT")) return null
        if (up == "STOPPED") return null
        return noSearching
    }

    /** Czy odpowiedz oznacza milczenie ECU (timeout tez - raw == null). */
    fun isEcuSilence(raw: String?): Boolean {
        if (raw == null) return true
        val compact = raw.replace(" ", "").uppercase()
        if (compact.isEmpty()) return true
        if (compact == "?") return true
        return ECU_ERRORS.any { compact.contains(it) }
    }

    /** Sonda 0100 udana = w odpowiedzi jest bitmaska PID 00 (41 00 ...). */
    fun probeOk(raw: String?): Boolean =
        !isEcuSilence(raw) && raw!!.replace(" ", "").uppercase().contains("4100")

    /** ATDPN -> ("6", false) albo ("6", true) gdy auto-detekcja (prefiks "A"). */
    fun parseDpn(raw: String?): Pair<String, Boolean>? {
        val token = raw?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: return null
        val up = token.trim().uppercase().replace(" ", "")
        if (up.startsWith("A") && up.length > 1) return up.substring(1) to true
        if (up.isNotEmpty() && up.first() in "123456789ABC") return up to false
        return null
    }

    /** Czytelna nazwa protokolu ELM (z ATDPN) do publikacji w UI. */
    fun protocolName(digit: String): String = when (digit.trim().uppercase()) {
        "1" -> "SAE J1850 PWM"
        "2" -> "SAE J1850 VPW"
        "3" -> "ISO 9141-2 (K-Line)"
        "4" -> "ISO 14230-4 KWP (5-baud init)"
        "5" -> "ISO 14230-4 KWP (fast init)"
        "6" -> "ISO 15765-4 CAN 11-bit 500k"
        "7" -> "ISO 15765-4 CAN 29-bit 500k"
        "8" -> "ISO 15765-4 CAN 11-bit 250k"
        "9" -> "ISO 15765-4 CAN 29-bit 250k"
        "A" -> "SAE J1939 CAN 29-bit"
        "B" -> "user-defined (user1)"
        "C" -> "user-defined CAN (user2)"
        else -> "auto"
    }

    /** ATRV -> "12.6V" => 12.6; NaN gdy brak odczytu. */
    fun parseVoltage(raw: String?): Float {
        val line = raw?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() && it[0].isDigit() && it.contains('V', ignoreCase = true) }
            ?: return Float.NaN
        val num = Regex("[0-9]+(\\.[0-9]+)?").find(line)?.value ?: return Float.NaN
        return num.toFloatOrNull() ?: Float.NaN
    }

    /** Czytelny opis bledu initu (trafia do lastError w UI). */
    fun errorName(raw: String?): String {
        if (raw == null) return "timeout - adapter lub ECU nie odpowiada"
        val compact = raw.replace(" ", "").uppercase()
        if (compact.isEmpty()) return "brak odpowiedzi adaptera"
        if (compact == "?") return "komenda nieznana dla adaptera (?)"
        if (compact.contains("UNABLETOCONNECT")) return "UNABLE TO CONNECT - ECU nie odpowiada (sprawdz zaplon/protokol)"
        if (compact.contains("NODATA")) return "NO DATA - ECU nie zwrocil danych"
        if (compact.contains("CANERROR")) return "CAN ERROR - blad magistrali CAN"
        if (compact.contains("BUSERROR")) return "BUS ERROR - blad inicjalizacji magistrali"
        if (compact.contains("BUFFERFULL")) return "BUFFER FULL - bufor ELM327 przepelniony"
        return "brak maski PID 00 w odpowiedzi"
    }
}
