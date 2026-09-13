package com.fahim.geminiApiComposeStarter.ui.chat

import com.fahim.geminiApiComposeStarter.data.local.ContextStatus
import com.fahim.geminiApiComposeStarter.data.local.MessageRole
import com.fahim.geminiApiComposeStarter.data.local.RequestStatus

data class ChatUiState(
    val prompt: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isSummarizing: Boolean = false,
    val promptError: PromptError? = null,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val customInstructions: String = "",
    val contextPanelOpen: Boolean = false,
    val privacyPanelOpen: Boolean = false,
    val estimatedTokens: Int = 0,
    val protectedCount: Int = 0,
    val excludedCount: Int = 0,
    val trimmedCount: Int = 0,
    val apiKeyConfigured: Boolean = false,
    val apiKeyNeedsRecovery: Boolean = false,
)

enum class PromptError { EMPTY, PROTECTED_CONTEXT_TOO_LARGE }

enum class ContextHealth { LOW, MEDIUM, HIGH, NEAR_LIMIT }

val ChatUiState.contextHealth: ContextHealth
    get() = when {
        estimatedTokens >= 21_000 -> ContextHealth.NEAR_LIMIT
        estimatedTokens >= 15_000 -> ContextHealth.HIGH
        estimatedTokens >= 7_000 -> ContextHealth.MEDIUM
        else -> ContextHealth.LOW
    }

data class ChatMessage(
    val id: Long,
    val text: String,
    val role: MessageRole,
    val contextStatus: ContextStatus,
    val requestStatus: RequestStatus,
    val createdAt: Long,
) {
    val isFromUser get() = role == MessageRole.USER
    val isSummary get() = role == MessageRole.SUMMARY
}
