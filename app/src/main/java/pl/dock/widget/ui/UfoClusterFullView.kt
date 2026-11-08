package pl.dock.widget.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import pl.dock.widget.repository.EngineStateLabel
import pl.dock.widget.repository.VehicleUiState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Honda OEM UFO Cluster - FOTOREALIZM (Multi-pass Bloom & Physical Geometry)
 */
class UfoClusterFullView(context: Context) : View(context) {

    companion object {
        val ARC_PARAMS = arrayOf("ECT", "BOOST", "VOLTS", "FUEL", "TPS", "IAT", "LTFT", "STFT", "MAF", "BARO", "AVG_TRIP_FUEL")
        val CHART_KEYS = arrayOf("BOOST", "ECT", "VOLTS", "TPS", "IAT", "LTFT", "STFT", "MAF", "BARO", "FUEL")

        fun labelOf(key: String): String = key
        private const val FUEL_DENSITY_G_PER_L = 745.0
        private const val AFR_GAS = 14.7
    }

    // ---------- Paleta Kolorów OEM LED ----------
    private val BG_NIGHT = Color.parseColor("#020305")
    private val TICK_OEM = Color.parseColor("#FFF5C0") // Ciepła biel podziałek
    private val TICK_RED = Color.parseColor("#FF0F1F") 
    private val BLUE_GLOW = Color.parseColor("#1433FF")
    private val CYAN_HUD = Color.parseColor("#00C3FF")
    private val LCD_BG = Color.parseColor("#0D1015")
    private val LCD_TEXT = Color.parseColor("#E1E6F0")
    private val OFF_SEGMENT = Color.parseColor("#151820")

    private var rpm = 0f
    private var rpmTarget = 0f
    private var speedKmh = -1
    private var connected = false
    private var connecting = false
    private var voltage = Float.NaN
    private var ectC = Float.NaN
    private var tpsPct = Float.NaN
    private var mapKpa = Float.NaN
    private var iatC = Float.NaN
    private var ltftPct = Float.NaN
    private var stftPct = Float.NaN
    private var baroKpa = Float.NaN
    private var lambdaAfr = Float.NaN
    private var mafGs = Float.NaN
    private var milOn = false
    private var dtcCount = -1
    private var lastDataMs = 0L
    private var useMph = false

    var redlineRpm = 6500
    var vtecRpm = 5200
    var ecoTps = 18f
    var ecoMap = 48f
    var ecoRpm = 2800f
    var ectMaxC = 102f
    var voltMin = 11.8f
    var voltMax = 14.8f
    var ltftMax = 10f
    var iatMax = 65f

    var onOpenDtc: () -> Unit = {}
    var onSaveEvent: () -> Unit = {}
    var onSwapUi: () -> Unit = {}
    var onOpenSettings: () -> Unit = {}
    var onSpeedTap: () -> Unit = {}
    var onArcAssign: (left: Boolean) -> Unit = { _ -> }
    var onOpenEReport: () -> Unit = {}

    private var engineOff = false
    private var savedLeftIndex = 0
    private var savedRightIndex = 0
    var offLeftParam = "VOLTS"
    var offRightParam = "AVG_TRIP_FUEL"

    private val midLoop = arrayOf("BOOST", "ECT", "VOLTS", "TPS", "IAT", "LTFT")
    private var midIndex = 0
    private fun midKey(entry: String): String = if (entry.startsWith("CHART:")) entry.substring(6) else entry

    private val leftLoop = arrayOf("ECT", "BOOST", "VOLTS")
    private val rightLoop = arrayOf("FUEL", "TPS", "IAT", "LTFT")
    private var leftIndex = 0
    private var rightIndex = 0

    // Geometria (Przesunięte dla idealnych proporcji)
    private val CX = 640f
    private val CY = 420f // Tarcza zjeżdża lekko w dół, dając miejsce na odseparowany HUD
    private val LCD_L = 520f; private val LCD_T = 300f
    private val LCD_R = 760f; private val LCD_B = 540f

    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private var staticLayer: Bitmap? = null

    // Painty pre-alokowane
    private val pFill = Paint()
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Specyficzny krój dla cyfrowego wyglądu (condensed)
    private val pSpeed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textScaleX = 0.85f // Imitacja ekranu LCD
    }
    private val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        textScaleX = 0.9f
    }
    
    private val rectF = RectF()
    // Wskaźniki boczne drastycznie zmniejszone i podniesione
    private val ovalL = RectF(120f, 260f, 280f, 580f)
    private val ovalR = RectF(1000f, 260f, 1160f, 580f)
    private val needlePath = Path()
    private var lastFrameMs = 0L
    private val tfBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    
    // Poświata HUD skompresowana na osi Y, by nie wylewała się w dół
    private val hudGlowShader = RadialGradient(640f, 80f, 300f,
        intArrayOf(0x991433FF.toInt(), 0x330011AA, 0x00000000),
        floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)

    private var spdCache = "---"
    private var spdCacheSrc = Int.MIN_VALUE
    private var voltText = "--- V"
    private var voltSrc = Float.NaN
    private var arcTxtCacheL = ""; private var arcTxtSrcL = Float.NaN
    private var arcTxtCacheR = ""; private var arcTxtSrcR = Float.NaN

    fun update(s: VehicleUiState) {
        val off = s.engineState == EngineStateLabel.OFF
        if (off != engineOff) {
            engineOff = off
            if (off) { savedLeftIndex = leftIndex; savedRightIndex = rightIndex } 
            else { leftIndex = savedLeftIndex; rightIndex = savedRightIndex }
        }
        rpmTarget = if (off || s.rpm < 0) 0f else s.rpm.toFloat()
        speedKmh = s.speedKmh
        voltage = s.voltage
        ectC = s.ectC; tpsPct = s.tpsPct; mapKpa = s.mapKpa; iatC = s.iatC
        ltftPct = s.ltftPct; stftPct = s.stftPct; baroKpa = s.baroKpa
        lambdaAfr = s.lambdaAfr; mafGs = s.mafGs
        milOn = s.milOn; dtcCount = s.dtcCount
        connected = s.connected; connecting = s.connecting
        if (s.connected) lastDataMs = System.currentTimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        scale = minOf(w / 1280f, h / 720f)
        offX = (w - 1280f * scale) / 2f
        offY = (h - 720f * scale) / 2f
        buildStaticLayer()
    }

    private fun buildStaticLayer() {
        val bmp = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(offX, offY)
        c.scale(scale, scale)
        drawStatic(c)
        staticLayer = bmp
    }

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        val dt = if (lastFrameMs == 0L) 16f else (now - lastFrameMs).coerceAtMost(100).toFloat()
        lastFrameMs = now
        val alpha = 1f - Math.pow(0.001, (dt / 1000f * 6f).toDouble()).toFloat()
        rpm += alpha * (rpmTarget - rpm)

        c.drawColor(BG_NIGHT)
        c.save()
        c.translate(offX, offY)
        c.scale(scale, scale)

        staticLayer?.let { c.drawBitmap(it, 0f, 0f, pFill) }
        drawDynamic(c)
        
        c.restore()
        if (connected || connecting || Math.abs(rpm - rpmTarget) > 1f) postInvalidateOnAnimation()
        else lastFrameMs = 0L
    }

    // ---------- WARSTWA STATYCZNA (Oświetlenie wypiekane raz dla wydajności) ----------
    private fun drawStatic(c: Canvas) {
        val ringRadius = 175f // Pierścień światła

        // 1. Matowa obudowa w tle (Faktura czarnego plastiku)
        pFill.color = Color.parseColor("#050608")
        c.drawCircle(CX, CY, 250f, pFill)

        // 2. Silnik Bloom dla błękitnego pierścienia (Fizyczne światło dyfuzyjne)
        pStroke.style = Paint.Style.STROKE
        pStroke.color = BLUE_GLOW
        
        // Pass 1: Rozległa, miękka aura
        pStroke.strokeWidth = 24f
        pStroke.setShadowLayer(40f, 0f, 0f, BLUE_GLOW)
        c.drawCircle(CX, CY, ringRadius, pStroke)
        
        // Pass 2: Mocniejsze światło wewnętrzne
        pStroke.strokeWidth = 10f
        pStroke.setShadowLayer(15f, 0f, 0f, BLUE_GLOW)
        c.drawCircle(CX, CY, ringRadius, pStroke)
        
        // Pass 3: Fizyczny rdzeń plastiku dyfuzora
        pStroke.strokeWidth = 4f
        pStroke.color = Color.parseColor("#88AAFF")
        pStroke.setShadowLayer(5f, 0f, 0f, Color.WHITE)
        c.drawCircle(CX, CY, ringRadius, pStroke)
        pStroke.clearShadowLayer()

        // 3. Cyfry wewnątrz pierścienia (zgodnie ze zdjęciami)
        val a0 = Math.toRadians(150.0)
        val sweep = Math.toRadians(240.0)
        pNum.textSize = 46f
        pNum.color = TICK_OEM
        pNum.textAlign = Paint.Align.CENTER
        
        for (r in 0..8000 step 1000) {
            val frac = r / 8000f
            val ang = a0 + sweep * frac
            if (r > 0) {
                val numR = 135f // Wewnętrzny promień!
                pNum.setShadowLayer(12f, 0f, 0f, TICK_OEM)
                c.drawText((r / 1000).toString(),
                    (CX + numR * cos(ang)).toFloat(),
                    (CY + numR * sin(ang)).toFloat() + 16f, pNum)
                pNum.clearShadowLayer()
            }
        }

        // 4. Podziałki na zewnątrz pierścienia (fizyczne wcięcia)
        for (r in 0..8000 step 100) {
            val frac = r / 8000f
            val ang = a0 + sweep * frac
            val major = r % 1000 == 0
            val mid = r % 500 == 0
            val isRed = r >= 6500
            
            val len = when { major -> 20f; mid -> 12f; else -> 6f }
            val w = when { major -> 4.5f; mid -> 3f; else -> 1.5f }
            
            pStroke.strokeWidth = w
            val tickColor = if (isRed) TICK_RED else TICK_OEM
            pStroke.color = tickColor
            pStroke.setShadowLayer(if (isRed) 15f else 8f, 0f, 0f, tickColor)
            
            val rInner = 195f // Podziałki startują po zewnętrznej stronie
            val rOuter = rInner + len
            c.drawLine(
                (CX + rInner * cos(ang)).toFloat(), (CY + rInner * sin(ang)).toFloat(),
                (CX + rOuter * cos(ang)).toFloat(), (CY + rOuter * sin(ang)).toFloat(), pStroke
            )
            pStroke.clearShadowLayer()
        }

        // 5. Czerwona linia odcinki na krawędzi
        pStroke.strokeWidth = 5f
        pStroke.color = TICK_RED
        pStroke.setShadowLayer(15f, 0f, 0f, TICK_RED)
        val redStart = 150f + 240f * (6500f / 8000f)
        c.drawArc(RectF(CX - 195f, CY - 195f, CX + 195f, CY + 195f),
            redStart, 150f + 240f - redStart, false, pStroke)
        pStroke.clearShadowLayer()

        // 6. Matryca ekranu MID (w samym centrum, z odpowiednimi cieniami wnęki)
        pStroke.style = Paint.Style.FILL
        pFill.color = LCD_BG
        c.drawRoundRect(RectF(LCD_L, LCD_T, LCD_R, LCD_B), 10f, 10f, pFill)
        
        // Ramka wokół LCD
        pStroke.style = Paint.Style.STROKE
        pStroke.strokeWidth = 3f
        pStroke.color = Color.parseColor("#050709")
        c.drawRoundRect(RectF(LCD_L + 1f, LCD_T + 1f, LCD_R - 1f, LCD_B - 1f), 8f, 8f, pStroke)
        pStroke.style = Paint.Style.FILL

        // 7. Zgaszone segmenty łuków bocznych (podniesione i zwarte geometrycznie)
        val numSeg = 16 // Mniej klocków, ale grubsze
        val segSweep = 70f / numSeg
        val gap = 1.5f
        val solid = segSweep - gap
        
        pStroke.style = Paint.Style.STROKE
        pStroke.strokeWidth = 18f // Grube, wyraźne klocki
        pStroke.color = OFF_SEGMENT
        
        // Łuki zaczynają się idealnie symetrycznie
        for (i in 0 until numSeg) {
            // Lewy łuk: od dołu w górę
            c.drawArc(ovalL, 160f + i * segSweep, solid, false, pStroke)
            // Prawy łuk: od dołu w górę
            c.drawArc(ovalR, 20f - i * segSweep - solid, solid, false, pStroke)
        }
        pStroke.style = Paint.Style.FILL
    }

    // ---------- WARSTWA DYNAMICZNA ----------
    private fun drawDynamic(c: Canvas) {
        drawHud(c)
        drawNeedle(c)
        drawLcdMid(c)
        drawWingArc(c, left = true)
        drawWingArc(c, left = false)
        drawFooter(c)
    }

    private fun drawHud(c: Canvas) {
        // Poświata ograniczona Y-skalą
        c.save()
        c.scale(1f, 0.4f, 640f, 90f) // Spłaszcza gradient w idealną elipsę, by nie wylewał się w dół
        pFill.shader = hudGlowShader
        c.drawCircle(640f, 90f, 300f, pFill)
        pFill.shader = null
        c.restore()

        if (speedKmh != spdCacheSrc) {
            spdCacheSrc = speedKmh
            spdCache = if (speedKmh < 0) "---" else speedKmh.toString()
        }
        
        // FONT: Duży, pogrubiony, z wąskimi proporcjami - wiernie oddaje zegar
        pSpeed.textSize = 110f
        pSpeed.color = Color.WHITE
        pSpeed.textAlign = Paint.Align.CENTER
        pSpeed.setShadowLayer(25f, 0f, 0f, CYAN_HUD)
        c.drawText(spdCache, 640f, 130f, pSpeed)
        pSpeed.clearShadowLayer()

        pSpeed.textSize = 28f
        c.drawText(if (useMph) "mph" else "km/h", 760f, 130f, pSpeed)

        // REV Line - Klocki jak w oryginale
        val segW = 18f
        for (i in 0 until 10) {
            val x = 320f + i * (segW + 8f)
            val lit = rpm >= 4200 + i * 300
            val inRed = rpm >= 6500
            
            pFill.color = when {
                inRed -> TICK_RED
                !lit -> OFF_SEGMENT
                i < 4 -> CYAN_HUD
                else -> TICK_OEM
            }
            if (lit || inRed) pFill.setShadowLayer(10f, 0f, 0f, pFill.color)
            c.drawRoundRect(x, 24f, x + segW, 44f, 4f, 4f, pFill)
            pFill.clearShadowLayer()
        }
    }

    private fun drawNeedle(c: Canvas) {
        // Wskazówka jako wielokątny "klin" światłowodu
        val a0 = Math.toRadians(150.0)
        val sweep = Math.toRadians(240.0)
        val frac = (rpm / 8000f).coerceIn(0f, 1f)
        val ang = a0 + sweep * frac
        
        val isRed = rpm >= redlineRpm
        val glowColor = if (isRed) TICK_RED else Color.parseColor("#FFAA00") // Gorący pomarańcz obrotomierza

        val rInner = 182f
        val rOuter = 230f
        val wBase = 12f // Gruba baza przy ekranie
        val wTip = 4f   // Cienki czubek

        val px = cos(ang).toFloat()
        val py = sin(ang).toFloat()
        val nx = -py
        val ny = px

        needlePath.reset()
        needlePath.moveTo(CX + px * rInner - nx * wBase, CY + py * rInner - ny * wBase)
        needlePath.lineTo(CX + px * rInner + nx * wBase, CY + py * rInner + ny * wBase)
        needlePath.lineTo(CX + px * rOuter + nx * wTip, CY + py * rOuter + ny * wTip)
        needlePath.lineTo(CX + px * rOuter - nx * wTip, CY + py * rOuter - ny * wTip)
        needlePath.close()

        // Podwójne oświetlenie klina
        pFill.color = glowColor
        pFill.setShadowLayer(25f, 0f, 0f, glowColor)
        c.drawPath(needlePath, pFill)
        
        pFill.color = Color.WHITE
        pFill.setShadowLayer(8f, 0f, 0f, Color.WHITE)
        c.drawPath(needlePath, pFill)
        pFill.clearShadowLayer()
    }

    private fun drawWingArc(c: Canvas, left: Boolean) {
        val key = if (left) leftLoop[leftIndex] else rightLoop[rightIndex]
        val v = paramValue(key)
        val frac = arcFrac(key, v)
        val alarm = arcAlarm(key, v)

        if (!v.isNaN() && frac > 0f) {
            val numSeg = 16
            val segSweep = 70f / numSeg
            val gap = 1.5f
            val solid = segSweep - gap
            val litCount = (frac * numSeg).toInt()
            
            pStroke.style = Paint.Style.STROKE
            pStroke.strokeWidth = 18f
            
            for (i in 0 until litCount) {
                // Wykrywanie czerwonej strefy klocków
                val isRedZone = if (left) i >= 13 else i <= 2
                val segColor = if (alarm || isRedZone) TICK_RED else TICK_OEM
                
                pStroke.color = segColor
                pStroke.setShadowLayer(15f, 0f, 0f, segColor) // Bloom podświetlonego klocka
                
                if (left) {
                    c.drawArc(ovalL, 160f + i * segSweep, solid, false, pStroke)
                } else {
                    c.drawArc(ovalR, 20f - i * segSweep - solid, solid, false, pStroke)
                }
            }
            pStroke.clearShadowLayer()
        }
        pStroke.style = Paint.Style.FILL

        // Podpisy klocków wyśrodkowane koło zegarów
        val innerX = if (left) 310f else 970f
        pText.textAlign = if (left) Paint.Align.LEFT else Paint.Align.RIGHT
        pText.textSize = 20f
        pText.typeface = tfBold
        pText.color = if (alarm) TICK_RED else Color.parseColor("#8894A8")
        c.drawText(key, innerX, 420f, pText)
        
        if (v != (if (left) arcTxtSrcL else arcTxtSrcR)) {
            if (left) { arcTxtSrcL = v; arcTxtCacheL = arcText(key, v) }
            else { arcTxtSrcR = v; arcTxtCacheR = arcText(key, v) }
        }
        pText.textSize = 28f
        pText.color = LCD_TEXT
        c.drawText(if (left) arcTxtCacheL else arcTxtCacheR, innerX, 456f, pText)
    }

    private fun drawLcdMid(c: Canvas) {
        val entry = midLoop[midIndex]
        val v = paramValue(entry)
        val have = !v.isNaN() && connected

        val ink = if (have) LCD_TEXT else Color.parseColor("#333A48")
        
        pSpeed.textSize = 22f
        pSpeed.color = ink
        pSpeed.textAlign = Paint.Align.LEFT
        c.drawText(entry, LCD_L + 20f, LCD_T + 40f, pSpeed)
        
        pSpeed.textSize = 66f
        pSpeed.textAlign = Paint.Align.RIGHT
        val vStr = if (!have) "---" else fmtParam(entry, v)
        c.drawText(vStr, LCD_R - 20f, LCD_B - 40f, pSpeed)
    }

    private fun drawFooter(c: Canvas) {
        pFill.color = Color.parseColor("#080B12")
        c.drawRect(0f, 660f, 1280f, 720f, pFill)
        
        pSpeed.textSize = 20f
        pSpeed.color = Color.parseColor("#444B59")
        pSpeed.textAlign = Paint.Align.CENTER
        c.drawText("CHECK ENGINE OK", 200f, 700f, pSpeed)
        c.drawText("ZAPISZ EVENT", 640f, 700f, pSpeed)
        c.drawText(if (connected) "OBD ONLINE" else "OFFLINE", 1080f, 700f, pSpeed)
    }

    private fun paramValue(key: String): Float = when (key) { "ECT"->ectC; "BOOST"->0f; "VOLTS"->voltage; "FUEL"->lambdaAfr; else->Float.NaN }
    private fun arcText(key: String, v: Float): String = if (v.isNaN()) "---" else fmtParam(key, v)
    private fun arcFrac(key: String, v: Float): Float = when (key) { "ECT" -> ((v - 40f) / 80f).coerceIn(0f, 1f); "FUEL" -> ((v - 10f) / 9f).coerceIn(0f, 1f); else -> 0.5f }
    private fun arcAlarm(key: String, v: Float): Boolean = false
    private fun fmtParam(key: String, v: Float): String = String.format("%.1f", v)

    override fun onTouchEvent(e: MotionEvent): Boolean = true
}
