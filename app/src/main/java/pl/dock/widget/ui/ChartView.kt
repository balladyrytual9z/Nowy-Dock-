package pl.dock.widget.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View

/**
 * Wykres pojedynczego parametru: gladka linia, subtelne wypelnienie (~20%),
 * min/max/avg, wartosc biezaca. Przechowuje ostatnie [capacity] probek w
 * kolowym buforze (bez alokacji w onDraw).
 */
class ChartView(context: Context) : View(context) {

    var label: String = ""
        set(v) { field = v; invalidate() }
    var unit: String = ""
        set(v) { field = v; invalidate() }
    var accentColor: Int = Color.parseColor("#0A84FF")

    private val capacity = 240 // ~2 min przy 0.5 s probkowaniu
    private val values = FloatArray(capacity)
    private val stamps = LongArray(capacity)
    private var head = 0
    private var count = 0

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
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
    private val path = Path()
    private val fillPath = Path()
    private var fillShader: LinearGradient? = null
    private var shaderBottom = -1f
    private var shaderAccent = Int.MIN_VALUE

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
        invalidate()
    }

    val sampleCount: Int get() = count

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        // naglowek: label + wartosc biezaca
        textPaint.color = Color.parseColor("#8E8E93")
        c.drawText(label, 24f, 44f, textPaint)
        val cur = current()
        val curText = if (cur.isNaN()) "---" else fmt(cur) + " " + unit
        valuePaint.color = accentColor
        c.drawText(curText, 24f, 88f, valuePaint)

        // linie siatki
        val top = 110f
        val bottom = h - 34f
        if (bottom <= top) return
        c.drawLine(24f, top, w - 24f, top, gridPaint)
        c.drawLine(24f, bottom, w - 24f, bottom, gridPaint)
        c.drawLine(24f, top, 24f, bottom, gridPaint)
        c.drawLine(w - 24f, top, w - 24f, bottom, gridPaint)

        if (count < 2) {
            textPaint.color = Color.parseColor("#48484A")
            c.drawText("brak danych", 30f, (top + bottom) / 2f, textPaint)
            return
        }

        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until count) {
            val v = values[(head - count + i + capacity) % capacity]
            if (v < minV) minV = v
            if (v > maxV) maxV = v
            sum += v
        }
        if (maxV - minV < 0.001f) { maxV += 0.5f; minV -= 0.5f }
        val span = (maxV - minV)

        val drawW = w - 48f
        val drawH = bottom - top
        path.reset()
        fillPath.reset()
        for (i in 0 until count) {
            val idx = (head - count + i + capacity) % capacity
            val x = 24f + drawW * i / (count - 1).toFloat()
            val y = bottom - drawH * (values[idx] - minV) / span
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
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

        // opisy min/max/avg
        textPaint.color = Color.parseColor("#8E8E93")
        textPaint.textSize = 20f
        c.drawText("min " + fmt(minV) + "  max " + fmt(maxV) + "  avg " + fmt(sum / count), 24f, h - 10f, textPaint)
        textPaint.textSize = 22f
    }

    private fun current(): Float =
        if (count == 0) Float.NaN else values[(head - 1 + capacity) % capacity]

    private fun fmt(v: Float): String =
        if (v >= 100f || v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v)
}
