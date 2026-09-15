package com.markdown.editor.presentation.ui.preview

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

enum class MermaidViewMode {
    DIAGRAM,
    CODE
}

/**
 * Interactive preview block for Mermaid diagrams.
 * Provides a dual-mode interface allowing users to switch between visual diagram rendering
 * and raw source code inspection with dark/light theme synchronization.
 */
@Composable
fun MermaidDiagramBlock(
    code: String,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(MermaidViewMode.DIAGRAM) }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val isDark = isSystemInDarkTheme()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = viewMode == MermaidViewMode.DIAGRAM,
                        onClick = { viewMode = MermaidViewMode.DIAGRAM },
                        label = { Text("Diagram", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Polyline,
                                contentDescription = "Diagram view",
                                modifier = Modifier.height(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    FilterChip(
                        selected = viewMode == MermaidViewMode.CODE,
                        onClick = { viewMode = MermaidViewMode.CODE },
                        label = { Text("Code", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Code view",
                                modifier = Modifier.height(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                    },
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Mermaid code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }

            val sanitizedCode = remember(code) { cleanMermaidCode(code) }

            when (viewMode) {
                MermaidViewMode.DIAGRAM -> {
                    MermaidWebView(
                        code = sanitizedCode,
                        isDark = isDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .heightIn(min = 180.dp, max = 500.dp)
                    )
                }
                MermaidViewMode.CODE -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = sanitizedCode,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun cleanMermaidCode(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("```")) return trimmed
    return trimmed
        .lines()
        .filterNot { it.trim().startsWith("```") }
        .joinToString("\n")
        .trim()
}

private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidWebView(
    code: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val themeName = if (isDark) "dark" else "default"
    val html = remember(code, themeName) {
        buildMermaidHtml(code, themeName)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0x00000000)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
        }
    )
}

private fun buildMermaidHtml(code: String, theme: String): String {
    val safeCode = escapeHtml(code)
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
            <style>
                body {
                    margin: 0;
                    padding: 8px;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    background-color: transparent;
                }
                .mermaid {
                    width: 100%;
                    text-align: center;
                    overflow-x: auto;
                }
                .mermaid svg {
                    max-width: 100% !important;
                    height: auto !important;
                }
                #err {
                    color: #e53935;
                    font-family: monospace;
                    font-size: 12px;
                    display: none;
                    white-space: pre-wrap;
                    padding: 8px;
                }
            </style>
        </head>
        <body>
            <div id="err"></div>
            <pre class="mermaid">
$safeCode
            </pre>
            <script>
                window.addEventListener('DOMContentLoaded', function() {
                    if (typeof mermaid === 'undefined') {
                        var err = document.getElementById('err');
                        err.style.display = 'block';
                        err.innerText = 'Mermaid.js library loading... (Check internet connection for initial cache or switch to Code view)';
                        return;
                    }
                    try {
                        mermaid.initialize({
                            startOnLoad: true,
                            theme: '$theme',
                            securityLevel: 'loose'
                        });
                    } catch(e) {
                        var err = document.getElementById('err');
                        err.style.display = 'block';
                        err.innerText = 'Mermaid Error: ' + e.message;
                    }
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}
