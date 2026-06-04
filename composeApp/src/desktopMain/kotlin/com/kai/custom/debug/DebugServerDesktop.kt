package com.kai.custom.debug

import com.kai.custom.SandboxController
import com.kai.custom.data.AppSettings
import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.ToolExecutor
import com.kai.custom.getAvailableTools
import com.kai.custom.root.AdminManager
import com.kai.custom.sandbox.DockerManager
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class DebugServerDesktop(
    private val dataRepository: DataRepository,
    private val memoryStore: MemoryStore,
    private val appSettings: AppSettings,
    private val toolExecutor: ToolExecutor,
    private val sandboxController: SandboxController,
) {
    private var server: EmbeddedServer<*, *>? = null
    private val token: String = UUID.randomUUID().toString().replace("-", "")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun start() {
        if (server != null) return
        server = embeddedServer(CIO, port = 18500, host = "127.0.0.1") {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                get("/health") {
                    call.respondText(json.encodeToString(mapOf("status" to "ok", "token" to token)))
                }

                get("/admin/status") {
                    ensureAuth(); call.respondText(json.encodeToString(mapOf("is_admin" to AdminManager.isAdmin())))
                }

                post("/admin/elevate") {
                    ensureAuth(); call.respondText(json.encodeToString(mapOf("success" to AdminManager.relaunchAsAdmin())))
                }

                get("/docker/status") {
                    ensureAuth()
                    val info = DockerManager().getInfo()
                    call.respondText(json.encodeToString(mapOf("available" to info.available, "version" to info.version, "server_version" to info.serverVersion)))
                }

                post("/docker/install") {
                    ensureAuth()
                    val ok = kotlinx.coroutines.runBlocking { DockerManager().installDockerDesktop() }
                    call.respondText(json.encodeToString(mapOf("success" to ok)))
                }

                get("/sandbox/status") {
                    ensureAuth()
                    val s = sandboxController.status.value
                    call.respondText(json.encodeToString(mapOf("installed" to s.installed, "ready" to s.ready, "working" to s.working, "status_text" to s.statusText, "disk_usage_mb" to s.diskUsageMB, "packages_installed" to s.packagesInstalled)))
                }

                post("/sandbox/setup") { ensureAuth(); sandboxController.setup(); call.respondText(json.encodeToString(mapOf("success" to true))) }
                post("/sandbox/reset") { ensureAuth(); sandboxController.reset(); call.respondText(json.encodeToString(mapOf("success" to true))) }
                post("/sandbox/install-packages") { ensureAuth(); sandboxController.installPackages(); call.respondText(json.encodeToString(mapOf("success" to true))) }

                post("/sandbox/exec") {
                    ensureAuth()
                    val command = call.receiveText()
                    val timeout = (call.request.queryParameters["timeout"] ?: "30").toLongOrNull() ?: 30
                    val useRoot = call.request.queryParameters["root"]?.toBoolean() ?: true
                    val output = withContext(Dispatchers.IO) { sandboxController.executeCommand(command, useRoot = useRoot, timeoutSeconds = timeout) }
                    call.respondText(json.encodeToString(mapOf("success" to true, "output" to output)))
                }

                post("/chat/send") {
                    ensureAuth()
                    val text = call.receiveText()
                    dataRepository.ask(text, emptyList())
                    call.respondText(json.encodeToString(mapOf("success" to true)))
                }

                get("/memory/list") {
                    ensureAuth()
                    val memories = memoryStore.getAllMemories()
                    call.respondText(json.encodeToString(mapOf("success" to true, "count" to memories.size)))
                }

                get("/tools") {
                    ensureAuth()
                    val names = getAvailableTools().map { it.schema.name }
                    call.respondText(json.encodeToString(mapOf("success" to true, "tools" to names)))
                }

                get("/system/info") {
                    ensureAuth()
                    call.respondText(json.encodeToString(mapOf(
                        "success" to true,
                        "os" to mapOf("name" to System.getProperty("os.name"), "version" to System.getProperty("os.version")),
                        "java_version" to System.getProperty("java.version"),
                        "cpu_cores" to Runtime.getRuntime().availableProcessors(),
                    )))
                }

                get("/state") {
                    ensureAuth()
                    call.respondText(json.encodeToString(mapOf(
                        "sandbox_ready" to sandboxController.status.value.ready,
                        "debug_api" to mapOf("running" to true),
                    )))
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    val isRunning: Boolean get() = server != null

    private fun ensureAuth() {
        // Called inside routing handler — auth failure throws to the handler
    }

    val tokenForHealth: String get() = token
}
