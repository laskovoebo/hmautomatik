package com.example.hmautomatik

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "sender") val sender: String,
    @ColumnInfo(name = "log_text") val logText: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)

