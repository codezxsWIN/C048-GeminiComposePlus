package com.fahim.geminiApiComposeStarter.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.fahim.geminiApiComposeStarter.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PrivacyDatabaseTest {
    @Test fun memoryRespectsBothChatPoliciesAndRevocation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, ChatDatabase::class.java,
        ).build()
        try {
            val chats = db.chatSessionDao()
            val messages = db.chatMessageDao()
            val destination = chats.insert(ChatSessionEntity(title = "Destination"))
            val source = chats.insert(ChatSessionEntity(title = "Source", securityLevel = "OPEN"))
            val detail = messages.insert(ChatMessageEntity(text = "Synthetic test detail", isFromUser = true, chatId = source))
            assertEquals(listOf(detail), chats.getCrossChatMemory(destination).map { it.id })
            chats.updateSecurityLevel(source, "PRIVATE")
            assertTrue(chats.getCrossChatMemory(destination).isEmpty())
            messages.updateContextStatus(detail, "PROTECTED")
            assertEquals(listOf(detail), chats.getCrossChatMemory(destination).map { it.id })
            chats.updateSecurityLevel(destination, "CONFIDENTIAL")
            // Even a caller with stale UI policy cannot import into a confidential chat.
            assertTrue(RoomChatSessionRepository(chats, messages).crossChatMemory(destination, ChatSecurityLevel.OPEN).isEmpty())
            chats.updateSecurityLevel(destination, "PRIVATE")
            chats.updateSecurityLevel(source, "CONFIDENTIAL")
            assertTrue(chats.getCrossChatMemory(destination).isEmpty())
            chats.updateSecurityLevel(source, "OPEN")
            messages.updateContextStatus(detail, "EXCLUDED")
            assertTrue(chats.getCrossChatMemory(destination).isEmpty())
            messages.updateContextStatus(detail, "UNKNOWN")
            assertTrue(chats.getCrossChatMemory(destination).isEmpty())
            messages.updateContextStatus(detail, "INCLUDED")
            messages.updateRequestStatus(detail, "FAILED")
            assertTrue(chats.getCrossChatMemory(destination).isEmpty())
        } finally { db.close() }
    }
}
