package com.interli.plural

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlin.math.cos
import kotlin.math.sin

class CoFrontingGraphView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var nodes: List<Node> = listOf()
    private var edges: List<Edge> = listOf()
    private val nodePositions = mutableListOf<PointF>()
    private val nodeRadius = 50f
    private val bitmaps = mutableMapOf<String, Bitmap>()
    
    private var selectedNodeIndex: Int = -1
    
    var onNodeClicked: ((String) -> Unit)? = null

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    data class Node(val name: String, val color: Int, val imageUri: String?)
    data class Edge(val node1: Int, val node2: Int, val weight: Int)

    fun setData(people: List<Person>, coFrontingCounts: Map<Pair<String, String>, Int>) {
        val activeIds = mutableSetOf<String>()
        coFrontingCounts.keys.forEach {
            activeIds.add(it.first)
            activeIds.add(it.second)
        }
        
        val filteredPeople = people.filter { activeIds.contains(it.id) || activeIds.contains(it.name) }
        nodes = filteredPeople.map { Node(it.name, it.profileColor, it.profilePictureUri) }
        
        val idToIndex = filteredPeople.withIndex().associate { it.value.id to it.index }
        val nameToIndexMap = filteredPeople.withIndex().associate { it.value.name to it.index }
        
        edges = coFrontingCounts.mapNotNull { (pair, count) ->
            val idx1 = idToIndex[pair.first] ?: nameToIndexMap[pair.first]
            val idx2 = idToIndex[pair.second] ?: nameToIndexMap[pair.second]
            if (idx1 != null && idx2 != null) {
                Edge(idx1, idx2, count)
            } else null
        }
        
        selectedNodeIndex = -1
        preloadImages()
        invalidate()
    }

    private fun preloadImages() {
        nodes.forEach { node ->
            if (node.imageUri != null && !bitmaps.containsKey(node.imageUri)) {
                val request = ImageRequest.Builder(context)
                    .data(node.imageUri)
                    .target { result ->
                        val bitmap = try { result.toBitmap() } catch (_: Exception) { null }
                        if (bitmap != null) {
                            bitmaps[node.imageUri] = bitmap
                            invalidate()
                        }
                    }
                    .build()
                context.imageLoader.enqueue(request)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, width)
    }

    private fun isConnected(nodeIdx: Int): Boolean {
        if (selectedNodeIndex == -1) return true
        if (nodeIdx == selectedNodeIndex) return true
        return edges.any { 
            (it.node1 == selectedNodeIndex && it.node2 == nodeIdx) || 
            (it.node2 == selectedNodeIndex && it.node1 == nodeIdx) 
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (nodes.isEmpty()) return

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (minOf(width, height) / 2f) * 0.7f

        nodePositions.clear()
        for (i in nodes.indices) {
            val angle = i * 2.0 * Math.PI / nodes.size
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            nodePositions.add(PointF(x, y))
        }

        val maxWeight = edges.maxOfOrNull { it.weight }?.toFloat() ?: 1f
        
        edges.forEach { edge ->
            val p1 = nodePositions[edge.node1]
            val p2 = nodePositions[edge.node2]
            
            val isHighlighted = selectedNodeIndex == -1 || edge.node1 == selectedNodeIndex || edge.node2 == selectedNodeIndex
            
            if (isHighlighted) {
                edgePaint.alpha = 180
                val color1 = nodes[edge.node1].color
                val color2 = nodes[edge.node2].color
                edgePaint.shader = LinearGradient(p1.x, p1.y, p2.x, p2.y, color1, color2, Shader.TileMode.CLAMP)
            } else {
                edgePaint.shader = null
                edgePaint.color = Color.LTGRAY
                edgePaint.alpha = 40
            }
            
            edgePaint.strokeWidth = 4f + (edge.weight / maxWeight) * 20f
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, edgePaint)
        }
        edgePaint.shader = null

        val defaultTextColor = ColorHelper.getTextColor(context)
        
        nodes.forEachIndexed { i, node ->
            val pos = nodePositions[i]
            val isNodeActive = isConnected(i)
            val nodeAlpha = if (isNodeActive) 255 else 60
            
            val bitmap = node.imageUri?.let { bitmaps[it] }
            if (bitmap != null) {
                val path = Path().apply {
                    addCircle(pos.x, pos.y, nodeRadius, Path.Direction.CCW)
                }
                canvas.save()
                canvas.clipPath(path)
                val destRect = RectF(pos.x - nodeRadius, pos.y - nodeRadius, pos.x + nodeRadius, pos.y + nodeRadius)
                imagePaint.alpha = nodeAlpha
                canvas.drawBitmap(bitmap, null, destRect, imagePaint)
                canvas.restore()
                
                nodePaint.style = Paint.Style.STROKE
                nodePaint.strokeWidth = if (i == selectedNodeIndex) 8f else 4f
                nodePaint.color = if (isNodeActive) node.color else Color.LTGRAY
                nodePaint.alpha = nodeAlpha
                canvas.drawCircle(pos.x, pos.y, nodeRadius, nodePaint)
            } else {
                nodePaint.style = Paint.Style.FILL
                nodePaint.color = if (isNodeActive) node.color else Color.LTGRAY
                nodePaint.alpha = nodeAlpha
                canvas.drawCircle(pos.x, pos.y, nodeRadius, nodePaint)
                if (i == selectedNodeIndex) {
                    nodePaint.style = Paint.Style.STROKE
                    nodePaint.strokeWidth = 4f
                    nodePaint.color = Color.BLACK
                    canvas.drawCircle(pos.x, pos.y, nodeRadius, nodePaint)
                }
            }
            
            textPaint.color = if (isNodeActive) defaultTextColor else Color.LTGRAY
            textPaint.alpha = nodeAlpha
            
            val label = node.name
            val textWidth = textPaint.measureText(label)
            val angle = i * 2.0 * Math.PI / nodes.size
            val labelRadius = radius + nodeRadius + 50f
            val lx = centerX + labelRadius * cos(angle).toFloat()
            val ly = centerY + labelRadius * sin(angle).toFloat()

            labelBgPaint.alpha = if (isNodeActive) 180 else 40
            val rect = RectF(
                lx - textWidth / 2 - 12,
                ly + textPaint.fontMetrics.ascent - 6,
                lx + textWidth / 2 + 12,
                ly + textPaint.fontMetrics.descent + 6
            )
            canvas.drawRoundRect(rect, 12f, 12f, labelBgPaint)
            canvas.drawText(label, lx, ly - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            
            nodePositions.forEachIndexed { index, pos ->
                val dx = x - pos.x
                val dy = y - pos.y
                if (dx * dx + dy * dy <= (nodeRadius * 1.5) * (nodeRadius * 1.5)) {
                    selectedNodeIndex = if (selectedNodeIndex == index) -1 else index
                    invalidate()
                    performClick()
                    onNodeClicked?.invoke(nodes[index].name)
                    return true
                }
            }
            if (selectedNodeIndex != -1) {
                selectedNodeIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
