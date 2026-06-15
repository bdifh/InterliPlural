package com.interli.plural

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import java.util.Calendar

class MemberSwitchChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var hourData: Array<Map<String, Int>> = Array(24) { mapOf<String, Int>() }
    private var personColors: Map<String, Int> = mapOf()
    private var personNames: Map<String, String> = mapOf()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            checkClick(e.x, e.y)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) return true
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun checkClick(touchX: Float, touchY: Float) {
        val density = resources.displayMetrics.density
        val padding = 8f * density
        val chartWidth = width - padding * 2
        val chartHeight = height - (52f * density)
        val barWidth = chartWidth / 24f

        if (touchX < padding || touchX > width - padding) return
        
        if (touchY > chartHeight + (25f * density) || touchY < 0) return

        val hour = ((touchX - padding) / barWidth).toInt().coerceIn(0, 23)
        val data = hourData[hour]
        
        if (data.isNotEmpty()) {
            showDetails(hour, data)
        }
    }

    private fun showDetails(hour: Int, data: Map<String, Int>) {
        val total = data.values.sum()
        val message = StringBuilder()
        
        message.append(context.getString(R.string.stats_hour_range, hour)).append("\n")
        message.append(context.getString(R.string.stats_total_switches, total)).append("\n\n")
        message.append(context.getString(R.string.stats_switches_by)).append("\n")
        
        data.toList().sortedByDescending { it.second }.forEach { (idOrName, count) ->
            val displayName = personNames[idOrName] ?: idOrName
            message.append("- $displayName: $count").append("\n")
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.stats_details))
            .setMessage(message.toString().trim())
            .setPositiveButton("OK", null)
            .show()
    }

    fun setData(sessions: List<FrontSession>, people: List<Person>) {
        val colorsMap = mutableMapOf<String, Int>()
        val namesMap = mutableMapOf<String, String>()
        people.forEach { 
            colorsMap[it.id] = it.profileColor
            colorsMap[it.name] = it.profileColor
            namesMap[it.id] = it.name
            namesMap[it.name] = it.name
        }
        personColors = colorsMap
        personNames = namesMap
        
        val newHourData = Array(24) { mutableMapOf<String, Int>() }
        sessions.forEach { s ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = s.startTime
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val key = s.personId ?: s.personName
            newHourData[hour][key] = (newHourData[hour][key] ?: 0) + 1
        }
        hourData = newHourData.map { it.toMap() }.toTypedArray()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (250 * resources.displayMetrics.density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hourData.all { it.isEmpty() }) return

        val density = resources.displayMetrics.density

        val padding = 8f * density
        val chartWidth = width - padding * 2
        val chartHeight = height - (52f * density)
        val barWidth = chartWidth / 24f
        val spacing = 1f * density

        val maxTotalPerHour = hourData.maxOfOrNull { it.values.sum() }?.toFloat() ?: 1f
        val textColor = ColorHelper.getTextColor(context)
        textPaint.color = textColor
        textPaint.textSize = minOf(12f * density, barWidth * 0.9f)

        for (h in 0 until 24) {
            val x = padding + h * barWidth
            val data = hourData[h]
            var currentY = chartHeight + (20f * density)
            
            val total = data.values.sum().toFloat()
            if (total > 0) {
                data.toList().sortedByDescending { it.second }.forEach { (name, count) ->
                    val segmentHeight = (count / maxTotalPerHour) * chartHeight
                    paint.color = personColors[name] ?: Color.GRAY
                    canvas.drawRect(
                        x + spacing, 
                        currentY - segmentHeight, 
                        x + barWidth - spacing, 
                        currentY, 
                        paint
                    )
                    currentY -= segmentHeight
                }
            }

            if (h % 6 == 0) {
                canvas.drawText("${h}u", x + barWidth / 2, height - (10f * density), textPaint)
            }
        }
    }
}
