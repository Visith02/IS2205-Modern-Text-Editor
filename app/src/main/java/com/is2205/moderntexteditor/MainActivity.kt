package com.is2205.moderntexteditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
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

    var fileName by rememberSaveable {
        mutableStateOf("untitled.txt")
    }

    var editorText by rememberSaveable {
        mutableStateOf("")
    }

    var statusMessage by rememberSaveable {
        mutableStateOf("Ready")
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),

        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Modern Text Editor")
                },

                actions = {

                    TextButton(
                        onClick = {
                            fileName = "untitled.txt"
                            editorText = ""
                            statusMessage = "New file created"
                        }
                    ) {
                        Text("New")
                    }

                    TextButton(
                        onClick = {
                            statusMessage = "Open function will be added next"
                        }
                    ) {
                        Text("Open")
                    }

                    TextButton(
                        onClick = {
                            statusMessage = "Save function will be added next"
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

            TextField(
                value = editorText,

                onValueChange = {
                    editorText = it
                    statusMessage = "Editing"
                },

                placeholder = {
                    Text("Start typing here...")
                },

                textStyle = MaterialTheme.typography.bodyLarge.copy(
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
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "Characters: ${editorText.length}"
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditorScreenPreview() {
    ModernTextEditorTheme {
        EditorScreen()
    }
}