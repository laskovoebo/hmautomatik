package com.example.hmautomatik

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Insert
    suspend fun insert(logEntry: LogEntry)

    @Query("SELECT * FROM LogEntry ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<LogEntry>>

    @Query("DELETE FROM LogEntry")
    suspend fun deleteAllLogs()
}