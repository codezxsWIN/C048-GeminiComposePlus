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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val repository: GeminiRepository,
    private val historyRepository: ChatHistoryRepository,
    private val preferences: AppPreferences? = null,
    private val apiKeyStore: ApiKeyStore? = null,
    private val sessionRepository: RoomChatSessionRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var entities: List<ChatMessageEntity> = emptyList()
    private var failedRequestId: Long? = null
    private var failedPrompt: String? = null
    private var nextPromptFromVoice = false
    private var searchJob: Job? = null
    private var previewJob: Job? = null
    private var policyJob: Job? = null
    private var preferencesJob: Job? = null
    private var switchingChat = false
    private val busy get() = switchingChat || _uiState.value.isLoading || _uiState.value.isSummarizing

    private fun saveDraft(value: String) {
        val chatId = _uiState.value.activeChatId
        val previous = preferencesJob
        preferencesJob = viewModelScope.launch {
            previous?.join()
            preferences?.setDraft(value, chatId)
        }
    }

    private suspend fun loadChatPreferences(id: Long) {
        preferencesJob?.join()
        val saved = preferences?.stateForChat(id)?.first()
        if (_uiState.value.activeChatId == id) {
            _uiState.update { it.copy(prompt = saved?.draft.orEmpty(), customInstructions = saved?.customInstructions.orEmpty()) }
            updateMessagesAndContext()
        }
    }

    init {
        viewModelScope.launch {
            val saved = preferences?.state?.first()
            if (saved != null) {
                _uiState.update {
                    it.copy(
                        themeMode = runCatching { ThemeMode.valueOf(saved.themeMode) }.getOrDefault(ThemeMode.SYSTEM),
                    )
                }
            }
            // Now that initial state is loaded, we can start observing messages
            historyRepository.messages.collect { loaded ->
                entities = loaded
                updateMessagesAndContext()
            }
        }
        sessionRepository?.let { repository ->
            viewModelScope.launch {
                val defaultId = repository.ensureDefault()
                historyRepository.selectChat(defaultId)
                _uiState.update { it.copy(activeChatId = defaultId) }
                loadChatPreferences(defaultId)
                repository.sessions.collect { sessions ->
                    val tabs = sessions.map { session ->
                        ChatTab(
                            id = session.id,
                            title = session.title,
                            securityLevel = runCatching { ChatSecurityLevel.valueOf(session.securityLevel) }
                                .getOrDefault(ChatSecurityLevel.PRIVATE),
                            updatedAt = session.updatedAt,
                        )
                    }
                    _uiState.update { current ->
                        val active = tabs.firstOrNull { it.id == current.activeChatId } ?: tabs.firstOrNull()
                        current.copy(
                            chats = tabs,
                            activeChatId = active?.id ?: defaultId,
                            securityLevel = active?.securityLevel ?: ChatSecurityLevel.PRIVATE,
                        )
                    }
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
        nextPromptFromVoice = false
        _uiState.update { it.copy(prompt = value, promptError = null) }
        saveDraft(value)
        updateMessagesAndContext()
    }

    fun beginEdit(messageId: Long) {
        val message = entities.firstOrNull { it.id == messageId && it.role == MessageRole.USER.name } ?: return
        nextPromptFromVoice = false
        _uiState.update { it.copy(prompt = message.text, editingMessageId = messageId, promptError = null) }
        saveDraft(message.text)
    }

    fun cancelEdit() {
        _uiState.update { it.copy(prompt = "", editingMessageId = null, promptError = null) }
        saveDraft("")
    }

    fun onChatSearchChange(query: String) {
        _uiState.update { it.copy(chatSearchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(chatSearchResults = emptyList()) }
            return
        }
        val repository = sessionRepository ?: return
        searchJob = viewModelScope.launch(ioDispatcher) {
            val results = repository.search(query).map { row ->
                ChatTab(
                    id = row.id,
                    title = row.title,
                    securityLevel = runCatching { ChatSecurityLevel.valueOf(row.securityLevel) }
                        .getOrDefault(ChatSecurityLevel.PRIVATE),
                    updatedAt = row.updatedAt,
                    matchPreview = row.matchPreview,
                )
            }
            _uiState.update { current ->
                if (current.chatSearchQuery == query) current.copy(chatSearchResults = results) else current
            }
        }
    }

    fun onVoiceResult(value: String) {
        nextPromptFromVoice = true
        _uiState.update { it.copy(prompt = value, promptError = null) }
        saveDraft(value)
        updateMessagesAndContext()
    }
    fun onVoiceUnavailable() = showError("Voice recognition is not available on this device.")
    fun onVoiceEmpty() = showError("No speech was detected. Check the microphone and try again.")
    fun onVoiceCancelled() = showError("Voice typing was cancelled. Nothing was sent.")
    fun onVoicePermissionDenied() = showError("Microphone access was denied by the speech service.")
    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }
    fun setContextPanel(open: Boolean) = _uiState.update { it.copy(contextPanelOpen = open) }
    fun setPrivacyPanel(open: Boolean) = _uiState.update { it.copy(privacyPanelOpen = open) }
    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        preferences?.let { viewModelScope.launch(ioDispatcher) { it.setThemeMode(mode.name) } }
    }
    fun selectChat(id: Long) {
        if (busy || (id == _uiState.value.activeChatId)) return
        if (policyJob?.isActive == true) { showError("Saving context settings. Try switching chats again in a moment."); return }
        switchingChat = true
        failedRequestId = null
        failedPrompt = null
        entities = emptyList()
        historyRepository.selectChat(id)
        val selected = _uiState.value.chats.firstOrNull { it.id == id }
        _uiState.update {
            it.copy(
                activeChatId = id,
                securityLevel = selected?.securityLevel ?: ChatSecurityLevel.PRIVATE,
                prompt = "",
                customInstructions = "",
                errorMessage = null,
                promptError = null,
                editingMessageId = null,
                messages = emptyList(),
                canRetry = false,
            )
        }
        updateMessagesAndContext()
        viewModelScope.launch {
            try { loadChatPreferences(id) } finally { switchingChat = false }
        }
    }
    fun newChat() {
        if (busy) return
        if (sessionRepository == null) return
        switchingChat = true
        sessionRepository?.let { repository ->
            viewModelScope.launch {
                policyJob?.join()
                val id = repository.create()
                historyRepository.selectChat(id)
                entities = emptyList()
                failedRequestId = null
                failedPrompt = null
                _uiState.update { it.copy(activeChatId = id, securityLevel = ChatSecurityLevel.PRIVATE, messages = emptyList(), prompt = "", customInstructions = "", editingMessageId = null, errorMessage = null, promptError = null, canRetry = false) }
                try { loadChatPreferences(id) } finally { switchingChat = false }
            }
        }
    }
    fun renameChat(title: String) {
        sessionRepository?.let { repository -> viewModelScope.launch(ioDispatcher) { repository.rename(_uiState.value.activeChatId, title) } }
    }
    fun setSecurityLevel(level: ChatSecurityLevel) {
        if (busy) { showError("Wait for the current response before changing privacy."); return }
        val chatId = _uiState.value.activeChatId
        val previous = policyJob
        _uiState.update { it.copy(securityLevel = level) }
        sessionRepository?.let { repository ->
            policyJob = viewModelScope.launch {
                previous?.join()
                withContext(ioDispatcher) { repository.setSecurityLevel(chatId, level) }
                updateMessagesAndContext()
            }
        }
    }
    fun deleteActiveChat() {
        if (busy) { showError("Wait for the current response before deleting this chat."); return }
        sessionRepository?.let { repository ->
            switchingChat = true
            viewModelScope.launch {
                try {
                policyJob?.join()
                preferencesJob?.join()
                val current = _uiState.value.chats
                if (current.size <= 1) {
                    historyRepository.clear()
                    preferences?.clearUserPreferences(_uiState.value.activeChatId)
                    loadChatPreferences(_uiState.value.activeChatId)
                    return@launch
                }
                val deletedId = _uiState.value.activeChatId
                repository.delete(deletedId)
                preferences?.clearUserPreferences(deletedId)
                val next = current.first { it.id != deletedId }
                historyRepository.selectChat(next.id)
                _uiState.update { it.copy(activeChatId = next.id, securityLevel = next.securityLevel, prompt = "") }
                loadChatPreferences(next.id)
                } finally {
                    failedRequestId = null
                    failedPrompt = null
                    _uiState.update { it.copy(canRetry = false, editingMessageId = null) }
                    switchingChat = false
                }
            }
        }
    }

    fun setCustomInstructions(value: String) {
        if (busy) return
        _uiState.update { it.copy(customInstructions = value) }
        val chatId = _uiState.value.activeChatId
        val previous = preferencesJob
        preferencesJob = viewModelScope.launch {
            previous?.join()
            preferences?.setCustomInstructions(value, chatId)
        }
        updateMessagesAndContext()
    }

    fun resetCustomInstructions() {
        setCustomInstructions("")
    }
    fun setContextStatus(id: Long, status: ContextStatus) {
        changeHistory { historyRepository.setContextStatus(id, status) }
    }

    private fun changeHistory(action: suspend () -> Unit) {
        if (busy) { showError("Wait for the current response before changing context."); return }
        val previous = policyJob
        policyJob = viewModelScope.launch {
            previous?.join()
            withContext(ioDispatcher) { action() }
        }
    }
    fun clearHistory() = changeHistory { historyRepository.clear() }
    fun clearDraftAndInstructions() {
        if (busy) return
        val chatId = _uiState.value.activeChatId
        _uiState.update { it.copy(prompt = "", customInstructions = "") }
        val previous = preferencesJob
        preferencesJob = viewModelScope.launch {
            previous?.join()
            preferences?.clearUserPreferences(chatId)
        }
        updateMessagesAndContext()
    }
    fun clearEncryptedApiKey() { viewModelScope.launch(ioDispatcher) { apiKeyStore?.clear() } }
    fun deleteSummary() = changeHistory { historyRepository.deleteSummaries() }
    fun deleteMessage(id: Long) = changeHistory { historyRepository.deleteMessage(id) }
    fun selectVariant(messageId: Long, groupId: Long) = changeHistory { historyRepository.selectVariant(messageId, groupId) }

    fun regenerate(messageId: Long) {
        if (busy) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
            policyJob?.join()
            val snapshot = withContext(ioDispatcher) { historyRepository.snapshot() }
            val original = snapshot.firstOrNull { (it.id == messageId) && (it.role == MessageRole.MODEL.name) }
                ?: return@launch
            val userId = original.replyToId ?: return@launch
            val user = snapshot.firstOrNull { it.id == userId } ?: return@launch
            if (user.contextStatus == ContextStatus.EXCLUDED.name) {
                showError("Include the original prompt before regenerating its answer.")
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val historyWithoutTurn = snapshot.takeWhile { it.id != userId }
            val sharedMemory = crossChatMemory()
            val context = ContextAssembler.assemble(
                messages = sharedMemory + historyWithoutTurn,
                currentMessageId = null,
                currentText = user.text,
                customInstructions = _uiState.value.customInstructions,
            )
            if (context.protectedOverflow) {
                _uiState.update { it.copy(isLoading = false) }
                showError("This request exceeds the context budget. Shorten the prompt or unpin some messages.")
                return@launch
            }
            val request = ChatRequest(user.text, context.history, _uiState.value.customInstructions, user.id)
            when (val result = repository.generate(request)) {
                is GeminiResult.Success -> withContext(ioDispatcher) {
                    historyRepository.addVariant(original.id, result.text, context.toMetadata(original.wasVoicePrompt))
                }
                is GeminiResult.Failure -> showError(result.reason.userMessage)
            }
            } finally { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun retry() {
        val id = failedRequestId ?: return
        val prompt = failedPrompt ?: return
        if (busy) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
            policyJob?.join()
            if (withContext(ioDispatcher) { historyRepository.snapshot() }.none { it.id == id && it.contextStatus != ContextStatus.EXCLUDED.name }) {
                showError("This failed message was removed or hidden. Send a new prompt instead.")
                return@launch
            }
            executeRequest(id, prompt, isRetry = true)
            } finally { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun onSend() {
        val raw = _uiState.value.prompt.trim()
        if (raw.isEmpty()) {
            _uiState.update { it.copy(promptError = PromptError.EMPTY) }
            return
        }
        if (busy) return
        when (raw.lowercase()) {
            "/summarize" -> { onPromptChange(""); summarizeChat(); return }
            "/reset-instructions" -> {
                onPromptChange("")
                resetCustomInstructions()
                showError("Custom instructions reset.")
                return
            }
        }
        val prompt = if (raw.startsWith("/formal", ignoreCase = true)) {
            val content = raw.substring(7).trim()
            if (content.isEmpty()) {
                showError("Please provide a message after /formal")
                return
            }
            "Respond in a professional, formal style. $content"
        } else raw
        val editedMessageId = _uiState.value.editingMessageId
        _uiState.update { it.copy(prompt = "", editingMessageId = null, isLoading = true, errorMessage = null, promptError = null, canRetry = false) }
        saveDraft("")
        val voicePrompt = nextPromptFromVoice
        nextPromptFromVoice = false
        viewModelScope.launch {
            if (editedMessageId != null) {
                withContext(ioDispatcher) { historyRepository.setContextStatus(editedMessageId, ContextStatus.EXCLUDED) }
            }
            val id = withContext(ioDispatcher) { historyRepository.addPendingUser(prompt) }
            executeRequest(id, prompt, wasVoicePrompt = voicePrompt)
        }
    }

    fun summarizeChat() {
        if (busy) return
        _uiState.update { it.copy(isSummarizing = true, errorMessage = null) }
        viewModelScope.launch {
            policyJob?.join()
            val snapshot = withContext(ioDispatcher) { historyRepository.snapshot() }
            val allowed = ContextAssembler.eligibleMessages(snapshot)
            if (allowed.isEmpty()) {
                _uiState.update { it.copy(isSummarizing = false) }
                showError("There is no included chat content to summarize.")
                return@launch
            }
            _uiState.update { it.copy(isSummarizing = true, errorMessage = null) }
            val transcript = allowed.joinToString("\n") {
                (if (it.role == MessageRole.USER.name) "User: " else "Gemini: ") + it.text
            }
            val request = ChatRequest(
                currentMessage = "Summarize this conversation clearly. Preserve decisions and important details:\n\n$transcript",
                orderedHistory = emptyList(),
                requestId = -1,
            )
            if (ContextAssembler.assemble(emptyList(), null, request.currentMessage, "").protectedOverflow) {
                _uiState.update { it.copy(isSummarizing = false) }
                showError("The allowed chat is too large to summarize at once. Hide some older messages first.")
                return@launch
            }
            when (val result = repository.generate(request)) {
                is GeminiResult.Success -> withContext(ioDispatcher) { historyRepository.addSummary(result.text) }
                is GeminiResult.Failure -> showError(result.reason.userMessage)
            }
            _uiState.update { it.copy(isSummarizing = false) }
        }
    }

    private suspend fun executeRequest(id: Long, prompt: String, isRetry: Boolean = false, wasVoicePrompt: Boolean = false) {
        policyJob?.join()
        if (isRetry) withContext(ioDispatcher) { historyRepository.markPending(id) }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, canRetry = false) }
        val snapshot = withContext(ioDispatcher) { historyRepository.snapshot() }
        val sharedMemory = crossChatMemory()
        val context = ContextAssembler.assemble(sharedMemory + snapshot, id, prompt, _uiState.value.customInstructions)
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
                withContext(ioDispatcher) { historyRepository.complete(id, result.text, context.toMetadata(wasVoicePrompt)) }
                withContext(ioDispatcher) { sessionRepository?.touch(_uiState.value.activeChatId) }
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
        val variantGroups = entities.asSequence().filter { it.variantGroupId != null }
            .groupBy { it.variantGroupId!! }
            .mapValues { (_, values) -> values.sortedWith(compareBy(ChatMessageEntity::createdAt, ChatMessageEntity::id)) }
        val visibleEntities = entities.filter { (it.variantGroupId == null) || it.isSelectedVariant }
        _uiState.update { state ->
            state.copy(
                messages = visibleEntities.map { entity ->
                    val variants = entity.variantGroupId?.let { groupId -> variantGroups[groupId] }.orEmpty()
                    ChatMessage(
                        id = entity.id,
                        text = entity.text,
                        role = runCatching { MessageRole.valueOf(entity.role) }.getOrDefault(if (entity.isFromUser) MessageRole.USER else MessageRole.MODEL),
                        contextStatus = runCatching { ContextStatus.valueOf(entity.contextStatus) }.getOrDefault(ContextStatus.INCLUDED),
                        requestStatus = runCatching { RequestStatus.valueOf(entity.requestStatus) }.getOrDefault(RequestStatus.COMPLETE),
                        createdAt = entity.createdAt,
                        replyToId = entity.replyToId,
                        variantGroupId = entity.variantGroupId,
                        variantIndex = variants.indexOfFirst { it.id == entity.id }.takeIf { it >= 0 }?.plus(1) ?: 1,
                        variantCount = variants.size.coerceAtLeast(1),
                        variantIds = variants.map { it.id },
                        contextMessageCount = entity.contextMessageCount,
                        protectedUsedCount = entity.protectedUsedCount,
                        excludedAtRequestCount = entity.excludedAtRequestCount,
                        trimmedAtRequestCount = entity.trimmedAtRequestCount,
                        customInstructionsUsed = entity.customInstructionsUsed,
                        wasVoicePrompt = entity.wasVoicePrompt,
                    )
                },
                estimatedTokens = context.estimatedTokens,
                protectedCount = context.protectedCount,
                excludedCount = context.excludedCount,
                trimmedCount = context.trimmedCount,
            )
        }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            policyJob?.join()
            val chatId = _uiState.value.activeChatId
            val memory = crossChatMemory()
            val state = _uiState.value
            val preview = ContextAssembler.assemble(memory + entities, null, state.prompt, state.customInstructions)
            if (state.activeChatId == chatId) _uiState.update {
                it.copy(
                    estimatedTokens = preview.estimatedTokens,
                    trimmedCount = preview.trimmedCount,
                    protectedCount = preview.protectedCount,
                    nextContextIds = preview.selectedMessageIds,
                    crossChatMemoryCount = memory.count { item -> item.id in preview.selectedMessageIds },
                    memoryPreview = memory.filter { item -> item.id in preview.selectedMessageIds }.map { item -> item.text },
                    contextBlocked = preview.protectedOverflow,
                )
            }
        }
    }

    private suspend fun crossChatMemory(): List<ChatMessageEntity> = withContext(ioDispatcher) {
        sessionRepository?.crossChatMemory(_uiState.value.activeChatId, _uiState.value.securityLevel).orEmpty()
    }

    private fun showError(message: String) = _uiState.update { it.copy(errorMessage = message) }

    private fun ContextSnapshot.toMetadata(voice: Boolean) =
        ResponseMetadata(
            contextMessageCount = selectedMessageIds.size,
            protectedUsedCount = protectedCount,
            excludedAtRequestCount = excludedCount,
            trimmedAtRequestCount = trimmedCount,
            customInstructionsUsed = _uiState.value.customInstructions.isNotBlank(),
            wasVoicePrompt = voice,
        )

    companion object {
        fun factory(repository: GeminiRepository, historyRepository: ChatHistoryRepository, preferences: AppPreferences, apiKeyStore: ApiKeyStore, sessionRepository: RoomChatSessionRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(repository, historyRepository, preferences, apiKeyStore, sessionRepository) as T
            }
    }
}
