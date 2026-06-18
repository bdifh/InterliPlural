package com.interli.plural

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
                            doc.finishPage(page)
                            pageCount++
                            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
                            page = doc.startPage(pageInfo)
                            canvas = page.canvas
                            currentY = MARGIN
                            continue
                        }

                        val startLineTop = layout.getLineTop(currentLine)
                        val endLineBottom = layout.getLineBottom(currentLine + linesThatFit - 1)
                        val heightToDraw = endLineBottom - startLineTop

                        drawClippedLayout(layout, canvas, MARGIN, currentY, startLineTop, endLineBottom)
                        
                        currentY += heightToDraw
                        currentLine += linesThatFit

                        if (currentLine < layout.lineCount) {
                            doc.finishPage(page)
                            pageCount++
                            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create()
                            page = doc.startPage(pageInfo)
                            canvas = page.canvas
                            currentY = MARGIN
                        }
                    }
                    currentY += spacing
                }

                // Main Title
                drawText("Interli Plural - Data Export", titlePaint, 20f)
                drawText("Export Date: ${sdf.format(Date())}", textPaint, 30f)

                val sharedPref = context.getSharedPreferences("my_app", Context.MODE_PRIVATE)
                val gson = Gson()
                val people = MemberHelper.loadAllPeople(context)

                val effectiveStart = startDate ?: 0L
                val effectiveEnd = endDate ?: Long.MAX_VALUE

                // Main Title
                drawText("Interli Plural - Data Export", titlePaint, 20f)
                drawText("Export Date: ${sdf.format(Date())}", textPaint, 10f)
                if (startDate != null || endDate != null) {
                    val startStr = startDate?.let { sdf.format(Date(it)) } ?: "Beginning"
                    val endStr = endDate?.let { sdf.format(Date(it)) } ?: "Present"
                    drawText("Period: $startStr to $endStr", textPaint, 30f)
                } else {
                    drawText("Period: All-time", textPaint, 30f)
                }

                // 1. Members
                drawText("Members", headerPaint, 15f)
                people.forEach { person ->
                    drawText("- ${person.name} (ID: ${person.id})", textPaint, 5f)
                }
                currentY += 20f

                // 2. Fronting Data
                if (selections[0]) {
                    startNewPage()
                    drawText("Fronting History", headerPaint, 15f)
                    
                    val sessionsJson = sharedPref.getString("sessions_list", "[]")
                    val allSessions: List<FrontSession> = gson.fromJson(sessionsJson, object : TypeToken<List<FrontSession>>() {}.type) ?: emptyList()
                    val sessions = allSessions.filter { 
                        (it.endTime ?: System.currentTimeMillis()) >= effectiveStart && it.startTime <= effectiveEnd
                    }

                    // Draw Chart
                    val chart = TimelineChartView(context, null)
                    chart.setData(sessions, people)
                    
                    val chartStart = if (startDate != null) startDate else (sessions.minOfOrNull { it.startTime } ?: (System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000))
                    val chartEnd = if (endDate != null) endDate else (sessions.maxOfOrNull { it.endTime ?: System.currentTimeMillis() } ?: System.currentTimeMillis())
                    
                    chart.setExportRange(chartStart, chartEnd)
                    
                    val chartWidth = PAGE_WIDTH - 2 * MARGIN.toInt()
                    val chartHeight = (chart.uniqueMembersSize() * 24f + 200f).coerceIn(200f, 600f) // Simplified height calculation
                    
                    if (currentY + chartHeight > PAGE_HEIGHT - MARGIN) {
                        startNewPage()
                    }
                    
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
                        val end = session.endTime?.let { sdf.format(Date(it)) } ?: "Active"
                        drawText("${session.personName}: $start to $end", textPaint, 3f)
                    }
                    currentY += 20f
                }

                // 3. Mood Data
                if (selections[1]) {
                    startNewPage()
                    drawText("Mood Data", headerPaint, 15f)
                    
                    val moodJson = sharedPref.getString("mood_entries", "[]")
                    val allMoods: List<MoodActivity.MoodEntry> = gson.fromJson(moodJson, object : TypeToken<List<MoodActivity.MoodEntry>>() {}.type) ?: emptyList()
                    val moods = allMoods.filter { it.timestamp in effectiveStart..effectiveEnd }

                    // Draw Chart
                    val chart = MoodChartView(context, null)
                    chart.setData(moods, MoodChartView.Mode.TIMELINE)
                    
                    val chartStart = if (startDate != null) startDate else (moods.firstOrNull()?.timestamp ?: (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000))
                    val chartEnd = if (endDate != null) endDate else (moods.lastOrNull()?.timestamp ?: System.currentTimeMillis())
                    
                    chart.setExportRange(chartStart, chartEnd)
                    
                    val chartWidth = PAGE_WIDTH - 2 * MARGIN.toInt()
                    val chartHeight = 300f
                    
                    if (currentY + chartHeight > PAGE_HEIGHT - MARGIN) {
                        startNewPage()
                    }

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
                        val members = mood.memberIds.map { id -> people.find { it.id == id }?.name ?: id }.joinToString(", ")
                        drawText("[$time] ${mood.moodEmoji} ${mood.moodLabel} - $members", textPaint, 2f)
                        if (mood.note.isNotEmpty()) {
                            drawText("  Note: ${mood.note}", textPaint, 5f)
                        } else {
                            currentY += 3f
                        }
                    }
                    currentY += 20f
                }

                // 4. Notes
                if (selections[2]) {
                    startNewPage()
                    drawText("Diary Notes", headerPaint, 15f)
                    val notesJson = sharedPref.getString("diary_notes", "[]")
                    val allNotes: List<DiaryNote> = gson.fromJson(notesJson, object : TypeToken<List<DiaryNote>>() {}.type) ?: emptyList()
                    val notes = allNotes.filter { it.timestamp in effectiveStart..effectiveEnd }
                    
                    notes.reversed().forEach { note ->
                        drawText("Title: ${note.title}", headerPaint, 5f)
                        drawText("Date: ${sdf.format(Date(note.timestamp))}", textPaint, 5f)
                        drawText(note.content, textPaint, 15f)
                    }
                    currentY += 20f
                }

                // 5. To-Do Lists
                if (selections[3]) {
                    startNewPage()
                    drawText("To-Do Lists", headerPaint, 15f)
                    val todoJson = sharedPref.getString("todo_lists", "[]")
                    val allTodoLists: List<TodoList> = gson.fromJson(todoJson, object : TypeToken<List<TodoList>>() {}.type) ?: emptyList()
                    val todoLists = allTodoLists.filter { it.timestamp in effectiveStart..effectiveEnd }
                    
                    todoLists.forEach { list ->
                        drawText("List: ${list.title}", headerPaint, 5f)
                        list.tasks.forEach { task ->
                            val status = if (task.status == "DONE") "[X]" else "[ ]"
                            drawText("$status ${task.title}", textPaint, 3f)
                        }
                        currentY += 10f
                    }
                }

                doc.finishPage(page)
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
