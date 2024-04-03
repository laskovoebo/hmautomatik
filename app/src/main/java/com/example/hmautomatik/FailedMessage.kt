package com.example.hmautomatik
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FailedMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "sender") val sender: String,
    @ColumnInfo(name = "message_json") val messageJson: String,
    @ColumnInfo(name = "attempts") val attempts: Int
)

