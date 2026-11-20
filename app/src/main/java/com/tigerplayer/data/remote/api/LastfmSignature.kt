package com.tigerplayer.data.remote.api

import java.security.MessageDigest

/**
 * Computes Last.fm Web Services `api_sig` MD5 signatures.
 */
object LastfmSignature {

    private val EXCLUDED_KEYS = setOf("format", "callback")

    /**
     * Builds the MD5 signature for [params] and [apiSecret] per Last.fm auth spec:
     * sort parameter names lexicographically (excluding `format` and `callback`),
     * concatenate `key + value`, append [apiSecret], and return lowercase hex MD5.
     */
    fun sign(params: Map<String, String>, apiSecret: String): String {
        val payload = buildString {
            params.entries
                .asSequence()
                .filter { it.key !in EXCLUDED_KEYS }
                .sortedBy { it.key }
                .forEach { (key, value) ->
                    append(key)
                    append(value)
                }
            append(apiSecret)
        }
        val digest = MessageDigest.getInstance("MD5").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /**
     * Returns a new map containing [params] plus `"api_sig"` computed with [apiSecret].
     */
    fun signedParams(params: Map<String, String>, apiSecret: String): Map<String, String> {
        val signature = sign(params, apiSecret)
        return LinkedHashMap(params).apply {
            put("api_sig", signature)
        }
    }
}
