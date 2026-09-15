package com.markdown.editor.presentation.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.markdown.editor.presentation.editor.EditorEffect
import com.markdown.editor.presentation.editor.EditorIntent
import com.markdown.editor.presentation.editor.EditorUiState
import com.markdown.editor.presentation.editor.EditorViewMode
import com.markdown.editor.presentation.export.AndroidExportHelper
import com.markdown.editor.presentation.security.BiometricSecurityHelper
import com.markdown.editor.presentation.ui.block.BlockSearchHighlight
import com.markdown.editor.presentation.ui.block.MarkdownBlockItem
import com.markdown.editor.presentation.ui.drawer.DocumentDrawerSheet
import com.markdown.editor.presentation.ui.preview.MarkdownPreviewPane
import com.markdown.editor.presentation.ui.search.FindReplaceActions
import com.markdown.editor.presentation.ui.search.FindReplaceBar
import com.markdown.editor.presentation.ui.search.FindReplaceState
import com.markdown.editor.presentation.ui.sync.rememberSynchronizedScroll
import com.markdown.editor.presentation.ui.toc.TableOfContentsSheet
import com.markdown.editor.presentation.ui.toolbar.MarkdownAccessoryToolbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private val IMPORT_MIME_TYPES = arrayOf(
    "text/plain",
    "text/markdown",
    "text/x-markdown",
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/csv",
    "text/html",
    "application/json",
    "text/*"
)

/**
 * Main screen composable hosting the block editor, navigation drawer, top bar, and preview panes.
 * Enforces key-based recomposition isolation per [BlockId] and supports Split-View mode.
 */
@Composable
fun EditorScreen(
    state: EditorUiState,
    effects: Flow<EditorEffect>,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val editorListState = rememberLazyListState()
    val previewListState = rememberLazyListState()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    rememberSynchronizedScroll(
        editorListState = editorListState,
        previewListState = previewListState,
        enabled = state.viewMode == EditorViewMode.SPLIT_VIEW
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                onIntent(EditorIntent.LockDocumentSession)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    SyncDrawerState(state.isDrawerOpen, drawerState, onIntent)
    HandleEditorEffects(effects, snackbarHostState, editorListState, previewListState, context, onIntent)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleFilePicked(context, uri, onIntent)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DocumentDrawerSheet(
                state = state,
                onIntent = onIntent,
                onOpenFile = {
                    onIntent(EditorIntent.ToggleDrawer(false))
                    filePickerLauncher.launch(IMPORT_MIME_TYPES)
                }
            )
        },
        gesturesEnabled = true
    ) {
        EditorScaffold(
            state = state,
            onIntent = onIntent,
            snackbarHostState = snackbarHostState,
            editorListState = editorListState,
            previewListState = previewListState,
            modifier = modifier
        )
    }

    if (state.isTableOfContentsVisible) {
        TableOfContentsSheet(
            items = state.tableOfContents,
            onItemClick = { item -> onIntent(EditorIntent.NavigateToHeading(item)) },
            onDismiss = { onIntent(EditorIntent.ToggleTableOfContents(visible = false)) }
        )
    }
}

@Composable
private fun SyncDrawerState(
    isDrawerOpen: Boolean,
    drawerState: androidx.compose.material3.DrawerState,
    onIntent: (EditorIntent) -> Unit
) {
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen && drawerState.isClosed) {
            drawerState.open()
        } else if (!isDrawerOpen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != isDrawerOpen) {
            onIntent(EditorIntent.ToggleDrawer(drawerState.isOpen))
        }
    }
}

@Composable
private fun HandleEditorEffects(
    effects: Flow<EditorEffect>,
    snackbarHostState: SnackbarHostState,
    editorListState: LazyListState,
    previewListState: LazyListState,
    context: Context,
    onIntent: (EditorIntent) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is EditorEffect.ShowToast -> snackbarHostState.showSnackbar(effect.message)
                is EditorEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is EditorEffect.RequestFocusOnBlock -> Unit
                is EditorEffect.ScrollToBlock -> {
                    editorListState.animateScrollToItem(effect.blockIndex)
                    previewListState.animateScrollToItem(effect.blockIndex)
                }
                is EditorEffect.PrintHtml -> {
                    AndroidExportHelper.printHtml(
                        context = context,
                        jobName = effect.jobName,
                        htmlContent = effect.htmlContent,
                        onError = { _ -> }
                    )
                }
                is EditorEffect.ShareContent -> {
                    AndroidExportHelper.shareText(
                        context = context,
                        title = effect.title,
                        content = effect.content,
                        mimeType = effect.mimeType
                    )
                }
                is EditorEffect.LaunchBiometricPrompt -> {
                    val activity = context as? FragmentActivity
                    if (activity != null) {
                        BiometricSecurityHelper().authenticate(
                            activity = activity,
                            title = effect.title,
                            subtitle = "Authenticate to view protected note",
                            onSuccess = {
                                onIntent(EditorIntent.UnlockDocumentSession)
                            },
                            onError = { errorCode, errString ->
                                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                ) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Authentication error: $errString")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScaffold(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    editorListState: LazyListState,
    previewListState: LazyListState,
    modifier: Modifier = Modifier
) {
    val isLocked = state.isDocumentLocked && !state.isUnlockedForSession

    Scaffold(
        topBar = {
            EditorTopBar(state = state, onIntent = onIntent)
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isLocked && state.isFindReplaceVisible) {
                FindReplaceBar(
                    state = FindReplaceState(
                        searchQuery = state.searchQuery,
                        replaceQuery = state.replaceQuery,
                        currentMatchIndex = state.currentMatchIndex,
                        totalMatches = state.findMatches.size,
                        isCaseSensitive = state.isCaseSensitive
                    ),
                    actions = FindReplaceActions(
                        onSearchQueryChange = { q -> onIntent(EditorIntent.SetSearchQuery(q)) },
                        onReplaceQueryChange = { q -> onIntent(EditorIntent.SetReplaceQuery(q)) },
                        onNextMatch = { onIntent(EditorIntent.FindNextMatch) },
                        onPreviousMatch = { onIntent(EditorIntent.FindPreviousMatch) },
                        onToggleCaseSensitive = { cs -> onIntent(EditorIntent.SetCaseSensitive(cs)) },
                        onReplace = { onIntent(EditorIntent.ReplaceCurrentMatch) },
                        onReplaceAll = { onIntent(EditorIntent.ReplaceAllMatches) },
                        onClose = { onIntent(EditorIntent.ToggleFindReplace(visible = false)) }
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    isLocked -> {
                        LockedDocumentView(
                            title = state.title,
                            onUnlockClick = { onIntent(EditorIntent.RequestBiometricUnlock) }
                        )
                    }
                    else -> {
                        EditorContentArea(
                            state = state,
                            editorListState = editorListState,
                            previewListState = previewListState,
                            onIntent = onIntent
                        )
                    }
                }
            }

            if (!isLocked && state.viewMode != EditorViewMode.PREVIEW_ONLY) {
                MarkdownAccessoryToolbar(onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun LockedDocumentView(
    title: String,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Protected Note",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Protected Note",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "\"${title.ifEmpty { "Untitled" }}\" is locked. Authenticate using biometrics or device credentials to view.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onUnlockClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Unlock Note")
        }
    }
}

@Composable
private fun EditorContentArea(
    state: EditorUiState,
    editorListState: LazyListState,
    previewListState: LazyListState,
    onIntent: (EditorIntent) -> Unit
) {
    when (state.viewMode) {
        EditorViewMode.EDITOR_ONLY -> {
            EditorPane(
                state = state,
                listState = editorListState,
                onIntent = onIntent
            )
        }
        EditorViewMode.PREVIEW_ONLY -> {
            MarkdownPreviewPane(
                blocks = state.blocks,
                searchQuery = state.searchQuery,
                isCaseSensitive = state.isCaseSensitive,
                listState = previewListState
            )
        }
        EditorViewMode.SPLIT_VIEW -> {
            EditorSplitView(
                state = state,
                editorListState = editorListState,
                previewListState = previewListState,
                onIntent = onIntent
            )
        }
    }
}

@Composable
private fun EditorSplitView(
    state: EditorUiState,
    editorListState: LazyListState,
    previewListState: LazyListState,
    onIntent: (EditorIntent) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(modifier = Modifier.fillMaxSize()) {
                EditorPane(
                    state = state,
                    listState = editorListState,
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
                MarkdownPreviewPane(
                    blocks = state.blocks,
                    searchQuery = state.searchQuery,
                    isCaseSensitive = state.isCaseSensitive,
                    listState = previewListState,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                EditorPane(
                    state = state,
                    listState = editorListState,
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
                MarkdownPreviewPane(
                    blocks = state.blocks,
                    searchQuery = state.searchQuery,
                    isCaseSensitive = state.isCaseSensitive,
                    listState = previewListState,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EditorPane(
    state: EditorUiState,
    listState: LazyListState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMatch = state.findMatches.getOrNull(state.currentMatchIndex)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = state.blocks,
            key = { it.id.value }
        ) { block ->
            val matchRange = if (activeMatch?.blockId == block.id) {
                activeMatch.startIndex..activeMatch.endIndex
            } else {
                null
            }
            MarkdownBlockItem(
                block = block,
                isFocused = state.focusedBlockId == block.id,
                requestedCursorPosition = if (state.focusedBlockId == block.id) state.cursorPosition else null,
                searchHighlight = BlockSearchHighlight(
                    searchQuery = state.searchQuery,
                    isCaseSensitive = state.isCaseSensitive,
                    activeMatchRange = matchRange
                ),
                onIntent = onIntent
            )
        }
    }
}

private fun handleFilePicked(
    context: Context,
    uri: Uri,
    onIntent: (EditorIntent) -> Unit
) {
    try {
        var fileName = "Imported Note"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes()
        } ?: ByteArray(0)
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val textExtensions = setOf("md", "markdown", "txt", "text", "log", "json", "xml", "yaml", "yml", "csv", "tsv", "html", "htm")
        val content = if (extension in textExtensions) {
            try {
                bytes.toString(Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
        onIntent(EditorIntent.OpenExternalDocument(fileName = fileName, content = content, rawBytes = bytes))
    } catch (_: Exception) {
        // Ignored
    }
}
