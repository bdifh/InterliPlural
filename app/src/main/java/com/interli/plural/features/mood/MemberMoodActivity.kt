package com.interli.plural.features.mood

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.BaseActivity
import com.interli.plural.core.ColorHelper
import com.interli.plural.DiaryNote
import com.interli.plural.core.MediaEmbedHelper
import com.interli.plural.features.mood.MemberMoodStatsActivity
import com.interli.plural.features.mood.MoodActivity
import com.interli.plural.R
import com.interli.plural.TodoList
import java.text.SimpleDateFormat
import java.util.*

class MemberMoodActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_member_mood)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        ColorHelper.applySettings(this)
        val personId = intent.getStringExtra("person_id") ?: return
        val personName = intent.getStringExtra("person_name") ?: ""
        findViewById<TextView>(R.id.memberMoodTitle).text = getString(R.string.mood_for_member, personName)
        findViewById<Button>(R.id.btnViewMemberMoodStats).setOnClickListener {
            val intent = android.content.Intent(this, MemberMoodStatsActivity::class.java)
            intent.putExtra("person_id", personId)
            intent.putExtra("person_name", personName)
            startActivity(intent)
        }
        setupNavigationDrawer()
        val prefs = getSharedPreferences("my_app", MODE_PRIVATE)
        val gson = Gson()
        val notesJson = prefs.getString("diary_notes", "[]") ?: "[]"
        val allNotes: List<DiaryNote> = try {
            gson.fromJson(notesJson, object : TypeToken<List<DiaryNote>>() {}.type)
        } catch (e: Exception) { emptyList() }
        val todoJson = prefs.getString("todo_lists", "[]") ?: "[]"
        val allTodos: List<TodoList> = try {
            gson.fromJson(todoJson, object : TypeToken<List<TodoList>>() {}.type)
        } catch (e: Exception) { emptyList() }
        val moodJson = prefs.getString("mood_entries", "[]") ?: "[]"
        val type = object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type
        val allMoods: List<MoodActivity.MoodEntry> = try { gson.fromJson(moodJson, type) } catch (_: Exception) { emptyList() }
        val container = findViewById<LinearLayout>(R.id.memberMoodContainer)
        container.removeAllViews()
        val filtered = allMoods.filter { it.memberIds.contains(personId) }.sortedByDescending { it.timestamp }
        val bgColor = ColorHelper.getBgColor(this)
        if (filtered.isEmpty()) {
            val message = TextView(this).apply {
                text = getString(R.string.no_mood_for_member)
                setPadding(24, 48, 24, 24)
                gravity = Gravity.CENTER
                alpha = 0.5f
                setTextColor(ColorHelper.getTextColor(this@MemberMoodActivity))
            }
            container.addView(message)
            return
        }
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val textColor = ColorHelper.getTextColor(this)
        filtered.forEach { entry ->
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
                orientation = LinearLayout.HORIZONTAL
                setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
                gravity = Gravity.CENTER_VERTICAL
            }
            val moodIndicator = FrameLayout(this).apply {
                setTag(R.id.color_tag, "skip")
                layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorHelper.getMoodColor(this@MemberMoodActivity, entry.moodLabel))
                }
            }
            val tvEmoji = TextView(this).apply {
                text = entry.moodEmoji
                rotation = entry.moodRotation
                textSize = 20f
                gravity = Gravity.CENTER
            }
            moodIndicator.addView(tvEmoji)
            content.addView(moodIndicator)
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 16.dpToPx()
                }
            }
            val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val timeTv = TextView(this).apply {
                text = sdf.format(Date(entry.timestamp))
                textSize = 12f
                alpha = 0.6f
                setTextColor(textColor)
            }
            topRow.addView(timeTv)
            val moodLabelTv = TextView(this).apply {
                text = " • ${entry.moodLabel}"
                textSize = 12f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                alpha = 0.8f
                setTextColor(textColor)
            }
            topRow.addView(moodLabelTv)
            info.addView(topRow)
            if (entry.activities.isNotEmpty()) {
                val actsTv = TextView(this).apply {
                    text = entry.activities.joinToString(", ")
                    textSize = 14f
                    setPadding(0, 4.dpToPx(), 0, 0)
                    setTextColor(textColor)
                }
                info.addView(actsTv)
            }
            if (entry.note.isNotEmpty()) {
                val noteTv = TextView(this).apply {
                    text = entry.note
                    textSize = 13f
                    alpha = 0.7f
                    setPadding(0, 4.dpToPx(), 0, 0)
                    setTextColor(textColor)
                }
                info.addView(noteTv)
                val mediaContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                MediaEmbedHelper.addEmbedsToContainer(mediaContainer, entry.note)
                info.addView(mediaContainer)
            }
            val linkedNote = allNotes.find { it.id == entry.linkedNoteId }
            if (linkedNote != null) {
                val noteTv = TextView(this).apply {
                    text = getString(R.string.label_linked_note, linkedNote.title)
                    textSize = 11f
                    alpha = 0.5f
                    setPadding(0, 2.dpToPx(), 0, 0)
                    setTextColor(textColor)
                }
                info.addView(noteTv)
                val mediaContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                MediaEmbedHelper.addEmbedsToContainer(mediaContainer, entry.note)
                info.addView(mediaContainer)
            }
            val linkedTodo = allTodos.find { it.id == entry.linkedTodoId }
            if (linkedTodo != null) {
                val todoTv = TextView(this).apply {
                    text = getString(R.string.label_linked_todo, linkedTodo.title)
                    textSize = 11f
                    alpha = 0.5f
                    setPadding(0, 2.dpToPx(), 0, 0)
                    setTextColor(textColor)
                }
                info.addView(todoTv)
            }
            content.addView(info)
            val btnEdit = android.widget.ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                background = null
                alpha = 0.4f
                setOnClickListener {
                    val intent = android.content.Intent(this@MemberMoodActivity, MoodActivity::class.java)
                    intent.putExtra("edit_entry_id", entry.id)
                    startActivity(intent)
                }
            }
            content.addView(btnEdit)
            card.addView(content)
            container.addView(card)
        }
    }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
