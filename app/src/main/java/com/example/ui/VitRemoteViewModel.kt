package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessage
import com.example.data.ChatMessageType
import com.example.data.LocalPreferencesRepository
import com.example.data.LocalUserIdentity
import com.example.data.PairingRequest
import com.example.data.PairingStatus
import com.example.data.QualityProfile
import com.example.data.ScreenShareSession
import com.example.data.SessionStatus
import com.example.data.UiState
import com.example.data.VitRemoteRepository
import com.example.data.WebRtcTelemetry
import com.example.service.ScreenShareForegroundService
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.stringsFor
import com.example.util.QrCodeUtils
import com.example.util.SessionNotificationHelper
import com.example.webrtc.WebRtcScreenShareController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack

@OptIn(ExperimentalCoroutinesApi::class)
class VitRemoteViewModel(
    private val repository: VitRemoteRepository,
    private val localPrefs: LocalPreferencesRepository,
    val currentUserId: String
) : ViewModel() {

    val deviceModelName: String = buildString {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        if (model.startsWith(manufacturer, ignoreCase = true)) {
            append(model)
        } else {
            append(manufacturer.replaceFirstChar { it.uppercase() })
            if (manufacturer.isNotEmpty() && model.isNotEmpty()) append(" ")
            append(model)
        }
    }.ifBlank { "Android Device" }

    val platformVersionLabel: String = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT}"

    private val _deviceCode = MutableStateFlow("")
    val deviceCode: StateFlow<String> = _deviceCode.asStateFlow()

    private val _isDeviceOnline = MutableStateFlow(false)
    val isDeviceOnline: StateFlow<Boolean> = _isDeviceOnline.asStateFlow()

    private val _isSendingPairing = MutableStateFlow(false)
    val isSendingPairing: StateFlow<Boolean> = _isSendingPairing.asStateFlow()

    private val _selectedChatPairingId = MutableStateFlow<String?>(null)
    val selectedChatPairingId: StateFlow<String?> = _selectedChatPairingId.asStateFlow()

    private val _activeViewerSessionId = MutableStateFlow<String?>(null)
    val activeViewerSessionId: StateFlow<String?> = _activeViewerSessionId.asStateFlow()

    private val _pendingHostConsentSession = MutableStateFlow<ScreenShareSession?>(null)
    val pendingHostConsentSession: StateFlow<ScreenShareSession?> = _pendingHostConsentSession.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val language: StateFlow<AppLanguage> = localPrefs.languageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AppLanguage.BN
    )

    val darkTheme: StateFlow<Boolean> = localPrefs.darkThemeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = true
    )

    val localUserIdentity: StateFlow<LocalUserIdentity?> = localPrefs.userIdentityFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null
    )

    val isHostStreaming: StateFlow<Boolean> = WebRtcScreenShareController.isHostStreaming
    val activeHostSessionId: StateFlow<String?> = WebRtcScreenShareController.activeHostSessionId
    val hostStartedAtMs: StateFlow<Long?> = WebRtcScreenShareController.hostStartedAtMs
    val telemetry: StateFlow<WebRtcTelemetry> = WebRtcScreenShareController.telemetry
    val remoteVideoTrack: StateFlow<VideoTrack?> = WebRtcScreenShareController.remoteVideoTrack

    val pairingsState: StateFlow<UiState<List<PairingRequest>>> =
        repository.observePairingRequests(currentUserId)
            .map<List<PairingRequest>, UiState<List<PairingRequest>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreErrorOrThrowCancellation(error)
                Log.w(TAG, "Error observing pairing requests", firestoreError)
                emit(UiState.Error(firestoreError.message ?: "Failed to load pairings"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val sessionsState: StateFlow<UiState<List<ScreenShareSession>>> =
        repository.observeSessions(currentUserId)
            .map<List<ScreenShareSession>, UiState<List<ScreenShareSession>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreErrorOrThrowCancellation(error)
                Log.w(TAG, "Error observing sessions", firestoreError)
                emit(UiState.Error(firestoreError.message ?: "Failed to load sessions"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val chatMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        _selectedChatPairingId.flatMapLatest { pairingId ->
            if (pairingId.isNullOrBlank()) {
                flowOf<UiState<List<ChatMessage>>>(UiState.Success(emptyList()))
            } else {
                repository.observeChatMessages(pairingId, currentUserId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreErrorOrThrowCancellation(error)
                        Log.w(TAG, "Error observing chat messages", firestoreError)
                        emit(UiState.Error(firestoreError.message ?: "Failed to load chat"))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Success(emptyList())
        )

    private val currentUserDisplayName: String
        get() = Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "VitRemote User"

    init {
        bootstrapDeviceIdentity()
        startSessionExpirationMonitor()
    }

    private fun bootstrapDeviceIdentity() {
        viewModelScope.launch {
            val code = localPrefs.getOrCreateDeviceCode()
            _deviceCode.value = code
            val user = Firebase.auth.currentUser
            if (user != null && user.uid == currentUserId) {
                localPrefs.saveUserIdentity(user)
                repository.ensureUserProfile(user, language.value.code)
                val regResult = repository.registerOrUpdateMyDevice(
                    deviceCode = code,
                    deviceName = deviceModelName,
                    ownerName = currentUserDisplayName,
                    platformVersion = platformVersionLabel,
                    online = true,
                    sharingActive = WebRtcScreenShareController.isHostStreaming.value
                )
                _isDeviceOnline.value = regResult.isSuccess
            }
        }
    }

    private fun startSessionExpirationMonitor() {
        viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                val currentSessions = (sessionsState.value as? UiState.Success)?.data.orEmpty()
                val nowMs = System.currentTimeMillis()
                currentSessions.forEach { session ->
                    val expMs = session.expiresAt?.toDate()?.time ?: return@forEach
                    if (session.parsedStatus().isLiveOrPending() && nowMs >= expMs) {
                        repository.endSession(session.id, expired = true)
                        if (activeHostSessionId.value == session.id || _activeViewerSessionId.value == session.id) {
                            WebRtcScreenShareController.stopAll()
                        }
                    }
                }
            }
        }
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (language.value == AppLanguage.BN) AppLanguage.EN else AppLanguage.BN
            localPrefs.setLanguage(next)
            repository.updatePreferredLanguage(next.code)
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            localPrefs.setDarkTheme(!darkTheme.value)
        }
    }

    fun emitFeedback(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    fun sendPairingRequestByCode(rawInput: String) {
        val strings = stringsFor(language.value)
        val normalized = QrCodeUtils.parseDeviceCodeFromQr(rawInput)
            ?: rawInput.trim().uppercase()
        if (!QrCodeUtils.isValidDeviceCodeFormat(normalized)) {
            emitFeedback(strings.errInvalidCode)
            return
        }
        val myCode = _deviceCode.value
        if (normalized == myCode) {
            emitFeedback(strings.errSelfPair)
            return
        }

        val existingPairings = (pairingsState.value as? UiState.Success)?.data.orEmpty()
        val duplicate = existingPairings.any { req ->
            val st = req.parsedStatus()
            (st == PairingStatus.PENDING || st == PairingStatus.ACCEPTED) &&
                req.peerDeviceCode(myCode).equals(normalized, ignoreCase = true)
        }
        if (duplicate) {
            emitFeedback(strings.errPairingExists)
            return
        }

        viewModelScope.launch {
            _isSendingPairing.value = true
            try {
                val lookup = repository.lookupDeviceByCode(normalized)
                val target = lookup.getOrNull()
                if (target == null) {
                    emitFeedback(strings.errDeviceNotFound)
                    return@launch
                }
                val result = repository.sendPairingRequest(
                    requesterDeviceCode = myCode,
                    requesterDeviceName = deviceModelName,
                    requesterName = currentUserDisplayName,
                    targetDevice = target
                )
                if (result.isSuccess) {
                    emitFeedback(strings.errPairingSent)
                } else {
                    emitFeedback(result.exceptionOrNull()?.localizedMessage ?: "Failed to send request")
                }
            } finally {
                _isSendingPairing.value = false
            }
        }
    }

    fun respondToPairingRequest(requestId: String, accept: Boolean) {
        viewModelScope.launch {
            val result = repository.respondToPairingRequest(requestId, accept)
            if (result.isFailure) {
                emitFeedback(result.exceptionOrNull()?.localizedMessage ?: "Action failed")
            }
        }
    }

    fun revokePairing(requestId: String) {
        viewModelScope.launch {
            val result = repository.revokePairing(requestId)
            if (result.isFailure) {
                emitFeedback(result.exceptionOrNull()?.localizedMessage ?: "Failed to remove device")
            }
            if (_selectedChatPairingId.value == requestId) {
                _selectedChatPairingId.value = null
            }
        }
    }

    fun deleteHistoryRecord(requestId: String) {
        viewModelScope.launch {
            repository.deletePairingRecord(requestId)
        }
    }

    fun requestViewRemoteScreen(pairing: PairingRequest) {
        val strings = stringsFor(language.value)
        val myCode = _deviceCode.value
        if (myCode.isBlank()) return
        viewModelScope.launch {
            val result = repository.createSessionRequestAsViewer(
                pairing = pairing,
                myDeviceCode = myCode,
                myDeviceName = deviceModelName
            )
            result.onSuccess { sessionId ->
                _activeViewerSessionId.value = sessionId
                emitFeedback(strings.errSessionRequested)
            }.onFailure { err ->
                emitFeedback(err.localizedMessage ?: "Unable to request screen")
            }
        }
    }

    fun startSharingMyScreenWithPair(
        pairing: PairingRequest,
        requestMediaProjectionConsent: () -> Unit
    ) {
        val myCode = _deviceCode.value
        if (myCode.isBlank()) return
        viewModelScope.launch {
            val result = repository.createSessionAsHost(
                pairing = pairing,
                myDeviceCode = myCode,
                myDeviceName = deviceModelName
            )
            result.onSuccess { sessionId ->
                val session = ScreenShareSession(
                    id = sessionId,
                    pairingId = pairing.id,
                    hostUid = currentUserId,
                    hostDeviceCode = myCode,
                    hostDeviceName = deviceModelName,
                    viewerUid = pairing.peerUid(myCode),
                    viewerDeviceCode = pairing.peerDeviceCode(myCode),
                    viewerDeviceName = pairing.peerDeviceName(myCode),
                    participantUids = listOf(currentUserId, pairing.peerUid(myCode)),
                    status = SessionStatus.APPROVED.name
                )
                _pendingHostConsentSession.value = session
                requestMediaProjectionConsent()
            }.onFailure { err ->
                emitFeedback(err.localizedMessage ?: "Unable to initiate screen sharing")
            }
        }
    }

    fun approveIncomingSessionRequest(
        session: ScreenShareSession,
        requestMediaProjectionConsent: () -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.respondToSessionRequest(session.id, approve = true)
            if (res.isSuccess) {
                _pendingHostConsentSession.value = session.copy(status = SessionStatus.APPROVED.name)
                requestMediaProjectionConsent()
            } else {
                emitFeedback(res.exceptionOrNull()?.localizedMessage ?: "Approval failed")
            }
        }
    }

    fun declineIncomingSessionRequest(session: ScreenShareSession) {
        viewModelScope.launch {
            repository.respondToSessionRequest(session.id, approve = false)
        }
    }

    fun onMediaProjectionConsentResult(context: Context, resultCode: Int, data: Intent?) {
        val session = _pendingHostConsentSession.value
        _pendingHostConsentSession.value = null
        val strings = stringsFor(language.value)
        if (session == null) return

        if (resultCode != Activity.RESULT_OK || data == null) {
            emitFeedback(strings.errMediaProjectionDenied)
            viewModelScope.launch {
                repository.endSession(session.id, expired = false)
            }
            return
        }

        ScreenShareForegroundService.start(
            context = context,
            session = session,
            userId = currentUserId,
            deviceCode = _deviceCode.value,
            senderName = currentUserDisplayName,
            resultCode = resultCode,
            projectionResultData = data
        )
    }

    fun stopHostScreenSharing(context: Context) {
        ScreenShareForegroundService.stop(context)
    }

    fun openViewerForSession(sessionId: String) {
        _activeViewerSessionId.value = sessionId
    }

    fun ensureViewerWebRtcStarted(context: Context, session: ScreenShareSession) {
        if (session.parsedStatus() == SessionStatus.APPROVED || session.parsedStatus() == SessionStatus.ACTIVE) {
            WebRtcScreenShareController.startViewerSession(
                context = context,
                repository = repository,
                session = session,
                currentUserId = currentUserId
            )
        }
    }

    fun changeViewerQuality(sessionId: String, profile: QualityProfile) {
        viewModelScope.launch {
            repository.updateSessionQuality(sessionId, profile)
        }
    }

    fun disconnectViewer(context: Context, notifyEndSession: Boolean = true) {
        val sessionId = _activeViewerSessionId.value
        _activeViewerSessionId.value = null
        WebRtcScreenShareController.stopAll()
        if (notifyEndSession && !sessionId.isNullOrBlank()) {
            viewModelScope.launch {
                repository.endSession(sessionId, expired = false)
                SessionNotificationHelper.showSessionAlert(
                    context = context,
                    notificationId = 4103,
                    title = "VitRemote",
                    body = stringsFor(language.value).viewerSessionEnded
                )
            }
        }
    }

    fun selectChatPairing(pairingId: String) {
        _selectedChatPairingId.value = pairingId
    }

    fun sendChatMessage(pairing: PairingRequest, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val result = repository.sendChatMessage(
                pairingId = pairing.id,
                participantUids = pairing.participantUids,
                senderName = currentUserDisplayName,
                text = trimmed,
                messageType = ChatMessageType.TEXT
            )
            if (result.isFailure) {
                emitFeedback(result.exceptionOrNull()?.localizedMessage ?: "Failed to send message")
            }
        }
    }

    suspend fun prepareForSignOut(context: Context) {
        if (WebRtcScreenShareController.isHostStreaming.value) {
            ScreenShareForegroundService.stop(context)
        }
        val activeViewer = _activeViewerSessionId.value
        if (!activeViewer.isNullOrBlank()) {
            repository.endSession(activeViewer, expired = false)
            _activeViewerSessionId.value = null
        }
        WebRtcScreenShareController.stopAll()
        val code = _deviceCode.value
        if (code.isNotBlank()) {
            repository.updateDevicePresence(code, online = false, sharingActive = false)
        }
        localPrefs.clearUserIdentity()
        _isDeviceOnline.value = false
    }

    private fun unwrapFirestoreErrorOrThrowCancellation(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }

    private companion object {
        const val TAG = "VitRemoteVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
