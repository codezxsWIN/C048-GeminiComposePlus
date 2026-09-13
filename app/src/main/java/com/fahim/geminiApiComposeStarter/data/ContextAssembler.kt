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
)

object ContextAssembler {
    const val APP_TOKEN_BUDGET = 24_000
    private const val CHARS_PER_TOKEN = 3.0

    fun assemble(
        messages: List<ChatMessageEntity>,
        currentMessageId: Long?,
        currentText: String,
        customInstructions: String,
    ): ContextSnapshot {
        val ordered = messages.sortedWith(compareBy(ChatMessageEntity::createdAt, ChatMessageEntity::id))
        val eligible = ordered.filter {
            it.id != currentMessageId &&
                it.requestStatus == RequestStatus.COMPLETE.name &&
                it.role != MessageRole.SUMMARY.name &&
                it.isSelectedVariant &&
                it.contextStatus != ContextStatus.EXCLUDED.name
        }
        val excludedCount = ordered.count {
            it.role != MessageRole.SUMMARY.name && it.contextStatus == ContextStatus.EXCLUDED.name
        }
        val protected = eligible.filter { it.contextStatus == ContextStatus.PROTECTED.name }
        val baseChars = currentText.length + customInstructions.length
        val protectedChars = protected.sumOf { it.text.length + 16 }
        val maxChars = (APP_TOKEN_BUDGET * CHARS_PER_TOKEN).toInt()
        if (baseChars + protectedChars > maxChars) {
            return ContextSnapshot(emptyList(), estimate(baseChars + protectedChars), protected.size, excludedCount, 0, true)
        }
        var remaining = maxChars - baseChars - protectedChars
        val ordinary = eligible.filterNot { it.contextStatus == ContextStatus.PROTECTED.name }
        val selectedOrdinary = mutableListOf<ChatMessageEntity>()
        for (message in ordinary.asReversed()) {
            val cost = message.text.length + 16
            if (cost <= remaining) {
                selectedOrdinary += message
                remaining -= cost
            }
        }
        val selectedIds = (protected + selectedOrdinary).mapTo(mutableSetOf()) { it.id }
        val selectedContext = eligible.filter { it.id in selectedIds }.mapNotNull {
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
            trimmedCount = eligible.size - selectedIds.size + invalidLeadingCount,
            protectedOverflow = false,
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
