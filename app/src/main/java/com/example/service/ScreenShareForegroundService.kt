package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ChatMessageType
import com.example.data.QualityProfile
import com.example.data.ScreenShareSession
import com.example.data.VitRemoteRepository
import com.example.util.SessionNotificationHelper
import com.example.webrtc.WebRtcScreenShareController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Mandatory visible mediaProjection foreground service for consent-based screen sharing.
 * Displays an ongoing notification with an immediate Stop Sharing action button.
 */
class ScreenShareForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentSessionId: String? = null
    private var currentPairingId: String? = null
    private var currentParticipantUids: List<String> = emptyList()
    private var currentDeviceCode: String = ""
    private var currentSenderName: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SHARING -> {
                val session = pendingSession
                val projectionData = pendingProjectionData
                val userId = pendingUserId
                val deviceCode = pendingDeviceCode
                val senderName = pendingSenderName
                if (session == null || projectionData == null || userId.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                currentSessionId = session.id
                currentPairingId = session.pairingId
                currentParticipantUids = session.participantUids
                currentDeviceCode = deviceCode
                currentSenderName = senderName

                // CRITICAL on Android 14+ (API 34+): Enter foreground with MEDIA_PROJECTION type
                // BEFORE invoking MediaProjectionManager / ScreenCapturerAndroid.
                SessionNotificationHelper.ensureChannels(this)
                val notification = buildOngoingNotification(session.viewerDeviceName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                val repo = VitRemoteRepository(applicationContext)
                serviceScope.launch {
                    repo.updateDevicePresence(deviceCode, online = true, sharingActive = true)
                    repo.sendChatMessage(
                        pairingId = session.pairingId,
                        participantUids = session.participantUids,
                        senderName = senderName,
                        text = "স্ক্রিন শেয়ারিং শুরু হয়েছে / Screen sharing started (${session.hostDeviceName})",
                        messageType = ChatMessageType.SESSION_EVENT
                    )
                }

                WebRtcScreenShareController.startHostScreenShare(
                    context = applicationContext,
                    repository = repo,
                    session = session,
                    currentUserId = userId,
                    mediaProjectionResultData = projectionData,
                    quality = session.parsedQuality(),
                    onProjectionStoppedBySystem = {
                        stopSharingAndCleanUp()
                    }
                )
            }

            ACTION_STOP_SHARING -> {
                stopSharingAndCleanUp()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopSharingAndCleanUp() {
        val sessionId = currentSessionId ?: WebRtcScreenShareController.activeHostSessionId.value
        val pairingId = currentPairingId
        val participants = currentParticipantUids
        val deviceCode = currentDeviceCode
        val senderName = currentSenderName

        WebRtcScreenShareController.stopAll()
        clearPendingPayload()

        val repo = VitRemoteRepository(applicationContext)
        serviceScope.launch {
            if (!sessionId.isNullOrBlank()) {
                repo.endSession(sessionId, expired = false)
            }
            if (deviceCode.isNotBlank()) {
                repo.updateDevicePresence(deviceCode, online = true, sharingActive = false)
            }
            if (!pairingId.isNullOrBlank() && participants.size == 2) {
                repo.sendChatMessage(
                    pairingId = pairingId,
                    participantUids = participants,
                    senderName = senderName.ifBlank { "VitRemote" },
                    text = "স্ক্রিন শেয়ারিং বন্ধ হয়েছে / Screen sharing ended",
                    messageType = ChatMessageType.SESSION_EVENT
                )
            }
        }

        SessionNotificationHelper.showSessionAlert(
            context = applicationContext,
            notificationId = 4102,
            title = "VitRemote",
            body = "স্ক্রিন শেয়ারিং সেশন বন্ধ করা হয়েছে / Screen sharing session ended"
        )

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildOngoingNotification(viewerDeviceName: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenShareForegroundService::class.java).apply {
            action = ACTION_STOP_SHARING
        }
        val stopPending = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (viewerDeviceName.isNotBlank()) {
            "${getString(R.string.notification_sharing_body)} ($viewerDeviceName)"
        } else {
            getString(R.string.notification_sharing_body)
        }

        return NotificationCompat.Builder(this, SessionNotificationHelper.CHANNEL_SHARING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_sharing_title))
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppPending)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_stop),
                stopPending
            )
            .build()
    }

    override fun onDestroy() {
        WebRtcScreenShareController.stopAll()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_SHARING = "com.example.action.START_SCREEN_SHARING"
        const val ACTION_STOP_SHARING = "com.example.action.STOP_SCREEN_SHARING"
        private const val NOTIFICATION_ID = 4001

        @Volatile
        private var pendingSession: ScreenShareSession? = null
        @Volatile
        private var pendingProjectionData: Intent? = null
        @Volatile
        private var pendingUserId: String? = null
        @Volatile
        private var pendingDeviceCode: String = ""
        @Volatile
        private var pendingSenderName: String = ""

        fun start(
            context: Context,
            session: ScreenShareSession,
            userId: String,
            deviceCode: String,
            senderName: String,
            resultCode: Int,
            projectionResultData: Intent
        ) {
            if (resultCode != Activity.RESULT_OK) return
            pendingSession = session
            pendingProjectionData = projectionResultData
            pendingUserId = userId
            pendingDeviceCode = deviceCode
            pendingSenderName = senderName

            val intent = Intent(context, ScreenShareForegroundService::class.java).apply {
                action = ACTION_START_SHARING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenShareForegroundService::class.java).apply {
                action = ACTION_STOP_SHARING
            }
            context.startService(intent)
        }

        private fun clearPendingPayload() {
            pendingSession = null
            pendingProjectionData = null
            pendingUserId = null
            pendingDeviceCode = ""
            pendingSenderName = ""
        }
    }
}
