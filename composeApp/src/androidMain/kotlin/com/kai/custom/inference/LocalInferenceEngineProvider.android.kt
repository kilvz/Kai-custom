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
    private fun isGgufModel(modelId: String?, filePath: String?): Boolean =
        isGgufModel(modelId) || filePath?.endsWith(".gguf") == true

    private fun getGgufEngine(): GgufInferenceEngine {
        if (ggufEngine == null) {
            val native = try {
                GgufPluginManager.ensureLoaded()
            } catch (_: Exception) { null }
            val cpuCount = Runtime.getRuntime().availableProcessors()
            ggufEngine = GgufInferenceEngine(
                native,
                GgufEngineConfig(
                    gpuLayers = 20,         // Offload most layers to GPU (Vulkan)
                    threads = cpuCount.coerceIn(2, 8),
                    batchSize = 512,
                ),
            )
        }
        return ggufEngine!!
    }

    private fun selectEngine(modelId: String?, filePath: String? = null): LocalInferenceEngine {
        return if (isGgufModel(modelId, filePath)) {
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
        if (isGgufModel(model.id, model.filePath)) {
            val engine = getGgufEngine()
            activeGgufModel = model.id
            engine.initialize(model, contextTokens)
        } else {
            activeGgufModel = null
            liteRt.initialize(model, contextTokens)
        }
    }

    override fun updateGpuLayers(modelId: String, gpuLayers: Int) {
        ggufEngine?.updateGpuLayers(modelId, gpuLayers)
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
            val safFile = modelDir.listFiles()?.firstOrNull { it.name.endsWith(".saf") }
            val nameFile = modelDir.listFiles()?.firstOrNull { it.name == "name.txt" }

            val displayName = nameFile?.let { try { it.readText().trim() } catch (_: Exception) { null } }
                ?: ggufFile?.nameWithoutExtension
                    ?.replace("_", " ")?.replace("-", " ")?.replace(".", " ")
                    ?.trim()?.replaceFirstChar { it.uppercase() }
                ?: modelDir.name.replace("_", " ").replaceFirstChar { it.uppercase() }

            if (ggufFile != null) {
                DownloadedModel(
                    id = modelDir.name,
                    displayName = displayName,
                    filePath = ggufFile.absolutePath,
                    sizeBytes = ggufFile.length(),
                )
            } else if (safFile != null) {
                DownloadedModel(
                    id = modelDir.name,
                    displayName = "$displayName (External)",
                    filePath = safFile.readText().trim(),
                    sizeBytes = 0L,
                )
            } else null
        }?.sortedByDescending { it.sizeBytes } ?: emptyList()
    }

    override fun getAvailableModels(): List<LocalModel> = liteRt.getAvailableModels() + GGUF_MODELS

    override fun getFreeSpaceBytes(): Long = liteRt.getFreeSpaceBytes()

    override fun startDownload(model: LocalModel) {
        if (isGgufModel(model.id, model.fileName)) {
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
        val isGguf = isGgufModel(modelId) || isGgufImportedModel(modelId)
        if (isGguf) {
            val engine = getGgufEngine()
            engine?.deleteModel(modelId)
            // Fallback: delete directory directly if engine didn't handle it
            val dir = java.io.File(GgufInferenceEngine.getGgufModelsDir(), modelId)
            if (dir.exists()) dir.deleteRecursively()
        } else {
            liteRt.deleteModel(modelId)
        }
    }

    private fun isGgufImportedModel(modelId: String): Boolean {
        val dir = java.io.File(GgufInferenceEngine.getGgufModelsDir(), modelId)
        return dir.exists()
    }
}
