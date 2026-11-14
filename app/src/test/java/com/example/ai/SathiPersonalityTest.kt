package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.OnlineAiConfig
import com.example.core.OnlineAiConfigStore
import com.example.core.configureAppCheck
import com.example.core.isFirebaseReady
import com.example.core.safeCurrentUser
import com.example.data.model.AiLanguageModel
import com.example.data.model.DevicePermissions
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
    fun creatorQuestions_femaleVoice_returnsExactFemaleSignature() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configStore = OnlineAiConfigStore(context)
        val engine = SathiAiEngine(context, configStore)
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
        val configStore = OnlineAiConfigStore(context)
        val engine = SathiAiEngine(context, configStore)
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
    fun normalChat_withoutApiKeyOrFirebase_failsWithMissingOnlineAiConfigException() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configStore = OnlineAiConfigStore(context)
        configStore.clearUserApiKey()
        if (!isFirebaseReady(context) && !configStore.config.value.hasApiKey) {
            val engine = SathiAiEngine(context, configStore)
            val res = engine.processUserTurn(
                userMessage = "আজকে আবহাওয়া কেমন?",
                tasks = emptyList(),
                permissions = DevicePermissions(),
                recentHistory = emptyList()
            )
            assertTrue(res.isFailure)
            assertTrue(res.exceptionOrNull() is MissingOnlineAiConfigException)
        }
    }

    @Test
    fun onlineAiConfigStore_savesAndClearsApiKeyAndEndpoint() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configStore = OnlineAiConfigStore(context)
        configStore.saveConfig("test-key-123", "https://custom.example.com/")

        val saved = configStore.config.value
        assertEquals("test-key-123", saved.userApiKey)
        assertEquals("test-key-123", saved.activeApiKey)
        assertEquals("https://custom.example.com", saved.normalizedEndpoint)
        assertTrue(saved.hasApiKey)

        configStore.clearUserApiKey()
        assertEquals("", configStore.config.value.userApiKey)

        configStore.saveConfig("", "")
        assertEquals(OnlineAiConfig.DEFAULT_GEMINI_ENDPOINT, configStore.config.value.normalizedEndpoint)
        assertFalse(configStore.config.value.userApiKey.isNotEmpty())
    }

    @Test
    fun languageModelSelection_resolvesKnownAndFallbackModels() {
        assertEquals(AiLanguageModel.GEMINI_2_5_PRO, AiLanguageModel.fromKey("GEMINI_2_5_PRO"))
        assertEquals(AiLanguageModel.GEMINI_2_0_FLASH_LITE, AiLanguageModel.fromKey("GEMINI_2_0_FLASH_LITE"))
        assertEquals(AiLanguageModel.CREATIVE_CHAT, AiLanguageModel.fromKey("CREATIVE_CHAT"))
        assertTrue(AiLanguageModel.CREATIVE_CHAT.creativeMode)
        assertEquals(AiLanguageModel.GEMINI_2_5_FLASH, AiLanguageModel.fromKey("UNKNOWN_MODEL"))
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
    fun localFallbackStore_persistsPersonalizationAndModelAndLocale() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalFallbackSathiDataStore(context)
        val updated = DevicePermissions(
            userId = "local_user",
            assistantName = "মায়া",
            conversationStyle = "CUSTOM",
            customStylePrompt = "সংক্ষেপে হাসিখুশি ভাবে বলবে",
            voiceGender = "MALE",
            voiceLocale = "bn-IN",
            selectedModel = "GEMINI_2_5_PRO"
        )
        val saveRes = store.savePermissions("local_user", updated)
        assertTrue(saveRes.isSuccess)

        val observed = store.observePermissions("local_user").first()
        assertEquals("মায়া", observed.assistantName)
        assertEquals("CUSTOM", observed.conversationStyle)
        assertEquals("MALE", observed.voiceGender)
        assertEquals("bn-IN", observed.voiceLocale)
        assertEquals("GEMINI_2_5_PRO", observed.selectedModel)
    }
}
