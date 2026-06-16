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
    private var headerStartMarginLeft = 0
    private var headerStartMarginTop = 0
    private var headerDragTotalX = 0f
    private var headerDragTotalY = 0f
    private var isHeaderDragging = false

    private lateinit var ballView: FloatingBallView
    private lateinit var chatView: FloatingChatView
    private var dismissOverlay: View? = null

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var layoutParams: WindowManager.LayoutParams? = null

    private val density = context.resources.displayMetrics.density

    private val ballSizePx: Float by lazy {
        (40 * density)
    }

    private val chatWidthPx: Int by lazy { (300 * density).toInt() }
    private val chatHeightPx: Int by lazy {
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

        chatView = FloatingChatView(
            context,
            chatController,
            onClose = { collapse() },
            onHeaderDrag = { dx, dy ->
                if (!isHeaderDragging) {
                    val cp = chatView.layoutParams as FrameLayout.LayoutParams
                    headerStartMarginLeft = cp.leftMargin
                    headerStartMarginTop = cp.topMargin
                    headerDragTotalX = 0f
                    headerDragTotalY = 0f
                    isHeaderDragging = true
                }
                headerDragTotalX += dx
                headerDragTotalY += dy
                chatView.translationX = headerDragTotalX
                chatView.translationY = headerDragTotalY
            },
            onHeaderDragEnd = {
                if (isHeaderDragging) {
                    isHeaderDragging = false
                    val cp = chatView.layoutParams as FrameLayout.LayoutParams
                    val newLeft = (headerStartMarginLeft + headerDragTotalX.toInt())
                        .coerceIn(0, context.resources.displayMetrics.widthPixels - chatWidthPx)
                    val newTop = (headerStartMarginTop + headerDragTotalY.toInt())
                        .coerceIn(0, context.resources.displayMetrics.heightPixels - chatHeightPx)
                    cp.setMargins(newLeft, newTop, 0, 0)
                    chatView.translationX = 0f
                    chatView.translationY = 0f
                    chatView.layoutParams = cp
                }
            },
        ).apply {
            visibility = View.GONE
            isClickable = true
        }
        addView(chatView, LayoutParams(chatWidthPx, chatHeightPx))

        com.kai.custom.ScreenReaderService.onOverlaySuppress = { suppress ->
            if (!isHeaderDragging && !isDragging) {
                if (suppress) {
                    dismissOverlay?.visibility = View.GONE
                    layoutParams?.let { lp ->
                        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        try {
                            windowManager.updateViewLayout(this, lp)
                        } catch (_: Exception) {}
                    }
                } else {
                    layoutParams?.let { lp ->
                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        try {
                            windowManager.updateViewLayout(this, lp)
                        } catch (_: Exception) {}
                    }
                    dismissOverlay?.visibility = View.VISIBLE
                }
            }
        }
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

        val params = layoutParams ?: run {
            Log.d("Kai_Ball", "toggleExpand: layoutParams=null")
            return
        }
        if (isExpanded) {
            com.kai.custom.ScreenReaderService.readScreenText()
            com.kai.custom.ScreenReaderService.setOverlayActive(true)
            dismissOverlay = View(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        Log.d("Kai_Ball", "dismiss overlay tap")
                        collapse()
                        return@setOnTouchListener true
                    }
                    false
                }
            }
            addView(dismissOverlay, 0)

            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.x = 0
            params.y = 0
            params.gravity = Gravity.TOP or Gravity.START
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()

            val screenW = context.resources.displayMetrics.widthPixels
            val screenH = context.resources.displayMetrics.heightPixels
            val ballSize = ballSizePx.toInt()
            val ballCenterX = initialX.toInt() + ballSize / 2
            val ballCenterY = initialY.toInt() + ballSize / 2
            val margin = (8 * density).toInt()

            var chatLeft: Int
            var chatTop: Int

            if (ballCenterX > screenW / 2) {
                chatLeft = ballCenterX - chatWidthPx
            } else {
                chatLeft = ballCenterX
            }
            chatTop = ballCenterY - chatHeightPx / 2

            chatLeft = chatLeft.coerceIn(margin, screenW - chatWidthPx - margin)
            chatTop = chatTop.coerceIn(margin, screenH - chatHeightPx - margin)

            val chatParams = chatView.layoutParams as FrameLayout.LayoutParams
            chatParams.width = chatWidthPx
            chatParams.height = chatHeightPx
            chatParams.gravity = Gravity.TOP or Gravity.START
            chatParams.setMargins(chatLeft, chatTop, 0, 0)
            chatView.layoutParams = chatParams
        } else {
            com.kai.custom.ScreenReaderService.setOverlayActive(false)
            com.kai.custom.ScreenReaderService.clearCache()
            dismissOverlay?.let { removeView(it) }
            dismissOverlay = null

            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = (initialX.toInt()).coerceAtLeast(0)
            params.y = (initialY.toInt()).coerceAtLeast(0)
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
        com.kai.custom.ScreenReaderService.setOverlayActive(false)
        com.kai.custom.ScreenReaderService.clearCache()
        ballView.visibility = View.VISIBLE
        chatView.visibility = View.GONE

        dismissOverlay?.let { removeView(it) }
        dismissOverlay = null

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
}
