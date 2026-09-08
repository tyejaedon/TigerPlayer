package com.example.tigerplayer.utils

/**
 * Monotonic elapsed-time source.
 *
 * Injected rather than calling [android.os.SystemClock] directly so that listened-duration
 * accounting can be unit tested on the JVM with virtual time, per the determinism rule in
 * `.github/instructions/testing.instructions.md`.
 *
 * Implementations must be monotonic and unaffected by wall-clock or timezone changes.
 */
fun interface ElapsedTimeSource {

    /** Milliseconds since boot, excluding deep sleep. Never moves backwards. */
    fun elapsedMs(): Long
}

