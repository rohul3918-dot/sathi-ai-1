package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DevicePermissions
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SathiRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun addAndObserveTasks_owner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = SathiRepository(firestore)

        val addResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addTask(
                userId = uid,
                title = "Read Bengali news",
                note = "Morning summary",
                category = "ROUTINE",
                priority = "HIGH",
                scheduledTime = "08:00 AM"
            )
        }
        assertTrue(addResult.isSuccess)
        val taskId = addResult.getOrThrow()

        val tasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks(uid).first { list -> list.any { it.id == taskId } }
        }
        val created = tasks.first { it.id == taskId }
        assertEquals("Read Bengali news", created.title)

        val toggleResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.toggleTaskCompletion(uid, created)
        }
        assertTrue(toggleResult.isSuccess)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteTask(uid, taskId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun saveAndObservePermissions_owner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = SathiRepository(firestore)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.savePermissions(
                uid,
                DevicePermissions(
                    userId = uid,
                    masterEnabled = true,
                    allowFlashlight = true,
                    allowVolumeControl = true,
                    assistantName = "নীরা",
                    conversationStyle = "CUSTOM",
                    customStylePrompt = "সব সময় আদর করে ও সংক্ষেপে কথা বলবে",
                    voiceGender = "MALE",
                    voiceLocale = "bn-IN",
                    voicePersona = "MALE_CALM"
                )
            )
        }
        assertTrue(saveResult.isSuccess)

        val loaded = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePermissions(uid).first { it.userId == uid && it.allowFlashlight }
        }
        assertTrue(loaded.allowFlashlight)
        assertEquals("নীরা", loaded.assistantName)
        assertEquals("CUSTOM", loaded.conversationStyle)
        assertEquals("MALE", loaded.voiceGender)
        assertEquals("bn-IN", loaded.voiceLocale)
        assertEquals("MALE_CALM", loaded.voicePersona)
    }

    @Test
    fun crossUserTaskWrite_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SathiRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addTask(userId = aliceUid, title = "Alice secret task")
        }.getOrThrow()

        signInTestUser(BOB_EMAIL)
        val bobRepo = SathiRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.addTask(userId = aliceUid, title = "Bob intrusion")
        }
        assertTrue(result.isFailure)
        val fsErr = result.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    @Test
    fun observeTasks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repo = SathiRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeTasks(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated task observer")
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
        }
    }

    @Test
    fun saveAndObserveUserProfile_owner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = SathiRepository(firestore)

        val saveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveUserProfile(
                uid,
                UserProfile(
                    userId = uid,
                    displayName = "Ruhul Amin",
                    nickname = "রুহুল",
                    email = ALICE_EMAIL,
                    bio = "Building Sathi AI",
                    googlePhotoUrl = "https://example.com/avatar.jpg",
                    customPhotoUri = ""
                )
            )
        }
        assertTrue(saveRes.isSuccess)

        val loaded = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first { it.displayName == "Ruhul Amin" }
        }
        assertEquals("রুহুল", loaded.nickname)
        assertEquals("Building Sathi AI", loaded.bio)
        assertEquals("https://example.com/avatar.jpg", loaded.effectivePhotoUrl)

        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveUserProfile(
                uid,
                loaded.copy(
                    nickname = "ভাইয়া",
                    customPhotoUri = "file:///data/user/0/com.example/files/avatars/alice.jpg"
                )
            )
        }
        assertTrue(updateRes.isSuccess)

        val updated = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first { it.nickname == "ভাইয়া" }
        }
        assertEquals("file:///data/user/0/com.example/files/avatars/alice.jpg", updated.effectivePhotoUrl)
    }

    @Test
    fun crossUserProfileAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = SathiRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveUserProfile(
                aliceUid,
                UserProfile(userId = aliceUid, displayName = "Alice", nickname = "Ali")
            )
        }.getOrThrow()

        signInTestUser(BOB_EMAIL)
        val bobRepo = SathiRepository(firestore)
        val writeRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.saveUserProfile(
                aliceUid,
                UserProfile(userId = aliceUid, displayName = "Mallory")
            )
        }
        assertTrue(writeRes.isFailure)
        val fsErr = writeRes.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_sathi@test.com"
        const val BOB_EMAIL = "bob_sathi@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
