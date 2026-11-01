package pl.dock.widget.maintenance

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pl.dock.widget.orchestration.ConnectionManager

/**
 * Maintenance: liczniki serwisowe (olej, filtr powietrza, rozrzad)
 * oparte na dystansie z telemetrii OBD. Progi zapisane w SharedPreferences;
 * ostrzezenia pojawiaja sie na liscie, nie w formie okien dialogowych.
 *
 * Dopracowanie: akumulacja dystansu (odometr) przeniesiona do ObdCoreService
 * - przebieg rosnie takze przy zamknietym ekranie (wczesniej liczyl sie
 * tylko przy otwartym Serwisie). Ekran tylko wyswietla stan prefs.
 */
class MaintenanceActivity : Activity() {

    private lateinit var body: TextView
    private var currentKm = 0L

    private val items = arrayOf(
        Triple("olej", "Olej + filtr", 10000L),
        Triple("filtr", "Filtr powietrza", 30000L),
        Triple("rozrzad", "Rozrzad", 100000L)
    )

    private fun sp() = getSharedPreferences("obd_maintenance", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this@MaintenanceActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(28, 36, 28, 20)
        }
        root.addView(TextView(this@MaintenanceActivity).apply {
            text = "SERWIS (MAINTENANCE)"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        root.addView(backBtn())
        body = TextView(this@MaintenanceActivity).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#E5E5EA"))
            setPadding(0, 16, 0, 8)
        }
        root.addView(ScrollView(this@MaintenanceActivity).apply {
            addView(LinearLayout(this@MaintenanceActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(body)
                for ((key, label, _) in items) addView(itemRow(key, label))
                addView(kmRow())
            })
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Wiersz: ostatni serwis (km) + interwal; zapis przy utracie fokusu. */
    private fun backBtn(): Button =
        Button(this@MaintenanceActivity).apply {
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

    private fun itemRow(key: String, label: String): View =
        LinearLayout(this@MaintenanceActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 16, 12, 8)
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#1C1C1E"))
                setStroke(1, Color.parseColor("#2C2C2E"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }

            addView(TextView(this@MaintenanceActivity).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@MaintenanceActivity).apply {
                text = "Ostatni serwis (km):"
                textSize = 12f
                setTextColor(Color.parseColor("#8E8E93"))
            })
            addView(EditText(this@MaintenanceActivity).apply {
                setText(sp().getLong(key, 0L).toString())
                textSize = 15f
                setTextColor(Color.WHITE)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val v = text.toString().toLongOrNull() ?: 0L
                        sp().edit().putLong(key, v).apply()
                        refresh()
                    }
                }
            })
            addView(TextView(this@MaintenanceActivity).apply {
                text = "Interwal (km):"
                textSize = 12f
                setTextColor(Color.parseColor("#8E8E93"))
            })
            addView(EditText(this@MaintenanceActivity).apply {
                setText(sp().getLong(key + "_int", defaultInterval(key)).toString())
                textSize = 15f
                setTextColor(Color.WHITE)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val v = text.toString().toLongOrNull() ?: defaultInterval(key)
                        sp().edit().putLong(key + "_int", v).apply()
                        refresh()
                    }
                }
            })
            statusViews[key] = TextView(this@MaintenanceActivity).apply {
                textSize = 13f
                setPadding(0, 8, 0, 0)
            }.also { addView(it) }
        }

    private val statusViews = HashMap<String, TextView>()

    private fun kmRow(): View =
        LinearLayout(this@MaintenanceActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 16, 12, 8)
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#1C1C1E"))
                setStroke(1, Color.parseColor("#2C2C2E"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
            addView(TextView(this@MaintenanceActivity).apply {
                text = "Przebieg bazowy (km) - odo zliczane w tle z VSS:"
                textSize = 12f
                setTextColor(Color.parseColor("#8E8E93"))
            })
            addView(EditText(this@MaintenanceActivity).apply {
                setText(sp().getLong("base_km", 0L).toString())
                textSize = 15f
                setTextColor(Color.WHITE)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val v = text.toString().toLongOrNull() ?: 0L
                        sp().edit().putLong("base_km", v).apply()
                        refresh()
                    }
                }
            })
            addView(Button(this@MaintenanceActivity).apply {
                text = "ZAPISZ I PRZELICZ"
                isAllCaps = false
                textSize = 12f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(Color.parseColor("#0A84FF"))
                }
                setOnClickListener { refresh() }
            })
        }

    private fun defaultInterval(key: String): Long =
        items.firstOrNull { it.first == key }?.third ?: 10000L

    private fun refresh() {
        currentKm = sp().getLong("base_km", 0L) + (sp().getFloat("odo_acc", 0f) / 1000f).toLong()
        val sb = StringBuilder()
        sb.append("Przebieg aktualny: ").append(currentKm).append(" km\n")
        body.text = sb.toString()
        for ((key, _, _) in items) {
            val last = sp().getLong(key, 0L)
            val interval = sp().getLong(key + "_int", defaultInterval(key))
            val due = last + interval
            val left = due - currentKm
            val tv = statusViews[key] ?: continue
            when {
                left <= 0 -> {
                    tv.text = "SERWIS ZALEGA (" + (-left) + " km po terminie)"
                    tv.setTextColor(Color.parseColor("#FF453A"))
                }
                left < 1000 -> {
                    tv.text = "Wkrotce: " + left + " km do serwisu"
                    tv.setTextColor(Color.parseColor("#FF9F0A"))
                }
                else -> {
                    tv.text = "OK: " + left + " km do serwisu"
                    tv.setTextColor(Color.parseColor("#30D158"))
                }
            }
        }
    }
}
