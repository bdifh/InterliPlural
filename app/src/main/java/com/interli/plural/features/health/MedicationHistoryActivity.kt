package com.interli.plural.features.health

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import com.google.android.material.button.MaterialButton
import com.interli.plural.R
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.PdfExportHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MedicationHistoryActivity : BaseActivity() {

    private lateinit var leftColumnContainer: LinearLayout
    private lateinit var rightGridContainer: LinearLayout
    private lateinit var horizontalScrollView: HorizontalScrollView
    private lateinit var btnExportPdf: MaterialButton

    private var currentMedications: List<MedicationItem> = emptyList()
    private var currentRelevantLogs: List<HealthLogEntry> = emptyList()
    private var pendingIncludeMemberNames: Boolean = true

    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { exportToPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medication_history)
        setupNavigationDrawer()

        leftColumnContainer = findViewById(R.id.leftColumnContainer)
        rightGridContainer = findViewById(R.id.rightGridContainer)
        horizontalScrollView = findViewById(R.id.horizontalScrollView)
        btnExportPdf = findViewById(R.id.btnExportPdf)

        styleMaterialButton(btnExportPdf)
        btnExportPdf.setOnClickListener {
            showPdfExportOptionsDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        renderMedicationTimeline()
    }

    private fun styleMaterialButton(btn: MaterialButton) {
        val btnBg = ColorHelper.getBtnColor(this)
        val btnText = ColorHelper.getBtnTextColor(this)
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(btnBg)
        btn.setTextColor(btnText)
        btn.cornerRadius = (10 * resources.displayMetrics.density).toInt()
    }

    private fun renderMedicationTimeline() {
        leftColumnContainer.removeAllViews()
        rightGridContainer.removeAllViews()

        val medications = HealthHelper.loadMedications(this)
        val allLogs = HealthHelper.loadLogs(this)
        val titleKey = getString(R.string.medication_title)

        val medicationLogs = allLogs.filter { log ->
            log.type == titleKey ||
                    medications.any { med ->
                        val name = med.name.orEmpty()
                        name.isNotBlank() &&
                                log.value == "$name" +
                                if (med.dosage.isNotEmpty()) {
                                    " (${med.dosage})"
                                } else {
                                    ""
                                }
                    }
        }

        val medicationValues = medications.associateWith { med ->
            "${med.name.orEmpty()}" +
                    if (med.dosage.isNotEmpty()) {
                        " (${med.dosage})"
                    } else {
                        ""
                    }
        }

        val relevantLogs = medicationLogs.filter { log ->
            medicationValues.values.any { it == log.value }
        }

        currentMedications = medications
        currentRelevantLogs = relevantLogs

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val firstDay = Calendar.getInstance().apply {
            timeInMillis = relevantLogs.minOfOrNull { it.timestamp }
                ?: today.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dates = mutableListOf<Long>()
        val cursor = firstDay.clone() as Calendar

        while (!cursor.after(today)) {
            dates.add(cursor.timeInMillis)
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }

        if (dates.isEmpty()) {
            dates.add(today.timeInMillis)
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val textColor = ColorHelper.getTextColor(this)
        val themeColor = ColorHelper.getBtnColor(this)
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

        leftColumnContainer.addView(TextView(this).apply {
            text = getString(R.string.medication_timeline_medicine)
            setTextColor(textColor)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(44))
            gravity = Gravity.CENTER_VERTICAL
        })

        val rightHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        dates.forEach { date ->
            rightHeader.addView(TextView(this).apply {
                text = dateFormat.format(Date(date))
                setTextColor(textColor)
                textSize = 11f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(44))
            })
        }
        rightGridContainer.addView(rightHeader)

        medications.forEach { med ->
            val medValue = medicationValues[med].orEmpty()

            leftColumnContainer.addView(TextView(this).apply {
                text = buildString {
                    append(med.name.orEmpty())
                    if (med.dosage.isNotEmpty()) {
                        append("\n")
                        append(med.dosage)
                    }
                }
                setTextColor(textColor)
                textSize = 12f
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(dp(120), dp(48))
                gravity = Gravity.CENTER_VERTICAL
            })

            leftColumnContainer.addView(View(this).apply {
                setBackgroundColor(themeColor and 0x33FFFFFF)
                layoutParams = LinearLayout.LayoutParams(
                    dp(120),
                    dp(1)
                )
            })

            val rightRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            dates.forEach { date ->
                val matchingLogs = relevantLogs.filter { log ->
                    log.value == medValue &&
                            HealthHelper.isSameDay(
                                log.timestamp,
                                date,
                                TimeZone.getDefault()
                            )
                }

                val taken = matchingLogs.isNotEmpty()

                val dot = View(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(if (taken) themeColor else Color.TRANSPARENT)
                        setStroke(
                            dp(1),
                            if (taken) themeColor else (textColor and 0x66FFFFFF)
                        )
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        dp(14),
                        dp(14)
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    contentDescription = getString(
                        if (taken) R.string.medication_timeline_taken
                        else R.string.medication_timeline_not_recorded
                    )
                }

                val cell = FrameLayout(this).apply {
                    addView(
                        dot,
                        FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER)
                    )
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                    if (taken) {
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            showLogDetailsDialog(med, date, matchingLogs)
                        }
                    }
                }

                rightRow.addView(cell)
            }

            rightGridContainer.addView(rightRow)

            rightGridContainer.addView(View(this).apply {
                setBackgroundColor(themeColor and 0x33FFFFFF)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                )
            })
        }

        horizontalScrollView.post {
            horizontalScrollView.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun showLogDetailsDialog(med: MedicationItem, dateMillis: Long, logs: List<HealthLogEntry>) {
        val dateStr = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(dateMillis))
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val titleText = buildString {
            append(med.name)
            if (med.dosage.isNotEmpty()) append(" (${med.dosage})")
        }

        logs.sortedBy { it.timestamp }.forEachIndexed { index, log ->
            if (index > 0) {
                val divider = View(this).apply {
                    setBackgroundColor(ColorHelper.getBtnColor(this@MedicationHistoryActivity) and 0x22FFFFFF)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    ).apply { setMargins(0, 12, 0, 12) }
                }
                layout.addView(divider)
            }

            val tvTime = TextView(this).apply {
                val timeStr = timeFormat.format(Date(log.timestamp))
                text = "⏰ $timeStr"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ColorHelper.getTextColor(this@MedicationHistoryActivity))
            }

            val tvMember = TextView(this).apply {
                val memberStr = log.memberName ?: getString(R.string.group_general)
                text = "👤 $memberStr"
                textSize = 13f
                setTextColor(ColorHelper.getTextColor(this@MedicationHistoryActivity))
                alpha = 0.85f
                setPadding(0, 4, 0, 0)
            }

            layout.addView(tvTime)
            layout.addView(tvMember)

            if (!log.note.isNullOrEmpty()) {
                val tvNote = TextView(this).apply {
                    text = "📝 ${log.note}"
                    textSize = 13f
                    setTextColor(ColorHelper.getTextColor(this@MedicationHistoryActivity))
                    alpha = 0.85f
                    setPadding(0, 4, 0, 0)
                }
                layout.addView(tvNote)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("$titleText ($dateStr)")
            .setView(layout)
            .setPositiveButton(R.string.close, null)
            .create()

        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun showPdfExportOptionsDialog() {
        val checkBox = AppCompatCheckBox(this).apply {
            text = getString(R.string.checkbox_include_member_names)
            isChecked = true
            buttonTintList = android.content.res.ColorStateList.valueOf(ColorHelper.getBtnColor(this@MedicationHistoryActivity))
            setTextColor(ColorHelper.getTextColor(this@MedicationHistoryActivity))
            setPadding(32, 24, 32, 24)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_pdf_include_members_title)
            .setView(checkBox)
            .setPositiveButton(R.string.action_export_pdf) { _, _ ->
                pendingIncludeMemberNames = checkBox.isChecked
                pdfLauncher.launch("medication_history.pdf")
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun exportToPdf(uri: Uri) {
        PdfExportHelper.exportMedicationHistoryToPdf(
            context = this,
            uri = uri,
            medications = currentMedications,
            relevantLogs = currentRelevantLogs,
            includeMemberNames = pendingIncludeMemberNames
        )
    }
}