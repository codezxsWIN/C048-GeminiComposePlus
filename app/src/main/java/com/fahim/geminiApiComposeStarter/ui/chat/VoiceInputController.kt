package com.fahim.geminiApiComposeStarter.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

data class VoiceInputController(
    val isListening: Boolean,
    val isAvailable: Boolean,
    val toggle: () -> Unit,
)

/** Owns one lightweight SpeechRecognizer for the lifetime of the chat route. */
@Composable
fun rememberVoiceInputController(
    onResult: (String) -> Unit,
    onEmpty: () -> Unit,
    onUnavailable: () -> Unit,
    onPermissionDenied: () -> Unit,
): VoiceInputController {
    val context = LocalContext.current
    val currentResult by rememberUpdatedState(onResult)
    val currentEmpty by rememberUpdatedState(onEmpty)
    val currentUnavailable by rememberUpdatedState(onUnavailable)
    val currentPermissionDenied by rememberUpdatedState(onPermissionDenied)
    val available = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var isListening by remember { mutableStateOf(value = false) }
    var startWhenReady by remember { mutableStateOf(value = false) }
    var lastPartialResult by remember { mutableStateOf("") }

    fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startWhenReady = true else currentPermissionDenied()
    }

    DisposableEffect(available) {
        if (!available) {
            onDispose { }
        } else {
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                    override fun onBeginningOfSpeech() { isListening = true }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { isListening = false }
                    override fun onError(error: Int) {
                        isListening = false
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                val partial = lastPartialResult.trim()
                                lastPartialResult = ""
                                if (partial.isNotEmpty()) currentResult(partial) else currentEmpty()
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> currentPermissionDenied()
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> currentUnavailable()
                            SpeechRecognizer.ERROR_CLIENT -> Unit
                            else -> currentUnavailable()
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            ?: lastPartialResult
                        lastPartialResult = ""
                        if (text.isBlank()) currentEmpty() else currentResult(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.takeIf(String::isNotBlank)
                            ?.let { lastPartialResult = it }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                },
            )
            recognizer = speechRecognizer
            onDispose {
                isListening = false
                recognizer = null
                speechRecognizer.cancel()
                speechRecognizer.destroy()
            }
        }
    }

    LaunchedEffect(startWhenReady, recognizer) {
        if (startWhenReady && (recognizer != null)) {
            startWhenReady = false
            lastPartialResult = ""
            recognizer?.startListening(recognitionIntent())
        }
    }

    val toggle = remember(available, isListening, recognizer) {
        {
            when {
                (!available) || (recognizer == null) -> currentUnavailable()
                isListening -> recognizer?.stopListening()
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                    lastPartialResult = ""
                    recognizer?.startListening(recognitionIntent())
                }
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            Unit
        }
    }
    return VoiceInputController(isListening = isListening, isAvailable = available, toggle = toggle)
}
