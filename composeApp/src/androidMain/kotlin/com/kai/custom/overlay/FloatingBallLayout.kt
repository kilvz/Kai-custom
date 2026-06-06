package com.kai.custom.overlay

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

internal class FloatingBallLayout(context: Context) : FrameLayout(context) {

    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isExpanded = false

    private lateinit var ballView: FloatingBallView
    private lateinit var chatView: FloatingChatView

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var layoutParams: WindowManager.LayoutParams? = null

    private val density = context.resources.displayMetrics.density

    private val ballSizePx: Float by lazy {
        (52 * density)
    }

    // Chat panel dimensions
    private val chatWidthPx: Int by lazy { (300 * density).toInt() }
    private val chatHeightPx: Int by lazy {
        // ~55% of screen height, capped at 420dp
        val maxDp = 420
        val screenHeightDp = context.resources.displayMetrics.heightPixels / density
        val targetDp = (screenHeightDp * 0.55f).coerceAtMost(maxDp.toFloat())
        (targetDp * density).toInt()
    }

    fun init(
        chatController: OverlayChatController,
        params: WindowManager.LayoutParams,
    ) {
        this.layoutParams = params
        Log.d("Kai_Ball", "init() ballSizePx=$ballSizePx")

        ballView = FloatingBallView(context, ballSizePx).apply {
            setOnTouchListener { v, event ->
                Log.d("Kai_Ball", "onTouch action=${event.action} raw=(${event.rawX},${event.rawY})")
                onBallTouch(v, event)
            }
        }
        addView(ballView)

        chatView = FloatingChatView(context, chatController, onClose = { collapse() }).apply {
            visibility = View.GONE
        }
        addView(chatView, LayoutParams(chatWidthPx, chatHeightPx))
    }

    fun updateLayoutParams(params: WindowManager.LayoutParams) {
        this.layoutParams = params
    }

    private fun onBallTouch(v: View, event: MotionEvent): Boolean {
        val params = layoutParams ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x.toFloat()
                initialY = params.y.toFloat()
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                    isDragging = true
                    params.x = (initialX + dx).toInt()
                    params.y = (initialY + dy).toInt()
                    clampToScreen(params)
                    windowManager.updateViewLayout(this, params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    Log.d("Kai_Ball", "tap detected, toggling expand")
                    v.performClick()
                    toggleExpand()
                }
                return true
            }
        }
        return false
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        Log.d("Kai_Ball", "toggleExpand isExpanded=$isExpanded")
        ballView.visibility = if (isExpanded) View.GONE else View.VISIBLE
        chatView.visibility = if (isExpanded) View.VISIBLE else View.GONE

        val params = layoutParams ?: run { Log.d("Kai_Ball", "toggleExpand: layoutParams=null"); return }
        if (isExpanded) {
            params.width = chatWidthPx
            params.height = chatHeightPx
            // Position: right-aligned with margin, vertically centered
            val screenW = context.resources.displayMetrics.widthPixels
            val screenH = context.resources.displayMetrics.heightPixels
            params.x = screenW - chatWidthPx - (12 * density).toInt()
            params.y = (screenH - chatHeightPx) / 2
            params.gravity = Gravity.TOP or Gravity.START
            // Allow focus so EditText can receive keyboard input
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = (initialX.toInt()).coerceAtLeast(0)
            params.y = (initialY.toInt()).coerceAtLeast(0)
            // Restore not-focusable so the ball doesn't steal focus from other apps
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            windowManager.updateViewLayout(this, params)
            Log.d("Kai_Ball", "updateViewLayout OK")
        } catch (e: Exception) {
            Log.e("Kai_Ball", "updateViewLayout failed", e)
        }
    }

    fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        ballView.visibility = View.VISIBLE
        chatView.visibility = View.GONE

        val params = layoutParams ?: return
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.x = (initialX.toInt()).coerceAtLeast(0)
        params.y = (initialY.toInt()).coerceAtLeast(0)
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager.updateViewLayout(this, params)
    }

    private fun clampToScreen(params: WindowManager.LayoutParams) {
        val displayMetrics = context.resources.displayMetrics
        val ballSize = ballSizePx.toInt()
        params.x = params.x.coerceIn(0, displayMetrics.widthPixels - ballSize)
        params.y = params.y.coerceIn(0, displayMetrics.heightPixels - ballSize)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }
}
