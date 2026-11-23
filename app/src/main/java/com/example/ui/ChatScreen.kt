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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.ChatMessage
import com.example.data.ChatMessageType
import com.example.data.PairingRequest
import com.example.data.PairingStatus
import com.example.data.UiState
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.LocalVitRemoteColors
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChatScreen(
    currentUserId: String,
    myDeviceCode: String,
    pairingsState: UiState<List<PairingRequest>>,
    selectedPairingId: String?,
    messagesState: UiState<List<ChatMessage>>,
    strings: AppStrings,
    onSelectPairing: (String) -> Unit,
    onSendMessage: (PairingRequest, String) -> Unit,
    onRequestScreenFromChat: (PairingRequest) -> Unit
) {
    val extColors = LocalVitRemoteColors.current
    val acceptedPairings = (pairingsState as? UiState.Success)?.data
        .orEmpty()
        .filter { it.parsedStatus() == PairingStatus.ACCEPTED }

    // Auto-select first paired device when none is chosen yet
    LaunchedEffect(acceptedPairings, selectedPairingId) {
        if (selectedPairingId.isNullOrBlank() && acceptedPairings.isNotEmpty()) {
            onSelectPairing(acceptedPairings.first().id)
        }
    }

    val activePairing = acceptedPairings.firstOrNull { it.id == selectedPairingId }
    var draftMessage by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strings.chatTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (activePairing != null) {
                    Surface(
                        onClick = { onRequestScreenFromChat(activePairing) },
                        shape = RoundedCornerShape(12.dp),
                        color = extColors.onlineGreenContainer,
                        border = BorderStroke(1.dp, extColors.onlineGreen.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("chat_request_screen_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ScreenShare,
                                contentDescription = strings.viewScreen,
                                tint = extColors.onlineGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.viewScreen,
                                style = MaterialTheme.typography.labelMedium,
                                color = extColors.onlineGreen
                            )
                        }
                    }
                }
            }

            if (acceptedPairings.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, extColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = extColors.onlineGreen,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = strings.chatEmptyPaired,
                                style = MaterialTheme.typography.bodyMedium,
                                color = extColors.subtleText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                return@Column
            }

            // Paired Device Selector Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(acceptedPairings, key = { it.id }) { pairing ->
                    val selected = pairing.id == activePairing?.id
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPairing(pairing.id) },
                        label = {
                            Text(
                                text = pairing.peerDeviceName(myDeviceCode),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = extColors.onlineGreenContainer,
                            selectedLabelColor = extColors.onlineGreen
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Messages Stream
            if (messagesState is UiState.Error) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extColors.dangerRedContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = messagesState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                        ) {
                            Text(
                                text = strings.chatEmptyMessages,
                                style = MaterialTheme.typography.bodyMedium,
                                color = extColors.subtleText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.parsedType() == ChatMessageType.SESSION_EVENT) {
                            SessionEventBubble(message = msg)
                        } else {
                            ChatMessageBubble(
                                message = msg,
                                isMine = msg.senderUid == currentUserId
                            )
                        }
                    }
                }
            }

            // Composer
            if (activePairing != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, extColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = draftMessage,
                            onValueChange = { draftMessage = it.take(1000) },
                            placeholder = { Text(strings.chatInputHint) },
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = extColors.onlineGreen,
                                unfocusedBorderColor = extColors.cardBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )
                        IconButton(
                            onClick = {
                                if (draftMessage.isNotBlank()) {
                                    onSendMessage(activePairing, draftMessage)
                                    draftMessage = ""
                                }
                            },
                            enabled = draftMessage.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (draftMessage.isNotBlank()) extColors.onlineGreen
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = strings.chatSend,
                                tint = if (draftMessage.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                else extColors.subtleText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionEventBubble(message: ChatMessage) {
    val extColors = LocalVitRemoteColors.current
    val timeText = formatMessageTime(message)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = extColors.accentCyanContainer,
            border = BorderStroke(1.dp, extColors.accentCyan.copy(alpha = 0.4f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = extColors.accentCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${message.text} • $timeText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean
) {
    val extColors = LocalVitRemoteColors.current
    val timeText = formatMessageTime(message)

    Column(
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            color = if (isMine) extColors.onlineGreenContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (isMine) extColors.onlineGreen.copy(alpha = 0.45f) else extColors.cardBorder
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isMine) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = extColors.accentCyan
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = extColors.subtleText,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun formatMessageTime(message: ChatMessage): String {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val date = message.createdAt?.toDate()
    return if (date != null) formatter.format(date) else "--:--"
}
