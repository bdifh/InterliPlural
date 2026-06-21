package com.interli.plural

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
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
        
        val cal = getStartOfRange()
        cal.add(Calendar.DAY_OF_YEAR, col)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000
        
        val timeInDay = (ty / hourHeight) * 60 * 60 * 1000
        val touchTime = dayStart + timeInDay.toLong()

        val possibleEvents = events.filter { event ->
            event.startTime <= touchTime && event.endTime >= touchTime &&
            event.startTime < dayEnd && event.endTime > dayStart
        }
        val clickedEvent = possibleEvents.minByOrNull { it.endTime - it.startTime }
        
        if (clickedEvent != null) {
            val density = resources.displayMetrics.density
            val eventStartCal = Calendar.getInstance().apply { timeInMillis = clickedEvent.startTime }
            val dayIdx = col
            val dayStartRange = getStartOfRange().apply { add(Calendar.DAY_OF_YEAR, dayIdx) }.timeInMillis

            val startY = if (clickedEvent.startTime < dayStartRange) 0f 
                        else (eventStartCal.get(Calendar.HOUR_OF_DAY) + eventStartCal.get(Calendar.MINUTE) / 60f) * hourHeight

            val relativeY = ty - startY

            if (relativeY > 20 * density) {
                val links = mutableListOf<Triple<String, String, String>>()
                clickedEvent.linkedMemberIds.forEach { id -> people.find { it.id == id }?.let { links.add(Triple("MEMBER", it.id, it.name)) } }
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
                    val textWidth = text.length * 7 * density // Rough estimate
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
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (24 * hourHeight).toInt()
        setMeasuredDimension(width, height)
    }

    private val eventRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= timeColumnWidth) return

        val density = resources.displayMetrics.density
        val colWidth = (width - timeColumnWidth) / daysCount

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.alpha = 255
        paint.alpha = 255
        
        val textColor = ColorHelper.getTextColor(context)
        val btnColor = ColorHelper.getBtnColor(context)
        val btnTextColor = ColorHelper.getBtnTextColor(context)
        val bgColor = ColorHelper.getBgColor(context)

        canvas.drawColor(bgColor)

        paint.color = textColor
        paint.alpha = 40
        paint.strokeWidth = 1f
        for (i in 0..24) {
            val y = i * hourHeight
            canvas.drawLine(timeColumnWidth, y, width.toFloat(), y, paint)
            
            if (i < 24) {
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

        visibleEvents.forEach { event ->
            val eventStartCal = Calendar.getInstance().apply { timeInMillis = event.startTime }
            val eventEndCal = Calendar.getInstance().apply { timeInMillis = event.endTime }
            
            for (i in 0 until daysCount) {
                val dayStartCal = startOfRange.clone() as Calendar
                dayStartCal.add(Calendar.DAY_OF_YEAR, i)
                val dayEndCal = dayStartCal.clone() as Calendar
                dayEndCal.add(Calendar.DAY_OF_YEAR, 1)
                
                if (event.startTime < dayEndCal.timeInMillis && event.endTime > dayStartCal.timeInMillis) {
                    val x = timeColumnWidth + i * colWidth

                    val startY = if (event.isAllDay) {
                        0f
                    } else if (event.startTime < dayStartCal.timeInMillis) {
                        0f
                    } else {
                        (eventStartCal.get(Calendar.HOUR_OF_DAY) + eventStartCal.get(Calendar.MINUTE) / 60f) * hourHeight
                    }

                    val endY = if (event.isAllDay) {
                        25 * density 
                    } else if (event.endTime > dayEndCal.timeInMillis) {
                        24 * hourHeight
                    } else {
                        (eventEndCal.get(Calendar.HOUR_OF_DAY) + eventEndCal.get(Calendar.MINUTE) / 60f) * hourHeight
                    }

                    eventRect.set(x + 2 * density, startY + 1 * density, x + colWidth - 2 * density, endY - 1 * density)
                    
                    val color = getEventColor(event)
                    paint.color = color
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(eventRect, 4 * density, 4 * density, paint)
                    
                    if (endY - startY > 15 * density) {
                        textPaint.color = if (ColorHelper.isDark(color)) Color.WHITE else Color.BLACK
                        textPaint.textAlign = Paint.Align.LEFT
                        textPaint.textSize = 12 * density
                        val availableWidth = colWidth - 8 * density
                        val textPaintForLayout = android.text.TextPaint(textPaint)
                        val ellipsizedTitle = android.text.TextUtils.ellipsize(
                            event.title,
                            textPaintForLayout,
                            availableWidth,
                            android.text.TextUtils.TruncateAt.END
                        )
                        canvas.drawText(ellipsizedTitle.toString(), eventRect.left + 4 * density, eventRect.top + 14 * density, textPaint)

                        textPaint.textSize = 10 * density
                        val links = mutableListOf<Triple<String, String, String>>()
                        event.linkedMemberIds.forEach { id -> people.find { it.id == id }?.let { links.add(Triple("MEMBER", it.id, it.name)) } }
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
                            val textWidth = text.length * 7 * density // Rough estimate
                            if (currentX + textWidth < colWidthAdjusted && eventRect.top + 32 * density < eventRect.bottom) {
                                canvas.drawText(text, eventRect.left + currentX, eventRect.top + 28 * density, textPaint)
                                currentX += textWidth + spacing
                            }
                        }
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
