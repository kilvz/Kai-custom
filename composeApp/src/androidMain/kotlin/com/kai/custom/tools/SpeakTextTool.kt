package com.kai.custom.tools

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.kai.custom.SandboxSessions
import com.kai.custom.data.currentConversationIdOrNull
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.sandbox.LinuxSandboxManager
import com.kai.custom.sandbox.SandboxState
import com.kai.custom.sandbox.resolveSandboxAbsolute
import org.koin.java.KoinJavaComponent.inject
import java.io.File

object SpeakTextTool : Tool {
    private val context: Context by inject(Context::class.java)
    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)

    private var edgeTtsInstalled = false

    val toolInfo = CommonTools.speakTextToolInfo

    override val schema = ToolSchema(
        name = "speak_text",
        description = "Generate speech from text using edge-tts (neural TTS) and play it on the device. The sandbox must be installed. Installs edge-tts automatically on first use.",
        parameters = mapOf(
            "text" to ParameterSchema(type = "string", description = "The text to speak aloud", required = true),
            "voice" to ParameterSchema(type = "string", description = "Voice to use (e.g. en-US-AndrewNeural, en-US-AriaNeural, en-GB-SoniaNeural, de-DE-KatjaNeural). Default: en-US-AndrewNeural", required = false),
            "rate" to ParameterSchema(type = "string", description = "Speaking rate adjustment (e.g. +0%, -10%, +20%). Default: +0%", required = false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val text = args["text"] as? String
            ?: return mapOf("success" to false, "error" to "text is required")

        if (sandboxManager.state.value !is SandboxState.Ready) {
            return mapOf("success" to false, "error" to "Linux sandbox is not installed. Set it up in Settings > Sandbox.")
        }

        val voice = args["voice"] as? String ?: "en-US-AndrewNeural"
        val rate = args["rate"] as? String ?: "+0%"

        val sessionId = currentConversationIdOrNull() ?: SandboxSessions.DEFAULT
        val shell = sandboxManager.shellFor(sessionId)

        if (!edgeTtsInstalled) {
            val checkResult = shell.run(
                command = "pip list 2>/dev/null | grep -qi edge-tts && echo INSTALLED || echo MISSING",
                timeoutSeconds = 15,
            )
            val checkOut = (checkResult["stdout"] as? String).orEmpty()
            edgeTtsInstalled = checkOut.contains("INSTALLED", ignoreCase = true)

            if (!edgeTtsInstalled) {
                val installResult = shell.run(
                    command = "pip install edge-tts 2>&1 | tail -5",
                    timeoutSeconds = 90,
                )
                if (installResult["success"] != true) {
                    val stderr = (installResult["stderr"] as? String).orEmpty()
                    val stdout = (installResult["stdout"] as? String).orEmpty()
                    return mapOf(
                        "success" to false,
                        "error" to "Failed to install edge-tts: ${stderr.ifEmpty { stdout }}",
                    )
                }
                edgeTtsInstalled = true
            }
        }

        val outputFile = "/root/kai_speech.mp3"
        val cmd = "edge-tts --voice \"$voice\" --rate \"$rate\" --text ${shellQuote(text)} --write-media \"$outputFile\" 2>&1"
        val result = shell.run(
            command = cmd,
            timeoutSeconds = 60,
        )

        if (result["success"] != true) {
            val stderr = (result["stderr"] as? String).orEmpty()
            val stdout = (result["stdout"] as? String).orEmpty()
            return mapOf(
                "success" to false,
                "error" to "edge-tts failed: ${stderr.ifEmpty { stdout }}",
            )
        }

        val sizeResult = shell.run(
            command = "stat -c%s \"$outputFile\" 2>/dev/null || echo 0",
            timeoutSeconds = 10,
        )
        val fileSize = ((sizeResult["stdout"] as? String)?.trim()?.toLongOrNull()) ?: 0L

        if (fileSize == 0L) {
            return mapOf(
                "success" to false,
                "error" to "Speech generation produced an empty file",
            )
        }

        val hostFile = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, outputFile)
        if (hostFile == null || !hostFile.exists()) {
            return mapOf(
                "success" to true,
                "file" to outputFile,
                "file_size" to fileSize,
                "message" to "Speech file generated but could not be located on host for playback.",
            )
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .build()

        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(audioAttributes)
            mp.setOnPreparedListener { player ->
                player.start()
            }
            mp.setOnCompletionListener { player ->
                audioManager.abandonAudioFocusRequest(focusRequest)
                player.release()
            }
            mp.setOnErrorListener { player, _, _ ->
                audioManager.abandonAudioFocusRequest(focusRequest)
                player.release()
                true
            }
            mp.setDataSource(hostFile.absolutePath)
            audioManager.requestAudioFocus(focusRequest)
            mp.prepareAsync()

            mapOf(
                "success" to true,
                "file" to outputFile,
                "file_size" to fileSize,
                "message" to "Speech generated and playing now.",
            )
        } catch (e: Exception) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            mapOf(
                "success" to false,
                "error" to "Speech generated but playback failed: ${e.message}",
                "file" to outputFile,
                "file_size" to fileSize,
            )
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
