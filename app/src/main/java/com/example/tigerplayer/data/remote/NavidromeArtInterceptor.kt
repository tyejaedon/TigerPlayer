package com.example.tigerplayer.data.remote

import android.net.Uri
import androidx.core.net.toUri
import coil.intercept.Interceptor
import coil.request.ImageResult

/**
 * Signs opaque `navidrome://art/<id>` requests just before Coil fetches them (issue #44).
 *
 * Artwork URIs are persisted alongside tracks, so they must not carry a rotating Subsonic token.
 * Resolving here keeps every `AsyncImage` call site unchanged, and means a cached or restored
 * artwork URI still loads after the original token has expired.
 */
class NavidromeArtInterceptor(
    private val signer: NavidromeUrlSigner
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val uri = when (val data = request.data) {
            is Uri -> data
            is String -> runCatching { data.toUri() }.getOrNull()
            else -> null
        }

        if (uri == null || !NavidromeUri.isNavidrome(uri)) {
            return chain.proceed(request)
        }

        // Without credentials there is nothing sensible to fetch; let Coil fail normally so the
        // caller's error placeholder shows. Never log the URI - a signed one carries a token.
        val signed = signer.sign(uri) ?: return chain.proceed(request)

        // Cache keys must be pinned to the *opaque* URI. Each sign() call mints a fresh salt, so
        // the signed URL differs every time; letting Coil derive keys from it would make every
        // lookup a miss and re-download artwork on every scroll.
        val stableKey = uri.toString()

        return chain.proceed(
            request.newBuilder()
                .data(signed.toString())
                .memoryCacheKey(stableKey)
                .diskCacheKey(stableKey)
                .build()
        )
    }
}

