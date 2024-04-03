package com.example.hmautomatik

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhoneNumberDao {
    @Query("SELECT * FROM phoneNumbers")
    fun getAllPhoneNumbers(): Flow<List<PhoneNumber>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoneNumber(phoneNumber: PhoneNumber)

    @Query("DELETE FROM phoneNumbers WHERE number = :number")
    suspend fun deletePhoneNumber(number: String)

    @Query("DELETE FROM phoneNumbers")
    suspend fun deleteAllPhoneNumbers()
}