package com.kai.custom.sms

actual class SmsSender actual constructor() {
    actual fun hasPermission(): Boolean = false
    actual suspend fun send(address: String, body: String): SmsSendResult = SmsSendResult.Failure("SMS sending not supported on this platform")
}
