package com.interli.plural.widgets

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.TodoList
import com.interli.plural.R
import com.interli.plural.core.ColorHelper

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TodoRemoteViewsFactory(this.applicationContext)
    }
}

class TodoRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var taskList = mutableListOf<TodoItemInfo>()

    data class TodoItemInfo(
        val title: String,
        val status: String,
        val listId: String,
        val listTitle: String,
        val isFirstInList: Boolean
    )

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun getCount(): Int = taskList.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun onDataSetChanged() {
        val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val json = sharedPref.getString("todo_lists", "[]") ?: "[]"
        val type = object : TypeToken<List<TodoList>>() {}.type
        val lists: List<TodoList> = try {
            Gson().fromJson(json, type) ?: emptyList()
        } catch(e: Exception) { emptyList() }

        taskList.clear()
        lists.forEach { list ->
            val activeTasks = list.tasks.filter { it.status != "CHECKED" }

            activeTasks.forEachIndexed { index, task ->
                val statusChar = when(task.status) {
                    "FORWARD" -> "→"
                    "BACKWARD" -> "←"
                    "WAITING" -> "⏳"
                    "CANCELED" -> "✕"
                    "QUESTION" -> "?"
                    else -> "☐"
                }

                taskList.add(TodoItemInfo(
                    title = task.title,
                    status = statusChar,
                    listId = list.id,
                    listTitle = list.title,
                    isFirstInList = (index == 0)
                ))
            }
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= taskList.size) return RemoteViews(context.packageName, R.layout.widget_todo_item)

        val views = RemoteViews(context.packageName, R.layout.widget_todo_item)
        val item = taskList[position]

        views.setTextViewText(R.id.tvWidgetTaskTitle, item.title)
        views.setTextViewText(R.id.tvWidgetTaskStatus, item.status)

        val textColor = ColorHelper.getTextColor(context)
        val btnColor = ColorHelper.getBtnColor(context)

        if (item.isFirstInList) {
            views.setViewVisibility(R.id.tvWidgetListName, View.VISIBLE)
            views.setTextViewText(R.id.tvWidgetListName, item.listTitle)
            views.setTextColor(R.id.tvWidgetListName, btnColor)
        } else {
            views.setViewVisibility(R.id.tvWidgetListName, View.GONE)
        }

        views.setTextColor(R.id.tvWidgetTaskTitle, textColor)
        views.setTextColor(R.id.tvWidgetTaskStatus, textColor)

        val checkIntent = Intent().apply {
            action = TodoWidgetProvider.ACTION_CHECK_TASK
            putExtra("task_title", item.title)
            putExtra("list_id", item.listId)
        }
        views.setOnClickFillInIntent(R.id.tvWidgetTaskStatus, checkIntent)

        val openIntent = Intent().apply {
            putExtra("list_id", item.listId)
        }
        views.setOnClickFillInIntent(R.id.tvWidgetTaskTitle, openIntent)

        return views
    }
}