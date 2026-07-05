package com.interli.plural

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
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

    init {
        setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                v.performClick()
                handleTouch(event.x, event.y)
            }
            true
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTouch(tx: Float, ty: Float) {
        if (tx < timeColumnWidth) return

        val colWidth = (width - timeColumnWidth) / daysCount
        val col = ((tx - timeColumnWidth) / colWidth).toInt()
        val density = resources.displayMetrics.density
        val topOffset = getTopOffset()
        val allDayHeight = 30 * density

        val startOfRange = getStartOfRange()
        val cal = startOfRange.clone() as Calendar
        cal.add(Calendar.DAY_OF_YEAR, col)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000

        val dayAllDayEvents = events.filter { it.isAllDay && it.startTime < dayEnd && it.endTime > dayStart }
        val stickyStart = maxOf(0f, scrollYOffset)
        dayAllDayEvents.forEachIndexed { index, event ->
            val eventTop = stickyStart + index * allDayHeight
            if (ty >= eventTop && ty <= eventTop + allDayHeight) {
                onEventClicked?.invoke(event)
                return
            }
        }

        if (ty < topOffset) return
        val adjustedTy = ty - topOffset

        val timeInDay = (adjustedTy / hourHeight) * 60 * 60 * 1000
        val touchTime = dayStart + timeInDay.toLong()

        val possibleEvents = events.filter { event ->
            !event.isAllDay &&
                    event.startTime <= touchTime && event.endTime >= touchTime &&
                    event.startTime < dayEnd && event.endTime > dayStart
        }
        val clickedEvent = possibleEvents.minByOrNull { it.endTime - it.startTime }

        if (clickedEvent != null) {
            val eventStartCal = Calendar.getInstance().apply { timeInMillis = clickedEvent.startTime }
            val dayStartRange = startOfRange.clone() as Calendar
            dayStartRange.add(Calendar.DAY_OF_YEAR, col)
            val dayStartMillis = dayStartRange.timeInMillis

            val startY = if (clickedEvent.startTime < dayStartMillis) 0f
            else (eventStartCal.get(Calendar.HOUR_OF_DAY) + eventStartCal.get(Calendar.MINUTE) / 60f) * hourHeight

            val relativeY = adjustedTy - startY

            if (relativeY > 20 * density) {
                val links = mutableListOf<Triple<String, String, String>>()
                clickedEvent.linkedMemberIds.forEach { id -> people.find { it.id == id && !it.isArchived }?.let { links.add(Triple("MEMBER", it.id, it.name)) } }
                clickedEvent.linkedNoteId?.let { id -> notes.find { it.id == id }?.let { links.add(Triple("NOTE", it.id, it.title.ifEmpty { "Note" })) } }
                clickedEvent.linkedTodoListId?.let { id -> todoLists.find { it.id == id }?.let { links.add(Triple("TODO", it.id, it.title.ifEmpty { "Todo" })) } }

                var currentX = 4 * density
                val spacing = 12 * density
                links.forEach { link ->
                    val text = when(link.first) {
                        "MEMBER" -> "👤 ${link.third}"
                        "NOTE" -> "📝 ${link.third}"
                        else -> "✅ ${link.third}"
                    }
                    val textWidth = text.length * 7 * density
                    val touchXInEvent = tx - (timeColumnWidth + col * colWidth)
                    if (touchXInEvent in currentX..(currentX + textWidth) && relativeY in (20 * density)..(35 * density)) {
                        onLinkSelected?.invoke(link.first, link.second)
                        return
                    }
                    currentX += textWidth + spacing
                }
            }

            onEventClicked?.invoke(clickedEvent)
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

        val stickyStart = maxOf(0f, scrollYOffset)

        for (i in 0 until daysCount) {
            val dayStartMillis = (startOfRange.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }.timeInMillis
            val dayEndMillis = dayStartMillis + 24 * 3600 * 1000
            val x = timeColumnWidth + i * colWidth

            val dayAllDayEvents = visibleEvents.filter { it.isAllDay && it.startTime < dayEndMillis && it.endTime > dayStartMillis }
            dayAllDayEvents.forEachIndexed { index, event ->
                val stickyY = stickyStart + index * allDayHeight
                drawEventBox(canvas, event, x, colWidth, stickyY + 1 * density, stickyY + allDayHeight - 1 * density, density)
            }

            val dayRegularEvents = visibleEvents.filter { !it.isAllDay && it.startTime < dayEndMillis && it.endTime > dayStartMillis }
            dayRegularEvents.forEach { event ->
                val eventStartCal = Calendar.getInstance().apply { timeInMillis = event.startTime }
                val eventEndCal = Calendar.getInstance().apply { timeInMillis = event.endTime }

                val startY = if (event.startTime < dayStartMillis) 0f
                else (eventStartCal.get(Calendar.HOUR_OF_DAY) + eventStartCal.get(Calendar.MINUTE) / 60f) * hourHeight
                val endY = if (event.endTime > dayEndMillis) 24 * hourHeight
                else (eventEndCal.get(Calendar.HOUR_OF_DAY) + eventEndCal.get(Calendar.MINUTE) / 60f) * hourHeight

                drawEventBox(canvas, event, x, colWidth, topOffset + startY + 1 * density, topOffset + endY - 1 * density, density)
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