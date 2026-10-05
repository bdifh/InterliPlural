package com.interli.plural.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.interli.plural.R
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.diary.CreatePostActivity
import com.interli.plural.features.sysmedia.SysmediaActivity
import com.interli.plural.features.sysmedia.SysmediaPostDetailActivity

class SysmediaTimelineWidgetProvider : AppWidgetProvider() {

    companion object {
        fun sendRefreshBroadcast(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SysmediaTimelineWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            val updateIntent = Intent(context, SysmediaTimelineWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(updateIntent)
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.lvWidgetSysmediaPosts)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val serviceIntent = Intent(context, SysmediaTimelineWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.widget_sysmedia_timeline).apply {
                setRemoteAdapter(R.id.lvWidgetSysmediaPosts, serviceIntent)
                setEmptyView(R.id.lvWidgetSysmediaPosts, R.id.tvWidgetSysmediaEmpty)

                val bgColor = ColorHelper.getBgColor(context)
                val btnColor = ColorHelper.getBtnColor(context)
                val textColor = ColorHelper.getTextColor(context)

                setInt(R.id.widget_root, "setBackgroundColor", bgColor)
                setTextColor(R.id.tvWidgetHeader, btnColor)
                setTextColor(R.id.tvWidgetSysmediaEmpty, textColor)
                setInt(R.id.btnWidgetNewPost, "setColorFilter", btnColor)
            }

            val mainIntent = Intent(context, SysmediaActivity::class.java)
            val mainPendingIntent = PendingIntent.getActivity(
                context, 1001, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tvWidgetHeader, mainPendingIntent)

            val createPostIntent = Intent(context, CreatePostActivity::class.java)
            val createPostPendingIntent = PendingIntent.getActivity(
                context, 1002, createPostIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetNewPost, createPostPendingIntent)

            val detailIntent = Intent(context, SysmediaPostDetailActivity::class.java)
            val detailPendingIntent = PendingIntent.getActivity(
                context, 1003, detailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.lvWidgetSysmediaPosts, detailPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, SysmediaTimelineWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.lvWidgetSysmediaPosts)
    }
}