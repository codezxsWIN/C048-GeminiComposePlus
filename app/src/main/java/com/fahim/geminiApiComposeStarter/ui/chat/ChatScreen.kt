package com.fahim.geminiApiComposeStarter.ui.chat

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fahim.geminiApiComposeStarter.data.local.ChatSecurityLevel
import com.fahim.geminiApiComposeStarter.data.local.ContextStatus
import com.fahim.geminiApiComposeStarter.data.local.RequestStatus
import com.fahim.geminiApiComposeStarter.ui.text.LightweightMarkdown
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatRoute(viewModel: ChatViewModel, widthSizeClass: WindowWidthSizeClass) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val voice = rememberVoiceInputController(
        onResult = viewModel::onVoiceResult,
        onEmpty = viewModel::onVoiceEmpty,
        onUnavailable = viewModel::onVoiceUnavailable,
        onPermissionDenied = viewModel::onVoicePermissionDenied,
    )
    ChatScreen(
        state, widthSizeClass, viewModel::onPromptChange, viewModel::onSend, viewModel::retry,
        viewModel::clearHistory, viewModel::dismissError, viewModel::setContextPanel,
        viewModel::setPrivacyPanel, viewModel::setContextStatus, viewModel::setCustomInstructions,
        viewModel::resetCustomInstructions, viewModel::summarizeChat, viewModel::deleteSummary,
        viewModel::deleteMessage, viewModel::regenerate, viewModel::selectVariant,
        viewModel::clearDraftAndInstructions, viewModel::clearEncryptedApiKey,
        viewModel::selectChat, viewModel::newChat, viewModel::renameChat, viewModel::setSecurityLevel,
        viewModel::setThemeMode, viewModel::deleteActiveChat,
        viewModel::beginEdit, viewModel::cancelEdit, viewModel::onChatSearchChange,
        voice.isListening, voice.isAvailable, voice.toggle,
    )
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
    onDeleteMessage: (Long) -> Unit,
    onRegenerate: (Long) -> Unit,
    onSelectVariant: (Long, Long) -> Unit,
    onClearPreferences: () -> Unit,
    onClearApiKey: () -> Unit,
    onSelectChat: (Long) -> Unit = {},
    onNewChat: () -> Unit = {},
    onRenameChat: (String) -> Unit = {},
    onSecurityLevel: (ChatSecurityLevel) -> Unit = {},
    onThemeMode: (ThemeMode) -> Unit = {},
    onDeleteChat: () -> Unit = {},
    onBeginEdit: (Long) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onChatSearchChange: (String) -> Unit = {},
    isVoiceListening: Boolean,
    isVoiceAvailable: Boolean,
    onVoiceInput: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    var themeMenuOpen by remember { mutableStateOf(false) }
    val speaker = rememberTextSpeaker()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
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
    if (state.privacyPanelOpen) PrivacySheet(state, { onPrivacyPanel(false) }, onClearPreferences, onClearApiKey, onSecurityLevel, onThemeMode, onDeleteChat)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                state = state,
                onSelectChat = { id -> onSelectChat(id); scope.launch { drawerState.close() } },
                onNewChat = { onNewChat(); scope.launch { drawerState.close() } },
                onRenameChat = onRenameChat,
                onSearchChange = onChatSearchChange,
            )
        },
    ) {
      Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Open chats") } },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.chats.firstOrNull { it.id == state.activeChatId }?.title ?: "Gemini Compose+", fontWeight = FontWeight.Bold)
                            if (state.securityLevel == ChatSecurityLevel.CONFIDENTIAL) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Lock, contentDescription = "Confidential chat", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(state.securityLevel.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { themeMenuOpen = true }) {
                            val icon = when (state.themeMode) {
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            }
                            Icon(icon, contentDescription = "Appearance: ${state.themeMode.displayName}")
                        }
                        DropdownMenu(expanded = themeMenuOpen, onDismissRequest = { themeMenuOpen = false }) {
                            ThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(mode.displayName)
                                            Text(mode.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                                ThemeMode.DARK -> Icons.Default.DarkMode
                                            },
                                            contentDescription = null,
                                        )
                                    },
                                    trailingIcon = if (state.themeMode == mode) ({ Icon(Icons.Default.Check, contentDescription = "Selected") }) else null,
                                    onClick = { onThemeMode(mode); themeMenuOpen = false },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { onContextPanel(true) },
                        modifier = Modifier.testTag("context_health"),
                    ) { Icon(Icons.Default.Tune, "Context controls: ${state.contextHealth.name.replace('_', ' ')}") }
                    IconButton(onClick = { onPrivacyPanel(true) }) { Icon(Icons.Default.Security, "Privacy and security") }
                    IconButton(onClick = { confirmClear = true }, enabled = state.messages.isNotEmpty()) { Icon(Icons.Default.Delete, "Clear chat") }
                },
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.weight(1f).fillMaxHeight().widthIn(max = 880.dp)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    if (state.messages.isNotEmpty() && widthSizeClass != WindowWidthSizeClass.Expanded) {
                        ContextSummaryCard(
                            state = state,
                            onOpen = { onContextPanel(true) },
                            onSummarize = onSummarize,
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("message_list"),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.messages.isEmpty() && !state.isLoading) item("empty") {
                            EmptyChat(onPromptChange)
                        }
                        items(state.messages, key = { it.id }) {
                            ChatBubble(
                                message = it,
                                onStatus = onContextStatus,
                                onDeleteSummary = onDeleteSummary,
                                onDeleteMessage = onDeleteMessage,
                                onEdit = onBeginEdit,
                                onRetry = onRetry,
                                onRegenerate = onRegenerate,
                                onSelectVariant = onSelectVariant,
                                isSpeaking = speaker.speakingMessageId == it.id,
                                onSpeak = { messageId, text -> speaker.toggle(messageId, text) },
                                onSuggestion = onPromptChange,
                                showSuggestions = it.id == state.messages.lastOrNull { candidate ->
                                    !candidate.isFromUser && !candidate.isSummary
                                }?.id,
                            )
                        }
                        if (state.isLoading) item("loading") {
                            Row(Modifier.padding(12.dp).testTag("loading_indicator"), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text("Gemini is thinking…")
                            }
                        }
                    }
                    PromptBar(state, onPromptChange, onSend, onCancelEdit, isVoiceListening, isVoiceAvailable, onVoiceInput)
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
}

private val ChatSecurityLevel.label: String
    get() = when (this) {
        ChatSecurityLevel.OPEN -> "Connected"
        ChatSecurityLevel.PRIVATE -> "Protected memory"
        ChatSecurityLevel.CONFIDENTIAL -> "Confidential"
    }

private val ThemeMode.displayName: String
    get() = name.lowercase().replaceFirstChar { it.titlecase() }

private val ThemeMode.description: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Match your device"
        ThemeMode.LIGHT -> "Always use light appearance"
        ThemeMode.DARK -> "Always use dark appearance"
    }

@Composable
private fun ChatDrawer(
    state: ChatUiState,
    onSelectChat: (Long) -> Unit,
    onNewChat: () -> Unit,
    onRenameChat: (String) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var title by remember(state.activeChatId) {
        mutableStateOf(state.chats.firstOrNull { it.id == state.activeChatId }?.title.orEmpty())
    }
    ModalDrawerSheet {
        Column(Modifier.fillMaxHeight().widthIn(max = 360.dp)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Gemini Compose+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("C048 · conversation workspace", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onNewChat, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start a new chat")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.chatSearchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth().testTag("chat_search"),
                    singleLine = true,
                    placeholder = { Text("Search conversations") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (state.chatSearchQuery.isNotEmpty()) ({
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }) else null,
                    shape = RoundedCornerShape(16.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                if (state.chatSearchQuery.isBlank()) "RECENT CHATS" else "SEARCH RESULTS",
                Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val displayedChats = if (state.chatSearchQuery.isBlank()) state.chats else state.chatSearchResults
            if (displayedChats.isEmpty() && state.chatSearchQuery.isNotBlank()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("No matching conversations", fontWeight = FontWeight.SemiBold)
                    Text("Try a different word or phrase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(displayedChats, key = { it.id }) { chat ->
                    NavigationDrawerItem(
                        label = {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(chat.title, maxLines = 1, fontWeight = if (chat.id == state.activeChatId) FontWeight.SemiBold else FontWeight.Normal)
                                Text(chat.securityLevel.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                chat.matchPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                                    Text(
                                        preview.replace('\n', ' '),
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        selected = chat.id == state.activeChatId,
                        onClick = { onSelectChat(chat.id) },
                        icon = {
                            Icon(
                                if (chat.securityLevel == ChatSecurityLevel.CONFIDENTIAL) Icons.Default.Lock else Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                            )
                        },
                        badge = if (chat.id == state.activeChatId) ({ Icon(Icons.Default.ChevronRight, contentDescription = "Current chat") }) else null,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(12.dp)) {
                if (renaming) {
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Chat name") })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { renaming = false }) { Text("Cancel") }
                        Button(onClick = { onRenameChat(title); renaming = false }, enabled = title.isNotBlank()) { Text("Save") }
                    }
                } else {
                    TextButton(onClick = { renaming = true }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Rename current chat")
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
private fun ContextSummaryCard(
    state: ChatUiState,
    onOpen: () -> Unit,
    onSummarize: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp).testTag("context_summary_card"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (state.contextHealth) {
                        ContextHealth.LOW -> MaterialTheme.colorScheme.primaryContainer
                        ContextHealth.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
                        ContextHealth.HIGH, ContextHealth.NEAR_LIMIT -> MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Icon(
                        if (state.contextHealth == ContextHealth.NEAR_LIMIT) Icons.Default.Warning else Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp).size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.contextBlocked) "Context needs attention" else "Next request · ${state.nextContextIds.size} messages",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "~${state.estimatedTokens} / 24,000 tokens · ${state.crossChatMemoryCount} memories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                IconButton(onClick = onSummarize, enabled = !state.isSummarizing && !state.isLoading) {
                    if (state.isSummarizing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Summarize, "Summarize allowed chat", Modifier.size(20.dp))
                    }
                }
                TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Manage") }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (state.estimatedTokens / 24000f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${state.securityLevel.label} · ${state.excludedCount} hidden · ${state.protectedCount} pinned",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (state.trimmedCount > 0) {
                Text(
                    state.trimmedCount.toString() + " older messages will be left out of the next request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 48.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onStatus: (Long, ContextStatus) -> Unit,
    onDeleteSummary: () -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onRetry: () -> Unit,
    onRegenerate: (Long) -> Unit,
    onSelectVariant: (Long, Long) -> Unit,
    isSpeaking: Boolean,
    onSpeak: (Long, String) -> Unit,
    onSuggestion: (String) -> Unit,
    showSuggestions: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    var expanded by remember(message.id) { mutableStateOf(message.text.length <= 1_200) }
    var showInsights by remember(message.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start) {
        Card(
            Modifier.fillMaxWidth(
                when {
                    message.isSummary -> 1f
                    message.isFromUser -> .84f
                    else -> .98f
                },
            )
                .testTag(if (message.isSummary) "summary_message" else if (message.isFromUser) "user_message" else "gemini_message"),
            shape = RoundedCornerShape(
                when {
                    message.isSummary -> 16.dp
                    message.isFromUser -> 22.dp
                    else -> 18.dp
                },
            ),
            colors = CardDefaults.cardColors(containerColor = when {
                message.isSummary -> MaterialTheme.colorScheme.tertiaryContainer
                message.isFromUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            }),
            elevation = CardDefaults.cardElevation(defaultElevation = if (message.isFromUser) 0.dp else 1.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
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
                                DropdownMenuItem(
                                    { Text("Copy text") },
                                    { menu = false; clipboard.setText(AnnotatedString(message.text)) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                )
                                if (message.isFromUser) {
                                    DropdownMenuItem(
                                        { Text("Edit and resend") },
                                        { menu = false; onEdit(message.id) },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    )
                                }
                                DropdownMenuItem({ Text("Include") }, { menu = false; onStatus(message.id, ContextStatus.INCLUDED) }, leadingIcon = { Icon(Icons.Default.Visibility, null) })
                                DropdownMenuItem({ Text("Exclude from AI") }, { menu = false; onStatus(message.id, ContextStatus.EXCLUDED) }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) })
                                DropdownMenuItem({ Text("Protect detail") }, { menu = false; onStatus(message.id, ContextStatus.PROTECTED) }, leadingIcon = { Icon(Icons.Default.PushPin, null) })
                                DropdownMenuItem(
                                    { Text("Delete message") },
                                    { menu = false; onDeleteMessage(message.id) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                )
                            }
                        }
                    }
                }
                ContextStatusLabel(message)
                val visibleText = if (expanded) message.text else message.text.take(1_050).substringBeforeLast('\n').ifBlank { message.text.take(1_050) } + "\n…"
                LightweightMarkdown(visibleText, Modifier.fillMaxWidth())
                if (message.text.length > 1_200) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Show less" else "Show full answer")
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                }
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)) + if (message.requestStatus == RequestStatus.FAILED) " · failed" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.requestStatus == RequestStatus.FAILED) {
                    TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End).testTag("inline_retry")) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
                if (!message.isFromUser && !message.isSummary && message.requestStatus == RequestStatus.COMPLETE) {
                    AssistantUtilities(
                        message = message,
                        showInsights = showInsights,
                        onToggleInsights = { showInsights = !showInsights },
                        onCopy = { clipboard.setText(AnnotatedString(message.text)) },
                        onShare = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message.text)
                            }, "Share Gemini response"))
                        },
                        isSpeaking = isSpeaking,
                        onSpeak = { onSpeak(message.id, message.text) },
                        onRegenerate = { onRegenerate(message.id) },
                        onSelectVariant = onSelectVariant,
                        onSuggestion = onSuggestion,
                        showSuggestions = showSuggestions,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextStatusLabel(message: ChatMessage) {
    val (label, icon) = when {
        message.isSummary -> "Local summary · not sent as context" to Icons.Default.Lock
        message.requestStatus == RequestStatus.FAILED -> "Failed · not sent as context" to Icons.Default.ErrorOutline
        message.contextStatus == ContextStatus.PROTECTED -> "Protected context" to Icons.Default.PushPin
        message.contextStatus == ContextStatus.EXCLUDED -> "Excluded from AI context" to Icons.Default.VisibilityOff
        else -> "Included in AI context" to Icons.Default.Visibility
    }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = .55f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AssistantUtilities(
    message: ChatMessage,
    showInsights: Boolean,
    onToggleInsights: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onRegenerate: () -> Unit,
    onSelectVariant: (Long, Long) -> Unit,
    onSuggestion: (String) -> Unit,
    showSuggestions: Boolean,
) {
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallAction(Icons.Default.ContentCopy, "Copy answer", onCopy)
            SmallAction(Icons.Default.Share, "Share answer", onShare)
            SmallAction(
                if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                if (isSpeaking) "Stop reading" else "Read answer aloud",
                onSpeak,
                testTag = "read_aloud_button_${message.id}",
                active = isSpeaking,
            )
            SmallAction(Icons.Default.Refresh, "Generate an alternative", onRegenerate)
            SmallAction(Icons.Default.Info, "Answer insights", onToggleInsights, active = showInsights)
        }
    }
    if (message.variantCount > 1 && message.variantGroupId != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { message.variantIds.getOrNull(message.variantIndex - 2)?.let { onSelectVariant(it, message.variantGroupId) } },
                enabled = message.variantIndex > 1,
            ) { Icon(Icons.Default.ChevronLeft, "Previous answer") }
            Text("Answer ${message.variantIndex} of ${message.variantCount}", style = MaterialTheme.typography.labelMedium)
            IconButton(
                onClick = { message.variantIds.getOrNull(message.variantIndex)?.let { onSelectVariant(it, message.variantGroupId) } },
                enabled = message.variantIndex < message.variantCount,
            ) { Icon(Icons.Default.ChevronRight, "Next answer") }
        }
    }
    if (showInsights) AnswerInsights(message)
    if (showSuggestions) {
        Text(
            "Continue the conversation",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            followUpSuggestions().forEach { (label, prompt) ->
                SuggestionChip(onClick = { onSuggestion(prompt) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String? = null,
    active: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Icon(
            icon,
            label,
            Modifier.size(19.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
private fun AnswerInsights(message: ChatMessage) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = .65f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("answer_insights"),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Answer insights", fontWeight = FontWeight.Bold)
            InsightRow("Confidence", "Unverified", "The API does not provide a reliable probability score.")
            InsightRow("Model", "Gemini 3.6 Flash")
            InsightRow("Conversation context", "${message.contextMessageCount} messages used")
            if (message.protectedUsedCount > 0) InsightRow("Protected details", "${message.protectedUsedCount} prioritized")
            if (message.excludedAtRequestCount > 0) InsightRow("Memory Firewall", "${message.excludedAtRequestCount} excluded")
            if (message.trimmedAtRequestCount > 0) InsightRow("Context trimming", "${message.trimmedAtRequestCount} older messages omitted")
            InsightRow("Custom instructions", if (message.customInstructionsUsed) "Applied" else "Not active")
            InsightRow("Input", if (message.wasVoicePrompt) "Voice draft" else "Typed prompt")
            InsightRow("External capabilities", "Live web not used", "This client sends chat text to Gemini; it does not browse the web.")
        }
    }
}

@Composable
private fun InsightRow(label: String, value: String, note: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1.25f), horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun followUpSuggestions(): List<Pair<String, String>> = listOf(
    "Simplify" to "Explain your previous answer more simply.",
    "Example" to "Give me a practical example based on your previous answer.",
    "Challenge it" to "Challenge your previous answer and show its strongest limitation.",
    "What to verify" to "What claims in your previous answer should I independently verify?",
)

private data class TextSpeakerController(
    val speakingMessageId: Long?,
    val toggle: (Long, String) -> Unit,
)

@Composable
private fun rememberTextSpeaker(): TextSpeakerController {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isReady by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var speakingMessageId by remember { mutableStateOf<Long?>(null) }
    var finalUtteranceId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(engine) {
        val currentEngine = engine
        onDispose {
            currentEngine?.stop()
            currentEngine?.shutdown()
        }
    }
    val toggle = remember(context, engine, isReady, speakingMessageId) {
        { messageId: Long, text: String ->
            val ready = engine
            if (speakingMessageId == messageId) {
                pending = null
                finalUtteranceId = null
                speakingMessageId = null
                ready?.stop()
            } else {
                pending = messageId to text
                speakingMessageId = messageId
                if (ready != null && isReady) {
                    finalUtteranceId = ready.speakQueued(text, messageId)
                    pending = null
                }
                if (ready == null) {
                    var createdEngine: TextToSpeech? = null
                    createdEngine = TextToSpeech(context.applicationContext) { status ->
                        val initializedEngine = createdEngine
                        if (status == TextToSpeech.SUCCESS && initializedEngine != null) {
                            val languageResult = initializedEngine.setLanguage(Locale.getDefault())
                            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                            ) {
                                initializedEngine.language = Locale.US
                            }
                            initializedEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) = Unit

                                override fun onDone(utteranceId: String?) {
                                    if (utteranceId == finalUtteranceId) {
                                        mainHandler.post {
                                            finalUtteranceId = null
                                            speakingMessageId = null
                                        }
                                    }
                                }

                                @Deprecated("Deprecated in Android")
                                override fun onError(utteranceId: String?) {
                                    mainHandler.post {
                                        finalUtteranceId = null
                                        speakingMessageId = null
                                    }
                                }

                                override fun onError(utteranceId: String?, errorCode: Int) {
                                    mainHandler.post {
                                        finalUtteranceId = null
                                        speakingMessageId = null
                                    }
                                }
                            })
                            isReady = true
                            pending?.let { (pendingId, pendingText) ->
                                finalUtteranceId = initializedEngine.speakQueued(pendingText, pendingId)
                            }
                        } else {
                            initializedEngine?.shutdown()
                            engine = null
                            speakingMessageId = null
                        }
                        pending = null
                    }
                    engine = createdEngine
                }
            }
            Unit
        }
    }
    return TextSpeakerController(speakingMessageId = speakingMessageId, toggle = toggle)
}

private fun TextToSpeech.speakQueued(text: String, messageId: Long): String? {
    val chunks = text.toSpeechChunks()
    stop()
    chunks.forEachIndexed { index, chunk ->
        speak(
            chunk,
            if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            "gemini-answer-$messageId-$index",
        )
    }
    return chunks.indices.lastOrNull()?.let { "gemini-answer-$messageId-$it" }
}

internal fun String.toSpeechChunks(
    maximumChunkLength: Int = TextToSpeech.getMaxSpeechInputLength() - 256,
): List<String> {
    val readable = replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace("```", "")
        .replace("**", "")
        .replace("__", "")
        .trim()
    if (readable.isEmpty()) return emptyList()

    val limit = maximumChunkLength.coerceAtLeast(128)
    val chunks = mutableListOf<String>()
    var offset = 0
    while (offset < readable.length) {
        val end = (offset + limit).coerceAtMost(readable.length)
        if (end == readable.length) {
            chunks += readable.substring(offset).trim()
            break
        }
        val candidate = readable.substring(offset, end)
        val sentenceBreak = candidate.lastIndexOfAny(charArrayOf('\n', '.', '!', '?'))
        val wordBreak = candidate.lastIndexOf(' ')
        val splitAt = when {
            sentenceBreak >= limit / 2 -> sentenceBreak + 1
            wordBreak >= limit / 2 -> wordBreak + 1
            else -> candidate.length
        }
        chunks += candidate.substring(0, splitAt).trim()
        offset += splitAt
    }
    return chunks.filter(String::isNotEmpty)
}

@Composable
private fun PromptBar(
    state: ChatUiState,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelEdit: () -> Unit,
    isVoiceListening: Boolean,
    isVoiceAvailable: Boolean,
    onVoice: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            if (state.editingMessageId != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag("edit_message_banner"),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Edit and resend", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text("The original stays saved but leaves AI context.", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = onCancelEdit, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel editing")
                        }
                    }
                }
            }
            OutlinedTextField(
                state.prompt, onChange, Modifier.fillMaxWidth().testTag("prompt_field"),
                placeholder = { Text("Message Gemini…") },
                isError = state.promptError != null,
                supportingText = state.promptError?.let { { Text(if (it == PromptError.EMPTY) "Message cannot be empty" else "Reduce protected context") } },
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (state.prompt.isNotBlank() && !state.isLoading) {
                        onSend()
                        focusManager.clearFocus()
                    }
                }),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onVoice,
                            enabled = !state.isLoading && isVoiceAvailable,
                            modifier = Modifier.testTag("voice_button"),
                        ) {
                            if (isVoiceListening) {
                                Icon(Icons.Default.Stop, "Stop listening", tint = MaterialTheme.colorScheme.error)
                            } else {
                                Icon(Icons.Default.Mic, "Voice typing")
                            }
                        }
                        FilledIconButton(
                            onClick = onSend,
                            enabled = !state.isLoading && state.prompt.isNotBlank(),
                            modifier = Modifier.testTag("send_button"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                if (state.editingMessageId != null) "Send edited message" else "Send",
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.editingMessageId != null) {
                    Text("Correction will be sent as a new turn", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else if (isVoiceListening) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Listening · tap stop when finished", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("/summarize  •  /formal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text("~${state.estimatedTokens} tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Context controls", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close context controls") }
                }
                Text("Preview the next request", style = MaterialTheme.typography.titleMedium)
                Text("This preview includes allowed chat history and shared memory. Token counts are estimates.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                LinearProgressIndicator(progress = { (state.estimatedTokens / 24000f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Text(state.estimatedTokens.toString() + " tokens · " + state.protectedCount + " protected · " + state.excludedCount + " excluded")
            }
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${state.nextContextIds.size} messages selected · ${state.trimmedCount} omitted", fontWeight = FontWeight.SemiBold)
                        Text(if (state.contextBlocked) "Request blocked: shorten your prompt or reduce pinned context." else "${state.crossChatMemoryCount} details from other chats are included.", style = MaterialTheme.typography.bodySmall)
                        Text("Hiding affects future requests. It cannot undo messages already sent to Gemini. Replies to hidden prompts are also left out.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.memoryPreview.isNotEmpty()) item {
                Text("Shared memory in this request", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                state.memoryPreview.forEach { detail ->
                    Text("• $detail", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                }
                Text("To remove a detail, hide it in its original chat. Confidential mode turns off all shared memory.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Text("Messages sent next time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Use allows a message if it fits. Hide excludes it from future requests. Pin preserves it during trimming and allows user details to be shared in Protected memory mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val controllable = state.messages.filter {
                !it.isSummary && it.requestStatus == RequestStatus.COMPLETE
            }.asReversed()
            if (controllable.isEmpty()) item { Text("Send a message to start managing context.") }
            items(controllable, key = { "context-" + it.id }) { message ->
                ContextMessageControl(message, message.id in state.nextContextIds, onStatus)
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("Instructions for this chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    state.customInstructions,
                    onInstructions,
                    Modifier.fillMaxWidth().testTag("custom_instructions"),
                    placeholder = { Text("Example: Be concise and explain technical terms") },
                    minLines = 3,
                )
                TextButton(onClick = onReset, enabled = state.customInstructions.isNotBlank()) { Text("Reset instructions") }
            }
            item {
                Text("Summarizing sends the allowed messages in this chat to Gemini. The resulting summary is saved locally and excluded from future context.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onSummarize, enabled = !state.isLoading && !state.isSummarizing && state.messages.any { !it.isSummary }, modifier = Modifier.fillMaxWidth().testTag("summarize_button")) {
                    Icon(Icons.Default.Summarize, null); Spacer(Modifier.width(8.dp)); Text(if (state.isSummarizing) "Summarizing…" else "Summarize allowed chat")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ContextMessageControl(message: ChatMessage, selectedForRequest: Boolean, onStatus: (Long, ContextStatus) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("context_message_${message.id}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (message.isFromUser) "You" else "Gemini", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        message.contextStatus == ContextStatus.EXCLUDED -> "Hidden from future requests"
                        selectedForRequest -> "Selected for next request"
                        else -> "Not selected for next request"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(message.text, maxLines = 3, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ContextChoice(
                    label = "Use", selected = message.contextStatus == ContextStatus.INCLUDED,
                    modifier = Modifier.weight(1f).testTag("context_include_${message.id}"),
                ) { onStatus(message.id, ContextStatus.INCLUDED) }
                ContextChoice(
                    label = "Hide", selected = message.contextStatus == ContextStatus.EXCLUDED,
                    modifier = Modifier.weight(1f).testTag("context_exclude_${message.id}"),
                ) { onStatus(message.id, ContextStatus.EXCLUDED) }
                ContextChoice(
                    label = "Pin", selected = message.contextStatus == ContextStatus.PROTECTED,
                    modifier = Modifier.weight(1f).testTag("context_protect_${message.id}"),
                ) { onStatus(message.id, ContextStatus.PROTECTED) }
            }
        }
    }
}

@Composable
private fun ContextChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        leadingIcon = if (selected) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySheet(
    state: ChatUiState,
    onDismiss: () -> Unit,
    onClearPreferences: () -> Unit,
    onClearApiKey: () -> Unit,
    onSecurityLevel: (ChatSecurityLevel) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDeleteChat: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Remove encrypted credentials?") },
        text = { Text("The encrypted key record and Android Keystore entry will be removed.") },
        confirmButton = { TextButton(onClick = { onClearApiKey(); confirm = false }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Privacy & appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${state.securityLevel.label} is active", fontWeight = FontWeight.Bold)
                    Text("${if (state.securityLevel == ChatSecurityLevel.CONFIDENTIAL) "No memory is shared between chats." else "Review shared details in Context controls."} Your prompt and selected context are still sent to Gemini to get an answer.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = { Text(mode.displayName) },
                        leadingIcon = {
                            Icon(
                                when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Default.LightMode
                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider()
            Text("Chat security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Choose what this conversation may contribute to other chats. Excluded messages are never shared.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThemeSecurityChoice(ChatSecurityLevel.OPEN, state.securityLevel, onSecurityLevel)
            ThemeSecurityChoice(ChatSecurityLevel.PRIVATE, state.securityLevel, onSecurityLevel)
            ThemeSecurityChoice(ChatSecurityLevel.CONFIDENTIAL, state.securityLevel, onSecurityLevel)
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Memory selected for next request", fontWeight = FontWeight.SemiBold)
                        Text("${state.crossChatMemoryCount} approved detail${if (state.crossChatMemoryCount == 1) "" else "s"} from other chats", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Encrypted key: " + if (state.apiKeyConfigured) "configured" else "not configured")
            Text("Local Room records: " + state.messages.size)
            Text("Chat history is stored in this app's local database; it is not encrypted by this app. Confidential controls sharing between chats, not device access or Google's data retention.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.apiKeyNeedsRecovery) Text("An unreadable encrypted key was safely removed.", color = MaterialTheme.colorScheme.error)
            HorizontalDivider()
            Text("The API key is encrypted at rest with AES-256-GCM and Android Keystore. It is decrypted only in memory for a request.")
            Text("Client limitation: a key shipped in an APK can still be extracted. Production apps should use a backend.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onClearPreferences, Modifier.fillMaxWidth()) { Text("Clear draft and instructions") }
            OutlinedButton(onClick = { confirm = true }, Modifier.fillMaxWidth()) { Text("Remove encrypted credentials") }
            OutlinedButton(onClick = onDeleteChat, Modifier.fillMaxWidth()) { Text("Delete this chat") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeSecurityChoice(
    level: ChatSecurityLevel,
    selected: ChatSecurityLevel,
    onSelect: (ChatSecurityLevel) -> Unit,
) {
    val description = when (level) {
        ChatSecurityLevel.OPEN -> "All completed, allowed user messages may support future chats."
        ChatSecurityLevel.PRIVATE -> "Only messages you explicitly mark Protected may support future chats."
        ChatSecurityLevel.CONFIDENTIAL -> "No content is imported from or exported to other conversations."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected == level) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected == level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        onClick = { onSelect(level) },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (level) {
                    ChatSecurityLevel.OPEN -> Icons.Default.Public
                    ChatSecurityLevel.PRIVATE -> Icons.Default.Shield
                    ChatSecurityLevel.CONFIDENTIAL -> Icons.Default.Lock
                },
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(level.label, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected == level, onClick = null)
        }
    }
}
