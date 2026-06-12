package com.kai.custom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal actual fun <T> runBlockingCompat(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T = runBlocking(context, block)
