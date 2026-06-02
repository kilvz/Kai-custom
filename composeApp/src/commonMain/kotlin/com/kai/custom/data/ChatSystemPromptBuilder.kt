@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kai.custom.data

import kotlin.time.Instant

enum class SystemPromptVariant {
    CHAT_REMOTE,
    CHAT_LOCAL,
}

internal data class ChatPromptRuntimeContext(
    val nowLocalIsoWithOffset: String,
    val timeZoneId: String,
    val nowUtcIsoString: String,
    val platform: String,
    val modelId: String,
    val providerName: String,
)

internal enum class ChatPromptUiMode { NONE, DYNAMIC_UI, INTERACTIVE_UI }

internal data class EmailAccountSummary(
    val email: String,
    val unreadCount: Int,
    val lastSyncEpochMs: Long,
    val lastError: String? = null,
)

private const val MEMORY_BUDGET_CHARS = 1024

internal fun buildChatSystemPrompt(
    variant: SystemPromptVariant,
    soul: String,
    hasTools: Boolean,
    memoryEnabled: Boolean,
    schedulingEnabled: Boolean,
    generalMemories: List<MemoryEntry>,
    preferenceMemories: List<MemoryEntry>,
    learningMemories: List<MemoryEntry>,
    errorMemories: List<MemoryEntry>,
    relevantMemories: List<MemoryEntry> = emptyList(),
    pendingTasks: List<ScheduledTask>,
    heartbeatAdditions: List<ScheduledTask>,
    emailAccounts: List<EmailAccountSummary>,
    runtime: ChatPromptRuntimeContext,
    uiMode: ChatPromptUiMode,
    preferredLanguage: String = "en",
    personaPromptStyle: PersonaPromptStyle = PersonaPromptStyle.KAI,
): String {
    val renderMode = when (personaPromptStyle) {
        PersonaPromptStyle.KAI -> RenderMode.UPSTREAM_COMPAT
        PersonaPromptStyle.ALT, PersonaPromptStyle.CUSTOM -> RenderMode.FORK_ENHANCED
    }
    val context = PromptContext(
        variant = variant,
        soul = soul,
        hasTools = hasTools,
        memoryEnabled = memoryEnabled,
        schedulingEnabled = schedulingEnabled,
        generalMemories = generalMemories,
        preferenceMemories = preferenceMemories,
        learningMemories = learningMemories,
        errorMemories = errorMemories,
        relevantMemories = relevantMemories,
        pendingTasks = pendingTasks,
        heartbeatAdditions = heartbeatAdditions,
        emailAccounts = emailAccounts,
        runtime = runtime,
        uiMode = uiMode,
        preferredLanguage = preferredLanguage,
        renderMode = renderMode,
        taskDomains = if (relevantMemories.isNotEmpty() || generalMemories.isNotEmpty()) {
            setOf(TaskDomain.MEMORY_QUERY)
        } else {
            setOf(TaskDomain.GENERAL_CHAT)
        },
    )
    return UnifiedPromptBuilder().build(context)
}

private fun StringBuilder.appendMemoryCategorySection(
    header: String,
    entries: List<MemoryEntry>,
    withHitCount: Boolean,
    remainingBudget: Int,
): Int {
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
    append(section)
    return (remainingBudget - section.length).coerceAtLeast(0)
}

private fun StringBuilder.appendEmailAccountsSection(accounts: List<EmailAccountSummary>) {
    append("\n\n## Email Accounts\n")
    append("The user has these email accounts connected. Use them via the existing email tools \u2014 ")
    append("do NOT suggest adding, re-authenticating, or connecting a new account unless the user explicitly asks.\n")
    append("**Sending policy**: before calling `compose_email` or `reply_email`, present the full draft (to, subject, body) in chat and get explicit confirmation (\"send it\" / \"looks good\" / \"yes\"). Never call the send tools on the same turn you draft \u2014 the user must have a chance to correct tone, recipients, or content first. If the user later says \"change X and send\", re-present the updated draft and confirm again.\n")
    for (account in accounts) {
        append("- **")
        append(account.email)
        append("**: ")
        if (account.lastError != null) {
            append("sync failing \u2014 ")
            append(account.lastError)
        } else {
            append(account.unreadCount)
            append(" unread")
            if (account.lastSyncEpochMs > 0) {
                append(" (last sync: ")
                append(Instant.fromEpochMilliseconds(account.lastSyncEpochMs))
                append(')')
            }
        }
        append('\n')
    }
}

private fun StringBuilder.appendScheduledTasksSection(pendingTasks: List<ScheduledTask>) {
    append("\n\n## Scheduled Tasks\n")
    for (t in pendingTasks) {
        append("- **")
        append(t.description)
        append("** (id: ")
        append(t.id)
        append(", scheduled: ")
        append(t.scheduledAt)
        append(")")
        if (t.cron != null) {
            append(" [cron: ")
            append(t.cron)
            append("]")
        }
        append('\n')
    }
}

private fun StringBuilder.appendHeartbeatAdditionsSection(additions: List<ScheduledTask>) {
    append("\n\n## Heartbeat Additions\n")
    append("Standing instructions the user has set to run on every heartbeat (trigger=HEARTBEAT). Don't duplicate these when the user asks for similar behaviour; cancel via `cancel_task` if they want one removed.\n")
    for (t in additions) {
        append("- **")
        append(t.description)
        append("** (id: ")
        append(t.id)
        append("): ")
        append(t.prompt)
        append('\n')
    }
}

private fun StringBuilder.appendContextSection(runtime: ChatPromptRuntimeContext) {
    append("\n\n## Context\n")
    append("- Local time: ").append(runtime.nowLocalIsoWithOffset).append(" (").append(runtime.timeZoneId).append(")\n")
    append("- UTC: ").append(runtime.nowUtcIsoString).append('\n')
    append("- Platform: ").append(runtime.platform).append('\n')
    append("- Model: ").append(runtime.modelId).append('\n')
    append("- Provider: ").append(runtime.providerName).append('\n')
}

private fun StringBuilder.appendDynamicUiSection() {
    append("\n## Dynamic UI\n")
    append("You can enhance your chat responses with interactive UI elements using kai-ui blocks. ")
    append("Proactively use them whenever you need input from the user.\n\n")
    append(KAI_UI_COMPONENT_CATALOG)
    append("Layout tips:\n")
    append("- Put buttons INSIDE cards, directly below related content\n")
    append("- Use rows for groups of buttons or chips \u2014 rows wrap automatically\n")
    append("- Keep button labels short (1-3 words)\n\n")
    append("Example:\n```kai-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Your name?\",\"style\":\"title\"},{\"type\":\"text_input\",\"id\":\"name\",\"placeholder\":\"Enter name\"},{\"type\":\"button\",\"label\":\"Submit\",\"action\":{\"type\":\"callback\",\"event\":\"submit\",\"collectFrom\":[\"name\"]}}]}\n```\n")
}

private fun StringBuilder.appendInteractiveUiSection() {
    append("\n## Interactive UI Mode (ACTIVE)\n")
    append("The user ONLY sees rendered kai-ui components. Your ENTIRE response must be a single ```kai-ui code fence.\n\n")
    append(KAI_UI_COMPONENT_CATALOG)
    append("Rules:\n")
    append("- Each response is a COMPLETE screen layout.\n")
    append("- Every screen MUST have at least one interactive element with a callback action.\n")
    append("- Use callbacks for collecting choices, submitting forms, navigating between steps.\n")
    append("- Do NOT include back buttons or navigation controls.\n")
    append("- Never show loading/fetching states \u2014 present all content immediately.\n")
    append("- Each screen is independent. No client-side state persistence.\n\n")
    append("Example:\n```kai-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Welcome\",\"style\":\"headline\"},{\"type\":\"card\",\"children\":[{\"type\":\"text\",\"value\":\"What would you like to do?\",\"style\":\"title\"},{\"type\":\"button\",\"label\":\"Get Started\",\"action\":{\"type\":\"callback\",\"event\":\"get_started\"}}]}]}\n```\n")
}


