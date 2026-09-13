package com.fahim.geminiApiComposeStarter.data

import com.fahim.geminiApiComposeStarter.data.local.ChatMessageDao
import com.fahim.geminiApiComposeStarter.data.local.ChatMessageEntity
import com.fahim.geminiApiComposeStarter.data.local.ContextStatus
import com.fahim.geminiApiComposeStarter.data.local.MessageRole
import com.fahim.geminiApiComposeStarter.data.local.RequestStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

interface ChatHistoryRepository {
    val messages: Flow<List<ChatMessageEntity>>
    fun selectChat(id: Long) = Unit
    suspend fun snapshot(): List<ChatMessageEntity>
    suspend fun addPendingUser(text: String): Long
    suspend fun complete(userMessageId: Long, response: String, metadata: ResponseMetadata = ResponseMetadata())
    suspend fun markPending(userMessageId: Long)
    suspend fun fail(userMessageId: Long)
    suspend fun addSummary(text: String)
    suspend fun deleteSummaries()
    suspend fun deleteMessage(id: Long)
    suspend fun addVariant(originalId: Long, response: String, metadata: ResponseMetadata)
    suspend fun selectVariant(messageId: Long, groupId: Long)
    suspend fun setContextStatus(id: Long, status: ContextStatus)
    suspend fun clear()
}

data class ResponseMetadata(
    val contextMessageCount: Int = 0,
    val protectedUsedCount: Int = 0,
    val excludedAtRequestCount: Int = 0,
    val trimmedAtRequestCount: Int = 0,
    val customInstructionsUsed: Boolean = false,
    val wasVoicePrompt: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoomChatHistoryRepository(private val dao: ChatMessageDao, initialChatId: Long = 1) : ChatHistoryRepository {
    private val activeChatId = MutableStateFlow(initialChatId)
    private val chatId get() = activeChatId.value
    override val messages = activeChatId.flatMapLatest { dao.observeAll(it) }
    override fun selectChat(id: Long) { activeChatId.value = id }
    override suspend fun snapshot() = dao.getAll(chatId)
    override suspend fun addPendingUser(text: String) = dao.insert(
        ChatMessageEntity(
            text = text,
            isFromUser = true,
            role = MessageRole.USER.name,
            requestStatus = RequestStatus.PENDING.name,
            chatId = chatId,
        ),
    )
    override suspend fun complete(userMessageId: Long, response: String, metadata: ResponseMetadata) {
        dao.completeRequest(
            userMessageId,
            ChatMessageEntity(
                text = response,
                isFromUser = false,
                role = MessageRole.MODEL.name,
                requestStatus = RequestStatus.COMPLETE.name,
                replyToId = userMessageId,
                contextMessageCount = metadata.contextMessageCount,
                protectedUsedCount = metadata.protectedUsedCount,
                excludedAtRequestCount = metadata.excludedAtRequestCount,
                trimmedAtRequestCount = metadata.trimmedAtRequestCount,
                customInstructionsUsed = metadata.customInstructionsUsed,
                wasVoicePrompt = metadata.wasVoicePrompt,
                chatId = chatId,
            ),
            chatId,
        )
    }
    override suspend fun markPending(userMessageId: Long) =
        dao.updateRequestStatus(userMessageId, RequestStatus.PENDING.name)
    override suspend fun fail(userMessageId: Long) = dao.updateRequestStatus(userMessageId, RequestStatus.FAILED.name)
    override suspend fun addSummary(text: String) {
        dao.deleteSummaries(chatId)
        dao.insert(
            ChatMessageEntity(
                text = text,
                isFromUser = false,
                role = MessageRole.SUMMARY.name,
                contextStatus = ContextStatus.EXCLUDED.name,
                chatId = chatId,
            ),
        )
    }
    override suspend fun deleteSummaries() = dao.deleteSummaries(chatId)
    override suspend fun deleteMessage(id: Long) = dao.deleteMessageThread(chatId, id)
    override suspend fun addVariant(originalId: Long, response: String, metadata: ResponseMetadata) {
        val original = dao.getAll(chatId).first { it.id == originalId }
        val groupId = original.variantGroupId ?: original.id
        dao.addVariant(
            originalId = original.id,
            groupId = groupId,
            response = original.copy(
                id = 0,
                text = response,
                createdAt = System.currentTimeMillis(),
                variantGroupId = groupId,
                isSelectedVariant = true,
                contextMessageCount = metadata.contextMessageCount,
                protectedUsedCount = metadata.protectedUsedCount,
                excludedAtRequestCount = metadata.excludedAtRequestCount,
                trimmedAtRequestCount = metadata.trimmedAtRequestCount,
                customInstructionsUsed = metadata.customInstructionsUsed,
                wasVoicePrompt = metadata.wasVoicePrompt,
                chatId = chatId,
            ),
            chatId,
        )
    }
    override suspend fun selectVariant(messageId: Long, groupId: Long) = dao.selectVariant(messageId, groupId)
    override suspend fun setContextStatus(id: Long, status: ContextStatus) = dao.updateContextStatus(id, status.name)
    override suspend fun clear() = dao.clear(chatId)
}
