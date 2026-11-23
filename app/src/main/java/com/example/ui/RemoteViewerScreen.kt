package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.NetworkGrade
import com.example.data.QualityProfile
import com.example.data.ScreenShareSession
import com.example.data.SessionStatus
import com.example.data.WebRtcTelemetry
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.LocalVitRemoteColors
import com.example.ui.theme.MonoFontFamily
import com.example.webrtc.WebRtcScreenShareController
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun RemoteViewerScreen(
    session: ScreenShareSession,
    remoteVideoTrack: VideoTrack?,
    telemetry: WebRtcTelemetry,
    strings: AppStrings,
    onStartWebRtc: () -> Unit,
    onChangeQuality: (QualityProfile) -> Unit,
    onDisconnect: () -> Unit
) {
    BackHandler { onDisconnect() }

    val extColors = LocalVitRemoteColors.current
    val status = session.parsedStatus()
    var fullScreenMode by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    // Trigger WebRTC viewer negotiation once Host approves or activates the session
    LaunchedEffect(session.id, status) {
        if (status == SessionStatus.APPROVED || status == SessionStatus.ACTIVE) {
            onStartWebRtc()
        }
    }

    // Live session duration clock
    LaunchedEffect(session.id, status) {
        if (status == SessionStatus.ACTIVE || status == SessionStatus.APPROVED) {
            val startMs = session.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    val formattedDuration = remember(elapsedSeconds) {
        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        "%02d:%02d".format(mins, secs)
    }

    val resolutionText = remember(telemetry.width, telemetry.height, session.screenWidth, session.screenHeight) {
        val w = if (telemetry.width > 0) telemetry.width else session.screenWidth.toInt()
        val h = if (telemetry.height > 0) telemetry.height else session.screenHeight.toInt()
        if (w > 0 && h > 0) "${w}×${h}" else "--"
    }

    val networkLabel = when (telemetry.networkGrade) {
        NetworkGrade.EXCELLENT -> strings.netExcellent
        NetworkGrade.GOOD -> strings.netGood
        NetworkGrade.FAIR -> strings.netFair
        NetworkGrade.POOR -> strings.netPoor
        NetworkGrade.CONNECTING -> strings.netConnecting
    }

    val networkColor = when (telemetry.networkGrade) {
        NetworkGrade.EXCELLENT, NetworkGrade.GOOD -> extColors.onlineGreen
        NetworkGrade.FAIR -> extColors.warningAmber
        NetworkGrade.POOR -> extColors.dangerRed
        NetworkGrade.CONNECTING -> extColors.accentCyan
    }

    Surface(
        color = Color(0xFF050608),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!fullScreenMode) Modifier.statusBarsPadding().navigationBarsPadding()
                    else Modifier
                )
        ) {
            // Main Live Screen Video Stage
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        if (remoteVideoTrack != null) {
                            fullScreenMode = !fullScreenMode
                        }
                    }
            ) {
                when {
                    status == SessionStatus.REJECTED -> {
                        ViewerStatusPlaceholder(
                            title = strings.viewerSessionRejected,
                            subtitle = session.hostDeviceName,
                            accentColor = extColors.dangerRed,
                            showSpinner = false,
                            actionLabel = strings.close,
                            onAction = onDisconnect
                        )
                    }

                    status == SessionStatus.ENDED || status == SessionStatus.EXPIRED -> {
                        ViewerStatusPlaceholder(
                            title = strings.viewerSessionEnded,
                            subtitle = session.hostDeviceName,
                            accentColor = extColors.subtleText,
                            showSpinner = false,
                            actionLabel = strings.close,
                            onAction = onDisconnect
                        )
                    }

                    status == SessionStatus.REQUESTED -> {
                        WaitingForApprovalPulse(
                            hostDeviceName = session.hostDeviceName,
                            strings = strings
                        )
                    }

                    remoteVideoTrack != null -> {
                        WebRtcVideoRenderer(
                            videoTrack = remoteVideoTrack,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        ViewerStatusPlaceholder(
                            title = strings.viewerConnectingWebRtc,
                            subtitle = "${session.hostDeviceName} (${session.hostDeviceCode})",
                            accentColor = extColors.accentCyan,
                            showSpinner = true,
                            actionLabel = null,
                            onAction = {}
                        )
                    }
                }
            }

            // Top HUD Overlay
            AnimatedVisibility(
                visible = !fullScreenMode,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC0A0C10))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isLive = remoteVideoTrack != null && status == SessionStatus.ACTIVE
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (isLive) extColors.onlineGreen else extColors.warningAmber)
                            )
                            Text(
                                text = session.hostDeviceName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isLive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = extColors.onlineGreenContainer
                                ) {
                                    Text(
                                        text = strings.viewerLiveBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extColors.onlineGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${session.hostDeviceCode} • ${telemetry.connectionState}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { fullScreenMode = !fullScreenMode },
                            modifier = Modifier.testTag("viewer_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = if (fullScreenMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = strings.viewerFullScreen,
                                tint = Color.White
                            )
                        }

                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extColors.dangerRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("viewer_disconnect_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = strings.viewerDisconnect,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Floating exit-fullscreen button when immersive
            if (fullScreenMode) {
                Surface(
                    onClick = { fullScreenMode = false },
                    shape = CircleShape,
                    color = Color(0xAA0D1117),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .testTag("viewer_exit_fullscreen_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FullscreenExit,
                        contentDescription = strings.viewerExitFullScreen,
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Bottom Telemetry & Adaptive Quality HUD
            AnimatedVisibility(
                visible = !fullScreenMode && status.isLiveOrPending(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xD90B0E14))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryChip(
                            icon = Icons.Filled.NetworkCheck,
                            label = strings.viewerNetworkQuality,
                            value = networkLabel,
                            accent = networkColor
                        )
                        TelemetryChip(
                            icon = Icons.Filled.Speed,
                            label = strings.viewerLatency,
                            value = if (telemetry.latencyMs > 0) "${telemetry.latencyMs} ms" else "--",
                            accent = extColors.accentCyan
                        )
                        TelemetryChip(
                            icon = Icons.Filled.ScreenShare,
                            label = strings.viewerResolution,
                            value = resolutionText,
                            accent = Color.White
                        )
                        TelemetryChip(
                            icon = Icons.Filled.Timer,
                            label = strings.viewerDuration,
                            value = formattedDuration,
                            accent = extColors.onlineGreen
                        )
                    }

                    // Adaptive Quality Profile Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeProfile = session.parsedQuality()
                        QualityProfile.entries.forEach { profile ->
                            val chipLabel = when (profile) {
                                QualityProfile.AUTO -> strings.qualityAuto
                                QualityProfile.HIGH -> "1080p"
                                QualityProfile.BALANCED -> "720p"
                                QualityProfile.LOW -> "480p"
                            }
                            FilterChip(
                                selected = activeProfile == profile,
                                onClick = { onChangeQuality(profile) },
                                label = {
                                    Text(
                                        text = chipLabel,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = extColors.onlineGreenContainer,
                                    selectedLabelColor = extColors.onlineGreen,
                                    labelColor = Color.White.copy(alpha = 0.75f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color.White
        )
    }
}

@Composable
private fun WaitingForApprovalPulse(
    hostDeviceName: String,
    strings: AppStrings
) {
    val extColors = LocalVitRemoteColors.current
    val transition = rememberInfiniteTransition(label = "approvalPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(extColors.warningAmber.copy(alpha = 0.14f))
                .alpha(alpha)
        ) {
            Icon(
                imageVector = Icons.Filled.HourglassTop,
                contentDescription = null,
                tint = extColors.warningAmber,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = strings.viewerWaitingHostTitle,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = hostDeviceName,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
            color = extColors.onlineGreen
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = strings.viewerWaitingHostBody,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ViewerStatusPlaceholder(
    title: String,
    subtitle: String,
    accentColor: Color,
    showSpinner: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                color = accentColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(20.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun WebRtcVideoRenderer(
    videoTrack: VideoTrack,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(context) {
        SurfaceViewRenderer(context).apply {
            init(WebRtcScreenShareController.getEglBase().eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setEnableHardwareScaler(true)
            setMirror(false)
        }
    }

    DisposableEffect(videoTrack, renderer) {
        videoTrack.addSink(renderer)
        onDispose {
            try {
                videoTrack.removeSink(renderer)
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            try {
                renderer.release()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}
