package com.yunlu.salesman.ui.recordOrder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.sqrt

class TouchImageView : AppCompatImageView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val mMatrix = Matrix()
    private val savedMatrix = Matrix()

    companion object{
        // 触摸状态
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
    private var mode = NONE

    // 缩放相关
    private val start = PointF()
    private val mid = PointF()
    private var lastEvent: FloatArray? = null
    private var minScale = 1.0f
    private var maxScale = 4.0f
    private var currentScale = 1.0f

    // 缩放检测器
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageBitmap(bm: Bitmap) {
        super.setImageBitmap(bm)
        setupInitialPosition(bm)
    }

    private fun setupInitialPosition(bitmap: Bitmap) {
        // 等待视图完成布局
        post {
            // 计算合适的缩放比例（适应视图宽度）
            val scale = width.toFloat() / bitmap.width.toFloat()

            // 创建初始变换矩阵
            val matrix = Matrix().apply {
                // 应用缩放
                postScale(scale, scale)

                // 计算居中偏移量
                val offsetX = (width - bitmap.width * scale) / 2
                val offsetY = (height - bitmap.height * scale) / 2

                // 应用偏移使图片居中
                postTranslate(offsetX, offsetY)
            }

            // 应用初始变换
            imageMatrix = matrix

            // 设置最小缩放比例
            minScale = scale
            currentScale = scale
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(mMatrix)
                start.set(event.x, event.y)
                mode = DRAG
                lastEvent = null
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val spacing = getSpacing(event)
                if (spacing > 10f) {
                    savedMatrix.set(mMatrix)
                    midPoint(mid, event)
                    mode = ZOOM
                }
                lastEvent = FloatArray(4)
                lastEvent?.apply {
                    this[0] = event.getX(0)
                    this[1] = event.getX(1)
                    this[2] = event.getY(0)
                    this[3] = event.getY(1)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    mMatrix.set(savedMatrix)
                    mMatrix.postTranslate(event.x - start.x, event.y - start.y)
                } else if (mode == ZOOM && lastEvent != null && event.pointerCount == 2) {
                    val newSpacing = getSpacing(event)
                    mMatrix.set(savedMatrix)
                    if (newSpacing > 10f) {
                        val scale = newSpacing / getSpacing(lastEvent!!)
                        mMatrix.postScale(scale, scale, mid.x, mid.y)
                    }
                }
                applyConstraints()
                imageMatrix = mMatrix
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                lastEvent = null
            }
        }
        return true
    }

    private fun getSpacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt(x * x + y * y)
    }

    private fun getSpacing(points: FloatArray): Float {
        val x = points[0] - points[1]
        val y = points[2] - points[3]
        return sqrt(x * x + y * y)
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = (event.getX(0) + event.getX(1)) / 2
        val y = (event.getY(0) + event.getY(1)) / 2
        point.set(x, y)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            currentScale *= scaleFactor
            currentScale = currentScale.coerceIn(minScale, maxScale)

            mMatrix.set(savedMatrix)
            mMatrix.postScale(
                currentScale,
                currentScale,
                detector.focusX,
                detector.focusY
            )

            applyConstraints()
            imageMatrix = mMatrix
            return true
        }
    }

    private fun applyConstraints() {
        // 边界约束实现（防止图片移出视图）
        val rect = FloatArray(9)
        mMatrix.getValues(rect)

        val transX = rect[Matrix.MTRANS_X]
        val transY = rect[Matrix.MTRANS_Y]
        val scaleX = rect[Matrix.MSCALE_X]
        val scaleY = rect[Matrix.MSCALE_Y]

        val width = if (drawable != null) drawable.intrinsicWidth * scaleX else 0f
        val height = if (drawable != null) drawable.intrinsicHeight * scaleY else 0f

        val offsetX = when {
            width < this.width -> (this.width - width) / 2
            else -> transX.coerceIn(this.width - width, 0f)
        }

        val offsetY = when {
            height < this.height -> (this.height - height) / 2
            else -> transY.coerceIn(this.height - height, 0f)
        }

        mMatrix.postTranslate(offsetX - transX, offsetY - transY)
    }
}