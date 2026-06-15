package com.kai.custom

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.kai.custom.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.xmlpull.v1.XmlPullParser

class ScreenReaderService : AccessibilityService() {

    companion object {
        private var instance: ScreenReaderService? = null
        private var cachedScreenText: String? = null
        private var isOverlayActive = false
        private const val DEBOUNCE_MS = 800L
        private val handler = Handler(Looper.getMainLooper())
        private var refreshPending = false
        private var polling = false
        private val pollRunnable = object : Runnable {
            override fun run() {
                refreshCache()
                if (polling) handler.postDelayed(this, 1000L)
            }
        }

        fun isConnected(): Boolean = instance != null

        fun setOverlayActive(active: Boolean) {
            isOverlayActive = active
            if (active) {
                polling = false
                handler.removeCallbacks(pollRunnable)
                refreshPending = false
            } else {
                polling = true
                handler.post(pollRunnable)
            }
        }

        fun clearCache() {
            cachedScreenText = null
        }

        @Suppress("DEPRECATION")
        fun readScreenText(): String? {
            if (isOverlayActive) {
                tryReadFromWindows()?.let { return it }
                return cachedScreenText
            }
            return refreshCache()
        }

        fun readScreenTextWithFallback(): String? {
            val text = readScreenText()
            if (!text.isNullOrBlank()) return text
            return shizukuUiAutomatorDump()
        }

        suspend fun extractScrollableContent(
            direction: String = "down",
            maxScrolls: Int = 20,
        ): String? = withTimeoutOrNull(60_000L) {
            val allText = StringBuilder()
            var previousSnapshot = ""
            var staleCount = 0

            for (i in 0 until maxScrolls) {
                val text = readScreenTextWithFallback() ?: ""
                if (text.isBlank()) {
                    if (i == 0) return@withTimeoutOrNull null
                    staleCount++
                    if (staleCount >= 2) break
                    delay(300)
                    continue
                }

                // Append if significantly different from previous snapshot
                if (text.length < previousSnapshot.length * 1.1 &&
                    previousSnapshot.isNotEmpty() &&
                    text.contains(previousSnapshot.take(100))
                ) {
                    staleCount++
                    if (staleCount >= 2) break
                } else {
                    staleCount = 0
                    if (allText.isNotEmpty()) allText.appendLine("\n---\n")
                    allText.appendLine(text)
                    previousSnapshot = text
                }

                val scrolled = if (direction == "up") scrollBackward() else scrollForward()
                if (!scrolled) break
                delay(600)
            }

            val result = allText.toString().trim()
            result.ifEmpty { null }
        }

        @Suppress("DEPRECATION")
        private fun tryReadFromWindows(): String? {
            val service = instance ?: return null
            val windows = service.windows ?: return null
            for (info in windows) {
                if (info.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = info.root ?: continue
                try {
                    if (root.packageName?.toString() == "com.kai.custom") continue
                    val text = collectText(root)
                    if (text.isNotBlank()) {
                        cachedScreenText = text
                        return text
                    }
                } finally {
                    root.recycle()
                }
            }
            return null
        }

        @Suppress("DEPRECATION")
        private fun shizukuUiAutomatorDump(): String? {
            if (!ShizukuManager.isAvailable || !ShizukuManager.hasPermission) return null
            return try {
                runBlocking(Dispatchers.IO) {
                    val result = ShizukuManager.runCommand("uiautomator dump /sdcard/kai_uidump.xml", 15)
                    if (result["success"] != true) return@runBlocking null
                    val file = java.io.File("/sdcard/kai_uidump.xml")
                    if (!file.exists()) return@runBlocking null
                    val xml = file.readText()
                    file.delete()
                    parseUiDumpXml(xml)
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun parseUiDumpXml(xml: String): String {
            val sb = StringBuilder()
            val parser = android.util.Xml.newPullParser()
            parser.setInput(java.io.StringReader(xml))
            var eventType = parser.eventType
            var depth = 0
            var lines = 0
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    val text = getAttr(parser, "text")?.trim()
                    val desc = getAttr(parser, "content-desc")?.trim()
                    val cls = getAttr(parser, "class")?.trim() ?: ""

                    val tag = when {
                        cls.contains("Button", ignoreCase = true) -> "BTN"
                        cls.contains("EditText", ignoreCase = true) -> "INP"
                        cls.contains("Image", ignoreCase = true) -> "ICO"
                        cls.contains("CheckBox", ignoreCase = true) || cls.contains("Check", ignoreCase = true) -> "CHK"
                        cls.contains("Switch", ignoreCase = true) -> "SWI"
                        cls.contains("Tab", ignoreCase = true) -> "TAB"
                        else -> null
                    }

                    val label = when {
                        !text.isNullOrBlank() && !desc.isNullOrBlank() && text != desc -> "$text ($desc)"
                        !text.isNullOrBlank() -> text
                        !desc.isNullOrBlank() -> desc
                        else -> null
                    }

                    if (label != null && lines < 300) {
                        val indent = "  ".repeat(depth.coerceAtMost(4))
                        if (tag != null) {
                            sb.appendLine("$indent[$tag] $label")
                        } else {
                            sb.appendLine("$indent$label")
                        }
                        lines++
                    }
                    depth++
                } else if (eventType == XmlPullParser.END_TAG && parser.name == "node") {
                    depth--
                }
                eventType = parser.next()
            }
            return sb.toString().trim()
        }

        fun captureScreenshot(): Bitmap? {
            val service = instance ?: return null
            return try {
                // Use UiAutomation via reflection (works at runtime regardless of compileSdk)
                val getUiAutomation = service.javaClass.getMethod("getUiAutomation")
                val uiAutomation = getUiAutomation.invoke(service)
                val takeScreenshot = uiAutomation.javaClass.getMethod("takeScreenshot")
                takeScreenshot.invoke(uiAutomation) as Bitmap
            } catch (_: Exception) {
                null
            }
        }

        private fun getAttr(parser: XmlPullParser, name: String): String? {
            for (i in 0 until parser.attributeCount) {
                if (parser.getAttributeName(i) == name) {
                    return parser.getAttributeValue(i)
                }
            }
            return null
        }

        @Suppress("DEPRECATION")
        private fun refreshCache(): String? {
            if (isOverlayActive) return cachedScreenText
            val service = instance ?: return null
            val root = service.rootInActiveWindow ?: return null
            return try {
                collectText(root).also { cachedScreenText = it }
            } finally {
                root.recycle()
            }
        }

        private var collectLineCount = 0

        @Suppress("DEPRECATION")
        private fun collectText(node: AccessibilityNodeInfo): String {
            val sb = StringBuilder()
            collectLineCount = 0
            collectTextRecursive(node, sb, 0)
            return sb.toString().trim()
        }

        @Suppress("DEPRECATION")
        private fun collectTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
            // Safety: cap output at 300 lines to avoid blowing context window
            if (collectLineCount > 300) return
            val indent = "  ".repeat(depth.coerceAtMost(4))
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            val className = node.className?.toString() ?: ""

            val tag = when {
                className.contains("Button", ignoreCase = true) -> "BTN"
                className.contains("EditText", ignoreCase = true) || node.isEditable -> "INP"
                className.contains("Image", ignoreCase = true) -> "ICO"
                className.contains("CheckBox", ignoreCase = true) || className.contains("Check", ignoreCase = true) -> "CHK"
                className.contains("Switch", ignoreCase = true) -> "SWI"
                className.contains("Tab", ignoreCase = true) -> "TAB"
                className.contains("Toolbar", ignoreCase = true) || className.contains("ActionBar", ignoreCase = true) -> "BAR"
                else -> null
            }

            val label = when {
                !text.isNullOrBlank() && !desc.isNullOrBlank() && text != desc -> "$text ($desc)"
                !text.isNullOrBlank() -> text
                !desc.isNullOrBlank() -> desc
                else -> null
            }

            if (label != null) {
                if (tag != null) {
                    sb.appendLine("$indent[$tag] $label")
                } else {
                    sb.appendLine("$indent$label")
                }
                collectLineCount++
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        collectTextRecursive(child, sb, depth + 1)
                    } finally {
                        child.recycle()
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        fun clickOnText(text: String): Boolean {
            val service = instance ?: return false
            if (isOverlayActive) {
                val windows = service.windows
                if (windows != null) {
                    for (info in windows) {
                        val root = info.root ?: continue
                        try {
                            if (findAndClick(root, text.trim())) return true
                        } finally {
                            root.recycle()
                        }
                    }
                }
                return false
            }
            val root = service.rootInActiveWindow ?: return false
            return try {
                findAndClick(root, text.trim())
            } finally {
                root.recycle()
            }
        }

        @Suppress("DEPRECATION")
        private fun findAndClick(node: AccessibilityNodeInfo, text: String): Boolean {
            val nodeText = node.text?.toString()?.trim()
            val nodeDesc = node.contentDescription?.toString()?.trim()
            if (text == nodeText || text == nodeDesc || (nodeText?.contains(text, ignoreCase = true) == true)) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        if (findAndClick(child, text)) return true
                    } finally {
                        child.recycle()
                    }
                }
            }
            return false
        }

        fun clickOnCoordinates(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            if (isOverlayActive) {
                onOverlaySuppress?.invoke(true)
                Thread.sleep(150)
            }
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val result = service.dispatchGesture(gesture, null, null)
            if (isOverlayActive) {
                handler.postDelayed({ onOverlaySuppress?.invoke(false) }, 200L)
            }
            return result
        }

        // Called by FloatingBallLayout to temporarily hide dismissOverlay/re-focus
        // so dispatchGesture can reach the underlying app
        var onOverlaySuppress: ((Boolean) -> Unit)? = null

        @Suppress("DEPRECATION")
        private fun getUnderlyingRoot(): AccessibilityNodeInfo? {
            val service = instance ?: return null
            val root = service.rootInActiveWindow
            if (root != null && root.childCount > 0) return root
            root?.recycle()
            val windows = service.windows ?: return null
            for (info in windows) {
                val wroot = info.root ?: continue
                if (wroot.childCount > 0 || !wroot.text.isNullOrBlank()) return wroot
                wroot.recycle()
            }
            return null
        }

        @Suppress("DEPRECATION")
        private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findScrollableNode(child)
                if (result != null && result !== node) {
                    if (result !== child) child.recycle()
                    return result
                }
                child.recycle()
            }
            return null
        }

        fun globalAction(action: Int): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(action)
        }

        @Suppress("DEPRECATION")
        fun scrollForward(): Boolean {
            val service = instance ?: return false
            val root = getUnderlyingRoot() ?: return gestureScroll(service, forward = true)
            return try {
                val scrollable = findScrollableNode(root)
                val result = if (scrollable != null) {
                    scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                } else {
                    gestureScroll(service, forward = true)
                }
                if (scrollable !== root) scrollable?.recycle()
                result
            } finally {
                root.recycle()
            }
        }

        @Suppress("DEPRECATION")
        fun scrollBackward(): Boolean {
            val service = instance ?: return false
            val root = getUnderlyingRoot() ?: return gestureScroll(service, forward = false)
            return try {
                val scrollable = findScrollableNode(root)
                val result = if (scrollable != null) {
                    scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                } else {
                    gestureScroll(service, forward = false)
                }
                if (scrollable !== root) scrollable?.recycle()
                result
            } finally {
                root.recycle()
            }
        }

        private fun gestureScroll(service: AccessibilityService, forward: Boolean): Boolean {
            if (isOverlayActive) {
                onOverlaySuppress?.invoke(true)
                Thread.sleep(150)
            }
            val displayMetrics = service.resources.displayMetrics
            val cx = displayMetrics.widthPixels / 2f
            val startY: Float
            val endY: Float
            if (forward) {
                startY = displayMetrics.heightPixels * 0.7f
                endY = displayMetrics.heightPixels * 0.3f
            } else {
                startY = displayMetrics.heightPixels * 0.3f
                endY = displayMetrics.heightPixels * 0.7f
            }
            val path = Path().apply {
                moveTo(cx, startY)
                lineTo(cx, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 200)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val result = service.dispatchGesture(gesture, null, null)
            if (isOverlayActive) {
                handler.postDelayed({ onOverlaySuppress?.invoke(false) }, 100L)
            }
            return result
        }
    }

    @Suppress("DEPRECATION")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only refresh when overlay is collapsed — ball has FLAG_NOT_FOCUSABLE
        // so rootInActiveWindow = the real app the user is looking at
        if (isOverlayActive) return
        if (refreshPending) return
        val now = System.currentTimeMillis()
        refreshPending = true
        handler.postDelayed({
            refreshPending = false
            refreshCache()
        }, DEBOUNCE_MS)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
        polling = true
        handler.post(pollRunnable)
    }

    override fun onDestroy() {
        instance = null
        polling = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
