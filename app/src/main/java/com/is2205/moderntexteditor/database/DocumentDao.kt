package com.is2205.moderntexteditor.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Upsert
    suspend fun upsertDocument(
        document: DocumentEntity
    )

    @Query(
        """
        SELECT *
        FROM documents
        WHERE documentKey = :documentKey
        LIMIT 1
        """
    )
    suspend fun getDocument(
        documentKey: String
    ): DocumentEntity?

    @Query(
        """
        SELECT *
        FROM documents
        ORDER BY updatedAt DESC
        """
    )
    fun observeDocuments():
            Flow<List<DocumentEntity>>

    @Query(
        """
        UPDATE documents
        SET isReadOnly = :readOnly,
            updatedAt = :updatedAt
        WHERE documentKey = :documentKey
        """
    )
    suspend fun updateReadOnly(
        documentKey: String,
        readOnly: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE documents
        SET baseSnapshotPath = :path,
            updatedAt = :updatedAt
        WHERE documentKey = :documentKey
        """
    )
    suspend fun updateBaseSnapshot(
        documentKey: String,
        path: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        DELETE FROM documents
        WHERE documentKey = :documentKey
        """
    )
    suspend fun deleteDocument(
        documentKey: String
    )
}