package com.example.hmautomatik
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val sharedPrefs = context.getSharedPreferences("AppSettings", MODE_PRIVATE)
            val encryptionKey = sharedPrefs.getString("encryptionKey", "123123123") ?: "123123123"
            val phoneNumberString = sharedPrefs.getString("phoneNumberList", "")
            val phoneNumberList = phoneNumberString?.split(",")?.toSet() ?: setOf()
            for (message in smsMessages) {
                val sender = message.displayOriginatingAddress
                if (phoneNumberList.contains(sender)) {
                    Log.d("SmsReceiver", "Сообщение собрано")
                    val body = message.messageBody
                    val currentTime = getCurrentTimeISO8601()
                    val json = JSONObject().apply {
                        put("sender", sender)
                        put("receiver", "+7123123123")
                        put("message", body)
                        put("time", currentTime)
                    }
                    Log.d("SmsReceiver", json.toString())
                    try {
                        val hmacSHA256 = generateHmacSHA256(json.toString(), encryptionKey)
                        sendSmsToApi(json, context, hmacSHA256)
                    } catch (e: Exception) {
                        sendLogToMainActivity(context, sender, "Ошибка при отправке: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }


    private fun sendLogToMainActivity(context: Context, sender: String, logMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val logEntry = LogEntry(
                sender = sender,
                logText = logMessage,
                timestamp = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(context).logEntryDao().insert(logEntry)
        }
    }

    private fun getCurrentTimeISO8601(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return dateFormat.format(System.currentTimeMillis())
    }

    private fun generateHmacSHA256(input: String, key: String): String {
        val sha256Hmac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        sha256Hmac.init(secretKey)

        val hash = sha256Hmac.doFinal(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { String.format("%02x", it) }
    }

    private fun sendSmsToApi(json: JSONObject, context: Context, hmacSHA256: String) {
        if (!isInternetAvailable(context)) {
            Log.d("SmsReceiver", "Тут1")
            enqueueFailedMessage(json, context)
            return
        }
        Log.d("SmsReceiver", "Тут2")
        val sharedPrefs = context.getSharedPreferences("AppSettings", MODE_PRIVATE)
        val savedUrl = sharedPrefs.getString("apiUrl", "https://api.moneyhoney.io/api/v1/payment/auto") ?: "https://api.moneyhoney.io/api/v1/payment/auto"
        val timeout = sharedPrefs.getString("timeout", "30000")?.toLongOrNull() ?: 60000L

        val apiUrl = savedUrl.trim()
        val signedUrl = "$apiUrl?sign=$hmacSHA256"

        val client = OkHttpClient.Builder()
            .callTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url(signedUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                CoroutineScope(Dispatchers.IO).launch {
                    val failIntent = Intent("com.example.hmautomatik.UPDATE_LOGS")
                    Log.d("SmsReceiver", "Ошибка в смс")
                    context.sendBroadcast(failIntent)

                }
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("SmsReceiver", "Тут3")
                CoroutineScope(Dispatchers.IO).launch {
                    val sender = json.optString("sender", "Неизвестный")
                    val message = json.optString("message", "Текст не извлечен")
                    val failIntent = Intent("com.example.hmautomatik.UPDATE_LOGS")
                    context.sendBroadcast(failIntent)
                    Log.d("SmsReceiver", response.toString())
                    if (response.code === 200 || response.code === 204 || response.code === 201 || response.code === 203 || response.code === 202 ) {
                        val logEntry = LogEntry(
                            sender = sender,
                            logText = message,
                            timestamp = System.currentTimeMillis()
                        )
                        AppDatabase.getDatabase(context).logEntryDao().insert(logEntry)
                    }
                    if (!response.isSuccessful) {
                        enqueueFailedMessage(json, context)
                        val db = AppDatabase.getDatabase(context)
                        context.sendBroadcast(failIntent)
                        val failedMessageDao = db.failedMessagesLogsDao()
                        failedMessageDao.insert(
                            FailedMessagesLogs(
                                sender = sender,
                                logText = message,
                                errorText = "${response.code} ${response.message}",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        })
    }

    private fun enqueueFailedMessage(json: JSONObject, context: Context) {
        Log.d("SmsReceiver", json.toString())
        CoroutineScope(Dispatchers.IO).launch {
            val sender = json.optString("sender", "Неизвестный")
            val db = AppDatabase.getDatabase(context)
            val failedMessageDao = db.failedMessageDao()
            failedMessageDao.insert(FailedMessage(sender = sender, messageJson = json.toString(), attempts = 0))
        }
    }

}
