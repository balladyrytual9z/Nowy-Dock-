package pl.dock.widget.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import pl.dock.widget.repository.EngineStateLabel
import pl.dock.widget.repository.VehicleUiState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Honda OEM UFO Cluster - FOTOREALIZM (Multi-pass Bloom & Physical Geometry)
 * Zawiera pełne API oraz kompletną logikę wykresów i dotyku dla UfoClusterActivity.
 */
class UfoClusterFullView(context: Context) : View(context) {

    companion object {
        val ARC_PARAMS = arrayOf(
            "ECT", "BOOST", "VOLTS", "FUEL", "TPS", "IAT", "LTFT", "STFT", "MAF", "BARO",
            "AVG_TRIP_FUEL"
        )
        val CHART_KEYS = arrayOf(
            "BOOST", "ECT", "VOLTS", "TPS", "IAT", "LTFT",
            "STFT", "MAF", "BARO", "FUEL"
        )

        fun labelOf(key: String): String = when (key) {
            "ECT" -> "ECT"
            "BOOST" -> "BOOST"
            "VOLTS" -> "VOLTS"
            "FUEL" -> "FUEL (AFR)"
            "TPS" -> "TPS"
            "IAT" -> "IAT"
            "LTFT" -> "LTFT"
            "STFT" -> "STFT"
            "MAF" -> "MAF"
            "BARO" -> "BARO"
            "AVG_TRIP_FUEL" -> "AVG L/100"
            else -> key
        }

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
    private val GREEN = Color.parseColor("#11E840")
    private val AMBER = Color.parseColor("#FFA600")

    private var BG = BG_NIGHT

    // ---------- Stan danych ----------
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

    // ---------- Konfiguracja ----------
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

    // ---------- Callbacki ----------
    var onOpenDtc: () -> Unit = {}
    var onSaveEvent: () -> Unit = {}
    var onSwapUi: () -> Unit = {}
    var onOpenSettings: () -> Unit = {}
    var onSpeedTap: () -> Unit = {}
    var onArcAssign: (left: Boolean) -> Unit = { _ -> }
    var onOpenEReport: () -> Unit = {}

    private var night = true
    private var engineOff = false
    private var savedLeftIndex = 0
    private var savedRightIndex = 0
    var offLeftParam = "VOLTS"
    var offRightParam = "AVG_TRIP_FUEL"

    // ---------- LCD MID ----------
    private val midLoop = arrayOf(
        "BOOST", "ECT", "VOLTS", "TPS", "IAT", "LTFT",
        "CHART:BOOST", "CHART:ECT", "CHART:VOLTS", "CHART:TPS",
        "CHART:IAT", "CHART:LTFT", "CHART:STFT", "CHART:MAF",
        "CHART:BARO", "CHART:FUEL"
    )
    private var midIndex = 0

    private fun midKey(entry: String): String =
        if (entry.startsWith("CHART:")) entry.substring(6) else entry

    private val histVals = HashMap<String, FloatArray>()
    private val histTs = HashMap<String, LongArray>()
    private val histLen = HashMap<String, Int>()
    private val histHead = HashMap<String, Int>()
    private val midMax = HashMap<String, Float>()
    private val midAvgSum = HashMap<String, Float>()
    private val midAvgCount = HashMap<String, Float>()

    // ---------- Łuki boczne ----------
    private val leftLoop = arrayOf("ECT", "BOOST", "VOLTS")
    private val rightLoop = arrayOf("FUEL", "TPS", "IAT", "LTFT")
    private var leftIndex = 0
    private var rightIndex = 0

    // ---------- Geometria (Przesunięte dla proporcji fotorealistycznych) ----------
    private val CX = 640f
    private val CY = 420f // Środek obrotomierza przesunięty niżej, by HUD miał miejsce na górze
    private val LCD_L = 520f; private val LCD_T = 300f
    private val LCD_R = 760f; private val LCD_B = 540f

    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private var staticLayer: Bitmap? = null

    // ---------- Painty ----------
    private val pFill = Paint()
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Węższa, techniczna czcionka imitująca cyfrowe LCD prędkościomierza
    private val pSpeed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textScaleX = 0.85f 
    }
    private val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        textScaleX = 0.9f
    }
    private val pMono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }
    
    private val rectF = RectF()
    // Drastycznie mniejsze i podniesione łuki, nie dotykają stopki
    private val ovalL = RectF(120f, 260f, 280f, 580f)
    private val ovalR = RectF(1000f, 260f, 1160f, 580f)
    private val needlePath = Path()
    private val iconPath = Path()
    private var lastFrameMs = 0L

    private val tfBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val tfBoldMono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    // Poświata HUD skompresowana Y-osią
    private val hudGlowShader = RadialGradient(640f, 80f, 300f,
        intArrayOf(0x991433FF.toInt(), 0x330011AA, 0x00000000),
        floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)

    private var spdCache = "---"
    private var spdCacheSrc = Int.MIN_VALUE
    private var mphCache = false
    private var voltText = "--- V"
    private var voltSrc = Float.NaN
    private var midMainCache = "---"
    private var midMainSrc = Float.NaN
    private var midKeyCache = ""
    private var arcTxtCacheL = ""
    private var arcTxtSrcL = Float.NaN
    private var arcTxtKeyL = ""
    private var arcTxtCacheR = ""
    private var arcTxtSrcR = Float.NaN
    private var arcTxtKeyR = ""
    private var statKey = ""
    private var statHave = false
    private var statMaxSrc: Float? = null
    private var statAvgSrc: Float? = null
    private var statMaxCache = "MAX: N/A"
    private var statAvgCache = "AVG: N/A"

    // ================= API PUBLICZNE DLA UfoClusterActivity ================= //

    fun update(s: VehicleUiState) {
        val off = s.engineState == EngineStateLabel.OFF
        if (off != engineOff) {
            engineOff = off
            if (off) {
                savedLeftIndex = leftIndex; savedRightIndex = rightIndex
            } else {
                leftIndex = savedLeftIndex; rightIndex = savedRightIndex
            }
        }
        rpmTarget = if (off || s.rpm < 0) 0f else s.rpm.toFloat()
        speedKmh = s.speedKmh
        voltage = s.voltage
        ectC = s.ectC
        tpsPct = s.tpsPct
        mapKpa = s.mapKpa
        iatC = s.iatC
        ltftPct = s.ltftPct
        stftPct = s.stftPct
        baroKpa = s.baroKpa
        lambdaAfr = s.lambdaAfr
        mafGs = s.mafGs
        milOn = s.milOn
        dtcCount = s.dtcCount
        connected = s.connected
        connecting = s.connecting
        if (s.connected) lastDataMs = System.currentTimeMillis()
        accumulateTrip(System.currentTimeMillis())
        trackMidStats()
        pushAllHistory()
        postInvalidateOnAnimation()
    }

    fun toggleUnits() { 
        useMph = !useMph 
        postInvalidateOnAnimation()
    }

    fun setNight(n: Boolean) {
        if (night == n) return
        night = n
        BG = if (n) BG_NIGHT else BG_NIGHT 
        if (width > 0 && height > 0) buildStaticLayer()
        postInvalidateOnAnimation()
    }

    fun setArcParam(left: Boolean, key: String) {
        if (engineOff) {
            if (left) offLeftParam = key else offRightParam = key
            postInvalidateOnAnimation()
            return
        }
        if (left) {
            val i = leftLoop.indexOf(key)
            leftIndex = if (i >= 0) i else 0
        } else {
            val i = rightLoop.indexOf(key)
            rightIndex = if (i >= 0) i else 0
        }
        postInvalidateOnAnimation()
    }

    // ================= LOGIKA DANYCH I WYKRESÓW ================= //

    private fun paramValue(key: String): Float = when (key) {
        "ECT" -> ectC
        "BOOST" -> if (mapKpa.isNaN() || baroKpa.isNaN()) Float.NaN else (mapKpa - baroKpa) / 100f
        "VOLTS" -> voltage
        "FUEL" -> lambdaAfr
        "TPS" -> tpsPct
        "IAT" -> iatC
        "LTFT" -> ltftPct
        "STFT" -> stftPct
        "MAF" -> mafGs
        "BARO" -> baroKpa
        "AVG_TRIP_FUEL" -> avgTripFuelL100()
        else -> Float.NaN
    }

    private fun paramUnit(key: String): String = when (key) {
        "ECT", "IAT" -> "C"
        "BOOST" -> "bar"
        "VOLTS" -> "V"
        "FUEL" -> "AFR"
        "TPS", "LTFT", "STFT" -> "%"
        "MAF" -> "g/s"
        "BARO" -> "kPa"
        "AVG_TRIP_FUEL" -> "L/100"
        else -> ""
    }

    private fun paramInterval(key: String): String = when (key) {
        "ECT", "IAT" -> "C / TRIP"
        "BOOST" -> "bar / 5m"
        "VOLTS" -> "V / 1h"
        "FUEL" -> "AFR / TRIP"
        "TPS", "LTFT", "STFT" -> "% / TRIP"
        "MAF" -> "g/s / 5m"
        "BARO" -> "kPa / 1h"
        "AVG_TRIP_FUEL" -> "L/100 / TRIP"
        else -> ""
    }

    private fun trackMidStats() {
        val key = midKey(midLoop[midIndex])
        val value = paramValue(key)
        if (value.isNaN()) return
        val mx = midMax[key]
        if (mx == null || value > mx) midMax[key] = value
        midAvgSum[key] = (midAvgSum[key] ?: 0f) + value
        midAvgCount[key] = (midAvgCount[key] ?: 0f) + 1f
    }

    private var tripFuelL = 0.0
    private var tripDistKm = 0.0
    private var lastTripMs = 0L

    private fun accumulateTrip(now: Long) {
        if (lastTripMs > 0L && now > lastTripMs) {
            val dtH = (now - lastTripMs) / 3_600_000.0
            if (speedKmh >= 0) tripDistKm += speedKmh * dtH
            if (!mafGs.isNaN() && mafGs > 0.5) {
                tripFuelL += (mafGs / AFR_GAS / FUEL_DENSITY_G_PER_L) * dtH
            }
        }
        lastTripMs = now
    }

    private fun avgTripFuelL100(): Float =
        if (tripDistKm < 0.2) Float.NaN else (tripFuelL / tripDistKm * 100.0).toFloat()

    // ================= CYKL ŻYCIA I RYSOWANIE ================= //

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
        
        if (connected || connecting || Math.abs(rpm - rpmTarget) > 1f) {
            postInvalidateOnAnimation()
        } else {
            lastFrameMs = 0L
        }
    }

    // ---------- WARSTWA STATYCZNA (Multi-pass Bloom) ----------

    private fun drawStatic(c: Canvas) {
        val ringRadius = 175f 

        // 1. Matowa faktura pod panelem zegarów
        pFill.color = Color.parseColor("#050608")
        c.drawCircle(CX, CY, 250f, pFill)

        // 2. Silnik Bloom dla błękitnego pierścienia (prawdziwy fizyczny dyfuzor LED)
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
        pStroke.style = Paint.Style.FILL

        val a0 = Math.toRadians(150.0)
        val sweep = Math.toRadians(240.0)

        // 3. Cyfry (Wewnątrz pierścienia)
        pNum.textSize = 46f
        pNum.color = TICK_OEM
        pNum.textAlign = Paint.Align.CENTER
        
        for (r in 0..8000 step 1000) {
            val frac = r / 8000f
            val ang = a0 + sweep * frac
            if (r > 0) {
                val numR = 135f // Cyfry są wewnątrz!
                pNum.setShadowLayer(12f, 0f, 0f, TICK_OEM)
                c.drawText((r / 1000).toString(),
                    (CX + numR * cos(ang)).toFloat(),
                    (CY + numR * sin(ang)).toFloat() + 16f, pNum)
                pNum.clearShadowLayer()
            }
        }

        // 4. Podziałki (Zewnętrzne krawędzie)
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
            
            val rInner = 195f // Start na zewnątrz pierścienia
            val rOuter = rInner + len
            c.drawLine(
                (CX + rInner * cos(ang)).toFloat(), (CY + rInner * sin(ang)).toFloat(),
                (CX + rOuter * cos(ang)).toFloat(), (CY + rOuter * sin(ang)).toFloat(), pStroke
            )
            pStroke.clearShadowLayer()
        }

        // Czerwona wstęga odcinki
        pStroke.strokeWidth = 5f
        pStroke.color = TICK_RED
        pStroke.setShadowLayer(15f, 0f, 0f, TICK_RED)
        val redStart = 150f + 240f * (6500f / 8000f)
        c.drawArc(RectF(CX - 195f, CY - 195f, CX + 195f, CY + 195f),
            redStart, 150f + 240f - redStart, false, pStroke)
        pStroke.clearShadowLayer()

        // 5. Matryca LCD MID
        pStroke.style = Paint.Style.FILL
        pFill.color = LCD_BG
        c.drawRoundRect(RectF(LCD_L, LCD_T, LCD_R, LCD_B), 10f, 10f, pFill)
        
        pStroke.style = Paint.Style.STROKE
        pStroke.strokeWidth = 3f
        pStroke.color = Color.parseColor("#050709") // Wewnętrzny cień wnęki
        c.drawRoundRect(RectF(LCD_L + 1f, LCD_T + 1f, LCD_R - 1f, LCD_B - 1f), 8f, 8f, pStroke)
        pStroke.style = Paint.Style.FILL

        // 6. Zgaszone klocki łuków bocznych (podniesione)
        val numSeg = 16 
        val segSweep = 70f / numSeg
        val gap = 1.5f
        val solid = segSweep - gap
        
        pStroke.style = Paint.Style.STROKE
        pStroke.strokeWidth = 18f // Grube fizyczne segmenty LCD
        pStroke.color = OFF_SEGMENT
        
        for (i in 0 until numSeg) {
            c.drawArc(ovalL, 160f + i * segSweep, solid, false, pStroke)
            c.drawArc(ovalR, 320f - i * segSweep - solid, solid, false, pStroke) // Start od dołu, kierunek przeciwny
        }
        pStroke.style = Paint.Style.FILL
    }

    // ---------- WARSTWA DYNAMICZNA ----------

    private fun drawDynamic(c: Canvas) {
        drawHud(c)
        drawEReport(c)
        drawNeedle(c)
        drawLcdMid(c)
        drawWingArc(c, left = true)
        drawWingArc(c, left = false)
        drawFooter(c)
    }

    private fun drawHud(c: Canvas) {
        // Poświata HUD skompresowana by nie nachodzić na zegary
        c.save()
        c.scale(1f, 0.4f, 640f, 90f) 
        pFill.shader = hudGlowShader
        c.drawCircle(640f, 90f, 300f, pFill)
        pFill.shader = null
        c.restore()

        if (speedKmh != spdCacheSrc || useMph != mphCache) {
            spdCacheSrc = speedKmh
            mphCache = useMph
            spdCache = if (speedKmh < 0) "---" else
                (if (useMph) (speedKmh * 0.621371f).toInt() else speedKmh).toString()
        }
        
        // Techniczna kondensowana czcionka dla HUD
        pSpeed.textSize = 110f
        pSpeed.color = Color.WHITE
        pSpeed.textAlign = Paint.Align.CENTER
        pSpeed.setShadowLayer(25f, 0f, 0f, CYAN_HUD)
        c.drawText(spdCache, 640f, 130f, pSpeed)
        pSpeed.clearShadowLayer()

        pSpeed.textSize = 28f
        c.drawText(if (useMph) "mph" else "km/h", 760f, 130f, pSpeed)

        val gear = if (speedKmh > 3 && rpmTarget > 500f) {
            val r = rpmTarget / speedKmh
            when {
                r < 35f -> "5"
                r < 44f -> "4"
                r < 60f -> "3"
                r < 100f -> "2"
                else -> "1"
            }
        } else if (speedKmh in 0..3) "N" else "-"
        pSpeed.color = Color.GRAY
        c.drawText(gear, 820f, 130f, pSpeed)

        // Klocki paska REV (cyfrowe)
        val segW = 18f
        val now = System.currentTimeMillis()
        for (i in 0 until 10) {
            val x = 320f + i * (segW + 8f)
            val lit = rpm >= 4200 + i * 300
            val inRed = rpm >= 6300
            
            val baseColor = when {
                inRed -> if ((now / 150) % 2 == 0L) TICK_RED else Color.parseColor("#440000")
                !lit -> OFF_SEGMENT
                i < 4 -> CYAN_HUD
                i < 8 -> GREEN
                else -> AMBER
            }
            pFill.color = baseColor
            if (lit) pFill.setShadowLayer(8f, 0f, 0f, baseColor) else pFill.clearShadowLayer()
            c.drawRoundRect(x, 24f, x + segW, 44f, 4f, 4f, pFill)
        }
        pFill.clearShadowLayer()

        val eco = !tpsPct.isNaN() && tpsPct < ecoTps && !mapKpa.isNaN() && mapKpa < ecoMap && rpm < ecoRpm
        pNum.textSize = 24f
        pNum.color = if (eco) GREEN else OFF_SEGMENT
        if (eco) pNum.setShadowLayer(10f, 0f, 0f, GREEN)
        c.drawText("ECO", 260f, 96f, pNum)
        pNum.clearShadowLayer()
        
        val vtec = rpm >= vtecRpm
        pNum.color = if (vtec) CYAN_HUD else OFF_SEGMENT
        if (vtec) pNum.setShadowLayer(10f, 0f, 0f, CYAN_HUD)
        c.drawText("VTEC", 1020f, 96f, pNum)
        pNum.clearShadowLayer()
    }

    private fun drawEReport(c: Canvas) {
        if (!engineOff) return
        rectF.set(48f, 56f, 268f, 104f)
        pStroke.strokeWidth = 2f
        pStroke.color = Color.parseColor("#4A5A72")
        pStroke.style = Paint.Style.STROKE
        c.drawRoundRect(rectF, 10f, 10f, pStroke)
        pStroke.style = Paint.Style.FILL
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = 24f
        pText.typeface = tfBold
        pText.color = TICK_OEM
        c.drawText("E-RAPORT", 158f, 88f, pText)
    }

    private fun drawNeedle(c: Canvas) {
        val a0 = Math.toRadians(150.0)
        val sweep = Math.toRadians(240.0)
        val frac = (rpm / 8000f).coerceIn(0f, 1f)
        val ang = a0 + sweep * frac
        
        val isRed = rpm >= redlineRpm
        val glowColor = if (isRed) TICK_RED else Color.parseColor("#FFAA00") // Gorący pomarańcz wskazówki

        val rInner = 182f
        val rOuter = 230f
        val wBase = 12f // Gruby start u podstawy
        val wTip = 4f   // Wąski czubek wskazówki

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

        // Silna pomarańczowa/czerwona poświata wokół wskazówki
        pFill.color = glowColor
        pFill.setShadowLayer(25f, 0f, 0f, glowColor)
        c.drawPath(needlePath, pFill)
        
        // Gorący biały rdzeń
        pFill.color = Color.WHITE
        pFill.setShadowLayer(8f, 0f, 0f, Color.WHITE)
        c.drawPath(needlePath, pFill)
        pFill.clearShadowLayer()
    }

    private fun drawMidIcon(c: Canvas, key: String, x: Float, y: Float, ink: Int) {
        pStroke.color = ink
        pStroke.strokeWidth = 3f
        iconPath.reset()
        when (key) {
            "ECT" -> { 
                c.drawLine(x + 10f, y + 2f, x + 10f, y + 14f, pStroke)
                pStroke.style = Paint.Style.STROKE
                rectF.set(x + 4f, y + 12f, x + 16f, y + 24f)
                c.drawOval(rectF, pStroke)
                pStroke.style = Paint.Style.FILL
            }
            "BOOST" -> { 
                iconPath.moveTo(x + 4f, y + 16f)
                iconPath.lineTo(x + 12f, y + 4f)
                iconPath.lineTo(x + 20f, y + 16f)
                pStroke.style = Paint.Style.STROKE
                c.drawPath(iconPath, pStroke)
                pStroke.style = Paint.Style.FILL
                c.drawLine(x + 12f, y + 8f, x + 12f, y + 24f, pStroke)
            }
            "VOLTS" -> { 
                iconPath.moveTo(x + 14f, y)
                iconPath.lineTo(x + 5f, y + 13f)
                iconPath.lineTo(x + 11f, y + 13f)
                iconPath.lineTo(x + 9f, y + 24f)
                iconPath.lineTo(x + 19f, y + 10f)
                iconPath.lineTo(x + 13f, y + 10f)
                iconPath.close()
                c.drawPath(iconPath, pFill.also { it.color = ink })
            }
            "TPS" -> { 
                pStroke.style = Paint.Style.STROKE
                c.drawCircle(x + 12f, y + 13f, 9f, pStroke)
                pStroke.style = Paint.Style.FILL
                c.drawLine(x + 12f, y + 13f, x + 18f, y + 5f, pStroke)
            }
            "IAT" -> { 
                for (k in 0 until 3) c.drawLine(x + 3f, y + 6f + k * 6f, x + 21f, y + 6f + k * 6f, pStroke)
            }
            "LTFT", "STFT", "FUEL" -> { 
                iconPath.moveTo(x + 12f, y + 2f)
                iconPath.lineTo(x + 20f, y + 14f)
                iconPath.lineTo(x + 4f, y + 14f)
                iconPath.close()
                c.drawPath(iconPath, pFill.also { it.color = ink })
                pStroke.style = Paint.Style.STROKE
                c.drawCircle(x + 12f, y + 18f, 5f, pStroke)
                pStroke.style = Paint.Style.FILL
            }
            "MAF" -> { 
                for (k in 0 until 3) {
                    val dx = (k - 1) * 7f
                    c.drawLine(x + 12f + dx * 0.4f, y + 4f, x + 12f + dx, y + 22f, pStroke)
                }
            }
            else -> { 
                pStroke.style = Paint.Style.STROKE
                c.drawCircle(x + 12f, y + 12f, 9f, pStroke)
                pStroke.style = Paint.Style.FILL
                c.drawLine(x + 3f, y + 12f, x + 21f, y + 12f, pStroke)
            }
        }
    }

    private fun drawLcdMid(c: Canvas) {
        val entry = midLoop[midIndex]
        val key = midKey(entry)
        val v = paramValue(key)
        val have = !v.isNaN() && connected

        val ink = if (have) LCD_TEXT else Color.parseColor("#333A48")
        
        drawMidIcon(c, key, LCD_L + 16f, LCD_T + 12f, ink)
        
        pText.color = ink
        pText.textSize = 24f
        pText.typeface = tfBold
        pText.textAlign = Paint.Align.LEFT
        c.drawText(key, LCD_L + 50f, LCD_T + 32f, pText)
        
        if (entry.startsWith("CHART:")) {
            pText.textAlign = Paint.Align.RIGHT
            pText.textSize = 16f
            pText.typeface = Typeface.DEFAULT
            c.drawText("WYKRES 30 S", LCD_R - 16f, LCD_T + 30f, pText)
            drawMidChart(c, key, have, ink)
        } else {
            if (v != midMainSrc || key != midKeyCache) {
                midMainSrc = v
                midKeyCache = key
                midMainCache = if (!have) "---" else fmtParam(key, v)
            }
            pMono.textSize = 60f
            pMono.typeface = tfBoldMono
            pMono.color = ink
            pMono.textAlign = Paint.Align.RIGHT
            if (have) pMono.setShadowLayer(8f, 0f, 0f, Color.parseColor("#66FFFFFF"))
            c.drawText(midMainCache, LCD_R - 54f, LCD_T + 110f, pMono)
            pMono.clearShadowLayer()
            
            pMono.textSize = 24f
            c.drawText(paramUnit(key), LCD_R - 16f, LCD_T + 110f, pMono)
        }

        // Panel statystyk MAX/AVG
        val winMs = statWindowMs(key)
        val mx: Float?
        val avg: Float?
        if (winMs > 0L) {
            refreshWinStats(key, winMs)
            mx = winMax[key]
            avg = winAvg[key]
        } else {
            mx = midMax[key]
            val avgSum = midAvgSum[key]
            val avgCnt = midAvgCount[key]
            avg = if (avgSum == null || avgCnt == null || avgCnt <= 0f) null else avgSum / avgCnt
        }
        if (key != statKey || have != statHave || mx != statMaxSrc || avg != statAvgSrc) {
            statKey = key; statHave = have; statMaxSrc = mx; statAvgSrc = avg
            statMaxCache = if (have && mx != null) "MAX: " + fmtParam(key, mx) else "MAX: N/A"
            statAvgCache = if (have && avg != null) "AVG: " + fmtParam(key, avg) else "AVG: N/A"
        }
        
        pText.textAlign = Paint.Align.LEFT
        pText.textSize = 20f
        pText.typeface = Typeface.DEFAULT
        pText.color = if (have) Color.parseColor("#AAB2BD") else Color.parseColor("#333A48")
        c.drawText(paramInterval(key), LCD_L + 20f, LCD_B - 80f, pText)

        pText.textAlign = Paint.Align.RIGHT
        pText.textSize = 24f
        pText.typeface = tfBold
        pText.color = if (have) CYAN_HUD else Color.parseColor("#333A48")
        c.drawText(statMaxCache, LCD_R - 16f, LCD_B - 50f, pText)
        pText.color = if (have) Color.parseColor("#AAB2BD") else Color.parseColor("#333A48")
        c.drawText(statAvgCache, LCD_R - 16f, LCD_B - 20f, pText)
    }

    private fun drawWingArc(c: Canvas, left: Boolean) {
        val key = if (engineOff) {
            if (left) offLeftParam else offRightParam
        } else if (left) leftLoop[leftIndex] else rightLoop[rightIndex]
        val v = paramValue(key)
        val alarm = arcAlarm(key, v)
        val frac = arcFrac(key, v)

        if (!v.isNaN() && frac > 0f) {
            val numSeg = 16
            val segSweep = 70f / numSeg
            val gap = 1.5f
            val solid = segSweep - gap
            val litCount = (frac * numSeg).toInt()
            
            pStroke.style = Paint.Style.STROKE
            pStroke.strokeWidth = 18f
            
            for (i in 0 until litCount) {
                // Skrajne klocki stają się czerwone
                val isRedZone = if (left) i >= 13 else i <= 2
                val segColor = if (alarm || isRedZone) TICK_RED else TICK_OEM
                
                pStroke.color = segColor
                pStroke.setShadowLayer(15f, 0f, 0f, segColor) 
                
                if (left) {
                    c.drawArc(ovalL, 160f + i * segSweep, solid, false, pStroke)
                } else {
                    c.drawArc(ovalR, 320f - i * segSweep - solid, solid, false, pStroke) // Start od dołu, prawy łuk
                }
            }
            pStroke.clearShadowLayer()
        }
        pStroke.style = Paint.Style.FILL

        val innerX = if (left) 310f else 970f
        pText.textAlign = if (left) Paint.Align.LEFT else Paint.Align.RIGHT
        pText.textSize = 22f
        pText.typeface = tfBold
        pText.color = if (alarm) TICK_RED else Color.parseColor("#8894A8")
        c.drawText(key, innerX, 420f, pText)
        
        if (v != (if (left) arcTxtSrcL else arcTxtSrcR) || key != (if (left) arcTxtKeyL else arcTxtKeyR)) {
            if (left) { arcTxtSrcL = v; arcTxtKeyL = key; arcTxtCacheL = arcText(key, v) }
            else { arcTxtSrcR = v; arcTxtKeyR = key; arcTxtCacheR = arcText(key, v) }
        }
        pText.textSize = 30f
        pText.color = LCD_TEXT
        c.drawText(if (left) arcTxtCacheL else arcTxtCacheR, innerX, 456f, pText)
    }

    private fun drawFooter(c: Canvas) {
        pFill.color = Color.parseColor("#06080D")
        c.drawRect(0f, 640f, 1280f, 720f, pFill)
        
        pStroke.strokeWidth = 2f
        pStroke.color = Color.parseColor("#222833")
        c.drawLine(0f, 640f, 1280f, 640f, pStroke)

        val mil = milOn || dtcCount > 0
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = 24f
        pText.typeface = tfBold
        pText.color = if (mil) TICK_RED else Color.parseColor("#666D7A")
        c.drawText(if (milOn) "CHECK ENGINE" else if (dtcCount > 0) "DTC: " + dtcCount
            else "CHECK ENGINE OK", 128f, 686f, pText)
        pText.textSize = 16f
        pText.color = Color.parseColor("#666D7A")
        c.drawText("KODY BŁĘDÓW", 128f, 712f, pText)

        pText.textSize = 24f
        pText.color = TICK_OEM
        c.drawText("ZAPISZ EVENT", 384f, 690f, pText)
        pText.textSize = 16f
        pText.color = Color.parseColor("#666D7A")
        c.drawText("30 S TELEMETRII", 384f, 712f, pText)

        pText.textSize = 24f
        pText.color = TICK_OEM
        c.drawText("SWAP UI", 640f, 690f, pText)
        pText.textSize = 16f
        pText.color = Color.parseColor("#666D7A")
        c.drawText("UFO / CARPLAY", 640f, 712f, pText)

        drawGearIcon(c, 896f, 664f, TICK_OEM)
        pText.textSize = 16f
        pText.color = Color.parseColor("#666D7A")
        c.drawText("USTAWIENIA", 896f, 712f, pText)

        val stale = connected && System.currentTimeMillis() - lastDataMs > 5000
        pText.textSize = 26f
        pText.typeface = tfBold
        pText.color = when {
            !connected && !connecting -> Color.parseColor("#666D7A")
            connecting -> AMBER
            stale -> Color.parseColor("#666D7A")
            else -> GREEN
        }
        c.drawText(when {
            !connected && !connecting -> "OFFLINE"
            connecting -> "CONNECTING"
            else -> "OBD OK"
        }, 1152f, 690f, pText)
        
        pText.textSize = 18f
        pText.color = TICK_OEM
        if (!voltage.equals(voltSrc)) {
            voltSrc = voltage
            voltText = if (voltage.isNaN()) "--- V" else String.format("%.1f V", voltage)
        }
        c.drawText(voltText, 1152f, 712f, pText)
    }

    private fun drawGearIcon(c: Canvas, cx: Float, cy: Float, ink: Int) {
        pStroke.color = ink
        pStroke.strokeWidth = 4f
        pStroke.style = Paint.Style.STROKE
        c.drawCircle(cx, cy + 12f, 10f, pStroke)
        for (k in 0 until 8) {
            val a = Math.toRadians((k * 45).toDouble())
            c.drawLine(
                (cx + 10f * cos(a)).toFloat(), (cy + 12f + 10f * sin(a)).toFloat(),
                (cx + 15f * cos(a)).toFloat(), (cy + 12f + 15f * sin(a)).toFloat(), pStroke)
        }
        pStroke.style = Paint.Style.FILL
    }

    // ---------- LOGIKA WYKRESÓW (MID) ----------

    private val HIST_CAP = 128
    private val HIST_MIN_DT_MS = 250L

    private fun pushAllHistory() {
        val now = System.currentTimeMillis()
        for (k in CHART_KEYS) {
            val v = paramValue(k)
            pushHistory(k, v, now)
            if (statWindowMs(k) > 0L) pushWinSample(k, v, now)
        }
    }

    private fun statWindowMs(key: String): Long = when (key) {
        "BOOST", "MAF" -> 300_000L
        "VOLTS", "BARO" -> 3_600_000L
        else -> 0L
    }

    private val winTs = HashMap<String, LongArray>()
    private val winVals = HashMap<String, FloatArray>()
    private val winLen = HashMap<String, Int>()
    private val winHead = HashMap<String, Int>()
    private val winMax = HashMap<String, Float?>()
    private val winAvg = HashMap<String, Float?>()
    private var winCalcMs = 0L

    private fun pushWinSample(key: String, v: Float, now: Long) {
        if (v.isNaN()) return
        val winMs = statWindowMs(key)
        if (winMs <= 0L) return
        val cap = if (winMs >= 3_600_000L) 14_400 else 1_200
        var ts = winTs[key]
        var vs = winVals[key]
        if (ts == null || vs == null || ts.size != cap) {
            ts = LongArray(cap); vs = FloatArray(cap)
            winTs[key] = ts; winVals[key] = vs
            winLen[key] = 0; winHead[key] = 0
        }
        val len = winLen[key] ?: 0
        val head = winHead[key] ?: 0
        if (len > 0) {
            val lastIdx = (head + len - 1) % cap
            if (now - ts[lastIdx] < HIST_MIN_DT_MS) {
                vs[lastIdx] = v
                return
            }
        }
        if (len < cap) {
            val idx = (head + len) % cap
            ts[idx] = now; vs[idx] = v
            winLen[key] = len + 1
        } else {
            ts[head] = now; vs[head] = v
            winHead[key] = (head + 1) % cap
        }
    }

    private fun refreshWinStats(key: String, winMs: Long) {
        val now = System.currentTimeMillis()
        if (now - winCalcMs < 250L && winMax.containsKey(key)) return
        winCalcMs = now
        val ts = winTs[key]
        val vs = winVals[key]
        val len = winLen[key] ?: 0
        val head = winHead[key] ?: 0
        if (ts == null || vs == null || len == 0) {
            winMax[key] = null; winAvg[key] = null
            return
        }
        val cap = ts.size
        var mx = -Float.MAX_VALUE
        var sum = 0.0
        var cnt = 0
        for (i in 0 until len) {
            val idx = (head + i) % cap
            if (now - ts[idx] > winMs) continue
            val v = vs[idx]
            if (v > mx) mx = v
            sum += v
            cnt++
        }
        winMax[key] = if (cnt == 0) null else mx
        winAvg[key] = if (cnt == 0) null else (sum / cnt).toFloat()
    }

    private fun pushHistory(key: String, v: Float, now: Long) {
        if (v.isNaN()) return
        val ts = histTs[key] ?: LongArray(HIST_CAP).also { histTs[key] = it }
        val vs = histVals[key] ?: FloatArray(HIST_CAP).also { histVals[key] = it }
        val len = histLen[key] ?: 0
        val head = histHead[key] ?: 0
        if (len > 0) {
            val lastIdx = (head + len - 1) % HIST_CAP
            if (now - ts[lastIdx] < HIST_MIN_DT_MS) {
                vs[lastIdx] = v
                return
            }
        }
        if (len < HIST_CAP) {
            val idx = (head + len) % HIST_CAP
            ts[idx] = now; vs[idx] = v
            histLen[key] = len + 1
        } else {
            ts[head] = now; vs[head] = v
            histHead[key] = (head + 1) % HIST_CAP
        }
    }

    private fun drawMidChart(c: Canvas, key: String, have: Boolean, ink: Int) {
        val len = histLen[key] ?: 0
        if (len < 2 || !have) {
            pText.textAlign = Paint.Align.CENTER
            pText.textSize = 20f
            pText.typeface = tfBold
            pText.color = Color.parseColor("#333A48")
            c.drawText(if (have) "ZBIERAM DANE..." else "BRAK DANYCH", 640f, LCD_T + 80f, pText)
            return
        }
        val head = histHead[key] ?: 0
        val ts = histTs[key] ?: return
        val vs = histVals[key] ?: return
        val now = System.currentTimeMillis()
        val winMs = 30_000L
        val x0 = LCD_L + 10f
        val x1 = LCD_R - 10f
        val yBase = LCD_B - 100f
        val yTop = LCD_T + 40f
        var vmin = Float.MAX_VALUE
        var vmax = -Float.MAX_VALUE
        for (i in 0 until len) {
            val v = vs[(head + i) % HIST_CAP]
            if (v < vmin) vmin = v
            if (v > vmax) vmax = v
        }
        if (vmax - vmin < 1e-4f) { vmax += 0.5f; vmin -= 0.5f }
        val pad = (vmax - vmin) * 0.1f
        vmax += pad; vmin -= pad

        val block = 4f
        pFill.color = (ink and 0x00FFFFFF) or 0x30000000
        for (i in 0 until len) {
            val idx = (head + i) % HIST_CAP
            val age = now - ts[idx]
            if (age > winMs) continue
            val x = x0 + (x1 - x0) * (1f - age.toFloat() / winMs)
            val hRaw = (yBase - yTop) * ((vs[idx] - vmin) / (vmax - vmin))
            val h = (Math.round(hRaw / block) * block.toInt()).toFloat()
            if (h > 0f) c.drawRect(x - block / 2f, yBase - h, x + block / 2f, yBase, pFill)
        }

        needlePath.reset()
        var px = 0f
        var py = 0f
        var started = false
        for (i in 0 until len) {
            val idx = (head + i) % HIST_CAP
            val age = now - ts[idx]
            if (age > winMs) continue
            val x = x0 + (x1 - x0) * (1f - age.toFloat() / winMs)
            val y = yBase - (yBase - yTop) * ((vs[idx] - vmin) / (vmax - vmin))
            if (!started) {
                needlePath.moveTo(x, y)
                started = true
            } else {
                val mx = (px + x) / 2f
                val my = (py + y) / 2f
                needlePath.quadTo(px, py, mx, my)
            }
            px = x; py = y
        }
        if (started) {
            needlePath.lineTo(px, py)
            pStroke.color = ink
            pStroke.strokeWidth = 2.5f
            pStroke.style = Paint.Style.STROKE
            c.drawPath(needlePath, pStroke)
            pStroke.style = Paint.Style.FILL
        }
    }

    // ---------- FORMATOWANIE I OBLICZENIA DLA ŁUKÓW ----------

    private fun arcText(key: String, v: Float): String =
        if (v.isNaN()) "---" else fmtParam(key, v) + " " + paramUnit(key)

    private fun fmtParam(key: String, v: Float): String = when (key) {
        "BOOST" -> String.format("%.2f", v)
        "FUEL" -> String.format("%.1f", v)
        "AVG_TRIP_FUEL" -> String.format("%.1f", v)
        else -> if (v >= 100f || v == v.toInt().toFloat()) v.toInt().toString()
            else String.format("%.1f", v)
    }

    private fun arcFrac(key: String, v: Float): Float = when (key) {
        "ECT" -> if (v.isNaN()) 0f else ((v - 40f) / 80f).coerceIn(0f, 1f)
        "BOOST" -> if (v.isNaN()) 0.5f else (0.5f + (v / 1.2f) * 0.5f).coerceIn(0f, 1f)
        "VOLTS" -> if (v.isNaN()) 0f else ((v - 11f) / 4f).coerceIn(0f, 1f)
        "FUEL" -> if (v.isNaN()) 0f else ((v - 10f) / 9f).coerceIn(0f, 1f)
        "TPS" -> if (v.isNaN()) 0f else (v / 100f).coerceIn(0f, 1f)
        "IAT" -> if (v.isNaN()) 0f else ((v + 20f) / 90f).coerceIn(0f, 1f)
        "LTFT" -> if (v.isNaN()) 0f else ((v + 15f) / 30f).coerceIn(0f, 1f)
        "STFT" -> if (v.isNaN()) 0f else ((v + 15f) / 30f).coerceIn(0f, 1f)
        "MAF" -> if (v.isNaN()) 0f else (v / 80f).coerceIn(0f, 1f)
        "BARO" -> if (v.isNaN()) 0f else ((v - 60f) / 45f).coerceIn(0f, 1f)
        "AVG_TRIP_FUEL" -> if (v.isNaN()) 0f else (v / 20f).coerceIn(0f, 1f)
        else -> 0.5f
    }

    private fun arcAlarm(key: String, v: Float): Boolean = when (key) {
        "ECT" -> !v.isNaN() && v > ectMaxC
        "VOLTS" -> !v.isNaN() && (v < voltMin || v > voltMax)
        "LTFT" -> !v.isNaN() && (v > ltftMax || v < -ltftMax)
        "IAT" -> !v.isNaN() && v > iatMax
        "BOOST" -> !v.isNaN() && v > 1.0f
        "FUEL" -> !v.isNaN() && (v < 12f || v > 16f)
        else -> false
    }

    // ---------- OBSŁUGA GESTÓW (Dopasowana do nowych proporcji) ----------

    private var downX = 0f
    private var downY = 0f
    private var downMs = 0L

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = (e.x - offX) / scale
        val y = (e.y - offY) / scale

        when (e.action) {
            MotionEvent.ACTION_DOWN -> { downX = x; downY = y; downMs = e.downTime; return true }
            MotionEvent.ACTION_UP -> Unit
            else -> return true
        }
        val holdMs = e.eventTime - downMs

        // Dyskretny E-RAPORT po zgaszeniu
        if (engineOff && x in 48f..268f && y in 56f..104f) {
            onOpenEReport()
            return true
        }

        // FOOTER (Y > 640) - 5 sektorów
        if (y > 640f) {
            when {
                x < 256f -> onOpenDtc()
                x < 512f -> onSaveEvent()
                x < 768f -> onSwapUi()
                x < 1024f -> onOpenSettings()
                else -> Unit 
            }
            return true
        }
        
        // LCD MID - Zaktualizowane współrzędne 
        if (x in LCD_L..LCD_R && y in LCD_T..LCD_B) {
            if (y > 450f && holdMs > 1500) { // Long press na statystyki
                val key = midKey(midLoop[midIndex])
                midMax.remove(key); midAvgSum.remove(key); midAvgCount.remove(key)
                winLen[key] = 0; winHead[key] = 0; winMax[key] = null; winAvg[key] = null
            } else {
                midIndex = (midIndex + 1) % midLoop.size
            }
            postInvalidateOnAnimation()
            return true
        }
        
        // Łuki boczne - Zaktualizowane współrzędne
        if (y in 260f..580f) {
            if (x < 280f) {
                if (holdMs > 1000) onArcAssign(true)
                else { leftIndex = (leftIndex + 1) % leftLoop.size; postInvalidateOnAnimation() }
                return true
            }
            if (x > 1000f) {
                if (holdMs > 1000) onArcAssign(false)
                else { rightIndex = (rightIndex + 1) % rightLoop.size; postInvalidateOnAnimation() }
                return true
            }
        }
        
        // Tapnięcie HUD (Prędkościomierz)
        if (y < 160f && x in 400f..680f) { 
            onSpeedTap()
            return true 
        }
        
        return true
    }
}
