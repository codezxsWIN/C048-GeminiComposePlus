package com.fahim.geminiApiComposeStarter.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

private const val DEFAULT_MODEL = "gemini-2.0-flash"

class GeminiRepositoryImpl(
    private val apiKeyStore: ApiKeyStore,
    private val modelName: String = DEFAULT_MODEL,
) : GeminiRepository {
    override suspend fun generate(request: ChatRequest): GeminiResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getDecryptedApiKey()
        if (apiKey.isBlank()) return@withContext GeminiResult.Failure(GeminiFailure.MissingKey)
        try {
            val systemInstruction = request.customInstructions.trim().takeIf(String::isNotEmpty)?.let {
                content { text(it) }
            }
            val model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                systemInstruction = systemInstruction,
            )
            val history = request.orderedHistory.map { message ->
                content(role = if (message.role == ChatRole.USER) "user" else "model") { text(message.text) }
            }
            val response = model.startChat(history).sendMessage(request.currentMessage)
            val text = response.text?.trim()
            if (text.isNullOrEmpty()) GeminiResult.Failure(GeminiFailure.Blocked)
            else GeminiResult.Success(text)
        } catch (error: CancellationException) {
            if (error is TimeoutCancellationException) GeminiResult.Failure(GeminiFailure.Timeout) else throw error
        } catch (error: IOException) {
            GeminiResult.Failure(GeminiFailure.Offline)
        } catch (error: Exception) {
            GeminiResult.Failure(classify(error))
        }
    }

    private fun classify(error: Exception): GeminiFailure {
        val message = error.message.orEmpty().lowercase()
        return when {
            "401" in message || "403" in message || "api key" in message -> GeminiFailure.Authentication
            "429" in message || "quota" in message || "rate limit" in message -> GeminiFailure.Quota
            "token" in message && ("limit" in message || "large" in message) -> GeminiFailure.ContextTooLarge
            "blocked" in message || "safety" in message -> GeminiFailure.Blocked
            "timeout" in message -> GeminiFailure.Timeout
            else -> GeminiFailure.Unknown
        }
    }
}
