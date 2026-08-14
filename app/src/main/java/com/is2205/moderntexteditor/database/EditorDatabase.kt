package com.is2205.moderntexteditor.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        VersionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EditorDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    abstract fun versionDao(): VersionDao

    companion object {

        @Volatile
        private var INSTANCE: EditorDatabase? = null

        fun getDatabase(
            context: Context
        ): EditorDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        EditorDatabase::class.java,
                        "modern_text_editor_database"
                    ).build()

                INSTANCE = instance

                instance
            }
        }
    }
}