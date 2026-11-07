package pl.dock.widget.analytics

import java.util.Locale

/**
 * Modul analityczny Multi-Overlay (pkt 19): liczy korelacje Pearsona
 * miedzy parametrami i opisuje je po polsku. UI nie wymysla diagnozy -
 * prezentuje wylacznie tekst i status zwracany przez ten modul.
 *
 * Reguly anty-fabrykacyjne (pkt 45):
 *  - za malo probek -> status BRAK + jawny komunikat, zadnej korelacji;
 *  - seria stala (zerowa wariancja) jest pomijana;
 *  - raport podaje kierunek, wspolczynnik r i liczbe probek N.
 */
object CorrelationAnalyzer {

    const val STATUS_INFO = "INFO"
    const val STATUS_OK = "OK"
    const val STATUS_NO_DATA = "BRAK"

    /** Minimalna liczba wspolnych probek do liczenia korelacji. */
    const val MIN_SAMPLES = 20

    /** Maksymalna liczba linii raportu (ribbon czytelny, nie przytloczony). */
    private const val MAX_LINES = 2

    data class Finding(val a: String, val b: String, val r: Float, val n: Int)
    data class Report(val status: String, val text: String)

    /** Pary z dokumentu (pkt 19): obciazenie - korekty paliwowe - temperatura. */
    val PAIRS = arrayOf(
        "LOAD" to "LTFT",
        "LOAD" to "STFT",
        "ECT" to "LTFT",
        "IAT" to "ECT",
        "LOAD" to "MAP"
    )

    /** Polska nazwa parametru do tekstu raportu (pkt 19: opis po polsku). */
    fun plName(key: String): String = when (key) {
        "LOAD" -> "obciazenie silnika"
        "STFT" -> "korekta krotkotrwala STFT"
        "LTFT" -> "korekta paliwowa LTFT"
        "ECT" -> "temperatura cieczy ECT"
        "IAT" -> "temperatura dolotu IAT"
        "MAP" -> "cisnienie w kolektorze MAP"
        "RPM" -> "obroty silnika"
        "SPEED" -> "predkosc pojazdu"
        "TPS" -> "polozenie przepustnicy TPS"
        "VOLT" -> "napiecie akumulatora"
        else -> key
    }

    /**
     * Analizuje serie (timestamp -> wartosc); pary sa laczone po wspolnych
     * timestampach. Zwraca raport z co najwyzej [MAX_LINES] najmocniejszymi
     * zaleznosciami; |r| >= 0.5 podbija status do INFO.
     */
    fun analyze(series: Map<String, List<Pair<Long, Float>>>): Report {
        val found = ArrayList<Finding>()
        for ((a, b) in PAIRS) {
            val sa = series[a] ?: continue
            val sb = series[b] ?: continue
            val byTime = HashMap<Long, Float>()
            for ((t, v) in sb) byTime[t] = v
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            for ((t, v) in sa) {
                val other = byTime[t]
                if (other != null) {
                    xs.add(v)
                    ys.add(other)
                }
            }
            if (xs.size < MIN_SAMPLES) continue
            val r = pearson(xs.toFloatArray(), ys.toFloatArray())
            if (!r.isNaN()) found.add(Finding(a, b, r, xs.size))
        }
        if (found.isEmpty()) {
            return Report(STATUS_NO_DATA,
                "Analiza korelacji: za malo wspolnych probek (min " + MIN_SAMPLES + ")." +
                " Prowadz auto, dane zbieraja sie same.")
        }
        found.sortByDescending { Math.abs(it.r) }
        val sb = StringBuilder()
        var status = STATUS_OK
        for (i in 0 until Math.min(MAX_LINES, found.size)) {
            val f = found[i]
            if (i > 0) sb.append("\n")
            val dir = if (f.r > 0f) "dodatnia" else "ujemna"
            sb.append("Korelacja ").append(dir).append(": ")
                .append(plName(f.a)).append(" i ").append(plName(f.b))
                .append(" (r=").append(String.format(Locale.US, "%.2f", f.r))
                .append(", N=").append(f.n).append(" probek)")
            if (Math.abs(f.r) >= 0.5f) status = STATUS_INFO
        }
        return Report(status, sb.toString())
    }

    /** Wspolczynnik Pearsona; NaN przy zerowej wariancji lub n < 2. */
    fun pearson(xs: FloatArray, ys: FloatArray): Float {
        val n = Math.min(xs.size, ys.size)
        if (n < 2) return Float.NaN
        var sx = 0f
        var sy = 0f
        for (i in 0 until n) {
            sx += xs[i]
            sy += ys[i]
        }
        val mx = sx / n
        val my = sy / n
        var num = 0f
        var dx2 = 0f
        var dy2 = 0f
        for (i in 0 until n) {
            val dx = xs[i] - mx
            val dy = ys[i] - my
            num += dx * dy
            dx2 += dx * dx
            dy2 += dy * dy
        }
        if (dx2 <= 0f || dy2 <= 0f) return Float.NaN
        return (num / Math.sqrt((dx2 * dy2).toDouble())).toFloat()
    }
}
