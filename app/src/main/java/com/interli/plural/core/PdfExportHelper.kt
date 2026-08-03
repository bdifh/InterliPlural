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
import com.interli.plural.Person
import com.interli.plural.R
import com.interli.plural.TodoList
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfExportHelper {
    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 50f
    fun exportNoteToPdf(context: Context, uri: Uri, title: String, content: String) {
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
                val textPaint = TextPaint().apply {
                    textSize = 12f
                    color = android.graphics.Color.BLACK
                }
                // Draw Title
                val titleLayout = createStaticLayout(title, titlePaint, PAGE_WIDTH - 2 * MARGIN.toInt())
                titleLayout.draw(canvas, MARGIN, currentY)
                currentY += titleLayout.height + 20f
                // Draw Content
                val contentLayout = createStaticLayout(content, textPaint, PAGE_WIDTH - 2 * MARGIN.toInt())
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
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
                        page = doc.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = MARGIN
                        continue
                    }
                    val startLineTop = contentLayout.getLineTop(currentLine)
                    val endLineBottom = contentLayout.getLineBottom(currentLine + linesThatFit - 1)
                    val heightToDraw = endLineBottom - startLineTop
                    drawClippedLayout(contentLayout, canvas, MARGIN, currentY, startLineTop, endLineBottom)
                    currentY += heightToDraw
                    currentLine += linesThatFit
                    if (currentLine < contentLayout.lineCount) {
                        doc.finishPage(page)
                        pageCount++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
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
                    Toast.makeText(context, context.getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            } finally {
                doc.close()
            }
        }.start()
    }
    fun exportFullDataToPdf(context: Context, uri: Uri, selections: BooleanArray, startDate: Long? = null, endDate: Long? = null) {
        Thread {
            val doc = PdfDocument()
            try {
                var pageCount = 1
                var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
                var page = doc.startPage(pageInfo)
                var canvas = page.canvas
                var currentY = MARGIN
                val titlePaint = TextPaint().apply { textSize = 22f; isFakeBoldText = true; color = android.graphics.Color.BLACK }
                val headerPaint = TextPaint().apply { textSize = 16f; isFakeBoldText = true; color = android.graphics.Color.BLACK }
                val textPaint = TextPaint().apply { textSize = 11f; color = android.graphics.Color.BLACK }
                val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

                fun startNewPage() {
                    doc.finishPage(page)
                    pageCount++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
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
                        drawClippedLayout(layout, canvas, MARGIN, currentY, startLineTop, endLineBottom)
                        currentY += heightToDraw
                        currentLine += linesThatFit
                        if (currentLine < layout.lineCount) {
                            startNewPage()
                        }
                    }
                    currentY += spacing
                }

                // 1. Hoofdtitels en Periode
                drawText("${context.getString(R.string.app_name)} - Data Export", titlePaint, 15f)
                drawText("Export Date: ${sdf.format(Date())}", textPaint, 10f)

                val effectiveStart = startDate ?: 0L
                val effectiveEnd = endDate ?: Long.MAX_VALUE

                if (startDate != null || endDate != null) {
                    val startStr = startDate?.let { sdf.format(Date(it)) } ?: context.getString(R.string.period_all_time)
                    val endStr = endDate?.let { sdf.format(Date(it)) } ?: context.getString(R.string.currently_active)
                    drawText("${context.getString(R.string.stats_period)} $startStr ${context.getString(R.string.to_date)} $endStr", textPaint, 30f)
                } else {
                    drawText("${context.getString(R.string.stats_period)} ${context.getString(R.string.period_all_time)}", textPaint, 30f)
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
                    if (currentY > PAGE_HEIGHT - 200) startNewPage()
                    drawText(context.getString(R.string.timeline_front_title), headerPaint, 15f)
                    val sessionsJson = sharedPref.getString("sessions_list", "[]")
                    val allSessions: List<FrontSession> = gson.fromJson(sessionsJson, object : TypeToken<List<FrontSession>>() {}.type) ?: emptyList()
                    val sessions = allSessions.filter { (it.endTime ?: System.currentTimeMillis()) >= effectiveStart && it.startTime <= effectiveEnd }

                    // Grafiek tekenen...
                    val chart = TimelineChartView(context, null)
                    chart.setData(sessions, people)
                    val chartStart = startDate ?: (sessions.minOfOrNull { it.startTime } ?: (System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000))
                    val chartEnd = endDate ?: (sessions.maxOfOrNull { it.endTime ?: System.currentTimeMillis() } ?: System.currentTimeMillis())
                    chart.setExportRange(chartStart, chartEnd)
                    val chartWidth = PAGE_WIDTH - 2 * MARGIN.toInt()
                    val chartHeight = (chart.uniqueMembersSize() * 24f + 200f).coerceIn(200f, 600f)

                    if (currentY + chartHeight > PAGE_HEIGHT - MARGIN) startNewPage()
                    val chartBitmap = android.graphics.Bitmap.createBitmap(chartWidth.toInt(), chartHeight.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
                    val chartCanvas = android.graphics.Canvas(chartBitmap)
                    chartCanvas.drawColor(android.graphics.Color.WHITE)
                    chart.measure(android.view.View.MeasureSpec.makeMeasureSpec(chartWidth.toInt(), android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(chartHeight.toInt(), android.view.View.MeasureSpec.EXACTLY))
                    chart.layout(0, 0, chartWidth.toInt(), chartHeight.toInt())
                    chart.draw(chartCanvas)
                    canvas.drawBitmap(chartBitmap, MARGIN, currentY, null)
                    currentY += chartHeight + 20f

                    sessions.reversed().take(500).forEach { session ->
                        val start = sdf.format(Date(session.startTime))
                        val end = session.endTime?.let { sdf.format(Date(it)) } ?: context.getString(R.string.currently_active)
                        drawText("${session.personName}: $start to $end", textPaint, 3f)
                    }
                }

                // 4. Mood Data
                if (selections[1]) {
                    startNewPage()
                    drawText(context.getString(R.string.mood_tracker), headerPaint, 15f)
                    val moodJson = sharedPref.getString("mood_entries", "[]")
                    val allMoods: List<MoodActivity.MoodEntry> = gson.fromJson(moodJson, object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type) ?: emptyList()
                    val moods = allMoods.filter { it.timestamp in effectiveStart..effectiveEnd }

                    val chart = MoodChartView(context, null)
                    chart.setData(moods, MoodChartView.Mode.TIMELINE)
                    val chartStart = startDate ?: (moods.firstOrNull()?.timestamp ?: (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000))
                    val chartEnd = endDate ?: (moods.lastOrNull()?.timestamp ?: System.currentTimeMillis())
                    chart.setExportRange(chartStart, chartEnd)
                    val chartWidth = PAGE_WIDTH - 2 * MARGIN.toInt()
                    val chartHeight = 300f

                    if (currentY + chartHeight > PAGE_HEIGHT - MARGIN) startNewPage()
                    val chartBitmap = android.graphics.Bitmap.createBitmap(chartWidth.toInt(), chartHeight.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
                    val chartCanvas = android.graphics.Canvas(chartBitmap)
                    chartCanvas.drawColor(android.graphics.Color.WHITE)
                    chart.measure(android.view.View.MeasureSpec.makeMeasureSpec(chartWidth.toInt(), android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(chartHeight.toInt(), android.view.View.MeasureSpec.EXACTLY))
                    chart.layout(0, 0, chartWidth.toInt(), chartHeight.toInt())
                    chart.draw(chartCanvas)
                    canvas.drawBitmap(chartBitmap, MARGIN, currentY, null)
                    currentY += chartHeight + 20f

                    moods.reversed().take(500).forEach { mood ->
                        val time = sdf.format(Date(mood.timestamp))
                        val memberNames = mood.memberIds.mapNotNull { id -> people.find { it.id == id && !it.isArchived }?.name }
                        val membersStr = if (memberNames.isNotEmpty()) " - ${memberNames.joinToString(", ")}" else ""
                        drawText("[$time] ${mood.moodEmoji} ${mood.moodLabel}$membersStr", textPaint, 2f)
                        if (mood.note.isNotEmpty()) drawText("  ${context.getString(R.string.note)}: ${mood.note}", textPaint, 5f)
                    }
                }

                // 5. Notes
                if (selections[2]) {
                    startNewPage()
                    drawText(context.getString(R.string.diary), headerPaint, 15f)
                    val notesJson = sharedPref.getString("diary_notes", "[]")
                    val allNotes: List<DiaryNote> = gson.fromJson(notesJson, object : TypeToken<List<DiaryNote>>() {}.type) ?: emptyList()
                    val notes = allNotes.filter { it.timestamp in effectiveStart..effectiveEnd }
                    notes.reversed().forEach { note ->
                        drawText("${context.getString(R.string.hint_note_title)}: ${note.title}", headerPaint, 5f)
                        drawText("${context.getString(R.string.date)}: ${sdf.format(Date(note.timestamp))}", textPaint, 5f)
                        drawText(note.content, textPaint, 15f)
                    }
                }

                // 6. To-Do Lists
                if (selections[3]) {
                    startNewPage()
                    drawText(context.getString(R.string.todo), headerPaint, 15f)
                    val todoJson = sharedPref.getString("todo_lists", "[]")
                    val allTodoLists: List<TodoList> = gson.fromJson(todoJson, object : TypeToken<List<TodoList>>() {}.type) ?: emptyList()
                    val todoLists = allTodoLists.filter { it.timestamp in effectiveStart..effectiveEnd }
                    todoLists.forEach { list ->
                        drawText("${context.getString(R.string.hint_todo_list_title)}: ${list.title}", headerPaint, 5f)
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
                    val environments: List<RelationEnvironment> = try { gson.fromJson(relationsJson, object : TypeToken<List<RelationEnvironment>>() {}.type) } catch (_: Exception) { emptyList() }
                    environments.forEach { env ->
                        drawText("${context.getString(R.string.label_bundle)}: ${env.name}", headerPaint, 8f)
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
                        gson.fromJson(calendarJson, object : com.google.gson.reflect.TypeToken<List<com.interli.plural.CalendarEvent>>() {}.type)
                    } catch (e: Exception) { emptyList() }

                    val events = allEvents.filter {
                        it.startTime <= effectiveEnd && (it.endTime ?: it.startTime) >= effectiveStart
                    }.sortedBy { it.startTime }

                    if (events.isEmpty()) {
                        drawText(context.getString(R.string.no_data_period), textPaint, 10f)
                    } else {
                        events.forEach { event ->
                            val startStr = sdf.format(Date(event.startTime))
                            drawText("$startStr: ${event.title}", headerPaint, 5f)
                            if (!event.location.isNullOrBlank()) drawText("${context.getString(R.string.event_location)}: ${event.location}", textPaint, 3f)
                            if (!event.description.isNullOrBlank()) drawText(event.description!!, textPaint, 5f)
                            currentY += 10f
                            if (currentY > PAGE_HEIGHT - MARGIN) startNewPage()
                        }
                    }
                }

                doc.finishPage(page)
                context.contentResolver.openOutputStream(uri)?.use { os -> doc.writeTo(os) }
                (context as? android.app.Activity)?.runOnUiThread { Toast.makeText(context, R.string.export_success, Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? android.app.Activity)?.runOnUiThread { Toast.makeText(context, context.getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show() }
            } finally {
                doc.close()
            }
        }.start()
    }
    fun exportRelationsToPdf(context: Context, uri: Uri, relationsData: RelationsData, mapView: RelationsMapView) {
        Thread {
            val doc = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                var currentY = MARGIN
                val titlePaint = TextPaint().apply { textSize = 22f; isFakeBoldText = true; color = android.graphics.Color.BLACK }
                val textPaint = TextPaint().apply { textSize = 11f; color = android.graphics.Color.BLACK }
                val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                val title = "Interli Plural - Relations Map"
                val titleLayout = createStaticLayout(title, titlePaint, PAGE_WIDTH - 2 * MARGIN.toInt())
                titleLayout.draw(canvas, MARGIN, currentY)
                currentY += titleLayout.height + 10f
                val dateStr = "Export Date: ${sdf.format(Date())}"
                val dateLayout = createStaticLayout(dateStr, textPaint, PAGE_WIDTH - 2 * MARGIN.toInt())
                dateLayout.draw(canvas, MARGIN, currentY)
                currentY += dateLayout.height + 30f
                val mapBitmap = mapView.captureFullMapBitmap()
                if (mapBitmap != null) {
                    val scale = 0.3f
                    val scaledWidth = mapBitmap.width * scale
                    val scaledHeight = mapBitmap.height * scale
                    val pageWidthPoints = PAGE_WIDTH - 2 * MARGIN
                    val pageHeightPoints = PAGE_HEIGHT - 2 * MARGIN
                    val cols = Math.ceil((scaledWidth / pageWidthPoints.toFloat()).toDouble()).toInt().coerceAtLeast(1)
                    val rows = Math.ceil((scaledHeight / pageHeightPoints.toFloat()).toDouble()).toInt().coerceAtLeast(1)
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
                                val tilePageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, doc.pages.size + 1).create()
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
                    Toast.makeText(context, context.getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
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
    private fun drawClippedLayout(layout: StaticLayout, canvas: Canvas, x: Float, y: Float, startY: Int, endY: Int) {
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
}
