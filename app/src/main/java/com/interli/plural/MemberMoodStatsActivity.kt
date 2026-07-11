package com.interli.plural

import android.os.Bundle
import android.view.View
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class MemberMoodStatsActivity : BaseActivity() {

    private val gson = Gson()
    private lateinit var personId: String
    private lateinit var personName: String
    private lateinit var allEntries: List<MoodActivity.MoodEntry>

    private var selectedStatsGroupId: String? = null
    private var selectedActivities = mutableListOf<String>()
    private var customStartDate: Calendar? = null
    private var customEndDate: Calendar? = null
    private var currentPeriodStart: Long = 0L
    private var currentPeriodEnd: Long = Long.MAX_VALUE
    
    private val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_member_mood_stats)

        ColorHelper.applySettings(this)

        personId = intent.getStringExtra("person_id") ?: return
        personName = intent.getStringExtra("person_name") ?: ""

        findViewById<TextView>(R.id.memberMoodStatsTitle).text = getString(R.string.member_mood_stats, personName)

        setupPeriodSpinner()
        setupDatePickers()
        setupSelectionButtons()
        setupNavigationDrawer()
        
        loadData()
        updateFilteredData(1) // Default to Last 30 Days
    }

    private fun loadData() {
        allEntries = loadEntries()
    }

    private fun setupPeriodSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerStatsPeriod)
        val periods = listOf(
            getString(R.string.period_all_time),
            getString(R.string.period_last_30_days),
            getString(R.string.period_current_month),
            getString(R.string.period_last_month),
            getString(R.string.period_last_7_days),
            getString(R.string.period_current_week),
            getString(R.string.period_custom)
        )
        
        val adapter = ColorHelper.createThemedAdapter(this, periods)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                findViewById<View>(R.id.layoutCustomRange).visibility = if (position == 6) View.VISIBLE else View.GONE
                updateFilteredData(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDatePickers() {
        val btnStart = findViewById<Button>(R.id.btnStartDate)
        val btnEnd = findViewById<Button>(R.id.btnEndDate)

        btnStart.setOnClickListener {
            val cal = customStartDate ?: Calendar.getInstance()
            val dialog = android.app.DatePickerDialog(this, { _, y, m, d ->
                val selected = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                customStartDate = selected
                btnStart.text = dateFormat.format(selected.time)
                updateFilteredData(6)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dialog.show()
            ColorHelper.styleAlertDialog(dialog, this)
        }

        btnEnd.setOnClickListener {
            val cal = customEndDate ?: Calendar.getInstance()
            val dialog = android.app.DatePickerDialog(this, { _, y, m, d ->
                val selected = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999) }
                customEndDate = selected
                btnEnd.text = dateFormat.format(selected.time)
                updateFilteredData(6)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dialog.show()
            ColorHelper.styleAlertDialog(dialog, this)
        }
    }

    private fun setupSelectionButtons() {
        findViewById<Button>(R.id.btnSelectStatsGroup).setOnClickListener {
            showGroupSelectionDialog()
        }

        findViewById<Button>(R.id.btnFilterActivities).setOnClickListener {
            val activities = allEntries.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            DialogHelper.showSearchableMultiSelectDialog(
                this,
                getString(R.string.filter_activities),
                activities,
                selectedActivities
            ) { newList ->
                selectedActivities.clear()
                selectedActivities.addAll(newList)
                findViewById<Button>(R.id.btnFilterActivities).text = if (selectedActivities.isEmpty()) getString(R.string.filter_activities) else getString(R.string.n_activities_selected, selectedActivities.size)
                renderStats(personId)
            }
        }
    }

    private fun updateFilteredData(periodPosition: Int) {
        currentPeriodEnd = Long.MAX_VALUE
        when (periodPosition) {
            0 -> currentPeriodStart = 0
            1 -> { val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -30); currentPeriodStart = cal.timeInMillis }
            2 -> { val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }; currentPeriodStart = cal.timeInMillis }
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
        renderStats(personId)
    }

    private fun renderStats(personId: String) {
        if (!::allEntries.isInitialized) return
        
        val groups = loadActivityGroups()
        val selectedGroup = groups.find { it.id == selectedStatsGroupId }
        val groupActivitySet = selectedGroup?.activityNames?.toSet() ?: emptySet()

        val filtered = allEntries.filter { entry ->
            if (!entry.memberIds.contains(personId)) return@filter false
            if (entry.timestamp !in currentPeriodStart..currentPeriodEnd) return@filter false
            if (selectedActivities.isNotEmpty() && !entry.activities.any { selectedActivities.contains(it) }) return@filter false
            if (selectedGroup != null && !entry.activities.any { groupActivitySet.contains(it) }) return@filter false
            true
        }
        
        findViewById<Button>(R.id.btnSelectStatsGroup).text = selectedGroup?.name ?: getString(R.string.group_activities)

        if (filtered.isEmpty()) {
            findViewById<View>(R.id.cardMemberMoodChart).visibility = View.GONE
            findViewById<View>(R.id.cardDailyAverageChart).visibility = View.GONE
            findViewById<LinearLayout>(R.id.containerMemberMoodCounts).removeAllViews()
            findViewById<LinearLayout>(R.id.containerMemberActivityInfluence).removeAllViews()
            findViewById<MemberMoodDotCalendarView>(R.id.moodDotCalendar).setData(emptyList(), personId)
            return
        }

        findViewById<View>(R.id.cardMemberMoodChart).visibility = View.VISIBLE
        findViewById<View>(R.id.cardDailyAverageChart).visibility = View.VISIBLE

        findViewById<MoodChartView>(R.id.memberMoodChart).setData(filtered, MoodChartView.Mode.TIMELINE_MONTH)
        
        val dailyAverageChart = findViewById<MoodChartView>(R.id.dailyAverageChart)
        dailyAverageChart.setData(filtered, MoodChartView.Mode.DAILY_AVERAGE_MONTH)
        dailyAverageChart.setRange(currentPeriodStart, if (currentPeriodEnd == Long.MAX_VALUE) System.currentTimeMillis() else currentPeriodEnd)

        renderMoodCounts(filtered)
        renderActivityInfluence(filtered)

        val filteredForCalendar = allEntries.filter { entry ->
            if (!entry.memberIds.contains(personId)) return@filter false
            if (selectedActivities.isNotEmpty() && !entry.activities.any { selectedActivities.contains(it) }) return@filter false
            if (selectedGroup != null && !entry.activities.any { groupActivitySet.contains(it) }) return@filter false
            true
        }
        findViewById<MemberMoodDotCalendarView>(R.id.moodDotCalendar).setData(filteredForCalendar, personId)
    }

    private fun renderMoodCounts(entries: List<MoodActivity.MoodEntry>) {
        val container = findViewById<LinearLayout>(R.id.containerMemberMoodCounts)
        container.removeAllViews()

        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
        val moodResIds = listOf(R.string.mood_awful, R.string.mood_bad, R.string.mood_meh, R.string.mood_good, R.string.mood_rad)
        val moodTranslations = moodKeys.zip(moodResIds.map { getString(it) }).toMap()
        val counts = entries.groupingBy { it.moodLabel }.eachCount()
        
        val consolidatedCounts = mutableMapOf<String, Int>()
        counts.forEach { (label, count) ->
            val key = if (moodKeys.contains(label)) label else {
                moodTranslations.entries.find { it.value == label }?.key ?: label
            }
            consolidatedCounts[key] = (consolidatedCounts[key] ?: 0) + count
        }

        val sorted = moodKeys.reversed().map { it to (consolidatedCounts[it] ?: 0) }
        val total = entries.size.toFloat()
        val textColor = ColorHelper.getTextColor(this)

        sorted.forEach { (key, count) ->
            if (count == 0) return@forEach

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16.dpToPx())
            }

            val moodIndex = moodKeys.indexOf(key)
            val displayName = (moodIndex + 1).toString()

            val percentage = (count / total * 100).toInt()
            val labelTv = TextView(this).apply {
                text = getString(R.string.stats_score_count, displayName, count, percentage)
                textSize = 14f
                setTextColor(textColor)
            }
            row.addView(labelTv)

            val bar = View(this).apply {
                val weight = count / total
                layoutParams = LinearLayout.LayoutParams(0, 8.dpToPx(), weight).apply {
                    topMargin = 4.dpToPx()
                }
                
                setBackgroundColor(ColorHelper.getMoodColor(this@MemberMoodStatsActivity, key))
            }
            
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(bar)
                addView(View(this@MemberMoodStatsActivity).apply { 
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f - (count / total))
                })
            }
            row.addView(barContainer)
            container.addView(row)
        }
    }

    private fun renderActivityInfluence(entries: List<MoodActivity.MoodEntry>) {
        val container = findViewById<LinearLayout>(R.id.containerMemberActivityInfluence)
        container.removeAllViews()

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
                    activityScores.getOrPut(activity) { mutableListOf() }.add(score)
                }
            }
        }

        if (activityScores.isEmpty()) return

        val activityStats = activityScores.map { (activity, scores) ->
            Triple(activity, scores.average(), scores.size)
        }.sortedByDescending { it.third }

        val maxCount = activityStats.maxOf { it.third }.toFloat()
        val textColor = ColorHelper.getTextColor(this)

        activityStats.forEach { (activity, avg, count) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16.dpToPx())
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val nameTv = TextView(this).apply {
                text = getString(R.string.stats_activity_count, activity, count)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(textColor)
                textSize = 14f
            }
            header.addView(nameTv)

            val scoreTv = TextView(this).apply {
                text = getString(R.string.stats_score_value, String.format(Locale.getDefault(), "%.1f", avg + 1))
                textSize = 12f
                setTextColor(textColor)
                alpha = 0.7f
            }
            header.addView(scoreTv)
            row.addView(header)

            val bar = View(this).apply {
                val weight = count / maxCount
                layoutParams = LinearLayout.LayoutParams(0, 8.dpToPx(), weight).apply {
                    topMargin = 4.dpToPx()
                }
                
                setBackgroundColor(ColorHelper.getMoodColorByScore(this@MemberMoodStatsActivity, avg.toFloat()))
            }
            
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(bar)
                addView(View(this@MemberMoodStatsActivity).apply { 
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f - (count / maxCount))
                })
            }
            row.addView(barContainer)
            container.addView(row)
        }
    }

    private fun loadEntries(): List<MoodActivity.MoodEntry> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("mood_entries", "[]") ?: "[]"
        val type = object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type
        val rawList: List<MoodActivity.MoodEntry> = try {
            gson.fromJson<List<MoodActivity.MoodEntry>>(json, type)?.filterNotNull() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return rawList.map { entry ->
            MoodActivity.MoodEntry(
                id = entry.id ?: UUID.randomUUID().toString(),
                timestamp = entry.timestamp,
                moodEmoji = entry.moodEmoji ?: "👍",
                moodRotation = entry.moodRotation,
                moodLabel = entry.moodLabel ?: "mood_meh",
                moodColor = entry.moodColor,
                memberIds = (entry.memberIds ?: emptyList()).filterNotNull(),
                activities = (entry.activities ?: emptyList()).filterNotNull(),
                imageUris = (entry.imageUris ?: emptyList()).filterNotNull(),
                note = entry.note ?: "",
                linkedNoteId = entry.linkedNoteId,
                linkedTodoId = entry.linkedTodoId
            )
        }
    }

    private fun showGroupSelectionDialog() {
        val groups = loadActivityGroups()
        val names = mutableListOf<String>()
        names.add(getString(R.string.group_activities))
        names.addAll(groups.map { it.name })
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.label_groups))
            .setItems(names.toTypedArray()) { _, which ->
                selectedStatsGroupId = if (which == 0) null else groups[which - 1].id
                renderStats(personId)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.show(); ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun loadActivityGroups(): List<MoodActivity.ActivityGroup> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("activity_groups", "[]") ?: "[]"
        val rawList: List<MoodActivity.ActivityGroup> = try {
            gson.fromJson(json, object : TypeToken<List<MoodActivity.ActivityGroup>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        return rawList.map { group ->
            MoodActivity.ActivityGroup(
                id = group.id ?: UUID.randomUUID().toString(),
                name = group.name ?: "Unnamed",
                activityNames = (group.activityNames ?: mutableListOf()).filterNotNull().toMutableList()
            )
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
