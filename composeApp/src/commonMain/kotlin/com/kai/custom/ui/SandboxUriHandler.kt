package com.kai.custom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.kai.custom.SandboxController
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SandboxAwareUriHandler(
    private val delegate: UriHandler,
    private val sandboxController: SandboxController,
    private val scope: CoroutineScope,
) : UriHandler {
    override fun openUri(uri: String) {
        val sandboxPath = toSandboxPath(uri)
        if (sandboxPath != null) {
            scope.launch { sandboxController.openFile(sandboxPath) }
        } else {
            delegate.openUri(uri)
        }
    }
}

internal fun toSandboxPath(uri: String): String? {
    val raw = when {
        uri.startsWith("file://") -> uri.removePrefix("file://")
        uri.startsWith("file:") -> uri.removePrefix("file:")
        uri.startsWith("/") -> uri
        else -> return null
    }
    if (!raw.startsWith("/")) return null
    return runCatching { raw.decodeURLPart() }.getOrDefault(raw)
}

@Composable
internal fun rememberSandboxAwareUriHandler(sandboxController: SandboxController): UriHandler {
    val delegate = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    return remember(delegate, sandboxController, scope) {
        SandboxAwareUriHandler(delegate, sandboxController, scope)
    }
}
