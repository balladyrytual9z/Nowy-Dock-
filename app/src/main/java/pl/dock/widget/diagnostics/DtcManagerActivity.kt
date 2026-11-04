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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.dock.widget.orchestration.ConnectionManager

/**
 * DTC Manager: odczyt Mode 03 (confirmed) + 07 (pending), kasowanie Mode 04,
 * Freeze Frame (Mode 02) i VIN (Mode 09).
 * UI nigdy samo nie uznaje operacji za skuteczna - czeka na odpowiedz adaptera.
 * Zakaz fabryzacji: brak danych = wyswietlone "-", nigdy zmyslone wartosci.
 */
class DtcManagerActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var statusTv: TextView
    private lateinit var list: LinearLayout
    private lateinit var readBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var ffBtn: Button
    private lateinit var vinBtn: Button
    private var busy = false
    private val scope = MainScope()

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
        ffBtn = button("FREEZE FRAME (MODE 02)") { readFreezeFrame() }
        vinBtn = button("VIN (MODE 09)") { readVin() }
        clearBtn.isEnabled = false
        clearBtn.alpha = 0.4f

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply { addView(list) }

        root.addView(title)
        root.addView(backBtn())
        root.addView(statusTv)
        root.addView(readBtn)
        root.addView(clearBtn)
        root.addView(ffBtn)
        root.addView(vinBtn)
        root.addView(scroll)
        setContentView(root)

        statusTv.text = if (manager.uiState.value.connected)
            "OBD: polaczono" else "OBD: rozlaczono - najpierw polacz adapter"
        read()
    }

    private fun backBtn(): Button = button("WSTECZ (ZAMKNIJ)") { finish() }

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
        scope.launch {
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
        scope.launch {
            val ok = Dtc.clearSucceeded(manager.rawRequest("04"))
            busy = false
            statusTv.text = if (ok)
                "Kody skasowane - ponowny odczyt..."
                else "Kasowanie NIE potwierdzone - sprawdz polaczenie"
            read()
        }
    }

    /** Mode 02: ramka zapisana przez ECU w momencie ostatniej usterki. */
    private fun readFreezeFrame() {
        busy = true
        ffBtn.isEnabled = false
        statusTv.text = "Odczyt Freeze Frame (Mode 02)..."
        scope.launch {
            val raw = manager.rawRequest("02")
            busy = false
            ffBtn.isEnabled = true
            if (raw.isNullOrBlank() ||
                raw.contains("NO DATA", ignoreCase = true) ||
                raw.contains("UNABLE", ignoreCase = true)) {
                statusTv.text = "Freeze Frame: brak zapisanej ramki"
            } else {
                statusTv.text = "Freeze Frame odczytana"
                list.addView(infoCard("FREEZE FRAME (MODE 02)", "RAMKA ZAPISANA PRZY USTERCE", raw.trim()))
            }
        }
    }

    /** Mode 09: numer VIN pojazdu. */
    private fun readVin() {
        busy = true
        vinBtn.isEnabled = false
        statusTv.text = "Odczyt VIN (Mode 09)..."
        scope.launch {
            val raw = manager.rawRequest("0902")
            busy = false
            vinBtn.isEnabled = true
            val vin = parseVin(raw)
            statusTv.text = if (vin != null) "VIN odczytany" else "VIN niedostepny"
            list.addView(infoCard(
                "VIN (MODE 09)", "NUMER IDENTYFIKACYJNY",
                vin ?: "Brak odpowiedzi VIN z ECU - sprawdz polaczenie"
            ))
        }
    }

    /**
     * VIN w odpowiedzi Mode 09: 49 02 01 [17 bajtow ASCII jako hex].
     * Parsowane znak po znaku; nieudane parsowanie = null (bez fabryzacji).
     */
    private fun parseVin(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val hex = raw.replace(">", " ").replace(" ", "").uppercase()
        val i = hex.indexOf("490201")
        if (i < 0) return null
        val start = i + "490201".length
        if (hex.length < start + 34) return null
        val sb = StringBuilder()
        for (k in 0 until 17) {
            val b = try {
                hex.substring(start + k * 2, start + k * 2 + 2).toInt(16)
            } catch (_: Exception) { return null }
            if (b < 0x20 || b > 0x7E) return null   // tylko drukowalne ASCII
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun card(c: Dtc.Code): View {
        return infoCard(c.code, if (c.pending) "PENDING" else "CONFIRMED", c.description, true)
    }

    private fun infoCard(head: String, tag: String, body: String, red: Boolean = false): View {
        val accent = if (red) "#FF453A" else "#0A84FF"
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setStroke(2, Color.parseColor(accent))
                setColor(Color.parseColor("#1C1C1E"))
            }
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        val headTv = TextView(this).apply {
            text = head
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.parseColor(accent))
        }
        val tagTv = TextView(this).apply {
            text = tag
            textSize = 12f
            setTextColor(Color.parseColor("#FF9F0A"))
            gravity = Gravity.END
        }
        val headRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        headRow.addView(headTv)
        headRow.addView(tagTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val bodyTv = TextView(this).apply {
            text = body
            textSize = if (body.length > 60) 12f else 14f
            setTextColor(Color.parseColor("#E5E5EA"))
            setPadding(0, 8, 0, 0)
        }
        card.addView(headRow)
        card.addView(bodyTv)
        return card
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
