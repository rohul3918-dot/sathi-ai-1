package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.configureAppCheck
import com.example.core.isFirebaseReady
import com.example.core.safeCurrentUser
import com.example.data.model.DevicePermissions
import com.example.data.repository.LocalFallbackSathiDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SathiPersonalityTest {

    @Test
    fun creatorQuestions_femaleVoice_returnsExactFemaleSignature() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SathiAiEngine(context)
        val perms = DevicePermissions(voiceGender = "FEMALE", assistantName = "সাথী")

        val queries = listOf(
            "তোমায় কে বানিয়েছেন?",
            "কে তৈরি করেছে?",
            "Who created you?",
            "Who made you?"
        )
        queries.forEach { q ->
            val res = engine.processUserTurn(q, emptyList(), perms, emptyList())
            assertTrue(res.isSuccess)
            assertEquals("আমার কাস  রুহুল আমিন", res.getOrThrow().reply)
            assertEquals("NONE", res.getOrThrow().actionType)
        }
    }

    @Test
    fun creatorQuestions_maleVoice_returnsExactMaleSignature() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SathiAiEngine(context)
        val perms = DevicePermissions(voiceGender = "MALE", assistantName = "নীরা")

        val queries = listOf(
            "তোমায় কে বানিয়েছেন?",
            "কে তৈরি করেছে?",
            "Who created you?",
            "Who made you?"
        )
        queries.forEach { q ->
            val res = engine.processUserTurn(q, emptyList(), perms, emptyList())
            assertTrue(res.isSuccess)
            assertEquals("রুহুল আমিন", res.getOrThrow().reply)
            assertEquals("NONE", res.getOrThrow().actionType)
        }
    }

    @Test
    fun missingFirebaseConfig_startupHelpersDoNotCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureAppCheck(context, null)
        // Must safely evaluate without throwing IllegalStateException
        val ready = isFirebaseReady(context)
        if (!ready) {
            assertNull(safeCurrentUser(context))
        }
    }

    @Test
    fun localFallbackStore_persistsPersonalization() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalFallbackSathiDataStore(context)
        val updated = DevicePermissions(
            userId = "local_user",
            assistantName = "মায়া",
            conversationStyle = "CUSTOM",
            customStylePrompt = "সংক্ষেপে হাসিখুশি ভাবে বলবে",
            voiceGender = "MALE"
        )
        val saveRes = store.savePermissions("local_user", updated)
        assertTrue(saveRes.isSuccess)

        val observed = store.observePermissions("local_user").first()
        assertEquals("মায়া", observed.assistantName)
        assertEquals("CUSTOM", observed.conversationStyle)
        assertEquals("MALE", observed.voiceGender)
    }
}
