package com.interli.plural

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.*

class MemberMoodDotCalendarView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    
    private var activityMap = mutableMapOf<Pair<Int, Int>, MutableList<MoodActivity.MoodEntry>>()
    
    private val monthLetters = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    private val daysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val year = Calendar.getInstance().get(Calendar.YEAR)

    var onDayClicked: ((List<MoodActivity.MoodEntry>) -> Unit)? = null

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
            val dayEntries = activityMap[month to day]
            if (!dayEntries.isNullOrEmpty()) {
                onDayClicked?.invoke(dayEntries)
            }
        }
    }

    fun setData(entries: List<MoodActivity.MoodEntry>, personId: String) {
        activityMap.clear()
        
        val cal = Calendar.getInstance()
        entries.filter { it.memberIds.contains(personId) }.forEach { entry ->
            cal.timeInMillis = entry.timestamp
            if (cal.get(Calendar.YEAR) == year) {
                val m = cal.get(Calendar.MONTH)
                val d = cal.get(Calendar.DAY_OF_MONTH)
                val key = m to d
                activityMap.getOrPut(key) { mutableListOf() }.add(entry)
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

        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")

        for (m in 0 until 12) {
            for (d in 1..daysInMonth[m]) {
                val x = m * colWidth + colWidth / 2
                val y = headerHeight + (d - 1) * rowHeight + rowHeight / 2
                
                val entries = activityMap[m to d]
                if (!entries.isNullOrEmpty()) {
                    val avgScore = entries.map { entry ->
                        val idx = moodKeys.indexOf(entry.moodLabel)
                        if (idx != -1) idx.toFloat() else 2f
                    }.average().toFloat()
                    
                    paint.color = ColorHelper.getMoodColorByScore(context, avgScore)
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
