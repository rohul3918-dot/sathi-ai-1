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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.data.model.ConversationStyle
import com.example.data.model.DevicePermissions
import com.example.data.model.VoiceGender

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantPersonalizationCard(
    permissions: DevicePermissions,
    onSave: (name: String, style: String, customPrompt: String, voiceGender: String) -> Unit,
    onTestVoice: (voiceGender: String) -> Unit,
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

    val hasUnsavedChanges = nameInput.trim().ifBlank { "সাথী" } != permissions.assistantName ||
        selectedStyle != permissions.conversationStyle ||
        customStyleInput.trim() != permissions.customStylePrompt ||
        selectedVoice != permissions.voiceGender

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
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
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "আপনার পছন্দমতো নাম দিন, কথা বলার স্টাইল ও নারী/পুরুষ ভয়েস বেছে নিন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = nameInput,
                onValueChange = { if (it.length <= 40) nameInput = it },
                label = { Text("অ্যাসিস্ট্যান্টের নাম") },
                placeholder = { Text("যেমন: সাথী, রোবট বন্ধু, নীরা") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_name_input")
            )

            Text(
                text = "কথা বলার ধরন (Conversation Style)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
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
                            "অতিরিক্ত স্টাইল নির্দেশনা (ঐচ্ছিক)"
                    )
                },
                placeholder = { Text("যেমন: হাসিখুশি ভাবে ছোট্ট করে উত্তর দিবে বা তুমি বলে সম্বোধন করবে") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_style_input")
            )

            Text(
                text = "অ্যাসিস্ট্যান্টের ভয়েস (নারী / পুরুষ)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceGender.entries.forEach { vg ->
                    val isSelected = selectedVoice == vg.key
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedVoice = vg.key
                            onSave(nameInput, selectedStyle, customStyleInput, vg.key)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onTestVoice(selectedVoice) },
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
                    Text("ভয়েস শুনুন")
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        onSave(nameInput, selectedStyle, customStyleInput, selectedVoice)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantPersonalizationDialog(
    permissions: DevicePermissions,
    onDismiss: () -> Unit,
    onSave: (name: String, style: String, customPrompt: String, voiceGender: String) -> Unit,
    onTestVoice: (voiceGender: String) -> Unit
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

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.RecordVoiceOver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "অ্যাসিস্ট্যান্ট কাস্টমাইজ করুন",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 40) nameInput = it },
                    label = { Text("AI অ্যাসিস্ট্যান্টের নাম") },
                    placeholder = { Text("যেমন: সাথী, রোবট বন্ধু") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_ai_name_input")
                )

                Text(
                    text = "কথা বলার ধরন",
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ConversationStyle.entries.forEach { style ->
                        FilterChip(
                            selected = selectedStyle == style.key,
                            onClick = { selectedStyle = style.key },
                            label = { Text(style.bnLabel) }
                        )
                    }
                }

                OutlinedTextField(
                    value = customStyleInput,
                    onValueChange = { if (it.length <= 300) customStyleInput = it },
                    label = { Text("কাস্টম কথা বলার নির্দেশনা") },
                    placeholder = { Text("যেমন: সব সময় আদর করে / সংক্ষেপে উত্তর দিবে") },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

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
                            onClick = { selectedVoice = vg.key },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (vg == VoiceGender.FEMALE) Icons.Rounded.Face3 else Icons.Rounded.Face,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(vg.bnLabel) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onTestVoice(selectedVoice) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("নির্বাচিত ভয়েস টেস্ট করুন")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(nameInput, selectedStyle, customStyleInput, selectedVoice)
                    onDismiss()
                },
                modifier = Modifier.testTag("dialog_save_personalization_button")
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
