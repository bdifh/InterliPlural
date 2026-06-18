package com.interli.plural

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MoodActivity : BaseActivity() {

    data class ActivityGroup(
        val id: String = UUID.randomUUID().toString(),
        var name: String,
        val activityNames: MutableList<String> = mutableListOf()
    )

    data class MoodEntry(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long,
        val moodEmoji: String,
        val moodRotation: Float,
        val moodLabel: String,
        val moodColor: Int,
        val memberIds: List<String> = emptyList(),
        val activities: List<String> = emptyList(),
        val note: String = "",
        val linkedNoteId: String? = null,
        val linkedTodoId: String? = null
    )

    private val gson = Gson()
    private var selectedEmoji: String? = null
    private var selectedRotation: Float = 0f
    private var selectedMoodLabel: String? = null
    private var selectedMoodColor: Int = Color.GRAY
    private var selectedMemberIds = mutableListOf<String>()
    private var selectedActivities = mutableSetOf<String>()
    private var selectedTimestamp: Long = System.currentTimeMillis()
    private var editingEntryId: String? = null
    private var selectedNoteId: String? = null
    private var selectedTodoId: String? = null

    private lateinit var moodEmojiContainer: LinearLayout
    private lateinit var activityGroupsContainer: LinearLayout
    private lateinit var etMoodNote: EditText
    private lateinit var etActivitySearch: EditText
    private lateinit var selectedMembersText: TextView
    private lateinit var selectedNoteText: TextView
    private lateinit var selectedTodoText: TextView
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView

    private var initialMoodLabel: String? = null
    private var initialActivities = setOf<String>()
    private var initialMemberIds = listOf<String>()
    private var initialNote = ""
    private var initialNoteId: String? = null
    private var initialTodoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_tracker)
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges()) {
                    showUnsavedChangesDialog { navigateToStartPage() }
                } else {
                    navigateToStartPage()
                }
            }
        })

        ColorHelper.applySettings(this)

        moodEmojiContainer = findViewById(R.id.moodEmojiContainer)
        activityGroupsContainer = findViewById(R.id.activityGroupsContainer)
        etMoodNote = findViewById(R.id.etMoodNote)
        etActivitySearch = findViewById(R.id.etActivitySearch)
        selectedMembersText = findViewById(R.id.selectedMembersText)
        selectedNoteText = findViewById(R.id.selectedNoteText)
        selectedTodoText = findViewById(R.id.selectedTodoText)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvSelectedTime = findViewById(R.id.tvSelectedTime)

        findViewById<Button>(R.id.btnAddActivity).setOnClickListener { showAddActivityDialog() }
        findViewById<Button>(R.id.btnManageGroups).setOnClickListener { showManageGroupsDialog() }
        val sp = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val frontEnabled = sp.getBoolean("module_fronting_enabled", true) && sp.getBoolean("sub_fronting_enabled", true)
        val btnSelectMembers = findViewById<Button>(R.id.btnSelectMembers)
        
        if (!frontEnabled) {
            btnSelectMembers.visibility = View.GONE
            findViewById<View>(R.id.selectedMembersText)?.visibility = View.GONE
        } else {
            findViewById<Button>(R.id.btnSelectMembers).setOnClickListener { showMemberSelectionDialog() }
        }
        findViewById<Button>(R.id.btnLinkNote).setOnClickListener { showNoteSelectionDialog() }
        findViewById<Button>(R.id.btnLinkTodo).setOnClickListener { showTodoSelectionDialog() }
        findViewById<Button>(R.id.btnSaveMood).setOnClickListener { saveMood() }
        
        setupNavigationDrawer()
        tvSelectedDate.setOnClickListener { showDatePicker() }
        tvSelectedTime.setOnClickListener { showTimePicker() }

        etActivitySearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderActivityGroups(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val editId = intent.getStringExtra("edit_entry_id")
        if (editId != null) {
            loadEntryForEditing(editId)
        }
        
        captureInitialState()

        updateDateTimeLabel()
        setupMoodButtons()
        renderActivityGroups()
        updateLinkedItemsText()
    }

    override fun onResume() {
        super.onResume()
        setupMoodButtons()
        renderActivityGroups(etActivitySearch.text.toString())
    }

    private fun captureInitialState() {
        initialMoodLabel = selectedMoodLabel
        initialActivities = selectedActivities.toSet()
        initialMemberIds = selectedMemberIds.toList()
        initialNote = etMoodNote.text.toString()
        initialNoteId = selectedNoteId
        initialTodoId = selectedTodoId
    }

    private fun hasChanges(): Boolean {
        return selectedMoodLabel != initialMoodLabel ||
               selectedActivities != initialActivities ||
               selectedMemberIds != initialMemberIds ||
               etMoodNote.text.toString() != initialNote ||
               selectedNoteId != initialNoteId ||
               selectedTodoId != initialTodoId
    }

    private fun loadEntryForEditing(id: String) {
        val entries = loadEntries()
        val entry = entries.find { it.id == id } ?: return
        
        editingEntryId = entry.id
        selectedTimestamp = entry.timestamp
        selectedEmoji = entry.moodEmoji
        selectedRotation = entry.moodRotation
        selectedMoodLabel = entry.moodLabel
        selectedMoodColor = entry.moodColor
        selectedMemberIds = entry.memberIds.toMutableList()
        selectedActivities = entry.activities.toMutableSet()
        selectedNoteId = entry.linkedNoteId
        selectedTodoId = entry.linkedTodoId
        etMoodNote.setText(entry.note)
        
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val peopleJson = prefs.getString("people_list", "[]") ?: "[]"
        val people: List<Person> = gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type)
        val selectedNames = people.filter { selectedMemberIds.contains(it.id) }.map { it.name }
        selectedMembersText.text = if (selectedNames.isEmpty()) "" else selectedNames.joinToString(", ")

        updateLinkedItemsText()
    }

    private fun updateLinkedItemsText() {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        
        if (selectedNoteId != null) {
            val notesJson = prefs.getString("diary_notes", "[]") ?: "[]"
            val notes: List<DiaryNote> = gson.fromJson(notesJson, object : TypeToken<List<DiaryNote>>() {}.type)
            val note = notes.find { it.id == selectedNoteId }
            selectedNoteText.text = if (note != null) getString(R.string.label_linked_note, note.title) else ""
        } else {
            selectedNoteText.text = ""
        }

        if (selectedTodoId != null) {
            val todoJson = prefs.getString("todo_lists", "[]") ?: "[]"
            val todos: List<TodoList> = gson.fromJson(todoJson, object : TypeToken<List<TodoList>>() {}.type)
            val todo = todos.find { it.id == selectedTodoId }
            selectedTodoText.text = if (todo != null) getString(R.string.label_linked_todo, todo.title) else ""
        } else {
            selectedTodoText.text = ""
        }
    }

    private fun updateDateTimeLabel() {
        val dateSdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvSelectedDate.text = dateSdf.format(Date(selectedTimestamp))
        tvSelectedTime.text = timeSdf.format(Date(selectedTimestamp))
        
        val textColor = ColorHelper.getTextColor(this)
        tvSelectedDate.setTextColor(textColor)
        tvSelectedTime.setTextColor(textColor)
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedTimestamp
        val dialog = android.app.DatePickerDialog(this, { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            selectedTimestamp = calendar.timeInMillis
            updateDateTimeLabel()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedTimestamp
        val dialog = android.app.TimePickerDialog(this, { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            selectedTimestamp = calendar.timeInMillis
            updateDateTimeLabel()
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun setupMoodButtons() {
        val moods = listOf(
            "mood_rad" to 0f,
            "mood_good" to 45f,
            "mood_meh" to 90f,
            "mood_bad" to 135f,
            "mood_awful" to 180f
        )

        moodEmojiContainer.removeAllViews()
        moods.forEach { pair ->
            val moodKey = pair.first
            val rotation = pair.second
            val color = ColorHelper.getMoodColor(this, moodKey)
            
            val moodKeysList = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
            val scoreLabel = (moodKeysList.indexOf(moodKey) + 1).toString()
            
            val moodView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(4, 8, 4, 8)
                isClickable = true
                isFocusable = true
                
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }

            val circleFrame = FrameLayout(this).apply {
                setTag(R.id.color_tag, "skip")
                val size = (48 * resources.displayMetrics.density).toInt()
                val lp = LinearLayout.LayoutParams(size, size)
                lp.gravity = Gravity.CENTER
                layoutParams = lp
                
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
                }
                background = shape
            }

            val thumbTv = TextView(this).apply {
                text = "👍"
                textSize = 24f
                this.rotation = rotation
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            circleFrame.addView(thumbTv)

            val labelTv = TextView(this).apply {
                text = scoreLabel
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
                setTextColor(ColorHelper.getTextColor(this@MoodActivity))
            }

            moodView.addView(circleFrame)
            moodView.addView(labelTv)

            val isSelected = (selectedMoodLabel == moodKey)
            
            moodView.alpha = if (isSelected || selectedMoodLabel == null) 1.0f else 0.4f
            moodView.scaleX = if (isSelected) 1.15f else 1.0f
            moodView.scaleY = if (isSelected) 1.15f else 1.0f
            
            val strokeWidth = if (isSelected) (3 * resources.displayMetrics.density).toInt() else (1 * resources.displayMetrics.density).toInt()
            val strokeColor = if (isSelected) Color.BLACK else Color.WHITE
            (circleFrame.background as? GradientDrawable)?.setStroke(strokeWidth, strokeColor)

            moodView.setOnClickListener {
                selectedEmoji = "👍"
                selectedRotation = rotation
                selectedMoodLabel = moodKey
                selectedMoodColor = color
                
                for (i in 0 until moodEmojiContainer.childCount) {
                    val child = moodEmojiContainer.getChildAt(i) as? LinearLayout ?: continue
                    val childMoodKey = moods[i].first
                    val isChildSelected = (childMoodKey == selectedMoodLabel)
                    
                    child.alpha = if (isChildSelected) 1.0f else 0.4f
                    child.scaleX = if (isChildSelected) 1.15f else 1.0f
                    child.scaleY = if (isChildSelected) 1.15f else 1.0f
                    
                    val childFrame = child.getChildAt(0) as? FrameLayout
                    val childBg = childFrame?.background as? GradientDrawable
                    if (childBg != null) {
                        val sWidth = if (isChildSelected) (3 * resources.displayMetrics.density).toInt() else (1 * resources.displayMetrics.density).toInt()
                        val sColor = if (isChildSelected) Color.BLACK else Color.WHITE
                        childBg.setStroke(sWidth, sColor)
                    }
                }
            }
            moodEmojiContainer.addView(moodView)
        }
    }

    private fun renderActivityGroups(query: String = "") {
        activityGroupsContainer.removeAllViews()
        val groups = loadActivityGroups()
        
        if (groups.isEmpty()) {
            val defaultGroup = ActivityGroup(name = getString(R.string.group_general))
            val defaultActs = mutableListOf("Relax", "Friends", "Gaming", "Exercise", "Reading", "Movies", "Cleaning", "Work")
            defaultGroup.activityNames.addAll(defaultActs)
            groups.add(defaultGroup)
            persistActivityGroups(groups)
        }

        val cardBgColor = ColorHelper.getBgColor(this)
        val textColor = ColorHelper.getTextColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val btnTextColor = ColorHelper.getBtnTextColor(this)

        groups.forEach { group ->
            val filteredActivities = group.activityNames.filter { 
                it != "test" && (query.isEmpty() || it.contains(query, ignoreCase = true)) 
            }
            
            if (filteredActivities.isEmpty() && query.isNotEmpty()) return@forEach

            val card = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 16.dpToPx())
                }
                radius = 12f * resources.displayMetrics.density
                setCardBackgroundColor(cardBgColor)
                strokeWidth = 2
                strokeColor = (textColor and 0x33FFFFFF) or 0x33000000
                cardElevation = 2f
            }

            val groupLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            }

            val titleLayout = RelativeLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val titleTv = TextView(this).apply {
                text = group.name
                textSize = 14f
                textStyle = android.graphics.Typeface.BOLD
                setTextColor(textColor)
                alpha = 0.8f
                setPadding(0, 0, 0, 8.dpToPx())
                layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                }
            }
            titleLayout.addView(titleTv)

            val btnReorder = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_sort_alphabetically)
                background = null
                alpha = 0.5f
                setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
                layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }
                setOnClickListener { showReorderActivitiesDialog(group) }
            }
            titleLayout.addView(btnReorder)

            val btnEditGroup = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                background = null
                alpha = 0.5f
                setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
                layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                    addRule(RelativeLayout.LEFT_OF, btnReorder.id.let { if (it == View.NO_ID) {
                        btnReorder.id = View.generateViewId()
                        btnReorder.id
                    } else it })
                }
                setOnClickListener { showEditGroupDialog(group) }
            }
            titleLayout.addView(btnEditGroup)

            groupLayout.addView(titleLayout)

            val gridLayout = GridLayout(this).apply {
                columnCount = 3
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            filteredActivities.forEach { activity ->
                val chip = Chip(this).apply {
                    setTag(R.id.color_tag, "custom")
                    text = activity
                    isCheckable = true
                    isChecked = selectedActivities.contains(activity)
                    
                    val unselectedBg = btnColor
                    val unselectedText = btnTextColor
                    val selectedBg = Color.TRANSPARENT
                    val selectedText = btnColor
                    
                    chipBackgroundColor = ColorStateList.valueOf(if (isChecked) selectedBg else unselectedBg)
                    setTextColor(if (isChecked) selectedText else unselectedText)
                    chipStrokeColor = ColorStateList.valueOf(btnColor)
                    chipStrokeWidth = 1.dpToPx().toFloat()
                    rippleColor = ColorStateList.valueOf(btnColor and 0x33FFFFFF)
                    
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    gravity = Gravity.CENTER

                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedActivities.add(activity) else selectedActivities.remove(activity)
                        chipBackgroundColor = ColorStateList.valueOf(if (checked) selectedBg else unselectedBg)
                        setTextColor(if (checked) selectedText else unselectedText)
                        
                        if (checked && etActivitySearch.text.isNotEmpty()) {
                            etActivitySearch.setText("")
                            renderActivityGroups("")
                        }
                    }
                    setOnLongClickListener {
                        showEditActivityDialog(group, activity)
                        true
                    }
                }
                
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                }
                gridLayout.addView(chip, params)
            }
            groupLayout.addView(gridLayout)
            card.addView(groupLayout)
            activityGroupsContainer.addView(card)
        }
    }

    private fun showReorderActivitiesDialog(group: ActivityGroup) {
        val currentActivities = group.activityNames.toMutableList()
        val recyclerView = androidx.recyclerview.widget.RecyclerView(this).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MoodActivity)
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }

        class ReorderAdapter(val items: MutableList<String>) : androidx.recyclerview.widget.RecyclerView.Adapter<ReorderAdapter.ViewHolder>() {
            inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
                val text: TextView = view.findViewById(android.R.id.text1)
            }
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
                val view = android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
                return ViewHolder(view)
            }
            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                holder.text.text = items[position]
                holder.text.setTextColor(ColorHelper.getTextColor(this@MoodActivity))
            }
            override fun getItemCount() = items.size
            fun moveItem(from: Int, to: Int) {
                val item = items.removeAt(from)
                items.add(to, item)
                notifyItemMoved(from, to)
            }
        }

        val reorderAdapter = ReorderAdapter(currentActivities)
        recyclerView.adapter = reorderAdapter

        val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView, vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                reorderAdapter.moveItem(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(recyclerView)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_manage_groups) + ": " + group.name)
            .setView(recyclerView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val groups = loadActivityGroups()
                groups.find { it.id == group.id }?.let {
                    it.activityNames.clear()
                    it.activityNames.addAll(currentActivities)
                    persistActivityGroups(groups)
                    renderActivityGroups(etActivitySearch.text.toString())
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private var TextView.textStyle: Int
        get() = typeface?.style ?: android.graphics.Typeface.NORMAL
        set(value) {
            setTypeface(typeface, value)
        }

    private fun showEditActivityDialog(currentGroup: ActivityGroup, activityName: String) {
        val groups = loadActivityGroups()
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 0)
        }

        val input = EditText(this).apply {
            setText(activityName)
            hint = getString(R.string.hint_activity_name)
        }
        container.addView(input)

        val spinner = Spinner(this)
        val groupNames = groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).map { it.name }
        spinner.adapter = ColorHelper.createThemedAdapter(this, groupNames)
        val currentIdx = groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).indexOfFirst { it.id == currentGroup.id }
        if (currentIdx != -1) spinner.setSelection(currentIdx)
        
        container.addView(TextView(this).apply { 
            text = getString(R.string.label_move_to_group)
            setPadding(0, 16.dpToPx(), 0, 4.dpToPx())
            setTextColor(ColorHelper.getTextColor(this@MoodActivity))
        })
        container.addView(spinner)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_activity))
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = input.text.toString().trim()
                val targetGroupIdx = spinner.selectedItemPosition
                
                if (newName.isNotEmpty() && targetGroupIdx != -1) {
                    val targetGroup = groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })[targetGroupIdx]
                    
                    val oldGroup = groups.find { it.id == currentGroup.id }
                    oldGroup?.activityNames?.remove(activityName)
                    
                    if (!targetGroup.activityNames.contains(newName)) {
                        targetGroup.activityNames.add(newName)
                    }
                    
                    persistActivityGroups(groups)
                    renderActivityGroups()
                }
            }
            .setNeutralButton(getString(R.string.delete), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            
        dialog.setOnShowListener {
            val btnDelete = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            var deleteClicks = 0
            btnDelete.setOnClickListener {
                deleteClicks++
                if (deleteClicks >= 8) {
                    val currentGroups = loadActivityGroups()
                    val targetGroup = currentGroups.find { it.id == currentGroup.id }
                    targetGroup?.activityNames?.remove(activityName)
                    persistActivityGroups(currentGroups)
                    renderActivityGroups()
                    dialog.dismiss()
                } else {
                    btnDelete.text = getString(R.string.delete_activity_8x, 8 - deleteClicks)
                }
            }
        }
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun showAddActivityDialog() {
        val groups = loadActivityGroups()
        if (groups.isEmpty()) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 0)
        }

        val input = EditText(this).apply { hint = getString(R.string.hint_activity_name) }
        container.addView(input)

        val spinner = Spinner(this)
        val sortedGroups = groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val groupNames = sortedGroups.map { it.name }
        spinner.adapter = ColorHelper.createThemedAdapter(this, groupNames)
        container.addView(TextView(this).apply { 
            text = getString(R.string.label_select_group)
            setPadding(0, 16.dpToPx(), 0, 4.dpToPx()) 
            setTextColor(ColorHelper.getTextColor(this@MoodActivity))
        })
        container.addView(spinner)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_activity))
            .setView(container)
            .setPositiveButton(getString(R.string.action_add)) { _, _ ->
                val name = input.text.toString().trim()
                val groupIdx = spinner.selectedItemPosition
                if (name.isNotEmpty() && groupIdx >= 0) {
                    val targetGroup = sortedGroups[groupIdx]
                    if (!targetGroup.activityNames.contains(name)) {
                        targetGroup.activityNames.add(name)
                        persistActivityGroups(groups)
                        renderActivityGroups()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun showManageGroupsDialog() {
        val groups = loadActivityGroups()
        val sortedGroups = groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val names = sortedGroups.map { it.name }.toMutableList()
        names.add(getString(R.string.action_new_group))

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_manage_groups))
            .setItems(names.toTypedArray()) { _, which ->
                if (which == names.size - 1) {
                    showCreateGroupDialog()
                } else {
                    showEditGroupDialog(sortedGroups[which])
                }
            }
            .setNegativeButton(getString(R.string.close), null)
            .create()
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this).apply { hint = getString(R.string.hint_group_name) }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_new_group_title))
            .setView(input)
            .setPositiveButton(getString(R.string.action_add)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val groups = loadActivityGroups()
                    groups.add(ActivityGroup(name = name))
                    persistActivityGroups(groups)
                    renderActivityGroups()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun showEditGroupDialog(group: ActivityGroup) {
        val input = EditText(this).apply { 
            hint = getString(R.string.hint_group_name)
            setText(group.name)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_group_title))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val groups = loadActivityGroups()
                    groups.find { it.id == group.id }?.name = newName
                    persistActivityGroups(groups)
                    renderActivityGroups()
                }
            }
            .setNeutralButton(getString(R.string.delete), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            val btnDelete = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            var deleteClicks = 0
            btnDelete.setOnClickListener {
                deleteClicks++
                if (deleteClicks >= 8) {
                    val groups = loadActivityGroups()
                    val groupToDelete = groups.find { it.id == group.id }
                    if (groupToDelete != null) {
                        val activitiesToMove = groupToDelete.activityNames.toList()
                        groups.remove(groupToDelete)
                        
                        if (activitiesToMove.isNotEmpty()) {
                            val generalName = getString(R.string.group_general)
                            var fallbackGroup = groups.find { it.name.equals(generalName, ignoreCase = true) }
                            if (fallbackGroup == null && groups.isNotEmpty()) {
                                fallbackGroup = groups[0]
                            }
                            if (fallbackGroup == null) {
                                fallbackGroup = ActivityGroup(name = generalName)
                                groups.add(fallbackGroup)
                            }
                            
                            activitiesToMove.forEach { act ->
                                if (!fallbackGroup.activityNames.contains(act)) {
                                    fallbackGroup.activityNames.add(act)
                                }
                            }
                        }
                    }
                    persistActivityGroups(groups)
                    renderActivityGroups()
                    dialog.dismiss()
                } else {
                    btnDelete.text = getString(R.string.delete_group_8x, 8 - deleteClicks)
                }
            }
        }
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
        input.setTextColor(ColorHelper.getTextColor(this))
    }

    private fun showMemberSelectionDialog() {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val peopleJson = prefs.getString("people_list", "[]") ?: "[]"
        val groupsJson = prefs.getString("groups_list", "[]") ?: "[]"
        val people: List<Person> = gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type)
        val filteredPeople = people.filter { !it.isArchived && !it.isSysmediaOnly }
        val groups: List<Group> = gson.fromJson(groupsJson, object : TypeToken<List<Group>>() {}.type)
        
        if (filteredPeople.isEmpty()) return
        
        DialogHelper.showMemberSelectionDialog(
            this,
            getString(R.string.action_link_members),
            filteredPeople,
            groups,
            selectedMemberIds
        ) { newList ->
            selectedMemberIds.clear()
            selectedMemberIds.addAll(newList)
            val selectedNames = people.filter { selectedMemberIds.contains(it.id) && !it.isArchived && !it.isSysmediaOnly }.map { it.name }
            selectedMembersText.text = if (selectedNames.isEmpty()) "" else selectedNames.joinToString(", ")
        }
    }

    private fun showNoteSelectionDialog() {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("diary_notes", "[]") ?: "[]"
        val notes: List<DiaryNote> = gson.fromJson(json, object : TypeToken<List<DiaryNote>>() {}.type)
        
        if (notes.isEmpty()) {
            Toast.makeText(this, "No notes found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val sortedNotes = notes.sortedByDescending { it.timestamp }
        val items = mutableListOf<String>()
        items.add("None")
        items.addAll(sortedNotes.map { it.title.ifEmpty { "Unnamed note" } })
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_link_note))
            .setItems(items.toTypedArray()) { _, which ->
                selectedNoteId = if (which == 0) null else sortedNotes[which - 1].id
                updateLinkedItemsText()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun showTodoSelectionDialog() {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("todo_lists", "[]") ?: "[]"
        val todos: List<TodoList> = gson.fromJson(json, object : TypeToken<List<TodoList>>() {}.type)
        
        if (todos.isEmpty()) {
            Toast.makeText(this, "No to-do lists found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val sortedTodos = todos.sortedByDescending { it.timestamp }
        val items = mutableListOf<String>()
        items.add("None")
        items.addAll(sortedTodos.map { it.title.ifEmpty { "Unnamed list" } })
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_link_todo))
            .setItems(items.toTypedArray()) { _, which ->
                selectedTodoId = if (which == 0) null else sortedTodos[which - 1].id
                updateLinkedItemsText()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            
        dialog.show()
        ColorHelper.styleSupportAlertDialog(dialog, this)
    }

    private fun saveMood() {
        val emoji = selectedEmoji ?: run {
            Toast.makeText(this, "Please select a mood", Toast.LENGTH_SHORT).show()
            return
        }

        val currentNote = etMoodNote.text?.toString()?.trim() ?: ""
        val entries = loadEntries()
        
        if (editingEntryId == null) {
            val entry = MoodEntry(
                timestamp = selectedTimestamp,
                moodEmoji = emoji,
                moodRotation = selectedRotation,
                moodLabel = selectedMoodLabel ?: "",
                moodColor = selectedMoodColor,
                memberIds = selectedMemberIds.toList(),
                activities = selectedActivities.toList(),
                note = currentNote,
                linkedNoteId = selectedNoteId,
                linkedTodoId = selectedTodoId
            )
            entries.add(entry)
        } else {
            var idx = entries.indexOfFirst { it.id == editingEntryId }
            if (idx == -1) {
                idx = entries.indexOfFirst { it.timestamp == selectedTimestamp }
            }

            if (idx != -1) {
                entries[idx] = entries[idx].copy(
                    timestamp = selectedTimestamp,
                    moodEmoji = emoji,
                    moodRotation = selectedRotation,
                    moodLabel = selectedMoodLabel ?: "",
                    moodColor = selectedMoodColor,
                    memberIds = selectedMemberIds.toList(),
                    activities = selectedActivities.toList(),
                    note = currentNote,
                    linkedNoteId = selectedNoteId,
                    linkedTodoId = selectedTodoId
                )
            } else {
                val entry = MoodEntry(
                    timestamp = selectedTimestamp,
                    moodEmoji = emoji,
                    moodRotation = selectedRotation,
                    moodLabel = selectedMoodLabel ?: "",
                    moodColor = selectedMoodColor,
                    memberIds = selectedMemberIds.toList(),
                    activities = selectedActivities.toList(),
                    note = currentNote,
                    linkedNoteId = selectedNoteId,
                    linkedTodoId = selectedTodoId
                )
                entries.add(entry)
            }
        }
        
        persistEntries(entries)

        selectedEmoji = null
        selectedMoodLabel = null
        selectedMoodColor = Color.GRAY
        selectedActivities.clear()
        selectedMemberIds.clear()
        selectedTimestamp = System.currentTimeMillis()
        editingEntryId = null
        etMoodNote.setText("")
        selectedMembersText.text = ""
        
        updateDateTimeLabel()
        setupMoodButtons()
        renderActivityGroups()
        
        Toast.makeText(this, getString(R.string.entry_saved), Toast.LENGTH_SHORT).show()
        val intentTimeline = android.content.Intent(this, MoodTimelineActivity::class.java)
        startActivity(intentTimeline)
        finish()
    }

    private fun loadActivityGroups(): MutableList<ActivityGroup> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("activity_groups", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<ActivityGroup>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
    }

    private fun persistActivityGroups(list: List<ActivityGroup>) {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        prefs.edit().putString("activity_groups", gson.toJson(list)).apply()
    }

    fun loadEntries(): MutableList<MoodEntry> {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val json = prefs.getString("mood_entries", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<MoodEntry>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
    }

    private fun persistEntries(list: List<MoodEntry>) {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        prefs.edit().putString("mood_entries", gson.toJson(list)).apply()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
