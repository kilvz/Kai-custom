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
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GgufInferenceEngine(
    private val native: GgufNative?,
) : LocalInferenceEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

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
                val ok = native.nativeInit(model.filePath, contextTokens)
                if (!ok) throw IllegalStateException("Failed to initialize GGUF model")
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
            _engineState.value = EngineState.UNINITIALIZED
        }
    }

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
        val messageStrings = messages.map { msg ->
            "${msg.role}|||${sanitizeForLiteRt(msg.content) ?: ""}"
        }.toTypedArray()

        val result = native.nativeChat(
            sanitizeForLiteRt(systemPrompt),
            messageStrings,
            topK,
            topP,
            temperature,
            maxTokens = 512,
        )

        stripThinkBlocks(result)
    }

    override fun getDownloadedModels(): List<DownloadedModel> {
        val modelsDir = getGgufModelsDir()
        if (!modelsDir.exists()) return emptyList()
        return modelsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { modelDir ->
            val ggufFile = modelDir.listFiles()?.firstOrNull { it.name.endsWith(".gguf") }
            if (ggufFile != null) {
                DownloadedModel(
                    id = modelDir.name,
                    displayName = modelDir.name.replace("_", " ").replaceFirstChar { it.uppercase() },
                    filePath = ggufFile.absolutePath,
                    sizeBytes = ggufFile.length(),
                )
            } else null
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

    companion object {
        fun getGgufModelsDir(): File = File(File(getModelStorageDirectory()).parent, "gguf_models")
    }
}
