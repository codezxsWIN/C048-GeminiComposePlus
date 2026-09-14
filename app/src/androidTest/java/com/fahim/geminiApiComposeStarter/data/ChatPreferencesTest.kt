package com.fahim.geminiApiComposeStarter.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChatPreferencesTest {
    @Test fun draftsAndInstructionsStayInTheirOwnChat() = runBlocking {
        // Instrumentation runs under the target UID. Redirect only this store to
        // an isolated cache directory instead of touching real chat preferences.
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = object : android.content.ContextWrapper(target) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): java.io.File = java.io.File(target.cacheDir, "privacy-preferences-test")
        }
        val preferences = AppPreferences(testContext)
        try {
            preferences.setDraft("Private draft", 101)
            preferences.setCustomInstructions("Private instructions", 101)
            preferences.setDraft("Other draft", 102)
            assertEquals("Private draft", preferences.stateForChat(101).first().draft)
            assertEquals("Other draft", preferences.stateForChat(102).first().draft)
            assertEquals("", preferences.stateForChat(102).first().customInstructions)
            preferences.clearUserPreferences(102)
            assertEquals("Private instructions", preferences.stateForChat(101).first().customInstructions)
        } finally {
            preferences.clearUserPreferences(101)
            preferences.clearUserPreferences(102)
        }
    }
}
