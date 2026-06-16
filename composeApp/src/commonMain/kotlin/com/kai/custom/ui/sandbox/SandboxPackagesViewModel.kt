package com.kai.custom.ui.sandbox

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.custom.SandboxController
import com.kai.custom.SandboxSessions
import com.kai.custom.data.DataRepository
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.sandbox_packages_install_failed
import kai.composeapp.generated.resources.sandbox_packages_install_success
import kai.composeapp.generated.resources.sandbox_packages_uninstall_failed
import kai.composeapp.generated.resources.sandbox_packages_uninstall_success
import kai.composeapp.generated.resources.sandbox_packages_up_to_date
import kai.composeapp.generated.resources.sandbox_packages_upgrade_count
import kai.composeapp.generated.resources.sandbox_packages_upgrade_failed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class PackageEntry(
    val name: String,
    val version: String,
    val description: String? = null,
)

@Immutable
data class PackagesUiState(
    val installed: ImmutableList<PackageEntry> = persistentListOf(),
    val installedNames: ImmutableSet<String> = persistentSetOf(),
    val searchQuery: String = "",
    val searchResults: ImmutableList<PackageEntry> = persistentListOf(),
    val loadingInstalled: Boolean = false,
    val searching: Boolean = false,
    val mutating: ImmutableSet<String> = persistentSetOf(),
    val pendingUninstall: PackageEntry? = null,
    val upgrading: Boolean = false,
    val snackbarMessage: SnackbarMessage? = null,
)

@Immutable
data class SnackbarMessage(val resource: StringResource, val arg: String? = null)

private const val SEARCH_DEBOUNCE_MS = 300L
private const val SEARCH_RESULT_LIMIT = 200
private const val ERROR_SUMMARY_MAX_CHARS = 200
private const val LOG_TAG = "SandboxPackages"

private val ALPINE_REVISION_SUFFIX = Regex("-r\\d+$")

private val UPGRADE_PROGRESS_LINE = Regex("""^\(\d+/\d+\)\s+Upgrading\s""")

class SandboxPackagesViewModel(
    private val sandboxController: SandboxController,
    private val dataRepository: DataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PackagesUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    private fun isUbuntu(): Boolean = dataRepository.getSandboxDistro() == "ubuntu"

    fun start() {
        val current = _state.value
        if (current.installed.isNotEmpty() || current.loadingInstalled) return
        refreshInstalled()
    }

    fun refreshInstalled() {
        if (_state.value.loadingInstalled) return
        _state.update { it.copy(loadingInstalled = true) }
        viewModelScope.launch {
            applyInstalled(loadInstalled())
            _state.update { it.copy(loadingInstalled = false) }
        }
    }

    private suspend fun loadInstalled(): List<PackageEntry> {
        val cmd = if (isUbuntu()) {
            "dpkg-query --show -f '${'$'}{Package} ${'$'}{Version}\\n' 2>/dev/null | sort"
        } else {
            "apk info -v | sort"
        }
        val output = sandboxController.executeCommand(cmd, SandboxSessions.SYSTEM)
        log("loadInstalled", cmd, output)
        return if (isUbuntu()) parseDpkgLines(output) else parseApkInfoLines(output)
    }

    private fun applyInstalled(parsed: List<PackageEntry>) {
        _state.update {
            it.copy(
                installed = parsed.toImmutableList(),
                installedNames = parsed.mapTo(mutableSetOf()) { p -> p.name }.toImmutableSet(),
            )
        }
    }

    fun updateSearchQuery(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = persistentListOf(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS.milliseconds)
            _state.update { it.copy(searching = true) }
            runSearch(query)
        }
    }

    private suspend fun runSearch(query: String) {
        val cmd = if (isUbuntu()) {
            "apt-cache search ${shellQuote(query)} | head -n $SEARCH_RESULT_LIMIT"
        } else {
            "apk search -v ${shellQuote(query)} | head -n $SEARCH_RESULT_LIMIT"
        }
        val output = sandboxController.executeCommand(cmd, SandboxSessions.SYSTEM)
        log("runSearch($query)", cmd, output)
        val results = if (isUbuntu()) {
            parseAptSearchLines(output).toImmutableList()
        } else {
            parseApkSearchLines(output).toImmutableList()
        }
        _state.update {
            if (it.searchQuery == query) {
                it.copy(searchResults = results, searching = false)
            } else {
                it
            }
        }
    }

    fun install(pkg: PackageEntry) {
        val cmd = if (isUbuntu()) {
            "DEBIAN_FRONTEND=noninteractive apt-get install -y ${shellQuote(pkg.name)}"
        } else {
            "apk add --no-cache ${shellQuote(pkg.name)}"
        }
        mutateInstalled(
            pkg = pkg,
            cmd = cmd,
            successWhenInstalled = true,
            successRes = Res.string.sandbox_packages_install_success,
            failureRes = Res.string.sandbox_packages_install_failed,
        )
    }

    fun requestUninstall(pkg: PackageEntry) {
        _state.update { it.copy(pendingUninstall = pkg) }
    }

    fun cancelUninstall() {
        _state.update { it.copy(pendingUninstall = null) }
    }

    fun confirmUninstall() {
        val pkg = _state.value.pendingUninstall ?: return
        _state.update { it.copy(pendingUninstall = null) }
        val cmd = if (isUbuntu()) {
            "DEBIAN_FRONTEND=noninteractive apt-get remove -y ${shellQuote(pkg.name)}"
        } else {
            "apk del ${shellQuote(pkg.name)}"
        }
        mutateInstalled(
            pkg = pkg,
            cmd = cmd,
            successWhenInstalled = false,
            successRes = Res.string.sandbox_packages_uninstall_success,
            failureRes = Res.string.sandbox_packages_uninstall_failed,
        )
    }

    private fun mutateInstalled(
        pkg: PackageEntry,
        cmd: String,
        successWhenInstalled: Boolean,
        successRes: StringResource,
        failureRes: StringResource,
    ) {
        if (pkg.name in _state.value.mutating) return
        markMutating(pkg.name, true)
        viewModelScope.launch {
            val result = runAndCapture(cmd)
            applyInstalled(loadInstalled())
            markMutating(pkg.name, false)
            val isInstalled = pkg.name in _state.value.installedNames
            val succeeded = isInstalled == successWhenInstalled
            val msg = if (succeeded) {
                SnackbarMessage(successRes, pkg.name)
            } else {
                SnackbarMessage(failureRes, result.errorSummary())
            }
            _state.update { it.copy(snackbarMessage = msg) }
        }
    }

    fun upgradePackages() {
        if (_state.value.upgrading) return
        _state.update { it.copy(upgrading = true) }
        viewModelScope.launch {
            val updateCmd = if (isUbuntu()) "apt-get update" else "apk update"
            val updateResult = runAndCapture(updateCmd)
            if (updateResult.hasPackageErrors()) {
                _state.update {
                    it.copy(
                        upgrading = false,
                        snackbarMessage = SnackbarMessage(
                            Res.string.sandbox_packages_upgrade_failed,
                            updateResult.errorSummary(),
                        ),
                    )
                }
                return@launch
            }
            val upgradeCmd = if (isUbuntu()) {
                "DEBIAN_FRONTEND=noninteractive apt-get upgrade -y"
            } else {
                "apk upgrade"
            }
            val upgradeResult = runAndCapture(upgradeCmd)
            applyInstalled(loadInstalled())
            _state.update {
                val msg = if (upgradeResult.hasPackageErrors()) {
                    SnackbarMessage(Res.string.sandbox_packages_upgrade_failed, upgradeResult.errorSummary())
                } else {
                    val count = if (isUbuntu()) countAptUpgraded(upgradeResult.stdout) else countApkUpgraded(upgradeResult.stdout)
                    if (count == 0) {
                        SnackbarMessage(Res.string.sandbox_packages_up_to_date)
                    } else {
                        SnackbarMessage(Res.string.sandbox_packages_upgrade_count, count.toString())
                    }
                }
                it.copy(upgrading = false, snackbarMessage = msg)
            }
        }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private fun markMutating(name: String, mutating: Boolean) {
        _state.update {
            val next = it.mutating.toMutableSet()
            if (mutating) next.add(name) else next.remove(name)
            it.copy(mutating = next.toImmutableSet())
        }
    }

    private data class CommandResult(val exit: Int, val stdout: String, val stderr: String) {
        fun errorSummary(): String {
            val tail = stderr.lineSequence().filter { it.isNotBlank() }.lastOrNull()
                ?: stdout.lineSequence().filter { it.isNotBlank() }.lastOrNull()
                ?: "exit code $exit"
            return tail.take(ERROR_SUMMARY_MAX_CHARS)
        }

        fun hasPackageErrors(): Boolean = stdout.lineSequence().any { it.startsWith("ERROR:") } ||
            stderr.lineSequence().any { it.startsWith("E:") } ||
            stderr.lineSequence().any { it.startsWith("ERROR:") }
    }

    private suspend fun runAndCapture(cmd: String): CommandResult {
        val stdoutChannel = Channel<String>(capacity = Channel.UNLIMITED)
        val stderrChannel = Channel<String>(capacity = Channel.UNLIMITED)
        val handle = sandboxController.executeCommandStreaming(
            command = cmd,
            onStdout = { stdoutChannel.trySend(it) },
            onStderr = { stderrChannel.trySend(it) },
            sessionId = SandboxSessions.SYSTEM,
        )
        val exit = handle.awaitExit()
        stdoutChannel.close()
        stderrChannel.close()
        val stdout = buildString { for (line in stdoutChannel) appendLine(line) }
        val stderr = buildString { for (line in stderrChannel) appendLine(line) }
        println("$LOG_TAG [runAndCapture] exit=$exit cmd=$cmd")
        logMultiline("runAndCapture stdout", stdout)
        logMultiline("runAndCapture stderr", stderr)
        return CommandResult(exit, stdout, stderr)
    }

    private fun log(label: String, cmd: String, output: String) {
        println("$LOG_TAG [$label] cmd=$cmd")
        logMultiline("$label output", output)
    }

    private fun logMultiline(label: String, body: String) {
        if (body.isEmpty()) {
            println("$LOG_TAG [$label] <empty>")
            return
        }
        body.lineSequence().forEach { line ->
            if (line.isNotEmpty()) println("$LOG_TAG [$label] $line")
        }
    }

    private fun countApkUpgraded(stdout: String): Int = stdout.lineSequence()
        .count { UPGRADE_PROGRESS_LINE.containsMatchIn(it) }

    private fun countAptUpgraded(stdout: String): Int {
        val upgraded = mutableSetOf<String>()
        for (line in stdout.lineSequence()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("Unpacking ") && trimmed.endsWith("(...)")) {
                val pkg = trimmed.removePrefix("Unpacking ").removeSuffix(" (...)")
                upgraded.add(pkg)
            } else if (trimmed.startsWith("Setting up ")) {
                val pkg = trimmed.removePrefix("Setting up ").substringBefore(" (")
                upgraded.add(pkg)
            }
        }
        return upgraded.size
    }

    private fun parseApkInfoLines(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("WARNING:") && !it.startsWith("ERROR:") }
        .mapNotNull { line -> parseApkNameVersion(line)?.let { PackageEntry(it.first, it.second) } }
        .distinctBy { "${it.name}@${it.version}" }
        .toList()

    private fun parseApkSearchLines(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("WARNING:") && !it.startsWith("ERROR:") }
        .mapNotNull { line ->
            val sepIdx = line.indexOf(" - ")
            val nameVer = if (sepIdx >= 0) line.substring(0, sepIdx) else line
            val description = if (sepIdx >= 0) line.substring(sepIdx + 3).trim() else null
            parseApkNameVersion(nameVer)?.let { (n, v) -> PackageEntry(n, v, description?.takeIf { it.isNotEmpty() }) }
        }
        .distinctBy { "${it.name}@${it.version}" }
        .toList()

    private fun parseApkNameVersion(s: String): Pair<String, String>? {
        if (s.isEmpty()) return null
        val revision = ALPINE_REVISION_SUFFIX.find(s)?.value.orEmpty()
        val withoutRev = if (revision.isNotEmpty()) s.dropLast(revision.length) else s
        var splitAt = -1
        for (i in withoutRev.length - 1 downTo 1) {
            if (withoutRev[i - 1] == '-' && withoutRev[i].isDigit()) {
                splitAt = i - 1
                break
            }
        }
        if (splitAt < 0) {
            return if (revision.isNotEmpty()) withoutRev to revision.trimStart('-') else s to ""
        }
        val name = withoutRev.substring(0, splitAt)
        val version = withoutRev.substring(splitAt + 1) + revision
        if (name.isEmpty()) return null
        return name to version
    }

    private fun parseDpkgLines(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val spaceIdx = line.indexOf(' ')
            if (spaceIdx < 0) return@mapNotNull null
            val name = line.substring(0, spaceIdx).trim()
            val version = line.substring(spaceIdx + 1).trim()
            if (name.isEmpty()) null else PackageEntry(name, version)
        }
        .distinctBy { "${it.name}@${it.version}" }
        .toList()

    private fun parseAptSearchLines(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val sepIdx = line.indexOf(" - ")
            if (sepIdx < 0) return@mapNotNull null
            val name = line.substring(0, sepIdx).trim()
            val description = line.substring(sepIdx + 3).trim()
            PackageEntry(name, "", description.takeIf { it.isNotEmpty() })
        }
        .distinctBy { it.name }
        .toList()

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
