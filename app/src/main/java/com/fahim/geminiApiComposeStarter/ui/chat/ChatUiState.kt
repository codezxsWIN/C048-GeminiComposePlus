package com.fahim.geminiApiComposeStarter.ui.chat

import com.fahim.geminiApiComposeStarter.data.local.ContextStatus
import com.fahim.geminiApiComposeStarter.data.local.MessageRole
import com.fahim.geminiApiComposeStarter.data.local.RequestStatus
import com.fahim.geminiApiComposeStarter.data.local.ChatSecurityLevel

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
    val crossChatMemoryCount: Int = 0,
    val nextContextIds: Set<Long> = emptySet(),
    val memoryPreview: List<String> = emptyList(),
    val contextBlocked: Boolean = false,
    val apiKeyConfigured: Boolean = false,
    val apiKeyNeedsRecovery: Boolean = false,
    val chats: List<ChatTab> = emptyList(),
    val chatSearchQuery: String = "",
    val chatSearchResults: List<ChatTab> = emptyList(),
    val activeChatId: Long = 1,
    val securityLevel: ChatSecurityLevel = ChatSecurityLevel.PRIVATE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val editingMessageId: Long? = null,
)

data class ChatTab(
    val id: Long,
    val title: String,
    val securityLevel: ChatSecurityLevel,
    val updatedAt: Long = 0,
    val matchPreview: String? = null,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
    val replyToId: Long? = null,
    val variantGroupId: Long? = null,
    val variantIndex: Int = 1,
    val variantCount: Int = 1,
    val variantIds: List<Long> = emptyList(),
    val contextMessageCount: Int = 0,
    val protectedUsedCount: Int = 0,
    val excludedAtRequestCount: Int = 0,
    val trimmedAtRequestCount: Int = 0,
    val customInstructionsUsed: Boolean = false,
    val wasVoicePrompt: Boolean = false,
) {
    val isFromUser get() = role == MessageRole.USER
    val isSummary get() = role == MessageRole.SUMMARY
}
