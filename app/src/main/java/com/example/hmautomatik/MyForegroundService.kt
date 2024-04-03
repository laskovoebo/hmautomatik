package com.example.hmautomatik

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MyForegroundService : Service() {
    private lateinit var retryService: RetryService

    override fun onCreate() {
        super.onCreate()
        retryService = RetryService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "ForegroundServiceChannel"
            val channelName = "Foreground Service Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Service Running")
                .setContentText("com.example.hmautomatik.RetryService is running in background")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()

            startForeground(1, notification)

            retryService.start()

            return START_STICKY
        } else {
            val notification: Notification = NotificationCompat.Builder(this)
                .setContentTitle("Service Running")
                .setContentText("com.example.hmautomatik.RetryService is running in background")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()

            startForeground(1, notification)

            retryService.start()

            return START_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        retryService.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
