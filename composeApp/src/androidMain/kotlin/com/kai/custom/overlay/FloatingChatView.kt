package com.kai.custom.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
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
) : FrameLayout(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val messagesContainer: LinearLayout
    private val scrollView: ScrollView
    private val loadingRow: LinearLayout
    private val sendButton: TextView
    private val inputField: EditText

    private val density = context.resources.displayMetrics.density
    private val maxBubbleWidthPx = (context.resources.displayMetrics.widthPixels * 0.62f).toInt()

    // Colors
    private val surfaceBg = Color.parseColor("#1A1A2E")
    private val headerStart = Color.parseColor("#2D5BFF")
    private val headerEnd = Color.parseColor("#7B2FFF")
    private val accentBlue = Color.parseColor("#4B8BFF")
    private val inputBgColor = Color.parseColor("#2A2A45")
    private val textPrimary = Color.parseColor("#F0F0F5")
    private val textSecondary = Color.parseColor("#8888AA")
    private val userBubbleStart = Color.parseColor("#3366FF")
    private val userBubbleEnd = Color.parseColor("#7744FF")
    private val assistantBubbleBg = Color.parseColor("#2A2A45")
    private val cornerRadiusPx = (20 * density)

    init {
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wc = ViewGroup.LayoutParams.WRAP_CONTENT

        // Outer container with rounded corners + shadow
        val outerCard = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(mp, mp)
            orientation = LinearLayout.VERTICAL
            val cardBg = GradientDrawable().apply {
                setColor(surfaceBg)
                cornerRadius = cornerRadiusPx
                setStroke((1 * density).toInt(), Color.parseColor("#333355"))
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
            // Only round top corners
            headerBg.cornerRadii = floatArrayOf(
                cornerRadiusPx, cornerRadiusPx, // top-left
                cornerRadiusPx, cornerRadiusPx, // top-right
                0f, 0f, // bottom-right
                0f, 0f, // bottom-left
            )
            background = headerBg
            setPadding(dp(14), dp(12), dp(10), dp(12))
        }
        outerCard.addView(header)

        // Kai icon circle
        val iconCircle = FrameLayout(context).apply {
            val iconSize = dp(28)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                setMargins(0, 0, dp(10), 0)
            }
            val circleBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFFFFF30"))
            }
            background = circleBg
        }
        val iconLetter = TextView(context).apply {
            text = "K"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        iconCircle.addView(iconLetter)
        header.addView(iconCircle)

        // Title
        TextView(context).apply {
            text = "Kai"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, wc, 1f)
        }.also { header.addView(it) }

        // Close button
        TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FFFFFFCC"))
            textSize = 16f
            gravity = Gravity.CENTER
            val closeSize = dp(32)
            layoutParams = LinearLayout.LayoutParams(closeSize, closeSize)
            val closeBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFFFFF20"))
            }
            background = closeBg
            setOnClickListener { controller.hide(); onClose() }
        }.also { header.addView(it) }

        // ── Messages area ──
        scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(mp, 0, 1f)
            setPadding(dp(10), dp(8), dp(10), dp(4))
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        outerCard.addView(scrollView)

        messagesContainer = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(mp, wc)
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(messagesContainer)

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
            text = "Thinking…"
            setTextColor(textSecondary)
            textSize = 12f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.ITALIC)
            val bg = GradientDrawable().apply {
                setColor(assistantBubbleBg)
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
            setBackgroundColor(Color.parseColor("#333355"))
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
                    0f, 0f, 0f, 0f,
                    cornerRadiusPx, cornerRadiusPx,
                    cornerRadiusPx, cornerRadiusPx,
                )
            }
            background = bottomBg
        }
        outerCard.addView(inputContainer)

        inputField = EditText(context).apply {
            hint = "Ask about your screen…"
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
                setStroke(dp(1), Color.parseColor("#3A3A5E"))
            }
            background = inputBg
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        inputContainer.addView(inputField)

        // Send button (circle with arrow)
        sendButton = TextView(context).apply {
            text = "↑"
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

        // ── Observe state ──
        scope.launch {
            controller.messages.collectLatest { messages ->
                updateMessages(messages)
            }
        }
        scope.launch {
            controller.isLoading.collectLatest { loading ->
                loadingRow.visibility = if (loading) View.VISIBLE else View.GONE
                if (loading) {
                    sendButton.isEnabled = false
                    sendButton.alpha = 0.4f
                    updateSendButtonBg(sendButton, false)
                    scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
                } else {
                    val hasText = inputField.text.isNotBlank()
                    sendButton.isEnabled = hasText
                    sendButton.alpha = if (hasText) 1.0f else 0.4f
                    updateSendButtonBg(sendButton, hasText)
                }
            }
        }
    }

    private fun updateSendButtonBg(view: TextView, active: Boolean) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (active) {
                setColor(accentBlue)
            } else {
                setColor(Color.parseColor("#3A3A5E"))
            }
        }
        view.background = bg
    }

    private fun updateMessages(messages: List<ChatMessage>) {
        messagesContainer.removeAllViews()
        if (messages.isEmpty()) {
            val emptyContainer = LinearLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(40), dp(16), dp(40))
            }

            // Icon
            TextView(context).apply {
                text = "💬"
                textSize = 28f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, 0, 0, dp(8)) }
            }.also { emptyContainer.addView(it) }

            // Text
            TextView(context).apply {
                text = "Ask about anything\non your screen"
                setTextColor(textSecondary)
                textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }.also { emptyContainer.addView(it) }

            messagesContainer.addView(emptyContainer)
        } else {
            for (msg in messages) {
                messagesContainer.addView(createBubble(msg))
            }
        }
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun createBubble(msg: ChatMessage): View {
        val isUser = msg.role == "user"
        val wrapper = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, dp(3), 0, dp(3))
            }
        }

        val bubble = TextView(context).apply {
            text = msg.content
            setTextColor(if (isUser) Color.WHITE else textPrimary)
            textSize = 13.5f
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(9), dp(14), dp(9))

            val bg = GradientDrawable()
            val r = dp(16).toFloat()
            if (isUser) {
                bg.colors = intArrayOf(userBubbleStart, userBubbleEnd)
                bg.orientation = GradientDrawable.Orientation.TL_BR
                bg.cornerRadii = floatArrayOf(r, r, r, r, dp(4).toFloat(), dp(4).toFloat(), r, r)
            } else {
                bg.setColor(assistantBubbleBg)
                bg.cornerRadii = floatArrayOf(r, r, r, r, r, r, dp(4).toFloat(), dp(4).toFloat())
            }
            background = bg
            maxWidth = maxBubbleWidthPx

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = if (isUser) Gravity.END else Gravity.START
            }
        }
        wrapper.addView(bubble)
        return wrapper
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
}
