package com.tigerplayer.data.repository

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.tigerplayer.utils.AttributionTags
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Full flavor implementation of LocationProvider using Google Play Services.
 * Only available in the `full` flavor which includes Google Play Services dependencies.
 */
@Singleton
class GooglePlayLocationProvider @Inject constructor(
    private val application: Application
) : LocationProvider {

    private val attributedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        application.createAttributionContext(AttributionTags.WEATHER_LOCATION)
    } else {
        application
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(attributedContext)

    @SuppressLint("MissingPermission") // Permissions are checked in the UI layer
    override suspend fun getLastLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            // Handle total failure (e.g., Google Play Services missing)
            null
        }
    }
}

/**
 * Hilt module binding LocationProvider to GooglePlayLocationProvider in the full flavor.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationProviderModule {
    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: GooglePlayLocationProvider): LocationProvider
}

