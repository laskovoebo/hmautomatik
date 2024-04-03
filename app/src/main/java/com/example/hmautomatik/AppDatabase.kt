package com.example.hmautomatik

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FailedMessage::class, LogEntry::class, FailedMessagesLogs::class, RetryLogs::class, PhoneNumber::class], version = 10)
abstract class AppDatabase : RoomDatabase() {
    abstract fun failedMessageDao(): FailedMessageDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun failedMessagesLogsDao(): FailedMessagesLogsDao
    abstract fun retryLogsDao(): RetryLogsDao
    abstract fun phoneNumberDao(): PhoneNumberDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS `FailedMessagesLogs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `log_text` TEXT NOT NULL, 
                `timestamp` INTEGER NOT NULL
            )
        """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS `RetryLogs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `log_text` TEXT NOT NULL, 
                `timestamp` INTEGER NOT NULL
            )
        """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `LogEntry` ADD COLUMN `sender` TEXT NOT NULL DEFAULT 'Неизвестный'")
                database.execSQL("ALTER TABLE `FailedMessagesLogs` ADD COLUMN `sender` TEXT NOT NULL DEFAULT 'Неизвестный'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `RetryLogs` ADD COLUMN `sender` TEXT NOT NULL DEFAULT 'Неизвестный'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE FailedMessage ADD COLUMN sender TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `FailedMessagesLogs` ADD COLUMN `error_text` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `phoneNumbers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `number` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `RetryLogs` ADD COLUMN `error_text` TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .build().also { instance = it }
            }
    }
}