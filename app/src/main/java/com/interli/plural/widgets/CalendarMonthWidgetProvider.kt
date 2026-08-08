package com.interli.plural.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.net.toUri
import com.interli.plural.R
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.calendar.CalendarActivity
import java.text.SimpleDateFormat
import java.util.*

class CalendarMonthWidgetProvider : AppWidgetProvider() {

    companion object {
        fun sendRefreshBroadcast(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CalendarMonthWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.gvWidgetCalendar)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, CalendarMonthWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.widget_calendar_month).apply {
                setRemoteAdapter(R.id.gvWidgetCalendar, intent)

                val now = Calendar.getInstance()
                val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now.time)
                setTextViewText(R.id.tvWidgetMonthTitle, monthName)

                val bgColor = ColorHelper.getBgColor(context)
                val textColor = ColorHelper.getTextColor(context)
                val btnColor = ColorHelper.getBtnColor(context)

                setInt(R.id.widget_root, "setBackgroundColor", bgColor)
                setTextColor(R.id.tvWidgetMonthTitle, btnColor)


                val daySdf = SimpleDateFormat("EEEEE", Locale.getDefault())
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

                val headerIds = intArrayOf(R.id.tvHeader1, R.id.tvHeader2, R.id.tvHeader3, R.id.tvHeader4, R.id.tvHeader5, R.id.tvHeader6, R.id.tvHeader7)
                headerIds.forEach { id ->
                    setTextViewText(id, daySdf.format(cal.time))
                    setTextColor(id, textColor)
                    setFloat(id, "setAlpha", 0.5f)
                    cal.add(Calendar.DAY_OF_WEEK, 1)
                }
            }
            val clickIntent = Intent(context, CalendarActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, clickIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setPendingIntentTemplate(R.id.gvWidgetCalendar, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}