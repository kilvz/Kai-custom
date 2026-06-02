package com.kai.custom.data

enum class TaskDomain {
    GENERAL_CHAT,
    MEMORY_QUERY,
    FILE_OPERATION,
    EMAIL,
    SCHEDULE,
    NOTIFICATION_QUERY,
    WEB_SEARCH,
    DEVICE_CONTROL,
    CODING,
    ANALYSIS,
}

object TaskClassifier {
    private val memoryKeywords = setOf(
        "remember", "forget", "recall", "memory", "what do you know",
        "what do i", "store this", "save this", "learn this",
    )
    private val emailKeywords = setOf(
        "email", "mail", "inbox", "send to", "compose", "reply",
        "check.*mail", "unread",
    )
    private val scheduleKeywords = setOf(
        "remind", "schedule", "task", "reminder", "calendar",
        "at \\d{1,2}:\\d{2}", "in \\d+ (minute|hour|day)",
    )
    private val notificationKeywords = setOf(
        "notification", "notify", "alert", "ping", "what.*new",
        "any update", "anything",
    )
    private val webSearchKeywords = setOf(
        "search", "look up", "find online", "google", "browse",
        "what is", "who is",
    )
    private val deviceKeywords = setOf(
        "location", "alarm", "contact", "device", "wifi", "bluetooth",
        "battery", "volume", "brightness",
    )
    private val codeKeywords = setOf(
        "code", "script", "function", "class", "debug", "compile",
        "refactor", "implement", "fix", "bug", "syntax",
    )
    private val analysisKeywords = setOf(
        "analyze", "compare", "summarize", "explain", "evaluate",
        "review", "audit", "investigate",
    )

    fun classify(query: String, hasFiles: Boolean = false): Set<TaskDomain> {
        val q = query.lowercase()
        val domains = mutableSetOf(TaskDomain.GENERAL_CHAT)
        if (hasFiles) domains.add(TaskDomain.FILE_OPERATION)
        if (matchesAny(q, memoryKeywords)) domains.add(TaskDomain.MEMORY_QUERY)
        if (matchesAny(q, emailKeywords)) domains.add(TaskDomain.EMAIL)
        if (matchesAny(q, scheduleKeywords)) domains.add(TaskDomain.SCHEDULE)
        if (matchesAny(q, notificationKeywords)) domains.add(TaskDomain.NOTIFICATION_QUERY)
        if (matchesAny(q, webSearchKeywords)) domains.add(TaskDomain.WEB_SEARCH)
        if (matchesAny(q, deviceKeywords)) domains.add(TaskDomain.DEVICE_CONTROL)
        if (matchesAny(q, codeKeywords)) domains.add(TaskDomain.CODING)
        if (matchesAny(q, analysisKeywords)) domains.add(TaskDomain.ANALYSIS)
        return domains
    }

    private fun matchesAny(text: String, keywords: Set<String>): Boolean {
        return keywords.any { keyword ->
            if (keyword.contains("\\")) {
                Regex(keyword).containsMatchIn(text)
            } else {
                text.contains(keyword)
            }
        }
    }
}

internal data class PromptContext(
    val variant: SystemPromptVariant,
    val soul: String,
    val hasTools: Boolean,
    val memoryEnabled: Boolean,
    val schedulingEnabled: Boolean,
    val generalMemories: List<MemoryEntry>,
    val preferenceMemories: List<MemoryEntry>,
    val learningMemories: List<MemoryEntry>,
    val errorMemories: List<MemoryEntry>,
    val relevantMemories: List<MemoryEntry>,
    val pendingTasks: List<ScheduledTask>,
    val heartbeatAdditions: List<ScheduledTask>,
    val emailAccounts: List<EmailAccountSummary>,
    val runtime: ChatPromptRuntimeContext,
    val uiMode: ChatPromptUiMode,
    val preferredLanguage: String,
    val renderMode: RenderMode,
    val taskDomains: Set<TaskDomain> = setOf(TaskDomain.GENERAL_CHAT),
    val attachedFiles: List<String> = emptyList(),
    val soulUserText: String = "",
    val soulAutoText: String = "",
)
