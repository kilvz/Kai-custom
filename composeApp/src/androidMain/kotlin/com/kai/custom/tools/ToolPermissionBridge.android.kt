package com.kai.custom.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Duration.Companion.seconds

actual class ToolPermissionBridge actual constructor() {
    private val context: Context by inject(Context::class.java)

    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    private val permissionResultFlow = MutableStateFlow<Boolean?>(null)

    private var _pendingPermissions: List<String> = emptyList()
    actual val pendingPermissions: List<String> get() = _pendingPermissions

    actual suspend fun requestPermission(vararg permissions: String): Boolean {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) return true

        _pendingPermissions = permissions.toList()
        permissionResultFlow.value = null
        _permissionRequested.value = true

        val result = withTimeoutOrNull(60.seconds) {
            permissionResultFlow.first { it != null }
        }

        _permissionRequested.value = false
        _pendingPermissions = emptyList()
        return result ?: false
    }

    actual fun onPermissionResult(granted: Boolean) {
        permissionResultFlow.value = granted
    }
}

@Composable
actual fun SetupToolPermissionHandler(controller: ToolPermissionBridge) {
    val permissionRequested by controller.permissionRequested.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        controller.onPermissionResult(permissions.values.all { it })
    }

    LaunchedEffect(permissionRequested) {
        if (permissionRequested) {
            val perms = controller.pendingPermissions.toTypedArray()
            if (perms.isNotEmpty()) {
                launcher.launch(perms)
            }
        }
    }
}
