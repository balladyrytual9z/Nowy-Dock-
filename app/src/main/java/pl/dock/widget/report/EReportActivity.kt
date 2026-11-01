package pl.dock.widget.report

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.dock.widget.blackbox.AppDatabase
import pl.dock.widget.diagnostics.Dtc
import pl.dock.widget.orchestration.ConnectionManager

/**
 * E-RAPORT: zrzut stanu pojazdu do udostepnienia (share intent).
 * Raport zawiera wylacznie zmierzone wartosci - brak danych oznaczany "-".
 */
class EReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = ConnectionManager.get(applicationContext)
        val db = AppDatabase(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(28, 36, 28, 20)
        }
        root.addView(TextView(this).apply {
            text = "E-RAPORT"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        val body = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#E5E5EA"))
            setPadding(0, 16, 0, 16)
            text = "Zbieranie danych raportu..."
        }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val shareBtn = Button(this).apply {
            text = "UDOSTEPNIJ RAPORT"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#0A84FF"))
            }
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, body.text.toString())
                }
                startActivity(Intent.createChooser(send, "E-RAPORT"))
            }
        }
        root.addView(shareBtn)
        setContentView(root)

        MainScope().launch {
            val s = manager.uiState.first()
            val confirmed = Dtc.parse(manager.rawRequest("03"), pending = false)
            val pending = Dtc.parse(manager.rawRequest("07"), pending = true)
            val frames = try { db.count() } catch (e: Exception) { -1L }

            val sb = StringBuilder()
            sb.append("=== E-RAPORT Dock-Launcher-OBD ===\n")
            sb.append("Pojazd: Honda Civic VIII UFO 1.4 L13Z1\n")
            sb.append("Data: ").append(java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())).append('\n')
            sb.append("Polaczenie OBD: ")
                .append(if (s.connected) "OK (" + s.adapterName + ")" else "BRAK").append('\n')
            sb.append("Protokol: ").append(if (s.protocol.isEmpty()) "-" else s.protocol).append('\n')
            sb.append("Stan silnika: ").append(s.engineState).append('\n')
            sb.append('\n')
            sb.append("--- Telemetria chwilowa ---\n")
            sb.append("Napiecie: ").append(f(s.voltage)).append(" V\n")
            sb.append("RPM: ").append(if (s.rpm >= 0) s.rpm else "-").append('\n')
            sb.append("Predkosc: ").append(if (s.speedKmh >= 0) s.speedKmh else "-").append(" km/h\n")
            sb.append("ECT: ").append(f(s.ectC)).append(" C\n")
            sb.append("IAT: ").append(f(s.iatC)).append(" C\n")
            sb.append("Obciazenie: ").append(f(s.engineLoadPct)).append(" %\n")
            sb.append("TPS: ").append(f(s.tpsPct)).append(" %\n")
            sb.append("MAP: ").append(f(s.mapKpa)).append(" kPa\n")
            sb.append("STFT: ").append(f(s.stftPct)).append(" %\n")
            sb.append("LTFT: ").append(f(s.ltftPct)).append(" %\n")
            sb.append('\n')
            sb.append("--- DTC ---\n")
            sb.append("Kody biezace: ").append(
                if (confirmed.isEmpty()) "brak" else confirmed.joinToString(", ") { it.code }).append('\n')
            sb.append("Kody oczekujace: ").append(
                if (pending.isEmpty()) "brak" else pending.joinToString(", ") { it.code }).append('\n')
            sb.append('\n')
            sb.append("--- Czarna skrzynka ---\n")
            sb.append("Zapisanych ramek: ").append(frames).append('\n')
            sb.append("Eksport: http://<IP-radia>:8080/csv\n")
            body.text = sb.toString()
        }
    }

    private fun f(v: Float): String = if (v.isNaN()) "-" else String.format("%.1f", v)
}
