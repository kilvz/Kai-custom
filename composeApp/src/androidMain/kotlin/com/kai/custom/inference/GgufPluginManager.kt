package com.kai.custom.inference

object GgufPluginManager {

    private const val LIB_LLAMA = "llama"
    private const val LIB_GGUF = "gguf_engine"

    private var nativeLoaded = false
    private var nativeInstance: GgufNative? = null

    @Synchronized
    fun ensureLoaded(): GgufNative? {
        if (nativeInstance != null) return nativeInstance

        return try {
            System.loadLibrary(LIB_LLAMA)
            System.loadLibrary(LIB_GGUF)
            nativeLoaded = true
            val instance = GgufNative()
            nativeInstance = instance
            instance
        } catch (e: UnsatisfiedLinkError) {
            null
        }
    }

    fun isLoaded(): Boolean = nativeLoaded

    fun reset() {
        nativeInstance?.nativeRelease()
        nativeInstance = null
        nativeLoaded = false
    }
}
