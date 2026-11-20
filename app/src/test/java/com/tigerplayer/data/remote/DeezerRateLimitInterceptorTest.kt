package com.tigerplayer.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class DeezerRateLimitInterceptorTest {

    private fun buildChain(
        requestUrl: String = "https://api.deezer.com/chart/0/tracks",
        responder: (Request) -> Response
    ): Interceptor.Chain {
        val initialRequest = Request.Builder().url(requestUrl).build()
        return object : Interceptor.Chain {
            override fun request(): Request = initialRequest
            override fun proceed(request: Request): Response = responder(request)
            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 15_000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 15_000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 15_000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }
    }

    private fun response(request: Request, code: Int, retryAfter: String? = null): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Too Many Requests")
            .body("{}".toResponseBody("application/json".toMediaType()))
        if (retryAfter != null) builder.header("Retry-After", retryAfter)
        return builder.build()
    }

    @Test
    fun `enforces sliding window limit by sleeping when quota is reached`() {
        val nowMs = AtomicLong(10_000L)
        val sleeps = mutableListOf<Long>()
        val interceptor = DeezerRateLimitInterceptor(
            maxRequests = 3,
            windowMs = 5_000L,
            clock = { nowMs.get() },
            sleeper = { ms ->
                sleeps.add(ms)
                nowMs.addAndGet(ms)
            }
        )

        val chain = buildChain { req -> response(req, 200) }
        repeat(4) {
            val res = interceptor.intercept(chain)
            assertEquals(200, res.code)
        }

        assertEquals(listOf(5_000L), sleeps)
    }

    @Test
    fun `retries HTTP 429 with exponential backoff and succeeds`() {
        val nowMs = AtomicLong(1_000L)
        val sleeps = mutableListOf<Long>()
        val calls = AtomicInteger(0)
        val interceptor = DeezerRateLimitInterceptor(
            maxRequests = 50,
            windowMs = 5_000L,
            maxRetries = 3,
            baseBackoffMs = 500L,
            clock = { nowMs.get() },
            sleeper = { ms ->
                sleeps.add(ms)
                nowMs.addAndGet(ms)
            }
        )

        val chain = buildChain { req ->
            val attempt = calls.incrementAndGet()
            if (attempt <= 2) response(req, 429) else response(req, 200)
        }

        val res = interceptor.intercept(chain)
        assertEquals(200, res.code)
        assertEquals(3, calls.get())
        assertEquals(listOf(500L, 1_000L), sleeps)
    }

    @Test
    fun `honors Retry-After header on HTTP 429`() {
        val nowMs = AtomicLong(1_000L)
        val sleeps = mutableListOf<Long>()
        val calls = AtomicInteger(0)
        val interceptor = DeezerRateLimitInterceptor(
            maxRetries = 2,
            baseBackoffMs = 500L,
            clock = { nowMs.get() },
            sleeper = { ms ->
                sleeps.add(ms)
                nowMs.addAndGet(ms)
            }
        )

        val chain = buildChain { req ->
            if (calls.incrementAndGet() == 1) response(req, 429, retryAfter = "3") else response(req, 200)
        }

        val res = interceptor.intercept(chain)
        assertEquals(200, res.code)
        assertTrue(sleeps.contains(3_000L))
    }
}
