package com.inspiredandroid.kai.network.tools

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource

/**
 * Represents tool information for display in settings.
 * This is decoupled from the Tool interface to allow showing tools
 * even on platforms that don't implement them.
 */
@Immutable
data class ToolInfo(
    val id: String,
    val name: String,
    val description: String,
    val nameRes: StringResource? = null,
    val descriptionRes: StringResource? = null,
    val isEnabled: Boolean = true,
)
