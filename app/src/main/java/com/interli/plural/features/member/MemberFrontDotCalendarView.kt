package com.interli.plural.features.member

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.interli.plural.core.ColorHelper
import com.interli.plural.FrontSession
import java.util.*

class MemberFrontDotCalendarView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private var activityMap = mutableMapOf<Pair<Int, Int>, MutableList<FrontSession>>() // Month/Day to Sessions
    private var baseColor = 0xFF7D4EBA.toInt()
    private val monthLetters = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    private val daysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val year = Calendar.getInstance().get(Calendar.YEAR)
    var onDayClicked: ((List<FrontSession>) -> Unit)? = null
    init {
        if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) {
            daysInMonth[1] = 29
        }
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
        val density = resources.displayMetrics.density
        val colWidth = width / 12f
        val rowHeight = 18 * density
        val headerHeight = 40 * density
        val month = (tx / colWidth).toInt().coerceIn(0, 11)
        val day = ((ty - headerHeight) / rowHeight).toInt() + 1
        if (day in 1..daysInMonth[month]) {
            val daySessions = activityMap[month to day]
            if (!daySessions.isNullOrEmpty()) {
                onDayClicked?.invoke(daySessions)
            }
        }
    }
    fun setData(sessions: List<FrontSession>, personId: String, color: Int) {
        this.baseColor = color
        activityMap.clear()
        val cal = Calendar.getInstance()
        sessions.filter { it.personId == personId }.forEach { session ->
            val start = session.startTime
            val end = session.endTime ?: System.currentTimeMillis()
            cal.timeInMillis = start
            while (cal.timeInMillis <= end) {
                if (cal.get(Calendar.YEAR) == year) {
                    val m = cal.get(Calendar.MONTH)
                    val d = cal.get(Calendar.DAY_OF_MONTH)
                    val dayStart = cal.apply { 
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val dayEnd = dayStart + 24 * 60 * 60 * 1000
                    val overlapStart = maxOf(start, dayStart)
                    val overlapEnd = minOf(end, dayEnd)
                    val durationMs = overlapEnd - overlapStart
                    if (durationMs > 0) {
                        val key = m to d
                        activityMap.getOrPut(key) { mutableListOf() }.add(session)
                    }
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                if (cal.get(Calendar.YEAR) > year) break
            }
        }
        invalidate()
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
        val cal = Calendar.getInstance()
        for (m in 0 until 12) {
            for (d in 1..daysInMonth[m]) {
                val x = m * colWidth + colWidth / 2
                val y = headerHeight + (d - 1) * rowHeight + rowHeight / 2
                val sessions = activityMap[m to d]
                if (!sessions.isNullOrEmpty()) {
                    // Calculate total duration for intensity
                    var totalMinutes = 0L
                    sessions.forEach { s ->
                        cal.set(year, m, d, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
                        val dayStart = cal.timeInMillis
                        val dayEnd = dayStart + 24 * 60 * 60 * 1000
                        val overlapStart = maxOf(s.startTime, dayStart)
                        val overlapEnd = minOf(s.endTime ?: System.currentTimeMillis(), dayEnd)
                        val duration = overlapEnd - overlapStart
                        if (duration > 0) totalMinutes += (duration / 60000)
                    }
                    val alpha = when {
                        totalMinutes < 60 -> 0x66
                        totalMinutes < 240 -> 0xAA
                        else -> 0xFF
                    }
                    paint.color = (baseColor and 0x00FFFFFF) or (alpha shl 24)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(x, y, dotRadius, paint)
                } else {
                    drawEmptyDot(canvas, x, y, dotRadius, textColor)
                }
            }
        }
    }
    private fun drawEmptyDot(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.color = color
        paint.alpha = 30
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawCircle(x, y, radius * 0.8f, paint)
        paint.alpha = 255
    }
}
