package com.kai.custom.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TelegramIncomingMessage? = null,
)

@Serializable
data class TelegramIncomingMessage(
    @SerialName("message_id") val messageId: Long,
    val chat: TelegramChat,
    val text: String? = null,
    val date: Long,
)

@Serializable
data class TelegramChat(
    val id: Long,
    val type: String = "",
    val title: String? = null,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
)

@Serializable
data class TelegramSyncState(
    val lastUpdateId: Long = 0L,
    val lastSyncEpochMs: Long = 0L,
    val lastAttemptEpochMs: Long = 0L,
    val lastError: String? = null,
)

@Serializable
data class TelegramOutgoingMessage(
    @SerialName("chat_id") val chatId: Long,
    val text: String,
    @SerialName("reply_to_message_id") val replyToMessageId: Long? = null,
)

@Immutable
@Serializable
data class TelegramPendingMessage(
    val chatId: Long,
    val messageId: Long,
    val text: String,
    val fromName: String = "",
    val date: Long,
    val preview: String = text.take(PREVIEW_CHARS),
) {
    companion object {
        const val PREVIEW_CHARS = 200
    }
}

@Serializable
data class TelegramGetUpdatesResponse(
    val ok: Boolean,
    val result: List<TelegramUpdate> = emptyList(),
)

@Serializable
data class TelegramSendMessageResponse(
    val ok: Boolean,
    val result: TelegramIncomingMessage? = null,
)
