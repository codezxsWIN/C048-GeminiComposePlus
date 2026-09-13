package com.fahim.geminiApiComposeStarter.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fahim.geminiApiComposeStarter.data.*
import com.fahim.geminiApiComposeStarter.data.local.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val repository: GeminiRepository,
    private val historyRepository: ChatHistoryRepository,
    private val preferences: AppPreferences? = null,
    private val apiKeyStore: ApiKeyStore? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var entities: List<ChatMessageEntity> = emptyList()
    private var failedRequestId: Long? = null
    private var failedPrompt: String? = null

    init {
        viewModelScope.launch {
            historyRepository.messages.collect { loaded ->
                entities = loaded
                updateMessagesAndContext()
            }
        }
        preferences?.let { store ->
            viewModelScope.launch {
                store.state.collect { saved ->
                    _uiState.update { it.copy(prompt = saved.draft, customInstructions = saved.customInstructions) }
                    updateMessagesAndContext()
                }
            }
        }
        apiKeyStore?.let { store ->
            viewModelScope.launch {
                store.status.collect { status ->
                    _uiState.update { it.copy(apiKeyConfigured = status.isConfigured, apiKeyNeedsRecovery = status.needsRecovery) }
                }
            }
        }
    }

    fun onPromptChange(value: String) {
        _uiState.update { it.copy(prompt = value, promptError = null) }
        preferences?.let { viewModelScope.launch(ioDispatcher) { it.setDraft(value) } }
        updateMessagesAndContext()
    }

    fun onVoiceResult(value: String) = onPromptChange(value)
    fun onVoiceUnavailable() = showError("Voice recognition is not available on this device.")
    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
    fun setContextPanel(open: Boolean) = _uiState.update { it.copy(contextPanelOpen = open) }
    fun setPrivacyPanel(open: Boolean) = _uiState.update { it.copy(privacyPanelOpen = open) }

    fun setCustomInstructions(value: String) {
        _uiState.update { it.copy(customInstructions = value) }
        preferences?.let { viewModelScope.launch(ioDispatcher) { it.setCustomInstructions(value) } }
        updateMessagesAndContext()
    }

    fun resetCustomInstructions() = setCustomInstructions("")
    fun setContextStatus(id: Long, status: ContextStatus) {
        viewModelScope.launch(ioDispatcher) { historyRepository.setContextStatus(id, status) }
    }
    fun clearHistory() { viewModelScope.launch(ioDispatcher) { historyRepository.clear() } }
    fun clearDraftAndInstructions() { viewModelScope.launch(ioDispatcher) { preferences?.clearUserPreferences() } }
    fun clearEncryptedApiKey() { viewModelScope.launch(ioDispatcher) { apiKeyStore?.clear() } }
    fun deleteSummary() { viewModelScope.launch(ioDispatcher) { historyRepository.deleteSummaries() } }

    fun retry() {
        val id = failedRequestId ?: return
        val prompt = failedPrompt ?: return
        if (!_uiState.value.isLoading) viewModelScope.launch { executeRequest(id, prompt, true) }
    }

    fun onSend() {
        val raw = _uiState.value.prompt.trim()
        if (raw.isEmpty()) {
            _uiState.update { it.copy(promptError = PromptError.EMPTY) }
            return
        }
        if (_uiState.value.isLoading) return
        when (raw.lowercase()) {
            "/summarize" -> { onPromptChange(""); summarizeChat(); return }
            "/reset-instructions" -> {
                onPromptChange("")
                resetCustomInstructions()
                showError("Custom instructions reset.")
                return
            }
        }
        val prompt = if (raw.startsWith("/formal ", ignoreCase = true)) {
            "Respond in a professional, formal style. " + raw.substringAfter(' ').trim()
        } else raw
        _uiState.update { it.copy(prompt = "", isLoading = true, errorMessage = null, promptError = null, canRetry = false) }
        preferences?.let { viewModelScope.launch(ioDispatcher) { it.setDraft("") } }
        viewModelScope.launch {
            val id = withContext(ioDispatcher) { historyRepository.addPendingUser(prompt) }
            executeRequest(id, prompt)
        }
    }

    fun summarizeChat() {
        if (_uiState.value.isLoading || _uiState.value.isSummarizing) return
        viewModelScope.launch {
            val snapshot = withContext(ioDispatcher) { historyRepository.snapshot() }
            val allowed = snapshot.filter {
                it.contextStatus != ContextStatus.EXCLUDED.name &&
                    it.requestStatus == RequestStatus.COMPLETE.name &&
                    it.role != MessageRole.SUMMARY.name
            }
            if (allowed.isEmpty()) {
                showError("There is no included chat content to summarize.")
                return@launch
            }
            _uiState.update { it.copy(isSummarizing = true, errorMessage = null) }
            val transcript = allowed.joinToString("\n") {
                (if (it.role == MessageRole.USER.name) "User: " else "Gemini: ") + it.text
            }
            val request = ChatRequest(
                currentMessage = "Summarize this conversation clearly. Preserve decisions and important details:\n\n" + transcript,
                orderedHistory = emptyList(),
                requestId = -1,
            )
            when (val result = repository.generate(request)) {
                is GeminiResult.Success -> withContext(ioDispatcher) { historyRepository.addSummary(result.text) }
                is GeminiResult.Failure -> showError(result.reason.userMessage)
            }
            _uiState.update { it.copy(isSummarizing = false) }
        }
    }

    private suspend fun executeRequest(id: Long, prompt: String, isRetry: Boolean = false) {
        if (isRetry) withContext(ioDispatcher) { historyRepository.markPending(id) }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, canRetry = false) }
        val snapshot = withContext(ioDispatcher) { historyRepository.snapshot() }
        val context = ContextAssembler.assemble(snapshot, id, prompt, _uiState.value.customInstructions)
        if (context.protectedOverflow) {
            withContext(ioDispatcher) { historyRepository.fail(id) }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    promptError = PromptError.PROTECTED_CONTEXT_TOO_LARGE,
                    errorMessage = "Protected details exceed the safe context budget. Unprotect or shorten some messages.",
                )
            }
            return
        }
        val request = ChatRequest(prompt, context.history, _uiState.value.customInstructions, id)
        when (val result = repository.generate(request)) {
            is GeminiResult.Success -> {
                withContext(ioDispatcher) { historyRepository.complete(id, result.text) }
                failedRequestId = null
                failedPrompt = null
                _uiState.update { it.copy(isLoading = false, canRetry = false) }
            }
            is GeminiResult.Failure -> {
                withContext(ioDispatcher) { historyRepository.fail(id) }
                failedRequestId = id
                failedPrompt = prompt
                _uiState.update { it.copy(isLoading = false, errorMessage = result.reason.userMessage, canRetry = true) }
            }
        }
    }

    private fun updateMessagesAndContext() {
        val current = _uiState.value
        val context = ContextAssembler.assemble(entities, null, current.prompt, current.customInstructions)
        _uiState.update {
            it.copy(
                messages = entities.map { entity ->
                    ChatMessage(
                        id = entity.id,
                        text = entity.text,
                        role = runCatching { MessageRole.valueOf(entity.role) }.getOrDefault(if (entity.isFromUser) MessageRole.USER else MessageRole.MODEL),
                        contextStatus = runCatching { ContextStatus.valueOf(entity.contextStatus) }.getOrDefault(ContextStatus.INCLUDED),
                        requestStatus = runCatching { RequestStatus.valueOf(entity.requestStatus) }.getOrDefault(RequestStatus.COMPLETE),
                        createdAt = entity.createdAt,
                    )
                },
                estimatedTokens = context.estimatedTokens,
                protectedCount = context.protectedCount,
                excludedCount = context.excludedCount,
                trimmedCount = context.trimmedCount,
            )
        }
    }

    private fun showError(message: String) = _uiState.update { it.copy(errorMessage = message) }

    companion object {
        fun factory(repository: GeminiRepository, historyRepository: ChatHistoryRepository, preferences: AppPreferences, apiKeyStore: ApiKeyStore) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(repository, historyRepository, preferences, apiKeyStore) as T
            }
    }
}
