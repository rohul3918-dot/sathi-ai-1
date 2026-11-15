package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.SathiAiEngine
import com.example.core.attemptAutoSignIn
import com.example.core.configureAppCheck
import com.example.core.isFirebaseReady
import com.example.core.onGoogleSignInClicked
import com.example.core.persistAuthenticatedUserSnapshot
import com.example.core.safeAuthStateFlow
import com.example.core.safeCurrentUser
import com.example.core.signOutUser
import com.example.data.model.UserProfile
import com.example.data.model.VoicePersona
import com.example.data.repository.AuthSessionStore
import com.example.data.repository.LocalFallbackSathiDataStore
import com.example.data.repository.LocalProfileCache
import com.example.data.repository.SathiDataStore
import com.example.data.repository.SathiRepository
import com.example.device.DeviceControlManager
import com.example.device.VoiceAssistantManager
import com.example.ui.DataUiState
import com.example.ui.SathiViewModel
import com.example.ui.components.VoicePersonaSheet
import com.example.ui.screens.AssistantChatScreen
import com.example.ui.screens.PermissionsDeviceScreen
import com.example.ui.screens.ProfileAvatarImage
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.SathiAiTheme
import com.google.firebase.firestore.FirebaseFirestore

private enum class SathiRoute {
    CHAT,
    TASKS,
    SETTINGS,
    PROFILE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            SathiAiTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firebaseReady = isFirebaseReady(context)
    val sessionStore = remember(context) {
        AuthSessionStore(context.applicationContext ?: context)
    }
    var cachedSession by remember(context) {
        mutableStateOf(sessionStore.load())
    }
    val currentUser by remember(firebaseReady) {
        safeAuthStateFlow(context)
    }.collectAsStateWithLifecycle(initialValue = remember(firebaseReady) { safeCurrentUser(context) })

    // Lift route state above key(accountKey) so signing in/out keeps the user on their current screen (e.g. Profile/Settings).
    var currentRoute by rememberSaveable { mutableStateOf(SathiRoute.CHAT) }

    LaunchedEffect(firebaseReady) {
        if (firebaseReady && safeCurrentUser(context) == null) {
            runCatching { CredentialManager.create(context) }.getOrNull()?.let { cm ->
                attemptAutoSignIn(
                    context = context,
                    credentialManager = cm,
                    onAuthSuccess = {
                        cachedSession = sessionStore.load()
                    },
                    onUnauthenticated = {},
                    scope = scope
                )
            }
        }
    }

    val user = currentUser
    val isSignedIn = firebaseReady && user != null
    val accountKey = if (isSignedIn) user!!.uid else "local_device_profile"

    LaunchedEffect(user?.uid, user?.displayName, user?.email, user?.photoUrl) {
        if (user != null && firebaseReady) {
            persistAuthenticatedUserSnapshot(context, user)
            cachedSession = sessionStore.load()
        }
    }

    val activeCachedSession = cachedSession?.takeIf { user != null && it.uid == user.uid }
    val resolvedDisplayName = if (isSignedIn) {
        user?.displayName.orEmpty().trim()
            .ifBlank { activeCachedSession?.displayName.orEmpty() }
            .ifBlank { user?.email?.substringBefore("@").orEmpty() }
            .ifBlank { "ব্যবহারকারী" }
    } else {
        "সাইন-ইন করুন"
    }
    val resolvedEmail = if (isSignedIn) {
        user?.email.orEmpty().trim()
            .ifBlank { activeCachedSession?.email.orEmpty() }
            .ifBlank { null }
    } else {
        null
    }
    val resolvedPhotoUrl = if (isSignedIn) {
        user?.photoUrl?.toString().orEmpty().trim()
            .ifBlank { activeCachedSession?.photoUrl.orEmpty() }
            .ifBlank { null }
    } else {
        null
    }

    // Open directly into clean AI chat UI without blocking startup.
    // Firestore is ONLY accessed when Google Sign-In has completed (isSignedIn == true).
    key(accountKey) {
        SathiHomeShell(
            currentUserId = accountKey,
            isSignedIn = isSignedIn,
            userDisplayName = resolvedDisplayName,
            userEmail = resolvedEmail,
            userPhotoUrl = resolvedPhotoUrl,
            firebaseReady = firebaseReady,
            currentRoute = currentRoute,
            onRouteChange = { currentRoute = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SathiHomeShell(
    currentUserId: String,
    isSignedIn: Boolean,
    userDisplayName: String,
    userEmail: String?,
    userPhotoUrl: String?,
    firebaseReady: Boolean,
    currentRoute: SathiRoute,
    onRouteChange: (SathiRoute) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }

    val viewModel: SathiViewModel = viewModel(
        key = "${currentUserId}_${isSignedIn}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val store: SathiDataStore = if (isSignedIn && firebaseReady) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    SathiRepository(db, app)
                } else {
                    LocalFallbackSathiDataStore(app)
                }
                val cachedProfile = LocalProfileCache(app).load(currentUserId)
                val fallbackName = userDisplayName.takeIf {
                    it.isNotBlank() && it != "সাইন-ইন করুন"
                }.orEmpty()
                val seededProfile = (cachedProfile ?: UserProfile(userId = currentUserId)).let { base ->
                    base.copy(
                        userId = currentUserId,
                        displayName = base.displayName.ifBlank { fallbackName },
                        email = base.email.ifBlank { userEmail.orEmpty() },
                        googlePhotoUrl = base.googlePhotoUrl.ifBlank { userPhotoUrl.orEmpty() }
                    )
                }
                SathiViewModel(
                    repository = store,
                    aiEngine = SathiAiEngine(app),
                    deviceManager = DeviceControlManager(app),
                    voiceManager = VoiceAssistantManager(app),
                    currentUserId = currentUserId,
                    initialUserProfile = seededProfile
                )
            }
        }
    )

    var showPersonaSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }

    val permissions by viewModel.permissionsState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfileState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val pendingTaskCount = (tasksState as? DataUiState.Success)?.data?.count { !it.completed } ?: 0

    LaunchedEffect(firebaseReady) {
        if (firebaseReady) {
            viewModel.warmUpAi()
        }
    }

    LaunchedEffect(currentUserId, isSignedIn, userDisplayName, userEmail, userPhotoUrl) {
        if (isSignedIn) {
            val safeName = userDisplayName.takeIf {
                it.isNotBlank() && it != "সাইন-ইন করুন"
            }.orEmpty()
            viewModel.syncSignedInUserIdentity(
                displayName = safeName,
                email = userEmail.orEmpty(),
                photoUrl = userPhotoUrl.orEmpty()
            )
        }
    }

    val aiName = permissions.assistantName.ifBlank { "সাথী" }
    val activePersona = VoicePersona.fromKey(permissions.voicePersona, permissions.voiceGender)
    val effectiveProfileName = userProfile.displayName.ifBlank { userDisplayName }
    val effectiveProfilePhoto = userProfile.effectivePhotoUrl.ifBlank { userPhotoUrl.orEmpty() }

    val triggerGoogleSignIn: () -> Unit = {
        if (!isSigningIn) {
            val cm = credentialManager
            if (!isFirebaseReady(context)) {
                authErrorMessage = "এই বিল্ডে Firebase (google-services.json) সক্রিয় নেই।"
                viewModel.showMessage("Google Sign-In-এর জন্য Firebase কনফিগারেশন প্রয়োজন।")
                onRouteChange(SathiRoute.SETTINGS)
            } else if (cm == null) {
                authErrorMessage = "এই ডিভাইসে Credential Manager পাওয়া যায়নি।"
                viewModel.showMessage("Credential Manager পাওয়া যায়নি।")
            } else {
                authErrorMessage = null
                isSigningIn = true
                onGoogleSignInClicked(
                    context = context,
                    credentialManager = cm,
                    onAuthSuccess = {
                        isSigningIn = false
                        viewModel.showMessage("Google সাইন-ইন সম্পন্ন হয়েছে ✨")
                    },
                    onAuthError = { msg ->
                        isSigningIn = false
                        authErrorMessage = msg
                        viewModel.showMessage(msg)
                    },
                    scope = scope,
                    onAuthCancelled = {
                        isSigningIn = false
                    }
                )
            }
        }
    }

    val triggerSignOut: () -> Unit = {
        credentialManager?.let { cm ->
            signOutUser(
                context = context,
                credentialManager = cm,
                scope = scope,
                onSignOutComplete = {
                    viewModel.showMessage("সাইন আউট সম্পন্ন হয়েছে")
                }
            )
        }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissBanner()
        }
    }

    if (currentRoute != SathiRoute.CHAT) {
        BackHandler {
            onRouteChange(SathiRoute.CHAT)
        }
    }

    if (showPersonaSheet) {
        VoicePersonaSheet(
            selectedPersonaKey = activePersona.key,
            onSelectPersona = { persona ->
                viewModel.selectVoicePersona(persona.key)
            },
            onPreviewVoice = { persona ->
                viewModel.testAssistantVoice(persona.key, permissions.voiceLocale)
            },
            onDismiss = { showPersonaSheet = false }
        )
    }

    if (showRenameDialog) {
        QuickRenameAssistantDialog(
            currentName = aiName,
            onConfirm = { newName ->
                viewModel.renameAssistant(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when (currentRoute) {
                SathiRoute.CHAT -> {
                    Surface(
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            // Row 1: One-tap Assistant Name + Signed-in Profile / Sign In + Settings
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    onClick = { showRenameDialog = true },
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .testTag("top_bar_assistant_name_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = aiName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "অ্যাসিস্ট্যান্টের নাম পরিবর্তন",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (isSignedIn) {
                                        val topBarUserName = effectiveProfileName
                                            .takeIf { it.isNotBlank() && it != "সাইন-ইন করুন" }
                                            ?: userProfile.nickname.ifBlank { "প্রোফাইল" }
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                            onClick = { onRouteChange(SathiRoute.PROFILE) },
                                            modifier = Modifier
                                                .padding(end = 2.dp)
                                                .testTag("header_auth_button")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .border(1.5.dp, EmeraldSuccess, CircleShape)
                                                        .padding(1.5.dp)
                                                ) {
                                                    ProfileAvatarImage(
                                                        photoUrl = effectiveProfilePhoto,
                                                        displayName = topBarUserName,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Text(
                                                    text = topBarUserName,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 96.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                            onClick = triggerGoogleSignIn,
                                            enabled = !isSigningIn,
                                            modifier = Modifier
                                                .padding(end = 2.dp)
                                                .testTag("header_auth_button")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                if (isSigningIn) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        text = "Signing in...",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Outlined.AccountCircle,
                                                        contentDescription = "Google Sign-In",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "Sign In",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { onRouteChange(SathiRoute.SETTINGS) },
                                        modifier = Modifier.testTag("nav_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Settings,
                                            contentDescription = "সেটিংস"
                                        )
                                    }
                                }
                            }

                            // Row 2: Instant one-tap Voice Number selector + Daily Tasks directly on top bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                    onClick = { showPersonaSheet = true },
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("active_voice_persona_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = activePersona.shortLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "ভয়েস পরিবর্তন",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                    onClick = { onRouteChange(SathiRoute.TASKS) },
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("nav_tasks_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Checklist,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (pendingTaskCount > 0) "দৈনন্দিন কাজ ($pendingTaskCount)" else "দৈনন্দিন কাজ",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                            )
                        }
                    }
                }
                SathiRoute.TASKS -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { onRouteChange(SathiRoute.CHAT) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "চ্যাটে ফিরুন"
                                )
                            }
                        },
                        title = {
                            Text("দৈনন্দিন কাজ ও রুটিন", style = MaterialTheme.typography.titleLarge)
                        },
                        actions = {
                            IconButton(onClick = { onRouteChange(SathiRoute.SETTINGS) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "সেটিংস"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
                SathiRoute.SETTINGS -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { onRouteChange(SathiRoute.CHAT) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "চ্যাটে ফিরুন"
                                )
                            }
                        },
                        title = {
                            Text("সেটিংস ও পারমিশন", style = MaterialTheme.typography.titleLarge)
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
                SathiRoute.PROFILE -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { onRouteChange(SathiRoute.CHAT) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "চ্যাটে ফিরুন"
                                )
                            }
                        },
                        title = {
                            Text("ব্যবহারকারী প্রোফাইল", style = MaterialTheme.typography.titleLarge)
                        },
                        actions = {
                            IconButton(
                                onClick = { onRouteChange(SathiRoute.SETTINGS) },
                                modifier = Modifier.testTag("profile_to_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "সেটিংস"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentRoute) {
                SathiRoute.CHAT -> AssistantChatScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { onRouteChange(SathiRoute.SETTINGS) }
                )
                SathiRoute.TASKS -> TasksScreen(viewModel = viewModel)
                SathiRoute.SETTINGS -> PermissionsDeviceScreen(
                    viewModel = viewModel,
                    isSignedIn = isSignedIn,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    userPhotoUrl = userPhotoUrl,
                    isSigningIn = isSigningIn,
                    authErrorMessage = authErrorMessage,
                    onOpenProfile = { onRouteChange(SathiRoute.PROFILE) },
                    onGoogleSignIn = triggerGoogleSignIn,
                    onSignOut = triggerSignOut
                )
                SathiRoute.PROFILE -> ProfileScreen(
                    viewModel = viewModel,
                    currentUserId = currentUserId,
                    isSignedIn = isSignedIn,
                    googleDisplayName = userDisplayName,
                    googleEmail = userEmail,
                    googlePhotoUrl = userPhotoUrl,
                    isSigningIn = isSigningIn,
                    authErrorMessage = authErrorMessage,
                    onGoogleSignIn = triggerGoogleSignIn,
                    onSignOut = triggerSignOut
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickRenameAssistantDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember(currentName) { mutableStateOf(currentName) }
    val presetNames = listOf("সাথী", "কুসুম", "নীরা", "বন্ধু")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "অ্যাসিস্ট্যান্টের নাম পরিবর্তন",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "আপনি যে নামে ডাকবেন, অ্যাসিস্ট্যান্ট সেই নামেই সাড়া দেবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 40) nameInput = it },
                    label = { Text("অ্যাসিস্ট্যান্টের নাম") },
                    placeholder = { Text("যেমন: কুসুম বা সাথী") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_rename_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetNames.forEach { preset ->
                        FilterChip(
                            selected = nameInput.trim() == preset,
                            onClick = { nameInput = preset },
                            label = { Text(preset) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nameInput.trim().ifBlank { "সাথী" }) },
                modifier = Modifier.testTag("quick_rename_save_button")
            ) {
                Text("সংরক্ষণ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

