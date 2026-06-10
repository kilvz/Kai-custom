package com.kai.custom.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

actual fun createLocalInferenceEngine(): LocalInferenceEngine? {
    val liteRt = if (android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) LiteRTInferenceEngine() else null
    if (liteRt != null) return CompositeEngine(liteRt)
    return null
}

/**
 * Delegates to LiteRT for standard models and GgufInferenceEngine for GGUF models.
 * The GGUF engine is created on-demand when a GGUF model is selected.
 */
private class CompositeEngine(
    private val liteRt: LiteRTInferenceEngine,
) : LocalInferenceEngine {

    private var ggufEngine: GgufInferenceEngine? = null
    private var activeGgufModel: String? = null

    private fun isGgufModel(modelId: String?): Boolean = modelId?.startsWith("gguf_") == true

    private fun getGgufEngine(): GgufInferenceEngine {
        if (ggufEngine == null) {
            val native = try {
                GgufPluginManager.ensureLoaded()
            } catch (_: Exception) { null }
            ggufEngine = GgufInferenceEngine(native)
        }
        return ggufEngine!!
    }

    private fun selectEngine(modelId: String?): LocalInferenceEngine {
        return if (isGgufModel(modelId)) {
            getGgufEngine()
        } else {
            liteRt
        }
    }

    override val engineState: StateFlow<EngineState>
        get() = ggufEngine?.engineState ?: liteRt.engineState

    override val currentModelId: String?
        get() = activeGgufModel ?: liteRt.currentModelId

    override val downloadingModelId: StateFlow<String?>
        get() = ggufEngine?.downloadingModelId ?: liteRt.downloadingModelId
    override val downloadProgress: StateFlow<Float?>
        get() = ggufEngine?.downloadProgress ?: liteRt.downloadProgress
    override val downloadError: StateFlow<DownloadError?>
        get() = ggufEngine?.downloadError ?: liteRt.downloadError

    override suspend fun initialize(model: DownloadedModel, contextTokens: Int) {
        if (isGgufModel(model.id)) {
            val engine = getGgufEngine()
            activeGgufModel = model.id
            engine.initialize(model, contextTokens)
        } else {
            activeGgufModel = null
            liteRt.initialize(model, contextTokens)
        }
    }

    override suspend fun release() {
        ggufEngine?.release()
        liteRt.release()
    }

    override fun releaseInBackground() {
        ggufEngine?.releaseInBackground()
        liteRt.releaseInBackground()
    }

    override suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
        tools: List<LocalTool>,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): String = withContext(Dispatchers.IO) {
        // Determine which engine based on the model that's currently initialized
        if (activeGgufModel != null && ggufEngine != null) {
            ggufEngine!!.chat(messages, systemPrompt, tools, temperature, topK, topP)
        } else {
            liteRt.chat(messages, systemPrompt, tools, temperature, topK, topP)
        }
    }

    override fun getDownloadedModels(): List<DownloadedModel> {
        val liteRtModels = liteRt.getDownloadedModels()
        val ggufModels = (ggufEngine?.getDownloadedModels() ?: scanGgufDir())
        return liteRtModels + ggufModels
    }

    private fun scanGgufDir(): List<DownloadedModel> {
        val dir = GgufInferenceEngine.getGgufModelsDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isDirectory }?.mapNotNull { modelDir ->
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

    override fun getAvailableModels(): List<LocalModel> = liteRt.getAvailableModels() + GGUF_MODELS

    override fun getFreeSpaceBytes(): Long = liteRt.getFreeSpaceBytes()

    override fun startDownload(model: LocalModel) {
        if (isGgufModel(model.id)) {
            getGgufEngine().startDownload(model)
        } else {
            liteRt.startDownload(model)
        }
    }
    override fun cancelDownload() { 
        ggufEngine?.cancelDownload()
        liteRt.cancelDownload() 
    }

    override suspend fun deleteModel(modelId: String) {
        if (isGgufModel(modelId)) {
            ggufEngine?.deleteModel(modelId)
        } else {
            liteRt.deleteModel(modelId)
        }
    }
}
