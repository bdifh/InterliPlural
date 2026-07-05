package com.interli.plural

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MoodChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    enum class Mode { TIMELINE, HOUR_OF_DAY, SEVEN_DAY_AVERAGE, MONTH_VIEW, TODAY_VIEW }
    private var chartMode = Mode.TIMELINE

    private var entries: List<MoodActivity.MoodEntry> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var scaleFactor = 1.0f
    private var scrollOffset = 0f
    private var isInitialZoomSet = false

    private val scaleDetector by lazy { ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scaleFactor
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(1.0f, 300.0f)
                
                if (oldScale != scaleFactor) {
                    val pLeft = calculatePaddingLeft()
                    val focusX = detector.focusX
                    val deltaScale = scaleFactor / oldScale
                    scrollOffset = (scrollOffset + focusX - pLeft) * deltaScale - (focusX - pLeft)
                }
                
                invalidate()
                return true
            }
        }) }

    private val gestureDetector by lazy { GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (!scaleDetector.isInProgress) {
                    scrollOffset += distanceX
                    invalidate()
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                checkPointClick(e.x, e.y)
                return true
            }
        }) }

    private var exportStartTime: Long? = null
    private var exportEndTime: Long? = null

    private var customStartTime: Long? = null
    private var customEndTime: Long? = null

    private data class ChartPoint(
        val x: Float, 
        val y: Float, 
        val color: Int, 
        val entry: MoodActivity.MoodEntry? = null,
        val groupedEntries: List<MoodActivity.MoodEntry>? = null
    )
    private val chartPoints = mutableListOf<ChartPoint>()

    private fun calculatePaddingLeft(): Float = if (exportStartTime != null) 60f else 140f

    init {
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            handled = gestureDetector.onTouchEvent(event) || handled
        }
        
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (scaleFactor > 1.0f) parent?.requestDisallowInterceptTouchEvent(true)
        }
        
        if (event.action == MotionEvent.ACTION_MOVE && scaleFactor > 1.0f) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        
        return handled || super.onTouchEvent(event)
    }

    fun setData(entries: List<MoodActivity.MoodEntry>, mode: Mode = Mode.TIMELINE) {
        this.entries = entries.sortedBy { it.timestamp }
        this.chartMode = mode
        this.isInitialZoomSet = false
        invalidate()
    }

    fun setRange(start: Long?, end: Long?) {
        customStartTime = start
        customEndTime = end
        invalidate()
    }

    fun setExportRange(start: Long, end: Long) {
        exportStartTime = start
        exportEndTime = end
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = calculatePaddingLeft()
        val paddingRight = if (exportStartTime != null) 30f else 40f
        val paddingTop = 60f
        val paddingBottom = if (chartMode == Mode.MONTH_VIEW) 250f else 80f
        
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom
        
        if (chartWidth <= 0 || chartHeight <= 0) return

        if (!isInitialZoomSet && entries.isNotEmpty()) {
            applyInitialZoom(chartWidth)
            isInitialZoomSet = true
        }

        val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
        val moodLabels = moodKeys.map { key ->
            val resId = context.resources.getIdentifier(key, "string", context.packageName)
            if (resId != 0) context.getString(resId) else key
        }

        val textColor = ColorHelper.getTextColor(context)
        labelPaint.color = if (exportStartTime != null) Color.BLACK else textColor
        labelPaint.textSize = if (exportStartTime != null) 14f else 24f
        labelPaint.textAlign = Paint.Align.RIGHT
        labelPaint.typeface = Typeface.DEFAULT

        paint.color = if (exportStartTime != null) Color.LTGRAY else (textColor and 0x33FFFFFF) or 0x33000000
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        paint.shader = null
        
        for (i in 0..4) {
            val yPos = paddingTop + chartHeight - (i / 4f * chartHeight)
            canvas.drawLine(paddingLeft, yPos, width - paddingRight, yPos, paint)
            canvas.drawText((i + 1).toString(), paddingLeft - 10f, yPos + 5f, labelPaint)
        }

        if (entries.isEmpty() && chartMode != Mode.TODAY_VIEW) return

        canvas.save()
        canvas.clipRect(paddingLeft, 0f, width - paddingRight, height.toFloat())

        chartPoints.clear()

        calculatePoints(chartWidth, chartHeight, paddingLeft, paddingTop, moodKeys, moodLabels)

        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        if (chartPoints.size > 1) {
            val colors = chartPoints.map { it.color }.toIntArray()
            val xRange = chartPoints.last().x - chartPoints.first().x
            
            val positions = if (xRange > 0) {
                chartPoints.map { 
                    ((it.x - chartPoints.first().x) / xRange).coerceIn(0f, 1f)
                }.toFloatArray()
            } else {
                FloatArray(chartPoints.size) { i -> i.toFloat() / (chartPoints.size - 1) }
            }
            
            if (positions.size > 1 && xRange > 0) {
                for (i in 1 until positions.size) {
                    if (positions[i] <= positions[i-1]) {
                        positions[i] = positions[i-1] + 0.00001f
                    }
                }
                if (positions.last() > 1f) {
                    val max = positions.last()
                    for (i in positions.indices) positions[i] /= max
                }
                paint.shader = LinearGradient(chartPoints.first().x, 0f, chartPoints.last().x, 0f, colors, positions, Shader.TileMode.CLAMP)
            } else {
                paint.shader = null
                paint.color = chartPoints.first().color
            }
        } else {
            paint.shader = null
            paint.color = ColorHelper.getBtnColor(context)
        }

        path.reset()
        if (chartPoints.isNotEmpty()) {
            path.moveTo(chartPoints[0].x, chartPoints[0].y)
            for (i in 1 until chartPoints.size) {
                val p0 = chartPoints[i - 1]
                val p1 = chartPoints[i]
                val controlX = (p0.x + p1.x) / 2
                path.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
            }
        }
        canvas.drawPath(path, paint)
        
        paint.shader = null
        chartPoints.forEach { p ->
            paint.style = Paint.Style.FILL
            paint.color = p.color
            canvas.drawCircle(p.x, p.y, 10f, paint)
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawCircle(p.x, p.y, 10f, paint)
        }
        
        canvas.restore()
        drawLabels(canvas, chartWidth, paddingLeft, height.toFloat() - 10f)
    }

    private var targetDateTimestamp: Long = System.currentTimeMillis()

    fun setTargetDate(timestamp: Long) {
        this.targetDateTimestamp = timestamp
        invalidate()
    }

    private fun applyInitialZoom(chartWidth: Float) {
        if (exportStartTime != null) return

        when (chartMode) {
            Mode.TIMELINE, Mode.SEVEN_DAY_AVERAGE, Mode.MONTH_VIEW -> {
                val cal = Calendar.getInstance()
                val today = cal.clone() as Calendar
                today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0)
                
                val rangeEnd = customEndTime ?: today.timeInMillis
                val rangeStart = customStartTime ?: (if (entries.isNotEmpty()) entries.first().timestamp else today.timeInMillis - 7 * 24 * 60 * 60 * 1000)
                
                val totalDays = ((rangeEnd - rangeStart) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(1) + 1
                val msPer30Days = 30L * 24 * 60 * 60 * 1000
                
                if (chartMode == Mode.TIMELINE && entries.size >= 2) {
                    val range = (entries.last().timestamp - entries.first().timestamp).coerceAtLeast(1L)
                    scaleFactor = (range.toFloat() / msPer30Days.toFloat()).coerceAtLeast(1.0f)
                } else {
                    scaleFactor = (totalDays / 30f).coerceAtLeast(1.0f)
                }
                scrollOffset = (chartWidth * scaleFactor) - chartWidth
            }
            Mode.TODAY_VIEW -> {
                scaleFactor = 1.0f
                scrollOffset = 0f
            }
            else -> {
                scaleFactor = 1.0f
                scrollOffset = 0f
            }
        }
    }

    private fun calculatePoints(
        chartWidth: Float,
        chartHeight: Float,
        paddingLeft: Float,
        paddingTop: Float,
        moodKeys: List<String>,
        moodLabels: List<String>
    ) {
        val effectiveTodayStart: Long
        val effectiveScale: Float
        val effectiveOffset: Float

        val calendar = Calendar.getInstance().apply {
            timeInMillis = targetDateTimestamp
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        
        if (exportStartTime != null && exportEndTime != null) {
            effectiveTodayStart = exportStartTime!!
            val rangeMs = (exportEndTime!! - exportStartTime!!).coerceAtLeast(1L)
            val msPerDay = 24 * 60 * 60 * 1000L
            effectiveScale = (msPerDay.toFloat() / rangeMs.toFloat()).coerceAtLeast(0.0001f)
            effectiveOffset = 0f
        } else {
            effectiveTodayStart = calendar.timeInMillis
            effectiveScale = scaleFactor
            effectiveOffset = scrollOffset
        }

        val totalContentWidth = chartWidth * effectiveScale

        when (chartMode) {
            Mode.TIMELINE -> {
                if (entries.isEmpty()) return
                
                val startTime: Long
                val range: Long
                
                if (exportStartTime != null && exportEndTime != null) {
                    startTime = exportStartTime!!
                    range = (exportEndTime!! - exportStartTime!!).coerceAtLeast(1L)
                } else {
                    startTime = entries.first().timestamp
                    range = (entries.last().timestamp - startTime).coerceAtLeast(1L)
                }

                entries.forEach { entry ->
                    val rawX = ((entry.timestamp - startTime).toFloat() / range) * chartWidth
                    val x = paddingLeft + (rawX * effectiveScale) - effectiveOffset
                    val moodIndex = getMoodIndex(entry.moodLabel, moodKeys, moodLabels)
                    val y = paddingTop + chartHeight - (moodIndex / 4f * chartHeight)
                    chartPoints.add(ChartPoint(x, y, getMoodColor(moodIndex.toFloat()), entry))
                }
            }
            Mode.TODAY_VIEW -> {
                val range = 24L * 60 * 60 * 1000
                val lastBefore = entries.lastOrNull { it.timestamp < effectiveTodayStart }
                if (lastBefore != null) {
                    val moodIndex = getMoodIndex(lastBefore.moodLabel, moodKeys, moodLabels)
                    val x = paddingLeft - effectiveOffset
                    val y = paddingTop + chartHeight - (moodIndex / 4f * chartHeight)
                    chartPoints.add(ChartPoint(x, y, getMoodColor(moodIndex.toFloat()), lastBefore))
                }
                entries.filter { it.timestamp in effectiveTodayStart..(effectiveTodayStart + range) }.forEach { entry ->
                    val rawX = ((entry.timestamp - effectiveTodayStart).toFloat() / range) * chartWidth
                    val x = paddingLeft + (rawX * effectiveScale) - effectiveOffset
                    val moodIndex = getMoodIndex(entry.moodLabel, moodKeys, moodLabels)
                    val y = paddingTop + chartHeight - (moodIndex / 4f * chartHeight)
                    chartPoints.add(ChartPoint(x, y, getMoodColor(moodIndex.toFloat()), entry))
                }
            }
            Mode.HOUR_OF_DAY -> {
                val hourData = mutableMapOf<Int, MutableList<MoodActivity.MoodEntry>>()
                val cal = Calendar.getInstance()
                entries.forEach { entry ->
                    cal.timeInMillis = entry.timestamp
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    hourData.getOrPut(hour) { mutableListOf() }.add(entry)
                }
                for (hour in 0..23) {
                    val hourEntries = hourData[hour]
                    if (hourEntries != null) {
                        val rawX = (hour / 23f) * chartWidth
                        val x = paddingLeft + (rawX * effectiveScale) - effectiveOffset
                        val avgScore = hourEntries.map { getMoodIndex(it.moodLabel, moodKeys, moodLabels) }.average().toFloat()
                        val y = paddingTop + chartHeight - (avgScore / 4f * chartHeight)
                        chartPoints.add(ChartPoint(x, y, getMoodColor(avgScore), groupedEntries = hourEntries))
                    }
                }
            }
            Mode.SEVEN_DAY_AVERAGE, Mode.MONTH_VIEW -> {
                if (entries.isEmpty() && customStartTime == null) return
                
                val cal = Calendar.getInstance()
                val today = cal.clone() as Calendar
                today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0)
                
                val rangeEnd = customEndTime ?: today.timeInMillis
                val rangeStart = customStartTime ?: (if (entries.isNotEmpty()) entries.first().timestamp else today.timeInMillis - 7 * 24 * 60 * 60 * 1000)
                
                val totalDays = ((rangeEnd - rangeStart) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(1) + 1
                
                val dailyData = mutableMapOf<Int, MutableList<MoodActivity.MoodEntry>>()
                entries.forEach { entry ->
                    cal.timeInMillis = entry.timestamp
                    val entryDay = cal.clone() as Calendar
                    entryDay.set(Calendar.HOUR_OF_DAY, 0); entryDay.set(Calendar.MINUTE, 0); entryDay.set(Calendar.SECOND, 0); entryDay.set(Calendar.MILLISECOND, 0)
                    val diff = ((entryDay.timeInMillis - rangeStart) / (24L * 60 * 60 * 1000)).toInt()
                    if (diff in 0 until totalDays) {
                        dailyData.getOrPut(diff) { mutableListOf() }.add(entry)
                    }
                }
                for (i in 0 until totalDays) {
                    val dayEntries = dailyData[i]
                    if (dayEntries != null) {
                        val rawX = (i / (totalDays - 1).toFloat()) * chartWidth
                        val x = paddingLeft + (rawX * effectiveScale) - effectiveOffset
                        val avgScore = dayEntries.map { getMoodIndex(it.moodLabel, moodKeys, moodLabels) }.average().toFloat()
                        val y = paddingTop + chartHeight - (avgScore / 4f * chartHeight)
                        chartPoints.add(ChartPoint(x, y, getMoodColor(avgScore), groupedEntries = dayEntries))
                    }
                }
            }
        }
        
        if (exportStartTime == null) {
            val maxScroll = (totalContentWidth - chartWidth).coerceAtLeast(0f)
            scrollOffset = effectiveOffset.coerceIn(0f, maxScroll)
        }
    }

    private fun drawLabels(canvas: Canvas, chartWidth: Float, paddingLeft: Float, y: Float) {
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.textSize = 18f
        
        when (chartMode) {
            Mode.TODAY_VIEW, Mode.HOUR_OF_DAY -> {
                val cal = Calendar.getInstance()
                cal.timeInMillis = targetDateTimestamp
                val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                val dateStr = sdf.format(cal.time)

                canvas.drawText("0h ($dateStr)", paddingLeft, y, labelPaint)
                canvas.drawText("12h", paddingLeft + (chartWidth / 2f) * scaleFactor - scrollOffset, y, labelPaint)
                canvas.drawText("24h", paddingLeft + chartWidth * scaleFactor - scrollOffset, y, labelPaint)
            }
            Mode.SEVEN_DAY_AVERAGE, Mode.MONTH_VIEW -> {
                if (entries.isEmpty() && customStartTime == null) return
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                
                val rangeEnd = customEndTime ?: today.timeInMillis
                val rangeStart = customStartTime ?: (if (entries.isNotEmpty()) entries.first().timestamp else today.timeInMillis - 7 * 24 * 60 * 60 * 1000)
                val totalDays = ((rangeEnd - rangeStart) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(1) + 1

                val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                
                val step = when {
                    scaleFactor > 20f -> 1
                    scaleFactor > 10f -> 2
                    scaleFactor > 5f -> 5
                    else -> 10
                }
                
                for (i in 0 until totalDays step step) {
                    val rawX = (i / (totalDays - 1).toFloat()) * chartWidth
                    val x = paddingLeft + (rawX * scaleFactor) - scrollOffset
                    if (x in paddingLeft..(paddingLeft + chartWidth)) {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = rangeStart
                        cal.add(Calendar.DAY_OF_YEAR, i)
                        
                        if (chartMode == Mode.MONTH_VIEW) {
                            canvas.save()
                            canvas.rotate(-90f, x, y - 100f)
                            labelPaint.textAlign = Paint.Align.RIGHT
                            canvas.drawText(sdf.format(cal.time), x, y - 100f, labelPaint)
                            canvas.restore()
                        } else {
                            labelPaint.textAlign = Paint.Align.CENTER
                            canvas.drawText(sdf.format(cal.time), x, y, labelPaint)
                        }
                    }
                }
                val todayX = paddingLeft + chartWidth * scaleFactor - scrollOffset
                if (todayX in paddingLeft..(paddingLeft + chartWidth)) {
                    val cal = Calendar.getInstance().apply { timeInMillis = rangeEnd }
                    val dateStr = sdf.format(cal.time)
                    
                    if (chartMode == Mode.MONTH_VIEW) {
                        canvas.save()
                        canvas.rotate(-90f, todayX, y - 100f)
                        labelPaint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(dateStr, todayX, y - 100f, labelPaint)
                        canvas.restore()
                    } else {
                        labelPaint.textAlign = Paint.Align.CENTER
                        canvas.drawText(dateStr, todayX, y, labelPaint)
                    }
                }
            }
            else -> {}
        }
    }

    private fun checkPointClick(touchX: Float, touchY: Float) {
        val radius = 40f
        val clickedPoint = chartPoints.find { 
            Math.hypot((it.x - touchX).toDouble(), (it.y - touchY).toDouble()) < radius 
        }
        if (clickedPoint != null) {
            showPointInfo(clickedPoint)
        }
    }

    private fun showPointInfo(point: ChartPoint) {
        val activity = context as? Activity ?: return
        val people = loadPeople()
        
        val message = StringBuilder()
        val entriesToShow = point.groupedEntries ?: listOfNotNull(point.entry)
        
        if (entriesToShow.size > 1) {
            message.append(context.getString(R.string.stats_average_of_n, entriesToShow.size)).append("\n\n")
        }

        entriesToShow.forEachIndexed { index, entry ->
            if (entriesToShow.size > 1) message.append(context.getString(R.string.stats_measurement_n, index + 1)).append("\n")
            
            message.append(context.getString(R.string.stats_time, SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(entry.timestamp)))).append("\n")
            
            val moodKeys = listOf("mood_awful", "mood_bad", "mood_meh", "mood_good", "mood_rad")
            val moodLabels = moodKeys.map { key ->
                val resId = context.resources.getIdentifier(key, "string", context.packageName)
                if (resId != 0) context.getString(resId) else key
            }
            val moodIndex = moodKeys.indexOf(entry.moodLabel).let { if (it == -1) moodLabels.indexOf(entry.moodLabel) else it }
            val moodScore = if (moodIndex != -1) (moodIndex + 1).toString() else entry.moodLabel
            message.append(context.getString(R.string.stats_mood_score, moodScore)).append("\n")
            
            if (entry.activities.isNotEmpty()) {
                message.append(context.getString(R.string.stats_activities, entry.activities.joinToString(", "))).append("\n")
            }
            if (entry.memberIds.isNotEmpty()) {
                val names = entry.memberIds.mapNotNull { id -> people.find { it.id == id && !it.isArchived }?.name }
                if (names.isNotEmpty()) {
                    message.append(context.getString(R.string.stats_members, names.joinToString(", "))).append("\n")
                }
            }
            if (!entry.note.isNullOrBlank()) {
                message.append(context.getString(R.string.stats_note, entry.note)).append("\n")
            }
            message.append("\n")
        }

        AlertDialog.Builder(activity)
            .setTitle(context.getString(R.string.stats_details))
            .setMessage(message.toString().trim())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadPeople(): List<Person> {
        val prefs = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
        val json = prefs.getString("people_list", "[]") ?: "[]"
        val type = object : TypeToken<List<Person>>() {}.type
        return try { Gson().fromJson(json, type) } catch (_: Exception) { emptyList() }
    }

    private fun getMoodIndex(label: String, keys: List<String>, labels: List<String>): Int {
        var idx = keys.indexOf(label)
        if (idx == -1) idx = labels.indexOf(label)
        return idx.coerceIn(0, 4)
    }

    private fun getMoodColor(score: Float): Int {
        return ColorHelper.getMoodColorByScore(context, score)
    }
}
