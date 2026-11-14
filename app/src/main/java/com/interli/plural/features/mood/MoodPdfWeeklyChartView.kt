package com.interli.plural.features.mood

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.text.TextPaint
import android.view.View
import com.interli.plural.features.mood.MoodActivity.MoodEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MoodPdfWeeklyChartView(
    context: Context,
    private val dayStarts: List<Long>,
    private val entries: List<MoodEntry>
) : View(context) {

    private val panelHeight = 96f
    private val dateFormat = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())

    val requiredHeight: Int
        get() = (dayStarts.size * panelHeight).toInt().coerceAtLeast(1)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCCCCCC.toInt()
        strokeWidth = 0.6f
        style = Paint.Style.STROKE
    }

    private val scorePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 8f
        textAlign = Paint.Align.RIGHT
    }

    private val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 9f
        isFakeBoldText = true
    }

    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 7f
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        val plotLeft = 38f
        val plotRight = width.toFloat() - 6f
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)

        dayStarts.forEachIndexed { dayIndex, dayStart ->
            val panelTop = dayIndex * panelHeight
            val plotTop = panelTop + 18f
            val plotBottom = panelTop + panelHeight - 17f
            val plotHeight = plotBottom - plotTop

            val nextDay = Calendar.getInstance().apply {
                timeInMillis = dayStart
                add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
            val dayDuration = (nextDay - dayStart).coerceAtLeast(1L)

            canvas.drawText(
                dateFormat.format(Date(dayStart)),
                3f,
                panelTop + 11f,
                datePaint
            )

            for (score in 1..5) {
                val y = plotBottom - ((score - 1) / 4f * plotHeight)
                canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
                canvas.drawText(score.toString(), plotLeft - 5f, y + 3f, scorePaint)
            }

            listOf(0, 6, 12, 18, 24).forEach { hour ->
                val tickTime = Calendar.getInstance().apply {
                    timeInMillis = dayStart
                    if (hour == 24) {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                    } else {
                        set(Calendar.HOUR_OF_DAY, hour)
                    }
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val fraction = ((tickTime - dayStart).toFloat() / dayDuration)
                    .coerceIn(0f, 1f)
                val x = plotLeft + plotWidth * fraction

                canvas.drawLine(x, plotTop, x, plotBottom, gridPaint)

                timePaint.textAlign = when (hour) {
                    0 -> Paint.Align.LEFT
                    24 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                canvas.drawText(
                    if (hour == 24) "24:00" else "%02d:00".format(hour),
                    x,
                    panelTop + panelHeight - 2f,
                    timePaint
                )
            }

            val dayEntries = entries
                .filter { it.timestamp >= dayStart && it.timestamp < nextDay }
                .sortedBy { it.timestamp }

            val points = mutableListOf<Triple<Float, Float, Int>>()

            fun pointFor(entry: MoodEntry, x: Float): Triple<Float, Float, Int> {
                val score = moodScore(entry.moodLabel).toFloat()
                val y = plotBottom - ((score - 1f) / 4f * plotHeight)
                return Triple(x, y, entry.moodColor)
            }

            val previousEntry = entries.lastOrNull { it.timestamp < dayStart }
            if (previousEntry != null) {
                points.add(pointFor(previousEntry, plotLeft))
            }

            dayEntries.forEach { entry ->
                val x = plotLeft +
                        ((entry.timestamp - dayStart).toFloat() / dayDuration) * plotWidth
                points.add(pointFor(entry, x))
            }

            val lastEntry = dayEntries.lastOrNull() ?: previousEntry
            if (lastEntry != null) {
                points.add(pointFor(lastEntry, plotRight))
            }

            if (points.size > 1) {
                val colors = points.map { it.third }.toIntArray()
                val positions = FloatArray(points.size) { index ->
                    index.toFloat() / (points.size - 1).toFloat()
                }
                linePaint.shader = LinearGradient(
                    points.first().first,
                    0f,
                    points.last().first,
                    0f,
                    colors,
                    positions,
                    Shader.TileMode.CLAMP
                )

                val path = Path().apply {
                    moveTo(points.first().first, points.first().second)
                    for (index in 1 until points.size) {
                        val previous = points[index - 1]
                        val current = points[index]
                        val controlX = (previous.first + current.first) / 2f
                        cubicTo(
                            controlX, previous.second,
                            controlX, current.second,
                            current.first, current.second
                        )
                    }
                }
                canvas.drawPath(path, linePaint)
                linePaint.shader = null
            }

            dayEntries.forEach { entry ->
                val x = plotLeft +
                        ((entry.timestamp - dayStart).toFloat() / dayDuration) * plotWidth
                val score = moodScore(entry.moodLabel).toFloat()
                val y = plotBottom - ((score - 1f) / 4f * plotHeight)

                pointPaint.style = Paint.Style.FILL
                pointPaint.color = entry.moodColor
                canvas.drawCircle(x, y, 5f, pointPaint)

                pointPaint.style = Paint.Style.STROKE
                pointPaint.strokeWidth = 1.2f
                pointPaint.color = Color.WHITE
                canvas.drawCircle(x, y, 5f, pointPaint)
            }
        }
    }

    private fun moodScore(label: String): Int {
        val keys = listOf(
            "mood_awful",
            "mood_bad",
            "mood_meh",
            "mood_good",
            "mood_rad"
        )

        val keyIndex = keys.indexOf(label)
        if (keyIndex >= 0) return keyIndex + 1

        val localizedIndex = keys.indexOfFirst { key ->
            val resourceId = context.resources.getIdentifier(
                key,
                "string",
                context.packageName
            )
            resourceId != 0 && context.getString(resourceId) == label
        }

        return if (localizedIndex >= 0) localizedIndex + 1 else 3
    }
}