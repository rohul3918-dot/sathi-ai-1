package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.core.safeAuthStateFlow
import com.example.core.safeCurrentUser
import com.example.core.signOutUser
import com.example.data.model.VoicePersona
import com.example.data.repository.LocalFallbackSathiDataStore
import com.example.data.repository.SathiDataStore
import com.example.data.repository.SathiRepository
import com.example.device.DeviceControlManager
import com.example.device.VoiceAssistantManager
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
    val firebaseReady = remember(context) { isFirebaseReady(context) }
    val currentUser by remember(firebaseReady) {
        safeAuthStateFlow(context)
    }.collectAsStateWithLifecycle(initialValue = remember(firebaseReady) { safeCurrentUser(context) })

    LaunchedEffect(firebaseReady) {
        if (firebaseReady && safeCurrentUser(context) == null) {
            runCatching { CredentialManager.create(context) }.getOrNull()?.let { cm ->
                attemptAutoSignIn(
                    context = context,
                    credentialManager = cm,
                    onAuthSuccess = {},
                    onUnauthenticated = {},
                    scope = scope
                )
            }
        }
    }

    val user = currentUser
    val isSignedIn = firebaseReady && user != null
    val accountKey = if (isSignedIn) user!!.uid else "local_device_profile"

    // Open directly into clean AI chat UI without blocking startup.
    // Firestore is ONLY accessed when Google Sign-In has completed (isSignedIn == true).
    key(accountKey) {
        SathiHomeShell(
            currentUserId = accountKey,
            isSignedIn = isSignedIn,
            userDisplayName = if (isSignedIn) (user?.displayName ?: user?.email ?: "ব্যবহারকারী") else "সাইন-ইন করুন",
            userEmail = if (isSignedIn) user?.email else null,
            userPhotoUrl = if (isSignedIn) user?.photoUrl?.toString() else null,
            firebaseReady = firebaseReady
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SathiHomeShell(
    currentUserId: String,
    isSignedIn: Boolean,
    userDisplayName: String,
    userEmail: String?,
    userPhotoUrl: String?,
    firebaseReady: Boolean
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
                SathiViewModel(
                    repository = store,
                    aiEngine = SathiAiEngine(app),
                    deviceManager = DeviceControlManager(app),
                    voiceManager = VoiceAssistantManager(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    var currentRoute by rememberSaveable { mutableStateOf(SathiRoute.CHAT) }
    var showPersonaSheet by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }

    val permissions by viewModel.permissionsState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfileState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val aiName = permissions.assistantName.ifBlank { "সাথী" }
    val activePersona = VoicePersona.fromKey(permissions.voicePersona, permissions.voiceGender)
    val effectiveProfileName = userProfile.displayName.ifBlank { userDisplayName }
    val effectiveProfilePhoto = userProfile.effectivePhotoUrl.ifBlank { userPhotoUrl.orEmpty() }

    val triggerGoogleSignIn: () -> Unit = {
        val cm = credentialManager
        if (!firebaseReady) {
            authErrorMessage = "এই বিল্ডে Firebase (google-services.json) সক্রিয় নেই।"
            viewModel.showMessage("Google Sign-In-এর জন্য Firebase কনফিগারেশন প্রয়োজন।")
            currentRoute = SathiRoute.SETTINGS
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
            currentRoute = SathiRoute.CHAT
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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when (currentRoute) {
                SathiRoute.CHAT -> {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = aiName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                    onClick = { showPersonaSheet = true },
                                    modifier = Modifier.testTag("active_voice_persona_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = activePersona.shortLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "ভয়েস পরিবর্তন",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { currentRoute = SathiRoute.TASKS },
                                modifier = Modifier.testTag("nav_tasks_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Checklist,
                                    contentDescription = "দৈনন্দিন কাজ"
                                )
                            }
                            if (isSignedIn) {
                                IconButton(
                                    onClick = { currentRoute = SathiRoute.PROFILE },
                                    modifier = Modifier.testTag("header_auth_button")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .border(1.5.dp, EmeraldSuccess, CircleShape)
                                            .padding(1.5.dp)
                                    ) {
                                        ProfileAvatarImage(
                                            photoUrl = effectiveProfilePhoto,
                                            displayName = effectiveProfileName,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                    onClick = { currentRoute = SathiRoute.PROFILE },
                                    modifier = Modifier
                                        .padding(end = 2.dp)
                                        .testTag("header_auth_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
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
                            IconButton(
                                onClick = { currentRoute = SathiRoute.SETTINGS },
                                modifier = Modifier.testTag("nav_settings_button")
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
                SathiRoute.TASKS -> {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { currentRoute = SathiRoute.CHAT }) {
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
                            IconButton(onClick = { currentRoute = SathiRoute.SETTINGS }) {
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
                            IconButton(onClick = { currentRoute = SathiRoute.CHAT }) {
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
                            IconButton(onClick = { currentRoute = SathiRoute.CHAT }) {
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
                                onClick = { currentRoute = SathiRoute.SETTINGS },
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
                    onNavigateToSettings = { currentRoute = SathiRoute.SETTINGS }
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
                    onOpenProfile = { currentRoute = SathiRoute.PROFILE },
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
