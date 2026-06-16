package com.kai.custom.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class ActivityResultBridge actual constructor() {
    actual val launchTriggered: StateFlow<Boolean> = MutableStateFlow(false)
    actual val pendingAction: String? = null
    actual val pendingDataUri: String? = null
    actual val pendingPackage: String? = null
    actual val pendingClass: String? = null
    actual val pendingMimeType: String? = null
    actual val pendingRequestCode: Int = 0
    actual suspend fun launchActivityForResult(
        action: String,
        dataUri: String?,
        packageName: String?,
        className: String?,
        mimeType: String?,
        requestCode: Int,
    ): ActivityResultData? = null
    actual fun onActivityResult(resultCode: Int, dataString: String?) {}
}

@Composable
actual fun SetupActivityResultHandler(bridge: ActivityResultBridge) {}
