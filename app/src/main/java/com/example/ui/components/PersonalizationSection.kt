package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.OnlineAiConfig
import com.example.data.model.ConversationStyle
import com.example.data.model.DevicePermissions
import com.example.data.model.VoiceGender
import com.example.data.model.VoiceLocaleOption
import com.example.ui.theme.EmeraldSuccess

@Composable
fun AccountStatusCard(
    firebaseReady: Boolean,
    isSignedIn: Boolean,
    userDisplayName: String,
    userEmail: String?,
    isSigningIn: Boolean,
    authErrorMessage: String?,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = if (isSignedIn) EmeraldSuccess else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSignedIn) userDisplayName else "Google অ্যাকাউন্ট",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            isSignedIn -> userEmail ?: "ক্লাউড সিঙ্ক সক্রিয়"
                            firebaseReady -> "কাজ ও সেটিংস ক্লাউডে সিঙ্ক করতে Google দিয়ে সাইন-ইন করুন"
                            else -> "Google সাইন-ইনের জন্য Firebase কনফিগারেশন (google-services.json) যুক্ত করতে হবে; তবে আপনার Gemini API Key দিয়ে অনলাইন AI চ্যাট এখনই চলবে।"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!authErrorMessage.isNullOrBlank()) {
                Text(
                    text = authErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isSignedIn) {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("settings_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("সাইন আউট করুন")
                }
            } else {
                Button(
                    onClick = onGoogleSignIn,
                    enabled = !isSigningIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("settings_google_sign_in_button")
                ) {
                    Text(
                        text = if (isSigningIn) "সাইন-ইন হচ্ছে..." else "Google দিয়ে সাইন-ইন করুন",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineApiSetupCard(
    onlineConfig: OnlineAiConfig,
    firebaseReady: Boolean,
    onSaveConfig: (apiKey: String, endpoint: String) -> Unit,
    onClearApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var apiKeyInput by remember(onlineConfig.userApiKey) { mutableStateOf(onlineConfig.userApiKey) }
    var endpointInput by remember(onlineConfig.customEndpoint) { mutableStateOf(onlineConfig.customEndpoint) }
    var showKey by remember { mutableStateOf(false) }
    var showEndpoint by remember { mutableStateOf(false) }

    val statusLabel = when {
        onlineConfig.userApiKey.isNotBlank() -> "কাস্টম Gemini API Key সক্রিয়"
        onlineConfig.buildTimeApiKey.isNotBlank() -> "বিল্ট-ইন Gemini API Key সক্রিয়"
        firebaseReady -> "Firebase অনলাইন AI সংযুক্ত"
        else -> "অনলাইন AI চালুর জন্য API Key প্রয়োজন"
    }
    val statusColor = if (onlineConfig.hasApiKey || firebaseReady) EmeraldSuccess else MaterialTheme.colorScheme.error

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (onlineConfig.hasApiKey || firebaseReady) Icons.Rounded.CloudDone else Icons.Rounded.Key,
                    contentDescription = null,
                    tint = statusColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "অনলাইন AI ও Gemini API Key",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("Gemini API Key") },
                placeholder = { Text("AIza...") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showKey) "লুকান" else "দেখুন"
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_api_key_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showEndpoint = !showEndpoint }) {
                    Text(if (showEndpoint) "এন্ডপয়েন্ট লুকান" else "কাস্টম এন্ডপয়েন্ট (ঐচ্ছিক)")
                }
                if (onlineConfig.userApiKey.isNotBlank()) {
                    TextButton(onClick = {
                        apiKeyInput = ""
                        onClearApiKey()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("মুছুন")
                    }
                }
            }

            if (showEndpoint) {
                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = { endpointInput = it },
                    label = { Text("API Base Endpoint") },
                    placeholder = { Text(OnlineAiConfig.DEFAULT_GEMINI_ENDPOINT) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_endpoint_input")
                )
            }

            Button(
                onClick = { onSaveConfig(apiKeyInput, endpointInput) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_api_key_button")
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("API কনফিগারেশন সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantPersonalizationCard(
    permissions: DevicePermissions,
    onSave: (
        name: String,
        style: String,
        customPrompt: String,
        voiceGender: String,
        voiceLocale: String
    ) -> Unit,
    onTestVoice: (voiceGender: String, voiceLocale: String) -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var nameInput by remember(permissions.assistantName) {
        mutableStateOf(permissions.assistantName.ifBlank { "সাথী" })
    }
    var selectedStyle by remember(permissions.conversationStyle) {
        mutableStateOf(permissions.conversationStyle.ifBlank { "FRIENDLY" })
    }
    var customStyleInput by remember(permissions.customStylePrompt) {
        mutableStateOf(permissions.customStylePrompt)
    }
    var selectedVoice by remember(permissions.voiceGender) {
        mutableStateOf(permissions.voiceGender.ifBlank { "FEMALE" })
    }
    var selectedVoiceLocale by remember(permissions.voiceLocale) {
        mutableStateOf(permissions.voiceLocale.ifBlank { "bn-BD" })
    }

    val hasUnsavedChanges = nameInput.trim().ifBlank { "সাথী" } != permissions.assistantName ||
        selectedStyle != permissions.conversationStyle ||
        customStyleInput.trim() != permissions.customStylePrompt ||
        selectedVoice != permissions.voiceGender ||
        selectedVoiceLocale != permissions.voiceLocale

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "অ্যাসিস্ট্যান্টের নাম, ধরন ও কণ্ঠ",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "নাম, কথা বলার স্টাইল এবং নারী/পুরুষ ভয়েস পছন্দ করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = nameInput,
                onValueChange = { if (it.length <= 40) nameInput = it },
                label = { Text("অ্যাসিস্ট্যান্টের নাম") },
                placeholder = { Text("যেমন: সাথী, নীরা, জার্ভিস") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_name_input")
            )

            Text(
                text = "চ্যাটের ভাষা",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "bilingual" to "বাংলা + English",
                    "bn" to "শুধু বাংলা",
                    "en" to "English"
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = permissions.assistantLanguage == code,
                        onClick = { onSelectLanguage(code) },
                        label = { Text(label) }
                    )
                }
            }

            Text(
                text = "কথা বলার ধরন (Tone / Style)",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ConversationStyle.entries.forEach { style ->
                    FilterChip(
                        selected = selectedStyle == style.key,
                        onClick = { selectedStyle = style.key },
                        label = { Text(style.bnLabel) },
                        modifier = Modifier.testTag("style_chip_${style.key.lowercase()}")
                    )
                }
            }

            OutlinedTextField(
                value = customStyleInput,
                onValueChange = { if (it.length <= 300) customStyleInput = it },
                label = {
                    Text(
                        if (selectedStyle == ConversationStyle.CUSTOM.key)
                            "কাস্টম কথা বলার নির্দেশনা (আবশ্যক)"
                        else
                            "অতিরিক্ত নির্দেশনা (ঐচ্ছিক)"
                    )
                },
                placeholder = { Text("যেমন: হাসিখুশি ভাবে ছোট্ট করে উত্তর দিবে") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_style_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("অটো ভয়েস উত্তর (TTS)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "মেসেজ এলে অ্যাসিস্ট্যান্ট মুখে পড়ে শোনাবে",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = permissions.allowTtsSpeech,
                    onCheckedChange = onToggleTts,
                    modifier = Modifier.testTag("settings_tts_switch")
                )
            }

            Text(
                text = "ভয়েস নির্বাচন (নারী / পুরুষ)",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VoiceGender.entries.forEach { vg ->
                    FilterChip(
                        selected = selectedVoice == vg.key,
                        onClick = {
                            selectedVoice = vg.key
                            onSave(nameInput, selectedStyle, customStyleInput, vg.key, selectedVoiceLocale)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (vg == VoiceGender.FEMALE) Icons.Rounded.Face3 else Icons.Rounded.Face,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(vg.bnLabel) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_${vg.key.lowercase()}_chip")
                    )
                }
            }

            Text(
                text = "ভয়েস ভাষা (TTS Locale)",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceLocaleOption.entries.forEach { localeOpt ->
                    FilterChip(
                        selected = selectedVoiceLocale == localeOpt.tag,
                        onClick = {
                            selectedVoiceLocale = localeOpt.tag
                            onSave(nameInput, selectedStyle, customStyleInput, selectedVoice, localeOpt.tag)
                        },
                        label = { Text(localeOpt.bnLabel) },
                        modifier = Modifier.testTag("voice_locale_${localeOpt.tag.lowercase()}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onTestVoice(selectedVoice, selectedVoiceLocale) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("test_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Test Voice")
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        onSave(nameInput, selectedStyle, customStyleInput, selectedVoice, selectedVoiceLocale)
                    },
                    enabled = hasUnsavedChanges || selectedStyle == ConversationStyle.CUSTOM.key,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("save_personalization_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
