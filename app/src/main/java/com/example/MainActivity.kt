package com.example

import android.Manifest
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.BananaAiViewModel
import com.example.ai.configureAppCheck
import com.example.auth.GoogleAuthHelper
import com.example.data.LocalPreferencesRepository
import com.example.data.PairingStatus
import com.example.data.SessionStatus
import com.example.data.UiState
import com.example.data.VitRemoteRepository
import com.example.ui.AuthScreen
import com.example.ui.BananaAiScreen
import com.example.ui.ChatScreen
import com.example.ui.DashboardScreen
import com.example.ui.QrScannerOverlay
import com.example.ui.RemoteControlScreen
import com.example.ui.RemoteViewerScreen
import com.example.ui.VitRemoteViewModel
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.stringsFor
import com.example.ui.theme.LocalVitRemoteColors
import com.example.ui.theme.VitRemoteTheme
import com.example.util.PermissionStatusHelper
import com.example.util.SessionNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class MainTab {
    THIS_DEVICE,
    REMOTE_CONTROL,
    CHAT
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        SessionNotificationHelper.ensureChannels(this)
        setContent {
            VitRemoteAppRoot()
        }
    }
}

@Composable
fun VitRemoteAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localPrefs = remember(context) { LocalPreferencesRepository(context.applicationContext) }

    val language by localPrefs.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.BN)
    val darkTheme by localPrefs.darkThemeFlow.collectAsStateWithLifecycle(initialValue = true)

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    VitRemoteTheme(darkTheme = darkTheme) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                localPrefs = localPrefs,
                language = language,
                darkTheme = darkTheme,
                onToggleLanguage = {
                    scope.launch {
                        val next = if (language == AppLanguage.BN) AppLanguage.EN else AppLanguage.BN
                        localPrefs.setLanguage(next)
                    }
                },
                onToggleDarkTheme = {
                    scope.launch { localPrefs.setDarkTheme(!darkTheme) }
                },
                onAuthSuccess = { signedInUser ->
                    currentUser = signedInUser
                }
            )
        } else {
            key(user.uid) {
                AuthenticatedVitRemoteShell(user = user)
            }
        }
    }
}

@Composable
private fun AuthenticatedVitRemoteShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: VitRemoteViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                VitRemoteViewModel(
                    repository = VitRemoteRepository(db),
                    localPrefs = LocalPreferencesRepository(app),
                    currentUserId = user.uid
                )
            }
        }
    )

    val language by viewModel.language.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val localUserIdentity by viewModel.localUserIdentity.collectAsStateWithLifecycle()
    val deviceCode by viewModel.deviceCode.collectAsStateWithLifecycle()
    val isDeviceOnline by viewModel.isDeviceOnline.collectAsStateWithLifecycle()
    val isSendingPairing by viewModel.isSendingPairing.collectAsStateWithLifecycle()
    val pairingsState by viewModel.pairingsState.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val selectedChatPairingId by viewModel.selectedChatPairingId.collectAsStateWithLifecycle()
    val chatMessagesState by viewModel.chatMessagesState.collectAsStateWithLifecycle()
    val activeViewerSessionId by viewModel.activeViewerSessionId.collectAsStateWithLifecycle()
    val isHostStreaming by viewModel.isHostStreaming.collectAsStateWithLifecycle()
    val hostStartedAtMs by viewModel.hostStartedAtMs.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()

    val strings = stringsFor(language)
    val extColors = LocalVitRemoteColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTab by rememberSaveable { mutableStateOf(MainTab.THIS_DEVICE) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showBananaStudio by rememberSaveable { mutableStateOf(false) }
    val bananaViewModel: BananaAiViewModel = viewModel(key = "banana_${user.uid}")
    var permissionSnapshot by remember { mutableStateOf(PermissionStatusHelper.readSnapshot(context)) }

    // Refresh real Android permissions & battery status whenever user returns from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionSnapshot = PermissionStatusHelper.readSnapshot(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Collect one-shot snackbar feedback
    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Official Android MediaProjection system consent launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onMediaProjectionConsentResult(
            context = context,
            resultCode = result.resultCode,
            data = result.data
        )
    }

    val launchMediaProjectionConsent: () -> Unit = {
        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    // Runtime Android 13+ POST_NOTIFICATIONS launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionSnapshot = PermissionStatusHelper.readSnapshot(context)
    }

    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PermissionStatusHelper.openNotificationSettings(context)
        }
    }

    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
    val pairings = (pairingsState as? UiState.Success)?.data.orEmpty()

    val incomingSessionRequests = remember(sessions, deviceCode) {
        sessions.filter {
            it.hostDeviceCode == deviceCode && it.parsedStatus() == SessionStatus.REQUESTED
        }
    }

    val pendingRemoteBadgeCount = remember(pairings, incomingSessionRequests, deviceCode) {
        val incomingPairCount = pairings.count {
            it.parsedStatus() == PairingStatus.PENDING && it.isIncomingFor(deviceCode)
        }
        incomingPairCount + incomingSessionRequests.size
    }

    val activeViewerSession = remember(sessions, activeViewerSessionId) {
        if (activeViewerSessionId.isNullOrBlank()) null
        else sessions.firstOrNull { it.id == activeViewerSessionId }
    }

    // Dedicated Full-Screen Remote Viewer Interface when viewing a session
    if (activeViewerSession != null) {
        RemoteViewerScreen(
            session = activeViewerSession,
            remoteVideoTrack = remoteVideoTrack,
            telemetry = telemetry,
            strings = strings,
            onStartWebRtc = {
                viewModel.ensureViewerWebRtcStarted(context, activeViewerSession)
            },
            onChangeQuality = { quality ->
                viewModel.changeViewerQuality(activeViewerSession.id, quality)
            },
            onDisconnect = {
                viewModel.disconnectViewer(context, notifyEndSession = activeViewerSession.parsedStatus().isLiveOrPending())
            }
        )
        return
    }

    // On-Demand QR Code Scanner Overlay
    if (showQrScanner) {
        QrScannerOverlay(
            strings = strings,
            onCodeScanned = { scannedCode ->
                showQrScanner = false
                viewModel.sendPairingRequestByCode(scannedCode)
            },
            onDismiss = { showQrScanner = false }
        )
        return
    }

    // Dedicated Banana AI Studio Interface
    if (showBananaStudio) {
        BananaAiScreen(
            viewModel = bananaViewModel,
            strings = strings,
            onClose = { showBananaStudio = false }
        )
        return
    }

    // Adaptive Layout: NavigationRail on Expanded tablets/foldables, 3-item NavigationBar on phones
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 720.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars,
                        tonalElevation = 0.dp
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        badgeCount = if (tab == MainTab.REMOTE_CONTROL) pendingRemoteBadgeCount else 0
                                    )
                                },
                                label = {
                                    Text(
                                        text = tabLabel(tab, strings),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = extColors.onlineGreen,
                                    selectedTextColor = extColors.onlineGreen,
                                    indicatorColor = extColors.onlineGreenContainer,
                                    unselectedIconColor = extColors.subtleText,
                                    unselectedTextColor = extColors.subtleText
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
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
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    TabIconWithBadge(
                                        tab = tab,
                                        selected = selected,
                                        badgeCount = if (tab == MainTab.REMOTE_CONTROL) pendingRemoteBadgeCount else 0
                                    )
                                },
                                label = { Text(tabLabel(tab, strings)) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (activeTab) {
                        MainTab.THIS_DEVICE -> DashboardScreen(
                            user = user,
                            localUserIdentity = localUserIdentity,
                            deviceCode = deviceCode,
                            deviceName = viewModel.deviceModelName,
                            isOnline = isDeviceOnline,
                            isHostStreaming = isHostStreaming,
                            hostStartedAtMs = hostStartedAtMs,
                            telemetry = telemetry,
                            incomingSessionRequests = incomingSessionRequests,
                            permissionSnapshot = permissionSnapshot,
                            language = language,
                            darkTheme = darkTheme,
                            strings = strings,
                            onToggleLanguage = { viewModel.toggleLanguage() },
                            onToggleDarkTheme = { viewModel.toggleDarkTheme() },
                            onLogoutClick = {
                                GoogleAuthHelper.signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    beforeSignOut = { viewModel.prepareForSignOut(context) }
                                )
                            },
                            onConnectDeviceClick = { activeTab = MainTab.REMOTE_CONTROL },
                            onCopySuccess = { msg -> viewModel.emitFeedback(msg) },
                            onStopSharingClick = { viewModel.stopHostScreenSharing(context) },
                            onApproveIncomingSession = { session ->
                                viewModel.approveIncomingSessionRequest(session, launchMediaProjectionConsent)
                            },
                            onDeclineIncomingSession = { session ->
                                viewModel.declineIncomingSessionRequest(session)
                            },
                            onRequestNotificationPermission = requestNotifications,
                            onOpenBananaAiClick = { showBananaStudio = true }
                        )

                        MainTab.REMOTE_CONTROL -> RemoteControlScreen(
                            myDeviceCode = deviceCode,
                            pairingsState = pairingsState,
                            sessionsState = sessionsState,
                            isSendingPairing = isSendingPairing,
                            strings = strings,
                            onSendPairingCode = { code -> viewModel.sendPairingRequestByCode(code) },
                            onOpenQrScanner = { showQrScanner = true },
                            onAcceptPairing = { id -> viewModel.respondToPairingRequest(id, accept = true) },
                            onRejectPairing = { id -> viewModel.respondToPairingRequest(id, accept = false) },
                            onRevokePairing = { id -> viewModel.revokePairing(id) },
                            onDeleteHistory = { id -> viewModel.deleteHistoryRecord(id) },
                            onRequestViewScreen = { pairing -> viewModel.requestViewRemoteScreen(pairing) },
                            onShareMyScreen = { pairing ->
                                viewModel.startSharingMyScreenWithPair(pairing, launchMediaProjectionConsent)
                            },
                            onOpenChatForPairing = { pairing ->
                                viewModel.selectChatPairing(pairing.id)
                                activeTab = MainTab.CHAT
                            },
                            onApproveIncomingSession = { session ->
                                viewModel.approveIncomingSessionRequest(session, launchMediaProjectionConsent)
                            },
                            onDeclineIncomingSession = { session ->
                                viewModel.declineIncomingSessionRequest(session)
                            },
                            onJoinActiveViewerSession = { session ->
                                viewModel.openViewerForSession(session.id)
                            }
                        )

                        MainTab.CHAT -> ChatScreen(
                            currentUserId = user.uid,
                            myDeviceCode = deviceCode,
                            pairingsState = pairingsState,
                            selectedPairingId = selectedChatPairingId,
                            messagesState = chatMessagesState,
                            strings = strings,
                            onSelectPairing = { id -> viewModel.selectChatPairing(id) },
                            onSendMessage = { pairing, text -> viewModel.sendChatMessage(pairing, text) },
                            onRequestScreenFromChat = { pairing ->
                                viewModel.requestViewRemoteScreen(pairing)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: MainTab,
    selected: Boolean,
    badgeCount: Int
) {
    val icon = when (tab) {
        MainTab.THIS_DEVICE -> if (selected) Icons.Filled.PhoneAndroid else Icons.Outlined.PhoneAndroid
        MainTab.REMOTE_CONTROL -> if (selected) Icons.Filled.Devices else Icons.Outlined.Devices
        MainTab.CHAT -> if (selected) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat
    }
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(badgeCount.toString()) }
            }
        ) {
            Icon(imageVector = icon, contentDescription = null)
        }
    } else {
        Icon(imageVector = icon, contentDescription = null)
    }
}

private fun tabLabel(tab: MainTab, strings: com.example.ui.i18n.AppStrings): String = when (tab) {
    MainTab.THIS_DEVICE -> strings.navThisDevice
    MainTab.REMOTE_CONTROL -> strings.navRemoteControl
    MainTab.CHAT -> strings.navChat
}
