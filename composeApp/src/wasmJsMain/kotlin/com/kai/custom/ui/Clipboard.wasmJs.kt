package com.kai.custom.ui

import androidx.compose.ui.platform.ClipEntry

internal actual fun clipEntryOfPlainText(text: String): ClipEntry = ClipEntry.withPlainText(text)
