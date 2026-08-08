package com.interli.plural.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.R
import com.interli.plural.TodoList
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.todo.TodoActivity

class TodoWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_CHECK_TASK = "com.interli.plural.ACTION_CHECK_TASK"

        fun sendRefreshBroadcast(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TodoWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.lvWidgetTodo)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, TodoWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.widget_todo).apply {
                setRemoteAdapter(R.id.lvWidgetTodo, intent)
                setEmptyView(R.id.lvWidgetTodo, R.id.tvWidgetEmpty)

                val bgColor = ColorHelper.getBgColor(context)
                val btnColor = ColorHelper.getBtnColor(context)
                setInt(R.id.widget_root, "setBackgroundColor", bgColor)
                setTextColor(R.id.tvWidgetHeader, btnColor)
            }

            val clickIntent = Intent(context, TodoWidgetProvider::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, clickIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setPendingIntentTemplate(R.id.lvWidgetTodo, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CHECK_TASK) {
            val taskTitle = intent.getStringExtra("task_title")
            val listId = intent.getStringExtra("list_id")
            checkTaskInBackground(context, listId, taskTitle)
        } else if (intent.hasExtra("list_id")) {
            val listId = intent.getStringExtra("list_id")
            val openIntent = Intent(context, TodoActivity::class.java).apply {
                putExtra("list_id", listId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(openIntent)
        }
        super.onReceive(context, intent)
    }

    private fun checkTaskInBackground(context: Context, listId: String?, taskTitle: String?) {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPref.getString("todo_lists", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<TodoList>>() {}.type
        val lists: MutableList<TodoList> = try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) { mutableListOf() }

        val statuses = listOf("EMPTY", "FORWARD", "BACKWARD", "WAITING", "QUESTION", "CANCELED", "CHECKED")

        lists.find { it.id == listId }?.let { list ->
            list.tasks.find { it.title == taskTitle }?.let { task ->
                val currentIdx = statuses.indexOf(task.status).coerceAtLeast(0)
                val nextIdx = (currentIdx + 1) % statuses.size
                task.status = statuses[nextIdx]
            }
        }

        sharedPref.edit().putString("todo_lists", gson.toJson(lists)).apply()
        sendRefreshBroadcast(context)
    }
}
