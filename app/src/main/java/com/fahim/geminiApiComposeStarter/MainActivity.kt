package com.fahim.geminiApiComposeStarter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.fahim.geminiApiComposeStarter.data.AppPreferencesState
import androidx.room.Room
import com.fahim.geminiApiComposeStarter.data.ApiKeyStore
import com.fahim.geminiApiComposeStarter.data.AppPreferences
import com.fahim.geminiApiComposeStarter.data.GeminiRepositoryImpl
import com.fahim.geminiApiComposeStarter.data.RoomChatHistoryRepository
import com.fahim.geminiApiComposeStarter.data.RoomChatSessionRepository
import com.fahim.geminiApiComposeStarter.data.local.ChatDatabase
import com.fahim.geminiApiComposeStarter.ui.chat.ChatRoute
import com.fahim.geminiApiComposeStarter.ui.chat.ChatViewModel
import com.fahim.geminiApiComposeStarter.ui.chat.ThemeMode
import com.fahim.geminiApiComposeStarter.ui.theme.GeminiApiComposeStarterTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val apiKeyStore by lazy { ApiKeyStore(applicationContext) }
    private val preferences by lazy { AppPreferences(applicationContext) }
    private val database by lazy {
        Room.databaseBuilder(applicationContext, ChatDatabase::class.java, "chat_history.db")
                .addMigrations(ChatDatabase.MIGRATION_1_2, ChatDatabase.MIGRATION_2_3, ChatDatabase.MIGRATION_3_4)
            .build()
    }
            private val sessionRepository by lazy { RoomChatSessionRepository(database.chatSessionDao(), database.chatMessageDao()) }

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.factory(
            repository = GeminiRepositoryImpl(apiKeyStore),
            historyRepository = RoomChatHistoryRepository(database.chatMessageDao()),
            preferences = preferences,
            apiKeyStore = apiKeyStore,
            sessionRepository = sessionRepository,
        )
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            apiKeyStore.seedFromBuildConfigIfNeeded(BuildConfig.GEMINI_API_KEY)
        }
        setContent {
            val savedPreferences by preferences.state.collectAsStateWithLifecycle(initialValue = AppPreferencesState())
            val darkTheme = when (runCatching { ThemeMode.valueOf(savedPreferences.themeMode) }.getOrDefault(ThemeMode.SYSTEM)) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            GeminiApiComposeStarterTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatRoute(
                        viewModel = viewModel,
                        widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
                    )
                }
            }
        }
    }
}
