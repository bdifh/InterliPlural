package com.interli.plural

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MemberTodoActivity : BaseActivity() {

    private lateinit var todoLists: MutableList<TodoList>
    private lateinit var people: List<Person>
    private val gson = Gson()
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_member_todo)

        ColorHelper.applySettings(this)

        val personId = intent.getStringExtra("person_id") ?: return
        val personName = intent.getStringExtra("person_name") ?: ""

        findViewById<TextView>(R.id.memberTodoTitle).text = getString(R.string.todo_for_member, personName)
        
        val btnAdd = findViewById<View>(R.id.btnAddMemberTodo)
        btnAdd.setOnClickListener {
            val intent = android.content.Intent(this, EditTodoListActivity::class.java)
            intent.putExtra("pre_link_member_id", personId)
            startActivity(intent)
        }

        listContainer = findViewById(R.id.memberTodoContainer)

        setupNavigationDrawer()
        loadData()
        renderMemberTasks(personId)
    }

    override fun onResume() {
        super.onResume()
        loadData()
        val personId = intent.getStringExtra("person_id") ?: return
        renderMemberTasks(personId)
    }

    private fun loadData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        val todoJson = sharedPref.getString("todo_lists", "[]") ?: "[]"
        todoLists = try {
            gson.fromJson(todoJson, object : TypeToken<MutableList<TodoList>>() {}.type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }

        val peopleJson = sharedPref.getString("people_list", "[]") ?: "[]"
        people = try {
            gson.fromJson(peopleJson, object : TypeToken<List<Person>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveData() {
        val sharedPref = getSharedPreferences("my_app", MODE_PRIVATE)
        sharedPref.edit {
            putString("todo_lists", gson.toJson(todoLists))
        }
    }

    private fun renderMemberTasks(personId: String) {
        listContainer.removeAllViews()
        
        val filteredLists = todoLists.filter { list ->
            list.linkedMemberIds.contains(personId) || list.tasks.any { it.linkedMemberIds.contains(personId) }
        }

        if (filteredLists.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.no_todo_for_member)
                gravity = Gravity.CENTER
                setPadding(0, 64, 0, 0)
                alpha = 0.5f
                setTextColor(ColorHelper.getTextColor(this@MemberTodoActivity))
            }
            listContainer.addView(tv)
            return
        }

        val textColor = ColorHelper.getTextColor(this)
        val bgColor = ColorHelper.getBgColor(this)

        filteredLists.forEach { list ->
            val tasksToShow = if (list.linkedMemberIds.contains(personId)) {
                list.tasks
            } else {
                list.tasks.filter { it.linkedMemberIds.contains(personId) }
            }

            if (tasksToShow.isEmpty()) return@forEach

            val card = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 16.dpToPx())
                }
                radius = 12f * resources.displayMetrics.density
                setCardBackgroundColor(bgColor)
                strokeWidth = 1
                strokeColor = (textColor and 0x33FFFFFF) or 0x33000000
                cardElevation = 2f
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            }

            val titleTv = TextView(this).apply {
                text = list.title
                textSize = 16f
                textStyle = android.graphics.Typeface.BOLD
                setTextColor(textColor)
                setPadding(0, 0, 0, 8.dpToPx())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val btnEdit = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                background = null
                alpha = 0.4f
                setOnClickListener {
                    val intent = android.content.Intent(this@MemberTodoActivity, EditTodoListActivity::class.java)
                    intent.putExtra("list_id", list.id)
                    startActivity(intent)
                }
            }

            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(titleTv)
                addView(btnEdit)
            }
            content.addView(titleRow)

            val listMediaContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            MediaEmbedHelper.addEmbedsToContainer(listMediaContainer, list.title)
            content.addView(listMediaContainer)

            list.deadline?.let { dl ->
                val deadlineTv = TextView(this).apply {
                    val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                    text = getString(R.string.deadline, sdf.format(java.util.Date(dl)))
                    textSize = 12f
                    alpha = 0.8f
                    setTextColor(textColor)
                    setPadding(0, 0, 0, 4.dpToPx())
                }
                content.addView(deadlineTv)
            }
            
            if (list.linkedMemberIds.isNotEmpty()) {
                val badgesRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, 8.dpToPx())
                }
                addMemberBadges(badgesRow, list.linkedMemberIds)
                content.addView(badgesRow)
            }

            tasksToShow.forEach { task ->
                val taskRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val indent = task.indentLevel * 12
                    setPadding(indent.dpToPx(), 4.dpToPx(), 0, 4.dpToPx())
                }

                val statusTv = TextView(this).apply {
                    text = getStatusChar(task.status)
                    textSize = 18f
                    setPadding(0, 0, 12.dpToPx(), 0)
                    setTextColor(textColor)
                    setOnClickListener {
                        val nextStatus = getNextStatus(task.status)
                        task.status = nextStatus
                        
                        text = getStatusChar(task.status)
                        saveData()
                        renderMemberTasks(personId)
                    }
                }
                taskRow.addView(statusTv)

                val taskTextContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val taskTitleTv = TextView(this).apply {
                    text = task.title
                    textSize = 14f
                    setTextColor(textColor)
                }
                taskTextContainer.addView(taskTitleTv)

                val mediaContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                MediaEmbedHelper.addEmbedsToContainer(mediaContainer, task.title)
                taskTextContainer.addView(mediaContainer)
                
                task.deadline?.let { dl ->
                    val deadlineTv = TextView(this).apply {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dl }
                        val isMidnight = cal.get(java.util.Calendar.HOUR_OF_DAY) == 0 && cal.get(java.util.Calendar.MINUTE) == 0
                        val fmt = if (isMidnight) "dd/MM" else "dd/MM HH:mm"
                        val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault())
                        text = getString(R.string.deadline, sdf.format(java.util.Date(dl)))
                        textSize = 10f
                        alpha = 0.6f
                        setTextColor(textColor)
                    }
                    taskTextContainer.addView(deadlineTv)
                }
                
                taskRow.addView(taskTextContainer)

                if (task.recurrence != null) {
                    val repeatBtn = TextView(this).apply {
                        text = "↻"
                        textSize = 20f
                        setPadding(12.dpToPx(), 0, 12.dpToPx(), 0)
                        setTextColor(textColor)
                        alpha = 0.7f
                        setOnClickListener {
                            handleRecurrence(task)
                            saveData()
                            renderMemberTasks(personId)
                        }
                    }
                    taskRow.addView(repeatBtn)
                }
                
                if (task.linkedMemberIds.isNotEmpty()) {
                    addMemberBadges(taskRow, task.linkedMemberIds)
                }

                content.addView(taskRow)
            }

            card.addView(content)
            listContainer.addView(card)
        }
    }

    private fun handleRecurrence(task: TodoTask) {
        val baseTime = task.deadline ?: System.currentTimeMillis()
        
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = baseTime
        val wasMidnight = cal.get(java.util.Calendar.HOUR_OF_DAY) == 0 && cal.get(java.util.Calendar.MINUTE) == 0
        
        when (task.recurrence) {
            "DAILY" -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            "MONTHLY" -> cal.add(java.util.Calendar.MONTH, 1)
            "YEARLY" -> cal.add(java.util.Calendar.YEAR, 1)
            "CUSTOM" -> {
                val days = task.recurrenceDays ?: emptyList()
                if (days.isNotEmpty()) {
                    var found = false
                    for (i in 1..7) {
                        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        val dayOfWeek = when(cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                            java.util.Calendar.MONDAY -> 1
                            java.util.Calendar.TUESDAY -> 2
                            java.util.Calendar.WEDNESDAY -> 3
                            java.util.Calendar.THURSDAY -> 4
                            java.util.Calendar.FRIDAY -> 5
                            java.util.Calendar.SATURDAY -> 6
                            java.util.Calendar.SUNDAY -> 7
                            else -> 1
                        }
                        if (days.contains(dayOfWeek)) {
                            found = true
                            break
                        }
                    }
                    if (!found) cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                } else {
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        
        if (wasMidnight) {
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
        }
        
        task.deadline = cal.timeInMillis
        task.status = "EMPTY"
        Toast.makeText(this, getString(R.string.entry_saved), Toast.LENGTH_SHORT).show()
    }

    private fun getStatusChar(status: String): String = when (status) {
        "CHECKED" -> "✓"
        "FORWARD" -> "→"
        "BACKWARD" -> "←"
        "WAITING" -> "⏳"
        "CANCELED" -> "✕"
        "QUESTION" -> "?"
        else -> "☐"
    }

    private fun getNextStatus(current: String): String {
        val statuses = listOf("EMPTY", "CHECKED", "FORWARD", "BACKWARD", "WAITING", "CANCELED", "QUESTION")
        val idx = statuses.indexOf(current)
        return statuses[(idx + 1) % statuses.size]
    }

    private fun addMemberBadges(container: LinearLayout, memberIds: List<String>) {
        memberIds.forEach { id ->
            val person = people.find { it.id == id } ?: return@forEach
            val badge = TextView(this).apply {
                text = person.name
                textSize = 9f
                setPadding(8.dpToPx(), 2.dpToPx(), 8.dpToPx(), 2.dpToPx())
                setTextColor(Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 100f
                    setColor(person.profileColor)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 4.dpToPx()
                }
            }
            container.addView(badge)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
    private var TextView.textStyle: Int
        get() = typeface?.style ?: android.graphics.Typeface.NORMAL
        set(value) { setTypeface(typeface, value) }
}
