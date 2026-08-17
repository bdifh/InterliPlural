package com.interli.plural.widgets

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.CalendarEvent
import com.interli.plural.R
import com.interli.plural.core.ColorHelper
import java.text.SimpleDateFormat
import java.util.*

class CalendarDayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CalendarRemoteViewsFactory(this.applicationContext)
    }
}

class CalendarRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var eventList = mutableListOf<Pair<String, String>>() // <Tijd, Titel>

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun getCount(): Int = eventList.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun onDataSetChanged() {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val json = sharedPref.getString("calendar_events", "[]") ?: "[]"
        val type = object : TypeToken<List<CalendarEvent>>() {}.type
        val rawEvents: List<CalendarEvent> = try {
            Gson().fromJson(json, type) ?: emptyList()
        } catch(e: Exception) {
            emptyList()
        }
        val now = Calendar.getInstance()
        val startOfDay = now.clone() as Calendar
        startOfDay.set(Calendar.HOUR_OF_DAY, 0); startOfDay.set(Calendar.MINUTE, 0); startOfDay.set(Calendar.SECOND, 0); startOfDay.set(Calendar.MILLISECOND, 0)
        val endOfDay = startOfDay.timeInMillis + 24 * 3600 * 1000
        val allEvents = expandEvents(rawEvents, startOfDay.timeInMillis, endOfDay)
        val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        eventList.clear()
        allEvents.filter { !it.hideInDay && it.startTime in startOfDay.timeInMillis..endOfDay }
            .sortedBy { it.startTime }
            .forEach { event ->
                eventList.add(Pair(timeSdf.format(Date(event.startTime)), event.title))
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
                val limit = end

                while (cal.timeInMillis <= limit) {
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
                    if (cal.timeInMillis > end) break
                }
            }
        }
        return expanded
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= eventList.size) return RemoteViews(context.packageName, R.layout.widget_calendar_item)

        val views = RemoteViews(context.packageName, R.layout.widget_calendar_item)
        val (time, title) = eventList[position]

        views.setTextViewText(R.id.tvWidgetEventTime, time)
        views.setTextViewText(R.id.tvWidgetEventTitle, title)

        val textColor = ColorHelper.getTextColor(context)
        views.setTextColor(R.id.tvWidgetEventTitle, textColor)

        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }
}