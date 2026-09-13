package com.fahim.geminiApiComposeStarter.data

import com.fahim.geminiApiComposeStarter.data.local.*
import org.junit.Assert.*
import org.junit.Test

class ContextAssemblerTest {
    @Test fun excludesFirewallSummaryFailedAndCurrentMessages() {
        val messages = listOf(
            message(1, "allowed"),
            message(2, "private", ContextStatus.EXCLUDED),
            message(3, "summary", role = MessageRole.SUMMARY),
            message(4, "failed", request = RequestStatus.FAILED),
            message(5, "current"),
        )
        val result = ContextAssembler.assemble(messages, 5, "current", "")
        assertEquals(listOf("allowed"), result.history.map { it.text })
        assertEquals(1, result.excludedCount)
    }

    @Test fun protectedMessagesSurviveOrdinaryTrimming() {
        val huge = "x".repeat(80_000)
        val result = ContextAssembler.assemble(
            listOf(message(1, "must survive", ContextStatus.PROTECTED), message(2, huge)),
            null, "question", "",
        )
        assertTrue(result.history.any { it.text == "must survive" })
        assertTrue(result.trimmedCount > 0)
    }

    @Test fun protectedOverflowBlocksRequest() {
        val result = ContextAssembler.assemble(
            listOf(message(1, "x".repeat(72_001), ContextStatus.PROTECTED)),
            null, "question", "",
        )
        assertTrue(result.protectedOverflow)
    }

    @Test fun orphanModelIsDroppedAndConsecutiveRolesAreNormalized() {
        val result = ContextAssembler.assemble(
            listOf(
                message(1, "hidden question", ContextStatus.EXCLUDED),
                message(2, "orphan answer", role = MessageRole.MODEL),
                message(3, "first user detail"),
                message(4, "second user detail"),
                message(5, "valid answer", role = MessageRole.MODEL),
            ),
            null, "next question", "",
        )

        assertEquals(listOf(ChatRole.USER, ChatRole.MODEL), result.history.map { it.role })
        assertFalse(result.history.any { "orphan answer" in it.text })
        assertEquals("first user detail\n\nsecond user detail", result.history.first().text)
        assertTrue(result.trimmedCount >= 1)
    }

    private fun message(
        id: Long,
        text: String,
        status: ContextStatus = ContextStatus.INCLUDED,
        role: MessageRole = MessageRole.USER,
        request: RequestStatus = RequestStatus.COMPLETE,
    ) = ChatMessageEntity(id, text, role == MessageRole.USER, id, role.name, status.name, request.name)
}
