package com.interli.plural.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.R
import com.interli.plural.features.subsystem.SubsystemActivity
import com.interli.plural.features.subsystem.SubsystemGroup
import com.interli.plural.core.ColorHelper

class SubsystemFronterWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_current_fronter)

            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val groupId = prefs.getString("subsystem_widget_$appWidgetId", null)

            val intent = Intent(context, SubsystemActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
            val json = sharedPref.getString("subsystem_data", "[]") ?: "[]"
            val type = object : TypeToken<MutableList<SubsystemGroup>>() {}.type
            val groups: List<SubsystemGroup> = try { Gson().fromJson(json, type) } catch (e: Exception) { emptyList() }

            val group = groups.find { it.id == groupId }
            val fronters = group?.members?.filter { it.isFronting } ?: emptyList()

            val namesText = if (fronters.isEmpty()) context.getString(R.string.nobody_fronting)
            else fronters.joinToString(", ") { it.name }

            views.setTextViewText(R.id.tvWidgetHeader, group?.name ?: "Subsystem")
            views.setTextViewText(R.id.tvWidgetFronterNames, namesText)

            val textColor = ColorHelper.getTextColor(context)
            views.setTextColor(R.id.tvWidgetFronterNames, textColor)
            views.setTextColor(R.id.tvWidgetHeader, ColorHelper.getBtnColor(context))
            views.setInt(R.id.widget_root, "setBackgroundColor", ColorHelper.getBgColor(context))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(context, SubsystemFronterWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, SubsystemFronterWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}