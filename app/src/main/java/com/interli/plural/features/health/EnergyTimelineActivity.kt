package com.interli.plural.features.health

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.HorizontalScrollView
import android.widget.TableLayout
import android.widget.TableRow
import com.google.android.material.card.MaterialCardView
import com.interli.plural.R
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EnergyTimelineActivity : BaseActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_energy_timeline)

        container = findViewById(R.id.energyTimelineContainer)
        setupNavigationDrawer()
    }

    override fun onResume() {
        super.onResume()
        renderTimeline()
    }

    private data class EnergyRowValues(
        val value: String,
        val description: String,
        val range: String
    )

    private fun parseEnergyValue(rawValue: String): EnergyRowValues {
        val parts = rawValue.split(" — ", limit = 2)
        val ratingPart = parts.firstOrNull().orEmpty().trim()
        val description = parts.getOrNull(1)?.trim().orEmpty()

        val numericMatch = Regex("""^(.*?)\s*\(([^()]*)\)\s*$""")
            .matchEntire(ratingPart)

        if (numericMatch != null) {
            return EnergyRowValues(
                value = numericMatch.groupValues[1].trim(),
                description = description,
                range = numericMatch.groupValues[2].trim()
            )
        }

        if (ratingPart.contains(" / 5")) {
            return EnergyRowValues(
                value = ratingPart.substringBefore(" / 5").trim(),
                description = description,
                range = "1–5"
            )
        }

        return EnergyRowValues(
            value = ratingPart,
            description = description,
            range = "—"
        )
    }

    private fun renderTimeline() {
        container.removeAllViews()

        val records = HealthHelper.loadEnergyHistory(this)
        val density = resources.displayMetrics.density
        val dayFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val recordsByDay = records.groupBy { record ->
            Calendar.getInstance().apply {
                timeInMillis = record.timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        recordsByDay.toSortedMap(reverseOrder()).forEach { (day, dayRecords) ->
            val card = MaterialCardView(this).apply {
                radius = 16f * density
                cardElevation = 2f
                useCompatPadding = true
                setCardBackgroundColor(ColorHelper.getBgColor(this@EnergyTimelineActivity))
                strokeColor = ColorHelper.getBtnColor(this@EnergyTimelineActivity) and 0x44FFFFFF
                strokeWidth = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (16 * density).toInt())
                }
            }

            val dayLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    (20 * density).toInt(),
                    (20 * density).toInt(),
                    (20 * density).toInt(),
                    (20 * density).toInt()
                )
            }

            val dayTitle = TextView(this).apply {
                text = dayFormat.format(Date(day))
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ColorHelper.getTextColor(this@EnergyTimelineActivity))
                setPadding(0, 0, 0, (12 * density).toInt())
            }
            dayLayout.addView(dayTitle)

            val table = TableLayout(this)

            fun addCell(
                row: TableRow,
                text: String,
                widthDp: Int,
                isHeader: Boolean = false
            ) {
                val cell = TextView(this@EnergyTimelineActivity).apply {
                    this.text = text
                    textSize = if (isHeader) 12f else 13f
                    setTextColor(ColorHelper.getTextColor(this@EnergyTimelineActivity))
                    if (isHeader) {
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    setPadding(
                        (8 * density).toInt(),
                        (8 * density).toInt(),
                        (8 * density).toInt(),
                        (8 * density).toInt()
                    )
                    layoutParams = TableRow.LayoutParams(
                        (widthDp * density).toInt(),
                        TableRow.LayoutParams.WRAP_CONTENT
                    )
                }
                row.addView(cell)
            }

            val headerRow = TableRow(this).apply {
                addCell(this, getString(R.string.energy_history_time), 70, true)
                addCell(this, getString(R.string.energy_history_value), 90, true)
                addCell(this, getString(R.string.energy_history_description), 170, true)
                addCell(this, getString(R.string.energy_history_member), 110, true)
                addCell(this, getString(R.string.energy_history_range), 80, true)
            }
            table.addView(headerRow)

            dayRecords.sortedByDescending { it.timestamp }.forEach { record ->
                val parsed = parseEnergyValue(record.value.orEmpty())

                val row = TableRow(this).apply {
                    addCell(
                        this,
                        timeFormat.format(Date(record.timestamp)),
                        70
                    )
                    addCell(this, parsed.value.ifBlank { "—" }, 90)
                    addCell(this, parsed.description.ifBlank { "—" }, 170)
                    addCell(this, record.memberName.orEmpty().ifBlank { "—" }, 110)
                    addCell(this, parsed.range, 80)
                }

                table.addView(row)
            }

            val horizontalScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = true
                addView(table)
            }

            dayLayout.addView(horizontalScroll)

            card.addView(dayLayout)
            container.addView(card)
        }
    }
}