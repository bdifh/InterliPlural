package com.interli.plural.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.MainActivity
import com.interli.plural.R
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.mood.MoodStatsActivity
import java.util.*

class MoodAverageWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_mood_average)

            val intent = Intent(context, MoodStatsActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
            val json = sharedPref.getString("mood_entries", "[]") ?: "[]"
            val entries: List<MoodEntry> = try {
                Gson().fromJson(json, object : TypeToken<List<MoodEntry>>() {}.type)
            } catch (e: Exception) { emptyList() }

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val startOfToday = cal.timeInMillis
            val todayEntries = entries.filter { it.timestamp >= startOfToday }

            val bgColor = ColorHelper.getBgColor(context)
            val textColor = ColorHelper.getTextColor(context)
            val btnColor = ColorHelper.getBtnColor(context)

            if (todayEntries.isEmpty()) {
                views.setTextViewText(R.id.tvWidgetMoodAverage, "--")
                views.setViewVisibility(R.id.layoutWidgetMoodCircle, View.GONE)
            } else {
                views.setViewVisibility(R.id.layoutWidgetMoodCircle, View.VISIBLE)

                val avg = todayEntries.map {
                    when(it.moodLabel) {
                        "mood_rad" -> 5.0
                        "mood_good" -> 4.0
                        "mood_meh" -> 3.0
                        "mood_bad" -> 2.0
                        "mood_awful" -> 1.0
                        else -> 3.0
                    }
                }.average()

                views.setTextViewText(R.id.tvWidgetMoodAverage, String.format("%.1f", avg))
                val rotation = when {
                    avg >= 4.5 -> 0f
                    avg >= 3.5 -> 45f
                    avg >= 2.5 -> 90f
                    avg >= 1.5 -> 135f
                    else -> 180f
                }
                views.setFloat(R.id.tvWidgetMoodThumb, "setRotation", rotation)
                views.setInt(R.id.layoutWidgetMoodCircle, "setBackgroundColor", bgColor)
            }

            views.setTextColor(R.id.tvWidgetMoodAverage, textColor)
            views.setTextColor(R.id.tvWidgetHeader, btnColor)
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    data class MoodEntry(val timestamp: Long, val moodLabel: String)

    companion object {
        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, MoodAverageWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}