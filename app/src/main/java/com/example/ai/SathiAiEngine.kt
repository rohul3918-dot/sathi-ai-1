package com.example.ai

import android.util.Log
import com.example.data.model.ChatMessageRecord
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

class SathiAiEngine {

    private val assistantSchema = Schema.obj(
        properties = mapOf(
            "reply" to Schema.string(
                description = "Conversational, friendly reply to the user (natural Bengali if user speaks Bengali or bilingual mode, English if English)."
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
                    You are Sathi AI (সাথী এআই), a warm, intelligent, respectful personal AI assistant on Android.
                    Core duties:
                    1. Talk naturally with the user (prefer natural Bengali unless user explicitly writes in English or language preference is English).
                    2. Help organize daily tasks: when user asks to add/remind/schedule a task, set actionType="CREATE_TASK" with concise taskTitle, category, priority, and taskTime. When they mark a task done or ask to remove one, match the closest task ID from the provided task list and set COMPLETE_TASK or DELETE_TASK.
                    3. Execute device controls ONLY if the user requested them. Always check the provided Permission Matrix:
                       - masterEnabled must be true for ANY DEVICE_* action.
                       - DEVICE_FLASHLIGHT requires allowFlashlight=true
                       - DEVICE_VOLUME requires allowVolumeControl=true
                       - DEVICE_BRIGHTNESS, DEVICE_WIFI_PANEL, DEVICE_BLUETOOTH_SETTINGS require allowDisplayAndSettings=true
                       - DEVICE_ALARM, DEVICE_TIMER require allowAlarmsAndTimers=true
                       - DEVICE_DIAL requires allowDialer=true
                       - DEVICE_NOTIFY requires allowNotifications=true
                       If a device permission is FALSE, still output the requested actionType ONLY IF you want the app to log the permission block, OR set actionType="NONE" and politely tell the user in Bengali which permission toggle to turn on in the Permission tab. Actually, ALWAYS output the matching DEVICE_* actionType when the user explicitly requests a device control so the app's permission gate enforces it deterministically and records it transparently—and mention in `reply` if it looks locked!
                    4. For DEVICE_ALARM, put 24-hour time "HH:MM" in paramText (e.g., "07:30") and alarm label in taskTitle.
                    5. For DEVICE_TIMER, put total seconds in paramInt and timer label in taskTitle.
                    6. For DEVICE_VOLUME or DEVICE_BRIGHTNESS, put 0..100 integer in paramInt.
                    7. For DEVICE_FLASHLIGHT, put true (on) or false (off) in paramBool.
                    8. For DEVICE_DIAL, put the digits/phone number in paramText.
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

    suspend fun processUserTurn(
        userMessage: String,
        tasks: List<DailyTask>,
        permissions: DevicePermissions,
        recentHistory: List<ChatMessageRecord>
    ): Result<AiAssistantDirective> {
        val taskContext = if (tasks.isEmpty()) {
            "No tasks currently saved."
        } else {
            tasks.take(25).joinToString("\n") { t ->
                "- id=${t.id} | title=${t.title} | cat=${t.category} | prio=${t.priority} | time=${t.scheduledTime} | done=${t.completed}"
            }
        }

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
            assistantLanguage=${permissions.assistantLanguage}
        """.trimIndent()

        val historyText = recentHistory.takeLast(8).joinToString("\n") { msg ->
            "${msg.sender}: ${msg.text}"
        }

        val prompt = """
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
                    reply = json.optString("reply", "আমি আপনার কথা শুনেছি।"),
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
            Result.failure(IllegalStateException("AI উত্তর দিতে সময় বেশি নিচ্ছে (৬০ সেকেন্ড)। আবার চেষ্টা করুন।", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI turn failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateSmartDayPlan(tasks: List<DailyTask>, language: String): Result<String> {
        val pending = tasks.filter { !it.completed }
        if (pending.isEmpty()) {
            return Result.success("আপনার সব কাজ সম্পন্ন হয়েছে! আজকের দিনটি উপভোগ করুন।")
        }
        val listSummary = pending.joinToString("\n") {
            "- ${it.title} (${it.category}, priority=${it.priority}, time=${it.scheduledTime.ifBlank { "unscheduled" }})"
        }
        val langNote = if (language == "en") "Write in clear English." else "Write in warm, well-structured Bengali with clear time blocks (সকাল, দুপুর, বিকাল, রাত)."
        val prompt = """
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
            Result.failure(IllegalStateException("রুটিন তৈরি করতে সময় শেষ হয়েছে। আবার চেষ্টা করুন।", e))
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
