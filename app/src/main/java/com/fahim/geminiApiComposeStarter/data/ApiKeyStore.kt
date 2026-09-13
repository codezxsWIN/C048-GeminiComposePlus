package com.fahim.geminiApiComposeStarter.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ApiKeyStatus(val isConfigured: Boolean, val needsRecovery: Boolean = false)

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "gemini_api_key_aes"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private val Context.secretsDataStore by preferencesDataStore(name = "encrypted_secrets")

class ApiKeyStore(private val context: Context) {
    private val encryptedKey = stringPreferencesKey("gemini_api_key_ciphertext")
    private val initializationVector = stringPreferencesKey("gemini_api_key_iv")
    private val recoveryRequired = booleanPreferencesKey("gemini_api_key_recovery")

    val status = context.secretsDataStore.data.map {
        ApiKeyStatus(
            isConfigured = it[encryptedKey] != null && it[initializationVector] != null,
            needsRecovery = it[recoveryRequired] ?: false,
        )
    }

    suspend fun seedFromBuildConfigIfNeeded(apiKey: String) {
        if (apiKey.isBlank()) return
        if (context.secretsDataStore.data.map { it[encryptedKey] }.first() != null) return
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        context.secretsDataStore.edit { preferences ->
            preferences[encryptedKey] = Base64.getEncoder().encodeToString(encrypted)
            preferences[initializationVector] = Base64.getEncoder().encodeToString(cipher.iv)
            preferences[recoveryRequired] = false
        }
    }

    suspend fun getDecryptedApiKey(): String {
        val preferences = context.secretsDataStore.data.first()
        val encrypted = preferences[encryptedKey] ?: return ""
        val iv = preferences[initializationVector] ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateSecretKey(),
                    GCMParameterSpec(128, Base64.getDecoder().decode(iv)),
                )
            }
            cipher.doFinal(Base64.getDecoder().decode(encrypted)).toString(Charsets.UTF_8)
        }.getOrElse {
            context.secretsDataStore.edit { preferences ->
                preferences.remove(encryptedKey)
                preferences.remove(initializationVector)
                preferences[recoveryRequired] = true
            }
            ""
        }
    }

    suspend fun clear() {
        context.secretsDataStore.edit {
            it.remove(encryptedKey)
            it.remove(initializationVector)
            it[recoveryRequired] = false
        }
        runCatching {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }
}
