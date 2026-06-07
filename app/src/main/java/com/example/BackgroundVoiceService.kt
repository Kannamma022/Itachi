package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BackgroundVoiceService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "voice_chat_channel"

        fun startService(context: Context) {
            val intent = Intent(context, BackgroundVoiceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundVoiceService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            openAppIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val toggleMuteIntent = Intent("com.example.ACTION_TOGGLE_MUTE").apply {
            setPackage(packageName)
        }
        val toggleMutePendingIntent = PendingIntent.getBroadcast(
            this,
            11,
            toggleMuteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val disconnectIntent = Intent("com.example.ACTION_DISCONNECT").apply {
            setPackage(packageName)
        }
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            this,
            12,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val vm = VoiceViewModel.instance
        val channelName = vm?.connectedChannel?.value?.name ?: "Voice Channel"
        val statusText = if (vm?.isMuted?.value == true) "Microphone Muted" else "Microphone Active (Background Transmitting)"
        val muteActionTitle = if (vm?.isMuted?.value == true) "Unmute" else "Mute"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Connected to $channelName")
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Connected to $channelName\n$statusText"))
            .addAction(
                android.R.drawable.ic_media_play,
                muteActionTitle,
                toggleMutePendingIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "Disconnect",
                disconnectPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(
                        NOTIFICATION_ID, 
                        notification, 
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Voice Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active background voice connections"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
