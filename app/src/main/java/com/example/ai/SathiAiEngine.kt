package com.example.ai

import android.content.Context
import android.util.Log
import com.example.core.OnlineAiConfigStore
import com.example.core.isFirebaseReady
import com.example.data.model.AiLanguageModel
import com.example.data.model.ChatMessageRecord
import com.example.data.model.ConversationStyle
import com.example.data.model.DailyTask
import com.example.data.model.DevicePermissions
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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

class MissingOnlineAiConfigException(message: String) : IllegalStateException(message)

class SathiAiEngine(
    private val context: Context,
    private val configStore: OnlineAiConfigStore
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val firebaseStructuredModels = ConcurrentHashMap<String, GenerativeModel>()
    private val firebasePlannerModels = ConcurrentHashMap<String, GenerativeModel>()

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

    private val systemInstructionText = """
        You are a personal online AI assistant on Android. Always adopt the exact Assistant Name, Conversation Style, Language Preference, Selected Model Behavior, and Voice Gender provided in the prompt's [Assistant Personality] block.
        Core duties:
        1. Identity & Creator Signature Rule (HIGHEST PRIORITY):
           - Always recognize and respond to the user-configured Assistant Name.
           - STRICT CREATOR RULE: Whenever the user asks who created, made, built, or developed you (e.g., "তোমায় কে বানিয়েছেন?", "কে তৈরি করেছে?", "Who created you?", "Who made you?"), set actionType="NONE" and set `reply` STRICTLY to:
             * If voiceGender == "FEMALE": "আমার কাস  রুহুল আমিন"
             * If voiceGender == "MALE": "রুহুল আমিন"
             Return ONLY that exact string with NO extra words, punctuation, or emojis.
        2. Expressive Emoji Chatting:
           - For all normal replies (everything except the creator signature question), naturally and tastefully decorate your `reply` with relevant expressive emojis (✨, 😊, 📋, ✅, 💡, ⏰, 🔦, 🔊, 🌟, 🎯, 🚀).
        3. Conversation Style & Language:
           - Strictly follow the requested conversationStyle and customStylePrompt.
           - If creativeMode == true, be extra imaginative, expressive, and engaging while remaining helpful.
           - Prefer natural Bengali unless user writes in English or assistantLanguage == "en".
        4. Daily Task Management:
           - When user asks to add/remind/schedule a task, set actionType="CREATE_TASK" with concise taskTitle, category, priority, and taskTime.
           - When marking a task done or removing one, match the closest task ID from [Current Daily Tasks] and set COMPLETE_TASK or DELETE_TASK.
        5. Permission-Gated Device Controls:
           - Whenever the user asks to control a device feature (flashlight, volume, brightness, alarm, timer, Wi-Fi, Bluetooth, dialer, notification), output the corresponding DEVICE_* actionType so the app's permission gate enforces it deterministically.
           - For DEVICE_ALARM: 24-hour "HH:MM" in paramText and label in taskTitle.
           - For DEVICE_TIMER: seconds in paramInt and label in taskTitle.
           - For DEVICE_VOLUME / DEVICE_BRIGHTNESS: 0..100 integer in paramInt.
           - For DEVICE_FLASHLIGHT: true (on) or false (off) in paramBool.
           - For DEVICE_DIAL: digits/phone number in paramText.
    """.trimIndent()

    private fun getFirebaseStructuredModel(modelSpec: AiLanguageModel): GenerativeModel {
        return firebaseStructuredModels.getOrPut(modelSpec.key) {
            Firebase.ai.generativeModel(
                modelName = modelSpec.apiModelName,
                generationConfig = generationConfig {
                    temperature = modelSpec.temperature
                    responseMimeType = "application/json"
                    responseSchema = assistantSchema
                },
                systemInstruction = content { text(systemInstructionText) }
            )
        }
    }

    private fun getFirebasePlannerModel(modelSpec: AiLanguageModel): GenerativeModel {
        return firebasePlannerModels.getOrPut(modelSpec.key) {
            Firebase.ai.generativeModel(
                modelName = modelSpec.apiModelName,
                generationConfig = generationConfig {
                    temperature = modelSpec.temperature
                }
            )
        }
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

    suspend fun processUserTurn(
        userMessage: String,
        tasks: List<DailyTask>,
        permissions: DevicePermissions,
        recentHistory: List<ChatMessageRecord>
    ): Result<AiAssistantDirective> {
        // Deterministic enforcement of the Creator Signature Rule
        if (isCreatorQuestion(userMessage)) {
            return Result.success(
                AiAssistantDirective(
                    reply = creatorSignatureReply(permissions.voiceGender),
                    actionType = "NONE"
                )
            )
        }

        val modelSpec = AiLanguageModel.fromKey(permissions.selectedModel)
        val prompt = buildStructuredPrompt(userMessage, tasks, permissions, recentHistory, modelSpec)
        val onlineConfig = configStore.config.value

        return try {
            if (onlineConfig.hasApiKey) {
                val raw = withTimeout(60_000L) {
                    callGeminiRestApi(
                        endpointBase = onlineConfig.normalizedEndpoint,
                        apiKey = onlineConfig.activeApiKey,
                        modelName = modelSpec.apiModelName,
                        systemPrompt = systemInstructionText,
                        userPrompt = prompt,
                        temperature = modelSpec.temperature,
                        structuredJson = true
                    )
                }
                Result.success(parseDirectiveResponse(raw))
            } else if (isFirebaseReady(context)) {
                val response = withTimeout(60_000L) {
                    getFirebaseStructuredModel(modelSpec).generateContent(prompt)
                }
                val raw = response.text ?: throw IllegalStateException("AI থেকে কোনো উত্তর আসেনি।")
                Result.success(parseDirectiveResponse(raw))
            } else {
                Result.failure(
                    MissingOnlineAiConfigException(
                        "অনলাইন AI চ্যাট চালু করতে Settings > অনলাইন AI ও API Key অংশে আপনার Gemini API Key সেট করুন 🔑"
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI turn timed out", e)
            Result.failure(IllegalStateException("অনলাইন AI উত্তর দিতে সময় বেশি নিচ্ছে (৬০ সেকেন্ড)। আবার চেষ্টা করুন ⏳", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: MissingOnlineAiConfigException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Online AI turn failed", e)
            Result.failure(IllegalStateException(formatReadableOnlineError(e), e))
        }
    }

    suspend fun generateSmartDayPlan(
        tasks: List<DailyTask>,
        permissions: DevicePermissions
    ): Result<String> {
        val pending = tasks.filter { !it.completed }
        if (pending.isEmpty()) {
            return Result.success("আপনার সব কাজ সম্পন্ন হয়েছে! আজকের দিনটি উপভোগ করুন 🎉✨")
        }

        val modelSpec = AiLanguageModel.fromKey(permissions.selectedModel)
        val listSummary = pending.joinToString("\n") {
            "- ${it.title} (${it.category}, priority=${it.priority}, time=${it.scheduledTime.ifBlank { "unscheduled" }})"
        }
        val style = ConversationStyle.fromKey(permissions.conversationStyle)
        val langNote = if (permissions.assistantLanguage == "en") {
            "Write in clear English with friendly emojis."
        } else {
            "Write in warm, well-structured Bengali with clear time blocks (সকাল, দুপুর, বিকাল, রাত) and relevant emojis."
        }
        val prompt = """
            You are ${permissions.assistantName.ifBlank { "সাথী" }}. Style: ${style.instruction} ${permissions.customStylePrompt}
            Organize these pending daily tasks into an actionable, calm, realistic daily routine.
            $langNote
            Keep it concise (under 180 words) with bullet points.
            Tasks:
            $listSummary
        """.trimIndent()

        val onlineConfig = configStore.config.value

        return try {
            val text = if (onlineConfig.hasApiKey) {
                withTimeout(60_000L) {
                    callGeminiRestApi(
                        endpointBase = onlineConfig.normalizedEndpoint,
                        apiKey = onlineConfig.activeApiKey,
                        modelName = modelSpec.apiModelName,
                        systemPrompt = "You are a helpful personal daily planner.",
                        userPrompt = prompt,
                        temperature = modelSpec.temperature,
                        structuredJson = false
                    )
                }.trim()
            } else if (isFirebaseReady(context)) {
                val response = withTimeout(60_000L) {
                    getFirebasePlannerModel(modelSpec).generateContent(prompt)
                }
                response.text?.trim().orEmpty()
            } else {
                return Result.failure(
                    MissingOnlineAiConfigException(
                        "AI রুটিন তৈরি করতে Settings থেকে Gemini API Key যুক্ত করুন 🔑"
                    )
                )
            }
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("অনলাইন AI রুটিন তৈরি করতে পারেনি।"))
            } else {
                Result.success(text)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Day plan timed out", e)
            Result.failure(IllegalStateException("রুটিন তৈরি করতে সময় শেষ হয়েছে। আবার চেষ্টা করুন ⏳", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Day plan failed", e)
            Result.failure(IllegalStateException(formatReadableOnlineError(e), e))
        }
    }

    private fun buildStructuredPrompt(
        userMessage: String,
        tasks: List<DailyTask>,
        permissions: DevicePermissions,
        recentHistory: List<ChatMessageRecord>,
        modelSpec: AiLanguageModel
    ): String {
        val taskContext = if (tasks.isEmpty()) {
            "No tasks currently saved."
        } else {
            tasks.take(25).joinToString("\n") { t ->
                "- id=${t.id} | title=${t.title} | cat=${t.category} | prio=${t.priority} | time=${t.scheduledTime} | done=${t.completed}"
            }
        }

        val styleEnum = ConversationStyle.fromKey(permissions.conversationStyle)
        val personalityContext = """
            assistantName=${permissions.assistantName.ifBlank { "সাথী" }}
            assistantLanguage=${permissions.assistantLanguage}
            voiceGender=${permissions.voiceGender}
            selectedModel=${modelSpec.displayName} (${modelSpec.apiModelName}, creativeMode=${modelSpec.creativeMode})
            conversationStyle=${styleEnum.key} (${styleEnum.instruction})
            customStylePrompt=${permissions.customStylePrompt}
        """.trimIndent()

        val permContext = """
            masterEnabled=${permissions.masterEnabled}
            allowVoiceMic=${permissions.allowVoiceMic}
            allowTtsSpeech=${permissions.allowTtsSpeech}
            allowFlashlight=${permissions.allowFlashlight}
            allowVolumeControl=${permissions.allowVolumeControl}
            allowDisplayAndSettings=${permissions.allowDisplayAndSettings}
            allowAlarmsAndTimers=${permissions.allowAlarmsAndTimers}
            allowDialer=${permissions.allowDialer}
            allowNotifications=${permissions.allowNotifications}
        """.trimIndent()

        val historyText = recentHistory.takeLast(10).joinToString("\n") { msg ->
            "${msg.sender}: ${msg.text}"
        }

        return """
            [Assistant Personality]
            $personalityContext

            [User Permission Settings]
            $permContext

            [Current Daily Tasks]
            $taskContext

            [Recent Conversation]
            $historyText

            [Latest User Message]
            $userMessage
        """.trimIndent()
    }

    private suspend fun callGeminiRestApi(
        endpointBase: String,
        apiKey: String,
        modelName: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Float,
        structuredJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        val url = "$endpointBase/v1beta/models/$modelName:generateContent?key=$apiKey"
        val effectiveSystem = if (structuredJson) {
            """
            $systemPrompt

            Respond ONLY with a valid raw JSON object matching these keys:
            {
              "reply": "string with relevant emojis",
              "actionType": "NONE|CREATE_TASK|COMPLETE_TASK|DELETE_TASK|DEVICE_FLASHLIGHT|DEVICE_VOLUME|DEVICE_BRIGHTNESS|DEVICE_ALARM|DEVICE_TIMER|DEVICE_WIFI_PANEL|DEVICE_BLUETOOTH_SETTINGS|DEVICE_DIAL|DEVICE_NOTIFY",
              "taskTitle": "optional string",
              "taskNote": "optional string",
              "taskCategory": "WORK|PERSONAL|HEALTH|ROUTINE",
              "taskPriority": "HIGH|MEDIUM|LOW",
              "taskTime": "optional string",
              "targetTaskId": "optional string",
              "paramBool": false,
              "paramInt": 0,
              "paramText": "optional string"
            }
            """.trimIndent()
        } else {
            systemPrompt
        }

        val generationCfg = JSONObject().apply {
            put("temperature", temperature.toDouble())
            if (structuredJson) {
                put("responseMimeType", "application/json")
            }
        }

        val bodyJson = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", effectiveSystem)))
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                    }
                )
            )
            put("generationConfig", generationCfg)
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val apiErrorMsg = runCatching {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                throw IllegalStateException(
                    apiErrorMsg.ifBlank { "HTTP ${response.code}: অনলাইন AI সার্ভারে সংযোগ ব্যর্থ হয়েছে।" }
                )
            }
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text").orEmpty()
            if (text.isBlank()) {
                throw IllegalStateException("অনলাইন AI থেকে খালি উত্তর পাওয়া গেছে।")
            }
            text
        }
    }

    private fun parseDirectiveResponse(rawText: String): AiAssistantDirective {
        val cleaned = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val json = JSONObject(cleaned)
            AiAssistantDirective(
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
        } catch (_: Exception) {
            AiAssistantDirective(
                reply = cleaned.ifBlank { "আমি আপনার কথা শুনেছি 😊✨" },
                actionType = "NONE"
            )
        }
    }

    private fun formatReadableOnlineError(e: Exception): String {
        val raw = e.localizedMessage.orEmpty()
        val lower = raw.lowercase()
        return when {
            lower.contains("unable to resolve host") || lower.contains("failed to connect") || lower.contains("network") ->
                "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না। অনুগ্রহ করে ইন্টারনেট পরীক্ষা করে আবার চেষ্টা করুন 🌐"
            lower.contains("api key") || lower.contains("403") || lower.contains("401") || lower.contains("permission") ->
                "API Key অথবা অ্যাক্সেস অনুমতি সঠিক নয়। Settings থেকে আপনার Gemini API Key যাচাই করুন 🔑"
            lower.contains("quota") || lower.contains("429") ->
                "এই মডেলের ব্যবহারের সীমা সাময়িকভাবে পূর্ণ হয়েছে। অন্য মডেল বেছে নিন অথবা কিছুক্ষণ পর চেষ্টা করুন ⏳"
            lower.isNotBlank() -> raw
            else -> "অনলাইন AI এর সাথে সংযোগ করা যায়নি। আবার চেষ্টা করুন।"
        }
    }

    private companion object {
        const val TAG = "SathiAiEngine"
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
    }
}
