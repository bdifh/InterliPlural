package com.interli.plural

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
import kotlin.math.sqrt

class RelationsMapView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var data: RelationsData = RelationsData()
    private val nodeRadius = 60f
    private val bitmaps = mutableMapOf<String, Bitmap>()
    
    private var draggedNodeId: String? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    private var offsetX = 0f
    private var offsetY = 0f
    private var scaleFactor = 1.0f

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val dottedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.GRAY
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.GRAY
    }
    private val groupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 60
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f
        color = Color.BLACK
        typeface = Typeface.DEFAULT_BOLD
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f
        color = Color.DKGRAY
    }

    private val commonPath = Path()
    private val bubbleRect = RectF()

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (draggedNodeId == null) {
                offsetX -= distanceX
                offsetY -= distanceY
                invalidate()
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val worldX = (e.x - offsetX) / scaleFactor
            val worldY = (e.y - offsetY) / scaleFactor
            val clickedNode = findNodeAt(worldX, worldY)
            if (clickedNode != null) {
                onNodeLongClicked?.invoke(clickedNode)
            } else {
                val clickedGroup = findGroupAt(worldX, worldY)
                if (clickedGroup != null) {
                    onGroupLongClicked?.invoke(clickedGroup)
                }
            }
        }
    })

    var onNodeLongClicked: ((RelationNode) -> Unit)? = null
    var onGroupLongClicked: ((RelationGroup) -> Unit)? = null
    var onDataChanged: (() -> Unit)? = null

    fun setData(newData: RelationsData) {
        data = newData
        preloadImages()
        invalidate()
    }

    private fun preloadImages() {
        data.nodes.forEach { node ->
            if (node.imageUri != null && !bitmaps.containsKey(node.imageUri)) {
                val request = ImageRequest.Builder(context)
                    .data(node.imageUri)
                    .target { result ->
                        val bitmap = try { result.toBitmap() } catch (_: Exception) { null }
                        if (bitmap != null) {
                            bitmaps[node.imageUri!!] = bitmap
                            invalidate()
                        }
                    }
                    .build()
                context.imageLoader.enqueue(request)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)

        data.groups.forEach { group ->
            val memberNodes = data.nodes.filter { group.nodeIds.contains(it.id) }
            if (memberNodes.isNotEmpty()) {
                groupPaint.color = group.color
                groupPaint.alpha = 40
                
                if (memberNodes.size == 1) {
                    canvas.drawCircle(memberNodes[0].x, memberNodes[0].y, nodeRadius * 2.5f, groupPaint)
                } else {
                    val minX = memberNodes.minOf { it.x } - nodeRadius * 1.5f
                    val maxX = memberNodes.maxOf { it.x } + nodeRadius * 1.5f
                    val minY = memberNodes.minOf { it.y } - nodeRadius * 1.5f
                    val maxY = memberNodes.maxOf { it.y } + nodeRadius * 1.5f
                    bubbleRect.set(minX, minY, maxX, maxY)
                    canvas.drawRoundRect(bubbleRect, nodeRadius * 2, nodeRadius * 2, groupPaint)
                }

                val centerX = memberNodes.map { it.x }.average().toFloat()
                val minY = memberNodes.minOf { it.y } - nodeRadius * 2f
                textPaint.color = ColorHelper.getTextColor(context)
                canvas.drawText(group.name, centerX, minY, textPaint)
            }
        }

        data.edges.forEach { edge ->
            val from = data.nodes.find { it.id == edge.fromNodeId }
            val to = data.nodes.find { it.id == edge.toNodeId }
            if (from != null && to != null) {
                canvas.drawLine(from.x, from.y, to.x, to.y, edgePaint)
                edge.tag?.let { tag ->
                    val midX = (from.x + to.x) / 2
                    val midY = (from.y + to.y) / 2
                    canvas.drawText(tag, midX, midY - 10f, tagPaint)
                }
            }
        }

        val memberGroups = data.nodes.filter { it.memberId != null }.groupBy { it.memberId }
        memberGroups.forEach { (_, nodes) ->
            if (nodes.size > 1) {
                for (i in 0 until nodes.size - 1) {
                    for (j in i + 1 until nodes.size) {
                        canvas.drawLine(nodes[i].x, nodes[i].y, nodes[j].x, nodes[j].y, dottedPaint)
                    }
                }
            }
        }

        data.nodes.forEach { node ->
            val bitmap = node.imageUri?.let { bitmaps[it] }
            if (bitmap != null) {
                commonPath.reset()
                commonPath.addCircle(node.x, node.y, nodeRadius, Path.Direction.CCW)
                canvas.save()
                canvas.clipPath(commonPath)
                bubbleRect.set(node.x - nodeRadius, node.y - nodeRadius, node.x + nodeRadius, node.y + nodeRadius)
                canvas.drawBitmap(bitmap, null, bubbleRect, null)
                canvas.restore()
                
                nodePaint.style = Paint.Style.STROKE
                nodePaint.strokeWidth = 4f
                nodePaint.color = node.color ?: Color.LTGRAY
                canvas.drawCircle(node.x, node.y, nodeRadius, nodePaint)
            } else {
                nodePaint.style = Paint.Style.FILL
                nodePaint.color = node.color ?: Color.LTGRAY
                canvas.drawCircle(node.x, node.y, nodeRadius, nodePaint)
            }

            textPaint.color = ColorHelper.getTextColor(context)
            canvas.drawText(node.name, node.x, node.y + nodeRadius + 20f, textPaint)
        }

        canvas.restore()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            performClick()
        }
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val worldX = (event.x - offsetX) / scaleFactor
        val worldY = (event.y - offsetY) / scaleFactor

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val node = findNodeAt(worldX, worldY)
                if (node != null) {
                    draggedNodeId = node.id
                    lastTouchX = worldX
                    lastTouchY = worldY
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                draggedNodeId?.let { id ->
                    val node = data.nodes.find { it.id == id }
                    if (node != null) {
                        node.x += (worldX - lastTouchX)
                        node.y += (worldY - lastTouchY)
                        lastTouchX = worldX
                        lastTouchY = worldY
                        invalidate()
                        onDataChanged?.invoke()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggedNodeId != null) {
                    onDataChanged?.invoke()
                }
                draggedNodeId = null
            }
        }
        return true
    }

    private fun findNodeAt(x: Float, y: Float): RelationNode? {
        return data.nodes.find {
            val dx = it.x - x
            val dy = it.y - y
            sqrt(dx * dx + dy * dy) <= nodeRadius * 1.5f
        }
    }

    private fun findGroupAt(x: Float, y: Float): RelationGroup? {
        data.groups.forEach { group ->
            val memberNodes = data.nodes.filter { group.nodeIds.contains(it.id) }
            if (memberNodes.isNotEmpty()) {
                if (memberNodes.size == 1) {
                    val dx = memberNodes[0].x - x
                    val dy = memberNodes[0].y - y
                    if (sqrt(dx * dx + dy * dy) <= nodeRadius * 2.5f) return group
                } else {
                    val minX = memberNodes.minOf { it.x } - nodeRadius * 1.5f
                    val maxX = memberNodes.maxOf { it.x } + nodeRadius * 1.5f
                    val minY = memberNodes.minOf { it.y } - nodeRadius * 1.5f
                    val maxY = memberNodes.maxOf { it.y } + nodeRadius * 1.5f
                    if (x in minX..maxX && y in minY..maxY) return group
                }
            }
        }
        return null
    }
}
