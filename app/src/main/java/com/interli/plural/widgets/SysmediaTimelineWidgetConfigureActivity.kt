package com.interli.plural.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.interli.plural.R
import com.interli.plural.core.ColorHelper
import com.interli.plural.features.member.MemberHelper

class SysmediaTimelineWidgetConfigureActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val bgColor = ColorHelper.getBgColor(this)
        val textColor = ColorHelper.getTextColor(this)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(bgColor)
        }

        val title = TextView(this).apply {
            text = getString(R.string.sysmedia_timeline_widget_select_accounts)
            textSize = 18f
            setPadding(0, 0, 0, 32)
            setTextColor(textColor)
        }
        rootLayout.addView(title)

        val people = MemberHelper.loadAllPeople(this).filter { !it.isArchived }

        val cbAll = CheckBox(this).apply {
            text = getString(R.string.select_all_accounts)
            setTextColor(textColor)
            isChecked = true
        }
        rootLayout.addView(cbAll)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val checkBoxes = mutableMapOf<String, CheckBox>()
        people.forEach { person ->
            val handleText = person.sysmediaProfile?.handle?.let { " (@$it)" } ?: ""
            val cb = CheckBox(this).apply {
                text = "${person.name}$handleText"
                setTextColor(textColor)
                isEnabled = false
            }
            scrollContent.addView(cb)
            checkBoxes[person.id] = cb
        }

        cbAll.setOnCheckedChangeListener { _, isChecked ->
            checkBoxes.values.forEach { cb ->
                cb.isEnabled = !isChecked
                if (isChecked) cb.isChecked = false
            }
        }

        scrollView.addView(scrollContent)
        rootLayout.addView(scrollView)

        val btnSave = Button(this).apply {
            text = getString(R.string.save)
            setOnClickListener {
                val selectedIds = if (cbAll.isChecked) {
                    listOf("ALL")
                } else {
                    checkBoxes.filter { it.value.isChecked }.map { it.key }
                }

                val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString(
                    "sysmedia_timeline_widget_$appWidgetId",
                    selectedIds.joinToString(",")
                ).apply()

                val appWidgetManager = AppWidgetManager.getInstance(this@SysmediaTimelineWidgetConfigureActivity)
                val provider = SysmediaTimelineWidgetProvider()
                provider.onUpdate(
                    this@SysmediaTimelineWidgetConfigureActivity,
                    appWidgetManager,
                    intArrayOf(appWidgetId)
                )
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lvWidgetSysmediaPosts)

                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(RESULT_OK, resultValue)
                finish()
            }
        }
        rootLayout.addView(btnSave)

        setContentView(rootLayout)
    }
}