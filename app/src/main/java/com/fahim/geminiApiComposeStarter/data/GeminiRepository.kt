package com.fahim.geminiApiComposeStarter.data

data class ContextMessage(val id: Long, val role: ChatRole, val text: String)
enum class ChatRole { USER, MODEL }

data class ChatRequest(
    val currentMessage: String,
    val orderedHistory: List<ContextMessage>,
    val customInstructions: String = "",
    val requestId: Long,
)

sealed interface GeminiFailure {
    val userMessage: String
    data object MissingKey : GeminiFailure { override val userMessage = "Add GEMINI_API_KEY to local.properties, then rebuild." }
    data object Offline : GeminiFailure { override val userMessage = "You're offline. Your chat is saved; reconnect and retry." }
    data object Timeout : GeminiFailure { override val userMessage = "Gemini took too long to respond. Please retry." }
    data object Authentication : GeminiFailure { override val userMessage = "The Gemini API key was rejected." }
    data object Quota : GeminiFailure { override val userMessage = "Gemini quota is temporarily unavailable." }
    data object Blocked : GeminiFailure { override val userMessage = "Gemini could not answer this request safely." }
    data object ContextTooLarge : GeminiFailure { override val userMessage = "This request exceeds the model context limit." }
    data object Unknown : GeminiFailure { override val userMessage = "Gemini is unavailable right now. Please retry." }
}

sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult
    data class Failure(val reason: GeminiFailure) : GeminiResult
}

interface GeminiRepository {
    suspend fun generate(request: ChatRequest): GeminiResult
}
