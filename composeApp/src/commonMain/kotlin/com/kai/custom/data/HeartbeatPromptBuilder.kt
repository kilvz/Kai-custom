@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kai.custom.data

import kotlin.time.Instant

internal data class HeartbeatPendingEmail(
    val accountEmail: String,
    val from: String,
    val subject: String,
    val preview: String,
)

internal data class HeartbeatPendingSms(
    val id: Long,
    val from: String,
    val preview: String,
)

internal data class HeartbeatPendingNotification(
    val id: String,
    val appLabel: String,
    val title: String,
    val preview: String,
)

internal data class HeartbeatPromotionCandidate(
    val key: String,
    val hitCount: Int,
    val category: MemoryCategory,
    val content: String,
)

internal fun buildHeartbeatPrompt(
    customOrDefaultPrompt: String,
    heartbeatAdditions: List<ScheduledTask>,
    recentResponses: List<String>,
    pendingTasks: List<ScheduledTask>,
    emailAccounts: List<EmailAccountSummary>,
    pendingEmails: List<HeartbeatPendingEmail>,
    pendingSms: List<HeartbeatPendingSms>,
    pendingNotifications: List<HeartbeatPendingNotification>,
    promotionCandidates: List<HeartbeatPromotionCandidate>,
    learnedPatterns: List<MemoryEntry> = emptyList(),
    personaTraitBlock: String? = null,
): String = buildString {
    append(customOrDefaultPrompt)
    append("\n")

    if (!personaTraitBlock.isNullOrBlank()) {
        append("\n## Behavior\n")
        append(personaTraitBlock)
        append('\n')
    }

    if (heartbeatAdditions.isNotEmpty()) {
        append("\n## Heartbeat Additions\n")
        append("Standing instructions the user asked to run on every heartbeat. Address each in your response alongside the main self-check \u2014 if all are satisfied and nothing else needs attention, respond with your acknowledgement rather than HEARTBEAT_OK (the additions are the attention).\n")
        for (addition in heartbeatAdditions) {
            append("- **")
            append(addition.description)
            append("** (id: ")
            append(addition.id)
            append("): ")
            append(addition.prompt)
            append('\n')
        }
    }

    if (recentResponses.isNotEmpty()) {
        append("\n## Previous Heartbeat Results\n")
        for ((i, response) in recentResponses.withIndex()) {
            append(i + 1)
            append(". ")
            append(response)
            append('\n')
        }
    }

    if (pendingTasks.isNotEmpty()) {
        append("\n## Pending Tasks\n")
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

    if (emailAccounts.isNotEmpty()) {
        append("\n## Email Status\n")
        for (account in emailAccounts) {
            append("- **")
            append(account.email)
            append("**: ")
            append(account.unreadCount)
            append(" unread")
            if (account.lastSyncEpochMs > 0) {
                append(" (last sync: ")
                append(Instant.fromEpochMilliseconds(account.lastSyncEpochMs))
                append(")")
            }
            append('\n')
        }
    }

    if (pendingEmails.isNotEmpty()) {
        append("\n## New Emails\n")
        append("These arrived since the last heartbeat. Summarise briefly; only flag items that genuinely need attention.\n")
        for (msg in pendingEmails) {
            append("- **")
            append(msg.subject.ifBlank { "(no subject)" })
            append("** \u2014 ")
            append(msg.from)
            append(" [")
            append(msg.accountEmail)
            append("]")
            if (msg.preview.isNotBlank()) {
                append(": ")
                append(msg.preview)
            }
            append('\n')
        }
    }

    if (pendingSms.isNotEmpty()) {
        append("\n## New SMS\n")
        append("These SMS arrived since the last heartbeat. Summarise briefly; only flag items that genuinely need attention.\n")
        for (msg in pendingSms) {
            append("- **")
            append(msg.from.ifBlank { "(unknown sender)" })
            append("** (id: ")
            append(msg.id)
            append(")")
            if (msg.preview.isNotBlank()) {
                append(": ")
                append(msg.preview)
            }
            append('\n')
        }
    }

    if (pendingNotifications.isNotEmpty()) {
        append("\n## New Notifications\n")
        append("These notifications arrived since the last heartbeat. Summarise briefly; only flag items that genuinely need attention.\n")
        for (notif in pendingNotifications) {
            append("- **")
            append(notif.appLabel.ifBlank { "(unknown app)" })
            append("**")
            if (notif.title.isNotBlank()) {
                append(" \u2014 ")
                append(notif.title)
            }
            append(" (id: ")
            append(notif.id)
            append(")")
            if (notif.preview.isNotBlank()) {
                append(": ")
                append(notif.preview)
            }
            append('\n')
        }
    }

    if (learnedPatterns.isNotEmpty()) {
        append("\n## Learned Patterns\n")
        append("These are behavioral patterns and preferences I've observed about the user. ")
        append("Review them \u2014 if any seem outdated or incorrect, update your understanding.\n")
        for (entry in learnedPatterns) {
            append("- **")
            append(entry.key)
            append("**")
            if (entry.hitCount > 1) append(" (reinforced ").append(entry.hitCount).append("x)")
            append(": ")
            append(entry.content)
            append('\n')
        }
    }

    if (promotionCandidates.isNotEmpty()) {
        append("\n## Promotion Candidates\n")
        append("These memories have been reinforced ")
        append(promotionCandidates.first().hitCount)
        append("+ times. ")
        append("Consider using the promote_learning tool to add well-established patterns to your soul/system prompt:\n")
        for (entry in promotionCandidates) {
            append("- **")
            append(entry.key)
            append("** (hits: ")
            append(entry.hitCount)
            append(", category: ")
            append(entry.category)
            append("): ")
            append(entry.content)
            append('\n')
        }
    }
}
