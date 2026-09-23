package com.tigerplayer.data.repository

import android.location.Location
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction for location services. Implemented differently in full and foss flavors.
 * Full flavor uses Google Play Services, foss flavor uses a no-op stub.
 */
interface LocationProvider {
    suspend fun getLastLocation(): Location?
}

/**
 * Default no-op implementation used by foss flavor.
 * Returns null, causing HomeViewModel to fall back to default coordinates (Nairobi).
 */
@Singleton
class NoOpLocationProvider @Inject constructor() : LocationProvider {
    override suspend fun getLastLocation(): Location? = null
}

