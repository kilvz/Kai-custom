package com.inspiredandroid.kai.sms

/**
 * Multiplatform SMS sender. Only the Android FOSS build actually sends â€” the
 * `foss` flavor declares `SEND_SMS`; other platforms stub with a failure result.
 */
expect class SmsSender() {
    fun hasPermission(): Boolean

    /**
     * Fires the message via the system's default SMS stack. Long bodies are
     * split into multiple parts. Returns [SmsSendResult.Success] on accepted
     * submission (delivery is best-effort and may complete asynchronously), or
     * [SmsSendResult.Failure] on a precondition violation (missing permission,
     * bad address, platform unsupported).
     */
    suspend fun send(address: String, body: String): SmsSendResult
}

sealed class SmsSendResult {
    data object Success : SmsSendResult()
    data class Failure(val message: String) : SmsSendResult()
}
