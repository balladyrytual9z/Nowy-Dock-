package pl.dock.widget

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import pl.dock.widget.orchestration.ConnectionManager
import java.util.Locale

/**
 * Skanowanie adapterow OBD (Bluetooth Classic + BLE) na osobym ekranie.
 *
 * Zgodnie ze specyfikacja (29.1): pokazuje nazwe urzadzenia, adres MAC,
 * rodzaj transportu (SPP / BLE), status sparowania i polaczenia oraz
 * przyciski SKANUJ PONOWNIE, POLACZ, ROZLACZ, USTAW JAKO DOMYSLNY.
 *
 * Wynik zapisywany jest w SharedPreferences "obd_settings":
 *  - adapter_mac / adapter_name  -> zapamietany adapter
 *  - prefer_ble                  -> wybor transportu (BLE vs SPP)
 *  - pair_lock                   -> stale wiazanie z zapamietanym adapterem
 *
 * Ekran jest w pelni opcjonalnym narzedziem - nie wymaga Google Services.
 * Uprawnienia: ACCESS_FINE_LOCATION (Android 9 wymaga go do skanu BLE).
 */
class ObdAdapterScanActivity : Activity() {

    private val deviceNames = LinkedHashMap<String, String>()
    private val scanResults = LinkedHashMap<String, BluetoothDevice>()
    private lateinit var list: LinearLayout
    private lateinit var statusTv: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? get() = BluetoothAdapter.getDefaultAdapter()

    private val scanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            onFound(result.device)
        }

        override fun onBatchScanResults(results: MutableList<android.bluetooth.le.ScanResult>) {
            for (r in results) onFound(r.device)
        }

        override fun onScanFailed(errorCode: Int) {
            statusTv.text = "Skanowanie nieudane (kod $errorCode)"
        }
    }

    private val render = Runnable { rebuild() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(28, 40, 28, 24)
        }

        val title = TextView(this).apply {
            text = "Skanowanie adapterow OBD"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        root.addView(title)

        statusTv = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 6, 0, 12)
        }
        root.addView(statusTv)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(action("SKANUJ PONOWNIE", "#0A84FF") { startScan() })
        buttons.addView(action("ZAMKNIJ", "#2C2C2E") { finish() })
        root.addView(buttons)

        root.addView(manualEntry())

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        if (!hasLocationPermission()) {
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOC)
            }
        } else {
            startScan()
        }
    }

    private fun manualEntry(): LinearLayout {
        val p = getSharedPreferences("obd_settings", MODE_PRIVATE)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 8)
        }
        val et = EditText(this).apply {
            hint = "MAC recznie (np. 00:1D:A5:68:98:8B)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8E8E93"))
            inputType = InputType.TYPE_CLASS_TEXT
            setText(p.getString("adapter_mac", ""))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(et)
        row.addView(action("ZAPISZ", "#30D158") {
            val mac = et.text.toString().trim().uppercase(Locale.US)
            if (mac.isEmpty()) {
                toast("Podaj adres MAC")
            } else {
                saveAdapter(mac, mac, preferBle = p.getBoolean("prefer_ble", false))
            }
        })
        return row
    }

    private fun action(label: String, color: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 22f
                setColor(Color.parseColor(color))
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 8, 0) }
        }

    // ---------------------------------------------------------------- skan

    private fun startScan() {
        val ad = adapter
        if (ad == null) {
            statusTv.text = "Brak adaptera Bluetooth"
            return
        }
        if (!ad.isEnabled) {
            statusTv.text = "Bluetooth wylaczony - wlacz go i skanuj ponownie"
            return
        }
        deviceNames.clear()
        scanResults.clear()
        try {
            ad.bondedDevices?.forEach { d -> onFound(d) }
        } catch (t: Throwable) {
            // brak uprawnien do listy sparowanych - skan BLE ponizej i tak dziala
        }
        statusTv.text = "Skanowanie... (sparowane + BLE)"
        rebuild()
        try {
            if (hasLocationPermission()) {
                ad.bluetoothLeScanner?.startScan(scanCallback)
            }
        } catch (t: Throwable) {
            android.util.Log.w("Dock", "BLE scan start failed", t)
        }
        handler.removeCallbacks(render)
        handler.postDelayed(render, 4000)
    }

    private fun onFound(d: BluetoothDevice) {
        val mac = try { d.address } catch (t: Throwable) { return } ?: return
        scanResults[mac] = d
        val name = try { d.name } catch (t: Throwable) { null }
        if (!name.isNullOrEmpty()) deviceNames[mac] = name
        handler.removeCallbacks(render)
        handler.post(render)
    }

    private fun stopScan() {
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (t: Throwable) {
        }
    }

    // ---------------------------------------------------------------- lista

    private fun rebuild() {
        list.removeAllViews()
        val p = getSharedPreferences("obd_settings", MODE_PRIVATE)
        val savedMac = p.getString("adapter_mac", "") ?: ""
        val connected = ConnectionManager.get(applicationContext).uiState.value.connected

        if (scanResults.isEmpty()) {
            list.addView(label("Brak znalezionych urzadzen. Sprawdz uprawnienia lokalizacji."))
            return
        }
        for ((mac, d) in scanResults) {
            list.addView(rowFor(d, mac, savedMac, connected))
        }
    }

    private fun rowFor(
        d: BluetoothDevice,
        mac: String,
        savedMac: String,
        connected: Boolean
    ): LinearLayout {
        val isSaved = mac.equals(savedMac, ignoreCase = true)
        val bonded = try {
            d.bondState == BluetoothDevice.BOND_BONDED
        } catch (t: Throwable) {
            false
        }
        val type = try {
            when (d.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Bluetooth Classic (SPP)"
                BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Classic + BLE"
                else -> "?"
            }
        } catch (t: Throwable) {
            "?"
        }
        val name = deviceNames[mac] ?: mac

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor(if (isSaved) "#15304A" else "#1C1C1E"))
                setStroke(2, Color.parseColor(if (isSaved) "#0A84FF" else "#2C2C2E"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
        }

        card.addView(TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        val detail = buildString {
            append("MAC: ").append(mac)
            append("  -  ").append(type)
            append(if (bonded) "  -  sparowany" else "  -  niesparowany")
            if (isSaved) append("  -  zapamietany")
        }
        card.addView(TextView(this).apply {
            text = detail
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 4, 0, 10)
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val preferBle = type.contains("BLE")
        row.addView(action("POLACZ", "#0A84FF") {
            saveAdapter(mac, name, preferBle)
            ConnectionManager.get(applicationContext).connect(mac, preferBle)
            toast("Lacze z " + name)
            finish()
        })
        if (connected && isSaved) {
            row.addView(action("ROZLACZ", "#FF453A") {
                ConnectionManager.get(applicationContext).disconnect()
                toast("Rozlaczono")
            })
        }
        if (!isSaved) {
            row.addView(action("USTAW JAKO DOMYSLNY", "#2C2C2E") {
                saveAdapter(mac, name, preferBle)
                toast("Ustawiono jako domyslny")
            })
        }
        card.addView(row)
        return card
    }

    private fun label(msg: String): TextView = TextView(this).apply {
        text = msg
        textSize = 13f
        setTextColor(Color.parseColor("#8E8E93"))
        setPadding(4, 12, 4, 12)
    }

    // ---------------------------------------------------------------- zapis

    private fun saveAdapter(mac: String, name: String, preferBle: Boolean) {
        getSharedPreferences("obd_settings", MODE_PRIVATE).edit()
            .putString("adapter_mac", mac)
            .putString("adapter_name", name)
            .putBoolean("prefer_ble", preferBle)
            .putBoolean("pair_lock", true)   // stale wiazanie z zapamietanym adapterem
            .apply()
        rebuild()
    }

    private fun hasLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(req, perms, res)
        if (req == REQ_LOC) {
            if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
                startScan()
            } else {
                statusTv.text = "Bez uprawnienia lokalizacji skan BLE nie jest mozliwy " +
                    "(sparowane urzadzenia nadal sa na liscie)"
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onStop() {
        super.onStop()
        stopScan()
        handler.removeCallbacks(render)
    }

    companion object {
        private const val REQ_LOC = 41
    }
}
