package com.kai.custom.inference

import android.content.Context
import java.io.File
import java.net.URL

object GgufPluginManager {

    private const val PLUGIN_DIR = "plugins"
    private const val PLUGIN_FILENAME = "libgguf_engine.so"

    private var loaded = false

    fun ensurePlugin(context: Context): Boolean {
        if (loaded) return true

        val pluginFile = getPluginFile(context)
        if (!pluginFile.exists()) return false

        return try {
            System.load(pluginFile.absolutePath)
            loaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    fun isPluginLoaded(): Boolean = loaded

    fun getPluginFile(context: Context): File {
        val dir = File(context.filesDir, PLUGIN_DIR)
        dir.mkdirs()
        return File(dir, PLUGIN_FILENAME)
    }
}
