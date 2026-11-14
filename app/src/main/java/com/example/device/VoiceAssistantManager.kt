package com.example.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface VoiceListeningState {
    data object Idle : VoiceListeningState
    data class Listening(val partialText: String = "") : VoiceListeningState
    data class Error(val message: String) : VoiceListeningState
}

class VoiceAssistantManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _listeningState = MutableStateFlow<VoiceListeningState>(VoiceListeningState.Idle)
    val listeningState: StateFlow<VoiceListeningState> = _listeningState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val bnLocale = Locale.forLanguageTag("bn-BD")
                val res = tts?.setLanguage(bnLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
            }
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        val hasBengali = text.any { it in '\u0980'..'\u09FF' }
        val targetLocale = if (hasBengali) Locale.forLanguageTag("bn-BD") else Locale.US
        val langResult = tts?.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
        }
        _isSpeaking.value = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sathi_tts_${System.currentTimeMillis()}")
        _isSpeaking.value = false
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun startListening(languageCode: String, onFinalResult: (String) -> Unit) {
        if (!isSpeechRecognitionAvailable()) {
            _listeningState.value = VoiceListeningState.Error(
                "এই ডিভাইসে ভয়েস রেকগনিশন সার্ভিস পাওয়া যায়নি। নিচে লিখে কথা বলুন।"
            )
            return
        }
        stopListening()
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _listeningState.value = VoiceListeningState.Listening("")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "কিছু শোনা যায়নি, আবার চেষ্টা করুন।"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "সময় শেষ হয়েছে, মাইকে ট্যাপ করে বলুন।"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোন পারমিশন প্রয়োজন।"
                        else -> "ভয়েস বুঝতে সমস্যা হয়েছে (কোড $error)।"
                    }
                    _listeningState.value = VoiceListeningState.Error(msg)
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                        .trim()
                    _listeningState.value = VoiceListeningState.Idle
                    if (text.isNotEmpty()) {
                        onFinalResult(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        _listeningState.value = VoiceListeningState.Listening(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val localeTag = when (languageCode) {
                "en" -> "en-US"
                else -> "bn-BD"
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            _listeningState.value = VoiceListeningState.Listening("")
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("VoiceAssistant", "Failed to start SpeechRecognizer", e)
            _listeningState.value = VoiceListeningState.Error(
                e.localizedMessage ?: "মাইক্রোফোন চালু করা যায়নি।"
            )
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        if (_listeningState.value is VoiceListeningState.Listening) {
            _listeningState.value = VoiceListeningState.Idle
        }
    }

    fun clearError() {
        if (_listeningState.value is VoiceListeningState.Error) {
            _listeningState.value = VoiceListeningState.Idle
        }
    }

    fun release() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
