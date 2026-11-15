package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.configureAppCheck
import com.example.core.isFirebaseReady
import com.example.core.safeCurrentUser
import com.example.data.model.ConversationStyle
import com.example.data.model.DevicePermissions
import com.example.data.model.UserProfile
import com.example.data.model.VoicePersona
import com.example.data.repository.LocalFallbackSathiDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun creatorQuestions_femalePersona_returnsExactFemaleSignature() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SathiAiEngine(context)
        val perms = DevicePermissions(
            voicePersona = VoicePersona.FEMALE_WARM.key,
            voiceGender = "FEMALE",
            assistantName = "সাথী"
        )

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
    fun creatorQuestions_malePersona_returnsExactMaleSignature() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SathiAiEngine(context)
        val perms = DevicePermissions(
            voicePersona = VoicePersona.MALE_CALM.key,
            voiceGender = "MALE",
            assistantName = "নীরা"
        )

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
    fun actionIntentDetection_separatesConversationalFromDeviceAndTaskCommands() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SathiAiEngine(context)

        assertFalse(engine.isLikelyActionOrDeviceIntent("কেমন আছো আজকে?"))
        assertFalse(engine.isLikelyActionOrDeviceIntent("Tell me a motivational quote"))
        assertTrue(engine.isLikelyActionOrDeviceIntent("টর্চ লাইট অন করো"))
        assertTrue(engine.isLikelyActionOrDeviceIntent("ভলিউম ৬০ শতাংশ করো"))
        assertTrue(engine.isLikelyActionOrDeviceIntent("সকাল ৭টায় একটা অ্যালার্ম দাও"))
        assertTrue(engine.isLikelyActionOrDeviceIntent("Add task: buy milk"))
    }

    @Test
    fun voicePersonaResolution_mapsDistinctMaleAndFemaleProfiles() {
        val f1 = VoicePersona.fromKey("FEMALE_WARM")
        val f2 = VoicePersona.fromKey("FEMALE_CRISP")
        val m1 = VoicePersona.fromKey("MALE_CALM")
        val m2 = VoicePersona.fromKey("MALE_ENERGETIC")

        assertEquals("FEMALE", f1.voiceGender)
        assertEquals("FEMALE", f2.voiceGender)
        assertEquals("MALE", m1.voiceGender)
        assertEquals("MALE", m2.voiceGender)
        assertEquals(ConversationStyle.FRIENDLY, f1.suggestedStyle)
        assertEquals(ConversationStyle.CONCISE, f2.suggestedStyle)
        assertEquals(ConversationStyle.POLITE, m1.suggestedStyle)
        assertEquals(ConversationStyle.CASUAL, m2.suggestedStyle)

        assertEquals(VoicePersona.MALE_CALM, VoicePersona.fromKey("UNKNOWN_PERSONA", fallbackGender = "MALE"))
        assertEquals(VoicePersona.FEMALE_WARM, VoicePersona.fromKey("UNKNOWN_PERSONA", fallbackGender = "FEMALE"))
    }

    @Test
    fun missingFirebaseConfig_startupHelpersDoNotCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureAppCheck(context, null)
        val ready = isFirebaseReady(context)
        if (!ready) {
            assertNull(safeCurrentUser(context))
        }
    }

    @Test
    fun localFallbackStore_persistsPersonalizationAndVoicePersonaAndLocale() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalFallbackSathiDataStore(context)
        val updated = DevicePermissions(
            userId = "local_user",
            assistantName = "মায়া",
            conversationStyle = "CUSTOM",
            customStylePrompt = "সংক্ষেপে হাসিখুশি ভাবে বলবে",
            voiceGender = "MALE",
            voiceLocale = "bn-IN",
            voicePersona = "MALE_ENERGETIC"
        )
        val saveRes = store.savePermissions("local_user", updated)
        assertTrue(saveRes.isSuccess)

        val observed = store.observePermissions("local_user").first()
        assertEquals("মায়া", observed.assistantName)
        assertEquals("CUSTOM", observed.conversationStyle)
        assertEquals("MALE", observed.voiceGender)
        assertEquals("bn-IN", observed.voiceLocale)
        assertEquals("MALE_ENERGETIC", observed.voicePersona)
    }

    @Test
    fun localFallbackStore_persistsAndObservesUserProfile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalFallbackSathiDataStore(context)
        val profile = UserProfile(
            userId = "uid_local_test",
            displayName = "রুহুল আমিন",
            nickname = "রুহুল",
            email = "ruhul@example.com",
            bio = "Android & AI creator",
            googlePhotoUrl = "https://example.com/google.jpg",
            customPhotoUri = "file:///data/user/0/com.example/files/avatars/custom.jpg"
        )
        val res = store.saveUserProfile("uid_local_test", profile)
        assertTrue(res.isSuccess)

        val observed = store.observeUserProfile("uid_local_test").first()
        assertEquals("রুহুল আমিন", observed.displayName)
        assertEquals("রুহুল", observed.nickname)
        assertEquals("রুহুল", observed.preferredAddressName)
        assertEquals("Android & AI creator", observed.bio)
        assertEquals("file:///data/user/0/com.example/files/avatars/custom.jpg", observed.effectivePhotoUrl)

        val resetPhoto = observed.copy(customPhotoUri = "")
        store.saveUserProfile("uid_local_test", resetPhoto)
        val afterReset = store.observeUserProfile("uid_local_test").first()
        assertEquals("https://example.com/google.jpg", afterReset.effectivePhotoUrl)
    }
}
