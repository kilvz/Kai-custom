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

internal const val DEFAULT_HONESTY_RULE =
    "Do not fabricate tool outputs, file contents, citations, or completed work."

internal fun buildChatSystemPrompt(
    variant: SystemPromptVariant,
    soul: String,
    hasTools: Boolean,
    memoryEnabled: Boolean,
    schedulingEnabled: Boolean,
    memoryInstructions: String?,
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
): String = buildString {
    append(soul)

    if (isNotEmpty()) append("\n\n")
    append("## Language\nAdapt to the user's language. Speak the language they write in.")

    if (isNotEmpty()) append("\n\n")
    append(DEFAULT_HONESTY_RULE)

    if (memoryEnabled && (generalMemories.isNotEmpty() || preferenceMemories.isNotEmpty() || learningMemories.isNotEmpty() || errorMemories.isNotEmpty() || relevantMemories.isNotEmpty())) {
        if (isNotEmpty()) append("\n\n")
        append("## What I Know About You\n")
        if (relevantMemories.isNotEmpty()) {
            for (entry in relevantMemories) {
                append("- **").append(entry.key).append("**")
                if (entry.hitCount > 1) {
                    append(" (reinforced ").append(entry.hitCount).append("x)")
                }
                append(": ").append(entry.content).append('\n')
            }
        }
        val memoryBudget = MEMORY_BUDGET_CHARS
        var remaining = memoryBudget
        remaining = appendMemoryCategorySection("Your Memories", generalMemories, withHitCount = false, remaining)
        remaining = appendMemoryCategorySection("User Preferences", preferenceMemories, withHitCount = false, remaining)
        remaining = appendMemoryCategorySection("Learnings", learningMemories, withHitCount = true, remaining)
        appendMemoryCategorySection("Known Issues & Resolutions", errorMemories, withHitCount = false, remaining)
    }

    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        if (emailAccounts.isNotEmpty()) {
            append("\n\n## Email Accounts\n")
            for (account in emailAccounts) {
                append("- **").append(account.email).append("**: ")
                if (account.lastError != null) {
                    append("sync failing — ").append(account.lastError)
                } else {
                    append(account.unreadCount).append(" unread")
                    if (account.lastSyncEpochMs > 0) {
                        append(" (last sync: ").append(Instant.fromEpochMilliseconds(account.lastSyncEpochMs)).append(')')
                    }
                }
                append('\n')
            }
        }
        if (pendingTasks.isNotEmpty()) {
            append("\n\n## Scheduled Tasks\n")
            for (t in pendingTasks) {
                append("- **").append(t.description).append("** (id: ").append(t.id).append(", scheduled: ").append(t.scheduledAt).append(")")
                if (t.cron != null) append(" [cron: ").append(t.cron).append("]")
                append('\n')
            }
        }
        if (heartbeatAdditions.isNotEmpty()) {
            append("\n\n## Heartbeat Additions\n")
            append("Standing instructions that run on every heartbeat:\n")
            for (t in heartbeatAdditions) {
                append("- **").append(t.description).append("** (id: ").append(t.id).append("): ").append(t.prompt).append('\n')
            }
        }
    }

    appendContextSection(runtime)

    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        when (uiMode) {
            ChatPromptUiMode.DYNAMIC_UI -> appendDynamicUiSection()
            ChatPromptUiMode.INTERACTIVE_UI -> appendInteractiveUiSection()
            ChatPromptUiMode.NONE -> {}
        }
    }
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
    val headerLen = section.length
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
    append("- Use rows for groups of buttons or chips — rows wrap automatically\n")
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
    append("- Never show loading/fetching states — present all content immediately.\n")
    append("- Each screen is independent. No client-side state persistence.\n\n")
    append("Example:\n```kai-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Welcome\",\"style\":\"headline\"},{\"type\":\"card\",\"children\":[{\"type\":\"text\",\"value\":\"What would you like to do?\",\"style\":\"title\"},{\"type\":\"button\",\"label\":\"Get Started\",\"action\":{\"type\":\"callback\",\"event\":\"get_started\"}}]}]}\n```\n")
}

private val KAI_UI_COMPONENT_CATALOG: String = buildString {
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
