package com.interli.plural.features.calendar

import java.util.Calendar
import kotlin.math.round
import com.interli.plural.CalendarEvent

object CalendarRecurrenceHelper {

    fun matchesIntervalWeek(
        eventStart: Long,
        occurrenceStart: Long,
        interval: Int?
    ): Boolean {
        val repeatEvery = interval?.coerceAtLeast(1) ?: 1
        if (repeatEvery == 1) return true

        fun startOfWeek(timestamp: Long): Long {
            val calendar = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val daysSinceMonday =
                (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            calendar.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
            return calendar.timeInMillis
        }

        val millisecondsPerWeek = 7L * 24 * 60 * 60 * 1000
        val weekDifference = round(
            (startOfWeek(occurrenceStart) - startOfWeek(eventStart)).toDouble() /
                    millisecondsPerWeek
        ).toInt()

        return weekDifference >= 0 && weekDifference % repeatEvery == 0
    }
    fun nextReminderTime(event: CalendarEvent, afterTime: Long): Long? {
        val configuredReminderTime = event.reminderTime ?: return null

        if (event.recurrence == null) {
            return configuredReminderTime.takeIf { it > afterTime }
        }

        val reminderOffset = configuredReminderTime - event.startTime
        val occurrence = Calendar.getInstance().apply {
            timeInMillis = event.startTime
        }

        while (true) {
            val occurrenceStart = occurrence.timeInMillis

            if (event.recurrenceUntil?.let { occurrenceStart > it } == true) {
                return null
            }

            val dayOfWeek = occurrence.get(Calendar.DAY_OF_WEEK).let {
                if (it == Calendar.SUNDAY) 7 else it - 1
            }

            val matches = when (event.recurrence) {
                "DAILY", "WEEKLY", "MONTHLY", "YEARLY" -> true
                "CUSTOM" ->
                    event.recurrenceDays?.contains(dayOfWeek) == true &&
                            matchesIntervalWeek(
                                event.startTime,
                                occurrenceStart,
                                event.recurrenceInterval
                            )
                else -> return null
            }

            val isExcluded = event.excludedDates?.contains(occurrenceStart) == true
            val reminderForOccurrence = occurrenceStart + reminderOffset

            if (matches && !isExcluded && reminderForOccurrence > afterTime) {
                return reminderForOccurrence
            }

            when (event.recurrence) {
                "DAILY" -> occurrence.add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> occurrence.add(
                    Calendar.WEEK_OF_YEAR,
                    event.recurrenceInterval?.coerceAtLeast(1) ?: 1
                )
                "MONTHLY" -> occurrence.add(Calendar.MONTH, 1)
                "YEARLY" -> occurrence.add(Calendar.YEAR, 1)
                "CUSTOM" -> occurrence.add(Calendar.DAY_OF_YEAR, 1)
                else -> return null
            }
        }
    }
}