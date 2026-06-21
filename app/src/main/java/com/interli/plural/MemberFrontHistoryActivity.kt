package com.interli.plural

import android.os.Bundle
import android.view.View
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class MemberFrontHistoryActivity : BaseActivity() {

    private lateinit var personId: String
    private lateinit var person: Person
    private lateinit var allSessions: List<FrontSession>
    private lateinit var filteredSessions: List<FrontSession>
    
    private lateinit var spinnerPeriod: Spinner
    private lateinit var dotCalendarView: MemberFrontDotCalendarView
    private lateinit var hourlyChartView: FrontDensityChartView
    private lateinit var tvAvgTime: TextView
    private lateinit var tvLongestSession: TextView
    private lateinit var tvTotalSessions: TextView
    private lateinit var tvMostActiveDay: TextView
    private lateinit var rvTimeline: androidx.recyclerview.widget.RecyclerView
    private lateinit var people: List<Person>
    
    private var customStartDate: Calendar? = null
    private var customEndDate: Calendar? = null
    private val dateDisplayFormat = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_member_front_history)

        personId = intent.getStringExtra("person_id") ?: ""
        loadData()

        if (personId.isEmpty()) { finish(); return }

        findViewById<View>(R.id.topAppBar).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvTitle).text = person.name

        spinnerPeriod = findViewById(R.id.spinnerPeriod)
        dotCalendarView = findViewById(R.id.dotCalendarView)
        hourlyChartView = findViewById(R.id.hourlyChartView)
        tvAvgTime = findViewById(R.id.tvAvgTime)
        tvLongestSession = findViewById(R.id.tvLongestSession)
        tvTotalSessions = findViewById(R.id.tvTotalSessions)
        tvMostActiveDay = findViewById(R.id.tvMostActiveDay)
        rvTimeline = findViewById(R.id.timelineRecyclerView)

        setupPeriodSpinner()
        
        dotCalendarView.setData(allSessions, personId, person.profileColor)
        dotCalendarView.onDayClicked = { sessions ->
            showDaySessionsDialog(sessions)
        }
        
        setupDatePickers()
        renderTimeline()
        updateStats(0) // Default to Day (or All depending on implementation)
        
        ColorHelper.applySettings(this)
        applyColors()
    }

    private fun setupPeriodSpinner() {
        val periods = arrayOf(
            getString(R.string.period_all_time),
            getString(R.string.today),
            getString(R.string.period_last_7_days),
            getString(R.string.period_current_month),
            getString(R.string.period_last_month),
            getString(R.string.period_custom)
        )
        
        val adapter = ColorHelper.createThemedAdapter(this, periods.toList())
        spinnerPeriod.adapter = adapter
        
        spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                findViewById<View>(R.id.layoutCustomRange).visibility = if (position == 5) View.VISIBLE else View.GONE
                updateStats(position)
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
                val newCal = Calendar.getInstance()
                newCal.set(y, m, d, 0, 0, 0); newCal.set(Calendar.MILLISECOND, 0)
                customStartDate = newCal
                btnStart.text = dateDisplayFormat.format(newCal.time)
                updateStats(5)
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
                btnEnd.text = dateDisplayFormat.format(newCal.time)
                updateStats(5)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dialog.show()
            ColorHelper.styleAlertDialog(dialog, this)
        }
    }

    private fun showDaySessionsDialog(sessions: List<FrontSession>) {
        val dateFormatDetail = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val message = StringBuilder()
        val sessieLabel = if (Locale.getDefault().language == "nl") "Sessie" else "Session"

        sessions.sortedBy { it.startTime }.forEachIndexed { index, session ->
            if (sessions.size > 1) message.append("$sessieLabel ${index + 1}:\n")
            
            val start = dateFormatDetail.format(Date(session.startTime))
            val end = session.endTime?.let { dateFormatDetail.format(Date(it)) } ?: getString(R.string.currently_active)
            
            message.append("▶ $start\n◀ $end\n")
            if (!session.note.isNullOrBlank()) {
                message.append("📝 ${session.note}\n")
            }
            message.append("\n")
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.front_history_timeline))
            .setMessage(message.toString().trim())
            .setPositiveButton("OK", null)
            .create()
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun updateStats(position: Int) {
        val now = Calendar.getInstance()
        val startCal = Calendar.getInstance()
        
        when (position) {
            0 -> startCal.set(1970, 0, 1) // All time
            1 -> { // Today
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
            }
            2 -> startCal.add(Calendar.DAY_OF_YEAR, -7)
            3 -> startCal.set(Calendar.DAY_OF_MONTH, 1)
            4 -> {
                startCal.add(Calendar.MONTH, -1)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
            }
            5 -> {
                startCal.timeInMillis = customStartDate?.timeInMillis ?: 0L
            }
        }
        
        val startTime = if (position == 0) 0L else startCal.timeInMillis
        val endTime = when (position) {
            4 -> {
                val end = startCal.clone() as Calendar
                end.add(Calendar.MONTH, 1)
                end.set(Calendar.DAY_OF_MONTH, 1)
                end.timeInMillis
            }
            5 -> customEndDate?.timeInMillis ?: now.timeInMillis
            else -> now.timeInMillis
        }

        filteredSessions = allSessions.filter { 
            it.personId == personId && (it.endTime ?: now.timeInMillis) >= (if (position == 0) 0L else startTime) && it.startTime <= endTime
        }

        hourlyChartView.setData(filteredSessions, listOf(person), startTime, endTime)
        
        // Compute Stats
        if (filteredSessions.isEmpty()) {
            tvAvgTime.text = "0h 0m"
            tvLongestSession.text = "0h 0m"
            tvTotalSessions.text = "0"
            tvMostActiveDay.text = "-"
            return
        }

        var totalMs = 0L
        var maxMs = 0L
        val dayCounts = IntArray(7) // Mon=0

        filteredSessions.forEach { s ->
            val sEnd = s.endTime ?: now.timeInMillis
            val effectiveStart = maxOf(s.startTime, startTime)
            val effectiveEnd = minOf(sEnd, endTime)
            val duration = effectiveEnd - effectiveStart
            if (duration > 0) {
                totalMs += duration
                if (duration > maxMs) maxMs = duration
                
                val cal = Calendar.getInstance()
                cal.timeInMillis = s.startTime
                val day = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                dayCounts[day]++
            }
        }

        val avgMs = totalMs / filteredSessions.size
        tvAvgTime.text = formatDuration(avgMs)
        tvLongestSession.text = formatDuration(maxMs)
        tvTotalSessions.text = filteredSessions.size.toString()
        
        val maxDayIdx = dayCounts.indices.maxByOrNull { dayCounts[it] } ?: 0
        val dayNames = arrayOf(
            getString(R.string.monday), getString(R.string.tuesday), getString(R.string.wednesday),
            getString(R.string.thursday), getString(R.string.friday), getString(R.string.saturday),
            getString(R.string.sunday)
        )
        tvMostActiveDay.text = dayNames[maxDayIdx]
    }

    private fun formatDuration(ms: Long): String {
        val hours = ms / 3600000
        val mins = (ms % 3600000) / 60000
        return "${hours}h ${mins}m"
    }

    private fun renderTimeline() {
        val sessions = allSessions.filter { it.personId == personId }.sortedByDescending { it.startTime }
        
        rvTimeline.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvTimeline.adapter = TimelineAdapter(sessions) { session ->
            DialogHelper.showSessionDetailsDialog(this, session, people, allSessions.toMutableList()) {
                loadData()
                renderTimeline()
            }
        }
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val peopleJson = sharedPref.getString("people_list", "[]")
        val peopleList: List<Person> = Gson().fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        people = peopleList
        person = peopleList.find { it.id == personId } ?: return

        val sessionsJson = sharedPref.getString("sessions_list", "[]")
        allSessions = Gson().fromJson(sessionsJson, object : TypeToken<List<FrontSession>>() {}.type) ?: emptyList()
    }

    private fun applyColors() {
        val textColor = ColorHelper.getTextColor(this)
        findViewById<TextView>(R.id.tvTitle).setTextColor(textColor)
        // ... apply to other static labels if needed, though ColorHelper.applySettings does most
    }
}
