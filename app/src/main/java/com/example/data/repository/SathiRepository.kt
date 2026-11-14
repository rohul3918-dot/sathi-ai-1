package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ChatMessageRecord
import com.example.data.model.DailyTask
import com.example.data.model.DeviceActionLog
import com.example.data.model.DevicePermissions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

interface SathiDataStore {
    fun observeTasks(userId: String): Flow<List<DailyTask>>
    suspend fun addTask(
        userId: String,
        title: String,
        note: String = "",
        category: String = "PERSONAL",
        priority: String = "MEDIUM",
        scheduledTime: String = ""
    ): Result<String>
    suspend fun updateTask(userId: String, task: DailyTask): Result<Unit>
    suspend fun toggleTaskCompletion(userId: String, task: DailyTask): Result<Unit>
    suspend fun deleteTask(userId: String, taskId: String): Result<Unit>
    fun observePermissions(userId: String): Flow<DevicePermissions>
    suspend fun savePermissions(userId: String, permissions: DevicePermissions): Result<Unit>
    fun observeActionLogs(userId: String): Flow<List<DeviceActionLog>>
    suspend fun logDeviceAction(
        userId: String,
        actionType: String,
        summary: String,
        status: String,
        permissionKey: String
    ): Result<String>
    fun observeMessages(userId: String): Flow<List<ChatMessageRecord>>
    suspend fun addMessage(
        userId: String,
        sender: String,
        text: String,
        actionLabel: String = "",
        actionStatus: String = "NONE",
        clientOrder: Long = System.currentTimeMillis()
    ): Result<String>
    suspend fun clearMessages(userId: String): Result<Unit>
}

class SathiRepository(private val db: FirebaseFirestore) : SathiDataStore {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun tasksCollection(userId: String) =
        db.collection("users").document(userId).collection("tasks")

    private fun permissionsDoc(userId: String) =
        db.collection("users").document(userId).collection("settings").document("permissions")

    private fun actionLogsCollection(userId: String) =
        db.collection("users").document(userId).collection("actionLogs")

    private fun messagesCollection(userId: String) =
        db.collection("users").document(userId).collection("messages")

    override fun observeTasks(userId: String): Flow<List<DailyTask>> =
        tasksCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DailyTask::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareBy<DailyTask> { it.completed }
                        .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                )
            }
            .catch { error ->
                logFlowErrorIfFirestore(error, OperationType.LIST, "users/$userId/tasks")
                throw error
            }

    override suspend fun addTask(
        userId: String,
        title: String,
        note: String,
        category: String,
        priority: String,
        scheduledTime: String
    ): Result<String> {
        val cleanTitle = title.trim().take(160)
        if (cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Task title cannot be empty"))
        }
        val docRef = tasksCollection(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "title" to cleanTitle,
            "note" to note.trim().take(500),
            "category" to category,
            "priority" to priority,
            "scheduledTime" to scheduledTime.trim().take(40),
            "completed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun updateTask(userId: String, task: DailyTask): Result<Unit> {
        require(task.id.isNotBlank()) { "Task ID must not be blank for update" }
        val docRef = tasksCollection(userId).document(task.id)
        return try {
            val existingCreatedAt = task.createdAt ?: Timestamp.now()
            val payload = task.copy(
                userId = userId,
                title = task.title.trim().take(160),
                note = task.note.trim().take(500),
                scheduledTime = task.scheduledTime.trim().take(40),
                createdAt = existingCreatedAt,
                updatedAt = null
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun toggleTaskCompletion(userId: String, task: DailyTask): Result<Unit> {
        require(task.id.isNotBlank()) { "Task ID must not be blank for toggle" }
        val docRef = tasksCollection(userId).document(task.id)
        return try {
            docRef.update(
                mapOf(
                    "completed" to !task.completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val docRef = tasksCollection(userId).document(taskId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    override fun observePermissions(userId: String): Flow<DevicePermissions> =
        permissionsDoc(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        DevicePermissions::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: DevicePermissions(userId = userId)
                } else {
                    DevicePermissions(userId = userId)
                }
            }
            .catch { error ->
                logFlowErrorIfFirestore(error, OperationType.GET, "users/$userId/settings/permissions")
                throw error
            }

    override suspend fun savePermissions(userId: String, permissions: DevicePermissions): Result<Unit> {
        val docRef = permissionsDoc(userId)
        val cleanName = permissions.assistantName.trim().ifBlank { "সাথী" }.take(40)
        val cleanStyle = permissions.conversationStyle.takeIf {
            it in setOf("FRIENDLY", "POLITE", "CASUAL", "CONCISE", "CUSTOM")
        } ?: "FRIENDLY"
        val cleanCustomPrompt = permissions.customStylePrompt.trim().take(300)
        val cleanVoiceGender = permissions.voiceGender.takeIf { it in setOf("FEMALE", "MALE") } ?: "FEMALE"
        val sanitized = permissions.copy(
            userId = userId,
            assistantName = cleanName,
            conversationStyle = cleanStyle,
            customStylePrompt = cleanCustomPrompt,
            voiceGender = cleanVoiceGender
        )
        return try {
            val docExists = sanitized.createdAt != null || docRef.get().await().exists()
            if (!docExists) {
                val createPayload = mapOf(
                    "userId" to userId,
                    "masterEnabled" to sanitized.masterEnabled,
                    "allowVoiceMic" to sanitized.allowVoiceMic,
                    "allowTtsSpeech" to sanitized.allowTtsSpeech,
                    "allowFlashlight" to sanitized.allowFlashlight,
                    "allowVolumeControl" to sanitized.allowVolumeControl,
                    "allowDisplayAndSettings" to sanitized.allowDisplayAndSettings,
                    "allowAlarmsAndTimers" to sanitized.allowAlarmsAndTimers,
                    "allowDialer" to sanitized.allowDialer,
                    "allowNotifications" to sanitized.allowNotifications,
                    "assistantLanguage" to sanitized.assistantLanguage,
                    "assistantName" to sanitized.assistantName,
                    "conversationStyle" to sanitized.conversationStyle,
                    "customStylePrompt" to sanitized.customStylePrompt,
                    "voiceGender" to sanitized.voiceGender,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "masterEnabled" to sanitized.masterEnabled,
                    "allowVoiceMic" to sanitized.allowVoiceMic,
                    "allowTtsSpeech" to sanitized.allowTtsSpeech,
                    "allowFlashlight" to sanitized.allowFlashlight,
                    "allowVolumeControl" to sanitized.allowVolumeControl,
                    "allowDisplayAndSettings" to sanitized.allowDisplayAndSettings,
                    "allowAlarmsAndTimers" to sanitized.allowAlarmsAndTimers,
                    "allowDialer" to sanitized.allowDialer,
                    "allowNotifications" to sanitized.allowNotifications,
                    "assistantLanguage" to sanitized.assistantLanguage,
                    "assistantName" to sanitized.assistantName,
                    "conversationStyle" to sanitized.conversationStyle,
                    "customStylePrompt" to sanitized.customStylePrompt,
                    "voiceGender" to sanitized.voiceGender,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    override fun observeActionLogs(userId: String): Flow<List<DeviceActionLog>> =
        actionLogsCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DeviceActionLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    .take(40)
            }
            .catch { error ->
                logFlowErrorIfFirestore(error, OperationType.LIST, "users/$userId/actionLogs")
                throw error
            }

    override suspend fun logDeviceAction(
        userId: String,
        actionType: String,
        summary: String,
        status: String,
        permissionKey: String
    ): Result<String> {
        val docRef = actionLogsCollection(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "actionType" to actionType.take(64),
            "summary" to summary.take(200),
            "status" to status,
            "permissionKey" to permissionKey.take(64),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    override fun observeMessages(userId: String): Flow<List<ChatMessageRecord>> =
        messagesCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatMessageRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.clientOrder }
            }
            .catch { error ->
                logFlowErrorIfFirestore(error, OperationType.LIST, "users/$userId/messages")
                throw error
            }

    override suspend fun addMessage(
        userId: String,
        sender: String,
        text: String,
        actionLabel: String,
        actionStatus: String,
        clientOrder: Long
    ): Result<String> {
        val cleanText = text.trim().take(4000)
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message text cannot be empty"))
        }
        val docRef = messagesCollection(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "sender" to sender,
            "text" to cleanText,
            "actionLabel" to actionLabel.take(160),
            "actionStatus" to actionStatus,
            "clientOrder" to clientOrder,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun clearMessages(userId: String): Result<Unit> {
        val path = "users/$userId/messages"
        return try {
            val snap = messagesCollection(userId).get().await()
            if (!snap.isEmpty) {
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun logFlowErrorIfFirestore(
        error: Throwable,
        operationType: OperationType,
        path: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError != null) {
            handleFirestoreError(firestoreError, operationType, path)
        }
    }
}
