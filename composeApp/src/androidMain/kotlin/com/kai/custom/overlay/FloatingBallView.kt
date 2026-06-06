package com.kai.custom.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
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

    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#401565C0")
    }

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.02f
        color = Color.parseColor("#5590CAF9")
    }

    private var gradientInitialized = false

    private val appIcon: android.graphics.drawable.Drawable? by lazy {
        try {
            val iconRes = context.resources.getIdentifier("ic_launcher", "drawable", context.packageName)
            if (iconRes != 0) context.resources.getDrawable(iconRes, context.theme) else null
        } catch (_: Exception) {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = sizePx / 2f

        if (!gradientInitialized) {
            gradientPaint.shader = LinearGradient(
                cx - radius,
                cy - radius,
                cx + radius,
                cy + radius,
                intArrayOf(
                    Color.parseColor("#1565C0"),
                    Color.parseColor("#1E88E5"),
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

        // App icon — centered at circle center (cx, cy), sized to 64% of circle diameter
        appIcon?.let { icon ->
            val iconSize = (sizePx * 0.64f).toInt()
            icon.setBounds(
                cx.toInt() - iconSize / 2,
                cy.toInt() - iconSize / 2,
                cx.toInt() + iconSize / 2,
                cy.toInt() + iconSize / 2,
            )
            icon.draw(canvas)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
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
