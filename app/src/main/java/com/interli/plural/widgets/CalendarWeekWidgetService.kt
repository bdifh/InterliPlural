package com.interli.plural.widgets

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.CalendarEvent
import com.interli.plural.R
import com.interli.plural.core.ColorHelper
import java.text.SimpleDateFormat
import java.util.*

class CalendarWeekWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CalendarWeekRemoteViewsFactory(this.applicationContext)
    }
}

class CalendarWeekRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var listItems = mutableListOf<Triple<Int, String, String>>()

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun getCount(): Int = listItems.size
    override fun getViewTypeCount(): Int = 2
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
        listItems.clear()
        val daySdf = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
        val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val startOfPeriod = cal.timeInMillis
        val endOfPeriod = startOfPeriod + (7L * 24 * 3600 * 1000)
        val allEvents = expandEvents(rawEvents, startOfPeriod, endOfPeriod)
        for (i in 0..6) {
            val startOfDay = cal.timeInMillis
            val endOfDay = startOfDay + (24 * 3600 * 1000) - 1
            val dayEvents = allEvents.filter { !it.hideInWeek && it.startTime in startOfDay..endOfDay }.sortedBy { it.startTime }

            if (dayEvents.isNotEmpty()) {
                listItems.add(Triple(0, daySdf.format(Date(startOfDay)), ""))

                dayEvents.forEach { event ->
                    listItems.add(Triple(1, timeSdf.format(Date(event.startTime)), event.title))
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
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
        if (position < 0 || position >= listItems.size) {
            return RemoteViews(context.packageName, R.layout.widget_calendar_item)
        }

        val (type, text1, text2) = listItems[position]
        val textColor = ColorHelper.getTextColor(context)
        val btnColor = ColorHelper.getBtnColor(context)

        return if (type == 0) {
            val headerViews = RemoteViews(context.packageName, R.layout.widget_calendar_week_header)
            headerViews.setTextViewText(R.id.tvWidgetHeaderDate, text1)
            headerViews.setTextColor(R.id.tvWidgetHeaderDate, btnColor)
            headerViews
        } else {
            val itemViews = RemoteViews(context.packageName, R.layout.widget_calendar_item)
            itemViews.setTextViewText(R.id.tvWidgetEventTime, text1)
            itemViews.setTextViewText(R.id.tvWidgetEventTitle, text2)
            itemViews.setTextColor(R.id.tvWidgetEventTitle, textColor)

            val fillInIntent = Intent()
            itemViews.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)
            itemViews
        }
    }
}