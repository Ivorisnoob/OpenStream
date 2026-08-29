package com.ivor.openstream.di

import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.data.streaming.StreamingRepositoryImpl
import com.ivor.openstream.data.streaming.providers.VidkingDirectApi
import com.ivor.openstream.data.streaming.providers.VidkingDirectProvider
import com.ivor.openstream.data.streaming.providers.VidkingServerSpec
import com.ivor.openstream.data.streaming.providers.VidkingWebViewProvider
import com.ivor.openstream.domain.repository.StreamingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamingBindingsModule {
    @Binds
    abstract fun bindStreamingRepository(implementation: StreamingRepositoryImpl): StreamingRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StreamingProvidersModule {
    @Provides
    @IntoSet
    fun provideYoruProvider(api: VidkingDirectApi): StreamProvider =
        VidkingDirectProvider(api, VidkingServerSpec("yoru", "Yoru", "cdn/sources-with-title", 0))

    @Provides
    @IntoSet
    fun provideCypherProvider(api: VidkingDirectApi): StreamProvider =
        VidkingDirectProvider(api, VidkingServerSpec("cypher", "Cypher", "downloader2/sources-with-title", 1))

    @Provides
    @IntoSet
    fun provideBreachProvider(api: VidkingDirectApi): StreamProvider =
        VidkingDirectProvider(api, VidkingServerSpec("breach", "Breach", "m4uhd/sources-with-title", 2))

    @Provides
    @IntoSet
    fun provideNeonProvider(api: VidkingDirectApi): StreamProvider =
        VidkingDirectProvider(api, VidkingServerSpec("neon", "Neon", "vsrc/sources-with-title", 3))

    @Provides
    @IntoSet
    @Singleton
    fun provideWebViewFallback(provider: VidkingWebViewProvider): StreamProvider = provider
}
