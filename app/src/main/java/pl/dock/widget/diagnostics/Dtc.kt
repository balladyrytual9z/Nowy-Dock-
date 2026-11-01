package pl.dock.widget.diagnostics

/**
 * Parser odpowiedzi Mode 03 (confirmed) i 07 (pending) ELM327 (ATH1 wlaczone).
 * Odpowiedzi Honda Keihin: "7E8 06 43 01 03 03 00 00 00" itp.
 */
object Dtc {

    data class Code(val code: String, val pending: Boolean, val description: String)

    private val DESCRIPTIONS = mapOf(
        "P0100" to "Usterka obwodu przeplywomierza powietrza (MAF)",
        "P0101" to "Sygnal MAF poza zakresem / nieprawidlowy",
        "P0102" to "Sygnal MAF za niski",
        "P0103" to "Sygnal MAF za wysoki",
        "P0106" to "Usterka czujnika cisnienia kolektora (MAP)",
        "P0107" to "Sygnal MAP za niski",
        "P0108" to "Sygnal MAP za wysoki",
        "P0112" to "Sygnal IAT za niski (zwarcie do masy)",
        "P0113" to "Sygnal IAT za wysoki (przerwa w obwodzie)",
        "P0116" to "Sygnal ECT poza zakresem",
        "P0117" to "Sygnal ECT za niski",
        "P0118" to "Sygnal ECT za wysoki",
        "P0121" to "Usterka czujnika polozenia przepustnicy (TPS)",
        "P0122" to "Sygnal TPS za niski",
        "P0123" to "Sygnal TPS za wysoki",
        "P0128" to "Termostat - silnik nie osiaga temperatury roboczej",
        "P0131" to "Sonda O2 (rzad 1, czujnik 1) - sygnal za niski",
        "P0132" to "Sonda O2 (rzad 1, czujnik 1) - sygnal za wysoki",
        "P0133" to "Sonda O2 (rzad 1, czujnik 1) - wolna odpowiedz",
        "P0134" to "Sonda O2 (rzad 1, czujnik 1) - brak aktywnosci",
        "P0135" to "Grzalka sondy O2 (rzad 1, czujnik 1)",
        "P0171" to "Mieszanka zbyt uboga (rzad 1)",
        "P0172" to "Mieszanka zbyt bogata (rzad 1)",
        "P0300" to "Wykryto wypadanie zaplonow (losowe cylindry)",
        "P0301" to "Wypadanie zaplonu - cylinder 1",
        "P0302" to "Wypadanie zaplonu - cylinder 2",
        "P0303" to "Wypadanie zaplonu - cylinder 3",
        "P0304" to "Wypadanie zaplonu - cylinder 4",
        "P0325" to "Usterka obwodu czujnika spalania stukowego",
        "P0335" to "Usterka czujnika polozenia walu korbowego (CKP)",
        "P0336" to "Sygnal CKP poza zakresem",
        "P0340" to "Usterka czujnika polozenia walu rozrzadu (CMP)",
        "P0420" to "Sprawnosc katalizatora ponizej progu (rzad 1)",
        "P0442" to "EVAP - wykryty niewielki wyciek",
        "P0455" to "EVAP - wykryty duzy wyciek",
        "P0497" to "EVAP - niski przeplyw purgi",
        "P0562" to "Napiecie systemowe za niskie",
        "P0563" to "Napiecie systemowe za wysokie",
        "P0700" to "Usterka ukladu kontroli skrzyni biegow (wymaga odczytu TCM)",
        "C1555" to "Usterka ukladu hamulcowego (ABS/VSA) - wymagany odczyt modulu",
        "U0100" to "Utrata lacznosci z modulem ECU (CAN)",
        "U0121" to "Utrata lacznosci z modulem ABS (CAN)"
    )

    fun describe(code: String): String =
        DESCRIPTIONS[code] ?: "Opis niedostepny w lokalnej bazie - zweryfikuj kod"

    /**
     * Parsuje surowa odpowiedz ELM327 na "03" lub "07".
     * Obsluguje naglowki CAN (ATH1), wieloliniowosc, "NO DATA" i znak zachety '>'.
     * Zwraca pusta liste przy braku kodow - nigdy nie zwraca zmyslonych danych.
     */
    fun parse(response: String?, pending: Boolean): List<Code> {
        if (response.isNullOrBlank()) return emptyList()
        val clean = response.replace(">", " ")
        if (clean.contains("NO DATA", ignoreCase = true)) return emptyList()
        if (clean.contains("UNABLE", ignoreCase = true)) return emptyList()
        if (clean.contains("CAN ERROR", ignoreCase = true)) return emptyList()

        val codes = ArrayList<Code>(8)
        for (line in clean.split("\r?\n".toRegex())) {
            val bytes = hexBytes(line) ?: continue
            var i = 0
            while (i < bytes.size && (bytes[i].toInt() and 0xFF) != 0x43 && (bytes[i].toInt() and 0xFF) != 0x47) i++
            i++
            while (i + 1 < bytes.size) {
                val b1 = bytes[i].toInt() and 0xFF
                val b2 = bytes[i + 1].toInt() and 0xFF
                if (b1 == 0x00 && b2 == 0x00) break
                val code = decode(b1, b2)
                if (codes.none { it.code == code }) {
                    codes.add(Code(code, pending, describe(code)))
                }
                i += 2
            }
        }
        return codes
    }

    /** Wyciaga bajty hex z linii, pomijajac naglowki CAN (np. "7E8", "7E8:"). */
    private fun hexBytes(line: String): ByteArray? {
        val tokens = line.trim().split(Regex("[ :]")).filter { it.isNotBlank() }
        val out = ArrayList<Int>(16)
        for (t in tokens) {
            val s = t.trim()
            var i = 0
            while (i + 2 <= s.length) {
                val v = s.substring(i, i + 2).toIntOrNull(16) ?: return null
                out.add(v)
                i += 2
            }
        }
        if (out.isEmpty()) return null
        return out.map { (it and 0xFF).toByte() }.toByteArray()
    }

    private fun decode(b1: Int, b2: Int): String {
        val letter = when ((b1 shr 6) and 0x03) {
            0x00 -> "P"
            0x01 -> "C"
            0x02 -> "B"
            else -> "U"
        }
        val num = ((b1 and 0x3F) shl 8) or (b2 and 0xFF)
        return letter + String.format("%04X", num)
    }

    /** Czy kasowanie (Mode 04) sie powiodlo - na podstawie surowej odpowiedzi. */
    fun clearSucceeded(response: String?): Boolean {
        if (response.isNullOrBlank()) return false
        val clean = response.replace(">", " ")
        if (clean.contains("NO DATA", ignoreCase = true)) return false
        if (clean.contains("ERROR", ignoreCase = true)) return false
        if (clean.contains("UNABLE", ignoreCase = true)) return false
        if (clean.contains("BUS", ignoreCase = true)) return false
        val hex = clean.replace(Regex("[^0-9A-Fa-f ]"), " ")
        return Regex("44").containsMatchIn(hex)
    }
}
