package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class VitRemoteRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // --- 1. Private User Profile ---

    suspend fun ensureUserProfile(user: FirebaseUser, preferredLanguage: String): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            require(uid.isNotBlank()) { "Authenticated UID is required" }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val safeName = (user.displayName?.takeIf { it.isNotBlank() } ?: "VitRemote User").take(100)
            val safeEmail = (user.email?.takeIf { it.length >= 3 } ?: "user@vitremote.app").take(200)
            val safeLang = if (preferredLanguage == "en") "en" else "bn"
            val photoUrl = user.photoUrl?.toString()?.take(500)?.takeIf { it.isNotBlank() }

            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to photoUrl,
                    "preferredLanguage" to safeLang,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updateMap = mutableMapOf<String, Any>(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "preferredLanguage" to safeLang,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (photoUrl != null) {
                    updateMap["photoUrl"] = photoUrl
                }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePreferredLanguage(languageCode: String): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val safeLang = if (languageCode == "en") "en" else "bn"
            db.collection("users").document(uid).update(
                mapOf(
                    "preferredLanguage" to safeLang,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 2. Device Registry ---

    suspend fun registerOrUpdateMyDevice(
        deviceCode: String,
        deviceName: String,
        ownerName: String,
        platformVersion: String,
        online: Boolean,
        sharingActive: Boolean = false
    ): Result<Unit> {
        val path = "devices/$deviceCode"
        return try {
            val uid = requireUserId()
            require(deviceCode.matches(Regex("^[a-zA-Z0-9_\\-]{4,32}$"))) { "Invalid device code" }
            val docRef = db.collection("devices").document(deviceCode)
            val snap = docRef.get().await()
            val safeDeviceName = deviceName.ifBlank { "Android Device" }.take(100)
            val safeOwnerName = ownerName.ifBlank { "VitRemote User" }.take(100)
            val safePlatform = platformVersion.ifBlank { "Android" }.take(40)

            if (!snap.exists()) {
                val payload = mapOf(
                    "ownerUid" to uid,
                    "deviceName" to safeDeviceName,
                    "ownerName" to safeOwnerName,
                    "online" to online,
                    "sharingActive" to sharingActive,
                    "platformVersion" to safePlatform,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else if (snap.getString("ownerUid") == uid) {
                docRef.update(
                    mapOf(
                        "deviceName" to safeDeviceName,
                        "ownerName" to safeOwnerName,
                        "online" to online,
                        "sharingActive" to sharingActive,
                        "platformVersion" to safePlatform,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateDevicePresence(
        deviceCode: String,
        online: Boolean,
        sharingActive: Boolean
    ): Result<Unit> {
        val path = "devices/$deviceCode"
        return try {
            requireUserId()
            if (deviceCode.isBlank()) return Result.success(Unit)
            db.collection("devices").document(deviceCode).update(
                mapOf(
                    "online" to online,
                    "sharingActive" to sharingActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun lookupDeviceByCode(deviceCode: String): Result<DeviceRegistration?> {
        val normalized = deviceCode.trim().uppercase()
        val path = "devices/$normalized"
        return try {
            requireUserId()
            if (!normalized.matches(Regex("^[A-Z0-9_\\-]{4,32}$"))) {
                return Result.success(null)
            }
            val snap = db.collection("devices").document(normalized).get().await()
            val device = if (snap.exists()) {
                snap.toObject(DeviceRegistration::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(device)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observeDevice(deviceCode: String): Flow<DeviceRegistration?> = flow {
        val uid = requireUserId()
        val path = "devices/$deviceCode"
        emitAll(
            db.collection("devices").document(deviceCode)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(DeviceRegistration::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logListenerErrorIfSignedIn(error, uid, OperationType.GET, path)
                    throw error
                }
        )
    }

    // --- 3. Pairing Requests & Connection History ---

    fun observePairingRequests(userId: String): Flow<List<PairingRequest>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session UID mismatch" }
        val path = "pairingRequests"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PairingRequest::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logListenerErrorIfSignedIn(error, uid, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun sendPairingRequest(
        requesterDeviceCode: String,
        requesterDeviceName: String,
        requesterName: String,
        targetDevice: DeviceRegistration
    ): Result<String> {
        var path = "pairingRequests"
        return try {
            val uid = requireUserId()
            require(requesterDeviceCode != targetDevice.deviceCode) { "Cannot pair with own device" }
            val docRef = db.collection("pairingRequests").document()
            path = docRef.path
            val payload = mapOf(
                "requesterUid" to uid,
                "requesterDeviceCode" to requesterDeviceCode.take(32),
                "requesterDeviceName" to requesterDeviceName.ifBlank { "Android Device" }.take(100),
                "requesterName" to requesterName.ifBlank { "VitRemote User" }.take(100),
                "targetOwnerUid" to targetDevice.ownerUid,
                "targetDeviceCode" to targetDevice.deviceCode.take(32),
                "targetDeviceName" to targetDevice.deviceName.ifBlank { "Remote Device" }.take(100),
                "targetOwnerName" to targetDevice.ownerName.ifBlank { "Device Owner" }.take(100),
                "participantUids" to listOf(uid, targetDevice.ownerUid),
                "status" to PairingStatus.PENDING.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun respondToPairingRequest(requestId: String, accept: Boolean): Result<Unit> {
        val path = "pairingRequests/$requestId"
        return try {
            requireUserId()
            require(requestId.isNotBlank())
            val nextStatus = if (accept) PairingStatus.ACCEPTED.name else PairingStatus.REJECTED.name
            db.collection("pairingRequests").document(requestId).update(
                mapOf(
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokePairing(requestId: String): Result<Unit> {
        val path = "pairingRequests/$requestId"
        return try {
            requireUserId()
            require(requestId.isNotBlank())
            db.collection("pairingRequests").document(requestId).update(
                mapOf(
                    "status" to PairingStatus.REVOKED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePairingRecord(requestId: String): Result<Unit> {
        val path = "pairingRequests/$requestId"
        return try {
            requireUserId()
            require(requestId.isNotBlank())
            db.collection("pairingRequests").document(requestId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 4. Consent-Gated Live Screen Sharing Sessions & WebRTC Signaling ---

    fun observeSessions(userId: String): Flow<List<ScreenShareSession>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session UID mismatch" }
        val path = "sessions"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ScreenShareSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logListenerErrorIfSignedIn(error, uid, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeSession(sessionId: String): Flow<ScreenShareSession?> = flow {
        val uid = requireUserId()
        val path = "sessions/$sessionId"
        emitAll(
            db.collection("sessions").document(sessionId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(ScreenShareSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logListenerErrorIfSignedIn(error, uid, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun createSessionRequestAsViewer(
        pairing: PairingRequest,
        myDeviceCode: String,
        myDeviceName: String
    ): Result<String> {
        var path = "sessions"
        return try {
            val uid = requireUserId()
            val hostUid = pairing.peerUid(myDeviceCode)
            val hostCode = pairing.peerDeviceCode(myDeviceCode)
            val hostName = pairing.peerDeviceName(myDeviceCode)
            val docRef = db.collection("sessions").document()
            path = docRef.path
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + SESSION_EXPIRATION_MS))
            val payload = mapOf(
                "pairingId" to pairing.id,
                "hostUid" to hostUid,
                "hostDeviceCode" to hostCode,
                "hostDeviceName" to hostName.ifBlank { "Host Device" }.take(100),
                "viewerUid" to uid,
                "viewerDeviceCode" to myDeviceCode,
                "viewerDeviceName" to myDeviceName.ifBlank { "Viewer Device" }.take(100),
                "participantUids" to listOf(hostUid, uid),
                "status" to SessionStatus.REQUESTED.name,
                "screenWidth" to 0L,
                "screenHeight" to 0L,
                "qualityProfile" to QualityProfile.AUTO.name,
                "expiresAt" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createSessionAsHost(
        pairing: PairingRequest,
        myDeviceCode: String,
        myDeviceName: String
    ): Result<String> {
        var path = "sessions"
        return try {
            val uid = requireUserId()
            val viewerUid = pairing.peerUid(myDeviceCode)
            val viewerCode = pairing.peerDeviceCode(myDeviceCode)
            val viewerName = pairing.peerDeviceName(myDeviceCode)
            val docRef = db.collection("sessions").document()
            path = docRef.path
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + SESSION_EXPIRATION_MS))
            val payload = mapOf(
                "pairingId" to pairing.id,
                "hostUid" to uid,
                "hostDeviceCode" to myDeviceCode,
                "hostDeviceName" to myDeviceName.ifBlank { "Host Device" }.take(100),
                "viewerUid" to viewerUid,
                "viewerDeviceCode" to viewerCode,
                "viewerDeviceName" to viewerName.ifBlank { "Viewer Device" }.take(100),
                "participantUids" to listOf(uid, viewerUid),
                "status" to SessionStatus.APPROVED.name,
                "screenWidth" to 0L,
                "screenHeight" to 0L,
                "qualityProfile" to QualityProfile.AUTO.name,
                "expiresAt" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun respondToSessionRequest(sessionId: String, approve: Boolean): Result<Unit> {
        val path = "sessions/$sessionId"
        return try {
            requireUserId()
            val nextStatus = if (approve) SessionStatus.APPROVED.name else SessionStatus.REJECTED.name
            db.collection("sessions").document(sessionId).update(
                mapOf(
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun publishOfferSdp(
        sessionId: String,
        offerSdp: String,
        width: Int,
        height: Int,
        qualityProfile: QualityProfile
    ): Result<Unit> {
        val path = "sessions/$sessionId"
        return try {
            requireUserId()
            db.collection("sessions").document(sessionId).update(
                mapOf(
                    "offerSdp" to offerSdp.take(24000),
                    "screenWidth" to width.coerceIn(0, 4320).toLong(),
                    "screenHeight" to height.coerceIn(0, 4320).toLong(),
                    "qualityProfile" to qualityProfile.name,
                    "status" to SessionStatus.ACTIVE.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun publishAnswerSdp(sessionId: String, answerSdp: String): Result<Unit> {
        val path = "sessions/$sessionId"
        return try {
            requireUserId()
            db.collection("sessions").document(sessionId).update(
                mapOf(
                    "answerSdp" to answerSdp.take(24000),
                    "status" to SessionStatus.ACTIVE.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSessionQuality(sessionId: String, quality: QualityProfile): Result<Unit> {
        val path = "sessions/$sessionId"
        return try {
            requireUserId()
            db.collection("sessions").document(sessionId).update(
                mapOf(
                    "qualityProfile" to quality.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun endSession(sessionId: String, expired: Boolean = false): Result<Unit> {
        val path = "sessions/$sessionId"
        return try {
            requireUserId()
            val nextStatus = if (expired) SessionStatus.EXPIRED.name else SessionStatus.ENDED.name
            db.collection("sessions").document(sessionId).update(
                mapOf(
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 5. WebRTC ICE Candidates ---

    fun observeIceCandidates(sessionId: String, userId: String): Flow<List<IceCandidateRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session UID mismatch" }
        val path = "sessions/$sessionId/iceCandidates"
        emitAll(
            db.collection("sessions").document(sessionId).collection("iceCandidates")
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        IceCandidateRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logListenerErrorIfSignedIn(error, uid, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addIceCandidate(
        sessionId: String,
        participantUids: List<String>,
        role: PeerRole,
        sdpMid: String,
        sdpMLineIndex: Int,
        sdp: String
    ): Result<Unit> {
        var path = "sessions/$sessionId/iceCandidates"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("sessions").document(sessionId)
                .collection("iceCandidates").document()
            path = docRef.path
            val payload = mapOf(
                "sessionId" to sessionId,
                "participantUids" to participantUids,
                "senderUid" to uid,
                "role" to role.name,
                "sdpMid" to sdpMid.take(128),
                "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 32).toLong(),
                "sdp" to sdp.take(2000),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- 6. Paired Chat & Session Notifications ---

    fun observeChatMessages(pairingId: String, userId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session UID mismatch" }
        val path = "pairingRequests/$pairingId/messages"
        emitAll(
            db.collection("pairingRequests").document(pairingId).collection("messages")
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logListenerErrorIfSignedIn(error, uid, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun sendChatMessage(
        pairingId: String,
        participantUids: List<String>,
        senderName: String,
        text: String,
        messageType: ChatMessageType = ChatMessageType.TEXT
    ): Result<Unit> {
        var path = "pairingRequests/$pairingId/messages"
        return try {
            val uid = requireUserId()
            val trimmed = text.trim()
            require(trimmed.isNotEmpty() && trimmed.length <= 1000) { "Message text must be 1..1000 chars" }
            val docRef = db.collection("pairingRequests").document(pairingId)
                .collection("messages").document()
            path = docRef.path
            val payload = mapOf(
                "pairingId" to pairingId,
                "participantUids" to participantUids,
                "senderUid" to uid,
                "senderName" to senderName.ifBlank { "VitRemote User" }.take(100),
                "text" to trimmed,
                "messageType" to messageType.name,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun logListenerErrorIfSignedIn(
        error: Throwable,
        uid: String,
        operationType: OperationType,
        path: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    companion object {
        private const val SESSION_EXPIRATION_MS = 30L * 60L * 1000L // 30 minutes
    }
}
