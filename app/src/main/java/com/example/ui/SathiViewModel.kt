package com.example.ui

import android.Manifest
import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAssistantDirective
import com.example.ai.SathiAiEngine
import com.example.data.model.ChatMessageRecord
import com.example.data.model.ConversationStyle
import com.example.data.model.DailyTask
import com.example.data.model.DeviceActionLog
import com.example.data.model.DevicePermissions
import com.example.data.model.UserProfile
import com.example.data.model.VoicePersona
import com.example.data.repository.SathiDataStore
import com.example.device.DeviceActionOutcome
import com.example.device.DeviceControlManager
import com.example.device.VoiceAssistantManager
import com.example.device.VoiceListeningState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

data class PendingPermissionPrompt(
    val permissionKey: String,
    val titleBn: String,
    val osPermission: String? = null
)

data class ChatTurnError(
    val message: String,
    val failedPrompt: String
)

class SathiViewModel(
    private val repository: SathiDataStore,
    private val aiEngine: SathiAiEngine,
    val deviceManager: DeviceControlManager,
    val voiceManager: VoiceAssistantManager,
    private val currentUserId: String,
    initialUserProfile: UserProfile = UserProfile(userId = currentUserId)
) : ViewModel() {

    val tasksState: StateFlow<DataUiState<List<DailyTask>>> =
        repository.observeTasks(currentUserId)
            .map<List<DailyTask>, DataUiState<List<DailyTask>>> { DataUiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Failed to observe tasks", root)
                emit(DataUiState.Error(root.message ?: "কাজের তালিকা লোড করা যায়নি"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val permissionsState: StateFlow<DevicePermissions> =
        repository.observePermissions(currentUserId)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Failed to observe permissions", error)
                emit(DevicePermissions(userId = currentUserId))
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000L),
                DevicePermissions(userId = currentUserId)
            )

    val actionLogsState: StateFlow<List<DeviceActionLog>> =
        repository.observeActionLogs(currentUserId)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Failed to observe action logs", error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val messagesState: StateFlow<List<ChatMessageRecord>> =
        repository.observeMessages(currentUserId)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Failed to observe messages", error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userProfileState: StateFlow<UserProfile> =
        repository.observeUserProfile(currentUserId)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Failed to observe user profile", error)
                emit(initialUserProfile)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000L),
                initialUserProfile
            )

    val listeningState: StateFlow<VoiceListeningState> = voiceManager.listeningState
    val isFlashlightOn: StateFlow<Boolean> = deviceManager.isFlashlightOn
    val currentVolumePercent: StateFlow<Int> = deviceManager.currentVolumePercent
    val appBrightnessPercent: StateFlow<Int> = deviceManager.appBrightnessPercent

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _streamingReplyText = MutableStateFlow<String?>(null)
    val streamingReplyText: StateFlow<String?> = _streamingReplyText.asStateFlow()

    private val _chatTurnError = MutableStateFlow<ChatTurnError?>(null)
    val chatTurnError: StateFlow<ChatTurnError?> = _chatTurnError.asStateFlow()

    private val _smartPlanText = MutableStateFlow<String?>(null)
    val smartPlanText: StateFlow<String?> = _smartPlanText.asStateFlow()

    private val _isGeneratingPlan = MutableStateFlow(false)
    val isGeneratingPlan: StateFlow<Boolean> = _isGeneratingPlan.asStateFlow()

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

    private val _pendingPermissionPrompt = MutableStateFlow<PendingPermissionPrompt?>(null)
    val pendingPermissionPrompt: StateFlow<PendingPermissionPrompt?> = _pendingPermissionPrompt.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun showMessage(msg: String) {
        _statusBanner.value = msg
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun dismissAiError() {
        _chatTurnError.value = null
    }

    fun dismissPermissionPrompt() {
        _pendingPermissionPrompt.value = null
    }

    fun sendUserMessage(
        rawText: String,
        activity: Activity?,
        skipAddingUserBubble: Boolean = false
    ) {
        val cleanText = rawText.trim()
        if (cleanText.isEmpty() || _isAiThinking.value) return

        viewModelScope.launch {
            _isAiThinking.value = true
            _streamingReplyText.value = null
            _chatTurnError.value = null
            _pendingPermissionPrompt.value = null
            val baseOrder = System.currentTimeMillis()

            if (!skipAddingUserBubble) {
                repository.addMessage(
                    userId = currentUserId,
                    sender = "USER",
                    text = cleanText,
                    clientOrder = baseOrder
                )
            }

            val currentTasks = (tasksState.value as? DataUiState.Success)?.data ?: emptyList()
            val currentPerms = permissionsState.value
            val recentHistory = messagesState.value
            val currentProfile = userProfileState.value

            val aiResult = aiEngine.processUserTurn(
                userMessage = cleanText,
                tasks = currentTasks,
                permissions = currentPerms,
                recentHistory = recentHistory,
                userProfile = currentProfile,
                onPartialReply = { partial ->
                    _streamingReplyText.value = partial
                }
            )

            _streamingReplyText.value = null

            aiResult.fold(
                onSuccess = { directive ->
                    val (actionLabel, actionStatus, finalReply) = executeDirective(
                        directive = directive,
                        tasks = currentTasks,
                        permissions = currentPerms,
                        activity = activity
                    )
                    repository.addMessage(
                        userId = currentUserId,
                        sender = "ASSISTANT",
                        text = finalReply,
                        actionLabel = actionLabel,
                        actionStatus = actionStatus,
                        clientOrder = baseOrder + 1
                    )
                    if (currentPerms.allowTtsSpeech) {
                        val persona = VoicePersona.fromKey(currentPerms.voicePersona, currentPerms.voiceGender)
                        voiceManager.speakWithPersona(
                            text = finalReply,
                            persona = persona,
                            voiceLocale = currentPerms.voiceLocale
                        )
                    }
                },
                onFailure = { err ->
                    val message = err.localizedMessage ?: "অনলাইন AI এর সাথে সংযোগ ব্যর্থ হয়েছে।"
                    _chatTurnError.value = ChatTurnError(
                        message = message,
                        failedPrompt = cleanText
                    )
                }
            )
            _isAiThinking.value = false
        }
    }

    fun retryAssistantMessage(assistantMessage: ChatMessageRecord, activity: Activity?) {
        val list = messagesState.value
        val idx = list.indexOfFirst { it.id == assistantMessage.id && it.clientOrder == assistantMessage.clientOrder }
        val precedingUserPrompt = if (idx > 0) {
            list.subList(0, idx).lastOrNull { it.sender == "USER" }?.text
        } else {
            list.lastOrNull { it.sender == "USER" }?.text
        }
        if (!precedingUserPrompt.isNullOrBlank()) {
            sendUserMessage(precedingUserPrompt, activity, skipAddingUserBubble = true)
        }
    }

    fun retryLastFailedTurn(activity: Activity?) {
        val failed = _chatTurnError.value?.failedPrompt ?: return
        sendUserMessage(failed, activity, skipAddingUserBubble = true)
    }

    fun speakMessage(text: String) {
        val perms = permissionsState.value
        val persona = VoicePersona.fromKey(perms.voicePersona, perms.voiceGender)
        voiceManager.speakWithPersona(text, persona, perms.voiceLocale)
    }

    private suspend fun executeDirective(
        directive: AiAssistantDirective,
        tasks: List<DailyTask>,
        permissions: DevicePermissions,
        activity: Activity?
    ): Triple<String, String, String> {
        return when (directive.actionType) {
            "CREATE_TASK" -> {
                val title = directive.taskTitle.ifBlank { "নতুন কাজ" }
                val res = repository.addTask(
                    userId = currentUserId,
                    title = title,
                    note = directive.taskNote,
                    category = directive.taskCategory,
                    priority = directive.taskPriority,
                    scheduledTime = directive.taskTime
                )
                if (res.isSuccess) {
                    Triple("কাজ যোগ হয়েছে: $title", "TASK_UPDATED", directive.reply)
                } else {
                    Triple("কাজ সংরক্ষণ করা যায়নি", "NONE", directive.reply)
                }
            }
            "COMPLETE_TASK" -> {
                val target = tasks.firstOrNull { it.id == directive.targetTaskId }
                    ?: tasks.firstOrNull { !it.completed && it.title.contains(directive.taskTitle, ignoreCase = true) }
                if (target != null && !target.completed) {
                    repository.toggleTaskCompletion(currentUserId, target)
                    Triple("সম্পন্ন হয়েছে: ${target.title}", "TASK_UPDATED", directive.reply)
                } else {
                    Triple("", "NONE", directive.reply)
                }
            }
            "DELETE_TASK" -> {
                val target = tasks.firstOrNull { it.id == directive.targetTaskId }
                if (target != null) {
                    repository.deleteTask(currentUserId, target.id)
                    Triple("মুছে ফেলা হয়েছে: ${target.title}", "TASK_UPDATED", directive.reply)
                } else {
                    Triple("", "NONE", directive.reply)
                }
            }
            "DEVICE_FLASHLIGHT" -> handleDeviceCommand(
                actionType = "DEVICE_FLASHLIGHT",
                permissionKey = "allowFlashlight",
                permissionLabelBn = "ফ্ল্যাশলাইট কন্ট্রোল",
                permissions = permissions
            ) {
                deviceManager.setFlashlight(directive.paramBool)
            }.let { Triple(it.first, it.second, it.third ?: directive.reply) }

            "DEVICE_VOLUME" -> handleDeviceCommand(
                actionType = "DEVICE_VOLUME",
                permissionKey = "allowVolumeControl",
                permissionLabelBn = "অডিও ও ভলিউম কন্ট্রোল",
                permissions = permissions
            ) {
                deviceManager.setMediaVolumePercent(directive.paramInt)
            }.let { Triple(it.first, it.second, it.third ?: directive.reply) }

            "DEVICE_BRIGHTNESS" -> handleDeviceCommand(
                actionType = "DEVICE_BRIGHTNESS",
                permissionKey = "allowDisplayAndSettings",
                permissionLabelBn = "ডিসপ্লে ও ব্রাইটনেস কন্ট্রোল",
                permissions = permissions
            ) {
                deviceManager.setAppBrightness(activity, directive.paramInt)
            }.let { Triple(it.first, it.second, it.third ?: directive.reply) }

            "DEVICE_WIFI_PANEL" -> handleDeviceCommand(
                actionType = "DEVICE_WIFI_PANEL",
                permissionKey = "allowDisplayAndSettings",
                permissionLabelBn = "ডিসপ্লে ও কানেক্টিভিটি",
                permissions = permissions
            ) {
                deviceManager.openConnectivitySettings("WIFI")
            }.let { Triple(it.first, it.second, it.third ?: directive.reply) }

            "DEVICE_BLUETOOTH_SETTINGS" -> handleDeviceCommand(
                actionType = "DEVICE_BLUETOOTH_SETTINGS",
                permissionKey = "allowDisplayAndSettings",
                permissionLabelBn = "ডিসপ্লে ও কানেক্টিভিটি",
                permissions = permissions
            ) {
                deviceManager.openConnectivitySettings("BLUETOOTH")
            }.let { Triple(it.first, it.second, it.third ?: directive.reply) }

            "DEVICE_ALARM" -> handleDeviceCommand(
                actionType = "DEVICE_ALARM",
                permissionKey = "allowAlarmsAndTimers",
                permissionLabelBn = "অ্যালার্ম ও টাইমার",
                permissions = permissions
            ) {
                val parts = directive.paramText.split(":")
                val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 7
                val min = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                deviceManager.setAlarm(hour, min, directive.taskTitle)
            }.let { Triple(it.first, it.second, it.third ?: directive.reply) }

            "DEVICE_TIMER" -> handleDeviceCommand(
                actionType = "DEVICE_TIMER",
                permissionKey = "allowAlarmsAndTimers",
                permissionLabelBn = "অ্যালার্ম ও টাইমার",
                permissions = permissions
            ) {
                val secs = if (directive.paramInt > 0) directive.paramInt else 60
                deviceManager.setTimer(secs, directive.taskTitle)
            }.let { Triple(it.first, it.second, it.third ?: directive.reply) }

            "DEVICE_DIAL" -> handleDeviceCommand(
                actionType = "DEVICE_DIAL",
                permissionKey = "allowDialer",
                permissionLabelBn = "ফোন ডায়ালার",
                permissions = permissions
            ) {
                deviceManager.openDialer(directive.paramText)
            }.let { Triple(it.first, it.second, it.third ?: directive.reply) }

            "DEVICE_NOTIFY" -> handleDeviceCommand(
                actionType = "DEVICE_NOTIFY",
                permissionKey = "allowNotifications",
                permissionLabelBn = "নোটিফিকেশন ও রিমাইন্ডার",
                permissions = permissions
            ) {
                val aiName = permissions.assistantName.ifBlank { "সাথী" }
                deviceManager.postReminderNotification(
                    directive.taskTitle.ifBlank { "$aiName রিমাইন্ডার" },
                    directive.paramText.ifBlank { directive.reply }
                )
            }.let { Triple(it.first, it.second, it.third ?: directive.reply) }

            else -> Triple("", "NONE", directive.reply)
        }
    }

    private suspend fun handleDeviceCommand(
        actionType: String,
        permissionKey: String,
        permissionLabelBn: String,
        permissions: DevicePermissions,
        executor: () -> DeviceActionOutcome
    ): Triple<String, String, String?> {
        if (!permissions.isAllowed(permissionKey)) {
            val blockedSummary = if (!permissions.masterEnabled) {
                "মাস্টার ডিভাইস কন্ট্রোল বন্ধ আছে।"
            } else {
                "$permissionLabelBn অনুমতি বন্ধ রয়েছে।"
            }
            repository.logDeviceAction(
                userId = currentUserId,
                actionType = actionType,
                summary = blockedSummary,
                status = "BLOCKED_PERMISSION",
                permissionKey = permissionKey
            )
            _pendingPermissionPrompt.value = PendingPermissionPrompt(
                permissionKey = if (!permissions.masterEnabled) "masterEnabled" else permissionKey,
                titleBn = if (!permissions.masterEnabled) "মাস্টার ডিভাইস কন্ট্রোল" else permissionLabelBn
            )
            val overrideReply = "আপনার সেফটি সেটিংস অনুযায়ী '$permissionLabelBn' অনুমতি এখন বন্ধ আছে। আপনি চাইলে নিচের বাটনে ট্যাপ করে অথবা Settings থেকে এটি চালু করতে পারেন।"
            return Triple("অনুমতি প্রয়োজন: $permissionLabelBn", "BLOCKED_PERMISSION", overrideReply)
        }

        val outcome = executor()
        if (outcome.missingOsPermission != null) {
            _pendingPermissionPrompt.value = PendingPermissionPrompt(
                permissionKey = permissionKey,
                titleBn = permissionLabelBn,
                osPermission = outcome.missingOsPermission
            )
        }
        val status = if (outcome.executed) "EXECUTED" else "FAILED"
        repository.logDeviceAction(
            userId = currentUserId,
            actionType = actionType,
            summary = outcome.summaryBn,
            status = status,
            permissionKey = permissionKey
        )
        val badgeStatus = if (outcome.executed) "EXECUTED" else "BLOCKED_PERMISSION"
        return Triple(outcome.summaryBn, badgeStatus, null)
    }

    fun enablePermissionKey(permissionKey: String) {
        val current = permissionsState.value
        val updated = when (permissionKey) {
            "masterEnabled" -> current.copy(masterEnabled = true)
            "allowVoiceMic" -> current.copy(masterEnabled = true, allowVoiceMic = true)
            "allowTtsSpeech" -> current.copy(masterEnabled = true, allowTtsSpeech = true)
            "allowFlashlight" -> current.copy(masterEnabled = true, allowFlashlight = true)
            "allowVolumeControl" -> current.copy(masterEnabled = true, allowVolumeControl = true)
            "allowDisplayAndSettings" -> current.copy(masterEnabled = true, allowDisplayAndSettings = true)
            "allowAlarmsAndTimers" -> current.copy(masterEnabled = true, allowAlarmsAndTimers = true)
            "allowDialer" -> current.copy(masterEnabled = true, allowDialer = true)
            "allowNotifications" -> current.copy(masterEnabled = true, allowNotifications = true)
            else -> current
        }
        updatePermissions(updated)
        _pendingPermissionPrompt.value = null
        _statusBanner.value = "পারমিশন আপডেট করা হয়েছে।"
    }

    fun selectVoicePersona(personaKey: String, applySuggestedStyle: Boolean = true) {
        val current = permissionsState.value
        val persona = VoicePersona.fromKey(personaKey, current.voiceGender)
        val nextStyle = if (applySuggestedStyle && current.conversationStyle != ConversationStyle.CUSTOM.key) {
            persona.suggestedStyle.key
        } else {
            current.conversationStyle
        }
        val updated = current.copy(
            voicePersona = persona.key,
            voiceGender = persona.voiceGender,
            conversationStyle = nextStyle
        )
        viewModelScope.launch {
            val res = repository.savePermissions(currentUserId, updated)
            if (res.isSuccess) {
                _statusBanner.value = "ভয়েস নির্বাচিত হয়েছে: ${persona.displayNameBn} ✨"
            } else {
                _statusBanner.value = "ভয়েস পরিবর্তন সংরক্ষণ করা যায়নি।"
            }
        }
    }

    fun updatePermissions(updated: DevicePermissions) {
        viewModelScope.launch {
            val res = repository.savePermissions(currentUserId, updated)
            if (res.isFailure) {
                _statusBanner.value = "সেটিংস সংরক্ষণ করা যায়নি।"
            }
        }
    }

    fun updatePersonalization(
        assistantName: String,
        conversationStyle: String,
        customStylePrompt: String,
        voicePersonaKey: String,
        voiceLocale: String
    ) {
        val current = permissionsState.value
        val persona = VoicePersona.fromKey(voicePersonaKey, current.voiceGender)
        val cleanName = assistantName.trim().ifBlank { "সাথী" }.take(40)
        val updated = current.copy(
            assistantName = cleanName,
            conversationStyle = conversationStyle,
            customStylePrompt = customStylePrompt.trim().take(300),
            voiceGender = persona.voiceGender,
            voiceLocale = voiceLocale,
            voicePersona = persona.key
        )
        viewModelScope.launch {
            val res = repository.savePermissions(currentUserId, updated)
            if (res.isSuccess) {
                _statusBanner.value = "অ্যাসিস্ট্যান্ট সেটিংস সংরক্ষণ করা হয়েছে ✨"
            } else {
                _statusBanner.value = "সেটিংস সংরক্ষণ করা যায়নি।"
            }
        }
    }

    fun testAssistantVoice(
        voicePersonaKey: String,
        voiceLocale: String = permissionsState.value.voiceLocale
    ) {
        val perms = permissionsState.value
        val persona = VoicePersona.fromKey(voicePersonaKey, perms.voiceGender)
        val aiName = perms.assistantName.ifBlank { "সাথী" }
        voiceManager.testVoice(persona.key, persona.voiceGender, aiName, voiceLocale)
    }

    fun addOrUpdateTask(
        existing: DailyTask?,
        title: String,
        note: String,
        category: String,
        priority: String,
        scheduledTime: String
    ) {
        viewModelScope.launch {
            val res = if (existing == null) {
                repository.addTask(currentUserId, title, note, category, priority, scheduledTime)
            } else {
                repository.updateTask(
                    currentUserId,
                    existing.copy(
                        title = title,
                        note = note,
                        category = category,
                        priority = priority,
                        scheduledTime = scheduledTime
                    )
                )
            }
            if (res.isFailure) {
                _statusBanner.value = "কাজ সংরক্ষণ করতে সমস্যা হয়েছে।"
            }
        }
    }

    fun toggleTask(task: DailyTask) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(currentUserId, task)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(currentUserId, taskId)
        }
    }

    fun updateUserProfile(
        displayName: String,
        nickname: String,
        bio: String,
        email: String,
        googlePhotoUrl: String,
        customPhotoUri: String
    ) {
        if (_isSavingProfile.value) return
        val existing = userProfileState.value
        val cleanName = displayName.trim().take(80)
        if (cleanName.isEmpty()) {
            _statusBanner.value = "অনুগ্রহ করে ডিসপ্লে নাম লিখুন।"
            return
        }
        val updated = existing.copy(
            userId = currentUserId,
            displayName = cleanName,
            nickname = nickname.trim().take(50),
            email = email.trim().take(120),
            bio = bio.trim().take(300),
            googlePhotoUrl = googlePhotoUrl.trim().take(500),
            customPhotoUri = customPhotoUri.trim().take(500)
        )
        viewModelScope.launch {
            _isSavingProfile.value = true
            val res = repository.saveUserProfile(currentUserId, updated)
            if (res.isSuccess) {
                runCatching {
                    val firebaseUser = Firebase.auth.currentUser
                    if (firebaseUser != null && firebaseUser.uid == currentUserId &&
                        firebaseUser.displayName != cleanName
                    ) {
                        val req = userProfileChangeRequest {
                            this.displayName = cleanName
                        }
                        firebaseUser.updateProfile(req).await()
                    }
                }
                _statusBanner.value = "প্রোফাইল সফলভাবে আপডেট হয়েছে ✨"
            } else {
                _statusBanner.value = "প্রোফাইল সংরক্ষণ করা যায়নি। আবার চেষ্টা করুন।"
            }
            _isSavingProfile.value = false
        }
    }

    fun syncSignedInUserIdentity(
        displayName: String,
        email: String,
        photoUrl: String
    ) {
        viewModelScope.launch {
            val res = repository.syncSignedInUser(
                userId = currentUserId,
                displayName = displayName,
                email = email,
                photoUrl = photoUrl
            )
            if (res.isFailure) {
                Log.w(TAG, "Firestore user sync deferred: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun generateSmartPlan() {
        val currentTasks = (tasksState.value as? DataUiState.Success)?.data ?: emptyList()
        val currentPerms = permissionsState.value
        val currentProfile = userProfileState.value
        viewModelScope.launch {
            _isGeneratingPlan.value = true
            val res = aiEngine.generateSmartDayPlan(currentTasks, currentPerms, currentProfile)
            res.fold(
                onSuccess = { _smartPlanText.value = it },
                onFailure = { _statusBanner.value = it.localizedMessage ?: "রুটিন তৈরি করা যায়নি।" }
            )
            _isGeneratingPlan.value = false
        }
    }

    fun clearSmartPlan() {
        _smartPlanText.value = null
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearMessages(currentUserId)
            _chatTurnError.value = null
        }
    }

    fun executeManualFlashlight(enable: Boolean) {
        val outcome = deviceManager.setFlashlight(enable)
        _statusBanner.value = outcome.summaryBn
    }

    fun executeManualVolume(percent: Int) {
        deviceManager.setMediaVolumePercent(percent)
    }

    fun executeManualBrightness(activity: Activity?, percent: Int) {
        deviceManager.setAppBrightness(activity, percent)
    }

    fun executeManualSettings(type: String) {
        val outcome = deviceManager.openConnectivitySettings(type)
        if (!outcome.executed) _statusBanner.value = outcome.summaryBn
    }

    fun executeManualTimer(seconds: Int, label: String) {
        val outcome = deviceManager.setTimer(seconds, label)
        _statusBanner.value = outcome.summaryBn
    }

    fun executeManualTestNotification() {
        val aiName = permissionsState.value.assistantName.ifBlank { "সাথী" }
        val outcome = deviceManager.postReminderNotification(
            "$aiName পরীক্ষা",
            "আপনার নোটিফিকেশন রিমাইন্ডার সঠিকভাবে কাজ করছে।"
        )
        _statusBanner.value = outcome.summaryBn
    }

    fun startVoiceInput(activity: Activity?) {
        val perms = permissionsState.value
        if (!perms.masterEnabled || !perms.allowVoiceMic) {
            _pendingPermissionPrompt.value = PendingPermissionPrompt(
                permissionKey = if (!perms.masterEnabled) "masterEnabled" else "allowVoiceMic",
                titleBn = "ভয়েস ও মাইক্রোফোন",
                osPermission = Manifest.permission.RECORD_AUDIO
            )
            _statusBanner.value = "ভয়েস ব্যবহারের জন্য মাইক্রোফোন পারমিশন চালু করুন।"
            return
        }
        val langForStt = when (perms.voiceLocale) {
            "en-US" -> "en"
            else -> perms.assistantLanguage
        }
        voiceManager.startListening(langForStt, perms.voiceLocale) { recognized ->
            sendUserMessage(recognized, activity)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }

    private companion object {
        const val TAG = "SathiViewModel"
    }
}
