package com.interli.plural

import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TimelineVisualActivity : BaseActivity() {

    private lateinit var sessions: MutableList<FrontSession>
    private lateinit var people: List<Person>
    private lateinit var chart: TimelineChartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timeline_visual)
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)

        val allPeople = MemberHelper.loadAllPeople(this)
        people = allPeople

        val displayPeople = allPeople.filter { !it.isArchived && !it.isSysmediaOnly }

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
        chart.setData(sessions, displayPeople)
        chart.onSessionClicked = { session ->
            DialogHelper.showSessionDetailsDialog(this, session, people, sessions) {
                val sJson = sharedPref.getString("sessions_list", "[]")
                val newSessions: MutableList<FrontSession> = Gson().fromJson(sJson, object : TypeToken<MutableList<FrontSession>>() {}.type)
                sessions.clear()
                sessions.addAll(newSessions)
                chart.setData(sessions, displayPeople)
            }
        }

        setupNavigationDrawer()
        ColorHelper.applySettings(this)
        chart.updateTextColor()
    }

}
