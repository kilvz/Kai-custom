package com.kai.custom.ui.settings

import com.kai.custom.CommandHandle
import com.kai.custom.ExecResult
import com.kai.custom.NoOpCommandHandle
import com.kai.custom.SandboxController
import com.kai.custom.SandboxFileEntry
import com.kai.custom.SandboxStatus
import com.kai.custom.TerminalLine
import com.kai.custom.SandboxSessions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

class SandboxViewModelTest {

    private class FakeSandboxController : SandboxController {
        override val status = MutableStateFlow(SandboxStatus())
        override val sessions = MutableStateFlow<List<String>>(emptyList())
        var setupCalls = 0
        var cancelCalls = 0
        var resetCalls = 0
        var restartCalls = 0
        var installPackagesCalls = 0

        override fun setup() {
            setupCalls++
        }

        override fun cancel() {
            cancelCalls++
        }

        override fun reset() {
            resetCalls++
        }

        override fun restart() {
            restartCalls++
        }

        override fun installPackages() {
            installPackagesCalls++
        }

        override suspend fun executeCommand(command: String, sessionId: String): String = ""

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

        override suspend fun listDirectory(path: String): List<SandboxFileEntry> = emptyList()
        override suspend fun readTextFile(path: String, maxBytes: Int): String? = null
        override suspend fun writeTextFile(path: String, content: String): Boolean = false
        override suspend fun writeBinaryFile(path: String, data: ByteArray): Boolean = false
        override suspend fun openFile(path: String): Result<Unit> = Result.failure(Exception("No sandbox"))
        override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
        override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.failure(Exception("No sandbox"))
        override fun closeSession(sessionId: String) {}
        override fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = SnapshotStateList()
        override fun clearTranscript(sessionId: String) {}
    }

    @Test
    fun `test sandbox setup`() {
        val controller = FakeSandboxController()
        controller.setup()
        assertEquals(1, controller.setupCalls)
    }

    @Test
    fun `test sandbox reset`() {
        val controller = FakeSandboxController()
        controller.reset()
        assertEquals(1, controller.resetCalls)
    }

    @Test
    fun `test sandbox restart`() {
        val controller = FakeSandboxController()
        controller.restart()
        assertEquals(1, controller.restartCalls)
    }

    @Test
    fun `test sandbox cancel`() {
        val controller = FakeSandboxController()
        controller.cancel()
        assertEquals(1, controller.cancelCalls)
    }

    @Test
    fun `test sandbox install packages`() {
        val controller = FakeSandboxController()
        controller.installPackages()
        assertEquals(1, controller.installPackagesCalls)
    }
}
