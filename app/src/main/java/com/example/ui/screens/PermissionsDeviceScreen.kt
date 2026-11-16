package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.isFirebaseReady
import com.example.data.model.DeviceActionLog
import com.example.ui.SathiViewModel
import com.example.ui.components.AccountStatusCard
import com.example.ui.components.AssistantPersonalizationCard
import com.example.ui.theme.EmeraldSuccess

@Composable
fun PermissionsDeviceScreen(
    viewModel: SathiViewModel,
    isSignedIn: Boolean,
    userDisplayName: String,
    userEmail: String?,
    userPhotoUrl: String?,
    isSigningIn: Boolean,
    authErrorMessage: String?,
    pendingTaskCount: Int = 0,
    onOpenTasks: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val firebaseReady = isFirebaseReady(context)

    val perms by viewModel.permissionsState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfileState.collectAsStateWithLifecycle()
    val logs by viewModel.actionLogsState.collectAsStateWithLifecycle()
    val isFlashOn by viewModel.isFlashlightOn.collectAsStateWithLifecycle()
    val volumePct by viewModel.currentVolumePercent.collectAsStateWithLifecycle()
    val brightnessPct by viewModel.appBrightnessPercent.collectAsStateWithLifecycle()
    val activeVoiceInfo by viewModel.voiceManager.activeVoiceInfo.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(viewModel.deviceManager.hasOsPermission(Manifest.permission.RECORD_AUDIO))
    }
    var hasNotifPermission by remember {
        mutableStateOf(viewModel.deviceManager.hasNotificationPermission())
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.updatePermissions(perms.copy(allowVoiceMic = true))
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted) {
            viewModel.updatePermissions(perms.copy(allowNotifications = true))
        }
    }

    var sliderVolume by remember(volumePct) { mutableFloatStateOf(volumePct.toFloat()) }
    var sliderBrightness by remember(brightnessPct) { mutableFloatStateOf(brightnessPct.toFloat()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Google Account, Profile & Cloud Sync
            item {
                AccountStatusCard(
                    firebaseReady = firebaseReady,
                    isSignedIn = isSignedIn,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    photoUrl = userPhotoUrl,
                    userProfile = userProfile,
                    isSigningIn = isSigningIn,
                    authErrorMessage = authErrorMessage,
                    onOpenProfile = onOpenProfile,
                    onGoogleSignIn = onGoogleSignIn,
                    onSignOut = onSignOut
                )
            }

            // 2. Daily Tasks & Routine Entry (Moved from Home Top Bar into Settings)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    onClick = onOpenTasks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                            RoundedCornerShape(20.dp)
                        )
                        .testTag("settings_open_tasks_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "দৈনন্দিন কাজ ও রুটিন",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (pendingTaskCount > 0) {
                                    "এখন $pendingTaskCount টি বাকি কাজ রয়েছে — দেখতে ট্যাপ করুন"
                                } else {
                                    "আপনার কাজের তালিকা ও রিমাইন্ডার পরিচালনা করুন"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onOpenTasks) {
                            Text(if (pendingTaskCount > 0) "খুলুন ($pendingTaskCount)" else "খুলুন")
                        }
                    }
                }
            }

            // 3. Custom AI Name, Style, Voice Persona + Test Voice
            item {
                AssistantPersonalizationCard(
                    permissions = perms,
                    activeVoiceInfo = activeVoiceInfo,
                    onSave = { name, style, customPrompt, voicePersonaKey, voiceLocale ->
                        viewModel.updatePersonalization(
                            assistantName = name,
                            conversationStyle = style,
                            customStylePrompt = customPrompt,
                            voicePersonaKey = voicePersonaKey,
                            voiceLocale = voiceLocale
                        )
                    },
                    onTestVoice = { voicePersonaKey, voiceLocale ->
                        viewModel.testAssistantVoice(voicePersonaKey, voiceLocale)
                    },
                    onToggleTts = { enabled ->
                        viewModel.updatePermissions(perms.copy(allowTtsSpeech = enabled))
                    },
                    onSelectLanguage = { langCode ->
                        viewModel.updatePermissions(perms.copy(assistantLanguage = langCode))
                    }
                )
            }

            // 3. Chat Management
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("চ্যাট ইতিহাস", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "পূর্ববর্তী কথোপকথন মুছে নতুন চ্যাট শুরু করুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.clearChatHistory()
                                viewModel.showMessage("চ্যাট ইতিহাস পরিষ্কার করা হয়েছে ✨")
                            },
                            modifier = Modifier.testTag("settings_clear_chat_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("মুছুন")
                        }
                    }
                }
            }

            // 4. Device Control Sovereignty Header
            item {
                Text(
                    text = "ডিভাইস কন্ট্রোল ও পারমিশন",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (perms.masterEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "মাস্টার AI ডিভাইস কন্ট্রোল",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (perms.masterEnabled) "সক্রিয় — কেবল আপনার নির্বাচিত সুইচগুলো AI চালাতে পারবে"
                                else "বন্ধ — AI কোনো ডিভাইস ফাংশন চালাতে পারবে না",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = perms.masterEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updatePermissions(perms.copy(masterEnabled = enabled))
                            },
                            modifier = Modifier.testTag("master_device_switch")
                        )
                    }
                }
            }

            item {
                PermissionControlCard(
                    icon = Icons.Rounded.Mic,
                    title = "ভয়েস মাইক্রোফোন (Voice Input)",
                    description = "আপনার কথা শুনে বাংলা/ইংরেজি কমান্ড গ্রহণ করবে।",
                    checked = perms.allowVoiceMic,
                    masterEnabled = perms.masterEnabled,
                    testTag = "perm_voice_mic_switch",
                    osStatusText = if (hasMicPermission) "সিস্টেম মাইক্রোফোন অনুমতি আছে" else "Android মাইক্রোফোন অনুমতি প্রয়োজন",
                    osPermissionGranted = hasMicPermission,
                    onRequestOsPermission = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onCheckedChange = {
                        viewModel.updatePermissions(perms.copy(allowVoiceMic = it))
                    }
                )
            }

            item {
                PermissionControlCard(
                    icon = Icons.Rounded.FlashlightOn,
                    title = "ফ্ল্যাশলাইট কন্ট্রোল",
                    description = "কথা বা চ্যাটের মাধ্যমে টর্চলাইট চালু/বন্ধ করা।",
                    checked = perms.allowFlashlight,
                    masterEnabled = perms.masterEnabled,
                    testTag = "perm_flashlight_switch",
                    onCheckedChange = {
                        viewModel.updatePermissions(perms.copy(allowFlashlight = it))
                    },
                    extraContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isFlashOn) "ফ্ল্যাশলাইট এখন চালু" else "ফ্ল্যাশলাইট বন্ধ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { viewModel.executeManualFlashlight(!isFlashOn) },
                                enabled = perms.masterEnabled && perms.allowFlashlight
                            ) {
                                Text(if (isFlashOn) "বন্ধ করুন" else "চালু পরীক্ষা")
                            }
                        }
                    }
                )
            }

            item {
                PermissionControlCard(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    title = "অডিও ও মিডিয়া ভলিউম",
                    description = "AI নির্দেশ অনুযায়ী ডিভাইসের মিডিয়া সাউন্ড কমানো/বাড়ানো।",
                    checked = perms.allowVolumeControl,
                    masterEnabled = perms.masterEnabled,
                    testTag = "perm_volume_switch",
                    onCheckedChange = {
                        viewModel.updatePermissions(perms.copy(allowVolumeControl = it))
                    },
                    extraContent = {
                        Column {
                            Text(
                                text = "মিডিয়া ভলিউম: ${sliderVolume.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = sliderVolume,
                                onValueChange = { sliderVolume = it },
                                onValueChangeFinished = {
                                    viewModel.executeManualVolume(sliderVolume.toInt())
                                },
                                valueRange = 0f..100f,
                                enabled = perms.masterEnabled && perms.allowVolumeControl
                            )
                        }
                    }
                )
            }

            item {
                PermissionControlCard(
                    icon = Icons.Rounded.Brightness6,
                    title = "ব্রাইটনেস, Wi-Fi ও Bluetooth",
                    description = "স্ক্রিন ব্রাইটনেস নিয়ন্ত্রণ এবং Wi-Fi/Bluetooth প্যানেল খোলা।",
                    checked = perms.allowDisplayAndSettings,
                    masterEnabled = perms.masterEnabled,
                    testTag = "perm_display_switch",
                    onCheckedChange = {
                        viewModel.updatePermissions(perms.copy(allowDisplayAndSettings = it))
                    },
                    extraContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "অ্যাপ ব্রাইটনেস: ${sliderBrightness.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = sliderBrightness,
                                onValueChange = {
                                    sliderBrightness = it
                                    if (perms.masterEnabled && perms.allowDisplayAndSettings) {
                                        viewModel.executeManualBrightness(activity, it.toInt())
                                    }
                                },
                                valueRange = 10f..100f,
                                enabled = perms.masterEnabled && perms.allowDisplayAndSettings
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.executeManualSettings("WIFI") },
                                    enabled = perms.masterEnabled && perms.allowDisplayAndSettings
                                ) {
                                    Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Wi-Fi")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.executeManualSettings("BLUETOOTH") },
                                    enabled = perms.masterEnabled && perms.allowDisplayAndSettings
                                ) {
                                    Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Bluetooth")
                                }
                            }
                        }
                    }
                )
            }

            item {
                PermissionControlCard(
                    icon = Icons.Rounded.Alarm,
                    title = "অ্যালার্ম ও টাইমার",
                    description = "দৈনন্দিন কাজের জন্য অ্যালার্ম বা ফোকাস টাইমার সেট করা।",
                    checked = perms.allowAlarmsAndTimers,
                    masterEnabled = perms.masterEnabled,
                    testTag = "perm_alarms_switch",
                    onCheckedChange = {
                        viewModel.updatePermissions(perms.copy(allowAlarmsAndTimers = it))
                    },
                    extraContent = {
                        OutlinedButton(
                            onClick = { viewModel.executeManualTimer(300, "ফোকাস টাইমার") },
                            enabled = perms.masterEnabled && perms.allowAlarmsAndTimers
                        ) {
                            Text("৫ মিনিটের ফোকাস টাইমার পরীক্ষা")
                        }
                    }
                )
            }

            item {
                PermissionControlCard(
                    icon = Icons.Rounded.Call,
                    title = "ফোন ডায়ালার (Safe Dial)",
                    description = "নিরাপদভাবে নম্বর ডায়ালারে প্রস্তুত করা (কল নিশ্চিত করবেন আপনিই)।",
                    checked = perms.allowDialer,
                    masterEnabled = perms.masterEnabled,
                    testTag = "perm_dialer_switch",
                    onCheckedChange = {
                        viewModel.updatePermissions(perms.copy(allowDialer = it))
                    }
                )
            }

            item {
                PermissionControlCard(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "রিমাইন্ডার নোটিফিকেশন",
                    description = "গুরুত্বপূর্ণ কাজের সময় নোটিফিকেশন ও হালকা ভাইব্রেশন সতর্কতা।",
                    checked = perms.allowNotifications,
                    masterEnabled = perms.masterEnabled,
                    testTag = "perm_notifications_switch",
                    osStatusText = if (hasNotifPermission) "নোটিফিকেশন অনুমতি আছে" else "Android নোটিফিকেশন অনুমতি প্রয়োজন",
                    osPermissionGranted = hasNotifPermission,
                    onRequestOsPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onCheckedChange = {
                        viewModel.updatePermissions(perms.copy(allowNotifications = it))
                    },
                    extraContent = {
                        OutlinedButton(
                            onClick = { viewModel.executeManualTestNotification() },
                            enabled = perms.masterEnabled && perms.allowNotifications
                        ) {
                            Text("রিমাইন্ডার নোটিফিকেশন পরীক্ষা")
                        }
                    }
                )
            }

            // Audit Log Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "AI ডিভাইস কন্ট্রোল অডিট লগ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (logs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "এখনো কোনো AI ডিভাইস অ্যাকশন লগ নেই। চ্যাট থেকে কোনো কমান্ড দিলে এখানে রেকর্ড থাকবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    ActionLogRow(log)
                }
            }
        }
    }
}

@Composable
private fun PermissionControlCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    masterEnabled: Boolean,
    testTag: String,
    osStatusText: String? = null,
    osPermissionGranted: Boolean = true,
    onRequestOsPermission: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = if (checked && masterEnabled) 0.45f else 0.22f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (checked && masterEnabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked && masterEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = masterEnabled,
                    modifier = Modifier.testTag(testTag)
                )
            }

            if (osStatusText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (osPermissionGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (osPermissionGranted) EmeraldSuccess else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = osStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (osPermissionGranted) EmeraldSuccess else MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (!osPermissionGranted && onRequestOsPermission != null) {
                        TextButton(onClick = onRequestOsPermission) {
                            Text("সিস্টেম অনুমতি দিন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (extraContent != null) {
                extraContent()
            }
        }
    }
}

@Composable
private fun ActionLogRow(log: DeviceActionLog) {
    val isExecuted = log.status == "EXECUTED"
    val color = if (isExecuted) EmeraldSuccess else MaterialTheme.colorScheme.secondary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isExecuted) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${log.actionType} • ${log.status}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
        }
    }
}
