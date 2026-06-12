package com.kai.custom.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

expect class ToolPermissionBridge() {
    val permissionRequested: StateFlow<Boolean>
    val pendingPermissions: List<String>
    suspend fun requestPermission(vararg permissions: String): Boolean
    fun onPermissionResult(granted: Boolean)
}

@Composable
expect fun SetupToolPermissionHandler(controller: ToolPermissionBridge)
