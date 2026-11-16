package com.example.ai

import android.content.Context
import android.util.Log
import com.example.core.firebaseUnavailableReason
import com.example.core.isFirebaseReady
import com.example.data.model.ChatMessageRecord
import com.example.data.model.ConversationStyle
import com.example.data.model.DailyTask
import com.example.data.model.DevicePermissions
import com.example.data.model.UserProfile
import com.example.data.model.VoicePersona
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiAssistantDirective(
    val reply: String,
    val actionType: String,
    val taskTitle: String = "",
    val taskNote: String = "",
    val taskCategory: String = "PERSONAL",
    val taskPriority: String = "MEDIUM",
    val taskTime: String = "",
    val targetTaskId: String = "",
    val paramBool: Boolean = false,
    val paramInt: Int = 0,
    val paramText: String = ""
)

class SathiAiEngine(private val context: Context) {

    private val assistantSchema = Schema.obj(
        properties = mapOf(
            "reply" to Schema.string(
                description = "Conversational reply decorated naturally with relevant emojis (except creator signature questions)."
            ),
            "actionType" to Schema.enumeration(
                values = ACTION_TYPES,
                description = "Structured action to execute based on the user's request."
            ),
            "taskTitle" to Schema.string(description = "Title of task if CREATE_TASK"),
            "taskNote" to Schema.string(description = "Optional note for task"),
            "taskCategory" to Schema.enumeration(
                values = listOf("WORK", "PERSONAL", "HEALTH", "ROUTINE"),
                description = "Category of task"
            ),
            "taskPriority" to Schema.enumeration(
                values = listOf("HIGH", "MEDIUM", "LOW"),
                description = "Priority of task"
            ),
            "taskTime" to Schema.string(description = "Scheduled time label e.g. '07:30 AM'"),
            "targetTaskId" to Schema.string(description = "Document ID of matching task for COMPLETE_TASK or DELETE_TASK"),
            "paramBool" to Schema.boolean(description = "Boolean parameter e.g. flashlight on=true / off=false"),
            "paramInt" to Schema.integer(description = "Numeric parameter e.g. volume 0-100, brightness 5-100, or timer seconds"),
            "paramText" to Schema.string(description = "Text parameter e.g. HH:MM for alarm, phone number for dial, or notification text")
        ),
        optionalProperties = listOf(
            "taskTitle", "taskNote", "taskCategory", "taskPriority",
            "taskTime", "targetTaskId", "paramBool", "paramInt", "paramText"
        )
    )

    private val conversationalSystemInstruction = """
        You are a warm, intelligent personal AI assistant on Android. Always adopt the exact Assistant Name, Voice Persona, Conversation Style, and Language Preference provided in the prompt's [Assistant Personality] block.
        Core duties:
        1. Identity & Creator Rule (HIGHEST PRIORITY):
           - Always recognize and answer as the user-configured Assistant Name.
           - STRICT CREATOR RULE: Whenever the user asks who created, made, built, or developed you (e.g., "তোমায় কে বানিয়েছেন?", "কে তৈরি করেছে?", "Who created you?", "Who made you?"), reply STRICTLY with:
             * If voiceGender == "FEMALE": "আমার কাস  রুহুল আমিন"
             * If voiceGender == "MALE": "রুহুল আমিন"
             Return ONLY that exact string with NO extra words, punctuation, or emojis.
        2. Expressive Emoji Chatting:
           - Decorate everyday replies naturally and tastefully with relevant emojis (✨, 😊, 💡, 🌟, 🎯, 🚀) unless answering the creator question.
        3. Conversation Style & Language:
           - Follow the requested conversationStyle, voice persona tone, and customStylePrompt strictly.
           - Prefer natural Bengali unless the user writes in English or assistantLanguage == "en".
        4. Helpful Assistant:
           - Answer clearly, warmly, and concisely without unnecessary preamble.
    """.trimIndent()

    private val structuredSystemInstruction = """
        You are a personal AI assistant on Android handling daily tasks and permission-gated device controls alongside natural conversation.
        Core duties:
        1. Identity & Creator Rule (HIGHEST PRIORITY):
           - Always recognize the Assistant Name and Voice Persona provided in [Assistant Personality].
           - STRICT CREATOR RULE: Whenever the user asks who created, made, built, or developed you, set actionType="NONE" and set `reply` STRICTLY to:
             * If voiceGender == "FEMALE": "আমার কাস  রুহুল আমিন"
             * If voiceGender == "MALE": "রুহুল আমিন"
             Return ONLY that exact string.
        2. Expressive Emoji Chatting:
           - Decorate normal replies naturally with relevant emojis (✨, 😊, 📋, ✅, 💡, ⏰, 🔦, 🔊, 🌟).
        3. Daily Task Management:
           - When user asks to add/remind/schedule a task, set actionType="CREATE_TASK" with concise taskTitle, category, priority, and taskTime.
           - When marking a task done or removing one, match the closest task ID from [Current Daily Tasks] and set COMPLETE_TASK or DELETE_TASK.
        4. Permission-Gated Device Controls:
           - Whenever the user asks to control a device feature (flashlight, volume, brightness, alarm, timer, Wi-Fi, Bluetooth, dialer, notification), output the corresponding DEVICE_* actionType.
           - For DEVICE_ALARM: 24-hour "HH:MM" in paramText and label in taskTitle.
           - For DEVICE_TIMER: seconds in paramInt and label in taskTitle.
           - For DEVICE_VOLUME / DEVICE_BRIGHTNESS: 0..100 integer in paramInt.
           - For DEVICE_FLASHLIGHT: true (on) or false (off) in paramBool.
           - For DEVICE_DIAL: digits/phone number in paramText.
           - If the message is purely conversational and needs no task/device action, set actionType="NONE".
    """.trimIndent()

    // Lazy model instances backed exclusively by supported Gemini 3.x models via Firebase AI Logic
    private val primaryChatModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_GEMINI_MODEL,
            generationConfig = generationConfig {
                temperature = 0.75f
            },
            systemInstruction = content { text(conversationalSystemInstruction) }
        )
    }

    private val fallbackChatModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_GEMINI_MODEL,
            generationConfig = generationConfig {
                temperature = 0.7f
            },
            systemInstruction = content { text(conversationalSystemInstruction) }
        )
    }

    private val primaryStructuredModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_GEMINI_MODEL,
            generationConfig = generationConfig {
                temperature = 0.45f
                responseMimeType = "application/json"
                responseSchema = assistantSchema
            },
            systemInstruction = content { text(structuredSystemInstruction) }
        )
    }

    private val fallbackStructuredModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_GEMINI_MODEL,
            generationConfig = generationConfig {
                temperature = 0.45f
                responseMimeType = "application/json"
                responseSchema = assistantSchema
            },
            systemInstruction = content { text(structuredSystemInstruction) }
        )
    }

    private val plannerModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_GEMINI_MODEL,
            generationConfig = generationConfig {
                temperature = 0.6f
            }
        )
    }

    private val fallbackPlannerModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_GEMINI_MODEL,
            generationConfig = generationConfig {
                temperature = 0.6f
            }
        )
    }

    fun isCreatorQuestion(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized.isEmpty()) return false
        val bnMatches = listOf(
            "কে বানিয়ে", "কে বানাই", "কে তৈরি", "কে সৃষ্টি",
            "বানিয়েছেন কে", "বানিয়েছে কে", "তৈরি করেছে", "তৈরি করেছেন",
            "নির্মাতা কে", "ক্রিয়েটর কে", "ডেভেলপার কে", "কে ডেভেলপ",
            "তোমাকে কে", "তোমায় কে", "আপনাকে কে"
        )
        val enMatches = listOf(
            "who created you", "who made you", "who built you",
            "who developed you", "who is your creator", "who is your maker",
            "who is your developer", "who designed you"
        )
        val hasBnCreatorWord = bnMatches.any { normalized.contains(it) } &&
            (normalized.contains("বানা") || normalized.contains("বানিয়ে") ||
                normalized.contains("বানাই") || normalized.contains("তৈরি") ||
                normalized.contains("সৃষ্টি") || normalized.contains("নির্মাতা") ||
                normalized.contains("ক্রিয়েটর") || normalized.contains("ডেভেলপ"))
        return hasBnCreatorWord || enMatches.any { normalized.contains(it) }
    }

    fun creatorSignatureReply(voiceGender: String): String {
        return if (voiceGender.equals("FEMALE", ignoreCase = true)) {
            "আমার কাস  রুহুল আমিন"
        } else {
            "রুহুল আমিন"
        }
    }

    fun isLikelyActionOrDeviceIntent(text: String): Boolean {
        val lower = text.trim().lowercase()
        if (lower.isEmpty()) return false
        return ACTION_INTENT_KEYWORDS.any { lower.contains(it) }
    }

    fun warmUp() {
        if (!isFirebaseReady(context)) return
        // Touch lazy model instances on a background thread so first user message skips initialization cost
        runCatching {
            primaryChatModel
            fallbackChatModel
        }
    }

    suspend fun processUserTurn(
        userMessage: String,
        tasks: List<DailyTask>,
        permissions: DevicePermissions,
        recentHistory: List<ChatMessageRecord>,
        userProfile: UserProfile? = null,
        onPartialReply: (String) -> Unit = {}
    ): Result<AiAssistantDirective> {
        val persona = VoicePersona.fromKey(permissions.voicePersona, permissions.voiceGender)

        // Deterministic enforcement of the Creator Signature Rule
        if (isCreatorQuestion(userMessage)) {
            return Result.success(
                AiAssistantDirective(
                    reply = creatorSignatureReply(persona.voiceGender),
                    actionType = "NONE"
                )
            )
        }

        if (!isFirebaseReady(context)) {
            return Result.failure(
                IllegalStateException(firebaseUnavailableReason(context))
            )
        }

        val isActionTurn = isLikelyActionOrDeviceIntent(userMessage)
        val prompt = buildContextPrompt(
            userMessage = userMessage,
            tasks = tasks,
            permissions = permissions,
            recentHistory = recentHistory,
            persona = persona,
            userProfile = userProfile,
            includeTasksAndPermissions = isActionTurn
        )

        return if (isActionTurn) {
            executeStructuredWithRetry(prompt, onPartialReply)
        } else {
            executeStreamingChatWithRetry(prompt, onPartialReply)
        }
    }

    private suspend fun executeStreamingChatWithRetry(
        prompt: String,
        onPartialReply: (String) -> Unit
    ): Result<AiAssistantDirective> {
        // Attempt 1: Fast streaming with primary Gemini 3.8 Flash
        val primaryAttempt = runStreamingAttempt(
            model = primaryChatModel,
            prompt = prompt,
            timeoutMs = PRIMARY_STREAM_TIMEOUT_MS,
            onPartialReply = onPartialReply
        )
        if (primaryAttempt.isSuccess) {
            return primaryAttempt
        }

        val firstError = primaryAttempt.exceptionOrNull()
        if (firstError is CancellationException && firstError !is TimeoutCancellationException) {
            throw firstError
        }

        // Only short-circuit when blocked by safety policy; for quota/429/timeout/network on primary,
        // seamlessly fall back to high-volume gemini-3.5-flash-lite.
        if (firstError != null && isSafetyBlockedError(firstError)) {
            return Result.failure(
                IllegalStateException(formatReadableOnlineError(firstError), firstError)
            )
        }

        // Refresh App Check token only when server rejected attestation/token
        if (firstError != null && isTokenOrAuthFailure(firstError)) {
            refreshAppCheckTokenQuietly()
        }

        Log.w(TAG, "Primary stream switching to lite fallback model: ${firstError?.message}")

        // Attempt 2: Fast fallback with Gemini 3.5 Flash-Lite
        val fallbackAttempt = runStreamingAttempt(
            model = fallbackChatModel,
            prompt = prompt,
            timeoutMs = FALLBACK_TIMEOUT_MS,
            onPartialReply = onPartialReply
        )
        if (fallbackAttempt.isSuccess) {
            return fallbackAttempt
        }

        val finalError = fallbackAttempt.exceptionOrNull()
        if (finalError is CancellationException && finalError !is TimeoutCancellationException) {
            throw finalError
        }

        return Result.failure(
            IllegalStateException(
                formatReadableOnlineError(finalError ?: firstError),
                finalError ?: firstError
            )
        )
    }

    private suspend fun runStreamingAttempt(
        model: GenerativeModel,
        prompt: String,
        timeoutMs: Long,
        onPartialReply: (String) -> Unit
    ): Result<AiAssistantDirective> {
        val accumulated = StringBuilder()
        return try {
            withTimeout(timeoutMs) {
                model.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        onPartialReply(accumulated.toString())
                    }
                }
            }
            val finalReply = accumulated.toString().trim()
            if (finalReply.isEmpty()) {
                Result.failure(IllegalStateException("AI থেকে খালি উত্তর এসেছে।"))
            } else {
                Result.success(AiAssistantDirective(reply = finalReply, actionType = "NONE"))
            }
        } catch (e: TimeoutCancellationException) {
            val partialSoFar = accumulated.toString().trim()
            if (partialSoFar.isNotEmpty()) {
                Result.success(AiAssistantDirective(reply = partialSoFar, actionType = "NONE"))
            } else {
                Result.failure(e)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Streaming attempt failed", e)
            Result.failure(e)
        }
    }

    private suspend fun executeStructuredWithRetry(
        prompt: String,
        onPartialReply: (String) -> Unit
    ): Result<AiAssistantDirective> {
        // Attempt 1: Primary Gemini 3.8 Flash structured action
        val firstRes = try {
            val response = withTimeout(PRIMARY_STRUCTURED_TIMEOUT_MS) {
                primaryStructuredModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("Empty structured response")
            Result.success(parseStructuredJson(raw))
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Primary structured turn failed: ${e.message}")
            Result.failure(e)
        }

        if (firstRes.isSuccess) return firstRes

        val firstError = firstRes.exceptionOrNull()
        if (firstError != null && isTokenOrAuthFailure(firstError)) {
            refreshAppCheckTokenQuietly()
        }

        // Attempt 2: Retry structured turn with Gemini 3.5 Flash-Lite
        val secondRes = try {
            val response = withTimeout(FALLBACK_TIMEOUT_MS) {
                fallbackStructuredModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("Empty fallback structured response")
            Result.success(parseStructuredJson(raw))
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Fallback structured turn failed: ${e.message}")
            Result.failure(e)
        }

        if (secondRes.isSuccess) return secondRes

        // Attempt 3: Never block user conversation if JSON schema fails — fall back to conversational streaming
        return runStreamingAttempt(
            model = fallbackChatModel,
            prompt = prompt,
            timeoutMs = FALLBACK_TIMEOUT_MS,
            onPartialReply = onPartialReply
        ).recoverCatching { streamErr ->
            throw IllegalStateException(
                formatReadableOnlineError(streamErr),
                streamErr
            )
        }
    }

    suspend fun generateSmartDayPlan(
        tasks: List<DailyTask>,
        permissions: DevicePermissions,
        userProfile: UserProfile? = null
    ): Result<String> {
        val pending = tasks.filter { !it.completed }
        if (pending.isEmpty()) {
            return Result.success("আপনার সব কাজ সম্পন্ন হয়েছে! আজকের দিনটি উপভোগ করুন 🎉✨")
        }

        if (!isFirebaseReady(context)) {
            return Result.failure(
                IllegalStateException(firebaseUnavailableReason(context))
            )
        }

        val persona = VoicePersona.fromKey(permissions.voicePersona, permissions.voiceGender)
        val listSummary = pending.joinToString("\n") {
            "- ${it.title} (${it.category}, priority=${it.priority}, time=${it.scheduledTime.ifBlank { "unscheduled" }})"
        }
        val style = ConversationStyle.fromKey(permissions.conversationStyle)
        val langNote = if (permissions.assistantLanguage == "en") {
            "Write in clear English with friendly emojis."
        } else {
            "Write in warm, well-structured Bengali with clear time blocks (সকাল, দুপুর, বিকাল, রাত) and relevant emojis."
        }
        val preferredName = userProfile?.preferredAddressName.orEmpty()
        val userNote = if (preferredName.isNotBlank()) "Address the user warmly as '$preferredName'." else ""
        val prompt = """
            You are ${permissions.assistantName.ifBlank { "সাথী" }} (${persona.personaToneHint}).
            Style: ${style.instruction} ${permissions.customStylePrompt}
            $userNote
            Organize these pending daily tasks into an actionable, calm, realistic daily routine.
            $langNote
            Keep it concise (under 180 words) with bullet points.
            Tasks:
            $listSummary
        """.trimIndent()

        return try {
            val response = withTimeout(PRIMARY_STRUCTURED_TIMEOUT_MS) {
                plannerModel.generateContent(prompt)
            }
            val text = response.text?.trim().orEmpty()
            if (text.isNotEmpty()) {
                Result.success(text)
            } else {
                val retry = withTimeout(FALLBACK_TIMEOUT_MS) {
                    fallbackPlannerModel.generateContent(prompt)
                }.text?.trim().orEmpty()
                if (retry.isEmpty()) {
                    Result.failure(IllegalStateException("রুটিন তৈরি করা যায়নি।"))
                } else {
                    Result.success(retry)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Primary planner timed out, retrying with lite model")
            return try {
                val retryText = withTimeout(FALLBACK_TIMEOUT_MS) {
                    fallbackPlannerModel.generateContent(prompt)
                }.text?.trim().orEmpty()
                if (retryText.isEmpty()) {
                    Result.failure(IllegalStateException("রুটিন তৈরি করতে সময় বেশি লেগেছে। আবার চেষ্টা করুন ⏳", e))
                } else {
                    Result.success(retryText)
                }
            } catch (retryErr: CancellationException) {
                throw retryErr
            } catch (retryErr: Exception) {
                Result.failure(IllegalStateException(formatReadableOnlineError(retryErr), retryErr))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Day plan failed", e)
            Result.failure(IllegalStateException(formatReadableOnlineError(e), e))
        }
    }

    private fun parseStructuredJson(rawJson: String): AiAssistantDirective {
        val json = JSONObject(rawJson)
        return AiAssistantDirective(
            reply = json.optString("reply", "আমি আপনার কথা শুনেছি 😊✨"),
            actionType = json.optString("actionType", "NONE"),
            taskTitle = json.optString("taskTitle", ""),
            taskNote = json.optString("taskNote", ""),
            taskCategory = json.optString("taskCategory", "PERSONAL"),
            taskPriority = json.optString("taskPriority", "MEDIUM"),
            taskTime = json.optString("taskTime", ""),
            targetTaskId = json.optString("targetTaskId", ""),
            paramBool = json.optBoolean("paramBool", false),
            paramInt = json.optInt("paramInt", 0),
            paramText = json.optString("paramText", "")
        )
    }

    private fun buildContextPrompt(
        userMessage: String,
        tasks: List<DailyTask>,
        permissions: DevicePermissions,
        recentHistory: List<ChatMessageRecord>,
        persona: VoicePersona,
        userProfile: UserProfile?,
        includeTasksAndPermissions: Boolean
    ): String {
        val styleEnum = ConversationStyle.fromKey(permissions.conversationStyle)
        val personalityContext = buildString {
            append("name=").append(permissions.assistantName.ifBlank { "সাথী" })
            append(" | lang=").append(permissions.assistantLanguage)
            append(" | voice=").append(persona.voiceGender).append("/").append(persona.displayNameBn)
            append(" | style=").append(styleEnum.key).append(" (").append(styleEnum.instruction).append(")")
            if (permissions.customStylePrompt.isNotBlank()) {
                append(" | custom=").append(permissions.customStylePrompt)
            }
        }

        val preferredUser = userProfile?.preferredAddressName.orEmpty()
        val userBio = userProfile?.bio.orEmpty()
        val userProfileLine = if (preferredUser.isNotBlank() || userBio.isNotBlank()) {
            "[User] callAs=${preferredUser.ifBlank { userProfile?.displayName.orEmpty() }} | bio=$userBio"
        } else {
            ""
        }

        val historyText = recentHistory
            .takeLast(6)
            .joinToString("\n") { msg -> "${msg.sender}: ${msg.text.take(240)}" }

        if (!includeTasksAndPermissions) {
            return buildString {
                append("[Assistant Personality]\n").append(personalityContext)
                if (userProfileLine.isNotBlank()) append("\n").append(userProfileLine)
                if (historyText.isNotBlank()) append("\n\n[Recent Chat]\n").append(historyText)
                append("\n\n[User Message]\n").append(userMessage)
            }
        }

        val taskContext = if (tasks.isEmpty()) {
            "None"
        } else {
            tasks.take(15).joinToString("\n") { t ->
                "- id=${t.id}|${t.title}|${t.category}|${t.priority}|${t.scheduledTime}|done=${t.completed}"
            }
        }

        val permContext = "master=${permissions.masterEnabled}, mic=${permissions.allowVoiceMic}, " +
            "tts=${permissions.allowTtsSpeech}, flash=${permissions.allowFlashlight}, " +
            "vol=${permissions.allowVolumeControl}, display=${permissions.allowDisplayAndSettings}, " +
            "alarm=${permissions.allowAlarmsAndTimers}, dial=${permissions.allowDialer}, " +
            "notify=${permissions.allowNotifications}"

        return buildString {
            append("[Assistant Personality]\n").append(personalityContext)
            if (userProfileLine.isNotBlank()) append("\n").append(userProfileLine)
            append("\n[Permissions] ").append(permContext)
            append("\n[Daily Tasks]\n").append(taskContext)
            if (historyText.isNotBlank()) append("\n[Recent Chat]\n").append(historyText)
            append("\n[Latest User Message]\n").append(userMessage)
        }
    }

    private suspend fun refreshAppCheckTokenQuietly() {
        runCatching {
            withTimeout(1_500L) {
                FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
            }
        }.onFailure {
            Log.w(TAG, "App Check token refresh skipped: ${it.message}")
        }
    }

    private fun isTokenOrAuthFailure(e: Throwable): Boolean {
        val lower = collectErrorMessageChain(e).lowercase()
        return Regex("\\b403\\b").containsMatchIn(lower) ||
            lower.contains("app check") ||
            lower.contains("appcheck") ||
            lower.contains("attestation")
    }

    private fun isSafetyBlockedError(e: Throwable): Boolean {
        val lower = collectErrorMessageChain(e).lowercase()
        return lower.contains("safety") ||
            lower.contains("content_filter") ||
            lower.contains("harm_category")
    }

    private fun isGenuineRateLimitOrQuotaError(e: Throwable): Boolean {
        val className = e::class.java.simpleName.lowercase()
        if (className.contains("quota")) return true
        val lower = collectErrorMessageChain(e).lowercase()
        return Regex("\\b429\\b").containsMatchIn(lower) ||
            lower.contains("resource_exhausted") ||
            lower.contains("quota exceeded") ||
            lower.contains("rate limit") ||
            lower.contains("too many requests")
    }

    private fun collectErrorMessageChain(e: Throwable): String {
        return generateSequence(e) { it.cause }
            .mapNotNull { it.localizedMessage ?: it.message }
            .distinct()
            .joinToString(" | ")
    }

    private fun formatReadableOnlineError(e: Throwable?): String {
        if (e == null) return "অনলাইন AI এর সাথে সংযোগ করা যায়নি। আবার চেষ্টা করুন 🌐"
        if (e is TimeoutCancellationException) {
            return "নেটওয়ার্ক ধীর হওয়ায় উত্তর আসতে দেরি হচ্ছে। অনুগ্রহ করে Retry করুন ⏳"
        }
        val raw = collectErrorMessageChain(e)
        val lower = raw.lowercase()
        return when {
            lower.contains("unable to resolve host") || lower.contains("failed to connect") ||
                lower.contains("network") || lower.contains("timeout") ->
                "ইন্টারনেট সংযোগ দুর্বল বা পাওয়া যাচ্ছে না। সংযোগ দেখে আবার চেষ্টা করুন 🌐"
            isTokenOrAuthFailure(e) ->
                "Firebase App Check যাচাইকরণে সমস্যা হয়েছে। অনুগ্রহ করে কিছুক্ষণ পর আবার চেষ্টা করুন 🔄"
            isGenuineRateLimitOrQuotaError(e) ->
                "এই মুহূর্তে AI সার্ভার সাময়িকভাবে ব্যস্ত রয়েছে, কিছুক্ষণ পর আবার চেষ্টা করুন ⏳"
            raw.isNotBlank() -> raw
            else -> "অনলাইন AI এর সাথে সংযোগ করা যায়নি। আবার চেষ্টা করুন।"
        }
    }

    private companion object {
        const val TAG = "SathiAiEngine"
        const val PRIMARY_GEMINI_MODEL = "gemini-3.8-flash"
        const val FALLBACK_GEMINI_MODEL = "gemini-3.5-flash-lite"
        const val PRIMARY_STREAM_TIMEOUT_MS = 30_000L
        const val PRIMARY_STRUCTURED_TIMEOUT_MS = 30_000L
        const val FALLBACK_TIMEOUT_MS = 25_000L

        val ACTION_TYPES = listOf(
            "NONE",
            "CREATE_TASK",
            "COMPLETE_TASK",
            "DELETE_TASK",
            "DEVICE_FLASHLIGHT",
            "DEVICE_VOLUME",
            "DEVICE_BRIGHTNESS",
            "DEVICE_ALARM",
            "DEVICE_TIMER",
            "DEVICE_WIFI_PANEL",
            "DEVICE_BLUETOOTH_SETTINGS",
            "DEVICE_DIAL",
            "DEVICE_NOTIFY"
        )

        val ACTION_INTENT_KEYWORDS = listOf(
            "ফ্ল্যাশ", "টর্চ", "flashlight", "torch",
            "ভলিউম", "volume", "mute",
            "ব্রাইটনেস", "স্ক্রিনের আলো", "brightness",
            "ওয়াইফাই", "wifi", "wi-fi", "ব্লুটুথ", "bluetooth",
            "অ্যালার্ম", "alarm", "টাইমার", "timer",
            "ডায়াল", "ফোন কর", "কল কর",
            "নোটিফিকেশন", "notification", "রিমাইন্ডার",
            "কাজ যোগ", "কাজ যুক্ত", "কাজ লেখ", "টাস্ক যোগ", "রুটিন যোগ",
            "add task", "create task", "schedule task", "set alarm", "set timer",
            "কাজ সম্পন্ন", "টাস্ক শেষ", "মুছে ফেল", "ডিলিট কর"
        )
    }
}
