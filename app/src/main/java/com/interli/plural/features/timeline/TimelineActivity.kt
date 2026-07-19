package com.interli.plural.features.timeline

import android.os.Bundle
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.DialogHelper
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.features.timeline.TimelineAdapter
import com.interli.plural.features.timeline.TimelineVisualActivity
import com.interli.plural.FrontSession
import com.interli.plural.Person
import com.interli.plural.R

class TimelineActivity : BaseActivity() {
    private lateinit var sessions: MutableList<FrontSession>
    private lateinit var people: List<Person>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timeline)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        loadData()
        val recyclerView = findViewById<RecyclerView>(R.id.timelineRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = TimelineAdapter(sessions) { session ->
            DialogHelper.showSessionDetailsDialog(this, session, people, sessions) {
                loadData()
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }
        findViewById<android.widget.Button>(R.id.btnGoVisual).setOnClickListener {
            val intent = android.content.Intent(this, TimelineVisualActivity::class.java)
            startActivity(intent)
        }
        setupNavigationDrawer()
        ColorHelper.applySettings(this)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val verversTimer = object : Runnable {
            override fun run() {
                recyclerView.adapter?.notifyDataSetChanged()
                handler.postDelayed(this, 60000)
            }
        }
        handler.postDelayed(verversTimer, 60000)
    }
    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val sessionsJson = sharedPref.getString("sessions_list", null)
        val peopleJson = sharedPref.getString("people_list", "[]")
        val allPeople = MemberHelper.loadAllPeople(this)
        people = allPeople
        val newSessions = if (sessionsJson != null) {
            val type = object : TypeToken<MutableList<FrontSession>>() {}.type
            val rawSessions: MutableList<FrontSession> = Gson().fromJson(sessionsJson, type) ?: mutableListOf()
            val excludedIds = allPeople.filter { it.isArchived || it.isSysmediaOnly }.map { it.id }.toSet()
            val excludedNames = allPeople.filter { it.isArchived || it.isSysmediaOnly }.map { it.name }.toSet()
            val filteredSessions = rawSessions.filter {
                val pId = it.personId
                if (pId != null) !excludedIds.contains(pId)
                else !excludedNames.contains(it.personName)
            }
            filteredSessions.asSequence().sortedWith { s1, s2 ->
                when {
                    s1.endTime == null && s2.endTime == null -> s2.startTime.compareTo(s1.startTime)
                    s1.endTime == null -> -1
                    s2.endTime == null -> 1
                    else -> s2.endTime!!.compareTo(s1.endTime!!)
                }
            }.toMutableList()
        } else {
            mutableListOf()
        }
        if (!::sessions.isInitialized) {
            sessions = newSessions
        } else {
            sessions.clear()
            sessions.addAll(newSessions)
        }
    }
    override fun onResume() {
        super.onResume()
        ColorHelper.applySettings(this)
    }
}
