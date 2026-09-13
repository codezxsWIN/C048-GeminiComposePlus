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
)

class AppPreferences(private val context: Context) {
    private val draftKey = stringPreferencesKey("draft")
    private val instructionsKey = stringPreferencesKey("custom_instructions")

    val state: Flow<AppPreferencesState> = context.appDataStore.data.map {
        AppPreferencesState(
            draft = it[draftKey].orEmpty(),
            customInstructions = it[instructionsKey].orEmpty(),
        )
    }

    suspend fun setDraft(value: String) {
        context.appDataStore.edit { it[draftKey] = value }
    }

    suspend fun setCustomInstructions(value: String) {
        context.appDataStore.edit { it[instructionsKey] = value }
    }

    suspend fun clearUserPreferences() {
        context.appDataStore.edit {
            it.remove(draftKey)
            it.remove(instructionsKey)
        }
    }
}
