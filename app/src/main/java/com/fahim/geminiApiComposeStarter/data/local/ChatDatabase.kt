package com.fahim.geminiApiComposeStarter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChatSessionEntity::class, ChatMessageEntity::class], version = 4, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN role TEXT NOT NULL DEFAULT 'USER'")
                db.execSQL("UPDATE chat_messages SET role = CASE WHEN isFromUser = 1 THEN 'USER' ELSE 'MODEL' END")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN contextStatus TEXT NOT NULL DEFAULT 'INCLUDED'")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN requestStatus TEXT NOT NULL DEFAULT 'COMPLETE'")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN replyToId INTEGER")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN variantGroupId INTEGER")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isSelectedVariant INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN contextMessageCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN protectedUsedCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN excludedAtRequestCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN trimmedAtRequestCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN customInstructionsUsed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN wasVoicePrompt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS chat_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, securityLevel TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("INSERT INTO chat_sessions (id, title, securityLevel, createdAt, updatedAt) VALUES (1, 'General chat', 'PRIVATE', strftime('%s','now') * 1000, strftime('%s','now') * 1000)")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN chatId INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
