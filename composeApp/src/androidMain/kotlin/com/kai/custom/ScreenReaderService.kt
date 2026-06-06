package com.kai.custom

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScreenReaderService : AccessibilityService() {

    companion object {
        private var instance: ScreenReaderService? = null

        fun isConnected(): Boolean = instance != null

        @Suppress("DEPRECATION")
        fun readScreenText(): String? {
            val service = instance ?: return null
            val root = service.rootInActiveWindow ?: return null
            return try {
                collectText(root)
            } finally {
                root.recycle()
            }
        }

        @Suppress("DEPRECATION")
        private fun collectText(node: AccessibilityNodeInfo): String {
            val sb = StringBuilder()
            if (!node.text.isNullOrBlank()) {
                sb.appendLine(node.text)
            }
            if (!node.contentDescription.isNullOrBlank()) {
                sb.appendLine(node.contentDescription)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    try {
                        sb.append(collectText(child))
                    } finally {
                        child.recycle()
                    }
                }
            }
            return sb.toString().trim()
        }

        @Suppress("DEPRECATION")
        fun clickOnText(text: String): Boolean {
            val service = instance ?: return false
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
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }

        fun globalAction(action: Int): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(action)
        }

        @Suppress("DEPRECATION")
        fun scrollForward(): Boolean {
            val service = instance ?: return false
            val root = service.rootInActiveWindow ?: return false
            return try {
                root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            } finally {
                root.recycle()
            }
        }

        @Suppress("DEPRECATION")
        fun scrollBackward(): Boolean {
            val service = instance ?: return false
            val root = service.rootInActiveWindow ?: return false
            return try {
                root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            } finally {
                root.recycle()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
