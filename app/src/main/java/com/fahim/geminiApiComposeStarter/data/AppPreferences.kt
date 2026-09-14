package com.fahim.geminiApiComposeStarter.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore by preferencesDataStore(name = "app_preferences")

data class AppPreferencesState(
    val draft: String = "",
    val customInstructions: String = "",
    val themeMode: String = "SYSTEM",
)

class AppPreferences(private val context: Context) {
    private val draftKey = stringPreferencesKey("draft")
    private val instructionsKey = stringPreferencesKey("custom_instructions")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val state: Flow<AppPreferencesState> get() = stateForChat(1)

    private fun draftKeyFor(chatId: Long) = if (chatId == 1L) draftKey else stringPreferencesKey("draft_$chatId")
    private fun instructionsKeyFor(chatId: Long) = if (chatId == 1L) instructionsKey else stringPreferencesKey("instructions_$chatId")

    fun stateForChat(chatId: Long): Flow<AppPreferencesState> = context.appDataStore.data.map {
        AppPreferencesState(
            draft = it[draftKeyFor(chatId)].orEmpty(),
            customInstructions = it[instructionsKeyFor(chatId)].orEmpty(),
            themeMode = it[themeModeKey] ?: "SYSTEM",
        )
    }

    suspend fun setDraft(value: String, chatId: Long = 1) {
        context.appDataStore.edit { it[draftKeyFor(chatId)] = value }
    }

    suspend fun setCustomInstructions(value: String, chatId: Long = 1) {
        context.appDataStore.edit { it[instructionsKeyFor(chatId)] = value }
    }

    suspend fun setThemeMode(value: String) {
        context.appDataStore.edit { it[themeModeKey] = value }
    }

    suspend fun clearUserPreferences(chatId: Long = 1) {
        context.appDataStore.edit {
            it.remove(draftKeyFor(chatId))
            it.remove(instructionsKeyFor(chatId))
        }
    }
}
