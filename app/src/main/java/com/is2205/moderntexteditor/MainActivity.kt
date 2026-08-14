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
import com.is2205.moderntexteditor.ui.theme.ModernTextEditorTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject


// =====================================================
// RECENT FILE MODEL
// =====================================================

data class RecentFile(
    val name: String,
    val uri: String
)


// =====================================================
// RECOVERY DRAFT MODEL
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
// MAIN EDITOR SCREEN
// =====================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {

    val context = LocalContext.current


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


    // =================================================
    // UNSAVED / CRASH RECOVERY STATE
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
    // MORE MENU
    // =================================================

    var showMoreMenu by remember {
        mutableStateOf(false)
    }


    // =================================================
    // RECENT FILES
    // =================================================

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

    val fileType = getFileType(fileName)

    val lineCount =
        if (editorText.isEmpty()) {
            1
        } else {
            editorText.count { it == '\n' } + 1
        }


    // =================================================
    // UPDATE EDITOR TEXT
    // =================================================

    fun updateEditorText(newText: String) {

        if (newText != editorText) {

            undoStack.add(editorText)

            if (undoStack.size > 100) {
                undoStack.removeAt(0)
            }

            redoStack.clear()

            editorText = newText

            isDirty = true

            statusMessage = "Editing"
        }
    }


    // =================================================
    // AUTO-SAVE CRASH RECOVERY EVERY 10 SECONDS
    // =================================================

    val latestEditorText by rememberUpdatedState(editorText)

    val latestFileName by rememberUpdatedState(fileName)

    val latestIsDirty by rememberUpdatedState(isDirty)


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

                    clearRecoveryDraft(context)
                }
            }
        }
    }


    // =================================================
    // OPEN FILE
    // =================================================

    val openFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {

                    try {

                        context.contentResolver
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

                        editorText = text

                        undoStack.clear()
                        redoStack.clear()

                        fileName =
                            getFileName(
                                context,
                                uri
                            ) ?: "unknown.txt"

                        currentFileUri = uri

                        isDirty = false

                        clearRecoveryDraft(context)

                        recoveryDraft = null


                        addRecentFile(
                            context,
                            fileName,
                            uri
                        )


                        recentFiles =
                            loadRecentFiles(context)


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
                ActivityResultContracts.CreateDocument("*/*")
        ) { uri ->

            if (uri != null) {

                try {

                    saveTextToFile(
                        context,
                        uri,
                        editorText
                    )


                    currentFileUri = uri


                    fileName =
                        getFileName(
                            context,
                            uri
                        ) ?: fileName


                    addRecentFile(
                        context,
                        fileName,
                        uri
                    )


                    recentFiles =
                        loadRecentFiles(context)


                    isDirty = false

                    clearRecoveryDraft(context)

                    recoveryDraft = null


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
                    loadRecentFiles(context)


                isDirty = false

                clearRecoveryDraft(context)

                recoveryDraft = null


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
                            text = "Modern Text Editor",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )


                        Text(
                            text =
                                if (isDirty) {
                                    "$fileName *"
                                } else {
                                    fileName
                                },

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                },

                actions = {


                    // =================================================
                    // SAVE BUTTON
                    // =================================================

                    TextButton(

                        onClick = {

                            saveCurrentFile()
                        }

                    ) {

                        Text("Save")
                    }


                    // =================================================
                    // MORE MENU
                    // =================================================

                    Box {


                        TextButton(

                            onClick = {

                                showMoreMenu = true
                            }

                        ) {

                            Text("More")
                        }


                        DropdownMenu(

                            expanded =
                                showMoreMenu,

                            onDismissRequest = {

                                showMoreMenu =
                                    false
                            }

                        ) {


                            // -----------------------------------------
                            // NEW FILE
                            // -----------------------------------------

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "New File"
                                    )
                                },

                                onClick = {

                                    showMoreMenu =
                                        false


                                    editorText = ""

                                    fileName =
                                        "untitled.txt"

                                    currentFileUri =
                                        null


                                    undoStack.clear()
                                    redoStack.clear()


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


                            // -----------------------------------------
                            // OPEN FILE
                            // -----------------------------------------

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


                            // -----------------------------------------
                            // RECENT FILES
                            // -----------------------------------------

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


                            // -----------------------------------------
                            // SAVE AS
                            // -----------------------------------------

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


                            // -----------------------------------------
                            // FIND / REPLACE
                            // -----------------------------------------

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


                            // -----------------------------------------
                            // UNDO
                            // -----------------------------------------

                            DropdownMenuItem(

                                text = {

                                    Text("Undo")
                                },

                                onClick = {

                                    showMoreMenu =
                                        false


                                    if (
                                        undoStack
                                            .isNotEmpty()
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


                            // -----------------------------------------
                            // REDO
                            // -----------------------------------------

                            DropdownMenuItem(

                                text = {

                                    Text("Redo")
                                },

                                onClick = {

                                    showMoreMenu =
                                        false


                                    if (
                                        redoStack
                                            .isNotEmpty()
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


                            // -----------------------------------------
                            // WORD WRAP
                            // -----------------------------------------

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
            // FILE INFO CARD
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

                            fileName = it
                        },

                        label = {

                            Text(
                                "File name"
                            )
                        },

                        singleLine =
                            true,

                        modifier =
                            Modifier
                                .fillMaxWidth()
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


                        if (isDirty) {

                            Text(

                                text =
                                    "Unsaved",

                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,

                                fontWeight =
                                    FontWeight.Bold
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
                        Arrangement
                            .SpaceBetween,

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
                                fileType,

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
                        "The editor found an auto-saved document that was not saved normally."
                    )


                    Spacer(

                        modifier =
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

                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )


                    Text(
                        "Preview:"
                    )


                    Spacer(

                        modifier =
                            Modifier.height(
                                4.dp
                            )
                    )


                    Text(

                        text =

                            if (
                                recoveryDraft!!
                                    .text
                                    .length >
                                200
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


                        if (
                            draft != null
                        ) {


                            editorText =
                                draft.text


                            fileName =
                                draft.fileName


                            currentFileUri =
                                null


                            undoStack.clear()
                            redoStack.clear()


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
    // RECENT FILES DIALOG
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


                                                if (
                                                    text != null
                                                ) {


                                                    editorText =
                                                        text


                                                    undoStack.clear()
                                                    redoStack.clear()


                                                    fileName =
                                                        recentFile.name


                                                    currentFileUri =
                                                        uri


                                                    isDirty =
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
    // FIND / REPLACE DIALOG
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

                        modifier =
                            Modifier.height(
                                16.dp
                            )
                    )


                    // FIND TEXT

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

                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )


                    // REPLACE TEXT

                    OutlinedTextField(

                        value =
                            replaceText,

                        onValueChange = {

                            replaceText =
                                it
                        },

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

                        modifier =
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

                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )
                    }


                    // FIND / REPLACE FIRST

                    Row(

                        modifier =
                            Modifier.fillMaxWidth()

                    ) {


                        TextButton(

                            onClick = {


                                if (
                                    searchText
                                        .isBlank()
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


                                if (
                                    searchText
                                        .isBlank()
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
                                            editorText.replaceRange(
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


                    // REPLACE ALL / CLOSE

                    Row(

                        modifier =
                            Modifier.fillMaxWidth()

                    ) {


                        TextButton(

                            onClick = {


                                if (
                                    searchText
                                        .isBlank()
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
// GET FILE TYPE
// =====================================================

fun getFileType(
    fileName: String
): String {


    return when {


        fileName.endsWith(
            ".kt",
            ignoreCase = true
        ) -> {

            "Kotlin"
        }


        fileName.endsWith(
            ".md",
            ignoreCase = true
        ) ||
                fileName.endsWith(
                    ".markdown",
                    ignoreCase = true
                ) -> {

            "Markdown"
        }


        fileName.endsWith(
            ".txt",
            ignoreCase = true
        ) -> {

            "Text"
        }


        else -> {

            "Plain Text"
        }
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

        modifier =
            modifier

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


        if (
            wordWrapEnabled
        ) {


            TextField(

                value =
                    text,

                onValueChange =
                    onTextChange,

                placeholder = {

                    Text(
                        "Start typing here..."
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

                    placeholder = {

                        Text(
                            "Start typing here..."
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
// KOTLIN SYNTAX HIGHLIGHTING
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


        var index = 0


        while (
            index <
            source.length
        ) {


            // SINGLE-LINE COMMENT

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


            // BLOCK COMMENT

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


            // TRIPLE-QUOTED STRING

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


            // NORMAL STRING

            if (
                source[index] ==
                '"'
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


            // CHARACTER

            if (
                source[index] ==
                '\''
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


            // ANNOTATION

            if (
                source[index] ==
                '@'
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
                                FontWeight
                                    .SemiBold
                        ),

                        index,

                        end
                    )


                    index =
                        end


                    continue
                }
            }


            // KEYWORDS

            if (
                source[index]
                    .isLetter() ||
                source[index] ==
                '_'
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

            builder
                .toAnnotatedString(),

            OffsetMapping.Identity
        )
    }
}


// =====================================================
// MARKDOWN SYNTAX HIGHLIGHTING
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


        // HEADINGS

        Regex(
            """(?m)^(#{1,6})[ \t]+.*$"""
        )
            .findAll(source)
            .forEach { match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            headingColor,

                        fontWeight =
                            FontWeight.Bold
                    ),

                    match.range.first,

                    match.range.last +
                            1
                )
            }


        // BLOCK QUOTES

        Regex(
            """(?m)^[ \t]*>[ \t]?.*$"""
        )
            .findAll(source)
            .forEach { match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            quoteColor,

                        fontStyle =
                            FontStyle.Italic
                    ),

                    match.range.first,

                    match.range.last +
                            1
                )
            }


        // UNORDERED LISTS

        Regex(
            """(?m)^[ \t]*[-+*][ \t]+"""
        )
            .findAll(source)
            .forEach { match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            listColor,

                        fontWeight =
                            FontWeight.Bold
                    ),

                    match.range.first,

                    match.range.last +
                            1
                )
            }


        // ORDERED LISTS

        Regex(
            """(?m)^[ \t]*\d+\.[ \t]+"""
        )
            .findAll(source)
            .forEach { match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            listColor,

                        fontWeight =
                            FontWeight.Bold
                    ),

                    match.range.first,

                    match.range.last +
                            1
                )
            }


        // LINKS

        Regex(
            """\[[^\]\n]+\]\([^\)\n]+\)"""
        )
            .findAll(source)
            .forEach { match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            linkColor,

                        textDecoration =
                            TextDecoration.Underline
                    ),

                    match.range.first,

                    match.range.last +
                            1
                )
            }


        // BOLD

        Regex(
            """\*\*[^*\n]+\*\*|__[^_\n]+__"""
        )
            .findAll(source)
            .forEach { match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            boldColor,

                        fontWeight =
                            FontWeight.Bold
                    ),

                    match.range.first,

                    match.range.last +
                            1
                )
            }


        // ITALIC

        Regex(
            """(?<!\*)\*[^*\n]+\*(?!\*)|(?<!_)_[^_\n]+_(?!_)"""
        )
            .findAll(source)
            .forEach { match ->

                builder.addStyle(

                    SpanStyle(
                        color =
                            italicColor,

                        fontStyle =
                            FontStyle.Italic
                    ),

                    match.range.first,

                    match.range.last +
                            1
                )
            }


        // INLINE CODE

        Regex(
            """`[^`\n]+`"""
        )
            .findAll(source)
            .forEach { match ->

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

                    match.range.last +
                            1
                )
            }


        // FENCED CODE BLOCK

        Regex(
            """```[\s\S]*?```"""
        )
            .findAll(source)
            .forEach { match ->

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

                    match.range.last +
                            1
                )
            }


        return TransformedText(

            builder
                .toAnnotatedString(),

            OffsetMapping.Identity
        )
    }
}


// =====================================================
// FIND END OF QUOTED TEXT
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
        index <
        text.length
    ) {


        val current =
            text[index]


        if (escaped) {

            escaped =
                false

        } else {


            if (
                current ==
                '\\'
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
// COUNT SEARCH OCCURRENCES
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


    var startIndex =
        0


    while (true) {


        val index =
            text.indexOf(

                query,

                startIndex,

                ignoreCase =
                    true
            )


        if (
            index == -1
        ) {

            break
        }


        count++


        startIndex =
            index +
                    query.length
    }


    return count
}


// =====================================================
// READ TEXT FILE
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
// SAVE TEXT FILE
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

                writer ->


            writer.write(
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
                    OpenableColumns
                        .DISPLAY_NAME
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
// ADD RECENT FILE
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


    val limitedList =
        recentFiles.take(
            10
        )


    saveRecentFiles(

        context,

        limitedList
    )
}


// =====================================================
// SAVE RECENT FILES
// =====================================================

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

            jsonArray
                .toString()
        )
        .apply()
}


// =====================================================
// LOAD RECENT FILES
// =====================================================

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
        preferences
            .getString(

                "recent_files",

                null
            )

            ?: return emptyList()


    val recentFiles =
        mutableListOf<RecentFile>()


    try {


        val jsonArray =
            JSONArray(
                json
            )


        for (
        i in
        0 until
                jsonArray.length()
        ) {


            val objectItem =
                jsonArray
                    .getJSONObject(
                        i
                    )


            recentFiles.add(


                RecentFile(


                    name =
                        objectItem
                            .getString(
                                "name"
                            ),


                    uri =
                        objectItem
                            .getString(
                                "uri"
                            )
                )
            )
        }


    } catch (
        _: Exception
    ) {


        return emptyList()
    }


    return recentFiles
}


// =====================================================
// SAVE CRASH RECOVERY DRAFT
// =====================================================

fun saveRecoveryDraft(

    context: Context,

    fileName: String,

    text: String

) {


    val preferences =
        context
            .getSharedPreferences(

                "editor_recovery",

                Context.MODE_PRIVATE
            )


    preferences
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
// LOAD CRASH RECOVERY DRAFT
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
        preferences
            .getString(

                "recovery_text",

                null
            )

            ?: return null


    val fileName =
        preferences
            .getString(

                "file_name",

                "untitled.txt"
            )

            ?: "untitled.txt"


    val savedAt =
        preferences
            .getLong(

                "saved_at",

                0L
            )


    return RecoveryDraft(

        fileName =
            fileName,

        text =
            text,

        savedAt =
            savedAt
    )
}


// =====================================================
// CLEAR CRASH RECOVERY DRAFT
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