package com.example.hmautomatik

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RetryLogsDao {
    @Insert
    suspend fun insert(retryLogs: RetryLogs)

    @Query("SELECT * FROM RetryLogs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<RetryLogs>>

    @Query("DELETE FROM RetryLogs")
    suspend fun deleteAllLogs()
}