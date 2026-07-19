package com.example.tigerplayer.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    // MediaControllerManager is now self-providing via @Inject constructor
}
