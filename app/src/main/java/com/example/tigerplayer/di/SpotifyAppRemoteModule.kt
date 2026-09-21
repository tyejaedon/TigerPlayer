package com.example.tigerplayer.di

import com.example.tigerplayer.data.repository.SpotifyAppRemoteClient
import com.example.tigerplayer.data.repository.SpotifyAppRemoteClientImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [SpotifyAppRemoteClient] to whichever `SpotifyAppRemoteClientImpl` is present on the
 * active flavor's source set (`src/full` = real SDK, `src/foss` = no-op stub). See
 * [SpotifyAppRemoteClient] for the full rationale.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SpotifyAppRemoteModule {

    @Binds
    @Singleton
    abstract fun bindSpotifyAppRemoteClient(impl: SpotifyAppRemoteClientImpl): SpotifyAppRemoteClient
}

