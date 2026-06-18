package com.interli.plural

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MoodStatsActivity : BaseActivity() {

    private val gson = Gson()

    private var allEntriesForCalendar: List<MoodActivity.MoodEntry> = emptyList()
    private var filteredEntries: List<MoodActivity.MoodEntry> = emptyList()
    private var selectedCalendarActivity: String? = null
    private var selectedCalendarMemberId: String? = null
    
    private var currentPeriodStart: Long = 0
    private var currentPeriodEnd: Long = Long.MAX_VALUE
    private var customStartDate: Calendar? = null
    private var customEndDate: Calendar? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_stats)

        ColorHelper.applySettings(this)

        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val frontEnabled = sp.getBoolean("module_fronting_enabled", true) && sp.getBoolean("sub_fronting_enabled", true)
        if (!frontEnabled) {
            findViewById<View>(R.id.cardMemberMoodAverages)?.visibility = View.GONE
            findViewById<View>(R.id.cardRecentFrontingActivity)?.visibility = View.GONE
            findViewById<View>(R.id.btnCalendarSelectMember)?.visibility = View.GONE
        }

        findViewById<View>(R.id.btnViewMonthStats).setOnClickListener {
            val intent = android.content.Intent(this, MoodMonthActivity::class.java)
            startActivity(intent)
        }
        findViewById<View>(R.id.btnViewTimeline).setOnClickListener {
            val intent = android.content.Intent(this, MoodTimelineActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnSelectMoodDate).setOnClickListener {
            val cal = Calendar.getInstance()
            val dialog = android.app.DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                val chart = findViewById<MoodChartView>(R.id.moodHistoryChart)
                chart.setTargetDate(cal.timeInMillis)
                findViewById<Button>(R.id.btnSelectMoodDate).text = SimpleDateFormat("dd/MM", Locale.getDefault()).format(cal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dialog.show()
            ColorHelper.styleAlertDialog(dialog, this)
        }

        findViewById<Button>(R.id.btnFilterActivities).setOnClickListener {
            showActivityFilterDialog()
        }

        findViewById<Button>(R.id.btnViewAllActivities).setOnClickListener {
            showAllActivitiesDialog()
        }

        findViewById<Button>(R.id.btnCalendarSelectMember).setOnClickListener {
            showCalendarMemberSelectionDialog()
        }
        findViewById<Button>(R.id.btnCalendarSelectMember).setOnLongClickListener {
            selectedCalendarMemberId = null
            updateCalendar()
            true
        }

        findViewById<Button>(R.id.btnCalendarSelectActivity).setOnClickListener {
            showCalendarActivitySelectionDialog()
        }
        findViewById<Button>(R.id.btnCalendarSelectActivity).setOnLongClickListener {
            selectedCalendarActivity = null
            updateCalendar()
            true
        }

        findViewById<MoodCalendarView>(R.id.moodCalendarView).onDayClicked = { dayEntries ->
            showDayEntriesDialog(dayEntries)
        }

        setupPeriodSpinner()
        setupDatePickers()
        setupNavigationDrawer()
        loadAndRender()
    }

    override fun onResume() {
        super.onResume()
        loadAndRender()
    }

    private fun setupPeriodSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerMoodPeriod)
        val periods = arrayOf(
            getString(R.string.period_all_time),
            getString(R.string.period_last_30_days),
            getString(R.string.period_current_month),
            getString(R.string.period_last_month),
            getString(R.string.period_last_7_days),
            getString(R.string.period_current_week),
            getString(R.string.period_custom)
        )

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, periods) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as TextView).setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity))
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as TextView).setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity))
                v.setBackgroundColor(ColorHelper.getBgColor(this@MoodStatsActivity))
                return v
            }
        }
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateFilteredData(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDatePickers() {
        val btnStart = findViewById<Button>(R.id.btnMoodStartDate)
        val btnEnd = findViewById<Button>(R.id.btnMoodEndDate)

        btnStart.setOnClickListener {
            val cal = customStartDate ?: Calendar.getInstance()
            val dialog = android.app.DatePickerDialog(this, { _, y, m, d ->
                val newCal = Calendar.getInstance()
                newCal.set(y, m, d, 0, 0, 0); newCal.set(Calendar.MILLISECOND, 0)
                customStartDate = newCal
                btnStart.text = dateFormat.format(newCal.time)
                updateFilteredData(6)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dialog.show()
            ColorHelper.styleAlertDialog(dialog, this)
        }

        btnEnd.setOnClickListener {
            val cal = customEndDate ?: Calendar.getInstance()
            val dialog = android.app.DatePickerDialog(this, { _, y, m, d ->
                val newCal = Calendar.getInstance()
                newCal.set(y, m, d, 23, 59, 59); newCal.set(Calendar.MILLISECOND, 999)
                customEndDate = newCal
                btnEnd.text = dateFormat.format(newCal.time)
                updateFilteredData(6)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dialog.show()
            ColorHelper.styleAlertDialog(dialog, this)
        }
    }

    private fun updateFilteredData(position: Int) {
        val layoutCustom = findViewById<View>(R.id.layoutMoodCustomRange)
        layoutCustom.visibility = if (position == 6) View.VISIBLE else View.GONE
        
        currentPeriodEnd = Long.MAX_VALUE
        val now = Calendar.getInstance()
        
        when (position) {
            0 -> currentPeriodStart = 0
            1 -> { val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -30); currentPeriodStart = cal.timeInMillis }
            2 -> { 
                val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                currentPeriodStart = cal.timeInMillis 
            }
            3 -> { 
                val calStart = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                currentPeriodStart = calStart.timeInMillis
                val calEnd = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MILLISECOND, -1) }
                currentPeriodEnd = calEnd.timeInMillis
            }
            4 -> { val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -7); currentPeriodStart = cal.timeInMillis }
            5 -> { 
                val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                currentPeriodStart = cal.timeInMillis 
            }
            6 -> { 
                currentPeriodStart = customStartDate?.timeInMillis ?: 0L
                currentPeriodEnd = customEndDate?.timeInMillis ?: Long.MAX_VALUE 
            }
        }

        loadAndRender()
    }

    private fun loadAndRender() {
        Thread {
            val rawEntries = loadEntries()
            
            val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
            val peopleJson = sharedPref.getString("people_list", "[]") ?: "[]"
            val allPeople: List<Person> = gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type)
            val excludedIds = allPeople.filter { it.excludeFromStats || it.isArchived }.map { it.id }.toSet()
            
            val settingsPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
            val excludedActivities = settingsPref.getStringSet("excluded_activities", emptySet()) ?: emptySet()

            val entries = rawEntries.filter { entry ->
                (entry.memberIds.isEmpty() || entry.memberIds.any { !excludedIds.contains(it) }) &&
                entry.timestamp in currentPeriodStart..currentPeriodEnd
            }
            
            allEntriesForCalendar = rawEntries.filter { entry ->
                (entry.memberIds.isEmpty() || entry.memberIds.any { !excludedIds.contains(it) })
            }
            
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val recentEntries = entries.filter { it.timestamp >= thirtyDaysAgo }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                renderStats(entries, recentEntries, excludedActivities)
                updateCalendar()
            }
        }.start()
    }

    private fun renderStats(allEntries: List<MoodActivity.MoodEntry>, recentEntries: List<MoodActivity.MoodEntry>, excludedActivities: Set<String>) {
        if (allEntries.isEmpty()) return

        findViewById<MoodChartView>(R.id.moodHistoryChart).setData(allEntries, MoodChartView.Mode.TODAY_VIEW)
        findViewById<MoodChartView>(R.id.moodAverageChart).setData(allEntries, MoodChartView.Mode.SEVEN_DAY_AVERAGE)

        renderMoodCounts(allEntries)
        renderMemberMoodAverages(allEntries)
        renderActivityInfluence(allEntries, excludedActivities)
        renderRecentFrontingActivity(allEntries)
    }

    private fun renderRecentFrontingActivity(recentEntries: List<MoodActivity.MoodEntry>) {
        val container = findViewById<LinearLayout>(R.id.containerRecentFrontingActivity) ?: return
        container.removeAllViews()

        if (recentEntries.isEmpty()) return

        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val excludedActivities = sp.getStringSet("excluded_activities", emptySet()) ?: emptySet()
        val pinnedActivities = sp.getStringSet("pinned_activities", emptySet()) ?: emptySet()

        val allActivityEntries = recentEntries.flatMap { it.activities }
        val totalActivityCount = allActivityEntries.size.toFloat()
        
        val activityCounts = allActivityEntries
            .filter { !excludedActivities.contains(it) }
            .groupingBy { it }.eachCount()

        val activitiesToShow = if (pinnedActivities.isNotEmpty()) {
            pinnedActivities.filter { activityCounts.containsKey(it) }.sorted()
        } else {
            val mostPopularActivity = activityCounts.maxByOrNull { it.value }?.key
            if (mostPopularActivity != null) listOf(mostPopularActivity) else emptyList()
        }

        val people = loadPeopleList()
        val textColor = ColorHelper.getTextColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val btnTextColor = ColorHelper.getBtnTextColor(this)

        val header = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, 8.dpToPx())
        }

        val titleTv = TextView(this).apply {
            text = getString(R.string.stats_top_member_activities)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            lp.addRule(RelativeLayout.ALIGN_PARENT_START)
            lp.addRule(RelativeLayout.CENTER_VERTICAL)
            layoutParams = lp
        }
        header.addView(titleTv)

        val btnPin = com.google.android.material.button.MaterialButton(this).apply {
            text = "Pin / Filter"
            textSize = 12f
            setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
            setBackgroundColor(btnColor)
            setTextColor(btnTextColor)
            rippleColor = android.content.res.ColorStateList.valueOf(btnTextColor and 0x33FFFFFF)
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, (36 * resources.displayMetrics.density).toInt())
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            lp.addRule(RelativeLayout.CENTER_VERTICAL)
            layoutParams = lp
            setOnClickListener { showPinnedActivitiesDialog() }
        }
        header.addView(btnPin)
        container.addView(header)

        if (activitiesToShow.isEmpty() && pinnedActivities.isNotEmpty()) {
            container.addView(TextView(this).apply {
                text = "No data for pinned activities"
                setTextColor(textColor)
                alpha = 0.5f
                setPadding(0, 0, 0, 8.dpToPx())
            })
        }

        activitiesToShow.forEach { activity ->
            val count = activityCounts[activity] ?: 0
            val percentage = if (totalActivityCount > 0) (count / totalActivityCount * 100).toInt() else 0
            
            val activityRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
            }
            activityRow.addView(TextView(this).apply {
                text = activity
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            activityRow.addView(TextView(this).apply {
                text = "$percentage%"
                textSize = 14f
                setTextColor(textColor)
                alpha = 0.7f
            })
            container.addView(activityRow)

            val activityEntries = recentEntries.filter { it.activities.contains(activity) }
            val memberIdsInActivity = activityEntries.flatMap { it.memberIds }
                .filter { mId -> people.any { it.id == mId && !it.isArchived && !it.isSysmediaOnly } }
            val memberCounts = memberIdsInActivity.groupingBy { it }.eachCount()
            val totalInActivity = memberIdsInActivity.size.toFloat()

            if (memberCounts.isEmpty()) {
                container.addView(TextView(this).apply {
                    text = getString(R.string.no_activities_found)
                    setTextColor(textColor)
                    alpha = 0.5f
                    setPadding(16.dpToPx(), 0, 0, 8.dpToPx())
                })
            } else {
                memberCounts.forEach { (memberId, mCount) ->
                    val person = people.find { it.id == memberId }
                    if (person == null || person.isArchived) return@forEach

                    val name = person.name
                    val mPercentage = if (totalInActivity > 0) (mCount / totalInActivity * 100).toInt() else 0
                    
                    val memberRow = LinearLayout(this@MoodStatsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(16.dpToPx(), 2.dpToPx(), 0, 2.dpToPx())
                    }
                    memberRow.addView(TextView(this@MoodStatsActivity).apply {
                        text = "• $name"
                        setTextColor(textColor)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    memberRow.addView(TextView(this@MoodStatsActivity).apply {
                        text = "$mPercentage%"
                        setTextColor(textColor)
                        alpha = 0.7f
                    })
                    container.addView(memberRow)
                }
            }
        }

        val btnExtensive = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.btn_extensive_stats)
            setBackgroundColor(btnColor)
            setTextColor(btnTextColor)
            rippleColor = android.content.res.ColorStateList.valueOf(btnTextColor and 0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16.dpToPx()
            }
            setOnClickListener {
                val intent = android.content.Intent(this@MoodStatsActivity, ExtensiveMoodStatsActivity::class.java)
                startActivity(intent)
            }
        }
        container.addView(btnExtensive)
    }

    private fun showPinnedActivitiesDialog() {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val pinned = sp.getStringSet("pinned_activities", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val allActivities = allEntriesForCalendar.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (allActivities.isEmpty()) return

        val options = arrayOf(getString(R.string.pin_activities), getString(R.string.filter_activities_label))
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.manage_activities))
            .setItems(options) { _, which ->
                if (which == 0) showMultiSelectPinDialog(allActivities, pinned, sp)
                else showActivityFilterDialog()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        ColorHelper.styleSupportAlertDialog(dialog, this)
        dialog.show()
    }

    private fun showMultiSelectPinDialog(allActivities: List<String>, pinned: MutableSet<String>, sp: android.content.SharedPreferences) {
        val checkedItems = BooleanArray(allActivities.size) { i -> pinned.contains(allActivities[i]) }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_activities_to_pin))
            .setMultiChoiceItems(allActivities.toTypedArray(), checkedItems) { _, which, isChecked ->
                val activity = allActivities[which]
                if (isChecked) pinned.add(activity) else pinned.remove(activity)
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                sp.edit().putStringSet("pinned_activities", pinned).apply()
                loadAndRender()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        dialog.show()
    }

    private fun renderMoodCounts(entries: List<MoodActivity.MoodEntry>) {
        val container = findViewById<LinearLayout>(R.id.containerMoodCounts)
        container.removeAllViews()

        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
        val moodRotations = listOf(180f, 135f, 90f, 45f, 0f)
        val counts = entries.groupingBy { it.moodLabel }.eachCount()
        
        val consolidatedCounts = mutableMapOf<String, Int>()
        counts.forEach { (label, count) ->
            val key = if (moodKeys.contains(label)) label else {
                moodKeys.find { key ->
                    val resId = resources.getIdentifier(key, "string", packageName)
                    resId != 0 && getString(resId) == label
                } ?: label
            }
            consolidatedCounts[key] = (consolidatedCounts[key] ?: 0) + count
        }

        val sorted = moodKeys.reversed().mapIndexed { i, key -> 
            val moodIndex = moodKeys.indexOf(key)
            Triple(key, consolidatedCounts[key] ?: 0, moodRotations[moodIndex])
        }
        val total = entries.size.toFloat()
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val defaultColors = listOf("#fffa94", "#54bd44", "#8844bd", "#4446bd", "#3a3a47")

        sorted.forEach { (key, count, rotation) ->
            if (count == 0) return@forEach

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val color = ColorHelper.getMoodColor(this, key)
            val moodIndex = moodKeys.indexOf(key)

            val thumbFrame = FrameLayout(this).apply {
                setTag(R.id.color_tag, "skip")
                layoutParams = LinearLayout.LayoutParams(36.dpToPx(), 36.dpToPx())
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                }
            }
            thumbFrame.addView(TextView(this).apply {
                text = "👍"
                textSize = 18f
                this.rotation = rotation
                gravity = android.view.Gravity.CENTER
            })
            row.addView(thumbFrame)

            val infoContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 12.dpToPx()
                }
            }

            val percentage = (count / total * 100).toInt()
            val labelTv = TextView(this).apply {
                text = getString(R.string.stats_score_count, (moodIndex + 1).toString(), count, percentage)
                textSize = 14f
                setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity))
            }
            infoContainer.addView(labelTv)

            val bar = View(this).apply {
                val weight = count / total
                layoutParams = LinearLayout.LayoutParams(0, 6.dpToPx(), weight).apply {
                    topMargin = 4.dpToPx()
                }
                setBackgroundColor(ColorHelper.getMoodColor(this@MoodStatsActivity, key))
            }
            
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(bar)
                addView(View(this@MoodStatsActivity).apply { 
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f - (count / total))
                })
            }
            infoContainer.addView(barContainer)
            row.addView(infoContainer)

            container.addView(row)
        }
    }

    private fun renderMemberMoodAverages(entries: List<MoodActivity.MoodEntry>) {
        val container = findViewById<LinearLayout>(R.id.containerMemberMoodAverages)
        container.removeAllViews()

        val people = loadPeopleList()
        val memberMap = people.associateBy { it.id }
        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
        
        val memberEntries = mutableMapOf<String, MutableList<MoodActivity.MoodEntry>>()
        for (entry in entries) {
            for (memberId in entry.memberIds) {
                val person = memberMap[memberId]
                if (person != null && !person.isArchived && !person.isSysmediaOnly) {
                    memberEntries.getOrPut(memberId) { mutableListOf() }.add(entry)
                }
            }
        }

        if (memberEntries.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = getString(R.string.stats_no_data_per_member)
                setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity))
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            }
            container.addView(emptyTv)
            return
        }

        val memberAverages = memberEntries.map { (memberId, memberMoodEntries) ->
            val totalScore = memberMoodEntries.sumOf { entry ->
                val label = entry.moodLabel
                val score = moodKeys.indexOf(label) + 1
                if (score > 0) score else {
                    val index = moodKeys.indexOfFirst { key ->
                        val resId = resources.getIdentifier(key, "string", packageName)
                        resId != 0 && getString(resId) == label
                    }
                    if (index != -1) index + 1 else 3
                }
            }
            val average = totalScore.toDouble() / memberMoodEntries.size
            val name = memberMap[memberId]?.name ?: getString(R.string.deleted_member)
            Triple(name, average, memberMoodEntries.size)
        }.sortedByDescending { it.second }

        memberAverages.forEach { (name, average, count) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val nameTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = name
                setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity))
                textSize = 15f
            }

            val averageTv = TextView(this).apply {
                text = String.format("%.2f", average)
                setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity))
                setTypeface(null, android.graphics.Typeface.BOLD)
                textSize = 16f
            }
            
            val countTv = TextView(this).apply {
                text = " ($count)"
                setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity))
                textSize = 12f
                alpha = 0.7f
                setPadding(4.dpToPx(), 0, 0, 0)
            }

            row.addView(nameTv)
            row.addView(averageTv)
            row.addView(countTv)
            container.addView(row)
            
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.LTGRAY)
                alpha = 0.3f
            }
            container.addView(divider)
        }
    }

    private fun updateCalendar() {
        val filtered = allEntriesForCalendar.filter { entry ->
            val matchesActivity = selectedCalendarActivity == null || entry.activities.contains(selectedCalendarActivity)
            val matchesMember = selectedCalendarMemberId == null || entry.memberIds.contains(selectedCalendarMemberId)
            matchesActivity && matchesMember
        }
        findViewById<MoodCalendarView>(R.id.moodCalendarView).setData(filtered)
        
        findViewById<Button>(R.id.btnCalendarSelectActivity).text = selectedCalendarActivity ?: getString(R.string.all_activities)
        
        val memberName = if (selectedCalendarMemberId == null) {
            getString(R.string.all_members)
        } else {
            val people = loadPeopleList()
            people.find { it.id == selectedCalendarMemberId }?.name ?: getString(R.string.deleted_member)
        }
        findViewById<Button>(R.id.btnCalendarSelectMember).text = memberName
    }

    private fun loadPeopleList(): List<Person> {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val peopleJson = sharedPref.getString("people_list", "[]") ?: "[]"
        return gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type)
    }

    private fun loadGroupsList(): List<Group> {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
        return try {
            gson.fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun showCalendarActivitySelectionDialog() {
        val activities = allEntriesForCalendar.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).toMutableList()
        activities.add(0, getString(R.string.all_activities))
        
        DialogHelper.showSearchableListDialog(
            this,
            getString(R.string.select_activity),
            activities
        ) { selected ->
            selectedCalendarActivity = if (selected == getString(R.string.all_activities)) null else selected
            updateCalendar()
        }
    }

    private fun showCalendarMemberSelectionDialog() {
        val people = loadPeopleList()
        val groups = loadGroupsList()
        
        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.select_member),
            people,
            groups,
            if (selectedCalendarMemberId != null) listOf(selectedCalendarMemberId!!) else emptyList(),
            isMultiSelect = false
        ) { newList ->
            selectedCalendarMemberId = if (newList.isEmpty()) null else newList[0]
            updateCalendar()
        }
    }

    private fun showDayEntriesDialog(entries: List<MoodActivity.MoodEntry>) {
        val people = loadPeopleList()
        val message = StringBuilder()

        entries.sortedBy { it.timestamp }.forEachIndexed { index, entry ->
            if (entries.size > 1) message.append(getString(R.string.stats_measurement_n, index + 1)).append("\n")
            
            message.append(getString(R.string.stats_time, SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(entry.timestamp)))).append("\n")
            
            val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
            val moodLabels = moodKeys.map { key ->
                val resId = resources.getIdentifier(key, "string", packageName)
                if (resId != 0) getString(resId) else key
            }
            val moodIndex = moodKeys.indexOf(entry.moodLabel).let { if (it == -1) moodLabels.indexOf(entry.moodLabel) else it }
            val moodScore = if (moodIndex != -1) (moodIndex + 1).toString() else entry.moodLabel
            message.append(getString(R.string.stats_mood_score, moodScore)).append("\n")
            
            if (entry.activities.isNotEmpty()) {
                message.append(getString(R.string.stats_activities, entry.activities.joinToString(", "))).append("\n")
            }
            if (entry.memberIds.isNotEmpty()) {
                val names = entry.memberIds
                    .mapNotNull { id -> people.find { it.id == id && !it.isArchived && !it.isSysmediaOnly }?.name }
                if (names.isNotEmpty()) {
                    message.append(getString(R.string.stats_members, names.joinToString(", "))).append("\n")
                }
            }
            if (!entry.note.isNullOrBlank()) {
                message.append(getString(R.string.stats_note, entry.note)).append("\n")
            }
            message.append("\n")
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.stats_details))
            .setMessage(message.toString().trim())
            .setPositiveButton("OK", null)
            .create()
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun renderActivityInfluence(entries: List<MoodActivity.MoodEntry>, excludedActivities: Set<String>, showAll: Boolean = false) {
        val container = findViewById<LinearLayout>(R.id.containerActivityInfluence)
        container.removeAllViews()

        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
        val moodLabels = moodKeys.map { key ->
            val resId = resources.getIdentifier(key, "string", packageName)
            if (resId != 0) getString(resId) else key
        }

        val activityScores = mutableMapOf<String, MutableList<Int>>()
        entries.forEach { entry ->
            var score = moodKeys.indexOf(entry.moodLabel)
            if (score == -1) {
                score = moodLabels.indexOf(entry.moodLabel)
            }

            if (score != -1) {
                entry.activities.forEach { activity ->
                    if (!excludedActivities.contains(activity)) {
                        activityScores.getOrPut(activity) { mutableListOf() }.add(score)
                    }
                }
            }
        }

        if (activityScores.isEmpty()) {
            findViewById<Button>(R.id.btnViewAllActivities).visibility = View.GONE
            return
        }

        var activityStats = activityScores.map { (activity, scores) ->
            Triple(activity, scores.average(), scores.size)
        }.sortedByDescending { it.third }

        if (!showAll && activityStats.size > 5) {
            findViewById<Button>(R.id.btnViewAllActivities).visibility = View.VISIBLE
            activityStats = activityStats.take(5)
        } else {
            findViewById<Button>(R.id.btnViewAllActivities).visibility = View.GONE
        }

        val maxCount = activityStats.maxOf { it.third }.toFloat()

        activityStats.forEach { (activity, avg, count) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val color = ColorHelper.getMoodColorByScore(this, avg.toFloat())
            val moodIndex = Math.round(avg).toInt().coerceIn(0, 4)

            val thumbFrame = FrameLayout(this).apply {
                setTag(R.id.color_tag, "skip")
                layoutParams = LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx())
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                }
            }
            thumbFrame.addView(TextView(this).apply {
                text = "👍"
                textSize = 14f
                val rotations = listOf(180f, 135f, 90f, 45f, 0f)
                this.rotation = rotations[moodIndex]
                gravity = android.view.Gravity.CENTER
            })
            row.addView(thumbFrame)

            val infoContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 12.dpToPx()
                }
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val nameTv = TextView(this).apply {
                text = getString(R.string.stats_activity_count, activity, count)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity))
                textSize = 14f
            }
            header.addView(nameTv)

            val scoreTv = TextView(this).apply {
                text = getString(R.string.stats_score_value, String.format(Locale.getDefault(), "%.1f", avg + 1))
                textSize = 12f
                setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity))
                alpha = 0.7f
            }
            header.addView(scoreTv)
            infoContainer.addView(header)

            val bar = View(this).apply {
                val weight = count / maxCount
                layoutParams = LinearLayout.LayoutParams(0, 6.dpToPx(), weight).apply {
                    topMargin = 4.dpToPx()
                }
                setBackgroundColor(color)
            }
            
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(bar)
                addView(View(this@MoodStatsActivity).apply { 
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f - (count / maxCount))
                })
            }
            infoContainer.addView(barContainer)
            row.addView(infoContainer)

            container.addView(row)
        }
    }

    private fun showActivityFilterDialog() {
        val rawEntries = loadEntries()
        val allActivities = rawEntries.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        
        if (allActivities.isEmpty()) return

        val settingsPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val excludedActivities = settingsPref.getStringSet("excluded_activities", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val checkedItems = BooleanArray(allActivities.size) { i -> !excludedActivities.contains(allActivities[i]) }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.exclude_activity_title))
            .setMultiChoiceItems(allActivities.toTypedArray(), checkedItems) { _, which, isChecked ->
                val activity = allActivities[which]
                if (isChecked) excludedActivities.remove(activity) else excludedActivities.add(activity)
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                settingsPref.edit().putStringSet("excluded_activities", excludedActivities).apply()
                loadAndRender()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        ColorHelper.styleSupportAlertDialog(dialog, this)
        dialog.show()

        val btnSave = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        if (btnSave is com.google.android.material.button.MaterialButton) {
            btnSave.setIconResource(android.R.drawable.ic_menu_save)
            btnSave.iconPadding = 12
            val textColor = btnSave.currentTextColor
            btnSave.iconTint = android.content.res.ColorStateList.valueOf(textColor)
        }
    }

    private fun showAllActivitiesDialog() {
        Thread {
            val rawEntries = loadEntries()
            val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
            val peopleJson = sharedPref.getString("people_list", "[]") ?: "[]"
            val allPeople: List<Person> = gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type)
            val excludedIds = allPeople.filter { it.excludeFromStats || it.isArchived }.map { it.id }.toSet()
            val entries = rawEntries.filter { entry -> (entry.memberIds.isEmpty() || entry.memberIds.any { !excludedIds.contains(it) }) }
            
            val settingsPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
            val excludedActivities = settingsPref.getStringSet("excluded_activities", emptySet()) ?: emptySet()

            runOnUiThread {
                val scrollView = ScrollView(this)
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
                }
                scrollView.addView(container)

                val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.stats_top_activities))
                    .setView(scrollView)
                    .setPositiveButton(getString(R.string.close), null)
                    .create()
                
                renderActivityInfluenceInContainer(container, entries, excludedActivities)
                dialog.show()
                ColorHelper.styleSupportAlertDialog(dialog, this)
            }
        }.start()
    }

    private fun renderActivityInfluenceInContainer(container: LinearLayout, entries: List<MoodActivity.MoodEntry>, excludedActivities: Set<String>) {
        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
        val moodLabels = moodKeys.map { key ->
            val resId = resources.getIdentifier(key, "string", packageName)
            if (resId != 0) getString(resId) else key
        }

        val activityScores = mutableMapOf<String, MutableList<Int>>()
        entries.forEach { entry ->
            var score = moodKeys.indexOf(entry.moodLabel)
            if (score == -1) score = moodLabels.indexOf(entry.moodLabel)
            if (score != -1) {
                entry.activities.forEach { activity ->
                    if (!excludedActivities.contains(activity)) {
                        activityScores.getOrPut(activity) { mutableListOf() }.add(score)
                    }
                }
            }
        }

        val activityStats = activityScores.map { (activity, scores) ->
            Triple(activity, scores.average(), scores.size)
        }.sortedByDescending { it.third }

        val maxCount = activityStats.maxOfOrNull { it.third }?.toFloat() ?: 1f

        activityStats.forEach { (activity, avg, count) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 16.dpToPx()) }
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            header.addView(TextView(this).apply {
                text = getString(R.string.stats_activity_count, activity, count)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity)); textSize = 14f
            })
            header.addView(TextView(this).apply {
                text = getString(R.string.stats_score_value, String.format(Locale.getDefault(), "%.1f", avg + 1))
                textSize = 12f; setTextColor(ColorHelper.getTextColor(this@MoodStatsActivity)); alpha = 0.7f
            })
            row.addView(header)
                val bar = View(this).apply {
                    val weight = count / maxCount
                    layoutParams = LinearLayout.LayoutParams(0, 8.dpToPx(), weight).apply { topMargin = 4.dpToPx() }
                    setBackgroundColor(ColorHelper.getMoodColorByScore(this@MoodStatsActivity, avg.toFloat()))
                }
            val barContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(bar)
                addView(View(this@MoodStatsActivity).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f - (count / maxCount)) }) }
            row.addView(barContainer)
            container.addView(row)
        }
    }

    private fun loadEntries(): List<MoodActivity.MoodEntry> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("mood_entries", "[]") ?: "[]"
        val type = object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type
        val list: List<MoodActivity.MoodEntry> = try { gson.fromJson(json, type) } catch (_: Exception) { emptyList() }
        
        return list.map { entry ->
            MoodActivity.MoodEntry(
                id = entry.id ?: UUID.randomUUID().toString(),
                timestamp = entry.timestamp,
                moodEmoji = entry.moodEmoji ?: "👍",
                moodRotation = entry.moodRotation,
                moodLabel = entry.moodLabel ?: "mood_meh",
                moodColor = entry.moodColor,
                memberIds = entry.memberIds ?: emptyList(),
                activities = entry.activities ?: emptyList(),
                note = entry.note ?: "",
                linkedNoteId = entry.linkedNoteId,
                linkedTodoId = entry.linkedTodoId
            )
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
