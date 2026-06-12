package com.kai.custom.tools

import com.kai.custom.data.AppSettings
import com.kai.custom.data.BehaviorStyle
import com.kai.custom.data.CharacterType
import com.kai.custom.data.LanguageStyle
import com.kai.custom.data.MemoryCategory
import com.kai.custom.data.MemoryEntry
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.PersonaConfig
import com.kai.custom.data.PersonaManager
import com.kai.custom.data.RenderMode
import com.kai.custom.httpClient
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.openUrl
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_get_local_time_description
import kai.composeapp.generated.resources.tool_get_local_time_name
import kai.composeapp.generated.resources.tool_get_location_description
import kai.composeapp.generated.resources.tool_get_location_name
import kai.composeapp.generated.resources.tool_memory_forget_description
import kai.composeapp.generated.resources.tool_memory_forget_name
import kai.composeapp.generated.resources.tool_memory_learn_description
import kai.composeapp.generated.resources.tool_memory_learn_name
import kai.composeapp.generated.resources.tool_memory_reinforce_description
import kai.composeapp.generated.resources.tool_memory_reinforce_name
import kai.composeapp.generated.resources.tool_memory_store_description
import kai.composeapp.generated.resources.tool_memory_store_name
import kai.composeapp.generated.resources.tool_open_url_description
import kai.composeapp.generated.resources.tool_open_url_name
import kai.composeapp.generated.resources.tool_run_adb_description
import kai.composeapp.generated.resources.tool_run_adb_name
import kai.composeapp.generated.resources.tool_run_opencode_description
import kai.composeapp.generated.resources.tool_run_opencode_name
import kai.composeapp.generated.resources.tool_speak_text_description
import kai.composeapp.generated.resources.tool_speak_text_name
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class IpLocationResponse(
    val ip: String? = null,
    val success: Boolean = false,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val postal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val connection: IpConnectionInfo? = null,
    val timezone: IpTimezoneInfo? = null,
    val message: String? = null,
)

@Serializable
private data class IpConnectionInfo(
    val isp: String? = null,
    val org: String? = null,
)

@Serializable
private data class IpTimezoneInfo(
    val id: String? = null,
)

/**
 * Common tool definitions that work across all platforms.
 */
object CommonTools {

    private val locationClient = httpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
        }
    }

    val ipLocationTool = object : Tool {
        override val schema = ToolSchema(
            name = "get_location_from_ip",
            description = "Get the user's estimated location based on their IP address. Returns city, region, country, coordinates, and timezone.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any = try {
            val response: IpLocationResponse = locationClient.get("https://ipwho.is/").body()
            if (response.success) {
                mapOf(
                    "success" to true,
                    "city" to response.city,
                    "region" to response.region,
                    "country" to response.country,
                    "country_code" to response.countryCode,
                    "latitude" to response.latitude,
                    "longitude" to response.longitude,
                    "timezone" to response.timezone?.id,
                    "zip" to response.postal,
                    "isp" to response.connection?.isp,
                    "ip" to response.ip,
                )
            } else {
                mapOf(
                    "success" to false,
                    "error" to (response.message ?: "Failed to get location"),
                )
            }
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to "Failed to get location: ${e.message}",
            )
        }
    }

    val ipLocationToolInfo = ToolInfo(
        id = "get_location_from_ip",
        name = "Get Location",
        description = "Get estimated location from IP address",
        nameRes = Res.string.tool_get_location_name,
        descriptionRes = Res.string.tool_get_location_description,
    )

    val localTimeTool = object : Tool {
        override val schema = ToolSchema(
            name = "get_local_time",
            description = "Get the current local date and time. Call this first when the user mentions relative dates like 'tomorrow', 'next week', 'in 2 hours', etc.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val timeZone = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val localDateTime = now.toLocalDateTime(timeZone)

            // Format display string manually since kotlinx-datetime doesn't have formatters
            val dayOfWeek = localDateTime.dayOfWeek.name.lowercase()
                .replaceFirstChar { it.uppercase() }
            val month = localDateTime.month.name.lowercase()
                .replaceFirstChar { it.uppercase() }
            val day = localDateTime.date.day
            val year = localDateTime.year
            val hour = localDateTime.hour
            val minute = localDateTime.minute.toString().padStart(2, '0')
            val amPm = if (hour < 12) "AM" else "PM"
            val hour12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }

            return mapOf(
                "iso_datetime" to "${localDateTime.date}T${localDateTime.hour.toString().padStart(2, '0')}:$minute:${localDateTime.second.toString().padStart(2, '0')}",
                "display_datetime" to "$dayOfWeek, $month $day, $year at $hour12:$minute $amPm",
                "timezone" to timeZone.id,
                "day_of_week" to localDateTime.dayOfWeek.name,
            )
        }
    }

    val localTimeToolInfo = ToolInfo(
        id = "get_local_time",
        name = "Get Local Time",
        description = "Get the current local date and time for interpreting relative dates",
        nameRes = Res.string.tool_get_local_time_name,
        descriptionRes = Res.string.tool_get_local_time_description,
    )

    val memoryStoreToolInfo = ToolInfo(
        id = "memory_store",
        name = "Store Memory",
        description = "Store or update a memory with a descriptive key",
        nameRes = Res.string.tool_memory_store_name,
        descriptionRes = Res.string.tool_memory_store_description,
    )

    val memoryForgetToolInfo = ToolInfo(
        id = "memory_forget",
        name = "Forget Memory",
        description = "Delete a stored memory by its key",
        nameRes = Res.string.tool_memory_forget_name,
        descriptionRes = Res.string.tool_memory_forget_description,
    )

    val memoryLearnToolInfo = ToolInfo(
        id = "memory_learn",
        name = "Learn Memory",
        description = "Store a categorized learning, error resolution, or preference",
        nameRes = Res.string.tool_memory_learn_name,
        descriptionRes = Res.string.tool_memory_learn_description,
    )

    val memoryReinforceToolInfo = ToolInfo(
        id = "memory_reinforce",
        name = "Reinforce Memory",
        description = "Reinforce a memory that produced a good outcome",
        nameRes = Res.string.tool_memory_reinforce_name,
        descriptionRes = Res.string.tool_memory_reinforce_description,
    )

    val searchMemoriesToolInfo = ToolInfo(
        id = "search_memories",
        name = "Search Memories",
        description = "Search stored memories using semantic (vector), keyword (FTS5), or hybrid mode",
    )

    val openUrlTool = object : Tool {
        override val schema = ToolSchema(
            name = "open_url",
            description = "Open a URL in the user's browser or default app. This ONLY opens the link for the user to view — you will NOT receive the page content back. Do not use this to fetch or read information from URLs. Use this when the user asks to open or visit a link.",
            parameters = mapOf(
                "url" to ParameterSchema(type = "string", description = "The URL to open", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val url = args["url"]?.toString()
                ?: return mapOf("success" to false, "error" to "URL is required")
            return try {
                val opened = openUrl(url)
                if (opened) {
                    mapOf("success" to true, "url" to url, "message" to "URL opened successfully")
                } else {
                    mapOf("success" to false, "error" to "Failed to open URL")
                }
            } catch (e: Exception) {
                mapOf("success" to false, "error" to "Failed to open URL: ${e.message}")
            }
        }
    }

    val runAdbToolInfo = ToolInfo(
        id = "run_adb",
        name = "Run ADB",
        description = "Execute shell commands with ADB-level privileges via Shizuku",
        nameRes = Res.string.tool_run_adb_name,
        descriptionRes = Res.string.tool_run_adb_description,
    )

    val runOpenCodeToolInfo = ToolInfo(
        id = "run_opencode",
        name = "Run OpenCode",
        description = "Delegate complex coding tasks to opencode's autonomous agent inside the Linux sandbox",
        nameRes = Res.string.tool_run_opencode_name,
        descriptionRes = Res.string.tool_run_opencode_description,
    )

    val speakTextToolInfo = ToolInfo(
        id = "speak_text",
        name = "Speak Text",
        description = "Read text aloud using neural text-to-speech via the Linux sandbox (edge-tts). Generates high-quality speech audio and plays it on the device.",
        nameRes = Res.string.tool_speak_text_name,
        descriptionRes = Res.string.tool_speak_text_description,
    )

    val openUrlToolInfo = ToolInfo(
        id = "open_url",
        name = "Open URL",
        description = "Open a URL or link on the device",
        nameRes = Res.string.tool_open_url_name,
        descriptionRes = Res.string.tool_open_url_description,
    )

    val commonToolDefinitions = listOf(
        WebSearchTool.toolInfo,
        localTimeToolInfo,
        ipLocationToolInfo,
        openUrlToolInfo,
        FetchUrlTool.toolInfo,
        speakTextToolInfo,
        runOpenCodeToolInfo,
        runAdbToolInfo,
    ) +
        listOf(memoryStoreToolInfo, memoryForgetToolInfo, memoryLearnToolInfo, memoryReinforceToolInfo, searchMemoriesToolInfo) +
        SchedulingTools.schedulingToolDefinitions +
        HeartbeatTools.heartbeatToolDefinitions +
        EmailTools.emailToolDefinitions +
        SmsTools.smsToolDefinitions

    // Tool IDs gated by master toggles in Settings → Agent (isMemoryEnabled / isSchedulingEnabled /
    // isEmailEnabled / isSmsEnabled / isSmsSendEnabled). They stay in `commonToolDefinitions` so the
    // chat UI can resolve their display names, but the Tools tab filters them out — toggling them
    // individually would have no effect, since `getAvailableTools()` only consults the master toggle
    // (heartbeat tools are bundled with scheduling under the same switch).
    val masterToggleControlledToolIds: Set<String> = setOf(
        memoryStoreToolInfo.id,
        memoryForgetToolInfo.id,
        memoryLearnToolInfo.id,
        memoryReinforceToolInfo.id,
        searchMemoriesToolInfo.id,
    ) + SchedulingTools.schedulingToolDefinitions.map { it.id }.toSet() +
        HeartbeatTools.heartbeatToolDefinitions.map { it.id }.toSet() +
        EmailTools.emailToolDefinitions.map { it.id }.toSet() +
        SmsTools.smsToolDefinitions.map { it.id }.toSet()

    fun getCommonTools(appSettings: AppSettings): List<Tool> = buildList {
        if (appSettings.isToolEnabled(localTimeTool.schema.name)) {
            add(localTimeTool)
        }
        if (appSettings.isToolEnabled(ipLocationTool.schema.name)) {
            add(ipLocationTool)
        }
        if (appSettings.isToolEnabled(WebSearchTool.schema.name)) {
            add(WebSearchTool)
        }
        if (appSettings.isToolEnabled(openUrlTool.schema.name)) {
            add(openUrlTool)
        }
        if (appSettings.isToolEnabled(FetchUrlTool.schema.name)) {
            add(FetchUrlTool)
        }
    }

    // Memory tools - always enabled, core agent functionality

    fun memoryStoreTool(memoryStore: MemoryStore) = object : Tool {
        override val schema = ToolSchema(
            name = "memory_store",
            description = "Store a memory. The key is searchable via search_memories — use a descriptive key so you can find it later.",
            parameters = mapOf(
                "key" to ParameterSchema(type = "string", description = "Descriptive key for the memory (e.g. user_name, preferred_language, project_details)", required = true),
                "content" to ParameterSchema(type = "string", description = "The content to store", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val key = args["key"]?.toString() ?: return mapOf("success" to false, "error" to "Missing key")
            val content = args["content"]?.toString() ?: return mapOf("success" to false, "error" to "Missing content")
            val entry = memoryStore.store(key, content)
            return mapOf("success" to true, "key" to entry.key, "content" to entry.content)
        }
    }

    fun memoryForgetTool(memoryStore: MemoryStore) = object : Tool {
        override val schema = ToolSchema(
            name = "memory_forget",
            description = "Delete a stored memory by its exact key.",
            parameters = mapOf(
                "key" to ParameterSchema(type = "string", description = "The exact key of the memory to delete", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val key = args["key"]?.toString() ?: return mapOf("success" to false, "error" to "Missing key")
            val removed = memoryStore.forget(key)
            return mapOf("success" to removed, "key" to key)
        }
    }

    fun memoryLearnTool(memoryStore: MemoryStore) = object : Tool {
        override val schema = ToolSchema(
            name = "memory_learn",
            description = "Store a structured learning with a category. Use LEARNING for things that worked, ERROR for error resolutions, PREFERENCE for user corrections/preferences.",
            parameters = mapOf(
                "key" to ParameterSchema(type = "string", description = "Descriptive key for the learning", required = true),
                "content" to ParameterSchema(type = "string", description = "What was learned", required = true),
                "category" to ParameterSchema(type = "string", description = "Category: LEARNING, ERROR, or PREFERENCE", required = true),
                "source" to ParameterSchema(type = "string", description = "How this was learned: user_correction, observation, or error_resolution", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val key = args["key"]?.toString() ?: return mapOf("success" to false, "error" to "Missing key")
            val content = args["content"]?.toString() ?: return mapOf("success" to false, "error" to "Missing content")
            val categoryStr = args["category"]?.toString()?.uppercase() ?: return mapOf("success" to false, "error" to "Missing category")
            val source = args["source"]?.toString()

            val category = try {
                MemoryCategory.valueOf(categoryStr)
            } catch (_: Exception) {
                return mapOf("success" to false, "error" to "Invalid category: $categoryStr. Use LEARNING, ERROR, or PREFERENCE")
            }

            if (category == MemoryCategory.GENERAL) {
                return mapOf("success" to false, "error" to "Use memory_store for GENERAL memories. memory_learn is for LEARNING, ERROR, or PREFERENCE")
            }

            val entry = memoryStore.store(key, content, category, source)
            return mapOf("success" to true, "key" to entry.key, "category" to entry.category.name, "content" to entry.content)
        }
    }

    fun memoryReinforceTool(memoryStore: MemoryStore) = object : Tool {
        override val schema = ToolSchema(
            name = "memory_reinforce",
            description = "Reinforce a stored memory by incrementing its hit count. Use this when a stored learning or preference produced a good outcome.",
            parameters = mapOf(
                "key" to ParameterSchema(type = "string", description = "The exact key of the memory to reinforce", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val key = args["key"]?.toString() ?: return mapOf("success" to false, "error" to "Missing key")
            val entry = memoryStore.reinforceMemory(key)
                ?: return mapOf("success" to false, "error" to "Memory not found: $key")
            return mapOf("success" to true, "key" to entry.key, "hit_count" to entry.hitCount)
        }
    }

    fun searchMemoriesTool(
        memoryStore: MemoryStore,
    ) = object : Tool {
        override val schema = ToolSchema(
            name = "search_memories",
            description = "Search your stored memories using semantic (vector) matching, keyword (FTS5) matching, or hybrid (both). Semantic search finds conceptually related memories even without exact keyword overlap. Default is hybrid.",
            parameters = mapOf(
                "query" to ParameterSchema(type = "string", description = "The search query to find matching memories", required = true),
                "limit" to ParameterSchema(type = "integer", description = "Maximum number of results (default 10)", required = false),
                "mode" to ParameterSchema(type = "string", description = "Search mode: 'hybrid' (semantic+keyword, default), 'vector' (pure semantic), or 'keyword' (exact match)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val query = args["query"]?.toString() ?: return mapOf("success" to false, "error" to "Missing query")
            val limit = (args["limit"] as? Number)?.toInt() ?: 10
            val mode = args["mode"]?.toString() ?: "hybrid"

            val results = memoryStore.searchMemories(query, limit, mode)
            return mapOf(
                "success" to true,
                "query" to query,
                "mode" to mode,
                "count" to results.size,
                "results" to results.map { entry ->
                    mapOf(
                        "key" to entry.key,
                        "content" to entry.content,
                        "category" to entry.category.name,
                        "hit_count" to entry.hitCount,
                        "source" to (entry.source ?: ""),
                    )
                },
            )
        }
    }

    fun getMemoryTools(
        memoryStore: MemoryStore,
    ): List<Tool> = listOf(
        memoryStoreTool(memoryStore),
        memoryForgetTool(memoryStore),
        memoryLearnTool(memoryStore),
        memoryReinforceTool(memoryStore),
        searchMemoriesTool(memoryStore),
    )

    // Knowledge graph tools

    val kgAddToolInfo = ToolInfo(
        id = "kg_add",
        name = "Add KG Fact",
        description = "Add a fact to the knowledge graph: subject -> predicate -> object. Use for relationships between entities.",
    )

    val kgQueryToolInfo = ToolInfo(
        id = "kg_query",
        name = "Query KG",
        description = "Query the knowledge graph for facts about an entity or relation.",
    )

    val kgInvalidateToolInfo = ToolInfo(
        id = "kg_invalidate",
        name = "Invalidate KG Fact",
        description = "Mark a knowledge graph fact as no longer true.",
    )

    fun kgAddTool(memoryStore: MemoryStore) = object : Tool {
        override val schema = ToolSchema(
            name = "kg_add",
            description = "Add a fact to the knowledge graph: subject -> predicate -> object. Use for relationships between entities (e.g. 'Alice' -> 'works_at' -> 'Acme Corp').",
            parameters = mapOf(
                "subject" to ParameterSchema(type = "string", description = "The subject entity", required = true),
                "predicate" to ParameterSchema(type = "string", description = "The relationship type (e.g. works_at, loves, parent_of)", required = true),
                "object" to ParameterSchema(type = "string", description = "The object entity", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val subject = args["subject"]?.toString() ?: return mapOf("success" to false, "error" to "Missing subject")
            val predicate = args["predicate"]?.toString() ?: return mapOf("success" to false, "error" to "Missing predicate")
            val `object` = args["object"]?.toString() ?: return mapOf("success" to false, "error" to "Missing object")
            val fact = memoryStore.addFact(subject, predicate, `object`)
            return mapOf("success" to true, "id" to fact.id, "subject" to fact.subject, "predicate" to fact.predicate, "object" to fact.`object`)
        }
    }

    fun kgQueryTool(memoryStore: MemoryStore) = object : Tool {
        override val schema = ToolSchema(
            name = "kg_query",
            description = "Query the knowledge graph for facts about an entity or relation. Returns matching facts with subject, predicate, and object.",
            parameters = mapOf(
                "entity" to ParameterSchema(type = "string", description = "Entity to find facts about (matches both subject and object)", required = false),
                "relation" to ParameterSchema(type = "string", description = "Filter by relationship type (predicate)", required = false),
                "limit" to ParameterSchema(type = "integer", description = "Maximum results (default 20)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val entity = args["entity"]?.toString()
            val relation = args["relation"]?.toString()
            val limit = (args["limit"] as? Number)?.toInt() ?: 20
            val results = memoryStore.queryFacts(entity, relation, limit)
            return mapOf(
                "success" to true,
                "count" to results.size,
                "facts" to results.map { fact ->
                    mapOf("id" to fact.id, "subject" to fact.subject, "predicate" to fact.predicate, "object" to fact.`object`, "created_at" to fact.createdAt)
                },
            )
        }
    }

    fun kgInvalidateTool(memoryStore: MemoryStore) = object : Tool {
        override val schema = ToolSchema(
            name = "kg_invalidate",
            description = "Mark a knowledge graph fact as no longer true. Provide the exact subject, predicate, and object of the fact to invalidate.",
            parameters = mapOf(
                "subject" to ParameterSchema(type = "string", description = "Subject of the fact to invalidate", required = true),
                "predicate" to ParameterSchema(type = "string", description = "Predicate of the fact to invalidate", required = true),
                "object" to ParameterSchema(type = "string", description = "Object of the fact to invalidate", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val subject = args["subject"]?.toString() ?: return mapOf("success" to false, "error" to "Missing subject")
            val predicate = args["predicate"]?.toString() ?: return mapOf("success" to false, "error" to "Missing predicate")
            val `object` = args["object"]?.toString() ?: return mapOf("success" to false, "error" to "Missing object")
            memoryStore.invalidateFact(subject, predicate, `object`)
            return mapOf("success" to true, "subject" to subject, "predicate" to predicate, "object" to `object`, "message" to "Fact invalidated")
        }
    }

    fun getKgTools(memoryStore: MemoryStore): List<Tool> = listOf(
        kgAddTool(memoryStore),
        kgQueryTool(memoryStore),
        kgInvalidateTool(memoryStore),
    )

    // Diary tools

    val diaryWriteToolInfo = ToolInfo(
        id = "diary_write",
        name = "Write Diary",
        description = "Write an entry to the agent's personal diary. Use for introspection, observations, and session summaries.",
    )

    val diaryReadToolInfo = ToolInfo(
        id = "diary_read",
        name = "Read Diary",
        description = "Read recent entries from the agent's personal diary.",
    )

    fun diaryWriteTool(memoryStore: MemoryStore) = object : Tool {
        override val schema = ToolSchema(
            name = "diary_write",
            description = "Write an entry to your personal diary. Use for introspection, observations, session summaries, and reflections.",
            parameters = mapOf(
                "content" to ParameterSchema(type = "string", description = "The diary entry content", required = true),
                "topic" to ParameterSchema(type = "string", description = "Optional topic tag (default: general)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val content = args["content"]?.toString() ?: return mapOf("success" to false, "error" to "Missing content")
            val topic = args["topic"]?.toString() ?: "general"
            memoryStore.diaryWrite("kai", content, topic)
            return mapOf("success" to true, "topic" to topic, "message" to "Diary entry written")
        }
    }

    fun diaryReadTool(memoryStore: MemoryStore) = object : Tool {
        override val schema = ToolSchema(
            name = "diary_read",
            description = "Read recent entries from your personal diary. Returns entries sorted by recency.",
            parameters = mapOf(
                "last_n" to ParameterSchema(type = "integer", description = "Number of recent entries to read (default 10)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val lastN = (args["last_n"] as? Number)?.toInt() ?: 10
            val entries = memoryStore.diaryRead("kai", lastN)
            return mapOf(
                "success" to true,
                "count" to entries.size,
                "entries" to entries.map { entry ->
                    mapOf("id" to entry.id, "topic" to entry.topic, "content" to entry.content, "created_at" to entry.createdAt)
                },
            )
        }
    }

    fun getDiaryTools(memoryStore: MemoryStore): List<Tool> = listOf(
        diaryWriteTool(memoryStore),
        diaryReadTool(memoryStore),
    )

    // ── Persona Management Tools ──

    val savePersonaToolSchema = ToolSchema(
        name = "save_persona",
        description = "Save a character persona. First generate BOTH a condensed version AND a full synthesized profile, then call this tool with both. The condensed version is used by default; the full version is stored so the user can switch anytime.",
        parameters = mapOf(
            "name" to ParameterSchema(
                type = "string",
                description = "Name for the persona",
                required = true,
            ),
            "character_text" to ParameterSchema(
                type = "string",
                description = "CONDENSED version — must use this exact format:\n\n---\n\n**Identity**: You are [sentence].\n\n**Key Characteristics**:\n*   **[Trait]**: [description]\n\n**Communication Style**:\n[paragraphs]\n\n**Essential Knowledge**:\n[paragraph]\n\n**Specific Behaviors & Phrases**:\n*   [behavior]\n\n**General Response Guidelines**:\n*   **[guideline]**: [description]",
                required = true,
            ),
            "full_text" to ParameterSchema(
                type = "string",
                description = "FULL synthesized profile — include ALL sections: ### 0. Core Essence, ### 1. Biographical Foundation and Personality, ### 2. Voice/Communication Analysis, ### 3. Signature Language Patterns, ### 4. Narrative/Communication Structure, ### 5. Subject Matter Expertise, ### 6. Philosophical Framework, ### 7. Emotional Range and Expression, ### 8. Distinctive Patterns and Quirks, ### 9. Evolution Over Time, ### 10. Practical Application Guidelines, ### 10.5. Platform Adaptation Bank. Written in second person. 3,500-4,500 words.",
                required = false,
            ),
            "description" to ParameterSchema(
                type = "string",
                description = "Short description of the persona (one sentence).",
                required = false,
            ),
        ),
    )

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun savePersonaTool(
        appSettings: AppSettings,
        personaManager: PersonaManager,
    ): Tool = object : Tool {
        override val schema = savePersonaToolSchema
        override val timeout: Duration = 30.seconds

        override suspend fun execute(args: Map<String, Any>): Any {
            val name = (args["name"] as? String)?.trim()
                ?: return mapOf("success" to false, "error" to "name is required")
            val characterText = (args["character_text"] as? String)?.trim()
                ?: return mapOf("success" to false, "error" to "character_text is required")
            val fullText = (args["full_text"] as? String)?.trim()
            val description = (args["description"] as? String)?.trim() ?: ""

            val id = "persona_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${kotlin.uuid.Uuid.random().toString().take(6)}"

            if (!fullText.isNullOrBlank()) {
                appSettings.settings.putString("persona_full_$id", fullText)
            }

            val config = PersonaConfig(
                id = id,
                name = name.take(50),
                description = description.take(200),
                behaviorStyle = BehaviorStyle.CUSTOM,
                languageStyle = LanguageStyle.NONE,
                characterType = CharacterType.NONE,
                defaultSoul = characterText,
                renderMode = RenderMode.CHARACTER,
                isBuiltIn = false,
            )
            personaManager.savePersona(config)

            val versions = if (!fullText.isNullOrBlank()) " (condensed + full)" else " (condensed)"
            return mapOf(
                "success" to true,
                "persona_id" to id,
                "persona_name" to name,
                "has_full_version" to !fullText.isNullOrBlank(),
                "message" to "Persona '$name' saved$versions. Call switch_persona with id '$id' to activate it.",
            )
        }
    }

    val switchPersonaToolSchema = ToolSchema(
        name = "switch_persona",
        description = "Switch the active persona to an existing one. Use list_personas first to find available personas.",
        parameters = mapOf(
            "persona_id" to ParameterSchema(
                type = "string",
                description = "The ID of the persona to switch to",
                required = true,
            ),
        ),
    )

    fun switchPersonaTool(personaManager: PersonaManager): Tool = object : Tool {
        override val schema = switchPersonaToolSchema
        override val timeout: Duration = 10.seconds

        override suspend fun execute(args: Map<String, Any>): Any {
            val personaId = (args["persona_id"] as? String)?.trim()
                ?: return mapOf("success" to false, "error" to "persona_id is required")
            val persona = personaManager.getPersona(personaId)
            if (persona == null) return mapOf("success" to false, "error" to "Persona not found: $personaId")
            personaManager.setActivePersonaId(personaId)
            return mapOf("success" to true, "persona_name" to persona.name, "message" to "Switched to '${persona.name}'.")
        }
    }

    val listPersonasToolSchema = ToolSchema(
        name = "list_personas",
        description = "List all available personas (both built-in and custom). Returns their IDs and names. Use the ID to switch with switch_persona.",
        parameters = mapOf(),
    )

    fun listPersonasTool(personaManager: PersonaManager): Tool = object : Tool {
        override val schema = listPersonasToolSchema
        override val timeout: Duration = 10.seconds

        override suspend fun execute(args: Map<String, Any>): Any {
            val all = personaManager.getAllPersonas()
            val activeId = personaManager.getActivePersonaId()
            return mapOf(
                "success" to true,
                "active_persona_id" to activeId,
                "personas" to all.map { mapOf("id" to it.id, "name" to it.name, "is_active" to (it.id == activeId)) },
            )
        }
    }

    val deletePersonaToolSchema = ToolSchema(
        name = "delete_persona",
        description = "Delete a custom persona by its ID. Cannot delete built-in personas. Use list_personas first to find persona IDs.",
        parameters = mapOf(
            "persona_id" to ParameterSchema(
                type = "string",
                description = "The ID of the persona to delete",
                required = true,
            ),
        ),
    )

    fun deletePersonaTool(personaManager: PersonaManager): Tool = object : Tool {
        override val schema = deletePersonaToolSchema
        override val timeout: Duration = 10.seconds

        override suspend fun execute(args: Map<String, Any>): Any {
            val personaId = (args["persona_id"] as? String)?.trim()
                ?: return mapOf("success" to false, "error" to "persona_id is required")
            val persona = personaManager.getPersona(personaId)
            if (persona == null) return mapOf("success" to false, "error" to "Persona not found: $personaId")
            if (persona.isBuiltIn) return mapOf("success" to false, "error" to "Cannot delete built-in persona")
            personaManager.deletePersona(personaId)
            return mapOf("success" to true, "message" to "Persona '${persona.name}' deleted.")
        }
    }
}
