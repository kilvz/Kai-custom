package com.kai.custom.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

expect class MicrophonePermissionController() {
    val permissionRequested: StateFlow<Boolean>

    fun hasPermission(): Boolean

    suspend fun requestPermission(): Boolean

    fun onPermissionResult(granted: Boolean)
}

@Composable
expect fun SetupMicrophonePermissionHandler(controller: MicrophonePermissionController)
