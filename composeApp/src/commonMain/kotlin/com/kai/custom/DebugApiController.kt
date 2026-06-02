package com.kai.custom

interface DebugApiController {
    fun start()
    fun stop()
    val isRunning: Boolean
    val isTransitioning: Boolean
}

expect fun createDebugApiController(): DebugApiController
