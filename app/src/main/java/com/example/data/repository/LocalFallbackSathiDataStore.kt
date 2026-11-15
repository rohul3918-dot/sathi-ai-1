package com.example.data.repository

import android.content.Context
import com.example.data.model.ChatMessageRecord
import com.example.data.model.DailyTask
import com.example.data.model.DeviceActionLog
import com.example.data.model.DevicePermissions
import com.example.data.model.UserProfile
import com.example.data.model.VoicePersona
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class CachedAuthSession(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = ""
)

internal class AuthSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(session: CachedAuthSession) {
        if (session.uid.isBlank()) return
        val o = JSONObject().apply {
            put("uid", session.uid.trim())
            put("displayName", session.displayName.trim())
            put("email", session.email.trim())
            put("photoUrl", session.photoUrl.trim())
        }
        prefs.edit().putString(KEY_ACTIVE_SESSION, o.toString()).commit()
    }

    fun load(): CachedAuthSession? {
        return try {
            val raw = prefs.getString(KEY_ACTIVE_SESSION, null) ?: return null
            val o = JSONObject(raw)
            val uid = o.optString("uid", "").trim()
            if (uid.isEmpty()) return null
            CachedAuthSession(
                uid = uid,
                displayName = o.optString("displayName", ""),
                email = o.optString("email", ""),
                photoUrl = o.optString("photoUrl", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_ACTIVE_SESSION).commit()
    }

    private companion object {
        const val PREFS_NAME = "sathi_auth_session"
        const val KEY_ACTIVE_SESSION = "active_user_session"
    }
}

internal class LocalProfileCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(userId: String): UserProfile? {
        return try {
            val raw = prefs.getString("$KEY_PREFIX$userId", null) ?: return null
            val o = JSONObject(raw)
            UserProfile(
                userId = userId,
                displayName = o.optString("displayName", ""),
                nickname = o.optString("nickname", ""),
                email = o.optString("email", ""),
                bio = o.optString("bio", ""),
                googlePhotoUrl = o.optString("googlePhotoUrl", ""),
                customPhotoUri = o.optString("customPhotoUri", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(userId: String, profile: UserProfile) {
        val o = JSONObject().apply {
            put("userId", userId)
            put("displayName", profile.displayName)
            put("nickname", profile.nickname)
            put("email", profile.email)
            put("bio", profile.bio)
            put("googlePhotoUrl", profile.googlePhotoUrl)
            put("customPhotoUri", profile.customPhotoUri)
        }
        prefs.edit().putString("$KEY_PREFIX$userId", o.toString()).commit()
    }

    private companion object {
        const val PREFS_NAME = "sathi_profile_cache"
        const val KEY_PREFIX = "profile_"
    }
}

/**
 * Local fallback store used when signed out or when the APK was built without Firebase google-services.json.
 * Allows the app to launch and persist user data locally without crashing.
 */
class LocalFallbackSathiDataStore(context: Context) : SathiDataStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val profileCache = LocalProfileCache(context.applicationContext)

    private val tasksFlow = MutableStateFlow(loadTasks())
    private val permissionsFlow = MutableStateFlow(loadPermissions())
    private val logsFlow = MutableStateFlow(loadLogs())
    private val messagesFlow = MutableStateFlow(loadMessages())
    private val profilesFlow = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

    override fun observeTasks(userId: String): Flow<List<DailyTask>> = tasksFlow

    override suspend fun addTask(
        userId: String,
        title: String,
        note: String,
        category: String,
        priority: String,
        scheduledTime: String
    ): Result<String> {
        val cleanTitle = title.trim().take(160)
        if (cleanTitle.isEmpty()) return Result.failure(IllegalArgumentException("Empty title"))
        val id = UUID.randomUUID().toString()
        val newTask = DailyTask(
            id = id,
            userId = userId,
            title = cleanTitle,
            note = note.trim().take(500),
            category = category,
            priority = priority,
            scheduledTime = scheduledTime.trim().take(40),
            completed = false,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        val updated = (listOf(newTask) + tasksFlow.value).sortedBy { it.completed }
        tasksFlow.value = updated
        persistTasks(updated)
        return Result.success(id)
    }

    override suspend fun updateTask(userId: String, task: DailyTask): Result<Unit> {
        val updated = tasksFlow.value.map {
            if (it.id == task.id) task.copy(updatedAt = Timestamp.now()) else it
        }.sortedBy { it.completed }
        tasksFlow.value = updated
        persistTasks(updated)
        return Result.success(Unit)
    }

    override suspend fun toggleTaskCompletion(userId: String, task: DailyTask): Result<Unit> {
        val updated = tasksFlow.value.map {
            if (it.id == task.id) it.copy(completed = !it.completed, updatedAt = Timestamp.now()) else it
        }.sortedBy { it.completed }
        tasksFlow.value = updated
        persistTasks(updated)
        return Result.success(Unit)
    }

    override suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val updated = tasksFlow.value.filterNot { it.id == taskId }
        tasksFlow.value = updated
        persistTasks(updated)
        return Result.success(Unit)
    }

    override fun observePermissions(userId: String): Flow<DevicePermissions> = permissionsFlow

    override suspend fun savePermissions(userId: String, permissions: DevicePermissions): Result<Unit> {
        val persona = VoicePersona.fromKey(permissions.voicePersona, permissions.voiceGender)
        val sanitized = permissions.copy(
            userId = userId,
            assistantName = permissions.assistantName.trim().ifBlank { "সাথী" }.take(40),
            customStylePrompt = permissions.customStylePrompt.trim().take(300),
            voiceGender = persona.voiceGender,
            voicePersona = persona.key
        )
        permissionsFlow.value = sanitized
        persistPermissions(sanitized)
        return Result.success(Unit)
    }

    override fun observeActionLogs(userId: String): Flow<List<DeviceActionLog>> = logsFlow

    override suspend fun logDeviceAction(
        userId: String,
        actionType: String,
        summary: String,
        status: String,
        permissionKey: String
    ): Result<String> {
        val id = UUID.randomUUID().toString()
        val entry = DeviceActionLog(
            id = id,
            userId = userId,
            actionType = actionType.take(64),
            summary = summary.take(200),
            status = status,
            permissionKey = permissionKey.take(64),
            createdAt = Timestamp.now()
        )
        val updated = (listOf(entry) + logsFlow.value).take(40)
        logsFlow.value = updated
        persistLogs(updated)
        return Result.success(id)
    }

    override fun observeMessages(userId: String): Flow<List<ChatMessageRecord>> = messagesFlow

    override suspend fun addMessage(
        userId: String,
        sender: String,
        text: String,
        actionLabel: String,
        actionStatus: String,
        clientOrder: Long
    ): Result<String> {
        val cleanText = text.trim().take(4000)
        if (cleanText.isEmpty()) return Result.failure(IllegalArgumentException("Empty text"))
        val id = UUID.randomUUID().toString()
        val msg = ChatMessageRecord(
            id = id,
            userId = userId,
            sender = sender,
            text = cleanText,
            actionLabel = actionLabel.take(160),
            actionStatus = actionStatus,
            clientOrder = clientOrder,
            createdAt = Timestamp.now()
        )
        val updated = (messagesFlow.value + msg).sortedBy { it.clientOrder }
        messagesFlow.value = updated
        persistMessages(updated)
        return Result.success(id)
    }

    override suspend fun clearMessages(userId: String): Result<Unit> {
        messagesFlow.value = emptyList()
        prefs.edit().remove(KEY_MESSAGES).apply()
        return Result.success(Unit)
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile> {
        if (!profilesFlow.value.containsKey(userId)) {
            val cached = profileCache.load(userId) ?: UserProfile(userId = userId)
            profilesFlow.value = profilesFlow.value + (userId to cached)
        }
        return profilesFlow.map { map ->
            map[userId] ?: profileCache.load(userId) ?: UserProfile(userId = userId)
        }
    }

    override suspend fun saveUserProfile(userId: String, profile: UserProfile): Result<Unit> {
        val sanitized = profile.copy(
            userId = userId,
            displayName = profile.displayName.trim().take(80),
            nickname = profile.nickname.trim().take(50),
            email = profile.email.trim().take(120),
            bio = profile.bio.trim().take(300),
            googlePhotoUrl = profile.googlePhotoUrl.trim().take(500),
            customPhotoUri = profile.customPhotoUri.trim().take(500),
            updatedAt = Timestamp.now()
        )
        profileCache.save(userId, sanitized)
        profilesFlow.value = profilesFlow.value + (userId to sanitized)
        return Result.success(Unit)
    }

    override suspend fun syncSignedInUser(
        userId: String,
        displayName: String,
        email: String,
        photoUrl: String
    ): Result<Unit> {
        val existing = profilesFlow.value[userId] ?: profileCache.load(userId) ?: UserProfile(userId = userId)
        val merged = existing.copy(
            userId = userId,
            displayName = existing.displayName.ifBlank { displayName.trim() }.take(80),
            email = email.trim().ifBlank { existing.email }.take(120),
            googlePhotoUrl = photoUrl.trim().ifBlank { existing.googlePhotoUrl }.take(500),
            updatedAt = Timestamp.now()
        )
        profileCache.save(userId, merged)
        profilesFlow.value = profilesFlow.value + (userId to merged)
        return Result.success(Unit)
    }

    private fun loadTasks(): List<DailyTask> {
        return try {
            val arr = JSONArray(prefs.getString(KEY_TASKS, "[]") ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DailyTask(
                    id = o.optString("id"),
                    userId = o.optString("userId", "local"),
                    title = o.optString("title"),
                    note = o.optString("note"),
                    category = o.optString("category", "PERSONAL"),
                    priority = o.optString("priority", "MEDIUM"),
                    scheduledTime = o.optString("scheduledTime"),
                    completed = o.optBoolean("completed", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistTasks(list: List<DailyTask>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("userId", t.userId)
                put("title", t.title)
                put("note", t.note)
                put("category", t.category)
                put("priority", t.priority)
                put("scheduledTime", t.scheduledTime)
                put("completed", t.completed)
            })
        }
        prefs.edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    private fun loadPermissions(): DevicePermissions {
        return try {
            val raw = prefs.getString(KEY_PERMISSIONS, null) ?: return DevicePermissions(userId = "local")
            val o = JSONObject(raw)
            DevicePermissions(
                userId = o.optString("userId", "local"),
                masterEnabled = o.optBoolean("masterEnabled", true),
                allowVoiceMic = o.optBoolean("allowVoiceMic", true),
                allowTtsSpeech = o.optBoolean("allowTtsSpeech", true),
                allowFlashlight = o.optBoolean("allowFlashlight", false),
                allowVolumeControl = o.optBoolean("allowVolumeControl", true),
                allowDisplayAndSettings = o.optBoolean("allowDisplayAndSettings", false),
                allowAlarmsAndTimers = o.optBoolean("allowAlarmsAndTimers", true),
                allowDialer = o.optBoolean("allowDialer", false),
                allowNotifications = o.optBoolean("allowNotifications", true),
                assistantLanguage = o.optString("assistantLanguage", "bilingual"),
                assistantName = o.optString("assistantName", "সাথী").ifBlank { "সাথী" },
                conversationStyle = o.optString("conversationStyle", "FRIENDLY"),
                customStylePrompt = o.optString("customStylePrompt", ""),
                voiceGender = o.optString("voiceGender", "FEMALE"),
                voiceLocale = o.optString("voiceLocale", "bn-BD"),
                voicePersona = VoicePersona.fromKey(
                    o.optString("voicePersona", ""),
                    o.optString("voiceGender", "FEMALE")
                ).key
            )
        } catch (_: Exception) {
            DevicePermissions(userId = "local")
        }
    }

    private fun persistPermissions(p: DevicePermissions) {
        val o = JSONObject().apply {
            put("userId", p.userId)
            put("masterEnabled", p.masterEnabled)
            put("allowVoiceMic", p.allowVoiceMic)
            put("allowTtsSpeech", p.allowTtsSpeech)
            put("allowFlashlight", p.allowFlashlight)
            put("allowVolumeControl", p.allowVolumeControl)
            put("allowDisplayAndSettings", p.allowDisplayAndSettings)
            put("allowAlarmsAndTimers", p.allowAlarmsAndTimers)
            put("allowDialer", p.allowDialer)
            put("allowNotifications", p.allowNotifications)
            put("assistantLanguage", p.assistantLanguage)
            put("assistantName", p.assistantName)
            put("conversationStyle", p.conversationStyle)
            put("customStylePrompt", p.customStylePrompt)
            put("voiceGender", p.voiceGender)
            put("voiceLocale", p.voiceLocale)
            put("voicePersona", p.voicePersona)
        }
        prefs.edit().putString(KEY_PERMISSIONS, o.toString()).apply()
    }

    private fun loadLogs(): List<DeviceActionLog> {
        return try {
            val arr = JSONArray(prefs.getString(KEY_LOGS, "[]") ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DeviceActionLog(
                    id = o.optString("id"),
                    userId = o.optString("userId", "local"),
                    actionType = o.optString("actionType"),
                    summary = o.optString("summary"),
                    status = o.optString("status", "EXECUTED"),
                    permissionKey = o.optString("permissionKey")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistLogs(list: List<DeviceActionLog>) {
        val arr = JSONArray()
        list.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id)
                put("userId", l.userId)
                put("actionType", l.actionType)
                put("summary", l.summary)
                put("status", l.status)
                put("permissionKey", l.permissionKey)
            })
        }
        prefs.edit().putString(KEY_LOGS, arr.toString()).apply()
    }

    private fun loadMessages(): List<ChatMessageRecord> {
        return try {
            val arr = JSONArray(prefs.getString(KEY_MESSAGES, "[]") ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessageRecord(
                    id = o.optString("id"),
                    userId = o.optString("userId", "local"),
                    sender = o.optString("sender", "ASSISTANT"),
                    text = o.optString("text"),
                    actionLabel = o.optString("actionLabel"),
                    actionStatus = o.optString("actionStatus", "NONE"),
                    clientOrder = o.optLong("clientOrder", i.toLong())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistMessages(list: List<ChatMessageRecord>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("userId", m.userId)
                put("sender", m.sender)
                put("text", m.text)
                put("actionLabel", m.actionLabel)
                put("actionStatus", m.actionStatus)
                put("clientOrder", m.clientOrder)
            })
        }
        prefs.edit().putString(KEY_MESSAGES, arr.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "sathi_local_store"
        const val KEY_TASKS = "tasks"
        const val KEY_PERMISSIONS = "permissions"
        const val KEY_LOGS = "logs"
        const val KEY_MESSAGES = "messages"
    }
}
