package com.interli.plural.features.member

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.interli.plural.core.ColorHelper
import com.interli.plural.FrontSession
import com.interli.plural.Person
import java.util.Calendar

class FrontDensityChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    data class MemberData(
        val id: String,
        val name: String,
        val color: Int,
        val hourlyMinutes: FloatArray
    )
    private var memberDataList: List<MemberData> = emptyList()
    private var maxMinutes: Float = 60f
    private var highlightedMemberId: String? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    fun setData(sessions: List<FrontSession>, people: List<Person>, startTime: Long, endTime: Long) {
        val actualStart = if (startTime <= 0) sessions.minOfOrNull { it.startTime } ?: System.currentTimeMillis() else startTime
        val actualEnd = if (endTime >= Long.MAX_VALUE / 2) System.currentTimeMillis() else endTime
        val dayMs = 24L * 60 * 60 * 1000
        val durationMs = (actualEnd - actualStart).coerceAtLeast(1)
        val numDays = (durationMs.toDouble() / dayMs).coerceAtLeast(1.0).toFloat()
        val dataMap = mutableMapOf<String, FloatArray>()
        val nameToId = people.associate { it.name to it.id }
        people.forEach { dataMap[it.id] = FloatArray(24) }
        sessions.forEach { session ->
            val memberId = session.personId ?: nameToId[session.personName] ?: return@forEach
            val minutesArray = dataMap[memberId] ?: return@forEach
            val sStart = session.startTime.coerceAtLeast(actualStart)
            val sEnd = (session.endTime ?: System.currentTimeMillis()).coerceAtMost(actualEnd)
            if (sStart >= sEnd) return@forEach
            var current = sStart
            while (current < sEnd) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = current
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.HOUR_OF_DAY, 1)
                val nextHourStart = cal.timeInMillis
                val endInThisHour = Math.min(sEnd, nextHourStart)
                val minutesInThisHour = (endInThisHour - current) / (1000f * 60f)
                minutesArray[hour] += minutesInThisHour
                current = endInThisHour
            }
        }
        var globalMax = 0f
        memberDataList = people.map { person ->
            val rawMinutes = dataMap[person.id] ?: FloatArray(24)
            val avgMinutes = FloatArray(24) { i ->
                val avg = (rawMinutes[i] / numDays).coerceIn(0f, 60f)
                if (avg > globalMax) globalMax = avg
                avg
            }
            MemberData(person.id, person.name, person.profileColor, avgMinutes)
        }.filter { data -> data.hourlyMinutes.any { it > 0.01f } }
        maxMinutes = when {
            globalMax <= 5f -> 5f
            globalMax <= 10f -> 10f
            globalMax <= 15f -> 15f
            globalMax <= 30f -> 30f
            globalMax <= 45f -> 45f
            else -> 60f
        }
        invalidate()
    }
    fun setHighlight(memberId: String?) {
        highlightedMemberId = if (highlightedMemberId == memberId) null else memberId
        invalidate()
    }
    fun getActiveMembers(): List<MemberData> = memberDataList
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val paddingLeft = 45f * density
        val paddingRight = 20f * density
        val paddingTop = 20f * density
        val paddingBottom = 40f * density
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom
        if (chartWidth <= 0 || chartHeight <= 0) return
        val textColor = ColorHelper.getTextColor(context)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = (textColor and 0x33FFFFFF) or 0x33000000
        val steps = 4
        for (i in 0..steps) {
            val ratio = i.toFloat() / steps
            val y = paddingTop + chartHeight - (ratio * chartHeight)
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, paint)
            textPaint.color = textColor
            textPaint.textSize = 10f * density
            textPaint.textAlign = Paint.Align.RIGHT
            val labelValue = (ratio * maxMinutes).toInt()
            canvas.drawText("${labelValue}m", paddingLeft - 5f * density, y + 4f * density, textPaint)
        }
        for (h in 0..24 step 6) {
            val x = paddingLeft + (h / 24f) * chartWidth
            canvas.drawLine(x, paddingTop, x, paddingTop + chartHeight, paint)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${h}h", x, paddingTop + chartHeight + 15f * density, textPaint)
        }
        paint.strokeWidth = 3f * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val sortedList = if (highlightedMemberId == null) {
            memberDataList
        } else {
            memberDataList.sortedBy { it.id == highlightedMemberId }
        }
        sortedList.forEach { member ->
            val isHighlighted = highlightedMemberId == member.id
            val isOtherHighlighted = highlightedMemberId != null && !isHighlighted
            if (isOtherHighlighted) {
                paint.color = Color.LTGRAY
                paint.alpha = 100
                paint.strokeWidth = 1.5f * density
            } else {
                paint.color = member.color
                paint.alpha = 255
                paint.strokeWidth = if (isHighlighted) 4.5f * density else 3f * density
            }
            path.reset()
            for (h in 0 until 24) {
                val x = paddingLeft + (h / 23f) * chartWidth
                val y = paddingTop + chartHeight - (member.hourlyMinutes[h] / maxMinutes * chartHeight)
                if (h == 0) path.moveTo(x, y)
                else {
                    val prevX = paddingLeft + ((h - 1) / 23f) * chartWidth
                    val prevY = paddingTop + chartHeight - (member.hourlyMinutes[h - 1] / maxMinutes * chartHeight)
                    val controlX = (prevX + x) / 2
                    path.cubicTo(controlX, prevY, controlX, y, x, y)
                }
            }
            canvas.drawPath(path, paint)
        }
    }
}
