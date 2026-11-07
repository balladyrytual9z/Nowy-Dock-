package pl.dock.widget.dashboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.dock.widget.LauncherActivity
import pl.dock.widget.ObdCoreService
import pl.dock.widget.blackbox.BlackBoxActivity
import pl.dock.widget.diagnostics.DtcManagerActivity
import pl.dock.widget.diagnostics.KasujDtcActivity
import pl.dock.widget.diagnostics.MisfireLabActivity
import pl.dock.widget.maintenance.MaintenanceActivity
import pl.dock.widget.orchestration.ConnectionManager
import pl.dock.widget.report.EReportActivity
import pl.dock.widget.repository.VehicleUiState
import pl.dock.widget.settings.ObdSettingsActivity
import pl.dock.widget.trip.TripActivity
import pl.dock.widget.analytics.CorrelationAnalyzer
import pl.dock.widget.ui.ChartView
import pl.dock.widget.ui.OverlayChartView
import pl.dock.widget.ui.UfoClusterActivity
import pl.dock.widget.util.SolarTimes

/**
 * CarPlay Dashboard (CZESC III dokumentu "ui obd.txt").
 *
 * Struktura: staly pionowy dock ~110dp po lewej + obszar roboczy.
 *  - dock: przewijalna lista modulow (pkt 15.1) + przypiete do dolu
 *    QUICK BUTTON / USTAWIENIA / HOME (pkt 15.2 - nie przewijaja sie);
 *  - aktywna zakladka = pigulka w kolorze akcentu (pkt 15);
 *  - QUICK BUTTON: akcja wybierana w Ustawieniach (pkt 15.3): ZAPISZ
 *    EVENT / wymus reconnect OBD / szybkie kasowanie DTC;
 *  - Overview: karty z naglowkiem uppercase, wartoscia, statusem;
 *    tap otwiera modul (pkt 16), press = delikatne 97% skali (pkt 16.2);
 *  - Live Data: lista parametrow ze statusem (pkt 17.1) + Multi-Select
 *    max 4 wykresy (pkt 17.2); nadmiar: FIFO albo BLOKADA z tostem -
 *    dokument nie rozstrzygnal, wiec jest konfigurowalne
 *    (multi_select_overflow, domyslnie FIFO - decyzja jawnie zapisana);
 *  - dzien/noc wg slonca (pkt 20 - bez recznego przelacznika w UI);
 *  - kolor akcentu konfigurowalny (pkt 13): blue / type-r red / emerald.
 *
 * Zakaz fabryzacji: kazdy parametr pokazuje wylacznie dane z VehicleUiState;
 * brak odczytu = "---" + status BRAK DANYCH, nigdy zmyslona wartosc.
 */
class CarPlayDashboardActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private val scope = MainScope()

    // ---------- motyw (pkt 13 + 20) ----------
    private var night = true
    private var accent: Int = Color.parseColor("#0A84FF")
    private fun cBg() = if (night) Color.parseColor("#121212") else Color.parseColor("#F2F2F7")
    private fun cCard() = if (night) Color.parseColor("#1C1C1E") else Color.WHITE
    private fun cBorder() = if (night) Color.parseColor("#2C2C2E") else Color.parseColor("#D1D1D6")
    private fun cText() = if (night) Color.WHITE else Color.BLACK
    private fun cCaption() = Color.parseColor("#8E8E93")

    // ---------- Overview ----------
    private lateinit var ovRpm: TextView
    private lateinit var ovSpeed: TextView
    private lateinit var ovVolt: TextView
    private lateinit var ovEct: TextView
    private lateinit var ovDtc: TextView
    private lateinit var ovConn: TextView
    private lateinit var overviewPage: View

    // ---------- Live Data ----------
    private lateinit var livePage: View
    private lateinit var paramList: LinearLayout
    private lateinit var chartsArea: FrameLayout
    private val paramRows = HashMap<String, LinearLayout>()
    private val paramValueViews = HashMap<String, TextView>()
    private val paramStatusViews = HashMap<String, TextView>()
    private val charts = HashMap<String, ChartView>()
    private val selection = ArrayList<String>() // max 4 (pkt 17.2)
    private var overflowFifo = true
    private var autoSort = true                  // pkt 17.3 Auto-Sort
    private var expandedParam: String? = null   // pkt 17.4 pelny ekran
    private val wasAnomaly = HashMap<String, Boolean>()
    private var chartRangeMs = 30_000L   // zakres osi czasu (pkt 18.4)
    private lateinit var rangeBtn: Button
    private lateinit var exportBtn: Button
    private lateinit var overlayBtn: Button
    private var overlayMode = false            // pkt 19 Multi-Overlay
    private var overlayChart: OverlayChartView? = null
    private var ribbon: TextView? = null        // Correlation Ribbon (pkt 19)
    private val lastOvPushMs = HashMap<String, Long>()
    private var lastAnalysisMs = 0L

    private val params = linkedMapOf(
        "RPM" to "obr/min",
        "SPEED" to "km/h",
        "LOAD" to "%",
        "TPS" to "%",
        "MAP" to "kPa",
        "ECT" to "C",
        "IAT" to "C",
        "STFT" to "%",
        "LTFT" to "%",
        "VOLT" to "V"
    )

    // ---------- dock ----------
    private val dockBtns = ArrayList<Button>()
    private var activePage = 0

    // ---------- Multi-Window / Split-Screen (pkt 36) ----------
    private var multiWindow = false
    private lateinit var workArea: FrameLayout
    private lateinit var liveScroll: ScrollView
    private lateinit var chartsCol: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = ConnectionManager.get(applicationContext)

        // motyw dzien/noc wg slonca (pkt 20; bez recznego przelacznika)
        val prefs = getSharedPreferences("obd_settings", MODE_PRIVATE)
        if (!prefs.getBoolean("night_auto", false)) {
            night = true   // domyslnie ciemny (samochodowy standard)
        } else {
            night = SolarTimes.isNight(
                prefs.getString("lat", "52.23")?.toDoubleOrNull() ?: 52.23,
                prefs.getString("lon", "21.01")?.toDoubleOrNull() ?: 21.01)
        }
        // kolor akcentu (pkt 13): Apple Blue / Type-R Red / Civic Emerald
        accent = when (prefs.getString("carplay_accent", "blue")) {
            "red" -> Color.parseColor("#FF3B30")
            "green" -> Color.parseColor("#30D158")
            else -> Color.parseColor("#0A84FF")
        }
        overflowFifo = prefs.getString("multi_select_overflow", "fifo") != "block"
        // pkt 17.3: domyslnie WL; uzytkownik moze wylaczyc w Ustawieniach
        autoSort = prefs.getBoolean("auto_sort_anomaly", true)

        // uklad korzenia zalezy od trybu okna (pkt 36 Multi-Window)
        multiWindow = isInMultiWindowMode
        workArea = FrameLayout(this).apply {
            addView(buildOverviewPage())
            addView(buildLivePage())
        }
        applyLiveStack()
        setContentView(buildRoot())

        showOverview()
        observe()
    }

    // ---------- DOCK (pkt 15) ----------

    private fun buildDock(): View {
        dockBtns.clear()   // rebuild w Split-Screen nie dubluje zakladek
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cCard())
            layoutParams = LinearLayout.LayoutParams(dp(110),
                LinearLayout.LayoutParams.MATCH_PARENT)
        }

        // lista modulow - przewijalna (pkt 15.1)
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)

        fun mod(label: String, onClick: (View) -> Unit): Button {
            val b = dockBtn(label, onClick)
            dockBtns.add(b)
            list.addView(b)
            return b
        }
        mod("OVERVIEW") { setActive(0) { showOverview() } }
        mod("LIVE DATA") { setActive(1) { showLive() } }
        mod("TRIP") { startActivity(Intent(this, TripActivity::class.java)) }
        mod("BLACK BOX") { startActivity(Intent(this, BlackBoxActivity::class.java)) }
        mod("MISFIRE") { startActivity(Intent(this, MisfireLabActivity::class.java)) }
        mod("DTC") { startActivity(Intent(this, DtcManagerActivity::class.java)) }
        mod("E-RAPORT") { startActivity(Intent(this, EReportActivity::class.java)) }
        mod("SERWIS") { startActivity(Intent(this, MaintenanceActivity::class.java)) }
        dock.addView(scroll)

        // dolna sekcja przypieta - NIE przewija sie (pkt 15.2)
        dock.addView(quickBtn())
        dock.addView(dockBtn("USTAWIENIA", {
            startActivity(Intent(this, ObdSettingsActivity::class.java))
        }))
        dock.addView(dockBtn("HOME", {
            startActivity(Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }))
        highlightActive()
        return dock
    }

    private fun setActive(idx: Int, show: () -> Unit) {
        activePage = idx
        highlightActive()
        show()
    }

    /** Aktywna zakladka = pigulka w kolorze akcentu (pkt 15). */
    private fun highlightActive() {
        for ((i, b) in dockBtns.withIndex()) {
            (b.background as GradientDrawable).setColor(
                if (i == activePage) accent else cBorder())
        }
    }

    private fun dockBtn(label: String, onClick: (View) -> Unit,
                        horizontal: Boolean = false): Button =
        Button(this).apply {
            text = label
            textSize = if (horizontal) 9f else 11f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(cBorder())
            }
            setOnClickListener { onClick(this) }
            layoutParams = if (horizontal) LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(4, 6, 4, 6) }
            else LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 72
            ).apply { setMargins(8, 8, 8, 0) }
        }

    /**
     * QUICK BUTTON (pkt 15.3): akcja wybierana w Ustawieniach OBD
     * (quick_button_action). Etykieta zawsze opisuje dokladnie, co zrobi.
     */
    private fun quickBtn(): Button {
        val prefs = getSharedPreferences("obd_settings", MODE_PRIVATE)
        val action = prefs.getString("quick_button_action", "save") ?: "save"
        val label = when (action) {
            "reconnect" -> "QUICK: RECONNECT OBD"
            "cleardtc" -> "QUICK: KASUJ DTC"
            else -> "QUICK: ZAPISZ EVENT"
        }
        return Button(this).apply {
            text = label
            textSize = 10f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(accent)
            }
            setOnClickListener {
                when (action) {
                    "reconnect" -> forceReconnect()
                    "cleardtc" -> startActivity(Intent(this@CarPlayDashboardActivity,
                        KasujDtcActivity::class.java))
                    else -> saveEvent()
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 72
            ).apply { setMargins(8, 16, 8, 0) }
        }
    }

    private fun saveEvent() {
        val i = Intent(this, ObdCoreService::class.java)
        i.action = ObdCoreService.ACTION_SAVE_EVENT
        try {
            startForegroundService(i)
        } catch (e: Exception) {
            try { startService(i) } catch (ignored: Exception) {}
        }
        Toast.makeText(this, "EVENT: zapisano telemetrie (30 s)", Toast.LENGTH_SHORT).show()
    }

    /** Wymus reconnect: rozlacz i polacz z zapamietanym adapterem. */
    private fun forceReconnect() {
        val prefs = getSharedPreferences("obd_settings", MODE_PRIVATE)
        val mac = prefs.getString("adapter_mac", "")?.trim().orEmpty()
        if (mac.isEmpty()) {
            Toast.makeText(this, "Brak zapamietanego adaptera - najpierw skan", Toast.LENGTH_SHORT).show()
            return
        }
        val preferBle = prefs.getBoolean("prefer_ble", false)
        manager.disconnect()
        manager.connect(mac, preferBle)
        Toast.makeText(this, "Reconnect: " + mac, Toast.LENGTH_SHORT).show()
    }

    // ---------- Multi-Window / Split-Screen (pkt 36) ----------

    /**
     * Split-Screen (pkt 36): w Multi-Window dok schodzi na dol jako
     * poziomy pasek ~50dp, zawartosc robocza plynie w jednej kolumnie,
     * wszystkie moduly pozostaja dostepne. Powrot do pelnego ekranu
     * odtwarza klasyczny uklad i przywraca UFO (jesli CarPlay wszedl
     * z UFO). Karty, zaznaczenia i wykresy to te same widoki
     * (re-parent workArea), wiec zadna konfiguracja nie ginie;
     * wymiary przelicza system po kazdym resize.
     */
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        if (multiWindow == isInMultiWindowMode) return
        multiWindow = isInMultiWindowMode
        applyLiveStack()
        setContentView(buildRoot())
        if (!multiWindow && intent.getBooleanExtra("from_ufo", false)) {
            startActivity(Intent(this, UfoClusterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
    }

    private fun buildRoot(): View {
        val root = LinearLayout(this)
        root.setBackgroundColor(cBg())
        if (multiWindow) {
            root.orientation = LinearLayout.VERTICAL
            root.addView(workArea, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(buildDockBar(), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)))
        } else {
            root.orientation = LinearLayout.HORIZONTAL
            root.addView(buildDock())
            root.addView(workArea, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        return root
    }

    /** Live Data: jedna przewijana kolumna w Split-Screen (pkt 36). */
    private fun applyLiveStack() {
        (livePage as LinearLayout).orientation = if (multiWindow)
            LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        if (multiWindow) {
            liveScroll.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            chartsCol.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.6f)
        } else {
            liveScroll.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            chartsCol.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.6f)
        }
    }

    /**
     * Poziomy pasek dokowania na dole (pkt 36): przewijalna lista
     * modulow + przypiete QUICK / USTAWIENIA / HOME - komplet modulow.
     */
    private fun buildDockBar(): View {
        dockBtns.clear()
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(cCard())
            gravity = Gravity.CENTER_VERTICAL
        }
        val scroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scroll.addView(list)
        fun mod(label: String, onClick: (View) -> Unit) {
            val b = dockBtn(label, onClick, horizontal = true)
            dockBtns.add(b)
            list.addView(b)
        }
        mod("OVERVIEW") { setActive(0) { showOverview() } }
        mod("LIVE DATA") { setActive(1) { showLive() } }
        mod("TRIP") { startActivity(Intent(this, TripActivity::class.java)) }
        mod("BLACK BOX") { startActivity(Intent(this, BlackBoxActivity::class.java)) }
        mod("MISFIRE") { startActivity(Intent(this, MisfireLabActivity::class.java)) }
        mod("DTC") { startActivity(Intent(this, DtcManagerActivity::class.java)) }
        mod("E-RAPORT") { startActivity(Intent(this, EReportActivity::class.java)) }
        mod("SERWIS") { startActivity(Intent(this, MaintenanceActivity::class.java)) }
        bar.addView(scroll)
        bar.addView(quickBtn(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT))
        bar.addView(dockBtn("USTAWIENIA", {
            startActivity(Intent(this, ObdSettingsActivity::class.java))
        }, horizontal = true), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT))
        bar.addView(dockBtn("HOME", {
            startActivity(Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }, horizontal = true), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT))
        highlightActive()
        return bar
    }

    // ---------- OVERVIEW (pkt 16) ----------

    /**
     * Kolumna kart Overview (pkt 16): pierwsza karta w kolejnosci = duza,
     * pozostale w rzedach po 2. Kolejnosc i widocznosc sa TRWALE (16.1):
     * prefs overview_cards_order + ov_card_*; Drag & Drop (long press,
     * upuszczenie na innej karcie) zamienia karty miejscami.
     */
    private fun buildOverviewPage(): View {
        overviewPage = ScrollView(this)
        ovColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
        }
        (overviewPage as ScrollView).addView(ovColumn)
        populateOverview(ovColumn)
        return overviewPage
    }

    private lateinit var ovColumn: LinearLayout

    private val ovCardIds = listOf("RPM_SPEED", "BATTERY", "ECT", "DTC", "OBD")

    /** Trwala kolejnosc kart (pkt 16.1) - prefs overview_cards_order. */
    private fun cardOrder(): ArrayList<String> {
        val saved = getSharedPreferences("obd_settings", MODE_PRIVATE)
            .getString("overview_cards_order", null)?.split(",") ?: emptyList()
        val order = ArrayList<String>()
        for (id in saved) if (ovCardIds.contains(id) && !order.contains(id)) order.add(id)
        for (id in ovCardIds) if (!order.contains(id)) order.add(id)
        return order
    }

    private fun cardVisible(id: String): Boolean =
        getSharedPreferences("obd_settings", MODE_PRIVATE).getBoolean("ov_card_" + id, true)

    /** Budowa karty wg id (pkt 16.1: moduly wymienne, nic na stale). */
    private fun buildCard(id: String): LinearLayout {
        val cardView = when (id) {
            "RPM_SPEED" -> overviewBigCard()
            "BATTERY" -> cardWith(bigValue("BATTERY").also { ovVolt = it })
            "ECT" -> cardWith(bigValue("ECT").also { ovEct = it })
            "DTC" -> cardWith(bigValue("DTC").also { ovDtc = it })
            else -> cardWith(bigValue("OBD").also { ovConn = it })
        }
        // akcje + start Drag & Drop z long-press (karty z animacja 16.2
        // konsumuja DOWN w touch listenerze - drag startuje z tego samego
        // timera; pozostale karty uzywaja klasycznego onLongClickListener)
        when (id) {
            "RPM_SPEED" -> pressable(cardView, { setActive(1) { showLive() } },
                { startCardDrag(cardView, id) })
            "DTC" -> pressable(cardView,
                { startActivity(Intent(this, DtcManagerActivity::class.java)) },
                { startCardDrag(cardView, id) })
            else -> cardView.setOnLongClickListener { startCardDrag(cardView, id); true }
        }
        return cardView
    }

    /** Duza karta RPM + SPEED (tap -> LIVE DATA, pkt 16). */
    private fun overviewBigCard(): LinearLayout {
        val bigCard = card()
        bigCard.orientation = LinearLayout.HORIZONTAL
        val bigColL = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bigColL.addView(captionRow("RPM"))
        ovRpm = bigValue("RPM")
        bigColL.addView(ovRpm)
        val bigColR = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bigColR.addView(captionRow("SPEED"))
        ovSpeed = bigValue("SPEED")
        bigColR.addView(ovSpeed)
        bigCard.addView(bigColL, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bigCard.addView(bigColR, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return bigCard
    }

    /** Przebudowa kolumny kart wg trwalej kolejnosci i widocznosci. */
    private fun populateOverview(col: LinearLayout) {
        col.removeAllViews()
        col.addView(TextView(this).apply {
            text = "OVERVIEW"
            textSize = 24f
            setTextColor(cText())
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        })
        val ids = cardOrder().filter { cardVisible(it) }
        var row: LinearLayout? = null
        for ((i, id) in ids.withIndex()) {
            val cardView = buildCard(id)
            cardView.tag = id
            attachDragSwap(cardView)
            if (i == 0) {
                col.addView(cardView)
            } else {
                if (i % 2 == 1) {
                    row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    col.addView(row)
                }
                row?.addView(cardView, LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
    }

    // ---------- Drag & Drop kart (pkt 16.1) ----------

    private var dragCardId: String? = null

    private fun startCardDrag(cardView: View, id: String) {
        dragCardId = id
        val cd = android.content.ClipData.newPlainText("card", id)
        cardView.startDragAndDrop(cd, View.DragShadowBuilder(cardView), null, 0)
    }

    /** Upuszczenie na innej karcie = zamiana miejscami + zapis kolejnosci. */
    private fun attachDragSwap(cardView: LinearLayout) {
        cardView.setOnDragListener { v, e ->
            if (e.action == android.view.DragEvent.ACTION_DROP) {
                val target = v.tag as String
                val src = dragCardId
                dragCardId = null
                if (src != null && src != target) swapCards(src, target)
            }
            true
        }
    }

    private fun swapCards(a: String, b: String) {
        val order = cardOrder()
        val ia = order.indexOf(a)
        val ib = order.indexOf(b)
        if (ia < 0 || ib < 0) return
        order[ia] = b
        order[ib] = a
        getSharedPreferences("obd_settings", MODE_PRIVATE).edit()
            .putString("overview_cards_order", order.joinToString(",")).apply()
        populateOverview(ovColumn)
    }

    private fun bigValue(caption: String): TextView =
        TextView(this).apply {
            text = "---"
            textSize = 40f
            setTextColor(cText())
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            tag = caption
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = 16f
            setColor(cCard())
            setStroke(1, cBorder())
        }
        setPadding(24, 20, 24, 20)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 }
    }

    private fun cardWith(value: TextView): LinearLayout {
        val c = card()
        c.addView(captionRow(value.tag as String))
        c.addView(value)
        return c
    }

    /**
     * Naglowek karty: ikona wektorowa 24x24 + podpis (pkt 16).
     * Glify w CardIcon - jeden spojny styl, bez plikow zasobow.
     */
    private fun captionRow(caption: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(View(this@CarPlayDashboardActivity).apply {
                background = CardIcon.forCard(caption)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    setMargins(0, 2, 10, 0)
                }
            })
            addView(TextView(this@CarPlayDashboardActivity).apply {
                text = caption
                textSize = 12f
                setTextColor(cCaption())
            })
        }

    /**
     * Animacja interakcji (pkt 16.2): press = delikatne zmniejszenie
     * karty do 97%, zwolnienie = powrot do 100%. Bez sprezynowania.
     * onLongPress (opcjonalnie, pkt 16.1) startuje Drag & Drop - ten sam
     * timer, bo touch listener konsumuje zdarzenia i klasyczny
     * OnLongClickListener nigdy by nie zadzialal.
     */
    private fun pressable(v: View, onTap: () -> Unit, onLongPress: (() -> Unit)? = null) {
        val longPress = Runnable { onLongPress?.invoke() }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.scaleX = 0.97f; v.scaleY = 0.97f
                    if (onLongPress != null) v.postDelayed(longPress, 550)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.scaleX = 1f; v.scaleY = 1f
                    v.removeCallbacks(longPress)
                    if (e.action == MotionEvent.ACTION_UP &&
                        isClick(e, v)) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun isClick(e: MotionEvent, v: View): Boolean {
        return e.x >= 0 && e.x <= v.width && e.y >= 0 && e.y <= v.height
    }

    // ---------- LIVE DATA (pkt 17-18) ----------

    private fun buildLivePage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        livePage = page
        liveScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        paramList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 24, 12, 24)
        }
        liveScroll.addView(paramList)
        for ((name, unit) in params) paramList.addView(paramRow(name, unit))

        // kolumna wykresow + kontrolki (zakres osi czasu, eksport PNG)
        chartsCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rangeBtn = miniBtn("ZAKRES: 30 S") {
            chartRangeMs = when (chartRangeMs) {
                30_000L -> 60_000L
                60_000L -> 300_000L
                300_000L -> 0L
                else -> 30_000L
            }
            applyRange()
        }
        exportBtn = miniBtn("EKSPORT PNG") { exportCharts() }
        overlayBtn = miniBtn("OVERLAY: OFF") { toggleOverlay() }
        controls.addView(rangeBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(exportBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(overlayBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        chartsArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        chartsCol.addView(controls)
        chartsCol.addView(chartsArea)
        page.addView(liveScroll)
        page.addView(chartsCol, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1.6f))
        livePage = page
        return page
    }

    private fun paramRow(name: String, unit: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(cCard())
                setStroke(1, cBorder())
            }
            setPadding(20, 14, 20, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(TextView(this).apply {
            text = name
            textSize = 13f
            setTextColor(cText())
        })
        val status = TextView(this).apply {
            text = "BRAK DANYCH"
            textSize = 11f
            setTextColor(cCaption())
            gravity = Gravity.END
        }
        head.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        // ikona rozwiniecia (pkt 17.4): pelny ekran parametru
        val expand = TextView(this).apply {
            text = "\u2197"
            textSize = 16f
            setTextColor(accent)
            setPadding(10, 0, 10, 0)
        }
        expand.setOnClickListener { expandParam(name) }
        head.addView(expand)
        val value = TextView(this).apply {
            text = "--- " + unit
            textSize = 24f
            setTextColor(cText())
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        row.addView(head)
        row.addView(value)
        paramRows[name] = row
        paramValueViews[name] = value
        paramStatusViews[name] = status

        // Multi-Select (pkt 17.2): max 4; nadmiar FIFO albo blokada z tostem
        // (dokument nierozstrzygniety -> konfigurowalne, domyslnie FIFO)
        row.setOnClickListener {
            if (selection.contains(name)) {
                selection.remove(name)
            } else {
                if (selection.size >= 4) {
                    if (!overflowFifo) {
                        Toast.makeText(this, "Maksymalnie 4 wykresy",
                            Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    selection.removeAt(0)
                }
                selection.add(name)
            }
            rebuildCharts()
            highlightSelection()
        }
        return row
    }

    /** Mini-przycisk w stylu docka (kontrolki wykresow). */
    private fun miniBtn(label: String, onClick: (View) -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 10f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(cBorder())
            }
            setOnClickListener { onClick(this) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 8, 8) }
        }

    /** Przelaczanie zakresu osi czasu (pkt 18.4) - dotyczy wszystkich wykresow. */
    private fun applyRange() {
        rangeBtn.text = "ZAKRES: " + when (chartRangeMs) {
            0L -> "CALA TRASA"
            60_000L -> "1 MIN"
            300_000L -> "5 MIN"
            else -> "30 S"
        }
        for (ch in charts.values) ch.timeRangeMs = chartRangeMs
        overlayChart?.timeRangeMs = chartRangeMs
    }

    /** Eksport PNG (pkt 18.6): wszystkie widoczne wykresy, z legenda i data. */
    private fun exportCharts() {
        if (charts.isEmpty()) {
            Toast.makeText(this, "Brak wykresow do eksportu", Toast.LENGTH_SHORT).show()
            return
        }
        var ok = 0
        var first: String? = null
        for (ch in charts.values) {
            val p = ch.exportPng()
            if (p != null) {
                ok++
                if (first == null) first = p
            }
        }
        if (ok == 0) {
            Toast.makeText(this, "Brak danych do eksportu (pkt 47.5)", Toast.LENGTH_SHORT).show()
        } else {
            val dir = first?.substringBeforeLast('/')
            Toast.makeText(this, "Zapisano $ok PNG: $dir", Toast.LENGTH_LONG).show()
        }
    }

    private fun rebuildCharts() {
        chartsArea.removeAllViews()
        charts.clear()
        cells.clear()
        overlayChart = null
        ribbon = null
        if (overlayMode) {
            buildOverlay()
            return
        }
        // pkt 17.4: rozwiniecie parametru wygrywa z matryca wykresow
        expandedParam?.let { name -> buildExpanded(name); return }
        if (selection.isEmpty()) {
            chartsArea.addView(TextView(this).apply {
                text = "Dotknij parametr, aby pokazac wykres (maks. 4)"
                setTextColor(cCaption())
                textSize = 14f
                gravity = Gravity.CENTER
            })
            return
        }
        chartMatrix(selection.size)
        for ((i, name) in selection.withIndex()) {
            val chart = ChartView(this)
            chart.label = name
            chart.unit = params[name] ?: ""
            if (i < cells.size) cells[i].addView(chart,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT))
            chart.timeRangeMs = chartRangeMs
            charts[name] = chart
        }
    }

    /**
     * Pelny ekran pojedynczego parametru (pkt 17.4): jeden wykres na
     * calej dostepnej przestrzeni roboczej + ZAMKNIJ. Wykres jest w
     * mapie charts, wiec telemetria plynie bez zmian.
     */
    private fun buildExpanded(name: String) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 0)
        }
        head.addView(TextView(this).apply {
            text = name + " (" + (params[name] ?: "") + ")"
            textSize = 18f
            setTextColor(cText())
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        head.addView(miniBtn("ZAMKNIJ") {
            expandedParam = null
            rebuildCharts()
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(head)
        val chart = ChartView(this)
        chart.label = name
        chart.unit = params[name] ?: ""
        chart.timeRangeMs = chartRangeMs
        col.addView(chart, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        chartsArea.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
        charts[name] = chart
    }

    /** Rozwiniecie parametru (pkt 17.4) - ikona strzalki w wierszu listy. */
    private fun expandParam(name: String) {
        expandedParam = name
        rebuildCharts()
    }

    /** Matryca wykresow (pkt 18): 1 / 2 pionowo / 1+2 / 2x2. */
    private fun chartMatrix(n: Int) {
        when (n) {
            1 -> splitRow(hRow(), 1)
            2 -> { splitRow(hRow(), 1); splitRow(hRow(), 1) }
            3 -> { splitRow(hRow(), 1); splitRow(hRow(), 2) }
            else -> { val r1 = hRow(); splitRow(r1, 2)
                      val r2 = hRow(); splitRow(r2, 2) }
        }
    }

    private fun hRow(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            chartsArea.addView(this)
        }

    private fun splitRow(row: LinearLayout, cols: Int) {
        for (i in 0 until cols) {
            val cell = FrameLayout(this)
            row.addView(cell, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            cells.add(cell)
        }
    }

    private val cells = ArrayList<FrameLayout>()

    /** Zaznaczenie = ramka w kolorze akcentu (pkt 17.2). */
    private fun highlightSelection() {
        for ((name, row) in paramRows) {
            (row.background as? GradientDrawable)?.setStroke(
                if (selection.contains(name)) 3 else 1,
                if (selection.contains(name)) accent else cBorder()
            )
        }
    }

    private fun showOverview() {
        overviewPage.visibility = View.VISIBLE
        livePage.visibility = View.GONE
    }

    private fun showLive() {
        overviewPage.visibility = View.GONE
        livePage.visibility = View.VISIBLE
        if (selection.isEmpty()) rebuildCharts()
        highlightSelection()
    }

    // ---------- DANE ----------

    private fun observe() {
        scope.launch {
            manager.uiState.collectLatest { s -> render(s) }
        }
    }

    private fun render(s: VehicleUiState) {
        ovRpm.text = if (s.rpm >= 0) s.rpm.toString() else "---"
        ovSpeed.text = if (s.speedKmh >= 0) s.speedKmh.toString() + " km/h" else "---"
        ovVolt.text = if (s.voltage.isNaN()) "---" else String.format("%.1f V", s.voltage)
        ovEct.text = if (s.ectC.isNaN()) "---" else String.format("%.0f C", s.ectC)
        // DTC z PID 0101 (pkt 16: karta z licznikiem; tap -> DTC Manager)
        ovDtc.text = when {
            s.milOn -> "MIL " + (if (s.dtcCount > 0) s.dtcCount else "!")
            s.dtcCount > 0 -> s.dtcCount.toString()
            s.dtcCount == 0 -> "0"
            else -> "---"
        }
        ovDtc.setTextColor(if (s.milOn || s.dtcCount > 0)
            Color.parseColor("#FF453A") else cText())
        ovConn.text = when {
            s.connected -> "OBD OK"
            s.connecting -> "LACZENIE..."
            else -> "OFFLINE"
        }
        ovConn.setTextColor(when {
            s.connected -> Color.parseColor("#30D158")
            s.connecting -> Color.parseColor("#FF9F0A")
            else -> Color.parseColor("#8E8E93")
        })

        val now = System.currentTimeMillis()
        updateParam("RPM", s.rpm.toFloat(), "obr/min", s.rpm >= 0, s.staleFlags.rpm)
        updateParam("SPEED", s.speedKmh.toFloat(), "km/h", s.speedKmh >= 0, s.staleFlags.speed)
        updateParam("LOAD", s.engineLoadPct, "%", !s.engineLoadPct.isNaN(), s.staleFlags.load)
        updateParam("TPS", s.tpsPct, "%", !s.tpsPct.isNaN(), s.staleFlags.tps)
        updateParam("MAP", s.mapKpa, "kPa", !s.mapKpa.isNaN(), s.staleFlags.map)
        updateParam("ECT", s.ectC, "C", !s.ectC.isNaN(), s.staleFlags.ect)
        updateParam("IAT", s.iatC, "C", !s.iatC.isNaN(), s.staleFlags.iat)
        updateParam("STFT", s.stftPct, "%", !s.stftPct.isNaN(), s.staleFlags.stft)
        updateParam("LTFT", s.ltftPct, "%", !s.ltftPct.isNaN(), s.staleFlags.ltft)
        updateParam("VOLT", s.voltage, "V", !s.voltage.isNaN(), false)

        if (!s.staleFlags.rpm) pushChart("RPM", s.rpm.toFloat(), now)
        if (!s.staleFlags.speed) pushChart("SPEED", s.speedKmh.toFloat(), now)
        if (!s.ectC.isNaN() && !s.staleFlags.ect) pushChart("ECT", s.ectC, now)
        if (!s.ltftPct.isNaN() && !s.staleFlags.ltft) pushChart("LTFT", s.ltftPct, now)
        if (!s.stftPct.isNaN() && !s.staleFlags.stft) pushChart("STFT", s.stftPct, now)
        if (!s.tpsPct.isNaN() && !s.staleFlags.tps) pushChart("TPS", s.tpsPct, now)
        if (!s.mapKpa.isNaN() && !s.staleFlags.map) pushChart("MAP", s.mapKpa, now)
        if (!s.iatC.isNaN() && !s.staleFlags.iat) pushChart("IAT", s.iatC, now)
        if (!s.voltage.isNaN()) pushChart("VOLT", s.voltage, now)

        if (overlayMode) pushOverlay(s, now)
    }

    /** Status parametru (pkt 17.1) - bez fabrykacji: brak danych pozostaje neutralny. */
    private fun updateParam(name: String, v: Float, unit: String, have: Boolean, stale: Boolean) {
        val tv = paramValueViews[name] ?: return
        val st = paramStatusViews[name] ?: return
        tv.text = if (!have) "--- " + unit else fmt(v) + " " + unit
        when {
            !have -> { st.text = "BRAK DANYCH"; st.setTextColor(cCaption()) }
            stale -> { st.text = "NIEAKTUALNE"; st.setTextColor(Color.parseColor("#FF9F0A")) }
            inNormalRange(name, v) -> { st.text = "NORMAL"; st.setTextColor(Color.parseColor("#30D158")) }
            isCritical(name, v) -> { st.text = "KRYTYCZNY"; st.setTextColor(Color.parseColor("#FF453A")) }
            else -> { st.text = "POZA ZAKRESEM"; st.setTextColor(Color.parseColor("#FF9F0A")) }
        }
        // pkt 17.3 Auto-Sort by Anomaly: parametr poza norma wchodzi na
        // poczatek listy; kolejnosc pozostalych parametrow pozostaje
        // stabilna. Nie wraca automatycznie (bez migotania UI).
        val anomaly = have && !stale && !inNormalRange(name, v)
        if (anomaly && autoSort && wasAnomaly[name] != true) {
            paramRows[name]?.let { row ->
                paramList.removeView(row)
                paramList.addView(row, 0)
            }
        }
        wasAnomaly[name] = anomaly
    }

    private fun inNormalRange(name: String, v: Float): Boolean = when (name) {
        "ECT" -> v in 70f..102f
        "IAT" -> v in 5f..65f
        "VOLT" -> v in 11.8f..14.8f
        "LTFT" -> v >= -10f && v <= 10f
        "STFT" -> v >= -10f && v <= 10f
        "MAP" -> v >= 10f && v <= 100f
        else -> true
    }

    private fun isCritical(name: String, v: Float): Boolean = when (name) {
        "ECT" -> v > 115f
        "VOLT" -> v < 10.5f
        "LTFT" -> v > 20f || v < -20f
        else -> false
    }

    // ---------- Multi-Overlay + Correlation Ribbon (pkt 19) ----------

    /** Overlay pokazuje ZAZNACZONE parametry (pkt 19: wybor parametrow). */
    private fun toggleOverlay() {
        overlayMode = !overlayMode
        overlayBtn.text = if (overlayMode) "OVERLAY: ON" else "OVERLAY: OFF"
        rebuildCharts()
    }

    private fun buildOverlay() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val oc = OverlayChartView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            timeRangeMs = chartRangeMs
        }
        for (name in params.keys) oc.setUnit(name, params[name] ?: "")
        overlayChart = oc
        ribbon = TextView(this).apply {
            text = "[BRAK] Zbieram dane do analizy korelacji..."
            textSize = 12f
            setTextColor(cCaption())
            setPadding(16, 16, 16, 16)
            background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(cCard())
                setStroke(1, cBorder())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(12, 8, 12, 12) }
        }
        col.addView(oc)
        col.addView(ribbon)
        chartsArea.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun paramValueOf(name: String, s: VehicleUiState): Pair<Float, Boolean> = when (name) {
        "RPM" -> Pair(s.rpm.toFloat(), s.rpm >= 0)
        "SPEED" -> Pair(s.speedKmh.toFloat(), s.speedKmh >= 0)
        "LOAD" -> Pair(s.engineLoadPct, !s.engineLoadPct.isNaN())
        "TPS" -> Pair(s.tpsPct, !s.tpsPct.isNaN())
        "MAP" -> Pair(s.mapKpa, !s.mapKpa.isNaN())
        "ECT" -> Pair(s.ectC, !s.ectC.isNaN())
        "IAT" -> Pair(s.iatC, !s.iatC.isNaN())
        "STFT" -> Pair(s.stftPct, !s.stftPct.isNaN())
        "LTFT" -> Pair(s.ltftPct, !s.ltftPct.isNaN())
        "VOLT" -> Pair(s.voltage, !s.voltage.isNaN())
        else -> Pair(Float.NaN, false)
    }

    /**
     * Push do overlay + Correlation Ribbon: UI prezentuje wylacznie
     * tekst modulu analitycznego (pkt 19 - UI nie wymysla diagnozy).
     * Analiza odswiezana najwyzej co 5 s, ze nie przeciazac UI.
     */
    private fun pushOverlay(s: VehicleUiState, now: Long) {
        val oc = overlayChart ?: return
        for (name in selection) {
            val (v, have) = paramValueOf(name, s)
            if (have && now - lastOvPushMs.getOrPut(name) { 0L } >= 250) {
                lastOvPushMs[name] = now
                oc.push(name, v, now)
            }
        }
        if (now - lastAnalysisMs >= 5000) {
            lastAnalysisMs = now
            val rep = CorrelationAnalyzer.analyze(oc.snapshot())
            ribbon?.text = "[" + rep.status + "] " + rep.text
            ribbon?.setTextColor(when (rep.status) {
                CorrelationAnalyzer.STATUS_INFO -> Color.parseColor("#FF9F0A")
                CorrelationAnalyzer.STATUS_OK -> Color.parseColor("#30D158")
                else -> cCaption()
            })
        }
    }

    private fun pushChart(name: String, v: Float, t: Long) {
        val ch = charts[name] ?: return
        if (ch.sampleCount == 0 || t - lastPushMs.getOrPut(name) { 0L } >= 500) {
            lastPushMs[name] = t
            ch.push(v, t)
        }
    }

    private val lastPushMs = HashMap<String, Long>()

    private fun fmt(v: Float): String =
        if (v >= 100f || v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); manager.onAppForeground() }
    override fun onStop() { super.onStop(); manager.onAppBackground() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
