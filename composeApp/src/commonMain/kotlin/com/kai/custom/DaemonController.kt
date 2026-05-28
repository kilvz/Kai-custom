package com.kai.custom

interface DaemonController {
    fun start()
    fun stop()
}

expect fun createDaemonController(): DaemonController
