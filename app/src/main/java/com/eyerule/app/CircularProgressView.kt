package com.eyerule.app

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * A simple ring: a full dim "track" circle plus a colored arc on top showing
 * progress (0f..1f), drawn clockwise starting at the top. No XML attrs needed -
 * colors and stroke width are set from code via the public properties below.
 */
class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var trackColor: Int = 0
        set(value) { field = value; trackPaint.color = value; invalidate() }

    var progressColor: Int = 0
        set(value) { field = value; progressPaint.color = value; invalidate() }

    var ringStrokeWidthPx: Float = 18f
        set(value) {
            field = value
            trackPaint.strokeWidth = value
            progressPaint.strokeWidth = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val arcRect = RectF()

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)

        val inset = ringStrokeWidthPx / 2f + paddingLeft
        arcRect.set(inset, inset, width - inset, height - inset)

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        if (progress > 0f) {
            canvas.drawArc(arcRect, -90f, 360f * progress, false, progressPaint)
        }
    }
}
