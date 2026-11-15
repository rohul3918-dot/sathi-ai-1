package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ChatMessageRecord
import com.example.data.model.ConversationStyle
import com.example.data.model.VoicePersona
import com.example.device.VoiceListeningState
import com.example.ui.ChatTurnError
import com.example.ui.PendingPermissionPrompt
import com.example.ui.SathiViewModel
import com.example.ui.theme.EmeraldSuccess

@Composable
fun AssistantChatScreen(
    viewModel: SathiViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboard = LocalClipboardManager.current

    val messages by viewModel.messagesState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionsState.collectAsStateWithLifecycle()
    val isThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReplyText.collectAsStateWithLifecycle()
    val listeningState by viewModel.listeningState.collectAsStateWithLifecycle()
    val turnError by viewModel.chatTurnError.collectAsStateWithLifecycle()
    val pendingPrompt by viewModel.pendingPermissionPrompt.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val aiName = permissions.assistantName.ifBlank { "সাথী" }
    val activePersona = VoicePersona.fromKey(permissions.voicePersona, permissions.voiceGender)
    val styleLabel = ConversationStyle.fromKey(permissions.conversationStyle).bnLabel

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

    LaunchedEffect(messages.size, isThinking, turnError) {
        val totalItems = messages.size + (if (isThinking || !streamingReply.isNullOrBlank()) 1 else 0) + (if (turnError != null) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem((totalItems - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(streamingReply) {
        if (!streamingReply.isNullOrBlank()) {
            val totalItems = messages.size + 1 + (if (turnError != null) 1 else 0)
            if (totalItems > 0) {
                listState.scrollToItem((totalItems - 1).coerceAtLeast(0))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Voice active listening / error indicator
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
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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

        // Inline device permission grant banner only when triggered by a blocked device command
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
                        onNavigateToSettings()
                    },
                    onDismiss = { viewModel.dismissPermissionPrompt() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // Conversation Feed (clean ChatGPT / Gemini style scrolling upwards)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (messages.isEmpty() && streamingReply.isNullOrBlank()) {
                item {
                    AssistantEmptyCanvas(
                        assistantName = aiName,
                        personaLabel = activePersona.displayNameBn,
                        styleLabel = styleLabel,
                        onPickPrompt = { prompt ->
                            viewModel.sendUserMessage(prompt, activity)
                        }
                    )
                }
            } else {
                items(messages, key = { it.id.ifBlank { it.clientOrder.toString() } }) { message ->
                    if (message.sender == "USER") {
                        UserChatBubble(message = message)
                    } else {
                        AssistantChatBubble(
                            message = message,
                            assistantName = aiName,
                            onCopy = {
                                clipboard.setText(AnnotatedString(message.text))
                                viewModel.showMessage("উত্তর কপি করা হয়েছে 📋")
                            },
                            onSpeak = {
                                viewModel.speakMessage(message.text)
                            },
                            onRetry = {
                                viewModel.retryAssistantMessage(message, activity)
                            },
                            onOpenPermissions = onNavigateToSettings
                        )
                    }
                }
            }

            if (isThinking) {
                item {
                    val liveText = streamingReply
                    if (!liveText.isNullOrBlank()) {
                        StreamingAssistantBubble(
                            assistantName = aiName,
                            text = liveText
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "$aiName ভাবছে...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            turnError?.let { errorState ->
                item {
                    TurnErrorBannerCard(
                        error = errorState,
                        onRetry = { viewModel.retryLastFailedTurn(activity) },
                        onDismiss = { viewModel.dismissAiError() }
                    )
                }
            }
        }

        // Clean Bottom Input Bar (Text input + Mic + Send)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "$aiName-কে লিখুন...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
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

                val isListening = listeningState is VoiceListeningState.Listening
                IconButton(
                    onClick = {
                        if (isListening) {
                            viewModel.voiceManager.stopListening()
                        } else if (viewModel.deviceManager.hasOsPermission(Manifest.permission.RECORD_AUDIO)) {
                            viewModel.startVoiceInput(activity)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_mic_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (isListening) "ভয়েস বন্ধ করুন" else "ভয়েস দিয়ে বলুন",
                        tint = if (isListening) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                        .size(48.dp)
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantEmptyCanvas(
    assistantName: String,
    personaLabel: String,
    styleLabel: String,
    onPickPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_assistant_orb_1794619681366),
                contentDescription = "$assistantName Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "হ্যালো! আমি $assistantName ✨",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$personaLabel • $styleLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "আপনি আমাকে যেকোনো প্রশ্ন করতে পারেন, দৈনন্দিন কাজ গোছাতে পারেন অথবা অনুমতি দিয়ে ডিভাইস কন্ট্রোল করতে পারেন।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                "আজকের দিনটি কীভাবে শুরু করা যায়? 🌟",
                "সন্ধ্যা ৬টায় ওষুধ খাওয়ার কাজ যোগ করো 📋",
                "ফ্ল্যাশলাইট চালু করো 🔦",
                "আমাকে একটি ছোট অনুপ্রেরণার গল্প বলো ✨"
            ).forEach { starter ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onPickPrompt(starter) }
                ) {
                    Text(
                        text = starter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UserChatBubble(message: ChatMessageRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    assistantName: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = assistantName,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AssistantChatBubble(
    message: ChatMessageRecord,
    assistantName: String,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onRetry: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = assistantName,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
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

            // Copy, Speak & Retry action row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("copy_reply_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "কপি করুন",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("speak_reply_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "পড়ে শোনান",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("retry_reply_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "পুনরায় উত্তর নিন",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TurnErrorBannerCard(
    error: ChatTurnError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("error_retry_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("আবার চেষ্টা করুন")
                }
                TextButton(onClick = onDismiss) {
                    Text("বন্ধ")
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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text("Settings দেখুন")
                }
                TextButton(onClick = onDismiss) {
                    Text("পরে")
                }
            }
        }
    }
}
