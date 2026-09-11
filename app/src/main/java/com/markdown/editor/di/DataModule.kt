package com.markdown.editor.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.core.logger.Logger
import com.markdown.editor.data.diff.DiffEngine
import com.markdown.editor.data.local.MarkdownDatabase
import com.markdown.editor.data.local.dao.BlockDao
import com.markdown.editor.data.local.dao.DocumentDao
import com.markdown.editor.data.local.dao.SnapshotDao
import com.markdown.editor.data.repository.RoomMarkdownRepository
import com.markdown.editor.data.repository.RoomSnapshotRepository
import com.markdown.editor.data.saf.SafTreeCoordinator
import com.markdown.editor.domain.parser.MarkdownBlockParser
import com.markdown.editor.domain.repository.MarkdownRepository
import com.markdown.editor.domain.repository.SnapshotRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideMarkdownDatabase(
        @ApplicationContext context: Context
    ): MarkdownDatabase {
        return Room.databaseBuilder(
            context,
            MarkdownDatabase::class.java,
            "markdown_editor.db"
        ).build()
    }

    @Provides
    fun provideDocumentDao(database: MarkdownDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideBlockDao(database: MarkdownDatabase): BlockDao = database.blockDao()

    @Provides
    fun provideSnapshotDao(database: MarkdownDatabase): SnapshotDao = database.snapshotDao()

    @Provides
    @Singleton
    fun provideDiffEngine(): DiffEngine = DiffEngine()

    @Provides
    @Singleton
    fun provideContentResolver(
        @ApplicationContext context: Context
    ): ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun provideSafTreeCoordinator(
        contentResolver: ContentResolver,
        documentDao: DocumentDao,
        logger: Logger
    ): SafTreeCoordinator = SafTreeCoordinator(contentResolver, documentDao, logger)

    @Provides
    @Singleton
    fun provideMarkdownRepository(
        documentDao: DocumentDao,
        blockDao: BlockDao,
        parser: MarkdownBlockParser,
        dispatcherProvider: DispatcherProvider
    ): MarkdownRepository = RoomMarkdownRepository(
        documentDao = documentDao,
        blockDao = blockDao,
        parser = parser,
        dispatcherProvider = dispatcherProvider
    )

    @Provides
    @Singleton
    fun provideSnapshotRepository(
        snapshotDao: SnapshotDao,
        dispatcherProvider: DispatcherProvider
    ): SnapshotRepository = RoomSnapshotRepository(
        snapshotDao = snapshotDao,
        dispatcherProvider = dispatcherProvider
    )
}

