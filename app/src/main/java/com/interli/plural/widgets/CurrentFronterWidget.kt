package com.interli.plural.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.MainActivity
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.core.ColorHelper

class CurrentFronterWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_current_fronter)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
            val json = sharedPref.getString("people_list", "[]") ?: "[]"
            val people: List<Person> = try {
                Gson().fromJson(json, object : TypeToken<List<Person>>() {}.type)
            } catch (e: Exception) { emptyList() }

            val fronters = people.filter { it.isFront && !it.isArchived }
            val namesText = if (fronters.isEmpty()) {
                context.getString(R.string.nobody_fronting)
            } else {
                fronters.joinToString(", ") { it.name }
            }

            val bgColor = ColorHelper.getBgColor(context)
            val textColor = ColorHelper.getTextColor(context)
            val btnColor = ColorHelper.getBtnColor(context)

            views.setTextViewText(R.id.tvWidgetFronterNames, namesText)

            views.setTextColor(R.id.tvWidgetFronterNames, textColor)
            views.setTextColor(R.id.tvWidgetHeader, btnColor)

            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
    companion object {
        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, CurrentFronterWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}