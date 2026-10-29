package com.tigerplayer.data.repository

/**
 * Diagnostic details and resolution steps for Spotify authentication failures.
 * Surfaces user-understandable explanations and setup guidance rather than cryptic
 * OAuth codes or fleeting toasts.
 */
data class SpotifyAuthError(
    val title: String,
    val description: String,
    val setupSteps: List<String> = emptyList(),
    val rawErrorCode: String? = null
) {
    companion object {
        fun missingClientId(): SpotifyAuthError = SpotifyAuthError(
            title = "Spotify Setup Required",
            description = "TigerPlayer needs a Spotify Client ID to connect. The repository setup steps have not been configured on this build.",
            setupSteps = listOf(
                "Open https://developer.spotify.com/dashboard and log into your Spotify account.",
                "Click 'Create app', enter an app name (e.g. TigerPlayer), and open its Settings.",
                "Under 'Redirect URIs', add: tigerplayer://callback",
                "Under 'Which API/SDKs are you planning to use?', check Web API and Spotify Android SDK.",
                "In your project root, add your Client ID to secrets.properties:\nSPOTIFY_CLIENT_ID=your_client_id_here",
                "Rebuild and relaunch TigerPlayer."
            ),
            rawErrorCode = "MISSING_CLIENT_ID"
        )

        fun noInternet(): SpotifyAuthError = SpotifyAuthError(
            title = "No Internet Connection",
            description = "TigerPlayer cannot reach Spotify authentication services without an active internet connection.",
            setupSteps = listOf(
                "Check that your device is connected to Wi-Fi or mobile cellular data.",
                "Confirm that you can load web pages in your browser.",
                "Return to TigerPlayer Settings and tap 'Connect' again."
            ),
            rawErrorCode = "NETWORK_UNAVAILABLE"
        )

        fun fromCallback(error: String): SpotifyAuthError = when (error.lowercase()) {
            "access_denied" -> SpotifyAuthError(
                title = "Authorization Cancelled",
                description = "The Spotify login was cancelled or the requested permissions were declined on the Spotify authorization screen.",
                setupSteps = listOf(
                    "Tap 'Connect' in TigerPlayer Settings to restart the login.",
                    "In the Spotify authorization screen, review the requested permissions.",
                    "Tap 'Agree' to allow TigerPlayer to connect with your Spotify account."
                ),
                rawErrorCode = error
            )

            "redirect_uri_mismatch" -> SpotifyAuthError(
                title = "Redirect URI Mismatch",
                description = "Spotify rejected the login because the app callback URL (tigerplayer://callback) is not registered in your Spotify Developer Dashboard.",
                setupSteps = listOf(
                    "Go to https://developer.spotify.com/dashboard and open your app.",
                    "Click 'Settings' and locate 'Redirect URIs'.",
                    "Add exactly: tigerplayer://callback",
                    "Click 'Save' at the bottom of the dashboard page.",
                    "Return to TigerPlayer and tap 'Connect' again."
                ),
                rawErrorCode = error
            )

            "invalid_client" -> SpotifyAuthError(
                title = "Invalid Client ID",
                description = "Spotify rejected the Client ID configured for this build. The value in secrets.properties may be incorrect.",
                setupSteps = listOf(
                    "Open https://developer.spotify.com/dashboard and check your App Client ID.",
                    "Ensure SPOTIFY_CLIENT_ID in secrets.properties exactly matches your dashboard Client ID.",
                    "Rebuild the application after updating secrets.properties."
                ),
                rawErrorCode = error
            )

            "unauthorized_client" -> SpotifyAuthError(
                title = "Client Not Authorized",
                description = "This Spotify app is not authorized to use the PKCE authorization code grant, or user access is restricted.",
                setupSteps = listOf(
                    "Verify your Spotify app status is active on developer.spotify.com/dashboard.",
                    "If the app is in Development mode, go to 'User Management' and add your Spotify account email.",
                    "Ensure Web API and Spotify Android SDK are selected under app settings."
                ),
                rawErrorCode = error
            )

            else -> SpotifyAuthError(
                title = "Spotify Login Failed",
                description = "Spotify returned an authorization error while processing the login request.",
                setupSteps = listOf(
                    "Verify your network connection is stable.",
                    "Check your Spotify Developer Dashboard settings.",
                    "Tap 'Connect' again to retry."
                ),
                rawErrorCode = error
            )
        }

        fun tokenExchangeFailed(details: String? = null): SpotifyAuthError = SpotifyAuthError(
            title = "Token Exchange Failed",
            description = "The authorization code was received from Spotify, but TigerPlayer could not exchange it for an access token.",
            setupSteps = listOf(
                "Verify that your internet connection is active and stable.",
                "If using a development Spotify app, confirm your account email is listed under 'User Management'.",
                "Tap 'Connect' again to request a fresh authorization code."
            ),
            rawErrorCode = details ?: "TOKEN_EXCHANGE_ERROR"
        )

        fun emptyToken(): SpotifyAuthError = SpotifyAuthError(
            title = "Empty Session Token",
            description = "Spotify authentication completed but returned an empty access token.",
            setupSteps = listOf(
                "Tap 'Connect' again to request a fresh session token.",
                "Check developer.spotify.com/dashboard for any Spotify API service notices."
            ),
            rawErrorCode = "EMPTY_TOKEN"
        )
    }
}
