package com.interli.plural

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Menu
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        applyFixedDisplayScale(newBase)
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    private fun applyFixedDisplayScale(context: android.content.Context) {
        val res = context.resources
        val config = res.configuration
        val dm = res.displayMetrics

        val targetWidthDp = 446f

        val targetDensity = dm.widthPixels / targetWidthDp
        val targetScaledDensity = targetDensity * (config.fontScale)
        val targetDensityDpi = (160 * targetDensity).toInt()

        dm.density = targetDensity
        dm.scaledDensity = targetScaledDensity
        dm.densityDpi = targetDensityDpi

        config.densityDpi = targetDensityDpi
        res.updateConfiguration(config, dm)
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

            // 1. Zorg dat de header er is (moet VOORDAT updateMenuVisibility wordt aangeroepen)
            val header = if (navigationView.headerCount > 0) navigationView.getHeaderView(0) else navigationView.inflateHeaderView(R.layout.nav_header)

            // 2. Stel klik listeners in
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

            // 3. Update visibility en styling
            updateMenuVisibility(navigationView)
            ColorHelper.styleNavigationView(navigationView)

            val menu = navigationView.menu

            val idToGroup = mapOf(
                R.id.action_add_person to 0,
                R.id.action_add_group to 0,
                R.id.action_front_page to 1,
                R.id.action_statistics to 1,
                R.id.action_who_am_i to 1,
                R.id.action_diary to 2,
                R.id.action_todo to 2,
                R.id.action_calendar to 2,
                R.id.action_mood_tracker to 3,
                R.id.action_mood_stats to 3,
                R.id.action_sysmedia to 4,
                R.id.action_settings to 99
            )

            val items = mutableListOf<android.view.MenuItem>()
            for (i in 0 until menu.size()) {
                items.add(menu.getItem(i))
            }

            val itemData = items.map { it.itemId to (it.title to it.icon) }.toMap()
            val itemVisibility = items.map { it.itemId to it.isVisible }.toMap()

            menu.clear()

            idToGroup.forEach { (id, groupId) ->
                val data = itemData[id]
                if (data != null) {
                    menu.add(groupId, id, Menu.NONE, data.first).apply {
                        setIcon(data.second)
                        setVisible(itemVisibility[id] ?: true)
                    }
                }
            }

            navigationView.setNavigationItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_add_person, R.id.action_add_group -> {
                        val intent = android.content.Intent(this, MainActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        intent.putExtra("SHOW_DIALOG", menuItem.itemId)
                        startActivity(intent)
                    }
                    R.id.action_front_page -> {
                        if (this !is MainActivity) {
                            val intent = android.content.Intent(this, MainActivity::class.java)
                            intent.putExtra("ignore_redirect", true)
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                        }
                    }
                    R.id.action_who_am_i -> {
                        if (this !is WhoAmIActivity) {
                            val intent = android.content.Intent(this, WhoAmIActivity::class.java)
                            startActivity(intent)
                        }
                    }
                    R.id.action_mood_tracker -> {
                        if (this !is MoodActivity) {
                            val intent = android.content.Intent(this, MoodActivity::class.java)
                            startActivity(intent)
                        }
                    }
                    R.id.action_mood_stats -> {
                        if (this !is MoodStatsActivity) {
                            val intent = android.content.Intent(this, MoodStatsActivity::class.java)
                            startActivity(intent)
                        }
                    }
                    R.id.action_statistics -> {
                        if (this !is StatisticsActivity) {
                            val intent = android.content.Intent(this, StatisticsActivity::class.java)
                            startActivity(intent)
                        }
                    }
                    R.id.action_diary -> {
                        if (this !is DiaryActivity) {
                            val intent = android.content.Intent(this, DiaryActivity::class.java)
                            startActivity(intent)
                        }
                    }
                    R.id.action_sysmedia -> {
                        if (this !is SysmediaActivity) {
                            val intent = android.content.Intent(this, SysmediaActivity::class.java)
                            startActivity(intent)
                        }
                    }
                    R.id.action_todo -> {
                        if (this !is TodoActivity) {
                            val intent = android.content.Intent(this, TodoActivity::class.java)
                            startActivity(intent)
                        }
                    }
                    R.id.action_calendar -> {
                        if (this !is CalendarActivity) {
                            val intent = android.content.Intent(this, CalendarActivity::class.java)
                            startActivity(intent)
                        }
                    }
                    R.id.action_settings -> {
                        if (this !is SettingsActivity) {
                            val intent = android.content.Intent(this, SettingsActivity::class.java)
                            startActivity(intent)
                        }
                    }
                }
                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                true
            }
        }
    }

    private fun updateMenuVisibility(navigationView: com.google.android.material.navigation.NavigationView) {
        val sharedPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val menu = navigationView.menu

        val pluralMaster = sharedPref.getBoolean("module_fronting_enabled", true)
        val moodMaster = sharedPref.getBoolean("module_mood_enabled", true)
        val notesEnabled = sharedPref.getBoolean("module_notes_enabled", true)
        val todoEnabled = sharedPref.getBoolean("module_todo_enabled", true)
        val calendarEnabled = sharedPref.getBoolean("module_calendar_enabled", true)

        val frontSub = sharedPref.getBoolean("sub_front_page", true) && pluralMaster
        val statsSub = sharedPref.getBoolean("sub_statistics", true) && pluralMaster
        val whoAmISub = sharedPref.getBoolean("sub_who_am_i", true) && pluralMaster

        val moodLogSub = sharedPref.getBoolean("sub_mood_log_enabled", true) && moodMaster
        val moodStatsSub = sharedPref.getBoolean("sub_mood_stats_enabled", true) && moodMaster

        val sysmediaSub = sharedPref.getBoolean("module_sysmedia_enabled", true) && pluralMaster

        menu.findItem(R.id.action_add_person)?.isVisible = frontSub
        menu.findItem(R.id.action_add_group)?.isVisible = frontSub
        menu.findItem(R.id.action_front_page)?.isVisible = frontSub
        menu.findItem(R.id.action_statistics)?.isVisible = statsSub
        menu.findItem(R.id.action_who_am_i)?.isVisible = whoAmISub

        menu.findItem(R.id.action_mood_tracker)?.isVisible = moodLogSub
        menu.findItem(R.id.action_mood_stats)?.isVisible = moodStatsSub

        menu.findItem(R.id.action_diary)?.isVisible = notesEnabled
        menu.findItem(R.id.action_todo)?.isVisible = todoEnabled
        menu.findItem(R.id.action_calendar)?.isVisible = calendarEnabled

        menu.findItem(R.id.action_sysmedia)?.isVisible = sysmediaSub

        val header = navigationView.getHeaderView(0)
        header?.findViewById<View>(R.id.btnNavAddMember)?.visibility = if (frontSub) View.VISIBLE else View.GONE
        header?.findViewById<View>(R.id.btnNavAddGroup)?.visibility = if (frontSub) View.VISIBLE else View.GONE

        menu.findItem(R.id.action_settings)?.isVisible = true
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
        val rawStartPage = settingsPref.getString("start_page", "members") ?: "members"
        val startPage = when(rawStartPage.uppercase()) {
            "FRONT" -> "members"
            "STATS" -> "stats"
            "DIARY" -> "diary"
            "TODO" -> "todo"
            "MOOD" -> "mood"
            else -> rawStartPage.lowercase()
        }

        val targetClass = when (startPage) {
            "mood" -> {
                val master = settingsPref.getBoolean("module_mood_enabled", true)
                val sub = settingsPref.getBoolean("sub_mood_log_enabled", true)
                if (master && sub) MoodActivity::class.java else MainActivity::class.java
            }
            "diary" -> if (settingsPref.getBoolean("module_notes_enabled", true)) DiaryActivity::class.java else MainActivity::class.java
            "calendar" -> if (settingsPref.getBoolean("module_calendar_enabled", true)) CalendarActivity::class.java else MainActivity::class.java
            "sysmedia" -> {
                val master = settingsPref.getBoolean("module_fronting_enabled", true)
                val sub = settingsPref.getBoolean("module_sysmedia_enabled", true)
                if (master && sub) SysmediaActivity::class.java else MainActivity::class.java
            }
            "todo" -> if (settingsPref.getBoolean("module_todo_enabled", true)) TodoActivity::class.java else MainActivity::class.java
            "stats" -> {
                val master = settingsPref.getBoolean("module_fronting_enabled", true)
                val sub = settingsPref.getBoolean("sub_front_page", true)
                if (master && sub) StatisticsActivity::class.java else MainActivity::class.java
            }
            else -> MainActivity::class.java
        }

        if (this::class.java == targetClass) {
            finish()
        } else {
            val intent = android.content.Intent(this, targetClass)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    protected fun showFrontMessageNotification(person: Person) {
        val title = getString(R.string.notification_front_message_title, person.name)
        val message = person.frontMessage ?: return

        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, person.id.hashCode(), intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "FRONT_CHANNEL_V2")
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(this).notify(person.id.hashCode() + 100, builder.build())
        } catch (_: SecurityException) { }
    }
}
