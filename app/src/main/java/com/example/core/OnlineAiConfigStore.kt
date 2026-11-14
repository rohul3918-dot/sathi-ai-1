package com.example.core

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OnlineAiConfig(
    val userApiKey: String = "",
    val customEndpoint: String = DEFAULT_GEMINI_ENDPOINT,
    val buildTimeApiKey: String = ""
) {
    val activeApiKey: String
        get() = userApiKey.trim().ifBlank { buildTimeApiKey.trim() }

    val normalizedEndpoint: String
        get() = customEndpoint.trim().trimEnd('/').ifBlank { DEFAULT_GEMINI_ENDPOINT }

    val hasApiKey: Boolean
        get() = activeApiKey.isNotBlank()

    companion object {
        const val DEFAULT_GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com"
    }
}

class OnlineAiConfigStore(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val buildTimeKey = readBuildConfigGeminiKey()

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<OnlineAiConfig> = _config.asStateFlow()

    fun saveConfig(apiKey: String, endpoint: String) {
        val cleanKey = apiKey.trim()
        val cleanEndpoint = endpoint.trim().trimEnd('/')
            .ifBlank { OnlineAiConfig.DEFAULT_GEMINI_ENDPOINT }
        prefs.edit()
            .putString(KEY_GEMINI_API_KEY, cleanKey)
            .putString(KEY_CUSTOM_ENDPOINT, cleanEndpoint)
            .apply()
        _config.value = OnlineAiConfig(
            userApiKey = cleanKey,
            customEndpoint = cleanEndpoint,
            buildTimeApiKey = buildTimeKey
        )
    }

    fun clearUserApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
        _config.value = _config.value.copy(userApiKey = "")
    }

    fun isOnlineAiConfigured(): Boolean {
        return _config.value.hasApiKey || isFirebaseReady(appContext)
    }

    private fun loadConfig(): OnlineAiConfig {
        val savedKey = prefs.getString(KEY_GEMINI_API_KEY, "").orEmpty()
        val savedEndpoint = prefs.getString(KEY_CUSTOM_ENDPOINT, OnlineAiConfig.DEFAULT_GEMINI_ENDPOINT)
            .orEmpty()
            .ifBlank { OnlineAiConfig.DEFAULT_GEMINI_ENDPOINT }
        return OnlineAiConfig(
            userApiKey = savedKey,
            customEndpoint = savedEndpoint,
            buildTimeApiKey = buildTimeKey
        )
    }

    private fun readBuildConfigGeminiKey(): String {
        return try {
            val field = BuildConfig::class.java.getDeclaredField("GEMINI_API_KEY")
            val raw = (field.get(null) as? String).orEmpty().trim()
            if (raw == "MY_GEMINI_API_KEY" || raw == "null") "" else raw
        } catch (_: Throwable) {
            ""
        }
    }

    private companion object {
        const val PREFS_NAME = "sathi_online_ai_config"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_CUSTOM_ENDPOINT = "custom_endpoint"
    }
}
