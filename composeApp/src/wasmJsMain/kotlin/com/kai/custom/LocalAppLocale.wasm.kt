package com.kai.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

actual object LocalAppLocale {
    private val LocalAppLocale = staticCompositionLocalOf { Locale.current }

    actual val current: String
        @Composable get() = LocalAppLocale.current.toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> = LocalAppLocale.provides(Locale.current)
}
