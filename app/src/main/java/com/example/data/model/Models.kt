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

enum class ConversationStyle(val key: String, val bnLabel: String, val instruction: String) {
    FRIENDLY(
        "FRIENDLY",
        "বন্ধুত্বপূর্ণ",
        "Speak warmly, cheerfully, and encouragingly like a caring close companion."
    ),
    POLITE(
        "POLITE",
        "ভদ্র ও মার্জিত",
        "Speak respectfully and politely (use 'আপনি' in Bengali), calm and courteous."
    ),
    CASUAL(
        "CASUAL",
        "সহজ ও ঘরোয়া",
        "Speak naturally and casually (use warm 'তুমি' in Bengali), relaxed and conversational."
    ),
    CONCISE(
        "CONCISE",
        "সংক্ষিপ্ত ও দ্রুত",
        "Keep answers crisp, ultra-short, and direct (1-2 sentences max) while staying pleasant."
    ),
    CUSTOM(
        "CUSTOM",
        "কাস্টম স্টাইল",
        "Follow the user's custom style instructions carefully."
    );

    companion object {
        fun fromKey(key: String): ConversationStyle =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FRIENDLY
    }
}

enum class VoiceGender(val key: String, val bnLabel: String) {
    FEMALE("FEMALE", "নারী কণ্ঠ (Female)"),
    MALE("MALE", "পুরুষ কণ্ঠ (Male)");

    companion object {
        fun fromKey(key: String): VoiceGender =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FEMALE
    }
}

enum class VoiceLocaleOption(val tag: String, val bnLabel: String) {
    BN_BD("bn-BD", "বাংলা (BD)"),
    BN_IN("bn-IN", "বাংলা (IN)"),
    EN_US("en-US", "English (US)");

    companion object {
        fun fromTag(tag: String): VoiceLocaleOption =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: BN_BD
    }
}

enum class VoicePersona(
    val key: String,
    val displayNameBn: String,
    val shortLabel: String,
    val badge: String,
    val descriptionBn: String,
    val voiceGender: String,
    val pitch: Float,
    val speechRate: Float,
    val voiceVariantIndex: Int,
    val suggestedStyle: ConversationStyle,
    val personaToneHint: String
) {
    FEMALE_WARM(
        key = "FEMALE_WARM",
        displayNameBn = "নারী কণ্ঠ ১ • স্নিগ্ধ",
        shortLabel = "নারী কণ্ঠ ১",
        badge = "Female Voice 1",
        descriptionBn = "মৃদু, আন্তরিক ও বন্ধুত্বপূর্ণ নারী কণ্ঠ (দৈনন্দিন আড্ডা ও সহায়তার জন্য)",
        voiceGender = "FEMALE",
        pitch = 1.03f,
        speechRate = 0.96f,
        voiceVariantIndex = 0,
        suggestedStyle = ConversationStyle.FRIENDLY,
        personaToneHint = "Warm, caring, and reassuring companion tone."
    ),
    FEMALE_CRISP(
        key = "FEMALE_CRISP",
        displayNameBn = "নারী কণ্ঠ ২ • চটপটে",
        shortLabel = "নারী কণ্ঠ ২",
        badge = "Female Voice 2",
        descriptionBn = "স্পষ্ট, উজ্জ্বল ও চটপটে নারী কণ্ঠ (দ্রুত তথ্য ও রিমাইন্ডারের জন্য)",
        voiceGender = "FEMALE",
        pitch = 1.09f,
        speechRate = 1.02f,
        voiceVariantIndex = 1,
        suggestedStyle = ConversationStyle.CONCISE,
        personaToneHint = "Bright, crisp, and energetic helper tone."
    ),
    MALE_CALM(
        key = "MALE_CALM",
        displayNameBn = "পুরুষ কণ্ঠ ১ • গম্ভীর",
        shortLabel = "পুরুষ কণ্ঠ ১",
        badge = "Male Voice 1",
        descriptionBn = "শান্ত, গভীর ও মার্জিত পুরুষ কণ্ঠ (গুরুত্বপূর্ণ কাজ ও পরামর্শের জন্য)",
        voiceGender = "MALE",
        pitch = 0.90f,
        speechRate = 0.95f,
        voiceVariantIndex = 0,
        suggestedStyle = ConversationStyle.POLITE,
        personaToneHint = "Calm, steady, respectful, and thoughtful tone."
    ),
    MALE_ENERGETIC(
        key = "MALE_ENERGETIC",
        displayNameBn = "পুরুষ কণ্ঠ ২ • প্রাণবন্ত",
        shortLabel = "পুরুষ কণ্ঠ ২",
        badge = "Male Voice 2",
        descriptionBn = "সহজ, স্বতঃস্ফূর্ত ও প্রাণবন্ত পুরুষ কণ্ঠ (সক্রিয় আড্ডা ও রুটিনের জন্য)",
        voiceGender = "MALE",
        pitch = 0.96f,
        speechRate = 1.01f,
        voiceVariantIndex = 1,
        suggestedStyle = ConversationStyle.CASUAL,
        personaToneHint = "Lively, natural, upbeat, and friendly buddy tone."
    );

    companion object {
        fun fromKey(key: String, fallbackGender: String = "FEMALE"): VoicePersona {
            val direct = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
            if (direct != null) return direct
            return if (fallbackGender.equals("MALE", ignoreCase = true)) MALE_CALM else FEMALE_WARM
        }

        fun defaultForGender(gender: String): VoicePersona =
            if (gender.equals("MALE", ignoreCase = true)) MALE_CALM else FEMALE_WARM
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
    val assistantName: String = "সাথী",
    val conversationStyle: String = "FRIENDLY",
    val customStylePrompt: String = "",
    val voiceGender: String = "FEMALE",
    val voiceLocale: String = "bn-BD",
    val voicePersona: String = "FEMALE_WARM",
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

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "profile",
    val userId: String = "",
    val displayName: String = "",
    val nickname: String = "",
    val email: String = "",
    val bio: String = "",
    val googlePhotoUrl: String = "",
    val customPhotoUri: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectivePhotoUrl: String
        get() = customPhotoUri.ifBlank { googlePhotoUrl }

    val preferredAddressName: String
        get() = nickname.trim().ifBlank { displayName.trim() }
}
