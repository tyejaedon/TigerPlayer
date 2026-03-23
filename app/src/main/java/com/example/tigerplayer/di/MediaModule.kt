package com.example.tigerplayer.di

import android.content.Context
import com.example.tigerplayer.data.local.PlaybackPrefs
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.service.MediaControllerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // This makes the engine live as long as the app is alive
object MediaModule {

    @Provides
    @Singleton
    fun provideMediaControllerManager(
        @ApplicationContext context: Context,
        playbackPrefs: PlaybackPrefs,
        audioRepository: AudioRepository
    ): MediaControllerManager {
        // Hilt will automatically pass the dependencies into your Manager
        return MediaControllerManager(context, playbackPrefs, audioRepository)
    }
}