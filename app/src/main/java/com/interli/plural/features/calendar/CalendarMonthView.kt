package com.interli.plural.features.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.interli.plural.CalendarEvent
import com.interli.plural.core.ColorHelper
import com.interli.plural.Person
import java.util.*

class CalendarMonthView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var events: List<CalendarEvent> = emptyList()
    private var people: List<Person> = emptyList()
    private var year = 2024
    private var month = 0
    private val dayRects = Array(42) { RectF() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
    var onDayClicked: ((Int) -> Unit)? = null
    init {
        setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                dayRects.forEachIndexed { index, rect ->
                    if (rect.contains(event.x, event.y)) {
                        val day = index - getFirstDayOffset() + 1
                        if (day in 1..getDaysInMonth()) {
                            onDayClicked?.invoke(day)
                        }
                    }
                }
            }
            true
        }
    }
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
    private fun getFirstDayOffset(): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val firstDay = cal.get(Calendar.DAY_OF_WEEK) // Sunday=1, Monday=2
        return (firstDay + 5) % 7 // Monday=0, Sunday=6
    }
    private fun getDaysInMonth(): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    fun setEvents(events: List<CalendarEvent>, people: List<Person>, year: Int, month: Int) {
        this.events = events
        this.people = people
        this.year = year
        this.month = month
        invalidate()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (7 * (width / 7))
        setMeasuredDimension(width, height)
    }
    private val tempRect = RectF()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val cellWidth = width / 7f
        val cellHeight = cellWidth
        val textColor = ColorHelper.getTextColor(context)
        val btnColor = ColorHelper.getBtnColor(context)
        val bgColor = ColorHelper.getBgColor(context)
        canvas.drawColor(bgColor)
        textPaint.color = textColor
        textPaint.textSize = 14 * density
        textPaint.isFakeBoldText = true
        // Draw Day Headers
        for (i in 0 until 7) {
            canvas.drawText(dayNames[i], i * cellWidth + cellWidth / 2, cellHeight * 0.6f, textPaint)
        }
        textPaint.isFakeBoldText = false
        val offset = getFirstDayOffset()
        val totalDays = getDaysInMonth()
        val startOfGrid = Calendar.getInstance().apply {
            set(year, month, 1)
            add(Calendar.DAY_OF_YEAR, -offset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val endOfGrid = (startOfGrid.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 42) }
        // Prep day rects
        for (i in 0 until 42) {
            val col = i % 7
            val row = (i / 7) + 1
            val x = col * cellWidth
            val y = row * cellHeight
            dayRects[i].set(x, y, x + cellWidth, y + cellHeight)
        }
        // Draw Cell Backgrounds & Numbers
        paint.color = textColor
        paint.alpha = 20
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        for (i in 0 until 42) {
            val rect = dayRects[i]
            canvas.drawRect(rect, paint)
        }
        paint.alpha = 255
        for (i in 0 until 42) {
            val rect = dayRects[i]
            val day = i - offset + 1
            if (day in 1..totalDays) {
                val today = Calendar.getInstance()
                if (today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month && today.get(Calendar.DAY_OF_MONTH) == day) {
                    paint.color = btnColor
                    paint.alpha = 40
                    tempRect.set(rect); tempRect.inset(4 * density, 4 * density)
                    canvas.drawRoundRect(tempRect, 8 * density, 8 * density, paint)
                    paint.alpha = 255
                }
                canvas.drawText(day.toString(), rect.centerX(), rect.centerY(), textPaint)
            }
        }
        // Draw Events
        val multiDayEvents = events.filter { 
            (it.endTime - it.startTime) > 24 * 60 * 60 * 1000 || 
            Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.DAY_OF_YEAR) != 
            Calendar.getInstance().apply { timeInMillis = it.endTime }.get(Calendar.DAY_OF_YEAR)
        }
        val singleDayEvents = events.filter { !multiDayEvents.contains(it) }
        // Single day stippen
        val eventCountsPerDay = mutableMapOf<Int, Int>()
        singleDayEvents.forEach { event ->
            val cal = Calendar.getInstance().apply { timeInMillis = event.startTime }
            if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                val d = cal.get(Calendar.DAY_OF_MONTH)
                val dayIdx = d + offset - 1
                if (dayIdx in 0..41) {
                    val count = eventCountsPerDay[dayIdx] ?: 0
                    eventCountsPerDay[dayIdx] = count + 1
                    val rect = dayRects[dayIdx]
                    val color = getEventColor(event)
                    paint.color = color
                    paint.style = Paint.Style.FILL
                    val dotY = rect.bottom - 15 * density
                    val dotX = rect.centerX() + (count - 1) * 8 * density - (if (count > 1) 4 * density else 0f)
                    canvas.drawCircle(dotX, dotY, 3 * density, paint)
                }
            }
        }
        // Multi-day balken
        multiDayEvents.forEach { event ->
            val color = getEventColor(event)
            paint.color = color
            paint.alpha = 180
            paint.style = Paint.Style.FILL
            var startIdx = -1
            var endIdx = -1
            for (i in 0 until 42) {
                val dayStart = (startOfGrid.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }.timeInMillis
                val dayEnd = dayStart + 24 * 60 * 60 * 1000
                if (event.startTime < dayEnd && event.endTime > dayStart) {
                    if (startIdx == -1) startIdx = i
                    endIdx = i
                }
            }
            if (startIdx != -1) {
                for (row in (startIdx / 7)..(endIdx / 7)) {
                    val rStart = Math.max(startIdx, row * 7)
                    val rEnd = Math.min(endIdx, row * 7 + 6)
                    val left = dayRects[rStart].left + 2 * density
                    val right = dayRects[rEnd].right - 2 * density
                    val bottom = dayRects[rStart].bottom - 4 * density
                    val top = bottom - 6 * density
                    canvas.drawRoundRect(left, top, right, bottom, 3 * density, 3 * density, paint)
                }
            }
            paint.alpha = 255
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
