package com.markdown.editor.di

import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.domain.parser.CommonmarkBlockParser
import com.markdown.editor.domain.parser.MarkdownBlockParser
import com.markdown.editor.domain.usecase.ParseDocumentUseCase
import com.markdown.editor.domain.usecase.UpdateBlockUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideMarkdownBlockParser(): MarkdownBlockParser = CommonmarkBlockParser()

    @Provides
    @Singleton
    fun provideParseDocumentUseCase(
        parser: MarkdownBlockParser,
        dispatcherProvider: DispatcherProvider
    ): ParseDocumentUseCase = ParseDocumentUseCase(parser, dispatcherProvider.diffAndParsing)

    @Provides
    @Singleton
    fun provideUpdateBlockUseCase(
        parser: MarkdownBlockParser,
        dispatcherProvider: DispatcherProvider
    ): UpdateBlockUseCase = UpdateBlockUseCase(parser, dispatcherProvider.diffAndParsing)

    @Provides
    @Singleton
    fun provideSplitBlockUseCase(
        parser: MarkdownBlockParser
    ): com.markdown.editor.domain.usecase.SplitBlockUseCase = com.markdown.editor.domain.usecase.SplitBlockUseCase(parser)

    @Provides
    @Singleton
    fun provideMergeBlockUseCase(
        parser: MarkdownBlockParser
    ): com.markdown.editor.domain.usecase.MergeBlockUseCase = com.markdown.editor.domain.usecase.MergeBlockUseCase(parser)

    @Provides
    @Singleton
    fun provideUndoBlockUseCase(
        diffCalculator: com.markdown.editor.domain.diff.DiffCalculator,
        parser: MarkdownBlockParser
    ): com.markdown.editor.domain.usecase.UndoBlockUseCase = com.markdown.editor.domain.usecase.UndoBlockUseCase(diffCalculator, parser)

    @Provides
    @Singleton
    fun provideRedoBlockUseCase(
        diffCalculator: com.markdown.editor.domain.diff.DiffCalculator,
        parser: MarkdownBlockParser
    ): com.markdown.editor.domain.usecase.RedoBlockUseCase = com.markdown.editor.domain.usecase.RedoBlockUseCase(diffCalculator, parser)

    @Provides
    @Singleton
    fun provideExportHtmlUseCase(
        dispatcherProvider: DispatcherProvider
    ): com.markdown.editor.domain.usecase.ExportHtmlUseCase = com.markdown.editor.domain.usecase.ExportHtmlUseCase(dispatcherProvider.diffAndParsing)

    @Provides
    @Singleton
    fun provideCodeSyntaxTokenizer(): com.markdown.editor.domain.syntax.CodeSyntaxTokenizer =
        com.markdown.editor.domain.syntax.RegexCodeSyntaxTokenizer()
}

