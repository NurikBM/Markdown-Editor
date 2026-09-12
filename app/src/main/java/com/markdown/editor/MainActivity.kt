package com.markdown.editor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.markdown.editor.di.EditorViewModelFactory
import com.markdown.editor.presentation.editor.EditorIntent
import com.markdown.editor.presentation.editor.EditorViewModel
import com.markdown.editor.presentation.theme.MarkdownEditorTheme
import com.markdown.editor.presentation.ui.editor.EditorScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var viewModelFactory: EditorViewModelFactory

    private val viewModel: EditorViewModel by viewModels { viewModelFactory }

    companion object {
        private const val KEY_ACTIVE_DOCUMENT_ID = "active_document_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only handle launch intent on fresh activity start, never on configuration changes (orientation/rotation)
        if (savedInstanceState == null) {
            handleIntent(intent)
        } else {
            // Restore active document across process recreation if ViewModel state was purged
            val restoredDocId = savedInstanceState.getString(KEY_ACTIVE_DOCUMENT_ID)
            if (!restoredDocId.isNullOrEmpty() && viewModel.uiState.value.documentId.isEmpty()) {
                viewModel.processIntent(EditorIntent.LoadDocument(restoredDocId))
            }
        }

        setContent {
            val state by viewModel.uiState.collectAsState()

            MarkdownEditorTheme(appTheme = state.appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EditorScreen(
                        state = state,
                        effects = viewModel.uiEffect,
                        onIntent = viewModel::processIntent
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentDocId = viewModel.uiState.value.documentId
        if (currentDocId.isNotEmpty()) {
            outState.putString(KEY_ACTIVE_DOCUMENT_ID, currentDocId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val isNewNote = intent.getBooleanExtra("new_note", false)
        val documentId = intent.getStringExtra("document_id")

        // Consume intent extras to prevent accidental replays on subsequent intent handling
        intent.removeExtra("new_note")
        intent.removeExtra("document_id")

        when {
            isNewNote -> {
                viewModel.processIntent(EditorIntent.CreateNewDocument)
            }
            !documentId.isNullOrEmpty() -> {
                viewModel.processIntent(EditorIntent.LoadDocument(documentId))
            }
            action == Intent.ACTION_SEND -> {
                handleSendIntent(intent)
            }
            action == Intent.ACTION_VIEW -> {
                handleViewIntent(intent)
            }
            else -> {
                if (viewModel.uiState.value.documentId.isEmpty()) {
                    viewModel.processIntent(EditorIntent.LoadDocument("default-document"))
                }
            }
        }
    }

    private fun handleSendIntent(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.getStringExtra(Intent.EXTRA_TITLE)

        if (!sharedText.isNullOrEmpty()) {
            val title = if (!subject.isNullOrBlank()) {
                subject.trim()
            } else {
                sharedText.lineSequence().firstOrNull { it.isNotBlank() }
                    ?.take(40)
                    ?.trim('#', ' ', '\t')
                    ?.ifEmpty { "Shared Note" }
                    ?: "Shared Note"
            }
            viewModel.processIntent(EditorIntent.OpenExternalDocument(fileName = "$title.md", content = sharedText))
            intent.action = null
            return
        }

        val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        if (streamUri != null) {
            readAndOpenUri(streamUri)
        }
        intent.action = null
    }

    private fun handleViewIntent(intent: Intent) {
        val uri = intent.data
        if (uri != null) {
            readAndOpenUri(uri)
        }
        intent.action = null
    }

    private fun readAndOpenUri(uri: Uri) {
        try {
            var fileName = "Imported Note.md"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            val bytes = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            } ?: ByteArray(0)
            val extension = fileName.substringAfterLast('.', "").lowercase()
            val textExtensions = setOf("md", "markdown", "txt", "text", "log", "json", "xml", "yaml", "yml", "csv", "tsv", "html", "htm")
            val content = if (extension in textExtensions) {
                try { bytes.toString(Charsets.UTF_8) } catch (_: Exception) { "" }
            } else ""
            viewModel.processIntent(EditorIntent.OpenExternalDocument(fileName = fileName, content = content, rawBytes = bytes))
        } catch (_: Exception) {
            // Gracefully ignore or log read error
        }
    }
}
