package com.example.tigerplayer.di

import android.content.Context
import androidx.room.Room
import com.example.tigerplayer.data.local.TigerDatabase
import com.example.tigerplayer.data.local.dao.PlaylistDao
import com.example.tigerplayer.data.local.dao.TigerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTigerDatabase(
        @ApplicationContext context: Context
    ): TigerDatabase {
        return Room.databaseBuilder(
            context,
            TigerDatabase::class.java,
            "tiger_player_vault.db"
        )
            // THE FIX: Destructive migration is safest during your current "Forging" phase
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun provideTigerDao(database: TigerDatabase): TigerDao {
        return database.tigerDao()
    }

    @Provides
    fun providePlaylistDao(database: TigerDatabase): PlaylistDao {
        return database.playlistDao()
    }
}