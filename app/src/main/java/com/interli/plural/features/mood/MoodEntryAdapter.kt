package com.interli.plural.features.mood

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.core.ColorHelper
import com.interli.plural.DiaryNote
import com.interli.plural.core.MediaEmbedHelper
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.TodoList
import java.text.SimpleDateFormat
import java.util.*

class MoodEntryAdapter(
    private val context: Context,
    private var entries: List<MoodActivity.MoodEntry>,
    private val people: List<Person>,
    private val onEdit: (MoodActivity.MoodEntry) -> Unit,
    private val onDelete: (MoodActivity.MoodEntry) -> Unit
) : RecyclerView.Adapter<MoodEntryAdapter.ViewHolder>() {
    private val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val daySdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
    private val bgColor = ColorHelper.getBgColor(context)
    private val textColor = ColorHelper.getTextColor(context)
    private val gson = Gson()
    private var allNotes: List<DiaryNote> = emptyList()
    private var allTodos: List<TodoList> = emptyList()
    private val deleteClickCounts = mutableMapOf<String, Int>()

    init {
        val prefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val notesJson = prefs.getString("diary_notes", "[]") ?: "[]"
        allNotes = try {
            gson.fromJson(notesJson, object : TypeToken<List<DiaryNote>>() {}.type)
        } catch (e: Exception) { emptyList() }
        val todoJson = prefs.getString("todo_lists", "[]") ?: "[]"
        allTodos = try {
            gson.fromJson(todoJson, object : TypeToken<List<TodoList>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    fun updateData(newEntries: List<MoodActivity.MoodEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val daySeparator: LinearLayout = view.findViewById(R.id.daySeparator)
        val tvDayHeader: TextView = view.findViewById(R.id.tvDayHeader)
        val card: MaterialCardView = view.findViewById(R.id.moodCard)
        val moodIndicator: FrameLayout = view.findViewById(R.id.moodIndicator)
        val tvEmoji: TextView = view.findViewById(R.id.tvMoodEmoji)
        val tvTime: TextView = view.findViewById(R.id.tvMoodTime)
        val tvLabel: TextView = view.findViewById(R.id.tvMoodLabel)
        val tvActivities: TextView = view.findViewById(R.id.tvMoodActivities)
        val tvNote: TextView = view.findViewById(R.id.tvMoodNote)
        val ivMoodImage: ImageView = view.findViewById(R.id.ivMoodImage)
        val mediaEmbedContainer: LinearLayout = view.findViewById(R.id.mediaEmbedContainerMood)
        val tvMembers: TextView = view.findViewById(R.id.tvMoodMembers)
        val tvLinkedNote: TextView = view.findViewById(R.id.tvMoodLinkedNote)
        val tvLinkedTodo: TextView = view.findViewById(R.id.tvMoodLinkedTodo)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditMood)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteMood)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_mood_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val prevEntry = if (position > 0) entries[position - 1] else null
        val currentDay = getDayString(entry.timestamp)
        val prevDay = prevEntry?.let { getDayString(it.timestamp) }

        if (prevDay == null || currentDay != prevDay) {
            holder.daySeparator.visibility = View.VISIBLE
            holder.tvDayHeader.text = currentDay
            holder.tvDayHeader.setTextColor(textColor)
            holder.tvDayHeader.alpha = 0.7f
        } else {
            holder.daySeparator.visibility = View.GONE
        }

        holder.card.setCardBackgroundColor(bgColor)
        holder.card.setOnClickListener { onEdit(entry) }
        holder.moodIndicator.setTag(R.id.color_tag, "skip")
        holder.moodIndicator.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorHelper.getMoodColor(context, entry.moodLabel))
        }

        holder.tvEmoji.text = entry.moodEmoji
        holder.tvEmoji.rotation = entry.moodRotation
        holder.tvTime.text = timeSdf.format(Date(entry.timestamp))
        holder.tvTime.setTextColor(textColor)

        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
        val displayLabel = entry.moodLabel.replace("mood_", "").capitalize()
        holder.tvLabel.text = " • $displayLabel"
        holder.tvLabel.setTextColor(textColor)

        if (entry.activities.isNotEmpty()) {
            holder.tvActivities.visibility = View.VISIBLE
            holder.tvActivities.text = entry.activities.joinToString(", ")
            holder.tvActivities.setTextColor(textColor)
        } else {
            holder.tvActivities.visibility = View.GONE
        }

        if (entry.note.isNotEmpty()) {
            holder.tvNote.visibility = View.VISIBLE
            holder.tvNote.text = entry.note
            holder.tvNote.setTextColor(textColor)
            MediaEmbedHelper.addEmbedsToContainer(holder.mediaEmbedContainer, entry.note)
        } else {
            holder.tvNote.visibility = View.GONE
            holder.mediaEmbedContainer.visibility = View.GONE
        }

        // AFBEELDING WEERGAVE
        if (!entry.imageUri.isNullOrEmpty()) {
            holder.ivMoodImage.visibility = View.VISIBLE
            context.imageLoader.enqueue(coil.request.ImageRequest.Builder(context)
                .data(entry.imageUri)
                .target(holder.ivMoodImage)
                .build())
        } else {
            holder.ivMoodImage.visibility = View.GONE
        }

        if (entry.memberIds.isNotEmpty()) {
            val memberNames = people.filter { entry.memberIds.contains(it.id) && !it.isArchived }.map { it.name }
            if (memberNames.isNotEmpty()) {
                holder.tvMembers.visibility = View.VISIBLE
                holder.tvMembers.text = context.getString(R.string.stats_members, memberNames.joinToString(", "))
                holder.tvMembers.setTextColor(textColor)
            } else {
                holder.tvMembers.visibility = View.GONE
            }
        } else {
            holder.tvMembers.visibility = View.GONE
        }

        val linkedNote = allNotes.find { it.id == entry.linkedNoteId }
        holder.tvLinkedNote.visibility = if (linkedNote != null) View.VISIBLE else View.GONE
        linkedNote?.let { holder.tvLinkedNote.text = context.getString(R.string.label_linked_note, it.title); holder.tvLinkedNote.setTextColor(textColor) }

        val linkedTodo = allTodos.find { it.id == entry.linkedTodoId }
        holder.tvLinkedTodo.visibility = if (linkedTodo != null) View.VISIBLE else View.GONE
        linkedTodo?.let { holder.tvLinkedTodo.text = context.getString(R.string.label_linked_todo, it.title); holder.tvLinkedTodo.setTextColor(textColor) }

        holder.btnEdit.setOnClickListener { onEdit(entry) }
        holder.btnDelete.setOnClickListener {
            val currentClicks = (deleteClickCounts[entry.id] ?: 0) + 1
            if (currentClicks >= 8) {
                deleteClickCounts.remove(entry.id)
                onDelete(entry)
            } else {
                deleteClickCounts[entry.id] = currentClicks
                android.widget.Toast.makeText(context, context.getString(R.string.delete_mood_8x, 8 - currentClicks), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getDayString(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val now = Calendar.getInstance()
        return when {
            isSameDay(calendar, now) -> context.getString(R.string.today)
            isSameDay(calendar, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }) -> context.getString(R.string.yesterday)
            else -> daySdf.format(calendar.time)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun getItemCount(): Int = entries.size
}