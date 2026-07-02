package com.kai.custom.data

import kotlin.time.Instant

enum class RenderMode {
    UPSTREAM_COMPAT,
    FORK_ENHANCED,
    CHARACTER,
}

internal interface PersonaSection {
    fun build(context: PromptContext): String?
}

internal interface TechnicalSection {
    val id: String
    fun shouldInclude(context: PromptContext): Boolean
    fun build(context: PromptContext): String?
}

internal interface TaskAdapter {
    val id: String
    fun shouldActivate(domains: Set<TaskDomain>): Boolean
    fun build(context: PromptContext): String?
}

// ─── Deprecated constants (kept for backward compat / upstream compat) ──

internal const val DEFAULT_HONESTY_RULE =
    "## Honesty\n" +
        "Don't claim actions or capabilities you haven't performed or don't have. " +
        "Be direct about what you can and cannot do. " +
        "Say \"I don't know\" when you don't. " +
        "You can update the user's soul text when they ask you to remember something about themselves."
internal const val DEFAULT_TOOL_USE_SECTION =
    "## Tool Use\n" +
        "Use tools to verify work and resolve ambiguity. " +
        "Don't ask the user for lookups you can do yourself. " +
        "Check for a tool before saying a capability is unavailable. " +
        "Summarize noisy output and state any uncertainty — don't dump raw logs."
internal const val DEFAULT_ACTING_SECTION =
    "## When to Act\n" +
        "Take the most reasonable interpretation and proceed. " +
        "Ask at most one clarifying question, only when genuinely blocked. " +
        "If a first attempt fails, try another approach or explain the blocker. " +
        "See work through to a usable result."
internal const val DEFAULT_STRUCTURED_LEARNING_SECTION =
    "## Structured Learning\n" +
        "Use memory_learn to record categorized learnings:\n" +
        "- Record user corrections and preferences as PREFERENCE entries\n" +
        "- Record things that worked well as LEARNING entries\n" +
        "- Record error resolutions as ERROR entries\n" +
        "Use memory_reinforce when a stored learning produced a good outcome."
internal const val DEFAULT_AUTOMATION_SECTION =
    "## Automation\n" +
        "Every form of \"run something without the user typing it\" goes through `schedule_task`. " +
        "The tool has three mutually exclusive triggers:\n" +
        "- `execute_at` — one-off at a specific datetime (reminders, \"check back at 3pm\").\n" +
        "- `cron` — recurring on a schedule (\"every morning at 8\", \"every 15 minutes\").\n" +
        "- `on_heartbeat: true` — appended to every heartbeat self-check. Use this when the user asks for *standing* heartbeat behaviour (e.g. \"greet me on every heartbeat\", \"always summarize new emails\", \"flag overdue tasks each check\"). These are `HEARTBEAT` trigger tasks and show up in `list_tasks` alongside time/cron tasks.\n" +
        "Each scheduled or heartbeat run starts fresh, so embed any context the prompt needs. Use `list_tasks` / `cancel_task` to inspect or remove.\n" +
        "Heartbeat itself (on/off toggle, interval, active hours) is user-controlled in Settings \u2192 Agent \u2192 Heartbeat \u2014 you cannot enable, disable, or reschedule it. If the user asks for recurring updates and heartbeat seems off, either schedule a cron task or tell them to enable Heartbeat in settings \u2014 never claim to have \"enabled\" or \"turned on\" heartbeat."

// ─── Persona Sections ────────────────────────────────────────────

internal class LocalStyleSection : PersonaSection {
    override fun build(context: PromptContext): String? {
        if (context.variant != SystemPromptVariant.CHAT_LOCAL) return null
        if (context.behaviorStyle != BehaviorStyle.CUSTOM) return null
        val instruction = context.localStyleInstruction
        if (instruction.isBlank()) return null
        return "## Style\n$instruction"
    }
}

internal class SoulIdentitySection : PersonaSection {
    override fun build(context: PromptContext): String = context.soul
}

internal class HonestyRuleSection : PersonaSection {
    override fun build(context: PromptContext): String? = DEFAULT_HONESTY_RULE
}

internal class LanguageSection : PersonaSection {
    override fun build(context: PromptContext): String? {
        if (context.renderMode == RenderMode.UPSTREAM_COMPAT) return null
        return "## Language\nAdapt to the user's language. Speak the language they write in."
    }
}

// ─── Technical Sections ──────────────────────────────────────────

internal class ToolUseSection : TechnicalSection {
    override val id = "tool_use"
    override fun shouldInclude(context: PromptContext): Boolean = context.hasTools

    override fun build(context: PromptContext): String = if (context.renderMode == RenderMode.UPSTREAM_COMPAT) {
        DEFAULT_TOOL_USE_SECTION + "\n\n" + DEFAULT_ACTING_SECTION
    } else {
        "## Tool Use\nUse tools to verify work and resolve ambiguity. Don't ask the user for lookups you can do yourself. Check available tools in the tools array before saying a capability is unavailable. Summarize noisy output instead of dumping raw logs."
    }
}

internal class MemorySystemSection : TechnicalSection {
    override val id = "memory_system"
    override fun shouldInclude(context: PromptContext): Boolean = context.memoryEnabled && context.variant == SystemPromptVariant.CHAT_REMOTE

    override fun build(context: PromptContext): String = if (context.renderMode == RenderMode.UPSTREAM_COMPAT) {
        DEFAULT_STRUCTURED_LEARNING_SECTION
    } else {
        "## Memory System\n" +
            "Use memory_store to record user preferences, corrections, project facts, decisions, fixes that worked, and error resolutions.\n" +
            "Use memory_learn to record categorized learnings (PREFERENCE, LEARNING, ERROR).\n" +
            "Use memory_reinforce when a stored learning produces a good outcome.\n" +
            "Search memory with memory_search before re-solving recurring problems or asking the user to repeat known facts.\n" +
            "Do not store transient chatter, guesses, secrets, or one-off noise.\n" +
            "If memory conflicts with current evidence or user correction, trust the current evidence/user and update memory."
    }
}

internal class MemorySearchGuidanceSection : TechnicalSection {
    override val id = "memory_search_guidance"
    override fun shouldInclude(context: PromptContext): Boolean = context.memoryEnabled && context.renderMode != RenderMode.UPSTREAM_COMPAT

    override fun build(context: PromptContext): String = "When you don't know something or need information, first search your memory with search_memories (supports vector/semantic and keyword matching). If not found, search the internet with web_search. Save what you learn with memory_store."
}

internal class AutomationSection : TechnicalSection {
    override val id = "automation"
    override fun shouldInclude(context: PromptContext): Boolean = context.schedulingEnabled && context.variant == SystemPromptVariant.CHAT_REMOTE

    override fun build(context: PromptContext): String = DEFAULT_AUTOMATION_SECTION
}

internal class EmailPolicySection : TechnicalSection {
    override val id = "email_policy"
    override fun shouldInclude(context: PromptContext): Boolean = context.emailAccounts.isNotEmpty() && context.variant == SystemPromptVariant.CHAT_REMOTE

    override fun build(context: PromptContext): String = "## Email Policy\n" +
        "Before calling compose_email or reply_email, present the full draft (to, subject, body) in chat and get explicit confirmation.\n" +
        "Never call send tools on the same turn you draft \u2014 the user must have a chance to correct."
}

internal class IntegrationStatusSection : TechnicalSection {
    override val id = "integration_status"
    override fun shouldInclude(context: PromptContext): Boolean = context.variant == SystemPromptVariant.CHAT_REMOTE &&
        (context.emailAccounts.isNotEmpty() || context.pendingTasks.isNotEmpty() || context.heartbeatAdditions.isNotEmpty())

    override fun build(context: PromptContext): String {
        val sb = StringBuilder()
        if (context.emailAccounts.isNotEmpty()) {
            sb.append("\n## Email Accounts\n")
            for (account in context.emailAccounts) {
                sb.append("- **").append(account.email).append("**: ")
                if (account.lastError != null) {
                    sb.append("sync failing \u2014 ").append(account.lastError)
                } else {
                    sb.append(account.unreadCount).append(" unread")
                    if (account.lastSyncEpochMs > 0) {
                        sb.append(" (last sync: ").append(Instant.fromEpochMilliseconds(account.lastSyncEpochMs)).append(')')
                    }
                }
                sb.append('\n')
            }
        }
        if (context.pendingTasks.isNotEmpty()) {
            sb.append("\n## Scheduled Tasks\n")
            for (t in context.pendingTasks) {
                sb.append("- **").append(t.description).append("** (id: ").append(t.id).append(", scheduled: ").append(t.scheduledAt).append(")")
                if (t.cron != null) sb.append(" [cron: ").append(t.cron).append("]")
                sb.append('\n')
            }
        }
        if (context.heartbeatAdditions.isNotEmpty()) {
            sb.append("\n## Heartbeat Additions\n")
            sb.append("Standing instructions the user has set to run on every heartbeat (trigger=HEARTBEAT). Don't duplicate these when the user asks for similar behaviour; cancel via `cancel_task` if they want one removed.\n")
            for (t in context.heartbeatAdditions) {
                sb.append("- **").append(t.description).append("** (id: ").append(t.id).append("): ").append(t.prompt).append('\n')
            }
        }
        return sb.toString().trimStart('\n')
    }
}

internal class ContextSection : TechnicalSection {
    override val id = "context"
    override fun shouldInclude(context: PromptContext): Boolean = true

    override fun build(context: PromptContext): String {
        val r = context.runtime
        return "## Context\n" +
            "- Local time: ${r.nowLocalIsoWithOffset} (${r.timeZoneId})\n" +
            "- UTC: ${r.nowUtcIsoString}\n" +
            "- Platform: ${r.platform}\n" +
            "- Model: ${r.modelId}\n" +
            "- Provider: ${r.providerName}\n"
    }
}

internal class DynamicUiSection : TechnicalSection {
    override val id = "dynamic_ui"
    override fun shouldInclude(context: PromptContext): Boolean = context.variant == SystemPromptVariant.CHAT_REMOTE && context.uiMode != ChatPromptUiMode.NONE

    override fun build(context: PromptContext): String = when (context.uiMode) {
        ChatPromptUiMode.DYNAMIC_UI -> {
            val sb = StringBuilder()
            sb.append("## Dynamic UI\n")
            sb.append("You can enhance your chat responses with interactive UI elements using kai-ui blocks. Proactively use them whenever you need input from the user.\n\n")
            sb.append(KAI_UI_COMPONENT_CATALOG)
            sb.append("Layout tips:\n")
            sb.append("- Put buttons INSIDE cards, directly below related content\n")
            sb.append("- Use rows for groups of buttons or chips \u2014 rows wrap automatically\n")
            sb.append("- Keep button labels short (1-3 words)\n\n")
            sb.append("Example:\n```kai-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Your name?\",\"style\":\"title\"},{\"type\":\"text_input\",\"id\":\"name\",\"placeholder\":\"Enter name\"},{\"type\":\"button\",\"label\":\"Submit\",\"action\":{\"type\":\"callback\",\"event\":\"submit\",\"collectFrom\":[\"name\"]}}]}\n```\n")
            sb.toString()
        }

        ChatPromptUiMode.INTERACTIVE_UI -> {
            val sb = StringBuilder()
            sb.append("## Interactive UI Mode (ACTIVE)\n")
            sb.append("The user ONLY sees rendered kai-ui components. Your ENTIRE response must be a single ```kai-ui code fence.\n\n")
            sb.append(KAI_UI_COMPONENT_CATALOG)
            sb.append("Rules:\n")
            sb.append("- Each response is a COMPLETE screen layout.\n")
            sb.append("- Every screen MUST have at least one interactive element with a callback action.\n")
            sb.append("- Use callbacks for collecting choices, submitting forms, navigating between steps.\n")
            sb.append("- Do NOT include back buttons or navigation controls.\n")
            sb.append("- Never show loading/fetching states \u2014 present all content immediately.\n")
            sb.append("- Each screen is independent. No client-side state persistence.\n\n")
            sb.append("Example:\n```kai-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Welcome\",\"style\":\"headline\"},{\"type\":\"card\",\"children\":[{\"type\":\"text\",\"value\":\"What would you like to do?\",\"style\":\"title\"},{\"type\":\"button\",\"label\":\"Get Started\",\"action\":{\"type\":\"callback\",\"event\":\"get_started\"}}]}]}\n```\n")
            sb.toString()
        }

        ChatPromptUiMode.NONE -> ""
    }
}

// ─── Task Adapters ───────────────────────────────────────────────

internal class RelevantMemoryDumpAdapter : TaskAdapter {
    override val id = "memory_dump"

    override fun shouldActivate(domains: Set<TaskDomain>): Boolean = domains.any { it != TaskDomain.GENERAL_CHAT } || TaskDomain.MEMORY_QUERY in domains

    override fun build(context: PromptContext): String? {
        if (!context.memoryEnabled) return null
        val hasRelevant = context.relevantMemories.isNotEmpty()
        val hasCategorized = context.generalMemories.isNotEmpty() ||
            context.preferenceMemories.isNotEmpty() ||
            context.learningMemories.isNotEmpty() ||
            context.errorMemories.isNotEmpty()
        if (!hasRelevant && !hasCategorized) return null

        val budget = when {
            context.variant == SystemPromptVariant.CHAT_LOCAL -> 1024
            context.renderMode == RenderMode.UPSTREAM_COMPAT -> Int.MAX_VALUE
            else -> 1024
        }

        val sb = StringBuilder()
        sb.append("## What I Know About You\n")

        if (context.relevantMemories.isNotEmpty()) {
            for (entry in context.relevantMemories) {
                if (entry.protected) continue
                sb.append("- **").append(entry.key).append("**")
                if (entry.hitCount > 1) sb.append(" (reinforced ").append(entry.hitCount).append("x)")
                sb.append(": ").append(entry.content).append('\n')
            }
        }

        var remaining = budget - sb.length
        remaining = appendCategory(sb, "Your Memories", context.generalMemories, withHitCount = false, remaining)
        remaining = appendCategory(sb, "User Preferences", context.preferenceMemories, withHitCount = false, remaining)
        remaining = appendCategory(sb, "Learnings", context.learningMemories, withHitCount = true, remaining)
        appendCategory(sb, "Known Issues & Resolutions", context.errorMemories, withHitCount = false, remaining)

        return sb.toString()
    }

    private fun appendCategory(sb: StringBuilder, header: String, entries: List<MemoryEntry>, withHitCount: Boolean, remainingBudget: Int): Int {
        if (entries.isEmpty() || remainingBudget <= 0) return remainingBudget
        val section = StringBuilder()
        section.append("\n\n## ").append(header).append("\n")
        var included = 0
        for (entry in entries) {
            if (entry.protected) continue
            val entryStart = section.length
            section.append("- **").append(entry.key).append("**")
            if (withHitCount) section.append(" (reinforced ").append(entry.hitCount).append("x)")
            section.append(": ").append(entry.content).append('\n')
            if (section.length > remainingBudget) {
                section.setLength(entryStart)
                break
            }
            included++
        }
        if (included == 0) return remainingBudget
        sb.append(section)
        return (remainingBudget - section.length).coerceAtLeast(0)
    }
}

internal class ActiveFileContextAdapter : TaskAdapter {
    override val id = "file_context"

    override fun shouldActivate(domains: Set<TaskDomain>): Boolean = TaskDomain.FILE_OPERATION in domains

    override fun build(context: PromptContext): String? {
        if (context.attachedFiles.isEmpty()) return null
        return "## Attached Files\n" + context.attachedFiles.joinToString("\n") { "- $it" }
    }
}

// ─── Builder ─────────────────────────────────────────────────────

internal class UnifiedPromptBuilder {
    private val personaSections: MutableList<PersonaSection> = mutableListOf(
        LocalStyleSection(),
        SoulIdentitySection(),
        HonestyRuleSection(),
        LanguageSection(),
    )

    private val technicalSections: MutableList<TechnicalSection> = mutableListOf(
        ToolUseSection(),
        MemorySystemSection(),
        MemorySearchGuidanceSection(),
        AutomationSection(),
        EmailPolicySection(),
        IntegrationStatusSection(),
        DynamicUiSection(),
        ContextSection(),
    )

    private val taskAdapters: MutableList<TaskAdapter> = mutableListOf(
        RelevantMemoryDumpAdapter(),
        ActiveFileContextAdapter(),
    )

    fun build(context: PromptContext): String {
        if (context.renderMode == RenderMode.CHARACTER) {
            // CHARACTER mode: pure persona definition, no assistant framing
            val soul = personaSections.mapNotNull { it.build(context) }.joinToString("\n\n")
            val customSoul = buildCustomSoul(context)
            val task = taskAdapters
                .filter { it.shouldActivate(context.taskDomains) }
                .mapNotNull { it.build(context) }
                .joinToString("\n\n")
            return listOfNotNull(soul, customSoul, task).joinToString("\n\n")
        }
        val persona = personaSections.mapNotNull { it.build(context) }.joinToString("\n\n")
        val technical = technicalSections
            .filter { it.shouldInclude(context) }
            .mapNotNull { it.build(context) }
            .joinToString("\n\n")
        val customSoul = buildCustomSoul(context)
        val task = if (context.renderMode == RenderMode.UPSTREAM_COMPAT) {
            taskAdapters.mapNotNull {
                it.build(context.copy(taskDomains = setOf(TaskDomain.MEMORY_QUERY)))
            }.joinToString("\n\n")
        } else {
            taskAdapters
                .filter { it.shouldActivate(context.taskDomains) }
                .mapNotNull { it.build(context) }
                .joinToString("\n\n")
        }
        return listOfNotNull(persona, technical, customSoul, task).joinToString("\n\n")
    }

    private fun buildCustomSoul(context: PromptContext): String? {
        val user = context.soulUserText
        val auto = context.soulAutoText
        val parts = mutableListOf<String>()
        if (user.isNotBlank()) parts.add(user)
        if (auto.isNotBlank()) parts.add("## Behavior Notes\n$auto")
        return parts.joinToString("\n\n").ifEmpty { null }
    }
}

internal val KAI_UI_COMPONENT_CATALOG: String = buildString {
    append("Format: wrap a JSON object in ```kai-ui fences.\n\n")
    append("Components: column, row, card, box, text, button, text_input, checkbox, switch, select, radio_group, slider, chip_group, table, list, divider, image, icon, code, progress, countdown, alert, tabs, accordion, quote, badge, stat, avatar.\n")
    append("- text: {\"type\":\"text\",\"value\":\"...\",\"style\":\"headline|title|body|caption\",\"bold\":true,\"color\":\"primary|secondary|error\"}\n")
    append("- button: {\"type\":\"button\",\"label\":\"...\",\"action\":{...},\"variant\":\"filled|outlined|text|tonal\"}\n")
    append("- text_input: {\"type\":\"text_input\",\"id\":\"...\",\"label\":\"...\",\"placeholder\":\"...\",\"value\":\"...\"}\n")
    append("- checkbox: {\"type\":\"checkbox\",\"id\":\"...\",\"label\":\"...\",\"checked\":false}\n")
    append("- switch: {\"type\":\"switch\",\"id\":\"...\",\"label\":\"...\",\"checked\":false}\n")
    append("- select: {\"type\":\"select\",\"id\":\"...\",\"label\":\"...\",\"options\":[\"A\",\"B\"],\"selected\":\"A\"}\n")
    append("- radio_group: {\"type\":\"radio_group\",\"id\":\"...\",\"label\":\"...\",\"options\":[\"A\",\"B\"],\"selected\":\"A\"}\n")
    append("- slider: {\"type\":\"slider\",\"id\":\"...\",\"value\":50,\"min\":0,\"max\":100}\n")
    append("- chip_group: {\"type\":\"chip_group\",\"id\":\"...\",\"chips\":[{\"label\":\"Tag\",\"value\":\"tag\"}],\"selection\":\"single|multi|none\"}\n")
    append("- list: {\"type\":\"list\",\"items\":[...],\"ordered\":false}\n")
    append("- table: {\"type\":\"table\",\"headers\":[\"Col1\",\"Col2\"],\"rows\":[[\"a\",\"b\"]]}\n")
    append("- icon: {\"type\":\"icon\",\"name\":\"...\",\"size\":24,\"color\":\"primary|secondary|error\"}\n")
    append("- code: {\"type\":\"code\",\"code\":\"...\",\"language\":\"kotlin\"}\n")
    append("- progress: {\"type\":\"progress\",\"value\":0.5,\"label\":\"50%\"}\n")
    append("- countdown: {\"type\":\"countdown\",\"seconds\":300,\"label\":\"Time left\",\"action\":{\"type\":\"callback\",\"event\":\"timer_done\"}}\n")
    append("- alert: {\"type\":\"alert\",\"message\":\"...\",\"title\":\"...\",\"severity\":\"info|success|warning|error\"}\n")
    append("- tabs: {\"type\":\"tabs\",\"tabs\":[{\"label\":\"Tab 1\",\"children\":[...]},{\"label\":\"Tab 2\",\"children\":[...]}]}\n")
    append("- accordion: {\"type\":\"accordion\",\"title\":\"...\",\"children\":[...],\"expanded\":false}\n")
    append("- box: {\"type\":\"box\",\"children\":[...],\"contentAlignment\":\"center|top_start|...\"}\n")
    append("- quote: {\"type\":\"quote\",\"text\":\"...\",\"source\":\"Author Name\"}\n")
    append("- badge: {\"type\":\"badge\",\"value\":\"3\",\"color\":\"primary|secondary|error\"}\n")
    append("- stat: {\"type\":\"stat\",\"value\":\"\$1,234\",\"label\":\"Revenue\"}\n")
    append("- avatar: {\"type\":\"avatar\",\"name\":\"John Doe\",\"imageUrl\":\"https://...\",\"size\":40}\n\n")
    append("Actions: callback (collects inputs, sends as user message), toggle (shows/hides), open_url, copy_to_clipboard.\n")
}
