package com.kai.custom.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kai.custom.PttTriggerManager
import com.kai.custom.data.ThemeMode
import com.kai.custom.data.languageOptions
import com.kai.custom.isDebugBuild
import com.kai.custom.keyCodeToName
import com.kai.custom.ui.KaiOutlinedTextField
import com.kai.custom.ui.components.KaiSlider
import com.kai.custom.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_arrow_drop_down
import kai.composeapp.generated.resources.settings_daemon_mode
import kai.composeapp.generated.resources.settings_daemon_mode_description
import kai.composeapp.generated.resources.settings_dynamic_ui
import kai.composeapp.generated.resources.settings_dynamic_ui_description
import kai.composeapp.generated.resources.settings_theme
import kai.composeapp.generated.resources.settings_theme_dark
import kai.composeapp.generated.resources.settings_theme_description
import kai.composeapp.generated.resources.settings_theme_light
import kai.composeapp.generated.resources.settings_theme_oled
import kai.composeapp.generated.resources.settings_theme_system
import kai.composeapp.generated.resources.settings_ui_scale
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt

@Composable
internal fun GeneralContent(
    actions: SettingsActions,
    showDaemonToggle: Boolean,
    isDaemonEnabled: Boolean,
    isFloatingBallEnabled: Boolean,
    isDynamicUiEnabled: Boolean,
    themeMode: ThemeMode,
    showUiScale: Boolean,
    uiScale: Float,
    isWakeWordEnabled: Boolean,
    wakeWordPhrase: String,
    wakeWordMode: String,
    wakeWordEnrolled: Boolean,
    isEnrolling: Boolean,
    wakeWordEnrollmentMessage: String,
    pttTriggerKeyCode: Int,
    preferredLanguage: String,
    showDebugApiSection: Boolean = false,
    isDebugApiEnabled: Boolean = false,
    debugApiRunning: Boolean = false,
    debugApiTransitioning: Boolean = false,
    isDebugEndpointEnabled: Boolean = false,
    shizukuPermissionGranted: Boolean = false,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStaggered = maxWidth >= 600.dp
        if (useStaggered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showDaemonToggle) {
                        SettingsCard {
                            DaemonModeToggle(
                                isDaemonEnabled = isDaemonEnabled,
                                onToggleDaemon = actions.onToggleDaemon,
                            )
                        }
                    }
                    if (showDaemonToggle) {
                        SettingsCard {
                            FloatingBallToggle(
                                isFloatingBallEnabled = isFloatingBallEnabled,
                                onToggleFloatingBall = actions.onToggleFloatingBall,
                                shizukuPermissionGranted = shizukuPermissionGranted,
                            )
                        }
                    }
                    SettingsCard {
                        DynamicUiToggle(
                            isDynamicUiEnabled = isDynamicUiEnabled,
                            onToggleDynamicUi = actions.onToggleDynamicUi,
                        )
                    }
                    SettingsCard {
                        ThemeModePicker(
                            themeMode = themeMode,
                            onChangeThemeMode = actions.onChangeThemeMode,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showUiScale) {
                        SettingsCard {
                            UiScaleSection(
                                uiScale = uiScale,
                                onChangeUiScale = actions.onChangeUiScale,
                            )
                        }
                    }
                    SettingsCard {
                        ExportImportSection(
                            onExportSettings = actions.onExportSettings,
                            onPrepareExport = actions.onPrepareExport,
                            onImportSettings = actions.onImportSettings,
                        )
                    }
                    SettingsCard {
                        WakeWordSection(
                            isWakeWordEnabled = isWakeWordEnabled,
                            wakeWordPhrase = wakeWordPhrase,
                            wakeWordMode = wakeWordMode,
                            wakeWordEnrolled = wakeWordEnrolled,
                            isEnrolling = isEnrolling,
                            wakeWordEnrollmentMessage = wakeWordEnrollmentMessage,
                            onToggleWakeWord = actions.onToggleWakeWord,
                            onChangeWakeWordPhrase = actions.onChangeWakeWordPhrase,
                            onChangeWakeWordMode = actions.onChangeWakeWordMode,
                            onEnrollWakeWord = actions.onEnrollWakeWord,
                        )
                    }
                    SettingsCard {
                        PttTriggerSection(
                            pttTriggerKeyCode = pttTriggerKeyCode,
                            onCapture = actions.onCapturePttTrigger,
                            onClear = actions.onClearPttTrigger,
                        )
                    }
                    SettingsCard {
                        LanguageSection(
                            preferredLanguage = preferredLanguage,
                            onChangePreferredLanguage = actions.onChangePreferredLanguage,
                        )
                    }
                    SettingsCard {
                        TtsSettingsSection(onOpenTtsSettings = actions.onOpenTtsSettings)
                    }
                    if (showDebugApiSection) {
                        SettingsCard {
                            DebugApiSection(
                                isDebugApiEnabled = isDebugApiEnabled,
                                debugApiRunning = debugApiRunning,
                                debugApiTransitioning = debugApiTransitioning,
                                onToggleDebugApi = actions.onToggleDebugApi,
                                isDebugEndpointEnabled = isDebugEndpointEnabled,
                                onToggleDebugEndpoint = actions.onToggleDebugEndpoint,
                            )
                        }
                    }
                } // end second column staggered
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (showDaemonToggle) {
                    SettingsCard {
                        DaemonModeToggle(
                            isDaemonEnabled = isDaemonEnabled,
                            onToggleDaemon = actions.onToggleDaemon,
                        )
                    }
                }
                if (showDaemonToggle) {
                    SettingsCard {
                        FloatingBallToggle(
                            isFloatingBallEnabled = isFloatingBallEnabled,
                            onToggleFloatingBall = actions.onToggleFloatingBall,
                            shizukuPermissionGranted = shizukuPermissionGranted,
                        )
                    }
                }
                SettingsCard {
                    DynamicUiToggle(
                        isDynamicUiEnabled = isDynamicUiEnabled,
                        onToggleDynamicUi = actions.onToggleDynamicUi,
                    )
                }
                SettingsCard {
                    ThemeModePicker(
                        themeMode = themeMode,
                        onChangeThemeMode = actions.onChangeThemeMode,
                    )
                }
                if (showUiScale) {
                    SettingsCard {
                        UiScaleSection(
                            uiScale = uiScale,
                            onChangeUiScale = actions.onChangeUiScale,
                        )
                    }
                }
                SettingsCard {
                    ExportImportSection(
                        onExportSettings = actions.onExportSettings,
                        onPrepareExport = actions.onPrepareExport,
                        onImportSettings = actions.onImportSettings,
                    )
                }
                SettingsCard {
                    WakeWordSection(
                        isWakeWordEnabled = isWakeWordEnabled,
                        wakeWordPhrase = wakeWordPhrase,
                        wakeWordMode = wakeWordMode,
                        wakeWordEnrolled = wakeWordEnrolled,
                        isEnrolling = isEnrolling,
                        wakeWordEnrollmentMessage = wakeWordEnrollmentMessage,
                        onToggleWakeWord = actions.onToggleWakeWord,
                        onChangeWakeWordPhrase = actions.onChangeWakeWordPhrase,
                        onChangeWakeWordMode = actions.onChangeWakeWordMode,
                        onEnrollWakeWord = actions.onEnrollWakeWord,
                    )
                }
                SettingsCard {
                    PttTriggerSection(
                        pttTriggerKeyCode = pttTriggerKeyCode,
                        onCapture = actions.onCapturePttTrigger,
                        onClear = actions.onClearPttTrigger,
                    )
                }
                SettingsCard {
                    LanguageSection(
                        preferredLanguage = preferredLanguage,
                        onChangePreferredLanguage = actions.onChangePreferredLanguage,
                    )
                }
                SettingsCard {
                    TtsSettingsSection(onOpenTtsSettings = actions.onOpenTtsSettings)
                }
                if (showDebugApiSection) {
                    SettingsCard {
                        DebugApiSection(
                            isDebugApiEnabled = isDebugApiEnabled,
                            debugApiRunning = debugApiRunning,
                            debugApiTransitioning = debugApiTransitioning,
                            onToggleDebugApi = actions.onToggleDebugApi,
                            isDebugEndpointEnabled = isDebugEndpointEnabled,
                            onToggleDebugEndpoint = actions.onToggleDebugEndpoint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PttTriggerSection(
    pttTriggerKeyCode: Int,
    onCapture: () -> Unit,
    onClear: () -> Unit,
) {
    val isCapturing by PttTriggerManager.captureMode.collectAsState()
    val keyName = if (pttTriggerKeyCode != 0) keyCodeToName(pttTriggerKeyCode) else null

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Push-to-Talk",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Press-and-hold a hardware button to start voice input in interactive mode",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (pttTriggerKeyCode != 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = CenterVertically,
            ) {
                Text(
                    text = "Button: ${keyName ?: pttTriggerKeyCode.toString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        } else {
            Text(
                text = "Button: Logo touch only",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCapture,
            enabled = !isCapturing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isCapturing) {
                Text("Press a button on your device...")
            } else {
                Text(if (pttTriggerKeyCode != 0) "Change button" else "Capture button")
            }
        }
    }
}

@Composable
private fun LanguageSection(
    preferredLanguage: String,
    onChangePreferredLanguage: (String) -> Unit,
) {
    val opt = languageOptions.firstOrNull { it.code == preferredLanguage }
    val selectedLabel = opt?.displayName ?: preferredLanguage
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Language",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Preferred language for AI responses and speech output",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    Icon(
                        modifier = Modifier.handCursor(),
                        imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .handCursor()
                    .clickable { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(16.dp),
            ) {
                languageOptions.forEach { opt ->
                    val isSelected = opt.code == preferredLanguage
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = opt.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        onClick = {
                            expanded = false
                            onChangePreferredLanguage(opt.code)
                        },
                        modifier = Modifier
                            .handCursor()
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .padding(horizontal = 4.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsSettingsSection(onOpenTtsSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Text-to-Speech",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Configure voice engine and language settings for speech output",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenTtsSettings,
        ) {
            Text("Open TTS Settings")
        }
    }
}

@Composable
private fun DaemonModeToggle(
    isDaemonEnabled: Boolean,
    onToggleDaemon: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_daemon_mode),
            description = stringResource(Res.string.settings_daemon_mode_description),
            checked = isDaemonEnabled,
            onCheckedChange = onToggleDaemon,
        )
    }
}

@Composable
private fun FloatingBallToggle(
    isFloatingBallEnabled: Boolean,
    onToggleFloatingBall: (Boolean) -> Unit,
    shizukuPermissionGranted: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = "Floating Assistant",
            description = "Show a draggable floating ball overlay for quick access. Requires Accessibility Service for screen reading and Shizuku for best results.",
            checked = isFloatingBallEnabled,
            onCheckedChange = onToggleFloatingBall,
            actions = {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "Experimental",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            },
        )
        if (isFloatingBallEnabled && !shizukuPermissionGranted) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Shizuku recommended for reliable screen reading. Grant Shizuku permission in the Agent tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicUiToggle(
    isDynamicUiEnabled: Boolean,
    onToggleDynamicUi: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_dynamic_ui),
            description = stringResource(Res.string.settings_dynamic_ui_description),
            checked = isDynamicUiEnabled,
            onCheckedChange = onToggleDynamicUi,
        )
    }
}

@Composable
private fun ThemeModePicker(
    themeMode: ThemeMode,
    onChangeThemeMode: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.System to stringResource(Res.string.settings_theme_system),
        ThemeMode.Light to stringResource(Res.string.settings_theme_light),
        ThemeMode.Dark to stringResource(Res.string.settings_theme_dark),
        ThemeMode.OledBlack to stringResource(Res.string.settings_theme_oled),
    )
    val selectedLabel = options.first { it.first == themeMode }.second
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.settings_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.settings_theme_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    Icon(
                        modifier = Modifier.handCursor(),
                        imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                },
            )
            // Transparent overlay to capture clicks reliably on all platforms
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .handCursor()
                    .clickable { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(16.dp),
            ) {
                options.forEach { (mode, label) ->
                    val isSelected = mode == themeMode
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        onClick = {
                            expanded = false
                            onChangeThemeMode(mode)
                        },
                        modifier = Modifier
                            .handCursor()
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .padding(horizontal = 4.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WakeWordSection(
    isWakeWordEnabled: Boolean,
    wakeWordPhrase: String,
    wakeWordMode: String,
    wakeWordEnrolled: Boolean,
    isEnrolling: Boolean,
    wakeWordEnrollmentMessage: String = "",
    onToggleWakeWord: (Boolean) -> Unit,
    onChangeWakeWordPhrase: (String) -> Unit,
    onChangeWakeWordMode: (String) -> Unit,
    onEnrollWakeWord: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = CenterVertically,
        ) {
            Row(verticalAlignment = CenterVertically) {
                Text(
                    text = "Voice",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "Experimental",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Switch(checked = isWakeWordEnabled, onCheckedChange = onToggleWakeWord)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Wake word detection — say \"hey kai\" to start voice input hands-free",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isWakeWordEnabled) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "This feature is experimental and may not work reliably.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (isWakeWordEnabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = wakeWordPhrase,
                onValueChange = onChangeWakeWordPhrase,
                label = { Text("Wake word phrase") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            // Mode selector
            val modes = listOf("GENERAL" to "General (anyone)", "PERSONAL" to "Personal (your voice)")
            Text(
                text = "Detection mode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { onChangeWakeWordMode(value) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            color = if (wakeWordMode == value) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
            if (wakeWordMode == "PERSONAL") {
                Spacer(Modifier.height(8.dp))
                if (wakeWordEnrolled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = CenterVertically,
                    ) {
                        Text(
                            text = "Your voice enrolled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(
                            onClick = onEnrollWakeWord,
                            enabled = !isEnrolling,
                        ) {
                            if (isEnrolling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("Re-enroll")
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onEnrollWakeWord,
                        enabled = !isEnrolling,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isEnrolling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Enroll your voice")
                        }
                    }
                }
                if (isEnrolling && wakeWordEnrollmentMessage.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = wakeWordEnrollmentMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun UiScaleSection(
    uiScale: Float,
    onChangeUiScale: (Float) -> Unit,
) {
    var sliderValue by remember(uiScale) { mutableStateOf(uiScale) }
    val steps = 14 // 16 snap points from 50% to 200% in 10% increments (14 intermediate)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_ui_scale),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${(sliderValue * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        KaiSlider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChangeUiScale(sliderValue) },
            valueRange = 0.5f..2.0f,
            steps = steps,
        )
    }
}

@Composable
private fun DebugApiSection(
    isDebugApiEnabled: Boolean,
    debugApiRunning: Boolean,
    debugApiTransitioning: Boolean = false,
    onToggleDebugApi: (Boolean) -> Unit,
    isDebugEndpointEnabled: Boolean = false,
    onToggleDebugEndpoint: ((Boolean) -> Unit)? = null,
) {
    val showAdvanced = remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced.value = !showAdvanced.value }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Advanced",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (showAdvanced.value) "▼" else "▶",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (showAdvanced.value) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ToggleableHeadline(
                    title = "Debug API Server",
                    description = if (debugApiRunning) {
                        "Running on 127.0.0.1:18500"
                    } else if (debugApiTransitioning) {
                        "Restarting..."
                    } else {
                        "HTTP server for debugging. Requires daemon. See logcat for auth token."
                    },
                    checked = isDebugApiEnabled,
                    enabled = !debugApiTransitioning,
                    onCheckedChange = onToggleDebugApi,
                )
                if (isDebugBuild.not()) {
                    Text(
                        text = "Debug API is only available in debug builds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (isDebugBuild && onToggleDebugEndpoint != null) {
                    ToggleableHeadline(
                        title = "Debug Endpoint",
                        description = if (isDebugEndpointEnabled) "Enabled" else "Debug-only endpoint",
                        checked = isDebugEndpointEnabled,
                        onCheckedChange = onToggleDebugEndpoint,
                    )
                }
            }
        }
    }
}
