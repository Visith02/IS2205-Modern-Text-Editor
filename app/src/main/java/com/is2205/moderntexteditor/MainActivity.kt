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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(
    val name: String,
    val uri: String
)

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

    var currentFileUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var showRecentDialog by remember {
        mutableStateOf(false)
    }

    var recentFiles by remember {
        mutableStateOf(loadRecentFiles(context))
    }

    // -----------------------------
    // OPEN FILE
    // -----------------------------

    val openFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {

                    // Keep permission to access the file later.
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                        // Some providers may not allow both permissions.
                    }

                    val text = readTextFromFile(
                        context,
                        uri
                    )

                    if (text != null) {

                        editorText = text

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

    // -----------------------------
    // SAVE AS
    // -----------------------------

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
                            fileName = "untitled.txt"
                            currentFileUri = null

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

                    // RECENT
                    TextButton(
                        onClick = {

                            recentFiles =
                                loadRecentFiles(context)

                            showRecentDialog = true
                        }
                    ) {
                        Text("Recent")
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

                                    addRecentFile(
                                        context = context,
                                        fileName = fileName,
                                        uri = currentFileUri!!
                                    )

                                    recentFiles =
                                        loadRecentFiles(context)

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

            // SAVE AS button
            TextButton(
                onClick = {

                    saveAsLauncher.launch(
                        fileName
                    )
                }
            ) {
                Text("Save As")
            }

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
                    MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace
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

                    text = statusMessage,

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

    // -----------------------------
    // RECENT FILES DIALOG
    // -----------------------------

    if (showRecentDialog) {

        AlertDialog(

            onDismissRequest = {
                showRecentDialog = false
            },

            title = {
                Text("Recent Files")
            },

            text = {

                Column {

                    if (recentFiles.isEmpty()) {

                        Text(
                            "No recent files yet."
                        )

                    } else {

                        recentFiles.forEach { recentFile ->

                            Text(
                                text = recentFile.name,

                                modifier = Modifier
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

                                                editorText = text

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

                                        } catch (e: Exception) {

                                            statusMessage =
                                                "Recent file is unavailable"
                                        }

                                        showRecentDialog =
                                            false
                                    }
                                    .padding(
                                        vertical = 12.dp
                                    )
                            )
                        }
                    }
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        showRecentDialog = false
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

// -----------------------------------------
// READ FILE
// -----------------------------------------

fun readTextFromFile(
    context: Context,
    uri: Uri
): String? {

    return context.contentResolver
        .openInputStream(uri)
        ?.bufferedReader()
        ?.use {
            it.readText()
        }
}

// -----------------------------------------
// SAVE FILE
// -----------------------------------------

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

// -----------------------------------------
// GET FILE NAME
// -----------------------------------------

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

// -----------------------------------------
// ADD RECENT FILE
// -----------------------------------------

fun addRecentFile(
    context: Context,
    fileName: String,
    uri: Uri
) {

    val recentFiles =
        loadRecentFiles(context)
            .toMutableList()

    // Remove duplicate if it already exists.
    recentFiles.removeAll {
        it.uri == uri.toString()
    }

    // Add newest file at the top.
    recentFiles.add(
        0,
        RecentFile(
            name = fileName,
            uri = uri.toString()
        )
    )

    // Keep only latest 10 files.
    val limitedList =
        recentFiles.take(10)

    saveRecentFiles(
        context,
        limitedList
    )
}

// -----------------------------------------
// SAVE RECENT FILE LIST
// -----------------------------------------

fun saveRecentFiles(
    context: Context,
    files: List<RecentFile>
) {

    val preferences =
        context.getSharedPreferences(
            "editor_preferences",
            Context.MODE_PRIVATE
        )

    val jsonArray =
        JSONArray()

    files.forEach { file ->

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

    preferences.edit()
        .putString(
            "recent_files",
            jsonArray.toString()
        )
        .apply()
}

// -----------------------------------------
// LOAD RECENT FILE LIST
// -----------------------------------------

fun loadRecentFiles(
    context: Context
): List<RecentFile> {

    val preferences =
        context.getSharedPreferences(
            "editor_preferences",
            Context.MODE_PRIVATE
        )

    val json =
        preferences.getString(
            "recent_files",
            null
        ) ?: return emptyList()

    val recentFiles =
        mutableListOf<RecentFile>()

    try {

        val jsonArray =
            JSONArray(json)

        for (i in 0 until jsonArray.length()) {

            val objectItem =
                jsonArray.getJSONObject(i)

            recentFiles.add(
                RecentFile(
                    name =
                        objectItem.getString(
                            "name"
                        ),
                    uri =
                        objectItem.getString(
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

@Preview(showBackground = true)
@Composable
fun EditorScreenPreview() {

    ModernTextEditorTheme {
        EditorScreen()
    }
}