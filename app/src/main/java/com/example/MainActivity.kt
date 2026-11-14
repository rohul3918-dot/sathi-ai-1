package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.core.safeAuthStateFlow
import com.example.core.safeCurrentUser
import com.example.core.signOutUser
import com.example.data.repository.LocalFallbackSathiDataStore
import com.example.data.repository.SathiDataStore
import com.example.data.repository.SathiRepository
import com.example.device.DeviceControlManager
import com.example.device.VoiceAssistantManager
import com.example.ui.SathiViewModel
import com.example.ui.components.AuthScreen
import com.example.ui.screens.AssistantChatScreen
import com.example.ui.screens.PermissionsDeviceScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.SathiAiTheme
import com.google.firebase.firestore.FirebaseFirestore

private enum class SathiTab(
    val labelBn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    ASSISTANT("সাথী চ্যাট", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "tab_assistant"),
    TASKS("দৈনন্দিন কাজ", Icons.Rounded.Checklist, Icons.Outlined.Checklist, "tab_tasks"),
    PERMISSIONS("ডিভাইস ও পারমিশন", Icons.Rounded.Tune, Icons.Outlined.Tune, "tab_permissions")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Safely configure App Check (no-op if google-services.json is absent)
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

    var localModeActive by rememberSaveable { mutableStateOf(false) }

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
    when {
        firebaseReady && user != null -> {
            key(user.uid) {
                SathiHomeShell(
                    currentUserId = user.uid,
                    userDisplayName = user.displayName ?: user.email ?: "ব্যবহারকারী",
                    isLocalMode = false,
                    onExitLocalMode = {}
                )
            }
        }
        !firebaseReady && localModeActive -> {
            key("local_user") {
                SathiHomeShell(
                    currentUserId = "local_user",
                    userDisplayName = "ব্যক্তিগত লোকাল প্রোফাইল",
                    isLocalMode = true,
                    onExitLocalMode = { localModeActive = false }
                )
            }
        }
        else -> {
            AuthScreen(
                onAuthSuccess = {},
                firebaseConfigured = firebaseReady,
                onContinueLocalMode = {
                    if (!firebaseReady) {
                        localModeActive = true
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SathiHomeShell(
    currentUserId: String,
    userDisplayName: String,
    isLocalMode: Boolean,
    onExitLocalMode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        runCatching { CredentialManager.create(context) }.getOrNull()
    }

    val viewModel: SathiViewModel = viewModel(
        key = "${currentUserId}_${isLocalMode}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val store: SathiDataStore = if (isLocalMode) {
                    LocalFallbackSathiDataStore(app)
                } else {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    SathiRepository(db)
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

    var selectedTab by rememberSaveable { mutableStateOf(SathiTab.ASSISTANT) }
    val permissions by viewModel.permissionsState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val aiName = permissions.assistantName.ifBlank { "সাথী" }

    LaunchedEffect(statusBanner) {
        statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissBanner()
        }
    }

    if (selectedTab != SathiTab.ASSISTANT) {
        BackHandler {
            selectedTab = SathiTab.ASSISTANT
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "$aiName AI",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = userDisplayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (permissions.masterEnabled)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedTab = SathiTab.PERMISSIONS },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = "পারমিশন স্ট্যাটাস",
                                    modifier = Modifier.size(15.dp),
                                    tint = if (permissions.masterEnabled)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (permissions.masterEnabled) "সুরক্ষিত" else "কন্ট্রোল বন্ধ",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (isLocalMode) {
                                    onExitLocalMode()
                                } else if (credentialManager != null) {
                                    signOutUser(
                                        context = context,
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onSignOutComplete = onExitLocalMode
                                    )
                                }
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "সাইন আউট"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        SathiTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val label = if (tab == SathiTab.ASSISTANT) "$aiName চ্যাট" else tab.labelBn
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.size(12.dp))
                        SathiTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val label = if (tab == SathiTab.ASSISTANT) "$aiName চ্যাট" else tab.labelBn
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        SathiTab.ASSISTANT -> AssistantChatScreen(
                            viewModel = viewModel,
                            onNavigateToPermissions = { selectedTab = SathiTab.PERMISSIONS }
                        )
                        SathiTab.TASKS -> TasksScreen(viewModel = viewModel)
                        SathiTab.PERMISSIONS -> PermissionsDeviceScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
