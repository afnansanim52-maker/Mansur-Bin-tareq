package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.LocalUserIdentity
import com.example.data.ScreenShareSession
import com.example.data.WebRtcTelemetry
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.LocalVitRemoteColors
import com.example.ui.theme.MonoFontFamily
import com.example.util.DevicePermissionSnapshot
import com.example.util.PermissionStatusHelper
import com.example.util.QrCodeUtils
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    user: FirebaseUser,
    localUserIdentity: LocalUserIdentity?,
    deviceCode: String,
    deviceName: String,
    isOnline: Boolean,
    isHostStreaming: Boolean,
    hostStartedAtMs: Long?,
    telemetry: WebRtcTelemetry,
    incomingSessionRequests: List<ScreenShareSession>,
    permissionSnapshot: DevicePermissionSnapshot,
    language: AppLanguage,
    darkTheme: Boolean,
    strings: AppStrings,
    onToggleLanguage: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onLogoutClick: () -> Unit,
    onConnectDeviceClick: () -> Unit,
    onCopySuccess: (String) -> Unit,
    onStopSharingClick: () -> Unit,
    onApproveIncomingSession: (ScreenShareSession) -> Unit,
    onDeclineIncomingSession: (ScreenShareSession) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBananaAiClick: () -> Unit
) {
    val context = LocalContext.current
    val extColors = LocalVitRemoteColors.current

    var showQrDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAccessibilityInfo by remember { mutableStateOf(false) }
    var showScreenCaptureInfo by remember { mutableStateOf(false) }

    val qrBitmap = remember(deviceCode) {
        if (deviceCode.isNotBlank()) {
            QrCodeUtils.generateQrBitmap(
                content = QrCodeUtils.buildPairingPayload(deviceCode),
                sizePx = 560
            )
        } else null
    }

    var sharingElapsedSec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isHostStreaming, hostStartedAtMs) {
        if (isHostStreaming && hostStartedAtMs != null) {
            while (true) {
                sharingElapsedSec = ((System.currentTimeMillis() - hostStartedAtMs) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        } else {
            sharingElapsedSec = 0L
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
            // 1. Header: VitRemote brand, User account info, Online indicator, Language/Theme, Logout
            item {
                DashboardHeaderCard(
                    user = user,
                    localUserIdentity = localUserIdentity,
                    isOnline = isOnline,
                    isHostStreaming = isHostStreaming,
                    language = language,
                    darkTheme = darkTheme,
                    strings = strings,
                    onToggleLanguage = onToggleLanguage,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onLogoutClick = { showLogoutDialog = true }
                )
            }

            // 2. Active Screen Sharing Live Indicator Banner (Immediate Stop Sharing!)
            if (isHostStreaming) {
                item {
                    ActiveSharingBanner(
                        elapsedSec = sharingElapsedSec,
                        telemetry = telemetry,
                        strings = strings,
                        onStopSharing = onStopSharingClick
                    )
                }
            }

            // 3. Incoming Live Screen Sharing Consent Requests
            if (incomingSessionRequests.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        incomingSessionRequests.forEach { request ->
                            IncomingSessionConsentCard(
                                session = request,
                                strings = strings,
                                onApprove = { onApproveIncomingSession(request) },
                                onDecline = { onDeclineIncomingSession(request) }
                            )
                        }
                    }
                }
            }

            // 4. Device Connection Hero Card ("অন্যের মোবাইল কন্ট্রোল করুন")
            item {
                DeviceConnectionHeroCard(
                    strings = strings,
                    darkTheme = darkTheme,
                    onConnectClick = onConnectDeviceClick
                )
            }

            // 5. My Device Card (Unique ID, Status, Copy, Share, Scannable QR Code)
            item {
                MyDeviceCard(
                    deviceCode = deviceCode,
                    deviceName = deviceName,
                    isOnline = isOnline,
                    isSharing = isHostStreaming,
                    qrBitmap = qrBitmap,
                    strings = strings,
                    onCopyCode = {
                        if (deviceCode.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("VitRemote Device ID", deviceCode))
                            onCopySuccess(strings.codeCopied)
                        }
                    },
                    onShareCode = {
                        if (deviceCode.isNotBlank()) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, strings.shareCodeTextTemplate.format(deviceCode))
                            }
                            context.startActivity(Intent.createChooser(shareIntent, strings.shareCode))
                        }
                    },
                    onEnlargeQr = { showQrDialog = true }
                )
            }

            // 5b. Banana AI Studio Feature Card
            item {
                BananaAiFeatureCard(
                    strings = strings,
                    darkTheme = darkTheme,
                    onOpenStudio = onOpenBananaAiClick
                )
            }

            // 6. Permissions & Setup Card (4 Real System Checks)
            item {
                PermissionsSetupCard(
                    isHostStreaming = isHostStreaming,
                    snapshot = permissionSnapshot,
                    strings = strings,
                    onScreenCaptureInfo = { showScreenCaptureInfo = true },
                    onRequestNotifications = onRequestNotificationPermission,
                    onOpenBatterySettings = { PermissionStatusHelper.openBatteryOptimizationSettings(context) },
                    onAccessibilityInfo = { showAccessibilityInfo = true }
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    // Enlarged QR Dialog
    if (showQrDialog && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = strings.qrPairingTitle,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = strings.qrPairingTitle,
                            modifier = Modifier
                                .size(240.dp)
                                .padding(14.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = deviceCode,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = MonoFontFamily,
                            letterSpacing = 2.sp
                        ),
                        color = extColors.onlineGreen
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = strings.qrPairingHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.subtleText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(strings.logoutConfirmTitle) },
            text = { Text(strings.logoutConfirmBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extColors.dangerRed)
                ) {
                    Text(strings.logout, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showAccessibilityInfo) {
        AlertDialog(
            onDismissRequest = { showAccessibilityInfo = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(strings.accessibilityDialogTitle) },
            text = { Text(strings.accessibilityDialogBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showAccessibilityInfo = false }) {
                    Text(strings.close)
                }
            }
        )
    }

    if (showScreenCaptureInfo) {
        AlertDialog(
            onDismissRequest = { showScreenCaptureInfo = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(strings.screenCaptureDialogTitle) },
            text = { Text(strings.screenCaptureDialogBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showScreenCaptureInfo = false }) {
                    Text(strings.close)
                }
            }
        )
    }
}

@Composable
private fun DashboardHeaderCard(
    user: FirebaseUser,
    localUserIdentity: LocalUserIdentity?,
    isOnline: Boolean,
    isHostStreaming: Boolean,
    language: AppLanguage,
    darkTheme: Boolean,
    strings: AppStrings,
    onToggleLanguage: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    val statusColor = when {
        isHostStreaming -> extColors.dangerRed
        isOnline -> extColors.onlineGreen
        else -> extColors.subtleText
    }
    val statusLabel = when {
        isHostStreaming -> strings.sharingLive
        isOnline -> strings.online
        else -> strings.offline
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extColors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.vitremote_icon_fg_1795422054494),
                        contentDescription = strings.appName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = onToggleLanguage,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, extColors.cardBorder),
                        modifier = Modifier.testTag("dashboard_language_toggle")
                    ) {
                        Text(
                            text = if (language == AppLanguage.BN) "বাংলা" else "EN",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleDarkTheme,
                        modifier = Modifier.testTag("dashboard_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.testTag("dashboard_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = strings.logout,
                            tint = extColors.dangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = extColors.cardBorder.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val photoUrl = localUserIdentity?.photoUrl ?: user.photoUrl?.toString()
                    val resolvedDisplayName = localUserIdentity?.displayName?.takeIf { it.isNotBlank() }
                        ?: user.displayName?.takeIf { it.isNotBlank() }
                        ?: "VitRemote User"
                    val resolvedEmail = localUserIdentity?.email?.takeIf { it.isNotBlank() }
                        ?: user.email.orEmpty()
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = resolvedDisplayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(extColors.onlineGreenContainer)
                        ) {
                            Text(
                                text = (resolvedDisplayName.firstOrNull() ?: 'V').uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = extColors.onlineGreen
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolvedDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = resolvedEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.subtleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSharingBanner(
    elapsedSec: Long,
    telemetry: WebRtcTelemetry,
    strings: AppStrings,
    onStopSharing: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    val timerLabel = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = extColors.dangerRedContainer),
        border = BorderStroke(1.5.dp, extColors.dangerRed.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ScreenShare,
                        contentDescription = null,
                        tint = extColors.dangerRed
                    )
                    Column {
                        Text(
                            text = strings.activeSharingBannerTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = strings.activeSharingBannerSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.35f)
                ) {
                    Text(
                        text = timerLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (telemetry.width > 0 && telemetry.height > 0) {
                Text(
                    text = "${telemetry.width}×${telemetry.height} • ${telemetry.fps} FPS • ${telemetry.latencyMs} ms",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }

            Button(
                onClick = onStopSharing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = extColors.dangerRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("dashboard_stop_sharing_button")
            ) {
                Icon(Icons.Filled.StopScreenShare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.stopSharingNow, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun IncomingSessionConsentCard(
    session: ScreenShareSession,
    strings: AppStrings,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = extColors.warningAmberContainer),
        border = BorderStroke(1.2.dp, extColors.warningAmber.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = extColors.warningAmber
                )
                Text(
                    text = strings.incomingSessionRequestTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = strings.incomingSessionRequestBody.format(
                    session.viewerDeviceName,
                    session.viewerDeviceCode
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("decline_session_${session.id}")
                ) {
                    Text(strings.declineRequest)
                }
                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = extColors.onlineGreen),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("approve_session_${session.id}")
                ) {
                    Text(strings.approveAndShare, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DeviceConnectionHeroCard(
    strings: AppStrings,
    darkTheme: Boolean,
    onConnectClick: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extColors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            extColors.onlineGreen.copy(alpha = if (darkTheme) 0.16f else 0.10f),
                            extColors.accentCyan.copy(alpha = if (darkTheme) 0.10f else 0.06f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(extColors.onlineGreenContainer)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Devices,
                        contentDescription = null,
                        tint = extColors.onlineGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = strings.connectCardTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = strings.connectCardDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = extColors.subtleText
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onConnectClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("connect_device_cta_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ScreenShare,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.connectDeviceButton,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun MyDeviceCard(
    deviceCode: String,
    deviceName: String,
    isOnline: Boolean,
    isSharing: Boolean,
    qrBitmap: android.graphics.Bitmap?,
    strings: AppStrings,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onEnlargeQr: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extColors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = extColors.accentCyan
                        )
                    }
                    Column {
                        Text(
                            text = strings.myDeviceTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.subtleText
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isOnline) extColors.onlineGreenContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = when {
                            isSharing -> strings.sharingLive
                            isOnline -> strings.online
                            else -> strings.offline
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) extColors.onlineGreen else extColors.subtleText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Recessed Unique Device ID Box + Compact QR Preview
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, extColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = strings.uniqueDeviceIdLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = extColors.subtleText
                        )
                        Text(
                            text = deviceCode.ifBlank { "VR-•••-•••" },
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag("my_device_code_text")
                        )
                        Text(
                            text = strings.qrPairingHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.subtleText
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, extColors.cardBorder),
                        modifier = Modifier
                            .size(84.dp)
                            .clickable(onClick = onEnlargeQr)
                            .testTag("enlarge_qr_code_button")
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = strings.enlargeQr,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.QrCode2,
                                    contentDescription = strings.enlargeQr,
                                    tint = Color.Black,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Copy & Share Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyCode,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, extColors.cardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("copy_device_code_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.copyCode, style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onShareCode,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, extColors.cardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_device_code_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.shareCode, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun BananaAiFeatureCard(
    strings: AppStrings,
    darkTheme: Boolean,
    onOpenStudio: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extColors.warningAmber.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            extColors.warningAmber.copy(alpha = if (darkTheme) 0.16f else 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(extColors.warningAmberContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = extColors.warningAmber,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = strings.bananaTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = extColors.warningAmberContainer
                    ) {
                        Text(
                            text = strings.bananaBadgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = extColors.warningAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = strings.bananaSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = extColors.subtleText
                )

                Button(
                    onClick = onOpenStudio,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extColors.warningAmber,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_banana_ai_studio_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.bananaOpenStudio,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsSetupCard(
    isHostStreaming: Boolean,
    snapshot: DevicePermissionSnapshot,
    strings: AppStrings,
    onScreenCaptureInfo: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onAccessibilityInfo: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extColors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = strings.permissionsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = strings.permissionsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = extColors.subtleText
                )
            }

            PermissionStatusRow(
                icon = Icons.Filled.ScreenShare,
                title = strings.permScreenCaptureTitle,
                description = strings.permScreenCaptureDesc,
                badgeText = if (isHostStreaming) strings.permScreenCaptureActive else strings.permScreenCaptureReady,
                badgeColor = if (isHostStreaming) extColors.onlineGreen else extColors.accentCyan,
                actionLabel = strings.actionDetails,
                testTag = "perm_screen_capture_action",
                onAction = onScreenCaptureInfo
            )

            HorizontalDivider(color = extColors.cardBorder.copy(alpha = 0.5f))

            PermissionStatusRow(
                icon = Icons.Filled.NotificationsActive,
                title = strings.permNotificationTitle,
                description = strings.permNotificationDesc,
                badgeText = if (snapshot.notificationGranted) strings.statusGranted else strings.statusRequired,
                badgeColor = if (snapshot.notificationGranted) extColors.onlineGreen else extColors.warningAmber,
                actionLabel = if (snapshot.notificationGranted) null else strings.actionAllow,
                testTag = "perm_notification_action",
                onAction = onRequestNotifications
            )

            HorizontalDivider(color = extColors.cardBorder.copy(alpha = 0.5f))

            PermissionStatusRow(
                icon = Icons.Filled.BatteryChargingFull,
                title = strings.permBatteryTitle,
                description = strings.permBatteryDesc,
                badgeText = if (snapshot.batteryUnrestricted) strings.statusUnrestricted else strings.statusOptimized,
                badgeColor = if (snapshot.batteryUnrestricted) extColors.onlineGreen else extColors.warningAmber,
                actionLabel = strings.actionOpenSettings,
                testTag = "perm_battery_action",
                onAction = onOpenBatterySettings
            )

            HorizontalDivider(color = extColors.cardBorder.copy(alpha = 0.5f))

            PermissionStatusRow(
                icon = Icons.Filled.AccessibilityNew,
                title = strings.permAccessibilityTitle,
                description = strings.permAccessibilityDesc,
                badgeText = strings.statusNotRequired,
                badgeColor = extColors.onlineGreen,
                actionLabel = strings.actionDetails,
                testTag = "perm_accessibility_action",
                onAction = onAccessibilityInfo
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    title: String,
    description: String,
    badgeText: String,
    badgeColor: Color,
    actionLabel: String?,
    testTag: String,
    onAction: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.14f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = extColors.subtleText
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = badgeColor.copy(alpha = 0.14f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor
                    )
                }
            }

            AnimatedVisibility(visible = actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.testTag(testTag)
                ) {
                    Text(
                        text = actionLabel.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = extColors.accentCyan
                    )
                }
            }
        }
    }
}
