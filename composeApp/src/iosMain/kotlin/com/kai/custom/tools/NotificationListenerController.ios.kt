package com.kai.custom.tools

actual class NotificationListenerController actual constructor() {
    actual fun isSupported(): Boolean = false
    actual fun isAccessGranted(): Boolean = false
    actual fun openAccessSettings() {}
}
