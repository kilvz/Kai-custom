package com.kai.custom.ui.chat.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kai.custom.getBackgroundDispatcher
import com.kai.custom.ui.dynamicui.FrozenSubmission
import com.kai.custom.ui.dynamicui.toSpeakableText
import com.kai.custom.ui.handCursor
import com.kai.custom.ui.markdown.MarkdownContent
import com.kai.custom.ui.markdown.parseMarkdown
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.bot_message_cancel_edit
import kai.composeapp.generated.resources.bot_message_copy_content_description
import kai.composeapp.generated.resources.bot_message_edit_submission
import kai.composeapp.generated.resources.bot_message_fork_conversation
import kai.composeapp.generated.resources.bot_message_regenerate_content_description
import kai.composeapp.generated.resources.bot_message_speech_content_description
import kai.composeapp.generated.resources.bot_message_thinking_expand_content_description
import kai.composeapp.generated.resources.bot_message_thinking_label
import kai.composeapp.generated.resources.ic_copy
import kai.composeapp.generated.resources.ic_refresh
import kai.composeapp.generated.resources.ic_stop
import kai.composeapp.generated.resources.ic_volume_up
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.errors.TextToSpeechSynthesisInterruptedError
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BotMessage(
    message: String,
    textToSpeech: TextToSpeechInstance?,
    isSpeaking: Boolean,
    setIsSpeaking: (Boolean) -> Unit,
    onRegenerate: (() -> Unit)? = null,
    onFork: (() -> Unit)? = null,
    isInteractive: Boolean = false,
    onUiCallback: ((event: String, data: Map<String, String>) -> Unit)? = null,
    frozen: FrozenSubmission? = null,
    onResubmit: ((event: String, data: Map<String, String>) -> Unit)? = null,
    reasoningSegments: ImmutableList<String> = persistentListOf(),
    hideThinking: Boolean = false,
    expandThinking: Boolean = false,
) {
    val document = remember(message) { parseMarkdown(message) }
    var isEditing by remember(frozen) { mutableStateOf(false) }
    val effectiveFrozen = if (isEditing && frozen != null) frozen.copy(pressedEvent = null) else frozen
    val effectiveInteractive = if (frozen != null) (onResubmit != null && isEditing) else isInteractive
    val kaiUiCallback: (String, Map<String, String>) -> Unit = if (onResubmit != null) {
        { event, data ->
            isEditing = false
            onResubmit(event, data)
        }
    } else {
        onUiCallback ?: { _, _ -> }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val nonBlankSegments = remember(reasoningSegments) {
                reasoningSegments.filter { it.isNotBlank() }.toImmutableList()
            }
            if (nonBlankSegments.isNotEmpty() && !hideThinking) {
                ReasoningBlockquote(
                    segments = nonBlankSegments,
                    initiallyExpanded = expandThinking,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                )
            }
            if (message.isNotEmpty()) {
                // When reasoning is shown above, the Thinking row already provides
                // the visual gap to the answer — drop the duplicated top inset.
                val answerTopPadding = if (nonBlankSegments.isNotEmpty()) 6.dp else 16.dp
                SelectionContainer {
                    MarkdownContent(
                        document = document,
                        isInteractive = effectiveInteractive,
                        onUiCallback = kaiUiCallback,
                        frozen = effectiveFrozen,
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 16.dp, top = answerTopPadding, end = 16.dp, bottom = 8.dp),
                    )
                }
            }
        }
        if (frozen != null && onResubmit != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .handCursor()
                    .clickable { isEditing = !isEditing },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = if (isEditing) stringResource(Res.string.bot_message_cancel_edit) else stringResource(Res.string.bot_message_edit_submission),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (message.isEmpty()) return
    Row(Modifier.padding(horizontal = 8.dp)) {
        if (textToSpeech != null) {
            val componentScope = rememberCoroutineScope()
            SmallIconButton(
                iconResource = if (isSpeaking) Res.drawable.ic_stop else Res.drawable.ic_volume_up,
                contentDescription = stringResource(Res.string.bot_message_speech_content_description),
                onClick = {
                    componentScope.launch(getBackgroundDispatcher()) {
                        textToSpeech.stop()
                        if (isSpeaking) {
                            setIsSpeaking(false)
                        } else {
                            setIsSpeaking(true)
                            try {
                                textToSpeech.say(text = message.toSpeakableText())
                            } catch (ignore: TextToSpeechSynthesisInterruptedError) {
                                // Expected interruption - no action needed
                            } catch (e: Exception) {
                                // Handle TTS errors gracefully (service failure, audio issues, etc.)
                            }
                            setIsSpeaking(false)
                        }
                    }
                },
            )
        }
        @Suppress("DEPRECATION")
        val clipboardManager = LocalClipboardManager.current
        SmallIconButton(
            iconResource = Res.drawable.ic_copy,
            contentDescription = stringResource(Res.string.bot_message_copy_content_description),
            onClick = {
                clipboardManager.setText(buildAnnotatedString { append(message) })
            },
        )
        if (onFork != null) {
            SmallIconButton(
                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                contentDescription = stringResource(Res.string.bot_message_fork_conversation),
                modifier = Modifier.rotate(180f),
                onClick = onFork,
            )
        }
        if (onRegenerate != null) {
            SmallIconButton(
                iconResource = Res.drawable.ic_refresh,
                contentDescription = stringResource(Res.string.bot_message_regenerate_content_description),
                onClick = onRegenerate,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ReasoningBlockquote(
    segments: ImmutableList<String>,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    // Preview always reflects the MOST RECENT thinking segment so the user gets a
    // visual update each time a new reasoning phase starts, without expanding.
    val preview = remember(segments) {
        segments.lastOrNull()
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
                .handCursor(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(Res.string.bot_message_thinking_expand_content_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringResource(Res.string.bot_message_thinking_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!expanded && preview.isNotEmpty()) {
                Text(
                    text = " · $preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (segment in segments) {
                    val dividerColor = MaterialTheme.colorScheme.outlineVariant
                    SelectionContainer(
                        modifier = Modifier
                            .drawBehind {
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                            .padding(start = 10.dp),
                    ) {
                        Text(
                            text = segment,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
