package com.kai.custom

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal actual fun <T> runBlockingCompat(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T = error("runBlocking is not available on WasmJS")
