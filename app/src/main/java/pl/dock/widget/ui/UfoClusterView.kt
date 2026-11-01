package pl.dock.widget.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * UFO Cluster - OBD dashboard rendered directly on Canvas.
 *
 * ZERO-ALLOCATION CONTRACT: [onDraw] never allocates. All Paint / RectF /
 * digit buffers are created in the constructor / [onSizeChanged].
 * canvas.clipRect() limits the redraw area (no GPU overdraw on Mali-T720).
 */
class UfoClusterView(context: Context) : View(context) {

    companion object {
        const val MAX_RPM = 8000
        const val REDLINE_RPM = 6800
        const val STALE_MS = 5_000L

        private const val COL_ACCENT = 0x0A84FF.toInt() or 0xFF000000.toInt()
        private const val COL_RED = 0xFF453A.toInt() or 0xFF000000.toInt()
        private const val COL_GREEN = 0x30D158.toInt() or 0xFF000000.toInt()
        private const val COL_AMBER = 0xFFD60A.toInt() or 0xFF000000.toInt()
        private const val COL_WHITE = 0xFFFFFF or 0xFF000000.toInt()
        private const val COL_DIM = 0x99FFFFFF
        private const val COL_ARC_BG_NIGHT = 0x33FFFFFF
        private const val COL_ARC_BG_DAY = 0x22000000
    }

    // --- live state (written by update(), read by onDraw) ---
    @Volatile private var rpm = -1
    @Volatile private var speedKmh = -1
    @Volatile private var voltage: Float = Float.NaN
    @Volatile private var ectC: Float = Float.NaN
    @Volatile private var lastUpdateMs = 0L
    @Volatile private var connected = false
    @Volatile private var night = true

    // --- pre-allocated drawing objects (NEVER allocated in onDraw) ---
    private val arcRect = RectF()
    private val arcBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 18f; color = COL_ARC_BG_NIGHT
    }
    private val arcFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND; color = COL_ACCENT
    }
    private val arcRed = Paint(arcFg).apply { color = COL_RED }
    private val textMain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COL_WHITE; textAlign = Paint.Align.CENTER
    }
    private val textDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COL_DIM; textAlign = Paint.Align.CENTER
    }
    private val voltBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COL_GREEN }
    private val voltBarLow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COL_AMBER }

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    // cached text constants (no allocation in onDraw)
    private val txtKmh = "km/h"
    private val txtRpm = " RPM"
    private val txtStale = "[stale]"
    private val txtNoConn = "brak polaczenia OBD"
    private val txtDash = "--"

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
        textMain.color = if (n) COL_WHITE else 0xFF111111.toInt()
        arcBg.color = if (n) COL_ARC_BG_NIGHT else COL_ARC_BG_DAY
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
        // clip to this view - avoid overdraw of layers beneath
        canvas.clipRect(0, 0, width, height)
        val stale = lastUpdateMs > 0 && System.currentTimeMillis() - lastUpdateMs > STALE_MS

        // 1) RPM arc (background)
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arcRect, 135f, 270f, false, arcBg)

        // 2) RPM arc (value) - red past redline
        val r = if (rpm < 0) 0f else rpm.coerceAtMost(MAX_RPM).toFloat() / MAX_RPM
        if (r > 0f) {
            val p = if (rpm >= REDLINE_RPM) arcRed else arcFg
            canvas.drawArc(arcRect, 135f, 270f * r, false, p)
        }

        // 3) digital speed in the middle
        if (speedKmh >= 0) drawInt(canvas, speedKmh, cx, cy + textMain.textSize * 0.35f, textMain)
        else canvas.drawText(txtDash, cx, cy + textMain.textSize * 0.35f, textMain)
        canvas.drawText(txtKmh, cx, cy + radius * 0.42f, textDim)

        // 4) RPM caption + stale marker (grey filter)
        if (rpm >= 0) drawInt(canvas, rpm, cx - textDim.measureText(txtRpm) * 0.5f, cy - radius * 0.55f, textDim)
        else canvas.drawText(txtDash, cx, cy - radius * 0.55f, textDim)
        canvas.drawText(txtRpm, cx + textDim.measureText("0000") * 0.4f, cy - radius * 0.55f, textDim)
        if (stale) canvas.drawText(txtStale, cx, cy - radius * 0.72f, textDim)
        if (!connected) canvas.drawText(txtNoConn, cx, cy + radius * 0.72f, textDim)

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

    // cached digit buffer - int -> drawText without any allocation in onDraw
    private val digitBuf = CharArray(8)

    private fun drawInt(canvas: Canvas, v: Int, x: Float, y: Float, p: Paint) {
        var n = v
        val start: Int
        if (n == 0) {
            digitBuf[0] = '0'
            start = digitBuf.size - 1
        } else {
            var s = digitBuf.size - 1
            while (n > 0) {
                digitBuf[s] = ('0' + (n % 10))
                n /= 10
                s--
            }
            start = s
        }
        val len = digitBuf.size - 1 - start
        if (len > 0) canvas.drawText(digitBuf, start + 1, len, x, y, p)
    }
}
