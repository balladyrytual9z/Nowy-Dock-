package pl.dock.widget.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wykres pojedynczego parametru (pkt 18.1-18.6 i 47.1-47.5 dokumentu):
 * gladka krzywa (wygladzanie quad przez srodki odcinkow), subtelne
 * wypelnienie ~20%, osi z etykietami, min/max/avg WIDOCZNEGO okna,
 * scrubber z crosshair (wartosc + timestamp pomiaru przy przesuwaniu
 * palcem), wybor zakresu osi czasu (30 s / 1 min / 5 min / cala trasa)
 * i eksport PNG z legenda, zakresem i data.
 *
 * Zakaz fabryzacji (pkt 45): brak probek = "brak danych", nigdy zero
 * ani zmyslona linia. Kazda probka ma wlasny timestamp.
 */
class ChartView(context: Context) : View(context) {

    var label: String = ""
        set(v) { field = v; invalidate() }
    var unit: String = ""
        set(v) { field = v; invalidate() }
    var accentColor: Int = Color.parseColor("#0A84FF")

    /** Zakres osi czasu [ms]: 30_000 / 60_000 / 300_000 albo 0 = cala trasa. */
    var timeRangeMs: Long = 30_000L
        set(v) { field = v; scrubX = -1f; invalidate() }

    /** Bufor ograniczony (pkt 47.2): 10 min przy probkowaniu 0.5 s. */
    private val capacity = 1200
    private val values = FloatArray(capacity)
    private val stamps = LongArray(capacity)
    private var head = 0
    private var count = 0

    /** Pozycja scrubbera, -1 = ukryty (pkt 47.3: znika po gesie). */
    private var scrubX = -1f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textSize = 22f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#2C2C2E")
        strokeWidth = 1f
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFEEA")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val path = Path()
    private val fillPath = Path()
    private var fillShader: LinearGradient? = null
    private var shaderBottom = -1f
    private var shaderAccent = Int.MIN_VALUE
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun push(value: Float, timestamp: Long) {
        values[head] = value
        stamps[head] = timestamp
        head = (head + 1) % capacity
        if (count < capacity) count++
        invalidate()
    }

    fun reset() {
        head = 0
        count = 0
        scrubX = -1f
        invalidate()
    }

    val sampleCount: Int get() = count

    // ---------- Scrubber (pkt 18.3 / 47.3) ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                scrubX = e.x
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scrubX = -1f
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ---------- Eksport PNG (pkt 18.6 / 47.5) ----------

    /**
     * Zapisuje wykres do PNG (obszar wykresu + legenda + nazwa + jednostka
     * + zakres czasu + data). Zwraca sciezke pliku albo null przy braku
     * danych - nie zapisuje pustego tla (pkt 47.5).
     */
    fun exportPng(): String? {
        if (count < 2) return null
        val w = 720
        val h = 520
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#1C1C1E"))
        drawChart(c, w.toFloat(), (h - 130).toFloat(), false)
        // legenda pod wykresem (pkt 47.5)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 24f
        textPaint.color = Color.WHITE
        c.drawText(label + "  [" + unit + "]", 24f, (h - 92).toFloat(), textPaint)
        textPaint.textSize = 20f
        textPaint.color = Color.parseColor("#8E8E93")
        c.drawText("zakres: " + rangeLabel() + "   probki: " + visibleCount(now()).toString(),
            24f, (h - 58).toFloat(), textPaint)
        c.drawText(statsLine(), 24f, (h - 26).toFloat(), textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        c.drawText(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            (w - 24).toFloat(), (h - 26).toFloat(), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        val dir = context.getExternalFilesDir(null) ?: return null
        val safe = label.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val f = File(dir, "chart_" + safe + "_" + System.currentTimeMillis() + ".png")
        return try {
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun rangeLabel(): String = when (timeRangeMs) {
        0L -> "cala trasa"
        30_000L -> "30 s"
        60_000L -> "1 min"
        300_000L -> "5 min"
        else -> (timeRangeMs / 1000).toString() + " s"
    }

    private fun now(): Long = System.currentTimeMillis()

    /** Liczba probek w widocznym oknie (0 = caly bufor). */
    private fun visibleCount(now: Long): Int {
        if (timeRangeMs == 0L) return count
        var n = 0
        for (i in 0 until count) {
            val idx = (head - count + i + capacity) % capacity
            if (now - stamps[idx] <= timeRangeMs) n++
        }
        return n
    }

    private fun visibleIdx(i: Int, n: Int): Int =
        (head - n + i + capacity) % capacity

    private fun statsLine(): String {
        val n = visibleCount(now())
        if (n < 2) return "brak danych"
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until n) {
            val v = values[visibleIdx(i, n)]
            if (v < minV) minV = v
            if (v > maxV) maxV = v
            sum += v
        }
        return "min " + fmt(minV) + "  max " + fmt(maxV) + "  avg " + fmt(sum / n)
    }

    override fun onDraw(c: Canvas) {
        drawChart(c, width.toFloat(), height.toFloat(), true)
    }

    private fun drawChart(c: Canvas, w: Float, h: Float, clear: Boolean) {
        if (clear) c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // naglowek: label + wartosc biezaca (pkt 18.1)
        textPaint.color = Color.parseColor("#8E8E93")
        textPaint.textSize = 22f
        c.drawText(label, 24f, 44f, textPaint)
        val cur = current()
        val curText = if (cur.isNaN()) "---" else fmt(cur) + " " + unit
        valuePaint.color = accentColor
        c.drawText(curText, 24f, 88f, valuePaint)

        val top = 110f
        val bottom = h - 46f
        if (bottom <= top) return
        c.drawLine(24f, top, w - 24f, top, gridPaint)
        c.drawLine(24f, bottom, w - 24f, bottom, gridPaint)
        c.drawLine(24f, top, 24f, bottom, gridPaint)
        c.drawLine(w - 24f, top, w - 24f, bottom, gridPaint)

        val n = visibleCount(now())
        if (n < 2) {
            textPaint.color = Color.parseColor("#48484A")
            c.drawText("brak danych", 30f, (top + bottom) / 2f, textPaint)
            return
        }

        // skala liczona z WIDOCZNEGO okna (bez zmyslonej skali)
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until n) {
            val v = values[visibleIdx(i, n)]
            if (v < minV) minV = v
            if (v > maxV) maxV = v
            sum += v
        }
        if (maxV - minV < 0.001f) { maxV += 0.5f; minV -= 0.5f }
        val span = maxV - minV
        val drawW = w - 48f
        val drawH = bottom - top

        // etykiety osi Y (pkt 18.2: czytelne osi)
        textPaint.color = Color.parseColor("#636366")
        textPaint.textSize = 18f
        c.drawText(fmt(maxV), 30f, top + 16f, textPaint)
        c.drawText(fmt((maxV + minV) / 2f), 30f, (top + bottom) / 2f + 6f, textPaint)
        c.drawText(fmt(minV), 30f, bottom - 6f, textPaint)
        // etykiety osi X: zakres po lewej, "teraz" po prawej
        c.drawText(rangeLabel(), 24f, bottom + 30f, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        c.drawText("teraz", w - 24f, bottom + 30f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        // gladka krzywa (pkt 18.2: krzywe Beziera) + wypelnienie ~20%
        path.reset()
        fillPath.reset()
        var px = 0f
        var py = 0f
        for (i in 0 until n) {
            val x = 24f + drawW * i / (n - 1).toFloat()
            val y = bottom - drawH * (values[visibleIdx(i, n)] - minV) / span
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                val mx = (px + x) / 2f
                val my = (py + y) / 2f
                path.quadTo(px, py, mx, my)
                fillPath.lineTo(mx, my)
            }
            px = x
            py = y
        }
        path.lineTo(px, py)
        fillPath.lineTo(px, py)
        fillPath.lineTo(w - 24f, bottom)
        fillPath.close()
        if (fillShader == null || shaderBottom != bottom || shaderAccent != accentColor) {
            shaderBottom = bottom
            shaderAccent = accentColor
            fillShader = LinearGradient(0f, top, 0f, bottom,
                (accentColor and 0x00FFFFFF) or 0x33000000,
                (accentColor and 0x00FFFFFF) or 0x00000000,
                Shader.TileMode.CLAMP)
        }
        fillPaint.shader = fillShader
        c.drawPath(fillPath, fillPaint)
        linePaint.color = accentColor
        c.drawPath(path, linePaint)

        // scrubber: crosshair + najblizszy punkt + wartosc i czas (pkt 47.3)
        if (scrubX >= 24f && scrubX <= w - 24f) {
            val i = ((scrubX - 24f) / drawW * (n - 1)).toInt().coerceIn(0, n - 1)
            val x = 24f + drawW * i / (n - 1).toFloat()
            val idx = visibleIdx(i, n)
            val y = bottom - drawH * (values[idx] - minV) / span
            crossPaint.style = Paint.Style.STROKE
            crossPaint.color = Color.parseColor("#FFFEEA")
            c.drawLine(x, top, x, bottom, crossPaint)
            crossPaint.style = Paint.Style.FILL
            c.drawCircle(x, y, 5f, crossPaint)
            textPaint.color = Color.parseColor("#FFFEEA")
            textPaint.textSize = 20f
            val sTxt = fmt(values[idx]) + " " + unit + "  " + timeFmt.format(Date(stamps[idx]))
            val tx = if (x + 280f > w - 24f) x - 280f else x + 12f
            c.drawText(sTxt, tx, top + 24f, textPaint)
        }

        // min/max/avg widocznego okna (pkt 18.1)
        textPaint.color = Color.parseColor("#8E8E93")
        textPaint.textSize = 20f
        c.drawText("min " + fmt(minV) + "  max " + fmt(maxV) + "  avg " + fmt(sum / n), 24f, h - 12f, textPaint)
        textPaint.textSize = 22f
    }

    private fun current(): Float =
        if (count == 0) Float.NaN else values[(head - 1 + capacity) % capacity]

    private fun fmt(v: Float): String =
        if (v >= 100f || v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v)
}
