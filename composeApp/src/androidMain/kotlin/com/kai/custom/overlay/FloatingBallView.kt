package com.kai.custom.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import android.view.MotionEvent
import android.view.View

internal class FloatingBallView(
    context: Context,
    private val sizePx: Float,
) : View(context) {

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sizePx * 0.42f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.05f
    }

    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#402D5BFF")
    }

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.02f
        color = Color.parseColor("#55FFFFFF")
    }

    private var gradientInitialized = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = sizePx / 2f

        if (!gradientInitialized) {
            gradientPaint.shader = LinearGradient(
                cx - radius, cy - radius,
                cx + radius, cy + radius,
                intArrayOf(
                    Color.parseColor("#3366FF"),
                    Color.parseColor("#7B2FFF"),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
            gradientInitialized = true
        }

        // Outer glow
        canvas.drawCircle(cx, cy, radius * 1.12f, outerGlowPaint)

        // Main circle with gradient
        canvas.drawCircle(cx, cy, radius, gradientPaint)

        // Subtle rim highlight
        canvas.drawCircle(cx, cy, radius - rimPaint.strokeWidth / 2f, rimPaint)

        // "K" letter
        val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText("K", cx, baseline, textPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Slightly larger to accommodate glow
        val size = (sizePx * 1.2f).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d("Kai_Ball", "onTouchEvent action=${event.action} x=${event.x} y=${event.y}")
        return super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d("Kai_Ball", "ball attached w=$width h=$height")
    }
}
