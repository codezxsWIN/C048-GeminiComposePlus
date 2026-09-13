package com.fahim.geminiApiComposeStarter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageRole { USER, MODEL, SUMMARY }
enum class ContextStatus { INCLUDED, EXCLUDED, PROTECTED }
enum class RequestStatus { PENDING, COMPLETE, FAILED }

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
)
