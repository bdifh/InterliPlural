package com.interli.plural

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TimelineAdapter(
    private val sessions: List<FrontSession>,
    private val onItemClick: (FrontSession) -> Unit
) :
    RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

    private val sdf = SimpleDateFormat("dd-MM HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view as com.google.android.material.card.MaterialCardView
        val name: TextView = view.findViewById(R.id.sessionName)
        val time: TextView = view.findViewById(R.id.sessionTime)
        val note: TextView = view.findViewById(R.id.sessionNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        val context = holder.itemView.context
        
        holder.card.setCardBackgroundColor(ColorHelper.getBgColor(context))
        holder.card.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
        holder.card.strokeColor = android.graphics.Color.argb(64, 0, 0, 0)
        holder.card.cardElevation = (2 * context.resources.displayMetrics.density)

        val textColor = ColorHelper.getTextColor(context)
        holder.name.setTextColor(textColor)
        holder.time.setTextColor(textColor)

        holder.name.text = session.personName

        holder.note.text = session.note
        holder.note.setTextColor(textColor)
        holder.note.visibility = if (session.note.isNullOrBlank()) View.GONE else View.VISIBLE
        
        val start = sdf.format(Date(session.startTime))
        val end = if (session.endTime != null) sdf.format(Date(session.endTime!!)) else context.getString(R.string.session_time_active)

        val eindTijd = session.endTime ?: System.currentTimeMillis()
        val verschilMs = (eindTijd - session.startTime).coerceAtLeast(0)

        val minuten = (verschilMs / (1000 * 60)) % 60
        val uren = (verschilMs / (1000 * 60 * 60))

        val durationText = if (uren > 0) {
            "\n" + context.getString(R.string.session_duration_h_m, uren, minuten)
        } else {
            "\n" + context.getString(R.string.session_duration_m, minuten)
        }

        holder.time.text = context.getString(R.string.session_time_format, start, end, durationText)

        holder.itemView.setOnClickListener {
            onItemClick(session)
        }
    }

    override fun getItemCount() = sessions.size
}