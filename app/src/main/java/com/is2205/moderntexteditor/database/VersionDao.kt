package com.is2205.moderntexteditor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VersionDao {

    @Insert(
        onConflict = OnConflictStrategy.ABORT
    )
    suspend fun insertVersion(
        version: VersionEntity
    ): Long

    @Query(
        """
        SELECT *
        FROM versions
        WHERE documentKey = :documentKey
        ORDER BY versionNumber DESC
        """
    )
    fun observeVersions(
        documentKey: String
    ): Flow<List<VersionEntity>>

    @Query(
        """
        SELECT *
        FROM versions
        WHERE documentKey = :documentKey
        ORDER BY versionNumber ASC
        """
    )
    suspend fun getVersions(
        documentKey: String
    ): List<VersionEntity>

    @Query(
        """
        SELECT *
        FROM versions
        WHERE documentKey = :documentKey
        AND versionNumber = :versionNumber
        LIMIT 1
        """
    )
    suspend fun getVersion(
        documentKey: String,
        versionNumber: Int
    ): VersionEntity?

    @Query(
        """
        SELECT *
        FROM versions
        WHERE documentKey = :documentKey
        ORDER BY versionNumber DESC
        LIMIT 1
        """
    )
    suspend fun getLatestVersion(
        documentKey: String
    ): VersionEntity?

    @Query(
        """
        SELECT COALESCE(MAX(versionNumber), 0)
        FROM versions
        WHERE documentKey = :documentKey
        """
    )
    suspend fun getLatestVersionNumber(
        documentKey: String
    ): Int

    @Query(
        """
        DELETE FROM versions
        WHERE documentKey = :documentKey
        AND versionNumber > :versionNumber
        """
    )
    suspend fun deleteVersionsAfter(
        documentKey: String,
        versionNumber: Int
    )

    @Query(
        """
        DELETE FROM versions
        WHERE documentKey = :documentKey
        """
    )
    suspend fun deleteVersions(
        documentKey: String
    )
}