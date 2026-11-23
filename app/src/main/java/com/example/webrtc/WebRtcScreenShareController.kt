package com.example.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import com.example.data.NetworkGrade
import com.example.data.PeerRole
import com.example.data.QualityProfile
import com.example.data.ScreenShareSession
import com.example.data.VitRemoteRepository
import com.example.data.WebRtcTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Production WebRTC controller managing genuine Android MediaProjection screen capture (Host)
 * and low-latency peer-to-peer rendering (Viewer) with Cloud Firestore SDP & Trickle-ICE signaling.
 *
 * Security invariants:
 * - Video-only screen capture via explicit MediaProjection consent (zero camera/microphone tracks).
 * - Immediate resource cleanup on stop or MediaProjection revocation.
 */
object WebRtcScreenShareController {

    private const val TAG = "VitRemoteWebRTC"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var factoryInitialized = false
    private var eglBaseInstance: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null

    private var peerConnection: PeerConnection? = null
    private var screenCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localVideoSender: RtpSender? = null

    private var signalingJob: Job? = null
    private var iceJob: Job? = null
    private var statsJob: Job? = null

    private val appliedIceIds = mutableSetOf<String>()
    private var appliedRemoteSdp: String? = null
    private var activeSessionId: String? = null
    private var activeRole: PeerRole? = null
    private var currentQuality: QualityProfile = QualityProfile.AUTO

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _telemetry = MutableStateFlow(WebRtcTelemetry())
    val telemetry: StateFlow<WebRtcTelemetry> = _telemetry.asStateFlow()

    private val _isHostStreaming = MutableStateFlow(false)
    val isHostStreaming: StateFlow<Boolean> = _isHostStreaming.asStateFlow()

    private val _activeHostSessionId = MutableStateFlow<String?>(null)
    val activeHostSessionId: StateFlow<String?> = _activeHostSessionId.asStateFlow()

    private val _hostStartedAtMs = MutableStateFlow<Long?>(null)
    val hostStartedAtMs: StateFlow<Long?> = _hostStartedAtMs.asStateFlow()

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
    )

    @Synchronized
    fun getEglBase(): EglBase {
        if (eglBaseInstance == null) {
            eglBaseInstance = EglBase.create()
        }
        return eglBaseInstance!!
    }

    @Synchronized
    private fun ensureFactory(context: Context): PeerConnectionFactory {
        if (!factoryInitialized) {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)
            factoryInitialized = true
        }
        if (peerConnectionFactory == null) {
            val eglContext = getEglBase().eglBaseContext
            val encoderFactory = DefaultVideoEncoderFactory(eglContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglContext)
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
        }
        return peerConnectionFactory!!
    }

    fun startHostScreenShare(
        context: Context,
        repository: VitRemoteRepository,
        session: ScreenShareSession,
        currentUserId: String,
        mediaProjectionResultData: Intent,
        quality: QualityProfile = QualityProfile.AUTO,
        onProjectionStoppedBySystem: () -> Unit
    ) {
        stopAll()
        activeSessionId = session.id
        activeRole = PeerRole.HOST
        currentQuality = quality
        _activeHostSessionId.value = session.id
        _hostStartedAtMs.value = System.currentTimeMillis()
        _isHostStreaming.value = true
        _telemetry.value = WebRtcTelemetry(
            connectionState = "CONNECTING",
            width = quality.targetWidth,
            height = quality.targetHeight,
            fps = quality.targetFps,
            networkGrade = NetworkGrade.CONNECTING
        )

        val factory = ensureFactory(context)
        val eglContext = getEglBase().eglBaseContext

        val capturer = ScreenCapturerAndroid(
            mediaProjectionResultData,
            object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by user or system")
                    onProjectionStoppedBySystem()
                }
            }
        )
        screenCapturer = capturer

        val helper = SurfaceTextureHelper.create("VitRemoteScreenCapture", eglContext)
        surfaceTextureHelper = helper

        // Video-only screencast source — no camera or microphone tracks created.
        val videoSource = factory.createVideoSource(true)
        localVideoSource = videoSource
        capturer.initialize(helper, context.applicationContext, videoSource.capturerObserver)
        capturer.startCapture(quality.targetWidth, quality.targetHeight, quality.targetFps)

        val videoTrack = factory.createVideoTrack("VITREMOTE_SCREEN_TRACK", videoSource)
        videoTrack.setEnabled(true)
        localVideoTrack = videoTrack

        val pc = createPeerConnection(
            factory = factory,
            repository = repository,
            session = session,
            role = PeerRole.HOST
        ) ?: run {
            stopAll()
            return
        }
        peerConnection = pc

        val sender = pc.addTrack(videoTrack, listOf("VITREMOTE_SCREEN_STREAM"))
        localVideoSender = sender
        applyBitrateCap(sender, quality.maxBitrateKbps)

        val offerConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        pc.createOffer(object : SimpleSdpObserver("HostCreateOffer") {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(SimpleSdpObserver("HostSetLocal"), desc)
                scope.launch {
                    repository.publishOfferSdp(
                        sessionId = session.id,
                        offerSdp = desc.description,
                        width = quality.targetWidth,
                        height = quality.targetHeight,
                        qualityProfile = quality
                    )
                }
            }
        }, offerConstraints)

        // Observe Viewer SDP Answer & quality changes
        signalingJob = scope.launch {
            repository.observeSession(session.id)
                .catch { e -> Log.w(TAG, "Host session signaling observer error", e) }
                .collectLatest { updated ->
                    if (updated == null || !updated.parsedStatus().isLiveOrPending()) {
                        onProjectionStoppedBySystem()
                        return@collectLatest
                    }
                    val answerSdp = updated.answerSdp
                    if (!answerSdp.isNullOrBlank() && answerSdp != appliedRemoteSdp) {
                        appliedRemoteSdp = answerSdp
                        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                        peerConnection?.setRemoteDescription(SimpleSdpObserver("HostSetRemote"), remoteDesc)
                    }
                    val requestedQuality = updated.parsedQuality()
                    if (requestedQuality != currentQuality) {
                        applyAdaptiveQuality(requestedQuality)
                    }
                }
        }

        observeRemoteIceCandidates(repository, session.id, currentUserId, PeerRole.HOST)
        startTelemetryPolling(pc)
    }

    fun startViewerSession(
        context: Context,
        repository: VitRemoteRepository,
        session: ScreenShareSession,
        currentUserId: String
    ) {
        if (activeSessionId == session.id && activeRole == PeerRole.VIEWER && peerConnection != null) {
            return
        }
        stopAll()
        activeSessionId = session.id
        activeRole = PeerRole.VIEWER
        _telemetry.value = WebRtcTelemetry(
            connectionState = "CONNECTING",
            networkGrade = NetworkGrade.CONNECTING
        )

        val factory = ensureFactory(context)
        val pc = createPeerConnection(
            factory = factory,
            repository = repository,
            session = session,
            role = PeerRole.VIEWER
        ) ?: return
        peerConnection = pc

        signalingJob = scope.launch {
            repository.observeSession(session.id)
                .catch { e -> Log.w(TAG, "Viewer session signaling observer error", e) }
                .collectLatest { updated ->
                    if (updated == null || !updated.parsedStatus().isLiveOrPending()) {
                        stopAll()
                        return@collectLatest
                    }
                    val offerSdp = updated.offerSdp
                    if (!offerSdp.isNullOrBlank() && offerSdp != appliedRemoteSdp) {
                        appliedRemoteSdp = offerSdp
                        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
                        pc.setRemoteDescription(object : SimpleSdpObserver("ViewerSetRemote") {
                            override fun onSetSuccess() {
                                val answerConstraints = MediaConstraints().apply {
                                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                                }
                                pc.createAnswer(object : SimpleSdpObserver("ViewerCreateAnswer") {
                                    override fun onCreateSuccess(desc: SessionDescription?) {
                                        if (desc == null) return
                                        pc.setLocalDescription(SimpleSdpObserver("ViewerSetLocal"), desc)
                                        scope.launch {
                                            repository.publishAnswerSdp(session.id, desc.description)
                                        }
                                    }
                                }, answerConstraints)
                            }
                        }, remoteDesc)
                    }
                }
        }

        observeRemoteIceCandidates(repository, session.id, currentUserId, PeerRole.VIEWER)
        startTelemetryPolling(pc)
    }

    fun applyAdaptiveQuality(profile: QualityProfile) {
        currentQuality = profile
        try {
            screenCapturer?.changeCaptureFormat(
                profile.targetWidth,
                profile.targetHeight,
                profile.targetFps
            )
            localVideoSender?.let { applyBitrateCap(it, profile.maxBitrateKbps) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to adapt capture quality: ${e.message}")
        }
    }

    private fun applyBitrateCap(sender: RtpSender, maxBitrateKbps: Int) {
        try {
            val params = sender.parameters
            params.encodings.forEach { encoding ->
                encoding.maxBitrateBps = maxBitrateKbps * 1000
                encoding.minBitrateBps = (maxBitrateKbps * 250).coerceAtLeast(150_000)
            }
            sender.parameters = params
        } catch (e: Exception) {
            Log.w(TAG, "Unable to set RTP bitrate cap: ${e.message}")
        }
    }

    private fun createPeerConnection(
        factory: PeerConnectionFactory,
        repository: VitRemoteRepository,
        session: ScreenShareSession,
        role: PeerRole
    ): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        return factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                scope.launch {
                    repository.addIceCandidate(
                        sessionId = session.id,
                        participantUids = session.participantUids,
                        role = role,
                        sdpMid = candidate.sdpMid ?: "",
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        sdp = candidate.sdp
                    )
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                val label = newState?.name ?: "UNKNOWN"
                val grade = when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> NetworkGrade.GOOD
                    PeerConnection.IceConnectionState.DISCONNECTED -> NetworkGrade.POOR
                    else -> NetworkGrade.CONNECTING
                }
                _telemetry.value = _telemetry.value.copy(
                    connectionState = label,
                    networkGrade = grade
                )
            }

            override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    track.setEnabled(true)
                    _remoteVideoTrack.value = track
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    track.setEnabled(true)
                    _remoteVideoTrack.value = track
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) {
                stream?.videoTracks?.firstOrNull()?.let { track ->
                    track.setEnabled(true)
                    _remoteVideoTrack.value = track
                }
            }
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(dc: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
        })
    }

    private fun observeRemoteIceCandidates(
        repository: VitRemoteRepository,
        sessionId: String,
        currentUserId: String,
        myRole: PeerRole
    ) {
        iceJob?.cancel()
        iceJob = scope.launch {
            repository.observeIceCandidates(sessionId, currentUserId)
                .catch { e -> Log.w(TAG, "ICE observer error", e) }
                .collectLatest { candidates ->
                    candidates.forEach { record ->
                        if (record.role != myRole.name && appliedIceIds.add(record.id)) {
                            peerConnection?.addIceCandidate(
                                IceCandidate(
                                    record.sdpMid,
                                    record.sdpMLineIndex.toInt(),
                                    record.sdp
                                )
                            )
                        }
                    }
                }
        }
    }

    private fun startTelemetryPolling(pc: PeerConnection) {
        statsJob?.cancel()
        statsJob = scope.launch {
            var lastBytes = 0L
            var lastTsMs = System.currentTimeMillis()
            while (isActive) {
                delay(2000L)
                pc.getStats { report ->
                    var rttMs = _telemetry.value.latencyMs
                    var width = _telemetry.value.width
                    var height = _telemetry.value.height
                    var fps = _telemetry.value.fps
                    var totalBytes = lastBytes

                    report.statsMap.values.forEach { stat ->
                        when (stat.type) {
                            "candidate-pair" -> {
                                val state = stat.members["state"] as? String
                                val nominated = stat.members["nominated"] as? Boolean ?: false
                                if (state == "succeeded" || nominated) {
                                    val rttSec = (stat.members["currentRoundTripTime"] as? Number)?.toDouble()
                                    if (rttSec != null && rttSec > 0) {
                                        rttMs = (rttSec * 1000.0).toInt()
                                    }
                                }
                            }
                            "inbound-rtp", "outbound-rtp" -> {
                                val kind = stat.members["kind"] as? String ?: stat.members["mediaType"] as? String
                                if (kind == "video") {
                                    (stat.members["frameWidth"] as? Number)?.toInt()?.takeIf { it > 0 }?.let { width = it }
                                    (stat.members["frameHeight"] as? Number)?.toInt()?.takeIf { it > 0 }?.let { height = it }
                                    (stat.members["framesPerSecond"] as? Number)?.toInt()?.takeIf { it > 0 }?.let { fps = it }
                                    val bytes = (stat.members["bytesReceived"] as? Number)?.toLong()
                                        ?: (stat.members["bytesSent"] as? Number)?.toLong()
                                    if (bytes != null) totalBytes = bytes
                                }
                            }
                        }
                    }

                    val now = System.currentTimeMillis()
                    val elapsedSec = ((now - lastTsMs).coerceAtLeast(500L)) / 1000.0
                    val kbps = if (totalBytes > lastBytes) {
                        (((totalBytes - lastBytes) * 8.0) / 1000.0 / elapsedSec).toInt()
                    } else {
                        _telemetry.value.bitrateKbps
                    }
                    lastBytes = totalBytes
                    lastTsMs = now

                    val grade = when {
                        pc.iceConnectionState() != PeerConnection.IceConnectionState.CONNECTED &&
                            pc.iceConnectionState() != PeerConnection.IceConnectionState.COMPLETED -> NetworkGrade.CONNECTING
                        rttMs in 1..65 -> NetworkGrade.EXCELLENT
                        rttMs in 66..140 -> NetworkGrade.GOOD
                        rttMs in 141..260 -> NetworkGrade.FAIR
                        rttMs > 260 -> NetworkGrade.POOR
                        else -> NetworkGrade.GOOD
                    }

                    _telemetry.value = WebRtcTelemetry(
                        connectionState = pc.iceConnectionState().name,
                        latencyMs = rttMs,
                        width = width,
                        height = height,
                        fps = fps,
                        bitrateKbps = kbps,
                        networkGrade = grade
                    )
                }
            }
        }
    }

    @Synchronized
    fun stopAll() {
        signalingJob?.cancel()
        signalingJob = null
        iceJob?.cancel()
        iceJob = null
        statsJob?.cancel()
        statsJob = null

        try {
            screenCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        try {
            screenCapturer?.dispose()
        } catch (_: Exception) {
        }
        screenCapturer = null

        try {
            localVideoTrack?.setEnabled(false)
            localVideoTrack?.dispose()
        } catch (_: Exception) {
        }
        localVideoTrack = null
        localVideoSender = null

        try {
            localVideoSource?.dispose()
        } catch (_: Exception) {
        }
        localVideoSource = null

        try {
            surfaceTextureHelper?.dispose()
        } catch (_: Exception) {
        }
        surfaceTextureHelper = null

        try {
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (_: Exception) {
        }
        peerConnection = null

        appliedIceIds.clear()
        appliedRemoteSdp = null
        activeSessionId = null
        activeRole = null
        _remoteVideoTrack.value = null
        _isHostStreaming.value = false
        _activeHostSessionId.value = null
        _hostStartedAtMs.value = null
        _telemetry.value = WebRtcTelemetry()
    }

    private open class SimpleSdpObserver(private val tag: String) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) {
            Log.e(TAG, "$tag create failure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e(TAG, "$tag set failure: $error")
        }
    }
}
