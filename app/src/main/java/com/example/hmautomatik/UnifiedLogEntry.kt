package com.example.hmautomatik

data class UnifiedLogEntry(
    val id: Int,
    val sender: String,
    val logText: String,
    val timestamp: Long,
    val errorText: String? = null
)
