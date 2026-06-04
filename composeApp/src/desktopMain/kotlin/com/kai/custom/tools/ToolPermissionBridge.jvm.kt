package com.kai.custom.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class ToolPermissionBridge actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = MutableStateFlow(false)
    actual val pendingPermissions: List<String> = emptyList()
    actual suspend fun requestPermission(vararg permissions: String): Boolean = true
    actual fun onPermissionResult(granted: Boolean) {}
}

@Composable
actual fun SetupToolPermissionHandler(controller: ToolPermissionBridge) {
}
