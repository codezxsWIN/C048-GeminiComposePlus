package com.fahim.geminiApiComposeStarter.ui.chat

import com.fahim.geminiApiComposeStarter.data.*
import com.fahim.geminiApiComposeStarter.data.local.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun summaryCannotLeakHiddenPromptReplyOrUnselectedVariant() = runTest(mainDispatcherRule.testDispatcher) {
        val history = FakeHistoryRepository()
        history.messages.value = listOf(
            ChatMessageEntity(id = 1, text = "Allowed", isFromUser = true),
            ChatMessageEntity(id = 2, text = "Secret", isFromUser = true, contextStatus = "EXCLUDED"),
            ChatMessageEntity(id = 3, text = "Secret echo", isFromUser = false, replyToId = 2),
            ChatMessageEntity(id = 4, text = "Old variant", isFromUser = false, replyToId = 1, isSelectedVariant = false),
        )
        var captured: ChatRequest? = null
        val model = ChatViewModel(object : GeminiRepository {
            override suspend fun generate(request: ChatRequest): GeminiResult {
                captured = request
                return GeminiResult.Success("Summary")
            }
        }, history, ioDispatcher = mainDispatcherRule.testDispatcher)
        advanceUntilIdle()
        model.summarizeChat()
        advanceUntilIdle()
        assertTrue(captured!!.currentMessage.contains("Allowed"))
        assertFalse(captured!!.currentMessage.contains("Secret"))
        assertFalse(captured!!.currentMessage.contains("Old variant"))
        assertFalse(model.uiState.value.isSummarizing)
    }

    @Test fun chatCannotSwitchWhileSummaryIsRunning() = runTest(mainDispatcherRule.testDispatcher) {
        val history = FakeHistoryRepository()
        history.messages.value = listOf(ChatMessageEntity(id = 1, text = "Allowed", isFromUser = true))
        val gate = kotlinx.coroutines.CompletableDeferred<GeminiResult>()
        val model = ChatViewModel(object : GeminiRepository {
            override suspend fun generate(request: ChatRequest) = gate.await()
        }, history, ioDispatcher = mainDispatcherRule.testDispatcher)
        advanceUntilIdle()
        model.summarizeChat()
        model.selectChat(2)
        assertEquals(1L, model.uiState.value.activeChatId)
        gate.complete(GeminiResult.Success("Summary"))
        advanceUntilIdle()
    }

    @Test fun emptyPromptShowsValidationError() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel(GeminiResult.Success("unused"))
        viewModel.onSend()
        assertEquals(PromptError.EMPTY, viewModel.uiState.value.promptError)
    }

    @Test fun voiceResultBecomesEditableDraftAndCancellationIsExplained() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel(GeminiResult.Success("unused"))
        viewModel.onVoiceResult("spoken draft")
        assertEquals("spoken draft", viewModel.uiState.value.prompt)

        viewModel.onVoiceCancelled()
        assertEquals("Voice typing was cancelled. Nothing was sent.", viewModel.uiState.value.errorMessage)
    }

    @Test fun successfulRequestPersistsBothMessagesAndSendsPromptOnce() = runTest(mainDispatcherRule.testDispatcher) {
        val history = FakeHistoryRepository()
        var captured: ChatRequest? = null
        val viewModel = ChatViewModel(
            repository = object : GeminiRepository {
                override suspend fun generate(request: ChatRequest): GeminiResult {
                    captured = request
                    return GeminiResult.Success("Hello from Gemini")
                }
            },
            historyRepository = history,
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )
        viewModel.onPromptChange("Hello")
        viewModel.onSend()
        advanceUntilIdle()
        assertEquals(listOf("Hello", "Hello from Gemini"), history.messages.value.map { it.text })
        assertEquals("Hello", captured?.currentMessage)
        assertFalse(captured!!.orderedHistory.any { it.text == "Hello" })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test fun failedRequestCanRetryWithoutDuplicateUserMessage() = runTest(mainDispatcherRule.testDispatcher) {
        val history = FakeHistoryRepository()
        var calls = 0
        val viewModel = ChatViewModel(
            repository = object : GeminiRepository {
                override suspend fun generate(request: ChatRequest): GeminiResult =
                    if (calls++ == 0) GeminiResult.Failure(GeminiFailure.Offline) else GeminiResult.Success("Recovered")
            },
            historyRepository = history,
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )
        viewModel.onPromptChange("Hello")
        viewModel.onSend()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canRetry)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(1, history.messages.value.count { it.isFromUser })
        assertEquals("Recovered", history.messages.value.last().text)
    }

    @Test fun regenerateKeepsAlternativesAndSelectsTheNewAnswer() = runTest(mainDispatcherRule.testDispatcher) {
        val history = FakeHistoryRepository()
        var calls = 0
        val viewModel = ChatViewModel(
            repository = object : GeminiRepository {
                override suspend fun generate(request: ChatRequest) =
                    GeminiResult.Success(if (calls++ == 0) "First answer" else "Alternative answer")
            },
            historyRepository = history,
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )
        viewModel.onPromptChange("Explain this")
        viewModel.onSend()
        advanceUntilIdle()

        val originalId = viewModel.uiState.value.messages.first { it.role == MessageRole.MODEL }.id
        viewModel.regenerate(originalId)
        advanceUntilIdle()

        val visible = viewModel.uiState.value.messages.first { it.role == MessageRole.MODEL }
        assertEquals("Alternative answer", visible.text)
        assertEquals(2, visible.variantCount)
        assertEquals(2, visible.variantIndex)
    }

    @Test fun editAndResendKeepsOriginalAndCreatesACorrectedTurn() = runTest(mainDispatcherRule.testDispatcher) {
        val history = FakeHistoryRepository()
        val viewModel = createViewModel(GeminiResult.Success("Answer"), history)
        viewModel.onPromptChange("Original question")
        viewModel.onSend()
        advanceUntilIdle()

        val original = viewModel.uiState.value.messages.first { it.isFromUser }
        viewModel.beginEdit(original.id)
        assertEquals(original.id, viewModel.uiState.value.editingMessageId)
        assertEquals("Original question", viewModel.uiState.value.prompt)

        viewModel.onPromptChange("Corrected question")
        viewModel.onSend()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editingMessageId)
        assertEquals(
            listOf("Original question", "Corrected question"),
            history.messages.value.filter { it.isFromUser }.map { it.text },
        )
        assertEquals(ContextStatus.EXCLUDED.name, history.messages.value.first { it.id == original.id }.contextStatus)
    }

    private fun createViewModel(result: GeminiResult, history: FakeHistoryRepository = FakeHistoryRepository()) =
        ChatViewModel(
            repository = object : GeminiRepository { override suspend fun generate(request: ChatRequest) = result },
            historyRepository = history,
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )
}

private class FakeHistoryRepository : ChatHistoryRepository {
    override val messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var nextId = 1L
    override suspend fun snapshot() = messages.value
    override suspend fun addPendingUser(text: String): Long {
        val id = nextId++
        messages.value += ChatMessageEntity(id, text, true, id, requestStatus = RequestStatus.PENDING.name)
        return id
    }
    override suspend fun complete(userMessageId: Long, response: String, metadata: ResponseMetadata) {
        messages.value = messages.value.map { if (it.id == userMessageId) it.copy(requestStatus = RequestStatus.COMPLETE.name) else it }
        messages.value += ChatMessageEntity(
            id = nextId++, text = response, isFromUser = false, createdAt = nextId,
            role = MessageRole.MODEL.name, replyToId = userMessageId,
            contextMessageCount = metadata.contextMessageCount,
            protectedUsedCount = metadata.protectedUsedCount,
            excludedAtRequestCount = metadata.excludedAtRequestCount,
            trimmedAtRequestCount = metadata.trimmedAtRequestCount,
            customInstructionsUsed = metadata.customInstructionsUsed,
            wasVoicePrompt = metadata.wasVoicePrompt,
        )
    }
    override suspend fun markPending(userMessageId: Long) {
        messages.value = messages.value.map { if (it.id == userMessageId) it.copy(requestStatus = RequestStatus.PENDING.name) else it }
    }
    override suspend fun fail(userMessageId: Long) {
        messages.value = messages.value.map { if (it.id == userMessageId) it.copy(requestStatus = RequestStatus.FAILED.name) else it }
    }
    override suspend fun addSummary(text: String) { messages.value += ChatMessageEntity(nextId++, text, false, role = MessageRole.SUMMARY.name) }
    override suspend fun deleteSummaries() { messages.value = messages.value.filter { it.role != MessageRole.SUMMARY.name } }
    override suspend fun deleteMessage(id: Long) {
        messages.value = messages.value.filterNot { it.id == id || it.replyToId == id }
    }
    override suspend fun addVariant(originalId: Long, response: String, metadata: ResponseMetadata) {
        val original = messages.value.first { it.id == originalId }
        val groupId = original.variantGroupId ?: original.id
        messages.value = messages.value.map {
            if (it.id == originalId) it.copy(variantGroupId = groupId, isSelectedVariant = false) else it
        } + original.copy(
            id = nextId++, text = response, createdAt = nextId, variantGroupId = groupId, isSelectedVariant = true,
            contextMessageCount = metadata.contextMessageCount,
            protectedUsedCount = metadata.protectedUsedCount,
            excludedAtRequestCount = metadata.excludedAtRequestCount,
            trimmedAtRequestCount = metadata.trimmedAtRequestCount,
            customInstructionsUsed = metadata.customInstructionsUsed,
            wasVoicePrompt = metadata.wasVoicePrompt,
        )
    }
    override suspend fun selectVariant(messageId: Long, groupId: Long) {
        messages.value = messages.value.map {
            if (it.variantGroupId == groupId) it.copy(isSelectedVariant = it.id == messageId) else it
        }
    }
    override suspend fun setContextStatus(id: Long, status: ContextStatus) {
        messages.value = messages.value.map { if (it.id == id) it.copy(contextStatus = status.name) else it }
    }
    override suspend fun clear() { messages.value = emptyList() }
}
