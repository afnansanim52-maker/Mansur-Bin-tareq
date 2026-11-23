package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PairingRequest
import com.example.data.PairingStatus
import com.example.data.ScreenShareSession
import com.example.data.SessionStatus
import com.example.data.UiState
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.LocalVitRemoteColors
import com.example.ui.theme.MonoFontFamily

@Composable
fun RemoteControlScreen(
    myDeviceCode: String,
    pairingsState: UiState<List<PairingRequest>>,
    sessionsState: UiState<List<ScreenShareSession>>,
    isSendingPairing: Boolean,
    strings: AppStrings,
    onSendPairingCode: (String) -> Unit,
    onOpenQrScanner: () -> Unit,
    onAcceptPairing: (String) -> Unit,
    onRejectPairing: (String) -> Unit,
    onRevokePairing: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onRequestViewScreen: (PairingRequest) -> Unit,
    onShareMyScreen: (PairingRequest) -> Unit,
    onOpenChatForPairing: (PairingRequest) -> Unit,
    onApproveIncomingSession: (ScreenShareSession) -> Unit,
    onDeclineIncomingSession: (ScreenShareSession) -> Unit,
    onJoinActiveViewerSession: (ScreenShareSession) -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    var codeInput by rememberSaveable { mutableStateOf("") }

    val pairings = (pairingsState as? UiState.Success)?.data.orEmpty()
    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()

    val incomingPairing = pairings.filter {
        it.parsedStatus() == PairingStatus.PENDING && it.isIncomingFor(myDeviceCode)
    }
    val outgoingPairing = pairings.filter {
        it.parsedStatus() == PairingStatus.PENDING && !it.isIncomingFor(myDeviceCode)
    }
    val acceptedPairings = pairings.filter { it.parsedStatus() == PairingStatus.ACCEPTED }
    val historyPairings = pairings.filter {
        it.parsedStatus() == PairingStatus.REJECTED || it.parsedStatus() == PairingStatus.REVOKED
    }

    val incomingSessions = sessions.filter {
        it.hostDeviceCode == myDeviceCode && it.parsedStatus() == SessionStatus.REQUESTED
    }
    val activeViewerSessions = sessions.filter {
        it.viewerDeviceCode == myDeviceCode && it.parsedStatus().isLiveOrPending()
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
            // Header
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.remoteTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = strings.connectCardDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.subtleText
                    )
                }
            }

            // Connect by Unique Device ID + QR Scan Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
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
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                tint = extColors.onlineGreen
                            )
                            Text(
                                text = strings.connectByIdTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it.uppercase().take(20) },
                            placeholder = {
                                Text(
                                    text = strings.deviceIdPlaceholder,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = extColors.onlineGreen,
                                unfocusedBorderColor = extColors.cardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("remote_device_code_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenQrScanner,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, extColors.cardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("open_qr_scanner_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(strings.scanQrButton, style = MaterialTheme.typography.labelLarge)
                            }

                            Button(
                                onClick = {
                                    onSendPairingCode(codeInput)
                                    codeInput = ""
                                },
                                enabled = codeInput.isNotBlank() && !isSendingPairing,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(50.dp)
                                    .testTag("send_pairing_request_button")
                            ) {
                                if (isSendingPairing) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(strings.sendRequestButton, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            // Active Viewer Sessions (Re-enter or join approved host share)
            if (activeViewerSessions.isNotEmpty()) {
                items(activeViewerSessions, key = { "viewer_sess_${it.id}" }) { session ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = extColors.onlineGreenContainer),
                        border = BorderStroke(1.dp, extColors.onlineGreen.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.hostDeviceName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${session.hostDeviceCode} • ${session.status}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = extColors.onlineGreen
                                )
                            }
                            Button(
                                onClick = { onJoinActiveViewerSession(session) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("join_viewer_session_${session.id}")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.viewScreen)
                            }
                        }
                    }
                }
            }

            // Incoming Screen Sharing Consent Requests
            if (incomingSessions.isNotEmpty()) {
                items(incomingSessions, key = { "in_sess_${it.id}" }) { req ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = extColors.warningAmberContainer),
                        border = BorderStroke(1.dp, extColors.warningAmber.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = strings.incomingSessionRequestTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strings.incomingSessionRequestBody.format(
                                    req.viewerDeviceName,
                                    req.viewerDeviceCode
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onDeclineIncomingSession(req) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text(strings.declineRequest)
                                }
                                Button(
                                    onClick = { onApproveIncomingSession(req) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = extColors.onlineGreen),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text(strings.approveAndShare, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Error / Loading states
            when (pairingsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            CircularProgressIndicator(color = extColors.onlineGreen)
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = extColors.dangerRedContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = pairingsState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    // Incoming Pairing Requests
                    if (incomingPairing.isNotEmpty()) {
                        item {
                            SectionTitle(title = strings.incomingRequestsTitle)
                        }
                        items(incomingPairing, key = { "in_pair_${it.id}" }) { req ->
                            IncomingPairingCard(
                                request = req,
                                strings = strings,
                                onAccept = { onAcceptPairing(req.id) },
                                onReject = { onRejectPairing(req.id) }
                            )
                        }
                    }

                    // Outgoing Pending Requests
                    if (outgoingPairing.isNotEmpty()) {
                        item {
                            SectionTitle(title = strings.outgoingRequestsTitle)
                        }
                        items(outgoingPairing, key = { "out_pair_${it.id}" }) { req ->
                            OutgoingPairingCard(
                                request = req,
                                myDeviceCode = myDeviceCode,
                                strings = strings,
                                onCancel = { onRevokePairing(req.id) }
                            )
                        }
                    }

                    // Paired Devices
                    item {
                        SectionTitle(title = strings.pairedDevicesTitle)
                    }

                    if (acceptedPairings.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.DevicesOther,
                                message = strings.emptyPairedDevices
                            )
                        }
                    } else {
                        items(acceptedPairings, key = { "paired_${it.id}" }) { pairing ->
                            PairedDeviceCard(
                                pairing = pairing,
                                myDeviceCode = myDeviceCode,
                                strings = strings,
                                onViewScreen = { onRequestViewScreen(pairing) },
                                onShareScreen = { onShareMyScreen(pairing) },
                                onOpenChat = { onOpenChatForPairing(pairing) },
                                onRemove = { onRevokePairing(pairing.id) }
                            )
                        }
                    }

                    // Connection History
                    item {
                        SectionTitle(title = strings.connectionHistoryTitle)
                    }

                    if (historyPairings.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.History,
                                message = strings.emptyHistory
                            )
                        }
                    } else {
                        items(historyPairings, key = { "hist_${it.id}" }) { item ->
                            HistoryItemCard(
                                pairing = item,
                                myDeviceCode = myDeviceCode,
                                strings = strings,
                                onDelete = { onDeleteHistory(item.id) }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun IncomingPairingCard(
    request: PairingRequest,
    strings: AppStrings,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extColors.accentCyan.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = request.requesterDeviceName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${request.requesterName} • ${request.requesterDeviceCode}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    color = extColors.subtleText
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("reject_pairing_${request.id}")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.reject)
                }
                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = extColors.onlineGreen),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("accept_pairing_${request.id}")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.accept, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OutgoingPairingCard(
    request: PairingRequest,
    myDeviceCode: String,
    strings: AppStrings,
    onCancel: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.peerDeviceName(myDeviceCode),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${request.peerDeviceCode(myDeviceCode)} • ${strings.waitingForApproval}",
                    style = MaterialTheme.typography.bodySmall,
                    color = extColors.warningAmber
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_outgoing_${request.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = strings.cancel,
                    tint = extColors.subtleText
                )
            }
        }
    }
}

@Composable
private fun PairedDeviceCard(
    pairing: PairingRequest,
    myDeviceCode: String,
    strings: AppStrings,
    onViewScreen: () -> Unit,
    onShareScreen: () -> Unit,
    onOpenChat: () -> Unit,
    onRemove: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extColors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            .clip(CircleShape)
                            .background(extColors.onlineGreenContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cast,
                            contentDescription = null,
                            tint = extColors.onlineGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pairing.peerDeviceName(myDeviceCode),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pairing.peerUserName(myDeviceCode)} • ${pairing.peerDeviceCode(myDeviceCode)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            color = extColors.subtleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onOpenChat,
                        modifier = Modifier.testTag("chat_paired_${pairing.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = strings.openChat,
                            tint = extColors.accentCyan
                        )
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("remove_paired_${pairing.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = strings.remove,
                            tint = extColors.dangerRed
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onViewScreen,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("view_screen_${pairing.id}")
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.viewScreen, style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onShareScreen,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, extColors.cardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_screen_${pairing.id}")
                ) {
                    Icon(Icons.Filled.ScreenShare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.shareMyScreen, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    pairing: PairingRequest,
    myDeviceCode: String,
    strings: AppStrings,
    onDelete: () -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    val statusText = when (pairing.parsedStatus()) {
        PairingStatus.REJECTED -> strings.statusRejected
        PairingStatus.REVOKED -> strings.statusRevoked
        PairingStatus.ACCEPTED -> strings.statusAccepted
        PairingStatus.PENDING -> strings.statusPending
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extColors.cardBorder.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pairing.peerDeviceName(myDeviceCode),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${pairing.peerDeviceCode(myDeviceCode)} • $statusText",
                    style = MaterialTheme.typography.bodySmall,
                    color = extColors.subtleText
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_history_${pairing.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = strings.remove,
                    tint = extColors.subtleText
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    val extColors = LocalVitRemoteColors.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extColors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = extColors.subtleText,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = extColors.subtleText
            )
        }
    }
}
