package com.markdown.editor.di

import com.markdown.editor.core.dispatcher.DefaultDispatcherProvider
import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.core.logger.AndroidLogger
import com.markdown.editor.core.logger.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    fun provideLogger(): Logger = AndroidLogger()
}

