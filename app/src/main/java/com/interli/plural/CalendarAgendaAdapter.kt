package com.interli.plural

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class CalendarAgendaAdapter(
    private var items: List<AgendaItem>,
    private val allNotes: List<DiaryNote>,
    private val allTodoLists: List<TodoList>,
    private val onEventClick: (CalendarEvent) -> Unit,
    private val onNoteClick: (DiaryNote) -> Unit,
    private val onTodoClick: (TodoList) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_EVENT = 1
    }

    sealed class AgendaItem {
        data class Header(val dateStr: String) : AgendaItem()
        data class Event(val event: CalendarEvent) : AgendaItem()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AgendaItem.Header -> TYPE_HEADER
            is AgendaItem.Event -> TYPE_EVENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_calendar_header, parent, false))
        } else {
            EventViewHolder(inflater.inflate(R.layout.item_calendar_event, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is AgendaItem.Header) {
            holder.bind(item)
        } else if (holder is EventViewHolder && item is AgendaItem.Event) {
            holder.bind(item.event)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<AgendaItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<TextView>(R.id.textCalendarHeader)
        fun bind(header: AgendaItem.Header) {
            textView.text = header.dateStr
            textView.setTextColor(ColorHelper.getTextColor(itemView.context))
        }
    }

    inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as? com.google.android.material.card.MaterialCardView
        private val title = view.findViewById<TextView>(R.id.textEventTitle)
        private val timeText = view.findViewById<TextView>(R.id.textEventTime)
        private val infoLayout = view.findViewById<LinearLayout>(R.id.layoutEventInfo)

        fun bind(event: CalendarEvent) {
            val context = itemView.context
            val textColor = ColorHelper.getTextColor(context)
            val btnColor = ColorHelper.getBtnColor(context)

            card?.apply {
                setCardBackgroundColor(ColorHelper.getBgColor(context))
                strokeColor = event.color ?: (textColor and 0x33FFFFFF)
                strokeWidth = if (event.color != null) (2 * resources.displayMetrics.density).toInt() else 0
            }

            title.text = event.title
            title.setTextColor(textColor)

            val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            var timeRange = "${timeSdf.format(Date(event.startTime))} - ${timeSdf.format(Date(event.endTime))}"
            if (event.recurrence != null) {
                timeRange += " (${context.getString(R.string.action_repeat)})"
            }
            timeText.text = timeRange
            timeText.setTextColor(textColor)

            infoLayout.removeAllViews()
            
            event.linkedNoteId?.let { noteId ->
                allNotes.find { it.id == noteId }?.let { note ->
                    val tv = TextView(context).apply {
                        text = "📝 ${note.title.ifEmpty { context.getString(R.string.unnamed_note) }}"
                        textSize = 12f
                        setTextColor(btnColor)
                        setPadding(0, 8, 0, 0)
                        setOnClickListener { onNoteClick(note) }
                    }
                    infoLayout.addView(tv)
                }
            }

            event.linkedTodoListId?.let { todoId ->
                allTodoLists.find { it.id == todoId }?.let { todo ->
                    val tv = TextView(context).apply {
                        text = "✅ ${todo.title.ifEmpty { context.getString(R.string.todo) }}"
                        textSize = 12f
                        setTextColor(btnColor)
                        setPadding(0, 8, 0, 0)
                        setOnClickListener { onTodoClick(todo) }
                    }
                    infoLayout.addView(tv)
                }
            }

            itemView.setOnClickListener { onEventClick(event) }
        }
    }
}
