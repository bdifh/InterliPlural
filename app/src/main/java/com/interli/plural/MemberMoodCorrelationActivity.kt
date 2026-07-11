package com.interli.plural

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MemberMoodCorrelationActivity : BaseActivity() {

    private val gson = Gson()
    private lateinit var allEntries: List<MoodActivity.MoodEntry>
    private lateinit var people: List<Person>
    
    private var selectedMemberIds = mutableListOf<String>()
    private var selectedActivities = mutableListOf<String>()
    private var selectedStatsGroupId: String? = null
    
    private var customStartDate: Calendar? = null
    private var customEndDate: Calendar? = null
    private var currentPeriodStart: Long = 0L
    private var currentPeriodEnd: Long = Long.MAX_VALUE
    
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_member_mood_correlation)

        ColorHelper.applySettings(this)
        loadData()
        
        setupPeriodSpinner()
        setupDatePickers()
        setupSelectionButtons()
        setupNavigationDrawer()

        findViewById<TextView>(R.id.labelMatrixTitle)?.setTextColor(ColorHelper.getTextColor(this))
        
        val spinner = findViewById<Spinner>(R.id.spinnerStatsPeriod)
        spinner.setSelection(1)
        updateFilteredData(1)
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val entriesJson = sharedPref.getString("mood_entries", "[]") ?: "[]"
        val peopleJson = sharedPref.getString("people_list", "[]") ?: "[]"
        
        val rawEntries: List<MoodActivity.MoodEntry> = try {
            gson.fromJson<List<MoodActivity.MoodEntry>>(entriesJson, object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type)?.filterNotNull() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        allEntries = rawEntries.map { entry ->
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

        val rawPeople: List<Person> = try {
            gson.fromJson<List<Person>>(peopleJson, object : TypeToken<List<Person>>() {}.type)?.filterNotNull() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        people = rawPeople.filter { 
            @Suppress("SENSELESS_COMPARISON")
            (it != null) && (it.id != null) && !it.isArchived && !it.isSysmediaOnly && !it.excludeFromStats
        }
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
        
        val textColor = ColorHelper.getTextColor(this)
        val bgColor = ColorHelper.getBgColor(this)

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, periods) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.setTextColor(textColor)
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as? TextView)?.setTextColor(textColor)
                v.setBackgroundColor(bgColor)
                return v
            }
        }
        
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
            val dialog = DatePickerDialog(this, { _, y, m, d ->
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
            val dialog = DatePickerDialog(this, { _, y, m, d ->
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
        findViewById<Button>(R.id.btnSelectMembers).setOnClickListener {
            val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
            val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
            val groups: List<Group> = try {
                gson.fromJson<List<Group>>(groupsJson, object : TypeToken<List<Group>>() {}.type)?.filterNotNull() ?: emptyList()
            } catch (_: Exception) { emptyList() }

            DialogHelper.showMemberSelectionDialog(
                this,
                getString(R.string.select_member),
                people,
                groups,
                selectedMemberIds,
                isMultiSelect = true
            ) { newList ->
                selectedMemberIds.clear()
                selectedMemberIds.addAll(newList)
                findViewById<Button>(R.id.btnSelectMembers).text = if (newList.isEmpty()) getString(R.string.all_members) else getString(R.string.n_members_selected, newList.size)
                render()
            }
        }

        findViewById<Button>(R.id.btnSelectStatsGroup).setOnClickListener {
            showGroupSelectionDialog()
        }

        findViewById<Button>(R.id.btnSelectActivities).setOnClickListener {
            val allActivities = allEntries.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

            DialogHelper.showSearchableMultiSelectDialog(
                this,
                getString(R.string.select_activities),
                allActivities,
                selectedActivities
            ) { newList ->
                selectedActivities.clear()
                selectedActivities.addAll(newList)
                findViewById<Button>(R.id.btnSelectActivities).text = if (selectedActivities.isEmpty()) getString(R.string.all_activities) else getString(R.string.n_activities_selected, selectedActivities.size)
                render()
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
        render()
    }

    private fun render() {
        if (!::allEntries.isInitialized || !::people.isInitialized) return

        val groups = loadActivityGroups()
        val selectedGroup = groups.find { it.id == selectedStatsGroupId }
        
        val stats = calculateStats(selectedGroup)

        findViewById<Button>(R.id.btnSelectStatsGroup).text = selectedGroup?.name ?: getString(R.string.all_activities)
        renderRecentFrontingActivityUi(stats.recentActivityData)
        renderMemberMoodAveragesUi(stats.memberAverages)
        renderMemberActivityMatrixUi(stats.matrixData)
    }

    private data class ProcessedStats(
        val recentActivityData: RecentActivityData,
        val memberAverages: List<Triple<String, Double, Int>>,
        val matrixData: MatrixData
    )

    private data class RecentActivityData(
        val activitiesToShow: List<String>,
        val activityCounts: Map<String, Int>,
        val activityMemberCounts: Map<String, Map<String, Int>>,
        val totalActivityCount: Int
    )

    private data class MatrixData(
        val targetMembers: List<Person>,
        val targetActivities: List<String>,
        val matrixScores: Map<Pair<String, String>, List<Int>>
    )

    private fun calculateStats(selectedGroup: MoodActivity.ActivityGroup?): ProcessedStats {
        val memberSet = selectedMemberIds.toSet()
        val activitySet = selectedActivities.toSet()
        val groupActivitySet = selectedGroup?.activityNames?.toSet() ?: emptySet()

        val filtered = allEntries.filter { entry ->
            if (entry.timestamp !in currentPeriodStart..currentPeriodEnd) return@filter false
            if (memberSet.isNotEmpty() && !entry.memberIds.any { memberSet.contains(it) }) return@filter false
            if (activitySet.isNotEmpty() && !entry.activities.any { activitySet.contains(it) }) return@filter false
            if (selectedGroup != null && !entry.activities.any { groupActivitySet.contains(it) }) return@filter false
            true
        }

        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val excludedActivities = getSafeSet(sp, "excluded_activities")
        val pinnedActivities = getSafeSet(sp, "pinned_activities")
        
        val activityCounts = mutableMapOf<String, Int>()
        val activityMemberCounts = mutableMapOf<String, MutableMap<String, Int>>()
        val peopleIdsSet = people.map { it.id }.toSet()
        var totalActivityCount = 0

        for (entry in filtered) {
            for (activity in entry.activities) {
                if (excludedActivities.contains(activity)) continue
                if (selectedGroup != null && !groupActivitySet.contains(activity)) continue
                
                totalActivityCount++
                activityCounts[activity] = (activityCounts[activity] ?: 0) + 1
                
                val memberMap = activityMemberCounts.getOrPut(activity) { mutableMapOf() }
                for (mId in entry.memberIds) {
                    if (peopleIdsSet.contains(mId)) {
                        memberMap[mId] = (memberMap[mId] ?: 0) + 1
                    }
                }
            }
        }

        val activitiesToShow = if (pinnedActivities.isNotEmpty()) {
            pinnedActivities.filter { activityCounts.containsKey(it) }.sorted()
        } else {
            val mostPopularActivity = activityCounts.maxByOrNull { it.value }?.key
            if (mostPopularActivity != null) listOf(mostPopularActivity) else emptyList()
        }

        val memberMap = people.associateBy { it.id }
        val memberScores = mutableMapOf<String, MutableList<Int>>()
        for (entry in filtered) {
            val score = moodKeys.indexOf(entry.moodLabel) + 1
            val effectiveScore = if (score > 0) score else 3
            for (memberId in entry.memberIds) {
                if (memberMap.containsKey(memberId)) {
                    memberScores.getOrPut(memberId) { mutableListOf() }.add(effectiveScore)
                }
            }
        }
        val memberAverages = memberScores.map { (memberId, scores) ->
            val average = scores.average()
            val name = memberMap[memberId]?.name ?: getString(R.string.deleted_member)
            Triple(name, average, scores.size)
        }.sortedByDescending { it.second }

        val matrixScores = mutableMapOf<Pair<String, String>, MutableList<Int>>()
        val activityCountsGlobal = mutableMapOf<String, Int>()

        for (entry in filtered) {
            val score = moodKeys.indexOf(entry.moodLabel) + 1
            val effectiveScore = if (score > 0) score else 3
            for (activity in entry.activities) {
                if (selectedGroup != null && !groupActivitySet.contains(activity)) continue

                activityCountsGlobal[activity] = (activityCountsGlobal[activity] ?: 0) + 1
                for (memberId in entry.memberIds) {
                    matrixScores.getOrPut(memberId to activity) { mutableListOf() }.add(effectiveScore)
                }
            }
        }

        val targetActivities = if (selectedActivities.isEmpty()) {
            activityCountsGlobal.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
        } else {
            selectedActivities.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

        return ProcessedStats(
            RecentActivityData(activitiesToShow, activityCounts, activityMemberCounts, totalActivityCount),
            memberAverages,
            MatrixData(people.filter { p -> matrixScores.keys.any { it.first == p.id } }, targetActivities, matrixScores)
        )
    }

    private fun renderRecentFrontingActivityUi(data: RecentActivityData) {
        val container = findViewById<LinearLayout>(R.id.containerRecentFrontingActivity) ?: return
        container.removeAllViews()
        if (data.activityCounts.isEmpty()) return

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
            text = getString(R.string.action_pin_filter)
            textSize = 12f
            setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
            setBackgroundColor(btnColor)
            setTextColor(btnTextColor)
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, (36 * resources.displayMetrics.density).toInt())
            lp.addRule(RelativeLayout.ALIGN_PARENT_END)
            lp.addRule(RelativeLayout.CENTER_VERTICAL)
            layoutParams = lp
            setOnClickListener { showPinnedActivitiesDialog() }
        }
        header.addView(btnPin)
        container.addView(header)

        val fTotal = data.totalActivityCount.toFloat()
        data.activitiesToShow.take(15).forEach { activity ->
            val count = data.activityCounts[activity] ?: 0
            val percentage = if (fTotal > 0) (count / fTotal * 100).toInt() else 0
            
            val activityRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8.dpToPx(), 0, 4.dpToPx())
            }
            val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            titleRow.addView(TextView(this).apply {
                text = activity
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            titleRow.addView(TextView(this).apply {
                text = String.format(Locale.getDefault(), "%d%%", percentage)
                textSize = 14f
                setTextColor(textColor)
                alpha = 0.7f
            })
            activityRow.addView(titleRow)
            container.addView(activityRow)

            val memberMap = data.activityMemberCounts[activity] ?: emptyMap()
            val totalInActivity = memberMap.values.sum().toFloat()

            memberMap.toList().sortedByDescending { it.second }.take(10).forEach { (memberId, mCount) ->
                val person = people.find { it.id == memberId } ?: return@forEach
                val mPercentage = if (totalInActivity > 0) (mCount / totalInActivity * 100).toInt() else 0
                val memberRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(16.dpToPx(), 2.dpToPx(), 0, 2.dpToPx())
                }
                memberRow.addView(TextView(this).apply {
                    text = "• ${person.name}"
                    setTextColor(textColor)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                memberRow.addView(TextView(this).apply {
                    text = "$mPercentage%"
                    setTextColor(textColor)
                    alpha = 0.7f
                })
                container.addView(memberRow)
            }
        }
    }

    private fun renderMemberMoodAveragesUi(memberAverages: List<Triple<String, Double, Int>>) {
        val container = findViewById<LinearLayout>(R.id.containerMemberMoodAverages) ?: return
        container.removeAllViews()
        if (memberAverages.isEmpty()) return

        memberAverages.forEach { (name, average, count) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = name
                setTextColor(ColorHelper.getTextColor(this@MemberMoodCorrelationActivity))
                textSize = 15f
            })
            row.addView(TextView(this).apply {
                text = String.format(Locale.getDefault(), "%.2f", average)
                setTextColor(ColorHelper.getTextColor(this@MemberMoodCorrelationActivity))
                setTypeface(null, android.graphics.Typeface.BOLD)
                textSize = 16f
            })
            row.addView(TextView(this).apply {
                text = " ($count)"
                setTextColor(ColorHelper.getTextColor(this@MemberMoodCorrelationActivity))
                textSize = 12f
                alpha = 0.7f
                setPadding(4.dpToPx(), 0, 0, 0)
            })
            container.addView(row)
        }
    }

    private fun renderMemberActivityMatrixUi(data: MatrixData) {
        val tableData = findViewById<TableLayout>(R.id.tableMemberActivityMatrix) ?: return
        val tableNames = findViewById<TableLayout>(R.id.tableMemberNamesSticky) ?: return
        tableData.removeAllViews()
        tableNames.removeAllViews()

        val textColor = ColorHelper.getTextColor(this)
        findViewById<TextView>(R.id.labelMatrixTitle)?.text = getString(R.string.mood_insights)

        if (data.targetActivities.isEmpty() || data.targetMembers.isEmpty()) return

        val rowHeight = 72.dpToPx()
        val headerRowNames = TableRow(this).apply { minimumHeight = rowHeight }
        headerRowNames.addView(TextView(this).apply {
            text = getString(R.string.label_member_activity)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            setPadding(8.dpToPx(), 16.dpToPx(), 24.dpToPx(), 16.dpToPx())
            layoutParams = TableRow.LayoutParams(120.dpToPx(), ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        tableNames.addView(headerRowNames)

        val headerRowData = TableRow(this).apply { minimumHeight = rowHeight }
        data.targetActivities.forEach { activity ->
            headerRowData.addView(TextView(this).apply {
                text = activity
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                gravity = android.view.Gravity.CENTER
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            })
        }
        tableData.addView(headerRowData)

        data.targetMembers.forEach { person ->
            val rowNames = TableRow(this).apply { minimumHeight = rowHeight }
            rowNames.addView(TextView(this).apply {
                text = person.name
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                setPadding(8.dpToPx(), 16.dpToPx(), 8.dpToPx(), 16.dpToPx())
                layoutParams = TableRow.LayoutParams(120.dpToPx(), ViewGroup.LayoutParams.WRAP_CONTENT)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            tableNames.addView(rowNames)

            val rowData = TableRow(this).apply { minimumHeight = rowHeight }
            data.targetActivities.forEach { activity ->
                val scores = data.matrixScores[person.id to activity]
                val cellTv = TextView(this).apply {
                    gravity = android.view.Gravity.CENTER
                    setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                    layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, rowHeight)
                    textSize = 12f
                    setTextColor(textColor)
                    maxLines = 2
                }

                if (scores != null && scores.isNotEmpty()) {
                    val avgScore = scores.average()
                    val sb = android.text.SpannableStringBuilder("${scores.size}x\n")
                    sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, sb.length, 0)
                    sb.append(String.format(Locale.getDefault(), "%.1f", avgScore))
                    cellTv.text = sb
                    val moodColor = ColorHelper.getMoodColorByScore(this@MemberMoodCorrelationActivity, (avgScore-1).toFloat())
                    cellTv.setBackgroundColor((moodColor and 0x00FFFFFF) or 0x22000000)
                } else {
                    cellTv.text = "-"; cellTv.alpha = 0.3f
                }
                rowData.addView(cellTv)
            }
            tableData.addView(rowData)
        }
    }

    private fun showPinnedActivitiesDialog() {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val pinned = getSafeSet(sp, "pinned_activities").toMutableSet()
        val allActivities = allEntries.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
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
        ColorHelper.styleAlertDialog(dialog, this); dialog.show()
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
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        ColorHelper.styleAlertDialog(dialog, this); dialog.show()
    }

    private fun showActivityFilterDialog() {
        val allActivities = allEntries.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (allActivities.isEmpty()) return
        val settingsPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val excluded = getSafeSet(settingsPref, "excluded_activities").toMutableSet()
        val checkedItems = BooleanArray(allActivities.size) { i -> !excluded.contains(allActivities[i]) }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.exclude_activity_title))
            .setMultiChoiceItems(allActivities.toTypedArray(), checkedItems) { _, which, isChecked ->
                val act = allActivities[which]
                if (isChecked) excluded.remove(act) else excluded.add(act)
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                settingsPref.edit().putStringSet("excluded_activities", excluded).apply()
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        ColorHelper.styleAlertDialog(dialog, this); dialog.show()
    }

    private fun showGroupSelectionDialog() {
        val groups = loadActivityGroups()
        val names = mutableListOf<String>()
        names.add(getString(R.string.all_activities))
        names.addAll(groups.map { it.name })
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.label_groups))
            .setItems(names.toTypedArray()) { _, which ->
                selectedStatsGroupId = if (which == 0) null else groups[which - 1].id
                render()
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

    private fun getSafeSet(sp: android.content.SharedPreferences, key: String): Set<String> {
        return try {
            sp.getStringSet(key, emptySet()) ?: emptySet()
        } catch (_: Exception) {
            val json = try { sp.getString(key, "[]") } catch (_: Exception) { "[]" }
            try { gson.fromJson<Set<String>>(json, object : TypeToken<Set<String>>() {}.type) ?: emptySet() } catch (_: Exception) { emptySet() }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
