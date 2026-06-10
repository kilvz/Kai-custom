package com.kai.custom.inference

expect fun getModelStorageDirectory(): String

expect fun getModelCacheDirectory(): String

expect fun getAvailableMemoryBytes(): Long

expect fun getTotalMemoryBytes(): Long

expect fun getAvailableDiskSpaceBytes(path: String): Long

expect fun startDownloadNotificationService()

expect fun stopDownloadNotificationService()

expect fun updateDownloadNotificationProgress(percent: Int)

/** Copies a file from a PlatformFile to the model storage directory using
 * platform-specific streaming (SAF ContentResolver on Android). Returns the
 * imported model ID, or null on failure. */
expect fun importPlatformFile(platformFile: io.github.vinceglb.filekit.PlatformFile, isGguf: Boolean): String?
