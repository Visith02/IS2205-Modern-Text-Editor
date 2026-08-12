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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.is2205.moderntexteditor.ui.theme.ModernTextEditorTheme
import org.json.JSONArray
import org.json.JSONObject


// ====================================================
// RECENT FILE
// ====================================================

data class RecentFile(
    val name: String,
    val uri: String
)


// ====================================================
// MAIN ACTIVITY
// ====================================================

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


// ====================================================
// MAIN EDITOR SCREEN
// ====================================================

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
    // RECENT FILE STATE
    // =================================================

    var showRecentDialog by remember {
        mutableStateOf(false)
    }

    var recentFiles by remember {
        mutableStateOf(loadRecentFiles(context))
    }


    // =================================================
    // SEARCH / REPLACE STATE
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
    // WORD WRAP STATE
    // =================================================

    var wordWrapEnabled by remember {
        mutableStateOf(true)
    }


    // =================================================
    // UNDO / REDO STACKS
    // =================================================

    val undoStack = remember {
        mutableListOf<String>()
    }

    val redoStack = remember {
        mutableListOf<String>()
    }


    // =================================================
    // UPDATE EDITOR TEXT
    // =================================================

    fun updateEditorText(newText: String) {

        if (newText != editorText) {

            undoStack.add(editorText)

            // Keep only 100 previous states
            if (undoStack.size > 100) {

                undoStack.removeAt(0)
            }

            // New editing clears redo history
            redoStack.clear()

            editorText = newText

            statusMessage = "Editing"
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

                        context.contentResolver.takePersistableUriPermission(

                            uri,

                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )

                    } catch (_: Exception) {

                        // Some file providers may not support
                        // persistent read/write access.
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


                        addRecentFile(
                            context = context,
                            fileName = fileName,
                            uri = uri
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
                        context = context,
                        uri = uri,
                        text = editorText
                    )


                    currentFileUri = uri


                    fileName =
                        getFileName(
                            context,
                            uri
                        ) ?: fileName


                    addRecentFile(
                        context = context,
                        fileName = fileName,
                        uri = uri
                    )


                    recentFiles =
                        loadRecentFiles(context)


                    statusMessage =
                        "File saved successfully"

                } catch (e: Exception) {

                    statusMessage =
                        "Unable to save file"
                }
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

                    Text(
                        "Modern Text Editor"
                    )
                },

                actions = {


                    // ---------------------------------
                    // NEW
                    // ---------------------------------

                    TextButton(

                        onClick = {

                            editorText = ""

                            fileName =
                                "untitled.txt"

                            currentFileUri =
                                null

                            undoStack.clear()
                            redoStack.clear()

                            statusMessage =
                                "New file created"
                        }

                    ) {

                        Text("New")
                    }


                    // ---------------------------------
                    // OPEN
                    // ---------------------------------

                    TextButton(

                        onClick = {

                            openFileLauncher.launch(

                                arrayOf(
                                    "text/plain",
                                    "text/markdown",
                                    "application/octet-stream"
                                )
                            )
                        }

                    ) {

                        Text("Open")
                    }


                    // ---------------------------------
                    // RECENT
                    // ---------------------------------

                    TextButton(

                        onClick = {

                            recentFiles =
                                loadRecentFiles(
                                    context
                                )

                            showRecentDialog =
                                true
                        }

                    ) {

                        Text("Recent")
                    }


                    // ---------------------------------
                    // SAVE
                    // ---------------------------------

                    TextButton(

                        onClick = {

                            if (
                                currentFileUri != null
                            ) {

                                try {

                                    saveTextToFile(

                                        context =
                                            context,

                                        uri =
                                            currentFileUri!!,

                                        text =
                                            editorText
                                    )


                                    addRecentFile(

                                        context =
                                            context,

                                        fileName =
                                            fileName,

                                        uri =
                                            currentFileUri!!
                                    )


                                    recentFiles =
                                        loadRecentFiles(
                                            context
                                        )


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

                    ) {

                        Text("Save")
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
            // FILE NAME
            // =================================================

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
                        .padding(
                            bottom = 8.dp
                        )
            )


            // =================================================
            // SAVE AS + FIND / REPLACE
            // =================================================

            Row(

                modifier =
                    Modifier.fillMaxWidth()

            ) {


                TextButton(

                    onClick = {

                        saveAsLauncher.launch(
                            fileName
                        )
                    }

                ) {

                    Text(
                        "Save As"
                    )
                }


                TextButton(

                    onClick = {

                        searchResultMessage =
                            ""

                        showSearchDialog =
                            true
                    }

                ) {

                    Text(
                        "Find / Replace"
                    )
                }
            }


            // =================================================
            // UNDO + REDO + WORD WRAP
            // =================================================

            Row(

                modifier =
                    Modifier.fillMaxWidth()

            ) {


                // ---------------------------------
                // UNDO
                // ---------------------------------

                TextButton(

                    onClick = {

                        if (
                            undoStack.isNotEmpty()
                        ) {

                            redoStack.add(
                                editorText
                            )


                            editorText =
                                undoStack.removeAt(
                                    undoStack.lastIndex
                                )


                            statusMessage =
                                "Undo"

                        } else {

                            statusMessage =
                                "Nothing to undo"
                        }
                    }

                ) {

                    Text("Undo")
                }


                // ---------------------------------
                // REDO
                // ---------------------------------

                TextButton(

                    onClick = {

                        if (
                            redoStack.isNotEmpty()
                        ) {

                            undoStack.add(
                                editorText
                            )


                            editorText =
                                redoStack.removeAt(
                                    redoStack.lastIndex
                                )


                            statusMessage =
                                "Redo"

                        } else {

                            statusMessage =
                                "Nothing to redo"
                        }
                    }

                ) {

                    Text("Redo")
                }


                // ---------------------------------
                // WORD WRAP
                // ---------------------------------

                TextButton(

                    onClick = {

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

                ) {

                    Text(

                        if (
                            wordWrapEnabled
                        ) {

                            "Wrap: ON"

                        } else {

                            "Wrap: OFF"
                        }
                    )
                }
            }


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


            // =================================================
            // STATUS BAR
            // =================================================

            Row(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 8.dp
                        )

            ) {

                Text(

                    text =
                        statusMessage,

                    modifier =
                        Modifier.weight(1f)
                )


                val fileType =

                    when {

                        fileName.endsWith(
                            ".kt",
                            ignoreCase = true
                        ) -> "Kotlin"

                        fileName.endsWith(
                            ".md",
                            ignoreCase = true
                        ) -> "Markdown"

                        else -> "Text"
                    }


                Text(

                    text =
                        "$fileType | Characters: ${editorText.length}"
                )
            }
        }
    }


    // =================================================
    // RECENT FILES DIALOG
    // =================================================

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


    // =================================================
    // FIND / REPLACE DIALOG
    // =================================================

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


                    // ---------------------------------
                    // FIND
                    // ---------------------------------

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


                    // ---------------------------------
                    // REPLACE WITH
                    // ---------------------------------

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
                            text =
                                searchResultMessage
                        )


                        Spacer(

                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )
                    }


                    // ---------------------------------
                    // FIND + REPLACE
                    // ---------------------------------

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


                    // ---------------------------------
                    // REPLACE ALL + CLOSE
                    // ---------------------------------

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


// ====================================================
// EDITOR TEXT AREA
// ====================================================

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


    // ------------------------------------------------
    // CHECK WHETHER CURRENT FILE IS KOTLIN
    // ------------------------------------------------

    val isKotlinFile =

        fileName.endsWith(
            ".kt",
            ignoreCase = true
        )


    // ------------------------------------------------
    // KOTLIN SYNTAX HIGHLIGHTER
    // ------------------------------------------------

    val syntaxTransformation =

        if (isKotlinFile) {

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

        } else {

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


        // ------------------------------------------------
        // WORD WRAP ON
        // ------------------------------------------------

        if (wordWrapEnabled) {


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


            // ------------------------------------------------
            // WORD WRAP OFF
            // ------------------------------------------------

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


// ====================================================
// KOTLIN SYNTAX HIGHLIGHTER
// ====================================================

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
            index < source.length
        ) {


            // =================================================
            // SINGLE LINE COMMENT //
            // =================================================

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


            // =================================================
            // BLOCK COMMENT /* */
            // =================================================

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


            // =================================================
            // TRIPLE-QUOTED STRING
            // =================================================

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


            // =================================================
            // NORMAL STRING "..."
            // =================================================

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


            // =================================================
            // CHARACTER 'a'
            // =================================================

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


            // =================================================
            // ANNOTATION
            // Example: @Composable
            // =================================================

            if (
                source[index] == '@'
            ) {


                var end =
                    index + 1


                while (
                    end < source.length &&
                    (
                            source[end].isLetterOrDigit() ||
                                    source[end] == '_'
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


            // =================================================
            // IDENTIFIER / KOTLIN KEYWORD
            // =================================================

            if (
                source[index].isLetter() ||
                source[index] == '_'
            ) {


                var end =
                    index + 1


                while (
                    end < source.length &&
                    (
                            source[end]
                                .isLetterOrDigit() ||
                                    source[end] == '_'
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

            text =
                builder.toAnnotatedString(),

            offsetMapping =
                OffsetMapping.Identity
        )
    }
}


// ====================================================
// FIND END OF STRING / CHARACTER
// ====================================================

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


// ====================================================
// KOTLIN KEYWORDS
// ====================================================

val KOTLIN_KEYWORDS =

    setOf(

        // Basic keywords

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


        // Modifier keywords

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


        // Other Kotlin keywords

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


// ====================================================
// COUNT SEARCH RESULTS
// ====================================================

fun countOccurrences(

    text: String,

    query: String

): Int {


    if (
        query.isEmpty()
    ) {

        return 0
    }


    var count = 0

    var startIndex = 0


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


// ====================================================
// READ FILE
// ====================================================

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


// ====================================================
// SAVE FILE
// ====================================================

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


// ====================================================
// GET FILE NAME
// ====================================================

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


// ====================================================
// ADD RECENT FILE
// ====================================================

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


// ====================================================
// SAVE RECENT FILES
// ====================================================

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


// ====================================================
// LOAD RECENT FILES
// ====================================================

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


// ====================================================
// PREVIEW
// ====================================================

@Preview(
    showBackground = true
)
@Composable
fun EditorScreenPreview() {


    ModernTextEditorTheme {


        EditorScreen()
    }
}