package com.fahim.geminiApiComposeStarter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET requestStatus = :status WHERE id = :id")
    suspend fun updateRequestStatus(id: Long, status: String)

    @Query("UPDATE chat_messages SET contextStatus = :status WHERE id = :id")
    suspend fun updateContextStatus(id: Long, status: String)

    @Query("DELETE FROM chat_messages WHERE role = 'SUMMARY'")
    suspend fun deleteSummaries()

    @Query("DELETE FROM chat_messages")
    suspend fun clear()

    @Transaction
    suspend fun completeRequest(userMessageId: Long, response: ChatMessageEntity) {
        insert(response)
        updateRequestStatus(userMessageId, RequestStatus.COMPLETE.name)
    }
}
