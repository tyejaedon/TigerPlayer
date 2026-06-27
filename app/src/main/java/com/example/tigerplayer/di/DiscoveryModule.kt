package com.example.tigerplayer.di

import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.engine.DiscoveryEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiscoveryModule {

    @Provides
    @Singleton
    fun provideDiscoveryEngine(
        mediaDataRepository: MediaDataRepository
    ): DiscoveryEngine {
        return DiscoveryEngine(mediaDataRepository)
    }
}

