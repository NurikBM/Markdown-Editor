package com.markdown.editor.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.domain.diff.DiffCalculator
import com.markdown.editor.domain.parser.MarkdownBlockParser
import com.markdown.editor.domain.repository.MarkdownRepository
import com.markdown.editor.domain.repository.SnapshotRepository
import com.markdown.editor.domain.usecase.ExportHtmlUseCase
import com.markdown.editor.domain.usecase.FindInDocumentUseCase
import com.markdown.editor.domain.usecase.GenerateTableOfContentsUseCase
import com.markdown.editor.domain.usecase.MergeBlockUseCase
import com.markdown.editor.domain.usecase.RedoBlockUseCase
import com.markdown.editor.domain.usecase.ReplaceInDocumentUseCase
import com.markdown.editor.domain.usecase.SplitBlockUseCase
import com.markdown.editor.domain.usecase.UndoBlockUseCase
import com.markdown.editor.presentation.editor.EditorViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

/**
 * ViewModel factory providing [EditorViewModel] with domain dependencies.
 */
class EditorViewModelFactory(
    private val markdownRepository: MarkdownRepository,
    private val snapshotRepository: SnapshotRepository,
    private val splitBlockUseCase: SplitBlockUseCase,
    private val mergeBlockUseCase: MergeBlockUseCase,
    private val undoBlockUseCase: UndoBlockUseCase,
    private val redoBlockUseCase: RedoBlockUseCase,
    private val diffCalculator: DiffCalculator,
    private val parser: MarkdownBlockParser,
    private val dispatcherProvider: DispatcherProvider,
    private val exportHtmlUseCase: ExportHtmlUseCase,
    private val generateTableOfContentsUseCase: GenerateTableOfContentsUseCase,
    private val findInDocumentUseCase: FindInDocumentUseCase,
    private val replaceInDocumentUseCase: ReplaceInDocumentUseCase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            return EditorViewModel(
                markdownRepository = markdownRepository,
                snapshotRepository = snapshotRepository,
                splitBlockUseCase = splitBlockUseCase,
                mergeBlockUseCase = mergeBlockUseCase,
                undoBlockUseCase = undoBlockUseCase,
                redoBlockUseCase = redoBlockUseCase,
                diffCalculator = diffCalculator,
                parser = parser,
                dispatcherProvider = dispatcherProvider,
                exportHtmlUseCase = exportHtmlUseCase,
                generateTableOfContentsUseCase = generateTableOfContentsUseCase,
                findInDocumentUseCase = findInDocumentUseCase,
                replaceInDocumentUseCase = replaceInDocumentUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}

@Module
@InstallIn(ActivityComponent::class)
object PresentationModule {

    @Provides
    fun provideEditorViewModelFactory(
        markdownRepository: MarkdownRepository,
        snapshotRepository: SnapshotRepository,
        splitBlockUseCase: SplitBlockUseCase,
        mergeBlockUseCase: MergeBlockUseCase,
        undoBlockUseCase: UndoBlockUseCase,
        redoBlockUseCase: RedoBlockUseCase,
        diffCalculator: DiffCalculator,
        parser: MarkdownBlockParser,
        dispatcherProvider: DispatcherProvider,
        exportHtmlUseCase: ExportHtmlUseCase,
        generateTableOfContentsUseCase: GenerateTableOfContentsUseCase,
        findInDocumentUseCase: FindInDocumentUseCase,
        replaceInDocumentUseCase: ReplaceInDocumentUseCase
    ): EditorViewModelFactory = EditorViewModelFactory(
        markdownRepository = markdownRepository,
        snapshotRepository = snapshotRepository,
        splitBlockUseCase = splitBlockUseCase,
        mergeBlockUseCase = mergeBlockUseCase,
        undoBlockUseCase = undoBlockUseCase,
        redoBlockUseCase = redoBlockUseCase,
        diffCalculator = diffCalculator,
        parser = parser,
        dispatcherProvider = dispatcherProvider,
        exportHtmlUseCase = exportHtmlUseCase,
        generateTableOfContentsUseCase = generateTableOfContentsUseCase,
        findInDocumentUseCase = findInDocumentUseCase,
        replaceInDocumentUseCase = replaceInDocumentUseCase
    )
}
