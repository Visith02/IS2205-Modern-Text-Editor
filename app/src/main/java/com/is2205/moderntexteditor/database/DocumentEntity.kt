package com.is2205.moderntexteditor.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(

    @PrimaryKey
    val documentKey: String,

    val fileName: String,

    val fileUri: String?,

    val baseSnapshotPath: String? = null,

    val isReadOnly: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)