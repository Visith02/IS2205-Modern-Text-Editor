package com.is2205.moderntexteditor.versioning

import android.content.Context
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.is2205.moderntexteditor.database.DocumentDao
import com.is2205.moderntexteditor.database.DocumentEntity
import com.is2205.moderntexteditor.database.VersionDao
import com.is2205.moderntexteditor.database.VersionEntity
import java.io.File
import java.security.MessageDigest


data class CreateVersionResult(
    val versionNumber: Int,
    val isBaseVersion: Boolean,
    val patchSize: Int
)


class NoVersionChangesException :
    Exception("No changes since the previous version")


class VersionManager(

    private val context: Context,
    private val documentDao: DocumentDao,
    private val versionDao: VersionDao

) {


    // =====================================================
    // CREATE VERSION
    // =====================================================

    suspend fun createVersion(

        documentKey: String,
        fileName: String,
        fileUri: String?,
        currentText: String,
        description: String? = null

    ): CreateVersionResult {


        val existingDocument =
            documentDao.getDocument(
                documentKey
            )


        val now =
            System.currentTimeMillis()


        val document =

            if (existingDocument != null) {

                existingDocument.copy(
                    fileName = fileName,
                    fileUri = fileUri,
                    updatedAt = now
                )

            } else {

                DocumentEntity(

                    documentKey =
                        documentKey,

                    fileName =
                        fileName,

                    fileUri =
                        fileUri,

                    createdAt =
                        now,

                    updatedAt =
                        now
                )
            }


        documentDao.upsertDocument(
            document
        )


        val versions =
            versionDao.getVersions(
                documentKey
            )


        // =================================================
        // VERSION 1
        // STORE ONE FULL BASE SNAPSHOT
        // =================================================

        if (versions.isEmpty()) {


            val baseSnapshotPath =
                writeBaseSnapshot(

                    documentKey =
                        documentKey,

                    text =
                        currentText
                )


            documentDao.upsertDocument(

                document.copy(

                    baseSnapshotPath =
                        baseSnapshotPath,

                    updatedAt =
                        System.currentTimeMillis()
                )
            )


            versionDao.insertVersion(

                VersionEntity(

                    documentKey =
                        documentKey,

                    versionNumber =
                        1,

                    patchData =
                        "",

                    isBaseVersion =
                        true,

                    description =
                        description
                            ?: "Initial version"
                )
            )


            return CreateVersionResult(

                versionNumber =
                    1,

                isBaseVersion =
                    true,

                patchSize =
                    0
            )
        }


        // =================================================
        // VERSION 2+
        // STORE ONLY DELTA
        // =================================================

        val previousText =
            reconstructLatestVersion(
                documentKey
            )


        val normalizedPrevious =
            normalizeText(
                previousText
            )


        val normalizedCurrent =
            normalizeText(
                currentText
            )


        if (
            normalizedPrevious ==
            normalizedCurrent
        ) {

            throw NoVersionChangesException()
        }


        val oldLines =
            textToLines(
                normalizedPrevious
            )


        val newLines =
            textToLines(
                normalizedCurrent
            )


        val patch =
            DiffUtils.diff(
                oldLines,
                newLines
            )


        val unifiedDiff =
            UnifiedDiffUtils.generateUnifiedDiff(

                "$fileName-old",

                "$fileName-new",

                oldLines,

                patch,

                3
            )


        val patchText =
            unifiedDiff.joinToString(
                "\n"
            )


        val nextVersion =
            versionDao
                .getLatestVersionNumber(
                    documentKey
                ) + 1


        versionDao.insertVersion(

            VersionEntity(

                documentKey =
                    documentKey,

                versionNumber =
                    nextVersion,

                patchData =
                    patchText,

                isBaseVersion =
                    false,

                description =
                    description
                        ?: "Version $nextVersion"
            )
        )


        return CreateVersionResult(

            versionNumber =
                nextVersion,

            isBaseVersion =
                false,

            patchSize =
                patchText
                    .toByteArray(
                        Charsets.UTF_8
                    )
                    .size
        )
    }


    // =====================================================
    // RECONSTRUCT LATEST VERSION
    // =====================================================

    suspend fun reconstructLatestVersion(
        documentKey: String
    ): String {


        val latestVersion =
            versionDao
                .getLatestVersionNumber(
                    documentKey
                )


        if (latestVersion <= 0) {

            throw IllegalStateException(
                "No versions found"
            )
        }


        return reconstructVersion(

            documentKey =
                documentKey,

            targetVersionNumber =
                latestVersion
        )
    }


    // =====================================================
    // RECONSTRUCT ANY VERSION
    // =====================================================

    suspend fun reconstructVersion(

        documentKey: String,

        targetVersionNumber: Int

    ): String {


        if (targetVersionNumber < 1) {

            throw IllegalArgumentException(
                "Version number must be at least 1"
            )
        }


        val document =
            documentDao
                .getDocument(
                    documentKey
                )
                ?: throw IllegalStateException(
                    "Document metadata not found"
                )


        val baseSnapshotPath =
            document.baseSnapshotPath
                ?: throw IllegalStateException(
                    "Base snapshot not found"
                )


        val baseFile =
            File(
                baseSnapshotPath
            )


        if (!baseFile.exists()) {

            throw IllegalStateException(
                "Base snapshot file is missing"
            )
        }


        var currentLines =
            textToLines(

                normalizeText(

                    baseFile.readText(
                        Charsets.UTF_8
                    )
                )
            )


        val versions =
            versionDao
                .getVersions(
                    documentKey
                )
                .sortedBy {
                    it.versionNumber
                }


        for (version in versions) {


            if (
                version.versionNumber <= 1
            ) {

                continue
            }


            if (
                version.versionNumber >
                targetVersionNumber
            ) {

                break
            }


            if (
                version.patchData.isBlank()
            ) {

                continue
            }


            val patchLines =
                version.patchData
                    .split("\n")


            val parsedPatch =
                UnifiedDiffUtils
                    .parseUnifiedDiff(
                        patchLines
                    )


            currentLines =
                DiffUtils.patch(
                    currentLines,
                    parsedPatch
                )
        }


        return currentLines
            .joinToString(
                "\n"
            )
    }


    // =====================================================
    // GET NUMBER OF VERSIONS
    // =====================================================

    suspend fun getVersionCount(
        documentKey: String
    ): Int {

        return versionDao
            .getLatestVersionNumber(
                documentKey
            )
    }


    // =====================================================
    // WRITE BASE SNAPSHOT
    // =====================================================

    private fun writeBaseSnapshot(

        documentKey: String,

        text: String

    ): String {


        val directory =
            File(

                context.filesDir,

                "version_bases"
            )


        if (!directory.exists()) {

            directory.mkdirs()
        }


        val safeName =
            sha256(
                documentKey
            )


        val file =
            File(

                directory,

                "$safeName.base"
            )


        file.writeText(

            normalizeText(
                text
            ),

            Charsets.UTF_8
        )


        return file.absolutePath
    }


    // =====================================================
    // NORMALIZE LINE ENDINGS
    // =====================================================

    private fun normalizeText(
        text: String
    ): String {

        return text
            .replace(
                "\r\n",
                "\n"
            )
            .replace(
                "\r",
                "\n"
            )
    }


    // =====================================================
    // TEXT TO LINES
    // =====================================================

    private fun textToLines(
        text: String
    ): List<String> {

        return text.split(
            "\n"
        )
    }


    // =====================================================
    // HASH DOCUMENT KEY FOR SAFE FILE NAME
    // =====================================================

    private fun sha256(
        value: String
    ): String {


        val bytes =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )
                .digest(
                    value.toByteArray(
                        Charsets.UTF_8
                    )
                )


        return bytes.joinToString(
            ""
        ) { byte ->

            "%02x".format(
                byte.toInt() and 0xff
            )
        }
    }
}