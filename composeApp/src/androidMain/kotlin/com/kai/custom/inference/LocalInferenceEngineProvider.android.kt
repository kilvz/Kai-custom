package com.kai.custom.inference

actual fun createLocalInferenceEngine(): LocalInferenceEngine? {
    val liteRt = if (android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) LiteRTInferenceEngine() else null
    // GGUF engine is created on-demand by the app when the user selects a GGUF model
    return liteRt
}
