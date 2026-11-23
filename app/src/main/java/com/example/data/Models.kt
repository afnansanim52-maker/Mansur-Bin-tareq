package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val preferredLanguage: String = "bn",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeviceRegistration(
    @DocumentId val deviceCode: String = "",
    val ownerUid: String = "",
    val deviceName: String = "",
    val ownerName: String = "",
    val online: Boolean = false,
    val sharingActive: Boolean = false,
    val platformVersion: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class PairingStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    REVOKED;

    companion object {
        fun fromRaw(raw: String?): PairingStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class PairingRequest(
    @DocumentId val id: String = "",
    val requesterUid: String = "",
    val requesterDeviceCode: String = "",
    val requesterDeviceName: String = "",
    val requesterName: String = "",
    val targetOwnerUid: String = "",
    val targetDeviceCode: String = "",
    val targetDeviceName: String = "",
    val targetOwnerName: String = "",
    val participantUids: List<String> = emptyList(),
    val status: String = PairingStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedStatus(): PairingStatus = PairingStatus.fromRaw(status)

    fun isIncomingFor(myDeviceCode: String): Boolean = targetDeviceCode == myDeviceCode

    fun peerDeviceCode(myDeviceCode: String): String =
        if (requesterDeviceCode == myDeviceCode) targetDeviceCode else requesterDeviceCode

    fun peerDeviceName(myDeviceCode: String): String =
        if (requesterDeviceCode == myDeviceCode) targetDeviceName else requesterDeviceName

    fun peerUserName(myDeviceCode: String): String =
        if (requesterDeviceCode == myDeviceCode) targetOwnerName else requesterName

    fun peerUid(myDeviceCode: String): String =
        if (requesterDeviceCode == myDeviceCode) targetOwnerUid else requesterUid
}

enum class ChatMessageType {
    TEXT,
    SESSION_EVENT;

    companion object {
        fun fromRaw(raw: String?): ChatMessageType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val pairingId: String = "",
    val participantUids: List<String> = emptyList(),
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = ChatMessageType.TEXT.name,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun parsedType(): ChatMessageType = ChatMessageType.fromRaw(messageType)
}

enum class SessionStatus {
    REQUESTED,
    APPROVED,
    ACTIVE,
    REJECTED,
    ENDED,
    EXPIRED;

    fun isLiveOrPending(): Boolean = this == REQUESTED || this == APPROVED || this == ACTIVE

    companion object {
        fun fromRaw(raw: String?): SessionStatus =
            entries.firstOrNull { it.name == raw } ?: ENDED
    }
}

enum class QualityProfile(
    val targetWidth: Int,
    val targetHeight: Int,
    val targetFps: Int,
    val maxBitrateKbps: Int
) {
    AUTO(720, 1280, 24, 1800),
    HIGH(1080, 1920, 30, 3200),
    BALANCED(720, 1280, 20, 1400),
    LOW(480, 854, 15, 700);

    companion object {
        fun fromRaw(raw: String?): QualityProfile =
            entries.firstOrNull { it.name == raw } ?: AUTO
    }
}

@Keep
@IgnoreExtraProperties
data class ScreenShareSession(
    @DocumentId val id: String = "",
    val pairingId: String = "",
    val hostUid: String = "",
    val hostDeviceCode: String = "",
    val hostDeviceName: String = "",
    val viewerUid: String = "",
    val viewerDeviceCode: String = "",
    val viewerDeviceName: String = "",
    val participantUids: List<String> = emptyList(),
    val status: String = SessionStatus.REQUESTED.name,
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val screenWidth: Long = 0L,
    val screenHeight: Long = 0L,
    val qualityProfile: String = QualityProfile.AUTO.name,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun parsedStatus(): SessionStatus = SessionStatus.fromRaw(status)
    fun parsedQuality(): QualityProfile = QualityProfile.fromRaw(qualityProfile)
}

enum class PeerRole {
    HOST,
    VIEWER;

    companion object {
        fun fromRaw(raw: String?): PeerRole =
            entries.firstOrNull { it.name == raw } ?: HOST
    }
}

@Keep
@IgnoreExtraProperties
data class IceCandidateRecord(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val participantUids: List<String> = emptyList(),
    val senderUid: String = "",
    val role: String = PeerRole.HOST.name,
    val sdpMid: String = "",
    val sdpMLineIndex: Long = 0L,
    val sdp: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class NetworkGrade {
    CONNECTING,
    EXCELLENT,
    GOOD,
    FAIR,
    POOR
}

data class WebRtcTelemetry(
    val connectionState: String = "NEW",
    val latencyMs: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val bitrateKbps: Int = 0,
    val networkGrade: NetworkGrade = NetworkGrade.CONNECTING
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
