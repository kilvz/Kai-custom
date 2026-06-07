package com.kai.custom.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class FloatingChatView(
    context: Context,
    private val controller: OverlayChatController,
    private val onClose: () -> Unit = {},
    private val onHeaderDrag: ((dx: Float, dy: Float) -> Unit)? = null,
    private val onHeaderDragEnd: (() -> Unit)? = null,
) : FrameLayout(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val messagesContainer: LinearLayout
    private val messagesScroll: ScrollView
    private val sendButton: TextView
    private val inputField: EditText
    private val loadingRow: LinearLayout

    private val density = context.resources.displayMetrics.density

    private val surfaceBg = Color.parseColor("#1E1E1E")
    private val headerStart = Color.parseColor("#1565C0")
    private val headerEnd = Color.parseColor("#1E88E5")
    private val accentBlue = Color.parseColor("#90CAF9")
    private val inputBgColor = Color.parseColor("#2A2A2A")
    private val textPrimary = Color.parseColor("#FFFFFF")
    private val textSecondary = Color.parseColor("#9E9E9E")
    private val userBubble = Color.parseColor("#1565C0")
    private val assistantBubble = Color.parseColor("#2A2A2A")
    private val cornerRadiusPx = (20 * density)

    init {
        val mp = LinearLayout.LayoutParams.MATCH_PARENT
        val wc = LinearLayout.LayoutParams.WRAP_CONTENT

        val outerCard = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(mp, mp)
            orientation = LinearLayout.VERTICAL
            val cardBg = GradientDrawable().apply {
                setColor(surfaceBg)
                cornerRadius = cornerRadiusPx
                setStroke((1 * density).toInt(), Color.parseColor("#333333"))
            }
            background = cardBg
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                }
            }
            elevation = 12 * density
        }
        addView(outerCard)

        // ── Header ──
        val header = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(mp, wc)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val headerBg = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(headerStart, headerEnd),
            )
            headerBg.cornerRadii = floatArrayOf(
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                cornerRadiusPx,
                0f,
                0f,
                0f,
                0f,
            )
            background = headerBg
            setPadding(dp(14), dp(12), dp(10), dp(12))
        }
        var headerDragStartRawX = 0f
        var headerDragStartRawY = 0f
        header.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    headerDragStartRawX = event.rawX
                    headerDragStartRawY = event.rawY
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - headerDragStartRawX
                    val dy = event.rawY - headerDragStartRawY
                    if (Math.abs(dx) > 5f || Math.abs(dy) > 5f) {
                        onHeaderDrag?.invoke(dx, dy)
                        headerDragStartRawX = event.rawX
                        headerDragStartRawY = event.rawY
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    onHeaderDragEnd?.invoke()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    onHeaderDragEnd?.invoke()
                    true
                }

                else -> false
            }
        }
        outerCard.addView(header)

        // Kai icon
        val iconSize = dp(28)
        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                setMargins(0, 0, dp(10), 0)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
            setOnClickListener {
                controller.hide()
                onClose()
            }
        }
        try {
            val iconRes = context.resources.getIdentifier("ic_launcher", "drawable", context.packageName)
            if (iconRes != 0) iconView.setImageResource(iconRes)
        } catch (_: Exception) {}
        header.addView(iconView)

        TextView(context).apply {
            text = controller.personaName
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, wc, 1f)
        }.also { header.addView(it) }

        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }.also { header.addView(it) }

        // ── Messages area (pure Views) ──
        messagesScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(mp, 0, 1f)
            isVerticalScrollBarEnabled = true
        }
        messagesContainer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(mp, wc)
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        messagesScroll.addView(messagesContainer)
        outerCard.addView(messagesScroll)

        // ── Empty state ──
        val emptyText = TextView(context).apply {
            text = "Ask about anything\non your screen"
            setTextColor(textSecondary)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(mp, dp(120))
        }
        messagesContainer.addView(emptyText)

        // ── Loading indicator ──
        loadingRow = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(mp, wc).apply {
                setMargins(dp(10), dp(2), dp(10), dp(4))
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        val loadingBubble = TextView(context).apply {
            text = "Thinking\u2026"
            setTextColor(textSecondary)
            textSize = 12f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.ITALIC)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2A2A"))
                cornerRadius = dp(14).toFloat()
            }
            background = bg
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        loadingRow.addView(loadingBubble)
        outerCard.addView(loadingRow)

        // ── Divider ──
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(mp, dp(1)).apply {
                setMargins(dp(12), 0, dp(12), 0)
            }
            setBackgroundColor(Color.parseColor("#333333"))
        }.also { outerCard.addView(it) }

        // ── Input row ──
        val inputContainer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(mp, wc)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            val bottomBg = GradientDrawable().apply {
                setColor(surfaceBg)
                cornerRadii = floatArrayOf(
                    0f,
                    0f,
                    0f,
                    0f,
                    cornerRadiusPx,
                    cornerRadiusPx,
                    cornerRadiusPx,
                    cornerRadiusPx,
                )
            }
            background = bottomBg
        }
        outerCard.addView(inputContainer)

        inputField = EditText(context).apply {
            hint = "Ask about your screen\u2026"
            setTextColor(textPrimary)
            setHintTextColor(textSecondary)
            textSize = 14f
            maxLines = 3
            layoutParams = LinearLayout.LayoutParams(0, wc, 1f).apply {
                setMargins(0, 0, dp(8), 0)
            }
            val inputBg = GradientDrawable().apply {
                setColor(inputBgColor)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#3A3A3A"))
            }
            background = inputBg
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        inputContainer.addView(inputField)

        sendButton = TextView(context).apply {
            text = "\u2191"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            val btnSize = dp(38)
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isEnabled = false
            alpha = 0.4f
            updateSendButtonBg(this, false)
            setOnClickListener {
                val text = inputField.text.toString().trim()
                if (text.isNotEmpty()) {
                    controller.sendMessage(text)
                    inputField.setText("")
                }
            }
        }
        inputContainer.addView(sendButton)

        inputField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                sendButton.isEnabled = hasText
                sendButton.alpha = if (hasText) 1.0f else 0.4f
                updateSendButtonBg(sendButton, hasText)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ── Observe messages ──
        scope.launch {
            controller.messages.collectLatest { msgs ->
                rebuildMessages(msgs)
            }
        }
        scope.launch {
            controller.isLoading.collectLatest { loading ->
                loadingRow.visibility = if (loading) View.VISIBLE else View.GONE
                if (loading) {
                    sendButton.isEnabled = false
                    sendButton.alpha = 0.4f
                    updateSendButtonBg(sendButton, false)
                } else {
                    val hasText = inputField.text.isNotBlank()
                    sendButton.isEnabled = hasText
                    sendButton.alpha = if (hasText) 1.0f else 0.4f
                    updateSendButtonBg(sendButton, hasText)
                }
            }
        }
    }

    private fun rebuildMessages(msgs: List<ChatMessage>) {
        messagesContainer.removeAllViews()
        if (msgs.isEmpty()) {
            val emptyText = TextView(context).apply {
                text = "Ask about anything\non your screen"
                setTextColor(textSecondary)
                textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(120),
                )
            }
            messagesContainer.addView(emptyText)
            return
        }
        for (msg in msgs) {
            val isUser = msg.role == "user"
            val bubble = createBubble(msg.content, isUser)
            messagesContainer.addView(bubble)
        }
        messagesScroll.post { messagesScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun createBubble(msgText: String, isUser: Boolean): View {
        val wc = LinearLayout.LayoutParams.WRAP_CONTENT
        val lp = if (isUser) {
            LinearLayout.LayoutParams(wc, wc).apply {
                gravity = Gravity.END
                setMargins(dp(40), dp(3), 0, dp(3))
            }
        } else {
            LinearLayout.LayoutParams(wc, wc).apply {
                gravity = Gravity.START
                setMargins(0, dp(3), dp(40), dp(3))
            }
        }

        val shape = GradientDrawable().apply {
            if (isUser) {
                setColor(userBubble)
                cornerRadii = floatArrayOf(
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(4).toFloat(),
                    dp(4).toFloat(),
                )
            } else {
                setColor(assistantBubble)
                cornerRadii = floatArrayOf(
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(4).toFloat(),
                    dp(4).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                )
            }
        }

        val tv = TextView(context)
        tv.layoutParams = lp
        tv.text = msgText
        tv.setTextColor(textPrimary)
        tv.textSize = 13.5f
        tv.setPadding(dp(14), dp(9), dp(14), dp(9))
        tv.background = shape
        tv.movementMethod = ScrollingMovementMethod()
        tv.maxWidth = dp(300)
        return tv
    }

    private fun updateSendButtonBg(view: TextView, active: Boolean) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (active) {
                setColor(accentBlue)
            } else {
                setColor(Color.parseColor("#3A3A3A"))
            }
        }
        view.background = bg
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
}
