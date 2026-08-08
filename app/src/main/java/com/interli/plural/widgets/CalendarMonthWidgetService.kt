package com.interli.plural.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.CalendarEvent
import com.interli.plural.R
import com.interli.plural.core.ColorHelper
import java.util.*

class CalendarMonthWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CalendarMonthRemoteViewsFactory(this.applicationContext)
    }
}

class CalendarMonthRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var daysList = mutableListOf<CalendarDay>()

    data class CalendarDay(
        val text: String,
        val events: List<Pair<String, Int?>>,
        val isCurrentDay: Boolean
    )

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun getCount(): Int = daysList.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun onDataSetChanged() {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val json = sharedPref.getString("calendar_events", "[]") ?: "[]"
        val rawEvents: List<CalendarEvent> = try {
            Gson().fromJson(json, object : TypeToken<List<CalendarEvent>>() {}.type) ?: emptyList()
        } catch(e: Exception) { emptyList() }

        daysList.clear()
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        val today = cal.get(Calendar.DAY_OF_MONTH)

        cal.set(currentYear, currentMonth, 1, 0, 0, 0)
        val startOfMonth = cal.timeInMillis
        val endOfMonth = startOfMonth + (32L * 24 * 3600 * 1000)

        val allEvents = expandEvents(rawEvents, startOfMonth, endOfMonth)

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val paddingDays = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
        for (i in 0 until paddingDays) {
            daysList.add(CalendarDay("", emptyList(), false))
        }

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val tempCal = Calendar.getInstance()
        for (day in 1..daysInMonth) {
            tempCal.set(currentYear, currentMonth, day, 0, 0, 0)
            val startOfDay = tempCal.timeInMillis
            val endOfDay = startOfDay + (24 * 3600 * 1000) - 1

            val dayEvents = allEvents.filter { it.startTime in startOfDay..endOfDay }
                .sortedBy { it.startTime }
                .map { Pair(it.title, it.color) }

            daysList.add(CalendarDay(day.toString(), dayEvents, day == today))
        }
    }

    private fun expandEvents(events: List<CalendarEvent>, start: Long, end: Long): List<CalendarEvent> {
        val expanded = mutableListOf<CalendarEvent>()
        events.forEach { event ->
            if (event.recurrence == null) {
                if (event.endTime >= start && event.startTime <= end) expanded.add(event)
            } else {
                val cal = Calendar.getInstance().apply { timeInMillis = event.startTime }
                val duration = event.endTime - event.startTime
                while (cal.timeInMillis <= end) {
                    val currentStart = cal.timeInMillis
                    val currentEnd = currentStart + duration
                    val matches = when (event.recurrence) {
                        "DAILY" -> true
                        "WEEKLY" -> true
                        "MONTHLY" -> true
                        "YEARLY" -> true
                        "CUSTOM" -> event.recurrenceDays?.contains(cal.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }) == true
                        else -> false
                    }
                    val isExcluded = event.excludedDates?.any { it == currentStart } == true
                    val isPastEnd = event.recurrenceUntil?.let { currentStart > it } ?: false
                    if (matches && !isExcluded && !isPastEnd && currentEnd >= start && currentStart <= end) {
                        expanded.add(event.copy(startTime = currentStart, endTime = currentEnd))
                    }
                    when (event.recurrence) {
                        "DAILY", "CUSTOM" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                        "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                        "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                        "YEARLY" -> cal.add(Calendar.YEAR, 1)
                        else -> break
                    }
                }
            }
        }
        return expanded
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= daysList.size) return RemoteViews(context.packageName, R.layout.widget_calendar_month_day)
        val views = RemoteViews(context.packageName, R.layout.widget_calendar_month_day)
        val day = daysList[position]
        val textColor = ColorHelper.getTextColor(context)
        val btnColor = ColorHelper.getBtnColor(context)

        views.setTextViewText(R.id.tvWidgetDayNumber, day.text)
        if (day.isCurrentDay && day.text.isNotEmpty()) {
            views.setTextColor(R.id.tvWidgetDayNumber, Color.WHITE)
            views.setInt(R.id.tvWidgetDayNumber, "setBackgroundColor", btnColor)
        } else {
            views.setTextColor(R.id.tvWidgetDayNumber, textColor)
            views.setInt(R.id.tvWidgetDayNumber, "setBackgroundColor", Color.TRANSPARENT)
        }

        val eventViewIds = intArrayOf(R.id.tvWidgetEvent1, R.id.tvWidgetEvent2, R.id.tvWidgetEvent3, R.id.tvWidgetEvent4, R.id.tvWidgetEvent5)
        for (i in 0 until 5) {
            val viewId = eventViewIds[i]
            if (i < day.events.size && day.text.isNotEmpty()) {
                val event = day.events[i]
                val eventColor = event.second ?: btnColor

                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(viewId, event.first)
                views.setInt(viewId, "setBackgroundColor", eventColor)

                views.setTextColor(viewId, getContrastColor(eventColor))
            } else {
                views.setViewVisibility(viewId, View.GONE)
            }
        }
        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.widget_day_root, fillInIntent)
        return views
    }
    private fun getContrastColor(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000
        return if (yiq >= 128) Color.BLACK else Color.WHITE
    }
}
