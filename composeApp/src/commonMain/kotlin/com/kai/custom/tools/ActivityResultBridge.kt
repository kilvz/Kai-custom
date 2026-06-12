package com.kai.custom.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

data class ActivityResultData(
    val success: Boolean,
    val resultCode: Int = -1,
    val dataString: String? = null,
    val error: String? = null,
)

/**
 * Bridge for launching Android activities (camera, screen capture, etc.) and
 * returning results to coroutine-based tool code. Uses the same signal+flow
 * pattern as ToolPermissionBridge: a composable observes the pending launch
 * signal, launches the ActivityResultLauncher, and calls back onResult().
 */
expect class ActivityResultBridge() {
    val launchTriggered: StateFlow<Boolean>
    val pendingAction: String?
    val pendingDataUri: String?
    val pendingPackage: String?
    val pendingClass: String?
    val pendingMimeType: String?
    val pendingRequestCode: Int

    suspend fun launchActivityForResult(
        action: String,
        dataUri: String? = null,
        packageName: String? = null,
        className: String? = null,
        mimeType: String? = null,
        requestCode: Int = 0,
    ): ActivityResultData?

    fun onActivityResult(resultCode: Int, dataString: String?)
}

@Composable
expect fun SetupActivityResultHandler(bridge: ActivityResultBridge)
