package com.fahim.geminiApiComposeStarter.data

import com.fahim.geminiApiComposeStarter.data.local.ChatMessageDao
import com.fahim.geminiApiComposeStarter.data.local.ChatMessageEntity
import com.fahim.geminiApiComposeStarter.data.local.ChatSecurityLevel
import com.fahim.geminiApiComposeStarter.data.local.ChatSessionDao
import com.fahim.geminiApiComposeStarter.data.local.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

class RoomChatSessionRepository(
    private val dao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
) {
    val sessions: Flow<List<ChatSessionEntity>> = dao.observeAll()

    suspend fun ensureDefault(): Long {
        val existing = dao.getAll()
        if (existing.isNotEmpty()) return existing.first().id
        return dao.insert(ChatSessionEntity(title = "General chat", securityLevel = ChatSecurityLevel.PRIVATE.name))
    }

    suspend fun create(title: String = "New chat"): Long = dao.insert(ChatSessionEntity(title = title))

    suspend fun rename(id: Long, title: String) {
        dao.updateTitle(id, title.trim().ifBlank { "New chat" })
    }

    suspend fun setSecurityLevel(id: Long, level: ChatSecurityLevel) {
        dao.updateSecurityLevel(id, level.name)
    }

    suspend fun touch(id: Long) = dao.touch(id)

    suspend fun crossChatMemory(currentChatId: Long, currentLevel: ChatSecurityLevel): List<ChatMessageEntity> {
        if (currentLevel == ChatSecurityLevel.CONFIDENTIAL) return emptyList()
        return dao.getCrossChatMemory(currentChatId)
            .asReversed()
            .map { it.copy(text = "Previously shared detail: ${it.text}") }
    }

    suspend fun delete(id: Long) {
        messageDao.deleteChatMessages(id)
        dao.delete(id)
    }
}
