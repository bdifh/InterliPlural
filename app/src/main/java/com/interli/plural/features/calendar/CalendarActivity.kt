package com.interli.plural.features.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.CalendarEvent
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.DialogHelper
import com.interli.plural.core.NotificationReceiver
import com.interli.plural.DiaryNote
import com.interli.plural.features.diary.DiaryActivity
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.features.member.ProfileActivity
import com.interli.plural.features.todo.TodoActivity
import com.interli.plural.Group
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.TodoList
import java.text.SimpleDateFormat
import java.util.*

class CalendarActivity : BaseActivity() {
    private var events = mutableListOf<CalendarEvent>()
    private var people = mutableListOf<Person>()
    private var allNotes = mutableListOf<DiaryNote>()
    private var allTodoLists = mutableListOf<TodoList>()
    private var currentViewMode = 0 // 0: Agenda, 1: Day, 2: Week, 3: Month, 4: Year
    private var selectedDate = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)
        loadData()
        setupNavigationDrawer()
        setupUI()
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = sharedPref.getString("calendar_events", "[]") ?: "[]"
        events = try { Gson().fromJson(json, object : TypeToken<MutableList<CalendarEvent>>() {}.type) } catch (_: Exception) { mutableListOf() }
        people = MemberHelper.loadAllPeople(this)
        allNotes = try { Gson().fromJson(sharedPref.getString("diary_notes", "[]"), object : TypeToken<MutableList<DiaryNote>>() {}.type) } catch (_: Exception) { mutableListOf() }
        allTodoLists = try { Gson().fromJson(sharedPref.getString("todo_lists", "[]"), object : TypeToken<MutableList<TodoList>>() {}.type) } catch (_: Exception) { mutableListOf() }
    }

    private fun saveData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit().putString("calendar_events", Gson().toJson(events)).apply()
    }

    private fun scheduleCalendarAlarm(event: CalendarEvent) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationReceiver::class.java).apply {
            action = "CALENDAR_REMINDER"
            putExtra("event_id", event.id)
            putExtra("event_title", event.title)
        }
        val pendingIntent = PendingIntent.getBroadcast(this, event.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        if (event.reminderTime != null && event.reminderTime!! > System.currentTimeMillis()) {
            val receiver = NotificationReceiver()
            receiver.scheduleAlarm(alarmManager, event.reminderTime!!, pendingIntent)
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun setupUI() {
        findViewById<View>(R.id.btnPrev).setOnClickListener { navigatePeriod(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { navigatePeriod(1) }
        findViewById<View>(R.id.btnToday).setOnClickListener { selectedDate = Calendar.getInstance(); updateCalendarView() }
        val tabLayout = findViewById<TabLayout>(R.id.calendarTabLayout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { currentViewMode = tab?.position ?: 0; updateCalendarView() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        findViewById<View>(R.id.fabAddEvent).setOnClickListener { showEditEventDialog(null) }
        updateCalendarView()
    }

    private fun navigatePeriod(direction: Int) {
        when (currentViewMode) {
            0, 1 -> selectedDate.add(Calendar.DAY_OF_YEAR, direction)
            2 -> selectedDate.add(Calendar.WEEK_OF_YEAR, direction)
            3 -> selectedDate.add(Calendar.MONTH, direction)
            4 -> selectedDate.add(Calendar.YEAR, direction)
        }
        updateCalendarView()
    }

    private fun updateCalendarView() {
        val container = findViewById<LinearLayout>(R.id.calendarContainer)
        val scrollView = findViewById<ScrollView>(R.id.calendarScrollView)
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.calendarRecyclerView)
        container.removeAllViews()
        val bgColor = ColorHelper.getBgColor(this)
        container.setBackgroundColor(bgColor)
        scrollView?.setBackgroundColor(bgColor)
        recyclerView?.setBackgroundColor(bgColor)
        updatePeriodText()
        if (currentViewMode == 0) {
            scrollView?.visibility = View.GONE; recyclerView?.visibility = View.VISIBLE
            renderAgendaView(recyclerView)
        } else {
            scrollView?.visibility = View.VISIBLE; recyclerView?.visibility = View.GONE
            when (currentViewMode) {
                1 -> renderDayView(container)
                2 -> renderWeekView(container)
                3 -> renderMonthView(container)
                4 -> renderYearView(container)
            }
        }
    }

    private fun updatePeriodText() {
        val tv = findViewById<TextView>(R.id.tvCurrentPeriod)
        val sdf = when (currentViewMode) {
            0, 1 -> SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            2 -> SimpleDateFormat("'Week' w, yyyy", Locale.getDefault())
            3 -> SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            else -> SimpleDateFormat("yyyy", Locale.getDefault())
        }
        tv.text = sdf.format(selectedDate.time)
        tv.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun showEditEventDialog(event: CalendarEvent?) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_calendar_event, null)
        val etTitle = view.findViewById<EditText>(R.id.etEventTitle)
        val etLocation = view.findViewById<EditText>(R.id.etEventLocation)
        val etDesc = view.findViewById<EditText>(R.id.etEventDescription)
        val btnStartDate = view.findViewById<Button>(R.id.btnEventStartDate)
        val btnStartTime = view.findViewById<Button>(R.id.btnEventStartTime)
        val btnEndDate = view.findViewById<Button>(R.id.btnEventEndDate)
        val btnEndTime = view.findViewById<Button>(R.id.btnEventEndTime)

        var startTime = event?.startTime ?: System.currentTimeMillis()
        var endTime = event?.endTime ?: (System.currentTimeMillis() + 3600000)
        var selectedReminderTime = event?.reminderTime

        val updateTimes = {
            val sdfD = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val sdfT = SimpleDateFormat("HH:mm", Locale.getDefault())
            btnStartDate.text = sdfD.format(Date(startTime))
            btnStartTime.text = sdfT.format(Date(startTime))
            btnEndDate.text = sdfD.format(Date(endTime))
            btnEndTime.text = sdfT.format(Date(endTime))
        }
        updateTimes()

        val btnReminder = Button(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = if (selectedReminderTime == null) "No Reminder" else "Reminder: " + SimpleDateFormat("dd/MM HH:mm").format(Date(selectedReminderTime!!))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 }
            setOnClickListener {
                showDateTimePicker(selectedReminderTime ?: startTime) {
                    selectedReminderTime = it
                    text = "Reminder: " + SimpleDateFormat("dd/MM HH:mm").format(Date(it))
                }
            }
        }
        (view.findViewById<LinearLayout>(R.id.layoutHideIn).parent as LinearLayout).addView(btnReminder, 8)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (event == null) R.string.add_event else R.string.edit_event)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    val e = event ?: CalendarEvent(title = title, startTime = startTime, endTime = endTime).also { events.add(it) }
                    e.title = title
                    e.description = etDesc.text.toString()
                    e.location = etLocation.text.toString()
                    e.startTime = startTime
                    e.endTime = endTime
                    e.reminderTime = selectedReminderTime
                    e.isAllDay = view.findViewById<CheckBox>(R.id.cbAllDay).isChecked
                    saveData()
                    scheduleCalendarAlarm(e)
                    updateCalendarView()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        if (event != null) {
            etTitle.setText(event.title); etLocation.setText(event.location); etDesc.setText(event.description)
            view.findViewById<CheckBox>(R.id.cbHideAgenda).isChecked = event.hideInOverview
        }

        btnStartDate.setOnClickListener { showDatePicker(startTime) { startTime = it; updateTimes() } }
        btnStartTime.setOnClickListener { showTimePicker(startTime) { startTime = it; updateTimes() } }
        btnEndDate.setOnClickListener { showDatePicker(endTime) { endTime = it; updateTimes() } }
        btnEndTime.setOnClickListener { showTimePicker(endTime) { endTime = it; updateTimes() } }

        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun renderAgendaView(rv: RecyclerView?) {
        val startOfToday = selectedDate.clone() as Calendar
        startOfToday.set(Calendar.HOUR_OF_DAY, 0); startOfToday.set(Calendar.MINUTE, 0); startOfToday.set(Calendar.SECOND, 0)
        val rangeEnd = startOfToday.timeInMillis + (60L * 24 * 3600 * 1000)
        val upcomingEvents = getExpandedEvents(startOfToday.timeInMillis, rangeEnd).filter { !it.hideInOverview }
        val agendaItems = mutableListOf<CalendarAgendaAdapter.AgendaItem>()
        var lastDateStr = ""
        val dateSdf = SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault())
        upcomingEvents.forEach { event ->
            val eventDateStr = dateSdf.format(Date(event.startTime))
            if (eventDateStr != lastDateStr) { agendaItems.add(CalendarAgendaAdapter.AgendaItem.Header(eventDateStr)); lastDateStr = eventDateStr }
            agendaItems.add(CalendarAgendaAdapter.AgendaItem.Event(event))
        }
        if (rv?.adapter == null) {
            rv?.layoutManager = LinearLayoutManager(this)
            rv?.adapter = CalendarAgendaAdapter(agendaItems, allNotes, allTodoLists, { showEditEventDialog(it) }, {}, {})
        } else (rv.adapter as? CalendarAgendaAdapter)?.updateData(agendaItems)
    }

    private fun renderDayView(container: LinearLayout) { /*...*/ }
    private fun renderWeekView(container: LinearLayout) { /*...*/ }
    private fun renderMonthView(container: LinearLayout) { /*...*/ }
    private fun renderYearView(container: LinearLayout) { /*...*/ }

    private fun getExpandedEvents(start: Long, end: Long): List<CalendarEvent> {
        return events.filter { it.endTime >= start && it.startTime <= end }.sortedBy { it.startTime }
    }

    private fun showDateTimePicker(initialTime: Long, onTimeSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialTime }
        android.app.DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            android.app.TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                onTimeSelected(cal.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showDatePicker(initialTime: Long, onDateSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialTime }
        android.app.DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d); onDateSelected(cal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(initialTime: Long, onTimeSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialTime }
        android.app.TimePickerDialog(this, { _, h, min ->
            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min); onTimeSelected(cal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }
}