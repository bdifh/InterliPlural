package com.interli.plural.features.calendar

import android.content.Context
import android.graphics.*
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

    private val eventDaysMap = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()

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
        daysInMonth[1] = if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    }

    private fun handleTouch(tx: Float, ty: Float) {
        val density = resources.displayMetrics.density
        val colWidth = width / 13f
        val rowHeight = 20 * density
        val headerHeight = 45 * density

        val month = (tx / colWidth).toInt() - 1
        if (month !in 0..11) return

        val day = ((ty - headerHeight) / rowHeight).toInt() + 1
        if (day in 1..daysInMonth[month]) {
            val cal = Calendar.getInstance()
            cal.set(year, month, day, 12, 0, 0)
            onDayClicked?.invoke(cal.time)
        }
    }

    fun setEvents(allEvents: List<CalendarEvent>, people: List<Person>, year: Int) {
        this.events = allEvents
        this.people = people
        this.year = year
        updateLeapYear()
        eventDaysMap.clear()

        val calEnd = Calendar.getInstance().apply { set(year, 11, 31, 23, 59, 59) }

        allEvents.forEach { event ->
            val color = getEventColor(event)

            if (event.recurrence == null) {
                markDay(event.startTime, color)
            } else {
                val cal = Calendar.getInstance().apply { timeInMillis = event.startTime }
                while (cal.timeInMillis <= calEnd.timeInMillis) {
                    val currentStart = cal.timeInMillis
                    val isPastEnd = event.recurrenceUntil?.let { currentStart > it } ?: false
                    val isExcluded = event.excludedDates?.contains(currentStart) == true

                    val matches = when (event.recurrence) {
                        "DAILY" -> true
                        "WEEKLY" -> true
                        "MONTHLY" -> true
                        "YEARLY" -> true
                        "CUSTOM" -> event.recurrenceDays?.contains(cal.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }) == true
                        else -> false
                    }

                    if (matches && !isPastEnd && !isExcluded) {
                        markDay(currentStart, color)
                    }

                    when (event.recurrence) {
                        "DAILY", "CUSTOM" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                        "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                        "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                        "YEARLY" -> cal.add(Calendar.YEAR, 1)
                        else -> break
                    }
                }
            }
        }
        invalidate()
    }

    private fun markDay(timestamp: Long, color: Int) {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        if (c.get(Calendar.YEAR) == year) {
            val key = c.get(Calendar.MONTH) to c.get(Calendar.DAY_OF_MONTH)
            val colors = eventDaysMap.getOrPut(key) { mutableListOf() }
            colors.add(color)
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val height = (31 * 20 * density + 70 * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val colWidth = width / 13f
        val rowHeight = 20 * density
        val headerHeight = 45 * density
        val dotRadius = 8.5f * density
        val textColor = ColorHelper.getTextColor(context)
        val btnColor = ColorHelper.getBtnColor(context)
        val btnTextColor = ColorHelper.getBtnTextColor(context)

        textPaint.color = textColor
        textPaint.textSize = 14 * density

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.alpha = 100
        textPaint.textSize = 10 * density
        for (d in 1..31) {
            val y = headerHeight + (d - 1) * rowHeight + rowHeight / 2 + (textPaint.textSize / 3)
            canvas.drawText(d.toString(), colWidth / 2, y, textPaint)
        }
        textPaint.alpha = 255
        textPaint.textSize = 14 * density

        for (i in 0 until 12) {
            canvas.drawText(monthLetters[i], (i + 1) * colWidth + colWidth / 2, 35 * density, textPaint)
        }

        for (m in 0 until 12) {
            for (d in 1..daysInMonth[m]) {
                val x = (m + 1) * colWidth + colWidth / 2
                val y = headerHeight + (d - 1) * rowHeight + rowHeight / 2

                val colors = eventDaysMap[m to d]
                if (!colors.isNullOrEmpty()) {
                    val count = colors.size
                    paint.color = btnColor
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(x, y, dotRadius, paint)

                    if (count > 1) {
                        val countText = if (count > 9) "9+" else count.toString()
                        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = btnTextColor
                            textSize = 10 * density
                            textAlign = Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                        canvas.drawText(countText, x, y + (countPaint.textSize / 3), countPaint)
                    }
                }
            }
        }
    }
}