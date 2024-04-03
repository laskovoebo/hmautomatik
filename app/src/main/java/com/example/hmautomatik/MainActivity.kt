package com.example.hmautomatik

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.ContentAlpha
import com.example.hmautomatik.ui.theme.HMAutomatikTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var logEntryDao: LogEntryDao

    companion object {
        private const val SMS_PERMISSION_CODE = 102
    }

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var retryService: RetryService

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(this)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag")
        wakeLock.acquire()
        logEntryDao = database.logEntryDao()
        val failedMessageLogsDao = database.failedMessagesLogsDao()
        val retryLogsDao = database.retryLogsDao()
        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        retryService = RetryService(this)
        retryService.start()
        startService(Intent(this, InternetCheckService::class.java))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
        }

        setContent {
            HMAutomatikTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        logEntryDao.getAllLogsFlow(),
                        failedMessageLogsDao.getAllLogsFlow(),
                        retryLogsDao.getAllLogsFlow()
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            logEntryDao.deleteAllLogs()
                            failedMessageLogsDao.deleteAllLogs()
                            retryLogsDao.deleteAllLogs()
                        }
                    }
                }
            }
        }



        val serviceIntent = Intent(this, MyForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        checkAndRequestPermissions()
    }

    @Composable
    fun MainScreen(
        logEntriesFlow: Flow<List<LogEntry>>,
        failedMessagesFlow: Flow<List<FailedMessagesLogs>>,
        retryLogsFlow: Flow<List<RetryLogs>>,
        onClearLogs: () -> Unit
    ) {
        val defaultEncryptionKey = "123123123"
        val defaultApiUrl = "https://api.moneyhoney.io/api/v1/payment/auto"
        var encryptionKey by remember { mutableStateOf(sharedPreferences.getString("encryptionKey", defaultEncryptionKey) ?: defaultEncryptionKey) }
        var url by remember { mutableStateOf(sharedPreferences.getString("apiUrl", defaultApiUrl) ?: defaultApiUrl) }
        val savedPhoneNumberList = sharedPreferences.getString("phoneNumberList", "") ?: ""
        val phoneNumberList = remember { mutableStateListOf(*savedPhoneNumberList.split(",").filter { it.isNotEmpty() }.toTypedArray()) }
        var newPhoneNumber by remember { mutableStateOf("") }
        var retryCount by remember { mutableStateOf(sharedPreferences.getString("retryCount", "") ?: "") }
        var selectedTabIndex by remember { mutableStateOf(0) }
        val tabTitles = listOf("Success", "Failed", "Retry")
        val isInternetAvailable by InternetCheckService.internetAvailable.collectAsState()
        var offlineDurationText by remember { mutableStateOf("") }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(key1 = isInternetAvailable) {
            if (!isInternetAvailable) {
                val offlineStartTime = System.currentTimeMillis()
                coroutineScope.launch {
                    while (!isInternetAvailable) {
                        val durationSeconds = (System.currentTimeMillis() - offlineStartTime) / 1000
                        offlineDurationText = "$durationSeconds seconds"
                        delay(1000)
                    }
                }
            } else {
                offlineDurationText = ""
            }
        }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = if (isInternetAvailable) "Online" else "Offline", color = if (isInternetAvailable) Color.Green else Color.Red)
            if (!isInternetAvailable && offlineDurationText.isNotEmpty()) {
                Text(text = offlineDurationText, color = Color.Red)
            }
            OutlinedTextField(
                value = encryptionKey,
                onValueChange = { newValue ->
                    encryptionKey = newValue
                    sharedPreferences.edit().putString("encryptionKey", newValue.ifEmpty { defaultEncryptionKey }).apply()
                },
                label = { Text("Ключ шифрования") }
            )

            OutlinedTextField(
                value = url,
                onValueChange = { newUrl ->
                    url = newUrl
                    val toSave = if (newUrl.isBlank()) defaultApiUrl else newUrl
                    sharedPreferences.edit().putString("apiUrl", toSave).apply()
                },
                label = { Text("URL для отправки сообщения") }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newPhoneNumber,
                    onValueChange = { newPhoneNumber = it },
                    label = { Text("Добавить номер телефона") }
                )
                IconButton(onClick = {
                    if (newPhoneNumber.isNotBlank()) {
                        phoneNumberList.add(newPhoneNumber)
                        updateSharedPreferences(phoneNumberList)
                        newPhoneNumber = ""
                        Log.d("MainActivity", "Номер добавлен: $newPhoneNumber")
                        Log.d("MainActivity", "Сохраненные номера: ${phoneNumberList.joinToString()}")
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить")
                }
            }

            phoneNumberList.forEach { phoneNumber ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = phoneNumber,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFFC100))
                            .padding(8.dp)
                    )
                    IconButton(onClick = {
                        val updatedList = phoneNumberList.filter { it != phoneNumber }
                        phoneNumberList.clear()
                        phoneNumberList.addAll(updatedList)
                        updateSharedPreferences(phoneNumberList)
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                    }
                }
            }

            OutlinedTextField(
                value = retryCount,
                onValueChange = { newRetryCount ->
                    retryCount = newRetryCount
                    sharedPreferences.edit().putString("retryCount", newRetryCount).apply()
                },
                label = { Text("Количество попыток (retry)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Button(
                onClick = onClearLogs,
                modifier = Modifier
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC100), contentColor = Color.Black)
            ) {
                Text("Очистить логи")
            }

            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.background(Color.Transparent),
                contentColor = Color.Black,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFFFFC100)
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        selectedContentColor = Color(0xeee3ac00),
                        unselectedContentColor = Color.Black.copy(alpha = ContentAlpha.medium),
                        text = { Text(title) },
                    )
                }
            }

            val unifiedLogEntriesFlow: Flow<List<UnifiedLogEntry>> = when (selectedTabIndex) {
                0 -> logEntriesFlow.map { entries ->
                    entries.map { UnifiedLogEntry(it.id, it.sender, it.logText, it.timestamp) }
                }
                1 -> failedMessagesFlow.map { entries ->
                    entries.map {
                        UnifiedLogEntry(
                            it.id,
                            it.sender,
                            it.logText,
                            it.timestamp,
                            it.errorText
                        )
                    }
                }
                2 -> retryLogsFlow.map { entries ->
                    entries.map { UnifiedLogEntry(it.id, it.sender, it.logText, it.timestamp, it.errorText) }
                }
                else -> flowOf(emptyList())
            }


            val unifiedLogEntries by unifiedLogEntriesFlow.collectAsState(initial = emptyList())


            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                items(unifiedLogEntries) { log ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .background(Color(0xFFFFC100))
                                .padding(8.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 5.dp)
                            ) {
                                Text(
                                    text = log.sender,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(top = 5.dp, start = 5.dp)
                                )
                                Row(
                                    modifier = Modifier.padding(top = 5.dp, end = 5.dp)
                                ) {
                                    Text(
                                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp)),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                    )
                                }
                            }

                            Text(
                                text = log.logText,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                        modifier = Modifier.padding(start = 5.dp)
                            )

                            log.errorText?.let {
                                Text(
                                    text = it,
                                    color = Color.Red,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

        }
    }


    private fun updateSharedPreferences(phoneNumbers: List<String>) {
        val sharedPrefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putString("phoneNumberList", phoneNumbers.joinToString(","))
            apply()
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
                SMS_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "SMS Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
