package com.kai.custom

actual fun createDaemonController(): DaemonController = NoOpDaemonController()
