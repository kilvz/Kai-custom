package com.kai.custom.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GgufInferenceEngine(
    private val native: GgufNative,
) : LocalInferenceEngine {

    private val _engineState = MutableStateFlow(EngineState.UNINITIALIZED)
    override val engineState: StateFlow<EngineState> = _engineState

    override val currentModelId: String? = null
    override val downloadingModelId: StateFlow<String?> = MutableStateFlow(null)
    override val downloadProgress: StateFlow<Float?> = MutableStateFlow(null)
    override val downloadError: StateFlow<DownloadError?> = MutableStateFlow(null)

    override suspend fun initialize(model: DownloadedModel, contextTokens: Int) {
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
            native.nativeRelease()
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
        // Downloads are handled at the app level via existing download infrastructure
    }

    override fun cancelDownload() {}

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
