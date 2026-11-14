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

class SathiRepository(private val db: FirebaseFirestore) {

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

    fun observeTasks(userId: String): Flow<List<DailyTask>> =
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

    suspend fun addTask(
        userId: String,
        title: String,
        note: String = "",
        category: String = "PERSONAL",
        priority: String = "MEDIUM",
        scheduledTime: String = ""
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

    suspend fun updateTask(userId: String, task: DailyTask): Result<Unit> {
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

    suspend fun toggleTaskCompletion(userId: String, task: DailyTask): Result<Unit> {
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

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
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

    fun observePermissions(userId: String): Flow<DevicePermissions> =
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

    suspend fun savePermissions(userId: String, permissions: DevicePermissions): Result<Unit> {
        val docRef = permissionsDoc(userId)
        return try {
            if (permissions.createdAt == null) {
                val payload = mapOf(
                    "userId" to userId,
                    "masterEnabled" to permissions.masterEnabled,
                    "allowVoiceMic" to permissions.allowVoiceMic,
                    "allowTtsSpeech" to permissions.allowTtsSpeech,
                    "allowFlashlight" to permissions.allowFlashlight,
                    "allowVolumeControl" to permissions.allowVolumeControl,
                    "allowDisplayAndSettings" to permissions.allowDisplayAndSettings,
                    "allowAlarmsAndTimers" to permissions.allowAlarmsAndTimers,
                    "allowDialer" to permissions.allowDialer,
                    "allowNotifications" to permissions.allowNotifications,
                    "assistantLanguage" to permissions.assistantLanguage,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.set(permissions.copy(userId = userId, updatedAt = null)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeActionLogs(userId: String): Flow<List<DeviceActionLog>> =
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

    suspend fun logDeviceAction(
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

    fun observeMessages(userId: String): Flow<List<ChatMessageRecord>> =
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

    suspend fun addMessage(
        userId: String,
        sender: String,
        text: String,
        actionLabel: String = "",
        actionStatus: String = "NONE",
        clientOrder: Long = System.currentTimeMillis()
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

    suspend fun clearMessages(userId: String): Result<Unit> {
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
