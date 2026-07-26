package com.interli.plural.features.mood

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.core.DialogHelper
import com.interli.plural.DiaryNote
import com.interli.plural.features.mood.MoodTimelineActivity
import com.interli.plural.Group
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.TodoList
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
        val linkedTodoId: String? = null,
        val imageUri: String? = null
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
    private var selectedImageUri: String? = null

    private lateinit var moodEmojiContainer: LinearLayout
    private lateinit var activityGroupsContainer: LinearLayout
    private lateinit var etMoodNote: EditText
    private lateinit var etActivitySearch: EditText
    private lateinit var selectedMembersText: TextView
    private lateinit var selectedNoteText: TextView
    private lateinit var selectedTodoText: TextView
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var imgMoodPreview: ImageView

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
                if (hasChanges()) showUnsavedChangesDialog { navigateToStartPage() } else navigateToStartPage()
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
        imgMoodPreview = findViewById(R.id.imgMoodPreview)

        findViewById<Button>(R.id.btnAddActivity).setOnClickListener { showAddActivityDialog() }
        findViewById<Button>(R.id.btnManageGroups).setOnClickListener { showManageGroupsDialog() }
        findViewById<Button>(R.id.btnSelectMembers).setOnClickListener { showMemberSelectionDialog() }
        findViewById<Button>(R.id.btnLinkNote).setOnClickListener { showNoteSelectionDialog() }
        findViewById<Button>(R.id.btnLinkTodo).setOnClickListener { showTodoSelectionDialog() }
        findViewById<Button>(R.id.btnSaveMood).setOnClickListener { saveMood() }
        findViewById<Button>(R.id.btnAddMoodImage).setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, 1004)
        }

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
        if (editId != null) loadEntryForEditing(editId)

        captureInitialState()
        updateDateTimeLabel()
        setupMoodButtons()
        renderActivityGroups()
        updateLinkedItemsText()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1004 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedImageUri = uri.toString()
                imgMoodPreview.setImageURI(uri)
                imgMoodPreview.visibility = View.VISIBLE
            }
        }
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
        selectedImageUri = entry.imageUri
        etMoodNote.setText(entry.note)

        if (selectedImageUri != null) {
            imgMoodPreview.setImageURI(android.net.Uri.parse(selectedImageUri))
            imgMoodPreview.visibility = View.VISIBLE
        }

        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val peopleJson = prefs.getString("people_list", "[]") ?: "[]"
        val people: List<Person> = gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type)
        val selectedNames = people.filter { selectedMemberIds.contains(it.id) && !it.isArchived }.map { it.name }
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
        } else selectedNoteText.text = ""

        if (selectedTodoId != null) {
            val todoJson = prefs.getString("todo_lists", "[]") ?: "[]"
            val todos: List<TodoList> = gson.fromJson(todoJson, object : TypeToken<List<TodoList>>() {}.type)
            val todo = todos.find { it.id == selectedTodoId }
            selectedTodoText.text = if (todo != null) getString(R.string.label_linked_todo, todo.title) else ""
        } else selectedTodoText.text = ""
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
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val dialog = android.app.DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)
            selectedTimestamp = calendar.timeInMillis
            updateDateTimeLabel()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
        val dialog = android.app.TimePickerDialog(this, { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            selectedTimestamp = calendar.timeInMillis
            updateDateTimeLabel()
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
        dialog.show()
        ColorHelper.styleAlertDialog(dialog, this)
    }

    private fun setupMoodButtons() {
        val moods = listOf("mood_rad" to 0f, "mood_good" to 45f, "mood_meh" to 90f, "mood_bad" to 135f, "mood_awful" to 180f)
        moodEmojiContainer.removeAllViews()
        moods.forEach { pair ->
            val moodKey = pair.first
            val rotation = pair.second
            val color = ColorHelper.getMoodColor(this, moodKey)
            val scoreLabel = when(moodKey) { "mood_rad" -> "5"; "mood_good" -> "4"; "mood_meh" -> "3"; "mood_bad" -> "2"; else -> "1" }

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
                layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
                }
            }

            circleFrame.addView(TextView(this).apply {
                text = "👍"
                textSize = 24f
                this.rotation = rotation
                gravity = Gravity.CENTER
            })

            moodView.addView(circleFrame)
            moodView.addView(TextView(this).apply {
                text = scoreLabel
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(0, 4.dpToPx(), 0, 0)
                setTextColor(ColorHelper.getTextColor(this@MoodActivity))
            })

            val isSelected = (selectedMoodLabel == moodKey)
            moodView.alpha = if (isSelected || selectedMoodLabel == null) 1.0f else 0.4f
            moodView.scaleX = if (isSelected) 1.15f else 1.0f
            moodView.scaleY = if (isSelected) 1.15f else 1.0f
            if (isSelected) (circleFrame.background as GradientDrawable).setStroke(3.dpToPx(), Color.BLACK)

            moodView.setOnClickListener {
                selectedEmoji = "👍"
                selectedRotation = rotation
                selectedMoodLabel = moodKey
                selectedMoodColor = color
                setupMoodButtons() // Herteken voor selectie-effect
            }
            moodEmojiContainer.addView(moodView)
        }
    }

    private fun renderActivityGroups(query: String = "") {
        activityGroupsContainer.removeAllViews()
        val groups = loadActivityGroups()
        val cardBgColor = ColorHelper.getBgColor(this)
        val textColor = ColorHelper.getTextColor(this)
        val btnColor = ColorHelper.getBtnColor(this)
        val btnTextColor = ColorHelper.getBtnTextColor(this)

        groups.forEach { group ->
            val filteredActivities = group.activityNames.filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
            if (filteredActivities.isEmpty() && query.isNotEmpty()) return@forEach

            val card = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 16.dpToPx()) }
                radius = 12f * resources.displayMetrics.density
                setCardBackgroundColor(cardBgColor)
                strokeWidth = 2
                strokeColor = (textColor and 0x33FFFFFF) or 0x33000000
            }

            val groupLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            }

            groupLayout.addView(TextView(this).apply {
                text = group.name
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                alpha = 0.8f
                setPadding(0, 0, 0, 8.dpToPx())
            })

            val gridLayout = GridLayout(this).apply { columnCount = 3 }
            filteredActivities.forEach { activity ->
                val chip = Chip(this).apply {
                    setTag(R.id.color_tag, "custom")
                    text = activity
                    isCheckable = true
                    isChecked = selectedActivities.contains(activity)
                    chipBackgroundColor = ColorStateList.valueOf(if (isChecked) Color.TRANSPARENT else btnColor)
                    setTextColor(if (isChecked) btnColor else btnTextColor)
                    chipStrokeColor = ColorStateList.valueOf(btnColor)
                    chipStrokeWidth = 1.dpToPx().toFloat()
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedActivities.add(activity) else selectedActivities.remove(activity)
                        renderActivityGroups(query)
                    }
                }
                gridLayout.addView(chip, GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                })
            }
            groupLayout.addView(gridLayout)
            card.addView(groupLayout)
            activityGroupsContainer.addView(card)
        }
    }

    private fun showMemberSelectionDialog() {
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val people: List<Person> = gson.fromJson(prefs.getString("people_list", "[]"), object : TypeToken<List<Person>>() {}.type)
        val groups: List<Group> = gson.fromJson(prefs.getString("groups_list", "[]"), object : TypeToken<List<Group>>() {}.type)
        DialogHelper.showMemberSelectionDialog(this, getString(R.string.action_link_members), people.filter { !it.isArchived }, groups, selectedMemberIds) { newList ->
            selectedMemberIds.clear()
            selectedMemberIds.addAll(newList)
            selectedMembersText.text = people.filter { selectedMemberIds.contains(it.id) }.joinToString(", ") { it.name }
        }
    }

    private fun showAddActivityDialog() { /* Originele logica voor toevoegen... */ }
    private fun showManageGroupsDialog() { /* Originele logica voor beheer... */ }
    private fun showNoteSelectionDialog() { /* Originele logica voor notities... */ }
    private fun showTodoSelectionDialog() { /* Originele logica voor todo... */ }

    private fun saveMood() {
        val emoji = selectedEmoji ?: run { Toast.makeText(this, "Please select a mood", Toast.LENGTH_SHORT).show(); return }
        val entries = loadEntries()
        val entry = MoodEntry(
            id = editingEntryId ?: UUID.randomUUID().toString(),
            timestamp = selectedTimestamp,
            moodEmoji = emoji,
            moodRotation = selectedRotation,
            moodLabel = selectedMoodLabel ?: "",
            moodColor = selectedMoodColor,
            memberIds = selectedMemberIds.toList(),
            activities = selectedActivities.toList(),
            note = etMoodNote.text.toString().trim(),
            imageUri = selectedImageUri
        )

        val idx = entries.indexOfFirst { it.id == entry.id }
        if (idx != -1) entries[idx] = entry else entries.add(entry)

        persistEntries(entries)
        Toast.makeText(this, getString(R.string.entry_saved), Toast.LENGTH_SHORT).show()
        val intentTimeline = android.content.Intent(this, MoodTimelineActivity::class.java)
        startActivity(intentTimeline)
        finish()
    }

    private fun loadActivityGroups(): MutableList<ActivityGroup> {
        val json = getSharedPreferences("my_app", MODE_PRIVATE).getString("activity_groups", "[]")
        return gson.fromJson(json, object : TypeToken<MutableList<ActivityGroup>>() {}.type) ?: mutableListOf()
    }

    fun loadEntries(): MutableList<MoodEntry> {
        val json = getSharedPreferences("my_app", MODE_PRIVATE).getString("mood_entries", "[]")
        return gson.fromJson(json, object : TypeToken<MutableList<MoodEntry>>() {}.type) ?: mutableListOf()
    }

    private fun persistEntries(list: List<MoodEntry>) {
        getSharedPreferences("my_app", MODE_PRIVATE).edit().putString("mood_entries", gson.toJson(list)).apply()
    }

    private fun Int.dpToPx() = (this * resources.displayMetrics.density).toInt()
}
