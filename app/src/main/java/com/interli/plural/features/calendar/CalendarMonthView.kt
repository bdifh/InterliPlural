package com.interli.plural.features.calendar

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.interli.plural.CalendarEvent
import com.interli.plural.core.ColorHelper
import com.interli.plural.Person
import java.text.SimpleDateFormat
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
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eventPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eventTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 0f
    }

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

    private fun getFirstDayOffset(): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val firstDay = cal.get(Calendar.DAY_OF_WEEK)
        return (firstDay + 5) % 7 // Monday=0
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
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val cellWidth = width / 7f
        val cellHeight = height / 7f
        val textColor = ColorHelper.getTextColor(context)
        val btnColor = ColorHelper.getBtnColor(context)
        val bgColor = ColorHelper.getBgColor(context)

        canvas.drawColor(bgColor)

        textPaint.color = textColor
        textPaint.textSize = 12 * density
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.alpha = 128

        val daySdf = SimpleDateFormat("EEEEE", Locale.getDefault())
        val calHeader = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
        for (i in 0 until 7) {
            canvas.drawText(daySdf.format(calHeader.time), i * cellWidth + cellWidth / 2, cellHeight * 0.4f, textPaint)
            calHeader.add(Calendar.DAY_OF_WEEK, 1)
        }

        val offset = getFirstDayOffset()
        val totalDays = getDaysInMonth()

        for (i in 0 until 42) {
            val col = i % 7
            val row = (i / 7) + 1
            val x = col * cellWidth
            val y = row * cellHeight
            val rect = dayRects[i].apply { set(x, y, x + cellWidth, y + cellHeight) }

            paint.style = Paint.Style.STROKE
            paint.color = textColor
            paint.alpha = 15
            canvas.drawRect(rect, paint)

            val day = i - offset + 1
            if (day in 1..totalDays) {
                val isToday = Calendar.getInstance().let {
                    it.get(Calendar.YEAR) == year && it.get(Calendar.MONTH) == month && it.get(Calendar.DAY_OF_MONTH) == day
                }

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.isFakeBoldText = true
                textPaint.alpha = 255

                if (isToday) {
                    paint.style = Paint.Style.FILL
                    paint.color = btnColor
                    canvas.drawRoundRect(rect.left + 2*density, rect.top + 2*density, rect.left + 22*density, rect.top + 22*density, 4*density, 4*density, paint)
                    textPaint.color = Color.WHITE
                } else {
                    textPaint.color = textColor
                }

                canvas.drawText(day.toString(), rect.left + 6*density, rect.top + 16*density, textPaint)

                drawEventsForDay(canvas, day, rect, density, btnColor)
            }
        }
    }

    private fun drawEventsForDay(canvas: Canvas, day: Int, rect: RectF, density: Float, defaultColor: Int) {
        val cal = Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val endOfDay = startOfDay + (24 * 3600 * 1000) - 1

        val dayEvents = events.filter { it.startTime <= endOfDay && it.endTime >= startOfDay }
            .sortedBy { it.startTime }
            .take(5)

        eventTextPaint.textSize = 10 * density
        var currentY = rect.top + 26 * density

        dayEvents.forEach { event ->
            val color = getEventColor(event, defaultColor)
            eventPaint.color = color
            eventPaint.style = Paint.Style.FILL

            val barRect = RectF(rect.left + 2*density, currentY, rect.right - 2*density, currentY + 16*density)
            canvas.drawRoundRect(barRect, 2*density, 2*density, eventPaint)

            eventTextPaint.color = getContrastColor(color)
            val title = if (event.title.length > 15) event.title.take(13) + ".." else event.title
            canvas.drawText(title, barRect.left + 4*density, barRect.centerY() + 4*density, eventTextPaint)

            currentY += 18 * density
        }
    }
    private fun getEventColor(event: CalendarEvent, defaultColor: Int): Int {
        if (event.color != null) return event.color!!
        if (event.linkedMemberIds.isNotEmpty()) {
            val person = people.find { it.id == event.linkedMemberIds[0] }
            if (person != null) return person.profileColor
        }
        return defaultColor
    }
    private fun getContrastColor(color: Int): Int {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        val yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000
        return if (yiq >= 128) Color.BLACK else Color.WHITE
    }
}