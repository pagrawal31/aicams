package com.aicams.viewer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Keeps the camera process eligible to capture after the display turns off. */
class CameraStreamingForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Camera streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while this phone is publishing its camera"
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("AI Cams camera is active")
            .setContentText("Battery Saver is allowing the display to sleep")
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (intent?.getBooleanExtra(EXTRA_HAS_MICROPHONE_PERMISSION, false) == true) {
                serviceTypes = serviceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, serviceTypes)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "camera_streaming"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.aicams.viewer.action.START_CAMERA_FOREGROUND"
        private const val EXTRA_HAS_MICROPHONE_PERMISSION = "has_microphone_permission"

        fun start(context: Context, hasMicrophonePermission: Boolean) {
            val intent = Intent(context, CameraStreamingForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_HAS_MICROPHONE_PERMISSION, hasMicrophonePermission)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraStreamingForegroundService::class.java))
        }
    }
}
