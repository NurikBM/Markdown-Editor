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
}

