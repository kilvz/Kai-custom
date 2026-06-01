package com.kai.custom.testutil

import com.kai.custom.CommandHandle
import com.kai.custom.NoOpCommandHandle
import com.kai.custom.SandboxController
import com.kai.custom.SandboxFileEntry
import com.kai.custom.SandboxStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSandboxController(installed: Boolean = true) : SandboxController {

    val files = mutableMapOf<String, String>()

    private val _status = MutableStateFlow(SandboxStatus(installed = installed, ready = installed))
    override val status: StateFlow<SandboxStatus> = _status
    override val sessions: StateFlow<List<String>> = MutableStateFlow(emptyList())

    override fun setup() {}
    override fun cancel() {}
    override fun reset() {}
    override fun installPackages() {}

    override suspend fun executeCommand(command: String, sessionId: String): String = ""
    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle = NoOpCommandHandle

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> {
        val prefix = if (path.endsWith("/")) path else "$path/"
        val children = linkedMapOf<String, Boolean>()
        for (p in files.keys) {
            if (!p.startsWith(prefix)) continue
            val rest = p.removePrefix(prefix)
            val slash = rest.indexOf('/')
            if (slash < 0) {
                children[rest] = false
            } else {
                val dir = rest.substring(0, slash)
                if (children[dir] != false) children[dir] = true
            }
        }
        return children.map { (name, isDir) ->
            SandboxFileEntry(name = name, path = "$prefix$name", isDirectory = isDir, sizeBytes = 0, lastModifiedMs = 0)
        }
    }

    override suspend fun readTextFile(path: String, maxBytes: Int): String? = files[path]

    override suspend fun writeTextFile(path: String, content: String): Boolean {
        files[path] = content
        return true
    }

    override suspend fun writeBinaryFile(path: String, data: ByteArray): Boolean {
        files[path] = data.decodeToString()
        return true
    }

    override suspend fun openFile(path: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean {
        val prefix = "$path/"
        val toRemove = files.keys.filter { it == path || (recursive && it.startsWith(prefix)) }
        toRemove.forEach { files.remove(it) }
        return toRemove.isNotEmpty()
    }

    override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.success(path)
}
