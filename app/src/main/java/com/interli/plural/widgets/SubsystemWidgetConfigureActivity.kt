package com.interli.plural.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.R
import com.interli.plural.features.subsystem.SubsystemGroup
import com.interli.plural.core.ColorHelper



class SubsystemWidgetConfigureActivity : AppCompatActivity() {
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
            setBackgroundColor(ColorHelper.getBgColor(this@SubsystemWidgetConfigureActivity))
        }

        val title = TextView(this).apply {
            text = "Select subsystem group for widget:"
            textSize = 18f
            setPadding(0, 0, 0, 32)
            setTextColor(ColorHelper.getTextColor(this@SubsystemWidgetConfigureActivity))
        }
        layout.addView(title)

        val sharedPref = getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val json = sharedPref.getString("subsystem_data", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<SubsystemGroup>>() {}.type
        val groups: List<SubsystemGroup> = Gson().fromJson(json, type) ?: emptyList()

        val radioGroup = RadioGroup(this)
        groups.forEach { group ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = group.name
                tag = group.id
                setTextColor(ColorHelper.getTextColor(this@SubsystemWidgetConfigureActivity))
            }
            radioGroup.addView(rb)
        }
        layout.addView(radioGroup)

        val btnSave = Button(this).apply {
            text = getString(R.string.save)
            setOnClickListener {
                val checkedId = radioGroup.checkedRadioButtonId
                if (checkedId != -1) {
                    val selectedRb = radioGroup.findViewById<RadioButton>(checkedId)
                    val groupId = selectedRb.tag as String
                    val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("subsystem_widget_$appWidgetId", groupId).apply()

                    val appWidgetManager = AppWidgetManager.getInstance(this@SubsystemWidgetConfigureActivity)
                    val provider = SubsystemFronterWidget()
                    provider.onUpdate(this@SubsystemWidgetConfigureActivity, appWidgetManager, intArrayOf(appWidgetId))

                    val resultValue = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) }
                    setResult(RESULT_OK, resultValue)
                    finish()
                } else {
                    Toast.makeText(this@SubsystemWidgetConfigureActivity, "Please select a group", Toast.LENGTH_SHORT).show()
                }
            }
        }

        layout.addView(btnSave)
        setContentView(layout)
    }
}