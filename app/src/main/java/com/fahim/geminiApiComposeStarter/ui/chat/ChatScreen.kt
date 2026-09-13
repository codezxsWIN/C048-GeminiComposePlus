package com.fahim.geminiApiComposeStarter.ui.chat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fahim.geminiApiComposeStarter.data.local.ContextStatus
import com.fahim.geminiApiComposeStarter.data.local.RequestStatus
import com.fahim.geminiApiComposeStarter.ui.text.toBoldAnnotatedString
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatRoute(viewModel: ChatViewModel, widthSizeClass: WindowWidthSizeClass) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (text.isNullOrBlank()) viewModel.onVoiceUnavailable() else viewModel.onVoiceResult(text)
        }
    }
    ChatScreen(
        state, widthSizeClass, viewModel::onPromptChange, viewModel::onSend, viewModel::retry,
        viewModel::clearHistory, viewModel::dismissError, viewModel::setContextPanel,
        viewModel::setPrivacyPanel, viewModel::setContextStatus, viewModel::setCustomInstructions,
        viewModel::resetCustomInstructions, viewModel::summarizeChat, viewModel::deleteSummary,
        viewModel::clearDraftAndInstructions, viewModel::clearEncryptedApiKey,
    ) {
        try {
            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
            })
        } catch (_: ActivityNotFoundException) { viewModel.onVoiceUnavailable() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    widthSizeClass: WindowWidthSizeClass,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onErrorShown: () -> Unit,
    onContextPanel: (Boolean) -> Unit,
    onPrivacyPanel: (Boolean) -> Unit,
    onContextStatus: (Long, ContextStatus) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onResetInstructions: () -> Unit,
    onSummarize: () -> Unit,
    onDeleteSummary: () -> Unit,
    onClearPreferences: () -> Unit,
    onClearApiKey: () -> Unit,
    onVoiceInput: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    val nearBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            val action = snackbar.showSnackbar(it, if (state.canRetry) "Retry" else null)
            onErrorShown()
            if (action == SnackbarResult.ActionPerformed) onRetry()
        }
    }
    LaunchedEffect(state.messages.size, state.isLoading) {
        if (nearBottom) {
            val end = state.messages.size + if (state.isLoading) 1 else 0
            if (end > 0) listState.animateScrollToItem(end - 1)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this conversation?") },
            text = { Text("All messages and summaries will be permanently removed.") },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    if (state.contextPanelOpen) ContextSheet(state, { onContextPanel(false) }, onInstructionsChange, onResetInstructions, onContextStatus, onSummarize)
    if (state.privacyPanelOpen) PrivacySheet(state, { onPrivacyPanel(false) }, onClearPreferences, onClearApiKey)

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Gemini Compose+", fontWeight = FontWeight.Bold)
                    Text("C048 · transparent context", style = MaterialTheme.typography.labelSmall)
                } },
                actions = {
                    AssistChip(
                        onClick = { onContextPanel(true) },
                        label = { Text(state.contextHealth.name.replace('_', ' ')) },
                        leadingIcon = { Icon(Icons.Default.Shield, "Context controls", Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("context_health"),
                    )
                    IconButton(onClick = { onPrivacyPanel(true) }) { Icon(Icons.Default.Security, "Privacy and security") }
                    IconButton(onClick = { confirmClear = true }, enabled = state.messages.isNotEmpty()) { Icon(Icons.Default.Delete, "Clear chat") }
                },
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.weight(1f).fillMaxHeight().widthIn(max = 880.dp)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("message_list"),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.messages.isEmpty() && !state.isLoading) item("empty") {
                            EmptyChat(onPromptChange)
                        }
                        items(state.messages, key = { it.id }) { ChatBubble(it, onContextStatus, onDeleteSummary) }
                        if (state.isLoading) item("loading") {
                            Row(Modifier.padding(12.dp).testTag("loading_indicator"), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text("Gemini is thinking…")
                            }
                        }
                    }
                    PromptBar(state, onPromptChange, onSend, onVoiceInput)
                }
                if (!nearBottom && state.messages.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            val end = state.messages.size + if (state.isLoading) 1 else 0
                            if (end > 0) listState.requestScrollToItem(end - 1)
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp, 24.dp, 24.dp, 110.dp).testTag("scroll_bottom"),
                    ) { Icon(Icons.Default.KeyboardArrowDown, "Scroll to newest") }
                }
            }
            if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                Surface(Modifier.width(280.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Context at a glance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(state.estimatedTokens.toString() + " approximate tokens")
                        LinearProgressIndicator(progress = { (state.estimatedTokens / 24000f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                        Text(state.protectedCount.toString() + " protected · " + state.excludedCount + " excluded")
                        if (state.trimmedCount > 0) Text(state.trimmedCount.toString() + " older messages will be omitted")
                        Button(onClick = { onContextPanel(true) }, Modifier.fillMaxWidth()) { Text("Manage context") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(onPromptChange: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoAwesome, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("A smarter conversation starts here", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Ask anything, then control exactly what Gemini remembers.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        listOf("Explain a difficult idea simply", "Help me compare two approaches", "Turn my notes into a clear plan").forEach {
            SuggestionChip(onClick = { onPromptChange(it) }, label = { Text(it) }, modifier = Modifier.padding(3.dp))
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, onStatus: (Long, ContextStatus) -> Unit, onDeleteSummary: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start) {
        Card(
            Modifier.fillMaxWidth(if (message.isSummary) 1f else .86f)
                .testTag(if (message.isSummary) "summary_message" else if (message.isFromUser) "user_message" else "gemini_message"),
            shape = RoundedCornerShape(if (message.isSummary) 12.dp else 22.dp),
            colors = CardDefaults.cardColors(containerColor = when {
                message.isSummary -> MaterialTheme.colorScheme.tertiaryContainer
                message.isFromUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (message.isSummary) "Conversation summary" else if (message.isFromUser) "You" else "Gemini", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    if (message.contextStatus == ContextStatus.PROTECTED) Icon(Icons.Default.PushPin, "Protected", Modifier.size(17.dp))
                    if (message.contextStatus == ContextStatus.EXCLUDED && !message.isSummary) Icon(Icons.Default.VisibilityOff, "Excluded", Modifier.size(17.dp))
                    Box {
                        IconButton(onClick = { menu = true }, Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Message actions") }
                        DropdownMenu(menu, { menu = false }) {
                            if (message.isSummary) {
                                DropdownMenuItem({ Text("Delete summary") }, { menu = false; onDeleteSummary() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                            } else {
                                DropdownMenuItem({ Text("Include") }, { menu = false; onStatus(message.id, ContextStatus.INCLUDED) }, leadingIcon = { Icon(Icons.Default.Visibility, null) })
                                DropdownMenuItem({ Text("Exclude from AI") }, { menu = false; onStatus(message.id, ContextStatus.EXCLUDED) }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) })
                                DropdownMenuItem({ Text("Protect detail") }, { menu = false; onStatus(message.id, ContextStatus.PROTECTED) }, leadingIcon = { Icon(Icons.Default.PushPin, null) })
                            }
                        }
                    }
                }
                Text(message.text.toBoldAnnotatedString())
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)) + if (message.requestStatus == RequestStatus.FAILED) " · failed" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PromptBar(state: ChatUiState, onChange: (String) -> Unit, onSend: () -> Unit, onVoice: () -> Unit) {
    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.padding(10.dp)) {
            OutlinedTextField(
                state.prompt, onChange, Modifier.fillMaxWidth().testTag("prompt_field"),
                placeholder = { Text("Message Gemini…") },
                isError = state.promptError != null,
                supportingText = state.promptError?.let { { Text(if (it == PromptError.EMPTY) "Message cannot be empty" else "Reduce protected context") } },
                maxLines = 6,
                trailingIcon = { Row {
                    IconButton(onVoice, enabled = !state.isLoading, modifier = Modifier.testTag("voice_button")) { Icon(Icons.Default.Mic, "Voice input") }
                    FilledIconButton(onSend, enabled = !state.isLoading, modifier = Modifier.testTag("send_button")) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                } },
            )
            Text("Estimated " + state.estimatedTokens + " tokens · /summarize · /formal", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextSheet(
    state: ChatUiState, onDismiss: () -> Unit, onInstructions: (String) -> Unit,
    onReset: () -> Unit, onStatus: (Long, ContextStatus) -> Unit, onSummarize: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Context controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("You decide what Gemini may use. Size is approximate.")
            }
            item {
                LinearProgressIndicator(progress = { (state.estimatedTokens / 24000f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Text(state.estimatedTokens.toString() + " tokens · " + state.protectedCount + " protected · " + state.excludedCount + " excluded")
            }
            item {
                OutlinedTextField(state.customInstructions, onInstructions, Modifier.fillMaxWidth().testTag("custom_instructions"), label = { Text("Custom AI instructions") }, minLines = 3)
                TextButton(onClick = onReset, enabled = state.customInstructions.isNotBlank()) { Text("Reset instructions") }
            }
            item {
                Button(onClick = onSummarize, enabled = !state.isSummarizing && state.messages.any { !it.isSummary }, modifier = Modifier.fillMaxWidth().testTag("summarize_button")) {
                    Icon(Icons.Default.Summarize, null); Spacer(Modifier.width(8.dp)); Text(if (state.isSummarizing) "Summarizing…" else "Summarize allowed chat")
                }
            }
            item { Text("Protected details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            val protected = state.messages.filter { it.contextStatus == ContextStatus.PROTECTED }
            if (protected.isEmpty()) item { Text("No protected details. Use a message's menu to protect one.") }
            items(protected, key = { "protected-" + it.id }) {
                ListItem(
                    headlineContent = { Text(it.text, maxLines = 2) },
                    trailingContent = { IconButton(onClick = { onStatus(it.id, ContextStatus.INCLUDED) }) { Icon(Icons.Default.Close, "Remove protection") } },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySheet(state: ChatUiState, onDismiss: () -> Unit, onClearPreferences: () -> Unit, onClearApiKey: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Remove encrypted credentials?") },
        text = { Text("The encrypted key record and Android Keystore entry will be removed.") },
        confirmButton = { TextButton(onClick = { onClearApiKey(); confirm = false }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Privacy & Security Center", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Encrypted key: " + if (state.apiKeyConfigured) "configured" else "not configured")
            Text("Local Room records: " + state.messages.size)
            if (state.apiKeyNeedsRecovery) Text("An unreadable encrypted key was safely removed.", color = MaterialTheme.colorScheme.error)
            HorizontalDivider()
            Text("The API key is encrypted at rest with AES-256-GCM and Android Keystore. It is decrypted only in memory for a request.")
            Text("Client limitation: a key shipped in an APK can still be extracted. Production apps should use a backend.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onClearPreferences, Modifier.fillMaxWidth()) { Text("Clear draft and instructions") }
            OutlinedButton(onClick = { confirm = true }, Modifier.fillMaxWidth()) { Text("Remove encrypted credentials") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
