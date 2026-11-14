package com.example.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    val bnLocale = Locale.forLanguageTag("bn-BD")
                    val res = tts?.setLanguage(bnLocale)
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.US)
                    }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.w("VoiceAssistant", "TextToSpeech init failed: ${e.message}")
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }
    }

    fun speak(
        text: String,
        voiceGender: String = "FEMALE",
        voiceLocale: String = "bn-BD"
    ) {
        val cleanForSpeech = stripDecorativeEmojis(text)
        if (!ttsReady || cleanForSpeech.isBlank()) return
        try {
            val hasBengali = cleanForSpeech.any { it in '\u0980'..'\u09FF' }
            val preferredTag = when {
                voiceLocale.equals("en-US", ignoreCase = true) && !hasBengali -> "en-US"
                voiceLocale.equals("bn-IN", ignoreCase = true) -> "bn-IN"
                hasBengali -> "bn-BD"
                else -> voiceLocale.ifBlank { "en-US" }
            }
            val targetLocale = Locale.forLanguageTag(preferredTag)
            val langResult = tts?.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                val fallbackTag = if (preferredTag == "bn-BD") "bn-IN" else "bn-BD"
                val secondTry = tts?.setLanguage(Locale.forLanguageTag(fallbackTag))
                if (secondTry == TextToSpeech.LANG_MISSING_DATA || secondTry == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
            }

            applyVoiceProfile(targetLocale, voiceGender)

            _isSpeaking.value = true
            tts?.speak(
                cleanForSpeech,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "sathi_tts_${System.currentTimeMillis()}"
            )
        } catch (e: Exception) {
            _isSpeaking.value = false
            Log.w("VoiceAssistant", "TTS speak failed: ${e.message}")
        }
    }

    fun testVoice(voiceGender: String, assistantName: String, voiceLocale: String = "bn-BD") {
        val sample = when {
            voiceLocale.equals("en-US", ignoreCase = true) -> {
                if (voiceGender == "MALE") {
                    "Hello, I am $assistantName. Speaking with the male assistant voice."
                } else {
                    "Hello, I am $assistantName. Speaking with the female assistant voice."
                }
            }
            voiceGender == "MALE" -> "হ্যালো, আমি $assistantName। এখন পুরুষ কণ্ঠে কথা বলছি।"
            else -> "হ্যালো, আমি $assistantName। এখন নারী কণ্ঠে কথা বলছি।"
        }
        speak(sample, voiceGender, voiceLocale)
    }

    private fun applyVoiceProfile(locale: Locale, voiceGender: String) {
        val engine = tts ?: return
        val wantMale = voiceGender.equals("MALE", ignoreCase = true)
        val matchedVoice = findMatchingVoice(engine, locale, wantMale)

        if (matchedVoice != null) {
            engine.voice = matchedVoice
            engine.setPitch(if (wantMale) 0.88f else 1.08f)
            engine.setSpeechRate(if (wantMale) 0.96f else 1.0f)
        } else {
            // Fallback pitch tuning guarantees distinct Male vs Female timbre even when device has 1 voice
            engine.setPitch(if (wantMale) 0.74f else 1.15f)
            engine.setSpeechRate(if (wantMale) 0.95f else 1.0f)
        }
    }

    private fun findMatchingVoice(engine: TextToSpeech, locale: Locale, wantMale: Boolean): Voice? {
        val voices = try {
            engine.voices ?: return null
        } catch (_: Exception) {
            return null
        }
        val langMatches = voices.filter {
            it.locale.language.equals(locale.language, ignoreCase = true) && !it.isNetworkConnectionRequired
        }.ifEmpty {
            voices.filter { it.locale.language.equals(locale.language, ignoreCase = true) }
        }
        if (langMatches.isEmpty()) return null

        val maleHints = listOf("-m-", "_m_", "male", "bgd-x-bgd", "bng-x-bng", "iom", "iol", "sfg")
        val femaleHints = listOf("-f-", "_f_", "female", "iol-female", "tpf", "sfc")

        return if (wantMale) {
            langMatches.firstOrNull { v ->
                val lower = v.name.lowercase(Locale.ROOT)
                maleHints.any { lower.contains(it) }
            }
        } else {
            langMatches.firstOrNull { v ->
                val lower = v.name.lowercase(Locale.ROOT)
                femaleHints.any { lower.contains(it) }
            }
        }
    }

    private fun stripDecorativeEmojis(text: String): String {
        return text.filter { ch ->
            val type = Character.getType(ch)
            type != Character.OTHER_SYMBOL.toInt() && type != Character.SURROGATE.toInt()
        }.replace(Regex("\\s+"), " ").trim()
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
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
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }
}
