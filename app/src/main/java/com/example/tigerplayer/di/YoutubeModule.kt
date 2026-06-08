package com.example.tigerplayer.di

import com.example.tigerplayer.data.remote.api.OfficialYouTubeDataSource
import com.example.tigerplayer.data.remote.api.YouTubeRepository
import com.example.tigerplayer.data.remote.api.YouTubeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class YouTubeModule {

    @Binds
    @Singleton
    abstract fun bindYouTubeRepository(
        impl: YouTubeRepositoryImpl
    ): YouTubeRepository

    companion object {
        @Provides
        @Singleton
        fun provideOfficialYouTubeDataSource(): OfficialYouTubeDataSource {
            return Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OfficialYouTubeDataSource::class.java)
        }
    }
}



// 1. Define a Qualifier to distinguish this dispatcher from others
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}