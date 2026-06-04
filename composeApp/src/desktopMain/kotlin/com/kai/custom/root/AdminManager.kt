package com.kai.custom.root

import java.io.File

object AdminManager {

    fun isAdmin(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return try {
            when {
                os.contains("win") -> {
                    val proc = Runtime.getRuntime().exec(arrayOf("net", "session"))
                    proc.waitFor() == 0
                }
                os.contains("mac") || os.contains("nix") || os.contains("nux") -> {
                    val proc = Runtime.getRuntime().exec(arrayOf("id", "-u"))
                    val uid = proc.inputStream.bufferedReader().readText().trim()
                    uid == "0"
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun relaunchAsAdmin(args: List<String> = emptyList()): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return try {
            when {
                os.contains("win") -> elevateWindows(args)
                os.contains("mac") -> elevateMac(args)
                os.contains("nix") || os.contains("nux") -> elevateLinux(args)
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun runAsAdmin(command: String): Result<String> {
        val os = System.getProperty("os.name").lowercase()
        return try {
            val result = when {
                os.contains("win") -> {
                    val script = createTempScript(".ps1", """
                        Start-Process -Verb RunAs -FilePath "cmd.exe" -ArgumentList "/c $command" -Wait
                    """.trimIndent())
                    val proc = Runtime.getRuntime().exec(arrayOf("powershell", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath))
                    val output = proc.inputStream.bufferedReader().readText() + proc.errorStream.bufferedReader().readText()
                    script.delete()
                    Result.success(output.trim())
                }
                os.contains("mac") -> {
                    val escaped = command.replace("\\", "\\\\").replace("\"", "\\\"")
                    val proc = Runtime.getRuntime().exec(arrayOf(
                        "osascript", "-e",
                        "do shell script \"$escaped\" with administrator privileges"
                    ))
                    val output = proc.inputStream.bufferedReader().readText()
                    Result.success(output.trim())
                }
                os.contains("nix") || os.contains("nux") -> {
                    val proc = Runtime.getRuntime().exec(arrayOf("pkexec", "sh", "-c", command))
                    val output = proc.inputStream.bufferedReader().readText() + proc.errorStream.bufferedReader().readText()
                    Result.success(output.trim())
                }
                else -> Result.failure(Exception("Unsupported OS"))
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun elevateWindows(args: List<String>): Boolean {
        val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe"
        val classPath = System.getProperty("java.class.path")
        val mainClass = "com.kai.custom.MainKt"
        val script = createTempScript(".ps1", """
            Start-Process -Verb RunAs -FilePath "$javaBin" -ArgumentList "-cp `"$classPath`" $mainClass --elevated $args" -Wait
        """.trimIndent())
        val proc = Runtime.getRuntime().exec(arrayOf("powershell", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath))
        val exitCode = proc.waitFor()
        script.delete()
        return exitCode == 0
    }

    private fun elevateMac(args: List<String>): Boolean {
        val javaBin = System.getProperty("java.home") + "/bin/java"
        val classPath = System.getProperty("java.class.path")
        val mainClass = "com.kai.custom.MainKt"
        val cmd = "$javaBin -cp \"$classPath\" $mainClass --elevated ${args.joinToString(" ")}"
        val escaped = cmd.replace("\\", "\\\\").replace("\"", "\\\"")
        val proc = Runtime.getRuntime().exec(arrayOf(
            "osascript", "-e",
            "do shell script \"$escaped\" with administrator privileges"
        ))
        proc.waitFor()
        return true
    }

    private fun elevateLinux(args: List<String>): Boolean {
        val javaBin = System.getProperty("java.home") + "/bin/java"
        val classPath = System.getProperty("java.class.path")
        val mainClass = "com.kai.custom.MainKt"
        val proc = Runtime.getRuntime().exec(arrayOf(
            "pkexec", "java", "-cp", classPath, mainClass, "--elevated", *args.toTypedArray()
        ))
        proc.waitFor()
        return true
    }

    private fun createTempScript(extension: String, content: String): File {
        val file = File.createTempFile("kai_admin", extension)
        file.writeText(content)
        file.deleteOnExit()
        return file
    }
}
