package com.example.tigerplayer.di

import android.os.SystemClock
import com.example.tigerplayer.utils.ElapsedTimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {
    // Currently, all audio components use @Inject constructor and @Singleton on the class itself.
    // This module can be used for interface bindings or complex provider logic in the future.

    /**
     * Monotonic clock used for listened-duration accounting (issue #42).
     *
     * `elapsedRealtime` is deliberate: it is unaffected by the user changing the system clock or
     * timezone, so a play cannot be inflated or lost by a wall-clock jump.
     */
    @Provides
    @Singleton
    fun provideElapsedTimeSource(): ElapsedTimeSource =
        ElapsedTimeSource { SystemClock.elapsedRealtime() }
}
