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

    @Test fun emptyPromptShowsValidationError() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel(GeminiResult.Success("unused"))
        viewModel.onSend()
        assertEquals(PromptError.EMPTY, viewModel.uiState.value.promptError)
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
    override suspend fun complete(userMessageId: Long, response: String) {
        messages.value = messages.value.map { if (it.id == userMessageId) it.copy(requestStatus = RequestStatus.COMPLETE.name) else it }
        messages.value += ChatMessageEntity(nextId++, response, false, nextId, role = MessageRole.MODEL.name, replyToId = userMessageId)
    }
    override suspend fun markPending(userMessageId: Long) {
        messages.value = messages.value.map { if (it.id == userMessageId) it.copy(requestStatus = RequestStatus.PENDING.name) else it }
    }
    override suspend fun fail(userMessageId: Long) {
        messages.value = messages.value.map { if (it.id == userMessageId) it.copy(requestStatus = RequestStatus.FAILED.name) else it }
    }
    override suspend fun addSummary(text: String) { messages.value += ChatMessageEntity(nextId++, text, false, role = MessageRole.SUMMARY.name) }
    override suspend fun deleteSummaries() { messages.value = messages.value.filter { it.role != MessageRole.SUMMARY.name } }
    override suspend fun setContextStatus(id: Long, status: ContextStatus) {
        messages.value = messages.value.map { if (it.id == id) it.copy(contextStatus = status.name) else it }
    }
    override suspend fun clear() { messages.value = emptyList() }
}
