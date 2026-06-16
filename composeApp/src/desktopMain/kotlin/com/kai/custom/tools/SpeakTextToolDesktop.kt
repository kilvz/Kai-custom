package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema
import java.io.File
import java.net.URL
import java.net.URLEncoder
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

object SpeakTextToolDesktop : Tool {
    private var edgeTtsInstalled = false

    override val schema = ToolSchema(
        name = "speak_text",
        description = "Generate speech from text and play it aloud on the system. Uses edge-tts (neural TTS) installed via pip, or native platform TTS as fallback.",
        parameters = mapOf(
            "text" to ParameterSchema(type = "string", description = "The text to speak aloud", required = true),
            "voice" to ParameterSchema(type = "string", description = "Voice (e.g. en-US-AndrewNeural, en-US-AriaNeural). Default: en-US-AndrewNeural", required = false),
        ),
    )

    val toolInfo = CommonTools.speakTextToolInfo

    override suspend fun execute(args: Map<String, Any>): Any {
        val text = args["text"] as? String ?: return mapOf("success" to false, "error" to "text is required")
        val voice = args["voice"] as? String ?: "en-US-AndrewNeural"
        val os = System.getProperty("os.name").lowercase()

        return try {
            if (os.contains("windows")) {
                speakWindows(text)
            } else if (os.contains("linux") || os.contains("mac")) {
                speakViaEdgeTts(text, voice)
            } else {
                mapOf("success" to false, "error" to "Unsupported OS")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Speech failed: ${e.message}")
        }
    }

    private fun speakWindows(text: String): Map<String, Any> {
        val escapedText = text.replace("\"", "\\\"")
        val psCmd = "Add-Type -AssemblyName System.Speech; " +
            "`${'$'}synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
            "`${'$'}synth.SetOutputToDefaultAudioDevice(); " +
            "`${'$'}synth.Speak(\"$escapedText\")"
        val proc = ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-Command",
            psCmd,
        ).redirectErrorStream(true).start()
        proc.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
        return mapOf("success" to true, "message" to "Speech played via Windows TTS")
    }

    private suspend fun speakViaEdgeTts(text: String, voice: String): Map<String, Any> {
        if (!edgeTtsInstalled) {
            val checkProc = ProcessBuilder("bash", "-c", "pip list 2>/dev/null | grep -qi edge-tts && echo INSTALLED || echo MISSING")
                .redirectErrorStream(true).start()
            checkProc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            val checkOut = checkProc.inputStream.reader().readText()
            edgeTtsInstalled = checkOut.contains("INSTALLED", ignoreCase = true)

            if (!edgeTtsInstalled) {
                val installProc = ProcessBuilder("bash", "-c", "pip install edge-tts 2>&1 | tail -5")
                    .redirectErrorStream(true).start()
                val done = installProc.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)
                if (!done || installProc.exitValue() != 0) {
                    return mapOf("success" to false, "error" to "Failed to install edge-tts")
                }
                edgeTtsInstalled = true
            }
        }

        val tmpFile = File(System.getProperty("java.io.tmpdir"), "kai_speech_${System.currentTimeMillis()}.mp3")
        val quotedText = text.replace("'", "'\\''")
        val cmd = "edge-tts --voice \"$voice\" --text '$quotedText' --write-media \"${tmpFile.absolutePath}\" 2>&1"
        val proc = ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start()
        val done = proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)

        if (!done || proc.exitValue() != 0 || !tmpFile.exists() || tmpFile.length() == 0L) {
            return mapOf("success" to false, "error" to "edge-tts failed to generate speech")
        }

        return try {
            val audioStream = AudioSystem.getAudioInputStream(tmpFile)
            val clip = AudioSystem.getClip()
            clip.open(audioStream)
            clip.start()
            val totalMicros = clip.microsecondLength
            Thread.sleep(totalMicros / 1000 + 100)
            clip.close()
            audioStream.close()
            tmpFile.delete()
            mapOf("success" to true, "message" to "Speech played successfully")
        } catch (e: Exception) {
            mapOf("success" to true, "file" to tmpFile.absolutePath, "message" to "Speech file generated at ${tmpFile.name}")
        }
    }
}
