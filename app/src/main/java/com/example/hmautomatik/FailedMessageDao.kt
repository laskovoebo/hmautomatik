package com.example.hmautomatik

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface FailedMessageDao {
    @Insert
    suspend fun insert(failedMessage: FailedMessage)

    @Query("SELECT * FROM FailedMessage")
    suspend fun getAllFailedMessages(): List<FailedMessage>

    @Update
    suspend fun updateFailedMessage(failedMessage: FailedMessage)

    @Delete
    suspend fun deleteFailedMessage(failedMessage: FailedMessage)
}
