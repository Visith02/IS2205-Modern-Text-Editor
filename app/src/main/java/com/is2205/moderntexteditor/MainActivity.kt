package com.is2205.moderntexteditor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.is2205.moderntexteditor.database.DocumentDao
import com.is2205.moderntexteditor.database.DocumentEntity
import com.is2205.moderntexteditor.database.EditorDatabase
import com.is2205.moderntexteditor.database.VersionEntity
import com.is2205.moderntexteditor.ui.theme.ModernTextEditorTheme
import com.is2205.moderntexteditor.versioning.NoVersionChangesException
import com.is2205.moderntexteditor.versioning.VersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


// =====================================================
// RECENT FILE MODEL
// =====================================================

data class RecentFile(
    val name: String,
    val uri: String
)


// =====================================================
// CRASH RECOVERY MODEL
// =====================================================

data class RecoveryDraft(
    val fileName: String,
    val text: String,
    val savedAt: Long
)


// =====================================================
// MAIN ACTIVITY
// =====================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContent {

            ModernTextEditorTheme {

                EditorScreen()
            }
        }
    }
}


// =====================================================
// MAIN EDITOR SCREEN
// =====================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()


    // =================================================
    // DATABASE
    // =================================================

    val database =
        remember {

            EditorDatabase.getDatabase(
                context
            )
        }

    val documentDao =
        remember {

            database.documentDao()
        }

    val versionDao =
        remember {

            database.versionDao()
        }

    val versionManager =
        remember {

            VersionManager(
                context = context,
                documentDao = documentDao,
                versionDao = versionDao
            )
        }


    // =================================================
    // BASIC EDITOR STATE
    // =================================================

    var fileName by remember {
        mutableStateOf("untitled.txt")
    }

    var editorText by remember {
        mutableStateOf("")
    }

    var statusMessage by remember {
        mutableStateOf("Ready")
    }

    var currentFileUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var documentKey by remember {

        mutableStateOf(
            "draft:${UUID.randomUUID()}"
        )
    }


    // =================================================
    // VERSION CONTROL
    // =================================================

    var currentVersionNumber by remember {
        mutableStateOf(0)
    }

    var isCreatingVersion by remember {
        mutableStateOf(false)
    }


    // =================================================
    // VERSION HISTORY
    // =================================================

    var showVersionHistoryDialog by remember {
        mutableStateOf(false)
    }

    var versionHistory by remember {
        mutableStateOf<List<VersionEntity>>(
            emptyList()
        )
    }

    var isLoadingVersionHistory by remember {
        mutableStateOf(false)
    }

    var showVersionPreviewDialog by remember {
        mutableStateOf(false)
    }

    var previewVersionNumber by remember {
        mutableStateOf(0)
    }

    var previewVersionText by remember {
        mutableStateOf("")
    }


    // =================================================
    // DIFF VIEWER
    // =================================================

    var showDiffDialog by remember {
        mutableStateOf(false)
    }

    var isLoadingDiff by remember {
        mutableStateOf(false)
    }

    var diffFromLabel by remember {
        mutableStateOf("")
    }

    var diffToLabel by remember {
        mutableStateOf("")
    }

    var diffLines by remember {
        mutableStateOf<List<String>>(
            emptyList()
        )
    }


    // =================================================
    // ROLLBACK - NEW M3.8
    // =================================================

    var showRollbackDialog by remember {
        mutableStateOf(false)
    }

    var rollbackTargetVersion by remember {
        mutableStateOf<VersionEntity?>(null)
    }

    var rollbackTargetText by remember {
        mutableStateOf("")
    }

    var isPreparingRollback by remember {
        mutableStateOf(false)
    }


    // =================================================
    // READ ONLY
    // =================================================

    var isReadOnly by remember {
        mutableStateOf(false)
    }


    // =================================================
    // CRASH RECOVERY
    // =================================================

    var isDirty by remember {
        mutableStateOf(false)
    }

    var recoveryDraft by remember {

        mutableStateOf(
            loadRecoveryDraft(
                context
            )
        )
    }

    var showRecoveryDialog by remember {

        mutableStateOf(
            recoveryDraft != null
        )
    }


    // =================================================
    // MENU STATES
    // =================================================

    var showMoreMenu by remember {
        mutableStateOf(false)
    }

    var showRecentDialog by remember {
        mutableStateOf(false)
    }

    var recentFiles by remember {

        mutableStateOf(
            loadRecentFiles(
                context
            )
        )
    }


    // =================================================
    // SEARCH AND REPLACE
    // =================================================

    var showSearchDialog by remember {
        mutableStateOf(false)
    }

    var searchText by remember {
        mutableStateOf("")
    }

    var replaceText by remember {
        mutableStateOf("")
    }

    var searchResultMessage by remember {
        mutableStateOf("")
    }


    // =================================================
    // WORD WRAP
    // =================================================

    var wordWrapEnabled by remember {
        mutableStateOf(true)
    }


    // =================================================
    // UNDO / REDO
    // =================================================

    val undoStack =
        remember {
            mutableListOf<String>()
        }

    val redoStack =
        remember {
            mutableListOf<String>()
        }


    // =================================================
    // FILE INFORMATION
    // =================================================

    val fileType =
        getFileType(
            fileName
        )

    val lineCount =

        if (editorText.isEmpty()) {

            1

        } else {

            editorText.count {
                it == '\n'
            } + 1
        }


    // =================================================
    // UPDATE EDITOR TEXT
    // =================================================

    fun updateEditorText(
        newText: String
    ) {

        if (isReadOnly) {

            statusMessage =
                "Read-only mode: editing disabled"

            return
        }

        if (newText != editorText) {

            undoStack.add(
                editorText
            )

            if (undoStack.size > 100) {

                undoStack.removeAt(0)
            }

            redoStack.clear()

            editorText =
                newText

            isDirty =
                true

            statusMessage =
                "Editing"
        }
    }


    // =================================================
    // CRASH RECOVERY AUTO SAVE
    // =================================================

    val latestText by
    rememberUpdatedState(
        editorText
    )

    val latestFileName by
    rememberUpdatedState(
        fileName
    )

    val latestDirty by
    rememberUpdatedState(
        isDirty
    )

    LaunchedEffect(Unit) {

        while (true) {

            delay(
                10_000
            )

            if (latestDirty) {

                if (latestText.isNotEmpty()) {

                    saveRecoveryDraft(
                        context = context,
                        fileName = latestFileName,
                        text = latestText
                    )

                    statusMessage =
                        "Recovery auto-saved"

                } else {

                    clearRecoveryDraft(
                        context
                    )
                }
            }
        }
    }


    // =================================================
    // OPEN FILE
    // =================================================

    val openFileLauncher =

        rememberLauncherForActivityResult(

            contract =
                ActivityResultContracts.OpenDocument()

        ) { uri ->

            if (uri != null) {

                try {

                    try {

                        context
                            .contentResolver
                            .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )

                    } catch (_: Exception) {
                    }

                    val text =
                        readTextFromFile(
                            context,
                            uri
                        )

                    if (text != null) {

                        val openedName =
                            getFileName(
                                context,
                                uri
                            ) ?: "unknown.txt"

                        val openedKey =
                            uri.toString()

                        editorText =
                            text

                        fileName =
                            openedName

                        currentFileUri =
                            uri

                        documentKey =
                            openedKey

                        currentVersionNumber =
                            0

                        versionHistory =
                            emptyList()

                        diffLines =
                            emptyList()

                        rollbackTargetVersion =
                            null

                        rollbackTargetText =
                            ""

                        undoStack.clear()
                        redoStack.clear()

                        isDirty =
                            false

                        isReadOnly =
                            false

                        clearRecoveryDraft(
                            context
                        )

                        recoveryDraft =
                            null

                        addRecentFile(
                            context,
                            openedName,
                            uri
                        )

                        recentFiles =
                            loadRecentFiles(
                                context
                            )

                        coroutineScope.launch {

                            val document =

                                getOrCreateDocument(
                                    documentDao = documentDao,
                                    documentKey = openedKey,
                                    fileName = openedName,
                                    fileUri = uri.toString()
                                )

                            isReadOnly =
                                document.isReadOnly

                            currentVersionNumber =

                                versionDao
                                    .getLatestVersionNumber(
                                        openedKey
                                    )
                        }

                        statusMessage =
                            "File opened successfully"
                    }

                } catch (
                    e: Exception
                ) {

                    statusMessage =
                        "Unable to open file"
                }
            }
        }


    // =================================================
    // SAVE AS
    // =================================================

    val saveAsLauncher =

        rememberLauncherForActivityResult(

            contract =
                ActivityResultContracts
                    .CreateDocument("*/*")

        ) { uri ->

            if (uri != null) {

                try {

                    saveTextToFile(
                        context,
                        uri,
                        editorText
                    )

                    val savedName =
                        getFileName(
                            context,
                            uri
                        ) ?: fileName

                    val savedKey =
                        uri.toString()

                    currentFileUri =
                        uri

                    fileName =
                        savedName

                    documentKey =
                        savedKey

                    currentVersionNumber =
                        0

                    versionHistory =
                        emptyList()

                    diffLines =
                        emptyList()

                    rollbackTargetVersion =
                        null

                    rollbackTargetText =
                        ""

                    addRecentFile(
                        context,
                        savedName,
                        uri
                    )

                    recentFiles =
                        loadRecentFiles(
                            context
                        )

                    isDirty =
                        false

                    clearRecoveryDraft(
                        context
                    )

                    recoveryDraft =
                        null

                    coroutineScope.launch {

                        saveDocumentMetadata(
                            documentDao = documentDao,
                            documentKey = savedKey,
                            fileName = savedName,
                            fileUri = uri.toString(),
                            isReadOnly = isReadOnly
                        )

                        currentVersionNumber =

                            versionDao
                                .getLatestVersionNumber(
                                    savedKey
                                )
                    }

                    statusMessage =
                        "File saved successfully"

                } catch (
                    e: Exception
                ) {

                    statusMessage =
                        "Unable to save file"
                }
            }
        }


    // =================================================
    // SAVE CURRENT FILE
    // =================================================

    fun saveCurrentFile() {

        if (currentFileUri != null) {

            try {

                saveTextToFile(
                    context,
                    currentFileUri!!,
                    editorText
                )

                addRecentFile(
                    context,
                    fileName,
                    currentFileUri!!
                )

                recentFiles =
                    loadRecentFiles(
                        context
                    )

                isDirty =
                    false

                clearRecoveryDraft(
                    context
                )

                recoveryDraft =
                    null

                coroutineScope.launch {

                    saveDocumentMetadata(
                        documentDao = documentDao,
                        documentKey = documentKey,
                        fileName = fileName,
                        fileUri =
                            currentFileUri
                                ?.toString(),
                        isReadOnly = isReadOnly
                    )
                }

                statusMessage =
                    "File saved"

            } catch (
                e: Exception
            ) {

                statusMessage =
                    "Unable to save file"
            }

        } else {

            saveAsLauncher.launch(
                fileName
            )
        }
    }


    // =================================================
    // TOGGLE READ ONLY
    // =================================================

    fun toggleReadOnly() {

        val newValue =
            !isReadOnly

        isReadOnly =
            newValue

        coroutineScope.launch {

            saveDocumentMetadata(
                documentDao = documentDao,
                documentKey = documentKey,
                fileName = fileName,
                fileUri =
                    currentFileUri
                        ?.toString(),
                isReadOnly = newValue
            )
        }

        statusMessage =

            if (newValue) {

                "Read-only mode ON"

            } else {

                "Read-only mode OFF"
            }
    }


    // =================================================
    // CREATE VERSION
    // =================================================

    fun createVersion() {

        if (currentFileUri == null) {

            statusMessage =
                "Save the file before creating a version"

            return
        }

        if (isCreatingVersion) {

            return
        }

        isCreatingVersion =
            true

        coroutineScope.launch {

            try {

                val result =

                    withContext(
                        Dispatchers.IO
                    ) {

                        versionManager.createVersion(
                            documentKey = documentKey,
                            fileName = fileName,
                            fileUri =
                                currentFileUri
                                    ?.toString(),
                            currentText = editorText
                        )
                    }

                currentVersionNumber =
                    result.versionNumber

                statusMessage =

                    if (result.isBaseVersion) {

                        "Version 1 created - base snapshot"

                    } else {

                        "Version ${result.versionNumber} created - delta ${result.patchSize} bytes"
                    }

            } catch (
                _: NoVersionChangesException
            ) {

                statusMessage =
                    "No changes since the previous version"

            } catch (
                e: Exception
            ) {

                statusMessage =
                    "Unable to create version: ${e.message}"

            } finally {

                isCreatingVersion =
                    false
            }
        }
    }


    // =================================================
    // OPEN VERSION HISTORY
    // =================================================

    fun openVersionHistory() {

        if (currentFileUri == null) {

            statusMessage =
                "Save the file before viewing history"

            return
        }

        isLoadingVersionHistory =
            true

        coroutineScope.launch {

            try {

                val versions =

                    withContext(
                        Dispatchers.IO
                    ) {

                        versionDao
                            .getVersions(
                                documentKey
                            )
                            .sortedByDescending {

                                it.versionNumber
                            }
                    }

                versionHistory =
                    versions

                showVersionHistoryDialog =
                    true

                statusMessage =

                    if (versions.isEmpty()) {

                        "No versions created yet"

                    } else {

                        "${versions.size} version(s) found"
                    }

            } catch (
                e: Exception
            ) {

                statusMessage =
                    "Unable to load version history: ${e.message}"

            } finally {

                isLoadingVersionHistory =
                    false
            }
        }
    }


    // =================================================
    // PREVIEW HISTORICAL VERSION
    // =================================================

    fun previewVersion(
        version: VersionEntity
    ) {

        statusMessage =
            "Loading Version ${version.versionNumber}..."

        coroutineScope.launch {

            try {

                val historicalText =

                    withContext(
                        Dispatchers.IO
                    ) {

                        versionManager
                            .reconstructVersion(
                                documentKey = documentKey,
                                targetVersionNumber =
                                    version.versionNumber
                            )
                    }

                previewVersionNumber =
                    version.versionNumber

                previewVersionText =
                    historicalText

                showVersionHistoryDialog =
                    false

                showVersionPreviewDialog =
                    true

                statusMessage =
                    "Viewing Version ${version.versionNumber}"

            } catch (
                e: Exception
            ) {

                statusMessage =
                    "Unable to load version: ${e.message}"
            }
        }
    }


    // =================================================
    // OPEN DIFF VIEWER
    // =================================================

    fun openDiffForVersion(
        version: VersionEntity
    ) {

        if (isLoadingDiff) {

            return
        }

        isLoadingDiff =
            true

        statusMessage =
            "Preparing diff for Version ${version.versionNumber}..."

        coroutineScope.launch {

            try {

                val result =

                    withContext(
                        Dispatchers.IO
                    ) {

                        val newText =

                            versionManager
                                .reconstructVersion(
                                    documentKey =
                                        documentKey,
                                    targetVersionNumber =
                                        version.versionNumber
                                )


                        val oldText =

                            if (
                                version.versionNumber > 1
                            ) {

                                versionManager
                                    .reconstructVersion(
                                        documentKey =
                                            documentKey,
                                        targetVersionNumber =
                                            version.versionNumber - 1
                                    )

                            } else {

                                ""
                            }


                        val fromLabel =

                            if (
                                version.versionNumber > 1
                            ) {

                                "Version ${version.versionNumber - 1}"

                            } else {

                                "Empty document"
                            }


                        val toLabel =
                            "Version ${version.versionNumber}"


                        val lines =
                            buildUnifiedDiff(
                                oldText = oldText,
                                newText = newText,
                                oldLabel = fromLabel,
                                newLabel = toLabel
                            )


                        Triple(
                            fromLabel,
                            toLabel,
                            lines
                        )
                    }


                diffFromLabel =
                    result.first

                diffToLabel =
                    result.second

                diffLines =
                    result.third

                showVersionHistoryDialog =
                    false

                showVersionPreviewDialog =
                    false

                showDiffDialog =
                    true

                statusMessage =
                    "Diff: $diffFromLabel → $diffToLabel"

            } catch (
                e: Exception
            ) {

                statusMessage =
                    "Unable to create diff: ${e.message}"

            } finally {

                isLoadingDiff =
                    false
            }
        }
    }


    // =================================================
    // PREPARE ROLLBACK - NEW M3.8
    // =================================================

    fun prepareRollback(
        version: VersionEntity
    ) {

        if (isReadOnly) {

            statusMessage =
                "Read-only mode: rollback disabled"

            return
        }

        if (isPreparingRollback) {

            return
        }

        isPreparingRollback =
            true

        statusMessage =
            "Preparing rollback to Version ${version.versionNumber}..."

        coroutineScope.launch {

            try {

                val historicalText =

                    withContext(
                        Dispatchers.IO
                    ) {

                        versionManager
                            .reconstructVersion(
                                documentKey =
                                    documentKey,
                                targetVersionNumber =
                                    version.versionNumber
                            )
                    }

                rollbackTargetVersion =
                    version

                rollbackTargetText =
                    historicalText

                showVersionHistoryDialog =
                    false

                showVersionPreviewDialog =
                    false

                showDiffDialog =
                    false

                showRollbackDialog =
                    true

                statusMessage =
                    "Ready to rollback to Version ${version.versionNumber}"

            } catch (
                e: Exception
            ) {

                statusMessage =
                    "Unable to prepare rollback: ${e.message}"

            } finally {

                isPreparingRollback =
                    false
            }
        }
    }


    // =================================================
    // CONFIRM ROLLBACK - NEW M3.8
    // =================================================

    fun confirmRollback() {

        val targetVersion =
            rollbackTargetVersion
                ?: return

        if (isReadOnly) {

            showRollbackDialog =
                false

            statusMessage =
                "Read-only mode: rollback disabled"

            return
        }

        if (
            editorText ==
            rollbackTargetText
        ) {

            showRollbackDialog =
                false

            rollbackTargetVersion =
                null

            rollbackTargetText =
                ""

            statusMessage =
                "Editor already matches Version ${targetVersion.versionNumber}"

            return
        }


        // Keep current content in Undo.
        // This allows rollback itself to be undone.

        undoStack.add(
            editorText
        )

        if (
            undoStack.size > 100
        ) {

            undoStack.removeAt(0)
        }

        redoStack.clear()


        // Restore historical content to working editor.

        editorText =
            rollbackTargetText


        // Rollback changes the working copy,
        // so it must be saved afterwards.

        isDirty =
            true


        showRollbackDialog =
            false

        rollbackTargetVersion =
            null

        rollbackTargetText =
            ""


        statusMessage =
            "Rolled back to Version ${targetVersion.versionNumber} - press Save"
    }


    // =================================================
    // MAIN UI
    // =================================================

    Scaffold(

        modifier =
            Modifier.fillMaxSize(),

        topBar = {

            TopAppBar(

                title = {

                    Column {

                        Text(
                            text =
                                "Modern Text Editor",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )

                        Text(
                            text =
                                buildString {

                                    append(
                                        fileName
                                    )

                                    if (isDirty) {

                                        append(" *")
                                    }

                                    if (isReadOnly) {

                                        append(
                                            " [Read Only]"
                                        )
                                    }
                                },

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                },

                actions = {

                    TextButton(

                        onClick = {

                            saveCurrentFile()
                        }

                    ) {

                        Text(
                            "Save"
                        )
                    }


                    Box {

                        TextButton(

                            onClick = {

                                showMoreMenu =
                                    true
                            }

                        ) {

                            Text(
                                "More"
                            )
                        }


                        DropdownMenu(

                            expanded =
                                showMoreMenu,

                            onDismissRequest = {

                                showMoreMenu =
                                    false
                            }

                        ) {


                            // NEW FILE

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "New File"
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false

                                    editorText =
                                        ""

                                    fileName =
                                        "untitled.txt"

                                    currentFileUri =
                                        null

                                    documentKey =
                                        "draft:${UUID.randomUUID()}"

                                    currentVersionNumber =
                                        0

                                    versionHistory =
                                        emptyList()

                                    diffLines =
                                        emptyList()

                                    rollbackTargetVersion =
                                        null

                                    rollbackTargetText =
                                        ""

                                    undoStack.clear()
                                    redoStack.clear()

                                    isReadOnly =
                                        false

                                    isDirty =
                                        false

                                    clearRecoveryDraft(
                                        context
                                    )

                                    recoveryDraft =
                                        null

                                    showRecoveryDialog =
                                        false

                                    statusMessage =
                                        "New file created"
                                }
                            )


                            // OPEN FILE

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "Open File"
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false

                                    openFileLauncher.launch(

                                        arrayOf(
                                            "text/plain",
                                            "text/markdown",
                                            "application/octet-stream"
                                        )
                                    )
                                }
                            )


                            // RECENT FILES

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "Recent Files"
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false

                                    recentFiles =
                                        loadRecentFiles(
                                            context
                                        )

                                    showRecentDialog =
                                        true
                                }
                            )


                            // SAVE AS

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "Save As"
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false

                                    saveAsLauncher.launch(
                                        fileName
                                    )
                                }
                            )


                            // FIND / REPLACE

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "Find / Replace"
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false

                                    searchResultMessage =
                                        ""

                                    showSearchDialog =
                                        true
                                }
                            )


                            // UNDO

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "Undo"
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false

                                    if (isReadOnly) {

                                        statusMessage =
                                            "Read-only mode: Undo disabled"

                                    } else if (
                                        undoStack.isNotEmpty()
                                    ) {

                                        redoStack.add(
                                            editorText
                                        )

                                        editorText =

                                            undoStack.removeAt(
                                                undoStack.lastIndex
                                            )

                                        isDirty =
                                            true

                                        statusMessage =
                                            "Undo"

                                    } else {

                                        statusMessage =
                                            "Nothing to undo"
                                    }
                                }
                            )


                            // REDO

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "Redo"
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false

                                    if (isReadOnly) {

                                        statusMessage =
                                            "Read-only mode: Redo disabled"

                                    } else if (
                                        redoStack.isNotEmpty()
                                    ) {

                                        undoStack.add(
                                            editorText
                                        )

                                        editorText =

                                            redoStack.removeAt(
                                                redoStack.lastIndex
                                            )

                                        isDirty =
                                            true

                                        statusMessage =
                                            "Redo"

                                    } else {

                                        statusMessage =
                                            "Nothing to redo"
                                    }
                                }
                            )


                            // WORD WRAP

                            DropdownMenuItem(

                                text = {

                                    Text(

                                        if (
                                            wordWrapEnabled
                                        ) {

                                            "Word Wrap: ON"

                                        } else {

                                            "Word Wrap: OFF"
                                        }
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false

                                    wordWrapEnabled =
                                        !wordWrapEnabled

                                    statusMessage =

                                        if (
                                            wordWrapEnabled
                                        ) {

                                            "Word Wrap ON"

                                        } else {

                                            "Word Wrap OFF"
                                        }
                                }
                            )


                            // CREATE VERSION

                            DropdownMenuItem(

                                text = {

                                    Text(

                                        if (
                                            isCreatingVersion
                                        ) {

                                            "Creating Version..."

                                        } else {

                                            "Create Version"
                                        }
                                    )
                                },

                                enabled =
                                    !isCreatingVersion,

                                onClick = {

                                    showMoreMenu =
                                        false

                                    createVersion()
                                }
                            )


                            // VERSION HISTORY

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "Version History"
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false

                                    openVersionHistory()
                                }
                            )


                            // READ ONLY

                            DropdownMenuItem(

                                text = {

                                    Text(

                                        if (
                                            isReadOnly
                                        ) {

                                            "Read-only Mode: ON"

                                        } else {

                                            "Read-only Mode: OFF"
                                        }
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false

                                    toggleReadOnly()
                                }
                            )
                        }
                    }
                }
            )
        }

    ) { innerPadding ->


        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        innerPadding
                    )
                    .padding(
                        12.dp
                    )

        ) {


            // =================================================
            // FILE INFORMATION
            // =================================================

            Surface(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(
                        12.dp
                    ),

                color =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant

            ) {

                Column(

                    modifier =
                        Modifier.padding(
                            12.dp
                        )

                ) {

                    OutlinedTextField(

                        value =
                            fileName,

                        onValueChange = {

                            if (!isReadOnly) {

                                fileName =
                                    it

                            } else {

                                statusMessage =
                                    "Read-only mode: rename disabled"
                            }
                        },

                        readOnly =
                            isReadOnly,

                        label = {

                            Text(
                                "File name"
                            )
                        },

                        singleLine =
                            true,

                        modifier =
                            Modifier.fillMaxWidth()
                    )


                    Spacer(

                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )


                    Row(

                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),

                        verticalAlignment =
                            Alignment.CenterVertically

                    ) {


                        FileTypeBadge(
                            fileType
                        )


                        Text(

                            text =

                                if (
                                    currentFileUri == null
                                ) {

                                    "New file"

                                } else {

                                    "Opened file"
                                },

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )


                        Text(

                            text =
                                "V$currentVersionNumber",

                            fontWeight =
                                FontWeight.Bold,

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall
                        )


                        if (isReadOnly) {

                            Text(

                                text =
                                    "READ ONLY",

                                fontWeight =
                                    FontWeight.Bold,

                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall
                            )
                        }


                        if (isDirty) {

                            Text(

                                text =
                                    "Unsaved",

                                fontWeight =
                                    FontWeight.Bold,

                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall
                            )
                        }
                    }
                }
            }


            Spacer(

                modifier =
                    Modifier.height(
                        10.dp
                    )
            )


            // =================================================
            // EDITOR
            // =================================================

            EditorTextArea(

                text =
                    editorText,

                fileName =
                    fileName,

                onTextChange = {

                        newText ->

                    updateEditorText(
                        newText
                    )
                },

                wordWrapEnabled =
                    wordWrapEnabled,

                isReadOnly =
                    isReadOnly,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
            )


            Spacer(

                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            // =================================================
            // STATUS BAR
            // =================================================

            Surface(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(
                        10.dp
                    ),

                color =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant

            ) {

                Row(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    12.dp,
                                vertical =
                                    8.dp
                            ),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically

                ) {


                    Column {

                        Text(

                            text =
                                statusMessage,

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )


                        Text(

                            text =

                                if (isReadOnly) {

                                    "$fileType | Read Only | V$currentVersionNumber"

                                } else {

                                    "$fileType | Editable | V$currentVersionNumber"
                                },

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall
                        )
                    }


                    Column(

                        horizontalAlignment =
                            Alignment.End

                    ) {

                        Text(

                            text =
                                "$lineCount lines | ${editorText.length} chars",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )


                        Text(

                            text =

                                if (
                                    wordWrapEnabled
                                ) {

                                    "Wrap ON"

                                } else {

                                    "Wrap OFF"
                                },

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall
                        )
                    }
                }
            }
        }
    }


    // =====================================================
    // CRASH RECOVERY DIALOG
    // =====================================================

    if (
        showRecoveryDialog &&
        recoveryDraft != null
    ) {

        AlertDialog(

            onDismissRequest = {
            },

            title = {

                Text(
                    "Recover Unsaved Work?"
                )
            },

            text = {

                Column {

                    Text(
                        "The editor found an auto-saved document."
                    )

                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )

                    Text(

                        text =
                            "File: ${recoveryDraft!!.fileName}",

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Text(
                        "Preview:"
                    )

                    Text(

                        text =

                            if (
                                recoveryDraft!!
                                    .text
                                    .length > 200
                            ) {

                                recoveryDraft!!
                                    .text
                                    .take(200) +
                                        "..."

                            } else {

                                recoveryDraft!!
                                    .text
                            }
                    )
                }
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        val draft =
                            recoveryDraft

                        if (draft != null) {

                            editorText =
                                draft.text

                            fileName =
                                draft.fileName

                            currentFileUri =
                                null

                            documentKey =
                                "draft:${UUID.randomUUID()}"

                            currentVersionNumber =
                                0

                            versionHistory =
                                emptyList()

                            diffLines =
                                emptyList()

                            rollbackTargetVersion =
                                null

                            rollbackTargetText =
                                ""

                            undoStack.clear()
                            redoStack.clear()

                            isReadOnly =
                                false

                            isDirty =
                                true

                            statusMessage =
                                "Recovered unsaved draft"
                        }

                        showRecoveryDialog =
                            false
                    }

                ) {

                    Text(
                        "Recover"
                    )
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {

                        clearRecoveryDraft(
                            context
                        )

                        recoveryDraft =
                            null

                        showRecoveryDialog =
                            false

                        statusMessage =
                            "Recovery discarded"
                    }

                ) {

                    Text(
                        "Discard"
                    )
                }
            }
        )
    }


    // =====================================================
    // RECENT FILES
    // =====================================================

    if (showRecentDialog) {

        AlertDialog(

            onDismissRequest = {

                showRecentDialog =
                    false
            },

            title = {

                Text(
                    "Recent Files"
                )
            },

            text = {

                Column {

                    if (
                        recentFiles.isEmpty()
                    ) {

                        Text(
                            "No recent files yet."
                        )

                    } else {

                        recentFiles.forEach {

                                recentFile ->

                            Text(

                                text =
                                    recentFile.name,

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {

                                            try {

                                                val uri =
                                                    Uri.parse(
                                                        recentFile.uri
                                                    )

                                                val text =
                                                    readTextFromFile(
                                                        context,
                                                        uri
                                                    )

                                                if (text != null) {

                                                    val key =
                                                        uri.toString()

                                                    editorText =
                                                        text

                                                    fileName =
                                                        recentFile.name

                                                    currentFileUri =
                                                        uri

                                                    documentKey =
                                                        key

                                                    currentVersionNumber =
                                                        0

                                                    versionHistory =
                                                        emptyList()

                                                    diffLines =
                                                        emptyList()

                                                    rollbackTargetVersion =
                                                        null

                                                    rollbackTargetText =
                                                        ""

                                                    undoStack.clear()
                                                    redoStack.clear()

                                                    isDirty =
                                                        false

                                                    isReadOnly =
                                                        false

                                                    clearRecoveryDraft(
                                                        context
                                                    )

                                                    recoveryDraft =
                                                        null

                                                    addRecentFile(
                                                        context,
                                                        fileName,
                                                        uri
                                                    )

                                                    recentFiles =
                                                        loadRecentFiles(
                                                            context
                                                        )

                                                    coroutineScope.launch {

                                                        val document =

                                                            getOrCreateDocument(
                                                                documentDao =
                                                                    documentDao,
                                                                documentKey =
                                                                    key,
                                                                fileName =
                                                                    recentFile.name,
                                                                fileUri =
                                                                    uri.toString()
                                                            )

                                                        isReadOnly =
                                                            document.isReadOnly

                                                        currentVersionNumber =

                                                            versionDao
                                                                .getLatestVersionNumber(
                                                                    key
                                                                )
                                                    }

                                                    statusMessage =
                                                        "Recent file opened"

                                                } else {

                                                    statusMessage =
                                                        "Unable to read file"
                                                }

                                            } catch (
                                                e: Exception
                                            ) {

                                                statusMessage =
                                                    "Recent file is unavailable"
                                            }

                                            showRecentDialog =
                                                false
                                        }
                                        .padding(
                                            vertical =
                                                12.dp
                                        )
                            )
                        }
                    }
                }
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        showRecentDialog =
                            false
                    }

                ) {

                    Text(
                        "Close"
                    )
                }
            }
        )
    }


    // =====================================================
    // FIND / REPLACE
    // =====================================================

    if (showSearchDialog) {

        Dialog(

            onDismissRequest = {

                showSearchDialog =
                    false
            }

        ) {

            Card(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            16.dp
                        )

            ) {

                Column(

                    modifier =
                        Modifier.padding(
                            20.dp
                        )

                ) {

                    Text(

                        text =
                            "Find and Replace",

                        style =
                            MaterialTheme
                                .typography
                                .titleLarge
                    )


                    Spacer(
                        Modifier.height(
                            16.dp
                        )
                    )


                    OutlinedTextField(

                        value =
                            searchText,

                        onValueChange = {

                            searchText =
                                it

                            searchResultMessage =
                                ""
                        },

                        label = {

                            Text(
                                "Find"
                            )
                        },

                        singleLine =
                            true,

                        modifier =
                            Modifier.fillMaxWidth()
                    )


                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )


                    OutlinedTextField(

                        value =
                            replaceText,

                        onValueChange = {

                            replaceText =
                                it
                        },

                        readOnly =
                            isReadOnly,

                        label = {

                            Text(
                                "Replace with"
                            )
                        },

                        singleLine =
                            true,

                        modifier =
                            Modifier.fillMaxWidth()
                    )


                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )


                    if (
                        searchResultMessage
                            .isNotEmpty()
                    ) {

                        Text(
                            searchResultMessage
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )
                    }


                    Row {

                        TextButton(

                            onClick = {

                                if (
                                    searchText.isBlank()
                                ) {

                                    searchResultMessage =
                                        "Enter text to search"

                                } else {

                                    val count =
                                        countOccurrences(
                                            editorText,
                                            searchText
                                        )

                                    searchResultMessage =

                                        if (
                                            count > 0
                                        ) {

                                            "$count match(es) found"

                                        } else {

                                            "Text not found"
                                        }
                                }
                            }

                        ) {

                            Text(
                                "Find"
                            )
                        }


                        TextButton(

                            onClick = {

                                if (isReadOnly) {

                                    searchResultMessage =
                                        "Read-only mode: Replace disabled"

                                } else if (
                                    searchText.isBlank()
                                ) {

                                    searchResultMessage =
                                        "Enter text to search"

                                } else {

                                    val index =
                                        editorText.indexOf(
                                            searchText,
                                            ignoreCase =
                                                true
                                        )

                                    if (
                                        index >= 0
                                    ) {

                                        val newText =

                                            editorText
                                                .replaceRange(
                                                    index,
                                                    index +
                                                            searchText.length,
                                                    replaceText
                                                )

                                        updateEditorText(
                                            newText
                                        )

                                        searchResultMessage =
                                            "First match replaced"

                                    } else {

                                        searchResultMessage =
                                            "Text not found"
                                    }
                                }
                            }

                        ) {

                            Text(
                                "Replace"
                            )
                        }
                    }


                    Row {

                        TextButton(

                            onClick = {

                                if (isReadOnly) {

                                    searchResultMessage =
                                        "Read-only mode: Replace All disabled"

                                } else if (
                                    searchText.isBlank()
                                ) {

                                    searchResultMessage =
                                        "Enter text to search"

                                } else {

                                    val count =
                                        countOccurrences(
                                            editorText,
                                            searchText
                                        )

                                    if (
                                        count > 0
                                    ) {

                                        val newText =

                                            editorText.replace(
                                                searchText,
                                                replaceText,
                                                ignoreCase =
                                                    true
                                            )

                                        updateEditorText(
                                            newText
                                        )

                                        searchResultMessage =
                                            "$count match(es) replaced"

                                    } else {

                                        searchResultMessage =
                                            "Text not found"
                                    }
                                }
                            }

                        ) {

                            Text(
                                "Replace All"
                            )
                        }


                        TextButton(

                            onClick = {

                                showSearchDialog =
                                    false
                            }

                        ) {

                            Text(
                                "Close"
                            )
                        }
                    }
                }
            }
        }
    }


    // =====================================================
    // VERSION HISTORY
    // =====================================================

    if (showVersionHistoryDialog) {

        AlertDialog(

            onDismissRequest = {

                showVersionHistoryDialog =
                    false
            },

            title = {

                Column {

                    Text(
                        "Version History"
                    )

                    Text(

                        text =
                            fileName,

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }
            },

            text = {

                Column {

                    if (isLoadingVersionHistory) {

                        Text(
                            "Loading versions..."
                        )

                    } else if (
                        versionHistory.isEmpty()
                    ) {

                        Text(
                            "No versions have been created for this file yet."
                        )

                    } else {

                        Text(

                            text =
                                "${versionHistory.size} version(s)",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )


                        Spacer(

                            modifier =
                                Modifier.height(
                                    10.dp
                                )
                        )


                        LazyColumn(

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        max =
                                            420.dp
                                    ),

                            verticalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )

                        ) {


                            items(

                                items =
                                    versionHistory,

                                key = {

                                    it.id
                                }

                            ) { version ->


                                Card(

                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {

                                                previewVersion(
                                                    version
                                                )
                                            }

                                ) {

                                    Column(

                                        modifier =
                                            Modifier.padding(
                                                14.dp
                                            )

                                    ) {


                                        Row(

                                            modifier =
                                                Modifier.fillMaxWidth(),

                                            horizontalArrangement =
                                                Arrangement.SpaceBetween,

                                            verticalAlignment =
                                                Alignment.CenterVertically

                                        ) {


                                            Text(

                                                text =
                                                    "Version ${version.versionNumber}",

                                                fontWeight =
                                                    FontWeight.Bold
                                            )


                                            Text(

                                                text =

                                                    when {

                                                        version.isBaseVersion -> {

                                                            "BASE"
                                                        }

                                                        version.versionNumber ==
                                                                currentVersionNumber -> {

                                                            "LATEST"
                                                        }

                                                        else -> {

                                                            "DELTA"
                                                        }
                                                    },

                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .labelSmall,

                                                fontWeight =
                                                    FontWeight.Bold
                                            )
                                        }


                                        Spacer(

                                            modifier =
                                                Modifier.height(
                                                    5.dp
                                                )
                                        )


                                        Text(

                                            text =
                                                version.description
                                                    ?: "Version ${version.versionNumber}",

                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodyMedium
                                        )


                                        Spacer(

                                            modifier =
                                                Modifier.height(
                                                    4.dp
                                                )
                                        )


                                        Text(

                                            text =
                                                formatVersionDate(
                                                    version.createdAt
                                                ),

                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodySmall
                                        )


                                        if (
                                            !version.isBaseVersion
                                        ) {

                                            val patchBytes =

                                                version
                                                    .patchData
                                                    .toByteArray(
                                                        Charsets.UTF_8
                                                    )
                                                    .size


                                            Spacer(

                                                modifier =
                                                    Modifier.height(
                                                        4.dp
                                                    )
                                            )


                                            Text(

                                                text =
                                                    "Delta size: $patchBytes bytes",

                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .labelSmall
                                            )
                                        }


                                        Spacer(

                                            modifier =
                                                Modifier.height(
                                                    8.dp
                                                )
                                        )


                                        Row(

                                            modifier =
                                                Modifier.fillMaxWidth(),

                                            horizontalArrangement =
                                                Arrangement.SpaceBetween,

                                            verticalAlignment =
                                                Alignment.CenterVertically

                                        ) {


                                            TextButton(

                                                onClick = {

                                                    openDiffForVersion(
                                                        version
                                                    )
                                                }

                                            ) {

                                                Text(
                                                    "View Diff"
                                                )
                                            }


                                            // =========================================
                                            // ROLLBACK BUTTON - NEW M3.8
                                            // =========================================

                                            TextButton(

                                                onClick = {

                                                    prepareRollback(
                                                        version
                                                    )
                                                },

                                                enabled =
                                                    !isReadOnly &&
                                                            !isPreparingRollback

                                            ) {

                                                Text(
                                                    "Rollback"
                                                )
                                            }
                                        }


                                        Text(

                                            text =
                                                "Tap card to preview",

                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        showVersionHistoryDialog =
                            false
                    }

                ) {

                    Text(
                        "Close"
                    )
                }
            }
        )
    }


    // =====================================================
    // HISTORICAL PREVIEW
    // =====================================================

    if (showVersionPreviewDialog) {

        AlertDialog(

            onDismissRequest = {

                showVersionPreviewDialog =
                    false

                showVersionHistoryDialog =
                    true
            },

            title = {

                Column {

                    Text(
                        "Version $previewVersionNumber"
                    )

                    Text(

                        text =
                            "Read-only historical preview",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }
            },

            text = {

                Surface(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                max =
                                    450.dp
                            ),

                    shape =
                        RoundedCornerShape(
                            8.dp
                        ),

                    color =
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant

                ) {

                    Text(

                        text =
                            previewVersionText,

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(
                                    rememberScrollState()
                                )
                                .padding(
                                    12.dp
                                ),

                        fontFamily =
                            FontFamily.Monospace
                    )
                }
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        showVersionPreviewDialog =
                            false

                        showVersionHistoryDialog =
                            true
                    }

                ) {

                    Text(
                        "Back to History"
                    )
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {

                        showVersionPreviewDialog =
                            false
                    }

                ) {

                    Text(
                        "Close"
                    )
                }
            }
        )
    }


    // =====================================================
    // DIFF VIEWER
    // =====================================================

    if (showDiffDialog) {

        val addedLines =

            diffLines.count {

                it.startsWith("+") &&
                        !it.startsWith("+++")
            }


        val removedLines =

            diffLines.count {

                it.startsWith("-") &&
                        !it.startsWith("---")
            }


        AlertDialog(

            onDismissRequest = {

                showDiffDialog =
                    false

                showVersionHistoryDialog =
                    true
            },

            title = {

                Column {

                    Text(
                        "Diff Viewer"
                    )

                    Text(

                        text =
                            "$diffFromLabel → $diffToLabel",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }
            },

            text = {

                Column {


                    if (isLoadingDiff) {

                        Text(
                            "Loading differences..."
                        )

                    } else {


                        Surface(

                            modifier =
                                Modifier.fillMaxWidth(),

                            shape =
                                RoundedCornerShape(
                                    8.dp
                                ),

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant

                        ) {

                            Column(

                                modifier =
                                    Modifier.padding(
                                        10.dp
                                    )

                            ) {


                                Text(

                                    text =
                                        "Changes",

                                    fontWeight =
                                        FontWeight.Bold
                                )


                                Spacer(

                                    modifier =
                                        Modifier.height(
                                            4.dp
                                        )
                                )


                                Text(

                                    text =
                                        "+ $addedLines added    - $removedLines removed",

                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall
                                )
                            }
                        }


                        Spacer(

                            modifier =
                                Modifier.height(
                                    10.dp
                                )
                        )


                        Text(

                            text =
                                "+ Added line",

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall
                        )


                        Text(

                            text =
                                "- Removed line",

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error,

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall
                        )


                        Text(

                            text =
                                "@@ Changed section",

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .secondary,

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall
                        )


                        Spacer(

                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )


                        Surface(

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        min =
                                            180.dp,
                                        max =
                                            450.dp
                                    ),

                            shape =
                                RoundedCornerShape(
                                    8.dp
                                ),

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .surface

                        ) {


                            DiffViewerText(

                                lines =
                                    diffLines,

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            4.dp
                                        )
                            )
                        }
                    }
                }
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        showDiffDialog =
                            false

                        showVersionHistoryDialog =
                            true
                    }

                ) {

                    Text(
                        "Back to History"
                    )
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {

                        showDiffDialog =
                            false
                    }

                ) {

                    Text(
                        "Close"
                    )
                }
            }
        )
    }


    // =====================================================
    // ROLLBACK CONFIRMATION - NEW M3.8
    // =====================================================

    if (
        showRollbackDialog &&
        rollbackTargetVersion != null
    ) {

        val target =
            rollbackTargetVersion!!


        AlertDialog(

            onDismissRequest = {

                showRollbackDialog =
                    false

                rollbackTargetVersion =
                    null

                rollbackTargetText =
                    ""

                showVersionHistoryDialog =
                    true
            },

            title = {

                Text(
                    "Rollback to Version ${target.versionNumber}?"
                )
            },

            text = {

                Column {


                    Text(
                        "The editor will restore the content from Version ${target.versionNumber}."
                    )


                    Spacer(

                        modifier =
                            Modifier.height(
                                10.dp
                            )
                    )


                    Text(

                        text =
                            "Existing versions will NOT be deleted.",

                        fontWeight =
                            FontWeight.Bold
                    )


                    Spacer(

                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )


                    Text(
                        "After rollback, the document will be marked Unsaved. Press Save to write the restored content to the file."
                    )


                    Spacer(

                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )


                    Text(
                        "You can then use Create Version to record the rollback as a new version."
                    )


                    Spacer(

                        modifier =
                            Modifier.height(
                                12.dp
                            )
                    )


                    Text(

                        text =
                            "Preview:",

                        fontWeight =
                            FontWeight.Bold
                    )


                    Spacer(

                        modifier =
                            Modifier.height(
                                5.dp
                            )
                    )


                    Surface(

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    max =
                                        180.dp
                                ),

                        shape =
                            RoundedCornerShape(
                                8.dp
                            ),

                        color =
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant

                    ) {


                        Text(

                            text =

                                if (
                                    rollbackTargetText.length >
                                    500
                                ) {

                                    rollbackTargetText
                                        .take(
                                            500
                                        ) + "..."

                                } else {

                                    rollbackTargetText
                                },

                            modifier =
                                Modifier
                                    .verticalScroll(
                                        rememberScrollState()
                                    )
                                    .padding(
                                        10.dp
                                    ),

                            fontFamily =
                                FontFamily.Monospace
                        )
                    }
                }
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        confirmRollback()
                    }

                ) {

                    Text(
                        "Rollback"
                    )
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {

                        showRollbackDialog =
                            false

                        rollbackTargetVersion =
                            null

                        rollbackTargetText =
                            ""

                        showVersionHistoryDialog =
                            true

                        statusMessage =
                            "Rollback cancelled"
                    }

                ) {

                    Text(
                        "Cancel"
                    )
                }
            }
        )
    }
}


// =====================================================
// DIFF VIEWER TEXT
// =====================================================

@Composable
fun DiffViewerText(

    lines: List<String>,

    modifier: Modifier = Modifier

) {

    val addedColor =
        MaterialTheme
            .colorScheme
            .primary

    val removedColor =
        MaterialTheme
            .colorScheme
            .error

    val metadataColor =
        MaterialTheme
            .colorScheme
            .secondary

    val normalColor =
        MaterialTheme
            .colorScheme
            .onSurface


    val builder =
        AnnotatedString.Builder()


    var currentPosition =
        0


    lines.forEachIndexed {

            index,
            line ->


        val color =

            when {

                line.startsWith("+++") -> {

                    metadataColor
                }

                line.startsWith("---") -> {

                    metadataColor
                }

                line.startsWith("@@") -> {

                    metadataColor
                }

                line.startsWith("+") -> {

                    addedColor
                }

                line.startsWith("-") -> {

                    removedColor
                }

                else -> {

                    normalColor
                }
            }


        builder.append(
            line
        )


        builder.addStyle(

            SpanStyle(

                color =
                    color,

                fontWeight =

                    if (
                        line.startsWith("+") ||
                        line.startsWith("-") ||
                        line.startsWith("@@")
                    ) {

                        FontWeight.SemiBold

                    } else {

                        FontWeight.Normal
                    }
            ),

            currentPosition,

            currentPosition +
                    line.length
        )


        currentPosition +=
            line.length


        if (
            index !=
            lines.lastIndex
        ) {

            builder.append(
                "\n"
            )

            currentPosition++
        }
    }


    val verticalScroll =
        rememberScrollState()

    val horizontalScroll =
        rememberScrollState()


    Text(

        text =
            builder.toAnnotatedString(),

        modifier =
            modifier
                .verticalScroll(
                    verticalScroll
                )
                .horizontalScroll(
                    horizontalScroll
                )
                .padding(
                    8.dp
                ),

        fontFamily =
            FontFamily.Monospace,

        style =
            MaterialTheme
                .typography
                .bodySmall
    )
}


// =====================================================
// CREATE UNIFIED DIFF
// =====================================================

fun buildUnifiedDiff(

    oldText: String,

    newText: String,

    oldLabel: String,

    newLabel: String

): List<String> {


    val normalizedOld =
        normalizeTextForDiff(
            oldText
        )


    val normalizedNew =
        normalizeTextForDiff(
            newText
        )


    if (
        normalizedOld ==
        normalizedNew
    ) {

        return listOf(
            "No differences between these versions."
        )
    }


    val oldLines =

        if (
            normalizedOld.isEmpty()
        ) {

            emptyList()

        } else {

            normalizedOld.split(
                "\n"
            )
        }


    val newLines =

        if (
            normalizedNew.isEmpty()
        ) {

            emptyList()

        } else {

            normalizedNew.split(
                "\n"
            )
        }


    val patch =
        DiffUtils.diff(
            oldLines,
            newLines
        )


    return UnifiedDiffUtils
        .generateUnifiedDiff(
            oldLabel,
            newLabel,
            oldLines,
            patch,
            3
        )
}


// =====================================================
// NORMALIZE TEXT FOR DIFF
// =====================================================

fun normalizeTextForDiff(
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
// DATABASE HELPERS
// =====================================================

suspend fun getOrCreateDocument(

    documentDao: DocumentDao,

    documentKey: String,

    fileName: String,

    fileUri: String?

): DocumentEntity {


    val existing =
        documentDao.getDocument(
            documentKey
        )


    if (
        existing != null
    ) {

        return existing
    }


    val document =

        DocumentEntity(
            documentKey =
                documentKey,
            fileName =
                fileName,
            fileUri =
                fileUri
        )


    documentDao.upsertDocument(
        document
    )


    return document
}


suspend fun saveDocumentMetadata(

    documentDao: DocumentDao,

    documentKey: String,

    fileName: String,

    fileUri: String?,

    isReadOnly: Boolean

) {


    val existing =

        documentDao.getDocument(
            documentKey
        )


    val now =
        System.currentTimeMillis()


    val document =

        DocumentEntity(
            documentKey =
                documentKey,
            fileName =
                fileName,
            fileUri =
                fileUri,
            baseSnapshotPath =
                existing
                    ?.baseSnapshotPath,
            isReadOnly =
                isReadOnly,
            createdAt =
                existing
                    ?.createdAt
                    ?: now,
            updatedAt =
                now
        )


    documentDao.upsertDocument(
        document
    )
}


// =====================================================
// VERSION DATE FORMATTER
// =====================================================

fun formatVersionDate(
    timestamp: Long
): String {


    val formatter =

        SimpleDateFormat(
            "yyyy-MM-dd  HH:mm:ss",
            Locale.getDefault()
        )


    return formatter.format(
        Date(timestamp)
    )
}


// =====================================================
// EDITOR TEXT AREA
// =====================================================

@Composable
fun EditorTextArea(

    text: String,

    fileName: String,

    onTextChange: (String) -> Unit,

    wordWrapEnabled: Boolean,

    isReadOnly: Boolean,

    modifier: Modifier = Modifier

) {


    val horizontalScrollState =
        rememberScrollState()


    val longestLineLength =

        remember(text) {

            text
                .split("\n")
                .maxOfOrNull {

                    it.length

                } ?: 0
        }


    val isKotlin =

        fileName.endsWith(
            ".kt",
            true
        )


    val isMarkdown =

        fileName.endsWith(
            ".md",
            true
        ) ||
                fileName.endsWith(
                    ".markdown",
                    true
                )


    val transformation:
            VisualTransformation =

        when {

            isKotlin ->

                KotlinSyntaxVisualTransformation(
                    MaterialTheme
                        .colorScheme
                        .primary,
                    MaterialTheme
                        .colorScheme
                        .tertiary,
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                    MaterialTheme
                        .colorScheme
                        .secondary
                )


            isMarkdown ->

                MarkdownSyntaxVisualTransformation(
                    MaterialTheme
                        .colorScheme
                        .primary,
                    MaterialTheme
                        .colorScheme
                        .secondary,
                    MaterialTheme
                        .colorScheme
                        .tertiary,
                    MaterialTheme
                        .colorScheme
                        .tertiary,
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant,
                    MaterialTheme
                        .colorScheme
                        .primary,
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                    MaterialTheme
                        .colorScheme
                        .secondary
                )


            else ->

                VisualTransformation.None
        }


    BoxWithConstraints(
        modifier
    ) {


        val calculated =
            (
                    longestLineLength *
                            12 +
                            80
                    ).dp


        val noWrapWidth =

            if (
                calculated > maxWidth
            ) {

                calculated

            } else {

                maxWidth
            }


        val textStyle =

            MaterialTheme
                .typography
                .bodyLarge
                .copy(
                    fontFamily =
                        FontFamily.Monospace
                )


        if (
            wordWrapEnabled
        ) {


            TextField(

                value =
                    text,

                onValueChange =
                    onTextChange,

                readOnly =
                    isReadOnly,

                placeholder = {

                    Text(

                        if (
                            isReadOnly
                        ) {

                            "Read-only document"

                        } else {

                            "Start typing here..."
                        }
                    )
                },

                textStyle =
                    textStyle,

                visualTransformation =
                    transformation,

                modifier =
                    Modifier.fillMaxSize()
            )


        } else {


            Box(

                Modifier
                    .fillMaxSize()
                    .horizontalScroll(
                        horizontalScrollState
                    )

            ) {


                TextField(

                    value =
                        text,

                    onValueChange =
                        onTextChange,

                    readOnly =
                        isReadOnly,

                    textStyle =
                        textStyle,

                    visualTransformation =
                        transformation,

                    modifier =
                        Modifier
                            .width(
                                noWrapWidth
                            )
                            .fillMaxHeight()
                )
            }
        }
    }
}


// =====================================================
// FILE TYPE BADGE
// =====================================================

@Composable
fun FileTypeBadge(
    fileType: String
) {


    Surface(

        shape =
            RoundedCornerShape(
                50
            ),

        color =
            MaterialTheme
                .colorScheme
                .primaryContainer

    ) {


        Text(

            text =
                fileType,

            modifier =
                Modifier.padding(
                    horizontal =
                        10.dp,
                    vertical =
                        5.dp
                )
        )
    }
}


// =====================================================
// FILE TYPE
// =====================================================

fun getFileType(
    fileName: String
): String {


    return when {

        fileName.endsWith(
            ".kt",
            true
        ) -> "Kotlin"


        fileName.endsWith(
            ".md",
            true
        ) ||
                fileName.endsWith(
                    ".markdown",
                    true
                ) -> "Markdown"


        fileName.endsWith(
            ".txt",
            true
        ) -> "Text"


        else ->
            "Plain Text"
    }
}


// =====================================================
// KOTLIN SYNTAX HIGHLIGHTER
// =====================================================

class KotlinSyntaxVisualTransformation(

    private val keywordColor: Color,

    private val stringColor: Color,

    private val commentColor: Color,

    private val annotationColor: Color

) : VisualTransformation {


    override fun filter(
        text: AnnotatedString
    ): TransformedText {


        val source =
            text.text


        val builder =
            AnnotatedString.Builder(
                source
            )


        var index =
            0


        while (
            index <
            source.length
        ) {


            if (
                source.startsWith(
                    "//",
                    index
                )
            ) {


                val endIndex =
                    source.indexOf(
                        '\n',
                        index
                    )


                val end =

                    if (
                        endIndex == -1
                    ) {

                        source.length

                    } else {

                        endIndex
                    }


                builder.addStyle(

                    SpanStyle(
                        color =
                            commentColor,
                        fontStyle =
                            FontStyle.Italic
                    ),

                    index,

                    end
                )


                index =
                    end

                continue
            }


            if (
                source.startsWith(
                    "/*",
                    index
                )
            ) {


                val closing =
                    source.indexOf(
                        "*/",
                        index + 2
                    )


                val end =

                    if (
                        closing == -1
                    ) {

                        source.length

                    } else {

                        closing + 2
                    }


                builder.addStyle(

                    SpanStyle(
                        color =
                            commentColor,
                        fontStyle =
                            FontStyle.Italic
                    ),

                    index,

                    end
                )


                index =
                    end

                continue
            }


            if (
                source.startsWith(
                    "\"\"\"",
                    index
                )
            ) {


                val closing =
                    source.indexOf(
                        "\"\"\"",
                        index + 3
                    )


                val end =

                    if (
                        closing == -1
                    ) {

                        source.length

                    } else {

                        closing + 3
                    }


                builder.addStyle(

                    SpanStyle(
                        color =
                            stringColor
                    ),

                    index,

                    end
                )


                index =
                    end

                continue
            }


            if (
                source[index] == '"'
            ) {


                val end =
                    findQuotedTextEnd(
                        source,
                        index,
                        '"'
                    )


                builder.addStyle(

                    SpanStyle(
                        color =
                            stringColor
                    ),

                    index,

                    end
                )


                index =
                    end

                continue
            }


            if (
                source[index] == '\''
            ) {


                val end =
                    findQuotedTextEnd(
                        source,
                        index,
                        '\''
                    )


                builder.addStyle(

                    SpanStyle(
                        color =
                            stringColor
                    ),

                    index,

                    end
                )


                index =
                    end

                continue
            }


            if (
                source[index] == '@'
            ) {


                var end =
                    index + 1


                while (
                    end <
                    source.length &&
                    (
                            source[end]
                                .isLetterOrDigit() ||
                                    source[end] ==
                                    '_'
                            )
                ) {

                    end++
                }


                if (
                    end >
                    index + 1
                ) {


                    builder.addStyle(

                        SpanStyle(
                            color =
                                annotationColor,
                            fontWeight =
                                FontWeight.SemiBold
                        ),

                        index,

                        end
                    )


                    index =
                        end

                    continue
                }
            }


            if (
                source[index].isLetter() ||
                source[index] == '_'
            ) {


                var end =
                    index + 1


                while (
                    end <
                    source.length &&
                    (
                            source[end]
                                .isLetterOrDigit() ||
                                    source[end] ==
                                    '_'
                            )
                ) {

                    end++
                }


                val word =
                    source.substring(
                        index,
                        end
                    )


                if (
                    word in
                    KOTLIN_KEYWORDS
                ) {


                    builder.addStyle(

                        SpanStyle(
                            color =
                                keywordColor,
                            fontWeight =
                                FontWeight.Bold
                        ),

                        index,

                        end
                    )
                }


                index =
                    end

                continue
            }


            index++
        }


        return TransformedText(

            builder.toAnnotatedString(),

            OffsetMapping.Identity
        )
    }
}


// =====================================================
// MARKDOWN SYNTAX HIGHLIGHTER
// =====================================================

class MarkdownSyntaxVisualTransformation(

    private val headingColor: Color,

    private val boldColor: Color,

    private val italicColor: Color,

    private val codeColor: Color,

    private val codeBackgroundColor: Color,

    private val linkColor: Color,

    private val quoteColor: Color,

    private val listColor: Color

) : VisualTransformation {


    override fun filter(
        text: AnnotatedString
    ): TransformedText {


        val source =
            text.text


        val builder =
            AnnotatedString.Builder(
                source
            )


        fun apply(
            regex: Regex,
            style: SpanStyle
        ) {


            regex
                .findAll(
                    source
                )
                .forEach {


                    builder.addStyle(
                        style,
                        it.range.first,
                        it.range.last + 1
                    )
                }
        }


        apply(

            Regex(
                """(?m)^(#{1,6})[ \t]+.*$"""
            ),

            SpanStyle(
                color =
                    headingColor,
                fontWeight =
                    FontWeight.Bold
            )
        )


        apply(

            Regex(
                """(?m)^[ \t]*>[ \t]?.*$"""
            ),

            SpanStyle(
                color =
                    quoteColor,
                fontStyle =
                    FontStyle.Italic
            )
        )


        apply(

            Regex(
                """(?m)^[ \t]*[-+*][ \t]+"""
            ),

            SpanStyle(
                color =
                    listColor,
                fontWeight =
                    FontWeight.Bold
            )
        )


        apply(

            Regex(
                """(?m)^[ \t]*\d+\.[ \t]+"""
            ),

            SpanStyle(
                color =
                    listColor,
                fontWeight =
                    FontWeight.Bold
            )
        )


        apply(

            Regex(
                """\[[^\]\n]+\]\([^\)\n]+\)"""
            ),

            SpanStyle(
                color =
                    linkColor,
                textDecoration =
                    TextDecoration.Underline
            )
        )


        apply(

            Regex(
                """\*\*[^*\n]+\*\*|__[^_\n]+__"""
            ),

            SpanStyle(
                color =
                    boldColor,
                fontWeight =
                    FontWeight.Bold
            )
        )


        apply(

            Regex(
                """(?<!\*)\*[^*\n]+\*(?!\*)|(?<!_)_[^_\n]+_(?!_)"""
            ),

            SpanStyle(
                color =
                    italicColor,
                fontStyle =
                    FontStyle.Italic
            )
        )


        apply(

            Regex(
                """`[^`\n]+`"""
            ),

            SpanStyle(
                color =
                    codeColor,
                background =
                    codeBackgroundColor,
                fontFamily =
                    FontFamily.Monospace
            )
        )


        apply(

            Regex(
                """```[\s\S]*?```"""
            ),

            SpanStyle(
                color =
                    codeColor,
                background =
                    codeBackgroundColor,
                fontFamily =
                    FontFamily.Monospace
            )
        )


        return TransformedText(

            builder.toAnnotatedString(),

            OffsetMapping.Identity
        )
    }
}


// =====================================================
// QUOTED TEXT HELPER
// =====================================================

fun findQuotedTextEnd(

    text: String,

    startIndex: Int,

    quote: Char

): Int {


    var index =
        startIndex + 1


    var escaped =
        false


    while (
        index < text.length
    ) {


        val character =
            text[index]


        if (escaped) {


            escaped =
                false


        } else if (
            character == '\\'
        ) {


            escaped =
                true


        } else if (
            character == quote
        ) {


            return index + 1
        }


        index++
    }


    return text.length
}


// =====================================================
// KOTLIN KEYWORDS
// =====================================================

val KOTLIN_KEYWORDS =

    setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while",
        "actual",
        "abstract",
        "annotation",
        "companion",
        "const",
        "crossinline",
        "data",
        "enum",
        "expect",
        "external",
        "final",
        "infix",
        "inline",
        "inner",
        "internal",
        "lateinit",
        "noinline",
        "open",
        "operator",
        "out",
        "override",
        "private",
        "protected",
        "public",
        "reified",
        "sealed",
        "suspend",
        "tailrec",
        "vararg",
        "by",
        "catch",
        "constructor",
        "delegate",
        "dynamic",
        "field",
        "file",
        "finally",
        "get",
        "import",
        "init",
        "param",
        "property",
        "receiver",
        "set",
        "setparam",
        "where"
    )


// =====================================================
// SEARCH HELPER
// =====================================================

fun countOccurrences(
    text: String,
    query: String
): Int {


    if (
        query.isEmpty()
    ) {

        return 0
    }


    var count =
        0


    var start =
        0


    while (true) {


        val index =
            text.indexOf(
                query,
                start,
                ignoreCase =
                    true
            )


        if (
            index == -1
        ) {

            break
        }


        count++


        start =
            index +
                    query.length
    }


    return count
}


// =====================================================
// FILE READ
// =====================================================

fun readTextFromFile(
    context: Context,
    uri: Uri
): String? {


    return context
        .contentResolver
        .openInputStream(
            uri
        )
        ?.bufferedReader()
        ?.use {

            it.readText()
        }
}


// =====================================================
// FILE SAVE
// =====================================================

fun saveTextToFile(
    context: Context,
    uri: Uri,
    text: String
) {


    context
        .contentResolver
        .openOutputStream(
            uri,
            "wt"
        )
        ?.bufferedWriter()
        ?.use {


            it.write(
                text
            )
        }
}


// =====================================================
// GET FILE NAME
// =====================================================

fun getFileName(
    context: Context,
    uri: Uri
): String? {


    var name: String? =
        null


    context
        .contentResolver
        .query(
            uri,
            null,
            null,
            null,
            null
        )
        ?.use {


            if (
                it.moveToFirst()
            ) {


                val index =

                    it.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )


                if (
                    index >= 0
                ) {


                    name =
                        it.getString(
                            index
                        )
                }
            }
        }


    return name
}


// =====================================================
// RECENT FILE ADD
// =====================================================

fun addRecentFile(
    context: Context,
    fileName: String,
    uri: Uri
) {


    val files =

        loadRecentFiles(
            context
        )
            .toMutableList()


    files.removeAll {

        it.uri ==
                uri.toString()
    }


    files.add(

        0,

        RecentFile(
            fileName,
            uri.toString()
        )
    )


    saveRecentFiles(
        context,
        files.take(
            10
        )
    )
}


// =====================================================
// RECENT FILE SAVE
// =====================================================

fun saveRecentFiles(
    context: Context,
    files: List<RecentFile>
) {


    val array =
        JSONArray()


    files.forEach {


        array.put(

            JSONObject()
                .put(
                    "name",
                    it.name
                )
                .put(
                    "uri",
                    it.uri
                )
        )
    }


    context
        .getSharedPreferences(
            "editor_preferences",
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            "recent_files",
            array.toString()
        )
        .apply()
}


// =====================================================
// RECENT FILE LOAD
// =====================================================

fun loadRecentFiles(
    context: Context
): List<RecentFile> {


    val value =

        context
            .getSharedPreferences(
                "editor_preferences",
                Context.MODE_PRIVATE
            )
            .getString(
                "recent_files",
                null
            )
            ?: return emptyList()


    return try {


        val array =
            JSONArray(
                value
            )


        buildList {


            for (
            i in
            0 until array.length()
            ) {


                val item =
                    array.getJSONObject(
                        i
                    )


                add(

                    RecentFile(
                        item.getString(
                            "name"
                        ),
                        item.getString(
                            "uri"
                        )
                    )
                )
            }
        }


    } catch (
        _: Exception
    ) {


        emptyList()
    }
}


// =====================================================
// SAVE CRASH RECOVERY
// =====================================================

fun saveRecoveryDraft(
    context: Context,
    fileName: String,
    text: String
) {


    context
        .getSharedPreferences(
            "editor_recovery",
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            "file_name",
            fileName
        )
        .putString(
            "recovery_text",
            text
        )
        .putLong(
            "saved_at",
            System.currentTimeMillis()
        )
        .apply()
}


// =====================================================
// LOAD CRASH RECOVERY
// =====================================================

fun loadRecoveryDraft(
    context: Context
): RecoveryDraft? {


    val preferences =

        context
            .getSharedPreferences(
                "editor_recovery",
                Context.MODE_PRIVATE
            )


    val text =

        preferences.getString(
            "recovery_text",
            null
        )
            ?: return null


    return RecoveryDraft(

        preferences.getString(
            "file_name",
            "untitled.txt"
        )
            ?: "untitled.txt",

        text,

        preferences.getLong(
            "saved_at",
            0L
        )
    )
}


// =====================================================
// CLEAR CRASH RECOVERY
// =====================================================

fun clearRecoveryDraft(
    context: Context
) {


    context
        .getSharedPreferences(
            "editor_recovery",
            Context.MODE_PRIVATE
        )
        .edit()
        .clear()
        .apply()
}


// =====================================================
// PREVIEW
// =====================================================

@Preview(
    showBackground = true
)
@Composable
fun EditorScreenPreview() {


    ModernTextEditorTheme {

        EditorScreen()
    }
}