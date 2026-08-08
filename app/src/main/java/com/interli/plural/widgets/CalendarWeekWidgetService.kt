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
        val events: List<CalendarEvent> = try { Gson().fromJson(json, type) ?: emptyList() } catch(e: Exception) { emptyList() }

        listItems.clear()
        val daySdf = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
        val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        for (i in 0..6) {
            val startOfDay = cal.timeInMillis
            val endOfDay = startOfDay + (24 * 3600 * 1000) - 1

            val dayEvents = events.filter { it.startTime in startOfDay..endOfDay }.sortedBy { it.startTime }

            if (dayEvents.isNotEmpty()) {
                listItems.add(Triple(0, daySdf.format(Date(startOfDay)), ""))

                dayEvents.forEach { event ->
                    listItems.add(Triple(1, timeSdf.format(Date(event.startTime)), event.title))
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        // Veiligheidscheck: als de positie niet klopt, geef een leeg item terug om een crash te voorkomen
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