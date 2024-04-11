package com.example.hmautomatik

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun collectAndLogSms(context: Context) {
    val smsUri = Uri.parse("content://sms/inbox")
    val projection = arrayOf("_id", "address", "date", "body")
    val lastSentHashes = getLastSentSmsHashes(context)

    context.contentResolver.query(smsUri, projection, null, null, "date DESC LIMIT 50")?.use { cursor ->
        while (cursor.moveToNext()) {
            val address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
            val date = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
            val body = cursor.getString(cursor.getColumnIndexOrThrow("body"))

            val smsHash = generateSmsHash(address, date, body)

            if (!lastSentHashes.contains(smsHash)) {
                val json = JSONObject().apply {
                    put("sender", address)
                    put("message", body)
                    put("time", date)
                }

                val encryptionKey = "123123123"
                val hmacSHA256 = generateHmacSHA256(json.toString(), encryptionKey)

                sendSmsToApi(json, context, hmacSHA256, smsHash, lastSentHashes)
            }
        }
    }
}

private fun generateSmsHash(address: String, date: Long, body: String): String {
    val input = "$address|$date|$body"
    return input.hashCode().toString()
}

private fun generateHmacSHA256(input: String, key: String): String {
    val sha256Hmac = Mac.getInstance("HmacSHA256")
    val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
    sha256Hmac.init(secretKey)

    val hash = sha256Hmac.doFinal(input.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { String.format("%02x", it) }
}

private fun sendSmsToApi(json: JSONObject, context: Context, hmacSHA256: String, smsHash: String, lastSentHashes: MutableSet<String>) {
    if (!isInternetAvailable(context)) {
        return
    }
    Log.d("smscollect", json.toString());
    val sharedPrefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    val savedUrl = "https://api-v2.moneyhoney.io/v2/team/automation"
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
            CoroutineScope(Dispatchers.IO).launch {
                val sender = json.optString("sender", "Неизвестный")
                val message = json.optString("message", "Текст не извлечен")
                val failIntent = Intent("com.example.hmautomatik.UPDATE_LOGS")
                context.sendBroadcast(failIntent)

                if (response.code === 200 || response.code === 204 || response.code === 201 || response.code === 203 || response.code === 202 ) {
                    lastSentHashes.add(smsHash)
                    saveLastSentSmsHashes(context, lastSentHashes)
                    val logEntry = LogEntry(
                        sender = sender,
                        logText = message,
                        timestamp = System.currentTimeMillis()
                    )
                    AppDatabase.getDatabase(context).logEntryDao().insert(logEntry)
                }
                if (!response.isSuccessful) {
                    Log.d("smscollectfailed", json.toString());
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

private fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkInfo = connectivityManager.activeNetworkInfo
    return networkInfo != null && networkInfo.isConnected
}

private fun saveLastSentSmsHashes(context: Context, hashes: Set<String>) {
    val sharedPrefs = context.getSharedPreferences("LastSentSmsHashes", Context.MODE_PRIVATE)
    with(sharedPrefs.edit()) {
        putStringSet("hashes", hashes)
        apply()
    }
}

private fun getLastSentSmsHashes(context: Context): MutableSet<String> {
    val sharedPrefs = context.getSharedPreferences("LastSentSmsHashes", Context.MODE_PRIVATE)
    val hashesSet = sharedPrefs.getStringSet("hashes", null) ?: return mutableSetOf()
    return hashesSet.toMutableSet()
}

