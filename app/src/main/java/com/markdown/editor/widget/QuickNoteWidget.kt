package com.markdown.editor.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.markdown.editor.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull

/**
 * Quick Note Home Screen Widget using Jetpack Glance.
 * Displays the most recently edited note and provides a 1-tap launcher to create a new markdown note.
 */
class QuickNoteWidget : GlanceAppWidget() {

    companion object {
        val KEY_DOCUMENT_ID = ActionParameters.Key<String>("document_id")
        val KEY_NEW_NOTE = ActionParameters.Key<Boolean>("new_note")
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun markdownRepository(): com.markdown.editor.domain.repository.MarkdownRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val repository = entryPoint.markdownRepository()
        val recentDocs = repository.observeAllMetadata().firstOrNull() ?: emptyList()
        val latestDoc = recentDocs.firstOrNull()

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(16.dp)
                        .padding(12.dp)
                ) {
                    // Header: App Title & New Note Button
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Markdown Notes",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )

                        // "+ New" action
                        Box(
                            modifier = GlanceModifier
                                .background(GlanceTheme.colors.primary)
                                .cornerRadius(8.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable(
                                    actionStartActivity<MainActivity>(
                                        actionParametersOf(KEY_NEW_NOTE to true)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ New",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Recent note preview or placeholder
                    if (latestDoc != null) {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(GlanceTheme.colors.surfaceVariant)
                                .cornerRadius(8.dp)
                                .padding(8.dp)
                                .clickable(
                                    actionStartActivity<MainActivity>(
                                        actionParametersOf(KEY_DOCUMENT_ID to latestDoc.id)
                                    )
                                )
                        ) {
                            Text(
                                text = latestDoc.title.ifEmpty { "Untitled Document" },
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = "Tap to open and edit note",
                                style = TextStyle(
                                    color = GlanceTheme.colors.outline,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }
                    } else {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No notes yet. Tap + New to start!",
                                style = TextStyle(
                                    color = GlanceTheme.colors.outline,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

