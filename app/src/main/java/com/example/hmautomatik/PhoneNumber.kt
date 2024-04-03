package com.example.hmautomatik

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "phoneNumbers")
data class PhoneNumber(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "number") val number: String
)
