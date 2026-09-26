package com.ivor.openstream.di

import com.ivor.openstream.data.repository.AnimeRepositoryImpl
import com.ivor.openstream.data.repository.DownloadRepositoryImpl
import com.ivor.openstream.data.repository.WatchLaterRepositoryImpl
import com.ivor.openstream.data.repository.WatchProgressRepositoryImpl
import com.ivor.openstream.data.repository.OpenSubtitlesRepository
import com.ivor.openstream.domain.repository.SubtitleRepository
import com.ivor.openstream.data.extensions.ExtensionRepositoryImpl
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.DownloadRepository
import com.ivor.openstream.domain.repository.ExtensionRepository
import com.ivor.openstream.domain.repository.WatchLaterRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAnimeRepository(
        animeRepositoryImpl: AnimeRepositoryImpl
    ): AnimeRepository

    @Binds
    abstract fun bindWatchLaterRepository(
        watchLaterRepositoryImpl: WatchLaterRepositoryImpl
    ): WatchLaterRepository

    @Binds
    abstract fun bindDownloadRepository(
        downloadRepositoryImpl: DownloadRepositoryImpl
    ): DownloadRepository

    @Binds
    abstract fun bindExtensionRepository(
        extensionRepositoryImpl: ExtensionRepositoryImpl
    ): ExtensionRepository

    @Binds
    abstract fun bindWatchProgressRepository(
        watchProgressRepositoryImpl: WatchProgressRepositoryImpl
    ): WatchProgressRepository

    @Binds
    abstract fun bindSubtitleRepository(
        openSubtitlesRepository: OpenSubtitlesRepository
    ): SubtitleRepository
}
