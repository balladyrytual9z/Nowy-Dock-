package pl.dock.widget.util

/**
 * Bezalokacyjny skaner odpowiedzi ELM327 - wspoldzielony przez PidScheduler
 * (goraca sciezka RPM 10 Hz) i ConnectionManager. Trzymany w jednym miejscu,
 * zeby poprawki w delikatnej logice parsowania nie rozjechaly sie miedzy
 * kopiami.
 */
object ElmHex {

    /** Nibble (0-15) albo -1 dla znaku spoza hex. */
    fun nib(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'F' -> c - 'A' + 10
        in 'a'..'f' -> c - 'a' + 10
        else -> -1
    }

    /**
     * Skaner 16-bit (A*256+B) za naglowkiem PID [h0 h1 h2 h3] bez alokacji:
     * omija spacje/CR/LF/tab/'>' i czyta znak po znaku. Zwraca -1 przy braku.
     */
    fun scanAb(raw: String, h0: Int, h1: Int, h2: Int, h3: Int): Int {
        val len = raw.length
        var n0 = -1
        var n1 = -1
        var n2 = -1
        var n3 = -1
        var have = 0
        var i = 0
        while (i < len) {
            val c = raw[i]
            i++
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '>') continue
            val v = nib(c)
            if (v < 0) {
                n0 = -1; n1 = -1; n2 = -1; n3 = -1
                have = 0
                continue
            }
            n0 = n1; n1 = n2; n2 = n3; n3 = v
            if (have < 4) {
                have++
                continue
            }
            if (n0 == h0 && n1 == h1 && n2 == h2 && n3 == h3) {
                var ab = 0
                var got = 0
                while (i < len && got < 4) {
                    val c2 = raw[i]
                    i++
                    if (c2 == ' ' || c2 == '\n' || c2 == '\r' || c2 == '\t' || c2 == '>') continue
                    val v2 = nib(c2)
                    if (v2 < 0) return -1
                    ab = (ab shl 4) or v2
                    got++
                }
                return if (got == 4) ab else -1
            }
        }
        return -1
    }
}
