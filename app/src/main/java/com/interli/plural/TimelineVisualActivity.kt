package com.interli.plural

import android.os.Bundle
import android.widget.Button
import androidx.core.view.WindowInsetsControllerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TimelineVisualActivity : BaseActivity() {

    private lateinit var sessions: MutableList<FrontSession>
    private lateinit var people: List<Person>
    private lateinit var chart: TimelineChartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timeline_visual)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true

        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        
        val peopleJson = sharedPref.getString("people_list", "[]")
        val allPeople: List<Person> = Gson().fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type)
        people = allPeople.filter { !it.isArchived && !it.isSysmediaOnly }

        val sessionsJson = sharedPref.getString("sessions_list", "[]")
        val allSessions: List<FrontSession> = Gson().fromJson(sessionsJson, object : TypeToken<MutableList<FrontSession>>() {}.type)
        
        val excludedIds = allPeople.filter { it.isArchived || it.isSysmediaOnly }.map { it.id }.toSet()
        val excludedNames = allPeople.filter { it.isArchived || it.isSysmediaOnly }.map { it.name }.toSet()
        
        sessions = allSessions.filter { 
            val pId = it.personId
            if (pId != null) !excludedIds.contains(pId)
            else !excludedNames.contains(it.personName)
        }.toMutableList()

        chart = findViewById(R.id.timelineChart)
        chart.setData(sessions, people)
        chart.updateTextColor()
        chart.onSessionClicked = { session ->
            DialogHelper.showSessionDetailsDialog(this, session, people, sessions) {
                val sJson = sharedPref.getString("sessions_list", "[]")
                val newSessions: MutableList<FrontSession> = Gson().fromJson(sJson, object : TypeToken<MutableList<FrontSession>>() {}.type)
                sessions.clear()
                sessions.addAll(newSessions)

                chart.setData(sessions, people)
            }
        }

        setupNavigationDrawer()
        ColorHelper.applySettings(this)
        chart.updateTextColor()
    }

}
