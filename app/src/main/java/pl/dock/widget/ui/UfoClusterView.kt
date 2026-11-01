package pl.dock.widget.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * UFO Cluster – OBD dashboard rendered directly on Canvas.
 *
 * ZERO-ALLOCATION CONTRACT: [onDraw] never allocates. All Paint / RectF /
 * StringBuilder objects are created in the constructor / [onSizeChanged].
 * canvas.clipRect() limits redraw to the dirty area (no GPU overdraw)
 * on Mali-T720.
 */
class UfoClusterView(context: Context) : View(context) {

    companion object {
        const val MAX_RPM = 8000
        const val REDLINE_RPM = 6800
        const val MAX_KMH = 220
        const val STALE_MS = 5_000L
    }

    // --- live state (written by update(), read by onDraw) ---
    @Volatile private var rpm = -1
    @Volatile private var speedKmh = -1
    @Volatile private var voltage = Float.NaN
    @Volatile private var ectC = Float.NaN
    @Volatile private var lastUpdateMs = 0L
    @Volatile private var connected = false
    @Volatile private var night = true

    // --- pre-allocated drawing objects (NEVER touched in onDraw) ---
    private val arcRect = RectF()
    private val arcBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 18f; color = 0x33FFFFFF
    }
    private val arcFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND; color = 0xFF0A84FF.toInt()
    }
    private val arcRed = Paint(arcFg).apply { color = 0xFFFF453A.toInt() }
    private val textMain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER
    }
    private val textDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF; textAlign = Paint.Align.CENTER
    }
    private val voltBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF30D158.toInt() }
    private val voltBarLow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD60A.toInt() }

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    init {
        textMain.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textDim.typeface = Typeface.DEFAULT
    }

    /** UI thread / service updates telemetry; triggers a repaint. */
    fun update(rpm: Int, speedKmh: Int, voltage: Float, ectC: Float, connected: Boolean) {
        this.rpm = rpm
        this.speedKmh = speedKmh
        this.voltage = voltage
        this.ectC = ectC
        this.connected = connected
        this.lastUpdateMs = System.currentTimeMillis()
        postInvalidate()
    }

    fun setNight(n: Boolean) {
        night = n
        val fg = if (n) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
        textMain.color = fg
        arcBg.color = if (n) 0x33FFFFFF else 0x22000000
        postInvalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h * 0.58f
        radius = Math.min(w, h) * 0.40f
        textMain.textSize = radius * 0.52f
        textDim.textSize = radius * 0.14f
    }

    override fun onDraw(canvas: Canvas) {
        // clip to this view – avoid overdraw of layers beneath
        canvas.clipRect(0, 0, width, height)
        val stale = lastUpdateMs > 0 && System.currentTimeMillis() - lastUpdateMs > STALE_MS

        // 1) RPM arc (background)
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arcRect, 135f, 270f, false, arcBg)

        // 2) RPM arc (value) – red past redline
        val r = if (rpm < 0) 0f else rpm.coerceAtMost(MAX_RPM).toFloat() / MAX_RPM
        if (r > 0f) {
            val p = arcFg
            if (rpm >= REDLINE_RPM) p = arcRed
            canvas.drawArc(arcRect, 135f, 270f * r, false, p)
        }

        // 3) digital speed in the middle
        if (speedKmh >= 0) drawInt(canvas, speedKmh, cx, cy + textMain.textSize * 0.35f, textMain)
        else canvas.drawText("--", cx, cy + textMain.textSize * 0.35f, textMain)
        canvas.drawText("km/h", cx, cy + radius * 0.42f, textDim)

        // 4) RPM caption + stale marker (grey filter)
        if (rpm >= 0) drawInt(canvas, rpm, cx - textDim.measureText(" RPM") * 0.5f, cy - radius * 0.55f, textDim)
        else canvas.drawText("--", cx, cy - radius * 0.55f, textDim)
        canvas.drawText(" RPM", cx + textDim.measureText("0000") * 0.4f, cy - radius * 0.55f, textDim)
        if (stale) {
            canvas.drawText("[stale]", cx, cy - radius * 0.72f, textDim)
        }
        if (!connected) {
            canvas.drawText("brak połączenia OBD", cx, cy + radius * 0.72f, textDim)
        }

        // 5) voltage bar at the bottom
        if (!voltage.isNaN()) {
            val frac = ((voltage - 10f) / 4f).coerceIn(0f, 1f)
            val bw = width * 0.5f
            val bx = (width - bw) / 2f
            val by = height - 12f
            val p = if (voltage < 12f) voltBarLow else voltBar
            canvas.drawRect(bx, by - 8f, bx + bw * frac, by, p)
        }
    }

    /** cached digit buffer – int → drawText without any allocation in onDraw */
    private val digitBuf = CharArray(8)
    private var digitLen = 0

    private fun drawInt(canvas: Canvas, v: Int, x: Float, y: Float, p: Paint) {
        var n = v
        digitLen = 0
        if (n == 0) {
            digitBuf[0] = '0'
            digitLen = 1
        } else {
            var start = digitBuf.size - 1
            while (n > 0) {
                digitBuf[start--] = ('0' + (n % 10))
                n /= 10
            }
            System.arraycopy(digitBuf, start + 1, digitBuf, 0, digitBuf.size - start - 1)
            digitLen = digitBuf.size - start - 1
        }
        canvas.drawText(digitBuf, 0, digitLen, x, y, p)
    }
}
