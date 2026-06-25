package com.example.tigerplayer.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    // Currently, all audio components use @Inject constructor and @Singleton on the class itself.
    // This module can be used for interface bindings or complex provider logic in the future.
}
