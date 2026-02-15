package com.ivor.openanime.di

import com.ivor.openanime.data.repository.AnimeRepositoryImpl
import com.ivor.openanime.data.repository.DownloadRepositoryImpl
import com.ivor.openanime.data.repository.WatchLaterRepositoryImpl
import com.ivor.openanime.domain.repository.AnimeRepository
import com.ivor.openanime.domain.repository.DownloadRepository
import com.ivor.openanime.domain.repository.WatchLaterRepository
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
}
