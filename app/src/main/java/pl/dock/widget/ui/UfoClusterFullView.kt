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
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import pl.dock.widget.repository.EngineStateLabel
import pl.dock.widget.repository.VehicleUiState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Honda OEM UFO Cluster - ULTIMATE FOTOREALIZM
 * Pełna geometria współśrodkowa, poprawiony Redline, fizyczne wskaźniki boczne,
 * skompresowana czcionka cyfrowa (VFD) i pełna, nieskompresowana objętość kodu.
 */
class UfoClusterFullView(context: Context) : View(context) {

    companion object {
        val ARC_PARAMS = arrayOf(
            "ECT", "BOOST", "VOLTS", "FUEL", "TPS", "IAT", "LTFT", "STFT", "MAF", "BARO", "AVG_TRIP_FUEL"
        )
        val CHART_KEYS = arrayOf(
            "BOOST", "ECT", "VOLTS", "TPS", "IAT", "LTFT", "STFT", "MAF", "BARO", "FUEL"
        )

        fun labelOf(key: String): String = key
        private const val FUEL_DENSITY_G_PER_L = 745.0
        private const val AFR_GAS = 14.7
    }

    // ---------- Paleta Kolorów OEM ----------
    private val BG_NIGHT = Color.parseColor("#020305")
    private val TICK_OEM = Color.parseColor("#F6EDB4") 
    private val TICK_RED = Color.parseColor("#FF0F1F") 
    private val BLUE_GLOW = Color.parseColor("#3A2CE8")
    private val CYAN_HUD = Color.parseColor("#00C3FF")
    private val LCD_BG = Color.parseColor("#6E706F")
    private val LCD_TEXT = Color.parseColor("#E1E6F0")
    private val OFF_SEGMENT = Color.parseColor("#13161C")
    private val GREEN = Color.parseColor("#11E840")
    private val AMBER = Color.parseColor("#FFA600")

    private var BG = BG_NIGHT

    // ---------- Stan telemetrii ----------
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

    private fun midKey(entry: String): String {
        if (entry.startsWith("CHART:")) {
            return entry.substring(6)
        }
        return entry
    }

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

    // ---------- Geometria Główna ----------
    private val CX = 640f
    private val CY = 352f // wyżej, żeby dół obrotomierza nie dotykał belki ze stopką (od y=640)
    
    // Scentrowany wyświetlacz LCD (Bezpieczny margines od cyfr obrotomierza)
    private val LCD_L = 530f
    private val LCD_T = CY - 110f
    private val LCD_R = 750f
    private val LCD_B = CY + 110f
    private val lcdShader = LinearGradient(0f, LCD_T, 0f, LCD_B,
        0xFF7C7E7D.toInt(), 0xFF626564.toInt(), Shader.TileMode.CLAMP)
    // Wyświetlacz MID: górne rogi ścięte pod 45 stopni jak w oryginale
    private val lcdPath = Path().apply {
        val ch = 22f
        moveTo(LCD_L + ch, LCD_T)
        lineTo(LCD_R - ch, LCD_T)
        lineTo(LCD_R, LCD_T + ch)
        lineTo(LCD_R, LCD_B)
        lineTo(LCD_L, LCD_B)
        lineTo(LCD_L, LCD_T + ch)
        close()
    }

    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private var staticLayer: Bitmap? = null

    // ---------- Painty ----------
    private val pFill = Paint()
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Zwężona (condensed) czcionka pochylona - idealna imitacja paneli VFD z UFO
    private val pSpeed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD) // ta sama czcionka co cyfry tarczy
        textSkewX = -0.2f // to samo pochylenie co cyfry tarczy
    }
    
    private val pNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSkewX = -0.2f // lekkie pochylenie jak na oryginalnych zegarach
    }
    
    private val pMono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    
    private val rectF = RectF()
    
    // Promień łuków bocznych (współśrodkowy z głównym zegarem - likwiduje pokrzywienia)
    private val sideGaugeRadius = 372f // współśrodkowo z tarczą, jak w oryginale 
    private val outerRect = RectF(CX - sideGaugeRadius, CY - sideGaugeRadius, CX + sideGaugeRadius, CY + sideGaugeRadius)
    
    private val needlePath = Path()
    private val iconPath = Path()
    private var lastFrameMs = 0L

    private val tfBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    // Poświata HUD - skompresowana na osi Y
    private val hudGlowShader = RadialGradient(640f, 90f, 250f,
        intArrayOf(0xBB142BFF.toInt(), 0x330011AA, 0x00000000),
        floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)

    // Połysk szyby zegarów (zero alokacji w onDraw)
    private val glassShader = LinearGradient(0f, 0f, 1280f, 640f,
        intArrayOf(0x00FFFFFF, 0x16FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x0CFFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.18f, 0.34f, 0.62f, 0.78f, 1f), Shader.TileMode.CLAMP)
    private var bootStartMs = 0L
    private val combCount = 30

    // Podziałka bocznych wskaźników: ta sama hierarchia co w obrotomierzu (duże/średnie/drobne kreski),
    // wszystkie kończą się na wspólnej krawędzi zewnętrznej, pas jest węższy
    private fun combTick(c: Canvas, left: Boolean, i: Int, p: Paint) {
        val per = 34f / combCount
        val deg = if (left) 163f + i * per else 17f - i * per
        val a = Math.toRadians(deg.toDouble())
        val major = i % 10 == 0
        val mid = i % 5 == 0
        val len = when { major -> 20f; mid -> 14f; else -> 8f }
        p.strokeWidth = when { major -> 4f; mid -> 3f; else -> 1.6f }
        val rOut = sideGaugeRadius + 10f
        val r1 = rOut - len
        c.drawLine((CX + r1 * cos(a)).toFloat(), (CY + r1 * sin(a)).toFloat(),
            (CX + rOut * cos(a)).toFloat(), (CY + rOut * sin(a)).toFloat(), p)
    }

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

    // ================= API PUBLICZNE ================= //

    fun update(s: VehicleUiState) {
        val off = s.engineState == EngineStateLabel.OFF
        if (off != engineOff) {
            engineOff = off
            if (off) { 
                savedLeftIndex = leftIndex
                savedRightIndex = rightIndex 
            } else { 
                leftIndex = savedLeftIndex
                rightIndex = savedRightIndex 
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
        
        if (s.connected) {
            lastDataMs = System.currentTimeMillis()
        }
        
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
        if (width > 0 && height > 0) {
            buildStaticLayer()
        }
        postInvalidateOnAnimation()
    }

    fun setArcParam(left: Boolean, key: String) {
        if (engineOff) {
            if (left) {
                offLeftParam = key 
            } else {
                offRightParam = key
            }
        } else {
            if (left) {
                leftIndex = maxOf(0, leftLoop.indexOf(key))
            } else {
                rightIndex = maxOf(0, rightLoop.indexOf(key))
            }
        }
        postInvalidateOnAnimation()
    }

    // ================= LOGIKA DANYCH ================= //

    private fun paramValue(key: String): Float {
        return when (key) {
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
    }

    private fun paramUnit(key: String): String {
        return when (key) {
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
    }

    private fun paramInterval(key: String): String {
        return when (key) {
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
    }

    private fun trackMidStats() {
        val key = midKey(midLoop[midIndex])
        val value = paramValue(key)
        
        if (value.isNaN()) {
            return
        }
        
        val mx = midMax[key]
        if (mx == null || value > mx) {
            midMax[key] = value
        }
        
        midAvgSum[key] = (midAvgSum[key] ?: 0f) + value
        midAvgCount[key] = (midAvgCount[key] ?: 0f) + 1f
    }

    private var tripFuelL = 0.0
    private var tripDistKm = 0.0
    private var lastTripMs = 0L

    private fun accumulateTrip(now: Long) {
        if (lastTripMs > 0L && now > lastTripMs) {
            val dtH = (now - lastTripMs) / 3_600_000.0
            if (speedKmh >= 0) {
                tripDistKm += speedKmh * dtH
            }
            if (!mafGs.isNaN() && mafGs > 0.5) {
                tripFuelL += (mafGs / AFR_GAS / FUEL_DENSITY_G_PER_L) * dtH
            }
        }
        lastTripMs = now
    }

    private fun avgTripFuelL100(): Float {
        if (tripDistKm < 0.2) {
            return Float.NaN
        }
        return (tripFuelL / tripDistKm * 100.0).toFloat()
    }

    // ================= CYKL ŻYCIA WIDOKU ================= //

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        scale = minOf(w / 1280f, h / 720f)
        offX = (w - 1280f * scale) / 2f
        offY = (h - 720f * scale) / 2f
        buildStaticLayer()
    }

    private fun buildStaticLayer() {
        val bmpWidth = (width * scale).toInt().coerceAtLeast(1)
        val bmpHeight = (height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
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
        
        // Płynna interpolacja wskazówki
        val alpha = 1f - Math.pow(0.001, (dt / 1000f * 6f).toDouble()).toFloat()
        if (bootStartMs == 0L) bootStartMs = now
        val bootT = now - bootStartMs
        val booting = bootT < 1600L
        if (booting) {
            // Rozruch jak w oryginale: wskazówka wybiega do 8000 i wraca
            rpm = 8000f * Math.sin(bootT / 1600.0 * Math.PI).toFloat()
        } else {
            rpm += alpha * (rpmTarget - rpm)
        }

        c.drawColor(BG)
        c.save()
        c.translate(offX, offY)
        c.scale(scale, scale)
        
        staticLayer?.let { 
            c.drawBitmap(it, 0f, 0f, pFill) 
        }
        
        drawDynamic(c)
        c.restore()
        
        if (booting || connected || connecting || Math.abs(rpm - rpmTarget) > 1f) {
            postInvalidateOnAnimation()
        } else {
            lastFrameMs = 0L
        }
    }

    // ---------- WARSTWA STATYCZNA (Rysowana raz) ----------

    private fun drawStatic(c: Canvas) {
        // Geometria tarczy (od zewnątrz): pierścień LED -> podziałki -> cyfry -> wskazówka
        val a0 = Math.toRadians(135.0)      // 0 RPM: lewy dół
        val sweep = Math.toRadians(270.0)   // 8000 RPM: prawy dół
        val ringR = 258f                    // błękitny pierścień
        val tickOut = 244f                  // zewnętrzny koniec wszystkich kresek
        val numR = 196f                     // promień środka cyfr

        // Tło obudowy
        pFill.color = Color.parseColor("#050608")
        c.drawCircle(CX, CY, ringR + 12f, pFill)

        // Poświata tarczy: wychodzi spod czarnego okręgu i podświetla cyfry
        val discR = 170f
        pFill.shader = RadialGradient(CX, CY, ringR,
            intArrayOf(0x00000000, 0x00000000, 0xEE3A2CC8.toInt(), 0xAA2A1CA0.toInt(), 0x441A1068, 0x66262AD0),
            floatArrayOf(0f, 0.62f, 0.67f, 0.78f, 0.90f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(CX, CY, ringR, pFill)
        pFill.shader = null

        // Czarny matowy okrąg, na którym leży wyświetlacz MID
        pFill.color = Color.parseColor("#040508")
        c.drawCircle(CX, CY, discR, pFill)
        pStroke.style = Paint.Style.STROKE
        pStroke.strokeWidth = 3f
        pStroke.color = Color.parseColor("#14152A")
        c.drawCircle(CX, CY, discR, pStroke)

        // 1. Matryca LCD
        pStroke.style = Paint.Style.FILL
        pFill.color = LCD_BG
        pFill.shader = lcdShader
        c.drawPath(lcdPath, pFill)
        pFill.shader = null

        pStroke.style = Paint.Style.STROKE
        pStroke.strokeWidth = 9f
        pStroke.color = Color.parseColor("#1A1E26")
        c.drawPath(lcdPath, pStroke)
        pStroke.strokeWidth = 4f
        pStroke.color = Color.parseColor("#030406")
        c.drawPath(lcdPath, pStroke)

        // 2. Błękitny pierścień LED na samym zewnątrzu
        pStroke.color = BLUE_GLOW
        pStroke.strokeWidth = 18f
        pStroke.setShadowLayer(26f, 0f, 0f, BLUE_GLOW)
        c.drawCircle(CX, CY, ringR, pStroke)

        pStroke.strokeWidth = 8f
        pStroke.setShadowLayer(12f, 0f, 0f, BLUE_GLOW)
        c.drawCircle(CX, CY, ringR, pStroke)

        pStroke.strokeWidth = 2.5f
        pStroke.color = Color.parseColor("#88AAFF")
        pStroke.setShadowLayer(4f, 0f, 0f, Color.WHITE)
        c.drawCircle(CX, CY, ringR, pStroke)
        pStroke.clearShadowLayer()

        // 3. Czerwone pole (6500-8000) - pasek na promieniu podziałek
        val redStartAng = 135f + 270f * (6500f / 8000f)
        val redSweepAng = 270f * (1500f / 8000f)
        val redBandW = 22f
        pStroke.strokeWidth = redBandW
        pStroke.color = TICK_RED
        pStroke.setShadowLayer(14f, 0f, 0f, TICK_RED)
        val redR = tickOut - redBandW / 2f
        c.drawArc(RectF(CX - redR, CY - redR, CX + redR, CY + redR), redStartAng, redSweepAng, false, pStroke)
        pStroke.clearShadowLayer()

        // 4. Podziałki: wszystkie kończą się na tickOut, rosną do środka
        for (r in 0..8000 step 100) {
            val ang = a0 + sweep * (r / 8000f)
            val major = r % 1000 == 0
            val mid = r % 500 == 0
            val isRed = r >= 6500

            val len = when { major -> 22f; mid -> 15f; else -> 9f }
            val w = when { major -> 4.5f; mid -> 3f; else -> 1.6f }

            pStroke.strokeWidth = w
            val tickColor = if (isRed) Color.parseColor("#400006") else TICK_OEM
            pStroke.color = tickColor
            if (!isRed) pStroke.setShadowLayer(8f, 0f, 0f, TICK_OEM)

            val rOuter = tickOut
            val rInner = tickOut - len
            c.drawLine(
                (CX + rInner * cos(ang)).toFloat(), (CY + rInner * sin(ang)).toFloat(),
                (CX + rOuter * cos(ang)).toFloat(), (CY + rOuter * sin(ang)).toFloat(), pStroke
            )
            pStroke.clearShadowLayer()
        }

        // 5. Cyfry 0-8 w środku, pod kreskami
        pNum.textSize = 46f
        pNum.color = TICK_OEM
        pNum.textAlign = Paint.Align.CENTER
        for (r in 0..8000 step 1000) {
            val ang = a0 + sweep * (r / 8000f)
            pNum.setShadowLayer(10f, 0f, 0f, TICK_OEM)
            c.drawText((r / 1000).toString(),
                (CX + numR * cos(ang)).toFloat(),
                (CY + numR * sin(ang)).toFloat() + 15f, pNum)
            pNum.clearShadowLayer()
        }

        // Napis jednostki pod tarczą
        pNum.textSize = 22f
        pNum.setShadowLayer(8f, 0f, 0f, TICK_OEM)
        c.drawText("x1000r/min", CX + 10f, CY + 230f, pNum)
        pNum.clearShadowLayer()

        // 6. Boczne wskaźniki: niebieski łuk obrysu + zgaszony grzebień kresek
        pStroke.style = Paint.Style.STROKE
        val outlineRect = RectF(CX - sideGaugeRadius - 24f, CY - sideGaugeRadius - 24f,
            CX + sideGaugeRadius + 24f, CY + sideGaugeRadius + 24f)
        pStroke.strokeWidth = 4f
        pStroke.color = BLUE_GLOW
        pStroke.setShadowLayer(10f, 0f, 0f, BLUE_GLOW)
        c.drawArc(outlineRect, 161f, 38f, false, pStroke)
        c.drawArc(outlineRect, 19f, -38f, false, pStroke)
        pStroke.clearShadowLayer()

        pStroke.strokeWidth = 3.2f
        pStroke.strokeCap = Paint.Cap.BUTT
        for (i in 0..combCount) {
            // lewy (temperatura): czerwień na górze, prawy (paliwo): czerwień na dole
            pStroke.color = if (i >= combCount - 3) TICK_RED else TICK_OEM
            pStroke.setShadowLayer(7f, 0f, 0f, pStroke.color)
            combTick(c, true, i, pStroke)
            pStroke.color = if (i <= 3) TICK_RED else TICK_OEM
            pStroke.setShadowLayer(7f, 0f, 0f, pStroke.color)
            combTick(c, false, i, pStroke)
        }
        pStroke.clearShadowLayer()
        pStroke.style = Paint.Style.FILL
    }

    // ---------- WARSTWA DYNAMICZNA (Co klatkę) ----------

    private fun drawDynamic(c: Canvas) {
        drawHud(c)
        drawEReport(c)
        drawNeedle(c)
        drawLcdMid(c)
        drawWingArc(c, left = true)
        drawWingArc(c, left = false)
        drawFooter(c)
        pFill.shader = glassShader
        c.drawRect(0f, 0f, 1280f, 640f, pFill)
        pFill.shader = null
    }

    private fun drawHud(c: Canvas) {
        if (speedKmh != spdCacheSrc || useMph != mphCache) {
            spdCacheSrc = speedKmh
            mphCache = useMph
            
            spdCache = if (speedKmh < 0) {
                "---"
            } else {
                if (useMph) (speedKmh * 0.621371f).toInt().toString() else speedKmh.toString()
            }
        }
        
        // Prędkość: na górze, nad obrotomierzem (między paskiem diod a pierścieniem)
        pSpeed.textSize = 40f
        pSpeed.color = Color.WHITE
        pSpeed.textAlign = Paint.Align.CENTER
        pSpeed.setShadowLayer(14f, 0f, 0f, CYAN_HUD)
        c.drawText(spdCache, 640f, 78f, pSpeed)
        pSpeed.clearShadowLayer()

        pSpeed.textSize = 16f
        pSpeed.textAlign = Paint.Align.LEFT
        c.drawText(if (useMph) "mph" else "km/h", 690f, 78f, pSpeed)

        val gear = if (speedKmh > 3 && rpmTarget > 500f) {
            val r = rpmTarget / speedKmh
            when { 
                r < 35f -> "5"
                r < 44f -> "4"
                r < 60f -> "3"
                r < 100f -> "2"
                else -> "1" 
            }
        } else if (speedKmh in 0..3) {
            "N"
        } else {
            "-"
        }
        pSpeed.color = Color.GRAY
        pSpeed.textSize = 30f
        pSpeed.textAlign = Paint.Align.RIGHT
        c.drawText(gear, 590f, 78f, pSpeed)
        pSpeed.textAlign = Paint.Align.CENTER

        val segW = 18f
        val now = System.currentTimeMillis()
        
        for (i in 0 until 10) {
            val x = 330f + i * (segW + 8f)
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
            if (lit) {
                pFill.setShadowLayer(10f, 0f, 0f, baseColor)
            } else {
                pFill.clearShadowLayer()
            }
            
            c.drawRoundRect(x, 10f, x + segW, 24f, 4f, 4f, pFill)
        }
        pFill.clearShadowLayer()

        val eco = !tpsPct.isNaN() && tpsPct < ecoTps && !mapKpa.isNaN() && mapKpa < ecoMap && rpm < ecoRpm
        
        pNum.textSize = 24f
        pNum.color = if (eco) GREEN else OFF_SEGMENT
        if (eco) pNum.setShadowLayer(10f, 0f, 0f, GREEN)
        c.drawText("ECO", 270f, 90f, pNum)
        pNum.clearShadowLayer()
        
        val vtec = rpm >= vtecRpm
        pNum.color = if (vtec) CYAN_HUD else OFF_SEGMENT
        if (vtec) pNum.setShadowLayer(10f, 0f, 0f, CYAN_HUD)
        c.drawText("VTEC", 1010f, 90f, pNum)
        pNum.clearShadowLayer()
    }

    private fun drawEReport(c: Canvas) {
        if (!engineOff) {
            return
        }
        
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
        val a0 = Math.toRadians(135.0)
        val sweep = Math.toRadians(270.0)
        val frac = (rpm / 8000f).coerceIn(0f, 1f)
        val ang = a0 + sweep * frac
        
        val isRed = rpm >= redlineRpm
        val glowColor = if (isRed) TICK_RED else Color.parseColor("#FF7A00") // Intensywny pomarańcz z UFO

        drawWedge(c, ang, 168f, 248f, 15f, 2f, 14f, glowColor)
    }

    // Wspólna wskazówka (obrotomierz i boczne wskaźniki): obrys, wnętrze prawie niepodświetlone
    private fun drawWedge(c: Canvas, ang: Double, rInner: Float, rOuter: Float,
                          wBase: Float, wTip: Float, cut: Float, glowColor: Int) {
        val px = cos(ang).toFloat()
        val py = sin(ang).toFloat()
        val nx = -py
        val ny = px

        needlePath.reset()
        needlePath.moveTo(CX + px * rInner - nx * wBase, CY + py * rInner - ny * wBase)
        needlePath.lineTo(CX + px * (rInner + cut) + nx * wBase, CY + py * (rInner + cut) + ny * wBase)
        needlePath.lineTo(CX + px * rOuter + nx * wTip, CY + py * rOuter + ny * wTip)
        needlePath.lineTo(CX + px * rOuter - nx * wTip, CY + py * rOuter - ny * wTip)
        needlePath.close()

        pFill.color = 0x1AF6EDB4
        c.drawPath(needlePath, pFill)

        pStroke.style = Paint.Style.STROKE
        pStroke.strokeJoin = Paint.Join.MITER
        pStroke.strokeWidth = 4f
        pStroke.color = glowColor
        pStroke.setShadowLayer(18f, 0f, 0f, glowColor)
        c.drawPath(needlePath, pStroke)
        pStroke.clearShadowLayer()

        pStroke.strokeWidth = 2.4f
        pStroke.color = Color.parseColor("#FFF6D0")
        pStroke.setShadowLayer(6f, 0f, 0f, Color.WHITE)
        c.drawPath(needlePath, pStroke)
        pStroke.clearShadowLayer()
        pStroke.style = Paint.Style.FILL
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
                for (k in 0 until 3) {
                    c.drawLine(x + 3f, y + 6f + k * 6f, x + 21f, y + 6f + k * 6f, pStroke)
                } 
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
        val ink = if (have) Color.parseColor("#F4F3EA") else Color.parseColor("#9DA09E")
        val lineY = LCD_T + 120f

        // --- Część nad kreską: ikona, nazwa parametru, ewentualnie wykres ---
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
            c.drawText("WYKRES 30 S", LCD_R - 24f, LCD_T + 30f, pText)
            drawMidChart(c, key, have, ink)
        }

        // --- Pozioma kreska oddzielająca ---
        pStroke.style = Paint.Style.STROKE
        pStroke.strokeWidth = 2.5f
        pStroke.color = if (have) Color.parseColor("#E8E8E0") else Color.parseColor("#9DA09E")
        c.drawLine(LCD_L + 10f, lineY, LCD_R - 10f, lineY, pStroke)
        pStroke.style = Paint.Style.FILL

        // --- Pod kreską, rząd 1: mała jednostka z lewej, duża wartość z prawej ---
        if (v != midMainSrc || key != midKeyCache) {
            midMainSrc = v
            midKeyCache = key
            midMainCache = if (!have) "---" else fmtParam(key, v)
        }

        pText.textAlign = Paint.Align.LEFT
        pText.textSize = 18f
        pText.typeface = tfBold
        pText.color = ink
        c.drawText(paramUnit(key), LCD_L + 14f, LCD_T + 164f, pText)

        pMono.textSize = 46f
        pMono.color = ink
        pMono.textAlign = Paint.Align.RIGHT
        c.drawText(midMainCache, LCD_R - 12f, LCD_T + 164f, pMono)

        // --- Statystyki ---
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

            if (avgSum == null || avgCnt == null || avgCnt <= 0f) {
                avg = null
            } else {
                avg = avgSum / avgCnt
            }
        }

        if (key != statKey || have != statHave || mx != statMaxSrc || avg != statAvgSrc) {
            statKey = key
            statHave = have
            statMaxSrc = mx
            statAvgSrc = avg

            statMaxCache = if (have && mx != null) "MAX: " + fmtParam(key, mx) else "MAX: N/A"
            statAvgCache = if (have && avg != null) "AVG: " + fmtParam(key, avg) else "AVG: N/A"
        }

        // --- Pod kreską, rząd 2: jasne pole z interwałem + MAX i AVG ---
        val boxTop = LCD_T + 176f
        val boxBot = LCD_T + 204f
        val baseY = boxBot - 8f

        pText.textSize = 15f
        pText.typeface = tfBold
        pText.textAlign = Paint.Align.LEFT
        val intr = paramInterval(key)
        val bw = pText.measureText(intr) + 14f
        pFill.color = if (have) Color.parseColor("#E6E3D2") else Color.parseColor("#9DA09E")
        c.drawRoundRect(LCD_L + 10f, boxTop, LCD_L + 10f + bw, boxBot, 4f, 4f, pFill)
        pText.color = Color.parseColor("#1A1D20")
        c.drawText(intr, LCD_L + 17f, baseY, pText)

        pText.color = ink
        pText.textAlign = Paint.Align.RIGHT
        c.drawText(statAvgCache, LCD_R - 12f, baseY, pText)
        val avgW = pText.measureText(statAvgCache)
        c.drawText(statMaxCache, LCD_R - 12f - avgW - 10f, baseY, pText)
    }

    private fun drawWingArc(c: Canvas, left: Boolean) {
        val key = if (engineOff) {
            if (left) offLeftParam else offRightParam
        } else {
            if (left) leftLoop[leftIndex] else rightLoop[rightIndex]
        }
        
        val v = paramValue(key)
        val alarm = arcAlarm(key, v)
        val frac = arcFrac(key, v)

        if (!v.isNaN() && frac > 0f) {
            // Wskazówka identyczna jak w obrotomierzu (obrys z niepodświetlonym wnętrzem), tylko mniejsza
            val deg = if (left) 163f + frac * 34f else 17f - frac * 34f
            drawWedge(c, Math.toRadians(deg.toDouble()),
                sideGaugeRadius - 14f, sideGaugeRadius + 16f, 9f, 1.5f, 8f,
                if (alarm) TICK_RED else Color.parseColor("#FF7A00"))
        }
        pStroke.style = Paint.Style.FILL

        // Podpisy po zewnętrznej stronie łuku, wyśrodkowane na środku łuku
        val labelX = if (left) 118f else 1162f

        if (v != (if (left) arcTxtSrcL else arcTxtSrcR) || key != (if (left) arcTxtKeyL else arcTxtKeyR)) {
            if (left) { 
                arcTxtSrcL = v
                arcTxtKeyL = key
                arcTxtCacheL = arcText(key, v) 
            } else { 
                arcTxtSrcR = v
                arcTxtKeyR = key
                arcTxtCacheR = arcText(key, v) 
            }
        }

        pText.textAlign = Paint.Align.CENTER
        pText.textSkewX = -0.2f // ta sama czcionka co cyfry tarczy
        pText.typeface = tfBold

        pText.textSize = 24f
        pText.color = if (alarm) TICK_RED else Color.parseColor("#B9AE7A")
        c.drawText(key, labelX, CY - 8f, pText)

        pText.textSize = 32f
        val valColor = if (alarm) TICK_RED else Color.parseColor("#FFF6D0")
        pText.color = valColor
        pText.setShadowLayer(8f, 0f, 0f, if (alarm) TICK_RED else TICK_OEM)
        c.drawText(if (left) arcTxtCacheL else arcTxtCacheR, labelX, CY + 30f, pText)
        pText.clearShadowLayer()
        pText.textSkewX = 0f
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
        c.drawText(if (milOn) "CHECK ENGINE" else if (dtcCount > 0) "DTC: " + dtcCount else "CHECK ENGINE OK", 128f, 686f, pText)
        
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
            voltText = if (voltage.isNaN()) {
                "--- V" 
            } else {
                String.format("%.1f V", voltage)
            }
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
                (cx + 10f * cos(a)).toFloat(), 
                (cy + 12f + 10f * sin(a)).toFloat(), 
                (cx + 15f * cos(a)).toFloat(), 
                (cy + 12f + 15f * sin(a)).toFloat(), 
                pStroke
            )
        }
        pStroke.style = Paint.Style.FILL
    }

    // ---------- LOGIKA WYKRESÓW ORAZ WBUDOWANY RING BUFFER ----------
    
    private val HIST_CAP = 128
    private val HIST_MIN_DT_MS = 250L
    
    private fun pushAllHistory() { 
        val now = System.currentTimeMillis()
        for (k in CHART_KEYS) { 
            val v = paramValue(k)
            pushHistory(k, v, now)
            
            if (statWindowMs(k) > 0L) {
                pushWinSample(k, v, now)
            }
        } 
    }
    
    private fun statWindowMs(key: String): Long {
        return when (key) { 
            "BOOST", "MAF" -> 300_000L
            "VOLTS", "BARO" -> 3_600_000L
            else -> 0L 
        }
    }
    
    private val winTs = HashMap<String, LongArray>()
    private val winVals = HashMap<String, FloatArray>()
    private val winLen = HashMap<String, Int>()
    private val winHead = HashMap<String, Int>()
    private val winMax = HashMap<String, Float?>()
    private val winAvg = HashMap<String, Float?>()
    private var winCalcMs = 0L
    
    private fun pushWinSample(key: String, v: Float, now: Long) { 
        if (v.isNaN()) {
            return
        }
        
        val winMs = statWindowMs(key)
        if (winMs <= 0L) {
            return
        }
        
        val cap = if (winMs >= 3_600_000L) {
            14_400 
        } else {
            1_200 
        }
        
        var ts = winTs[key]
        var vs = winVals[key]
        
        if (ts == null || vs == null || ts.size != cap) { 
            ts = LongArray(cap)
            vs = FloatArray(cap)
            winTs[key] = ts
            winVals[key] = vs
            winLen[key] = 0
            winHead[key] = 0 
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
            ts[idx] = now
            vs[idx] = v
            winLen[key] = len + 1 
        } else { 
            ts[head] = now
            vs[head] = v
            winHead[key] = (head + 1) % cap 
        } 
    }
    
    private fun refreshWinStats(key: String, winMs: Long) { 
        val now = System.currentTimeMillis()
        if (now - winCalcMs < 250L && winMax.containsKey(key)) {
            return
        }
        
        winCalcMs = now
        val ts = winTs[key]
        val vs = winVals[key]
        val len = winLen[key] ?: 0
        val head = winHead[key] ?: 0
        
        if (ts == null || vs == null || len == 0) { 
            winMax[key] = null
            winAvg[key] = null
            return 
        }
        
        val cap = ts.size
        var mx = -Float.MAX_VALUE
        var sum = 0.0
        var cnt = 0
        
        for (i in 0 until len) { 
            val idx = (head + i) % cap
            if (now - ts[idx] > winMs) {
                continue
            }
            val v = vs[idx]
            if (v > mx) {
                mx = v
            }
            sum += v
            cnt++ 
        }
        
        if (cnt == 0) {
            winMax[key] = null
            winAvg[key] = null
        } else {
            winMax[key] = mx
            winAvg[key] = (sum / cnt).toFloat()
        }
    }
    
    private fun pushHistory(key: String, v: Float, now: Long) { 
        if (v.isNaN()) {
            return
        }
        
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
            ts[idx] = now
            vs[idx] = v
            histLen[key] = len + 1 
        } else { 
            ts[head] = now
            vs[head] = v
            histHead[key] = (head + 1) % HIST_CAP 
        } 
    }
    
    private fun drawMidChart(c: Canvas, key: String, have: Boolean, ink: Int) {
        val len = histLen[key] ?: 0
        if (len < 2 || !have) { 
            pText.textAlign = Paint.Align.CENTER
            pText.textSize = 20f
            pText.typeface = tfBold
            pText.color = Color.parseColor("#C9CBC7")
            
            c.drawText(
                if (have) "ZBIERAM DANE..." else "BRAK DANYCH", 
                640f, 
                LCD_T + 80f, 
                pText
            )
            return 
        }
        
        val head = histHead[key] ?: 0
        val ts = histTs[key] ?: return
        val vs = histVals[key] ?: return
        val now = System.currentTimeMillis()
        val winMs = 30_000L
        
        val x0 = LCD_L + 10f
        val x1 = LCD_R - 10f
        val yBase = LCD_T + 112f
        val yTop = LCD_T + 40f
        
        var vmin = Float.MAX_VALUE
        var vmax = -Float.MAX_VALUE
        
        for (i in 0 until len) { 
            val v = vs[(head + i) % HIST_CAP]
            if (v < vmin) vmin = v
            if (v > vmax) vmax = v 
        }
        
        if (vmax - vmin < 1e-4f) { 
            vmax += 0.5f
            vmin -= 0.5f 
        }
        
        val pad = (vmax - vmin) * 0.1f
        vmax += pad
        vmin -= pad
        
        val block = 4f
        pFill.color = (ink and 0x00FFFFFF) or 0x30000000
        
        for (i in 0 until len) { 
            val idx = (head + i) % HIST_CAP
            val age = now - ts[idx]
            
            if (age > winMs) {
                continue
            }
            
            val x = x0 + (x1 - x0) * (1f - age.toFloat() / winMs)
            val hRaw = (yBase - yTop) * ((vs[idx] - vmin) / (vmax - vmin))
            val h = (Math.round(hRaw / block) * block.toInt()).toFloat()
            
            if (h > 0f) {
                c.drawRect(x - block / 2f, yBase - h, x + block / 2f, yBase, pFill)
            }
        }
        
        needlePath.reset()
        var px = 0f
        var py = 0f
        var started = false
        
        for (i in 0 until len) { 
            val idx = (head + i) % HIST_CAP
            val age = now - ts[idx]
            
            if (age > winMs) {
                continue
            }
            
            val x = x0 + (x1 - x0) * (1f - age.toFloat() / winMs)
            val y = yBase - (yBase - yTop) * ((vs[idx] - vmin) / (vmax - vmin))
            
            if (!started) { 
                needlePath.moveTo(x, y)
                started = true 
            } else { 
                needlePath.quadTo(px, py, (px + x) / 2f, (py + y) / 2f) 
            }
            
            px = x
            py = y 
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

    // ---------- KALKULACJE I POMOCNICZE WIDOKI ----------

    private fun arcText(key: String, v: Float): String {
        if (v.isNaN()) {
            return "---"
        }
        return fmtParam(key, v) + " " + paramUnit(key)
    }
    
    private fun fmtParam(key: String, v: Float): String {
        return when (key) { 
            "BOOST" -> String.format("%.2f", v)
            "FUEL", "AVG_TRIP_FUEL" -> String.format("%.1f", v)
            else -> if (v >= 100f || v == v.toInt().toFloat()) {
                v.toInt().toString() 
            } else {
                String.format("%.1f", v)
            }
        }
    }
    
    private fun arcFrac(key: String, v: Float): Float {
        return when (key) { 
            "ECT" -> ((v - 40f) / 80f).coerceIn(0f, 1f)
            "BOOST" -> if (v.isNaN()) 0.5f else (0.5f + (v / 1.2f) * 0.5f).coerceIn(0f, 1f)
            "VOLTS" -> ((v - 11f) / 4f).coerceIn(0f, 1f)
            "FUEL" -> ((v - 10f) / 9f).coerceIn(0f, 1f)
            "TPS" -> (v / 100f).coerceIn(0f, 1f)
            "IAT" -> ((v + 20f) / 90f).coerceIn(0f, 1f)
            "LTFT", "STFT" -> ((v + 15f) / 30f).coerceIn(0f, 1f)
            "MAF" -> (v / 80f).coerceIn(0f, 1f)
            "BARO" -> ((v - 60f) / 45f).coerceIn(0f, 1f)
            "AVG_TRIP_FUEL" -> (v / 20f).coerceIn(0f, 1f)
            else -> 0.5f 
        }
    }
    
    private fun arcAlarm(key: String, v: Float): Boolean {
        return when (key) { 
            "ECT" -> !v.isNaN() && v > ectMaxC
            "VOLTS" -> !v.isNaN() && (v < voltMin || v > voltMax)
            "LTFT" -> !v.isNaN() && (v > ltftMax || v < -ltftMax)
            "IAT" -> !v.isNaN() && v > iatMax
            "BOOST" -> !v.isNaN() && v > 1.0f
            "FUEL" -> !v.isNaN() && (v < 12f || v > 16f)
            else -> false 
        }
    }

    // ---------- OBSŁUGA GESTÓW DOTYKOWYCH ----------

    private var downX = 0f
    private var downY = 0f
    private var downMs = 0L
    
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = (e.x - offX) / scale
        val y = (e.y - offY) / scale
        
        when (e.action) { 
            MotionEvent.ACTION_DOWN -> { 
                downX = x
                downY = y
                downMs = e.downTime
                return true 
            }
            MotionEvent.ACTION_UP -> Unit
            else -> return true 
        }
        
        val holdMs = e.eventTime - downMs
        
        if (engineOff && x in 48f..268f && y in 56f..104f) { 
            onOpenEReport()
            return true 
        }
        
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
        
        // Zabezpieczony obszar LCD MID - strefa w centrum ekranu
        if (x in LCD_L..LCD_R && y in LCD_T..LCD_B) { 
            if (y > 450f && holdMs > 1500) { 
                val key = midKey(midLoop[midIndex])
                midMax.remove(key)
                midAvgSum.remove(key)
                midAvgCount.remove(key)
                winLen[key] = 0
                winHead[key] = 0
                winMax[key] = null
                winAvg[key] = null 
            } else { 
                midIndex = (midIndex + 1) % midLoop.size 
            }
            postInvalidateOnAnimation()
            return true 
        }
        
        // Obszar na zewnątrz centrum (bezpieczne maski dla łuków)
        if (y in 260f..580f) { 
            // Lewa strona
            if (x < CX - 200f) { 
                if (holdMs > 1000) {
                    onArcAssign(true)
                } else { 
                    leftIndex = (leftIndex + 1) % leftLoop.size
                    postInvalidateOnAnimation() 
                }
                return true 
            }
            // Prawa strona
            if (x > CX + 200f) { 
                if (holdMs > 1000) {
                    onArcAssign(false)
                } else { 
                    rightIndex = (rightIndex + 1) % rightLoop.size
                    postInvalidateOnAnimation() 
                }
                return true 
            } 
        }
        
        // Obszar HUD (Prędkościomierz)
        if (y < 160f && x in 400f..680f) { 
            onSpeedTap()
            return true 
        }
        
        return true
    }
}
