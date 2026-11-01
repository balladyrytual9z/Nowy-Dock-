package pl.dock.widget.diagnostics

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import pl.dock.widget.orchestration.ConnectionManager

/**
 * Misfire Lab: cztery slupki cylinderow 1-4 na bazie Mode 06 (MID Keihin).
 * Wyswietla wylacznie dane zwrocone przez adapter - przy braku odpowiedzi
 * slupek pokazuje "N/D", nigdy nie pokazuje zmyslonej wartosci.
 */
class MisfireLabActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var analyzer: MisfireAnalyzer
    private lateinit var statusTv: TextView
    private lateinit var bars: Array<TextView?>
    private lateinit var counters: Array<TextView?>
    private lateinit var dtcTv: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false

    private val poller = object : Runnable {
        override fun run() {
            if (!resumed) return
            pollOnce()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = ConnectionManager.get(applicationContext)
        analyzer = MisfireAnalyzer()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(32, 40, 32, 24)
        }

        val title = TextView(this).apply {
            text = "MISFIRE LAB"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        statusTv = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 8, 0, 16)
        }

        root.addView(title)
        root.addView(backBtn())
        root.addView(statusTv)

        val barsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bars = arrayOfNulls(4)
        counters = arrayOfNulls(4)
        for (c in 0 until 4) barsRow.addView(cylinderColumn(c + 1))
        root.addView(barsRow)

        dtcTv = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#FF453A"))
            setPadding(0, 24, 0, 0)
        }
        root.addView(dtcTv)

        val pollBtn = Button(this).apply {
            text = "ODSWIEZ (MODE 06)"
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#0A84FF"))
            }
            setOnClickListener { pollOnce() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 96
            ).apply { topMargin = 20 }
        }
        root.addView(pollBtn)

        setContentView(root)
        statusTv.text = "Oczekiwanie na dane Mode 06..."
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        handler.post(poller)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        handler.removeCallbacks(poller)
    }

    private fun backBtn(): Button =
        Button(this).apply {
            text = "WSTECZ (ZAMKNIJ)"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 13f
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#2C2C2E"))
            }
            setOnClickListener { finish() }
        }

    private fun cylinderColumn(cyl: Int): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val counter = TextView(this).apply {
            text = "N/D"
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#E5E5EA"))
            gravity = Gravity.CENTER
        }
        val bar = TextView(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 10f
                setColor(Color.parseColor("#1C1C1E"))
                setStroke(1, Color.parseColor("#2C2C2E"))
            }
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(140, 240)
        }
        val label = TextView(this).apply {
            text = "CYL " + cyl
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        }
        counters[cyl - 1] = counter
        bars[cyl - 1] = bar
        col.addView(counter)
        col.addView(bar)
        col.addView(label)
        return col
    }

    private fun pollOnce() {
        MainScope().launch {
            val results = analyzer.poll { cmd -> manager.rawRequest(cmd) }
            val dtcs = Dtc.parse(manager.rawRequest("03"), pending = false)
                .filter { it.code in setOf("P0300","P0301","P0302","P0303","P0304") }

            statusTv.text = if (results.isEmpty()) {
                "Mode 06: brak danych (adapter nie odpowiada lub monitor niedostepny)"
            } else {
                "Mode 06: " + results.size + " wynikow monitorow"
            }

            for (c in 0 until 4) {
                val cyl = c + 1
                val r = results.firstOrNull { it.mid == cyl }
                val bar = bars[c]
                val counter = counters[c]
                if (r == null) {
                    counter?.text = "N/D"
                    (bar?.background as? GradientDrawable)?.setColor(Color.parseColor("#1C1C1E"))
                } else {
                    counter?.text = r.value.toString()
                    val color = when {
                        r.failed -> Color.parseColor("#FF453A")
                        r.value > 0 -> Color.parseColor("#FF9F0A")
                        else -> Color.parseColor("#30D158")
                    }
                    (bar?.background as? GradientDrawable)?.setColor(color)
                }
            }

            dtcTv.text = if (dtcs.isEmpty()) "" else
                "Kody zaplonu: " + dtcs.joinToString(", ") { it.code }
        }
    }
}
