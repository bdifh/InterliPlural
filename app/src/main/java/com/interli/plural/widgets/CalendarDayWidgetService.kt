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
        val events: List<CalendarEvent> = try { Gson().fromJson(json, type) } catch(e: Exception) { emptyList() }

        val now = Calendar.getInstance()
        val startOfDay = now.clone() as Calendar
        startOfDay.set(Calendar.HOUR_OF_DAY, 0); startOfDay.set(Calendar.MINUTE, 0); startOfDay.set(Calendar.SECOND, 0)
        val endOfDay = startOfDay.timeInMillis + 24 * 3600 * 1000

        val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())

        eventList.clear()
        events.filter { it.startTime in startOfDay.timeInMillis..endOfDay }
            .sortedBy { it.startTime }
            .forEach { event ->
                eventList.add(Pair(timeSdf.format(Date(event.startTime)), event.title))
            }
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