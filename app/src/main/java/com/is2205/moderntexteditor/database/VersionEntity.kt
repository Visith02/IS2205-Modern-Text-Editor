package com.is2205.moderntexteditor.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "versions",

    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentKey"],
            childColumns = ["documentKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],

    indices = [
        Index(value = ["documentKey"]),
        Index(
            value = ["documentKey", "versionNumber"],
            unique = true
        )
    ]
)
data class VersionEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val documentKey: String,

    val versionNumber: Int,

    // Later this stores the delta / patch
    val patchData: String,

    val isBaseVersion: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),

    val description: String? = null
)