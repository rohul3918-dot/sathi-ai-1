package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ChatMessageRecord
import com.example.data.model.ConversationStyle
import com.example.data.model.VoiceGender
import com.example.device.VoiceListeningState
import com.example.ui.PendingPermissionPrompt
import com.example.ui.SathiViewModel
import com.example.ui.components.AssistantPersonalizationDialog
import com.example.ui.theme.EmeraldSuccess

@Composable
fun AssistantChatScreen(
    viewModel: SathiViewModel,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val messages by viewModel.messagesState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionsState.collectAsStateWithLifecycle()
    val isThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    val listeningState by viewModel.listeningState.collectAsStateWithLifecycle()
    val aiError by viewModel.aiErrorMessage.collectAsStateWithLifecycle()
    val pendingPrompt by viewModel.pendingPermissionPrompt.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showCustomizeDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val aiName = permissions.assistantName.ifBlank { "সাথী" }
    val styleLabel = ConversationStyle.fromKey(permissions.conversationStyle).bnLabel
    val voiceLabel = VoiceGender.fromKey(permissions.voiceGender).bnLabel

    if (showCustomizeDialog) {
        AssistantPersonalizationDialog(
            permissions = permissions,
            onDismiss = { showCustomizeDialog = false },
            onSave = { name, style, customPrompt, voiceGender ->
                viewModel.updatePersonalization(name, style, customPrompt, voiceGender)
            },
            onTestVoice = { voiceGender ->
                viewModel.testAssistantVoice(voiceGender)
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput(activity)
        }
    }

    val osPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.dismissPermissionPrompt()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val quickPrompts = remember {
        listOf(
            "আজকের কাজের রুটিন গুছিয়ে দাও",
            "সকাল ৭টায় হাঁটার কাজ যোগ করো",
            "ফ্ল্যাশলাইট চালু করো",
            "ভলিউম ৬০% করো",
            "৫ মিনিটের টাইমার সেট করো"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Companion Hero Strip
        CompanionOrbHeader(
            assistantName = aiName,
            styleAndVoiceSummary = "$styleLabel • $voiceLabel",
            isThinking = isThinking,
            isListening = listeningState is VoiceListeningState.Listening,
            ttsEnabled = permissions.allowTtsSpeech,
            hasMessages = messages.isNotEmpty(),
            onCustomize = { showCustomizeDialog = true },
            onToggleTts = {
                viewModel.updatePermissions(permissions.copy(allowTtsSpeech = !permissions.allowTtsSpeech))
            },
            onClearChat = { viewModel.clearChatHistory() }
        )

        // Voice partial listening / error strip
        AnimatedVisibility(visible = listeningState !is VoiceListeningState.Idle) {
            val (bannerText, isErr) = when (val st = listeningState) {
                is VoiceListeningState.Listening ->
                    (if (st.partialText.isBlank()) "শুনছি... কথা বলুন" else "শুনছি: ${st.partialText}") to false
                is VoiceListeningState.Error -> st.message to true
                else -> "" to false
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isErr) MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = bannerText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (isErr) {
                        TextButton(onClick = { viewModel.voiceManager.clearError() }) {
                            Text("ঠিক আছে")
                        }
                    } else {
                        TextButton(onClick = { viewModel.voiceManager.stopListening() }) {
                            Text("থামুন")
                        }
                    }
                }
            }
        }

        // Inline permission grant card if user tried a blocked device command
        AnimatedVisibility(visible = pendingPrompt != null) {
            pendingPrompt?.let { prompt ->
                PermissionQuickAllowCard(
                    prompt = prompt,
                    onAllowClick = {
                        viewModel.enablePermissionKey(prompt.permissionKey)
                        val osPerm = prompt.osPermission
                        if (osPerm != null && !viewModel.deviceManager.hasOsPermission(osPerm)) {
                            if (osPerm == Manifest.permission.POST_NOTIFICATIONS &&
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                            ) {
                                viewModel.dismissPermissionPrompt()
                            } else {
                                osPermissionLauncher.launch(osPerm)
                            }
                        }
                    },
                    onOpenSettingsTab = {
                        viewModel.dismissPermissionPrompt()
                        onNavigateToPermissions()
                    },
                    onDismiss = { viewModel.dismissPermissionPrompt() }
                )
            }
        }

        AnimatedVisibility(visible = aiError != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = aiError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.dismissAiError() }) {
                        Text("বন্ধ করুন")
                    }
                }
            }
        }

        // Conversation Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    WelcomeAssistantCard(
                        assistantName = aiName,
                        onCustomize = { showCustomizeDialog = true },
                        onPickPrompt = { prompt ->
                            viewModel.sendUserMessage(prompt, activity)
                        }
                    )
                }
            } else {
                items(messages, key = { it.id.ifBlank { it.clientOrder.toString() } }) { message ->
                    ChatBubbleItem(
                        message = message,
                        onOpenPermissions = onNavigateToPermissions
                    )
                }
            }

            if (isThinking) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$aiName ভাবছে...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick Action Suggestion Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEach { prompt ->
                SuggestionChip(
                    onClick = { viewModel.sendUserMessage(prompt, activity) },
                    label = {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        // Input Composer + Mic
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(bottom = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isListening = listeningState is VoiceListeningState.Listening
            FilledIconButton(
                onClick = {
                    if (isListening) {
                        viewModel.voiceManager.stopListening()
                    } else if (viewModel.deviceManager.hasOsPermission(Manifest.permission.RECORD_AUDIO)) {
                        viewModel.startVoiceInput(activity)
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isListening) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .size(50.dp)
                    .testTag("voice_mic_button")
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (isListening) "ভয়েস বন্ধ করুন" else "ভয়েস দিয়ে বলুন"
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = "$aiName-কে লিখুন বা নির্দেশ দিন...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !isThinking) {
                            val toSend = inputText
                            inputText = ""
                            viewModel.sendUserMessage(toSend, activity)
                        }
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )

            FilledIconButton(
                onClick = {
                    val toSend = inputText
                    inputText = ""
                    viewModel.sendUserMessage(toSend, activity)
                },
                enabled = inputText.isNotBlank() && !isThinking,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .size(50.dp)
                    .testTag("send_message_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "মেসেজ পাঠান"
                )
            }
        }
    }
}

@Composable
private fun CompanionOrbHeader(
    assistantName: String,
    styleAndVoiceSummary: String,
    isThinking: Boolean,
    isListening: Boolean,
    ttsEnabled: Boolean,
    hasMessages: Boolean,
    onCustomize: () -> Unit,
    onToggleTts: () -> Unit,
    onClearChat: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isThinking) 1.09f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 650 else 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        CircleShape
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_assistant_orb_1794619681366),
                    contentDescription = "$assistantName Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$assistantName অ্যাসিস্ট্যান্ট",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        isListening -> "আপনার কথা শুনছি..."
                        isThinking -> "কাজ ও কমান্ড প্রসেস হচ্ছে..."
                        else -> styleAndVoiceSummary
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onCustomize,
                modifier = Modifier.testTag("customize_assistant_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "নাম, ধরন ও ভয়েস কাস্টমাইজ করুন",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onToggleTts,
                modifier = Modifier.testTag("toggle_tts_button")
            ) {
                Icon(
                    imageVector = if (ttsEnabled) Icons.AutoMirrored.Rounded.VolumeUp
                    else Icons.AutoMirrored.Rounded.VolumeOff,
                    contentDescription = if (ttsEnabled) "ভয়েস উত্তর চালু আছে" else "ভয়েস উত্তর বন্ধ আছে",
                    tint = if (ttsEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hasMessages) {
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier.testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "চ্যাট পরিষ্কার করুন",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeAssistantCard(
    assistantName: String,
    onCustomize: () -> Unit,
    onPickPrompt: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "হ্যালো! আমি আপনার ব্যক্তিগত $assistantName 😊✨",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = onCustomize) {
                    Text("কাস্টমাইজ")
                }
            }
            Text(
                text = "আপনি আমাকে বাংলা বা ইংরেজিতে বলতে পারেন—আমি আপনার কাজ লিখে রাখব, দিন সাজিয়ে দেব এবং আপনার দেওয়া পারমিশন অনুযায়ী ফ্ল্যাশলাইট, ভলিউম, অ্যালার্ম বা সেটিংস কন্ট্রোল করব।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "সন্ধ্যা ৬টায় ওষুধ খাওয়ার কাজ যোগ করো",
                    "মিডিয়া ভলিউম ৫০% করে দাও",
                    "আমার আজকের কাজগুলো গুছিয়ে বলো"
                ).forEach { example ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickPrompt(example) }
                    ) {
                        Text(
                            text = "“$example”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessageRecord,
    onOpenPermissions: () -> Unit
) {
    val isUser = message.sender == "USER"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 6.dp,
                    bottomEnd = if (isUser) 6.dp else 20.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isUser) 2.dp else 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )

                    if (message.actionLabel.isNotBlank() && message.actionStatus != "NONE") {
                        val (badgeBg, badgeFg, badgeIcon) = when (message.actionStatus) {
                            "EXECUTED" -> Triple(
                                EmeraldSuccess.copy(alpha = 0.16f),
                                EmeraldSuccess,
                                Icons.Rounded.CheckCircle
                            )
                            "TASK_UPDATED" -> Triple(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                MaterialTheme.colorScheme.primary,
                                Icons.Rounded.TaskAlt
                            )
                            else -> Triple(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.secondary,
                                Icons.Rounded.Lock
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = badgeBg,
                            modifier = Modifier.then(
                                if (message.actionStatus == "BLOCKED_PERMISSION") {
                                    Modifier.clickable { onOpenPermissions() }
                                } else Modifier
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null,
                                    tint = badgeFg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = message.actionLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = badgeFg
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionQuickAllowCard(
    prompt: PendingPermissionPrompt,
    onAllowClick: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .padding(bottom = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "“${prompt.titleBn}” পারমিশন চালু করতে চান?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAllowClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("quick_allow_permission_button")
                ) {
                    Text("অনুমতি দিন")
                }
                TextButton(onClick = onOpenSettingsTab) {
                    Text("সব পারমিশন দেখুন")
                }
                TextButton(onClick = onDismiss) {
                    Text("পরে")
                }
            }
        }
    }
}
