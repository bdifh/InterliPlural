package com.interli.plural.features.timeline

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.interli.plural.core.ColorHelper
import com.interli.plural.FrontSession
import com.interli.plural.Person
import java.text.SimpleDateFormat
import java.util.*

class TimelineChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var sessions: List<FrontSession> = listOf()
    private var people: List<Person> = listOf()
    private var uniqueMembers: List<Person> = listOf()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 16f
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val leftColumnWidth = 175f
    private val headerHeight = 120f
    private val rowHeight = 86f
    private val imageSize = 44f
    private val imageMargin = 12f
    private val barCornerRadius = 15f
    private val barHeight = 56f
    private var offsetX = 0f
    private var scaleFactor = 1.0f
    private val minScale = 0.5f
    private val maxScale = 48.0f
    private var isInteractive = true
    private var exportStartTime: Long? = null
    private var exportEndTime: Long? = null
    private var isExportMode = false
    private val memberBitmaps = mutableMapOf<String, Bitmap>()
    private val sdfDay = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
    private val sdfHour = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val gestureDetector by lazy { GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX -= distanceX
            invalidate()
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }
    }) }
    private val scaleDetector by lazy { ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScale = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(minScale, maxScale)
            val focusX = detector.focusX
            if (focusX > leftColumnWidth) {
                val relX = focusX - leftColumnWidth - offsetX
                offsetX -= relX * (scaleFactor / oldScale - 1)
            }
            invalidate()
            return true
        }
    }) }
    fun setData(sessions: List<FrontSession>, people: List<Person>) {
        this.sessions = sessions
        this.people = people
        val sessionsByPerson = sessions.groupBy { it.personId ?: it.personName }
        val idsInSessions = sessions.mapNotNull { it.personId }.toSet()
        val namesInSessions = sessions.map { it.personName }.toSet()
        val activeMembers = mutableListOf<Person>()
        people.forEach { p ->
            if (idsInSessions.contains(p.id) || namesInSessions.contains(p.name)) {
                activeMembers.add(p)
            }
        }
        sessionsByPerson.forEach { (key, sList) ->
            val found = activeMembers.any { it.id == key || it.name == key }
            if (!found) {
                activeMembers.add(Person(id = key, name = sList.first().personName, profileColor = Color.GRAY))
            }
        }
        val lastActiveMap = sessionsByPerson.mapValues { (_, sList) -> sList.maxOfOrNull { it.startTime } ?: 0L }
        this.uniqueMembers = activeMembers.sortedByDescending { lastActiveMap[it.id] ?: lastActiveMap[it.name] ?: 0L }
        loadBitmaps()
        invalidate()
    }
    private fun loadBitmaps() {
        uniqueMembers.forEach { person ->
            if (person.profilePictureUri != null && !memberBitmaps.containsKey(person.id)) {
                val request = ImageRequest.Builder(context)
                    .data(person.profilePictureUri)
                    .target { result ->
                        val bitmap = try { result.toBitmap() } catch (_: Exception) { null }
                        if (bitmap != null) {
                            memberBitmaps[person.id] = bitmap
                            invalidate()
                        }
                    }
                    .build()
                context.imageLoader.enqueue(request)
            }
        }
    }
    fun updateTextColor() {
        val color = ColorHelper.getTextColor(context)
        textPaint.color = color
        headerPaint.color = color
        linePaint.color = color and 0x33ffffff or 0x33000000
        invalidate()
    }
    fun setExportRange(start: Long, end: Long) {
        exportStartTime = start
        exportEndTime = end
        isInteractive = false
        isExportMode = true
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (uniqueMembers.isEmpty()) return
        val currentRowHeight = if (isExportMode) 24f else rowHeight
        val currentBarHeight = if (isExportMode) 14f else barHeight
        val currentHeaderHeight = if (isExportMode) 180f else headerHeight
        val currentTextSize = if (isExportMode) 8f else 16f
        val currentHeaderTextSize = if (isExportMode) 9f else 20f
        textPaint.textSize = currentTextSize
        headerPaint.textSize = currentHeaderTextSize
        val chartWidth = width - leftColumnWidth
        val msPerDay = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val effectiveTodayStart: Long
        val effectiveScale: Float
        val effectiveOffset: Float
        if (exportStartTime != null && exportEndTime != null) {
            effectiveTodayStart = exportStartTime!!
            val rangeMs = (exportEndTime!! - exportStartTime!!).coerceAtLeast(1L)
            effectiveScale = (msPerDay.toFloat() / rangeMs.toFloat()).coerceAtLeast(0.0001f)
            effectiveOffset = 0f
        } else {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            effectiveTodayStart = calendar.timeInMillis
            effectiveScale = scaleFactor
            effectiveOffset = offsetX
        }
        val dayWidth = chartWidth * effectiveScale
        canvas.save()
        canvas.clipRect(leftColumnWidth, 0f, width.toFloat(), height.toFloat())
        canvas.translate(leftColumnWidth + effectiveOffset, 0f)
        val firstDayOffset: Int
        val lastDayOffset: Int
        if (exportStartTime != null && exportEndTime != null) {
            firstDayOffset = 0
            lastDayOffset = ((exportEndTime!! - exportStartTime!!) / msPerDay).toInt() + 1
        } else {
            firstDayOffset = (-effectiveOffset / dayWidth).toInt() - 1
            lastDayOffset = ((-effectiveOffset + chartWidth) / dayWidth).toInt() + 1
        }
        for (d in firstDayOffset..lastDayOffset) {
            val dayStart = effectiveTodayStart + (d * msPerDay)
            val dayX = d * dayWidth
            val minutesStep = when {
                effectiveScale >= 36f -> 1
                effectiveScale >= 24f -> 2
                effectiveScale >= 16f -> 5
                effectiveScale >= 10f -> 15
                effectiveScale >= 6f -> 30
                else -> 60
            }
            val stepsPerDay = 24 * 60 / minutesStep
            for (i in 0 until stepsPerDay) {
                val x = dayX + (i.toFloat() / stepsPerDay) * dayWidth
                canvas.drawLine(x, currentHeaderHeight, x, height.toFloat(), linePaint)
            }
            uniqueMembers.forEachIndexed { rowIndex, person ->
                val rowTop = currentHeaderHeight + rowIndex * currentRowHeight
                val personSessions = sessions.filter {
                    val matches = if (it.personId != null) it.personId == person.id else it.personName == person.name
                    matches &&
                            it.startTime < dayStart + msPerDay &&
                            (it.endTime ?: now) > dayStart
                }
                personSessions.forEach { session ->
                    val s = Math.max(session.startTime, dayStart)
                    val e = Math.min(session.endTime ?: now, dayStart + msPerDay)
                    val left = dayX + ((s - dayStart).toFloat() / msPerDay) * dayWidth
                    val right = dayX + ((e - dayStart).toFloat() / msPerDay) * dayWidth
                    paint.color = person.profileColor
                    val top = rowTop + (currentRowHeight - currentBarHeight) / 2f
                    val bottom = top + currentBarHeight
                    canvas.drawRoundRect(left, top, right, bottom, if(isExportMode) 2f else barCornerRadius, if(isExportMode) 2f else barCornerRadius, paint)
                    if (!session.note.isNullOrBlank()) {
                        val indicatorRadius = if (isExportMode) 2f else 5f
                        val centerX = (left + right) / 2f
                        val centerY = (top + bottom) / 2f
                        if (right - left > indicatorRadius * 2.5f) {
                            paint.color = Color.WHITE
                            paint.alpha = 180
                            canvas.drawCircle(centerX, centerY, indicatorRadius, paint)
                            paint.alpha = 255
                        }
                    }
                }
            }
        }
        canvas.restore()
        paint.color = if (isExportMode) Color.WHITE else ColorHelper.getBgColor(context)
        canvas.drawRect(0f, 0f, leftColumnWidth, height.toFloat(), paint)
        canvas.drawLine(leftColumnWidth, 0f, leftColumnWidth, height.toFloat(), linePaint)
        canvas.save()
        canvas.clipRect(0f, 0f, leftColumnWidth, height.toFloat())
        uniqueMembers.forEachIndexed { rowIndex, person ->
            val rowTop = currentHeaderHeight + rowIndex * currentRowHeight
            val centerY = rowTop + currentRowHeight / 2
            if (!isExportMode) {
                val bitmap = memberBitmaps[person.id]
                if (bitmap != null) {
                    val src = Rect(0, 0, bitmap.width, bitmap.height)
                    val dst = RectF(imageMargin, centerY - imageSize / 2, imageMargin + imageSize, centerY + imageSize / 2)
                    canvas.save()
                    val path = Path().apply { addCircle(dst.centerX(), dst.centerY(), imageSize / 2, Path.Direction.CW) }
                    canvas.clipPath(path)
                    canvas.drawBitmap(bitmap, src, dst, paint)
                    canvas.restore()
                } else {
                    paint.color = person.profileColor
                    canvas.drawCircle(imageMargin + imageSize / 2, centerY, imageSize / 2, paint)
                }
            }
            textPaint.textAlign = Paint.Align.LEFT
            val startX = if (isExportMode) 8f else imageMargin + imageSize + 12f
            val maxWidth = leftColumnWidth - startX - 8f
            val ellipsizedName = android.text.TextUtils.ellipsize(
                person.name,
                android.text.TextPaint(textPaint),
                maxWidth,
                android.text.TextUtils.TruncateAt.END
            ).toString()
            canvas.drawText(ellipsizedName, startX, centerY + (textPaint.textSize * 0.35f), textPaint)
            canvas.drawLine(0f, rowTop + currentRowHeight, leftColumnWidth, rowTop + currentRowHeight, linePaint)
        }
        canvas.restore()
        paint.color = if (isExportMode) Color.WHITE else ColorHelper.getBgColor(context)
        canvas.drawRect(0f, 0f, width.toFloat(), currentHeaderHeight, paint)
        canvas.drawLine(0f, currentHeaderHeight, width.toFloat(), currentHeaderHeight, linePaint)
        canvas.save()
        canvas.clipRect(leftColumnWidth, 0f, width.toFloat(), currentHeaderHeight)
        canvas.translate(leftColumnWidth + effectiveOffset, 0f)
        for (d in firstDayOffset..lastDayOffset) {
            val dayStart = effectiveTodayStart + (d * msPerDay)
            val dayX = d * dayWidth
            headerPaint.textAlign = Paint.Align.CENTER
            if (isExportMode) {
                canvas.save()
                canvas.rotate(-90f, dayX + dayWidth / 2, 60f)
                canvas.drawText(sdfDay.format(Date(dayStart)), dayX + dayWidth / 2, 60f, headerPaint)
                canvas.restore()
            } else {
                canvas.drawText(sdfDay.format(Date(dayStart)), dayX + dayWidth / 2, 60f, headerPaint)
            }
            val minutesStep = when {
                effectiveScale >= 36f -> 5
                effectiveScale >= 24f -> 10
                effectiveScale >= 16f -> 15
                effectiveScale >= 10f -> 30
                effectiveScale >= 6f -> 60
                else -> if (isExportMode) 120 else 360
            }
            val labelSteps = 24 * 60 / minutesStep
            val cal = Calendar.getInstance()
            for (i in 0..labelSteps) {
                val minutes = i * minutesStep
                val x = dayX + (minutes.toFloat() / (24f * 60f)) * dayWidth
                cal.timeInMillis = dayStart + minutes * 60_000L
                textPaint.textAlign = Paint.Align.CENTER
                val label = if (minutesStep >= 60) "${cal.get(Calendar.HOUR_OF_DAY)}h" else sdfHour.format(Date(cal.timeInMillis))
                if (isExportMode) {
                    canvas.save()
                    canvas.rotate(-90f, x, currentHeaderHeight - 15f)
                    canvas.drawText(label, x, currentHeaderHeight - 15f, textPaint)
                    canvas.restore()
                } else {
                    canvas.drawText(label, x, 105f, textPaint)
                }
            }
        }
        canvas.restore()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive) return false
        var handled = scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            handled = gestureDetector.onTouchEvent(event) || handled
        }
        return handled || super.onTouchEvent(event)
    }
    fun setInteractive(enabled: Boolean) {
        isInteractive = enabled
    }
    fun uniqueMembersSize() = uniqueMembers.size
    fun resetViewport() {
        offsetX = 0f
        scaleFactor = 1.0f
        invalidate()
    }
    var onSessionClicked: ((FrontSession) -> Unit)? = null
    private fun handleTap(x: Float, y: Float) {
        if (x < leftColumnWidth || y < headerHeight) return
        val now = System.currentTimeMillis()
        val chartWidth = width - leftColumnWidth
        val dayWidth = chartWidth * scaleFactor
        val msPerDay = 24 * 60 * 60 * 1000L
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = calendar.timeInMillis
        val timeAtX = ( (x - leftColumnWidth - offsetX) / dayWidth ) * msPerDay + todayStart
        val rowIndex = ((y - headerHeight) / rowHeight).toInt()
        val person = uniqueMembers.getOrNull(rowIndex) ?: return
        val hit = sessions.find {
            val matches = if (it.personId != null) it.personId == person.id else it.personName == person.name
            matches &&
                    it.startTime <= timeAtX && (it.endTime ?: now) >= timeAtX
        }
        if (hit != null) {
            onSessionClicked?.invoke(hit)
        }
    }
}
