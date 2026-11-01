package pl.dock.widget.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

/**
 * Honda OEM UFO Cluster - pelna implementacja wg specyfikacji 1280x720.
 *
 * Warstwa statyczna (tlo, ramki, podzialki, cyfry) jest cache'owana w Bitmap
 * i przerysowywana tylko przy zmianie rozmiaru. Warstwa dynamiczna
 * (wskazowka, HUD, LCD, luki, footer) nie alokuje obiektow w onDraw.
 *
 * Wszystkie wspolrzedne sa zdefiniowane w wirtualnej przestrzeni 1280x720
 * i skalowane ze zachowaniem proporcji (letterboxing przy innych proporcjach).
 */
class UfoClusterFullView(context: Context) : View(context) {

    // ---------- Paleta ----------
    private val BG = Color.parseColor("#0A0D14")
    private val DIAL_BG = Color.parseColor("#05070B")
    private val METAL_HI = Color.parseColor("#2A3448")
    private val METAL_LO = Color.parseColor("#141B29")
    private val BLUE_GLOW = Color.parseColor("#0055FF")
    private val CYAN = Color.parseColor("#00E5FF")
    private val TICK = Color.parseColor("#FFFEEA")
    private val RED = Color.parseColor("#FF0033")
    private val LCD_BG = Color.parseColor("#5E6F73")
    private val LCD_ON = Color.parseColor("#0D1517")
    private val LCD_OFF = Color.parseColor("#526266")
    private val STAT_BG = Color.parseColor("#E2E8E4")
    private val GREEN = Color.parseColor("#30D158")
    private val AMBER = Color.parseColor("#FF9F0A")
    private val GRAY = Color.parseColor("#8E8E93")

    // ---------- Stan danych ----------
    private var rpm = 0f          // wyrenderowane (interpolowane)
    private var rpmTarget = 0f
    private var speedKmh = -1
    private var gear = "?"
    private var connected = false
    private var connecting = false
    private var voltage = Float.NaN
    private var ectC = Float.NaN
    private var tpsPct = Float.NaN
    private var mapKpa = Float.NaN
    private var iatC = Float.NaN
    private var ltftPct = Float.NaN
    private var voltageAdapter = Float.NaN
    private var lastDataMs = 0L
    private var useMph = false

    // konfiguracja (docelowo z Ustawien)
    var redlineRpm = 6500
    var vtecRpm = 5200

    // ---------- Callbacki ----------
    var onOpenDtc: () -> Unit = {}
    var onSaveEvent: () -> Unit = {}
    var onSwapUi: () -> Unit = {}
    var onOpenSettings: () -> Unit = {}
    var onSpeedTap: () -> Unit = {}

    // ---------- LCD MID ----------
    private val midLoop = arrayOf("ECT", "MAP", "VOLT", "TPS", "IAT", "LTFT")
    private var midIndex = 0
    private val midMax = HashMap<String, Float>()
    private val midAvgSum = HashMap<String, Float>()
    private val midAvgCount = HashMap<String, Float>()

    // ---------- Wing Arcs ----------
    private val leftLoop = arrayOf("ECT", "MAP", "VOLTS")
    private val rightLoop = arrayOf("TPS", "IAT", "LTFT")
    private var leftIndex = 0
    private var rightIndex = 0

    // ---------- Geometria wirtualna ----------
    private val CX = 640f
    private val CY = 400f
    private val R_DIAL = 220f          // srednica 440
    private val LCD_L = 530f; private val LCD_T = 290f
    private val LCD_R = 750f; private val LCD_B = 510f

    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private var staticLayer: Bitmap? = null
    private val staticCanvasC = Canvas()

    // ---------- Painty (bez alokacji w onDraw) ----------
    private val pFill = Paint()
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pItalic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
    }
    private val pMono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }
    private val rectF = RectF()
    private val path = Path()
    private val needlePath = Path()
    private var lastFrameMs = 0L

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ---------- API publiczne ----------

    fun update(rpmVal: Int, speedVal: Int, v: Float, ect: Float,
               tps: Float, mapKpaV: Float, iat: Float, ltft: Float,
               conn: Boolean, conn_ing: Boolean) {
        rpmTarget = if (rpmVal < 0) 0f else rpmVal.toFloat()
        speedKmh = speedVal
        voltage = v
        ectC = ect
        tpsPct = tps
        mapKpa = mapKpaV
        iatC = iat
        ltftPct = ltft
        connected = conn
        connecting = conn_ing
        if (conn) lastDataMs = System.currentTimeMillis()
        trackMidStats()
        postInvalidateOnAnimation()
    }

    fun toggleUnits() { useMph = !useMph }

    private fun trackMidStats() {
        val key = midLoop[midIndex]
        val value = midValue(key)
        if (value.isNaN()) return
        val mx = midMax[key]
        if (mx == null || value > mx) midMax[key] = value
        midAvgSum[key] = (midAvgSum[key] ?: 0f) + value
        midAvgCount[key] = (midAvgCount[key] ?: 0f) + 1f
    }

    private fun midValue(key: String): Float = when (key) {
        "ECT" -> ectC
        "MAP" -> if (mapKpa.isNaN()) Float.NaN else mapKpa
        "VOLT" -> voltage
        "TPS" -> tpsPct
        "IAT" -> iatC
        "LTFT" -> ltftPct
        else -> Float.NaN
    }

    private fun midUnit(key: String): String = when (key) {
        "ECT", "IAT" -> "C"
        "MAP" -> "kPa"
        "VOLT" -> "V"
        "TPS", "LTFT" -> "%"
        else -> ""
    }

    private fun midLabel(key: String): String = when (key) {
        "ECT" -> "ECT"
        "MAP" -> "MAP"
        "VOLT" -> "VOLTAGE"
        "TPS" -> "TPS"
        "IAT" -> "IAT"
        "LTFT" -> "LTFT"
        else -> ""
    }

    // ---------- Cykl zycia ----------

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        scale = minOf(w / 1280f, h / 720f)
        offX = (w - 1280f * scale) / 2f
        offY = (h - 720f * scale) / 2f
        buildStaticLayer()
    }

    private fun buildStaticLayer() {
        val bmp = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(offX, offY)
        c.scale(scale, scale)
        drawStatic(c)
        staticLayer = bmp
    }

    override fun onDraw(c: Canvas) {
        // interpolacja zalezna od czasu klatki: V += alpha*(Vt - V)
        val now = System.currentTimeMillis()
        val dt = if (lastFrameMs == 0L) 16f else (now - lastFrameMs).coerceAtMost(100).toFloat()
        lastFrameMs = now
        val alpha = 1f - Math.pow(0.001, (dt / 1000f * 6f).toDouble()).toFloat()
        rpm += alpha * (rpmTarget - rpm)

        c.drawColor(BG)
        staticLayer?.let { c.drawBitmap(it, 0f, 0f, pFill) }
        c.save()
        c.translate(offX, offY)
        c.scale(scale, scale)
        drawDynamic(c)
        c.restore()
        postInvalidateOnAnimation()
    }

    // ---------- Warstwa statyczna ----------

    private fun drawStatic(c: Canvas) {
        // tlo panelu
        pFill.color = BG
        c.drawRect(0f, 0f, 1280f, 720f, pFill)

        // metaliczny pierscien tarczy (gradient liniowy)
        rectF.set(CX - R_DIAL, CY - R_DIAL, CX + R_DIAL, CY + R_DIAL)
        pFill.shader = LinearGradient(CX - R_DIAL, CY - R_DIAL, CX + R_DIAL, CY + R_DIAL,
            METAL_HI, METAL_LO, Shader.TileMode.CLAMP)
        c.drawCircle(CX, CY, R_DIAL, pFill)
        pFill.shader = null

        // wnetrze tarczy
        pFill.color = DIAL_BG
        c.drawCircle(CX, CY, R_DIAL - 26f, pFill)

        // niebieska poswiata pierscienia
        pFill.shader = RadialGradient(CX, CY, R_DIAL, 0x000055FF, 0x660055FF, Shader.TileMode.CLAMP)
        c.drawCircle(CX, CY, R_DIAL, pFill)
        pFill.shader = null

        // podzialki obrotomierza 0-8000, katy: -210..30 stopni (240 st. luku)
        val a0 = Math.toRadians(150.0)   // start (lewy dol)
        val sweep = Math.toRadians(240.0)
        val maxRpmS = 8000f
        for (r in 0..8000 step 100) {
            val frac = r / maxRpmS
            val ang = a0 + sweep * frac
            val major = r % 1000 == 0
            val mid = r % 500 == 0
            val len = when { major -> 14f; mid -> 9f; else -> 5f }
            val w = when { major -> 2.5f; mid -> 2f; else -> 1.2f }
            pStroke.strokeWidth = w
            pStroke.color = if (r >= 6300) RED else TICK
            val r1 = R_DIAL - 34f
            val r2 = r1 - len
            c.drawLine((CX + r1 * Math.cos(ang)).toFloat(), (CY + r1 * Math.sin(ang)).toFloat(),
                (CX + r2 * Math.cos(ang)).toFloat(), (CY + r2 * Math.sin(ang)).toFloat(), pStroke)
        }

        // cyfry 1-8 (pochylone, nie obracane radialnie)
        pItalic.textSize = 30f
        pItalic.color = TICK
        pItalic.textAlign = Paint.Align.CENTER
        for (n in 1..8) {
            val ang = a0 + sweep * (n * 1000f / maxRpmS)
            val r = R_DIAL - 68f
            c.drawText(n.toString(), (CX + r * Math.cos(ang)).toFloat(),
                (CY + r * Math.sin(ang)).toFloat() + 10f, pItalic)
        }

        // obwod LCD MID (kwadrat, ostre naroza, 2dp)
        pStroke.strokeWidth = 6f
        pStroke.color = METAL_HI
        pStroke.style = Paint.Style.STROKE
        c.drawRect(LCD_L, LCD_T, LCD_R, LCD_B, pStroke)
        pStroke.style = Paint.Style.FILL

        // tlo LCD
        pFill.color = LCD_BG
        c.drawRect(LCD_L, LCD_T, LCD_R, LCD_B, pFill)

        // separator LCD (Y=400)
        pFill.color = METAL_LO
        c.drawRect(LCD_L, 399f, LCD_R, 401f, pFill)

        // jasny panel statystyk 204x96 na (538,408)-(742,504)
        pFill.color = STAT_BG
        c.drawRect(538f, 408f, 742f, 504f, pFill)

        // ramki bocznych lukow (180x480, grubosc 8dp=24px)
        drawArcFrame(c, left = true)
        drawArcFrame(c, left = false)
    }

    private fun drawArcFrame(c: Canvas, left: Boolean) {
        val cx = if (left) 130f else 1150f
        val halfT = 54f
        rectF.set(cx - halfT, 160f, cx + halfT, 640f)
        pStroke.strokeWidth = 20f
        pStroke.color = METAL_LO
        pStroke.style = Paint.Style.STROKE
        c.drawRoundRect(rectF, 27f, 27f, pStroke)
        pStroke.style = Paint.Style.FILL
        // etykieta wartosci przy wewnetrznej krawedzi - rysowana dynamicznie
    }

    // ---------- Warstwa dynamiczna ----------

    private fun drawDynamic(c: Canvas) {
        drawHud(c)
        drawNeedle(c)
        drawLcdMid(c)
        drawWingArc(c, left = true)
        drawWingArc(c, left = false)
        drawFooter(c)
    }

    private fun drawHud(c: Canvas) {
        // predkosc 96sp Bold Italic, bez zer wiodacych
        val spd = if (speedKmh < 0) "---" else
            (if (useMph) (speedKmh * 0.621371f).toInt() else speedKmh).toString()
        pItalic.textSize = 96f
        pItalic.color = TICK
        pItalic.textAlign = Paint.Align.CENTER
        c.drawText(spd, 520f, 128f, pItalic)

        // jednostka + bieg
        pItalic.textSize = 28f
        c.drawText(if (useMph) "mph" else "km/h", 640f, 128f, pItalic)
        c.drawText(gear, 730f, 128f, pItalic)

        // pasek REV: 10 segmentow nad predkoscia (Y=20, X=300..500)
        val segW = 18f
        for (i in 0 until 10) {
            val x = 300f + i * (segW + 6f)
            val lit = rpm >= 4200 + i * 300
            val inRed = rpm >= 6300
            pFill.color = when {
                inRed -> if ((System.currentTimeMillis() / 150) % 2 == 0L) RED else 0x66FF0033.toInt()
                !lit -> 0x141B29
                i < 4 -> 0x3399FF
                i < 8 -> GREEN
                else -> AMBER
            }
            c.drawRect(x, 20f, x + segW, 44f, pFill)
        }

        // ECO
        val eco = !tpsPct.isNaN() && tpsPct < 18f && !mapKpa.isNaN() && mapKpa < 48f && rpm < 2800f
        pItalic.textSize = 24f
        pItalic.color = if (eco) GREEN else 0x2230D158
        c.drawText("ECO", 300f, 90f, pItalic)
        // VTEC
        val vtec = rpm >= vtecRpm
        pItalic.color = if (vtec) CYAN else 0x2200E5FF.toInt()
        c.drawText("VTEC", 700f, 90f, pItalic)

        // soczewka pod predkoscia
        pFill.shader = RadialGradient(520f, 150f, 120f,
            0x400022AA.toInt(), 0x000022AA.toInt(), Shader.TileMode.CLAMP)
        c.drawOval(380f, 130f, 660f, 170f, pFill)
        pFill.shader = null
    }

    private fun drawNeedle(c: Canvas) {
        val a0 = Math.toRadians(150.0)
        val sweep = Math.toRadians(240.0)
        val frac = (rpm / 8000f).coerceIn(0f, 1f)
        val ang = a0 + sweep * frac
        val rN = R_DIAL - 44f
        val nx = (rN * Math.cos(ang)).toFloat()
        val ny = (rN * Math.sin(ang)).toFloat()
        needlePath.reset()
        needlePath.moveTo(CX, CY)
        needlePath.lineTo(CX + ny * 0.06f - nx * 0.06f, CY - nx * 0.06f + ny * 0.06f * 0f)
        needlePath.lineTo(CX + nx, CY + ny)
        needlePath.lineTo(CX - ny * 0.06f - nx * 0.06f, CY + nx * 0.06f + ny * 0.06f * 0f)
        needlePath.close()
        val red = rpm >= redlineRpm
        pFill.color = if (red) RED else TICK
        c.drawPath(needlePath, pFill)
        // os
        pFill.color = CYAN
        c.drawCircle(CX, CY, 10f, pFill)
    }

    private fun drawLcdMid(c: Canvas) {
        val key = midLoop[midIndex]
        val v = midValue(key)
        val have = !v.isNaN() && connected

        // naglowek (ikona zastapiona kwadracikiem 24x24 + nazwa 12sp BOLD)
        pText.color = if (have) LCD_ON else LCD_OFF
        pText.textSize = 24f
        pText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        pText.textAlign = Paint.Align.LEFT
        c.drawRect(540f, 300f, 564f, 324f, pFill.also { it.color = if (have) LCD_ON else LCD_OFF })
        c.drawText(midLabel(key), 574f, 320f, pText)

        // wartosc glowna 38sp Monospace Bold
        pMono.textSize = 58f
        pMono.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        pMono.color = if (have) LCD_ON else LCD_OFF
        pMono.textAlign = Paint.Align.RIGHT
        val main = if (!have) "---" else fmtVal(v)
        c.drawText(main, 700f, 378f, pMono)
        // jednostka 14sp
        pMono.textSize = 26f
        c.drawText(midUnit(key), 742f, 378f, pMono)

        // panel statystyk: lewa opis interwalu, prawa MAX/AVG
        pText.color = Color.BLACK
        pText.textSize = 20f
        pText.typeface = Typeface.DEFAULT
        pText.textAlign = Paint.Align.LEFT
        c.drawText("LAST", 548f, 452f, pText)

        val mx = midMax[key]
        val avgSum = midAvgSum[key]
        val avgCnt = midAvgCount[key]
        pText.textAlign = Paint.Align.RIGHT
        pText.textSize = 24f
        pText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        pText.color = BLUE_GLOW
        c.drawText(if (mx == null) "MAX: N/A" else "MAX: " + fmtVal(mx), 734f, 442f, pText)
        pText.color = if (have) Color.BLACK else LCD_OFF
        c.drawText(if (avgSum == null || avgCnt == null || avgCnt <= 0f) "AVG: N/A"
            else "AVG: " + fmtVal(avgSum / avgCnt), 734f, 478f, pText)
    }

    private fun drawWingArc(c: Canvas, left: Boolean) {
        val key = if (left) leftLoop[leftIndex] else rightLoop[rightIndex]
        val v = midValue(key.replace("VOLTS", "VOLT"))
        val cx = if (left) 130f else 1150f
        val cy = 400f
        val halfT = 40f

        // wypelnienie pionowego suwaka wewnatrz ramki (gradient sweep)
        val frac = when (key) {
            "ECT" -> if (v.isNaN()) 0.5f else ((v - 40f) / 80f).coerceIn(0f, 1f)
            "MAP", "VOLTS", "VOLT" -> if (v.isNaN()) 0.5f else when (key) {
                "MAP" -> (v / 100f).coerceIn(0f, 1f)
                else -> ((v - 11f) / 4f).coerceIn(0f, 1f)
            }
            "TPS" -> if (v.isNaN()) 0.5f else (v / 100f).coerceIn(0f, 1f)
            "IAT" -> if (v.isNaN()) 0.5f else ((v + 20f) / 90f).coerceIn(0f, 1f)
            "LTFT" -> if (v.isNaN()) 0.5f else ((v + 15f) / 30f).coerceIn(0f, 1f)
            else -> 0.5f
        }
        // kolor alarmowy
        val alarm = when (key) {
            "ECT" -> !v.isNaN() && v > 102f
            "VOLTS", "VOLT" -> !v.isNaN() && (v < 11.8f || v > 14.8f)
            "LTFT" -> !v.isNaN() && (v > 10f || v < -10f)
            "IAT" -> !v.isNaN() && v > 65f
            else -> false
        }
        val col = if (alarm) RED else BLUE_GLOW
        pFill.shader = LinearGradient(0f, 640f, 0f, 160f,
            0x000055FF, col, Shader.TileMode.CLAMP)
        // wypelnienie od dolu do poziomu frac
        val topY = 620f - (1f - frac) * 0f
        rectF.set(cx - halfT + 8f, 620f - (620f - 180f) * frac, cx + halfT - 8f, 620f)
        c.drawRect(rectF, pFill)
        pFill.shader = null

        // wartosc + etykieta po stronie srodka ekranu
        val innerX = if (left) cx + 70f else cx - 70f
        pText.textAlign = if (left) Paint.Align.LEFT else Paint.Align.RIGHT
        pText.textSize = 22f
        pText.color = if (alarm) RED else TICK
        pText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val label = if (left) leftLoop[leftIndex] else rightLoop[rightIndex]
        val shown = midValue(label.replace("VOLTS", "VOLT"))
        c.drawText(label, innerX, 380f, pText)
        pText.textSize = 30f
        c.drawText(if (shown.isNaN()) "---" else fmtVal(shown) + " " + midUnit(label.replace("VOLTS", "VOLT")),
            innerX, 416f, pText)
    }

    private fun drawFooter(c: Canvas) {
        // pasek 80px: Y=640..720, 5 sektorow
        pFill.color = Color.parseColor("#0D1220")
        c.drawRect(0f, 640f, 1280f, 720f, pFill)
        pStroke.strokeWidth = 2f
        pStroke.color = METAL_HI
        c.drawLine(0f, 640f, 1280f, 640f, pStroke)

        // 1. Check Engine / DTC
        val stale = System.currentTimeMillis() - lastDataMs > 5000 && lastDataMs > 0
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = 26f
        pText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        pText.color = if (stale) GRAY else GREEN
        c.drawText(if (!connected && !connecting) "OFFLINE" else
            if (connecting) "CONNECTING" else "OBD OK", 128f, 690f, pText)
        pText.textSize = 18f
        pText.color = TICK
        c.drawText(if (voltage.isNaN()) "--- V" else String.format("%.1f V", voltage), 128f, 712f, pText)

        // 2-4. przyciski tekstowe
        pText.textSize = 24f
        pText.color = TICK
        c.drawText("ZAPISZ EVENT", 384f, 690f, pText)
        c.drawText("SWAP UI", 640f, 690f, pText)
        c.drawText("USTAWIENIA", 896f, 690f, pText)
        // 5. DTC
        pText.textSize = 24f
        pText.color = RED
        c.drawText("CHECK ENGINE", 1152f, 690f, pText)
        pText.textSize = 16f
        pText.color = GRAY
        c.drawText("DTC", 1152f, 712f, pText)
    }

    private fun fmtVal(v: Float): String =
        if (v >= 100f || v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v)

    // ---------- Gesty ----------

    private var downX = 0f
    private var downY = 0f
    private var downMs = 0L

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // przelicz na przestrzen wirtualna
        val x = (e.x - offX) / scale
        val y = (e.y - offY) / scale

        when (e.action) {
            MotionEvent.ACTION_DOWN -> { downX = x; downY = y; downMs = e.downTime; return true }
            MotionEvent.ACTION_UP -> Unit
            else -> return true
        }
        val longPress = e.eventTime - downMs > 1000

        // LCD MID: tap = nastepny parametr; long press w dolnym panelu = reset MAX/AVG
        if (x in LCD_L..LCD_R && y in LCD_T..LCD_B) {
            if (y > 400f && longPress) {
                val key = midLoop[midIndex]
                midMax.remove(key); midAvgSum.remove(key); midAvgCount.remove(key)
            } else {
                midIndex = (midIndex + 1) % midLoop.size
            }
            postInvalidateOnAnimation()
            return true
        }
        // luki
        if (x < 260f) { leftIndex = (leftIndex + 1) % leftLoop.size; postInvalidateOnAnimation(); return true }
        if (x > 1020f) { rightIndex = (rightIndex + 1) % rightLoop.size; postInvalidateOnAnimation(); return true }
        // HUD predkosc
        if (y < 160f && x in 400f..680f) { onSpeedTap(); return true }
        // footer
        if (y > 640f) {
            when {
                x < 256f -> { /* DTC szczegoly */ onOpenDtc() }
                x < 512f -> onSaveEvent()
                x < 768f -> onSwapUi()
                x < 1024f -> onOpenSettings()
                else -> onOpenDtc()
            }
            return true
        }
        return true
    }
}
