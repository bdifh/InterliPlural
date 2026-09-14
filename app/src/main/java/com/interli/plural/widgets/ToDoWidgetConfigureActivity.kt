package com.interli.plural.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.R
import com.interli.plural.TodoList
import com.interli.plural.core.ColorHelper

class TodoWidgetConfigureActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(ColorHelper.getBgColor(this@TodoWidgetConfigureActivity))
        }

        val title = TextView(this).apply {
            text = getString(R.string.todo_widget_select_lists)
            textSize = 18f
            setPadding(0, 0, 0, 32)
            setTextColor(ColorHelper.getTextColor(this@TodoWidgetConfigureActivity))
        }
        layout.addView(title)

        val sharedPref = getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val json = sharedPref.getString("todo_lists", "[]") ?: "[]"
        val lists: List<TodoList> = Gson().fromJson(json, object : TypeToken<List<TodoList>>() {}.type) ?: emptyList()

        val checkBoxes = mutableMapOf<String, CheckBox>()
        lists.forEach { list ->
            val cb = CheckBox(this).apply {
                text = list.title
                setTextColor(ColorHelper.getTextColor(this@TodoWidgetConfigureActivity))
            }
            layout.addView(cb)
            checkBoxes[list.id] = cb
        }

        val btnSave = Button(this).apply {
            text = getString(R.string.save)
            setOnClickListener {
                val selectedIds = checkBoxes.filter { it.value.isChecked }.map { it.key }
                val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("todo_widget_$appWidgetId", selectedIds.joinToString(",")).apply()

                val appWidgetManager = AppWidgetManager.getInstance(this@TodoWidgetConfigureActivity)
                val provider = TodoWidgetProvider()
                provider.onUpdate(this@TodoWidgetConfigureActivity, appWidgetManager, intArrayOf(appWidgetId))

                val resultValue = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) }
                setResult(RESULT_OK, resultValue)
                finish()
            }
        }
        layout.addView(btnSave)

        setContentView(layout)
    }
}