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
import pl.dock.widget.repository.EngineStateLabel
import pl.dock.widget.repository.VehicleUiState

/**
 * Honda OEM UFO Cluster - pelna implementacja wg specyfikacji 1280x720
 * (dokument "ui obd.txt", CZESC II).
 *
 * Warstwa statyczna (tlo, ramki, podzialki, cyfry) jest cache'owana w
 * Bitmap i przerysowywana tylko przy zmianie rozmiaru. Warstwa dynamiczna
 * (wskazowka, HUD, LCD, luki, footer) nie alokuje obiektow w onDraw.
 *
 * Zgodnosc ze specyfikacja (CZESC II):
 *  - tarcza 440 px, srodek (640, 400), podzialki 100/500/1000 RPM,
 *    cyfry 1-8 pochylone (bez rotacji radialnej);
 *  - LCD MID 220x220 w (530, 290): ikona 24x24 + nazwa 12sp bold,
 *    wartosc glowna 38sp Monospace Bold + jednostka 14sp, separator
 *    2 px na Y=400, jasny panel statystyk 204x96 z etykieta interwalu
 *    (np. "C / TRIP"), MAX #0055FF, AVG czarny;
 *  - petla MID: BOOST -> ECT -> VOLTAGE -> TPS -> IAT -> LTFT;
 *  - HUD: predkosc 96sp Bold Italic bez zer wiodacych, pasek REV
 *    10 segmentow (1-4 blekitne, 5-8 zielone, 9-10 bursztynowe),
 *    >= 6300 caly pasek pulsuje czerwono, ECO/VTEC wg progow z Ustawien,
 *    soczewka #0022AA 25% pod predkoscia;
 *  - biezny wskaznik jest WYLICZANY (RPM/km/h) - rysowany przygaszonym
 *    (spec: zgadana wartosc nie moze wygladac jak pewny pomiar);
 *  - luki boczne 180x480, grubosc 8 dp, SweepGradient, bez podzialek,
 *    wartosc + etykieta po stronie srodka ekranu, alarm #FF0033;
 *    lewy: ECT -> BOOST -> VOLTS, prawy: FUEL -> TPS -> IAT -> LTFT;
 *  - footer 80 px, 5 sektorow od lewej: Check Engine/DTC, ZAPISZ EVENT,
 *    SWAP UI, zebatka (ustawienia), status OBD + napiecie;
 *  - brak danych: "---", MAX/AVG "N/A", piksele LCD przygaszone.
 *
 * Progi alarmow (ECT/VOLTS/LTFT/IAT) sa konfigurowalne z Ustawien OBD
 * (prefs alarm_*), tak jak exige pkt 10.7 specyfikacji.
 *
 * Motyw dzien/noc (pkt 34.2/34.3): setNight() - dzien tlo #121722 bez
 * neonowej poswiaty, noc #05070B z ciepla skala #FFFDE7; bez ingerencji
 * w systemowa jasnosc ekranu (pkt 34.4).
 *
 * Engine OFF (pkt 35.1/35.2): po zgaszeniu wskazowki opadaja do zera,
 * luki przechodza na konfigurowalne przypisania OFF (domyslnie VOLTS /
 * AVG_TRIP_FUEL), pojawia sie dyskretny E-RAPORT; po rozruchu luki
 * wracaja do konfiguracji jazdy.
 */
class UfoClusterFullView(context: Context) : View(context) {

    companion object {
        /** Parametry dostepne do przypisania na luki (menu long-press, pkt 10.5). */
        val ARC_PARAMS = arrayOf(
            "ECT", "BOOST", "VOLTS", "FUEL", "TPS", "IAT", "LTFT", "STFT", "MAF", "BARO",
            "AVG_TRIP_FUEL"
        )

        /** Parametry dostepne jako pozycja wykresu w MID (pkt 8.7).
         *  Pelna lista tego, co apka udostepnia - nic na stale. */
        val CHART_KEYS = arrayOf(
            "BOOST", "ECT", "VOLTS", "TPS", "IAT", "LTFT",
            "STFT", "MAF", "BARO", "FUEL")

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

        /** Gestosc benzyny [g/l] - szacunek zuzycia trasy z MAF (pkt 35.1). */
        private const val FUEL_DENSITY_G_PER_L = 745.0

        /** Stechiometria benzyny [g powietrza / g paliwa]. */
        private const val AFR_GAS = 14.7
    }

    // ---------- Paleta (pkt 6 + 34.2/34.3) ----------
    // Dzien (34.2): tlo #121722, jasne elementy tarczy, neonowa poswiata
    // WYLACZONA, ostre krawedzie wektorowe. Noc (34.3): tlo #05070B,
    // ciepla skala #FFFDE7, aktywne pierscienie #0055FF, ograniczona
    // intensywnosc efektow. Przejscie zmienia jasnosc TRESCI, nigdy
    // systemowa jasnosc ekranu (pkt 34.4).
    private val BG_DAY = Color.parseColor("#121722")
    private val BG_NIGHT = Color.parseColor("#05070B")
    private val TICK_DAY = Color.WHITE
    private val TICK_NIGHT = Color.parseColor("#FFFDE7")
    private var BG = BG_NIGHT
    private var TICK = TICK_NIGHT
    private val DIAL_BG = Color.parseColor("#05070B")
    private val METAL_HI = Color.parseColor("#2A3448")
    private val METAL_LO = Color.parseColor("#141B29")
    private val BLUE_GLOW = Color.parseColor("#0055FF")
    private val CYAN = Color.parseColor("#00E5FF")
    private val RED = Color.parseColor("#FF0033")
    private val LCD_BG = Color.parseColor("#5E6F73")
    private val LCD_ON = Color.parseColor("#0D1517")
    private val LCD_OFF = Color.parseColor("#526266")
    private val STAT_BG = Color.parseColor("#E2E8E4")
    private val GREEN = Color.parseColor("#30D158")
    private val AMBER = Color.parseColor("#FF9F0A")
    private val GRAY = Color.parseColor("#8E8E93")
    private val AVG_INK = Color.BLACK
    private val MAX_INK = Color.parseColor("#0055FF")

    // ---------- Stan danych ----------
    private var rpm = 0f          // wyrenderowane (interpolowane)
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

    // ---------- Konfiguracja (z Ustawien OBD; host aplikuje w onResume) ----------
    var redlineRpm = 6500
    var vtecRpm = 5200
    var ecoTps = 18f
    var ecoMap = 48f
    var ecoRpm = 2800f
    /** Progi alarmowe luk (pkt 10.7) - edytowalne w Ustawieniach. */
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
    /** Long press na luku (powyzej 1 s, pkt 10.5): menu szybkiego przypisania. */
    var onArcAssign: (left: Boolean) -> Unit = { _ -> }

    // ---------- Motyw dzien/noc (pkt 34.2/34.3) ----------
    private var night = true

    // ---------- Stan silnika OFF (pkt 35.1 / 35.2) ----------
    private var engineOff = false
    /** Konfiguracja jazdy luk - zapamietana i przywracana po rozruchu (35.2). */
    private var savedLeftIndex = 0
    private var savedRightIndex = 0
    /** Konfigurowalne przypisania luk w stanie Engine OFF (35.1). */
    var offLeftParam = "VOLTS"
    var offRightParam = "AVG_TRIP_FUEL"
    /** Dyskretny E-RAPORT po zgaszeniu (35.1): otwiera raport trasy. */
    var onOpenEReport: () -> Unit = {}

    // ---------- LCD MID (pkt 8) ----------
    // Petla MID (pkt 8.6) + pozycje wykresow (pkt 8.7): wykres da sie
    // przypisac do KAZDEGO parametru udostepnianego przez apke - lista
    // nie jest ograniczona do stalego zbioru. Naprawa: bylo "VOLTAGE",
    // a paramValue() obsluguje "VOLTS" - pozycja nigdy nie miala danych.
    private val midLoop = arrayOf(
        "BOOST", "ECT", "VOLTS", "TPS", "IAT", "LTFT",
        "CHART:BOOST", "CHART:ECT", "CHART:VOLTS", "CHART:TPS",
        "CHART:IAT", "CHART:LTFT", "CHART:STFT", "CHART:MAF",
        "CHART:BARO", "CHART:FUEL")
    private var midIndex = 0

    /** Klucz parametru z pozycji petli MID (bez prefiksu CHART:). */
    private fun midKey(entry: String): String =
        if (entry.startsWith("CHART:")) entry.substring(6) else entry

    // Ring buffer 30 s historii dla wykresow MID (pkt 8.7).
    // Bufory alokowane leniwie, zero alokacji w onDraw.
    private val histVals = HashMap<String, FloatArray>()
    private val histTs = HashMap<String, LongArray>()
    private val histLen = HashMap<String, Int>()
    private val histHead = HashMap<String, Int>()
    private val midMax = HashMap<String, Float>()
    private val midAvgSum = HashMap<String, Float>()
    private val midAvgCount = HashMap<String, Float>()

    // ---------- Luki boczne (pkt 10) ----------
    private val leftLoop = arrayOf("ECT", "BOOST", "VOLTS")
    private val rightLoop = arrayOf("FUEL", "TPS", "IAT", "LTFT")
    private var leftIndex = 0
    private var rightIndex = 0

    // ---------- Geometria wirtualna 1280x720 ----------
    private val CX = 640f
    private val CY = 400f
    private val R_DIAL = 220f          // srednica 440 (pkt 7.1)
    private val LCD_L = 530f; private val LCD_T = 290f
    private val LCD_R = 750f; private val LCD_B = 510f

    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private var staticLayer: Bitmap? = null

    // ---------- Painty (prealokacja: zero alokacji w onDraw) ----------
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
    private val ovalL = RectF()
    private val ovalR = RectF()
    private val needlePath = Path()
    private val iconPath = Path()
    private var lastFrameMs = 0L

    // Shadery prealokowane (konstrukcja przy kazdej klatce = jank na Mali-T720)
    private val hudGlowShader = RadialGradient(520f, 150f, 120f,
        0x400022AA, 0x000022AA, Shader.TileMode.CLAMP)
    private val sweepBlueL = SweepGradient(130f, 400f, BLUE_GLOW, CYAN)
    private val sweepBlueR = SweepGradient(1150f, 400f, CYAN, BLUE_GLOW)
    private val sweepRedL = SweepGradient(130f, 400f, RED, 0xFFFF6688.toInt())
    private val sweepRedR = SweepGradient(1150f, 400f, 0xFFFF6688.toInt(), RED)
    private val tfBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val tfBoldMono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    // Cache tekstow rysowanych co klatke (60x/s - zero String.format)
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

    // ---------- API publiczne ----------

    /** Pelny stan telemetrii (UI nigdy nie dotyka transportu). */
    fun update(s: VehicleUiState) {
        val off = s.engineState == EngineStateLabel.OFF
        if (off != engineOff) {
            engineOff = off
            if (off) {
                // 35.1: zgaszenie - zapamietaj konfiguracje jazdy
                savedLeftIndex = leftIndex
                savedRightIndex = rightIndex
            } else {
                // 35.2: rozruch - luki wracaja do jazdy, E-RAPORT znika
                leftIndex = savedLeftIndex
                rightIndex = savedRightIndex
            }
        }
        // 35.1: po zgaszeniu wskazowki plynnie opadaja do zera
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

    fun toggleUnits() { useMph = !useMph }

    /**
     * Motyw dzien/noc (pkt 34.2/34.3) - automatyczny wg NOAA Sunset Engine;
     * host przekazuje pore dnia. Zmienia palete i odbudowuje warstwe
     * statyczna; NIE rusza systemowej jasnsci ekranu (pkt 34.4).
     */
    fun setNight(n: Boolean) {
        if (night == n) return
        night = n
        BG = if (n) BG_NIGHT else BG_DAY
        TICK = if (n) TICK_NIGHT else TICK_DAY
        if (width > 0 && height > 0) buildStaticLayer()
        postInvalidateOnAnimation()
    }

    /**
     * Przypisanie parametru do luku z menu (pkt 10.5: long press).
     * W stanie Engine OFF konfiguruje przypisania OFF (pkt 35.1).
     */
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

    // ---------- Wartosci parametrow ----------

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

    /** Etykieta interwalu w panelu statystyk (pkt 8.5: "C / TRIP", "V / 1h"). */
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

    // ---------- Srednie zuzycie trasy (AVG_TRIP_FUEL, pkt 35.1) ----------
    // Szacunek z MAF po stechiometrii 14.7 i gestosci benzyny 745 g/l,
    // dystans ze speedKmh. NaN dopoki dystans < 0.2 km (bez fabrykacji).
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
        if (tripDistKm < 0.2) Float.NaN
        else (tripFuelL / tripDistKm * 100.0).toFloat()

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
        // Petla animacji TYLKO gdy trzeba: wskazowka doleci albo polaczenie
        // zyje (puls pasa REV / blinker statusu). Offline = zero klatek.
        if (connected || connecting || Math.abs(rpm - rpmTarget) > 1f) {
            postInvalidateOnAnimation()
        } else {
            lastFrameMs = 0L
        }
    }

    // ---------- Warstwa statyczna ----------

    private fun drawStatic(c: Canvas) {
        pFill.color = BG
        c.drawRect(0f, 0f, 1280f, 720f, pFill)

        // metaliczny pierscien tarczy (pkt 6: #2A3448 -> #141B29)
        rectF.set(CX - R_DIAL, CY - R_DIAL, CX + R_DIAL, CY + R_DIAL)
        pFill.shader = LinearGradient(CX - R_DIAL, CY - R_DIAL, CX + R_DIAL, CY + R_DIAL,
            METAL_HI, METAL_LO, Shader.TileMode.CLAMP)
        c.drawCircle(CX, CY, R_DIAL, pFill)
        pFill.shader = null

        pFill.color = DIAL_BG
        c.drawCircle(CX, CY, R_DIAL - 26f, pFill)

        // neonowa poswiata tarczy: noc = ograniczona intensywnosc (34.3),
        // dzien = wylaczona (34.2)
        if (night) {
            pFill.shader = RadialGradient(CX, CY, R_DIAL, 0x000055FF, 0x330055FF, Shader.TileMode.CLAMP)
            c.drawCircle(CX, CY, R_DIAL, pFill)
            pFill.shader = null
        }

        // podzialki 0-8000, luk 240 stopni (pkt 7.2: 100/500/1000 RPM)
        val a0 = Math.toRadians(150.0)
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

        // cyfry 1-8: kroj pochylony, bez rotacji radialnej (pkt 7.2)
        pItalic.textSize = 30f
        pItalic.color = TICK
        pItalic.textAlign = Paint.Align.CENTER
        for (n in 1..8) {
            val ang = a0 + sweep * (n * 1000f / maxRpmS)
            val r = R_DIAL - 68f
            c.drawText(n.toString(), (CX + r * Math.cos(ang)).toFloat(),
                (CY + r * Math.sin(ang)).toFloat() + 10f, pItalic)
        }

        // LCD MID: obwod 2dp, bez zaokraglen (pkt 8.1), separator 2px Y=400 (8.4)
        pStroke.strokeWidth = 6f
        pStroke.color = METAL_HI
        pStroke.style = Paint.Style.STROKE
        c.drawRect(LCD_L, LCD_T, LCD_R, LCD_B, pStroke)
        pStroke.style = Paint.Style.FILL

        pFill.color = LCD_BG
        c.drawRect(LCD_L, LCD_T, LCD_R, LCD_B, pFill)

        pFill.color = METAL_LO
        c.drawRect(LCD_L, 399f, LCD_R, 401f, pFill)

        // jasny panel statystyk 204x96 (pkt 8.5)
        pFill.color = STAT_BG
        c.drawRect(538f, 408f, 742f, 504f, pFill)

        // tla luk bocznych 180x480 (pkt 10.1) - czesc statyczna
        ovalL.set(40f, 160f, 220f, 640f)
        ovalR.set(1060f, 160f, 1240f, 640f)
        pStroke.strokeWidth = 8f   // grubosc linii 8 dp
        pStroke.color = METAL_LO
        pStroke.style = Paint.Style.STROKE
        c.drawArc(ovalL, 90f, 180f, false, pStroke)
        c.drawArc(ovalR, 90f, -180f, false, pStroke)
        pStroke.style = Paint.Style.FILL
    }

    // ---------- Warstwa dynamiczna ----------

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
        if (speedKmh != spdCacheSrc || useMph != mphCache) {
            spdCacheSrc = speedKmh
            mphCache = useMph
            spdCache = if (speedKmh < 0) "---" else
                (if (useMph) (speedKmh * 0.621371f).toInt() else speedKmh).toString()
        }
        val spd = spdCache
        pItalic.textSize = 96f
        pItalic.color = TICK
        pItalic.textAlign = Paint.Align.CENTER
        c.drawText(spd, 520f, 128f, pItalic)

        pItalic.textSize = 28f
        c.drawText(if (useMph) "mph" else "km/h", 640f, 128f, pItalic)

        // Bieg: WYLICZANY ze stosunku RPM/km/h - rysowany przygaszonym,
        // zgodnie z pkt 9.2 (zgadywanie nie moze udawac pomiaru).
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
        pItalic.color = GRAY
        c.drawText(gear, 730f, 128f, pItalic)

        // pasek REV: 10 segmentow (pkt 9.3) - 1-4 blekitne, 5-8 zielone,
        // 9-10 bursztynowe; >= 6300 pulsuje czerwono do odciecia 6700
        val segW = 18f
        val now = System.currentTimeMillis()
        for (i in 0 until 10) {
            val x = 300f + i * (segW + 6f)
            val lit = rpm >= 4200 + i * 300
            val inRed = rpm >= 6300
            pFill.color = when {
                inRed -> if ((now / 150) % 2 == 0L) RED else 0x66FF0033
                !lit -> METAL_LO
                i < 4 -> 0x3399FF
                i < 8 -> GREEN
                else -> AMBER
            }
            c.drawRect(x, 20f, x + segW, 44f, pFill)
        }

        // ECO (pkt 9.4: TPS < prog && MAP < prog && RPM < prog) i VTEC (9.5)
        val eco = !tpsPct.isNaN() && tpsPct < ecoTps &&
            !mapKpa.isNaN() && mapKpa < ecoMap && rpm < ecoRpm
        pItalic.textSize = 24f
        pItalic.color = if (eco) GREEN else 0x2230D158
        c.drawText("ECO", 300f, 90f, pItalic)
        val vtec = rpm >= vtecRpm
        pItalic.color = if (vtec) CYAN else 0x2200E5FF
        c.drawText("VTEC", 700f, 90f, pItalic)

        // soczewka pod predkoscia (pkt 9.6: #0022AA, 25%)
        pFill.shader = hudGlowShader
        c.drawOval(380f, 130f, 660f, 170f, pFill)
        pFill.shader = null
    }

    /** Dyskretny przycisk E-RAPORT po zgaszeniu silnika (pkt 35.1). */
    private fun drawEReport(c: Canvas) {
        if (!engineOff) return
        rectF.set(48f, 56f, 268f, 104f)
        pStroke.strokeWidth = 2f
        pStroke.color = METAL_HI
        pStroke.style = Paint.Style.STROKE
        c.drawRoundRect(rectF, 10f, 10f, pStroke)
        pStroke.style = Paint.Style.FILL
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = 24f
        pText.typeface = tfBold
        pText.color = TICK
        c.drawText("E-RAPORT", 158f, 88f, pText)
    }

    private fun drawNeedle(c: Canvas) {
        val a0 = Math.toRadians(150.0)
        val sweep = Math.toRadians(240.0)
        val frac = (rpm / 8000f).coerceIn(0f, 1f)
        val ang = a0 + sweep * frac
        val rN = R_DIAL - 44f
        val nx = (rN * Math.cos(ang)).toFloat()
        val ny = (rN * Math.sin(ang)).toFloat()
        val px = -Math.sin(ang).toFloat()
        val py = Math.cos(ang).toFloat()
        val hubR = 7f
        needlePath.reset()
        needlePath.moveTo(CX + px * hubR, CY + py * hubR)
        needlePath.lineTo(CX - px * hubR, CY - py * hubR)
        needlePath.lineTo(CX + nx, CY + ny)
        needlePath.close()
        // wskazowka: jasna z blekitnym akcentem; Redline -> #FF0033 (pkt 7.3)
        val red = rpm >= redlineRpm
        pFill.color = if (red) RED else TICK
        c.drawPath(needlePath, pFill)
        pFill.color = if (red) RED else CYAN
        c.drawCircle(CX, CY, 10f, pFill)
    }

    /** Ikona parametru 24x24 (pkt 8.3) - proste glify wektorowe. */
    private fun drawMidIcon(c: Canvas, key: String, x: Float, y: Float, ink: Int) {
        pStroke.color = ink
        pStroke.strokeWidth = 3f
        iconPath.reset()
        when (key) {
            "ECT" -> { // termometr
                c.drawLine(x + 10f, y + 2f, x + 10f, y + 14f, pStroke)
                pStroke.style = Paint.Style.STROKE
                rectF.set(x + 4f, y + 12f, x + 16f, y + 24f)
                c.drawOval(rectF, pStroke)
                pStroke.style = Paint.Style.FILL
            }
            "BOOST" -> { // strzalka w gore
                iconPath.moveTo(x + 4f, y + 16f)
                iconPath.lineTo(x + 12f, y + 4f)
                iconPath.lineTo(x + 20f, y + 16f)
                pStroke.style = Paint.Style.STROKE
                c.drawPath(iconPath, pStroke)
                pStroke.style = Paint.Style.FILL
                c.drawLine(x + 12f, y + 8f, x + 12f, y + 24f, pStroke)
            }
            "VOLTS" -> { // blyskawica
                iconPath.moveTo(x + 14f, y)
                iconPath.lineTo(x + 5f, y + 13f)
                iconPath.lineTo(x + 11f, y + 13f)
                iconPath.lineTo(x + 9f, y + 24f)
                iconPath.lineTo(x + 19f, y + 10f)
                iconPath.lineTo(x + 13f, y + 10f)
                iconPath.close()
                c.drawPath(iconPath, pFill.also { it.color = ink })
            }
            "TPS" -> { // obrotowy potencjometr
                pStroke.style = Paint.Style.STROKE
                c.drawCircle(x + 12f, y + 13f, 9f, pStroke)
                pStroke.style = Paint.Style.FILL
                c.drawLine(x + 12f, y + 13f, x + 18f, y + 5f, pStroke)
            }
            "IAT" -> { // fale powietrza
                for (k in 0 until 3) c.drawLine(x + 3f, y + 6f + k * 6f, x + 21f, y + 6f + k * 6f, pStroke)
            }
            "LTFT", "STFT", "FUEL" -> { // kropla paliwa / korekta
                iconPath.moveTo(x + 12f, y + 2f)
                iconPath.lineTo(x + 20f, y + 14f)
                iconPath.lineTo(x + 4f, y + 14f)
                iconPath.close()
                c.drawPath(iconPath, pFill.also { it.color = ink })
                pStroke.style = Paint.Style.STROKE
                c.drawCircle(x + 12f, y + 18f, 5f, pStroke)
                pStroke.style = Paint.Style.FILL
            }
            "MAF" -> { // wachlarz przeplywu
                for (k in 0 until 3) {
                    val dx = (k - 1) * 7f
                    c.drawLine(x + 12f + dx * 0.4f, y + 4f, x + 12f + dx, y + 22f, pStroke)
                }
            }
            else -> { // BARO: kula
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

        // naglowek: ikona 24x24 + nazwa 12sp Bold, marginesy 8/10 px (pkt 8.3)
        val ink = if (have) LCD_ON else LCD_OFF
        drawMidIcon(c, key, 540f, 300f, ink)
        pText.color = ink
        pText.textSize = 24f
        pText.typeface = tfBold
        pText.textAlign = Paint.Align.LEFT
        c.drawText(key, 574f, 320f, pText)
        if (entry.startsWith("CHART:")) {
            pText.textAlign = Paint.Align.RIGHT
            pText.textSize = 16f
            pText.typeface = Typeface.DEFAULT
            c.drawText("WYKRES 30 S", 744f, 320f, pText)
        }

        // wartosc glowna: 38sp Monospace Bold + jednostka 14sp (pkt 8.3);
        // pozycja wykresu (pkt 8.7) rysuje w tej strefie wykres 30 s
        if (entry.startsWith("CHART:")) {
            drawMidChart(c, key, have, ink)
        } else {
            if (v != midMainSrc || key != midKeyCache) {
                midMainSrc = v
                midKeyCache = key
                midMainCache = if (!have) "---" else fmtParam(key, v)
            }
            pMono.textSize = 56f
            pMono.typeface = tfBoldMono
            pMono.color = ink
            pMono.textAlign = Paint.Align.RIGHT
            c.drawText(midMainCache, 700f, 378f, pMono)
            pMono.textSize = 24f
            c.drawText(paramUnit(key), 744f, 378f, pMono)
        }

        // panel statystyk: interwal z lewej (pkt 8.5), MAX/AVG z prawej
        pText.color = if (have) Color.BLACK else LCD_OFF
        pText.textSize = 20f
        pText.typeface = Typeface.DEFAULT
        pText.textAlign = Paint.Align.LEFT
        c.drawText(paramInterval(key), 548f, 452f, pText)

        val mx = midMax[key]
        val avgSum = midAvgSum[key]
        val avgCnt = midAvgCount[key]
        pText.textAlign = Paint.Align.RIGHT
        pText.textSize = 24f
        pText.typeface = tfBold
        pText.color = if (have) MAX_INK else LCD_OFF
        c.drawText(if (mx == null) "MAX: N/A" else "MAX: " + fmtParam(key, mx), 734f, 442f, pText)
        pText.color = if (have) AVG_INK else LCD_OFF
        c.drawText(if (avgSum == null || avgCnt == null || avgCnt <= 0f) "AVG: N/A"
            else "AVG: " + fmtParam(key, avgSum / avgCnt), 734f, 478f, pText)
    }

    /** Formatowanie zalezne od parametru (bar z dwoma miejscami). */
    private fun fmtParam(key: String, v: Float): String = when (key) {
        "BOOST" -> String.format("%.2f", v)
        "FUEL" -> String.format("%.1f", v)
        "AVG_TRIP_FUEL" -> String.format("%.1f", v)
        else -> if (v >= 100f || v == v.toInt().toFloat()) v.toInt().toString()
            else String.format("%.1f", v)
    }

    // ---------- Wykres w LCD MID (pkt 8.7) ----------

    /** Pojemnosc bufora: 30 s przy probkowaniu do 4 Hz. */
    private val HIST_CAP = 128

    /** Minimalny odstep probek [ms] - ~120 probek na 30 s. */
    private val HIST_MIN_DT_MS = 250L

    /** Historia WSZYSTKICH parametrow apki (pkt 8.7: pelna lista). */
    private fun pushAllHistory() {
        val now = System.currentTimeMillis()
        for (k in CHART_KEYS) pushHistory(k, paramValue(k), now)
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
                vs[lastIdx] = v   // nadpisanie probki w oknie minimalnym
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

    /**
     * Wykres 30 s w gornej strefie LCD (pkt 8.7): gladka linia wektorowa
     * + pikselowe wypelnienie pod linia (bloki 4x4 px). Skala liczona
     * z widocznego okna 30 s (min/max + 10% marginesu) - bez zmyslonej
     * skali. Brak probek = "ZBIERAM DANE...", nie udany przebieg.
     */
    private fun drawMidChart(c: Canvas, key: String, have: Boolean, ink: Int) {
        val len = histLen[key] ?: 0
        if (len < 2 || !have) {
            pText.textAlign = Paint.Align.CENTER
            pText.textSize = 20f
            pText.typeface = tfBold
            pText.color = LCD_OFF
            c.drawText(if (have) "ZBIERAM DANE..." else "BRAK DANYCH", 640f, 366f, pText)
            return
        }
        val head = histHead[key] ?: 0
        val ts = histTs[key] ?: return
        val vs = histVals[key] ?: return
        val now = System.currentTimeMillis()
        val winMs = 30_000L
        val x0 = 534f
        val x1 = 746f
        val yBase = 394f
        val yTop = 328f
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

        // pikselowe wypelnienie: bloki 4x4 pod przebiegiem (styl LCD)
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

        // gladka linia wektorowa (wygladzanie quad przez srodki odcinkow)
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

        private fun drawWingArc(c: Canvas, left: Boolean) {
        // pkt 35.1: po zgaszeniu luki pokazuja przypisania stanu OFF
        val key = if (engineOff) {
            if (left) offLeftParam else offRightParam
        } else if (left) leftLoop[leftIndex] else rightLoop[rightIndex]
        val v = paramValue(key)
        val alarm = arcAlarm(key, v)

        // frakcja wypelnienia (pkt 10.6: min na dole, max na gorze;
        // wartosci dwukierunkowe jak BOOST: zero w polowie)
        val frac = arcFrac(key, v)

        pStroke.strokeWidth = 8f
        pStroke.style = Paint.Style.STROKE
        pStroke.shader = when {
            alarm && left -> sweepRedL
            alarm && !left -> sweepRedR
            left -> sweepBlueL
            else -> sweepBlueR
        }
        if (!v.isNaN()) {
            // wypelnienie rosnie od dolu luku (lewy: CCW, prawy: CW)
            val sweepAng = 180f * frac
            c.drawArc(if (left) ovalL else ovalR,
                90f, if (left) sweepAng else -sweepAng, false, pStroke)
        }
        pStroke.shader = null
        pStroke.style = Paint.Style.FILL

        // wartosc + etykieta po wewnetrznej stronie (pkt 10.2)
        val innerX = if (left) 246f else 1034f
        pText.textAlign = if (left) Paint.Align.LEFT else Paint.Align.RIGHT
        pText.textSize = 22f
        pText.typeface = tfBold
        pText.color = if (alarm) RED else TICK
        c.drawText(key, innerX, 380f, pText)
        if (v != (if (left) arcTxtSrcL else arcTxtSrcR) || key != (if (left) arcTxtKeyL else arcTxtKeyR)) {
            if (left) { arcTxtSrcL = v; arcTxtKeyL = key; arcTxtCacheL = arcText(key, v) }
            else { arcTxtSrcR = v; arcTxtKeyR = key; arcTxtCacheR = arcText(key, v) }
        }
        pText.textSize = 30f
        c.drawText(if (left) arcTxtCacheL else arcTxtCacheR, innerX, 416f, pText)
    }

    private fun arcText(key: String, v: Float): String =
        if (v.isNaN()) "---" else fmtParam(key, v) + " " + paramUnit(key)

    /** Skala luk (pkt 10.6) - wartosc nominalna w srodku, min na dole. */
    private fun arcFrac(key: String, v: Float): Float = when (key) {
        "ECT" -> if (v.isNaN()) 0f else ((v - 40f) / 80f).coerceIn(0f, 1f)
        // BOOST dwukierunkowy: zero w polowie, podcisnienie w dol, doladowanie w gore
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

    /** Progi alarmowe #FF0033 (pkt 10.7) - edytowalne z Ustawien OBD. */
    private fun arcAlarm(key: String, v: Float): Boolean = when (key) {
        "ECT" -> !v.isNaN() && v > ectMaxC
        "VOLTS" -> !v.isNaN() && (v < voltMin || v > voltMax)
        "LTFT" -> !v.isNaN() && (v > ltftMax || v < -ltftMax)
        "IAT" -> !v.isNaN() && v > iatMax
        "BOOST" -> !v.isNaN() && v > 1.0f
        "FUEL" -> !v.isNaN() && (v < 12f || v > 16f)
        else -> false
    }

    // ---------- Footer (pkt 11): 5 sektorow od lewej ----------
    // 1: Check Engine/DTC | 2: ZAPISZ EVENT | 3: SWAP UI | 4: zebatka | 5: status

    private fun drawFooter(c: Canvas) {
        pFill.color = Color.parseColor("#0D1220")
        c.drawRect(0f, 640f, 1280f, 720f, pFill)
        pStroke.strokeWidth = 2f
        pStroke.color = METAL_HI
        c.drawLine(0f, 640f, 1280f, 640f, pStroke)

        // Sektor 1: Check Engine (pkt 11.1) - ikona + kod/liczba DTC
        val mil = milOn || dtcCount > 0
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = 24f
        pText.typeface = tfBold
        pText.color = if (mil) RED else GRAY
        c.drawText(if (milOn) "CHECK ENGINE" else if (dtcCount > 0) "DTC: " + dtcCount
            else "CHECK ENGINE OK", 128f, 686f, pText)
        pText.textSize = 16f
        pText.color = GRAY
        c.drawText("KODY BLEDOW", 128f, 712f, pText)

        // Sektor 2: ZAPISZ EVENT (pkt 11.2 - dyskretny, bez czerwonej ramki)
        pText.textSize = 24f
        pText.color = TICK
        c.drawText("ZAPISZ EVENT", 384f, 690f, pText)
        pText.textSize = 16f
        pText.color = GRAY
        c.drawText("30 S TELEMETRII", 384f, 712f, pText)

        // Sektor 3: SWAP UI (pkt 11.3)
        pText.textSize = 24f
        pText.color = TICK
        c.drawText("SWAP UI", 640f, 690f, pText)
        pText.textSize = 16f
        pText.color = GRAY
        c.drawText("UFO / CARPLAY", 640f, 712f, pText)

        // Sektor 4: zebatka (pk 11.4 ustawienia)
        drawGearIcon(c, 896f, 664f, TICK)
        pText.textSize = 16f
        pText.color = GRAY
        c.drawText("USTAWIENIA", 896f, 712f, pText)

        // Sektor 5: status OBD + napiecie (pkt 11.4)
        val stale = connected && System.currentTimeMillis() - lastDataMs > 5000
        pText.textSize = 26f
        pText.typeface = tfBold
        pText.color = when {
            !connected && !connecting -> GRAY
            connecting -> AMBER
            stale -> GRAY
            else -> GREEN
        }
        c.drawText(when {
            !connected && !connecting -> "OFFLINE"
            connecting -> "CONNECTING"
            else -> "OBD OK"
        }, 1152f, 690f, pText)
        pText.textSize = 18f
        pText.color = TICK
        if (!voltage.equals(voltSrc)) {
            voltSrc = voltage
            voltText = if (voltage.isNaN()) "--- V" else String.format("%.1f V", voltage)
        }
        c.drawText(voltText, 1152f, 712f, pText)
    }

    /** Glify zebatki dla sektora ustawien. */
    private fun drawGearIcon(c: Canvas, cx: Float, cy: Float, ink: Int) {
        pStroke.color = ink
        pStroke.strokeWidth = 4f
        pStroke.style = Paint.Style.STROKE
        c.drawCircle(cx, cy + 12f, 10f, pStroke)
        for (k in 0 until 8) {
            val a = Math.toRadians((k * 45).toDouble())
            c.drawLine(
                (cx + 10f * Math.cos(a)).toFloat(), (cy + 12f + 10f * Math.sin(a)).toFloat(),
                (cx + 15f * Math.cos(a)).toFloat(), (cy + 12f + 15f * Math.sin(a)).toFloat(), pStroke)
        }
        pStroke.style = Paint.Style.FILL
    }

    // ---------- Gesty (pkt 8.6, 9.7, 10.5, 11) ----------

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

        // dyskretny E-RAPORT po zgaszeniu (pkt 35.1)
        if (engineOff && x in 48f..268f && y in 56f..104f) {
            onOpenEReport()
            return true
        }

        // FOOTER ma najwyzszy priorytet (sektory wg pkt 11, od lewej):
        // 1: CE/DTC, 2: ZAPISZ EVENT, 3: SWAP UI, 4: ustawienia, 5: status
        if (y > 640f) {
            when {
                x < 256f -> onOpenDtc()
                x < 512f -> onSaveEvent()
                x < 768f -> onSwapUi()
                x < 1024f -> onOpenSettings()
                else -> Unit // status - nieinteraktywny
            }
            return true
        }
        // LCD MID (pkt 8.6): tap = nastepny parametr; long press > 1.5 s
        // na dolnym panelu = reset MAX/AVG
        if (x in LCD_L..LCD_R && y in LCD_T..LCD_B) {
            if (y > 400f && holdMs > 1500) {
                val key = midKey(midLoop[midIndex])
                midMax.remove(key); midAvgSum.remove(key); midAvgCount.remove(key)
            } else {
                midIndex = (midIndex + 1) % midLoop.size
            }
            postInvalidateOnAnimation()
            return true
        }
        // luki boczne (pkt 10.5): tap = nastepny parametr; long press > 1 s
        // = menu szybkiego przypisania
        if (y in 160f..640f) {
            if (x < 260f) {
                if (holdMs > 1000) onArcAssign(true)
                else { leftIndex = (leftIndex + 1) % leftLoop.size; postInvalidateOnAnimation() }
                return true
            }
            if (x > 1020f) {
                if (holdMs > 1000) onArcAssign(false)
                else { rightIndex = (rightIndex + 1) % rightLoop.size; postInvalidateOnAnimation() }
                return true
            }
        }
        // HUD: tapniecie predkosciomierza (pkt 9.7 - akcja konfigurowalna)
        if (y < 160f && x in 400f..680f) { onSpeedTap(); return true }
        return true
    }
}
