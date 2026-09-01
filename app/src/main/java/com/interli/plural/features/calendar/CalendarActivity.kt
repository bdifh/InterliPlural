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
    private var isDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)
        loadData()
        setupNavigationDrawer()
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        loadData()
        updateCalendarView()
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
        com.interli.plural.widgets.CalendarDayWidgetProvider.sendRefreshBroadcast(this)
        com.interli.plural.widgets.CalendarMonthWidgetProvider.sendRefreshBroadcast(this)
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
        if (isDialogShowing) return
        isDialogShowing = true
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
        var selectedColor = event?.color
        val selectedMemberIds = event?.linkedMemberIds?.toMutableList() ?: mutableListOf()
        var selectedNoteId = event?.linkedNoteId
        var selectedTodoListId = event?.linkedTodoListId
        var selectedRecurrence = event?.recurrence
        var selectedRecurrenceDays = event?.recurrenceDays?.toMutableList()

        val btnRecurrence = view.findViewById<Button>(R.id.btnEventRecurrence)
        val updateRecurrenceUI = {
            btnRecurrence.text = when (selectedRecurrence) {
                "DAILY" -> getString(R.string.recurrence_daily)
                "WEEKLY" -> getString(R.string.recurrence_weekly)
                "MONTHLY" -> getString(R.string.recurrence_monthly)
                "YEARLY" -> getString(R.string.recurrence_yearly)
                "CUSTOM" -> {
                    val days = selectedRecurrenceDays ?: emptyList()
                    if (days.isEmpty()) getString(R.string.recurrence_custom)
                    else days.joinToString(",") { d ->
                        when(d) { 1-> "Ma"; 2-> "Di"; 3-> "Wo"; 4-> "Do"; 5-> "Vr"; 6-> "Za"; else -> "Zo" }
                    }
                }
                else -> getString(R.string.recurrence_none)
            }
        }
        updateRecurrenceUI()

        btnRecurrence.setOnClickListener {
            val options = arrayOf(
                getString(R.string.recurrence_none), getString(R.string.recurrence_daily),
                getString(R.string.recurrence_weekly), getString(R.string.recurrence_monthly),
                getString(R.string.recurrence_yearly), getString(R.string.recurrence_custom)
            )
            val values = arrayOf(null, "DAILY", "WEEKLY", "MONTHLY", "YEARLY", "CUSTOM")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.label_recurrence)
                .setItems(options) { _, which ->
                    val selected = values[which]
                    if (selected == "CUSTOM") {
                        showCustomRecurrenceDialog(selectedRecurrenceDays) { days ->
                            selectedRecurrence = if (days.isEmpty()) null else "CUSTOM"
                            selectedRecurrenceDays = days.toMutableList()
                            updateRecurrenceUI()
                        }
                    } else {
                        selectedRecurrence = selected
                        selectedRecurrenceDays = null
                        updateRecurrenceUI()
                    }
                }.show().also { ColorHelper.styleAlertDialog(it, this) }
        }

        val colorContainer = view.findViewById<LinearLayout>(R.id.layoutEventColors)
        DialogHelper.setupColorPicker(this, colorContainer, selectedColor) { color ->
            selectedColor = color
        }

        val tvMembers = view.findViewById<TextView>(R.id.tvLinkedMemberName)
        val tvNote = view.findViewById<TextView>(R.id.tvLinkedNoteTitle)
        val tvTodo = view.findViewById<TextView>(R.id.tvLinkedTodoTitle)

        fun updateLinkTexts() {
            tvMembers.text = if (selectedMemberIds.isEmpty()) "" else selectedMemberIds.mapNotNull { id -> people.find { it.id == id }?.name }.joinToString(", ")
            tvMembers.visibility = if (tvMembers.text.isEmpty()) View.GONE else View.VISIBLE

            tvNote.text = allNotes.find { it.id == selectedNoteId }?.title ?: ""
            tvNote.visibility = if (tvNote.text.isEmpty()) View.GONE else View.VISIBLE

            tvTodo.text = allTodoLists.find { it.id == selectedTodoListId }?.title ?: ""
            tvTodo.visibility = if (tvTodo.text.isEmpty()) View.GONE else View.VISIBLE
        }
        updateLinkTexts()

        view.findViewById<Button>(R.id.btnLinkMemberEvent).setOnClickListener {
            val groups = try {
                val json = getSharedPreferences("my_app", MODE_PRIVATE).getString("groups_list", "[]")
                Gson().fromJson<List<com.interli.plural.Group>>(json, object : TypeToken<List<com.interli.plural.Group>>() {}.type)
            } catch(_: Exception) { emptyList() }

            DialogHelper.showMemberSelectionDialog(this, getString(R.string.select_member), people, groups, selectedMemberIds) { ids ->
                selectedMemberIds.clear(); selectedMemberIds.addAll(ids); updateLinkTexts()
            }
        }

        view.findViewById<Button>(R.id.btnLinkNoteEvent).setOnClickListener {
            val titles = allNotes.map { it.title.ifEmpty { "Naamloze notitie" } }
            DialogHelper.showSearchableListDialog(this, getString(R.string.action_link_note), titles) { title ->
                selectedNoteId = allNotes.find { it.title == title || (it.title.isEmpty() && title == "Naamloze notitie") }?.id
                updateLinkTexts()
            }
        }

        view.findViewById<Button>(R.id.btnLinkTodoEvent).setOnClickListener {
            val titles = allTodoLists.map { it.title.ifEmpty { "To Do Lijst" } }
            DialogHelper.showSearchableListDialog(this, getString(R.string.action_link_todo), titles) { title ->
                selectedTodoListId = allTodoLists.find { it.title == title || (it.title.isEmpty() && title == "To Do Lijst") }?.id
                updateLinkTexts()
            }
        }

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
        (view.findViewById<View>(R.id.layoutHideIn).parent as LinearLayout).addView(btnReminder, 8)

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (event == null) R.string.add_event else R.string.edit_event)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    if (endTime <= startTime) endTime = startTime + 3600000
                    val applyChanges = { target: com.interli.plural.CalendarEvent ->
                        target.title = title
                        target.description = etDesc.text.toString()
                        target.location = etLocation.text.toString()
                        target.startTime = startTime
                        target.endTime = endTime
                        target.reminderTime = selectedReminderTime
                        target.color = selectedColor
                        target.linkedMemberIds = selectedMemberIds.toMutableList()
                        target.linkedNoteId = selectedNoteId
                        target.linkedTodoListId = selectedTodoListId
                        target.recurrence = selectedRecurrence
                        target.recurrenceDays = selectedRecurrenceDays
                        target.isAllDay = view.findViewById<CheckBox>(R.id.cbAllDay).isChecked
                        target.hideInOverview = view.findViewById<CheckBox>(R.id.cbHideAgenda).isChecked
                        target.hideInDay = view.findViewById<CheckBox>(R.id.cbHideDay).isChecked
                        target.hideInWeek = view.findViewById<CheckBox>(R.id.cbHideWeek).isChecked
                        target.hideInMonth = view.findViewById<CheckBox>(R.id.cbHideMonth).isChecked
                        target.hideInYear = view.findViewById<CheckBox>(R.id.cbHideYear).isChecked
                    }

                    val original = events.find { it.id == event?.id }
                    if (original != null && original.recurrence != null) {
                        val choices = arrayOf(getString(R.string.recurrence_edit_only_this), getString(R.string.recurrence_edit_this_and_future), getString(R.string.recurrence_edit_all))
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle(R.string.recurrence_edit_title)
                            .setItems(choices) { _, which ->
                                when (which) {
                                    0 -> {
                                        if (original.excludedDates == null) original.excludedDates = mutableListOf()
                                        original.excludedDates!!.add(event!!.startTime)

                                        selectedRecurrence = null
                                        selectedRecurrenceDays = null

                                        val newEvent = event!!.copy(id = java.util.UUID.randomUUID().toString())
                                        applyChanges(newEvent)
                                        events.add(newEvent)
                                        scheduleCalendarAlarm(newEvent)
                                    }
                                    1 -> {
                                        original.recurrenceUntil = event!!.startTime - 1000
                                        val newEvent = event!!.copy(id = java.util.UUID.randomUUID().toString())
                                        applyChanges(newEvent)
                                        events.add(newEvent)
                                        scheduleCalendarAlarm(newEvent)
                                        scheduleCalendarAlarm(original)
                                    }
                                    2 -> {
                                        val deltaStart = startTime - (event?.startTime ?: startTime)
                                        val deltaEnd = endTime - (event?.endTime ?: endTime)
                                        startTime = original.startTime + deltaStart
                                        endTime = original.endTime + deltaEnd

                                        applyChanges(original)
                                        scheduleCalendarAlarm(original)
                                    }
                                }
                                saveData(); updateCalendarView()
                            }.show().also { com.interli.plural.core.ColorHelper.styleAlertDialog(it, this) }
                    } else {
                        val e = original ?: com.interli.plural.CalendarEvent(title = title, startTime = startTime, endTime = endTime).also { events.add(it) }
                        applyChanges(e)
                        saveData(); scheduleCalendarAlarm(e); updateCalendarView()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { isDialogShowing = false }

        if (event != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                val original = events.find { it.id == event.id }
                if (original != null && original.recurrence != null) {
                    val choices = arrayOf(getString(R.string.recurrence_edit_only_this), getString(R.string.recurrence_edit_this_and_future), getString(R.string.recurrence_edit_all))
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.recurrence_delete_title)
                        .setItems(choices) { _, which ->
                            when (which) {
                                0 -> {
                                    if (original.excludedDates == null) original.excludedDates = mutableListOf()
                                    original.excludedDates!!.add(event.startTime)
                                }
                                1 -> {
                                    original.recurrenceUntil = event.startTime - 1000
                                    scheduleCalendarAlarm(original)
                                }
                                2 -> {
                                    events.remove(original)
                                    original.reminderTime = null
                                    scheduleCalendarAlarm(original)
                                }
                            }
                            saveData(); updateCalendarView()
                        }.show().also { com.interli.plural.core.ColorHelper.styleAlertDialog(it, this) }
                } else {
                    events.remove(event)
                    saveData()
                    event.reminderTime = null
                    scheduleCalendarAlarm(event)
                    updateCalendarView()
                }
            }
        }
        val dialog = builder.create()

        if (event != null) {
            etTitle.setText(event.title); etLocation.setText(event.location); etDesc.setText(event.description)
            view.findViewById<CheckBox>(R.id.cbHideAgenda).isChecked = event.hideInOverview
            view.findViewById<CheckBox>(R.id.cbHideDay).isChecked = event.hideInDay
            view.findViewById<CheckBox>(R.id.cbHideWeek).isChecked = event.hideInWeek
            view.findViewById<CheckBox>(R.id.cbHideMonth).isChecked = event.hideInMonth
            view.findViewById<CheckBox>(R.id.cbHideYear).isChecked = event.hideInYear
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
            rv?.adapter = CalendarAgendaAdapter(agendaItems, people, allNotes, allTodoLists, { showEditEventDialog(it) }, {}, {})
        } else (rv.adapter as? CalendarAgendaAdapter)?.updateData(agendaItems)
    }

    private fun renderDayView(container: LinearLayout) {
        container.removeAllViews()
        val view = CalendarTimelineView(this)
        container.addView(view)

        val startOfDay = selectedDate.clone() as Calendar
        startOfDay.set(Calendar.HOUR_OF_DAY, 0); startOfDay.set(Calendar.MINUTE, 0)
        startOfDay.set(Calendar.SECOND, 0); startOfDay.set(Calendar.MILLISECOND, 0)
        val endOfDay = startOfDay.timeInMillis + 24 * 3600 * 1000
        val dayEvents = getExpandedEvents(startOfDay.timeInMillis, endOfDay).filter { !it.hideInDay }

        view.setEvents(dayEvents, people, allNotes, allTodoLists, selectedDate, 1)
        view.onEventClicked = { showEditEventDialog(it) }
    }

    private fun renderWeekView(container: LinearLayout) {
        container.removeAllViews()
        val view = CalendarTimelineView(this)
        container.addView(view)

        val startOfWeek = selectedDate.clone() as Calendar
        startOfWeek.set(Calendar.DAY_OF_WEEK, startOfWeek.firstDayOfWeek)
        startOfWeek.set(Calendar.HOUR_OF_DAY, 0); startOfWeek.set(Calendar.MINUTE, 0)
        startOfWeek.set(Calendar.SECOND, 0); startOfWeek.set(Calendar.MILLISECOND, 0)
        val endOfWeek = startOfWeek.timeInMillis + 7L * 24 * 3600 * 1000
        val weekEvents = getExpandedEvents(startOfWeek.timeInMillis, endOfWeek).filter { !it.hideInWeek }

        view.setEvents(weekEvents, people, allNotes, allTodoLists, selectedDate, 7)
        view.onEventClicked = { showEditEventDialog(it) }
    }
    private fun renderMonthView(container: LinearLayout) {
        container.removeAllViews()
        val view = CalendarMonthView(this)
        container.addView(view)


        val startOfMonth = (selectedDate.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0) }
        val endOfMonth = (selectedDate.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val monthEvents = getExpandedEvents(startOfMonth.timeInMillis, endOfMonth.timeInMillis).filter { !it.hideInMonth }

        view.setEvents(monthEvents, people, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH))

        view.onDayClicked = { day ->
            selectedDate.set(Calendar.DAY_OF_MONTH, day)
            currentViewMode = 1
            findViewById<TabLayout>(R.id.calendarTabLayout).getTabAt(1)?.select()
            updateCalendarView()
        }
    }
    private fun renderYearView(container: LinearLayout) {
        container.removeAllViews()
        val view = CalendarYearView(this)
        container.addView(view)

        val yearEvents = events.filter { event -> !event.hideInYear }
        view.setEvents(yearEvents, people, selectedDate.get(Calendar.YEAR))

        view.onDayClicked = { date ->
            selectedDate.time = date
            currentViewMode = 1
            findViewById<TabLayout>(R.id.calendarTabLayout).getTabAt(1)?.select()
            updateCalendarView()
        }
    }

    private fun getExpandedEvents(start: Long, end: Long): List<CalendarEvent> {
        val expanded = mutableListOf<CalendarEvent>()
        events.forEach { event ->
            if (event.recurrence == null) {
                if (event.endTime >= start && event.startTime <= end) expanded.add(event)
            } else {
                val cal = Calendar.getInstance().apply { timeInMillis = event.startTime }
                val duration = event.endTime - event.startTime
                val limit = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.YEAR, 1) }.timeInMillis
                val actualEnd = if (end > limit) limit else end

                while (cal.timeInMillis <= actualEnd) {
                    val currentStart = cal.timeInMillis
                    val currentEnd = currentStart + duration

                    val matches = when (event.recurrence) {
                        "DAILY" -> true
                        "WEEKLY" -> true
                        "MONTHLY" -> true
                        "YEARLY" -> true
                        "CUSTOM" -> event.recurrenceDays?.contains(cal.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }) == true
                        else -> false
                    }

                    val isExcluded = event.excludedDates?.any { it == currentStart } == true
                    val isPastEnd = event.recurrenceUntil?.let { currentStart > it } ?: false

                    if (matches && !isExcluded && !isPastEnd && currentEnd >= start && currentStart <= actualEnd) {
                        expanded.add(event.copy(startTime = currentStart, endTime = currentEnd))
                    }

                    when (event.recurrence) {
                        "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                        "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                        "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                        "YEARLY" -> cal.add(Calendar.YEAR, 1)
                        "CUSTOM" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                        else -> break
                    }
                    if (event.recurrence != "DAILY" && event.recurrence != "CUSTOM" && cal.timeInMillis > actualEnd) break
                }
            }
        }
        return expanded.sortedBy { it.startTime }
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

    private fun showCustomRecurrenceDialog(initialDays: List<Int>?, onDone: (List<Int>) -> Unit) {
        val days = arrayOf(
            getString(R.string.monday), getString(R.string.tuesday), getString(R.string.wednesday),
            getString(R.string.thursday), getString(R.string.friday), getString(R.string.saturday),
            getString(R.string.sunday)
        )
        val selectedDays = BooleanArray(7) { i -> initialDays?.contains(i + 1) == true }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.select_days)
            .setMultiChoiceItems(days, selectedDays) { _, which, isChecked ->
                selectedDays[which] = isChecked
            }
            .setPositiveButton(R.string.done) { _, _ ->
                val result = mutableListOf<Int>()
                for (i in 0 until 7) if (selectedDays[i]) result.add(i + 1)
                onDone(result)
            }
            .setNegativeButton(R.string.cancel, null)
            .show().also { com.interli.plural.core.ColorHelper.styleAlertDialog(it, this) }
    }

}