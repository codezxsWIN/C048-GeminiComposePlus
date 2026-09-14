package com.fahim.geminiApiComposeStarter.data

import com.fahim.geminiApiComposeStarter.data.local.ChatMessageEntity
import com.fahim.geminiApiComposeStarter.data.local.ContextStatus
import com.fahim.geminiApiComposeStarter.data.local.MessageRole
import com.fahim.geminiApiComposeStarter.data.local.RequestStatus
import kotlin.math.ceil

data class ContextSnapshot(
    val history: List<ContextMessage>,
    val estimatedTokens: Int,
    val protectedCount: Int,
    val excludedCount: Int,
    val trimmedCount: Int,
    val protectedOverflow: Boolean,
    val selectedMessageIds: Set<Long> = emptySet(),
)

object ContextAssembler {
    const val APP_TOKEN_BUDGET = 24_000
    private const val CHARS_PER_TOKEN = 3.0

    /** Apply the same privacy gate to chat requests, summaries and previews. */
    fun eligibleMessages(messages: List<ChatMessageEntity>, currentMessageId: Long? = null): List<ChatMessageEntity> {
        val allowed = messages.filter {
            it.id != currentMessageId && it.requestStatus == RequestStatus.COMPLETE.name &&
                it.role in setOf(MessageRole.USER.name, MessageRole.MODEL.name) &&
                it.isSelectedVariant &&
                it.contextStatus in setOf(ContextStatus.INCLUDED.name, ContextStatus.PROTECTED.name)
        }
        val userIds = allowed.filter { it.role == MessageRole.USER.name }.mapTo(mutableSetOf()) { it.id }
        return allowed.filter {
            it.role != MessageRole.MODEL.name || it.replyToId == null || it.replyToId in userIds
        }.sortedWith(compareBy(ChatMessageEntity::createdAt, ChatMessageEntity::id))
    }

    fun assemble(
        messages: List<ChatMessageEntity>,
        currentMessageId: Long?,
        currentText: String,
        customInstructions: String,
    ): ContextSnapshot {
        val ordered = messages.sortedWith(compareBy(ChatMessageEntity::createdAt, ChatMessageEntity::id))
        val eligible = eligibleMessages(ordered, currentMessageId)
        val excludedCount = ordered.count {
            it.role != MessageRole.SUMMARY.name && it.contextStatus == ContextStatus.EXCLUDED.name
        }
        val protected = eligible.filter { it.contextStatus == ContextStatus.PROTECTED.name }
        val parentIds = protected.mapNotNull { it.replyToId }.toSet()
        val required = eligible.filter { it.contextStatus == ContextStatus.PROTECTED.name || it.id in parentIds }
        val baseChars = currentText.length + customInstructions.length
        val protectedChars = required.sumOf { it.text.length + 16 }
        val maxChars = (APP_TOKEN_BUDGET * CHARS_PER_TOKEN).toInt()
        if (baseChars + protectedChars > maxChars) {
            return ContextSnapshot(emptyList(), estimate(baseChars + protectedChars), protected.size, excludedCount, 0, true)
        }
        var remaining = maxChars - baseChars - protectedChars
        val requiredIds = required.map { it.id }.toSet()
        val ordinary = eligible.filterNot { it.id in requiredIds }
        val selectedOrdinary = mutableListOf<ChatMessageEntity>()
        for (message in ordinary.asReversed()) {
            val cost = message.text.length + 16
            if (cost <= remaining) {
                selectedOrdinary += message
                remaining -= cost
            }
        }
        val selectedIds = (required + selectedOrdinary).mapTo(mutableSetOf()) { it.id }
        // A reply must not survive after its parent was removed by the budget.
        val selectedUsers = eligible.filter { it.id in selectedIds && it.role == MessageRole.USER.name }.map { it.id }.toSet()
        val selectedEntities = eligible.filter {
            it.id in selectedIds && (it.role != MessageRole.MODEL.name || it.replyToId == null || it.replyToId in selectedUsers)
        }.dropWhile { it.role == MessageRole.MODEL.name }
        val actualIds = selectedEntities.mapTo(mutableSetOf()) { it.id }
        if (protected.any { it.id !in actualIds }) {
            return ContextSnapshot(emptyList(), estimate(baseChars + protectedChars), protected.size, excludedCount, 0, true)
        }
        val selectedContext = selectedEntities.mapNotNull {
            val role = when (it.role) {
                MessageRole.USER.name -> ChatRole.USER
                MessageRole.MODEL.name -> ChatRole.MODEL
                else -> null
            }
            role?.let { roleValue -> ContextMessage(it.id, roleValue, it.text) }
        }
        val (context, invalidLeadingCount) = normalizeForChatHistory(selectedContext)
        val serializedChars = baseChars + context.sumOf { it.text.length + 16 }
        return ContextSnapshot(
            history = context,
            estimatedTokens = estimate(serializedChars),
            protectedCount = protected.size,
            excludedCount = excludedCount,
            trimmedCount = eligible.size - actualIds.size + invalidLeadingCount,
            protectedOverflow = false,
            selectedMessageIds = actualIds,
        )
    }

    private fun estimate(chars: Int) = ceil(chars / CHARS_PER_TOKEN).toInt()

    /** Gemini chat history must begin with a user turn and remain role-normalized. */
    private fun normalizeForChatHistory(messages: List<ContextMessage>): Pair<List<ContextMessage>, Int> {
        val withoutLeadingModels = messages.dropWhile { it.role == ChatRole.MODEL }
        val dropped = messages.size - withoutLeadingModels.size
        val normalized = mutableListOf<ContextMessage>()
        for (message in withoutLeadingModels) {
            val previous = normalized.lastOrNull()
            if (previous?.role == message.role) {
                normalized[normalized.lastIndex] = previous.copy(text = previous.text + "\n\n" + message.text)
            } else {
                normalized += message
            }
        }
        return normalized to dropped
    }
}
