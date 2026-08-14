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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.is2205.moderntexteditor.database.DocumentDao
import com.is2205.moderntexteditor.database.DocumentEntity
import com.is2205.moderntexteditor.database.EditorDatabase
import com.is2205.moderntexteditor.ui.theme.ModernTextEditorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


// =====================================================
// RECENT FILE MODEL
// =====================================================

data class RecentFile(
    val name: String,
    val uri: String
)


// =====================================================
// RECOVERY MODEL
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            ModernTextEditorTheme {

                EditorScreen()
            }
        }
    }
}


// =====================================================
// EDITOR SCREEN
// =====================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {

    val context = LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()

    val database =
        remember {
            EditorDatabase.getDatabase(context)
        }

    val documentDao =
        remember {
            database.documentDao()
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

    /*
     * A saved/opened document uses its URI as the key.
     * An unsaved document receives a temporary unique key.
     */
    var documentKey by remember {
        mutableStateOf(
            "draft:${UUID.randomUUID()}"
        )
    }


    // =================================================
    // READ-ONLY MODE
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
        mutableStateOf(loadRecoveryDraft(context))
    }

    var showRecoveryDialog by remember {
        mutableStateOf(recoveryDraft != null)
    }


    // =================================================
    // UI STATES
    // =================================================

    var showMoreMenu by remember {
        mutableStateOf(false)
    }

    var showRecentDialog by remember {
        mutableStateOf(false)
    }

    var recentFiles by remember {
        mutableStateOf(loadRecentFiles(context))
    }


    // =================================================
    // SEARCH / REPLACE
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

    val undoStack = remember {
        mutableListOf<String>()
    }

    val redoStack = remember {
        mutableListOf<String>()
    }


    // =================================================
    // FILE INFORMATION
    // =================================================

    val fileType =
        getFileType(fileName)

    val lineCount =
        if (editorText.isEmpty()) {

            1

        } else {

            editorText.count {
                it == '\n'
            } + 1
        }


    // =================================================
    // TEXT UPDATE
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
    // AUTO RECOVERY
    // =================================================

    val latestEditorText by
    rememberUpdatedState(editorText)

    val latestFileName by
    rememberUpdatedState(fileName)

    val latestIsDirty by
    rememberUpdatedState(isDirty)


    LaunchedEffect(Unit) {

        while (true) {

            delay(10_000)

            if (latestIsDirty) {

                if (latestEditorText.isNotEmpty()) {

                    saveRecoveryDraft(
                        context = context,
                        fileName = latestFileName,
                        text = latestEditorText
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
                                    documentDao =
                                        documentDao,

                                    documentKey =
                                        openedKey,

                                    fileName =
                                        openedName,

                                    fileUri =
                                        uri.toString()
                                )

                            isReadOnly =
                                document.isReadOnly
                        }


                        statusMessage =
                            "File opened successfully"
                    }

                } catch (e: Exception) {

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
                            documentDao =
                                documentDao,

                            documentKey =
                                savedKey,

                            fileName =
                                savedName,

                            fileUri =
                                uri.toString(),

                            isReadOnly =
                                isReadOnly
                        )
                    }


                    statusMessage =
                        "File saved successfully"

                } catch (e: Exception) {

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
                        documentDao =
                            documentDao,

                        documentKey =
                            documentKey,

                        fileName =
                            fileName,

                        fileUri =
                            currentFileUri
                                ?.toString(),

                        isReadOnly =
                            isReadOnly
                    )
                }


                statusMessage =
                    "File saved"

            } catch (e: Exception) {

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
                documentDao =
                    documentDao,

                documentKey =
                    documentKey,

                fileName =
                    fileName,

                fileUri =
                    currentFileUri
                        ?.toString(),

                isReadOnly =
                    newValue
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

                                    append(fileName)

                                    if (isDirty) {
                                        append(" *")
                                    }

                                    if (isReadOnly) {
                                        append(" [Read Only]")
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


                    // SAVE

                    TextButton(

                        onClick = {

                            saveCurrentFile()
                        }

                    ) {

                        Text(
                            "Save"
                        )
                    }


                    // MORE

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


                            // OPEN

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


                            // RECENT

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

                                        if (wordWrapEnabled) {

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


                            // READ ONLY

                            DropdownMenuItem(

                                text = {

                                    Text(

                                        if (isReadOnly) {

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
                    .padding(innerPadding)
                    .padding(12.dp)

        ) {


            // =================================================
            // FILE INFO
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

                                fileName = it

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
                            fileType =
                                fileType
                        )


                        Text(

                            text =

                                if (
                                    currentFileUri ==
                                    null
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
                                horizontal = 12.dp,
                                vertical = 8.dp
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

                                    "$fileType | Read Only"

                                } else {

                                    "$fileType | Editable"
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
                        Modifier.height(12.dp)
                    )


                    Text(

                        text =
                            "File: ${recoveryDraft!!.fileName}",

                        fontWeight =
                            FontWeight.Bold
                    )


                    Spacer(
                        Modifier.height(8.dp)
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


                    if (recentFiles.isEmpty()) {

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
                                                                documentDao,
                                                                key,
                                                                recentFile.name,
                                                                uri.toString()
                                                            )

                                                        isReadOnly =
                                                            document.isReadOnly
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
                        .padding(16.dp)

            ) {


                Column(

                    modifier =
                        Modifier.padding(20.dp)

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
                        Modifier.height(16.dp)
                    )


                    OutlinedTextField(

                        value =
                            searchText,

                        onValueChange = {

                            searchText = it

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
                        Modifier.height(8.dp)
                    )


                    OutlinedTextField(

                        value =
                            replaceText,

                        onValueChange = {

                            replaceText = it
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
                        Modifier.height(12.dp)
                    )


                    if (
                        searchResultMessage
                            .isNotEmpty()
                    ) {

                        Text(
                            searchResultMessage
                        )

                        Spacer(
                            Modifier.height(8.dp)
                        )
                    }


                    Row {


                        // FIND

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

                                        if (count > 0) {

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


                        // REPLACE

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
                                            ignoreCase = true
                                        )


                                    if (index >= 0) {

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


                        // REPLACE ALL

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


                                    if (count > 0) {

                                        val newText =
                                            editorText.replace(
                                                searchText,
                                                replaceText,
                                                ignoreCase = true
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
            RoundedCornerShape(50),

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
                    horizontal = 10.dp,
                    vertical = 5.dp
                ),

            style =
                MaterialTheme
                    .typography
                    .labelMedium
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
            ignoreCase = true
        ) -> "Kotlin"


        fileName.endsWith(
            ".md",
            ignoreCase = true
        ) ||
                fileName.endsWith(
                    ".markdown",
                    ignoreCase = true
                ) -> "Markdown"


        fileName.endsWith(
            ".txt",
            ignoreCase = true
        ) -> "Text"


        else -> "Plain Text"
    }
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


    val isKotlinFile =
        fileName.endsWith(
            ".kt",
            ignoreCase = true
        )


    val isMarkdownFile =
        fileName.endsWith(
            ".md",
            ignoreCase = true
        ) ||
                fileName.endsWith(
                    ".markdown",
                    ignoreCase = true
                )


    val syntaxTransformation:
            VisualTransformation =

        when {

            isKotlinFile ->

                KotlinSyntaxVisualTransformation(

                    keywordColor =
                        MaterialTheme
                            .colorScheme
                            .primary,

                    stringColor =
                        MaterialTheme
                            .colorScheme
                            .tertiary,

                    commentColor =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,

                    annotationColor =
                        MaterialTheme
                            .colorScheme
                            .secondary
                )


            isMarkdownFile ->

                MarkdownSyntaxVisualTransformation(

                    headingColor =
                        MaterialTheme
                            .colorScheme
                            .primary,

                    boldColor =
                        MaterialTheme
                            .colorScheme
                            .secondary,

                    italicColor =
                        MaterialTheme
                            .colorScheme
                            .tertiary,

                    codeColor =
                        MaterialTheme
                            .colorScheme
                            .tertiary,

                    codeBackgroundColor =
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant,

                    linkColor =
                        MaterialTheme
                            .colorScheme
                            .primary,

                    quoteColor =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,

                    listColor =
                        MaterialTheme
                            .colorScheme
                            .secondary
                )


            else ->

                VisualTransformation.None
        }


    BoxWithConstraints(
        modifier = modifier
    ) {

        val calculatedWidth =
            (
                    longestLineLength *
                            12 +
                            80
                    ).dp


        val noWrapWidth =

            if (
                calculatedWidth >
                maxWidth
            ) {

                calculatedWidth

            } else {

                maxWidth
            }


        if (wordWrapEnabled) {

            TextField(

                value =
                    text,

                onValueChange =
                    onTextChange,

                readOnly =
                    isReadOnly,

                placeholder = {

                    Text(
                        if (isReadOnly) {
                            "Read-only document"
                        } else {
                            "Start typing here..."
                        }
                    )
                },

                textStyle =
                    MaterialTheme
                        .typography
                        .bodyLarge
                        .copy(
                            fontFamily =
                                FontFamily.Monospace
                        ),

                visualTransformation =
                    syntaxTransformation,

                modifier =
                    Modifier.fillMaxSize()
            )

        } else {

            Box(

                modifier =
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

                    placeholder = {

                        Text(
                            if (isReadOnly) {
                                "Read-only document"
                            } else {
                                "Start typing here..."
                            }
                        )
                    },

                    textStyle =
                        MaterialTheme
                            .typography
                            .bodyLarge
                            .copy(
                                fontFamily =
                                    FontFamily.Monospace
                            ),

                    visualTransformation =
                        syntaxTransformation,

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
// KOTLIN HIGHLIGHTER
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
            index < source.length
        ) {


            if (
                source.startsWith(
                    "//",
                    index
                )
            ) {

                val lineEnd =
                    source.indexOf(
                        '\n',
                        index
                    )


                val end =

                    if (
                        lineEnd == -1
                    ) {

                        source.length

                    } else {

                        lineEnd
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
                    end > index + 1
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
// MARKDOWN HIGHLIGHTER
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


        Regex(
            """(?m)^(#{1,6})[ \t]+.*$"""
        )
            .findAll(source)
            .forEach {

                    match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            headingColor,

                        fontWeight =
                            FontWeight.Bold
                    ),

                    match.range.first,
                    match.range.last + 1
                )
            }


        Regex(
            """(?m)^[ \t]*>[ \t]?.*$"""
        )
            .findAll(source)
            .forEach {

                    match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            quoteColor,

                        fontStyle =
                            FontStyle.Italic
                    ),

                    match.range.first,
                    match.range.last + 1
                )
            }


        Regex(
            """(?m)^[ \t]*[-+*][ \t]+"""
        )
            .findAll(source)
            .forEach {

                    match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            listColor,

                        fontWeight =
                            FontWeight.Bold
                    ),

                    match.range.first,
                    match.range.last + 1
                )
            }


        Regex(
            """(?m)^[ \t]*\d+\.[ \t]+"""
        )
            .findAll(source)
            .forEach {

                    match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            listColor,

                        fontWeight =
                            FontWeight.Bold
                    ),

                    match.range.first,
                    match.range.last + 1
                )
            }


        Regex(
            """\[[^\]\n]+\]\([^\)\n]+\)"""
        )
            .findAll(source)
            .forEach {

                    match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            linkColor,

                        textDecoration =
                            TextDecoration.Underline
                    ),

                    match.range.first,
                    match.range.last + 1
                )
            }


        Regex(
            """\*\*[^*\n]+\*\*|__[^_\n]+__"""
        )
            .findAll(source)
            .forEach {

                    match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            boldColor,

                        fontWeight =
                            FontWeight.Bold
                    ),

                    match.range.first,
                    match.range.last + 1
                )
            }


        Regex(
            """(?<!\*)\*[^*\n]+\*(?!\*)|(?<!_)_[^_\n]+_(?!_)"""
        )
            .findAll(source)
            .forEach {

                    match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            italicColor,

                        fontStyle =
                            FontStyle.Italic
                    ),

                    match.range.first,
                    match.range.last + 1
                )
            }


        Regex(
            """`[^`\n]+`"""
        )
            .findAll(source)
            .forEach {

                    match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            codeColor,

                        background =
                            codeBackgroundColor,

                        fontFamily =
                            FontFamily.Monospace
                    ),

                    match.range.first,
                    match.range.last + 1
                )
            }


        Regex(
            """```[\s\S]*?```"""
        )
            .findAll(source)
            .forEach {

                    match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            codeColor,

                        background =
                            codeBackgroundColor,

                        fontFamily =
                            FontFamily.Monospace
                    ),

                    match.range.first,
                    match.range.last + 1
                )
            }


        return TransformedText(

            builder.toAnnotatedString(),

            OffsetMapping.Identity
        )
    }
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


    if (existing != null) {

        return existing
    }


    val newDocument =
        DocumentEntity(

            documentKey =
                documentKey,

            fileName =
                fileName,

            fileUri =
                fileUri,

            isReadOnly =
                false
        )


    documentDao.upsertDocument(
        newDocument
    )


    return newDocument
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


    val currentTime =
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
                    ?: currentTime,

            updatedAt =
                currentTime
        )


    documentDao.upsertDocument(
        document
    )
}


// =====================================================
// QUOTE HELPER
// =====================================================

fun findQuotedTextEnd(

    text: String,

    startIndex: Int,

    quoteCharacter: Char

): Int {

    var index =
        startIndex + 1

    var escaped =
        false


    while (
        index < text.length
    ) {

        val current =
            text[index]


        if (escaped) {

            escaped =
                false

        } else {

            if (
                current == '\\'
            ) {

                escaped =
                    true

            } else if (
                current ==
                quoteCharacter
            ) {

                return index + 1
            }
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
// SEARCH
// =====================================================

fun countOccurrences(

    text: String,

    query: String

): Int {

    if (query.isEmpty()) {

        return 0
    }


    var count =
        0

    var startIndex =
        0


    while (true) {

        val index =
            text.indexOf(
                query,
                startIndex,
                ignoreCase = true
            )


        if (
            index == -1
        ) {

            break
        }


        count++

        startIndex =
            index + query.length
    }


    return count
}


// =====================================================
// FILE FUNCTIONS
// =====================================================

fun readTextFromFile(

    context: Context,

    uri: Uri

): String? {

    return context
        .contentResolver
        .openInputStream(uri)
        ?.bufferedReader()
        ?.use {
            it.readText()
        }
}


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

                writer ->

            writer.write(
                text
            )
        }
}


fun getFileName(

    context: Context,

    uri: Uri

): String? {

    var name: String? =
        null


    val cursor =
        context
            .contentResolver
            .query(
                uri,
                null,
                null,
                null,
                null
            )


    cursor?.use {

        if (
            it.moveToFirst()
        ) {

            val nameIndex =
                it.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )


            if (
                nameIndex >= 0
            ) {

                name =
                    it.getString(
                        nameIndex
                    )
            }
        }
    }


    return name
}


// =====================================================
// RECENT FILES
// =====================================================

fun addRecentFile(

    context: Context,

    fileName: String,

    uri: Uri

) {

    val recentFiles =
        loadRecentFiles(
            context
        )
            .toMutableList()


    recentFiles.removeAll {

        it.uri ==
                uri.toString()
    }


    recentFiles.add(

        0,

        RecentFile(
            name =
                fileName,

            uri =
                uri.toString()
        )
    )


    saveRecentFiles(
        context,
        recentFiles.take(10)
    )
}


fun saveRecentFiles(

    context: Context,

    files: List<RecentFile>

) {

    val preferences =
        context
            .getSharedPreferences(
                "editor_preferences",
                Context.MODE_PRIVATE
            )


    val jsonArray =
        JSONArray()


    files.forEach {

            file ->

        val jsonObject =
            JSONObject()


        jsonObject.put(
            "name",
            file.name
        )

        jsonObject.put(
            "uri",
            file.uri
        )


        jsonArray.put(
            jsonObject
        )
    }


    preferences
        .edit()
        .putString(
            "recent_files",
            jsonArray.toString()
        )
        .apply()
}


fun loadRecentFiles(

    context: Context

): List<RecentFile> {

    val preferences =
        context
            .getSharedPreferences(
                "editor_preferences",
                Context.MODE_PRIVATE
            )


    val json =
        preferences.getString(
            "recent_files",
            null
        )
            ?: return emptyList()


    val recentFiles =
        mutableListOf<RecentFile>()


    try {

        val jsonArray =
            JSONArray(json)


        for (
        i in
        0 until jsonArray.length()
        ) {

            val item =
                jsonArray
                    .getJSONObject(i)


            recentFiles.add(

                RecentFile(

                    name =
                        item.getString(
                            "name"
                        ),

                    uri =
                        item.getString(
                            "uri"
                        )
                )
            )
        }

    } catch (_: Exception) {

        return emptyList()
    }


    return recentFiles
}


// =====================================================
// CRASH RECOVERY
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


    val fileName =
        preferences.getString(
            "file_name",
            "untitled.txt"
        )
            ?: "untitled.txt"


    return RecoveryDraft(

        fileName =
            fileName,

        text =
            text,

        savedAt =
            preferences.getLong(
                "saved_at",
                0L
            )
    )
}


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