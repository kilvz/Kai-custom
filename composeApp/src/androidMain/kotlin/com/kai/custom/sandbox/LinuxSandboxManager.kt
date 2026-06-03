package com.kai.custom.sandbox

import android.content.Context
import android.os.Build
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kai.custom.SandboxSessions
import com.kai.custom.TerminalLine
import com.kai.custom.data.AppSettings
import com.kai.custom.data.ConversationStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private val TRANSCRIPT_SAVE_DEBOUNCE = 500.milliseconds

class LinuxSandboxManager(
    private val context: Context,
    private val conversationStorage: ConversationStorage,
    private val appSettings: AppSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private val _state = MutableStateFlow<SandboxState>(SandboxState.NotInstalled)
    val state: StateFlow<SandboxState> = _state

    private val sandboxDir: File
        get() = File(context.filesDir, "linux-sandbox")

    val rootfsPath: String get() = File(sandboxDir, "rootfs").absolutePath

    // Sandbox /sdcard is bind-mounted from externally-visible app storage so files
    // produced by the agent can be opened via FileProvider Intents. Computed
    // lazily on first access; mkdirs and the one-time legacy-home migration run
    // once per process, then the cached path is reused for every shell call.
    val homePath: String by lazy {
        val external = context.getExternalFilesDir(null)
        val target = if (external != null) {
            File(external, "sandbox-home")
        } else {
            File(sandboxDir, "home")
        }
        target.mkdirs()
        val legacy = File(sandboxDir, "home")
        val newHomeIsEmpty = target.listFiles().isNullOrEmpty()
        if (legacy.isDirectory && legacy.absolutePath != target.absolutePath && newHomeIsEmpty) {
            try {
                legacy.listFiles()?.forEach { entry ->
                    val dest = File(target, entry.name)
                    if (!dest.exists()) entry.copyRecursively(dest, overwrite = false)
                }
            } catch (e: Exception) {
                android.util.Log.w("LinuxSandbox", "Legacy home migration failed: ${e.message}")
            }
        }
        target.absolutePath
    }

    val tmpPath: String get() = File(sandboxDir, "tmp").absolutePath

    // Run proot directly from nativeLibraryDir where Android grants execute permission
    val prootPath: String get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so").absolutePath
    val nativeLibDir: String get() = context.applicationInfo.nativeLibraryDir

    private val downloader = RootfsDownloader(HttpClient(Android))

    init {
        checkExistingInstallation()
    }

    private fun checkExistingInstallation() {
        val rootfs = File(sandboxDir, "rootfs")
        val proot = File(prootPath)
        val rootfsOk = rootfs.isDirectory
        val prootExists = proot.exists()
        val prootExec = proot.canExecute()
        android.util.Log.d(
            "LinuxSandbox",
            "checkExistingInstallation: rootfs=$rootfs dir=$rootfsOk proot=$prootPath exists=$prootExists canExec=$prootExec",
        )
        if (rootfsOk && prootExists && prootExec) {
            _state.value = SandboxState.Ready
        }
    }

    private fun getLinuxArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "aarch64"
        }
    }

    fun setup() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                setupInternal()
            } catch (e: kotlinx.coroutines.CancellationException) {
                checkExistingInstallation()
            } catch (e: Exception) {
                _state.value = SandboxState.Error(e.message ?: "Setup failed")
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        // Clean up partial downloads
        File(sandboxDir, "rootfs.tar.gz").delete()
        // Determine correct state based on what exists
        val rootfs = File(sandboxDir, "rootfs")
        if (rootfs.isDirectory && File(prootPath).exists()) {
            _state.value = SandboxState.Ready
        } else {
            _state.value = SandboxState.NotInstalled
        }
    }

    private suspend fun setupInternal() {
        val arch = getLinuxArch()
        val distro = appSettings.getSandboxDistro()

        // Verify proot is available in nativeLibraryDir
        val proot = File(prootPath)
        if (!proot.exists()) {
            throw IllegalStateException(
                "Proot binary not found at $prootPath. " +
                    "nativeLibraryDir contents: ${File(nativeLibDir).listFiles()?.map { it.name } ?: "empty"}",
            )
        }

        // Create directories. `homePath` getter creates the externally-visible
        // sandbox-home dir on access, so we only need to ensure sandboxDir + tmp.
        sandboxDir.mkdirs()
        File(sandboxDir, "tmp").mkdirs()

        // Copy libtalloc with correct soname (Android strips .so.2 suffix in jniLibs)
        copyLibtalloc()

        // Download rootfs
        val rootfsDir = File(sandboxDir, "rootfs")
        if (!rootfsDir.isDirectory) {
            val tarGzFile = File(sandboxDir, "rootfs.tar.gz")
            try {
                _state.value = SandboxState.Downloading(0f)
                downloader.download(arch, tarGzFile, distro) { progress ->
                    _state.value = SandboxState.Downloading(progress)
                }

                _state.value = SandboxState.Extracting
                downloader.extractTarGz(tarGzFile, rootfsDir)
            } finally {
                tarGzFile.delete()
            }
        }

        // Post-setup
        _state.value = SandboxState.Installing("Configuring...")
        downloader.makeWritable(rootfsDir)
        downloader.writeResolvConf(rootfsDir)
        downloader.fixAptDirectories(rootfsDir)

        // Skip apk update/apt-get update if packages are already installed
        val pythonBinary = if (distro == "ubuntu") "usr/bin/python3" else "usr/bin/python3"
        val packagesAlreadyInstalled = File(rootfsDir, pythonBinary).exists() ||
            File(rootfsDir, "usr/bin/ssh").exists()

        if (!packagesAlreadyInstalled) {
            val executor = createProotExecutor()
            val updateCmd = if (distro == "ubuntu") "apt-get update" else "apk update"
            var updated = false
            for (mirror in downloader.getMirrors(distro, arch)) {
                downloader.writeRepositories(rootfsDir, mirror, distro)
                val result = executor.execute(updateCmd, timeoutSeconds = 180)
                if (result["success"] as? Boolean == true) {
                    updated = true
                    break
                }
            }
            if (!updated) {
                throw IllegalStateException("$updateCmd failed on all mirrors")
            }
        }

        _state.value = SandboxState.Ready
    }

    private fun copyLibtalloc() {
        val tallocTarget = File(sandboxDir, "libtalloc.so.2")
        if (tallocTarget.exists()) return

        val source = File(nativeLibDir, "libtalloc.so")
        if (source.exists()) {
            source.copyTo(tallocTarget, overwrite = true)
        }
    }

    fun createProotExecutor(): ProotExecutor = ProotExecutor(
        prootPath = prootPath,
        libDir = sandboxDir.absolutePath,
        rootfsPath = rootfsPath,
        homePath = homePath,
        tmpPath = tmpPath,
    ).apply {
        sandboxStorageMountEnabled = appSettings.isSandboxStorageMountEnabled()
        sandboxRootEnabled = appSettings.isSandboxRootEnabled() && appSettings.isRootEnabled()
    }

    // One bash session per logical caller (chat conversation, terminal scratch,
    // package-manager UI, etc.). Lazily created on first access; tracked here so
    // the sandbox-level `reset()` and per-conversation deletion can tear them
    // down. Live during the app process only — not persisted.
    private val shells = mutableMapOf<String, SessionShell>()
    private val _sessions = MutableStateFlow<List<String>>(emptyList())
    val sessions: StateFlow<List<String>> = _sessions

    // Debounce per-session transcript writes. A burst of commands (e.g. a
    // 1000-iteration loop) would otherwise re-serialize the entire conversations
    // JSON and rewrite SharedPreferences once per command.
    private val pendingSaves = mutableMapOf<String, Job>()

    fun shellFor(sessionId: String): SessionShell = synchronized(shells) {
        shells[sessionId]?.let { return it }
        val inner = PersistentSandboxShell(createProotExecutor(), tmpPath)
        val persistable = SandboxSessions.isPersistable(sessionId)
        val initialLines = if (persistable) {
            conversationStorage.conversations.value
                .firstOrNull { it.id == sessionId }?.shellTranscript.orEmpty()
        } else {
            emptyList()
        }
        val onChange: ((List<TerminalLine>) -> Unit)? = if (persistable) {
            { lines -> scheduleTranscriptSave(sessionId, lines) }
        } else {
            null
        }
        val wrapper = SessionShell(sessionId, inner, initialLines, onChange)
        shells[sessionId] = wrapper
        _sessions.value = shells.keys.toList()
        wrapper
    }

    private fun scheduleTranscriptSave(sessionId: String, lines: List<TerminalLine>) {
        synchronized(pendingSaves) {
            pendingSaves[sessionId]?.cancel()
            pendingSaves[sessionId] = scope.launch {
                try {
                    delay(TRANSCRIPT_SAVE_DEBOUNCE)
                    conversationStorage.updateShellTranscript(sessionId, lines)
                } finally {
                    synchronized(pendingSaves) { pendingSaves.remove(sessionId) }
                }
            }
        }
    }

    fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = shellFor(sessionId).transcript

    fun clearTranscript(sessionId: String) {
        synchronized(shells) { shells[sessionId] }?.transcript?.clear()
    }

    fun closeShell(sessionId: String) {
        val removed = synchronized(shells) {
            val s = shells.remove(sessionId)
            _sessions.value = shells.keys.toList()
            s
        }
        removed?.reset()
    }

    private fun closeAllShells() {
        val all = synchronized(shells) {
            val snapshot = shells.values.toList()
            shells.clear()
            _sessions.value = emptyList()
            snapshot
        }
        all.forEach { it.reset() }
    }

    fun installPackages() {
        if (currentJob?.isActive == true) return
        val distro = appSettings.getSandboxDistro()
        val packages = if (distro == "ubuntu") {
            listOf(
                "bash", "apt-utils", "curl", "wget", "git", "jq", "python3", "python3-pip", "nodejs",
                "openssh-client", "lftp", "rsync", "ca-certificates",
            )
        } else {
            listOf(
                "bash", "curl", "wget", "git", "jq", "python3", "py3-pip", "nodejs",
                "openssh-client", "lftp", "rsync", "ca-certificates",
            )
        }
        val updateCmd = if (distro == "ubuntu") "apt-get update" else "apk update"
        val installCmdPrefix = if (distro == "ubuntu") "DEBIAN_FRONTEND=noninteractive apt-get install -y" else "apk add"

        currentJob = scope.launch {
            try {
                val arch = getLinuxArch()
                val rootfsDir = File(sandboxDir, "rootfs")
                val executor = createProotExecutor()
                downloader.fixAptDirectories(rootfsDir)

                // Kill stale dpkg processes and remove lock files from interrupted installs
                if (distro == "ubuntu") {
                    executor.execute(
                        "for pid in /proc/[0-9]*/cmdline; do " +
                        "  grep -q dpkg \"\$pid\" 2>/dev/null && " +
                        "  kill -9 \$(echo \"\$pid\" | cut -d/ -f3) 2>/dev/null || true; " +
                        "done; " +
                        "rm -f /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock " +
                        "/var/lib/apt/lists/lock /var/cache/apt/archives/lock; " +
                        "dpkg --configure -a 2>/dev/null",
                        timeoutSeconds = 30,
                    )
                }

                _state.value = SandboxState.Installing("Updating package lists...")

                val updateTimeout = if (distro == "ubuntu") 180L else 120L
                var updated = false
                for (mirror in downloader.getMirrors(distro, arch)) {
                    downloader.writeRepositories(rootfsDir, mirror, distro)
                    val result = executor.execute(updateCmd, timeoutSeconds = updateTimeout)
                    if (result["success"] as? Boolean == true) {
                        updated = true
                        break
                    }
                }
                if (!updated) {
                    for (mirror in downloader.getMirrors(distro, arch)) {
                        val httpMirror = mirror.replace("https://", "http://")
                        downloader.writeRepositories(rootfsDir, httpMirror, distro)
                        val result = executor.execute(updateCmd, timeoutSeconds = updateTimeout + 60)
                        if (result["success"] as? Boolean == true) {
                            updated = true
                            break
                        }
                    }
                }
                if (!updated) {
                    android.util.Log.w("LinuxSandbox", "$updateCmd timed out or failed — proceeding with cached package lists")
                }

                // Install packages sequentially per-package, regardless of distro.
                // This isolates failures — one bad package doesn't block the rest,
                // each package gets its own timeout, and there's no stale-cache issue
                // from mirror-switching without apt-get update.
                val installTimeout = if (distro == "ubuntu") 120L else 120L
                val failed = mutableListOf<String>()
                for (pkg in packages) {
                    ensureActive()
                    _state.value = SandboxState.Installing("Installing $pkg...")

                    // For Ubuntu: retry once with apt-get update if first attempt fails
                    var pkgResult = executor.execute(
                        "$installCmdPrefix --no-install-recommends $pkg",
                        timeoutSeconds = installTimeout,
                    )
                    if (pkgResult["success"] as? Boolean != true && distro == "ubuntu") {
                        android.util.Log.w("LinuxSandbox", "First attempt for $pkg failed — running apt-get update and retrying")
                        runCatching { executor.execute("apt-get update", timeoutSeconds = 180) }
                        ensureActive()
                        pkgResult = executor.execute(
                            "$installCmdPrefix --no-install-recommends $pkg",
                            timeoutSeconds = installTimeout,
                        )
                    }

                    ensureActive()
                    if (pkgResult["success"] as? Boolean != true) {
                        val stderr = pkgResult["stderr"] as? String ?: ""
                        val stdout = pkgResult["stdout"] as? String ?: ""
                        val error = pkgResult["error"] as? String ?: ""
                        val timedOut = pkgResult["timed_out"] as? Boolean ?: false
                        val exitCode = pkgResult["exit_code"] as? Int ?: -1
                        android.util.Log.w("LinuxSandbox", "Failed to install $pkg: exit=$exitCode timedOut=$timedOut error=$error stdout=$stdout stderr=$stderr")
                        failed.add(pkg)
                    }
                }
                if (failed.isNotEmpty()) {
                    _state.value = SandboxState.Error("Failed to install: ${failed.joinToString(", ")}")
                    return@launch
                }
                runCatching { SshConfigManager(java.io.File(homePath)).ensureDefaults() }
                    .onFailure { android.util.Log.w("LinuxSandbox", "ssh defaults seed failed: ${it.message}") }
                _state.value = SandboxState.Ready
            } catch (_: kotlinx.coroutines.CancellationException) {
                _state.value = SandboxState.Ready
            } catch (e: Exception) {
                android.util.Log.e("LinuxSandbox", "Package install exception", e)
                _state.value = SandboxState.Error("Install failed: ${e.message}")
            }
        }
    }

    fun reset() {
        scope.launch {
            closeAllShells()
            sandboxDir.deleteRecursively()
            _state.value = SandboxState.NotInstalled
        }
    }

    fun getDiskUsageMB(): Long {
        if (!sandboxDir.isDirectory) return 0
        // Manual stack walk instead of walkTopDown(): the latter throws an
        // AssertionError if a child entry transitions from directory→non-directory
        // between the iterator's isDirectory check and DirectoryState construction.
        // The rootfs can contain unix sockets / FIFOs / broken symlinks (e.g. from
        // user-run programs like node), and concurrent install activity also races
        // the walk. We skip bad entries and keep going.
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(sandboxDir)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = try {
                dir.listFiles()
            } catch (_: Throwable) {
                null
            } ?: continue
            for (child in children) {
                try {
                    when {
                        child.isDirectory -> stack.addLast(child)
                        child.isFile -> total += child.length()
                        // skip sockets, FIFOs, broken symlinks
                    }
                } catch (_: Throwable) {
                    // skip transient/inaccessible entry, keep iterating
                }
            }
        }
        return total / (1024 * 1024)
    }

    fun arePackagesInstalled(): Boolean {
        val bins = listOf(
            "usr/bin/bash", "usr/bin/curl", "usr/bin/wget", "usr/bin/git",
            "usr/bin/jq", "usr/bin/python3", "usr/bin/pip3", "usr/bin/node",
            "usr/bin/ssh", "usr/bin/lftp", "usr/bin/rsync",
        )
        val allBins = bins.all { File(rootfsPath, it).exists() }
        val caCerts = File(rootfsPath, "etc/ssl/certs/ca-certificates.crt").exists()
        return allBins && caCerts
    }
}
