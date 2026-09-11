package com.markdown.editor.presentation.ui.toolbar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdown.editor.domain.model.MarkdownFormatAction
import com.markdown.editor.presentation.editor.EditorIntent

/**
 * Accessory toolbar docked directly above the IME keyboard or at the bottom of the editor.
 * Provides instant one-tap formatting for common markdown syntax constructs.
 */
@Composable
fun MarkdownAccessoryToolbar(
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolbarButton(
                icon = Icons.Default.Title,
                contentDescription = "Heading",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.HEADING)) }
            )

            ToolbarButton(
                icon = Icons.Default.FormatBold,
                contentDescription = "Bold",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.BOLD)) }
            )

            ToolbarButton(
                icon = Icons.Default.FormatItalic,
                contentDescription = "Italic",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.ITALIC)) }
            )

            ToolbarButton(
                icon = Icons.Default.FormatStrikethrough,
                contentDescription = "Strikethrough",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.STRIKETHROUGH)) }
            )

            ToolbarButton(
                icon = Icons.Default.FormatQuote,
                contentDescription = "Quote",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.QUOTE)) }
            )

            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = "Bullet List",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.BULLET_LIST)) }
            )

            ToolbarButton(
                icon = Icons.Default.FormatListNumbered,
                contentDescription = "Numbered List",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.NUMBERED_LIST)) }
            )

            ToolbarButton(
                icon = Icons.Default.Code,
                contentDescription = "Inline Code",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.INLINE_CODE)) }
            )

            ToolbarButton(
                icon = Icons.Default.DataObject,
                contentDescription = "Code Block",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.CODE_BLOCK)) }
            )

            ToolbarButton(
                icon = Icons.Default.Link,
                contentDescription = "Insert Link",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.LINK)) }
            )

            ToolbarButton(
                icon = Icons.Default.HorizontalRule,
                contentDescription = "Horizontal Rule",
                onClick = { onIntent(EditorIntent.ApplyFormatting(MarkdownFormatAction.HORIZONTAL_RULE)) }
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        shape = RoundedCornerShape(8.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp)
        )
    }
}

