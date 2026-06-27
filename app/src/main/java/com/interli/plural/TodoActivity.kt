package com.interli.plural

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
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


sealed class TodoItem {
    data class BundleHeader(val bundle: TodoBundle) : TodoItem()
    data class ListCard(val list: TodoList) : TodoItem()
}

class TodoActivity : BaseActivity() {

    private lateinit var todoLists: MutableList<TodoList>
    private lateinit var todoBundles: MutableList<TodoBundle>
    private lateinit var people: List<Person>
    private lateinit var allNotes: List<DiaryNote>
    private val gson = Gson()
    private lateinit var rvTodoMain: RecyclerView
    private lateinit var todoAdapter: TodoMainAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_todo)

        ColorHelper.applySettings(this)

        rvTodoMain = findViewById(R.id.rvTodoMain)
        rvTodoMain.layoutManager = LinearLayoutManager(this)
        todoAdapter = TodoMainAdapter()
        rvTodoMain.adapter = todoAdapter

        setupItemTouchHelper()

        findViewById<Button>(R.id.btnAddTodoList).setOnClickListener { 
            val intent = android.content.Intent(this, EditTodoListActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnAddTodoBundle).setOnClickListener {
            showAddBundleDialog()
        }
        
        val color = ColorHelper.getBtnColor(this)
        val btnTextColor = ColorHelper.getBtnTextColor(this)
        findViewById<Button>(R.id.btnAddTodoBundle).apply {
            setBackgroundColor(color)
            setTextColor(btnTextColor)
            if (this is com.google.android.material.button.MaterialButton) {
                strokeWidth = 0
                rippleColor = android.content.res.ColorStateList.valueOf(btnTextColor and 0x33FFFFFF)
            }
        }
        
        setupNavigationDrawer()
        loadData()
        renderLists()
    }

    private fun setupItemTouchHelper() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                    todoAdapter.moveItem(fromPos, toPos)
                    return true
                }
                return false
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                saveManualOrder()
                renderLists()
            }
        })
        itemTouchHelper.attachToRecyclerView(rvTodoMain)
        
        todoAdapter.onDragStart = { viewHolder ->
            if (viewHolder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                itemTouchHelper.startDrag(viewHolder)
            }
        }
    }

    private fun saveManualOrder() {
        val currentItems = todoAdapter.getCurrentItems()
        var order = 0
        
        currentItems.forEach { item ->
            when (item) {
                is TodoItem.BundleHeader -> {
                    item.bundle.manualOrder = order++
                }
                is TodoItem.ListCard -> {
                    item.list.manualOrder = order++
                }
            }
        }
        saveData()
    }

    private fun showAddBundleDialog() {
        val input = EditText(this).apply { hint = getString(R.string.hint_note_title) }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_new_bundle)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    todoBundles.add(TodoBundle(name = name, manualOrder = todoBundles.size + todoLists.size))
                    saveData()
                    renderLists()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun showEditBundleDialog(bundle: TodoBundle) {
        val input = EditText(this).apply { 
            hint = getString(R.string.hint_note_title)
            setText(bundle.name)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_todo_list)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                bundle.name = input.text.toString().trim()
                saveData()
                renderLists()
            }
            .setNeutralButton(R.string.delete, null) // Set to null first to override listener
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))

        var deleteClicks = 8
        val deleteBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
        deleteBtn?.text = "${getString(R.string.delete)} ($deleteClicks)"
        deleteBtn?.setOnClickListener {
            deleteClicks--
            if (deleteClicks <= 0) {
                todoLists.forEach { if (it.bundleId == bundle.id) it.bundleId = null }
                todoBundles.remove(bundle)
                saveData()
                renderLists()
                dialog.dismiss()
            } else {
                deleteBtn.text = "${getString(R.string.delete)} ($deleteClicks)"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
        autoResetPastRecurringTasks()
        renderLists()
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val todoJson = sharedPref.getString("todo_lists", "[]") ?: "[]"
        todoLists = try {
            gson.fromJson(todoJson, object : TypeToken<MutableList<TodoList>>() {}.type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }

        val bundlesJson = sharedPref.getString("todo_bundles", "[]") ?: "[]"
        todoBundles = try {
            gson.fromJson(bundlesJson, object : TypeToken<MutableList<TodoBundle>>() {}.type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }

        val peopleJson = sharedPref.getString("people_list", "[]") ?: "[]"
        people = try {
            gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val notesJson = sharedPref.getString("diary_notes", "[]") ?: "[]"
        allNotes = try {
            gson.fromJson(notesJson, object : TypeToken<List<DiaryNote>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit()
            .putString("todo_lists", gson.toJson(todoLists))
            .putString("todo_bundles", gson.toJson(todoBundles))
            .apply()
    }

    private fun renderLists() {
        val items = mutableListOf<TodoItem>()

        val bundlesMap = todoBundles.filter { it.id != null }.associateBy { it.id }
        val topLevelLists = todoLists.filter { it.bundleId == null || !bundlesMap.containsKey(it.bundleId) }

        val allContainers = mutableListOf<Any>()
        allContainers.addAll(todoBundles)
        allContainers.addAll(topLevelLists)
        
        val sortedContainers = allContainers.sortedBy { 
            when (it) {
                is TodoBundle -> it.manualOrder
                is TodoList -> it.manualOrder
                else -> 0
            }
        }

        sortedContainers.forEach { container ->
            if (container is TodoBundle) {
                items.add(TodoItem.BundleHeader(container))
                if (container.isExpanded) {
                    val childLists = todoLists.filter { it.bundleId != null && it.bundleId == container.id }.sortedBy { it.manualOrder }
                    childLists.forEach { items.add(TodoItem.ListCard(it)) }
                }
            } else if (container is TodoList) {
                items.add(TodoItem.ListCard(container))
            }
        }

        todoAdapter.setItems(items)
    }

    private inner class TodoMainAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: MutableList<TodoItem> = mutableListOf()
        var onDragStart: ((RecyclerView.ViewHolder) -> Unit)? = null

        fun setItems(newItems: List<TodoItem>) {
            items = newItems.toMutableList()
            notifyDataSetChanged()
        }
        
        fun getCurrentItems() = items

        fun moveItem(from: Int, to: Int) {
            Collections.swap(items, from, to)
            notifyItemMoved(from, to)
        }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is TodoItem.BundleHeader -> 1
            is TodoItem.ListCard -> 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                1 -> BundleViewHolder(com.google.android.material.card.MaterialCardView(this@TodoActivity))
                else -> ListViewHolder(com.google.android.material.card.MaterialCardView(this@TodoActivity))
            }
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val textColor = ColorHelper.getTextColor(this@TodoActivity)
            val bgColor = ColorHelper.getBgColor(this@TodoActivity)
            
            when (val item = items[position]) {
                is TodoItem.BundleHeader -> {
                    (holder as BundleViewHolder).bind(item.bundle)
                }
                is TodoItem.ListCard -> {
                    (holder as ListViewHolder).bind(item.list, textColor, bgColor)
                }
            }
        }

        inner class BundleViewHolder(val card: com.google.android.material.card.MaterialCardView) : RecyclerView.ViewHolder(card) {
            @android.annotation.SuppressLint("ClickableViewAccessibility")
            fun bind(bundle: TodoBundle) {
                card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 8.dpToPx()) }
                card.radius = 8f * resources.displayMetrics.density
                card.setCardBackgroundColor(ColorHelper.getBtnColor(this@TodoActivity))
                card.cardElevation = 4f
                card.removeAllViews()

                val content = LinearLayout(this@TodoActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
                }
                
                val ivDrag = ImageView(this@TodoActivity).apply {
                    setImageResource(android.R.drawable.ic_menu_sort_by_size)
                    setColorFilter(ColorHelper.getBtnTextColor(this@TodoActivity))
                    setPadding(0, 0, 8.dpToPx(), 0)
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, event ->
                        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                            onDragStart?.invoke(this@BundleViewHolder)
                        }
                        true
                    }
                }
                content.addView(ivDrag)

                val tvName = TextView(this@TodoActivity).apply {
                    text = bundle.name
                    textSize = 16f
                    textStyle = android.graphics.Typeface.BOLD
                    setTextColor(ColorHelper.getBtnTextColor(this@TodoActivity))
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                content.addView(tvName)
                
                val btnExpand = ImageView(this@TodoActivity).apply {
                    setImageResource(android.R.drawable.arrow_down_float)
                    rotation = if (bundle.isExpanded) 0f else -90f
                    setColorFilter(ColorHelper.getBtnTextColor(this@TodoActivity))
                }
                content.addView(btnExpand)
                
                val btnEdit = ImageButton(this@TodoActivity).apply {
                    setImageResource(android.R.drawable.ic_menu_edit)
                    background = null
                    setColorFilter(ColorHelper.getBtnTextColor(this@TodoActivity))
                    alpha = 0.6f
                    setOnClickListener { showEditBundleDialog(bundle) }
                }
                content.addView(btnEdit)

                card.addView(content)

                card.setOnClickListener {
                    bundle.isExpanded = !bundle.isExpanded
                    saveData()
                    renderLists()
                }
                card.setOnLongClickListener { showEditBundleDialog(bundle); true }
            }
        }

        inner class ListViewHolder(val card: com.google.android.material.card.MaterialCardView) : RecyclerView.ViewHolder(card) {
            @android.annotation.SuppressLint("ClickableViewAccessibility")
            fun bind(list: TodoList, textColor: Int, bgColor: Int) {
                val isNested = list.bundleId != null
                val sp = card.context.getSharedPreferences("settings_prefs", MODE_PRIVATE)
                val frontEnabled = sp.getBoolean("module_fronting_enabled", true) && sp.getBoolean("sub_fronting_enabled", true)
                card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { 
                    setMargins(if (isNested) 32.dpToPx() else 0, 0, 0, 16.dpToPx()) 
                }
                card.radius = 12f * resources.displayMetrics.density
                card.setCardBackgroundColor(bgColor)
                card.strokeWidth = 1
                card.strokeColor = (textColor and 0x33FFFFFF) or 0x33000000
                card.cardElevation = 2f
                card.removeAllViews()

                val content = LinearLayout(this@TodoActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                }

                val titleRow = LinearLayout(this@TodoActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                
                val ivDrag = ImageView(this@TodoActivity).apply {
                    setImageResource(android.R.drawable.ic_menu_sort_by_size)
                    setColorFilter(textColor)
                    alpha = 0.3f
                    setPadding(0, 0, 12.dpToPx(), 0)
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, event ->
                        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                            onDragStart?.invoke(this@ListViewHolder)
                        }
                        true
                    }
                }
                titleRow.addView(ivDrag)

                val titleTv = TextView(this@TodoActivity).apply {
                    text = list.title
                    textSize = 18f
                    textStyle = android.graphics.Typeface.BOLD
                    setTextColor(textColor)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    
                    // Enable horizontal scrolling for long titles
                    setSingleLine(true)
                    ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                    marqueeRepeatLimit = -1
                    isSelected = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
                titleRow.addView(titleTv)
                val btnEdit = ImageButton(this@TodoActivity).apply {
                    setImageResource(android.R.drawable.ic_menu_edit)
                    background = null
                    alpha = 0.4f
                    setOnClickListener { 
                        val intent = android.content.Intent(this@TodoActivity, EditTodoListActivity::class.java)
                        intent.putExtra("list_id", list.id)
                        startActivity(intent)
                    }
                }
                titleRow.addView(btnEdit)
                content.addView(titleRow)

                val listMediaContainer = LinearLayout(this@TodoActivity).apply { orientation = LinearLayout.VERTICAL }
                MediaEmbedHelper.addEmbedsToContainer(listMediaContainer, list.title)
                content.addView(listMediaContainer)

                list.deadline?.let { dl ->
                    content.addView(TextView(this@TodoActivity).apply {
                        text = getString(R.string.deadline, SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(dl)))
                        textSize = 12f
                        alpha = 0.8f
                        setTextColor(textColor)
                        setPadding(0, 0, 0, 4.dpToPx())
                    })
                }

                if (frontEnabled && list.linkedMemberIds.isNotEmpty()) {
                    val badgesRow = com.google.android.material.chip.ChipGroup(this@TodoActivity).apply { 
                        setPadding(0, 0, 0, 8.dpToPx()) 
                        chipSpacingVertical = 0
                    }
                    addMemberBadges(badgesRow, list.linkedMemberIds)
                    content.addView(badgesRow)
                }

                val note = allNotes.find { it.id == list.linkedNoteId }
                if (note != null) {
                    content.addView(TextView(this@TodoActivity).apply {
                        text = "${getString(R.string.label_linked_note)}: ${note.title}"
                        textSize = 12f
                        alpha = 0.8f
                        setTextColor(textColor)
                        setTypeface(null, android.graphics.Typeface.ITALIC)
                        setPadding(0, 0, 0, 8.dpToPx())
                    })
                }

                list.tasks.forEach { task ->
                    val taskRow = LinearLayout(this@TodoActivity).apply {
                        val indent = task.indentLevel * 24
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(indent.dpToPx(), 4.dpToPx(), 0, 4.dpToPx())
                    }
                    val statusBtn = TextView(this@TodoActivity).apply {
                        text = getStatusChar(task.status)
                        textSize = 20f
                        setPadding(12.dpToPx(), 0.dpToPx(), 20.dpToPx(), 0.dpToPx())
                        setTextColor(textColor)
                        setOnClickListener {
                            val nextStatus = getNextStatus(task.status)
                            task.status = nextStatus
                            text = getStatusChar(task.status)
                            saveData()
                            
                            if (nextStatus == "CHECKED" && task.recurrence != null && task.resetType == "DELAYED") {
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    if (task.status == "CHECKED") {
                                        handleRecurrence(task)
                                        saveData()
                                        renderLists()
                                    }
                                }, 2000)
                            }
                            renderLists()
                        }
                    }
                    taskRow.addView(statusBtn)

                    val taskTextContainer = LinearLayout(this@TodoActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                        setOnClickListener { 
                            val intent = android.content.Intent(this@TodoActivity, EditTodoListActivity::class.java)
                            intent.putExtra("list_id", list.id)
                            startActivity(intent)
                        }
                    }
                    taskTextContainer.addView(TextView(this@TodoActivity).apply { text = task.title; textSize = 15f; setTextColor(textColor) })
                    val mediaContainer = LinearLayout(this@TodoActivity).apply { orientation = LinearLayout.VERTICAL }
                    MediaEmbedHelper.addEmbedsToContainer(mediaContainer, task.title)
                    taskTextContainer.addView(mediaContainer)
                    
                    task.deadline?.let { dl ->
                        taskTextContainer.addView(TextView(this@TodoActivity).apply {
                            val cal = Calendar.getInstance().apply { timeInMillis = dl }
                            val isMidnight = cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0
                            text = getString(R.string.deadline, SimpleDateFormat(if (isMidnight) "dd/MM" else "dd/MM HH:mm", Locale.getDefault()).format(Date(dl)))
                            textSize = 11f; alpha = 0.6f; setTextColor(textColor)
                        })
                    }
                    taskRow.addView(taskTextContainer)

                    if (task.recurrence != null) {
                        taskRow.addView(TextView(this@TodoActivity).apply {
                            text = "↻"; textSize = 20f; setPadding(12.dpToPx(), 0, 12.dpToPx(), 0); setTextColor(textColor); alpha = 0.7f
                            setOnClickListener { handleRecurrence(task); saveData(); renderLists() }
                        })
                    }
                    if (frontEnabled && task.linkedMemberIds.isNotEmpty()) {
                        val taskBadges = com.google.android.material.chip.ChipGroup(this@TodoActivity).apply {
                            chipSpacingVertical = 0
                        }
                        addMemberBadges(taskBadges, task.linkedMemberIds)
                        taskRow.addView(taskBadges, LinearLayout.LayoutParams(0, -2, 0.4f).apply { marginStart = 8.dpToPx() })
                    }
                    content.addView(taskRow)
                }



                card.addView(content)
                card.setOnClickListener {
                    val intent = android.content.Intent(this@TodoActivity, EditTodoListActivity::class.java)
                    intent.putExtra("list_id", list.id)
                    startActivity(intent)
                }
            }
        }
    }

    private fun handleRecurrence(task: TodoTask, showToast: Boolean = true) {
        val baseTime = task.deadline ?: System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = baseTime
        val wasMidnight = cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0
        
        when (task.recurrence) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "MONTHLY" -> cal.add(Calendar.MONTH, 1)
            "YEARLY" -> cal.add(Calendar.YEAR, 1)
            "CUSTOM" -> {
                val days = task.recurrenceDays ?: emptyList()
                if (days.isNotEmpty()) {
                    var found = false
                    for (i in 1..7) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                        val d = when(cal.get(Calendar.DAY_OF_WEEK)) {
                            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
                            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6; Calendar.SUNDAY -> 7; else -> 1
                        }
                        if (days.contains(d)) { found = true; break }
                    }
                    if (!found) cal.add(Calendar.WEEK_OF_YEAR, 1)
                } else cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        if (wasMidnight) { cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0) }
        task.deadline = cal.timeInMillis
        task.status = "EMPTY"
        if (showToast) Toast.makeText(this, getString(R.string.entry_saved), Toast.LENGTH_SHORT).show()
    }

    private fun getStatusChar(status: String): String = when (status) {
        "CHECKED" -> "✓"; "FORWARD" -> "→"; "BACKWARD" -> "←"; "WAITING" -> "⏳"; "CANCELED" -> "✕"; "QUESTION" -> "?"; else -> "☐"
    }

    private fun getNextStatus(current: String): String {
        val statuses = listOf("EMPTY", "CHECKED", "FORWARD", "BACKWARD", "WAITING", "CANCELED", "QUESTION")
        return statuses[(statuses.indexOf(current) + 1) % statuses.size]
    }

    private fun addMemberBadges(container: ViewGroup, memberIds: List<String>) {
        memberIds.forEach { id ->
            val person = people.find { it.id == id } ?: return@forEach
            val badge = TextView(this).apply {
                text = person.name; textSize = 9f; setPadding(8.dpToPx(), 2.dpToPx(), 8.dpToPx(), 2.dpToPx())
                setTextColor(Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 100f; setColor(person.profileColor) }
                layoutParams = (if (container is LinearLayout) LinearLayout.LayoutParams(-2, -2) else ViewGroup.MarginLayoutParams(-2, -2)).apply { 
                    setMargins(4.dpToPx(), 2.dpToPx(), 4.dpToPx(), 2.dpToPx())
                }
            }
            container.addView(badge)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private var TextView.textStyle: Int
        get() = typeface?.style ?: android.graphics.Typeface.NORMAL
        set(value) { setTypeface(typeface, value) }

    private fun autoResetPastRecurringTasks() {
        val now = Calendar.getInstance()
        var changed = false
        
        todoLists.forEach { list ->
            list.tasks.forEach { task ->
                if (task.recurrence != null && task.status == "CHECKED") {
                    if (task.deadline == null) {
                        val today = Calendar.getInstance().apply { 
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }
                        task.deadline = today.timeInMillis
                        changed = true
                    }

                    // Reset if the current time is past the deadline
                    while (now.timeInMillis > (task.deadline ?: 0L)) {
                        handleRecurrence(task, showToast = false)
                        changed = true
                    }
                }
            }
        }
        if (changed) {
            saveData()
            renderLists()
        }
    }
}
