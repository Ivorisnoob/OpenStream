package com.ivor.openstream.di

import com.ivor.openstream.data.streaming.StreamingRepositoryImpl
import com.ivor.openstream.domain.repository.StreamingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Stream providers are no longer declared here. They are created at runtime by
 * `ExtensionProviderRegistry` from whichever extensions the user installed from the marketplace.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingBindingsModule {
    @Binds
    abstract fun bindStreamingRepository(implementation: StreamingRepositoryImpl): StreamingRepository
}
