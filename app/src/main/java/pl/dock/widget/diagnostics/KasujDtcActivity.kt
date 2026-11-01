package pl.dock.widget.diagnostics

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import pl.dock.widget.ObdCoreService
import pl.dock.widget.orchestration.ConnectionManager

/**
 * Osobny komponent startowy "Kasuj DTC" (wlasna ikona w launcherze).
 * Nacisniecie kasuje kody natychmiast - bez dodatkowych dialogow.
 * Po operacji nastepuje automatyczny ponowny odczyt i zamkniecie ekranu.
 */
class KasujDtcActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = ConnectionManager.get(applicationContext)
        val tv = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            text = "Kasowanie kodow DTC..."
        }
        setContentView(tv)

        ObdCoreService.start(this)

        MainScope().launch {
            val ok = Dtc.clearSucceeded(manager.rawRequest("04"))
            manager.rawRequest("03") // natychmiastowy ponowny odczyt po skasowaniu
            tv.text = if (ok) "KODY SKASOWANE" else "BRAK POLACZENIA / NIEPOTWIERDZONE"
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
        }
    }
}
