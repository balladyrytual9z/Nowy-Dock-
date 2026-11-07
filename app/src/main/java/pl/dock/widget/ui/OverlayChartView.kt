package pl.dock.widget.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.view.View

/**
 * Multi-Overlay Chart (pkt 19): kilka parametrow na jednym wykresie,
 * wspolna os X (czas), osobna linia i indywidualny kolor na parametr,
 * legenda z nazwa i jednostka. Rozne jednostki nie maja wspolnej skali
 * Y, wiec kazda linia jest znormalizowana do wlasnego zakresu w oknie
 * (jawnie podpisane na wykresie - bez udawania wspolnej skali).
 *
 * Zakaz fabrykacji: brak probek = brak linii, nigdy zmyslone dane;
 * bufor ograniczony (pkt 47.2), zero alokacji w onDraw.
 */
class OverlayChartView(context: Context) : View(context) {

    private class Series {
        val values = FloatArray(CAP)
        val stamps = LongArray(CAP)
        var head = 0
        var count = 0
    }

    private val series = LinkedHashMap<String, Series>()
    private val colors = LinkedHashMap<String, Int>()
    private val units = HashMap<String, String>()
    private val palette = intArrayOf(
        Color.parseColor("#0A84FF"),
        Color.parseColor("#FF3B30"),
        Color.parseColor("#30D158"),
        Color.parseColor("#FF9F0A"))

    /** Zakres osi X [ms]; 0 = caly bufor. */
    var timeRangeMs: Long = 30_000L
        set(v) { field = v; invalidate() }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textSize = 20f
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#2C2C2E")
        strokeWidth = 1f
    }
    private val path = Path()

    fun setUnit(name: String, unit: String) {
        units[name] = unit
    }

    fun push(name: String, value: Float, timestamp: Long) {
        val s = series.getOrPut(name) { Series() }
        if (!colors.containsKey(name)) {
            colors[name] = palette[colors.size % palette.size]
        }
        s.values[s.head] = value
        s.stamps[s.head] = timestamp
        s.head = (s.head + 1) % CAP
        if (s.count < CAP) s.count++
        invalidate()
    }

    /**
     * Migawka danych (ostatnie [SNAP] probek na serie) dla modulu
     * analitycznego CorrelationAnalyzer (pkt 19). Alokuje dopiero na
     * zadanie - nigdy w onDraw.
     */
    fun snapshot(): Map<String, List<Pair<Long, Float>>> {
        val out = LinkedHashMap<String, List<Pair<Long, Float>>>()
        for ((name, s) in series) {
            val n = Math.min(s.count, SNAP)
            val list = ArrayList<Pair<Long, Float>>(n)
            for (i in 0 until n) {
                val idx = (s.head - n + i + CAP) % CAP
                list.add(Pair(s.stamps[idx], s.values[idx]))
            }
            out[name] = list
        }
        return out
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val w = width.toFloat()
        val h = height.toFloat()
        val top = 40f + 26f * Math.min(series.size, 4)
        val bottom = h - 34f
        if (bottom <= top) return
        c.drawLine(24f, top, w - 24f, top, gridPaint)
        c.drawLine(24f, bottom, w - 24f, bottom, gridPaint)

        // legenda u gory: kolor + nazwa [jednostka] (pkt 19)
        var legendY = 26f
        for ((name, color) in colors) {
            if (legendY > top) break
            textPaint.color = color
            c.drawText(name + " [" + (units[name] ?: "") + "]", 24f, legendY, textPaint)
            legendY += 26f
        }

        val now = System.currentTimeMillis()
        textPaint.color = Color.parseColor("#636366")
        textPaint.textSize = 16f
        c.drawText(rangeLabel(), 24f, bottom + 22f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        c.drawText("skala znormalizowana do zakresu kazdej linii", w - 24f, bottom + 22f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 20f

        var drew = false
        for ((name, s) in series) {
            var n = 0
            if (timeRangeMs == 0L) {
                n = s.count
            } else {
                for (i in 0 until s.count) {
                    val idx = (s.head - s.count + i + CAP) % CAP
                    if (now - s.stamps[idx] <= timeRangeMs) n++
                }
            }
            if (n < 2) continue
            var minV = Float.MAX_VALUE
            var maxV = -Float.MAX_VALUE
            for (i in 0 until n) {
                val v = s.values[(s.head - n + i + CAP) % CAP]
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
            if (maxV - minV < 0.001f) { maxV += 0.5f; minV -= 0.5f }
            val span = maxV - minV
            val drawW = w - 48f
            val drawH = bottom - top
            path.reset()
            var px = 0f
            var py = 0f
            for (i in 0 until n) {
                val idx = (s.head - n + i + CAP) % CAP
                val age = now - s.stamps[idx]
                val x = if (timeRangeMs == 0L)
                    24f + drawW * i / (n - 1).toFloat()
                else
                    24f + drawW * (1f - age.toFloat() / timeRangeMs)
                val y = bottom - drawH * (s.values[idx] - minV) / span
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    val mx = (px + x) / 2f
                    val my = (py + y) / 2f
                    path.quadTo(px, py, mx, my)
                }
                px = x
                py = y
            }
            path.lineTo(px, py)
            linePaint.color = colors[name] ?: Color.WHITE
            c.drawPath(path, linePaint)
            drew = true
        }
        if (!drew) {
            textPaint.color = Color.parseColor("#48484A")
            c.drawText("brak danych", 30f, (top + bottom) / 2f, textPaint)
        }
    }

    private fun rangeLabel(): String = when (timeRangeMs) {
        0L -> "cala trasa"
        30_000L -> "30 s"
        60_000L -> "1 min"
        300_000L -> "5 min"
        else -> (timeRangeMs / 1000).toString() + " s"
    }

    companion object {
        /** Bufor ograniczony (pkt 47.2): 10 min przy 0.5 s. */
        private const val CAP = 1200
        /** Probki na serie w migawce dla analizy. */
        private const val SNAP = 120
    }
}
