package com.interli.plural

import android.os.Bundle
import android.widget.Button
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MoodMonthActivity : BaseActivity() {

    private var allEntries: List<MoodActivity.MoodEntry> = emptyList()
    private var startDate: Long? = null
    private var endDate: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_month)

        ColorHelper.applySettings(this)

        setupNavigationDrawer()
        val btnStart = findViewById<Button>(R.id.btnMonthStartDate)
        val btnEnd = findViewById<Button>(R.id.btnMonthEndDate)

        btnStart.setOnClickListener { showDatePicker(true) }
        btnEnd.setOnClickListener { showDatePicker(false) }

        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("mood_entries", "[]") ?: "[]"
        val type = object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type
        val rawEntries: List<MoodActivity.MoodEntry> = try { Gson().fromJson(json, type) } catch (_: Exception) { emptyList() }

        val peopleJson = prefs.getString("people_list", "[]") ?: "[]"
        val allPeople: List<Person> = Gson().fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type)
        val excludedIds = allPeople.filter { it.excludeFromStats || it.isArchived || it.isSysmediaOnly }.map { it.id }.toSet()

        allEntries = rawEntries.filter { entry ->
            if (entry.memberIds.isEmpty()) true
            else entry.memberIds.any { !excludedIds.contains(it) }
        }

        updateChart()
    }

    override fun onResume() {
        super.onResume()
        updateChart()
    }

    private fun showDatePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        if (isStart && startDate != null) cal.timeInMillis = startDate!!
        if (!isStart && endDate != null) cal.timeInMillis = endDate!!

        val dialog = android.app.DatePickerDialog(this, { _, y, m, d ->
            val result = Calendar.getInstance()
            result.set(y, m, d, 0, 0, 0)
            result.set(Calendar.MILLISECOND, 0)
            
            if (isStart) {
                startDate = result.timeInMillis
                findViewById<Button>(R.id.btnMonthStartDate).text = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(result.time)
            } else {
                result.set(Calendar.HOUR_OF_DAY, 23)
                result.set(Calendar.MINUTE, 59)
                endDate = result.timeInMillis
                findViewById<Button>(R.id.btnMonthEndDate).text = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(result.time)
            }
            updateChart()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun updateChart() {
        val chart = findViewById<MoodChartView>(R.id.moodMonthChart)
        chart.setRange(startDate, endDate)
        chart.setData(allEntries, MoodChartView.Mode.DAILY_AVERAGE_MONTH)
    }
}
