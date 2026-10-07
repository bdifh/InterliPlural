package com.interli.plural.core

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.LocaleHelper
import com.interli.plural.core.SettingsActivity
import com.interli.plural.core.SilentUi
import com.interli.plural.core.StatisticsActivity
import com.interli.plural.features.calendar.CalendarActivity
import com.interli.plural.features.diary.DiaryActivity
import com.interli.plural.features.member.WhoAmIActivity
import com.interli.plural.features.mood.MemberMoodCorrelationActivity
import com.interli.plural.features.mood.MoodActivity
import com.interli.plural.features.mood.MoodStatsActivity
import com.interli.plural.features.relations.RelationsActivity
import com.interli.plural.features.sysmedia.SysmediaActivity
import com.interli.plural.features.todo.TodoActivity
import com.interli.plural.MainActivity
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.features.subsystem.SubsystemActivity
import com.interli.plural.features.health.HealthActivity

abstract class BaseActivity : AppCompatActivity() {
    private fun applyFixedDisplayScale(context: android.content.Context): android.content.Context {
        val res = context.resources
        val dm = res.displayMetrics
        val config = android.content.res.Configuration(res.configuration)
        val isTablet = config.smallestScreenWidthDp >= 600
        val targetWidthDp = if (isTablet) 600f else 446f
        val shorterSidePx = kotlin.math.min(dm.widthPixels, dm.heightPixels).toFloat()
        val targetDensity = shorterSidePx / targetWidthDp
        val targetDensityDpi = (160 * targetDensity).toInt()
        val sharedPref = context.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
        val fontMultiplier = try { sharedPref.getFloat("font_size_multiplier", 1.0f) } catch (e: Exception) {
            (sharedPref.all["font_size_multiplier"] as? Number)?.toFloat() ?: 1.0f
        }
        config.densityDpi = targetDensityDpi
        config.fontScale = fontMultiplier
        dm.density = targetDensity
        dm.scaledDensity = targetDensity * fontMultiplier
        dm.densityDpi = targetDensityDpi
        return context.createConfigurationContext(config)
    }
    override fun attachBaseContext(newBase: android.content.Context) {
        val scaledContext = applyFixedDisplayScale(newBase)
        super.attachBaseContext(LocaleHelper.wrapContext(scaledContext))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SilentUi.disableSoundEffects(window?.decorView)
    }
    override fun onResume() {
        super.onResume()
        ColorHelper.applySettings(this)
    }

    protected fun setupNavigationDrawer() {
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)

        if (drawerLayout != null && navigationView != null && toolbar != null) {
            ColorHelper.styleNavigationView(navigationView)
            toolbar.setTitleTextColor(ColorHelper.getTextColor(this))
            toolbar.setNavigationIconTint(ColorHelper.getTextColor(this))
            toolbar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size)
            toolbar.setNavigationOnClickListener {
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
            }

            val header = if (navigationView.headerCount > 0) navigationView.getHeaderView(0) else navigationView.inflateHeaderView(R.layout.nav_header)
            header?.findViewById<View>(R.id.btnNavAddMember)?.setOnClickListener {
                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                val intent = android.content.Intent(this, MainActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                intent.putExtra("SHOW_DIALOG", R.id.action_add_person)
                startActivity(intent)
            }
            header?.findViewById<View>(R.id.btnNavAddGroup)?.setOnClickListener {
                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                val intent = android.content.Intent(this, MainActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                intent.putExtra("SHOW_DIALOG", R.id.action_add_group)
                startActivity(intent)
            }

            updateNavigationMenu(navigationView)

            navigationView.setNavigationItemSelectedListener { menuItem ->
                if (handleCategoryClick(menuItem.itemId, navigationView)) {
                    return@setNavigationItemSelectedListener true
                }

                when (menuItem.itemId) {
                    R.id.action_add_person, R.id.action_add_group -> {
                        val intent = android.content.Intent(this, MainActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        intent.putExtra("SHOW_DIALOG", menuItem.itemId)
                        startActivity(intent)
                    }
                    R.id.action_front_page -> if (this !is MainActivity) startActivity(android.content.Intent(this, MainActivity::class.java).apply { putExtra("ignore_redirect", true); flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP })
                    R.id.action_who_am_i -> if (this !is WhoAmIActivity) startActivity(android.content.Intent(this, WhoAmIActivity::class.java))
                    R.id.action_relations -> if (this !is RelationsActivity) startActivity(android.content.Intent(this, RelationsActivity::class.java))
                    R.id.action_mood_tracker -> if (this !is MoodActivity) startActivity(android.content.Intent(this, MoodActivity::class.java))
                    R.id.action_mood_stats -> if (this !is MoodStatsActivity) startActivity(android.content.Intent(this, MoodStatsActivity::class.java))
                    R.id.action_mood_insights -> if (this !is MemberMoodCorrelationActivity) startActivity(android.content.Intent(this, MemberMoodCorrelationActivity::class.java))
                    R.id.action_health_tracker -> if (this !is HealthActivity) startActivity(android.content.Intent(this, HealthActivity::class.java))
                    R.id.action_statistics -> if (this !is StatisticsActivity) startActivity(android.content.Intent(this, StatisticsActivity::class.java))
                    R.id.action_diary -> if (this !is DiaryActivity) startActivity(android.content.Intent(this, DiaryActivity::class.java))
                    R.id.action_sysmail -> {
                        if (this is DiaryActivity) {
                            findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayout)?.getTabAt(1)?.select()
                        } else {
                            val intent = android.content.Intent(this, DiaryActivity::class.java).apply {
                                putExtra("OPEN_SYSMAIL", true)
                                putExtra("SELECT_TAB", 1)
                            }
                            startActivity(intent)
                        }
                    }
                    R.id.action_sysmedia -> if (this !is SysmediaActivity) startActivity(android.content.Intent(this, SysmediaActivity::class.java))
                    R.id.action_todo -> if (this !is TodoActivity) startActivity(android.content.Intent(this, TodoActivity::class.java))
                    R.id.action_calendar -> if (this !is CalendarActivity) startActivity(android.content.Intent(this, CalendarActivity::class.java))
                    R.id.action_settings -> if (this !is SettingsActivity) startActivity(android.content.Intent(this, SettingsActivity::class.java))
                    R.id.action_subsystem -> if (this !is SubsystemActivity) startActivity(android.content.Intent(this, SubsystemActivity::class.java))
                }
                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                true
            }
        }
    }

    private class ButtonBoxSpan(
        private val bgColor: Int,
        private val textColor: Int,
        private val cornerRadiusDp: Float = 8f,
        private val paddingHorizontalDp: Float = 12f,
        private val paddingVerticalDp: Float = 8f
    ) : android.text.style.ReplacementSpan() {

        override fun getSize(
            paint: android.graphics.Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: android.graphics.Paint.FontMetricsInt?
        ): Int {
            val density = android.content.res.Resources.getSystem().displayMetrics.density
            val paddingV = paddingVerticalDp * density
            if (fm != null) {
                val metrics = paint.fontMetricsInt
                fm.top = metrics.top - paddingV.toInt()
                fm.ascent = metrics.ascent - paddingV.toInt()
                fm.descent = metrics.descent + paddingV.toInt()
                fm.bottom = metrics.bottom + paddingV.toInt()
            }
            val textWidth = paint.measureText(text, start, end)
            return (textWidth + paddingHorizontalDp * density * 2).toInt()
        }

        override fun draw(
            canvas: android.graphics.Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: android.graphics.Paint
        ) {
            val density = android.content.res.Resources.getSystem().displayMetrics.density
            val paddingH = paddingHorizontalDp * density
            val paddingV = paddingVerticalDp * density
            val cornerRadius = cornerRadiusDp * density

            val fontMetrics = paint.fontMetrics
            val textTop = y + fontMetrics.ascent
            val textBottom = y + fontMetrics.descent

            val rectLeft = x
            val rightMargin = if (x > 60f * density) x - 20f * density else x
            val rectRight = kotlin.math.max(x + paint.measureText(text, start, end) + paddingH * 2, canvas.width.toFloat() - rightMargin)
            val rectTop = textTop - paddingV
            val rectBottom = textBottom + paddingV

            val boxPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColor
                style = android.graphics.Paint.Style.FILL
            }
            val rect = android.graphics.RectF(rectLeft, rectTop, rectRight, rectBottom)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, boxPaint)

            val origColor = paint.color
            paint.color = textColor

            val subText = text.subSequence(start, end).toString()
            val lastSpaceIdx = subText.lastIndexOf("  ")
            if (lastSpaceIdx != -1) {
                val titlePart = subText.substring(0, lastSpaceIdx).trim()
                val arrowPart = subText.substring(lastSpaceIdx).trim()

                canvas.drawText(titlePart, rectLeft + paddingH, y.toFloat(), paint)

                val arrowWidth = paint.measureText(arrowPart)
                val arrowX = rectRight - paddingH - arrowWidth
                canvas.drawText(arrowPart, arrowX, y.toFloat(), paint)
            } else {
                canvas.drawText(text, start, end, rectLeft + paddingH, y.toFloat(), paint)
            }

            paint.color = origColor
        }
    }

    private fun formatCategoryTitle(titleResId: Int, expanded: Boolean, indent: String = ""): CharSequence {
        val arrow = if (expanded) "▼" else "▶"
        val fullText = "$indent${getString(titleResId)}  $arrow"
        val spannable = android.text.SpannableString(fullText)
        val btnColor = ColorHelper.getBtnColor(this)
        val btnTextColor = ColorHelper.getBtnTextColor(this)

        val span = ButtonBoxSpan(
            bgColor = btnColor,
            textColor = btnTextColor,
            cornerRadiusDp = 8f,
            paddingHorizontalDp = 12f,
            paddingVerticalDp = 8f
        )

        val start = indent.length
        val end = fullText.length
        spannable.setSpan(span, start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    companion object {
        private const val ID_CAT_PLURAL = 10001
        private const val ID_CAT_COMMUNICATION = 10002
        private const val ID_CAT_MOOD = 10003
        private const val ID_CAT_ADMIN = 10004
        private const val ID_CAT_STATS = 10005
    }

    private fun handleCategoryClick(itemId: Int, navigationView: com.google.android.material.navigation.NavigationView): Boolean {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val key = when (itemId) {
            ID_CAT_PLURAL -> "nav_cat_plural_expanded"
            ID_CAT_COMMUNICATION -> "nav_cat_comm_expanded"
            ID_CAT_MOOD -> "nav_cat_mood_expanded"
            ID_CAT_ADMIN -> "nav_cat_admin_expanded"
            ID_CAT_STATS -> "nav_cat_stats_expanded"
            else -> return false
        }
        val current = sp.getBoolean(key, true)
        sp.edit().putBoolean(key, !current).apply()
        
        navigationView.post {
            updateNavigationMenu(navigationView)
        }
        return true
    }

    protected fun updateNavigationMenu(navigationView: com.google.android.material.navigation.NavigationView) {
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)

        val pluralExpanded = sharedPref.getBoolean("nav_cat_plural_expanded", true)
        val commExpanded = sharedPref.getBoolean("nav_cat_comm_expanded", true)
        val moodExpanded = sharedPref.getBoolean("nav_cat_mood_expanded", true)
        val adminExpanded = sharedPref.getBoolean("nav_cat_admin_expanded", true)
        val statsExpanded = sharedPref.getBoolean("nav_cat_stats_expanded", true)

        val pluralMaster = sharedPref.getBoolean("module_fronting_enabled", true)
        val frontSub = sharedPref.getBoolean("sub_front_page", true) && pluralMaster
        val subsystemSub = sharedPref.getBoolean("sub_subsystems_enabled", true) && pluralMaster
        val whoAmISub = sharedPref.getBoolean("sub_who_am_i", true) && pluralMaster
        val relationsSub = sharedPref.getBoolean("sub_relations_enabled", true) && pluralMaster
        val sysmediaSub = sharedPref.getBoolean("module_sysmedia_enabled", true) && pluralMaster

        val moodMaster = sharedPref.getBoolean("module_mood_enabled", true)
        val moodLogSub = sharedPref.getBoolean("sub_mood_log_enabled", true) && moodMaster
        val moodStatsSub = sharedPref.getBoolean("sub_mood_stats_enabled", true) && moodMaster
        val moodInsightsSub = sharedPref.getBoolean("sub_mood_insights", true) && moodMaster

        val notesEnabled = sharedPref.getBoolean("module_notes_enabled", true)
        val todoEnabled = sharedPref.getBoolean("module_todo_enabled", true)
        val calendarEnabled = sharedPref.getBoolean("module_calendar_enabled", true)

        val statsSub = sharedPref.getBoolean("sub_statistics", true) && pluralMaster

        val header = if (navigationView.headerCount > 0) navigationView.getHeaderView(0) else navigationView.inflateHeaderView(R.layout.nav_header)
        header?.findViewById<View>(R.id.btnNavAddMember)?.visibility = if (frontSub) View.VISIBLE else View.GONE
        header?.findViewById<View>(R.id.btnNavAddGroup)?.visibility = if (frontSub) View.VISIBLE else View.GONE

        val menu = navigationView.menu
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            menu.setGroupDividerEnabled(true)
        }
        menu.clear()

        val hasPluralChildren = frontSub || subsystemSub || whoAmISub || relationsSub || sysmediaSub
        if (hasPluralChildren) {
            menu.add(1, ID_CAT_PLURAL, Menu.NONE, formatCategoryTitle(R.string.nav_cat_plural, pluralExpanded))
            if (pluralExpanded) {
                if (frontSub) menu.add(1, R.id.action_front_page, Menu.NONE, "    ${getString(R.string.front_page)}")
                if (subsystemSub) menu.add(1, R.id.action_subsystem, Menu.NONE, "    ${getString(R.string.subsystem_page)}")
                if (whoAmISub) menu.add(1, R.id.action_who_am_i, Menu.NONE, "    ${getString(R.string.who_am_i)}")
                if (relationsSub) menu.add(1, R.id.action_relations, Menu.NONE, "    ${getString(R.string.module_relations)}")

                if (sysmediaSub) {
                    menu.add(1, ID_CAT_COMMUNICATION, Menu.NONE, formatCategoryTitle(R.string.nav_cat_communication, commExpanded, "    "))
                    if (commExpanded) {
                        menu.add(1, R.id.action_sysmedia, Menu.NONE, "        ${getString(R.string.sysmedia)}")
                        menu.add(1, R.id.action_sysmail, Menu.NONE, "        ${getString(R.string.tab_messages)}")
                    }
                }
            }
        }

        if (moodLogSub) {
            menu.add(2, ID_CAT_MOOD, Menu.NONE, formatCategoryTitle(R.string.nav_cat_mood, moodExpanded))
            if (moodExpanded) {
                menu.add(2, R.id.action_health_tracker, Menu.NONE, "    ${getString(R.string.health_title)}")
                menu.add(2, R.id.action_mood_tracker, Menu.NONE, "    ${getString(R.string.mood_tracker)}")
            }
        }

        val hasAdminChildren = notesEnabled || todoEnabled || calendarEnabled
        if (hasAdminChildren) {
            menu.add(3, ID_CAT_ADMIN, Menu.NONE, formatCategoryTitle(R.string.nav_cat_administration, adminExpanded))
            if (adminExpanded) {
                if (notesEnabled) menu.add(3, R.id.action_diary, Menu.NONE, "    ${getString(R.string.diary)}")
                if (todoEnabled) menu.add(3, R.id.action_todo, Menu.NONE, "    ${getString(R.string.todo)}")
                if (calendarEnabled) menu.add(3, R.id.action_calendar, Menu.NONE, "    ${getString(R.string.calendar)}")
            }
        }

        val hasStatsChildren = statsSub || moodStatsSub || moodInsightsSub
        if (hasStatsChildren) {
            menu.add(4, ID_CAT_STATS, Menu.NONE, formatCategoryTitle(R.string.nav_cat_statistics, statsExpanded))
            if (statsExpanded) {
                if (statsSub) menu.add(4, R.id.action_statistics, Menu.NONE, "    ${getString(R.string.statistics)}")
                if (moodStatsSub) menu.add(4, R.id.action_mood_stats, Menu.NONE, "    ${getString(R.string.mood_stats)}")
                if (moodInsightsSub) menu.add(4, R.id.action_mood_insights, Menu.NONE, "    ${getString(R.string.mood_insights)}")
            }
        }

        menu.add(5, R.id.action_settings, Menu.NONE, getString(R.string.settings))
    }

    override fun setContentView(@LayoutRes layoutResID: Int) {
        super.setContentView(layoutResID)
        SilentUi.disableSoundEffects(window?.decorView)
    }
    override fun setContentView(view: View?) {
        super.setContentView(view)
        SilentUi.disableSoundEffects(window?.decorView)
    }
    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        SilentUi.disableSoundEffects(window?.decorView)
    }
    protected fun showUnsavedChangesDialog(onConfirm: () -> Unit) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.unsaved_changes_title))
            .setMessage(getString(R.string.unsaved_changes_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ -> onConfirm() }
            .setNegativeButton(getString(R.string.no), null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }
    protected fun navigateToStartPage() {
        val settingsPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val startPage = (settingsPref.getString("start_page", "members") ?: "members").lowercase()
        val targetClass = when (startPage) {
            "mood" -> if (settingsPref.getBoolean("module_mood_enabled", true) && settingsPref.getBoolean("sub_mood_log_enabled", true)) MoodActivity::class.java else MainActivity::class.java
            "mood_insights" -> if (settingsPref.getBoolean("module_mood_enabled", true) && settingsPref.getBoolean("sub_mood_insights", true)) MemberMoodCorrelationActivity::class.java else MainActivity::class.java
            "diary" -> if (settingsPref.getBoolean("module_notes_enabled", true)) DiaryActivity::class.java else MainActivity::class.java
            "calendar" -> if (settingsPref.getBoolean("module_calendar_enabled", true)) CalendarActivity::class.java else MainActivity::class.java
            "sysmedia" -> if (settingsPref.getBoolean("module_fronting_enabled", true) && settingsPref.getBoolean("module_sysmedia_enabled", true)) SysmediaActivity::class.java else MainActivity::class.java
            "todo" -> if (settingsPref.getBoolean("module_todo_enabled", true)) TodoActivity::class.java else MainActivity::class.java
            "stats" -> if (settingsPref.getBoolean("module_fronting_enabled", true) && settingsPref.getBoolean("sub_front_page", true)) StatisticsActivity::class.java else MainActivity::class.java
            "relations" -> if (settingsPref.getBoolean("module_fronting_enabled", true) && settingsPref.getBoolean("sub_relations_enabled", true)) RelationsActivity::class.java else MainActivity::class.java
            "subsystem" -> if (settingsPref.getBoolean("module_fronting_enabled", true) && settingsPref.getBoolean("sub_subsystems_enabled", true)) com.interli.plural.features.subsystem.SubsystemActivity::class.java else MainActivity::class.java
            else -> MainActivity::class.java
        }
        if (this::class.java == targetClass) finish() else {
            startActivity(android.content.Intent(this, targetClass).apply { flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP })
            finish()
        }
    }
    protected fun showFrontMessageNotification(person: Person) {
        val title = getString(R.string.notification_front_message_title, person.name)
        val message = person.frontMessage ?: return
        val intent = android.content.Intent(this, MainActivity::class.java).apply { flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = android.app.PendingIntent.getActivity(this, person.id.hashCode(), intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, "FRONT_CHANNEL_V2").setSmallIcon(R.drawable.ic_stat_name).setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message)).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).setContentIntent(pendingIntent)
        try { NotificationManagerCompat.from(this).notify(person.id.hashCode() + 100, builder.build()) } catch (_: SecurityException) { }
    }
}
