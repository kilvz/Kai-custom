@file:OptIn(ExperimentalMaterial3Api::class)

package com.kai.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.kai.custom.data.AppSettings
import com.kai.custom.data.ThemeMode
import com.kai.custom.tools.ActivityResultBridge
import com.kai.custom.tools.CalendarPermissionController
import com.kai.custom.tools.MicrophonePermissionController
import com.kai.custom.tools.NotificationPermissionController
import com.kai.custom.tools.SetupActivityResultHandler
import com.kai.custom.tools.SetupCalendarPermissionHandler
import com.kai.custom.tools.SetupMicrophonePermissionHandler
import com.kai.custom.tools.SetupNotificationPermissionHandler
import com.kai.custom.tools.SetupSmsPermissionHandler
import com.kai.custom.tools.SetupSmsSendPermissionHandler
import com.kai.custom.tools.SetupToolPermissionHandler
import com.kai.custom.tools.SmsPermissionController
import com.kai.custom.tools.SmsSendPermissionController
import com.kai.custom.tools.ToolPermissionBridge
import com.kai.custom.ui.DarkColorScheme
import com.kai.custom.ui.LightColorScheme
import com.kai.custom.ui.Theme
import com.kai.custom.ui.chat.ChatScreen
import com.kai.custom.ui.chat.ChatViewModel
import com.kai.custom.ui.components.FullScreenImageHost
import com.kai.custom.ui.handCursor
import com.kai.custom.ui.rememberSandboxAwareUriHandler
import com.kai.custom.ui.settings.SettingsScreen
import com.kai.custom.ui.withBlackBackground
import com.kai.custom.wakeword.WakeWordController
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tab_chat
import kai.composeapp.generated.resources.tab_settings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.experimental.ExperimentalVoiceApi
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.Module
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

@Serializable
@SerialName("home")
object Home

@Serializable
@SerialName("settings")
data class Settings(val tab: String = "")

@Composable
fun App(
    navController: NavHostController,
    lightColorScheme: ColorScheme = LightColorScheme,
    darkColorScheme: ColorScheme = DarkColorScheme,
    textToSpeech: TextToSpeechInstance? = null,
    isKoinStarted: Boolean = false,
    onAppOpens: ((Int) -> Unit)? = null,
    extraKoinModules: List<Module> = emptyList(),
) {
    setSingletonImageLoaderFactory { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .build()
    }

    // Reuse global Koin if already started (Android Application class),
    // otherwise create a new instance (iOS, Desktop, Wasm).
    if (isKoinStarted) {
        AppContent(navController, lightColorScheme, darkColorScheme, textToSpeech, onAppOpens)
    } else {
        KoinApplication(
            configuration = koinConfiguration {
                modules(appModule + extraKoinModules)
            },
        ) {
            AppContent(navController, lightColorScheme, darkColorScheme, textToSpeech, onAppOpens)
        }
    }
}

@Composable
private fun AppContent(
    navController: NavHostController,
    lightColorScheme: ColorScheme,
    darkColorScheme: ColorScheme,
    textToSpeech: TextToSpeechInstance?,
    onAppOpens: ((Int) -> Unit)?,
) {
    val appSettings = koinInject<AppSettings>()

    // Track app opens after Koin is initialized
    onAppOpens?.let { callback ->
        LaunchedEffect(Unit) {
            callback(appSettings.trackAppOpen())
        }
    }

    // Set up permission handlers
    val calendarPermissionController = koinInject<CalendarPermissionController>()
    SetupCalendarPermissionHandler(calendarPermissionController)

    val notificationPermissionController = koinInject<NotificationPermissionController>()
    SetupNotificationPermissionHandler(notificationPermissionController)

    val smsPermissionController = koinInject<SmsPermissionController>()
    SetupSmsPermissionHandler(smsPermissionController)

    val smsSendPermissionController = koinInject<SmsSendPermissionController>()
    SetupSmsSendPermissionHandler(smsSendPermissionController)

    val microphonePermissionController = koinInject<MicrophonePermissionController>()
    SetupMicrophonePermissionHandler(microphonePermissionController)

    val toolPermissionBridge = koinInject<ToolPermissionBridge>()
    SetupToolPermissionHandler(toolPermissionBridge)

    val activityResultBridge = koinInject<ActivityResultBridge>()
    SetupActivityResultHandler(activityResultBridge)

    // Wake word — start/stop listening when the Voice toggle or mode changes
    val wakeWordController = koinInject<WakeWordController>()
    val isWakeWordEnabled by appSettings.wakeWordEnabledFlow.collectAsStateWithLifecycle(false)
    val wakeWordMode by appSettings.wakeWordModeFlow.collectAsStateWithLifecycle(appSettings.getWakeWordMode())
    LaunchedEffect(isWakeWordEnabled, wakeWordMode) {
        if (isWakeWordEnabled) {
            val phrase = appSettings.getWakeWordPhrase()
            val mode = appSettings.getWakeWordMode()
            val template = appSettings.getWakeWordTemplate()
            wakeWordController.startListening(phrase, com.kai.custom.wakeword.WakeWordMode.valueOf(mode), template)
        } else {
            wakeWordController.stopListening()
        }
    }

    // Set TTS voice to match preferred language
    @OptIn(ExperimentalVoiceApi::class)
    LaunchedEffect(textToSpeech) {
        val tts = textToSpeech ?: return@LaunchedEffect
        val preferredLang = appSettings.getPreferredLanguage()
        val matchingVoice = tts.voices
            .firstOrNull { it.languageTag.startsWith(preferredLang) }
        if (matchingVoice != null) {
            tts.currentVoice = matchingVoice
        }
    }

    val uiScale by appSettings.uiScaleFlow.collectAsStateWithLifecycle()
    val defaultDensity = LocalDensity.current
    val scaledDensity = remember(defaultDensity, uiScale) {
        Density(defaultDensity.density * uiScale, defaultDensity.fontScale)
    }

    val themeMode by appSettings.themeModeFlow.collectAsStateWithLifecycle()
    val systemInDark = isSystemInDarkTheme()
    val effectiveColorScheme = when (themeMode) {
        ThemeMode.System -> if (systemInDark) darkColorScheme else lightColorScheme
        ThemeMode.Light -> lightColorScheme
        ThemeMode.Dark -> darkColorScheme
        ThemeMode.OledBlack -> darkColorScheme.withBlackBackground()
    }

    val sandboxController = koinInject<SandboxController>()
    val sandboxAwareUriHandler = rememberSandboxAwareUriHandler(sandboxController)

    val preferredLanguage by appSettings.preferredLanguageFlow.collectAsStateWithLifecycle()
    LaunchedEffect(preferredLanguage) {
        customAppLocale = preferredLanguage
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalUriHandler provides sandboxAwareUriHandler,
    ) {
        AppEnvironment {
            Theme(colorScheme = effectiveColorScheme) {
                FullScreenImageHost {
                    val chatViewModel: ChatViewModel = koinViewModel()
                    val showTabBar = currentPlatform !is Platform.Mobile
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val isHome = currentBackStackEntry?.destination?.route == "home"

                    val navigationTabBar: @Composable () -> Unit = {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        val count = 2
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = isHome,
                                onClick = {
                                    navController.navigate(Home) {
                                        popUpTo(Home) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = if (isRtl) count - 1 else 0, count = count),
                                modifier = Modifier.handCursor(),
                            ) {
                                Text(stringResource(Res.string.tab_chat))
                            }
                            SegmentedButton(
                                selected = !isHome,
                                onClick = {
                                    navController.navigate(Settings(tab = "")) {
                                        popUpTo(Home)
                                        launchSingleTop = true
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = if (isRtl) 0 else count - 1, count = count),
                                modifier = Modifier.handCursor(),
                            ) {
                                Text(stringResource(Res.string.tab_settings))
                            }
                        }
                    }

                    NavHost(
                        navController,
                        startDestination = Home,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    ) {
                        composable<Home> {
                            ChatScreen(
                                viewModel = chatViewModel,
                                textToSpeech = textToSpeech,
                                onNavigateToSettings = { tab ->
                                    navController.navigate(Settings(tab = tab))
                                },
                                isSandboxAvailable = currentPlatform is Platform.Mobile.Android,
                                isSshAvailable = currentPlatform is Platform.Mobile.Android,
                                navigationTabBar = if (showTabBar) navigationTabBar else null,
                            )
                        }
                        composable<Settings> { backStackEntry ->
                            val settingsRoute: Settings = backStackEntry.toRoute()
                            if (showTabBar) {
                                DisposableEffect(Unit) {
                                    onDispose {
                                        chatViewModel.refreshSettings()
                                    }
                                }
                            }
                            SettingsScreen(
                                initialTab = settingsRoute.tab,
                                onNavigateBack = {
                                    chatViewModel.refreshSettings()
                                    navController.navigateUp()
                                },
                                navigationTabBar = if (showTabBar) navigationTabBar else null,
                            )
                        }
                    }
                }
            }
        }
    }
}
