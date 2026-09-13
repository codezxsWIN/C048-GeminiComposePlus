package com.fahim.geminiApiComposeStarter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageRole { USER, MODEL, SUMMARY }
enum class ContextStatus { INCLUDED, EXCLUDED, PROTECTED }
enum class RequestStatus { PENDING, COMPLETE, FAILED }
enum class ChatSecurityLevel { OPEN, PRIVATE, CONFIDENTIAL }

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New chat",
    val securityLevel: String = ChatSecurityLevel.PRIVATE.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val isFromUser: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val role: String = if (isFromUser) MessageRole.USER.name else MessageRole.MODEL.name,
    val contextStatus: String = ContextStatus.INCLUDED.name,
    val requestStatus: String = RequestStatus.COMPLETE.name,
    val replyToId: Long? = null,
    val variantGroupId: Long? = null,
    val isSelectedVariant: Boolean = true,
    val contextMessageCount: Int = 0,
    val protectedUsedCount: Int = 0,
    val excludedAtRequestCount: Int = 0,
    val trimmedAtRequestCount: Int = 0,
    val customInstructionsUsed: Boolean = false,
    val wasVoicePrompt: Boolean = false,
    val chatId: Long = 1,
)
