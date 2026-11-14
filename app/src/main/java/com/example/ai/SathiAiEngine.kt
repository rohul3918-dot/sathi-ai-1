package com.example.ai

import android.content.Context
import android.util.Log
import com.example.core.isFirebaseReady
import com.example.data.model.ChatMessageRecord
import com.example.data.model.ConversationStyle
import com.example.data.model.DailyTask
import com.example.data.model.DevicePermissions
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
                description = "Conversational reply decorated naturally with relevant emojis (except creator signature questions which must return strictly the creator string)."
            ),
            "actionType" to Schema.enumeration(
                values = listOf(
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
                ),
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

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = assistantSchema
            },
            systemInstruction = content {
                text(
                    """
                    You are a personal AI assistant on Android. Always adopt the exact Assistant Name, Conversation Style, Language Preference, and Voice Gender provided in the prompt's [Assistant Personality] block.
                    Core duties:
                    1. Identity & Creator Signature Rule (HIGHEST PRIORITY):
                       - Always recognize and respond to the user-configured Assistant Name.
                       - STRICT CREATOR RULE: Whenever the user asks who created, made, built, or developed you (e.g., "তোমায় কে বানিয়েছেন?", "কে তৈরি করেছে?", "Who created you?", "Who made you?"), set actionType="NONE" and set `reply` STRICTLY to:
                         * If voiceGender == "FEMALE": "আমার কাস  রুহুল আমিন"
                         * If voiceGender == "MALE": "রুহুল আমিন"
                         Return ONLY that exact string with NO extra words, punctuation, or emojis.
                    2. Expressive Emoji Chatting:
                       - For all normal replies (everything except the creator signature question), naturally and tastefully decorate your `reply` with relevant expressive emojis (✨, 😊, 📋, ✅, 💡, ⏰, 🔦, 🔊, 🌟, 🎯).
                    3. Conversation Style & Language:
                       - Strictly follow the requested conversationStyle and customStylePrompt.
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
                )
            }
        )
    }

    private val plannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
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

        if (!isFirebaseReady(context)) {
            return Result.success(processLocalFallbackTurn(userMessage, tasks, permissions))
        }

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

        val historyText = recentHistory.takeLast(8).joinToString("\n") { msg ->
            "${msg.sender}: ${msg.text}"
        }

        val prompt = """
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

        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("AI empty response")
            val json = JSONObject(raw)
            Result.success(
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
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI turn timed out", e)
            Result.failure(IllegalStateException("AI উত্তর দিতে সময় বেশি নিচ্ছে (৬০ সেকেন্ড)। আবার চেষ্টা করুন ⏳", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI turn failed", e)
            Result.failure(e)
        }
    }

    private fun processLocalFallbackTurn(
        userMessage: String,
        tasks: List<DailyTask>,
        permissions: DevicePermissions
    ): AiAssistantDirective {
        val lower = userMessage.lowercase()
        val aiName = permissions.assistantName.ifBlank { "সাথী" }
        val isShort = permissions.conversationStyle == "CONCISE"

        return when {
            lower.contains("ফ্ল্যাশ") || lower.contains("টর্চ") || lower.contains("flashlight") || lower.contains("torch") -> {
                val enable = !(lower.contains("বন্ধ") || lower.contains("off"))
                AiAssistantDirective(
                    reply = if (enable) "ঠিক আছে! ফ্ল্যাশলাইট চালু করছি 🔦✨" else "ঠিক আছে! ফ্ল্যাশলাইট বন্ধ করছি 🔦🌙",
                    actionType = "DEVICE_FLASHLIGHT",
                    paramBool = enable
                )
            }
            lower.contains("ভলিউম") || lower.contains("volume") -> {
                val pct = Regex("\\d+").find(lower)?.value?.toIntOrNull()?.coerceIn(0, 100) ?: 50
                AiAssistantDirective(
                    reply = "মিডিয়া ভলিউম $pct% এ সেট করছি 🔊✨",
                    actionType = "DEVICE_VOLUME",
                    paramInt = pct
                )
            }
            lower.contains("ব্রাইটনেস") || lower.contains("brightness") -> {
                val pct = Regex("\\d+").find(lower)?.value?.toIntOrNull()?.coerceIn(10, 100) ?: 70
                AiAssistantDirective(
                    reply = "স্ক্রিন ব্রাইটনেস $pct% করছি ☀️✨",
                    actionType = "DEVICE_BRIGHTNESS",
                    paramInt = pct
                )
            }
            lower.contains("টাইমার") || lower.contains("timer") -> {
                val mins = Regex("\\d+").find(lower)?.value?.toIntOrNull()?.coerceIn(1, 180) ?: 5
                AiAssistantDirective(
                    reply = "$mins মিনিটের টাইমার সেট করছি ⏱️✨",
                    actionType = "DEVICE_TIMER",
                    taskTitle = "$aiName টাইমার",
                    paramInt = mins * 60
                )
            }
            lower.contains("wifi") || lower.contains("ওয়াইফাই") -> {
                AiAssistantDirective(
                    reply = "Wi-Fi প্যানেল খুলছি 📶✨",
                    actionType = "DEVICE_WIFI_PANEL"
                )
            }
            lower.contains("bluetooth") || lower.contains("ব্লুটুথ") -> {
                AiAssistantDirective(
                    reply = "Bluetooth সেটিংস খুলছি 🔵✨",
                    actionType = "DEVICE_BLUETOOTH_SETTINGS"
                )
            }
            lower.contains("যোগ") || lower.contains("add") || lower.contains("রিমাইন্ড") -> {
                AiAssistantDirective(
                    reply = "দারুণ! আপনার কাজটি তালিকায় যোগ করেছি 📋✅",
                    actionType = "CREATE_TASK",
                    taskTitle = userMessage.take(80),
                    taskCategory = "PERSONAL",
                    taskPriority = "MEDIUM"
                )
            }
            lower.contains("কাজ") || lower.contains("রুটিন") || lower.contains("task") -> {
                val pending = tasks.filter { !it.completed }
                val summary = if (pending.isEmpty()) {
                    "আপনার এখন কোনো বাকি কাজ নেই! 🎉✨"
                } else {
                    "আপনার ${pending.size} টি কাজ বাকি আছে 📋💡:\n" +
                        pending.take(5).joinToString("\n") { "• ${it.title}" }
                }
                AiAssistantDirective(reply = summary, actionType = "NONE")
            }
            else -> {
                val greeting = if (isShort) {
                    "আমি $aiName 😊 আপনার কাজ ও ডিভাইস কমান্ডের জন্য প্রস্তুত ✨"
                } else {
                    "হ্যালো! আমি $aiName 🌟 আপনার কাজ সাজাতে বা ডিভাইস কন্ট্রোল করতে আমাকে নির্দেশ দিন 😊✨ (এই লোকাল বিল্ডে অফলাইন মোড চালু আছে; পূর্ণ Gemini চ্যাটের জন্য `google-services.json` যুক্ত করুন 💡)"
                }
                AiAssistantDirective(reply = greeting, actionType = "NONE")
            }
        }
    }

    suspend fun generateSmartDayPlan(tasks: List<DailyTask>, permissions: DevicePermissions): Result<String> {
        val pending = tasks.filter { !it.completed }
        if (pending.isEmpty()) {
            return Result.success("আপনার সব কাজ সম্পন্ন হয়েছে! আজকের দিনটি উপভোগ করুন 🎉✨")
        }
        if (!isFirebaseReady(context)) {
            val formatted = pending.mapIndexed { idx, task ->
                val timePart = if (task.scheduledTime.isNotBlank()) " (${task.scheduledTime})" else ""
                "${idx + 1}. ${task.title}$timePart ✅"
            }.joinToString("\n")
            return Result.success("আজকের কাজের তালিকা 📋✨:\n$formatted")
        }
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
            You are ${permissions.assistantName}. Style: ${style.instruction} ${permissions.customStylePrompt}
            Organize these pending daily tasks into an actionable, calm, realistic daily routine.
            $langNote
            Keep it concise (under 180 words) with bullet points.
            Tasks:
            $listSummary
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                plannerModel.generateContent(prompt)
            }
            val text = response.text?.trim().orEmpty()
            if (text.isEmpty()) {
                Result.failure(IllegalStateException("AI রুটিন তৈরি করতে পারেনি।"))
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
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "SathiAiEngine"
    }
}
