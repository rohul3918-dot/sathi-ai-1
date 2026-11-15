package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.core.isFirebaseReady
import com.example.ui.SathiViewModel
import com.example.ui.theme.EmeraldSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun ProfileScreen(
    viewModel: SathiViewModel,
    currentUserId: String,
    isSignedIn: Boolean,
    googleDisplayName: String,
    googleEmail: String?,
    googlePhotoUrl: String?,
    isSigningIn: Boolean,
    authErrorMessage: String?,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val firebaseReady = isFirebaseReady(context)

    if (!isSignedIn) {
        UnauthenticatedProfilePrompt(
            firebaseReady = firebaseReady,
            isSigningIn = isSigningIn,
            authErrorMessage = authErrorMessage,
            onGoogleSignIn = onGoogleSignIn,
            modifier = modifier
        )
        return
    }

    val savedProfile by viewModel.userProfileState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSavingProfile.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val resolvedGoogleName = googleDisplayName.takeIf {
        it.isNotBlank() && it != "সাইন-ইন করুন"
    }.orEmpty()
    val resolvedEmail = savedProfile.email.ifBlank { googleEmail.orEmpty() }
    val resolvedGooglePhoto = googlePhotoUrl.orEmpty().ifBlank { savedProfile.googlePhotoUrl }

    val initialDisplayName = savedProfile.displayName.ifBlank { resolvedGoogleName }
    val initialNickname = savedProfile.nickname
    val initialBio = savedProfile.bio
    val initialCustomPhoto = savedProfile.customPhotoUri

    var displayNameInput by remember(savedProfile.displayName, resolvedGoogleName) {
        mutableStateOf(initialDisplayName)
    }
    var nicknameInput by remember(savedProfile.nickname) {
        mutableStateOf(initialNickname)
    }
    var bioInput by remember(savedProfile.bio) {
        mutableStateOf(initialBio)
    }
    var customPhotoUriInput by remember(savedProfile.customPhotoUri) {
        mutableStateOf(initialCustomPhoto)
    }
    var isProcessingPhoto by remember { mutableStateOf(false) }

    val activePhotoModel = customPhotoUriInput.ifBlank { resolvedGooglePhoto }
    val hasChanges = displayNameInput.trim() != savedProfile.displayName.trim() ||
        nicknameInput.trim() != savedProfile.nickname.trim() ||
        bioInput.trim() != savedProfile.bio.trim() ||
        customPhotoUriInput.trim() != savedProfile.customPhotoUri.trim() ||
        savedProfile.displayName.isBlank()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingPhoto = true
                val copiedPath = copyAvatarToAppFiles(context, currentUserId, uri)
                if (copiedPath != null) {
                    customPhotoUriInput = copiedPath
                    viewModel.showMessage("নতুন ছবি নির্বাচিত হয়েছে। সংরক্ষণ করতে Update Profile চাপুন।")
                } else {
                    viewModel.showMessage("ছবি লোড করা যায়নি। অনুগ্রহ করে অন্য ছবি বেছে নিন।")
                }
                isProcessingPhoto = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Avatar & Google Identity Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 3.dp,
                                    brush = Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isProcessingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                ProfileAvatarImage(
                                    photoUrl = activePhotoModel,
                                    displayName = displayNameInput.ifBlank { resolvedGoogleName },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = displayNameInput.ifBlank { "ব্যবহারকারী" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (nicknameInput.isNotBlank()) {
                                Text(
                                    text = "ডাকনাম: ${nicknameInput.trim()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (resolvedEmail.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = resolvedEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EmeraldSuccess.copy(alpha = 0.14f),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudDone,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Google অ্যাকাউন্ট ও ক্লাউড সিঙ্ক সক্রিয়",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSuccess,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isProcessingPhoto && !isSaving,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_pick_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("ছবি পরিবর্তন")
                            }

                            if (customPhotoUriInput.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        customPhotoUriInput = ""
                                        viewModel.showMessage("Google প্রোফাইল ছবি পুনরায় নির্বাচিত হয়েছে। সংরক্ষণ করতে Update Profile চাপুন।")
                                    },
                                    enabled = !isProcessingPhoto && !isSaving,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("profile_reset_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Google ছবি রাখুন")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Editable Profile Form Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            RoundedCornerShape(22.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "প্রোফাইল তথ্য এডিট করুন",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedTextField(
                            value = displayNameInput,
                            onValueChange = { if (it.length <= 80) displayNameInput = it },
                            label = { Text("ডিসপ্লে নাম (Display Name)") },
                            placeholder = { Text("আপনার পূর্ণ নাম") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Person, contentDescription = null)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_display_name_input")
                        )

                        OutlinedTextField(
                            value = nicknameInput,
                            onValueChange = { if (it.length <= 50) nicknameInput = it },
                            label = { Text("ডাকনাম / সাথী যেভাবে ডাকবে (Preferred Name)") },
                            placeholder = { Text("যেমন: রুহুল, ভাইয়া, বন্ধু") },
                            supportingText = {
                                Text("সাথী AI কথা বলার সময় আপনাকে এই নামে সম্বোধন করবে ✨")
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Badge, contentDescription = null)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_nickname_input")
                        )

                        OutlinedTextField(
                            value = bioInput,
                            onValueChange = { if (it.length <= 300) bioInput = it },
                            label = { Text("সংক্ষিপ্ত পরিচিতি / নোট (Bio)") },
                            placeholder = { Text("যেমন: সফটওয়্যার ইঞ্জিনিয়ার, সকালে হাঁটতে পছন্দ করি") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_bio_input")
                        )

                        Button(
                            onClick = {
                                viewModel.updateUserProfile(
                                    displayName = displayNameInput,
                                    nickname = nicknameInput,
                                    bio = bioInput,
                                    email = resolvedEmail,
                                    googlePhotoUrl = resolvedGooglePhoto,
                                    customPhotoUri = customPhotoUriInput
                                )
                            },
                            enabled = !isSaving && !isProcessingPhoto && displayNameInput.isNotBlank() && hasChanges,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("save_profile_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("সংরক্ষণ হচ্ছে...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = if (hasChanges) Icons.Rounded.AutoAwesome else Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (hasChanges) "Update Profile" else "প্রোফাইল সংরক্ষিত আছে",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 3. Sign-Out Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "অ্যাকাউন্ট সেশন",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "সাইন আউট করলে ক্লাউড সিঙ্ক বন্ধ হবে এবং নিরাপদভাবে লোকাল সেশনে ফিরে যাবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("profile_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("সাইন আউট করুন", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnauthenticatedProfilePrompt(
    firebaseReady: Boolean,
    isSigningIn: Boolean,
    authErrorMessage: String?,
    onGoogleSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(26.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = "ব্যবহারকারী প্রোফাইল",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Text(
                    text = "আপনার প্রোফাইল সেটআপ করুন",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (firebaseReady) {
                        "Google অ্যাকাউন্ট দিয়ে সাইন-ইন করলে আপনার নাম, ডাকনাম, প্রোফাইল ছবি, কাজ ও চ্যাট ইতিহাস সব ডিভাইসে সিঙ্ক থাকবে।"
                    } else {
                        "এই বিল্ডে Firebase কনফিগারেশন অনুপস্থিত রয়েছে। সাইন-ইন চালু করতে google-services.json যোগ করুন।"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (!authErrorMessage.isNullOrBlank()) {
                    Text(
                        text = authErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onGoogleSignIn,
                    enabled = !isSigningIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("profile_google_sign_in_button")
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("সাইন-ইন হচ্ছে...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Google দিয়ে সাইন-ইন করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileAvatarImage(
    photoUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier
) {
    val cleanUrl = photoUrl.orEmpty().trim()
    val initials = displayName.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "S" }

    if (cleanUrl.isBlank()) {
        AvatarMonogram(initials = initials, modifier = modifier)
        return
    }

    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(cleanUrl)
            .crossfade(true)
            .build(),
        contentDescription = "$displayName এর প্রোফাইল ছবি",
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape),
        loading = {
            AvatarMonogram(initials = initials, modifier = Modifier.fillMaxSize())
        },
        error = {
            AvatarMonogram(initials = initials, modifier = Modifier.fillMaxSize())
        }
    )
}

@Composable
private fun AvatarMonogram(
    initials: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private suspend fun copyAvatarToAppFiles(
    context: Context,
    userId: String,
    sourceUri: Uri
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
        val safeUserPrefix = userId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        avatarDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("${safeUserPrefix}_")) {
                file.delete()
            }
        }

        val targetFile = File(avatarDir, "${safeUserPrefix}_${System.currentTimeMillis()}.jpg")
        val decoded = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return@withContext null

        val maxSide = 512
        val scale = minOf(
            1f,
            maxSide.toFloat() / maxOf(decoded.width, decoded.height).coerceAtLeast(1)
        )
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }

        FileOutputStream(targetFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        if (scaled !== decoded) {
            scaled.recycle()
        }
        decoded.recycle()
        Uri.fromFile(targetFile).toString()
    }.getOrNull()
}
