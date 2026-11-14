package com.interli.plural.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.interli.plural.DiaryNote
import com.interli.plural.features.member.MemberHelper
import com.interli.plural.features.mood.MoodActivity
import com.interli.plural.features.mood.MoodChartView
import com.interli.plural.features.relations.NodeType
import com.interli.plural.features.relations.RelationEnvironment
import com.interli.plural.features.relations.RelationsData
import com.interli.plural.features.relations.RelationsMapView
import com.interli.plural.features.timeline.TimelineChartView
import com.interli.plural.FrontSession
import com.interli.plural.features.health.HealthLogEntry
import com.interli.plural.features.health.MedicationItem
import com.interli.plural.features.timeline.FrontHistoryPdfChartView
import com.interli.plural.features.mood.MoodPdfWeeklyChartView
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.TodoList
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.text.append
import kotlin.text.isNotEmpty
import kotlin.text.isNullOrEmpty

object PdfExportHelper {
    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 50f
    fun exportNoteToPdf(context: Context, uri: Uri, title: String, content: String) {
        Thread {
            val doc = PdfDocument()
            try {
                var pageCount = 1
                var pageInfo =
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
                var page = doc.startPage(pageInfo)
                var canvas = page.canvas
                var currentY = MARGIN
                val titlePaint = TextPaint().apply {
                    textSize = 18f
                    isFakeBoldText = true
                    color = android.graphics.Color.BLACK
                }
                val textPaint = TextPaint().apply {
                    textSize = 12f
                    color = android.graphics.Color.BLACK
                }
                val titleLayout =
                    createStaticLayout(title, titlePaint, PAGE_WIDTH - 2 * MARGIN.toInt())
                titleLayout.draw(canvas, MARGIN, currentY)
                currentY += titleLayout.height + 20f
                val contentLayout =
                    createStaticLayout(content, textPaint, PAGE_WIDTH - 2 * MARGIN.toInt())
                var currentLine = 0
                while (currentLine < contentLayout.lineCount) {
                    val remainingHeight = PAGE_HEIGHT - MARGIN - currentY
                    var linesThatFit = 0
                    for (i in currentLine until contentLayout.lineCount) {
                        val lineBottom = contentLayout.getLineBottom(i)
                        val lineTop = contentLayout.getLineTop(currentLine)
                        if (lineBottom - lineTop <= remainingHeight) {
                            linesThatFit++
                        } else {
                            break
                        }
                    }
                    if (linesThatFit == 0) {
                        doc.finishPage(page)
                        pageCount++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount)
                            .create()
                        page = doc.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = MARGIN
                        continue
                    }
                    val startLineTop = contentLayout.getLineTop(currentLine)
                    val endLineBottom = contentLayout.getLineBottom(currentLine + linesThatFit - 1)
                    val heightToDraw = endLineBottom - startLineTop
                    drawClippedLayout(
                        contentLayout,
                        canvas,
                        MARGIN,
                        currentY,
                        startLineTop,
                        endLineBottom
                    )
                    currentY += heightToDraw
                    currentLine += linesThatFit
                    if (currentLine < contentLayout.lineCount) {
                        doc.finishPage(page)
                        pageCount++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount)
                            .create()
                        page = doc.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = MARGIN
                    }
                }
                doc.finishPage(page)
                context.contentResolver.openOutputStream(uri)?.use { os -> doc.writeTo(os) }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                doc.close()
            }
        }.start()
    }

    fun exportFullDataToPdf(
        context: Context,
        uri: Uri,
        selections: BooleanArray,
        startDate: Long? = null,
        endDate: Long? = null
    ) {
        Thread {
            val doc = PdfDocument()
            try {
                var pageCount = 1
                var pageInfo =
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
                var page = doc.startPage(pageInfo)
                var canvas = page.canvas
                var currentY = MARGIN
                val titlePaint = TextPaint().apply {
                    textSize = 22f; isFakeBoldText = true; color = android.graphics.Color.BLACK
                }
                val headerPaint = TextPaint().apply {
                    textSize = 16f; isFakeBoldText = true; color = android.graphics.Color.BLACK
                }
                val textPaint =
                    TextPaint().apply { textSize = 11f; color = android.graphics.Color.BLACK }
                val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

                fun startNewPage() {
                    doc.finishPage(page)
                    pageCount++
                    pageInfo =
                        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = MARGIN
                }

                fun startNewLandscapePage() {
                    doc.finishPage(page)
                    pageCount++

                    pageInfo = PdfDocument.PageInfo.Builder(
                        PAGE_HEIGHT,
                        PAGE_WIDTH,
                        pageCount
                    ).create()

                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = MARGIN
                }

                fun drawText(text: String, paint: TextPaint, spacing: Float = 10f) {
                    val layout = createStaticLayout(text, paint, PAGE_WIDTH - 2 * MARGIN.toInt())
                    var currentLine = 0
                    while (currentLine < layout.lineCount) {
                        val remainingHeight = PAGE_HEIGHT - MARGIN - currentY
                        var linesThatFit = 0
                        for (i in currentLine until layout.lineCount) {
                            val lineBottom = layout.getLineBottom(i)
                            val lineTop = layout.getLineTop(currentLine)
                            if (lineBottom - lineTop <= remainingHeight) {
                                linesThatFit++
                            } else {
                                break
                            }
                        }
                        if (linesThatFit == 0) {
                            startNewPage()
                            continue
                        }
                        val startLineTop = layout.getLineTop(currentLine)
                        val endLineBottom = layout.getLineBottom(currentLine + linesThatFit - 1)
                        val heightToDraw = endLineBottom - startLineTop
                        drawClippedLayout(
                            layout,
                            canvas,
                            MARGIN,
                            currentY,
                            startLineTop,
                            endLineBottom
                        )
                        currentY += heightToDraw
                        currentLine += linesThatFit
                        if (currentLine < layout.lineCount) {
                            startNewPage()
                        }
                    }
                    currentY += spacing
                }

                // 1. Headtitles and periods
                drawText("${context.getString(R.string.app_name)} - Data Export", titlePaint, 15f)
                drawText("Export Date: ${sdf.format(Date())}", textPaint, 10f)

                val effectiveStart = startDate ?: 0L
                val effectiveEnd = endDate ?: Long.MAX_VALUE

                if (startDate != null || endDate != null) {
                    val startStr = startDate?.let { sdf.format(Date(it)) }
                        ?: context.getString(R.string.period_all_time)
                    val endStr = endDate?.let { sdf.format(Date(it)) }
                        ?: context.getString(R.string.currently_active)
                    drawText(
                        "${context.getString(R.string.stats_period)} $startStr ${
                            context.getString(
                                R.string.to_date
                            )
                        } $endStr", textPaint, 30f
                    )
                } else {
                    drawText(
                        "${context.getString(R.string.stats_period)} ${context.getString(R.string.period_all_time)}",
                        textPaint,
                        30f
                    )
                }

                val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
                val gson = Gson()
                val people = MemberHelper.loadAllPeople(context)

                // 2. Members
                if (selections[0]) {
                    drawText(context.getString(R.string.front_page), headerPaint, 15f)
                    people.forEach { person ->
                        drawText("- ${person.name} (ID: ${person.id})", textPaint, 5f)
                    }
                    currentY += 20f
                }

                // 3. Fronting History
                if (selections[0]) {
                    val sessionsJson = sharedPref.getString("sessions_list", "[]")
                    val allSessions: List<FrontSession> = gson.fromJson(
                        sessionsJson,
                        object : TypeToken<List<FrontSession>>() {}.type
                    ) ?: emptyList()

                    val now = System.currentTimeMillis()
                    val sessions = allSessions.filter {
                        (it.endTime ?: now) >= effectiveStart && it.startTime <= effectiveEnd
                    }

                    val chartStart = startDate
                        ?: sessions.minOfOrNull { it.startTime }
                        ?: (now - 6L * 24 * 60 * 60 * 1000)
                    val chartEnd = maxOf(
                        chartStart,
                        endDate ?: sessions.maxOfOrNull { it.endTime ?: now } ?: now
                    )

                    val firstDay = Calendar.getInstance().apply {
                        timeInMillis = chartStart
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val lastDay = Calendar.getInstance().apply {
                        timeInMillis = chartEnd
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val dayStarts = mutableListOf<Long>()
                    val dayCursor = firstDay.clone() as Calendar
                    while (dayCursor.timeInMillis <= lastDay.timeInMillis) {
                        dayStarts.add(dayCursor.timeInMillis)
                        dayCursor.add(Calendar.DAY_OF_YEAR, 1)
                    }

                    val memberCount = sessions
                        .map { it.personId ?: "name:${it.personName}" }
                        .distinct()
                        .size
                    val headerHeight = 20f
                    val panelSpacing = 8f
                    val minLaneHeight = 7f
                    val maxLaneHeight = 12f

                    val availableChartHeight =
                        PAGE_HEIGHT - MARGIN - (MARGIN + headerPaint.textSize + 14f)

                    val minimumPanelHeight =
                        headerHeight + memberCount * minLaneHeight + panelSpacing

                    val daysPerPage = (availableChartHeight / minimumPanelHeight)
                        .toInt()
                        .coerceIn(1, 7)

                    val laneHeight = if (memberCount == 0) {
                        maxLaneHeight
                    } else {
                        (
                                (availableChartHeight / daysPerPage - headerHeight - panelSpacing) /
                                        memberCount
                                ).coerceIn(minLaneHeight, maxLaneHeight)
                    }

                    val chartWidth = PAGE_WIDTH - 2 * MARGIN.toInt()

                    dayStarts.chunked(daysPerPage).forEach { daysOnPage ->
                        startNewPage()
                        drawText(context.getString(R.string.timeline_front_title), headerPaint, 8f)

                        val chart = FrontHistoryPdfChartView(
                            context = context,
                            dayStarts = daysOnPage,
                            sessions = sessions,
                            people = people,
                            laneHeight = laneHeight,
                            panelSpacing = panelSpacing
                        )
                        val chartHeight = chart.requiredHeight

                        val bitmap = android.graphics.Bitmap.createBitmap(
                            chartWidth,
                            chartHeight,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val chartCanvas = android.graphics.Canvas(bitmap)
                        chartCanvas.drawColor(android.graphics.Color.WHITE)

                        chart.measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(
                                chartWidth,
                                android.view.View.MeasureSpec.EXACTLY
                            ),
                            android.view.View.MeasureSpec.makeMeasureSpec(
                                chartHeight,
                                android.view.View.MeasureSpec.EXACTLY
                            )
                        )
                        chart.layout(0, 0, chartWidth, chartHeight)
                        chart.draw(chartCanvas)

                        canvas.drawBitmap(bitmap, MARGIN, currentY, null)
                        currentY += chartHeight + 10f
                        bitmap.recycle()
                    }

                    startNewPage()

                    val tableBottom = PAGE_HEIGHT - MARGIN
                    val tableLeft = MARGIN
                    val columnWidths = listOf(70f, 58f, 42f, 58f, 42f, 43f, 55f, 127f)
                    val tableWidth = columnWidths.sum()

                    val tableHeaderPaint = TextPaint().apply {
                        textSize = 7.5f
                        isFakeBoldText = true
                        color = android.graphics.Color.BLACK
                    }
                    val tableBodyPaint = TextPaint().apply {
                        textSize = 7.5f
                        color = android.graphics.Color.BLACK
                    }
                    val tableBorderPaint = android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG
                    ).apply {
                        color = 0xFF888888.toInt()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 0.6f
                    }
                    val tableHeaderBackgroundPaint = android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG
                    ).apply {
                        color = 0xFFE8E8E8.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }

                    val tableHeaders = listOf(
                        context.getString(R.string.pdf_front_table_fronter),
                        context.getString(R.string.pdf_front_table_start_date),
                        context.getString(R.string.pdf_front_table_start_time),
                        context.getString(R.string.pdf_front_table_end_date),
                        context.getString(R.string.pdf_front_table_end_time),
                        context.getString(R.string.pdf_front_table_percentage),
                        context.getString(R.string.pdf_front_table_label),
                        context.getString(R.string.pdf_front_table_note)
                    )

                    fun drawFrontTableHeader() {
                        drawText(context.getString(R.string.timeline_front_title), headerPaint, 8f)

                        val layouts = tableHeaders.mapIndexed { index, text ->
                            createStaticLayout(
                                text,
                                tableHeaderPaint,
                                columnWidths[index].toInt() - 8
                            )
                        }
                        val rowHeight = maxOf(26f, layouts.maxOf { it.height.toFloat() } + 8f)

                        canvas.drawRect(
                            tableLeft,
                            currentY,
                            tableLeft + tableWidth,
                            currentY + rowHeight,
                            tableHeaderBackgroundPaint
                        )

                        var x = tableLeft
                        layouts.forEachIndexed { index, layout ->
                            val width = columnWidths[index]
                            layout.draw(canvas, x + 4f, currentY + (rowHeight - layout.height) / 2f)
                            canvas.drawRect(x, currentY, x + width, currentY + rowHeight, tableBorderPaint)
                            x += width
                        }

                        currentY += rowHeight
                    }

                    fun drawFrontTableRow(cells: List<String>) {
                        val layouts = cells.mapIndexed { index, text ->
                            createStaticLayout(
                                text.ifEmpty { " " },
                                tableBodyPaint,
                                columnWidths[index].toInt() - 8
                            )
                        }

                        val firstLayout = layouts.first()
                        val lineHeight = (
                                firstLayout.getLineBottom(0) - firstLayout.getLineTop(0)
                                ).coerceAtLeast(1).toFloat() * 1.1f

                        val maxLines = layouts.maxOf { it.lineCount }
                        var firstLine = 0

                        while (firstLine < maxLines) {
                            if (currentY + lineHeight + 8f > tableBottom) {
                                startNewPage()
                                drawFrontTableHeader()
                            }

                            val linesThatFit = (
                                    (tableBottom - currentY - 8f) / lineHeight
                                    ).toInt().coerceAtLeast(1)
                            val lastLine = minOf(maxLines, firstLine + linesThatFit)

                            val contentHeight = layouts.maxOf { layout ->
                                val from = minOf(firstLine, layout.lineCount)
                                val until = minOf(lastLine, layout.lineCount)

                                if (until > from) {
                                    layout.getLineBottom(until - 1) - layout.getLineTop(from)
                                } else {
                                    0
                                }
                            }.coerceAtLeast(lineHeight.toInt())

                            val rowHeight = contentHeight + 8f
                            var x = tableLeft

                            layouts.forEachIndexed { index, layout ->
                                val width = columnWidths[index]
                                val from = minOf(firstLine, layout.lineCount)
                                val until = minOf(lastLine, layout.lineCount)

                                canvas.drawRect(x, currentY, x + width, currentY + rowHeight, tableBorderPaint)

                                if (until > from) {
                                    drawClippedLayout(
                                        layout,
                                        canvas,
                                        x + 4f,
                                        currentY + 4f,
                                        layout.getLineTop(from),
                                        layout.getLineBottom(until - 1)
                                    )
                                }

                                x += width
                            }

                            currentY += rowHeight
                            firstLine = lastLine
                        }
                    }

                    drawFrontTableHeader()

                    val tableDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                    val tableTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                    sessions.sortedBy { it.startTime }.forEach { session ->
                        val startDate = tableDateFormat.format(Date(session.startTime))
                        val endDate = session.endTime?.let { endTime ->
                            tableDateFormat.format(Date(endTime))
                                .takeIf { it != startDate }
                                .orEmpty()
                        }.orEmpty()

                        val endTime = session.endTime?.let {
                            tableTimeFormat.format(Date(it))
                        } ?: context.getString(R.string.currently_active)

                        val percentage = session.frontPercentage
                            ?.takeIf { it != 100 }
                            ?.let { "$it%" }
                            .orEmpty()

                        drawFrontTableRow(
                            listOf(
                                session.personName,
                                startDate,
                                tableTimeFormat.format(Date(session.startTime)),
                                endDate,
                                endTime,
                                percentage,
                                session.frontRole.orEmpty(),
                                session.note.orEmpty()
                            )
                        )
                    }
                }

                // 4. Mood Data
                if (selections[1]) {
                    val moodJson = sharedPref.getString("mood_entries", "[]")
                    val allMoods: List<MoodActivity.MoodEntry> = gson.fromJson(
                        moodJson,
                        object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type
                    ) ?: emptyList()

                    val moods = allMoods
                        .filter { it.timestamp in effectiveStart..effectiveEnd }
                        .sortedBy { it.timestamp }

                    val now = System.currentTimeMillis()
                    val chartStart = startDate ?: moods.firstOrNull()?.timestamp
                    ?: (now - 6L * 24 * 60 * 60 * 1000)
                    val chartEnd = maxOf(
                        chartStart,
                        endDate ?: moods.lastOrNull()?.timestamp ?: now
                    )

                    val firstDay = Calendar.getInstance().apply {
                        timeInMillis = chartStart
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val lastDay = Calendar.getInstance().apply {
                        timeInMillis = chartEnd
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val dayStarts = mutableListOf<Long>()
                    val dayCursor = firstDay.clone() as Calendar
                    while (dayCursor.timeInMillis <= lastDay.timeInMillis) {
                        dayStarts.add(dayCursor.timeInMillis)
                        dayCursor.add(Calendar.DAY_OF_YEAR, 1)
                    }

                    val chartWidth = PAGE_WIDTH - 2 * MARGIN.toInt()

                    dayStarts.chunked(7).forEach { daysOnPage ->
                        startNewPage()
                        drawText(context.getString(R.string.mood_tracker), headerPaint, 8f)

                        val chart = MoodPdfWeeklyChartView(
                            context = context,
                            dayStarts = daysOnPage,
                            entries = moods
                        )
                        val chartHeight = chart.requiredHeight

                        val bitmap = android.graphics.Bitmap.createBitmap(
                            chartWidth,
                            chartHeight,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val chartCanvas = android.graphics.Canvas(bitmap)
                        chartCanvas.drawColor(android.graphics.Color.WHITE)

                        chart.measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(
                                chartWidth,
                                android.view.View.MeasureSpec.EXACTLY
                            ),
                            android.view.View.MeasureSpec.makeMeasureSpec(
                                chartHeight,
                                android.view.View.MeasureSpec.EXACTLY
                            )
                        )
                        chart.layout(0, 0, chartWidth, chartHeight)
                        chart.draw(chartCanvas)

                        canvas.drawBitmap(bitmap, MARGIN, currentY, null)
                        currentY += chartHeight + 10f
                        bitmap.recycle()
                    }

                    startNewPage()

                    val tableLeft = MARGIN
                    val tableBottom = PAGE_HEIGHT - MARGIN
                    val columnWidths = listOf(72f, 50f, 42f, 125f, 206f)
                    val tableWidth = columnWidths.sum()

                    val tableHeaderPaint = TextPaint().apply {
                        textSize = 8f
                        isFakeBoldText = true
                        color = android.graphics.Color.BLACK
                    }
                    val tableBodyPaint = TextPaint().apply {
                        textSize = 8f
                        color = android.graphics.Color.BLACK
                    }
                    val tableBorderPaint = android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG
                    ).apply {
                        color = 0xFF888888.toInt()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 0.6f
                    }
                    val tableHeaderBackgroundPaint = android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG
                    ).apply {
                        color = 0xFFE8E8E8.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }

                    val tableHeaders = listOf(
                        context.getString(R.string.pdf_mood_table_date),
                        context.getString(R.string.pdf_mood_table_time),
                        context.getString(R.string.pdf_mood_table_score),
                        context.getString(R.string.pdf_mood_table_members),
                        context.getString(R.string.pdf_mood_table_note)
                    )

                    fun drawMoodTableHeader() {
                        drawText(context.getString(R.string.mood_tracker), headerPaint, 8f)

                        val layouts = tableHeaders.mapIndexed { index, text ->
                            createStaticLayout(
                                text,
                                tableHeaderPaint,
                                columnWidths[index].toInt() - 8
                            )
                        }
                        val rowHeight = maxOf(26f, layouts.maxOf { it.height.toFloat() } + 8f)

                        canvas.drawRect(
                            tableLeft,
                            currentY,
                            tableLeft + tableWidth,
                            currentY + rowHeight,
                            tableHeaderBackgroundPaint
                        )

                        var x = tableLeft
                        layouts.forEachIndexed { index, layout ->
                            val width = columnWidths[index]
                            layout.draw(canvas, x + 4f, currentY + (rowHeight - layout.height) / 2f)
                            canvas.drawRect(
                                x,
                                currentY,
                                x + width,
                                currentY + rowHeight,
                                tableBorderPaint
                            )
                            x += width
                        }
                        currentY += rowHeight
                    }

                    fun drawMoodTableRow(cells: List<String>) {
                        val layouts = cells.mapIndexed { index, text ->
                            createStaticLayout(
                                text.ifEmpty { " " },
                                tableBodyPaint,
                                columnWidths[index].toInt() - 8
                            )
                        }

                        val lineHeight = (
                                layouts.first().getLineBottom(0) -
                                        layouts.first().getLineTop(0)
                                ).coerceAtLeast(1).toFloat() * 1.1f

                        val maxLines = layouts.maxOf { it.lineCount }
                        var firstLine = 0

                        while (firstLine < maxLines) {
                            if (currentY + lineHeight + 8f > tableBottom) {
                                startNewPage()
                                drawMoodTableHeader()
                            }

                            val linesThatFit = (
                                    (tableBottom - currentY - 8f) / lineHeight
                                    ).toInt().coerceAtLeast(1)
                            val lastLine = minOf(maxLines, firstLine + linesThatFit)

                            val contentHeight = layouts.maxOf { layout ->
                                val from = minOf(firstLine, layout.lineCount)
                                val until = minOf(lastLine, layout.lineCount)

                                if (until > from) {
                                    layout.getLineBottom(until - 1) - layout.getLineTop(from)
                                } else {
                                    0
                                }
                            }.coerceAtLeast(lineHeight.toInt())

                            val rowHeight = contentHeight + 8f
                            var x = tableLeft

                            layouts.forEachIndexed { index, layout ->
                                val width = columnWidths[index]
                                val from = minOf(firstLine, layout.lineCount)
                                val until = minOf(lastLine, layout.lineCount)

                                canvas.drawRect(
                                    x,
                                    currentY,
                                    x + width,
                                    currentY + rowHeight,
                                    tableBorderPaint
                                )

                                if (until > from) {
                                    drawClippedLayout(
                                        layout,
                                        canvas,
                                        x + 4f,
                                        currentY + 4f,
                                        layout.getLineTop(from),
                                        layout.getLineBottom(until - 1)
                                    )
                                }
                                x += width
                            }

                            currentY += rowHeight
                            firstLine = lastLine
                        }
                    }

                    drawMoodTableHeader()

                    val moodDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                    val moodTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val moodKeys = listOf(
                        "mood_awful",
                        "mood_bad",
                        "mood_meh",
                        "mood_good",
                        "mood_rad"
                    )

                    var previousMoodDate: String? = null

                    moods.takeLast(500).forEach { mood ->
                        val moodDate = moodDateFormat.format(Date(mood.timestamp))
                        val dateCell = if (moodDate != previousMoodDate) moodDate else ""
                        previousMoodDate = moodDate

                        val moodIndex = moodKeys.indexOf(mood.moodLabel).takeIf { it >= 0 }
                            ?: moodKeys.indexOfFirst { key ->
                                val resourceId = context.resources.getIdentifier(
                                    key,
                                    "string",
                                    context.packageName
                                )
                                resourceId != 0 && context.getString(resourceId) == mood.moodLabel
                            }

                        val score = if (moodIndex >= 0) (moodIndex + 1).toString() else ""

                        val memberNames = mood.memberIds.mapNotNull { id ->
                            people.firstOrNull { it.id == id && !it.isArchived }?.name
                        }

                        drawMoodTableRow(
                            listOf(
                                dateCell,
                                moodTimeFormat.format(Date(mood.timestamp)),
                                score,
                                memberNames.joinToString(", "),
                                mood.note
                            )
                        )
                    }
                }

                // 5. Notes
                if (selections[2]) {
                    startNewPage()
                    drawText(context.getString(R.string.diary), headerPaint, 15f)

                    val notesJson = sharedPref.getString("diary_notes", "[]")
                    val allNotes: List<DiaryNote> =
                        gson.fromJson(
                            notesJson,
                            object : TypeToken<List<DiaryNote>>() {}.type
                        ) ?: emptyList()

                    val notes = allNotes
                        .filter { note ->
                            val noteDate = note.noteEditTimestamp ?: note.timestamp
                            noteDate in effectiveStart..effectiveEnd
                        }
                        .sortedByDescending { note ->
                            note.noteEditTimestamp ?: note.timestamp
                        }

                    notes.forEach { note ->
                        val noteDate = note.noteEditTimestamp ?: note.timestamp

                        drawText(
                            "${context.getString(R.string.hint_note_title)}: ${note.title}",
                            headerPaint,
                            5f
                        )
                        drawText(
                            "${context.getString(R.string.date)}: ${sdf.format(Date(noteDate))}",
                            textPaint,
                            5f
                        )
                        drawText(note.content, textPaint, 15f)
                    }
                }

                // 6. To-Do Lists
                if (selections[3]) {
                    startNewPage()
                    drawText(context.getString(R.string.todo), headerPaint, 15f)
                    val todoJson = sharedPref.getString("todo_lists", "[]")
                    val allTodoLists: List<TodoList> =
                        gson.fromJson(todoJson, object : TypeToken<List<TodoList>>() {}.type)
                            ?: emptyList()
                    val todoLists =
                        allTodoLists.filter { it.timestamp in effectiveStart..effectiveEnd }
                    todoLists.forEach { list ->
                        drawText(
                            "${context.getString(R.string.hint_todo_list_title)}: ${list.title}",
                            headerPaint,
                            5f
                        )
                        list.tasks.forEach { task ->
                            val status = if (task.status == "DONE") "[X]" else "[ ]"
                            drawText("$status ${task.title}", textPaint, 3f)
                        }
                        currentY += 10f
                    }
                }

                // 7. Relationships
                if (selections.size > 4 && selections[4]) {
                    startNewPage()
                    drawText(context.getString(R.string.module_relations), headerPaint, 15f)
                    val relationsJson = sharedPref.getString("relations_environments", "[]")
                    val environments: List<RelationEnvironment> = try {
                        gson.fromJson(
                            relationsJson,
                            object : TypeToken<List<RelationEnvironment>>() {}.type
                        )
                    } catch (_: Exception) {
                        emptyList()
                    }
                    environments.forEach { env ->
                        drawText(
                            "${context.getString(R.string.label_bundle)}: ${env.name}",
                            headerPaint,
                            8f
                        )
                        env.data.nodes.forEach { node ->
                            val nodeType = if (node.type == NodeType.MEMBER) "Member" else "Orb"
                            drawText("- [$nodeType] ${node.name}", textPaint, 3f)
                        }
                        currentY += 10f
                    }
                }

                // 8. Agenda Events
                if (selections.size > 5 && selections[5]) {
                    startNewPage()
                    drawText(context.getString(R.string.calendar), headerPaint, 15f)
                    val calendarJson = sharedPref.getString("calendar_events", "[]")
                    val allEvents: List<com.interli.plural.CalendarEvent> = try {
                        gson.fromJson(
                            calendarJson,
                            object :
                                com.google.gson.reflect.TypeToken<List<com.interli.plural.CalendarEvent>>() {}.type
                        )
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val events = allEvents.filter {
                        it.startTime <= effectiveEnd && (it.endTime
                            ?: it.startTime) >= effectiveStart
                    }.sortedBy { it.startTime }

                    if (events.isEmpty()) {
                        drawText(context.getString(R.string.no_data_period), textPaint, 10f)
                    } else {
                        events.forEach { event ->
                            val startStr = sdf.format(Date(event.startTime))
                            drawText("$startStr: ${event.title}", headerPaint, 5f)
                            if (!event.location.isNullOrBlank()) drawText(
                                "${context.getString(R.string.event_location)}: ${event.location}",
                                textPaint,
                                3f
                            )
                            if (!event.description.isNullOrBlank()) drawText(
                                event.description!!,
                                textPaint,
                                5f
                            )
                            currentY += 10f
                            if (currentY > PAGE_HEIGHT - MARGIN) startNewPage()
                        }
                    }
                }
                // 9. Subsystems
                if (selections.size > 6 && selections[6]) {
                    startNewPage()
                    drawText(context.getString(R.string.subsystem_page), headerPaint, 15f)

                    val subJson = sharedPref.getString("subsystem_data", "[]")
                    val subSessionsJson = sharedPref.getString("subsystem_sessions", "[]")

                    val subGroups: List<Map<String, Any>> = try {
                        gson.fromJson(subJson, object : TypeToken<List<Map<String, Any>>>() {}.type)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val subSessions: List<FrontSession> = try {
                        gson.fromJson(
                            subSessionsJson,
                            object : TypeToken<List<FrontSession>>() {}.type
                        )
                    } catch (_: Exception) {
                        emptyList()
                    }

                    subGroups.forEach { group ->
                        val groupName = group["name"] as? String ?: "Unnamed Group"
                        drawText(
                            "${context.getString(R.string.hint_group_name)}: $groupName",
                            headerPaint,
                            8f
                        )

                        val members = group["members"] as? List<Map<String, Any>> ?: emptyList()
                        members.forEach { m ->
                            val mName = m["name"] as? String ?: "Unknown"
                            drawText("  - $mName", textPaint, 3f)
                        }
                        currentY += 10f
                    }

                    if (subSessions.isNotEmpty()) {
                        currentY += 10f
                        drawText(
                            context.getString(R.string.timeline_front_title) + " (Subsystems)",
                            headerPaint,
                            10f
                        )
                        val filteredSubSessions = subSessions.filter {
                            (it.endTime
                                ?: System.currentTimeMillis()) >= effectiveStart && it.startTime <= effectiveEnd
                        }

                        filteredSubSessions.reversed().take(200).forEach { session ->
                            val start = sdf.format(Date(session.startTime))
                            val end = session.endTime?.let { sdf.format(Date(it)) }
                                ?: context.getString(R.string.currently_active)
                            drawText("${session.personName}: $start to $end", textPaint, 3f)
                        }
                    }
                }

                doc.finishPage(page)
                context.contentResolver.openOutputStream(uri)?.use { os -> doc.writeTo(os) }
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(
                        context,
                        R.string.export_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                doc.close()
            }
        }.start()
    }

    fun exportRelationsToPdf(
        context: Context,
        uri: Uri,
        relationsData: RelationsData,
        mapView: RelationsMapView
    ) {
        Thread {
            val doc = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                var currentY = MARGIN
                val titlePaint = TextPaint().apply {
                    textSize = 22f; isFakeBoldText = true; color = android.graphics.Color.BLACK
                }
                val textPaint =
                    TextPaint().apply { textSize = 11f; color = android.graphics.Color.BLACK }
                val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                val title = "Interli Plural - Relations Map"
                val titleLayout =
                    createStaticLayout(title, titlePaint, PAGE_WIDTH - 2 * MARGIN.toInt())
                titleLayout.draw(canvas, MARGIN, currentY)
                currentY += titleLayout.height + 10f
                val dateStr = "Export Date: ${sdf.format(Date())}"
                val dateLayout =
                    createStaticLayout(dateStr, textPaint, PAGE_WIDTH - 2 * MARGIN.toInt())
                dateLayout.draw(canvas, MARGIN, currentY)
                currentY += dateLayout.height + 30f
                val mapBitmap = mapView.captureFullMapBitmap()
                if (mapBitmap != null) {
                    val scale = 0.3f
                    val scaledWidth = mapBitmap.width * scale
                    val scaledHeight = mapBitmap.height * scale
                    val pageWidthPoints = PAGE_WIDTH - 2 * MARGIN
                    val pageHeightPoints = PAGE_HEIGHT - 2 * MARGIN
                    val cols =
                        Math.ceil((scaledWidth / pageWidthPoints.toFloat()).toDouble()).toInt()
                            .coerceAtLeast(1)
                    val rows =
                        Math.ceil((scaledHeight / pageHeightPoints.toFloat()).toDouble()).toInt()
                            .coerceAtLeast(1)
                    var firstPageFinished = false
                    for (row in 0 until rows) {
                        for (col in 0 until cols) {
                            if (row == 0 && col == 0) {
                                canvas.save()
                                canvas.translate(MARGIN, currentY)
                                canvas.scale(scale, scale)
                                canvas.drawBitmap(mapBitmap, 0f, 0f, null)
                                canvas.restore()
                                doc.finishPage(page)
                                firstPageFinished = true
                            } else {
                                val tilePageInfo = PdfDocument.PageInfo.Builder(
                                    PAGE_WIDTH,
                                    PAGE_HEIGHT,
                                    doc.pages.size + 1
                                ).create()
                                val tilePage = doc.startPage(tilePageInfo)
                                val tileCanvas = tilePage.canvas
                                val offsetX = col * pageWidthPoints
                                val offsetY = row * pageHeightPoints
                                tileCanvas.save()
                                tileCanvas.translate(MARGIN, MARGIN)
                                tileCanvas.translate(-offsetX, -offsetY)
                                tileCanvas.scale(scale, scale)
                                tileCanvas.drawBitmap(mapBitmap, 0f, 0f, null)
                                tileCanvas.restore()
                                doc.finishPage(tilePage)
                            }
                        }
                    }
                    if (!firstPageFinished) doc.finishPage(page)
                } else {
                    doc.finishPage(page)
                }
                context.contentResolver.openOutputStream(uri)?.use { os -> doc.writeTo(os) }
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, R.string.export_success, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                doc.close()
            }
        }.start()
    }

    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false)
        }
    }

    private fun drawClippedLayout(
        layout: StaticLayout,
        canvas: Canvas,
        x: Float,
        y: Float,
        startY: Int,
        endY: Int
    ) {
        canvas.save()
        canvas.translate(x, y - startY)
        canvas.clipRect(0f, startY.toFloat(), layout.width.toFloat(), endY.toFloat())
        layout.draw(canvas)
        canvas.restore()
    }

    private fun StaticLayout.draw(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        this.draw(canvas)
        canvas.restore()
    }

    fun exportMedicationHistoryToPdf(
        context: Context,
        uri: Uri,
        medications: List<MedicationItem>,
        relevantLogs: List<HealthLogEntry>,
        includeMemberNames: Boolean = true
    ) {
        Thread {
            val doc = PdfDocument()
            try {
                var pageCount = 1
                var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
                var page = doc.startPage(pageInfo)
                var canvas = page.canvas
                var currentY = MARGIN

                val titlePaint = TextPaint().apply {
                    textSize = 18f
                    isFakeBoldText = true
                    color = android.graphics.Color.BLACK
                }
                val headerPaint = TextPaint().apply {
                    textSize = 13f
                    isFakeBoldText = true
                    color = android.graphics.Color.BLACK
                }
                val textPaint = TextPaint().apply {
                    textSize = 10f
                    color = android.graphics.Color.BLACK
                }
                val subTextPaint = TextPaint().apply {
                    textSize = 9f
                    color = android.graphics.Color.DKGRAY
                }
                val linePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    strokeWidth = 1f
                }

                fun startNewPage() {
                    doc.finishPage(page)
                    pageCount++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = MARGIN
                }

                val title = context.getString(R.string.btn_medication_history)
                val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

                canvas.drawText(title, MARGIN, currentY + 18f, titlePaint)
                currentY += 30f

                val exportDateStr = context.getString(R.string.export_date_label, sdfDate.format(Date()))
                canvas.drawText(exportDateStr, MARGIN, currentY + 12f, subTextPaint)
                currentY += 25f

                medications.forEach { med ->
                    val medTitle = buildString {
                        append(med.name)
                        if (med.dosage.isNotEmpty()) append(" (${med.dosage})")
                    }

                    if (currentY > PAGE_HEIGHT - MARGIN - 60) {
                        startNewPage()
                    }

                    canvas.drawText(medTitle, MARGIN, currentY + 14f, headerPaint)
                    currentY += 20f

                    val medValue = "${med.name.orEmpty()}" + if (med.dosage.isNotEmpty()) " (${med.dosage})" else ""
                    val medLogs = relevantLogs.filter { log ->
                        log.value == medValue
                    }.sortedByDescending { it.timestamp }

                    if (medLogs.isEmpty()) {
                        canvas.drawText("- ${context.getString(R.string.no_intake_recorded)}", MARGIN + 10f, currentY + 12f, subTextPaint)
                        currentY += 18f
                    } else {
                        medLogs.forEach { log ->
                            if (currentY > PAGE_HEIGHT - MARGIN - 25) {
                                startNewPage()
                            }
                            val dateStr = sdfDate.format(Date(log.timestamp))
                            val timeStr = sdfTime.format(Date(log.timestamp))
                            val noteStr = if (!log.note.isNullOrEmpty()) " (${log.note})" else ""

                            val lineText = if (includeMemberNames) {
                                val memberStr = log.memberName ?: context.getString(R.string.group_general)
                                "• $dateStr $timeStr - $memberStr$noteStr"
                            } else {
                                "• $dateStr $timeStr$noteStr"
                            }

                            canvas.drawText(lineText, MARGIN + 10f, currentY + 12f, textPaint)
                            currentY += 16f
                        }
                    }

                    canvas.drawLine(MARGIN, currentY + 5f, PAGE_WIDTH - MARGIN, currentY + 5f, linePaint)
                    currentY += 15f
                }

                doc.finishPage(page)
                context.contentResolver.openOutputStream(uri)?.use { os -> doc.writeTo(os) }

                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            } finally {
                doc.close()
            }
        }.start()
    }
}
