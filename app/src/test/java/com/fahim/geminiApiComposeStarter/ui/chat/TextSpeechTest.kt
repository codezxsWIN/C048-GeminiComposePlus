package com.fahim.geminiApiComposeStarter.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSpeechTest {
    @Test
    fun longAnswerIsSplitIntoSafeOrderedChunks() {
        val answer = (1..180).joinToString(" ") { "Sentence $it explains a useful detail." }

        val chunks = answer.toSpeechChunks(maximumChunkLength = 500)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 500 })
        assertEquals(
            answer.replace(Regex("\\s+"), " ").trim(),
            chunks.joinToString(" ").replace(Regex("\\s+"), " ").trim(),
        )
    }

    @Test
    fun markdownDecorationIsNotReadLiterally() {
        val chunks = "## Heading\n**Important** answer".toSpeechChunks(maximumChunkLength = 500)

        assertEquals(listOf("Heading\nImportant answer"), chunks)
    }
}
