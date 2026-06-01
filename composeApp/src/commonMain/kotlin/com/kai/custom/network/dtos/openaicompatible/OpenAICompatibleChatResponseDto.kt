package com.kai.custom.network.dtos.openaicompatible

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val toolCallMarkerRegex = Regex("<TOOLCALL>[\\s\\S]*?</TOOLCALL>|<TOOLCALL>[\\s\\S]*$")
private val chatTemplateTokenRegex = Regex("</?assistant>|</?system>|</?user>|<\\|im_start\\||<\\|im_end\\|>|<\\|end_of_turn\\|>|</s>|<\\|eot_id\\|>")
private val thinkBlockRegex = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)

/**
 * Reads `message.content` whether the provider sends a plain string or an OpenAI-style array of
 * content blocks (e.g. `[{"type":"text","text":"..."}]`). Array forms are flattened by
 * concatenating the `text` fields, so downstream code keeps seeing a simple [String]. Applied only
 * to nullable fields, so kotlinx handles a literal JSON `null` before this runs.
 */
internal object FlexibleContentSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleContent", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.content

            is JsonArray -> element.mapNotNull { part ->
                (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }.joinToString("")

            else -> ""
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class OpenAICompatibleChatResponseDto(
    val choices: List<Choice>,
) {
    @Serializable
    data class Choice(val message: Message? = null) {
        @Serializable
        data class Message(
            val role: String? = null,
            @Serializable(with = FlexibleContentSerializer::class)
            val content: String? = null,
            // DeepSeek returns `reasoning_content`; OpenRouter returns `reasoning`.
            // Some providers may send non-string types (objects, arrays) for reasoning fields,
            // so apply the same FlexibleContentSerializer protection as `content`.
            @SerialName("reasoning_content")
            @Serializable(with = FlexibleContentSerializer::class)
            val reasoningContent: String? = null,
            val reasoning: String? = null,
            @SerialName("tool_calls")
            val toolCalls: List<ToolCall>? = null,
        ) {
            /** Whichever reasoning field the provider used, normalized to one accessor. */
            val effectiveReasoning: String?
                get() = reasoningContent ?: reasoning

            /** Returns [content] if non-blank, otherwise falls back to reasoning. */
            val effectiveContent: String?
                get() {
                    val raw = content?.takeIf { it.isNotBlank() } ?: effectiveReasoning
                    if (raw == null) return null
                    var cleaned = raw
                    // Some providers (e.g. Ollama) embed tool calls as <TOOLCALL>[...] markers
                    // in the content field alongside structured tool_calls — strip them.
                    if (!toolCalls.isNullOrEmpty()) {
                        cleaned = cleaned.replace(toolCallMarkerRegex, "").trim()
                    }
                    // Strip chat template tokens that some models leak into content
                    cleaned = cleaned.replace(chatTemplateTokenRegex, "").trim()
                    // Strip think blocks from reasoning models (Qwen3-Thinking, Gemma 4, etc.)
                    if (cleaned.contains("</think>")) {
                        if (!cleaned.contains("<think>")) {
                            // Qwen3-Thinking omits opening <think> tag — everything before </think> is reasoning
                            cleaned = cleaned.substringAfter("</think>").trim()
                        } else {
                            cleaned = thinkBlockRegex.replace(cleaned, "").trim()
                        }
                    }
                    return cleaned.takeIf { it.isNotBlank() }
                }

            /** True when the effective content comes from reasoning rather than [content]. */
            val isContentFromReasoning: Boolean
                get() = content.isNullOrBlank() && !effectiveReasoning.isNullOrBlank()

            /**
             * Reasoning trace with the answer text trimmed off if the provider appended it.
             * LongCat (flash thinking) and a few others stream the final answer as the tail of
             * `reasoning_content`, then return the same text in `content` — without this, the
             * "Thinking" section duplicates the answer rendered below it.
             */
            fun reasoningTraceFor(answer: String?): String? {
                val reasoning = effectiveReasoning ?: return null
                if (answer.isNullOrBlank() || isContentFromReasoning) return reasoning
                val trimmedReasoning = reasoning.trimEnd()
                val trimmedAnswer = answer.trim()
                if (!trimmedReasoning.endsWith(trimmedAnswer)) return reasoning
                return trimmedReasoning.removeSuffix(trimmedAnswer).trimEnd().takeIf { it.isNotBlank() }
            }
        }
    }

    @Serializable
    data class ToolCall(
        val id: String,
        val type: String = "function",
        val function: FunctionCall,
    )

    @Serializable
    data class FunctionCall(
        val name: String,
        val arguments: String,
    )
}
