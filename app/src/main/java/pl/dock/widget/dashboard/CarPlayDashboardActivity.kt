package pl.dock.widget.dashboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.dock.widget.LauncherActivity
import pl.dock.widget.diagnostics.DtcManagerActivity
import pl.dock.widget.diagnostics.MisfireLabActivity
import pl.dock.widget.orchestration.ConnectionManager
import pl.dock.widget.repository.VehicleUiState
import pl.dock.widget.ui.ChartView

/**
 * CarPlay Dashboard: staly pionowy dock (~110dp) po lewej + obszar roboczy.
 * Moduly: Overview, Live Data (Multi-Select max 4 wykresy), Misfire Lab,
 * DTC Manager, Home. Karty #1C1C1E, radius 16dp, obramowanie #2C2C2E.
 */
class CarPlayDashboardActivity : Activity() {

    private lateinit var manager: ConnectionManager

    // Overview
    private lateinit var ovRpm: TextView
    private lateinit var ovSpeed: TextView
    private lateinit var ovVolt: TextView
    private lateinit var ovEct: TextView
    private lateinit var ovConn: TextView
    private lateinit var overviewPage: View

    // Live Data
    private lateinit var livePage: View
    private lateinit var paramList: LinearLayout
    private lateinit var chartsArea: FrameLayout
    private val paramRows = HashMap<String, LinearLayout>()
    private val paramValueViews = HashMap<String, TextView>()
    private val paramStatusViews = HashMap<String, TextView>()
    private val charts = HashMap<String, ChartView>()
    private val selection = ArrayList<String>() // FIFO, max 4

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = ConnectionManager.get(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        root.addView(buildDock())
        root.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            addView(buildOverviewPage())
            addView(buildLivePage())
        })
        setContentView(root)

        showOverview()
        observe()
    }

    // ---------- DOCK ----------

    private fun buildDock(): View {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C1C1E"))
            layoutParams = LinearLayout.LayoutParams(dp(110),
                LinearLayout.LayoutParams.MATCH_PARENT)
        }
        dock.addView(dockBtn("OVERVIEW") { showOverview() })
        dock.addView(dockBtn("LIVE DATA") { showLive() })
        dock.addView(dockBtn("MISFIRE") { startActivity(Intent(this, MisfireLabActivity::class.java)) })
        dock.addView(dockBtn("DTC") { startActivity(Intent(this, DtcManagerActivity::class.java)) })
        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(1, 0, 1f)
        dock.addView(spacer)
        dock.addView(dockBtn("HOME") {
            startActivity(Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        })
        return dock
    }

    private fun dockBtn(label: String, onClick: (View) -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#2C2C2E"))
            }
            setOnClickListener { onClick(this) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 72
            ).apply { setMargins(8, 8, 8, 0) }
        }

    // ---------- OVERVIEW ----------

    private fun buildOverviewPage(): View {
        overviewPage = ScrollView(this).apply {
            addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 36, 28, 28)
                addView(TextView(this).apply {
                    text = "OVERVIEW"
                    textSize = 24f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(0, 0, 0, 20)
                })
            })
        }
        val col = (overviewPage as ScrollView).getChildAt(0) as LinearLayout

        // duza karta: RPM + SPEED
        val bigCard = card()
        bigCard.orientation = LinearLayout.HORIZONTAL
        val bigColL = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ovRpm = bigValue("RPM")
        bigColL.addView(ovRpm)
        val bigColR = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ovSpeed = bigValue("SPEED")
        bigColR.addView(ovSpeed)
        bigCard.addView(bigColL, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bigCard.addView(bigColR, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(bigCard)

        // mniejsze karty
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ovVolt = bigValue("VOLTAGE")
        ovEct = bigValue("ECT")
        row1.addView(cardWith(ovVolt), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(cardWith(ovEct), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(row1)

        // status polaczenia + DTC
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ovConn = bigValue("OBD")
        row2.addView(cardWith(ovConn), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(row2)
        return overviewPage
    }

    private fun bigValue(caption: String): TextView =
        TextView(this).apply {
            text = "---"
            textSize = 40f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            tag = caption
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = 16f
            setColor(Color.parseColor("#1C1C1E"))
            setStroke(1, Color.parseColor("#2C2C2E"))
        }
        setPadding(24, 20, 24, 20)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 }
    }

    private fun cardWith(value: TextView): LinearLayout {
        val c = card()
        c.addView(TextView(this).apply {
            text = value.tag as String
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
        })
        c.addView(value)
        return c
    }

    // ---------- LIVE DATA ----------

    private fun buildLivePage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        // lista parametrow po lewej
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        paramList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 24, 12, 24)
        }
        scroll.addView(paramList)
        for ((name, unit) in params) paramList.addView(paramRow(name, unit))

        // wykresy po prawej
        chartsArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.6f)
        }
        page.addView(scroll)
        page.addView(chartsArea)
        livePage = page
        return page
    }

    private fun paramRow(name: String, unit: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#1C1C1E"))
                setStroke(1, Color.parseColor("#2C2C2E"))
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
            setTextColor(Color.WHITE)
        })
        val status = TextView(this).apply {
            text = "BRAK DANYCH"
            textSize = 11f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = Gravity.END
        }
        head.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val value = TextView(this).apply {
            text = "--- " + unit
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        row.addView(head)
        row.addView(value)
        paramRows[name] = row
        paramValueViews[name] = value
        paramStatusViews[name] = status

        // Multi-Select: tap zaznacza/odznacza, max 4 (FIFO - najstarszy wypada)
        row.setOnClickListener {
            if (selection.contains(name)) {
                selection.remove(name)
            } else {
                if (selection.size >= 4) selection.removeAt(0)
                selection.add(name)
            }
            rebuildCharts()
            highlightSelection()
        }
        return row
    }

    private fun rebuildCharts() {
        chartsArea.removeAllViews()
        charts.clear()
        if (selection.isEmpty()) {
            chartsArea.addView(TextView(this).apply {
                text = "Dotknij parametr, aby pokazac wykres (maks. 4)"
                setTextColor(Color.parseColor("#8E8E93"))
                textSize = 14f
                gravity = Gravity.CENTER
            })
            return
        }
    private fun rebuildCharts() {
        chartsArea.removeAllViews()
        charts.clear()
        cells.clear()
        if (selection.isEmpty()) {
            chartsArea.addView(TextView(this).apply {
                text = "Dotknij parametr, aby pokazac wykres (maks. 4)"
                setTextColor(Color.parseColor("#8E8E93"))
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
            charts[name] = chart
        }
    }

    /**
     * Dynamiczna matryca wykresow:
     * 1 - pelny ekran; 2 - dwa pionowo; 3 - duzy na gorze + dwa male;
     * 4 - siatka 2x2. Wypelnia [cells] w kolejnosci dodawania.
     */
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

    private fun highlightSelection() {
        for ((name, row) in paramRows) {
            (row.background as? GradientDrawable)?.setStroke(
                if (selection.contains(name)) 3 else 1,
                if (selection.contains(name)) Color.parseColor("#0A84FF")
                else Color.parseColor("#2C2C2E")
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
        MainScope().launch {
            manager.uiState.collectLatest { s -> render(s) }
        }
    }

    private fun render(s: VehicleUiState) {
        ovRpm.text = if (s.rpm >= 0) s.rpm.toString() else "---"
        ovSpeed.text = if (s.speedKmh >= 0) s.speedKmh.toString() + " km/h" else "---"
        ovVolt.text = if (s.voltage.isNaN()) "---" else String.format("%.1f V", s.voltage)
        ovEct.text = if (s.ectC.isNaN()) "---" else String.format("%.0f C", s.ectC)
        ovConn.text = when {
            s.connected -> "POLACZONO"
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

        // probki do wykresow tylko dla parametrow z wiarygodnym odczytem
        if (!s.staleFlags.rpm) pushChart("RPM", s.rpm.toFloat(), now)
        if (!s.staleFlags.speed) pushChart("SPEED", s.speedKmh.toFloat(), now)
        if (!s.ectC.isNaN() && !s.staleFlags.ect) pushChart("ECT", s.ectC, now)
        if (!s.ltftPct.isNaN() && !s.staleFlags.ltft) pushChart("LTFT", s.ltftPct, now)
        if (!s.stftPct.isNaN() && !s.staleFlags.stft) pushChart("STFT", s.stftPct, now)
        if (!s.tpsPct.isNaN() && !s.staleFlags.tps) pushChart("TPS", s.tpsPct, now)
        if (!s.mapKpa.isNaN() && !s.staleFlags.map) pushChart("MAP", s.mapKpa, now)
        if (!s.iatC.isNaN() && !s.staleFlags.iat) pushChart("IAT", s.iatC, now)
        if (!s.voltage.isNaN()) pushChart("VOLT", s.voltage, now)
    }

    private fun updateParam(name: String, v: Float, unit: String, have: Boolean, stale: Boolean) {
        val tv = paramValueViews[name] ?: return
        val st = paramStatusViews[name] ?: return
        tv.text = if (!have) "--- " + unit else fmt(v) + " " + unit
        when {
            !have -> { st.text = "BRAK DANYCH"; st.setTextColor(Color.parseColor("#8E8E93")) }
            stale -> { st.text = "NIEAKTUALNE"; st.setTextColor(Color.parseColor("#FF9F0A")) }
            inNormalRange(name, v) -> { st.text = "NORMAL"; st.setTextColor(Color.parseColor("#30D158")) }
            isCritical(name, v) -> { st.text = "KRYTYCZNY"; st.setTextColor(Color.parseColor("#FF453A")) }
            else -> { st.text = "POZA ZAKRESEM"; st.setTextColor(Color.parseColor("#FF9F0A")) }
        }
    }

    /** Zakresy referencyjne (konfigurowalne docelowo w Ustawieniach). */
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

    private fun pushChart(name: String, v: Float, t: Long) {
        val ch = charts[name] ?: return
        // ogranicz czestotliwosc przerysowania (wykres sam interpoluje w czasie)
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
}
