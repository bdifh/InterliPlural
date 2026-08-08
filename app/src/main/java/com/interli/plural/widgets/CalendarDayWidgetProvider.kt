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

class CalendarDayWidgetProvider : AppWidgetProvider() {

    companion object {
        fun sendRefreshBroadcast(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CalendarDayWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.lvWidgetCalendar)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, CalendarDayWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.widget_calendar_day).apply {
                setRemoteAdapter(R.id.lvWidgetCalendar, intent)
                setEmptyView(R.id.lvWidgetCalendar, R.id.tvWidgetEmpty)

                val bgColor = ColorHelper.getBgColor(context)
                val btnColor = ColorHelper.getBtnColor(context)
                setInt(R.id.widget_root, "setBackgroundColor", bgColor)
                setTextColor(R.id.tvWidgetHeader, btnColor)
            }

            val clickIntent = Intent(context, CalendarActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, clickIntent, PendingIntent.FLAG_IMMUTABLE)
            views.setPendingIntentTemplate(R.id.lvWidgetCalendar, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}