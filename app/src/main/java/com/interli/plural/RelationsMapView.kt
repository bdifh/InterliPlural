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
    
    private var offsetX = 100f
    private var offsetY = 100f
    private var scaleFactor = 0.7f

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
        textSize = 36f
        color = Color.BLACK
        typeface = Typeface.DEFAULT_BOLD
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 36f
        color = Color.DKGRAY
    }
    private val noteIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.YELLOW
        alpha = 180
    }
    private val noteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.BLACK
        alpha = 150
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
                } else {
                    val clickedEdge = findEdgeAt(worldX, worldY)
                    if (clickedEdge != null) {
                        onEdgeLongClicked?.invoke(clickedEdge)
                    }
                }
            }
        }
    })

    private val smartLayoutRunnable = object : Runnable {
        override fun run() {
            if (data.smartLayoutEnabled) {
                for (i in 0 until 50) {
                    applySmartLayout()
                }
                invalidate()
                postDelayed(this, 100) 
            }
        }
    }

    private fun applySmartLayout() {
        val repulsionConstant = 1500f
        val minDistance = nodeRadius * 3.5f
        val damping = 0.2f

        val nodes = data.nodes
        val nodeCount = nodes.size

        for (i in 0 until nodeCount) {
            val nodeA = nodes[i]
            if (nodeA.id == draggedNodeId) continue

            var forceX = 0f
            var forceY = 0f

            for (j in 0 until nodeCount) {
                if (i == j) continue
                val nodeB = nodes[j]
                
                val dx = nodeA.x - nodeB.x
                val dy = nodeA.y - nodeB.y
                val distanceSq = dx * dx + dy * dy
                
                if (distanceSq < minDistance * minDistance) {
                    val distance = sqrt(distanceSq)
                    val force = repulsionConstant / (distanceSq + 500f)
                    forceX += (dx / (distance + 0.1f)) * force
                    forceY += (dy / (distance + 0.1f)) * force
                }
            }
            
            nodeA.x += forceX * damping
            nodeA.y += forceY * damping
        }
    }

    var onNodeLongClicked: ((RelationNode) -> Unit)? = null
    var onGroupLongClicked: ((RelationGroup) -> Unit)? = null
    var onEdgeLongClicked: ((RelationEdge) -> Unit)? = null
    var onDataChanged: (() -> Unit)? = null

    fun setData(newData: RelationsData) {
        data = newData
        preloadImages()
        removeCallbacks(smartLayoutRunnable)
        if (data.smartLayoutEnabled) {
            post(smartLayoutRunnable)
        }
        invalidate()
    }

    private fun getGroupCenter(group: RelationGroup): PointF? {
        val memberNodes = data.nodes.filter { group.nodeIds.contains(it.id) }
        return if (memberNodes.isNotEmpty()) {
            PointF(memberNodes.map { it.x }.average().toFloat(), memberNodes.map { it.y }.average().toFloat())
        } else {
            null
        }
    }

    private fun getGroupBoundaryPoint(center: PointF, target: PointF, group: RelationGroup): PointF {
        val memberNodes = data.nodes.filter { group.nodeIds.contains(it.id) }
        if (memberNodes.isEmpty()) return center

        val dx = target.x - center.x
        val dy = target.y - center.y
        if (Math.abs(dx) < 0.01f && Math.abs(dy) < 0.01f) return center

        if (memberNodes.size == 1) {
            val radius = nodeRadius * 2.5f
            val angle = Math.atan2(dy.toDouble(), dx.toDouble())
            return PointF(
                (center.x + radius * Math.cos(angle)).toFloat(),
                (center.y + radius * Math.sin(angle)).toFloat()
            )
        } else {
            val minX = memberNodes.minOf { it.x } - nodeRadius * 1.5f
            val maxX = memberNodes.maxOf { it.x } + nodeRadius * 1.5f
            val minY = memberNodes.minOf { it.y } - nodeRadius * 1.5f
            val maxY = memberNodes.maxOf { it.y } + nodeRadius * 1.5f

            val tX = if (dx > 0) (maxX - center.x) / dx else if (dx < 0) (minX - center.x) / dx else Float.MAX_VALUE
            val tY = if (dy > 0) (maxY - center.y) / dy else if (dy < 0) (minY - center.y) / dy else Float.MAX_VALUE

            val t = Math.min(tX, tY).coerceIn(0f, 1f)

            val bx = center.x + t * dx
            val by = center.y + t * dy

            val angle = Math.atan2(dy.toDouble(), dx.toDouble())
            return PointF(
                (bx + 5f * Math.cos(angle)).toFloat(),
                (by + 5f * Math.sin(angle)).toFloat()
            )
        }
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
            groupPaint.color = group.color
            
            if (memberNodes.isNotEmpty()) {
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
            val points = mutableListOf<PointF>()
            
            edge.getSafeNodeIds().forEach { id ->
                data.nodes.find { it.id == id }?.let { points.add(PointF(it.x, it.y)) }
            }
            
            edge.groupIds.forEach { id ->
                data.groups.find { it.id == id }?.let { group ->
                    getGroupCenter(group)?.let { points.add(it) }
                }
            }
            
            if (points.size >= 2) {
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i+1]
                    
                    var startX = p1.x
                    var startY = p1.y
                    var endX = p2.x
                    var endY = p2.y

                    val group1 = data.groups.find { g -> getGroupCenter(g)?.let { Math.abs(it.x - p1.x) < 5f && Math.abs(it.y - p1.y) < 5f } ?: false }
                    if (group1 != null) {
                        val boundary = getGroupBoundaryPoint(p1, p2, group1)
                        startX = boundary.x
                        startY = boundary.y
                    } else {
                        val node1 = data.nodes.find { Math.abs(it.x - p1.x) < 5f && Math.abs(it.y - p1.y) < 5f }
                        if (node1 != null) {
                            val angle = Math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                            startX += (nodeRadius * Math.cos(angle)).toFloat()
                            startY += (nodeRadius * Math.sin(angle)).toFloat()
                        }
                    }
                    
                    val group2 = data.groups.find { g -> getGroupCenter(g)?.let { Math.abs(it.x - p2.x) < 5f && Math.abs(it.y - p2.y) < 5f } ?: false }
                    if (group2 != null) {
                        val boundary = getGroupBoundaryPoint(p2, p1, group2)
                        endX = boundary.x
                        endY = boundary.y
                    } else {
                        val node2 = data.nodes.find { Math.abs(it.x - p2.x) < 5f && Math.abs(it.y - p2.y) < 5f }
                        if (node2 != null) {
                            val angle = Math.atan2((p1.y - p2.y).toDouble(), (p1.x - p2.x).toDouble())
                            endX += (nodeRadius * Math.cos(angle)).toFloat()
                            endY += (nodeRadius * Math.sin(angle)).toFloat()
                        }
                    }

                    canvas.drawLine(startX, startY, endX, endY, edgePaint)
                }

                edge.tag?.let { tag ->
                    val midX = points.map { it.x }.average().toFloat()
                    val midY = points.map { it.y }.average().toFloat()
                    tagPaint.color = ColorHelper.getTextColor(context)
                    canvas.drawText(tag, midX, midY - 10f, tagPaint)
                }

                if (!edge.note.isNullOrBlank()) {
                    val midX = points.map { it.x }.average().toFloat()
                    val midY = points.map { it.y }.average().toFloat()
                    val size = 15f
                    canvas.drawRect(midX - size, midY + 10f, midX + size, midY + 40f, noteIconPaint)
                    canvas.drawRect(midX - size, midY + 10f, midX + size, midY + 40f, noteStrokePaint)
                    canvas.drawLine(midX - size + 5f, midY + 20f, midX + size - 5f, midY + 20f, noteStrokePaint)
                    canvas.drawLine(midX - size + 5f, midY + 30f, midX + size - 5f, midY + 30f, noteStrokePaint)
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

                val bitmapWidth = bitmap.width
                val bitmapHeight = bitmap.height
                val size = if (bitmapWidth > bitmapHeight) bitmapHeight else bitmapWidth
                val left = (bitmapWidth - size) / 2
                val top = (bitmapHeight - size) / 2
                val srcRect = Rect(left, top, left + size, top + size)
                
                bubbleRect.set(node.x - nodeRadius, node.y - nodeRadius, node.x + nodeRadius, node.y + nodeRadius)
                canvas.drawBitmap(bitmap, srcRect, bubbleRect, null)
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
            canvas.drawText(node.name, node.x, node.y + nodeRadius + 30f, textPaint)
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

    private fun findEdgeAt(x: Float, y: Float): RelationEdge? {
        data.edges.forEach { edge ->
            val points = mutableListOf<PointF>()
            edge.getSafeNodeIds().forEach { id ->
                data.nodes.find { it.id == id }?.let { points.add(PointF(it.x, it.y)) }
            }
            edge.groupIds.forEach { id ->
                data.groups.find { it.id == id }?.let { group ->
                    getGroupCenter(group)?.let { points.add(it) }
                }
            }
            if (points.size >= 2) {
                val midX = points.map { it.x }.average().toFloat()
                val midY = points.map { it.y }.average().toFloat()
                if (Math.abs(x - midX) < 40f && Math.abs(y - midY) < 40f) {
                    return edge
                }
            }
        }
        return null
    }

    private fun findGroupAt(x: Float, y: Float): RelationGroup? {
        return data.groups.find { group ->
            val memberNodes = data.nodes.filter { group.nodeIds.contains(it.id) }
            if (memberNodes.isEmpty()) return@find false
            
            if (memberNodes.size == 1) {
                val node = memberNodes[0]
                val dx = node.x - x
                val dy = node.y - y
                sqrt(dx * dx + dy * dy) <= nodeRadius * 2.5f
            } else {
                val minX = memberNodes.minOf { it.x } - nodeRadius * 1.5f
                val maxX = memberNodes.maxOf { it.x } + nodeRadius * 1.5f
                val minY = memberNodes.minOf { it.y } - nodeRadius * 1.5f
                val maxY = memberNodes.maxOf { it.y } + nodeRadius * 1.5f
                x in minX..maxX && y in minY..maxY
            }
        }
    }

    fun captureFullMapBitmap(): Bitmap? {
        if (data.nodes.isEmpty()) return null
        
        val padding = 100f
        val minX = (data.nodes.minOf { it.x } - nodeRadius * 3).coerceAtMost(0f)
        val maxX = (data.nodes.maxOf { it.x } + nodeRadius * 3)
        val minY = (data.nodes.minOf { it.y } - nodeRadius * 4).coerceAtMost(0f)
        val maxY = (data.nodes.maxOf { it.y } + nodeRadius * 4)
        
        val width = (maxX - minX + padding * 2).toInt()
        val height = (maxY - minY + padding * 2).toInt()

        val maxDim = 4000
        var scale = 1.0f
        if (width > maxDim || height > maxDim) {
            scale = maxDim.toFloat() / if (width > height) width else height
        }
        
        val bitmap = Bitmap.createBitmap((width * scale).toInt(), (height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ColorHelper.getBgColor(context))
        canvas.scale(scale, scale)
        canvas.translate(-minX + padding, -minY + padding)

        val oldOffsetX = offsetX
        val oldOffsetY = offsetY
        val oldScale = scaleFactor
        
        offsetX = 0f
        offsetY = 0f
        scaleFactor = 1.0f
        
        draw(canvas)
        
        offsetX = oldOffsetX
        offsetY = oldOffsetY
        scaleFactor = oldScale
        
        return bitmap
    }
}
