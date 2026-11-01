package pl.dock.widget.settings

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import pl.dock.widget.util.SolarTimes
import java.util.Calendar

/**
 * Ustawienia OBD - 5 zakladek:
 * 1) POLACZENIE  2) ZEGARY  3) DIAGNOSTYKA  4) DZIEN/NOC (NOAA)  5) INFO
 * Wartosci zapisywane w SharedPreferences "obd_settings" i czytane
 * przez zegary UFO / scheduler przy kolejnym starcie.
 */
class ObdSettingsActivity : Activity() {

    private lateinit var pages: Array<View>
    private lateinit var tabs: Array<Button>
    private var pageIndex = 0

    private fun sp() = getSharedPreferences("obd_settings", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this@ObdSettingsActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(20, 32, 20, 16)
        }

        // pasek zakladek
        val tabBar = LinearLayout(this@ObdSettingsActivity).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = arrayOf("POLACZ", "ZEGARY", "DIAG", "DZIEN/NOC", "INFO")
        tabs = Array(5) { i ->
            Button(this@ObdSettingsActivity).apply {
                text = labels[i]
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setOnClickListener { showTab(i) }
                background = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(Color.parseColor("#2C2C2E"))
                }
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(4, 4, 4, 4)
                }
            }.also { tabBar.addView(it) }
        }
        root.addView(tabBar)

        pages = arrayOf(
            buildConnPage(), buildGaugesPage(), buildDiagPage(),
            buildNightPage(), buildInfoPage()
        )
        val container = FrameLayout(this@ObdSettingsActivity)
        for (p in pages) {
            container.addView(p, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
            p.visibility = View.GONE
        }
        root.addView(container, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        showTab(0)
    }

    private fun showTab(i: Int) {
        pageIndex = i
        for (j in pages.indices) {
            pages[j].visibility = if (j == i) View.VISIBLE else View.GONE
            (tabs[j].background as GradientDrawable).setColor(
                if (j == i) Color.parseColor("#0A84FF") else Color.parseColor("#2C2C2E"))
        }
    }

    private fun page(): ScrollView = ScrollView(this@ObdSettingsActivity).apply {
        addView(LinearLayout(this@ObdSettingsActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 20, 8, 20)
        })
    }

    private fun pageBody(p: View): LinearLayout = (p as ScrollView).getChildAt(0) as LinearLayout

    private fun header(text: String): TextView = TextView(this@ObdSettingsActivity).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.WHITE)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(0, 16, 0, 8)
    }

    private fun caption(text: String): TextView = TextView(this@ObdSettingsActivity).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#8E8E93"))
        setPadding(4, 4, 4, 12)
    }

    private fun numField(key: String, hint: String): EditText {
        val et = EditText(this@ObdSettingsActivity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8E8E93"))
            this.hint = hint
            setText(sp().getString(key, ""))
            textSize = 15f
        }
        et.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                sp().edit().putString(key, et.text.toString()).apply()
                toast("Zapisano")
            }
        }
        return et
    }

    private fun toggleBtn(key: String, label: String): Button {
        val btn = Button(this@ObdSettingsActivity).apply {
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = 16f }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        fun refresh() {
            val on = sp().getBoolean(key, true)
            (btn.background as GradientDrawable).setColor(
                if (on) Color.parseColor("#30D158") else Color.parseColor("#2C2C2E"))
            btn.text = label + ": " + if (on) "WL" else "WYL"
        }
        btn.setOnClickListener {
            sp().edit().putBoolean(key, !sp().getBoolean(key, true)).apply()
            refresh()
        }
        refresh()
        return btn
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---- Zakladka 1: POLACZENIE ----
    private fun buildConnPage(): View {
        val p = page(); val b = pageBody(p)
        b.addView(header("Polaczenie adaptera"))
        b.addView(caption("Preferowany transport (zmiana zadziala przy nastepnym polaczeniu)"))
        b.addView(toggleBtn("prefer_ble", "Bluetooth LE"))
        b.addView(caption("Adres MAC adaptera zapamietany przez ConnectionManager"))
        b.addView(numField("adapter_mac", "np. 00:1D:A5:..."))
        b.addView(caption("Ochrona przed rozruchem: blokada zapytan przy V < 11 V / 1-450 RPM"))
        b.addView(toggleBtn("cranking_protect", "Ochrona rozruchu"))
        return p
    }

    // ---- Zakladka 2: ZEGARY ----
    private fun buildGaugesPage(): View {
        val p = page(); val b = pageBody(p)
        b.addView(header("Zegary UFO - progi"))
        b.addView(caption("Obrotomierz: obroty alarmowe (przekroczenie = czerwona wskazowka)"))
        val rl = numField("redline_rpm", "6500"); b.addView(rl)
        b.addView(caption("VTEC: obroty wlaczenia (podswietlenie niebieskie)"))
        b.addView(numField("vtec_rpm", "5200"))
        b.addView(caption("REV bar: progi segmentow 4200 / 5100 / 6000 / 6300 (steruje klaster)"))
        b.addView(header("ECO"))
        b.addView(caption("ECO zapala sie przy: TPS < 18%, MAP < 48 kPa, RPM < 2800"))
        b.addView(numField("eco_tps", "18"))
        b.addView(numField("eco_map", "48"))
        b.addView(numField("eco_rpm", "2800"))
        return p
    }

    // ---- Zakladka 3: DIAGNOSTYKA ----
    private fun buildDiagPage(): View {
        val p = page(); val b = pageBody(p)
        b.addView(header("Alerty i diagnostyka"))
        b.addView(caption("Sygnal zmiany biegow (shift light) przy redline"))
        b.addView(toggleBtn("shift_light", "Shift light"))
        b.addView(caption("Ostrzezenie ECT > 102 C"))
        b.addView(toggleBtn("ect_alert", "Alert temperatury"))
        b.addView(caption("Analityka w tle: VirtualDyno, MechanicalHealthMonitor, MisfireAnalyzer"))
        b.addView(toggleBtn("analytics", "Analityka"))
        b.addView(caption("Czarna skrzynka: eksport CSV przez http://<IP-radia>:8080/csv (WAL-safe)"))
        b.addView(toggleBtn("blackbox", "Czarna skrzynka"))
        return p
    }

    // ---- Zakladka 4: DZIEN/NOC (NOAA) ----
    private fun buildNightPage(): View {
        val p = page(); val b = pageBody(p)
        b.addView(header("Dzien / noc wg slonca (NOAA)"))
        b.addView(caption("Wpolrzedne (dlugosc ustawienia - auto przejscie motywu noca)"))
        b.addView(numField("lat", "52.23"))
        b.addView(numField("lon", "21.01"))
        b.addView(toggleBtn("night_auto", "Tryb automatyczny"))
        val status = TextView(this@ObdSettingsActivity).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#E5E5EA"))
            setPadding(4, 20, 4, 0)
        }
        b.addView(status)
        val calcBtn = Button(this@ObdSettingsActivity).apply {
            text = "PRZELICZ TERAZ"
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#0A84FF"))
            }
            setOnClickListener {
                val lat = sp().getString("lat", "52.23")?.toDoubleOrNull() ?: 52.23
                val lon = sp().getString("lon", "21.01")?.toDoubleOrNull() ?: 21.01
                val ss = SolarTimes.sunriseSunset(lat, lon, Calendar.getInstance())
                status.text = if (ss == null) {
                    "Doba polarna - uzywaj przejscia po godzinach"
                } else {
                    "Wschod: " + SolarTimes.fmtMinutes(ss[0]) +
                        "  Zachod: " + SolarTimes.fmtMinutes(ss[1]) + "\n" +
                        "Teraz: " + (if (SolarTimes.isNight(lat, lon)) "NOC" else "DZIEN")
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        b.addView(calcBtn)
        calcBtn.performClick()
        return p
    }

    // ---- Zakladka 5: INFO ----
    private fun buildInfoPage(): View {
        val p = page(); val b = pageBody(p)
        b.addView(header("Dock-Launcher-OBD"))
        b.addView(caption(
            "Honda Civic VIII UFO 1.4 L13Z1 + radio ESSGOO Android 9 (MT8163).\n\n" +
                "Stos: SPP/BLE -> ElmSession (ATZ/ATE0/ATSP6/ATH1/ATAT1) -> PidScheduler " +
                "(100/500/2000 ms) -> ConnectionManager (StateFlow).\n\n" +
                "Czarna skrzynka: SQLite WAL, 300 ramek w RAM, eksport CSV przez NanoServer :8080.\n\n" +
                "Zakaz fabryzacji: UI pokazuje wylacznie dane zwrocone przez adapter."))
        return p
    }
}
