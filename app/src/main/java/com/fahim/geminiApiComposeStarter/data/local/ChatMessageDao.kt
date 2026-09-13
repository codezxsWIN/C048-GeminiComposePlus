package com.fahim.geminiApiComposeStarter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    fun observeAll(chatId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(chatId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET requestStatus = :status WHERE id = :id")
    suspend fun updateRequestStatus(id: Long, status: String)

    @Query("UPDATE chat_messages SET contextStatus = :status WHERE id = :id")
    suspend fun updateContextStatus(id: Long, status: String)

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId AND role = 'SUMMARY'")
    suspend fun deleteSummaries(chatId: Long)

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId AND (id = :id OR replyToId = :id)")
    suspend fun deleteMessageThread(chatId: Long, id: Long)

    @Query("UPDATE chat_messages SET variantGroupId = :groupId WHERE id = :messageId")
    suspend fun assignVariantGroup(messageId: Long, groupId: Long)

    @Query("UPDATE chat_messages SET isSelectedVariant = CASE WHEN id = :messageId THEN 1 ELSE 0 END WHERE variantGroupId = :groupId")
    suspend fun selectVariant(messageId: Long, groupId: Long)

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun clear(chatId: Long)

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun deleteChatMessages(chatId: Long)

    @Transaction
    suspend fun completeRequest(userMessageId: Long, response: ChatMessageEntity, chatId: Long) {
        insert(response)
        updateRequestStatus(userMessageId, RequestStatus.COMPLETE.name)
    }

    @Transaction
    suspend fun addVariant(originalId: Long, groupId: Long, response: ChatMessageEntity, chatId: Long) {
        assignVariantGroup(originalId, groupId)
        val insertedId = insert(response)
        selectVariant(insertedId, groupId)
    }
}
