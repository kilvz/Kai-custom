package com.kai.custom.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatSystemPromptBuilderTest {

    private val runtime = ChatPromptRuntimeContext(
        nowLocalIsoWithOffset = "2026-04-11T02:00:00+02:00",
        timeZoneId = "Europe/Berlin",
        nowUtcIsoString = "2026-04-11T00:00:00Z",
        platform = "Test",
        modelId = "test-model",
        providerName = "Test Provider",
    )

    private fun memory(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        hitCount: Int = 1,
    ) = MemoryEntry(
        key = key,
        content = content,
        createdAt = 0L,
        updatedAt = 0L,
        category = category,
        hitCount = hitCount,
    )

    private fun task(
        id: String = "task-1",
        description: String = "Do the thing",
        scheduledAtEpochMs: Long = 0L,
        cron: String? = null,
    ) = ScheduledTask(
        id = id,
        description = description,
        prompt = "",
        scheduledAtEpochMs = scheduledAtEpochMs,
        createdAtEpochMs = 0L,
        cron = cron,
    )

    private fun build(
        variant: SystemPromptVariant,
        soul: String = "You are Kai.",
        hasTools: Boolean = true,
        memoryEnabled: Boolean = true,
        schedulingEnabled: Boolean = true,
        relevantMemories: List<MemoryEntry> = emptyList(),
        generalMemories: List<MemoryEntry> = emptyList(),
        preferenceMemories: List<MemoryEntry> = emptyList(),
        learningMemories: List<MemoryEntry> = emptyList(),
        errorMemories: List<MemoryEntry> = emptyList(),
        pendingTasks: List<ScheduledTask> = emptyList(),
        heartbeatAdditions: List<ScheduledTask> = emptyList(),
        emailAccounts: List<EmailAccountSummary> = emptyList(),
        uiMode: ChatPromptUiMode = ChatPromptUiMode.NONE,
        activeSkill: com.inspiredandroid.kai.skills.SkillManifest? = null,
    ) = UnifiedPromptBuilder().build(
        PromptContext(
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
            preferredLanguage = "en",
            renderMode = RenderMode.FORK_ENHANCED,
            taskDomains = if (relevantMemories.isNotEmpty() || generalMemories.isNotEmpty()) {
                setOf(TaskDomain.MEMORY_QUERY)
            } else {
                setOf(TaskDomain.GENERAL_CHAT)
            },
        ),
    )

    private val languageLine = "## Language"

    // region CHAT_REMOTE — focused tests

    @Test
    fun `CHAT_REMOTE default emits soul + language + honesty + context`() {
        val out = build(SystemPromptVariant.CHAT_REMOTE)
        assertTrue(out.startsWith("You are Kai."))
        assertTrue("## Language" in out)
        assertTrue(DEFAULT_HONESTY_RULE in out)
        assertTrue("## Context" in out)
        assertTrue("- Local time: 2026-04-11T02:00:00+02:00 (Europe/Berlin)" in out)
        assertTrue("- UTC: 2026-04-11T00:00:00Z" in out)
        assertTrue("- Platform: Test" in out)
        assertTrue("- Model: test-model" in out)
        assertTrue("- Provider: Test Provider" in out)
    }

    @Test
    fun `removed sections are not present in either variant`() {
        val remote = build(SystemPromptVariant.CHAT_REMOTE)
        val local = build(SystemPromptVariant.CHAT_LOCAL)
        for (out in listOf(remote, local)) {
            assertFalse("## Tool Use" in out)
            assertFalse("## When to Act" in out)
            assertFalse("## Memory System" in out)
            assertFalse("## Automation" in out)
        }
    }

    @Test
    fun `honesty rule is composed into both variants`() {
        val remote = build(SystemPromptVariant.CHAT_REMOTE)
        val local = build(SystemPromptVariant.CHAT_LOCAL)
        for (out in listOf(remote, local)) {
            assertTrue(DEFAULT_HONESTY_RULE in out)
        }
    }

    @Test
    fun `CHAT_REMOTE includes What I Know About You when memories exist`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            generalMemories = listOf(memory("user_name", "Alice")),
        )
        assertTrue("## What I Know About You" in out)
        assertTrue("- **user_name**: Alice" in out)
    }

    @Test
    fun `CHAT_REMOTE omits What I Know About You when memory is disabled`() {
        val out = build(SystemPromptVariant.CHAT_REMOTE, memoryEnabled = false)
        assertFalse("## What I Know About You" in out)
    }

    @Test
    fun `CHAT_REMOTE includes User Preferences when preference memories present`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            preferenceMemories = listOf(memory("tone", "concise", category = MemoryCategory.PREFERENCE)),
        )
        assertTrue("## User Preferences" in out)
        assertTrue("- **tone**: concise" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Learnings with reinforcement counts`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            learningMemories = listOf(
                memory("commit_style", "gerund verbs", category = MemoryCategory.LEARNING, hitCount = 5),
            ),
        )
        assertTrue("## Learnings" in out)
        assertTrue("- **commit_style** (reinforced 5x): gerund verbs" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Known Issues section when error memories present`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            errorMemories = listOf(memory("flaky_test", "retry twice", category = MemoryCategory.ERROR)),
        )
        assertTrue("## Known Issues & Resolutions" in out)
        assertTrue("- **flaky_test**: retry twice" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Scheduled Tasks with cron annotation`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            pendingTasks = listOf(
                task(id = "t1", description = "Morning check", cron = "0 9 * * *"),
            ),
        )
        assertTrue("## Scheduled Tasks" in out)
        assertTrue("- **Morning check** (id: t1" in out)
        assertTrue("[cron: 0 9 * * *]" in out)
    }

    @Test
    fun `CHAT_REMOTE omits Scheduled Tasks when list is empty`() {
        val out = build(variant = SystemPromptVariant.CHAT_REMOTE)
        assertFalse("## Scheduled Tasks" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Email Accounts when list non-empty`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            emailAccounts = listOf(
                EmailAccountSummary(
                    email = "alice@example.com",
                    unreadCount = 3,
                    lastSyncEpochMs = 1_700_000_000_000L,
                ),
            ),
        )
        assertTrue("## Email Accounts" in out)
        assertTrue("- **alice@example.com**: 3 unread" in out)
    }

    @Test
    fun `CHAT_REMOTE Email Accounts surfaces sync failures via lastError`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            emailAccounts = listOf(
                EmailAccountSummary(
                    email = "bob@example.com",
                    unreadCount = 0,
                    lastSyncEpochMs = 0L,
                    lastError = "AUTHENTICATIONFAILED",
                ),
            ),
        )
        assertTrue("## Email Accounts" in out)
        assertTrue("sync failing" in out)
        assertTrue("AUTHENTICATIONFAILED" in out)
    }

    @Test
    fun `CHAT_REMOTE no longer includes sending policy in Email Accounts`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            emailAccounts = listOf(
                EmailAccountSummary(email = "a@b.com", unreadCount = 0, lastSyncEpochMs = 0L),
            ),
        )
        assertFalse("do NOT suggest adding" in out)
        assertFalse("only if" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Heartbeat Additions when present`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            heartbeatAdditions = listOf(
                ScheduledTask(
                    id = "h1",
                    description = "Greeting",
                    prompt = "Say hi",
                    scheduledAtEpochMs = 0L,
                    createdAtEpochMs = 0L,
                    trigger = TaskTrigger.HEARTBEAT,
                ),
            ),
        )
        assertTrue("## Heartbeat Additions" in out)
        assertTrue("Standing instructions" in out)
        assertTrue("Greeting" in out)
    }

    @Test
    fun `CHAT_REMOTE Email Accounts still render when scheduling is disabled`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            schedulingEnabled = false,
            emailAccounts = listOf(
                EmailAccountSummary(email = "alice@example.com", unreadCount = 1, lastSyncEpochMs = 0L),
            ),
        )
        assertTrue("## Email Accounts" in out)
        assertTrue("alice@example.com" in out)
    }

    @Test
    fun `CHAT_REMOTE with everything deactivated is soul + language + honesty + context`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            soul = "You're a personal assistant.",
            hasTools = false,
            memoryEnabled = false,
            schedulingEnabled = false,
        )
        val expected = "You're a personal assistant.\n\n" +
            "## Language\nAdapt to the user's language. Speak the language they write in.\n\n" +
            DEFAULT_HONESTY_RULE + "\n\n" +
            "## Context\n" +
            "- Local time: 2026-04-11T02:00:00+02:00 (Europe/Berlin)\n" +
            "- UTC: 2026-04-11T00:00:00Z\n" +
            "- Platform: Test\n" +
            "- Model: test-model\n" +
            "- Provider: Test Provider\n"
        assertEquals(expected, out)
    }

    // endregion

    // region CHAT_REMOTE — relevant memories

    @Test
    fun `CHAT_REMOTE includes relevant memories before category dumps`() {
        val relevant = listOf(memory("fav_lang", "Kotlin", hitCount = 3))
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            generalMemories = listOf(memory("user_name", "Alice")),
            relevantMemories = relevant,
        )
        assertTrue("## What I Know About You" in out)
        assertTrue("**fav_lang** (reinforced 3x): Kotlin" in out)
    }

    // endregion

    // region CHAT_LOCAL — focused tests

    @Test
    fun `CHAT_LOCAL default emits soul + language + honesty + context`() {
        val out = build(SystemPromptVariant.CHAT_LOCAL)
        assertTrue(out.startsWith("You are Kai."))
        assertTrue(languageLine in out)
        assertTrue(DEFAULT_HONESTY_RULE in out)
        assertTrue("## Context" in out)
    }

    @Test
    fun `CHAT_LOCAL includes memory category sections when within budget`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            generalMemories = listOf(memory("user_name", "Alice")),
            preferenceMemories = listOf(memory("tone", "concise", category = MemoryCategory.PREFERENCE)),
            learningMemories = listOf(memory("style", "gerunds", category = MemoryCategory.LEARNING, hitCount = 3)),
            errorMemories = listOf(memory("flaky_test", "retry", category = MemoryCategory.ERROR)),
        )
        assertTrue("## What I Know About You" in out)
        assertTrue("## Your Memories" in out)
        assertTrue("- **user_name**: Alice" in out)
        assertTrue("## User Preferences" in out)
        assertTrue("- **tone**: concise" in out)
        assertTrue("## Learnings" in out)
        assertTrue("- **style** (reinforced 3x): gerunds" in out)
        assertTrue("## Known Issues & Resolutions" in out)
        assertTrue("- **flaky_test**: retry" in out)
    }

    @Test
    fun `CHAT_LOCAL truncates memories at entry boundary when over budget`() {
        val big = (1..50).map { i ->
            memory(
                key = "key_$i",
                content = "x".repeat(100),
                category = MemoryCategory.GENERAL,
            )
        }
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            generalMemories = big,
        )
        assertTrue("## Your Memories" in out)
        assertTrue("- **key_1**:" in out, "First entry should be included")
        assertFalse("- **key_50**:" in out, "Last entry should be dropped (budget exhausted)")
        val memStart = out.indexOf("## What I Know About You")
        val memEnd = out.indexOf("## Context")
        val memSectionLen = memEnd - memStart
        assertTrue(memSectionLen <= 1150, "Memory section should be ~1024 chars, was $memSectionLen")
    }

    @Test
    fun `CHAT_LOCAL drops lower-priority categories when earlier ones exhaust budget`() {
        val bigGeneral = (1..19).map { i ->
            memory(
                key = "g_$i",
                content = "x".repeat(80),
                category = MemoryCategory.GENERAL,
            )
        }
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            generalMemories = bigGeneral,
            preferenceMemories = listOf(memory("pref_key", "small content", category = MemoryCategory.PREFERENCE)),
            learningMemories = listOf(memory("learn_key", "small content", category = MemoryCategory.LEARNING)),
            errorMemories = listOf(memory("err_key", "small content", category = MemoryCategory.ERROR)),
        )
        assertTrue("## Your Memories" in out)
        val memStart = out.indexOf("## What I Know About You")
        val memEnd = out.indexOf("## Context")
        val memLen = memEnd - memStart
        assertTrue(memLen <= 1200, "Combined memory sections should respect budget, was $memLen")
    }

    @Test
    fun `CHAT_LOCAL omits Scheduled Tasks regardless of input`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            pendingTasks = listOf(task(description = "Do the thing")),
        )
        assertFalse("## Scheduled Tasks" in out)
    }

    @Test
    fun `CHAT_LOCAL omits Email Accounts regardless of input`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            emailAccounts = listOf(
                EmailAccountSummary(email = "alice@example.com", unreadCount = 3, lastSyncEpochMs = 0L),
            ),
        )
        assertFalse("## Email Accounts" in out)
    }

    @Test
    fun `CHAT_LOCAL omits Heartbeat Additions regardless of input`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            heartbeatAdditions = listOf(
                ScheduledTask(
                    id = "h1",
                    description = "Greeting",
                    prompt = "Hi!",
                    scheduledAtEpochMs = 0L,
                    createdAtEpochMs = 0L,
                    trigger = TaskTrigger.HEARTBEAT,
                ),
            ),
        )
        assertFalse("## Heartbeat Additions" in out)
    }

    @Test
    fun `CHAT_LOCAL omits Dynamic UI even when uiMode is DYNAMIC_UI`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            uiMode = ChatPromptUiMode.DYNAMIC_UI,
        )
        assertFalse("## Dynamic UI" in out)
    }

    @Test
    fun `CHAT_LOCAL omits Interactive UI Mode even when uiMode is INTERACTIVE_UI`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            uiMode = ChatPromptUiMode.INTERACTIVE_UI,
        )
        assertFalse("## Interactive UI Mode" in out)
    }

    // endregion

    // region Golden snapshots

    @Test
    fun `golden CHAT_LOCAL with soul + language + honesty + context`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            soul = "You are Kai, a helpful assistant.",
            pendingTasks = listOf(task(description = "ignored task")),
            uiMode = ChatPromptUiMode.DYNAMIC_UI,
        )
        val expected = "You are Kai, a helpful assistant.\n\n" +
            "## Language\nAdapt to the user's language. Speak the language they write in.\n\n" +
            DEFAULT_HONESTY_RULE + "\n\n" +
            "## Context\n" +
            "- Local time: 2026-04-11T02:00:00+02:00 (Europe/Berlin)\n" +
            "- UTC: 2026-04-11T00:00:00Z\n" +
            "- Platform: Test\n" +
            "- Model: test-model\n" +
            "- Provider: Test Provider\n"
        assertEquals(expected, out)
    }

    @Test
    fun `golden CHAT_REMOTE with every section enabled`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            soul = "You are Kai.",
            generalMemories = listOf(memory("fact", "value")),
            preferenceMemories = listOf(memory("pref", "val", category = MemoryCategory.PREFERENCE)),
            learningMemories = listOf(memory("lesson", "body", category = MemoryCategory.LEARNING, hitCount = 3)),
            errorMemories = listOf(memory("issue", "resolution", category = MemoryCategory.ERROR)),
            pendingTasks = listOf(task(id = "t1", description = "First task")),
            emailAccounts = listOf(
                EmailAccountSummary(email = "alice@example.com", unreadCount = 1, lastSyncEpochMs = 0L),
            ),
            heartbeatAdditions = listOf(
                ScheduledTask(
                    id = "h1",
                    description = "Greeting",
                    prompt = "Say hi",
                    scheduledAtEpochMs = 0L,
                    createdAtEpochMs = 0L,
                    trigger = TaskTrigger.HEARTBEAT,
                ),
            ),
            uiMode = ChatPromptUiMode.NONE,
        )
        val headerOrder = listOf(
            "You are Kai.",
            "## Language",
            DEFAULT_HONESTY_RULE,
            "## What I Know About You",
            "## Your Memories",
            "## User Preferences",
            "## Learnings",
            "## Known Issues & Resolutions",
            "## Email Accounts",
            "## Scheduled Tasks",
            "## Heartbeat Additions",
            "## Context",
        )
        var lastIdx = -1
        for (header in headerOrder) {
            val idx = out.indexOf(header)
            assertTrue(idx >= 0, "Expected '$header' in output but was not found. Output:\n$out")
            assertTrue(idx > lastIdx, "Expected '$header' to come after previous section. Output:\n$out")
            lastIdx = idx
        }
    }

    // endregion
}
