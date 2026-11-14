package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class DailyTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val note: String = "",
    val category: String = "PERSONAL",
    val priority: String = "MEDIUM",
    val scheduledTime: String = "",
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class TaskCategory(val key: String, val bnLabel: String, val enLabel: String) {
    WORK("WORK", "কাজ", "Work"),
    PERSONAL("PERSONAL", "ব্যক্তিগত", "Personal"),
    HEALTH("HEALTH", "স্বাস্থ্য", "Health"),
    ROUTINE("ROUTINE", "রুটিন", "Routine");

    companion object {
        fun fromKey(key: String): TaskCategory =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PERSONAL
    }
}

enum class TaskPriority(val key: String, val bnLabel: String, val enLabel: String) {
    HIGH("HIGH", "জরুরি", "High"),
    MEDIUM("MEDIUM", "মাঝারি", "Medium"),
    LOW("LOW", "সাধারণ", "Low");

    companion object {
        fun fromKey(key: String): TaskPriority =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: MEDIUM
    }
}

@IgnoreExtraProperties
data class DevicePermissions(
    @DocumentId val id: String = "permissions",
    val userId: String = "",
    val masterEnabled: Boolean = true,
    val allowVoiceMic: Boolean = true,
    val allowTtsSpeech: Boolean = true,
    val allowFlashlight: Boolean = false,
    val allowVolumeControl: Boolean = true,
    val allowDisplayAndSettings: Boolean = false,
    val allowAlarmsAndTimers: Boolean = true,
    val allowDialer: Boolean = false,
    val allowNotifications: Boolean = true,
    val assistantLanguage: String = "bilingual",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isAllowed(key: String): Boolean {
        if (!masterEnabled) return false
        return when (key) {
            "allowVoiceMic" -> allowVoiceMic
            "allowTtsSpeech" -> allowTtsSpeech
            "allowFlashlight" -> allowFlashlight
            "allowVolumeControl" -> allowVolumeControl
            "allowDisplayAndSettings" -> allowDisplayAndSettings
            "allowAlarmsAndTimers" -> allowAlarmsAndTimers
            "allowDialer" -> allowDialer
            "allowNotifications" -> allowNotifications
            else -> false
        }
    }
}

@IgnoreExtraProperties
data class DeviceActionLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actionType: String = "",
    val summary: String = "",
    val status: String = "EXECUTED",
    val permissionKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = "ASSISTANT",
    val text: String = "",
    val actionLabel: String = "",
    val actionStatus: String = "NONE",
    val clientOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
