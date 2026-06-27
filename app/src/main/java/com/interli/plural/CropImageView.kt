package com.interli.plural

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import coil.Coil
import coil.request.ImageRequest
import coil.target.Target

class CropImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private val mainMatrix = Matrix()
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#AA000000")
        style = Paint.Style.FILL
    }
    private val overlayPath = Path()
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    private val cropRect = RectF()
    private val imageRect = RectF()
    
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            mainMatrix.postScale(scale, scale, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            mainMatrix.postTranslate(-distanceX, -distanceY)
            invalidate()
            return true
        }
    })

    fun setImageUri(uri: Uri) {
        val loader = Coil.imageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .target { result ->
                sourceBitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (sourceBitmap != null) {
                    imageRect.set(0f, 0f, sourceBitmap!!.width.toFloat(), sourceBitmap!!.height.toFloat())
                    post {
                        centerImage()
                        invalidate()
                    }
                }
            }
            .build()
        loader.enqueue(request)
    }

    private fun centerImage() {
        val bitmap = sourceBitmap ?: return
        mainMatrix.reset()
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val bitWidth = bitmap.width.toFloat()
        val bitHeight = bitmap.height.toFloat()
        
        val scale = maxOf(viewWidth / bitWidth, viewHeight / bitHeight)
        mainMatrix.postScale(scale, scale)
        
        val tx = (viewWidth - bitWidth * scale) / 2f
        val ty = (viewHeight - bitHeight * scale) / 2f
        mainMatrix.postTranslate(tx, ty)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val size = minOf(viewWidth, viewHeight) * 0.8f
        cropRect.set(
            (viewWidth - size) / 2f,
            (viewHeight - size) / 2f,
            (viewWidth + size) / 2f,
            (viewHeight + size) / 2f
        )
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = sourceBitmap ?: return

        canvas.drawBitmap(bitmap, mainMatrix, null)

        overlayPath.reset()
        overlayPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        overlayPath.addRect(cropRect, Path.Direction.CCW)
        canvas.drawPath(overlayPath, overlayPaint)

        canvas.drawRect(cropRect, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    fun getCroppedBitmap(): Bitmap? {
        val bitmap = sourceBitmap ?: return null

        val inverse = Matrix()
        mainMatrix.invert(inverse)
        
        val mappedCropRect = RectF()
        inverse.mapRect(mappedCropRect, cropRect)

        val left = maxOf(0, mappedCropRect.left.toInt())
        val top = maxOf(0, mappedCropRect.top.toInt())
        val width = minOf(bitmap.width - left, mappedCropRect.width().toInt())
        val height = minOf(bitmap.height - top, mappedCropRect.height().toInt())
        
        if (width <= 0 || height <= 0) return null
        
        return try {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
