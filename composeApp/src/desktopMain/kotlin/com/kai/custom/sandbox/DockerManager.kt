package com.kai.custom.sandbox

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class DockerManager {

    data class DockerInfo(
        val available: Boolean,
        val version: String = "",
        val serverVersion: String = "",
    )

    fun getInfo(): DockerInfo = try {
        val proc = Runtime.getRuntime().exec(arrayOf("docker", "version", "--format", "{{.Client.Version}}|{{.Server.Version}}"))
        if (proc.waitFor(5, TimeUnit.SECONDS)) {
            val output = proc.inputStream.bufferedReader().readText().trim()
            val parts = output.split("|")
            DockerInfo(
                available = parts.size >= 2 && parts[1].isNotBlank(),
                version = parts.getOrElse(0) { "" },
                serverVersion = parts.getOrElse(1) { "" },
            )
        } else {
            proc.destroyForcibly()
            DockerInfo(available = false)
        }
    } catch (_: Exception) {
        DockerInfo(available = false)
    }

    suspend fun pullImage(image: String): Boolean = runCommand("docker", "pull", image)

    suspend fun createContainer(
        image: String,
        containerName: String,
        bindMounts: Map<String, String> = emptyMap(),
        portMappings: Map<Int, Int> = emptyMap(),
    ): String? {
        val args = mutableListOf("docker", "run", "-d", "--name", containerName, "--init")
        bindMounts.forEach { (host, container) ->
            args.add("-v")
            args.add("$host:$container")
        }
        portMappings.forEach { (host, container) ->
            args.add("-p")
            args.add("$host:$container")
        }
        args.add(image)
        args.add("tail")
        args.add("-f")
        args.add("/dev/null")
        return try {
            val proc = Runtime.getRuntime().exec(args.toTypedArray())
            val output = if (proc.waitFor(30, TimeUnit.SECONDS) && proc.exitValue() == 0) {
                proc.inputStream.bufferedReader().readText().trim()
            } else {
                null
            }
            if (output != null && output.length == 64) output else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun containerExists(name: String): Boolean = containerInspect(name, "container") != null

    suspend fun isContainerRunning(name: String): Boolean {
        val status = containerInspect(name, "running") ?: return false
        return status == "true"
    }

    suspend fun execCommand(containerName: String, command: String): String = runCommandWithOutput("docker", "exec", containerName, "sh", "-c", command)

    suspend fun execInteractive(containerName: String, command: String): Pair<Process, ByteArrayOutputStream> {
        val proc = Runtime.getRuntime().exec(arrayOf("docker", "exec", "-i", containerName, "sh", "-c", command))
        val stdout = ByteArrayOutputStream()
        return proc to stdout
    }

    suspend fun copyIn(containerName: String, hostPath: String, containerPath: String): Boolean = runCommand("docker", "cp", hostPath, "$containerName:$containerPath")

    suspend fun copyOut(containerName: String, containerPath: String, hostPath: String): Boolean = runCommand("docker", "cp", "$containerName:$containerPath", hostPath)

    suspend fun exportContainer(containerName: String, outputPath: String): Boolean = runCommand("docker", "export", containerName, "-o", outputPath)

    suspend fun importContainer(tarPath: String, imageName: String): Boolean = runCommand("docker", "import", tarPath, imageName)

    suspend fun removeContainer(name: String, force: Boolean = true): Boolean {
        val args = mutableListOf("docker", "rm")
        if (force) args.add("-f")
        args.add(name)
        return runCommand(*args.toTypedArray())
    }

    suspend fun listContainers(): List<String> = try {
        val proc = Runtime.getRuntime().exec(arrayOf("docker", "ps", "-a", "--format", "{{.Names}}"))
        if (proc.waitFor(10, TimeUnit.SECONDS)) {
            proc.inputStream.bufferedReader().readLines().filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun getContainerDiskUsage(name: String): Long {
        val output = execCommand(name, "du -sb / 2>/dev/null | head -1 | cut -f1")
        return output.trim().toLongOrNull() ?: 0L
    }

    suspend fun checkPackagesInstalled(name: String): Boolean {
        val output = execCommand(name, "which node python3 curl git 2>/dev/null | wc -l")
        return output.trim().toIntOrNull()?.let { it >= 3 } ?: false
    }

    suspend fun installDockerDesktop(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> installDockerWindows()
            os.contains("mac") -> installDockerMac()
            else -> false
        }
    }

    private suspend fun installDockerWindows(): Boolean {
        val tempDir = System.getProperty("java.io.tmpdir")
        val installer = File(tempDir, "DockerDesktopInstaller.exe")
        return try {
            runCommand(
                "curl",
                "-L",
                "-o",
                installer.absolutePath,
                "https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe",
            )
            if (!installer.exists()) return false
            val proc = Runtime.getRuntime().exec(
                arrayOf(
                    installer.absolutePath,
                    "install",
                    "--quiet",
                ),
            )
            val installed = proc.waitFor(180, TimeUnit.SECONDS) && proc.exitValue() == 0
            installer.delete()
            installed
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun installDockerMac(): Boolean = runCommand(
        "bash",
        "-c",
        "which brew >/dev/null 2>&1 && brew install --cask docker || " +
            "curl -fsSL https://desktop.docker.com/mac/main/amd64/Docker.dmg -o /tmp/Docker.dmg",
    )

    private suspend fun runCommand(vararg args: String): Boolean = try {
        val proc = Runtime.getRuntime().exec(args)
        proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0
    } catch (_: Exception) {
        false
    }

    private suspend fun runCommandWithOutput(vararg args: String): String = try {
        val proc = Runtime.getRuntime().exec(args)
        if (proc.waitFor(30, TimeUnit.SECONDS)) {
            (proc.inputStream.bufferedReader().readText() + proc.errorStream.bufferedReader().readText()).trim()
        } else {
            proc.destroyForcibly()
            ""
        }
    } catch (_: Exception) {
        ""
    }

    private suspend fun containerInspect(name: String, format: String): String? = try {
        val proc = Runtime.getRuntime().exec(arrayOf("docker", "inspect", "--format", "{{.$format}}", name))
        if (proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0) {
            proc.inputStream.bufferedReader().readText().trim()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
