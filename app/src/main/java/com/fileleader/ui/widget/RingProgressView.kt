package com.fileleader.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.fileleader.R

/**
 * Circular ring progress view for storage usage display.
 * White background, green filled arc.
 */
class RingProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress: Int = 0   // 0–100

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = ContextCompat.getColor(context, R.color.green_100)
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = ContextCompat.getColor(context, R.color.green_500)
        strokeCap = Paint.Cap.ROUND
    }

    private val rect = RectF()

    fun setProgress(value: Int) {
        progress = value.coerceIn(0, 100)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padding = trackPaint.strokeWidth / 2 + 4f
        rect.set(padding, padding, width - padding, height - padding)

        // Track (full circle)
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        // Progress arc
        val sweep = 360f * progress / 100f
        canvas.drawArc(rect, -90f, sweep, false, progressPaint)
    }
}
