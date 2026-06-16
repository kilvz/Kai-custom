package com.kai.custom.tools

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

actual class ActivityResultBridge actual constructor() {
    private var pendingDeferred = CompletableDeferred<ActivityResultData?>()
    private val _launchTriggered = MutableStateFlow(false)
    private var _pendingAction: String? = null
    private var _pendingDataUri: String? = null
    private var _pendingPackage: String? = null
    private var _pendingClass: String? = null
    private var _pendingMimeType: String? = null
    private var _pendingRequestCode: Int = 0
    private var _pendingIntent: Intent? = null
    private var _pendingResultIntent: Intent? = null

    actual val launchTriggered: StateFlow<Boolean> = _launchTriggered
    actual val pendingAction: String? get() = _pendingAction
    actual val pendingDataUri: String? get() = _pendingDataUri
    actual val pendingPackage: String? get() = _pendingPackage
    actual val pendingClass: String? get() = _pendingClass
    actual val pendingMimeType: String? get() = _pendingMimeType
    actual val pendingRequestCode: Int get() = _pendingRequestCode
    val pendingIntent: Intent? get() = _pendingIntent
    val pendingResultIntent: Intent? get() = _pendingResultIntent

    actual suspend fun launchActivityForResult(
        action: String,
        dataUri: String?,
        packageName: String?,
        className: String?,
        mimeType: String?,
        requestCode: Int,
    ): ActivityResultData? {
        _pendingIntent = null
        pendingDeferred = CompletableDeferred()
        _pendingAction = action
        _pendingDataUri = dataUri
        _pendingPackage = packageName
        _pendingClass = className
        _pendingMimeType = mimeType
        _pendingRequestCode = requestCode
        _launchTriggered.value = true
        return withTimeoutOrNull(60.seconds) {
            pendingDeferred.await()
        }
    }

    /** Launch a pre-built intent (e.g. MediaProjection screen capture) and return the result. */
    suspend fun launchIntentForResult(intent: Intent): ActivityResultData? {
        _pendingAction = null
        _pendingDataUri = null
        _pendingPackage = null
        _pendingClass = null
        _pendingMimeType = null
        _pendingResultIntent = null
        _pendingIntent = intent
        pendingDeferred = CompletableDeferred()
        _launchTriggered.value = true
        val result = withTimeoutOrNull(60.seconds) {
            pendingDeferred.await()
        }
        _pendingIntent = null
        _launchTriggered.value = false
        return result
    }

    actual fun onActivityResult(resultCode: Int, dataString: String?) {
        pendingDeferred.complete(
            ActivityResultData(
                success = resultCode == android.app.Activity.RESULT_OK,
                resultCode = resultCode,
                dataString = dataString,
            ),
        )
        _launchTriggered.value = false
        _pendingAction = null
        _pendingDataUri = null
        _pendingPackage = null
        _pendingClass = null
        _pendingMimeType = null
        _pendingIntent = null
    }

    /** Called by the composable handler with the full result Intent */
    fun onActivityResultWithIntent(resultCode: Int, data: Intent?) {
        _pendingResultIntent = data
        val dataString = data?.data?.toString() ?: data?.extras?.getString("data")
        onActivityResult(resultCode, dataString)
    }

    fun cancelPending() {
        pendingDeferred.complete(
            ActivityResultData(success = false, error = "Cancelled"),
        )
        _launchTriggered.value = false
        _pendingIntent = null
    }
}

@Composable
actual fun SetupActivityResultHandler(bridge: ActivityResultBridge) {
    val launchTriggered by bridge.launchTriggered.collectAsState()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        bridge.onActivityResultWithIntent(result.resultCode, result.data)
    }
    LaunchedEffect(launchTriggered) {
        if (launchTriggered) {
            val prebuilt = bridge.pendingIntent
            val intent = if (prebuilt != null) {
                prebuilt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                Intent(bridge.pendingAction ?: Intent.ACTION_VIEW).apply {
                    bridge.pendingDataUri?.let { data = android.net.Uri.parse(it) }
                    val pkg = bridge.pendingPackage
                    val cls = bridge.pendingClass
                    if (pkg != null && cls != null) {
                        setClassName(pkg, cls)
                    } else {
                        pkg?.let { `package` = it }
                    }
                    bridge.pendingMimeType?.let { type = it }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            try {
                launcher.launch(intent)
            } catch (_: Exception) {
                bridge.cancelPending()
            }
        }
    }
}
