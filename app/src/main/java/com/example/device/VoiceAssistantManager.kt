package com.example.device

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.model.VoicePersona
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface VoiceListeningState {
    data object Idle : VoiceListeningState
    data class Listening(val partialText: String = "") : VoiceListeningState
    data class Error(val message: String) : VoiceListeningState
}

internal enum class VoiceGenderHint {
    FEMALE,
    MALE,
    UNKNOWN
}

class VoiceAssistantManager(private val context: Context) {

    private data class PendingUtterance(
        val text: String,
        val persona: VoicePersona,
        val voiceLocale: String
    )

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var switchedToGoogleEngine = false
    private var pendingUtterance: PendingUtterance? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val _listeningState = MutableStateFlow<VoiceListeningState>(VoiceListeningState.Idle)
    val listeningState: StateFlow<VoiceListeningState> = _listeningState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeVoiceInfo = MutableStateFlow("")
    val activeVoiceInfo: StateFlow<String> = _activeVoiceInfo.asStateFlow()

    init {
        initTtsEngine(preferredEnginePkg = null)
    }

    private fun initTtsEngine(preferredEnginePkg: String?) {
        try {
            val appCtx = context.applicationContext ?: context
            val listener = TextToSpeech.OnInitListener { status ->
                if (status != TextToSpeech.SUCCESS) {
                    Log.w("VoiceAssistant", "TextToSpeech init failed with status=$status")
                    return@OnInitListener
                }
                val engine = tts ?: return@OnInitListener

                // If the default OEM engine lacks Bengali voices while Google TTS is installed, switch once cleanly.
                if (preferredEnginePkg == null && !switchedToGoogleEngine && shouldSwitchToGoogleTts(engine)) {
                    switchedToGoogleEngine = true
                    runCatching { engine.shutdown() }
                    ttsReady = false
                    initTtsEngine(GOOGLE_TTS_PACKAGE)
                    return@OnInitListener
                }

                ttsReady = true
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val bnLocale = Locale.forLanguageTag("bn-BD")
                val res = engine.setLanguage(bnLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val inRes = engine.setLanguage(Locale.forLanguageTag("bn-IN"))
                    if (inRes == TextToSpeech.LANG_MISSING_DATA || inRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.US)
                    }
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.endsWith("_last") == true || utteranceId?.startsWith("sathi_tts_single") == true) {
                            _isSpeaking.value = false
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })

                pendingUtterance?.let { queued ->
                    pendingUtterance = null
                    speakWithPersona(queued.text, queued.persona, queued.voiceLocale)
                }
            }

            tts = if (preferredEnginePkg != null) {
                TextToSpeech(appCtx, listener, preferredEnginePkg)
            } else {
                TextToSpeech(appCtx, listener)
            }
        } catch (e: Exception) {
            Log.w("VoiceAssistant", "TextToSpeech init failed: ${e.message}")
        }
    }

    private fun shouldSwitchToGoogleTts(engine: TextToSpeech): Boolean {
        return try {
            val defaultEngine = engine.defaultEngine.orEmpty()
            if (defaultEngine == GOOGLE_TTS_PACKAGE) return false
            val hasGoogleEngine = engine.engines?.any { it.name == GOOGLE_TTS_PACKAGE } == true
            if (!hasGoogleEngine) return false
            val voices = engine.voices.orEmpty()
            val hasBengaliFemale = voices.any { v ->
                v.locale.language.equals("bn", ignoreCase = true) &&
                    classifyVoiceGender(v.name, v.features) == VoiceGenderHint.FEMALE
            }
            !hasBengaliFemale
        } catch (_: Exception) {
            false
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
        voiceLocale: String = "bn-BD",
        voicePersonaKey: String = ""
    ) {
        val persona = if (voicePersonaKey.isNotBlank()) {
            VoicePersona.fromKey(voicePersonaKey, voiceGender)
        } else {
            VoicePersona.defaultForGender(voiceGender)
        }
        speakWithPersona(text, persona, voiceLocale)
    }

    fun speakWithPersona(
        text: String,
        persona: VoicePersona,
        voiceLocale: String = "bn-BD"
    ) {
        val segments = splitSentencesForTts(text)
        if (segments.isEmpty()) return

        if (!ttsReady) {
            pendingUtterance = PendingUtterance(text, persona, voiceLocale)
            return
        }
        val engine = tts ?: return
        try {
            val combined = segments.joinToString(" ")
            val hasBengali = combined.any { it in '\u0980'..'\u09FF' }
            val preferredTag = when {
                voiceLocale.equals("en-US", ignoreCase = true) && !hasBengali -> "en-US"
                voiceLocale.equals("bn-IN", ignoreCase = true) -> "bn-IN"
                hasBengali -> "bn-BD"
                else -> voiceLocale.ifBlank { "en-US" }
            }
            val targetLocale = Locale.forLanguageTag(preferredTag)
            applyPersonaProfile(engine, targetLocale, persona)

            _isSpeaking.value = true
            val baseId = "sathi_tts_${System.currentTimeMillis()}"
            if (segments.size == 1) {
                engine.speak(
                    segments.first(),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "sathi_tts_single_$baseId"
                )
            } else {
                segments.forEachIndexed { index, segment ->
                    val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    val isLast = index == segments.lastIndex
                    val uttId = if (isLast) "${baseId}_${index}_last" else "${baseId}_$index"
                    engine.speak(segment, queueMode, null, uttId)
                    if (!isLast) {
                        engine.playSilentUtterance(135L, TextToSpeech.QUEUE_ADD, "${baseId}_pause_$index")
                    }
                }
            }
        } catch (e: Exception) {
            _isSpeaking.value = false
            Log.w("VoiceAssistant", "TTS speak failed: ${e.message}")
        }
    }

    fun testVoice(
        voicePersonaKey: String,
        voiceGender: String,
        assistantName: String,
        voiceLocale: String = "bn-BD"
    ) {
        val persona = VoicePersona.fromKey(voicePersonaKey, voiceGender)
        val sample = when {
            voiceLocale.equals("en-US", ignoreCase = true) -> {
                "Hello, I am $assistantName, speaking with ${persona.badge}."
            }
            else -> "হ্যালো আমি $assistantName এখন ${persona.shortLabel} কণ্ঠে কথা বলছি"
        }
        speakWithPersona(sample, persona, voiceLocale)
    }

    private fun applyPersonaProfile(
        engine: TextToSpeech,
        locale: Locale,
        persona: VoicePersona
    ) {
        val wantMale = persona.voiceGender.equals("MALE", ignoreCase = true)
        val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val selection = selectVoiceCandidate(
            candidates = voices.map {
                VoiceCandidate(
                    id = it.name,
                    locale = it.locale,
                    quality = it.quality,
                    isNetworkRequired = it.isNetworkConnectionRequired,
                    features = it.features.orEmpty()
                )
            },
            preferredLocale = locale,
            wantMale = wantMale,
            variantIndex = persona.voiceVariantIndex
        )

        val matchedVoice = selection?.candidate?.let { chosen ->
            voices.firstOrNull { it.name == chosen.id }
        }

        if (matchedVoice != null) {
            // Set language to the matched voice's locale first, then lock the exact Voice object
            runCatching { engine.setLanguage(matchedVoice.locale) }
            engine.voice = matchedVoice
            val matchedGender = selection.genderHint
            val pitch = computeNaturalPitch(persona, matchedGender)
            val rate = persona.speechRate
            engine.setPitch(pitch)
            engine.setSpeechRate(rate)
            _activeVoiceInfo.value = "${matchedVoice.name} ($matchedGender, pitch=$pitch)"
            Log.d("VoiceAssistant", "Selected TTS voice: ${matchedVoice.name}, gender=$matchedGender, pitch=$pitch, rate=$rate")
        } else {
            val langResult = engine.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                val fallbackTag = if (locale.language.equals("bn", ignoreCase = true)) "bn-IN" else "en-US"
                val secondTry = engine.setLanguage(Locale.forLanguageTag(fallbackTag))
                if (secondTry == TextToSpeech.LANG_MISSING_DATA || secondTry == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }
            }
            val pitch = computeNaturalPitch(persona, VoiceGenderHint.UNKNOWN)
            engine.setPitch(pitch)
            engine.setSpeechRate(persona.speechRate)
            _activeVoiceInfo.value = "default (${locale.toLanguageTag()}, pitch=$pitch)"
        }
    }

    fun stopSpeaking() {
        pendingUtterance = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
    }

    fun startListening(
        languageCode: String,
        voiceLocale: String = "bn-BD",
        onFinalResult: (String) -> Unit
    ) {
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

            val localeTag = when {
                languageCode == "en" -> "en-US"
                voiceLocale in setOf("bn-BD", "bn-IN", "en-US") -> voiceLocale
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
        pendingUtterance = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    private companion object {
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    }
}

internal data class VoiceCandidate(
    val id: String,
    val locale: Locale,
    val quality: Int = 300,
    val isNetworkRequired: Boolean = false,
    val features: Set<String> = emptySet()
)

internal data class VoiceSelectionResult(
    val candidate: VoiceCandidate,
    val genderHint: VoiceGenderHint
)

internal fun classifyVoiceGender(voiceName: String, features: Set<String> = emptySet()): VoiceGenderHint {
    val lowerFeatures = features.map { it.lowercase(Locale.ROOT) }
    if (lowerFeatures.any { it.contains("female") }) return VoiceGenderHint.FEMALE
    if (lowerFeatures.any { it.contains("male") && !it.contains("female") }) return VoiceGenderHint.MALE

    val lower = voiceName.lowercase(Locale.ROOT)
    val femaleTokens = listOf(
        "female", "-f-", "_f_", "#female",
        "-bnf-", "-bna-", "-bnx-", "-bnc-",
        "-sfg-", "-tpf-", "-iob-", "-iog-", "-tpc-", "-fis-"
    )
    val maleTokens = listOf(
        "male", "-m-", "_m_", "#male",
        "-bnm-", "-bnd-", "-bgd-", "-bng-",
        "-iom-", "-iol-", "-tpd-"
    )

    val matchesFemale = femaleTokens.any { lower.contains(it) } ||
        Regex("(?:^|[-_])f\\d+(?:[-_]|$)").containsMatchIn(lower)
    if (matchesFemale) return VoiceGenderHint.FEMALE

    val matchesMale = maleTokens.any { lower.contains(it) } ||
        Regex("(?:^|[-_])m\\d+(?:[-_]|$)").containsMatchIn(lower)
    if (matchesMale) return VoiceGenderHint.MALE

    return VoiceGenderHint.UNKNOWN
}

internal fun selectVoiceCandidate(
    candidates: List<VoiceCandidate>,
    preferredLocale: Locale,
    wantMale: Boolean,
    variantIndex: Int
): VoiceSelectionResult? {
    val languageMatches = candidates.filter {
        it.locale.language.equals(preferredLocale.language, ignoreCase = true)
    }
    if (languageMatches.isEmpty()) return null

    val desiredGender = if (wantMale) VoiceGenderHint.MALE else VoiceGenderHint.FEMALE
    val classified = languageMatches.map { it to classifyVoiceGender(it.id, it.features) }

    // Rank matching country + offline + high quality first
    val rankedComparator = compareByDescending<Pair<VoiceCandidate, VoiceGenderHint>> { (c, _) ->
        c.locale.country.equals(preferredLocale.country, ignoreCase = true)
    }.thenByDescending { (c, _) ->
        !c.isNetworkRequired
    }.thenByDescending { (c, _) ->
        c.quality
    }.thenBy { (c, _) ->
        c.id
    }

    // 1. Prefer explicit gender match across ALL dialects of this language (e.g. bn-IN female when bn-BD only has male)
    val explicitMatches = classified
        .filter { (_, gender) -> gender == desiredGender }
        .sortedWith(rankedComparator)
    if (explicitMatches.isNotEmpty()) {
        val picked = explicitMatches[variantIndex % explicitMatches.size]
        return VoiceSelectionResult(picked.first, picked.second)
    }

    // 2. Fallback to gender-unknown voices before opposite-gender voices
    val unknownMatches = classified
        .filter { (_, gender) -> gender == VoiceGenderHint.UNKNOWN }
        .sortedWith(rankedComparator)
    if (unknownMatches.isNotEmpty()) {
        val picked = unknownMatches[variantIndex % unknownMatches.size]
        return VoiceSelectionResult(picked.first, picked.second)
    }

    // 3. Last resort: use available language voices (pitch compensation will adapt gender)
    val allSorted = classified.sortedWith(rankedComparator)
    val picked = allSorted[variantIndex % allSorted.size]
    return VoiceSelectionResult(picked.first, picked.second)
}

internal fun computeNaturalPitch(
    persona: VoicePersona,
    matchedGender: VoiceGenderHint
): Float {
    val wantMale = persona.voiceGender.equals("MALE", ignoreCase = true)
    return when {
        !wantMale && matchedGender == VoiceGenderHint.FEMALE -> {
            if (persona == VoicePersona.FEMALE_CRISP) 1.05f else 0.99f
        }
        !wantMale && matchedGender == VoiceGenderHint.MALE -> {
            // Device only has a male Bengali base voice; lift pitch clearly into female register
            if (persona == VoicePersona.FEMALE_CRISP) 1.28f else 1.22f
        }
        !wantMale -> {
            if (persona == VoicePersona.FEMALE_CRISP) 1.22f else 1.16f
        }
        wantMale && matchedGender == VoiceGenderHint.MALE -> {
            if (persona == VoicePersona.MALE_ENERGETIC) 1.01f else 0.96f
        }
        wantMale && matchedGender == VoiceGenderHint.FEMALE -> {
            // Device only has a female base voice; lower pitch cleanly into male register
            if (persona == VoicePersona.MALE_ENERGETIC) 0.86f else 0.80f
        }
        else -> {
            if (persona == VoicePersona.MALE_ENERGETIC) 0.90f else 0.84f
        }
    }
}

internal fun splitSentencesForTts(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    // Preserve link labels and strip URLs before sentence segmentation so URLs don't split weirdly
    val withoutLinks = raw
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), " $1 ")
        .replace(Regex("(?:https?://|ftp://|mailto:|www\\.)\\S+", RegexOption.IGNORE_CASE), " ")

    return withoutLinks
        .split(Regex("[\\r\\n।!?;:]+|(?<!\\d)\\.(?!\\d)"))
        .map { sanitizeTextForTts(it) }
        .filter { it.isNotBlank() }
}

internal fun sanitizeTextForTts(raw: String): String {
    if (raw.isBlank()) return ""

    // 1. Preserve readable markdown link labels and drop their URLs
    var text = raw.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), " $1 ")

    // 2. Strip bare URLs and email/web links
    text = text.replace(Regex("(?:https?://|ftp://|mailto:|www\\.)\\S+", RegexOption.IGNORE_CASE), " ")

    // 3. Strip fenced code block markers
    text = text.replace(Regex("```[a-zA-Z0-9_+.-]*"), " ")

    // 4. Filter out emojis, surrogates, variation selectors, ZWJ, and non-text symbols
    val filtered = StringBuilder(text.length)
    for (ch in text) {
        if (ch == '\uFE0E' || ch == '\uFE0F' || ch == '\u200D' || ch == '\u20E3') continue
        val type = Character.getType(ch)
        if (type == Character.OTHER_SYMBOL.toInt() ||
            type == Character.SURROGATE.toInt() ||
            type == Character.CURRENCY_SYMBOL.toInt() ||
            type == Character.MATH_SYMBOL.toInt() ||
            type == Character.MODIFIER_SYMBOL.toInt()
        ) {
            filtered.append(' ')
        } else {
            filtered.append(ch)
        }
    }
    text = filtered.toString()

    // 5. Strip ALL markdown symbols, brackets, quotes, and punctuation symbols so none are ever spoken aloud
    text = text
        .replace(Regex("(?<!\\d)\\.(?!\\d)"), " ")
        .replace(
            Regex("[*#_`~()\\[\\]{}<>【】「」«»„\"'“”‘’/\\\\|@\$%^&+=\\-–—•·…¡¿,!?;:।\\r\\n]"),
            " "
        )

    // 6. Collapse extra spaces cleanly
    return text.replace(Regex("\\s+"), " ").trim()
}
