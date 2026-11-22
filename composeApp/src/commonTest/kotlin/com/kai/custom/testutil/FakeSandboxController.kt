package com.kai.custom.testutil

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kai.custom.*
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSandboxController(installed: Boolean = true) : SandboxController {
    override val status: StateFlow<SandboxStatus> = MutableStateFlow(
        SandboxStatus(
            installed = installed,
            ready = installed,
            working = false,
        ),
    )
    override val sessions: StateFlow<List<String>> = MutableStateFlow(emptyList())

    /** Guest path -> text content; backs listDirectory/readTextFile/writeTextFile. */
    val files = mutableMapOf<String, String>()

    override fun setup() {}
    override fun cancel() {}
    override fun reset() {}
    override fun restart() {}
    override fun installPackages() {}

    override suspend fun executeCommand(
        command: String,
        sessionId: String,
        useRoot: Boolean,
        timeoutSeconds: Long,
    ): String = ""

    override suspend fun executeCommandStructured(
        command: String,
        sessionId: String,
        useRoot: Boolean,
        timeoutSeconds: Long,
    ): ExecResult = ExecResult(stdout = "")

    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle = NoOpCommandHandle

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> {
        val dir = path.trimEnd('/')
        val children = mutableMapOf<String, Boolean>() // name -> isDirectory
        for (key in files.keys) {
            if (!key.startsWith("$dir/")) continue
            val rest = key.removePrefix("$dir/")
            val first = rest.substringBefore('/')
            if ('/' in rest) children[first] = true else children[first] = false
        }
        return children.map { (name, isDir) ->
            val childPath = "$dir/$name"
            SandboxFileEntry(
                name = name,
                path = childPath,
                isDirectory = isDir,
                sizeBytes = if (isDir) 0 else (files[childPath]?.length?.toLong() ?: 0),
                lastModifiedMs = 0,
            )
        }.sortedWith(compareByDescending<SandboxFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun readTextFile(path: String, maxBytes: Int, force: Boolean): TextFileResult {
        val content = files[path] ?: return TextFileResult.Unreadable
        if (content.length > maxBytes) return TextFileResult.TooLarge(content.length.toLong())
        return TextFileResult.Text(content)
    }

    override suspend fun writeTextFile(path: String, content: String): Boolean {
        files[path] = content
        return true
    }
    override suspend fun writeBinaryFile(path: String, data: ByteArray): Boolean = true
    override suspend fun openFile(path: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean {
        if (recursive) {
            val dir = path.trimEnd('/')
            files.keys.removeAll { it == path || it.startsWith("$dir/") }
        } else {
            files.remove(path)
        }
        return true
    }
    override suspend fun renameEntry(path: String, newName: String): Result<String> {
        val parent = path.substringBeforeLast('/', "")
        val newPath = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
        files.remove(path)?.let { files[newPath] = it }
        return Result.success(newPath)
    }

    override suspend fun importFile(directoryPath: String, source: PlatformFile): Result<String> = Result.failure(UnsupportedOperationException("Not needed by tests"))

    override fun closeSession(sessionId: String) {}
    override fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = mutableStateListOf()
    override fun clearTranscript(sessionId: String) {}
}
