package com.fahim.geminiApiComposeStarter.ui.chat

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.fahim.geminiApiComposeStarter.data.local.ContextStatus
import com.fahim.geminiApiComposeStarter.data.local.ChatSecurityLevel
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

    @Test fun answerInsightsExplainCapabilitiesWithoutInventingConfidence() {
        composeRule.setContent {
            TestScreen(
                ChatUiState(
                    messages = listOf(
                        ChatMessage(1, "Question", MessageRole.USER, ContextStatus.INCLUDED, RequestStatus.COMPLETE, 1),
                        ChatMessage(
                            2, "Answer", MessageRole.MODEL, ContextStatus.INCLUDED, RequestStatus.COMPLETE, 2,
                            contextMessageCount = 1, excludedAtRequestCount = 1,
                        ),
                    ),
                ),
            )
        }
        composeRule.onNodeWithContentDescription("Answer insights").performClick()
        composeRule.onNodeWithTag("answer_insights").assertIsDisplayed()
        composeRule.onNodeWithText("Unverified").assertIsDisplayed()
        composeRule.onNodeWithText("Live web not used").assertIsDisplayed()
    }

    @Test fun contextDrawerCanExcludeAndProtectMessagesDirectly() {
        var changed: Pair<Long, ContextStatus>? = null
        composeRule.setContent {
            TestScreen(
                ChatUiState(
                    contextPanelOpen = true,
                    messages = listOf(
                        ChatMessage(41, "Private detail", MessageRole.USER, ContextStatus.INCLUDED, RequestStatus.COMPLETE, 1),
                    ),
                ),
                onContextStatus = { id, status -> changed = id to status },
            )
        }
        composeRule.onNodeWithTag("context_exclude_41").performClick()
        assertEquals(41L to ContextStatus.EXCLUDED, changed)
    }

    @Test fun visibleVoiceTypingControlUsesHoistedCallback() {
        var launches = 0
        composeRule.setContent { TestScreen(ChatUiState(), onVoice = { launches++ }) }

        composeRule.onNodeWithTag("voice_button").assertIsDisplayed().performClick()
        assertEquals(1, launches)
    }

    @Test fun conversationDrawerCreatesAndSwitchesChats() {
        var created = 0
        var selected = 0L
        composeRule.setContent {
            TestScreen(
                ChatUiState(
                    chats = listOf(
                        ChatTab(1, "General chat", ChatSecurityLevel.PRIVATE),
                        ChatTab(2, "Project ideas", ChatSecurityLevel.CONFIDENTIAL),
                    ),
                ),
                onNewChat = { created++ },
                onSelectChat = { selected = it },
            )
        }
        composeRule.onNodeWithContentDescription("Open chats").performClick()
        composeRule.onNodeWithText("Start a new chat").assertIsDisplayed().performClick()
        assertEquals(1, created)

        composeRule.onNodeWithContentDescription("Open chats").performClick()
        composeRule.onNodeWithText("Project ideas").performClick()
        assertEquals(2L, selected)
    }

    @Test fun securityAndAppearanceChoicesAreExplicit() {
        var level: ChatSecurityLevel? = null
        var theme: ThemeMode? = null
        composeRule.setContent {
            TestScreen(
                ChatUiState(privacyPanelOpen = true),
                onSecurityLevel = { level = it },
                onThemeMode = { theme = it },
            )
        }
        composeRule.onNodeWithText("Dark").performClick()
        assertEquals(ThemeMode.DARK, theme)
        composeRule.onNodeWithText("Confidential").performScrollTo().performClick()
        assertEquals(ChatSecurityLevel.CONFIDENTIAL, level)
    }

    @Composable
    private fun TestScreen(
        state: ChatUiState,
        onPrompt: (String) -> Unit = {},
        onSend: () -> Unit = {},
        onContextStatus: (Long, ContextStatus) -> Unit = { _, _ -> },
        onVoice: () -> Unit = {},
        onNewChat: () -> Unit = {},
        onSelectChat: (Long) -> Unit = {},
        onSecurityLevel: (ChatSecurityLevel) -> Unit = {},
        onThemeMode: (ThemeMode) -> Unit = {},
    ) {
        var localPrompt by remember { mutableStateOf(state.prompt) }
        ChatScreen(
            state = state.copy(prompt = localPrompt),
            widthSizeClass = WindowWidthSizeClass.Compact,
            onPromptChange = { localPrompt = it; onPrompt(it) },
            onSend = onSend,
            onRetry = {},
            onClear = {},
            onErrorShown = {},
            onContextPanel = {},
            onPrivacyPanel = {},
            onContextStatus = onContextStatus,
            onInstructionsChange = {},
            onResetInstructions = {},
            onSummarize = {},
            onDeleteSummary = {},
            onDeleteMessage = {},
            onRegenerate = {},
            onSelectVariant = { _, _ -> },
            onClearPreferences = {},
            onClearApiKey = {},
            onNewChat = onNewChat,
            onSelectChat = onSelectChat,
            onSecurityLevel = onSecurityLevel,
            onThemeMode = onThemeMode,
            isVoiceListening = false,
            isVoiceAvailable = true,
            onVoiceInput = onVoice,
        )
    }
}
