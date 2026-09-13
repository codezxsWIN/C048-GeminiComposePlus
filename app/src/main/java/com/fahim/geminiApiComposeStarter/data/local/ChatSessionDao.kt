package com.fahim.geminiApiComposeStarter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC, id DESC")
    suspend fun getAll(): List<ChatSessionEntity>

    @Insert
    suspend fun insert(session: ChatSessionEntity): Long

    @Update
    suspend fun update(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET securityLevel = :securityLevel, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSecurityLevel(id: Long, securityLevel: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT m.* FROM chat_messages AS m
        INNER JOIN chat_sessions AS s ON s.id = m.chatId
        WHERE m.chatId != :currentChatId
          AND m.role = 'USER'
          AND m.requestStatus = 'COMPLETE'
          AND m.contextStatus != 'EXCLUDED'
          AND (
              s.securityLevel = 'OPEN'
              OR (s.securityLevel = 'PRIVATE' AND m.contextStatus = 'PROTECTED')
          )
        ORDER BY m.createdAt DESC, m.id DESC
        LIMIT :limit
        """,
    )
    suspend fun getCrossChatMemory(currentChatId: Long, limit: Int = 24): List<ChatMessageEntity>
}
