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

class StatisticsActivity : BaseActivity() {

    private var allSessions: List<FrontSession> = emptyList()
    private var filteredSessions: List<FrontSession> = emptyList()
    private var people: List<Person> = emptyList()

    private var customStartDate: Calendar? = null
    private var customEndDate: Calendar? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private var currentPeriodStart: Long = 0L
    private var currentPeriodEnd: Long = Long.MAX_VALUE
    private var highlightedDensityMemberId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        applyColors()
        setupNavigationDrawer()
        setupPeriodSpinner()
        setupDatePickers()
        
        findViewById<Button>(R.id.btnOpenFullVisualTimeline).setOnClickListener {
            val intent = android.content.Intent(this, TimelineVisualActivity::class.java)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnOpenFrontTimeline).setOnClickListener {
            val intent = android.content.Intent(this, TimelineActivity::class.java)
            startActivity(intent)
        }

        loadData()
    }

    private fun setupMiniTimelineLast24h() {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        val miniSessions = allSessions.filter { 
            val end = it.endTime ?: System.currentTimeMillis()
            end >= twentyFourHoursAgo
        }
        
        val chart = findViewById<TimelineChartView>(R.id.miniTimelineChart)
        chart.setData(miniSessions, people)
    }

    private fun setupPeriodSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinnerStatsPeriod)
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
                (v as? TextView)?.setTextColor(ColorHelper.getTextColor(this@StatisticsActivity))
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                val bgColor = ColorHelper.getBgColor(this@StatisticsActivity)
                v.setBackgroundColor(bgColor)
                (v as? TextView)?.setTextColor(ColorHelper.getTextColor(this@StatisticsActivity))
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                findViewById<LinearLayout>(R.id.layoutCustomRange).visibility = if (position == 6) View.VISIBLE else View.GONE
                updateFilteredData(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDatePickers() {
        findViewById<Button>(R.id.btnStartDate).setOnClickListener {
            val cal = customStartDate ?: Calendar.getInstance()
            val dpd = android.app.DatePickerDialog(this, { _, year, month, day ->
                customStartDate = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
                findViewById<Button>(R.id.btnStartDate).text = dateFormat.format(customStartDate!!.time)
                updateFilteredData(6)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dpd.show()
            ColorHelper.styleAlertDialog(dpd, this)
        }

        findViewById<Button>(R.id.btnEndDate).setOnClickListener {
            val cal = customEndDate ?: Calendar.getInstance()
            val dpd = android.app.DatePickerDialog(this, { _, year, month, day ->
                customEndDate = Calendar.getInstance().apply { set(year, month, day, 23, 59, 59) }
                findViewById<Button>(R.id.btnEndDate).text = dateFormat.format(customEndDate!!.time)
                updateFilteredData(6)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dpd.show()
            ColorHelper.styleAlertDialog(dpd, this)
        }
    }

    private fun updateFilteredData(periodPosition: Int) {
        currentPeriodEnd = Long.MAX_VALUE

        when (periodPosition) {
            0 -> currentPeriodStart = 0
            1 -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -30)
                currentPeriodStart = cal.timeInMillis
            }
            2 -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                currentPeriodStart = cal.timeInMillis
            }
            3 -> {
                val calStart = Calendar.getInstance()
                calStart.add(Calendar.MONTH, -1)
                calStart.set(Calendar.DAY_OF_MONTH, 1)
                calStart.set(Calendar.HOUR_OF_DAY, 0)
                calStart.set(Calendar.MINUTE, 0)
                calStart.set(Calendar.SECOND, 0)
                currentPeriodStart = calStart.timeInMillis
                
                val calEnd = Calendar.getInstance()
                calEnd.set(Calendar.DAY_OF_MONTH, 1)
                calEnd.set(Calendar.HOUR_OF_DAY, 0)
                calEnd.set(Calendar.MINUTE, 0)
                calEnd.set(Calendar.SECOND, 0)
                calEnd.add(Calendar.MILLISECOND, -1)
                currentPeriodEnd = calEnd.timeInMillis
            }
            4 -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -7)
                currentPeriodStart = cal.timeInMillis
            }
            5 -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                currentPeriodStart = cal.timeInMillis
            }
            6 -> {
                currentPeriodStart = customStartDate?.timeInMillis ?: 0L
                currentPeriodEnd = customEndDate?.timeInMillis ?: Long.MAX_VALUE
            }
            else -> currentPeriodStart = 0
        }

        filterAndRender()
    }

    private fun filterAndRender() {
        filteredSessions = allSessions.filter { session ->
            val start = session.startTime
            start >= currentPeriodStart && start <= currentPeriodEnd
            val end = session.endTime ?: System.currentTimeMillis()
            end >= currentPeriodStart && start <= currentPeriodEnd
        }
        renderAll()
    }

    private fun applyColors() {
        ColorHelper.applySettings(this)
        val textColor = ColorHelper.getTextColor(this)
        
        findViewById<TextView>(R.id.labelStatsPeriod).setTextColor(textColor)
        findViewById<TextView>(R.id.statsMiniTimelineTitle).setTextColor(textColor)
        findViewById<TextView>(R.id.statsMostSwitchTitle).setTextColor(textColor)
        findViewById<TextView>(R.id.statsTotalTitle).setTextColor(textColor)
        findViewById<TextView>(R.id.statsCoTitle).setTextColor(textColor)
        findViewById<TextView>(R.id.statsFreqTitle).setTextColor(textColor)
        findViewById<TextView>(R.id.statsDensityTitle).setTextColor(textColor)
        findViewById<TextView>(R.id.statsHourAxis).setTextColor(textColor)
        
        findViewById<Button>(R.id.btnStartDate).setTextColor(textColor)
        findViewById<Button>(R.id.btnEndDate).setTextColor(textColor)
        findViewById<Spinner>(R.id.spinnerStatsPeriod).let { sp ->
            (sp.selectedView as? TextView)?.setTextColor(textColor)
        }
        findViewById<TimelineChartView>(R.id.miniTimelineChart).updateTextColor()
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val sessionsJson = sharedPref.getString("sessions_list", "[]")
        val peopleJson = sharedPref.getString("people_list", "[]")

        val gson = Gson()
        val rawPeople: List<Person> = gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type)
        val rawSessions: List<FrontSession> = gson.fromJson(sessionsJson, object : TypeToken<List<FrontSession>>() {}.type)
        
        val excludedIds = rawPeople.filter { it.excludeFromStats || it.isArchived || it.isSysmediaOnly }.map { it.id }.toSet()
        val excludedNames = rawPeople.filter { it.excludeFromStats || it.isArchived || it.isSysmediaOnly }.map { it.name }.toSet()

        people = rawPeople.filter { !it.excludeFromStats && !it.isArchived && !it.isSysmediaOnly }
        allSessions = rawSessions.filter { 
            val pId = it.personId
            if (pId != null) {
                !excludedIds.contains(pId)
            } else {
                !excludedNames.contains(it.personName)
            }
        }
        filteredSessions = allSessions
        
        runOnUiThread {
            renderAll()
        }
    }

    private fun renderAll() {
        renderTotalHours()
        renderCoFronting()
        renderMiniTimelineSection()
        renderMostSwitchingMembers()
        renderFrontDensityChart()
        renderMemberSwitchChart()
    }

    private fun renderMiniTimelineSection() {
        setupMiniTimelineLast24h()
    }

    private fun renderMostSwitchingMembers() {
        val container = findViewById<LinearLayout>(R.id.containerMostSwitching)
        container.removeAllViews()

        val counts = filteredSessions.groupingBy { it.personId ?: it.personName }.eachCount()
        val sorted = counts.toList().sortedByDescending { it.second }.take(10)

        if (sorted.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.no_activities_found)
                setTextColor(ColorHelper.getTextColor(this@StatisticsActivity))
            }
            container.addView(tv)
            return
        }

        val maxVal = sorted.first().second.toFloat()

        sorted.forEach { (idOrName, count) ->
            val person = people.find { it.id == idOrName || it.name == idOrName }
            val name = person?.name ?: idOrName

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16.dpToPx())
            }

            val label = TextView(this).apply {
                text = "$name: $count switches"
                setTextColor(ColorHelper.getTextColor(this@StatisticsActivity))
                textSize = 14f
            }
            row.addView(label)

            val bar = View(this).apply {
                val weight = count / maxVal
                layoutParams = LinearLayout.LayoutParams(0, 8.dpToPx(), weight).apply {
                    topMargin = 4.dpToPx()
                }
                setBackgroundColor(person?.profileColor ?: Color.GRAY)
            }
            
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(bar)
                addView(View(this@StatisticsActivity).apply { 
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f - (count / maxVal))
                })
            }
            row.addView(barContainer)
            container.addView(row)
        }
    }

    private fun renderTotalHours() {
        val container = findViewById<LinearLayout>(R.id.containerTotalHours)
        container.removeAllViews()

        val durations = mutableMapOf<String, Long>()
        filteredSessions.forEach { s ->
            val start = s.startTime
            val end = s.endTime ?: System.currentTimeMillis()
            val duration = (end - start).coerceAtLeast(0)
            val key = s.personId ?: s.personName
            durations[key] = (durations[key] ?: 0L) + duration
        }

        val sorted = durations.toList().sortedByDescending { it.second }

        if (sorted.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.no_activities_found)
                setTextColor(ColorHelper.getTextColor(this@StatisticsActivity))
            }
            container.addView(tv)
            return
        }

        val maxVal = sorted.first().second.toFloat()

        sorted.forEach { (idOrName, durationMs) ->
            val person = people.find { it.id == idOrName || it.name == idOrName }
            val name = person?.name ?: idOrName

            val hours = (durationMs / (1000 * 60 * 60)).toInt()
            val minutes = ((durationMs / (1000 * 60)) % 60).toInt()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16.dpToPx())
            }

            val label = TextView(this).apply {
                text = getString(R.string.stats_person_duration, name, hours, minutes)
                setTextColor(ColorHelper.getTextColor(this@StatisticsActivity))
                textSize = 14f
            }
            row.addView(label)

            val bar = View(this).apply {
                val weight = durationMs / maxVal
                layoutParams = LinearLayout.LayoutParams(0, 8.dpToPx(), weight).apply {
                    topMargin = 4.dpToPx()
                }
                setBackgroundColor(person?.profileColor ?: Color.GRAY)
            }
            
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(bar)
                addView(View(this@StatisticsActivity).apply { 
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f - (durationMs / maxVal))
                })
            }
            row.addView(barContainer)
            container.addView(row)
        }
    }

    private fun renderCoFronting() {
        val counts = computeCoFrontingCounts(filteredSessions, currentPeriodStart, currentPeriodEnd)
        val graph = findViewById<CoFrontingGraphView>(R.id.coFrontingGraph)
        graph.setData(people, counts)
        
        val container = findViewById<LinearLayout>(R.id.containerCoFronting)
        container.removeAllViews()

        if (counts.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.stats_no_cofronting_data)
                setTextColor(ColorHelper.getTextColor(this@StatisticsActivity))
            })
        } else {
            counts.toList().sortedByDescending { it.second }.forEach { (pair, count) ->
                val p1 = people.find { it.id == pair.first }?.name ?: pair.first
                val p2 = people.find { it.id == pair.second }?.name ?: pair.second
                
                val tv = TextView(this).apply {
                    text = getString(R.string.stats_cofronting_pair, p1, p2, count)
                    setTextColor(ColorHelper.getTextColor(this@StatisticsActivity))
                    setPadding(0, 0, 0, 4.dpToPx())
                }
                container.addView(tv)
            }
        }
    }

    data class SessionInterval(val name: String, val start: Long, val end: Long)

    private fun computeCoFrontingCounts(sessions: List<FrontSession>, startRange: Long, endRange: Long): Map<Pair<String, String>, Int> {
        val personIntervals = sessions.map { s ->
            val start = s.startTime.coerceAtLeast(startRange)
            val end = (s.endTime ?: System.currentTimeMillis()).coerceAtMost(endRange)
            SessionInterval(s.personId ?: s.personName, start, end)
        }.filter { it.start < it.end }.sortedBy { it.start }

        val coFrontCount = mutableMapOf<Pair<String, String>, Int>()
        val active = mutableListOf<SessionInterval>()

        fun removeEnded(time: Long) {
            active.removeAll { it.end <= time }
        }

        for (session in personIntervals) {
            removeEnded(session.start)
            for (other in active) {
                if (other.name != session.name) {
                    val pair = if (session.name < other.name) session.name to other.name else other.name to session.name
                    coFrontCount[pair] = (coFrontCount[pair] ?: 0) + 1
                }
            }
            active.add(session)
        }
        return coFrontCount
    }

    private fun renderMemberSwitchChart() {
        val chart = findViewById<MemberSwitchChartView>(R.id.memberSwitchChart)
        chart.setData(filteredSessions, people)
    }

    private fun renderFrontDensityChart() {
        val chart = findViewById<FrontDensityChartView>(R.id.frontDensityChart)
        chart.setData(filteredSessions, people, currentPeriodStart, currentPeriodEnd)
        chart.setHighlight(highlightedDensityMemberId)

        val activeMembers = chart.getActiveMembers()

        val legendContainer = findViewById<LinearLayout>(R.id.containerDensityLegend)
        legendContainer.removeAllViews()

        activeMembers.forEach { member ->
            val isHighlighted = highlightedDensityMemberId == member.id
            
            val legendItem = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                isClickable = true
                isFocusable = true
                val backgroundResource = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, backgroundResource, true)
                setBackgroundResource(backgroundResource.resourceId)
                
                setOnClickListener {
                    highlightedDensityMemberId = if (highlightedDensityMemberId == member.id) null else member.id
                    renderFrontDensityChart()
                }
            }

            val colorDot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(12.dpToPx(), 12.dpToPx())
                setBackgroundColor(if (highlightedDensityMemberId != null && !isHighlighted) Color.LTGRAY else member.color)
            }
            
            val nameTv = TextView(this).apply {
                text = member.name
                textSize = 11f
                setPadding(6.dpToPx(), 0, 0, 0)
                setTextColor(ColorHelper.getTextColor(this@StatisticsActivity))
                if (highlightedDensityMemberId != null && !isHighlighted) {
                    alpha = 0.5f
                } else if (isHighlighted) {
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }

            legendItem.addView(colorDot)
            legendItem.addView(nameTv)
            legendContainer.addView(legendItem)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
