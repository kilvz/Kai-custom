package com.kai.custom

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kai.custom.data.MemoryEntry
import com.kai.custom.sandbox.LinuxSandboxManager
import com.kai.custom.sandbox.SandboxState
import com.kai.custom.sandbox.SessionShell
import com.kai.custom.sandbox.openFileWithIntent
import com.kai.custom.sandbox.resolveSandboxAbsolute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

actual fun createSandboxController(): SandboxController = AndroidSandboxController()

class AndroidSandboxController : SandboxController {

    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)
    private val context: Context by inject(Context::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var cachedDiskUsageMB = 0L
    private var previousState: SandboxState? = null
    private val _status = MutableStateFlow(SandboxStatus())
    override val status: StateFlow<SandboxStatus> = _status
    override val sessions: StateFlow<List<String>> = sandboxManager.sessions

    init {
        // Synchronously seed the status from the manager's current state so the
        // first observer doesn't briefly see "not installed" before the launched
        // collector below catches up. Skip the disk-usage walk in this fast path —
        // it iterates the rootfs and could block the calling thread (often main,
        // since Koin singletons are created lazily on first injection from
        // Composables). The launched collect immediately re-emits the same state
        // and fills in disk usage on Dispatchers.IO.
        val initial = sandboxManager.state.value
        _status.value = if (initial is SandboxState.Ready) {
            SandboxStatus(
                installed = true,
                ready = true,
                statusText = "Ready",
                packagesInstalled = sandboxManager.arePackagesInstalled(),
            )
        } else {
            mapState(initial)
        }
        // Leave previousState null so the launched collect's first mapState(Ready)
        // computes disk usage on IO.

        scope.launch {
            sandboxManager.state.collect { state ->
                // Guard the mapping: if anything inside (e.g. disk-usage walk)
                // throws, we'd lose the collector and the UI would freeze on
                // its current status until process restart.
                try {
                    _status.value = mapState(state)
                } catch (e: Throwable) {
                    android.util.Log.e("SandboxController", "mapState failed for $state", e)
                    _status.value = SandboxStatus(
                        error = true,
                        statusText = "Sandbox status error: ${e.message ?: e::class.simpleName}",
                    )
                }
                previousState = state
            }
        }
    }

    private fun mapState(state: SandboxState): SandboxStatus = when (state) {
        is SandboxState.NotInstalled -> SandboxStatus(
            statusText = "Not installed",
        )

        is SandboxState.Downloading -> SandboxStatus(
            working = true,
            progress = state.progress,
            statusText = "Downloading rootfs...",
        )

        is SandboxState.Extracting -> SandboxStatus(
            working = true,
            statusText = "Extracting...",
        )

        is SandboxState.Installing -> {
            val rootfsExists = java.io.File(sandboxManager.rootfsPath).isDirectory
            SandboxStatus(
                installed = rootfsExists,
                working = true,
                statusText = state.detail.ifEmpty { "Installing..." },
                diskUsageMB = cachedDiskUsageMB,
            )
        }

        is SandboxState.Ready -> {
            if (previousState !is SandboxState.Ready) {
                cachedDiskUsageMB = sandboxManager.getDiskUsageMB()
            }
            SandboxStatus(
                installed = true,
                ready = true,
                statusText = "Ready",
                diskUsageMB = cachedDiskUsageMB,
                packagesInstalled = sandboxManager.arePackagesInstalled(),
            )
        }

        is SandboxState.Error -> SandboxStatus(
            error = true,
            statusText = "Error: ${state.message}",
        )
    }

    override fun setup() {
        sandboxManager.setup()
    }

    override fun cancel() {
        sandboxManager.cancel()
    }

    override fun reset() {
        sandboxManager.reset()
    }

    override fun installPackages() {
        sandboxManager.installPackages()
    }

    override fun closeSession(sessionId: String) {
        sandboxManager.closeShell(sessionId)
    }

    override fun transcriptFor(sessionId: String): SnapshotStateList<com.kai.custom.TerminalLine> = sandboxManager.transcriptFor(sessionId)

    override fun clearTranscript(sessionId: String) {
        sandboxManager.clearTranscript(sessionId)
    }

    override suspend fun executeCommand(command: String, sessionId: String): String = withContext(Dispatchers.IO) {
        val state = sandboxManager.state.value
        if (state !is SandboxState.Ready) return@withContext SANDBOX_NOT_READY

        val result = sandboxManager.shellFor(sessionId).run(command, timeoutSeconds = 30)

        val stdout = result["stdout"] as? String ?: ""
        val stderr = result["stderr"] as? String ?: ""
        val exitCode = result["exit_code"] as? Int
        val error = result["error"] as? String

        buildString {
            if (stdout.isNotEmpty()) append(stdout)
            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(stderr)
            }
            if (error != null) {
                if (isNotEmpty()) append("\n")
                append(error)
            }
            if (exitCode != null && exitCode != 0 && isEmpty()) {
                append("Exit code: $exitCode")
            }
        }
    }

    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle {
        val state = sandboxManager.state.value
        if (state !is SandboxState.Ready) {
            onStderr(SANDBOX_NOT_READY)
            return NoOpCommandHandle
        }
        val shell = sandboxManager.shellFor(sessionId)
        val deferred = CompletableDeferred<Map<String, Any>>()
        val cancelled = AtomicBoolean(false)
        // No implicit timeout in the streaming path — UI cancel + process exit
        // are the real "done" signals. The persistent shell still recovers
        // from a wedged shell via reset() on the next call.
        val streamingTimeoutSeconds = 24L * 60 * 60
        scope.launch {
            runCatching {
                shell.run(
                    command = command,
                    timeoutSeconds = streamingTimeoutSeconds,
                    onStdout = onStdout,
                    onStderr = onStderr,
                )
            }.onSuccess { deferred.complete(it) }
                .onFailure { deferred.complete(mapOf("exit_code" to -1)) }
        }
        return PersistentCommandHandle(shell, deferred, cancelled)
    }

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = withContext(Dispatchers.IO) {
        val dir = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext emptyList()
        if (!dir.isDirectory) return@withContext emptyList()

        val normalized = if (path.endsWith("/")) path.dropLast(1) else path
        val isRoot = normalized.isEmpty() || normalized == "/"

        val children = dir.listFiles().orEmpty()
            .filterNot { isRoot && it.name == "root" }
            .map { it.toEntry(parent = if (isRoot) "" else normalized) }
            .toMutableList()

        if (isRoot) {
            val home = File(sandboxManager.homePath)
            if (home.isDirectory) {
                children.add(
                    SandboxFileEntry(
                        name = "root",
                        path = "/root",
                        isDirectory = true,
                        sizeBytes = 0,
                        lastModifiedMs = home.lastModified(),
                    ),
                )
            }
        }
        children.sortedWith(
            compareByDescending<SandboxFileEntry> { it.isDirectory }
                .thenBy { it.name.lowercase() },
        )
    }

    override suspend fun readTextFile(path: String, maxBytes: Int): String? = withContext(Dispatchers.IO) {
        val file = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext null
        if (!file.isFile) return@withContext null
        if (file.length() > maxBytes) return@withContext null
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
            return@withContext null
        }
        if (bytes.any { it == 0.toByte() }) return@withContext null
        bytes.toString(Charsets.UTF_8)
    }

    override suspend fun writeTextFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext false
        if (file.exists() && !file.isFile) return@withContext false
        try {
            file.parentFile?.mkdirs()
            file.writeBytes(content.toByteArray(Charsets.UTF_8))
            true
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun writeBinaryFile(path: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val file = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext false
        if (file.exists() && !file.isFile) return@withContext false
        try {
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun openFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val file = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path: $path"))
        if (!file.isFile) return@withContext Result.failure(IllegalArgumentException("Not a file: $path"))
        val result = openFileWithIntent(context, file)
        if (result.success) Result.success(Unit) else Result.failure(IllegalStateException(result.error ?: "Open failed"))
    }

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
        val file = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext false
        if (!file.exists()) return@withContext false
        // Refuse to delete the sandbox roots themselves.
        val canonical = file.canonicalPath
        if (canonical == File(sandboxManager.homePath).canonicalPath) return@withContext false
        if (canonical == File(sandboxManager.rootfsPath).canonicalPath) return@withContext false
        when {
            file.isDirectory && !recursive -> {
                val empty = file.list()?.isEmpty() != false
                if (empty) file.delete() else false
            }

            file.isDirectory -> file.deleteRecursively()

            else -> file.delete()
        }
    }

    override suspend fun renameEntry(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\') ||
            newName == "." || newName == ".."
        ) {
            return@withContext Result.failure(IllegalArgumentException("Invalid name"))
        }
        val src = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path"))
        if (!src.exists()) return@withContext Result.failure(IllegalArgumentException("Not found"))
        val canonical = src.canonicalPath
        if (canonical == File(sandboxManager.homePath).canonicalPath) {
            return@withContext Result.failure(IllegalArgumentException("Cannot rename sandbox root"))
        }
        if (canonical == File(sandboxManager.rootfsPath).canonicalPath) {
            return@withContext Result.failure(IllegalArgumentException("Cannot rename sandbox root"))
        }
        val parentSandbox = path.substringBeforeLast('/', "")
        val newSandboxPath = if (parentSandbox.isEmpty()) "/$newName" else "$parentSandbox/$newName"
        val dest = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, newSandboxPath)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid destination"))
        if (dest.exists()) return@withContext Result.failure(IllegalStateException("collision"))
        if (src.renameTo(dest)) {
            Result.success(newSandboxPath)
        } else {
            Result.failure(IllegalStateException("rename failed"))
        }
    }

    // ── memory semantic search via sandbox + kai-mempalace ──────────────

    private var searchDepsReady = false

    override suspend fun searchMemories(
        memories: List<MemoryEntry>,
        query: String,
        limit: Int,
    ): List<MemoryEntry>? {
        if (_status.value.ready != true) return null
        if (memories.isEmpty() || query.isBlank()) return null

        return withContext(Dispatchers.IO) {
            val shell = sandboxManager.shellFor(SandboxSessions.SYSTEM)

            if (!ensureSearchDeps(shell)) return@withContext null

            val inputObj = buildJsonObject {
                putJsonArray("memories") {
                    memories.forEach { mem ->
                        addJsonObject {
                            put("key", mem.key)
                            put("content", mem.content)
                            put("category", mem.category.name)
                            put("hit_count", mem.hitCount)
                        }
                    }
                }
                put("query", query)
                put("limit", limit)
            }

            val dataFile = File(sandboxManager.tmpPath, "__kai_search.json")
            try {
                dataFile.parentFile?.mkdirs()
                dataFile.writeText(inputObj.toString())
            } catch (_: Exception) {
                return@withContext null
            }

            val result = shell.run(
                command = "python3 /root/kai_search.py /tmp/__kai_search.json",
                timeoutSeconds = 30,
            )
            val stdout = result["stdout"] as? String ?: ""
            val exitCode = result["exit_code"] as? Int ?: -1
            if (exitCode != 0 || stdout.isBlank()) return@withContext null

            try {
                val parsed = Json.parseToJsonElement(stdout).jsonArray
                val scoredKeys = parsed.mapNotNull { obj ->
                    val jo = obj.jsonObject
                    val key = jo["key"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val score = jo["score"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    if (score > 0.01) key else null
                }
                val keyToEntry = memories.associateBy { it.key }
                scoredKeys.mapNotNull { keyToEntry[it] }.take(limit)
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun ensureSearchDeps(shell: SessionShell): Boolean {
        if (searchDepsReady) return true

        try {
            val kaiMempalaceDir = File(sandboxManager.homePath, "kai-mempalace")
            if (!kaiMempalaceDir.isDirectory) {
                android.util.Log.i("SandboxSearch", "Cloning kai-mempalace...")
                val clone = shell.run(
                    command = "git clone --depth 1 https://github.com/kilvz/kai-mempalace.git /root/kai-mempalace 2>&1 | tail -5",
                    timeoutSeconds = 60,
                )
                if (clone["success"] != true) {
                    val err = (clone["stderr"] as? String).orEmpty()
                    android.util.Log.w("SandboxSearch", "git clone failed: $err")
                    return false
                }
            }

            android.util.Log.i("SandboxSearch", "Installing kai-mempalace Python dependencies...")
            // Try Alpine packages first (musl-compatible), fall back to pip
            var depsOk = shell.run(
                command = "python3 -c 'import faiss, scipy, yaml, dateutil; print(\"OK\")' 2>&1",
                timeoutSeconds = 15,
            )
            if ((depsOk["stdout"] as? String)?.trim() != "OK") {
                val apkInstall = shell.run(
                    command = "apk add py3-faiss py3-scipy py3-yaml py3-dateutil 2>&1 | tail -5",
                    timeoutSeconds = 120,
                )
                depsOk = shell.run(
                    command = "python3 -c 'import faiss, scipy, yaml, dateutil; print(\"OK\")' 2>&1",
                    timeoutSeconds = 15,
                )
            }
            if ((depsOk["stdout"] as? String)?.trim() != "OK") {
                val install = shell.run(
                    command = "pip install faiss-cpu scipy pyyaml python-dateutil 2>&1 | tail -5",
                    timeoutSeconds = 180,
                )
                if (install["success"] != true) {
                    val err = (install["stderr"] as? String).orEmpty()
                    android.util.Log.w("SandboxSearch", "pip install deps failed: $err")
                    return false
                }
            }

            val wrapperFile = File(sandboxManager.homePath, "kai_search.py")
            if (!wrapperFile.exists()) {
                wrapperFile.parentFile?.mkdirs()
                wrapperFile.writeText(SEARCH_WRAPPER)
            }

            searchDepsReady = true
            return true
        } catch (e: Exception) {
            android.util.Log.w("SandboxSearch", "search deps setup failed", e)
            return false
        }
    }

    private companion object {
        val SEARCH_WRAPPER = """
            |import json, sys, os
            |sys.path.insert(0, os.path.expanduser('/root/kai-mempalace'))
            |
            |from embedder import NumpyEmbedder
            |import numpy as np
            |
            |try:
            |    import faiss
            |    HAS_FAISS = True
            |except ImportError:
            |    HAS_FAISS = False
            |
            |data = json.load(open(sys.argv[1]))
            |memories = data['memories']
            |query = data['query']
            |limit = data.get('limit', 10)
            |
            |if not memories or not query:
            |    print('[]')
            |    sys.exit(0)
            |
            |texts = [m['content'] for m in memories] + [query]
            |embedder = NumpyEmbedder()
            |vectors = embedder.embed(texts)
            |
            |query_vec = vectors[-1]
            |doc_vecs = vectors[:-1]
            |
            |if HAS_FAISS:
            |    qv = query_vec.reshape(1, -1).astype(np.float32)
            |    dv = doc_vecs.astype(np.float32)
            |    faiss.normalize_L2(qv)
            |    faiss.normalize_L2(dv)
            |    idx = faiss.IndexFlatIP(embedder.dimension)
            |    idx.add(dv)
            |    k = min(limit, idx.ntotal)
            |    if k == 0:
            |        print('[]')
            |        sys.exit(0)
            |    dists, idxs = idx.search(qv, k)
            |    results = []
            |    for i in range(k):
            |        pos = int(idxs[0][i])
            |        score = float(dists[0][i])
            |        if score > 0.01:
            |            mem = dict(memories[pos])
            |            mem['score'] = round(score, 4)
            |            results.append(mem)
            |else:
            |    qn = np.linalg.norm(query_vec)
            |    if qn == 0:
            |        print('[]')
            |        sys.exit(0)
            |    dn = np.linalg.norm(doc_vecs, axis=1)
            |    sims = np.dot(doc_vecs, query_vec) / (dn * qn + 1e-10)
            |    top = np.argsort(sims)[::-1][:limit]
            |    results = []
            |    for pos in top:
            |        score = float(sims[pos])
            |        if score > 0.01:
            |            mem = dict(memories[pos])
            |            mem['score'] = round(score, 4)
            |            results.append(mem)
            |
            |print(json.dumps(results, ensure_ascii=False))
        """.trimMargin()
    }
}

private fun File.toEntry(parent: String): SandboxFileEntry = SandboxFileEntry(
    name = name,
    path = if (parent.isEmpty()) "/$name" else "$parent/$name",
    isDirectory = isDirectory,
    sizeBytes = if (isFile) length() else 0,
    lastModifiedMs = lastModified(),
)

private const val SANDBOX_NOT_READY = "Sandbox is not ready"

private class PersistentCommandHandle(
    private val shell: SessionShell,
    private val result: CompletableDeferred<Map<String, Any>>,
    private val cancelled: AtomicBoolean,
) : CommandHandle {
    override fun cancel() {
        cancelled.set(true)
        shell.cancelForeground()
    }
    override fun isCancelled(): Boolean = cancelled.get()
    override suspend fun writeInput(line: String) {
        withContext(Dispatchers.IO) { shell.writeInput(line) }
    }
    override suspend fun awaitExit(): Int = (result.await()["exit_code"] as? Int) ?: -1
}
