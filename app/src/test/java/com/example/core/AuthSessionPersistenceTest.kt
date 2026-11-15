package com.example.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserProfile
import com.example.data.repository.AuthSessionStore
import com.example.data.repository.CachedAuthSession
import com.example.data.repository.LocalFallbackSathiDataStore
import com.example.data.repository.LocalProfileCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AuthSessionPersistenceTest {

    @Test
    fun authSessionStore_persistsAndClearsAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val writer = AuthSessionStore(context)
        writer.clear()

        writer.save(
            CachedAuthSession(
                uid = "uid-123",
                displayName = "Ruhul Amin",
                email = "ruhul@example.com",
                photoUrl = "https://example.com/photo.jpg"
            )
        )

        val readerAfterRestart = AuthSessionStore(context)
        val loaded = readerAfterRestart.load()
        assertNotNull(loaded)
        assertEquals("uid-123", loaded?.uid)
        assertEquals("Ruhul Amin", loaded?.displayName)
        assertEquals("ruhul@example.com", loaded?.email)
        assertEquals("https://example.com/photo.jpg", loaded?.photoUrl)

        readerAfterRestart.clear()
        assertNull(AuthSessionStore(context).load())
    }

    @Test
    fun localProfileCacheAndFallbackStore_syncSignedInUserPreservesCustomizations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uid = "uid-restart-test"
        val cache = LocalProfileCache(context)
        cache.save(uid, UserProfile(userId = uid))

        val store1 = LocalFallbackSathiDataStore(context)
        store1.syncSignedInUser(
            userId = uid,
            displayName = "Ruhul Amin",
            email = "ruhul@example.com",
            photoUrl = "https://example.com/google.jpg"
        )

        val initialProfile = store1.observeUserProfile(uid).first()
        assertEquals("Ruhul Amin", initialProfile.displayName)
        assertEquals("ruhul@example.com", initialProfile.email)
        assertEquals("https://example.com/google.jpg", initialProfile.googlePhotoUrl)

        store1.saveUserProfile(
            uid,
            initialProfile.copy(
                displayName = "রুহুল ভাই",
                nickname = "রুহুল",
                bio = "Android Engineer"
            )
        )

        // Simulate app restart with a fresh LocalFallbackSathiDataStore instance
        val store2 = LocalFallbackSathiDataStore(context)
        store2.syncSignedInUser(
            userId = uid,
            displayName = "Ruhul Google",
            email = "ruhul@example.com",
            photoUrl = "https://example.com/google_updated.jpg"
        )

        val reloaded = store2.observeUserProfile(uid).first()
        assertEquals("রুহুল ভাই", reloaded.displayName)
        assertEquals("রুহুল", reloaded.nickname)
        assertEquals("Android Engineer", reloaded.bio)
        assertEquals("https://example.com/google_updated.jpg", reloaded.googlePhotoUrl)
    }
}
