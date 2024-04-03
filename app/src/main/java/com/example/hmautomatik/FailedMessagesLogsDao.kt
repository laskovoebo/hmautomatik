package com.example.hmautomatik

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FailedMessagesLogsDao {
    @Insert
    suspend fun insert(failedMessagesLogs: FailedMessagesLogs)

    @Query("SELECT * FROM FailedMessagesLogs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<FailedMessagesLogs>>

    @Query("DELETE FROM FailedMessagesLogs")
    suspend fun deleteAllLogs()
}