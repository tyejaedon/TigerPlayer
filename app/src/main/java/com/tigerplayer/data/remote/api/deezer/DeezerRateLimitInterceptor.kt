package com.tigerplayer.data.remote.api.deezer

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Enforces Deezer's 50-requests-per-5-seconds rate limit using a sliding window
 * without holding the lock across sleeps, and retries HTTP 429 responses with backoff.
 */
class DeezerRateLimitInterceptor(
    private val maxRequests: Int = 50,
    private val timeWindowMillis: Long = 5_000L,
    private val maxRetryAttempts: Int = 3,
    private val maxRetryAfterMillis: Long = 30_000L,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val sleepMs: (Long) -> Unit = Thread::sleep
) : Interceptor {

    private val requestTimestamps = ArrayDeque<Long>()
    private val lock = ReentrantLock()

    override fun intercept(chain: Interceptor.Chain): Response {
        acquireRateLimitSlot(chain)
        var response = chain.proceed(chain.request())

        var attempt = 1
        while (response.code == 429 && attempt <= maxRetryAttempts) {
            val retryAfterHeader = response.header("Retry-After")
            response.close()

            val backoffMillis = parseRetryAfterMillis(retryAfterHeader)
                ?: (1000L * (1 shl attempt)) // 2s, 4s, 8s
            sleepCancelAware(chain, backoffMillis)
            acquireRateLimitSlot(chain)
            response = chain.proceed(chain.request())
            attempt++
        }

        return response
    }

    private fun acquireRateLimitSlot(chain: Interceptor.Chain) {
        while (true) {
            if (chain.call().isCanceled()) {
                throw IOException("Canceled")
            }

            var waitMillis = 0L
            lock.withLock {
                val now = nowMs()
                while (requestTimestamps.isNotEmpty() && now - requestTimestamps.first() >= timeWindowMillis) {
                    requestTimestamps.removeFirst()
                }

                if (requestTimestamps.size < maxRequests) {
                    requestTimestamps.addLast(now)
                    return
                }

                waitMillis = (timeWindowMillis - (now - requestTimestamps.first())).coerceAtLeast(1L)
            }

            sleepCancelAware(chain, waitMillis)
        }
    }

    private fun sleepCancelAware(chain: Interceptor.Chain, durationMillis: Long) {
        if (durationMillis <= 0L) return
        if (chain.call().isCanceled()) {
            throw IOException("Canceled")
        }
        try {
            sleepMs(durationMillis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted during rate-limit wait", e)
        }
        if (chain.call().isCanceled()) {
            throw IOException("Canceled")
        }
    }

    private fun parseRetryAfterMillis(header: String?): Long? {
        val seconds = header?.trim()?.toLongOrNull() ?: return null
        if (seconds <= 0L) return null
        return (seconds * 1000L).coerceAtMost(maxRetryAfterMillis)
    }
}
