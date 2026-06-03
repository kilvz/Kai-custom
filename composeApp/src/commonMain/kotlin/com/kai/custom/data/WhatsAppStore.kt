package com.kai.custom.data

import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppPendingMessage(
    val chatId: String,
    val messageId: String,
    val text: String,
    val fromName: String,
    val fromMe: Boolean = false,
    val timestamp: Long = 0,
)

class WhatsAppStore(private val appSettings: AppSettings) {

    fun isWhatsAppEnabled(): Boolean = appSettings.isWhatsAppEnabled()
    fun setWhatsAppEnabled(enabled: Boolean) = appSettings.setWhatsAppEnabled(enabled)

    fun isWhatsAppInstalled(): Boolean = appSettings.isWhatsAppInstalled()
    fun setWhatsAppInstalled(installed: Boolean) = appSettings.setWhatsAppInstalled(installed)

    fun isWhatsAppAuthenticated(): Boolean = appSettings.isWhatsAppAuthenticated()
    fun setWhatsAppAuthenticated(auth: Boolean) = appSettings.setWhatsAppAuthenticated(auth)

    fun getWhatsAppQrCode(): String = appSettings.getWhatsAppQrCode()
    fun setWhatsAppQrCode(qr: String) = appSettings.setWhatsAppQrCode(qr)

    fun isWhatsAppReadOnly(): Boolean = appSettings.isWhatsAppReadOnly()
    fun setWhatsAppReadOnly(readOnly: Boolean) = appSettings.setWhatsAppReadOnly(readOnly)

    fun getWhatsAppReplyMode(): String = appSettings.getWhatsAppReplyMode()
    fun setWhatsAppReplyMode(mode: String) = appSettings.setWhatsAppReplyMode(mode)

    fun getWhatsAppAllowedContacts(): String = appSettings.getWhatsAppAllowedContacts()
    fun setWhatsAppAllowedContacts(contacts: String) = appSettings.setWhatsAppAllowedContacts(contacts)

    fun isWhatsAppReadReceipt(): Boolean = appSettings.isWhatsAppReadReceipt()
    fun setWhatsAppReadReceipt(enabled: Boolean) = appSettings.setWhatsAppReadReceipt(enabled)

    private val json = SharedJson

    fun getPending(): List<WhatsAppPendingMessage> {
        val raw = appSettings.getWhatsAppPendingJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<WhatsAppPendingMessage>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setPending(messages: List<WhatsAppPendingMessage>) {
        appSettings.setWhatsAppPendingJson(json.encodeToString(messages))
    }

    fun clearPending() {
        appSettings.setWhatsAppPendingJson("")
    }
}
