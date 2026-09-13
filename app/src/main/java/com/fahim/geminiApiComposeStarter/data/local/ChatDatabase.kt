package com.fahim.geminiApiComposeStarter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChatMessageEntity::class], version = 2, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao

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
    }
}
