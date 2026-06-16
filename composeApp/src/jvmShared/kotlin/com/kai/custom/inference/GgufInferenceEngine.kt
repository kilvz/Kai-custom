package com.kai.custom.inference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GgufEngineConfig(
    val gpuLayers: Int = 0,
    val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 16),
    val batchSize: Int = 512,
)

class GgufInferenceEngine(
    private val native: GgufNative?,
    config: GgufEngineConfig = GgufEngineConfig(),
) : LocalInferenceEngine {
    private var config: GgufEngineConfig = config

    override fun updateGpuLayers(modelId: String, gpuLayers: Int) {
        config = config.copy(gpuLayers = gpuLayers.coerceIn(0, 999))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private var activeSafHandle: PlatformSafHandle? = null

    private val _engineState = MutableStateFlow(EngineState.UNINITIALIZED)
    override val engineState: StateFlow<EngineState> = _engineState

    override var currentModelId: String? = null
        private set

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    override val downloadingModelId: StateFlow<String?> = _downloadingModelId

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _downloadError = MutableStateFlow<DownloadError?>(null)
    override val downloadError: StateFlow<DownloadError?> = _downloadError

    override suspend fun initialize(model: DownloadedModel, contextTokens: Int) {
        if (native == null) throw IllegalStateException("GGUF native library not loaded. Please ensure the app was built with GGUF support.")
        withContext(Dispatchers.IO) {
            _engineState.value = EngineState.INITIALIZING
            try {
                var resolvedPath = model.filePath
                // If filePath points to a .saf reference file, read the content URI from it
                if (resolvedPath.endsWith(".saf")) {
                    val safFile = java.io.File(resolvedPath)
                    if (safFile.exists()) {
                        resolvedPath = safFile.readText().trim()
                    }
                }
                if (resolvedPath.startsWith("content://")) {
                    // SAF content:// URIs don't support mmap — copy to local on first load
                    val localFile = java.io.File(
                        java.io.File(getGgufModelsDir(), model.id),
                        "model.gguf",
                    )
                    val copiedPath = resolveSafUriToLocal(resolvedPath, localFile.absolutePath)
                    if (copiedPath != null) {
                        resolvedPath = copiedPath
                    } else {
                        // Fallback: use fd path (may fail on some devices)
                        activeSafHandle = openSafPath(resolvedPath)
                        resolvedPath = activeSafHandle?.let { getSafResolvedPath(it) } ?: resolvedPath
                    }
                }

                val ok = native.nativeInit(resolvedPath, contextTokens, config.gpuLayers, config.threads, config.batchSize)
                if (!ok) throw IllegalStateException("Failed to initialize GGUF model")
                currentModelId = model.id
                _engineState.value = EngineState.READY
            } catch (e: Exception) {
                _engineState.value = EngineState.ERROR
                throw e
            }
        }
    }

    override suspend fun release() {
        withContext(Dispatchers.IO) {
            native?.nativeRelease()
            activeSafHandle?.let { closeSafHandle(it) }
            activeSafHandle = null
            _engineState.value = EngineState.UNINITIALIZED
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun releaseInBackground() {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            release()
        }
    }

    override suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
        tools: List<LocalTool>,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): String = withContext(Dispatchers.IO) {
        if (native == null) throw IllegalStateException("GGUF native library not loaded")
        // GGUF/llama.cpp handles all Unicode natively — no need for sanitizeForLiteRt
        val messageStrings = messages.map { msg ->
            "${msg.role}|||${msg.content}"
        }.toTypedArray()

        val result = native.nativeChat(
            systemPrompt,
            messageStrings,
            topK,
            topP,
            temperature,
            maxTokens = 4096,
        )

        stripThinkBlocks(result)
    }

    override fun getDownloadedModels(): List<DownloadedModel> {
        val modelsDir = getGgufModelsDir()
        if (!modelsDir.exists()) return emptyList()
        return modelsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { modelDir ->
            val ggufFile = modelDir.listFiles()?.firstOrNull { it.name.endsWith(".gguf") }
            val safFile = modelDir.listFiles()?.firstOrNull { it.name.endsWith(".saf") }
            val metaFile = modelDir.listFiles()?.firstOrNull { it.name == "metadata.json" }
            val nameFile = modelDir.listFiles()?.firstOrNull { it.name == "name.txt" }

            // Name from name.txt written during import
            val nameFromFile = nameFile?.let {
                try {
                    it.readText().trim()
                } catch (_: Exception) {
                    null
                }
            }

            // Cached metadata (written on first header parse)
            var metaJson: kotlinx.serialization.json.JsonObject? = null
            if (metaFile != null) {
                try {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    metaJson = json.parseToJsonElement(metaFile.readText()).jsonObject
                } catch (_: Exception) {}
            }

            // If we have a safFile but no metadata.json, try to parse GGUF header now
            val ggufPath: String?
            val isExternal: Boolean
            if (safFile != null) {
                ggufPath = safFile.readText().trim()
                isExternal = true
            } else if (ggufFile != null) {
                ggufPath = ggufFile.absolutePath
                isExternal = false
            } else {
                return@mapNotNull null
            }

            // Parse GGUF header on first discovery if metadata.json is missing
            if (metaJson == null && native != null) {
                try {
                    var resolvePath = ggufPath
                    if (resolvePath.startsWith("content://")) {
                        val handle = openSafPath(resolvePath)
                        if (handle != null) resolvePath = getSafResolvedPath(handle)
                    }
                    val raw = native.nativeGetModelInfo(resolvePath)
                    if (!raw.contains("\"error\"")) {
                        File(modelDir, "metadata.json").writeText(raw)
                        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        metaJson = json.parseToJsonElement(raw).jsonObject
                    }
                } catch (_: Exception) {}
            }

            // Extract display name from metadata
            val metaName = metaJson?.let { obj ->
                try {
                    obj["general.name"]?.jsonPrimitive?.contentOrNull
                } catch (_: Exception) {
                    null
                }
            }
            val baseName = nameFromFile ?: metaName
                ?: modelDir.name.replace("_", " ").replaceFirstChar { it.uppercase() }

            // File size from metadata, model.size, or filesystem
            val sizeBytes = metaJson?.let { obj ->
                try {
                    obj["_total_file_size"]?.jsonPrimitive?.longOrNull
                } catch (_: Exception) {
                    null
                }
            } ?: (modelDir.listFiles()?.firstOrNull { it.name == "model.size" }?.let { f ->
                try { f.readText().trim().toLongOrNull() } catch (_: Exception) { null }
            }) ?: (ggufFile?.length() ?: 0L)

            DownloadedModel(
                id = modelDir.name,
                displayName = if (isExternal) "$baseName (External)" else baseName,
                filePath = ggufPath,
                sizeBytes = sizeBytes,
            )
        }?.sortedByDescending { it.sizeBytes } ?: emptyList()
    }

    override fun getAvailableModels(): List<LocalModel> = GGUF_MODELS

    override fun getFreeSpaceBytes(): Long {
        val dir = getGgufModelsDir()
        if (!dir.exists()) return 0L
        return dir.freeSpace
    }

    override fun startDownload(model: LocalModel) {
        cancelDownload()
        downloadJob = scope.launch {
            _downloadingModelId.value = model.id
            _downloadProgress.value = 0f
            _downloadError.value = null

            try {
                val modelsDir = getGgufModelsDir()
                modelsDir.mkdirs()
                val modelDir = File(modelsDir, model.id)
                modelDir.mkdirs()
                val targetFile = File(modelDir, model.fileName)
                val tempFile = File(modelsDir, "${model.id}.tmp")
                var lastNotifiedPercent = -1

                @Suppress("DEPRECATION")
                val connection = URL(model.downloadUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    throw IOException("Download failed: HTTP $responseCode")
                }

                val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: model.sizeBytes
                val buffer = ByteArray(65536)
                var totalBytesRead = 0L

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        while (true) {
                            ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val percent = (totalBytesRead * 100 / contentLength).toInt().coerceIn(1, 100)
                            if (percent != lastNotifiedPercent) {
                                lastNotifiedPercent = percent
                                _downloadProgress.value = percent / 100f
                            }
                        }
                    }
                }
                connection.disconnect()

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _downloadError.value = DownloadError.NETWORK_ERROR
            } finally {
                _downloadingModelId.value = null
                _downloadProgress.value = null
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.IO) {
            val modelDir = File(getGgufModelsDir(), modelId)
            if (modelDir.exists()) modelDir.deleteRecursively()
        }
    }

    /** Reads GGUF header metadata from a model path without loading the full model.
     * Returns a JSON string with keys like general.architecture, general.name,
     * general.file_type, general.size_label, <arch>.context_length, etc.
     * Returns null if the native method is unavailable or parsing fails. */
    fun getModelInfo(modelPath: String): String? = try {
        var resolvedPath = modelPath
        if (resolvedPath.endsWith(".saf")) {
            val safFile = java.io.File(resolvedPath)
            if (safFile.exists()) resolvedPath = safFile.readText().trim()
        }
        if (resolvedPath.startsWith("content://")) {
            val handle = openSafPath(resolvedPath)
            if (handle != null) resolvedPath = getSafResolvedPath(handle)
        }
        native?.nativeGetModelInfo(resolvedPath)
    } catch (e: Exception) {
        null
    }

    companion object {
        fun getGgufModelsDir(): File = File(File(getModelStorageDirectory()).parent, "gguf_models")
    }
}
