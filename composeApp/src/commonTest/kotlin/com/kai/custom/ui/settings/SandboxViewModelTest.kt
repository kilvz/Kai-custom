package com.kai.custom.ui.settings

import androidx.compose.runtime.snapshots.SnapshotStateList
import app.cash.turbine.test
import com.kai.custom.CommandHandle
import com.kai.custom.ExecResult
import com.kai.custom.NoOpCommandHandle
import com.kai.custom.SandboxController
import com.kai.custom.SandboxFileEntry
import com.kai.custom.SandboxMigration
import com.kai.custom.SandboxSessions
import com.kai.custom.SandboxStatus
import com.kai.custom.SandboxStatusLabel
import com.kai.custom.TerminalLine
import com.kai.custom.TextFileResult
import com.kai.custom.linux.LinuxDistro
import com.kai.custom.testutil.FakeDataRepository
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SandboxViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeDataRepository
    private lateinit var fakeSandboxController: FakeSandboxController

    private class FakeSandboxController : SandboxController {
        override val status = MutableStateFlow(SandboxStatus())
        override val sessions = MutableStateFlow<List<String>>(emptyList())
        var setupCalls = 0
        var cancelCalls = 0
        var resetCalls = 0
        var restartCalls = 0
        var installPackagesCalls = 0
        var selectedDistro: LinuxDistro? = null
        var migrateHomeCalls = 0

        override fun selectDistro(distro: LinuxDistro) {
            selectedDistro = distro
        }

        override fun migrateHome() {
            migrateHomeCalls++
        }

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
        override suspend fun readTextFile(path: String, maxBytes: Int, force: Boolean): TextFileResult = TextFileResult.Unreadable
        override suspend fun writeTextFile(path: String, content: String): Boolean = false
        override suspend fun writeBinaryFile(path: String, data: ByteArray): Boolean = false
        override suspend fun openFile(path: String): Result<Unit> = Result.failure(Exception("No sandbox"))
        override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
        override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.failure(Exception("No sandbox"))
        override suspend fun importFile(directoryPath: String, source: PlatformFile): Result<String> = Result.failure(Exception("No sandbox"))
        override fun closeSession(sessionId: String) {}
        override fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = SnapshotStateList()
        override fun clearTranscript(sessionId: String) {}
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeDataRepository()
        fakeSandboxController = FakeSandboxController()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
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

    @Test
    fun `controller status updates flow into state`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            val initial = awaitItem()
            assertFalse(initial.sandboxReady)

            fakeSandboxController.status.value = SandboxStatus(
                installed = true,
                ready = true,
                working = false,
                progress = 1.0f,
                label = SandboxStatusLabel.Ready,
                diskUsageMB = 250L,
                packagesInstalled = true,
                error = false,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertTrue(updated.sandboxInstalled)
            assertTrue(updated.sandboxReady)
            assertEquals(1.0f, updated.sandboxProgress)
            assertEquals(SandboxStatusLabel.Ready, updated.sandboxStatusLabel)
            assertEquals(250L, updated.sandboxDiskUsageMB)
            assertTrue(updated.sandboxPackagesInstalled)
            assertFalse(updated.isWorking)
            assertFalse(updated.hasError)
        }
    }

    @Test
    fun `onSelectDistro persists the choice before anything is installed`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            assertEquals(LinuxDistro.DEBIAN, awaitItem().distro)

            viewModel.onSelectDistro(LinuxDistro.ALPINE)

            assertEquals(LinuxDistro.ALPINE, awaitItem().distro)
            assertEquals(LinuxDistro.ALPINE, fakeRepository.getSandboxDistro())
            assertEquals(LinuxDistro.ALPINE, fakeSandboxController.selectedDistro)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSelectDistro re-points the sandbox once a rootfs exists`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            skipItems(1)
            // Each distribution keeps its own install, so switching away from an
            // installed one is a change of address rather than a reinstall.
            fakeSandboxController.status.value = SandboxStatus(
                installed = true,
                ready = true,
                distro = LinuxDistro.DEBIAN,
                installedDistros = setOf(LinuxDistro.DEBIAN, LinuxDistro.ALPINE),
            )
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(awaitItem().sandboxInstalled)

            viewModel.onSelectDistro(LinuxDistro.ALPINE)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LinuxDistro.ALPINE, awaitItem().distro)
            assertEquals(LinuxDistro.ALPINE, fakeRepository.getSandboxDistro())
            assertEquals(LinuxDistro.ALPINE, fakeSandboxController.selectedDistro)
        }
    }

    @Test
    fun `onSelectDistro is ignored while an install is running`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            skipItems(1)
            fakeSandboxController.status.value = SandboxStatus(
                working = true,
                label = SandboxStatusLabel.Downloading,
                distro = LinuxDistro.DEBIAN,
            )
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(awaitItem().isWorking)

            viewModel.onSelectDistro(LinuxDistro.ALPINE)
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
            assertEquals(LinuxDistro.DEBIAN, fakeRepository.getSandboxDistro())
            assertEquals(null, fakeSandboxController.selectedDistro)
        }
    }

    @Test
    fun `onMigrateHome delegates to controller`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)
        viewModel.onMigrateHome()
        assertEquals(1, fakeSandboxController.migrateHomeCalls)
    }

    @Test
    fun `a pending migration flows into state so the card can offer it`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            assertEquals(null, awaitItem().migration)

            fakeSandboxController.status.value = SandboxStatus(
                installed = true,
                ready = true,
                distro = LinuxDistro.DEBIAN,
                installedDistros = setOf(LinuxDistro.DEBIAN, LinuxDistro.ALPINE),
                migration = SandboxMigration(from = LinuxDistro.ALPINE, fileCount = 42, bytes = 1_500_000),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val migration = awaitItem().migration
            assertEquals(LinuxDistro.ALPINE, migration?.from)
            assertEquals(42, migration?.fileCount)
        }
    }

    @Test
    fun `installed distributions flow into state for the picker`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            assertEquals(emptySet(), awaitItem().installedDistros)

            fakeSandboxController.status.value = SandboxStatus(
                installed = true,
                ready = true,
                distro = LinuxDistro.ALPINE,
                installedDistros = setOf(LinuxDistro.ALPINE),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(setOf(LinuxDistro.ALPINE), awaitItem().installedDistros)
        }
    }

    @Test
    fun `installed distro from the controller wins over the stored setting`() = runTest {
        // Legacy installs are Alpine no matter what the setting defaults to.
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            skipItems(1)
            fakeSandboxController.status.value = SandboxStatus(
                installed = true,
                ready = true,
                distro = LinuxDistro.ALPINE,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LinuxDistro.ALPINE, awaitItem().distro)
            assertEquals(LinuxDistro.DEBIAN, fakeRepository.getSandboxDistro())
        }
    }

    @Test
    fun `controller error status flows into hasError`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            skipItems(1)
            fakeSandboxController.status.value =
                SandboxStatus(error = true, label = SandboxStatusLabel.Failure.Setup("boom"))
            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertTrue(updated.hasError)
            assertEquals(SandboxStatusLabel.Failure.Setup("boom"), updated.sandboxStatusLabel)
        }
    }
}
