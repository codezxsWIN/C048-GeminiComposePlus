package com.fahim.geminiApiComposeStarter.ui.chat

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.fahim.geminiApiComposeStarter.data.local.ContextStatus
import com.fahim.geminiApiComposeStarter.data.local.MessageRole
import com.fahim.geminiApiComposeStarter.data.local.RequestStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun messagesLoadingAndContextHealthAreVisible() {
        composeRule.setContent {
            TestScreen(
                ChatUiState(
                    messages = listOf(
                        ChatMessage(1, "Question", MessageRole.USER, ContextStatus.PROTECTED, RequestStatus.COMPLETE, 1),
                        ChatMessage(2, "Answer", MessageRole.MODEL, ContextStatus.INCLUDED, RequestStatus.COMPLETE, 2),
                    ),
                    isLoading = true,
                ),
            )
        }
        composeRule.onNodeWithTag("user_message").assertIsDisplayed()
        composeRule.onNodeWithTag("gemini_message").assertIsDisplayed()
        composeRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("context_health").assertIsDisplayed()
    }

    @Test fun promptAndSendCallbacksAreHoisted() {
        var prompt = ""
        var sends = 0
        composeRule.setContent { TestScreen(ChatUiState(), { prompt = it }, { sends++ }) }
        composeRule.onNodeWithTag("prompt_field").performTextInput("Hello")
        composeRule.onNodeWithTag("send_button").performClick()
        assertEquals("Hello", prompt)
        assertEquals(1, sends)
    }

    @Composable
    private fun TestScreen(state: ChatUiState, onPrompt: (String) -> Unit = {}, onSend: () -> Unit = {}) {
        ChatScreen(
            state = state,
            widthSizeClass = WindowWidthSizeClass.Compact,
            onPromptChange = onPrompt,
            onSend = onSend,
            onRetry = {},
            onClear = {},
            onErrorShown = {},
            onContextPanel = {},
            onPrivacyPanel = {},
            onContextStatus = { _, _ -> },
            onInstructionsChange = {},
            onResetInstructions = {},
            onSummarize = {},
            onDeleteSummary = {},
            onClearPreferences = {},
            onClearApiKey = {},
            onVoiceInput = {},
        )
    }
}
