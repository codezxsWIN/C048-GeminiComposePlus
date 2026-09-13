package com.fahim.geminiApiComposeStarter.data

import com.fahim.geminiApiComposeStarter.data.local.ChatMessageDao
import com.fahim.geminiApiComposeStarter.data.local.ChatMessageEntity
import com.fahim.geminiApiComposeStarter.data.local.ContextStatus
import com.fahim.geminiApiComposeStarter.data.local.MessageRole
import com.fahim.geminiApiComposeStarter.data.local.RequestStatus
import kotlinx.coroutines.flow.Flow

interface ChatHistoryRepository {
    val messages: Flow<List<ChatMessageEntity>>
    suspend fun snapshot(): List<ChatMessageEntity>
    suspend fun addPendingUser(text: String): Long
    suspend fun complete(userMessageId: Long, response: String)
    suspend fun markPending(userMessageId: Long)
    suspend fun fail(userMessageId: Long)
    suspend fun addSummary(text: String)
    suspend fun deleteSummaries()
    suspend fun setContextStatus(id: Long, status: ContextStatus)
    suspend fun clear()
}

class RoomChatHistoryRepository(private val dao: ChatMessageDao) : ChatHistoryRepository {
    override val messages = dao.observeAll()
    override suspend fun snapshot() = dao.getAll()
    override suspend fun addPendingUser(text: String) = dao.insert(
        ChatMessageEntity(
            text = text,
            isFromUser = true,
            role = MessageRole.USER.name,
            requestStatus = RequestStatus.PENDING.name,
        ),
    )
    override suspend fun complete(userMessageId: Long, response: String) {
        dao.completeRequest(
            userMessageId,
            ChatMessageEntity(
                text = response,
                isFromUser = false,
                role = MessageRole.MODEL.name,
                requestStatus = RequestStatus.COMPLETE.name,
                replyToId = userMessageId,
            ),
        )
    }
    override suspend fun markPending(userMessageId: Long) =
        dao.updateRequestStatus(userMessageId, RequestStatus.PENDING.name)
    override suspend fun fail(userMessageId: Long) = dao.updateRequestStatus(userMessageId, RequestStatus.FAILED.name)
    override suspend fun addSummary(text: String) {
        dao.deleteSummaries()
        dao.insert(
            ChatMessageEntity(
                text = text,
                isFromUser = false,
                role = MessageRole.SUMMARY.name,
                contextStatus = ContextStatus.EXCLUDED.name,
            ),
        )
    }
    override suspend fun deleteSummaries() = dao.deleteSummaries()
    override suspend fun setContextStatus(id: Long, status: ContextStatus) = dao.updateContextStatus(id, status.name)
    override suspend fun clear() = dao.clear()
}
