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
import android.widget.Toast
import pl.dock.widget.orchestration.ConnectionManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Maintenance: liczniki serwisowe (olej, filtr powietrza, rozrzad)
 * oparte na dystansie z telemetrii OBD. Progi zapisane w SharedPreferences;
 * ostrzezenia pojawiaja sie na liscie, nie w formie okien dialogowych.
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(28, 36, 28, 20)
        }
        root.addView(TextView(this).apply {
            text = "MAINTENANCE"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        body = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#E5E5EA"))
            setPadding(0, 16, 0, 8)
        }
        root.addView(ScrollView(this).apply {
            addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(body)
                for ((key, label, _) in items) addView(itemRow(key, label))
                addView(kmRow())
            })
        })
        setContentView(root)
        observeOdometer()
    }

    /** Wiersz: ostatni serwis (km) + interwal; zapis przy utracie fokusu. */
    private fun itemRow(key: String, label: String): View =
        LinearLayout(this).apply {
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

            addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this).apply {
                text = "Ostatni serwis (km):"
                textSize = 12f
                setTextColor(Color.parseColor("#8E8E93"))
            })
            addView(EditText(this).apply {
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
            addView(TextView(this).apply {
                text = "Interwal (km):"
                textSize = 12f
                setTextColor(Color.parseColor("#8E8E93"))
            })
            addView(EditText(this).apply {
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
            statusViews[key] = TextView(this).apply {
                textSize = 13f
                setPadding(0, 8, 0, 0)
            }.also { addView(it) }
        }

    private val statusViews = HashMap<String, TextView>()

    private fun kmRow(): View =
        LinearLayout(this).apply {
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
            addView(TextView(this).apply {
                text = "Przebieg bazowy (km) - docelowo zliczany z VSS:"
                textSize = 12f
                setTextColor(Color.parseColor("#8E8E93"))
            })
            addView(EditText(this).apply {
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
            addView(Button(this).apply {
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

    private fun observeOdometer() {
        // dystans liczony w tle z predkosci - przyrosty sumowane
        MainScope().launch {
            ConnectionManager.get(applicationContext).uiState.collectLatest { s ->
                if (s.speedKmh > 0) {
                    val now = System.currentTimeMillis()
                    val last = sp().getLong("odo_ts", now)
                    val dt = (now - last).coerceAtMost(5000)
                    if (dt in 100..5000) {
                        val meters = s.speedKmh * (dt / 3_600_000.0)
                        sp().edit()
                            .putLong("odo_ts", now)
                            .putFloat("odo_acc", sp().getFloat("odo_acc", 0f) + meters.toFloat())
                            .apply()
                    } else {
                        sp().edit().putLong("odo_ts", now).apply()
                    }
                }
                refresh()
            }
        }
    }

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
