package com.kai.custom.inference

import io.github.vinceglb.filekit.PlatformFile

expect class PlatformSafHandle

expect fun getModelStorageDirectory(): String
expect fun getModelCacheDirectory(): String
expect fun getAvailableMemoryBytes(): Long
expect fun getTotalMemoryBytes(): Long
expect fun getAvailableDiskSpaceBytes(path: String): Long
expect fun startDownloadNotificationService()
expect fun stopDownloadNotificationService()
expect fun updateDownloadNotificationProgress(percent: Int)

expect fun openSafPath(path: String): PlatformSafHandle?
expect fun getSafResolvedPath(handle: PlatformSafHandle): String
expect fun closeSafHandle(handle: PlatformSafHandle)

expect fun importPlatformFile(platformFile: PlatformFile, isGguf: Boolean): String?

/** Opens a SAF file picker and copies the selected file to the model directory.
 * Returns the model ID on success. The actual file content is streamed, not referenced. */
expect fun importSafFile(uri: String, isGguf: Boolean): String?

/** Links a GGUF model by URI reference without copying the file.
 * Creates a `.saf` reference file in the gguf_models directory containing
 * the content:// URI. The model can then be loaded via [openSafPath].
 * Returns the model ID on success, null on failure. */
expect fun linkGgufExternal(uri: String, displayName: String, sizeBytes: Long): String?

/** Copies a file at a content:// URI to a local file path.
 * Returns the local path on success, or null on failure.
 * Used to materialize SAF-referenced files for libraries that need
 * direct file-system access (e.g. mmap). */
expect fun resolveSafUriToLocal(uri: String, localPath: String): String?

@androidx.compose.runtime.Composable
expect fun rememberSafFilePicker(
    extensions: List<String>,
    onResult: (uriOrPath: String?, displayName: String?, sizeBytes: Long) -> Unit,
): () -> Unit
