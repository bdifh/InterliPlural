package com.interli.plural.features.calendar

import java.util.Calendar
import kotlin.math.round

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
}