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

    // NAPRAWA "przycisk nie zapisuje": EditText zapisywal wartosc dopiero
    // przy utracie fokusu, ale na dotyku klik przycisku/zakladki NIE odbiera
    // fokusu, wiec wartosc nigdy nie byla zapisywana. Rejestrujemy jawne
    // savery i zapisujemy WSZYSTKIE pola przed kazda akcja + w onPause.
    private val fieldSavers = ArrayList<() -> Unit>()

    private fun saveAllFields() {
        for (s in fieldSavers) s()
    }

    override fun onPause() {
        saveAllFields()
        super.onPause()
    }

    private fun sp() = getSharedPreferences("obd_settings", MODE_PRIVATE)

    private fun backBtn(): Button =
        Button(this@ObdSettingsActivity).apply {
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
        root.addView(backBtn())

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
        saveAllFields()   // NAPRAWA: zmiana zakladki nie odbiera fokusu z pola
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
        fieldSavers.add { sp().edit().putString(key, et.text.toString()).apply() }
        return et
    }

    /**
     * Pole numeryczne z ulamkiem dziesietnym (np. 11.8 V) - TYPE_CLASS_NUMBER
     * bez flagi DECIMAL nie przyjmuje kropki/przecinka na ekranowej klawiaturze.
     */
    private fun numFieldDec(key: String, hint: String): EditText {
        val et = EditText(this@ObdSettingsActivity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8E8E93"))
            this.hint = hint
            setText(sp().getString(key, ""))
            textSize = 15f
        }
        fieldSavers.add { sp().edit().putString(key, et.text.toString()).apply() }
        return et
    }

    /**
     * Przycisk cykliczny dla opcji tekstowych (pkt 9.7 specyfikacji: akcja
     * tapniecia predkosciomierza ma byc czytelna, nie domyslnie domyslna).
     */
    private fun cycleBtn(key: String, label: String, values: Array<Pair<String, String>>): Button {
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
            val cur = sp().getString(key, values[0].first) ?: values[0].first
            val shown = values.firstOrNull { it.first == cur }?.second ?: values[0].second
            btn.text = label + ": " + shown
            (btn.background as GradientDrawable).setColor(Color.parseColor("#0A84FF"))
        }
        btn.setOnClickListener {
            val cur = sp().getString(key, values[0].first) ?: values[0].first
            val idx = values.indexOfFirst { it.first == cur }.let { if (it < 0) 0 else it }
            sp().edit().putString(key, values[(idx + 1) % values.size].first).apply()
            refresh()
        }
        refresh()
        return btn
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
        b.addView(caption("Skanuj i sparuj nowe urzadzenie (Bluetooth Classic + BLE), " +
            "wybierz adapter i ustaw go jako domyslny."))
        val scan = Button(this@ObdSettingsActivity).apply {
            text = "SKANUJ ADAPTERY (SPP / BLE)"
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#0A84FF"))
            }
            setOnClickListener {
                startActivity(android.content.Intent(
                    this@ObdSettingsActivity, pl.dock.widget.ObdAdapterScanActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4; bottomMargin = 8 }
        }
        b.addView(scan)
        b.addView(caption("Preferowany transport (zmiana zadziala przy nastepnym polaczeniu)"))
        b.addView(toggleBtn("prefer_ble", "Bluetooth LE"))
        b.addView(caption("Adres MAC adaptera zapamietany przez ConnectionManager"))
        b.addView(numField("adapter_mac", "np. 00:1D:A5:..."))
        b.addView(caption("Stale wiazanie: ponawiaj polaczenie tylko z zapamietanym adapterem"))
        b.addView(toggleBtn("pair_lock", "Stale wiazanie z adapterem"))
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

        b.addView(header("Progi alarmowe luk (CZESC II pkt 10.7)"))
        b.addView(caption("Przekroczenie progu = luk i wartosc czerwone #FF003A. " +
            "Puste pole = wartosc domyslna z zegarow."))
        b.addView(caption("ECT: temperatura alarmowa w C (domyslnie 102)"))
        b.addView(numFieldDec("alarm_ect_max", "102"))
        b.addView(caption("VOLTS: minimum (ponizej = alarm), domyslnie 11.8"))
        b.addView(numFieldDec("alarm_volt_min", "11.8"))
        b.addView(caption("VOLTS: maksimum (powyzej = alarm), domyslnie 14.8"))
        b.addView(numFieldDec("alarm_volt_max", "14.8"))
        b.addView(caption("LTFT: prog bezwgledny w % (domyslnie 10)"))
        b.addView(numFieldDec("alarm_ltft_max", "10"))
        b.addView(caption("IAT: temperatura alarmowa w C (domyslnie 65)"))
        b.addView(numFieldDec("alarm_iat_max", "65"))

        b.addView(header("Akcja tapniecia predkosciomierza (pkt 9.7)"))
        b.addView(caption("Co robi tapniecie na cyfrowej predkosci na zegarach UFO"))
        b.addView(cycleBtn("speed_tap_action", "Tapniecie predkosci", arrayOf(
            Pair("units", "km/h <-> mph"),
            Pair("dtc", "szybkie kasowanie DTC"),
            Pair("none", "brak akcji"))))
        b.addView(header("CarPlay Dashboard (CZESC III)"))
        b.addView(caption("Kolor akcentu interfejsu (pigulki, zaznaczenia, QUICK BUTTON)"))
        b.addView(cycleBtn("carplay_accent", "Akcent", arrayOf(
            Pair("blue", "niebieski"),
            Pair("red", "czerwony Type-R"),
            Pair("green", "zielony Civic"))))
        b.addView(caption("QUICK BUTTON - przypity dolny pasek docka"))
        b.addView(cycleBtn("quick_button_action", "Szybki przycisk", arrayOf(
            Pair("save", "zapisz event (czarna skrzynka)"),
            Pair("reconnect", "wymus reconnect OBD"),
            Pair("cleardtc", "kasuj DTC"))))
        b.addView(caption("Multi-Select: co sie dzieje z 5. parametrem przy pelnych 4 wykresach"))
        b.addView(cycleBtn("multi_select_overflow", "5. parametr", arrayOf(
            Pair("fifo", "zastepuje najstarszy (FIFO)"),
            Pair("block", "blokuje z komunikatem"))))
        b.addView(caption("Auto-Sort by Anomaly (pkt 17.3): parametr poza norma " +
            "wchodzi na poczatek listy Live Data"))
        b.addView(toggleBtn("auto_sort_anomaly", "Auto-sort po anomalii"))
        b.addView(header("Zachowanie po zgaszeniu silnika (pkt 35.1)"))
        b.addView(caption("Przypisania luk UFO w stanie Engine OFF - konfigurowalne"))
        b.addView(cycleBtn("ufo_off_left", "Lewy luk po zgaszeniu", arrayOf(
            Pair("VOLTS", "napiecie"),
            Pair("ECT", "temperatura"),
            Pair("BOOST", "doladowanie"),
            Pair("AVG_TRIP_FUEL", "srednie zuzycie"))))
        b.addView(cycleBtn("ufo_off_right", "Prawy luk po zgaszeniu", arrayOf(
            Pair("AVG_TRIP_FUEL", "srednie zuzycie"),
            Pair("FUEL", "AFR"),
            Pair("TPS", "przepustnica"),
            Pair("IAT", "temp. dolotowa"),
            Pair("LTFT", "korekta paliwa"))))
        b.addView(header("Personalizacja Overview (pkt 16.1)"))
        b.addView(caption("Widocznosc kart; kolejnosc zmienia sie przez " +
            "przeciaganie (long press) na ekranie Overview"))
        b.addView(toggleBtn("ov_card_RPM_SPEED", "Karta RPM / SPEED"))
        b.addView(toggleBtn("ov_card_BATTERY", "Karta akumulator"))
        b.addView(toggleBtn("ov_card_ECT", "Karta temperatura"))
        b.addView(toggleBtn("ov_card_DTC", "Karta DTC"))
        b.addView(toggleBtn("ov_card_OBD", "Karta status OBD"))
        b.addView(header("Widzet OBD (pkt 38)"))
        b.addView(caption("Wybor do 4 parametrow widzetu na ekranie " +
            "glownym; slot pusty = krotszy widzet"))
        val slotDefs = arrayOf(
            Triple("widget_p1", "Slot 1", arrayOf(
                Pair("RPM", "RPM"), Pair("SPEED", "predkosc"),
                Pair("MAF", "przeplyw powietrza"), Pair("BARO", "cisnienie baro"),
                Pair("AFR", "sonda AFR"),
                Pair("LOAD", "obciazenie"), Pair("TPS", "przepustnica"),
                Pair("MAP", "MAP"), Pair("ECT", "temp. plynu"),
                Pair("IAT", "temp. dolotowa"), Pair("STFT", "korekta krotka"),
                Pair("LTFT", "korekta dluga"), Pair("VOLT", "napiecie"),
                Pair("NONE", "pusty"))),
            Triple("widget_p2", "Slot 2", arrayOf(
                Pair("SPEED", "predkosc"), Pair("RPM", "RPM"),
                Pair("MAF", "przeplyw powietrza"), Pair("BARO", "cisnienie baro"),
                Pair("AFR", "sonda AFR"),
                Pair("LOAD", "obciazenie"), Pair("TPS", "przepustnica"),
                Pair("MAP", "MAP"), Pair("ECT", "temp. plynu"),
                Pair("IAT", "temp. dolotowa"), Pair("STFT", "korekta krotka"),
                Pair("LTFT", "korekta dluga"), Pair("VOLT", "napiecie"),
                Pair("NONE", "pusty"))),
            Triple("widget_p3", "Slot 3", arrayOf(
                Pair("NONE", "pusty"), Pair("ECT", "temp. plynu"),
                Pair("MAF", "przeplyw powietrza"), Pair("BARO", "cisnienie baro"),
                Pair("AFR", "sonda AFR"),
                Pair("IAT", "temp. dolotowa"), Pair("LOAD", "obciazenie"),
                Pair("TPS", "przepustnica"), Pair("MAP", "MAP"),
                Pair("STFT", "korekta krotka"), Pair("LTFT", "korekta dluga"),
                Pair("VOLT", "napiecie"), Pair("RPM", "RPM"),
                Pair("SPEED", "predkosc"))),
            Triple("widget_p4", "Slot 4", arrayOf(
                Pair("NONE", "pusty"), Pair("LTFT", "korekta dluga"),
                Pair("MAF", "przeplyw powietrza"), Pair("BARO", "cisnienie baro"),
                Pair("AFR", "sonda AFR"),
                Pair("VOLT", "napiecie"), Pair("ECT", "temp. plynu"),
                Pair("IAT", "temp. dolotowa"), Pair("LOAD", "obciazenie"),
                Pair("TPS", "przepustnica"), Pair("MAP", "MAP"),
                Pair("STFT", "korekta krotka"), Pair("RPM", "RPM"),
                Pair("SPEED", "predkosc"))))
        for (d in slotDefs) b.addView(cycleBtn(d.first, d.second, d.third))
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
                saveAllFields()   // NAPRAWA: zapis lat/lon PRZED przeliczeniem
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
