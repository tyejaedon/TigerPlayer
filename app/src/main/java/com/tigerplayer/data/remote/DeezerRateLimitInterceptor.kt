package com.tigerplayer.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Thread-safe sliding-window rate-limiting OkHttp [Interceptor] for the Deezer Simple API.
 *
 * Enforces Deezer's public quota of at most [maxRequests] requests per [windowMs] milliseconds
 * (default 50 requests / 5 seconds) and performs exponential backoff on HTTP 429 responses.
 */
class DeezerRateLimitInterceptor(
    private val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val baseBackoffMs: Long = DEFAULT_BASE_BACKOFF_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = Thread::sleep
) : Interceptor {

    private val lock = Any()
    private val requestTimestamps = ArrayDeque<Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val baseRequest = chain.request().newBuilder()
            .header("Accept", "application/json; charset=utf-8")
            .build()

        var attempt = 0
        while (true) {
            acquireSlot()
            val response = chain.proceed(baseRequest)
            if (response.code != HTTP_TOO_MANY_REQUESTS || attempt >= maxRetries) {
                return response
            }

            val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
            response.close()

            val backoffMs = retryAfterMs ?: (baseBackoffMs shl attempt)
            attempt++
            try {
                sleeper(backoffMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during Deezer rate-limit backoff", e)
            }
        }
    }

    private fun acquireSlot() {
        while (true) {
            val waitMs: Long
            synchronized(lock) {
                val now = clock()
                val cutoff = now - windowMs
                while (requestTimestamps.isNotEmpty() && requestTimestamps.first() <= cutoff) {
                    requestTimestamps.removeFirst()
                }
                if (requestTimestamps.size < maxRequests) {
                    requestTimestamps.addLast(now)
                    return
                }
                val oldest = requestTimestamps.first()
                waitMs = (oldest + windowMs - now).coerceAtLeast(1L)
            }
            try {
                sleeper(waitMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while waiting for Deezer rate-limit window", e)
            }
        }
    }

    private fun parseRetryAfterMs(header: String?): Long? {
        val seconds = header?.trim()?.toLongOrNull() ?: return null
        return (seconds * 1000L).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_MAX_REQUESTS = 50
        const val DEFAULT_WINDOW_MS = 5_000L
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_BASE_BACKOFF_MS = 1_000L
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
