package com.interli.plural

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class ExtensiveMoodStatsActivity : BaseActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extensive_mood_stats)

        ColorHelper.applySettings(this)
        loadData()
        
        setupPeriodSpinner()
        setupDatePickers()
        setupSelectionButtons()
        setupNavigationDrawer()

        val spinner = findViewById<Spinner>(R.id.spinnerStatsPeriod)
        spinner.setSelection(1)
        updateFilteredData(1)
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val entriesJson = sharedPref.getString("mood_entries", "[]") ?: "[]"
        val peopleJson = sharedPref.getString("people_list", "[]") ?: "[]"
        val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
        
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
        val groups: List<Group> = try {
            gson.fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        
        this.intent.putExtra("temp_groups", groupsJson)
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
                    getString(R.string.n_activities_selected, newList.size).replace(getString(R.string.activities).lowercase(), getString(R.string.label_person).replace(":","").lowercase())
                }

                if (newList.isEmpty()) {
                    findViewById<Button>(R.id.btnSelectMembers).text = getString(R.string.all_members)
                } else {
                    findViewById<Button>(R.id.btnSelectMembers).text = getString(R.string.n_members_selected, newList.size)
                }
                
                render()
            }
        }

        findViewById<Button>(R.id.btnSelectMembers).setOnLongClickListener {
            selectedMemberIds.clear()
            findViewById<Button>(R.id.btnSelectMembers).text = getString(R.string.all_members)
            render()
            Toast.makeText(this, getString(R.string.all_members), Toast.LENGTH_SHORT).show()
            true
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

        findViewById<Button>(R.id.btnSelectActivities).setOnLongClickListener {
            selectedActivities.clear()
            findViewById<Button>(R.id.btnSelectActivities).text = getString(R.string.all_activities)
            render()
            Toast.makeText(this, getString(R.string.all_activities), Toast.LENGTH_SHORT).show()
            true
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
        val container = findViewById<LinearLayout>(R.id.containerDetailedStats)
        container.removeAllViews()

        val filtered = allEntries.filter { it.timestamp in currentPeriodStart..currentPeriodEnd }
        
        if (filtered.isEmpty()) {
            container.addView(TextView(this).apply { 
                text = getString(R.string.no_mood_entries)
                setTextColor(ColorHelper.getTextColor(this@ExtensiveMoodStatsActivity))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 32.dpToPx(), 0, 32.dpToPx())
            })
            return
        }

        val targetMembers = if (selectedMemberIds.isEmpty()) people.map { it.id } else selectedMemberIds
        val targetActivities = if (selectedActivities.isEmpty()) {
            filtered.flatMap { it.activities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        } else {
            selectedActivities.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

        val totalAllActivityEntries = filtered.flatMap { it.activities }.size.toFloat()
        val textColor = ColorHelper.getTextColor(this)

        targetActivities.forEach { activity ->
            val activityEntries = filtered.filter { it.activities.contains(activity) }
            if (activityEntries.isEmpty()) return@forEach

            val totalForThisActivity = activityEntries.size
            val activityPercentage =
                if (totalAllActivityEntries > 0) (totalForThisActivity / totalAllActivityEntries * 100).toInt() else 0

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 24.dpToPx())
            }

            val activityHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            activityHeader.addView(TextView(this).apply {
                text = activity
                textSize = 16f
                textStyle = android.graphics.Typeface.BOLD
                setTextColor(textColor)
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            activityHeader.addView(TextView(this).apply {
                text = "$activityPercentage%"
                textSize = 14f
                setTextColor(textColor)
                alpha = 0.7f
            })
            row.addView(activityHeader)

            var hasAnyData = false
            targetMembers.forEach { memberId ->
                val person = people.find { it.id == memberId } ?: return@forEach
                val count = activityEntries.count { it.memberIds.contains(memberId) }

                if (count == 0 && selectedMemberIds.isEmpty() && selectedActivities.isEmpty()) return@forEach

                hasAnyData = true
                val memberRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(16.dpToPx(), 4.dpToPx(), 0, 0)
                }

                val memberPercentage =
                    if (totalForThisActivity > 0) (count.toFloat() / totalForThisActivity * 100).toInt() else 0

                memberRow.addView(TextView(this).apply {
                    text = getString(R.string.stats_person_label, person.name)
                    layoutParams =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setTextColor(textColor)
                })

                memberRow.addView(TextView(this).apply {
                    text = "$count ($memberPercentage%)"
                    setTextColor(textColor)
                    textStyle = android.graphics.Typeface.BOLD
                })

                row.addView(memberRow)
            }

            if (hasAnyData) {
                container.addView(row)
            }
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    
    private var TextView.textStyle: Int
        get() = typeface?.style ?: android.graphics.Typeface.NORMAL
        set(value) { setTypeface(typeface, value) }
}
