package com.tigerplayer.data.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding LocationProvider to NoOpLocationProvider in the foss flavor.
 * This allows the foss flavor to compile and run without Google Play Services.
 * HomeViewModel will gracefully fall back to default coordinates.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationProviderModule {
    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: NoOpLocationProvider): LocationProvider
}

