package com.interli.plural.features.health

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.interli.plural.R
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.ImageButton

class RestHistoryActivity : BaseActivity() {

    private lateinit var logsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rest_history)
        setupNavigationDrawer()

        logsContainer = findViewById(R.id.restHistoryContainer)
    }

    override fun onResume() {
        super.onResume()
        renderLogs()
    }

    private fun isRestLog(log: HealthLogEntry): Boolean {
        return log.type == getString(R.string.rest_title) ||
                log.type == "Pause / Rest" ||
                log.type == "Pauze / Rust"
    }

    private fun renderLogs() {
        logsContainer.removeAllViews()

        val logs = HealthHelper.loadLogs(this)
            .filter { isRestLog(it) }
            .sortedByDescending { it.timestamp }

        if (logs.isEmpty()) {
            logsContainer.addView(TextView(this).apply {
                text = getString(R.string.rest_no_history)
                setTextColor(ColorHelper.getTextColor(this@RestHistoryActivity))
                setPadding(16, 24, 16, 24)
            })
            return
        }

        val dateFormat = SimpleDateFormat(
            "dd-MM-yyyy HH:mm",
            Locale.getDefault()
        )

        logs.forEach { log ->
            val card = MaterialCardView(this).apply {
                radius = 12f * resources.displayMetrics.density
                cardElevation = 2f * resources.displayMetrics.density
                setCardBackgroundColor(ColorHelper.getBgColor(this@RestHistoryActivity))

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = (12 * resources.displayMetrics.density).toInt()
                layoutParams = params
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
            }

            val startText = log.startTimestamp?.let {
                dateFormat.format(Date(it))
            } ?: getString(R.string.rest_not_set)

            val endText = dateFormat.format(Date(log.timestamp))
            val durationText = log.value.orEmpty()
            val memberText = log.memberName.orEmpty()

            val dateTimeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val dateTimeText = TextView(this).apply {
                text = getString(R.string.rest_history_entry, startText, endText)
                setTextColor(ColorHelper.getTextColor(this@RestHistoryActivity))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val editButton = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                contentDescription = getString(R.string.edit)
                alpha = 0.4f
                setColorFilter(ColorHelper.getTextColor(this@RestHistoryActivity))

                val selectableBackground = android.util.TypedValue()
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless,
                    selectableBackground,
                    true
                )
                if (selectableBackground.resourceId != 0) {
                    setBackgroundResource(selectableBackground.resourceId)
                }

                layoutParams = LinearLayout.LayoutParams(
                    (40 * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt()
                )

                setOnClickListener {
                    RestLogEditorDialog.show(
                        this@RestHistoryActivity,
                        existingLog = log
                    ) {
                        renderLogs()
                    }
                }
            }

            dateTimeRow.addView(dateTimeText)
            dateTimeRow.addView(editButton)
            row.addView(dateTimeRow)

            row.addView(TextView(this).apply {
                text = getString(R.string.rest_history_duration, durationText)
                setTextColor(ColorHelper.getTextColor(this@RestHistoryActivity))
                setPadding(0, 4, 0, 0)
            })

            if (memberText.isNotBlank()) {
                row.addView(TextView(this).apply {
                    text = memberText
                    setTextColor(ColorHelper.getTextColor(this@RestHistoryActivity))
                    setPadding(0, 4, 0, 0)
                })
            }

            card.addView(row)
            logsContainer.addView(card)
        }
    }
}