package com.interli.plural.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.interli.plural.R
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.mood.MoodActivity

class MoodLogWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_mood_log)

            setupMoodClick(context, views, R.id.btnWidgetMoodRad, "mood_rad")
            setupMoodClick(context, views, R.id.btnWidgetMoodGood, "mood_good")
            setupMoodClick(context, views, R.id.btnWidgetMoodMeh, "mood_meh")
            setupMoodClick(context, views, R.id.btnWidgetMoodBad, "mood_bad")
            setupMoodClick(context, views, R.id.btnWidgetMoodAwful, "mood_awful")

            val bgColor = ColorHelper.getBgColor(context)
            val textColor = ColorHelper.getTextColor(context)

            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)

            val scoreIds = intArrayOf(
                R.id.tvWidgetMoodScore5, R.id.tvWidgetMoodScore4,
                R.id.tvWidgetMoodScore3, R.id.tvWidgetMoodScore2,
                R.id.tvWidgetMoodScore1
            )
            for (id in scoreIds) {
                views.setTextColor(id, textColor)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun setupMoodClick(context: Context, views: RemoteViews, viewId: Int, moodKey: String) {
        val intent = Intent(context, MoodActivity::class.java).apply {
            putExtra("quick_log_mood", moodKey)
            action = "COM.INTERLI.PLURAL.MOOD_$moodKey"
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(viewId, pendingIntent)
    }
}