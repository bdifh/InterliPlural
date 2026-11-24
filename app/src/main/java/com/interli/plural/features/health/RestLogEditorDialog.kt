package com.interli.plural.features.health

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.interli.plural.R
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RestLogEditorDialog {

    fun show(
        context: Context,
        existingLog: HealthLogEntry? = null,
        memberId: String? = existingLog?.memberId,
        memberName: String? = existingLog?.memberName,
        onChanged: () -> Unit
    ) {
        var startTime: Long? = existingLog?.startTimestamp
        var endTime: Long? = existingLog?.timestamp

        val dateFormat = SimpleDateFormat(
            "dd-MM-yyyy HH:mm",
            Locale.getDefault()
        )

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 16)
        }

        val startButton = MaterialButton(context)
        val clearStartButton = MaterialButton(context)
        val endButton = MaterialButton(context)
        val clearEndButton = MaterialButton(context)

        fun updateTimeButtons() {
            startButton.text = context.getString(
                R.string.rest_start_time,
                startTime?.let { dateFormat.format(Date(it)) }
                    ?: context.getString(R.string.rest_not_set)
            )
            clearStartButton.visibility =
                if (startTime == null) View.GONE else View.VISIBLE

            endButton.text = context.getString(
                R.string.rest_end_time,
                endTime?.let { dateFormat.format(Date(it)) }
                    ?: context.getString(R.string.rest_not_set)
            )
            clearEndButton.visibility =
                if (endTime == null) View.GONE else View.VISIBLE
        }

        fun pickDateTime(initialTime: Long, onSelected: (Long) -> Unit) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = initialTime
            }

            DatePickerDialog(
                context,
                { _, year, month, day ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, day)

                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hour)
                            calendar.set(Calendar.MINUTE, minute)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            onSelected(calendar.timeInMillis)
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        android.text.format.DateFormat.is24HourFormat(context)
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        startButton.setOnClickListener {
            pickDateTime(startTime ?: System.currentTimeMillis()) {
                startTime = it
                updateTimeButtons()
            }
        }

        clearStartButton.text = context.getString(R.string.rest_clear)
        clearStartButton.setOnClickListener {
            startTime = null
            updateTimeButtons()
        }

        endButton.setOnClickListener {
            pickDateTime(endTime ?: System.currentTimeMillis()) {
                endTime = it
                updateTimeButtons()
            }
        }

        clearEndButton.text = context.getString(R.string.rest_clear)
        clearEndButton.setOnClickListener {
            endTime = null
            updateTimeButtons()
        }

        val etMinutes = EditText(context).apply {
            hint = context.getString(R.string.rest_duration_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(ColorHelper.getTextColor(context))
            setHintTextColor(
                ColorHelper.getTextColor(context) and 0x88FFFFFF.toInt()
            )

            existingLog?.value
                ?.substringBefore(" ")
                ?.toIntOrNull()
                ?.let { setText(it.toString()) }
        }

        val hint = TextView(context).apply {
            text = context.getString(R.string.rest_duration_fallback_hint)
            textSize = 12f
            setTextColor(ColorHelper.getTextColor(context))
        }

        layout.addView(startButton)
        layout.addView(clearStartButton)
        layout.addView(endButton)
        layout.addView(clearEndButton)
        layout.addView(hint)
        layout.addView(etMinutes)

        updateTimeButtons()

        val builder = AlertDialog.Builder(context)
            .setTitle(
                if (existingLog == null) {
                    R.string.rest_log_custom
                } else {
                    R.string.rest_edit_log
                }
            )
            .setView(layout)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)

        if (existingLog != null) {
            builder.setNeutralButton(R.string.delete, null)
        }

        val dialog = builder.create()
        dialog.show()

        if (context is BaseActivity) {
            ColorHelper.styleAlertDialog(dialog, context)
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val durationMinutes: Int
            val finalEndTime: Long

            if (startTime != null && endTime != null) {
                if (endTime!! <= startTime!!) {
                    Toast.makeText(
                        context,
                        R.string.rest_end_must_follow_start,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                durationMinutes = (
                        (endTime!! - startTime!! + 59_999L) / 60_000L
                        ).toInt().coerceAtLeast(1)
                finalEndTime = endTime!!
            } else {
                val enteredMinutes = etMinutes.text.toString().toIntOrNull()
                if (enteredMinutes == null || enteredMinutes <= 0) {
                    Toast.makeText(
                        context,
                        R.string.rest_duration_invalid,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                durationMinutes = enteredMinutes
                finalEndTime = endTime
                    ?: startTime?.plus(durationMinutes * 60_000L)
                            ?: System.currentTimeMillis()
            }

            val log = existingLog ?: HealthLogEntry()

            log.type = context.getString(R.string.rest_title)
            log.memberId = memberId
            log.memberName = memberName
            log.startTimestamp = startTime
            log.timestamp = finalEndTime
            log.value = "$durationMinutes min"

            HealthHelper.saveOrUpdateLogEntry(context, log)
            dialog.dismiss()
            onChanged()
        }

        if (existingLog != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                HealthHelper.deleteLogEntry(context, existingLog.id)
                dialog.dismiss()
                onChanged()
            }
        }
    }
}