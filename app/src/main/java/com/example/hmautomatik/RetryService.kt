package com.example.hmautomatik

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class RetryService(private val context: Context) {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var retryLimit: Int = 5

    init {
        initRetryLimit()
    }

    fun start() {
        val runnable = Runnable { retryMessages() }
        scheduler.scheduleAtFixedRate(runnable, 0, 15, TimeUnit.SECONDS)
    }

    fun stop() {
        scheduler.shutdownNow()
    }

    private fun initRetryLimit() {
        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val storedRetryLimit = prefs.getString("retryCount", "5")?.toIntOrNull() ?: 5

        CoroutineScope(Dispatchers.IO).launch {
            InternetCheckService.internetAvailable.collect { isAvailable ->
                retryLimit = if (isAvailable) storedRetryLimit else 10000
            }
        }
    }

    private fun retryMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val failedMessageDao = db.failedMessageDao()
            val failedMessages = failedMessageDao.getAllFailedMessages()
            val sharedPrefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val encryptionKey = sharedPrefs.getString("encryptionKey", "123123123") ?: "123123123"

            failedMessages.forEach { failedMessage ->
                if (failedMessage.attempts < retryLimit) {
                    try {
                        val json = JSONObject(failedMessage.messageJson)
                        val hmacSHA256 = generateHmacSHA256(json.toString(), encryptionKey)
                        sendSmsToApi(json, hmacSHA256) { success, messageLog ->
                            CoroutineScope(Dispatchers.IO).launch {
                                if (success) {
                                    failedMessageDao.deleteFailedMessage(failedMessage)
                                } else {
                                    val updatedMessage = failedMessage.copy(attempts = failedMessage.attempts + 1)
                                    failedMessageDao.updateFailedMessage(updatedMessage)
                                }
                                sendLogToMainActivity(json, messageLog)
                            }
                        }
                    } catch (e: Exception) {
                        sendLogToMainActivity(JSONObject(failedMessage.messageJson), e.localizedMessage)
                    }
                } else {
                    CoroutineScope(Dispatchers.IO).launch {
                        failedMessageDao.deleteFailedMessage(failedMessage)
                        deleteSendLogToMainActivity(JSONObject(failedMessage.messageJson))
                    }
                }
            }

        }
    }

    private fun generateHmacSHA256(input: String, key: String): String {
        val sha256Hmac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        sha256Hmac.init(secretKey)
        val hash = sha256Hmac.doFinal(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { String.format("%02x", it) }
    }

    private fun sendSmsToApi(json: JSONObject, hmacSHA256: String, callback: (Boolean, String) -> Unit) {
        val apiUrl = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE).getString("apiUrl", "https://api.moneyhoney.io/api/v1/payment/auto") ?: "https://api.moneyhoney.io/api/v1/payment/auto"
        val signedUrl = "$apiUrl?sign=$hmacSHA256"

        val client = OkHttpClient()
        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder().url(signedUrl).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val logMessage = "Failed to send message: ${e.localizedMessage}"
                Log.e("com.example.hmautomatik.RetryService", logMessage)

                CoroutineScope(Dispatchers.IO).launch {
                    sendLogToMainActivity(json, e.localizedMessage)
                }

                callback(false, logMessage)
            }

            override fun onResponse(call: Call, response: Response) {
                var logMessage: String

                if (!response.isSuccessful) {
                    Log.d("SmsReceiver", "Не отправлено")
                    logMessage = "Failed to send message, server responded with ${response.code}"
                } else {
                    Log.d("SmsReceiver", "отправлено")
                    logMessage = "Message sent successfully: ${json.toString()}"
                    CoroutineScope(Dispatchers.IO).launch {
                        successSendLogToMainActivity(json)
                    }
                }

                Log.d("com.example.hmautomatik.RetryService", logMessage)
                callback(response.isSuccessful, logMessage)
            }
        })
    }

    private fun successSendLogToMainActivity(message: JSONObject, errorText: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val sender = message.optString("sender", "Неизвестный")
            val logMessage = message.optString("message", "Текст сообщения не найден")
            val retryLog = RetryLogs(
                sender = "Отправлено $sender",
                logText = logMessage,
                errorText = errorText,
                timestamp = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(context).retryLogsDao().insert(retryLog)
        }
    }

    private fun deleteSendLogToMainActivity(message: JSONObject, errorText: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val sender = message.optString("sender", "Неизвестный")
            val logMessage = message.optString("message", "Текст сообщения не найден")
            val retryLog = RetryLogs(
                sender = "Удалено $sender",
                logText = logMessage,
                errorText = errorText,
                timestamp = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(context).retryLogsDao().insert(retryLog)
        }
    }

    private fun sendLogToMainActivity(message: JSONObject, errorText: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val sender = message.optString("sender", "Неизвестный")
            val logMessage = message.optString("message", "Текст сообщения не найден")
            Log.d("errorText", errorText.toString())
            val retryLog = RetryLogs(
                sender = "Не отправлено $sender",
                logText = logMessage,
                errorText = errorText,
                timestamp = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(context).retryLogsDao().insert(retryLog)
        }
    }
}
