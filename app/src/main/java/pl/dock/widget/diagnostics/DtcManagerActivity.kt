package pl.dock.widget.diagnostics

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import pl.dock.widget.orchestration.ConnectionManager

/**
 * DTC Manager: odczyt Mode 03 (confirmed) + 07 (pending), kasowanie Mode 04.
 * UI nigdy samo nie uznaje operacji za skuteczna - czeka na odpowiedz adaptera.
 */
class DtcManagerActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var statusTv: TextView
    private lateinit var list: LinearLayout
    private lateinit var readBtn: Button
    private lateinit var clearBtn: Button
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = ConnectionManager.get(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(32, 40, 32, 24)
        }

        val title = TextView(this).apply {
            text = "DTC MANAGER"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        statusTv = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 12, 0, 16)
        }

        readBtn = button("ODCZYTAJ KODY") { read() }
        clearBtn = button("KASUJ WSZYSTKIE DTC") { clearAll() }
        clearBtn.isEnabled = false
        clearBtn.alpha = 0.4f

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply { addView(list) }

        root.addView(title)
        root.addView(statusTv)
        root.addView(readBtn)
        root.addView(clearBtn)
        root.addView(scroll)
        setContentView(root)

        statusTv.text = if (manager.uiState.value.connected)
            "OBD: polaczono" else "OBD: rozlaczono - najpierw polacz adapter"
        read()
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#0A84FF"))
            }
            setOnClickListener { if (!busy) onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 96
            ).apply { topMargin = 12 }
        }

    private fun read() {
        busy = true
        readBtn.isEnabled = false
        statusTv.text = "Odczyt kodow (Mode 03/07)..."
        list.removeAllViews()
        MainScope().launch {
            val confirmed = Dtc.parse(manager.rawRequest("03"), pending = false)
            val pending = Dtc.parse(manager.rawRequest("07"), pending = true)
            val all = confirmed + pending.filter { p -> confirmed.none { it.code == p.code } }
            busy = false
            readBtn.isEnabled = true
            clearBtn.isEnabled = all.isNotEmpty() && manager.uiState.value.connected
            clearBtn.alpha = if (clearBtn.isEnabled) 1f else 0.4f
            if (all.isEmpty()) {
                statusTv.text = "Brak zapisanych kodow usterek"
            } else {
                statusTv.text = "Kodow: " + all.size
                for (c in all) list.addView(card(c))
            }
        }
    }

    private fun clearAll() {
        busy = true
        clearBtn.isEnabled = false
        statusTv.text = "Kasowanie kodow (Mode 04)..."
        MainScope().launch {
            val ok = Dtc.clearSucceeded(manager.rawRequest("04"))
            busy = false
            statusTv.text = if (ok)
                "Kody skasowane - ponowny odczyt..."
                else "Kasowanie NIE potwierdzone - sprawdz polaczenie"
            read()
        }
    }

    private fun card(c: Dtc.Code): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#FF453A"))
                setColor(Color.parseColor("#1C1C1E"))
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        val head = TextView(this).apply {
            text = c.code
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.parseColor("#FF453A"))
        }
        val state = TextView(this).apply {
            text = if (c.pending) "PENDING" else "CONFIRMED"
            textSize = 12f
            setTextColor(Color.parseColor("#FF9F0A"))
            gravity = Gravity.END
        }
        val headRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        headRow.addView(head)
        headRow.addView(state, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val desc = TextView(this).apply {
            text = c.description
            textSize = 14f
            setTextColor(Color.parseColor("#E5E5EA"))
            setPadding(0, 8, 0, 0)
        }
        card.addView(headRow)
        card.addView(desc)
        return card
    }
}
