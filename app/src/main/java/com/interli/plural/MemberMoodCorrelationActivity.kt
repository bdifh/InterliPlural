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
        
        allEntries = try {
            gson.fromJson(entriesJson, object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val allPeople: List<Person> = try {
            gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        people = allPeople.filter { !it.isArchived && !it.isSysmediaOnly && !it.excludeFromStats }
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
            val groupsJson = getSharedPreferences("my_app", MODE_PRIVATE).getString("groups_list", "[]") ?: "[]"
            val groups: List<Group> = gson.fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type)

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
                findViewById<Button>(R.id.btnSelectMembers).text = if (newList.isEmpty()) {
                    getString(R.string.all_members)
                } else {
                    getString(R.string.n_members_selected, newList.size)
                }
                render()
            }
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

                findViewById<Button>(R.id.btnSelectActivities).text = if (selectedActivities.isEmpty()) {
                    getString(R.string.all_activities)
                } else {
                    getString(R.string.n_activities_selected, selectedActivities.size)
                }
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
        val filtered = allEntries.filter { entry ->
            val matchesPeriod = entry.timestamp in currentPeriodStart..currentPeriodEnd
            val matchesMembers = selectedMemberIds.isEmpty() || entry.memberIds.any { selectedMemberIds.contains(it) }
            val matchesActivities = selectedActivities.isEmpty() || entry.activities.any { selectedActivities.contains(it) }
            matchesPeriod && matchesMembers && matchesActivities
        }

        renderRecentFrontingActivity(filtered)
        renderMemberMoodAverages(filtered)
        renderMemberActivityMatrix(filtered)
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

        val peopleList = people
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
                text = getString(R.string.no_data_pinned)
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
                .filter { mId -> peopleList.any { it.id == mId } }
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
                    val person = peopleList.find { it.id == memberId }
                    if (person == null) return@forEach

                    val name = person.name
                    val mPercentage = if (totalInActivity > 0) (mCount / totalInActivity * 100).toInt() else 0
                    
                    val memberRow = LinearLayout(this@MemberMoodCorrelationActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(16.dpToPx(), 2.dpToPx(), 0, 2.dpToPx())
                    }
                    memberRow.addView(TextView(this@MemberMoodCorrelationActivity).apply {
                        text = "• $name"
                        setTextColor(textColor)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    memberRow.addView(TextView(this@MemberMoodCorrelationActivity).apply {
                        text = "$mPercentage%"
                        setTextColor(textColor)
                        alpha = 0.7f
                    })
                    container.addView(memberRow)
                }
            }
        }
    }

    private fun showPinnedActivitiesDialog() {
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val pinned = sp.getStringSet("pinned_activities", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val allActivitiesFromEntries = allEntries.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (allActivitiesFromEntries.isEmpty()) return

        val options = arrayOf(getString(R.string.pin_activities), getString(R.string.filter_activities_label))
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.manage_activities))
            .setItems(options) { _, which ->
                if (which == 0) showMultiSelectPinDialog(allActivitiesFromEntries, pinned, sp)
                else showActivityFilterDialog()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        ColorHelper.styleAlertDialog(dialog, this)
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
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        ColorHelper.styleAlertDialog(dialog, this)
        dialog.show()
    }

    private fun showActivityFilterDialog() {
        val allActivitiesFromEntries = allEntries.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (allActivitiesFromEntries.isEmpty()) return

        val settingsPref = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val excludedActivities = settingsPref.getStringSet("excluded_activities", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val checkedItems = BooleanArray(allActivitiesFromEntries.size) { i -> !excludedActivities.contains(allActivitiesFromEntries[i]) }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.exclude_activity_title))
            .setMultiChoiceItems(allActivitiesFromEntries.toTypedArray(), checkedItems) { _, which, isChecked ->
                val activity = allActivitiesFromEntries[which]
                if (isChecked) excludedActivities.remove(activity) else excludedActivities.add(activity)
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                settingsPref.edit().putStringSet("excluded_activities", excludedActivities).apply()
                render()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        ColorHelper.styleAlertDialog(dialog, this)
        dialog.show()
    }

    private fun renderMemberMoodAverages(entries: List<MoodActivity.MoodEntry>) {
        val container = findViewById<LinearLayout>(R.id.containerMemberMoodAverages) ?: return
        container.removeAllViews()

        val memberMap = people.associateBy { it.id }
        
        val memberEntries = mutableMapOf<String, MutableList<MoodActivity.MoodEntry>>()
        for (entry in entries) {
            for (memberId in entry.memberIds) {
                if (memberMap.containsKey(memberId)) {
                    memberEntries.getOrPut(memberId) { mutableListOf() }.add(entry)
                }
            }
        }

        if (memberEntries.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = getString(R.string.stats_no_data_per_member)
                setTextColor(ColorHelper.getTextColor(this@MemberMoodCorrelationActivity))
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            }
            container.addView(emptyTv)
            return
        }

        val memberAverages = memberEntries.map { (memberId, memberMoodEntries) ->
            val totalScore = memberMoodEntries.sumOf { entry ->
                val score = moodKeys.indexOf(entry.moodLabel) + 1
                if (score > 0) score else 3
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
                setTextColor(ColorHelper.getTextColor(this@MemberMoodCorrelationActivity))
                textSize = 15f
            }

            val averageTv = TextView(this).apply {
                text = String.format(Locale.getDefault(), "%.2f", average)
                setTextColor(ColorHelper.getTextColor(this@MemberMoodCorrelationActivity))
                setTypeface(null, android.graphics.Typeface.BOLD)
                textSize = 16f
            }
            
            val countTv = TextView(this).apply {
                text = " ($count)"
                setTextColor(ColorHelper.getTextColor(this@MemberMoodCorrelationActivity))
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
                setBackgroundColor(android.graphics.Color.LTGRAY)
                alpha = 0.3f
            }
            container.addView(divider)
        }
    }

    private fun renderMemberActivityMatrix(entries: List<MoodActivity.MoodEntry>) {
        val tableData = findViewById<TableLayout>(R.id.tableMemberActivityMatrix) ?: return
        val tableNames = findViewById<TableLayout>(R.id.tableMemberNamesSticky) ?: return

        tableData.removeAllViews()
        tableNames.removeAllViews()

        val textColor = ColorHelper.getTextColor(this)
        findViewById<TextView>(R.id.labelMatrixTitle)?.setTextColor(textColor)

        val targetMembers = if (selectedMemberIds.isEmpty()) people else people.filter { selectedMemberIds.contains(it.id) }
        val targetActivities = if (selectedActivities.isEmpty()) {
            entries.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        } else {
            selectedActivities.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

        if (targetActivities.isEmpty() || targetMembers.isEmpty()) {
            val row = TableRow(this)
            row.addView(TextView(this).apply {
                text = getString(R.string.no_data_period)
                setTextColor(textColor)
                alpha = 0.5f
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            })
            tableData.addView(row)
            return
        }

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
        targetActivities.forEach { activity ->
            headerRowData.addView(TextView(this).apply {
                text = activity
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                gravity = android.view.Gravity.CENTER
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            })
        }
        tableData.addView(headerRowData)

        targetMembers.forEach { person ->
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
            targetActivities.forEach { activity ->
                val matches = entries.filter { it.memberIds.contains(person.id) && it.activities.contains(activity) }

                val cellLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                    layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, rowHeight)
                }

                if (matches.isNotEmpty()) {
                    val count = matches.size
                    val avgScore = matches.map { entry ->
                        val score = moodKeys.indexOf(entry.moodLabel) + 1
                        if (score > 0) score else 3
                    }.average()

                    cellLayout.addView(TextView(this).apply {
                        text = "${count}x"
                        textSize = 14f
                        setTextColor(textColor)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    })
                    cellLayout.addView(TextView(this).apply {
                        text = String.format(Locale.getDefault(), "%.1f", avgScore)
                        textSize = 12f
                        setTextColor(textColor)
                        alpha = 0.7f
                    })

                    val moodColor = ColorHelper.getMoodColorByScore(this@MemberMoodCorrelationActivity, (avgScore-1).toFloat())
                    cellLayout.setBackgroundColor((moodColor and 0x00FFFFFF) or 0x22000000)
                } else {
                    cellLayout.addView(TextView(this).apply {
                        text = "-"
                        setTextColor(textColor)
                        alpha = 0.3f
                    })
                }
                rowData.addView(cellLayout)
            }
            tableData.addView(rowData)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
