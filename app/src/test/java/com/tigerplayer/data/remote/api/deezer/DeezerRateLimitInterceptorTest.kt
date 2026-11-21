package com.tigerplayer.data.remote.api.deezer

import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DeezerRateLimitInterceptorTest {

    private fun buildResponse(request: Request, code: Int, retryAfter: String? = null): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Too Many Requests")
            .body("{}".toResponseBody("application/json".toMediaType()))
        if (retryAfter != null) {
            builder.header("Retry-After", retryAfter)
        }
        return builder.build()
    }

    private fun mockChain(
        request: Request = Request.Builder().url("https://api.deezer.com/chart").build(),
        canceled: () -> Boolean = { false },
        onProceed: (Request) -> Response
    ): Interceptor.Chain {
        val call = mockk<Call>()
        every { call.isCanceled() } answers { canceled() }
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.call() } returns call
        every { chain.proceed(any()) } answers { onProceed(firstArg()) }
        return chain
    }

    @Test
    fun `requests within window proceed without sleeping and 51st sleeps until oldest expires`() {
        var currentTimeMs = 1_000L
        val sleeps = mutableListOf<Long>()
        val interceptor = DeezerRateLimitInterceptor(
            maxRequests = 50,
            timeWindowMillis = 5_000L,
            nowMs = { currentTimeMs },
            sleepMs = { ms ->
                sleeps += ms
                currentTimeMs += ms
            }
        )

        val chain = mockChain { req -> buildResponse(req, 200) }

        repeat(50) {
            currentTimeMs += 10L
            interceptor.intercept(chain).close()
        }
        assertTrue("First 50 requests should not sleep", sleeps.isEmpty())

        // 51st request arrives before the 5000ms window from the first request (at 1010ms) expires
        interceptor.intercept(chain).close()
        assertEquals(1, sleeps.size)
        assertTrue("51st request should sleep for remaining window", sleeps.first() > 0L)
    }

    @Test
    fun `http 429 retries respecting Retry-After header and stops after success`() {
        var currentTimeMs = 10_000L
        val sleeps = mutableListOf<Long>()
        val interceptor = DeezerRateLimitInterceptor(
            nowMs = { currentTimeMs },
            sleepMs = { ms ->
                sleeps += ms
                currentTimeMs += ms
            }
        )

        var calls = 0
        val chain = mockChain { req ->
            calls++
            if (calls <= 2) buildResponse(req, 429, retryAfter = "3")
            else buildResponse(req, 200)
        }

        val response = interceptor.intercept(chain)
        assertEquals(200, response.code)
        assertEquals(3, calls)
        assertEquals(listOf(3_000L, 3_000L), sleeps)
    }

    @Test
    fun `http 429 stops retrying after maxRetryAttempts`() {
        var currentTimeMs = 10_000L
        val sleeps = mutableListOf<Long>()
        val interceptor = DeezerRateLimitInterceptor(
            maxRetryAttempts = 2,
            nowMs = { currentTimeMs },
            sleepMs = { ms ->
                sleeps += ms
                currentTimeMs += ms
            }
        )

        var calls = 0
        val chain = mockChain { req ->
            calls++
            buildResponse(req, 429)
        }

        val response = interceptor.intercept(chain)
        assertEquals(429, response.code)
        assertEquals(3, calls) // initial + 2 retries
        assertEquals(listOf(2_000L, 4_000L), sleeps)
    }

    @Test
    fun `canceled call throws IOException without sleeping`() {
        val sleeps = mutableListOf<Long>()
        val interceptor = DeezerRateLimitInterceptor(
            maxRequests = 1,
            timeWindowMillis = 5_000L,
            nowMs = { 1_000L },
            sleepMs = { sleeps += it }
        )

        var isCanceled = false
        val chain = mockChain(canceled = { isCanceled }) { req -> buildResponse(req, 200) }
        interceptor.intercept(chain).close()

        isCanceled = true
        assertThrows(IOException::class.java) {
            interceptor.intercept(chain)
        }
        assertTrue(sleeps.isEmpty())
    }
}
