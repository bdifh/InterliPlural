package com.interli.plural

import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class EditTodoListActivity : BaseActivity() {

    private var listId: String? = null
    private lateinit var todoLists: MutableList<TodoList>
    private lateinit var todoBundles: List<TodoBundle>
    private lateinit var people: List<Person>
    private val gson = Gson()
    
    private lateinit var editTitle: EditText
    private lateinit var linkedMembersText: TextView
    private lateinit var btnSelectBundle: Button
    private lateinit var rvTasks: RecyclerView
    private lateinit var tasksAdapter: TasksAdapter
    
    private var selectedMemberIds = mutableListOf<String>()
    private var tasks = mutableListOf<TodoTask>()
    private var listDeadline: Long? = null
    private var reminderTime: Long? = null
    private var selectedBundleId: String? = null
    
    private var deleteClickCount = 0
    private var lastDeleteClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_todo_list)

        listId = intent.getStringExtra("list_id")
        loadData()

        editTitle = findViewById(R.id.editTodoTitle)
        linkedMembersText = findViewById(R.id.linkedMembersListText)
        rvTasks = findViewById(R.id.rvTasksEdit)

        val btnLinkMembers = findViewById<Button>(R.id.btnLinkMembersList)
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val frontEnabled = sp.getBoolean("module_fronting_enabled", true) && sp.getBoolean("sub_fronting_enabled", true)
        if (!frontEnabled) {
            btnLinkMembers.visibility = View.GONE
            findViewById<View>(R.id.labelLinkedMembersList).visibility = View.GONE
            findViewById<View>(R.id.linkedMembersListText).visibility = View.GONE
        }
        val parent = btnLinkMembers.parent as ViewGroup
        val index = parent.indexOfChild(btnLinkMembers)
        
        parent.removeView(btnLinkMembers)
        
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dpToPx() }
        }
        
        btnLinkMembers.layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f)
        
        btnSelectBundle = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.label_bundle)
            setOnClickListener { showBundleSelectionDialog() }
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.6f).apply { marginStart = 8.dpToPx() }
            textSize = 10f
            setPadding(4.dpToPx(), 0, 4.dpToPx(), 0)
            
            val color = ColorHelper.getBtnColor(this@EditTodoListActivity)
            setTextColor(color)
            strokeColor = android.content.res.ColorStateList.valueOf(color and 0x44FFFFFF)
            rippleColor = android.content.res.ColorStateList.valueOf(color and 0x33FFFFFF)
        }
        
        buttonRow.addView(btnLinkMembers)
        buttonRow.addView(btnSelectBundle)
        parent.addView(buttonRow, index)

        val btnBack = findViewById<Button>(R.id.btnEditTodoBack)
        val btnSave = findViewById<Button>(R.id.btnSaveTodoList)
        val btnAddTask = findViewById<Button>(R.id.btnAddNewTask)
        val btnDelete = findViewById<Button>(R.id.btnDeleteTodoList)
        
        val btnSetListDeadline = findViewById<Button>(R.id.btnSetListDeadlineCombined)
        val btnSetReminder = findViewById<Button>(R.id.btnSetReminderCombined)

        val existingList = todoLists.find { it.id == listId }
        if (existingList != null) {
            editTitle.setText(existingList.title)
            selectedMemberIds = existingList.linkedMemberIds.toMutableList()
            tasks = existingList.tasks.toMutableList()
            listDeadline = existingList.deadline
            reminderTime = existingList.reminderTime
            selectedBundleId = existingList.bundleId
            btnDelete.visibility = View.VISIBLE
            btnDelete.text = getString(R.string.delete_todo_8x, 8)
        } else {
            val preLink = intent.getStringExtra("pre_link_member_id")
            if (!preLink.isNullOrEmpty()) selectedMemberIds.add(preLink)
        }

        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        val updateListDeadlineUI = {
            btnSetListDeadline.text = listDeadline?.let { sdf.format(Date(it)) } ?: getString(R.string.date)
        }

        val updateReminderUI = {
            btnSetReminder.text = reminderTime?.let { sdf.format(Date(it)) } ?: getString(R.string.date)
        }
        
        val updateBundleUI = {
            val bundle = todoBundles.find { it.id == selectedBundleId }
            btnSelectBundle.text = bundle?.name ?: getString(R.string.label_bundle)
        }

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveList() }
        btnLinkMembers.setOnClickListener { showMemberSelectionDialog(selectedMemberIds) { updateLinkedMembersText() } }
        btnAddTask.setOnClickListener { addTask() }
        btnDelete.setOnClickListener { deleteList() }

        btnSetListDeadline.setOnClickListener {
            showDateTimePicker(listDeadline) { newTime ->
                listDeadline = newTime
                updateListDeadlineUI()
            }
        }
        btnSetListDeadline.setOnLongClickListener { listDeadline = null; updateListDeadlineUI(); true }

        btnSetReminder.setOnClickListener {
            showDateTimePicker(reminderTime ?: (System.currentTimeMillis() + 3600000)) { newTime ->
                reminderTime = newTime
                updateReminderUI()
            }
        }
        btnSetReminder.setOnLongClickListener { reminderTime = null; updateReminderUI(); true }

        ColorHelper.applySettings(this)
        updateLinkedMembersText()
        updateListDeadlineUI()
        updateReminderUI()
        updateBundleUI()

        setupRecyclerView()

        val textColor = ColorHelper.getTextColor(this)
        editTitle.setTextColor(textColor)
        linkedMembersText.setTextColor(textColor)
        findViewById<TextView>(R.id.labelLinkedMembersList).setTextColor(textColor)
        findViewById<TextView>(R.id.tvScreenTitle).setTextColor(textColor)
        
        val hintColor = textColor and 0x00FFFFFF or 0x99000000.toInt()
        val states = android.content.res.ColorStateList.valueOf(hintColor)
        findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutTodoTitle).defaultHintTextColor = states
    }

    private fun showBundleSelectionDialog() {
        val options = mutableListOf<String>()
        options.add(getString(R.string.group_none_main))
        todoBundles.forEach { options.add(it.name) }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.label_bundle)
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    selectedBundleId = null
                } else {
                    selectedBundleId = todoBundles[which - 1].id
                }
                val bundle = todoBundles.find { it.id == selectedBundleId }
                btnSelectBundle.text = bundle?.name ?: getString(R.string.label_bundle)
            }
            .show()
    }

    private fun setupRecyclerView() {
        tasksAdapter = TasksAdapter()
        rvTasks.layoutManager = LinearLayoutManager(this)
        rvTasks.adapter = tasksAdapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition

                if (fromPos != toPos) {
                    Collections.swap(tasks, fromPos, toPos)
                    tasksAdapter.notifyItemMoved(fromPos, toPos)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                val task = tasks[pos]

                if (direction == ItemTouchHelper.RIGHT) {
                    if (pos > 0 && task.indentLevel < 10) {
                        task.indentLevel++
                    }
                } else if (direction == ItemTouchHelper.LEFT) {
                    if (task.indentLevel > 0) {
                        task.indentLevel--
                    }
                }
                tasksAdapter.notifyItemChanged(pos)
            }
        })
        itemTouchHelper.attachToRecyclerView(rvTasks)
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val todoJson = sharedPref.getString("todo_lists", "[]") ?: "[]"
        todoLists = try {
            gson.fromJson(todoJson, object : TypeToken<MutableList<TodoList>>() {}.type) ?: mutableListOf()
        } catch (_: Exception) { mutableListOf() }

        val bundlesJson = sharedPref.getString("todo_bundles", "[]") ?: "[]"
        todoBundles = try {
            gson.fromJson(bundlesJson, object : TypeToken<List<TodoBundle>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }

        val peopleJson = sharedPref.getString("people_list", "[]") ?: "[]"
        people = try {
            gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun updateLinkedMembersText() {
        val names = people.filter { selectedMemberIds.contains(it.id) && !it.isArchived && !it.isSysmediaOnly }.map { it.name }
        linkedMembersText.text = if (names.isEmpty()) "" else names.joinToString(", ")
    }

    private fun addTask() {
        tasks.add(TodoTask(title = ""))
        tasksAdapter.notifyItemInserted(tasks.size - 1)
        rvTasks.scrollToPosition(tasks.size - 1)
    }

    private fun showMemberSelectionDialog(selectedIds: MutableList<String>, onDone: () -> Unit) {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val groupsJson = sharedPref.getString("groups_list", "[]") ?: "[]"
        val groups: List<Group> = gson.fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type)

        val filteredPeople = people.filter { !it.isArchived && !it.isSysmediaOnly }

        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.action_link_members),
            filteredPeople,
            groups,
            selectedIds
        ) { newList ->
            selectedIds.clear()
            selectedIds.addAll(newList)
            onDone()
        }
    }

    private fun showDateTimePicker(current: Long?, onSelected: (Long) -> Unit) {
        val cal = java.util.Calendar.getInstance()
        if (current != null) cal.timeInMillis = current
        
        val dateDialog = DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.set_deadline)
                .setMessage(R.string.dialog_add_time)
                .setPositiveButton(R.string.yes) { _, _ ->
                    val timeDialog = TimePickerDialog(this, { _, hh, mm ->
                        cal.set(java.util.Calendar.HOUR_OF_DAY, hh)
                        cal.set(java.util.Calendar.MINUTE, mm)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        onSelected(cal.timeInMillis)
                    }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true)
                    timeDialog.show()
                    ColorHelper.styleAlertDialog(timeDialog, this)
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    onSelected(cal.timeInMillis)
                }
                .show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
        
        dateDialog.show()
        ColorHelper.styleAlertDialog(dateDialog, this)
    }

    private fun saveList() {
        val title = editTitle.text.toString().trim()
        if (title.isEmpty() && tasks.isEmpty()) { finish(); return }

        val existing = todoLists.find { it.id == listId }
        val newList = TodoList(
            id = listId ?: UUID.randomUUID().toString(),
            title = title,
            tasks = tasks,
            linkedMemberIds = selectedMemberIds,
            deadline = listDeadline,
            reminderTime = reminderTime,
            bundleId = selectedBundleId,
            manualOrder = existing?.manualOrder ?: (todoLists.size + 100)
        )

        if (listId == null) {
            todoLists.add(newList)
        } else {
            val idx = todoLists.indexOfFirst { it.id == listId }
            if (idx != -1) todoLists[idx] = newList
        }

        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit().putString("todo_lists", gson.toJson(todoLists)).apply()

        if (reminderTime != null && reminderTime!! > System.currentTimeMillis()) {
            scheduleReminder(newList)
        } else {
            cancelReminder(newList)
        }

        finish()
    }

    private fun scheduleReminder(list: TodoList) {
        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(this, NotificationReceiver::class.java).apply {
            action = "TODO_REMINDER"
            putExtra("todo_id", list.id)
            putExtra("todo_title", list.title)
        }
        
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, list.id.hashCode(), intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, list.reminderTime!!, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, list.reminderTime!!, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, list.reminderTime!!, pendingIntent)
        }
    }

    private fun cancelReminder(list: TodoList) {
        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(this, NotificationReceiver::class.java).apply {
            action = "TODO_REMINDER"
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, list.id.hashCode(), intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun deleteList() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDeleteClickTime > 2000) {
            deleteClickCount = 0
        }
        deleteClickCount++
        lastDeleteClickTime = currentTime

        if (deleteClickCount >= 8) {
            todoLists.removeAll { it.id == listId }
            val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
            sharedPref.edit().putString("todo_lists", gson.toJson(todoLists)).apply()
            finish()
        } else {
            val remaining = 8 - deleteClickCount
            val btnDelete = findViewById<Button>(R.id.btnDeleteTodoList)
            btnDelete.text = getString(R.string.delete_todo_8x, remaining)
        }
    }

    private inner class TasksAdapter : RecyclerView.Adapter<TasksAdapter.TaskViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_edit_todo_task, parent, false)
            return TaskViewHolder(view)
        }

        override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
            val task = tasks[position]
            val context = holder.itemView.context
            val textColor = ColorHelper.getTextColor(context)
            val btnColor = ColorHelper.getBtnColor(context)
            val btnTextColor = ColorHelper.getBtnTextColor(context)
            val frontColor = ColorHelper.getFrontColor(context)
            val p = holder.card.layoutParams as ViewGroup.MarginLayoutParams
            p.marginStart = 0 
            holder.card.layoutParams = p
            
            val indent = task.indentLevel * 12
            holder.itemView.findViewById<View>(R.id.indentContainer).setPadding(indent.dpToPx(), 0, 0, 0)
            holder.card.setCardBackgroundColor(frontColor)
            holder.etTitle.setTextColor(textColor)
            holder.etTitle.setHintTextColor(textColor and 0x88FFFFFF.toInt())
            holder.btnRemove.setColorFilter(textColor)
            
            listOf(holder.btnLink, holder.btnDate, holder.btnRepeat, holder.btnReset).forEach { btn ->
                val spTasks = context.getSharedPreferences("settings_prefs", MODE_PRIVATE)
                val frontEnabledTasks = spTasks.getBoolean("module_fronting_enabled", true) && spTasks.getBoolean("sub_fronting_enabled", true)
                holder.btnLink.visibility = if (frontEnabledTasks) View.VISIBLE else View.GONE

                btn.setTextColor(btnTextColor)
                btn.setBackgroundColor(btnColor)
                btn.iconTint = android.content.res.ColorStateList.valueOf(btnTextColor)
                btn.rippleColor = android.content.res.ColorStateList.valueOf(btnTextColor and 0x33FFFFFF)
            }

            holder.etTitle.tag = task
            holder.etTitle.setText(task.title)
            
            holder.etTitle.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as EditText).addTextChangedListener(object : android.text.TextWatcher {
                        override fun afterTextChanged(s: android.text.Editable?) { 
                            if (v.tag == task) task.title = s.toString() 
                        }
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    })
                }
            }
            
            val updateTaskLinkText = {
                val names = people.filter { task.linkedMemberIds.contains(it.id) && !it.isArchived && !it.isSysmediaOnly }.map { it.name }
                holder.btnLink.text = if (names.isEmpty()) {
                    getString(R.string.action_link_members)
                } else if (names.size > 2) {
                    "${names.size} ${getString(R.string.stats_members).split(":")[0].trim()}"
                } else {
                    names.joinToString(", ")
                }
            }
            updateTaskLinkText()
            
            val updateTaskDeadlineUI = {
                holder.btnDate.text = task.deadline?.let { dl ->
                    val c = Calendar.getInstance().apply { timeInMillis = dl }
                    val isMidnight = c.get(Calendar.HOUR_OF_DAY) == 0 && c.get(Calendar.MINUTE) == 0
                    val fmt = if (isMidnight) "dd/MM" else "dd/MM HH:mm"
                    SimpleDateFormat(fmt, Locale.getDefault()).format(Date(dl))
                } ?: getString(R.string.date)
            }
            updateTaskDeadlineUI()

            val updateRecurrenceUI = {
                holder.btnRepeat.text = when (task.recurrence) {
                    "DAILY" -> getString(R.string.recurrence_daily)
                    "WEEKLY" -> getString(R.string.recurrence_weekly)
                    "MONTHLY" -> getString(R.string.recurrence_monthly)
                    "YEARLY" -> getString(R.string.recurrence_yearly)
                    "CUSTOM" -> {
                        val days = task.recurrenceDays ?: emptyList()
                        if (days.isEmpty()) getString(R.string.recurrence_custom)
                        else days.joinToString(",") { d -> 
                            when(d) { 1-> "Ma"; 2-> "Di"; 3-> "Wo"; 4-> "Do"; 5-> "Vr"; 6-> "Za"; else -> "Zo" }
                        }
                    }
                    else -> getString(R.string.action_repeat)
                }

                if (task.recurrence != null) {
                    holder.btnReset.visibility = View.VISIBLE
                    val resetInfo = if (task.resetType == "DELAYED") {
                        "2s"
                    } else {
                        String.format("%02d:%02d", task.resetHour, task.resetMinute)
                    }
                    holder.btnReset.text = resetInfo
                } else {
                    holder.btnReset.visibility = View.GONE
                }
            }
            updateRecurrenceUI()

            holder.btnLink.setOnClickListener {
                showMemberSelectionDialog(task.linkedMemberIds) { updateTaskLinkText() }
            }

            holder.btnDate.setOnClickListener {
                showDateTimePicker(task.deadline) { newTime ->
                    task.deadline = newTime
                    updateTaskDeadlineUI()
                }
            }
            holder.btnDate.setOnLongClickListener { task.deadline = null; updateTaskDeadlineUI(); true }

            holder.btnRepeat.setOnClickListener {
                val options = arrayOf(
                    getString(R.string.recurrence_none), getString(R.string.recurrence_daily),
                    getString(R.string.recurrence_weekly), getString(R.string.recurrence_monthly),
                    getString(R.string.recurrence_yearly), getString(R.string.recurrence_custom)
                )
                val values = arrayOf(null, "DAILY", "WEEKLY", "MONTHLY", "YEARLY", "CUSTOM")
                
                val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(R.string.action_repeat)
                    .setItems(options) { _, which ->
                        val selected = values[which]
                        if (selected == "CUSTOM") {
                            showCustomRecurrenceDialog(task) { updateRecurrenceUI() }
                        } else {
                            task.recurrence = selected
                            task.recurrenceDays = null
                            updateRecurrenceUI()
                        }
                    }
                    .create()
                dialog.show()
                ColorHelper.styleSupportAlertDialog(dialog, context)
            }

            holder.btnReset.setOnClickListener {
                showResetTypeDialog(task) { updateRecurrenceUI() }
            }

            holder.btnRemove.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    tasks.removeAt(pos)
                    notifyItemRemoved(pos)
                }
            }
        }

        override fun getItemCount() = tasks.size

        inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.taskCard)
            val etTitle: EditText = view.findViewById(R.id.editTaskTitle)
            val btnLink: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnLinkTaskMembers)
            val btnDate: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnSetTaskDate)
            val btnRepeat: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnSetTaskRecurrence)
            val btnReset: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnSetTaskReset)
            val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveTask)
        }
    }

    private fun showCustomRecurrenceDialog(task: TodoTask, onDone: () -> Unit) {
        val days = arrayOf(
            getString(R.string.monday), getString(R.string.tuesday), getString(R.string.wednesday),
            getString(R.string.thursday), getString(R.string.friday), getString(R.string.saturday),
            getString(R.string.sunday)
        )
        val selectedDays = BooleanArray(7) { i -> task.recurrenceDays?.contains(i + 1) == true }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.select_days)
            .setMultiChoiceItems(days, selectedDays) { _, which, isChecked ->
                selectedDays[which] = isChecked
            }
            .setPositiveButton(R.string.done) { _, _ ->
                val result = mutableListOf<Int>()
                for (i in 0 until 7) if (selectedDays[i]) result.add(i + 1)
                task.recurrence = if (result.isEmpty()) null else "CUSTOM"
                task.recurrenceDays = if (result.isEmpty()) null else result
                onDone()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showResetTypeDialog(task: TodoTask, onDone: () -> Unit) {
        val options = arrayOf(
            getString(R.string.reset_delayed),
            getString(R.string.reset_at_time)
        )
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.reset_type)
            .setItems(options) { _, which ->
                if (which == 0) {
                    task.resetType = "DELAYED"
                    onDone()
                } else {
                    val timeDialog = TimePickerDialog(this, { _, h, m ->
                        task.resetType = "NEXT_DAY"
                        task.resetHour = h
                        task.resetMinute = m
                        onDone()
                    }, task.resetHour, task.resetMinute, true)
                    timeDialog.show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
