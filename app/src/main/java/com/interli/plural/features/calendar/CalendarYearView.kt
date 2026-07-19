package com.interli.plural.features.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.interli.plural.CalendarEvent
import com.interli.plural.core.ColorHelper
import com.interli.plural.Person
import java.util.*

class CalendarYearView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var events: List<CalendarEvent> = emptyList()
    private var people: List<Person> = emptyList()
    private val eventDaysMap = mutableMapOf<Pair<Int, Int>, Int>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val monthLetters = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    private val daysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private var year = Calendar.getInstance().get(Calendar.YEAR)
    var onDayClicked: ((Date) -> Unit)? = null
    init {
        updateLeapYear()
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
    private fun updateLeapYear() {
        if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) {
            daysInMonth[1] = 29
        } else {
            daysInMonth[1] = 28
        }
    }
    private fun handleTouch(tx: Float, ty: Float) {
        val density = resources.displayMetrics.density
        val colWidth = width / 12f
        val rowHeight = 18 * density
        val headerHeight = 40 * density
        val month = (tx / colWidth).toInt().coerceIn(0, 11)
        val day = ((ty - headerHeight) / rowHeight).toInt() + 1
        if (day in 1..daysInMonth[month]) {
            val cal = Calendar.getInstance()
            cal.set(year, month, day, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onDayClicked?.invoke(cal.time)
        }
    }
    fun setEvents(events: List<CalendarEvent>, people: List<Person>, year: Int) {
        this.events = events
        this.people = people
        this.year = year
        updateLeapYear()
        eventDaysMap.clear()
        val cal = Calendar.getInstance()
        events.forEach { event ->
            val color = getEventColor(event)
            // Use startTime to mark the beginning
            cal.timeInMillis = event.startTime
            if (cal.get(Calendar.YEAR) == year) {
                val key = cal.get(Calendar.MONTH) to cal.get(Calendar.DAY_OF_MONTH)
                if (!eventDaysMap.containsKey(key)) eventDaysMap[key] = color
            }
            if (event.endTime > event.startTime) {
                val startCal = Calendar.getInstance().apply { timeInMillis = event.startTime }
                startCal.set(Calendar.HOUR_OF_DAY, 0); startCal.set(Calendar.MINUTE, 0); startCal.set(Calendar.SECOND, 0); startCal.set(Calendar.MILLISECOND, 0)
                val tempCal = startCal.clone() as Calendar
                while (tempCal.timeInMillis < event.endTime) {
                    if (tempCal.get(Calendar.YEAR) == year) {
                        val key = tempCal.get(Calendar.MONTH) to tempCal.get(Calendar.DAY_OF_MONTH)
                        if (!eventDaysMap.containsKey(key)) eventDaysMap[key] = color
                    } else if (tempCal.get(Calendar.YEAR) > year) break
                    tempCal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        invalidate()
    }
    private fun getEventColor(event: CalendarEvent): Int {
        if (event.color != null) return event.color!!
        if (event.linkedMemberIds.isNotEmpty()) {
            val person = people.find { it.id == event.linkedMemberIds[0] }
            if (person != null) return person.profileColor
        }
        return ColorHelper.getBtnColor(context)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val height = (31 * 18 * density + 60 * density).toInt()
        setMeasuredDimension(width, height)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val colWidth = width / 12f
        val rowHeight = 18 * density
        val headerHeight = 40 * density
        val dotRadius = 6 * density
        val textColor = ColorHelper.getTextColor(context)
        textPaint.color = textColor
        textPaint.textSize = 14 * density
        for (i in 0 until 12) {
            canvas.drawText(monthLetters[i], i * colWidth + colWidth / 2, 30 * density, textPaint)
        }
        for (m in 0 until 12) {
            for (d in 1..daysInMonth[m]) {
                val x = m * colWidth + colWidth / 2
                val y = headerHeight + (d - 1) * rowHeight + rowHeight / 2
                eventDaysMap[m to d]?.let { color ->
                    paint.color = color
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(x, y, dotRadius, paint)
                }
            }
        }
    }
}
