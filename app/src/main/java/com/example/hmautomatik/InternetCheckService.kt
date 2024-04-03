package com.example.hmautomatik
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class InternetCheckService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var previousInternetAvailability = true

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "internet_status_channel"
        private const val NOTIFICATION_ID = 1
        private val _internetAvailable = MutableStateFlow(true)
        val internetAvailable = _internetAvailable.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        scope.launch {
            while (true) {
                val isAvailable = checkInternetConnection()
                if (isAvailable != previousInternetAvailability) {
                    _internetAvailable.emit(isAvailable)
                    if (!isAvailable) {
                        showNoInternetNotification()
                    }
                    previousInternetAvailability = isAvailable
                }
                delay(10000)
            }
        }
        return START_STICKY
    }

    private fun checkInternetConnection(): Boolean {
        return try {
            val timeoutMs = 1500
            val sock = Socket()
            val sockaddr = InetSocketAddress("8.8.8.8", 53)

            sock.connect(sockaddr, timeoutMs)
            sock.close()

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Internet Status"
            val descriptionText = "Shows internet connectivity status"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNoInternetNotification() {
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_background2)
            .setContentTitle("HM Automatik")
            .setContentText("Интернет отсутствует")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        Log.d("InternetCheckService", "Showing no internet notification")
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }
}
