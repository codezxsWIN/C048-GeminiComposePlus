package com.fahim.geminiApiComposeStarter.data

import com.fahim.geminiApiComposeStarter.data.local.*
import org.junit.Assert.*
import org.junit.Test

class ContextAssemblerTest {
    @Test fun hidingPromptAlsoExcludesItsReplyEvenWithEarlierAllowedTurns() {
        val input = listOf(message(1, "public"), message(2, "secret", ContextStatus.EXCLUDED),
            message(3, "echo of secret", role = MessageRole.MODEL).copy(replyToId = 2))
        val result = ContextAssembler.assemble(input, null, "next", "")
        assertEquals(setOf(1L), result.selectedMessageIds)
        assertFalse(result.history.any { "secret" in it.text })
        assertEquals(listOf(1L), ContextAssembler.eligibleMessages(input).map { it.id })
    }

    @Test fun unselectedVariantsAndUnknownPrivacyValuesFailClosed() {
        val result = ContextAssembler.eligibleMessages(listOf(
            message(1, "allowed"),
            message(2, "old answer", role = MessageRole.MODEL).copy(isSelectedVariant = false),
            message(3, "invalid").copy(contextStatus = "UNKNOWN"),
        ))
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test fun trimmingParentAlsoDropsItsReply() {
        val result = ContextAssembler.assemble(listOf(message(1, "start"), message(2, "x".repeat(80000)),
            message(3, "dependent answer", role = MessageRole.MODEL).copy(replyToId = 2)), null, "next", "")
        assertEquals(setOf(1L), result.selectedMessageIds)
    }

    @Test fun oversizedPromptBlocksEvenWithoutHistory() {
        assertTrue(ContextAssembler.assemble(emptyList(), null, "x".repeat(72001), "").protectedOverflow)
    }

    @Test fun normalizedHistoryRetainsAllSourceIdsForAudit() {
        val result = ContextAssembler.assemble(listOf(message(1, "one"), message(2, "two")), null, "next", "")
        assertEquals(1, result.history.size)
        assertEquals(setOf(1L, 2L), result.selectedMessageIds)
    }

    @Test fun pinningAnswerReservesItsParentBeforeNewerOrdinaryMessages() {
        val result = ContextAssembler.assemble(listOf(
            message(1, "Parent question"),
            message(2, "Pinned answer", ContextStatus.PROTECTED, MessageRole.MODEL).copy(replyToId = 1),
            message(3, "x".repeat(71970)),
        ), null, "next", "")
        assertFalse(result.protectedOverflow)
        assertEquals(setOf(1L, 2L), result.selectedMessageIds)
    }
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
