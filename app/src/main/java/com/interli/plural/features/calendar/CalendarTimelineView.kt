package com.interli.plural.features.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.interli.plural.CalendarEvent
import com.interli.plural.core.ColorHelper
import com.interli.plural.DiaryNote
import com.interli.plural.Person
import com.interli.plural.TodoList
import java.util.*

class CalendarTimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var events: List<CalendarEvent> = emptyList()
    private var people: List<Person> = emptyList()
    private var notes: List<DiaryNote> = emptyList()
    private var todoLists: List<TodoList> = emptyList()
    private var baseDate: Calendar = Calendar.getInstance()
    private var daysCount = 1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }
    private val hourHeight = 60f * resources.displayMetrics.density
    private val timeColumnWidth = 50f * resources.displayMetrics.density
    private val eventRect = RectF()
    private var scrollYOffset = 0f
    var onEventClicked: ((CalendarEvent) -> Unit)? = null
    var onLinkSelected: ((String, String) -> Unit)? = null

    private data class EventPosition(
        val event: CalendarEvent,
        val colIndex: Int,
        val maxCols: Int
    )

    init {
        setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                handleTouch(event.x, event.y)
            }
            true
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun computeEventPositions(dayEvents: List<CalendarEvent>): List<EventPosition> {
        if (dayEvents.isEmpty()) return emptyList()

        val sorted = dayEvents.sortedWith(compareBy({ it.startTime }, { -(it.endTime - it.startTime) }))

        val clusters = mutableListOf<MutableList<CalendarEvent>>()
        var currentCluster = mutableListOf<CalendarEvent>()
        var clusterEnd = -1L

        for (event in sorted) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(event)
                clusterEnd = event.endTime
            } else {
                if (event.startTime < clusterEnd) {
                    currentCluster.add(event)
                    clusterEnd = maxOf(clusterEnd, event.endTime)
                } else {
                    clusters.add(currentCluster)
                    currentCluster = mutableListOf(event)
                    clusterEnd = event.endTime
                }
            }
        }
        if (currentCluster.isNotEmpty()) {
            clusters.add(currentCluster)
        }

        val result = mutableListOf<EventPosition>()

        for (cluster in clusters) {
            val colEndTimes = mutableListOf<Long>()
            val eventColMap = mutableMapOf<CalendarEvent, Int>()

            for (event in cluster) {
                var assignedCol = -1
                for (c in colEndTimes.indices) {
                    if (colEndTimes[c] <= event.startTime) {
                        assignedCol = c
                        colEndTimes[c] = event.endTime
                        break
                    }
                }
                if (assignedCol == -1) {
                    assignedCol = colEndTimes.size
                    colEndTimes.add(event.endTime)
                }
                eventColMap[event] = assignedCol
            }

            val totalCols = colEndTimes.size
            for (event in cluster) {
                val colIdx = eventColMap[event] ?: 0
                result.add(EventPosition(event, colIdx, totalCols))
            }
        }

        return result
    }

    private fun handleTouch(tx: Float, ty: Float) {
        if (tx < timeColumnWidth) return
        val colWidth = (width - timeColumnWidth) / daysCount
        val col = ((tx - timeColumnWidth) / colWidth).toInt().coerceIn(0, daysCount - 1)
        val density = resources.displayMetrics.density
        val topOffset = getTopOffset()
        val allDayHeight = 30 * density
        val startOfRange = getStartOfRange()
        val cal = startOfRange.clone() as Calendar
        cal.add(Calendar.DAY_OF_YEAR, col)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000
        val dayAllDayEvents = events.filter { it.isAllDay && it.startTime < dayEnd && it.endTime > dayStart }

        if (ty < topOffset) {
            val index = (ty / allDayHeight).toInt()
            if (index in dayAllDayEvents.indices) {
                onEventClicked?.invoke(dayAllDayEvents[index])
            }
            return
        }

        val dayRegularEvents = events.filter { !it.isAllDay && it.startTime < dayEnd && it.endTime >= dayStart }
        val positions = computeEventPositions(dayRegularEvents)

        val dayX = timeColumnWidth + col * colWidth
        val adjustedTy = ty - topOffset

        for (pos in positions) {
            val event = pos.event
            val eventStartCal = Calendar.getInstance().apply { timeInMillis = event.startTime }
            val eventEndCal = Calendar.getInstance().apply { timeInMillis = event.endTime }
            val startY = if (event.startTime < dayStart) 0f
            else (eventStartCal.get(Calendar.HOUR_OF_DAY) + eventStartCal.get(Calendar.MINUTE) / 60f) * hourHeight
            val endY = if (event.endTime > dayEnd) 24 * hourHeight
            else (eventEndCal.get(Calendar.HOUR_OF_DAY) + eventEndCal.get(Calendar.MINUTE) / 60f) * hourHeight

            val subColWidth = colWidth / pos.maxCols
            val eventX = dayX + pos.colIndex * subColWidth

            if (tx in eventX..(eventX + subColWidth) && adjustedTy in startY..endY) {
                val relativeY = adjustedTy - startY
                if (relativeY > 20 * density) {
                    val links = mutableListOf<Triple<String, String, String>>()
                    event.linkedMemberIds.forEach { id -> people.find { it.id == id && !it.isArchived }?.let { links.add(Triple("MEMBER", it.id, it.name)) } }
                    event.linkedNoteId?.let { id -> notes.find { it.id == id }?.let { links.add(Triple("NOTE", it.id, it.title.ifEmpty { "Note" })) } }
                    event.linkedTodoListId?.let { id -> todoLists.find { it.id == id }?.let { links.add(Triple("TODO", it.id, it.title.ifEmpty { "Todo" })) } }
                    var currentX = 4 * density
                    val spacing = 12 * density
                    links.forEach { link ->
                        val text = when(link.first) {
                            "MEMBER" -> "👤 ${link.third}"
                            "NOTE" -> "📝 ${link.third}"
                            else -> "✅ ${link.third}"
                        }
                        val textWidth = text.length * 7 * density
                        val touchXInEvent = tx - eventX
                        if (touchXInEvent in currentX..(currentX + textWidth) && relativeY in (20 * density)..(35 * density)) {
                            onLinkSelected?.invoke(link.first, link.second)
                            return
                        }
                        currentX += textWidth + spacing
                    }
                }
                onEventClicked?.invoke(event)
                return
            }
        }
    }

    private fun getStartOfRange(): Calendar {
        val cal = baseDate.clone() as Calendar
        if (daysCount == 7) {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    fun setEvents(events: List<CalendarEvent>, people: List<Person>, notes: List<DiaryNote>, todoLists: List<TodoList>, baseDate: Calendar, daysCount: Int) {
        this.events = events
        this.people = people
        this.notes = notes
        this.todoLists = todoLists
        this.baseDate = baseDate
        this.daysCount = daysCount
        invalidate()
        requestLayout()
    }

    fun setScrollYOffset(offset: Int) {
        if (this.scrollYOffset != offset.toFloat()) {
            this.scrollYOffset = offset.toFloat()
            invalidate()
        }
    }

    private fun getTopOffset(): Float {
        val density = resources.displayMetrics.density
        val allDayHeight = 30 * density
        val startOfRange = getStartOfRange()
        val maxAllDayCount = (0 until daysCount).map { i ->
            val dStart = (startOfRange.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }.timeInMillis
            val dEnd = dStart + 24 * 3600 * 1000
            events.count { it.isAllDay && it.startTime < dEnd && it.endTime > dStart }
        }.maxOrNull() ?: 0
        return maxAllDayCount * allDayHeight
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (24 * hourHeight + getTopOffset()).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= timeColumnWidth) return
        val density = resources.displayMetrics.density
        val colWidth = (width - timeColumnWidth) / daysCount
        val topOffset = getTopOffset()
        val allDayHeight = 30 * density
        val textColor = ColorHelper.getTextColor(context)
        val bgColor = ColorHelper.getBgColor(context)
        canvas.drawColor(bgColor)

        if (topOffset > 0f) {
            paint.color = textColor
            paint.alpha = 12
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, width.toFloat(), topOffset, paint)

            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = textColor
            textPaint.alpha = 180
            textPaint.textSize = 10 * density
            canvas.drawText(context.getString(com.interli.plural.R.string.event_all_day), timeColumnWidth - 8 * density, 18 * density, textPaint)

            paint.color = textColor
            paint.alpha = 60
            paint.strokeWidth = 1 * density
            canvas.drawLine(0f, topOffset, width.toFloat(), topOffset, paint)
        }

        paint.color = textColor
        paint.alpha = 40
        paint.strokeWidth = 1f
        for (i in 0..24) {
            val y = topOffset + i * hourHeight
            canvas.drawLine(timeColumnWidth, y, width.toFloat(), y, paint)
            if (i < 24) {
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = textColor
                textPaint.alpha = 200
                textPaint.textSize = 12 * density
                canvas.drawText(String.format("%02d:00", i), timeColumnWidth - 8 * density, y + 15 * density, textPaint)
            }
        }
        paint.alpha = 255
        if (daysCount > 1) {
            for (i in 1 until daysCount) {
                val x = timeColumnWidth + i * colWidth
                canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            }
        }
        val startOfRange = getStartOfRange()
        val endOfRange = startOfRange.clone() as Calendar
        endOfRange.add(Calendar.DAY_OF_YEAR, daysCount)
        val visibleEvents = events.filter {
            it.startTime < endOfRange.timeInMillis && it.endTime > startOfRange.timeInMillis
        }
        for (i in 0 until daysCount) {
            val dayStartMillis = (startOfRange.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }.timeInMillis
            val dayEndMillis = dayStartMillis + 24 * 3600 * 1000
            val x = timeColumnWidth + i * colWidth
            val dayAllDayEvents = visibleEvents.filter { it.isAllDay && it.startTime < dayEndMillis && it.endTime > dayStartMillis }
            dayAllDayEvents.forEachIndexed { index, event ->
                val eventTop = index * allDayHeight
                drawEventBox(canvas, event, x, colWidth, eventTop + 1 * density, eventTop + allDayHeight - 1 * density, density)
            }

            val dayRegularEvents = visibleEvents.filter { !it.isAllDay && it.startTime < dayEndMillis && it.endTime >= dayStartMillis }
            val positions = computeEventPositions(dayRegularEvents)

            positions.forEach { pos ->
                val event = pos.event
                val eventStartCal = Calendar.getInstance().apply { timeInMillis = event.startTime }
                val eventEndCal = Calendar.getInstance().apply { timeInMillis = event.endTime }
                val startY = if (event.startTime < dayStartMillis) 0f
                else (eventStartCal.get(Calendar.HOUR_OF_DAY) + eventStartCal.get(Calendar.MINUTE) / 60f) * hourHeight
                val endY = if (event.endTime > dayEndMillis) 24 * hourHeight
                else (eventEndCal.get(Calendar.HOUR_OF_DAY) + eventEndCal.get(Calendar.MINUTE) / 60f) * hourHeight

                val subColWidth = colWidth / pos.maxCols
                val eventX = x + pos.colIndex * subColWidth

                drawEventBox(canvas, event, eventX, subColWidth, topOffset + startY + 1 * density, topOffset + endY - 1 * density, density)
            }
        }
    }

    private fun drawEventBox(canvas: Canvas, event: CalendarEvent, x: Float, colWidth: Float, top: Float, bottom: Float, density: Float) {
        eventRect.set(x + 2 * density, top, x + colWidth - 2 * density, bottom)
        val color = getEventColor(event)
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(eventRect, 4 * density, 4 * density, paint)
        if (bottom - top > 15 * density) {
            textPaint.color = if (ColorHelper.isDark(color)) Color.WHITE else Color.BLACK
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 12 * density
            val availableWidth = colWidth - 8 * density
            val ellipsizedTitle = android.text.TextUtils.ellipsize(
                event.title,
                android.text.TextPaint(textPaint),
                availableWidth,
                android.text.TextUtils.TruncateAt.END
            )
            canvas.drawText(ellipsizedTitle.toString(), eventRect.left + 4 * density, eventRect.top + 14 * density, textPaint)
            if (bottom - top > 30 * density) {
                textPaint.textSize = 10 * density
                val links = mutableListOf<Triple<String, String, String>>()
                event.linkedMemberIds.forEach { id -> people.find { it.id == id && !it.isArchived }?.let { links.add(Triple("MEMBER", it.id, it.name)) } }
                event.linkedNoteId?.let { id -> notes.find { it.id == id }?.let { links.add(Triple("NOTE", it.id, it.title.ifEmpty { "Note" })) } }
                event.linkedTodoListId?.let { id -> todoLists.find { it.id == id }?.let { links.add(Triple("TODO", it.id, it.title.ifEmpty { "Todo" })) } }
                val colWidthAdjusted = colWidth - 4 * density
                var currentX = 4 * density
                val spacing = 12 * density
                links.forEach { link ->
                    val text = when(link.first) {
                        "MEMBER" -> "👤 ${link.third}"
                        "NOTE" -> "📝 ${link.third}"
                        else -> "✅ ${link.third}"
                    }
                    val textWidth = text.length * 7 * density
                    if (currentX + textWidth < colWidthAdjusted && eventRect.top + 32 * density < eventRect.bottom) {
                        canvas.drawText(text, eventRect.left + currentX, eventRect.top + 28 * density, textPaint)
                        currentX += textWidth + spacing
                    }
                }
            }
        }
    }

    private fun getEventColor(event: CalendarEvent): Int {
        if (event.color != null) return event.color!!
        if (event.linkedMemberIds.isNotEmpty()) {
            val person = people.find { it.id == event.linkedMemberIds[0] }
            if (person != null) return person.profileColor
        }
        return ColorHelper.getBtnColor(context)
    }
}