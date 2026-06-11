@file:OptIn(ExperimentalMaterial3Api::class)

package com.kai.custom.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kai.custom.data.Service
import com.kai.custom.formatFileSize
import com.kai.custom.inference.DevicePerformance
import com.kai.custom.inference.DownloadError
import com.kai.custom.inference.LocalModel
import com.kai.custom.inference.calculateDevicePerformance
import com.kai.custom.inference.estimateGpuMemoryMb
import com.kai.custom.ui.KaiClearableTextField
import com.kai.custom.ui.components.KaiSlider
import com.kai.custom.ui.components.VerticalScrollbarForScroll
import com.kai.custom.ui.handCursor
import com.kai.custom.ui.icons.DragIndicator
import com.kai.custom.ui.kaiAdaptiveCardBorder
import com.kai.custom.ui.kaiAdaptiveCardColors
import com.kai.custom.ui.kaiAdaptiveCardSurface
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_arrow_drop_down
import kai.composeapp.generated.resources.litert_cancel
import kai.composeapp.generated.resources.litert_context_size
import kai.composeapp.generated.resources.litert_download
import kai.composeapp.generated.resources.litert_error_download_incomplete
import kai.composeapp.generated.resources.litert_error_network
import kai.composeapp.generated.resources.litert_error_not_enough_disk_space
import kai.composeapp.generated.resources.litert_free_space
import kai.composeapp.generated.resources.litert_on_device_description
import kai.composeapp.generated.resources.litert_performance_good
import kai.composeapp.generated.resources.litert_performance_ok
import kai.composeapp.generated.resources.litert_performance_poor
import kai.composeapp.generated.resources.litert_recommended
import kai.composeapp.generated.resources.litert_tool_support
import kai.composeapp.generated.resources.settings_add_service
import kai.composeapp.generated.resources.settings_api_key_label
import kai.composeapp.generated.resources.settings_api_key_optional_label
import kai.composeapp.generated.resources.settings_base_url_label
import kai.composeapp.generated.resources.settings_become_sponsor
import kai.composeapp.generated.resources.settings_free_fallback
import kai.composeapp.generated.resources.settings_free_tier_description
import kai.composeapp.generated.resources.settings_free_tier_title
import kai.composeapp.generated.resources.settings_openai_compatible_or_other_service
import kai.composeapp.generated.resources.settings_openai_compatible_providers
import kai.composeapp.generated.resources.settings_openai_compatible_setup_ollama
import kai.composeapp.generated.resources.settings_remove_service
import kai.composeapp.generated.resources.settings_reorder_content_description
import kai.composeapp.generated.resources.settings_sign_in_copy_api_key_from
import kai.composeapp.generated.resources.settings_status_checking
import kai.composeapp.generated.resources.settings_status_connected
import kai.composeapp.generated.resources.settings_status_error
import kai.composeapp.generated.resources.settings_status_error_connection_failed
import kai.composeapp.generated.resources.settings_status_error_invalid_key
import kai.composeapp.generated.resources.settings_status_error_quota_exhausted
import kai.composeapp.generated.resources.settings_status_error_rate_limited
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableColumn
import kotlin.math.roundToInt

@Composable
internal fun FreeSettings(
    showFallbackToggle: Boolean = false,
    isFreeFallbackEnabled: Boolean = true,
    onToggleFreeFallback: (Boolean) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = kaiAdaptiveCardColors(),
        border = kaiAdaptiveCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.settings_free_tier_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (showFallbackToggle) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleFreeFallback(!isFreeFallbackEnabled) }
                        .handCursor(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_free_fallback),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = isFreeFallbackEnabled,
                        onCheckedChange = onToggleFreeFallback,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(Res.string.settings_free_tier_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            val uriHandler = LocalUriHandler.current
            Button(
                onClick = { uriHandler.openUri("https://github.com/sponsors/kilvz") },
                modifier = Modifier.align(CenterHorizontally).handCursor(),
            ) {
                Text("❤️ ${stringResource(Res.string.settings_become_sponsor)}")
            }
        }
    }
}

@Composable
internal fun ServicesContent(
    actions: SettingsActions,
    configuredServices: ImmutableList<ConfiguredServiceEntry>,
    expandedServiceId: String?,
    localAvailableModels: ImmutableList<LocalModel>,
    totalDeviceMemoryBytes: Long,
    localFreeSpaceBytes: Long,
    localDownloadingModelId: String?,
    localDownloadProgress: Float?,
    localDownloadError: DownloadError?,
    modelContextTokens: ImmutableMap<String, Int>,
    modelMaxTokens: ImmutableMap<String, Int> = persistentMapOf(),
    onChangeModelMaxTokens: (String, Int) -> Unit = { _, _ -> },
    modelTemperature: ImmutableMap<String, Float> = persistentMapOf(),
    onChangeModelTemperature: (String, Float) -> Unit = { _, _ -> },
    localStyleInstruction: String = "",
    onChangeLocalStyleInstruction: (String) -> Unit = {},
    localModelFullPrompt: Boolean = false,
    onChangeLocalModelFullPrompt: (Boolean) -> Unit = {},
    modelTopK: ImmutableMap<String, Int> = persistentMapOf(),
    onChangeModelTopK: (String, Int) -> Unit = { _, _ -> },
    modelTopP: ImmutableMap<String, Float> = persistentMapOf(),
    onChangeModelTopP: (String, Float) -> Unit = { _, _ -> },
    onImportLocalModel: (ByteArray, String) -> Unit = { _, _ -> },
    availableServicesToAdd: ImmutableList<Service>,
    isFreeFallbackEnabled: Boolean,
) {
    var showAddServiceSheet by remember { mutableStateOf(false) }

    // Configured services list
    val entries = configuredServices
    ReorderableColumn(
        list = entries,
        onSettle = { fromIndex, toIndex ->
            val ids = entries.map { it.instanceId }.toMutableList()
            ids.add(toIndex, ids.removeAt(fromIndex))
            actions.onReorderServices(ids)
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { _, entry, isDragging ->
        key(entry.instanceId) {
            ReorderableItem {
                ConfiguredServiceCardContent(
                    entry = entry,
                    isExpanded = expandedServiceId == entry.instanceId,
                    onExpand = { actions.onExpandService(if (expandedServiceId == entry.instanceId) null else entry.instanceId) },
                    onChangeApiKey = { apiKey -> actions.onChangeApiKey(entry.instanceId, apiKey) },
                    onChangeBaseUrl = { baseUrl -> actions.onChangeBaseUrl(entry.instanceId, baseUrl) },
                    onSelectModel = { modelId -> actions.onSelectModel(entry.instanceId, modelId) },
                    onRemove = { actions.onRemoveService(entry.instanceId) },
                    isDragging = isDragging,
                    dragHandleModifier = if (entries.size >= 2) Modifier.draggableHandle() else null,
                    localAvailableModels = localAvailableModels,
                    totalDeviceMemoryBytes = totalDeviceMemoryBytes,
                    localFreeSpaceBytes = localFreeSpaceBytes,
                    localDownloadingModelId = localDownloadingModelId,
                    localDownloadProgress = localDownloadProgress,
                    localDownloadError = localDownloadError,
                    onDownloadLocalModel = actions.onDownloadLocalModel,
                    onCancelLocalModelDownload = actions.onCancelLocalModelDownload,
                    onDeleteLocalModel = actions.onDeleteLocalModel,
                    onChangeModelContextTokens = actions.onChangeModelContextTokens,
                    modelContextTokens = modelContextTokens,
                    onChangeModelMaxTokens = onChangeModelMaxTokens,
                    modelMaxTokens = modelMaxTokens,
                    onChangeModelTemperature = onChangeModelTemperature,
                    modelTemperature = modelTemperature,
                    localStyleInstruction = localStyleInstruction,
                    onChangeLocalStyleInstruction = onChangeLocalStyleInstruction,
                    localModelFullPrompt = localModelFullPrompt,
                    onChangeLocalModelFullPrompt = onChangeLocalModelFullPrompt,
                    modelTopK = modelTopK,
                    onChangeModelTopK = onChangeModelTopK,
                    modelTopP = modelTopP,
                    onChangeModelTopP = onChangeModelTopP,
                    onImportLocalModel = onImportLocalModel,
                    onImportPlatformFileComplete = actions.onImportPlatformFileComplete,
                )
            }
        }
    }

    if (availableServicesToAdd.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showAddServiceSheet = true }, modifier = Modifier.handCursor()) {
            Text(stringResource(Res.string.settings_add_service))
        }
    }

    Spacer(Modifier.height(16.dp))
    FreeSettings(
        showFallbackToggle = entries.isNotEmpty(),
        isFreeFallbackEnabled = isFreeFallbackEnabled,
        onToggleFreeFallback = actions.onToggleFreeFallback,
    )

    // Add service bottom sheet
    if (showAddServiceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddServiceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            val addServiceScrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(addServiceScrollState).padding(16.dp)) {
                    val services = availableServicesToAdd
                    services.forEachIndexed { index, service ->
                        val isFirst = index == 0
                        val isLast = index == services.lastIndex
                        val itemShape = RoundedCornerShape(
                            topStart = if (isFirst) 12.dp else 0.dp,
                            topEnd = if (isFirst) 12.dp else 0.dp,
                            bottomStart = if (isLast) 12.dp else 0.dp,
                            bottomEnd = if (isLast) 12.dp else 0.dp,
                        )
                        val isSpecial = service.isOnDevice || service is Service.OpenAICompatible || service is Service.AtlasCloud
                        Surface(
                            onClick = {
                                actions.onAddService(service)
                                showAddServiceSheet = false
                            },
                            modifier = Modifier.fillMaxWidth().handCursor(),
                            shape = itemShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .then(
                                            if (isSpecial) {
                                                Modifier.background(
                                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = vectorResource(service.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = service.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopKSlider(
    modelId: String,
    topK: Int,
    onChangeTopK: (Int) -> Unit,
) {
    var sliderValue by remember(topK) { mutableStateOf(topK.toFloat().coerceIn(1f, 100f)) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Top-K",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = sliderValue.roundToInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChangeTopK(sliderValue.roundToInt()) },
            valueRange = 1f..100f,
            steps = 98,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "1 — Focused",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Default: 40",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "100 — Diverse",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Limits token candidates. Lower = fewer choices, more predictable. Higher = more variety.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopPSlider(
    modelId: String,
    topP: Float,
    onChangeTopP: (Float) -> Unit,
) {
    var sliderValue by remember(topP) { mutableStateOf(topP.coerceIn(0.0f, 1.0f)) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Top-P",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "%.2f".format(sliderValue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = (it * 20).roundToInt() / 20f },
            onValueChangeFinished = { onChangeTopP(sliderValue) },
            valueRange = 0.0f..1.0f,
            steps = 19,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "0.0 — Precise",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Default: 0.95",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "1.0 — Broad",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Nucleus sampling. Lower = more conservative, higher = more diverse word choices.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LocalStyleInstructionField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Local Model Style Instruction",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(onClick = { onValueChange(com.kai.custom.data.AppSettings.DEFAULT_LOCAL_STYLE_INSTRUCTION) }) {
                Text("Reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "This instruction is prepended to the system prompt only when using an on-device model. Use it to override the model's default writing style.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfiguredServiceCardContent(
    entry: ConfiguredServiceEntry,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onChangeApiKey: (String) -> Unit,
    onChangeBaseUrl: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onRemove: () -> Unit,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier? = null,
    localAvailableModels: ImmutableList<LocalModel> = persistentListOf(),
    totalDeviceMemoryBytes: Long = Long.MAX_VALUE,
    localFreeSpaceBytes: Long = 0L,
    localDownloadingModelId: String? = null,
    localDownloadProgress: Float? = null,
    localDownloadError: DownloadError? = null,
    onDownloadLocalModel: (LocalModel) -> Unit = {},
    onCancelLocalModelDownload: () -> Unit = {},
    onDeleteLocalModel: (String) -> Unit = {},
    onChangeModelContextTokens: (String, Int) -> Unit = { _, _ -> },
    modelContextTokens: ImmutableMap<String, Int> = persistentMapOf(),
    onChangeModelMaxTokens: (String, Int) -> Unit = { _, _ -> },
    modelMaxTokens: ImmutableMap<String, Int> = persistentMapOf(),
    onChangeModelTemperature: (String, Float) -> Unit = { _, _ -> },
    modelTemperature: ImmutableMap<String, Float> = persistentMapOf(),
    localStyleInstruction: String = "",
    onChangeLocalStyleInstruction: (String) -> Unit = {},
    localModelFullPrompt: Boolean = false,
    onChangeLocalModelFullPrompt: (Boolean) -> Unit = {},
    modelTopK: ImmutableMap<String, Int> = persistentMapOf(),
    onChangeModelTopK: (String, Int) -> Unit = { _, _ -> },
    modelTopP: ImmutableMap<String, Float> = persistentMapOf(),
    onChangeModelTopP: (String, Float) -> Unit = { _, _ -> },
    onImportLocalModel: (ByteArray, String) -> Unit = { _, _ -> },
    onImportPlatformFileComplete: (com.kai.custom.inference.ImportedModel) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .kaiAdaptiveCardSurface()
            .fillMaxWidth()
            .clickable { onExpand() }
            .handCursor(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Drag handle
                if (dragHandleModifier != null) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = stringResource(Res.string.settings_reorder_content_description),
                        modifier = dragHandleModifier.handCursor(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // Connection status dot
                val dotColor = when (entry.connectionStatus) {
                    ConnectionStatus.Connected -> StatusColorConnected
                    ConnectionStatus.Checking -> StatusColorChecking
                    ConnectionStatus.Unknown -> StatusColorUnknown
                    else -> StatusColorError
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )

                Spacer(Modifier.width(12.dp))

                // Service name and model
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.service.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (entry.selectedModel != null) {
                        Text(
                            text = entry.selectedModel.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Expand/collapse chevron
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Expanded content
        if (isExpanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                if (entry.service.isOnDevice) {
                    LiteRTSettings(
                        selectedModel = entry.selectedModel,
                        downloadedModels = entry.models,
                        availableModels = localAvailableModels,
                        totalDeviceMemoryBytes = totalDeviceMemoryBytes,
                        freeSpaceBytes = localFreeSpaceBytes,
                        downloadingModelId = localDownloadingModelId,
                        downloadProgress = localDownloadProgress,
                        downloadError = localDownloadError,
                        onSelectModel = onSelectModel,
                        onDownloadModel = onDownloadLocalModel,
                        onCancelDownload = onCancelLocalModelDownload,
                        onDeleteModel = onDeleteLocalModel,
                        onChangeModelContextTokens = onChangeModelContextTokens,
                        modelContextTokens = modelContextTokens,
                        onImportLocalModel = onImportLocalModel,
                        onImportPlatformFileComplete = onImportPlatformFileComplete,
                    )
                } else if (entry.service is Service.OpenAICompatible) {
                    OpenAICompatibleSettings(
                        baseUrl = entry.baseUrl,
                        onChangeBaseUrl = onChangeBaseUrl,
                        apiKey = entry.apiKey,
                        onChangeApiKey = onChangeApiKey,
                        selectedModel = entry.selectedModel,
                        models = entry.models,
                        onSelectModel = onSelectModel,
                        connectionStatus = entry.connectionStatus,
                    )
                } else {
                    ServiceSettings(
                        apiKey = entry.apiKey,
                        onChangeApiKey = onChangeApiKey,
                        apiKeyUrl = entry.service.apiKeyUrl ?: "",
                        apiKeyUrlDisplay = entry.service.apiKeyUrlDisplay ?: "",
                        selectedModel = entry.selectedModel,
                        models = entry.models,
                        onSelectModel = onSelectModel,
                        connectionStatus = entry.connectionStatus,
                    )
                }

                Spacer(Modifier.height(8.dp))

                var advancedExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().handCursor().clickable { advancedExpanded = !advancedExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (advancedExpanded) "Advanced ▾" else "Advanced ▸",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (advancedExpanded) {
                    MaxTokensSlider(
                        modelId = entry.selectedModel?.id ?: entry.service.id,
                        maxTokens = modelMaxTokens[entry.selectedModel?.id ?: entry.service.id] ?: 0,
                        onChangeMaxTokens = { onChangeModelMaxTokens(entry.selectedModel?.id ?: entry.service.id, it) },
                    )
                    if (entry.service.isOnDevice) {
                        TopKSlider(
                            modelId = entry.selectedModel?.id ?: entry.service.id,
                            topK = modelTopK[entry.selectedModel?.id ?: entry.service.id] ?: 40,
                            onChangeTopK = { onChangeModelTopK(entry.selectedModel?.id ?: entry.service.id, it) },
                        )
                        TopPSlider(
                            modelId = entry.selectedModel?.id ?: entry.service.id,
                            topP = modelTopP[entry.selectedModel?.id ?: entry.service.id] ?: 0.95f,
                            onChangeTopP = { onChangeModelTopP(entry.selectedModel?.id ?: entry.service.id, it) },
                        )
                        TemperatureSlider(
                            modelId = entry.selectedModel?.id ?: entry.service.id,
                            temperature = modelTemperature[entry.selectedModel?.id ?: entry.service.id] ?: 0.8f,
                            onChangeTemperature = { onChangeModelTemperature(entry.selectedModel?.id ?: entry.service.id, it) },
                        )
                    }
                    if (entry.service.isOnDevice) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Full Prompt for Local Models",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text = "When on, uses the full persona prompt instead of the truncated Identity-only version.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = localModelFullPrompt,
                                onCheckedChange = onChangeLocalModelFullPrompt,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        LocalStyleInstructionField(
                            value = localStyleInstruction,
                            onValueChange = onChangeLocalStyleInstruction,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Remove action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_remove_service),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceSettings(
    apiKey: String,
    onChangeApiKey: (String) -> Unit,
    apiKeyUrl: String,
    apiKeyUrlDisplay: String,
    selectedModel: SettingsModel?,
    models: ImmutableList<SettingsModel>,
    onSelectModel: (String) -> Unit,
    connectionStatus: ConnectionStatus,
    testTag: String? = null,
) {
    ApiKeyField(
        apiKey = apiKey,
        onChangeApiKey = onChangeApiKey,
        labelText = stringResource(Res.string.settings_api_key_label),
        testTag = testTag,
    )

    Spacer(Modifier.height(8.dp))

    ConnectionStatusIndicator(connectionStatus)

    Spacer(Modifier.height(8.dp))

    val linkColor = MaterialTheme.colorScheme.primary

    val copyApiKeyPromptString = stringResource(Res.string.settings_sign_in_copy_api_key_from)
    val annotatedString = remember(apiKeyUrl, apiKeyUrlDisplay) {
        buildAnnotatedString {
            append(copyApiKeyPromptString)
            append(" ")
            withLink(LinkAnnotation.Url(url = apiKeyUrl)) {
                withStyle(style = SpanStyle(color = linkColor)) {
                    append(apiKeyUrlDisplay)
                }
            }
        }
    }
    Text(
        annotatedString,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(16.dp))

    if (connectionStatus == ConnectionStatus.Connected || models.isNotEmpty()) {
        ModelSelection(selectedModel, models, onSelectModel)
    }
}

@Composable
private fun OpenAICompatibleSettings(
    baseUrl: String,
    onChangeBaseUrl: (String) -> Unit,
    apiKey: String,
    onChangeApiKey: (String) -> Unit,
    selectedModel: SettingsModel?,
    models: ImmutableList<SettingsModel>,
    onSelectModel: (String) -> Unit,
    connectionStatus: ConnectionStatus,
) {
    KaiClearableTextField(
        value = baseUrl,
        onValueChange = onChangeBaseUrl,
        label = {
            Text(
                stringResource(Res.string.settings_base_url_label),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        singleLine = true,
    )
    if (baseUrl.isNotBlank()) {
        Text(
            text = "${baseUrl.trimEnd('/')}${Service.OpenAICompatible.chatUrl}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
        )
    }

    Spacer(Modifier.height(8.dp))

    ApiKeyField(
        apiKey = apiKey,
        onChangeApiKey = onChangeApiKey,
        labelText = stringResource(Res.string.settings_api_key_optional_label),
        singleLine = true,
    )

    Spacer(Modifier.height(8.dp))

    ConnectionStatusIndicator(connectionStatus)

    Spacer(Modifier.height(8.dp))

    val linkColor = MaterialTheme.colorScheme.primary
    val setupOllamaText = stringResource(Res.string.settings_openai_compatible_setup_ollama)
    val orOtherServiceText = stringResource(Res.string.settings_openai_compatible_or_other_service)
    val providersText = stringResource(Res.string.settings_openai_compatible_providers)
    val annotatedString = remember(setupOllamaText, orOtherServiceText, providersText, linkColor) {
        buildAnnotatedString {
            append(setupOllamaText)
            append(" ")
            withLink(LinkAnnotation.Url(url = "https://github.com/ollama/ollama")) {
                withStyle(style = SpanStyle(color = linkColor)) {
                    append("github.com/ollama/ollama")
                }
            }
            append(" ")
            append(orOtherServiceText)
            append(" ")
            withLink(LinkAnnotation.Url(url = "https://docs.litellm.ai/docs/providers")) {
                withStyle(style = SpanStyle(color = linkColor)) {
                    append(providersText)
                }
            }
        }
    }
    Text(
        annotatedString,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(16.dp))

    if (connectionStatus == ConnectionStatus.Connected) {
        ModelSelection(selectedModel, models, onSelectModel)
    }
}

@Composable
private fun LiteRTSettings(
    selectedModel: SettingsModel?,
    downloadedModels: ImmutableList<SettingsModel>,
    availableModels: ImmutableList<LocalModel>,
    totalDeviceMemoryBytes: Long,
    freeSpaceBytes: Long,
    downloadingModelId: String?,
    downloadProgress: Float?,
    downloadError: DownloadError?,
    onSelectModel: (String) -> Unit,
    onDownloadModel: (LocalModel) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: (String) -> Unit,
    onChangeModelContextTokens: (String, Int) -> Unit,
    modelContextTokens: ImmutableMap<String, Int>,
    onImportLocalModel: (ByteArray, String) -> Unit = { _, _ -> },
    onImportPlatformFileComplete: (com.kai.custom.inference.ImportedModel) -> Unit = {},
) {
    val downloadedIds = remember(downloadedModels) { downloadedModels.map { it.id }.toSet() }
    val importScope = rememberCoroutineScope()

    Text(
        text = stringResource(Res.string.litert_on_device_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text = stringResource(Res.string.litert_tool_support),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    var shownUncensoredHeader by remember { mutableStateOf(false) }
    availableModels.forEach { model ->
        val isUncensored = model.id.startsWith("uncensored-")
        if (isUncensored && !shownUncensoredHeader) {
            shownUncensoredHeader = true
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Uncensored",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        val isDownloaded = model.id in downloadedIds
        val isSelected = selectedModel?.id == model.id
        val isDownloading = downloadingModelId == model.id
        val steps = (model.maxContextTokens - model.defaultContextTokens) / 1024
        val storedContextTokens = modelContextTokens[model.id] ?: model.defaultContextTokens
        var contextSliderValue by remember(storedContextTokens) {
            mutableStateOf(((storedContextTokens - model.defaultContextTokens) / 1024).toFloat())
        }
        val contextTokens = model.defaultContextTokens + (contextSliderValue.roundToInt() * 1024)
        val estimatedMemoryMb = estimateGpuMemoryMb(model, contextTokens)
        val performance = calculateDevicePerformance(totalDeviceMemoryBytes, estimatedMemoryMb)

        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = if (isSelected) 3.dp else 1.dp,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isDownloaded) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectModel(model.id) },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (model.isRecommended) {
                                "${model.displayName} (${stringResource(Res.string.litert_recommended)})"
                            } else {
                                model.displayName
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatFileSize(model.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            DevicePerformanceLabel(performance)
                        }
                    }
                    if (isDownloaded) {
                        IconButton(
                            onClick = { onDeleteModel(model.id) },
                            modifier = Modifier.handCursor(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (!isDownloading) {
                        TextButton(
                            onClick = { onDownloadModel(model) },
                            modifier = Modifier.handCursor(),
                            enabled = downloadingModelId == null,
                        ) {
                            Text(stringResource(Res.string.litert_download))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.litert_context_size, "${contextTokens / 1024}K"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KaiSlider(
                    value = contextSliderValue,
                    onValueChange = { contextSliderValue = it },
                    onValueChangeFinished = {
                        onChangeModelContextTokens(model.id, contextTokens)
                    },
                    valueRange = 0f..steps.toFloat(),
                    steps = steps - 1,
                )
                if (isDownloading && downloadProgress != null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onCancelDownload,
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(
                                text = stringResource(Res.string.litert_cancel),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (downloadError != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                when (downloadError) {
                    DownloadError.NOT_ENOUGH_DISK_SPACE -> Res.string.litert_error_not_enough_disk_space
                    DownloadError.NETWORK_ERROR -> Res.string.litert_error_network
                    DownloadError.DOWNLOAD_INCOMPLETE -> Res.string.litert_error_download_incomplete
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text = stringResource(Res.string.litert_free_space, formatFileSize(freeSpaceBytes)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    var importError by remember { mutableStateOf(false) }
    val importFilePicker = com.kai.custom.inference.rememberSafFilePicker(
        extensions = listOf("litertlm", "gguf"),
    ) { uriOrPath, displayName, sizeBytes ->
        if (uriOrPath != null) {
            importScope.launch {
                try {
                    val isGguf = try { displayName?.endsWith(".gguf") == true } catch (_: Exception) { false }
                    
                    val id = com.kai.custom.inference.handleImportedSafFile(uriOrPath, isGguf)
                    
                    if (id != null) {
                        val cleanDisplayName = (displayName ?: "model")
                            .removeSuffix(".litertlm")
                            .removeSuffix(".gguf")
                            .replace("_", " ").replace("-", " ").trim()
                            .replaceFirstChar { it.uppercase() }

                        val model = com.kai.custom.inference.ImportedModel(
                            id = id,
                            displayName = cleanDisplayName,
                            filePath = uriOrPath,
                            sizeBytes = sizeBytes,
                        )
                        onImportPlatformFileComplete(model)
                        importError = false
                    } else {
                        importError = true
                    }
                } catch (_: Exception) {
                    importError = true
                }
            }
        }
    }

    OutlinedButton(
        onClick = { importFilePicker() },
        modifier = Modifier.handCursor(),
    ) { Text("Import Model File (.litertlm / .gguf)") }

    if (importError) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Import failed — check file format and space.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DevicePerformanceLabel(performance: DevicePerformance) {
    when (performance) {
        DevicePerformance.GOOD -> Text(
            text = stringResource(Res.string.litert_performance_good),
            style = MaterialTheme.typography.labelSmall,
            color = StatusColorConnected,
        )

        DevicePerformance.OK -> Text(
            text = stringResource(Res.string.litert_performance_ok),
            style = MaterialTheme.typography.labelSmall,
            color = StatusColorChecking,
        )

        DevicePerformance.POOR -> Text(
            text = stringResource(Res.string.litert_performance_poor),
            style = MaterialTheme.typography.labelSmall,
            color = StatusColorError,
        )
    }
}

@Composable
private fun ConnectionStatusIndicator(status: ConnectionStatus) {
    when (status) {
        ConnectionStatus.Unknown -> return

        ConnectionStatus.Checking -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.settings_status_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ConnectionStatus.Connected -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.settings_status_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        ConnectionStatus.ErrorQuotaExhausted -> {
            val warningColor = Color(0xFFFF9800)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = warningColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.settings_status_error_quota_exhausted),
                    style = MaterialTheme.typography.bodySmall,
                    color = warningColor,
                )
            }
        }

        ConnectionStatus.ErrorInvalidKey,
        ConnectionStatus.ErrorRateLimited,
        ConnectionStatus.ErrorConnectionFailed,
        ConnectionStatus.Error,
        -> {
            val errorMessage = when (status) {
                ConnectionStatus.ErrorInvalidKey -> stringResource(Res.string.settings_status_error_invalid_key)
                ConnectionStatus.ErrorRateLimited -> stringResource(Res.string.settings_status_error_rate_limited)
                ConnectionStatus.ErrorConnectionFailed -> stringResource(Res.string.settings_status_error_connection_failed)
                else -> stringResource(Res.string.settings_status_error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ApiKeyField(
    apiKey: String,
    onChangeApiKey: (String) -> Unit,
    labelText: String,
    testTag: String? = null,
    singleLine: Boolean = false,
) {
    KaiClearableTextField(
        modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
        value = apiKey,
        onValueChange = onChangeApiKey,
        label = {
            Text(
                labelText,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        singleLine = singleLine,
    )
}

@Composable
private fun MaxTokensSlider(
    modelId: String,
    maxTokens: Int,
    onChangeMaxTokens: (Int) -> Unit,
) {
    var sliderValue by remember(maxTokens) { mutableStateOf(maxTokens.toFloat().coerceIn(512f, 32768f)) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Max Output Tokens",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (sliderValue.roundToInt() <= 512) "Default" else sliderValue.roundToInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val value = sliderValue.roundToInt()
                onChangeMaxTokens(if (value <= 512) 0 else value)
            },
            valueRange = 512f..32768f,
            steps = 62,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Set to 512 for default (provider-specific). Adjust up for longer responses, down for faster responses.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TemperatureSlider(
    modelId: String,
    temperature: Float,
    onChangeTemperature: (Float) -> Unit,
) {
    var sliderValue by remember(temperature) { mutableStateOf(temperature.coerceIn(0.0f, 2.0f)) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Temperature",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "%.2f".format(sliderValue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = (it * 20).roundToInt() / 20f },
            onValueChangeFinished = { onChangeTemperature(sliderValue) },
            valueRange = 0.0f..2.0f,
            steps = 39,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "0.0 — Deterministic",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Default: 0.8",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "2.0 — Creative",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Lower values make output more focused and deterministic. Higher values make it more creative and varied.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
