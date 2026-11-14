package com.interli.plural.features.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import com.interli.plural.FrontSession
import com.interli.plural.Person
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FrontHistoryPdfChartView(
    context: Context,
    private val dayStarts: List<Long>,
    private val sessions: List<FrontSession>,
    people: List<Person>,
    private val laneHeight: Float,
    private val panelSpacing: Float = 8f
) : View(context) {

    private data class MemberLane(
        val key: String,
        val name: String,
        val color: Int
    )

    private val lanes = sessions
        .distinctBy { memberKey(it) }
        .map { session ->
            val person = session.personId?.let { id ->
                people.firstOrNull { it.id == id }
            } ?: people.firstOrNull { it.name == session.personName }

            MemberLane(
                key = memberKey(session),
                name = person?.name ?: session.personName,
                color = person?.profileColor ?: Color.GRAY
            )
        }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }

    private val dateFormat = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
    private val headerHeight = 20f
    private val nameColumnWidth = 125f

    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 7f
        strokeWidth = 0.6f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCCCCCC.toInt()
        strokeWidth = 0.5f
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 8.5f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 210
    }

    private val panelHeight =
        headerHeight + lanes.size * laneHeight + panelSpacing

    val requiredHeight: Int
        get() = (
                dayStarts.size * panelHeight - panelSpacing
                ).toInt().coerceAtLeast(1)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)

        val plotLeft = nameColumnWidth
        val plotRight = width.toFloat() - 5f
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val textSize = (laneHeight * 0.78f).coerceIn(5.5f, 7.5f)
        namePaint.textSize = textSize

        dayStarts.forEachIndexed { dayIndex, dayStart ->
            val panelTop = dayIndex * panelHeight
            val rowsTop = panelTop + headerHeight
            val panelBottom = rowsTop + lanes.size * laneHeight

            val dayEndCalendar = Calendar.getInstance().apply {
                timeInMillis = dayStart
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val dayEnd = dayEndCalendar.timeInMillis
            val dayDuration = (dayEnd - dayStart).coerceAtLeast(1L)

            canvas.drawText(
                dateFormat.format(Date(dayStart)),
                4f,
                panelTop + 14f,
                datePaint
            )

            listOf(0, 6, 12, 18, 24).forEach { hour ->
                val tickCalendar = Calendar.getInstance().apply {
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
                }

                val fraction = (
                        (tickCalendar.timeInMillis - dayStart).toFloat() / dayDuration
                        ).coerceIn(0f, 1f)
                val x = plotLeft + plotWidth * fraction

                canvas.drawLine(x, rowsTop, x, panelBottom, gridPaint)

                axisPaint.textAlign = when (hour) {
                    0 -> Paint.Align.LEFT
                    24 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                canvas.drawText(
                    if (hour == 24) "24:00" else "%02d:00".format(hour),
                    x,
                    panelTop + 14f,
                    axisPaint
                )
            }

            lanes.forEachIndexed { laneIndex, lane ->
                val rowTop = rowsTop + laneIndex * laneHeight
                val rowBottom = rowTop + laneHeight
                val centerY = rowTop + laneHeight / 2f

                val label = TextUtils.ellipsize(
                    lane.name,
                    namePaint,
                    nameColumnWidth - 10f,
                    TextUtils.TruncateAt.END
                ).toString()
                canvas.drawText(label, 4f, centerY + textSize * 0.35f, namePaint)
                canvas.drawLine(0f, rowBottom, width.toFloat(), rowBottom, gridPaint)

                sessions.forEach sessionLoop@{ session ->
                    if (memberKey(session) != lane.key) return@sessionLoop

                    val sessionEnd = session.endTime ?: System.currentTimeMillis()
                    if (session.startTime >= dayEnd || sessionEnd <= dayStart) {
                        return@sessionLoop
                    }

                    val clippedStart = maxOf(session.startTime, dayStart)
                    val clippedEnd = minOf(sessionEnd, dayEnd)
                    val left = plotLeft + plotWidth *
                            ((clippedStart - dayStart).toFloat() / dayDuration)
                    val right = plotLeft + plotWidth *
                            ((clippedEnd - dayStart).toFloat() / dayDuration)

                    val barHeight = (laneHeight * 0.62f).coerceAtLeast(3.5f)
                    val barTop = centerY - barHeight / 2f

                    barPaint.color = lane.color
                    canvas.drawRoundRect(
                        RectF(
                            left,
                            barTop,
                            right.coerceAtLeast(left + 1f),
                            barTop + barHeight
                        ),
                        2f,
                        2f,
                        barPaint
                    )

                    if (!session.note.isNullOrBlank() && right - left > 6f) {
                        canvas.drawCircle((left + right) / 2f, centerY, 1.5f, notePaint)
                    }
                }
            }
        }
    }

    private fun memberKey(session: FrontSession): String =
        session.personId ?: "name:${session.personName}"
}