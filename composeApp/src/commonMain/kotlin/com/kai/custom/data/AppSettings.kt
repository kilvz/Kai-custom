package com.kai.custom.data

import com.kai.custom.SshProfile
import com.kai.custom.data.getDefaultLanguage
import com.kai.custom.defaultUiScale
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class ImportSection {
    SERVICES,
    SOUL,
    MEMORY,
    SCHEDULING,
    HEARTBEAT,
    EMAIL,
    SMS,
    WHATSAPP,
    SPLINTERLANDS,
    TOOLS,
    MCP,
    CONVERSATIONS,
}

enum class ThemeMode {
    System,
    Light,
    Dark,
    OledBlack,
}

/**
 * Stricter than [detectImportSections]: only includes sections that contain actual user data,
 * skipping ones that exist purely because of default feature-toggle flags (e.g. `sms_enabled = false`,
 * `splinterlands_enabled = false`, `mcp_servers = []`). Used to drive the Export preview dialog.
 */
fun detectExportableSections(json: JsonObject): Map<ImportSection, String?> {
    val sections = mutableMapOf<ImportSection, String?>()

    val configured = json["configured_services"]?.jsonArray
    if (configured != null && configured.isNotEmpty()) {
        sections[ImportSection.SERVICES] = "${configured.size}"
    }

    if (json["soul_text"] != null || json["soul_auto"] != null || json["persona_list"] != null) {
        sections[ImportSection.SOUL] = null
    }

    if (json["memory_enabled"] != null) {
        sections[ImportSection.MEMORY] = null
    }

    val tasks = json["scheduled_tasks"]?.jsonArray
    if (tasks != null && tasks.isNotEmpty()) {
        sections[ImportSection.SCHEDULING] = "${tasks.size}"
    }

    val heartbeatHasPrompt = json["heartbeat_prompt"] != null
    val heartbeatHasConfig = json["heartbeat_config"] != null
    val heartbeatHasLog = json["heartbeat_log"]?.jsonArray?.isNotEmpty() == true
    if (heartbeatHasPrompt || heartbeatHasConfig || heartbeatHasLog) {
        sections[ImportSection.HEARTBEAT] = null
    }

    val emails = json["email_accounts"]?.jsonArray
    if (emails != null && emails.isNotEmpty()) {
        sections[ImportSection.EMAIL] = "${emails.size}"
    }

    val smsEnabled = json["sms_enabled"]?.jsonPrimitive?.content?.toBoolean() == true
    val smsSendEnabled = json["sms_send_enabled"]?.jsonPrimitive?.content?.toBoolean() == true
    if (smsEnabled || smsSendEnabled) {
        sections[ImportSection.SMS] = null
    }

    if (json["splinterlands_account"] != null) {
        sections[ImportSection.SPLINTERLANDS] = null
    }

    val waEnabled = json["whatsapp_enabled"]?.jsonPrimitive?.content?.toBoolean() == true
    val waHasContacts = json["whatsapp_allowed_contacts"]?.jsonPrimitive?.content?.isNotBlank() == true
    if (waEnabled || waHasContacts) {
        sections[ImportSection.WHATSAPP] = null
    }

    val toolOverrides = json["tool_overrides"]?.jsonObject
    if (toolOverrides != null && toolOverrides.isNotEmpty()) {
        val enabled = toolOverrides.count { (_, v) ->
            try {
                v.jsonPrimitive.content.toBoolean()
            } catch (_: Exception) {
                false
            }
        }
        sections[ImportSection.TOOLS] = "$enabled"
    }

    val mcp = json["mcp_servers"]?.jsonArray
    if (mcp != null && mcp.isNotEmpty()) {
        sections[ImportSection.MCP] = "${mcp.size}"
    }

    val conversations = json["conversations"]?.jsonArray
    if (conversations != null && conversations.isNotEmpty()) {
        sections[ImportSection.CONVERSATIONS] = "${conversations.size}"
    }

    return sections
}

fun detectImportSections(json: JsonObject): Map<ImportSection, String?> {
    val sections = mutableMapOf<ImportSection, String?>()
    if (json["configured_services"] != null || json["current_service_id"] != null || json["free_fallback_enabled"] != null || json["instance_settings"] != null) {
        val count = json["configured_services"]?.jsonArray?.size
        sections[ImportSection.SERVICES] = count?.let { "$it" }
    }
    if (json["soul_text"] != null || json["soul_auto"] != null || json["persona_list"] != null) {
        sections[ImportSection.SOUL] = null
    }
    if (json["memory_enabled"] != null) {
        sections[ImportSection.MEMORY] = null
    }
    if (json["scheduling_enabled"] != null || json["scheduled_tasks"] != null) {
        val count = json["scheduled_tasks"]?.jsonArray?.size
        sections[ImportSection.SCHEDULING] = count?.let { "$it" }
    }
    if (json["heartbeat_config"] != null || json["heartbeat_prompt"] != null || json["heartbeat_log"] != null) {
        sections[ImportSection.HEARTBEAT] = null
    }
    if (json["email_enabled"] != null || json["email_accounts"] != null) {
        val count = json["email_accounts"]?.jsonArray?.size
        sections[ImportSection.EMAIL] = count?.let { "$it" }
    }
    if (json["sms_enabled"] != null || json["sms_poll_interval"] != null || json["sms_send_enabled"] != null) {
        sections[ImportSection.SMS] = null
    }
    if (json["splinterlands_enabled"] != null || json["splinterlands_account"] != null) {
        sections[ImportSection.SPLINTERLANDS] = null
    }
    if (json["whatsapp_enabled"] != null || json["whatsapp_read_only"] != null || json["whatsapp_reply_mode"] != null) {
        sections[ImportSection.WHATSAPP] = null
    }
    if (json["tool_overrides"] != null) {
        val enabled = json["tool_overrides"]?.jsonObject?.count { (_, v) ->
            try {
                v.jsonPrimitive.content.toBoolean()
            } catch (_: Exception) {
                false
            }
        }
        sections[ImportSection.TOOLS] = enabled?.let { "$it" }
    }
    if (json["mcp_servers"] != null) {
        val count = json["mcp_servers"]?.jsonArray?.size
        sections[ImportSection.MCP] = count?.let { "$it" }
    }
    if (json["conversations"] != null) {
        val count = try {
            json["conversations"]?.jsonArray?.size
        } catch (_: Exception) {
            null
        }
        sections[ImportSection.CONVERSATIONS] = count?.let { "$it" }
    }
    return sections
}

data class ServiceInstance(
    val instanceId: String,
    val serviceId: String,
)

class AppSettings(internal val settings: Settings) {

    // App open tracking
    fun trackAppOpen(): Int {
        val currentCount = settings.getInt(KEY_APP_OPENS, 0)
        val newCount = currentCount + 1
        settings.putInt(KEY_APP_OPENS, newCount)
        return newCount
    }

    // Tool enable/disable settings
    fun isToolEnabled(toolId: String, defaultEnabled: Boolean = true): Boolean = settings.getBoolean("$KEY_TOOL_PREFIX$toolId", defaultEnabled)

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        settings.putBoolean("$KEY_TOOL_PREFIX$toolId", enabled)
    }

    fun getConversationsJson(): String? = settings.getStringOrNull(KEY_CONVERSATIONS)

    fun setConversationsJson(json: String) {
        settings.putString(KEY_CONVERSATIONS, json)
    }

    fun getCurrentConversationId(): String? = settings.getStringOrNull(KEY_CURRENT_CONVERSATION_ID)

    fun setCurrentConversationId(id: String?) {
        if (id == null) {
            settings.remove(KEY_CURRENT_CONVERSATION_ID)
        } else {
            settings.putString(KEY_CURRENT_CONVERSATION_ID, id)
        }
    }

    fun getCurrentInteractiveMode(): Boolean = settings.getBoolean(KEY_CURRENT_INTERACTIVE_MODE, false)

    fun setCurrentInteractiveMode(enabled: Boolean) {
        settings.putBoolean(KEY_CURRENT_INTERACTIVE_MODE, enabled)
    }

    fun isCurrentConversationMigrated(): Boolean = settings.getBoolean(KEY_CURRENT_CONVERSATION_MIGRATED, false)

    fun markCurrentConversationMigrated() {
        settings.putBoolean(KEY_CURRENT_CONVERSATION_MIGRATED, true)
    }

    fun getEncryptionKey(): ByteArray? {
        val encoded = settings.getStringOrNull(KEY_ENCRYPTION_KEY) ?: return null
        return try {
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.decode(encoded)
        } catch (_: Exception) {
            null
        }
    }

    // Free fallback
    fun isFreeFallbackEnabled(): Boolean = settings.getBoolean(KEY_FREE_FALLBACK_ENABLED, true)

    fun setFreeFallbackEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_FREE_FALLBACK_ENABLED, enabled)
    }

    fun getFreeMode(): FreeMode {
        val stored = settings.getStringOrNull(KEY_FREE_MODE) ?: return FreeMode.FAST
        return FreeMode.entries.find { it.name == stored } ?: FreeMode.FAST
    }

    fun setFreeMode(mode: FreeMode) {
        settings.putString(KEY_FREE_MODE, mode.name)
    }

    fun isFreeServicePrimary(): Boolean = settings.getBoolean(KEY_FREE_SERVICE_PRIMARY, false)

    fun setFreeServicePrimary(primary: Boolean) {
        settings.putBoolean(KEY_FREE_SERVICE_PRIMARY, primary)
    }

    // Soul — user-edited + auto-generated behavior summary (per-persona)
    fun getSoulUser(personaId: String = getActivePersonaId()): String {
        val key = "soul_user_$personaId"
        val stored = settings.getStringOrNull(key) ?: run {
            // migrate legacy if this is the active persona
            if (personaId == getActivePersonaId()) return migrateSoulFromLegacy(personaId)
            return ""
        }
        return stored
    }

    fun setSoulUser(text: String, personaId: String = getActivePersonaId()) {
        settings.putString("soul_user_$personaId", text)
    }

    fun getSoulAuto(personaId: String = getActivePersonaId()): String = settings.getString("soul_auto_$personaId", "")

    fun setSoulAuto(text: String, personaId: String = getActivePersonaId()) {
        settings.putString("soul_auto_$personaId", text)
    }

    /** Returns persona identity: name prefix + default soul (character definition). */
    fun getSoulText(personaId: String = getActivePersonaId(), localModel: Boolean = false): String {
        val persona = getPersonaName(personaId)
        val prefix = "You are $persona."
        val config = personaManagerSafe?.getPersona(personaId)
        val defaultSoul = config?.defaultSoul
        if (!defaultSoul.isNullOrBlank()) {
            val firstLine = defaultSoul.lineSequence().firstOrNull()?.trim() ?: ""
            if (firstLine.startsWith("You are ") || firstLine.startsWith("**Identity**:")) {
                return defaultSoul
            }
            if (localModel) {
                val identity = defaultSoul.lineSequence()
                    .firstOrNull { it.startsWith("**Identity**:") }
                    ?.removePrefix("**Identity**: You are ")
                    ?.removePrefix("**Identity**:")
                    ?.trim()
                if (!identity.isNullOrBlank()) return "$prefix $identity"
                return defaultSoul.lineSequence().first().take(100).let { "$prefix $it" }
            }
            return "$prefix\n\n$defaultSoul"
        }
        return prefix
    }

    /** Kept for backward compat — writes to soul_user for active persona. */
    fun setSoulText(text: String) {
        settings.putString("soul_user_${getActivePersonaId()}", text)
    }

    fun getPersonaName(personaId: String = getActivePersonaId()): String {
        val stored = settings.getStringOrNull(KEY_PERSONA_NAME)
        // legacy single-key fallback
        val config = personaManagerSafe?.getPersona(personaId)
        return config?.name ?: stored ?: "Kai"
    }

    fun getActivePersonaId(): String {
        // migrate from legacy KEY_PERSONA_NAME to persona_manager
        val legacy = settings.getStringOrNull(KEY_PERSONA_NAME)
        if (legacy != null && settings.getStringOrNull(KEY_ACTIVE_PERSONA_ID) == null) {
            // legacy key stores the persona name, not id — map it
            val id = if (legacy == "alt" || legacy == "Kai") legacy.lowercase() else buildInsFirstId()
            settings.putString(KEY_ACTIVE_PERSONA_ID, id)
            settings.remove(KEY_PERSONA_NAME)
            return id
        }
        return settings.getString(KEY_ACTIVE_PERSONA_ID, buildInsFirstId())
    }

    private fun buildInsFirstId(): String = "kai"

    private val personaManagerSafe: PersonaManager? by lazy {
        try {
            PersonaManager(this)
        } catch (_: Exception) {
            null
        }
    }

    private fun migrateSoulFromLegacy(personaId: String): String {
        val old = settings.getString("soul_text", "")
        if (old.isNotEmpty()) {
            settings.putString("soul_user_$personaId", old)
            settings.remove("soul_text")
        }
        // also migrate legacy soul_user/soul_auto to per-persona for alt
        if (personaId == "alt") {
            val legacyUser = settings.getStringOrNull(KEY_SOUL_USER)
            if (legacyUser != null) {
                settings.putString("soul_user_alt", legacyUser)
                settings.remove(KEY_SOUL_USER)
            }
            val legacyAuto = settings.getStringOrNull(KEY_SOUL_AUTO)
            if (legacyAuto != null) {
                settings.putString("soul_auto_alt", legacyAuto)
                settings.remove(KEY_SOUL_AUTO)
            }
        }
        return old
    }

    // Memory
    fun isMemoryEnabled(): Boolean = settings.getBoolean(KEY_MEMORY_ENABLED, true)

    fun setMemoryEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_MEMORY_ENABLED, enabled)
    }

    // Alt-memory (auto-installed in sandbox, user-toggleable)
    fun isAltMemoryEnabled(): Boolean = settings.getBoolean(KEY_ALT_MEMORY_ENABLED, false)

    fun setAltMemoryEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_ALT_MEMORY_ENABLED, enabled)
    }

    fun isAltMemoryInstalled(): Boolean = settings.getBoolean(KEY_ALT_MEMORY_INSTALLED, false)

    fun setAltMemoryInstalled(installed: Boolean) {
        settings.putBoolean(KEY_ALT_MEMORY_INSTALLED, installed)
    }

    fun getAltMemoryMode(): String = settings.getString(KEY_ALT_MEMORY_MODE, "native")
    fun setAltMemoryMode(mode: String) {
        settings.putString(KEY_ALT_MEMORY_MODE, mode)
    }

    fun getPttTriggerKeyCode(): Int = settings.getInt(KEY_PTT_TRIGGER_KEYCODE, 0)
    fun setPttTriggerKeyCode(keyCode: Int) {
        settings.putInt(KEY_PTT_TRIGGER_KEYCODE, keyCode)
    }

    // Agent memories
    fun getMemoriesJson(): String = settings.getString(KEY_AGENT_MEMORIES, "[]")

    fun setMemoriesJson(json: String) {
        settings.putString(KEY_AGENT_MEMORIES, json)
    }

    // Scheduling
    fun isSchedulingEnabled(): Boolean = settings.getBoolean(KEY_SCHEDULING_ENABLED, true)

    fun setSchedulingEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SCHEDULING_ENABLED, enabled)
    }

    // Dynamic UI
    fun isDynamicUiEnabled(): Boolean = settings.getBoolean(KEY_DYNAMIC_UI_ENABLED, true)

    fun setDynamicUiEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DYNAMIC_UI_ENABLED, enabled)
    }

    // Thinking display
    fun getHideThinking(): Boolean = settings.getBoolean(KEY_HIDE_THINKING, false)

    fun setHideThinking(enabled: Boolean) {
        settings.putBoolean(KEY_HIDE_THINKING, enabled)
    }

    fun getExpandThinking(): Boolean = settings.getBoolean(KEY_EXPAND_THINKING, false)

    fun setExpandThinking(enabled: Boolean) {
        settings.putBoolean(KEY_EXPAND_THINKING, enabled)
    }

    private val _themeModeFlow = MutableStateFlow(loadInitialThemeMode())
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow

    fun getThemeMode(): ThemeMode = _themeModeFlow.value

    fun setThemeMode(mode: ThemeMode) {
        settings.putString(KEY_THEME_MODE, mode.name)
        _themeModeFlow.value = mode
    }

    private fun loadInitialThemeMode(): ThemeMode {
        val raw = settings.getString(KEY_THEME_MODE, "")
        if (raw.isNotEmpty()) {
            return try {
                ThemeMode.valueOf(raw)
            } catch (_: IllegalArgumentException) {
                ThemeMode.System
            }
        }
        // Migrate the legacy boolean OLED toggle: true → OledBlack, false → System.
        return if (settings.getBoolean(KEY_OLED_MODE_ENABLED, false)) ThemeMode.OledBlack else ThemeMode.System
    }

    // Daemon mode
    fun isDaemonEnabled(): Boolean = settings.getBoolean(KEY_DAEMON_ENABLED, false)

    fun setDaemonEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DAEMON_ENABLED, enabled)
    }

    // Floating ball overlay
    fun isFloatingBallEnabled(): Boolean = settings.getBoolean(KEY_FLOATING_BALL_ENABLED, false)

    fun setFloatingBallEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_FLOATING_BALL_ENABLED, enabled)
    }

    // Linux Sandbox
    fun isSandboxEnabled(): Boolean = settings.getBoolean(KEY_SANDBOX_ENABLED, true)

    fun setSandboxEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SANDBOX_ENABLED, enabled)
    }

    fun isSandboxStorageMountEnabled(): Boolean = settings.getBoolean(KEY_SANDBOX_STORAGE_MOUNT, false)

    fun setSandboxStorageMountEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SANDBOX_STORAGE_MOUNT, enabled)
    }

    fun getSandboxWorkDir(): String = settings.getString(KEY_SAF_WORK_DIR, "")

    fun setSandboxWorkDir(uri: String) {
        settings.putString(KEY_SAF_WORK_DIR, uri)
    }

    fun getSandboxDistro(): String = settings.getString(KEY_SANDBOX_DISTRO, "alpine")

    fun setSandboxDistro(distro: String) {
        settings.putString(KEY_SANDBOX_DISTRO, distro)
    }

    fun isAltMemoryMigrationComplete(): Boolean = settings.getBoolean(KEY_ALT_MEMORY_MIGRATION_COMPLETE, false)

    fun setAltMemoryMigrationComplete(complete: Boolean) {
        settings.putBoolean(KEY_ALT_MEMORY_MIGRATION_COMPLETE, complete)
    }

    // Preferred language
    private val _preferredLanguageFlow = MutableStateFlow(settings.getString(KEY_PREFERRED_LANGUAGE, getDefaultLanguage()))
    val preferredLanguageFlow: StateFlow<String> = _preferredLanguageFlow

    fun getPreferredLanguage(): String = _preferredLanguageFlow.value

    fun setPreferredLanguage(lang: String) {
        settings.putString(KEY_PREFERRED_LANGUAGE, lang)
        _preferredLanguageFlow.value = lang
    }

    // Telegram Bot (pure HTTP — works on all platforms)
    fun isTelegramEnabled(): Boolean = settings.getBoolean(KEY_TELEGRAM_ENABLED, false)

    fun setTelegramEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_TELEGRAM_ENABLED, enabled)
    }

    fun getTelegramBotToken(): String = settings.getString(KEY_TELEGRAM_BOT_TOKEN, "")

    fun setTelegramBotToken(token: String) {
        settings.putString(KEY_TELEGRAM_BOT_TOKEN, token)
    }

    fun getTelegramPendingJson(): String = settings.getString(KEY_TELEGRAM_PENDING, "")

    fun setTelegramPendingJson(json: String) {
        settings.putString(KEY_TELEGRAM_PENDING, json)
    }

    fun getTelegramSyncStateJson(): String = settings.getString(KEY_TELEGRAM_SYNC_STATE, "")

    fun setTelegramSyncStateJson(json: String) {
        settings.putString(KEY_TELEGRAM_SYNC_STATE, json)
    }

    fun getTelegramAuthorizedChatIds(): Set<Long> {
        val raw = settings.getString(KEY_TELEGRAM_AUTHORIZED_CHAT_IDS, "")
        if (raw.isBlank()) return emptySet()
        return try {
            raw.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setTelegramAuthorizedChatIds(ids: Set<Long>) {
        settings.putString(KEY_TELEGRAM_AUTHORIZED_CHAT_IDS, ids.joinToString(","))
    }

    // WhatsApp Bridge (Baileys MCP via proot sandbox)
    fun isWhatsAppEnabled(): Boolean = settings.getBoolean(KEY_WHATSAPP_ENABLED, false)
    fun setWhatsAppEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_WHATSAPP_ENABLED, enabled)
    }

    fun isWhatsAppInstalled(): Boolean = settings.getBoolean(KEY_WHATSAPP_INSTALLED, false)
    fun setWhatsAppInstalled(installed: Boolean) {
        settings.putBoolean(KEY_WHATSAPP_INSTALLED, installed)
    }

    fun isWhatsAppAuthenticated(): Boolean = settings.getBoolean(KEY_WHATSAPP_AUTHENTICATED, false)
    fun setWhatsAppAuthenticated(auth: Boolean) {
        settings.putBoolean(KEY_WHATSAPP_AUTHENTICATED, auth)
    }

    fun getWhatsAppQrCode(): String = settings.getString(KEY_WHATSAPP_QR_CODE, "")
    fun setWhatsAppQrCode(qr: String) {
        settings.putString(KEY_WHATSAPP_QR_CODE, qr)
    }

    fun getWhatsAppPendingJson(): String = settings.getString(KEY_WHATSAPP_PENDING, "")
    fun setWhatsAppPendingJson(json: String) {
        settings.putString(KEY_WHATSAPP_PENDING, json)
    }

    fun isWhatsAppReadOnly(): Boolean = settings.getBoolean(KEY_WHATSAPP_READ_ONLY, true)
    fun setWhatsAppReadOnly(readOnly: Boolean) {
        settings.putBoolean(KEY_WHATSAPP_READ_ONLY, readOnly)
    }

    fun getWhatsAppReplyMode(): String = settings.getString(KEY_WHATSAPP_REPLY_MODE, "all")
    fun setWhatsAppReplyMode(mode: String) {
        settings.putString(KEY_WHATSAPP_REPLY_MODE, mode)
    }

    fun getWhatsAppAllowedContacts(): String = settings.getString(KEY_WHATSAPP_ALLOWED_CONTACTS, "")
    fun setWhatsAppAllowedContacts(contacts: String) {
        settings.putString(KEY_WHATSAPP_ALLOWED_CONTACTS, contacts)
    }

    fun isWhatsAppReadReceipt(): Boolean = settings.getBoolean(KEY_WHATSAPP_READ_RECEIPT, false)
    fun setWhatsAppReadReceipt(enabled: Boolean) {
        settings.putBoolean(KEY_WHATSAPP_READ_RECEIPT, enabled)
    }

    // Baileys connection config
    fun getBaileysBrowserName(): String = settings.getString(KEY_BAILEYS_BROWSER_NAME, "Windows")
    fun setBaileysBrowserName(v: String) {
        settings.putString(KEY_BAILEYS_BROWSER_NAME, v)
    }

    fun getBaileysBrowserVersion(): String = settings.getString(KEY_BAILEYS_BROWSER_VERSION, "130.0.0.0")
    fun setBaileysBrowserVersion(v: String) {
        settings.putString(KEY_BAILEYS_BROWSER_VERSION, v)
    }

    fun getBaileysMarkOnline(): Boolean = settings.getBoolean(KEY_BAILEYS_MARK_ONLINE, true)
    fun setBaileysMarkOnline(v: Boolean) {
        settings.putBoolean(KEY_BAILEYS_MARK_ONLINE, v)
    }

    fun getBaileysSyncHistory(): Boolean = settings.getBoolean(KEY_BAILEYS_SYNC_HISTORY, true)
    fun setBaileysSyncHistory(v: Boolean) {
        settings.putBoolean(KEY_BAILEYS_SYNC_HISTORY, v)
    }

    fun getBaileysLinkPreviews(): Boolean = settings.getBoolean(KEY_BAILEYS_LINK_PREVIEWS, true)
    fun setBaileysLinkPreviews(v: Boolean) {
        settings.putBoolean(KEY_BAILEYS_LINK_PREVIEWS, v)
    }

    // SSH connection
    fun isSshEnabled(): Boolean = settings.getBoolean(KEY_SSH_ENABLED, true)

    fun setSshEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SSH_ENABLED, enabled)
    }

    fun getSshHost(): String = settings.getString(KEY_SSH_HOST, "")

    fun setSshHost(host: String) {
        settings.putString(KEY_SSH_HOST, host)
    }

    fun getSshPort(): Int = settings.getInt(KEY_SSH_PORT, 22)

    fun setSshPort(port: Int) {
        settings.putInt(KEY_SSH_PORT, port)
    }

    fun getSshUsername(): String = settings.getString(KEY_SSH_USERNAME, "")

    fun setSshUsername(username: String) {
        settings.putString(KEY_SSH_USERNAME, username)
    }

    fun getSshAuthMethod(): com.kai.custom.SshAuthMethod {
        val name = settings.getString(KEY_SSH_AUTH_METHOD, com.kai.custom.SshAuthMethod.PASSWORD.name)
        return try {
            com.kai.custom.SshAuthMethod.valueOf(name)
        } catch (_: Exception) {
            com.kai.custom.SshAuthMethod.PASSWORD
        }
    }

    fun setSshAuthMethod(method: com.kai.custom.SshAuthMethod) {
        settings.putString(KEY_SSH_AUTH_METHOD, method.name)
    }

    fun getSshPassword(): String = settings.getString(KEY_SSH_PASSWORD, "")

    fun setSshPassword(password: String) {
        settings.putString(KEY_SSH_PASSWORD, password)
    }

    fun getSshPrivateKey(): String = settings.getString(KEY_SSH_PRIVATE_KEY, "")

    fun setSshPrivateKey(key: String) {
        settings.putString(KEY_SSH_PRIVATE_KEY, key)
    }

    fun getSshPassphrase(): String = settings.getString(KEY_SSH_PASSPHRASE, "")

    fun setSshPassphrase(passphrase: String) {
        settings.putString(KEY_SSH_PASSPHRASE, passphrase)
    }

    fun getSshProfiles(): List<SshProfile> {
        val json = settings.getString(KEY_SSH_PROFILES, "")
        if (json.isBlank()) return emptyList()
        return try {
            SharedJson.decodeFromString(ListSerializer(SshProfile.serializer()), json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSshProfiles(profiles: List<SshProfile>) {
        val json = SharedJson.encodeToString(ListSerializer(SshProfile.serializer()), profiles)
        settings.putString(KEY_SSH_PROFILES, json)
    }

    fun saveSshProfile(profile: SshProfile) {
        val profiles = getSshProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.name == profile.name }
        if (index >= 0) {
            profiles[index] = profile
        } else {
            profiles.add(profile)
        }
        saveSshProfiles(profiles)
    }

    fun deleteSshProfile(name: String) {
        val profiles = getSshProfiles().toMutableList()
        profiles.removeAll { it.name == name }
        saveSshProfiles(profiles)
        if (getActiveSshProfileName() == name) {
            setActiveSshProfileName("")
        }
    }

    fun getActiveSshProfileName(): String = settings.getString(KEY_SSH_ACTIVE_PROFILE, "")

    fun setActiveSshProfileName(name: String) {
        settings.putString(KEY_SSH_ACTIVE_PROFILE, name)
        val profile = getSshProfiles().find { it.name == name }
        if (profile != null) {
            setSshHost(profile.host)
            setSshPort(profile.port)
            setSshUsername(profile.username)
            setSshAuthMethod(profile.authMethod)
            setSshPassword(profile.password)
            setSshPrivateKey(profile.privateKey)
            setSshPassphrase(profile.passphrase)
        }
    }

    // Wake word detection
    private val _wakeWordEnabledFlow = MutableStateFlow(settings.getBoolean(KEY_WAKE_WORD_ENABLED, false))
    val wakeWordEnabledFlow: StateFlow<Boolean> = _wakeWordEnabledFlow

    fun isWakeWordEnabled(): Boolean = _wakeWordEnabledFlow.value

    fun setWakeWordEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_WAKE_WORD_ENABLED, enabled)
        _wakeWordEnabledFlow.value = enabled
    }

    fun getWakeWordPhrase(): String = settings.getString(KEY_WAKE_WORD_PHRASE, "hey kai")

    fun setWakeWordPhrase(phrase: String) {
        settings.putString(KEY_WAKE_WORD_PHRASE, phrase)
    }

    fun getWakeWordMode(): String = settings.getString(KEY_WAKE_WORD_MODE, "GENERAL")

    private val _wakeWordModeFlow = MutableStateFlow(getWakeWordMode())
    val wakeWordModeFlow: StateFlow<String> = _wakeWordModeFlow

    fun setWakeWordMode(mode: String) {
        settings.putString(KEY_WAKE_WORD_MODE, mode)
        _wakeWordModeFlow.value = mode
    }

    fun getWakeWordTemplate(): String = settings.getString(KEY_WAKE_WORD_TEMPLATE, "")

    fun setWakeWordTemplate(template: String) {
        settings.putString(KEY_WAKE_WORD_TEMPLATE, template)
    }

    fun getScheduledTasksJson(): String = settings.getString(KEY_SCHEDULED_TASKS, "[]")

    fun setScheduledTasksJson(json: String) {
        settings.putString(KEY_SCHEDULED_TASKS, json)
    }

    // Heartbeat config
    fun getHeartbeatConfigJson(): String = settings.getString(KEY_HEARTBEAT_CONFIG, "")

    fun setHeartbeatConfigJson(json: String) {
        settings.putString(KEY_HEARTBEAT_CONFIG, json)
    }

    // Heartbeat log
    fun getHeartbeatLogJson(): String = settings.getString(KEY_HEARTBEAT_LOG, "")

    fun setHeartbeatLogJson(json: String) {
        settings.putString(KEY_HEARTBEAT_LOG, json)
    }

    // Heartbeat prompt
    fun getHeartbeatPrompt(): String = settings.getString(KEY_HEARTBEAT_PROMPT, "")

    fun setHeartbeatPrompt(text: String) {
        settings.putString(KEY_HEARTBEAT_PROMPT, text)
    }

    // MCP Servers
    fun getMcpServersJson(): String = settings.getString(KEY_MCP_SERVERS, "")

    fun setMcpServersJson(json: String) {
        settings.putString(KEY_MCP_SERVERS, json)
    }

    // UI Scale
    private val _uiScaleFlow = MutableStateFlow(settings.getFloat(KEY_UI_SCALE, defaultUiScale))
    val uiScaleFlow: StateFlow<Float> = _uiScaleFlow

    fun getUiScale(): Float = _uiScaleFlow.value

    fun setUiScale(scale: Float) {
        settings.putFloat(KEY_UI_SCALE, scale)
        _uiScaleFlow.value = scale
    }

    // Email
    fun isEmailEnabled(): Boolean = settings.getBoolean(KEY_EMAIL_ENABLED, true)

    fun setEmailEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_EMAIL_ENABLED, enabled)
    }

    fun getEmailAccountsJson(): String = settings.getString(KEY_EMAIL_ACCOUNTS, "")

    fun setEmailAccountsJson(json: String) {
        settings.putString(KEY_EMAIL_ACCOUNTS, json)
    }

    fun getEmailPassword(accountId: String): String = settings.getString("${KEY_EMAIL_PASSWORD_PREFIX}$accountId", "")

    fun setEmailPassword(accountId: String, password: String) {
        settings.putString("${KEY_EMAIL_PASSWORD_PREFIX}$accountId", password)
    }

    fun removeEmailPassword(accountId: String) {
        settings.remove("${KEY_EMAIL_PASSWORD_PREFIX}$accountId")
    }

    fun getEmailSyncStateJson(accountId: String): String = settings.getString("${KEY_EMAIL_SYNC_PREFIX}$accountId", "")

    fun setEmailSyncStateJson(accountId: String, json: String) {
        settings.putString("${KEY_EMAIL_SYNC_PREFIX}$accountId", json)
    }

    fun getEmailPollIntervalMinutes(): Int = settings.getInt(KEY_EMAIL_POLL_INTERVAL, 15)

    fun setEmailPollIntervalMinutes(minutes: Int) {
        settings.putInt(KEY_EMAIL_POLL_INTERVAL, minutes)
    }

    fun getEmailPendingJson(): String = settings.getString(KEY_EMAIL_PENDING, "")

    fun setEmailPendingJson(json: String) {
        settings.putString(KEY_EMAIL_PENDING, json)
    }

    // SMS (FOSS-only, Android-only — settings layer is platform-agnostic, feature gate
    // is enforced by the READ_SMS permission being declared only in foss/AndroidManifest.xml)
    fun isSmsEnabled(): Boolean = settings.getBoolean(KEY_SMS_ENABLED, false)

    fun setSmsEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SMS_ENABLED, enabled)
    }

    fun getSmsPollIntervalMinutes(): Int = settings.getInt(KEY_SMS_POLL_INTERVAL, 15)

    fun setSmsPollIntervalMinutes(minutes: Int) {
        settings.putInt(KEY_SMS_POLL_INTERVAL, minutes)
    }

    fun getSmsPendingJson(): String = settings.getString(KEY_SMS_PENDING, "")

    fun setSmsPendingJson(json: String) {
        settings.putString(KEY_SMS_PENDING, json)
    }

    fun getSmsSyncStateJson(): String = settings.getString(KEY_SMS_SYNC_STATE, "")

    fun setSmsSyncStateJson(json: String) {
        settings.putString(KEY_SMS_SYNC_STATE, json)
    }

    fun isSmsSendEnabled(): Boolean = settings.getBoolean(KEY_SMS_SEND_ENABLED, false)

    fun setSmsSendEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SMS_SEND_ENABLED, enabled)
    }

    fun getSmsDraftsJson(): String = settings.getString(KEY_SMS_DRAFTS, "")

    fun setSmsDraftsJson(json: String) {
        settings.putString(KEY_SMS_DRAFTS, json)
    }

    // Shizuku / ADB commands (Android-only — settings layer is platform-agnostic; feature
    // gate is enforced by Platform.isShizukuSupported)
    fun isShizukuEnabled(): Boolean = settings.getBoolean(KEY_SHIZUKU_ENABLED, false)

    fun setShizukuEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SHIZUKU_ENABLED, enabled)
    }

    // Root shell (Android-only with su; feature gate is Platform.isRootSupported)
    fun isRootEnabled(): Boolean = settings.getBoolean(KEY_ROOT_ENABLED, false)

    fun setRootEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_ROOT_ENABLED, enabled)
    }

    fun isSandboxRootEnabled(): Boolean = settings.getBoolean(KEY_SANDBOX_ROOT_ENABLED, false)

    fun setSandboxRootEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SANDBOX_ROOT_ENABLED, enabled)
    }

    // Debug API server (Android-only debug builds only; feature gate is Platform.isDebugBuild)
    fun isDebugApiEnabled(): Boolean = settings.getBoolean(KEY_DEBUG_API_ENABLED, false)
    fun setDebugApiEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DEBUG_API_ENABLED, enabled)
    }

    // Debug endpoint (opencode.ai/zen, debug builds only)
    fun isDebugEndpointEnabled(): Boolean = settings.getBoolean(KEY_DEBUG_ENDPOINT_ENABLED, false)
    fun setDebugEndpointEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DEBUG_ENDPOINT_ENABLED, enabled)
    }

    // Auto-update
    fun isAutoUpdateEnabled(): Boolean = settings.getBoolean(KEY_AUTO_UPDATE_ENABLED, true)
    fun setAutoUpdateEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled)
    }
    fun isAutoDownloadEnabled(): Boolean = settings.getBoolean(KEY_AUTO_DOWNLOAD_ENABLED, false)
    fun setAutoDownloadEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_AUTO_DOWNLOAD_ENABLED, enabled)
    }
    fun isAutoInstallEnabled(): Boolean = settings.getBoolean(KEY_AUTO_INSTALL_ENABLED, false)
    fun setAutoInstallEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_AUTO_INSTALL_ENABLED, enabled)
    }

    // Notifications (FOSS-only, Android-only — settings layer is platform-agnostic, feature
    // gate is enforced by the listener service being declared only in foss/AndroidManifest.xml)
    fun isNotificationsEnabled(): Boolean = settings.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)

    fun setNotificationsEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
    }

    fun getNotificationsPendingJson(): String = settings.getString(KEY_NOTIFICATIONS_PENDING, "")

    fun setNotificationsPendingJson(json: String) {
        settings.putString(KEY_NOTIFICATIONS_PENDING, json)
    }

    fun getNotificationsStoreJson(): String = settings.getString(KEY_NOTIFICATIONS_STORE, "")

    fun setNotificationsStoreJson(json: String) {
        settings.putString(KEY_NOTIFICATIONS_STORE, json)
    }

    fun getNotificationsSyncStateJson(): String = settings.getString(KEY_NOTIFICATIONS_SYNC_STATE, "")

    fun setNotificationsSyncStateJson(json: String) {
        settings.putString(KEY_NOTIFICATIONS_SYNC_STATE, json)
    }

    // Local model context size
    fun getModelContextTokens(modelId: String): Int = settings.getInt("$KEY_MODEL_CONTEXT_PREFIX$modelId", 0)

    fun setModelContextTokens(modelId: String, contextTokens: Int) {
        settings.putInt("$KEY_MODEL_CONTEXT_PREFIX$modelId", contextTokens)
    }

    // Max output tokens per model (0 = use provider default / not configured)
    fun getModelMaxTokens(modelId: String): Int = settings.getInt("$KEY_MODEL_MAX_TOKENS_PREFIX$modelId", 0)

    fun setModelMaxTokens(modelId: String, maxTokens: Int) {
        settings.putInt("$KEY_MODEL_MAX_TOKENS_PREFIX$modelId", maxTokens)
    }

    // Temperature per model (0.0 = min, 2.0 = max, 0.8 = default)
    fun getModelTemperature(modelId: String): Float = settings.getFloat("$KEY_MODEL_TEMPERATURE_PREFIX$modelId", 0.8f)

    fun setModelTemperature(modelId: String, temperature: Float) {
        settings.putFloat("$KEY_MODEL_TEMPERATURE_PREFIX$modelId", temperature.coerceIn(0.0f, 2.0f))
    }

    // Local model style instruction — prepended to system prompt for on-device models
    fun getLocalStyleInstruction(): String = settings.getString(KEY_LOCAL_STYLE_INSTRUCTION, DEFAULT_LOCAL_STYLE_INSTRUCTION)

    fun setLocalStyleInstruction(text: String) {
        settings.putString(KEY_LOCAL_STYLE_INSTRUCTION, text)
    }

    fun isLocalModelFullPrompt(): Boolean = settings.getBoolean(KEY_LOCAL_MODEL_FULL_PROMPT, false)

    fun setLocalModelFullPrompt(enabled: Boolean) {
        settings.putBoolean(KEY_LOCAL_MODEL_FULL_PROMPT, enabled)
    }

    // Top-K per model (1-100, 40 = default)
    fun getModelTopK(modelId: String): Int = settings.getInt("$KEY_MODEL_TOP_K_PREFIX$modelId", 40)

    fun setModelTopK(modelId: String, topK: Int) {
        settings.putInt("$KEY_MODEL_TOP_K_PREFIX$modelId", topK.coerceIn(1, 100))
    }

    // Top-P per model (0.0-1.0, 0.95 = default)
    fun getModelTopP(modelId: String): Float = settings.getFloat("$KEY_MODEL_TOP_P_PREFIX$modelId", 0.95f)

    fun setModelTopP(modelId: String, topP: Float) {
        settings.putFloat("$KEY_MODEL_TOP_P_PREFIX$modelId", topP.coerceIn(0.0f, 1.0f))
    }

    // GPU layers per model (0 = CPU only, 999 = all layers, default 20)
    fun getModelGpuLayers(modelId: String): Int = settings.getInt("$KEY_MODEL_GPU_LAYERS_PREFIX$modelId", 20)

    fun setModelGpuLayers(modelId: String, gpuLayers: Int) {
        settings.putInt("$KEY_MODEL_GPU_LAYERS_PREFIX$modelId", gpuLayers.coerceIn(0, 999))
    }

    // Imported custom models
    private val importedModelsJson = Json { ignoreUnknownKeys = true }
    private val importedModelsSerializer = ListSerializer(com.kai.custom.inference.ImportedModel.serializer())

    fun getImportedModels(): List<com.kai.custom.inference.ImportedModel> {
        val raw = settings.getStringOrNull(KEY_IMPORTED_MODELS) ?: return emptyList()
        return try {
            importedModelsJson.decodeFromString(importedModelsSerializer, raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addImportedModel(model: com.kai.custom.inference.ImportedModel) {
        val list = getImportedModels().toMutableList()
        list.removeAll { it.id == model.id }
        list.add(model)
        settings.putString(KEY_IMPORTED_MODELS, importedModelsJson.encodeToString(importedModelsSerializer, list))
    }

    fun removeImportedModel(modelId: String) {
        val list = getImportedModels().filter { it.id != modelId }
        settings.putString(KEY_IMPORTED_MODELS, importedModelsJson.encodeToString(importedModelsSerializer, list))
    }

    // Default calendar account ID
    fun getDefaultCalendarId(): Long = settings.getLong(KEY_DEFAULT_CALENDAR_ID, -1L)

    fun setDefaultCalendarId(calendarId: Long) {
        settings.putLong(KEY_DEFAULT_CALENDAR_ID, calendarId)
    }

    // Splinterlands
    fun isSplinterlandsEnabled(): Boolean = settings.getBoolean(KEY_SPLINTERLANDS_ENABLED, false)

    fun setSplinterlandsEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SPLINTERLANDS_ENABLED, enabled)
    }

    fun getSplinterlandsAccountJson(): String = settings.getString(KEY_SPLINTERLANDS_ACCOUNT, "")

    fun setSplinterlandsAccountJson(json: String) {
        settings.putString(KEY_SPLINTERLANDS_ACCOUNT, json)
    }

    fun getSplinterlandsPostingKey(): String = settings.getString(KEY_SPLINTERLANDS_POSTING_KEY, "")

    fun getSplinterlandsPostingKey(accountId: String): String = settings.getString("${KEY_SPLINTERLANDS_POSTING_KEY}_$accountId", "")
        .ifEmpty { getSplinterlandsPostingKey() } // fallback to legacy key

    fun setSplinterlandsPostingKey(accountId: String, key: String) {
        settings.putString("${KEY_SPLINTERLANDS_POSTING_KEY}_$accountId", key)
    }

    fun getSplinterlandsInstanceId(): String = settings.getString(KEY_SPLINTERLANDS_INSTANCE_ID, "")

    fun setSplinterlandsInstanceId(instanceId: String) {
        settings.putString(KEY_SPLINTERLANDS_INSTANCE_ID, instanceId)
    }

    fun getSplinterlandsInstanceIdsJson(): String = settings.getString(KEY_SPLINTERLANDS_INSTANCE_IDS, "")

    fun setSplinterlandsInstanceIdsJson(json: String) {
        settings.putString(KEY_SPLINTERLANDS_INSTANCE_IDS, json)
    }

    fun getSplinterlandsBattleLogJson(): String = settings.getString(KEY_SPLINTERLANDS_BATTLE_LOG, "")

    fun setSplinterlandsBattleLogJson(json: String) {
        settings.putString(KEY_SPLINTERLANDS_BATTLE_LOG, json)
    }

    fun getActiveSkillId(): String? = settings.getStringOrNull(KEY_ACTIVE_SKILL_ID)

    fun setActiveSkillId(id: String?) {
        if (id != null) settings.putString(KEY_ACTIVE_SKILL_ID, id) else settings.remove(KEY_ACTIVE_SKILL_ID)
    }

    companion object {
        const val KEY_CURRENT_SERVICE_ID = "current_service_id"
        const val KEY_APP_OPENS = "app_opens"

        const val KEY_ACTIVE_SKILL_ID = "active_skill_id"

        const val KEY_CONVERSATIONS = "conversations_json"
        const val KEY_CURRENT_CONVERSATION_ID = "current_conversation_id"
        const val KEY_CURRENT_INTERACTIVE_MODE = "current_interactive_mode"
        const val KEY_CURRENT_CONVERSATION_MIGRATED = "current_conversation_migrated"
        const val KEY_ENCRYPTION_KEY = "encryption_key"
        const val KEY_MIGRATION_COMPLETE = "migration_complete_v1"
        const val KEY_TOOL_PREFIX = "tool_enabled_"
        const val KEY_SOUL_USER = "soul_user" // legacy, use per-persona keys
        const val KEY_SOUL_AUTO = "soul_auto" // legacy, use per-persona keys
        const val KEY_PERSONA_NAME = "current_persona" // legacy, use active_persona_id
        const val KEY_ACTIVE_PERSONA_ID = "active_persona_id"
        const val KEY_MEMORY_ENABLED = "memory_enabled"
        const val KEY_PTT_TRIGGER_KEYCODE = "ptt_trigger_keycode"
        const val KEY_ALT_MEMORY_ENABLED = "alt_memory_enabled"
        const val KEY_ALT_MEMORY_INSTALLED = "alt_memory_installed"
        const val KEY_ALT_MEMORY_MODE = "alt_memory_mode"
        const val KEY_AGENT_MEMORIES = "agent_memories"
        const val KEY_SCHEDULED_TASKS = "scheduled_tasks"
        const val KEY_SCHEDULING_ENABLED = "scheduling_enabled"
        const val KEY_DYNAMIC_UI_ENABLED = "dynamic_ui_enabled"
        const val KEY_HIDE_THINKING = "hide_thinking"
        const val KEY_EXPAND_THINKING = "expand_thinking"
        const val KEY_OLED_MODE_ENABLED = "oled_mode_enabled"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DAEMON_ENABLED = "daemon_enabled"
        const val KEY_FLOATING_BALL_ENABLED = "floating_ball_enabled"
        const val KEY_HEARTBEAT_CONFIG = "heartbeat_config"
        const val KEY_HEARTBEAT_PROMPT = "heartbeat_prompt"
        const val KEY_HEARTBEAT_LOG = "heartbeat_log"

        const val KEY_EMAIL_ENABLED = "email_enabled"
        const val KEY_EMAIL_ACCOUNTS = "email_accounts"
        const val KEY_EMAIL_PASSWORD_PREFIX = "email_password_"
        const val KEY_EMAIL_SYNC_PREFIX = "email_sync_"
        const val KEY_EMAIL_POLL_INTERVAL = "email_poll_interval"
        const val KEY_EMAIL_PENDING = "email_pending"

        const val KEY_SMS_ENABLED = "sms_enabled"
        const val KEY_SMS_POLL_INTERVAL = "sms_poll_interval"
        const val KEY_SMS_PENDING = "sms_pending"
        const val KEY_SMS_SYNC_STATE = "sms_sync_state"
        const val KEY_SMS_SEND_ENABLED = "sms_send_enabled"
        const val KEY_SMS_DRAFTS = "sms_drafts"

        const val KEY_SHIZUKU_ENABLED = "shizuku_enabled"
        const val KEY_ROOT_ENABLED = "root_enabled"
        const val KEY_SANDBOX_ROOT_ENABLED = "sandbox_root_enabled"
        const val KEY_DEBUG_API_ENABLED = "debug_api_enabled"
        const val KEY_DEBUG_ENDPOINT_ENABLED = "debug_endpoint_enabled"

        const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"
        const val KEY_AUTO_DOWNLOAD_ENABLED = "auto_download_enabled"
        const val KEY_AUTO_INSTALL_ENABLED = "auto_install_enabled"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_NOTIFICATIONS_PENDING = "notifications_pending"
        const val KEY_NOTIFICATIONS_STORE = "notifications_store"
        const val KEY_NOTIFICATIONS_SYNC_STATE = "notifications_sync_state"
        const val KEY_CONFIGURED_SERVICES = "configured_services"
        const val KEY_FREE_FALLBACK_ENABLED = "free_fallback_enabled"
        const val KEY_FREE_MODE = "free_mode"
        const val KEY_FREE_SERVICE_PRIMARY = "free_service_primary"
        const val KEY_SERVICES_MIGRATION_COMPLETE = "services_migration_complete_v1"
        const val KEY_UI_SCALE = "ui_scale"
        const val KEY_MCP_SERVERS = "mcp_servers"
        const val KEY_INSTANCE_MIGRATION_COMPLETE = "instance_migration_complete_v1"
        const val KEY_BASE_URL_V1_MIGRATION_COMPLETE = "base_url_v1_migration_complete"

        const val KEY_SPLINTERLANDS_ENABLED = "splinterlands_enabled"
        const val KEY_SPLINTERLANDS_ACCOUNT = "splinterlands_account"
        const val KEY_SPLINTERLANDS_POSTING_KEY = "splinterlands_posting_key"
        const val KEY_SPLINTERLANDS_BATTLE_LOG = "splinterlands_battle_log"
        const val KEY_SPLINTERLANDS_INSTANCE_ID = "splinterlands_instance_id"
        const val KEY_SPLINTERLANDS_INSTANCE_IDS = "splinterlands_instance_ids"

        const val KEY_MODEL_CONTEXT_PREFIX = "model_context_"
        const val KEY_MODEL_MAX_TOKENS_PREFIX = "model_maxtokens_"
        const val KEY_MODEL_TEMPERATURE_PREFIX = "model_temperature_"
        const val KEY_LOCAL_STYLE_INSTRUCTION = "local_style_instruction"
        const val DEFAULT_LOCAL_STYLE_INSTRUCTION = "Speak naturally and conversationally. Avoid internal monologue, clinical analysis, or robotic formatting. Just respond like a normal person with a natural voice."
        const val KEY_LOCAL_MODEL_FULL_PROMPT = "local_model_full_prompt"
        const val KEY_MODEL_TOP_K_PREFIX = "model_topk_"
        const val KEY_MODEL_TOP_P_PREFIX = "model_topp_"
        const val KEY_MODEL_GPU_LAYERS_PREFIX = "model_gpu_layers_"
        const val KEY_IMPORTED_MODELS = "imported_models"

        const val KEY_DEFAULT_CALENDAR_ID = "default_calendar_id"

        const val KEY_SANDBOX_ENABLED = "sandbox_enabled"
        const val KEY_SANDBOX_STORAGE_MOUNT = "sandbox_storage_mount"
        const val KEY_SAF_WORK_DIR = "saf_work_dir"
        const val KEY_SANDBOX_DISTRO = "sandbox_distro"
        const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        const val KEY_WAKE_WORD_PHRASE = "wake_word_phrase"
        const val KEY_WAKE_WORD_MODE = "wake_word_mode"
        const val KEY_WAKE_WORD_TEMPLATE = "wake_word_template"
        const val KEY_PREFERRED_LANGUAGE = "preferred_language"

        const val KEY_SSH_ENABLED = "ssh_enabled"
        const val KEY_SSH_HOST = "ssh_host"
        const val KEY_SSH_PORT = "ssh_port"
        const val KEY_SSH_USERNAME = "ssh_username"
        const val KEY_SSH_AUTH_METHOD = "ssh_auth_method"
        const val KEY_SSH_PASSWORD = "ssh_password"
        const val KEY_SSH_PRIVATE_KEY = "ssh_private_key"
        const val KEY_SSH_PASSPHRASE = "ssh_passphrase"
        const val KEY_SSH_PROFILES = "ssh_profiles"

        const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        const val KEY_TELEGRAM_PENDING = "telegram_pending"
        const val KEY_TELEGRAM_SYNC_STATE = "telegram_sync_state"
        const val KEY_TELEGRAM_POLL_INTERVAL = "telegram_poll_interval"
        const val KEY_TELEGRAM_AUTHORIZED_CHAT_IDS = "telegram_authorized_chat_ids"
        const val KEY_SSH_ACTIVE_PROFILE = "ssh_active_profile"
        const val KEY_ALT_MEMORY_MIGRATION_COMPLETE = "alt_memory_migration_complete"
        const val KEY_WHATSAPP_ENABLED = "whatsapp_enabled"
        const val KEY_WHATSAPP_INSTALLED = "whatsapp_installed"
        const val KEY_WHATSAPP_AUTHENTICATED = "whatsapp_authenticated"
        const val KEY_WHATSAPP_QR_CODE = "whatsapp_qr_code"
        const val KEY_WHATSAPP_PENDING = "whatsapp_pending"
        const val KEY_WHATSAPP_READ_ONLY = "whatsapp_read_only"
        const val KEY_WHATSAPP_REPLY_MODE = "whatsapp_reply_mode"
        const val KEY_WHATSAPP_ALLOWED_CONTACTS = "whatsapp_allowed_contacts"
        const val KEY_WHATSAPP_READ_RECEIPT = "whatsapp_read_receipt"
        const val KEY_BAILEYS_BROWSER_NAME = "baileys_browser_name"
        const val KEY_BAILEYS_BROWSER_VERSION = "baileys_browser_version"
        const val KEY_BAILEYS_MARK_ONLINE = "baileys_mark_online"
        const val KEY_BAILEYS_SYNC_HISTORY = "baileys_sync_history"
        const val KEY_BAILEYS_LINK_PREVIEWS = "baileys_link_previews"
        const val KEY_ENTER_TO_SEND = "enter_to_send"
    }

    fun isEnterToSend(): Boolean = settings.getBoolean(KEY_ENTER_TO_SEND, false)
    fun setEnterToSend(enabled: Boolean) {
        settings.putBoolean(KEY_ENTER_TO_SEND, enabled)
    }
}
