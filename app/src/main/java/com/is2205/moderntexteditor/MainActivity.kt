package com.is2205.moderntexteditor

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.is2205.moderntexteditor.ui.theme.ModernTextEditorTheme

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {

    val context = LocalContext.current

    var fileName by remember {
        mutableStateOf("untitled.txt")
    }

    var editorText by remember {
        mutableStateOf("")
    }

    var statusMessage by remember {
        mutableStateOf("Ready")
    }

    // Stores the currently opened/saved file location.
    var currentFileUri by remember {
        mutableStateOf<Uri?>(null)
    }

    // -------------------------
    // OPEN FILE
    // -------------------------

    val openFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {

                    val text =
                        context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }

                    if (text != null) {

                        editorText = text

                        fileName =
                            getFileName(
                                context,
                                uri
                            ) ?: "unknown.txt"

                        currentFileUri = uri

                        statusMessage =
                            "File opened successfully"
                    }

                } catch (e: Exception) {

                    statusMessage =
                        "Unable to open file"
                }
            }
        }

    // -------------------------
    // SAVE AS
    // -------------------------

    val saveAsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(
                "*/*"
            )
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

                    statusMessage =
                        "File saved successfully"

                } catch (e: Exception) {

                    statusMessage =
                        "Unable to save file"
                }
            }
        }

    Scaffold(

        modifier = Modifier.fillMaxSize(),

        topBar = {

            TopAppBar(

                title = {
                    Text("Modern Text Editor")
                },

                actions = {

                    // NEW
                    TextButton(
                        onClick = {

                            editorText = ""

                            fileName =
                                "untitled.txt"

                            currentFileUri =
                                null

                            statusMessage =
                                "New file created"
                        }
                    ) {
                        Text("New")
                    }

                    // OPEN
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

                    // SAVE
                    TextButton(
                        onClick = {

                            if (currentFileUri != null) {

                                try {

                                    saveTextToFile(
                                        context = context,
                                        uri = currentFileUri!!,
                                        text = editorText
                                    )

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
                    ) {
                        Text("Save")
                    }

                    // SAVE AS
                    TextButton(
                        onClick = {

                            saveAsLauncher.launch(
                                fileName
                            )
                        }
                    ) {
                        Text("Save As")
                    }
                }
            )
        }

    ) { innerPadding ->

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
        ) {

            OutlinedTextField(

                value = fileName,

                onValueChange = {
                    fileName = it
                },

                label = {
                    Text("File name")
                },

                singleLine = true,

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            TextField(

                value = editorText,

                onValueChange = {

                    editorText = it

                    statusMessage =
                        "Editing"
                },

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

                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Row(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {

                Text(

                    text =
                        statusMessage,

                    modifier =
                        Modifier.weight(1f)
                )

                Text(
                    text =
                        "Characters: ${editorText.length}"
                )
            }
        }
    }
}

/*
    Saves editor text into the selected file.
 */
fun saveTextToFile(
    context: Context,
    uri: Uri,
    text: String
) {

    context.contentResolver
        .openOutputStream(
            uri,
            "wt"
        )
        ?.bufferedWriter()
        ?.use { writer ->

            writer.write(text)
        }
}

/*
    Gets the actual file name
    from the Android file picker.
 */
fun getFileName(
    context: Context,
    uri: Uri
): String? {

    var name: String? = null

    val cursor =
        context.contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )

    cursor?.use {

        if (it.moveToFirst()) {

            val nameIndex =
                it.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

            if (nameIndex >= 0) {

                name =
                    it.getString(nameIndex)
            }
        }
    }

    return name
}

@Preview(showBackground = true)
@Composable
fun EditorScreenPreview() {

    ModernTextEditorTheme {
        EditorScreen()
    }
}