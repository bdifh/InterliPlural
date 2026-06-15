package com.interli.plural

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class MemberDurationBoxPlotView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private data class Stats(
        val min: Float,
        val q1: Float,
        val median: Float,
        val q3: Float,
        val max: Float,
        val mean: Float,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private var rows: List<Pair<String, Stats>> = emptyList()
    private var colors: Map<String, Int> = emptyMap()
    private var maxValue: Float = 1f

    fun setData(durationsMsByName: Map<String, List<Long>>, people: List<Person>) {
        colors = people.associate { it.name to it.profileColor }

        val computed = durationsMsByName.mapNotNull { (name, values) ->
            val sorted = values.filter { it > 0L }.sorted()
            if (sorted.isEmpty()) return@mapNotNull null

            val minV = sorted.first().toFloat()
            val maxV = sorted.last().toFloat()
            val q1 = quantile(sorted, 0.25f)
            val median = quantile(sorted, 0.5f)
            val q3 = quantile(sorted, 0.75f)
            val mean = (sorted.sum().toDouble() / sorted.size.toDouble()).toFloat()

            name to Stats(
                min = minV,
                q1 = q1,
                median = median,
                q3 = q3,
                max = maxV,
                mean = mean
            )
        }

        rows = computed.sortedByDescending { it.second.mean }
        maxValue = max(1f, rows.maxOfOrNull { it.second.max } ?: 1f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val rowH = 38f * density
        val h = (paddingTop + paddingBottom + (rows.size.coerceAtLeast(1) * rowH)).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rows.isEmpty()) return

        val density = resources.displayMetrics.density

        val leftLabelW = 120f * density
        val rowH = 38f * density
        val plotH = 16f * density
        val plotTopPad = (rowH - plotH) / 2f

        val plotLeft = paddingLeft + leftLabelW
        val plotRight = width - paddingRight.toFloat()
        val plotW = max(1f, plotRight - plotLeft)

        val textColor = ColorHelper.getTextColor(context)
        textPaint.color = textColor
        textPaint.textSize = 12f * density

        linePaint.color = (textColor and 0x00FFFFFF) or 0x33000000

        fun xFor(v: Float): Float = plotLeft + (v / maxValue) * plotW

        rows.forEachIndexed { idx, (name, s) ->
            val rowTop = paddingTop + idx * rowH
            val centerY = rowTop + rowH / 2f

            canvas.drawText(name, paddingLeft.toFloat(), centerY + (textPaint.textSize * 0.35f), textPaint)

            val xMin = xFor(s.min)
            val xMax = xFor(s.max)
            paint.color = linePaint.color
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(xMin, centerY, xMax, centerY, paint)

            canvas.drawLine(xMin, centerY - plotH / 2f, xMin, centerY + plotH / 2f, paint)
            canvas.drawLine(xMax, centerY - plotH / 2f, xMax, centerY + plotH / 2f, paint)

            val color = colors[name] ?: Color.GRAY
            val box = RectF(
                xFor(s.q1),
                rowTop + plotTopPad,
                xFor(s.q3),
                rowTop + plotTopPad + plotH
            )
            paint.style = Paint.Style.FILL
            paint.color = (color and 0x00FFFFFF) or 0x33000000
            canvas.drawRoundRect(box, 6f * density, 6f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.color = (color and 0x00FFFFFF) or 0x99000000.toInt()
            canvas.drawRoundRect(box, 6f * density, 6f * density, paint)

            val xMed = xFor(s.median)
            paint.color = (color and 0x00FFFFFF) or 0xCC000000.toInt()
            canvas.drawLine(xMed, box.top, xMed, box.bottom, paint)

            val xMean = xFor(s.mean)
            paint.style = Paint.Style.FILL
            paint.color = (color and 0x00FFFFFF) or 0xFF000000.toInt()
            canvas.drawCircle(xMean, centerY, 3.5f * density, paint)
        }
    }

    private fun quantile(sorted: List<Long>, q: Float): Float {
        if (sorted.isEmpty()) return 0f
        if (sorted.size == 1) return sorted[0].toFloat()
        val pos = q * (sorted.size - 1)
        val i = pos.toInt()
        val frac = pos - i
        val a = sorted[i].toFloat()
        val b = sorted[min(i + 1, sorted.lastIndex)].toFloat()
        return a + (b - a) * frac
    }
}
