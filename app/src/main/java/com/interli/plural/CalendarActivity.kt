package com.interli.plural

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
        val type = object : TypeToken<MutableList<CalendarEvent>>() {}.type
        events = try { Gson().fromJson(json, type) } catch (_: Exception) { mutableListOf() }
        
        people = MemberHelper.loadAllPeople(this)
        
        val notesJson = sharedPref.getString("diary_notes", "[]")
        allNotes = try { Gson().fromJson(notesJson, object : TypeToken<MutableList<DiaryNote>>() {}.type) } catch (_: Exception) { mutableListOf() }
        
        val todoJson = sharedPref.getString("todo_lists", "[]")
        allTodoLists = try { Gson().fromJson(todoJson, object : TypeToken<MutableList<TodoList>>() {}.type) } catch (_: Exception) { mutableListOf() }
    }

    private fun saveData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit().putString("calendar_events", Gson().toJson(events)).apply()
    }

    private fun setupUI() {
        findViewById<View>(R.id.btnPrev).setOnClickListener {
            navigatePeriod(-1)
        }
        findViewById<View>(R.id.btnNext).setOnClickListener {
            navigatePeriod(1)
        }
        findViewById<View>(R.id.btnToday).setOnClickListener {
            selectedDate = Calendar.getInstance()
            updateCalendarView()
        }

        val tabLayout = findViewById<TabLayout>(R.id.calendarTabLayout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentViewMode = tab?.position ?: 0
                updateCalendarView()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<View>(R.id.fabAddEvent).setOnClickListener {
            showEditEventDialog(null)
        }

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
        val scrollView = findViewById<View>(R.id.calendarScrollView)
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.calendarRecyclerView)

        container.removeAllViews()
        
        val bgColor = ColorHelper.getBgColor(this)
        container.setBackgroundColor(bgColor)
        scrollView?.setBackgroundColor(bgColor)
        recyclerView?.setBackgroundColor(bgColor)
        findViewById<View>(R.id.calendarContentFrame)?.setBackgroundColor(bgColor)

        updatePeriodText()

        if (currentViewMode == 0) {
            scrollView?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
            renderAgendaView(recyclerView)
        } else {
            scrollView?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
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
            4 -> SimpleDateFormat("yyyy", Locale.getDefault())
            else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        }
        tv.text = sdf.format(selectedDate.time)
        tv.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun renderAgendaView(recyclerView: androidx.recyclerview.widget.RecyclerView?) {
        if (recyclerView == null) return
        
        val startOfToday = selectedDate.clone() as Calendar
        startOfToday.set(Calendar.HOUR_OF_DAY, 0)
        startOfToday.set(Calendar.MINUTE, 0)
        startOfToday.set(Calendar.SECOND, 0)
        startOfToday.set(Calendar.MILLISECOND, 0)
        
        val rangeEnd = startOfToday.timeInMillis + (60L * 24 * 3600 * 1000)
        val upcomingEvents = getExpandedEvents(startOfToday.timeInMillis, rangeEnd).filter { !it.hideInOverview }

        val agendaItems = mutableListOf<CalendarAgendaAdapter.AgendaItem>()
        var lastDateStr = ""
        val dateSdf = SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault())
        
        upcomingEvents.forEach { event ->
            val eventDateStr = dateSdf.format(Date(event.startTime))
            if (eventDateStr != lastDateStr) {
                agendaItems.add(CalendarAgendaAdapter.AgendaItem.Header(eventDateStr))
                lastDateStr = eventDateStr
            }
            agendaItems.add(CalendarAgendaAdapter.AgendaItem.Event(event))
        }

        if (recyclerView.adapter == null) {
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            recyclerView.adapter = CalendarAgendaAdapter(
                agendaItems,
                allNotes,
                allTodoLists,
                { showEditEventDialog(it) },
                { note ->
                    val intent = android.content.Intent(this, DiaryActivity::class.java)
                    intent.putExtra("NOTE_ID", note.id)
                    startActivity(intent)
                },
                { todo ->
                    val intent = android.content.Intent(this, TodoActivity::class.java)
                    intent.putExtra("TODO_ID", todo.id)
                    startActivity(intent)
                }
            )
        } else {
            (recyclerView.adapter as? CalendarAgendaAdapter)?.updateData(agendaItems)
        }
    }

    private fun renderDayView(container: LinearLayout) {
        val timelineView = CalendarTimelineView(this)
        timelineView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        val dayStart = selectedDate.clone() as Calendar
        dayStart.set(Calendar.HOUR_OF_DAY, 0); dayStart.set(Calendar.MINUTE, 0); dayStart.set(Calendar.SECOND, 0); dayStart.set(Calendar.MILLISECOND, 0)
        val dayEnd = dayStart.timeInMillis + 24 * 3600 * 1000
        val expanded = getExpandedEvents(dayStart.timeInMillis, dayEnd).filter { !it.hideInDay }
        
        timelineView.setEvents(expanded, people, allNotes, allTodoLists, selectedDate, 1)
        timelineView.onEventClicked = { showEditEventDialog(it) }
        timelineView.onLinkSelected = { type, id -> handleLinkClick(type, id) }
        container.addView(timelineView)
    }

    private fun renderWeekView(container: LinearLayout) {
        val timelineView = CalendarTimelineView(this)
        timelineView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        val weekStart = selectedDate.clone() as Calendar
        weekStart.set(Calendar.DAY_OF_WEEK, weekStart.firstDayOfWeek)
        weekStart.set(Calendar.HOUR_OF_DAY, 0); weekStart.set(Calendar.MINUTE, 0); weekStart.set(Calendar.SECOND, 0); weekStart.set(Calendar.MILLISECOND, 0)
        val weekEnd = weekStart.timeInMillis + 7 * 24 * 3600 * 1000
        val expanded = getExpandedEvents(weekStart.timeInMillis, weekEnd).filter { !it.hideInWeek }
        
        timelineView.setEvents(expanded, people, allNotes, allTodoLists, selectedDate, 7)
        timelineView.onEventClicked = { showEditEventDialog(it) }
        timelineView.onLinkSelected = { type, id -> handleLinkClick(type, id) }
        container.addView(timelineView)
    }

    private fun handleLinkClick(type: String, id: String) {
        when (type) {
            "MEMBER" -> {
                val index = people.indexOfFirst { it.id == id }
                if (index != -1) {
                    val intent = android.content.Intent(this, ProfileActivity::class.java)
                    intent.putExtra("person_index", index)
                    startActivity(intent)
                }
            }
            "NOTE" -> {
                val intent = android.content.Intent(this, DiaryActivity::class.java)
                intent.putExtra("NOTE_ID", id)
                startActivity(intent)
            }
            "TODO" -> {
                val intent = android.content.Intent(this, TodoActivity::class.java)
                intent.putExtra("TODO_ID", id)
                startActivity(intent)
            }
        }
    }

    private fun renderMonthView(container: LinearLayout) {
        val monthView = CalendarMonthView(this)
        
        val startOfMonth = selectedDate.clone() as Calendar
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        startOfMonth.set(Calendar.HOUR_OF_DAY, 0)
        startOfMonth.set(Calendar.MINUTE, 0)
        startOfMonth.set(Calendar.SECOND, 0)
        
        val endOfMonth = startOfMonth.clone() as Calendar
        endOfMonth.add(Calendar.MONTH, 1)
        endOfMonth.add(Calendar.MILLISECOND, -1)

        val monthEvents = getExpandedEvents(startOfMonth.timeInMillis, endOfMonth.timeInMillis).filter { !it.hideInMonth }

        monthView.setEvents(monthEvents, people, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH))
        monthView.onDayClicked = { day: Int ->
            selectedDate.set(Calendar.DAY_OF_MONTH, day)
            currentViewMode = 1 // Day view
            val tabLayout = findViewById<TabLayout>(R.id.calendarTabLayout)
            tabLayout.getTabAt(1)?.select()
            updateCalendarView()
        }
        container.addView(monthView)
    }

    private fun renderYearView(container: LinearLayout) {
        val calView = CalendarYearView(this)
        
        val startOfYear = selectedDate.clone() as Calendar
        startOfYear.set(Calendar.MONTH, 0)
        startOfYear.set(Calendar.DAY_OF_MONTH, 1)
        startOfYear.set(Calendar.HOUR_OF_DAY, 0)
        startOfYear.set(Calendar.MINUTE, 0)
        startOfYear.set(Calendar.SECOND, 0)
        
        val endOfYear = startOfYear.clone() as Calendar
        endOfYear.add(Calendar.YEAR, 1)
        endOfYear.add(Calendar.MILLISECOND, -1)

        val yearEvents = getExpandedEvents(startOfYear.timeInMillis, endOfYear.timeInMillis).filter { !it.hideInYear }

        calView.setEvents(yearEvents, people, selectedDate.get(Calendar.YEAR))
        calView.onDayClicked = { date: Date ->
            selectedDate.time = date
            currentViewMode = 1 // Switch to Day view
            val tabLayout = findViewById<TabLayout>(R.id.calendarTabLayout)
            tabLayout.getTabAt(1)?.select()
            updateCalendarView()
        }
        container.addView(calView)
    }

    private var TextView.textStyle: Int
        get() = 0
        set(value) { setTypeface(null, value) }

    private fun showEditEventDialog(event: CalendarEvent?) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_calendar_event, null)
        val etTitle = view.findViewById<EditText>(R.id.etEventTitle)
        val etLocation = view.findViewById<EditText>(R.id.etEventLocation)
        val etDesc = view.findViewById<EditText>(R.id.etEventDescription)
        
        val btnStartDate = view.findViewById<Button>(R.id.btnEventStartDate)
        val btnStartTime = view.findViewById<Button>(R.id.btnEventStartTime)
        val btnEndDate = view.findViewById<Button>(R.id.btnEventEndDate)
        val btnEndTime = view.findViewById<Button>(R.id.btnEventEndTime)
        
        val btnLinkMember = view.findViewById<Button>(R.id.btnLinkMemberEvent)
        val tvLinkedMember = view.findViewById<TextView>(R.id.tvLinkedMemberName)
        val btnLinkNote = view.findViewById<Button>(R.id.btnLinkNoteEvent)
        val tvLinkedNote = view.findViewById<TextView>(R.id.tvLinkedNoteTitle)
        val btnLinkTodo = view.findViewById<Button>(R.id.btnLinkTodoEvent)
        val tvLinkedTodo = view.findViewById<TextView>(R.id.tvLinkedTodoTitle)

        var startTime = event?.startTime ?: System.currentTimeMillis()
        var endTime = event?.endTime ?: (System.currentTimeMillis() + 3600000)
        var selectedMemberIds = event?.linkedMemberIds?.toMutableList() ?: mutableListOf()
        var selectedNoteId = event?.linkedNoteId
        var selectedTodoId = event?.linkedTodoListId

        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

        val updateTimes = {
            btnStartDate.text = sdfDate.format(Date(startTime))
            btnStartTime.text = sdfTime.format(Date(startTime))
            btnEndDate.text = sdfDate.format(Date(endTime))
            btnEndTime.text = sdfTime.format(Date(endTime))
        }
        updateTimes()

        var selectedColor = event?.color
        val colorLayout = view.findViewById<LinearLayout>(R.id.layoutEventColors)
        val colors = listOf(null, -0xbbcca, -0x123456, -0x654321, -0xabcdef, -0x1, -0xff0000, -0x00ff00, -0x0000ff) 
        // Example colors, let's use something more standard or from ColorHelper if possible
        val eventColors = listOf(null, 
            0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 
            0xFF673AB7.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt(),
            0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(),
            0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
            0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(),
            0xFFFF5722.toInt())

        lateinit var colorUIRef: Runnable
        colorUIRef = Runnable {
            colorLayout.removeAllViews()
            eventColors.forEach { color ->
                val colorView = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams((32 * resources.displayMetrics.density).toInt(), (32 * resources.displayMetrics.density).toInt()).apply {
                        setMargins(8, 0, 8, 0)
                    }
                    val drawable = android.graphics.drawable.GradientDrawable()
                    drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                    drawable.setColor(color ?: ColorHelper.getBtnColor(this@CalendarActivity))
                    if (selectedColor == color) {
                        drawable.setStroke(4, ColorHelper.getTextColor(this@CalendarActivity))
                    }
                    background = drawable
                    setOnClickListener {
                        selectedColor = color
                        colorUIRef.run()
                    }
                }
                colorLayout.addView(colorView)
            }
        }
        colorUIRef.run()

        var selectedRecurrence = event?.recurrence
        var selectedRecurrenceDays = event?.recurrenceDays?.toMutableList() ?: mutableListOf()
        val btnRecurrence = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEventRecurrence)
        
        val cbHideAgenda = view.findViewById<CheckBox>(R.id.cbHideAgenda)
        val cbHideDay = view.findViewById<CheckBox>(R.id.cbHideDay)
        val cbHideWeek = view.findViewById<CheckBox>(R.id.cbHideWeek)
        val cbHideMonth = view.findViewById<CheckBox>(R.id.cbHideMonth)
        val cbHideYear = view.findViewById<CheckBox>(R.id.cbHideYear)

        if (event != null) {
            cbHideAgenda.isChecked = event.hideInOverview
            cbHideDay.isChecked = event.hideInDay
            cbHideWeek.isChecked = event.hideInWeek
            cbHideMonth.isChecked = event.hideInMonth
            cbHideYear.isChecked = event.hideInYear
        }

        ColorHelper.applyTextColorToAllViews(view.findViewById(R.id.layoutHideIn), ColorHelper.getTextColor(this))

        val updateRecurrenceText = {
            btnRecurrence.text = when (selectedRecurrence) {
                "DAILY" -> getString(R.string.recurrence_daily)
                "WEEKLY" -> {
                    if (selectedRecurrenceDays.isEmpty()) getString(R.string.recurrence_weekly)
                    else {
                        val dayNames = listOf(getString(R.string.monday), getString(R.string.tuesday), getString(R.string.wednesday), getString(R.string.thursday), getString(R.string.friday), getString(R.string.saturday), getString(R.string.sunday))
                        val active = selectedRecurrenceDays.sorted().map { dayNames[it - 1].take(2) }
                        "${getString(R.string.recurrence_weekly)} (${active.joinToString(", ")})"
                    }
                }
                "MONTHLY" -> getString(R.string.recurrence_monthly)
                "YEARLY" -> getString(R.string.recurrence_yearly)
                else -> getString(R.string.recurrence_none)
            }
        }
        updateRecurrenceText()

        btnRecurrence.setOnClickListener {
            val options = arrayOf(getString(R.string.recurrence_none), getString(R.string.recurrence_daily), getString(R.string.recurrence_weekly), getString(R.string.recurrence_monthly), getString(R.string.recurrence_yearly))
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.label_recurrence)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> { selectedRecurrence = null; selectedRecurrenceDays.clear() }
                        1 -> { selectedRecurrence = "DAILY"; selectedRecurrenceDays.clear() }
                        2 -> {
                            selectedRecurrence = "WEEKLY"
                            // Show day selection
                            val days = arrayOf(getString(R.string.monday), getString(R.string.tuesday), getString(R.string.wednesday), getString(R.string.thursday), getString(R.string.friday), getString(R.string.saturday), getString(R.string.sunday))
                            val checked = BooleanArray(7) { i -> selectedRecurrenceDays.contains(i + 1) }
                            androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle(R.string.select_days)
                                .setMultiChoiceItems(days, checked) { _, dayIdx, isChecked ->
                                    if (isChecked) selectedRecurrenceDays.add(dayIdx + 1)
                                    else selectedRecurrenceDays.remove(dayIdx + 1)
                                }
                                .setPositiveButton(R.string.done) { _, _ -> updateRecurrenceText() }
                                .show().let { ColorHelper.styleSupportAlertDialog(it, this) }
                        }
                        3 -> { selectedRecurrence = "MONTHLY"; selectedRecurrenceDays.clear() }
                        4 -> { selectedRecurrence = "YEARLY"; selectedRecurrenceDays.clear() }
                    }
                    updateRecurrenceText()
                }
                .show().let { ColorHelper.styleSupportAlertDialog(it, this) }
        }

        val updateLinks = {
            if (selectedMemberIds.isNotEmpty()) {
                val names = people.filter { selectedMemberIds.contains(it.id) }.map { it.name }
                tvLinkedMember.text = names.joinToString(", ")
                tvLinkedMember.visibility = View.VISIBLE
            } else {
                tvLinkedMember.visibility = View.GONE
            }
            
            if (selectedNoteId != null) {
                tvLinkedNote.text = allNotes.find { it.id == selectedNoteId }?.title ?: "Unknown Note"
                tvLinkedNote.visibility = View.VISIBLE
            } else {
                tvLinkedNote.visibility = View.GONE
            }
            
            if (selectedTodoId != null) {
                tvLinkedTodo.text = allTodoLists.find { it.id == selectedTodoId }?.title ?: "Unknown To-Do"
                tvLinkedTodo.visibility = View.VISIBLE
            } else {
                tvLinkedTodo.visibility = View.GONE
            }
        }
        updateLinks()

        if (event != null) {
            etTitle.setText(event.title)
            etLocation.setText(event.location)
            etDesc.setText(event.description)
        }

        btnStartDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = startTime }
            android.app.DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                startTime = cal.timeInMillis
                if (endTime < startTime) {
                    endTime = startTime + 3600000
                }
                updateTimes()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        btnStartTime.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = startTime }
            android.app.TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                startTime = cal.timeInMillis
                if (endTime < startTime) {
                    endTime = startTime + 3600000
                }
                updateTimes()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
        btnEndDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = endTime }
            android.app.DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                endTime = cal.timeInMillis
                updateTimes()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        btnEndTime.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = endTime }
            android.app.TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                endTime = cal.timeInMillis
                updateTimes()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        btnLinkMember.setOnClickListener {
            val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
            val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
            val groups: List<Group> = Gson().fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type)
            DialogHelper.showMemberSelectionDialog(this, getString(R.string.action_link_members), people, groups, selectedMemberIds) { newList ->
                selectedMemberIds.clear()
                selectedMemberIds.addAll(newList)
                updateLinks()
            }
        }

        btnLinkNote.setOnClickListener {
            val noteTitles = allNotes.map { it.title.ifEmpty { "Unnamed Note" } }
            DialogHelper.showSearchableListDialog(this, getString(R.string.action_link_note), noteTitles) { selectedTitle ->
                selectedNoteId = allNotes.find { it.title == selectedTitle }?.id
                updateLinks()
            }
        }

        btnLinkTodo.setOnClickListener {
            val todoTitles = allTodoLists.map { it.title.ifEmpty { "Unnamed To-Do" } }
            DialogHelper.showSearchableListDialog(this, getString(R.string.action_link_todo), todoTitles) { selectedTitle ->
                selectedTodoId = allTodoLists.find { it.title == selectedTitle }?.id
                updateLinks()
            }
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (event == null) R.string.add_event else R.string.edit_event)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    val e = event ?: CalendarEvent(title = title, startTime = startTime, endTime = endTime)
                    e.title = title
                    e.description = etDesc.text.toString()
                    e.location = etLocation.text.toString()
                    e.startTime = startTime
                    e.endTime = endTime
                    e.color = selectedColor
                    e.recurrence = selectedRecurrence
                    e.recurrenceDays = if (selectedRecurrenceDays.isEmpty()) null else selectedRecurrenceDays
                    e.linkedMemberIds = selectedMemberIds
                    e.linkedNoteId = selectedNoteId
                    e.linkedTodoListId = selectedTodoId
                    
                    e.hideInOverview = cbHideAgenda.isChecked
                    e.hideInDay = cbHideDay.isChecked
                    e.hideInWeek = cbHideWeek.isChecked
                    e.hideInMonth = cbHideMonth.isChecked
                    e.hideInYear = cbHideYear.isChecked

                    if (event == null) events.add(e)
                    saveData()
                    updateCalendarView()
                }
            }
            .setNegativeButton(R.string.cancel, null)
        
        if (event != null) {
            dialog.setNeutralButton(R.string.delete) { _, _ ->
                showDeleteConfirm(event)
            }
        }

        val d = dialog.create()
        d.show()
        ColorHelper.styleSupportAlertDialog(d, this)
        etTitle.setTextColor(ColorHelper.getTextColor(this))
        etDesc.setTextColor(ColorHelper.getTextColor(this))
        etLocation.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun showDateTimePicker(initialTime: Long, onTimeSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = initialTime
        android.app.DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            android.app.TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                onTimeSelected(cal.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showDeleteConfirm(event: CalendarEvent) {
        var clicks = 0
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.delete_event_8x, 8))
            .setPositiveButton(getString(R.string.delete) + " (8)", null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            clicks++
            if (clicks >= 8) {
                events.remove(event)
                saveData()
                updateCalendarView()
                dialog.dismiss()
            } else {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).text = "${getString(R.string.delete)} (${8 - clicks})"
            }
        }
    }

    private fun getExpandedEvents(startTime: Long, endTime: Long): List<CalendarEvent> {
        val result = mutableListOf<CalendarEvent>()
        events.forEach { event ->
            if (event.recurrence == null) {
                if (event.endTime >= startTime && event.startTime <= endTime) {
                    result.add(event)
                }
            } else {
                result.addAll(expandRecurringEvent(event, startTime, endTime))
            }
        }
        return result.sortedBy { it.startTime }
    }

    private fun expandRecurringEvent(event: CalendarEvent, rangeStart: Long, rangeEnd: Long): List<CalendarEvent> {
        val expanded = mutableListOf<CalendarEvent>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = event.startTime
        
        val duration = event.endTime - event.startTime
        
        // Safety break to prevent infinite loops if misconfigured
        var count = 0
        while (cal.timeInMillis <= rangeEnd && count < 1000) {
            count++
            val currentStart = cal.timeInMillis
            val currentEnd = currentStart + duration
            
            if (currentEnd >= rangeStart && currentStart <= rangeEnd) {
                // Check recurrence type
                var shouldAdd = false
                when (event.recurrence) {
                    "DAILY" -> shouldAdd = true
                    "WEEKLY" -> {
                        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
                        val mappedDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1 // 1=Mon...7=Sun
                        if (event.recurrenceDays?.contains(mappedDay) == true) shouldAdd = true
                    }
                    "MONTHLY" -> shouldAdd = true
                    "YEARLY" -> shouldAdd = true
                }
                
                if (shouldAdd) {
                    expanded.add(event.copy(startTime = currentStart, endTime = currentEnd))
                }
            }
            
            // Increment cal based on recurrence
            when (event.recurrence) {
                "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> cal.add(Calendar.DAY_OF_YEAR, 1) // Check every day for weekly
                "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                "YEARLY" -> cal.add(Calendar.YEAR, 1)
                else -> break
            }
            
            // Optimization: if we are far before the range, jump closer
            if (cal.timeInMillis < rangeStart - (31L * 24 * 3600 * 1000)) {
                when (event.recurrence) {
                    "YEARLY" -> {
                        val diffYears = ((rangeStart - cal.timeInMillis) / (365L * 24 * 3600 * 1000)).toInt()
                        if (diffYears > 0) cal.add(Calendar.YEAR, diffYears)
                    }
                    "MONTHLY" -> {
                        val diffMonths = ((rangeStart - cal.timeInMillis) / (31L * 24 * 3600 * 1000)).toInt()
                        if (diffMonths > 0) cal.add(Calendar.MONTH, diffMonths)
                    }
                    "DAILY", "WEEKLY" -> {
                        val diffDays = ((rangeStart - cal.timeInMillis) / (24L * 3600 * 1000)).toInt()
                        if (diffDays > 7) cal.add(Calendar.DAY_OF_YEAR, diffDays - 7)
                    }
                }
            }
        }
        return expanded
    }
}
