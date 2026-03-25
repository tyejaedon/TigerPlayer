package com.example.tigerplayer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import com.example.tigerplayer.data.repository.SpotifyRepository
import com.example.tigerplayer.navigation.TigerPlayerNavGraph
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.settings.SettingsViewModel
import com.example.tigerplayer.ui.settings.ThemeMode
import com.example.tigerplayer.ui.theme.TigerPlayerTheme
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // --- DEPENDENCIES ---
    @Inject
    lateinit var spotifyRepository: SpotifyRepository

    @Inject
    lateinit var authManager: SpotifyAuthManager

    private val playerViewModel: PlayerViewModel by viewModels()

    private val redirectUri = "tigerplayer://callback"

    // --- SPOTIFY AUTH RITUAL ---
    private val spotifyAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val response = AuthorizationClient.getResponse(result.resultCode, result.data)

        when (response.type) {
            AuthorizationResponse.Type.CODE -> {
                val authCode = response.code
                Log.d("SpotifyAuth", "Code acquired! Swapping for token...")

                lifecycleScope.launch {
                    try {
                        // Swap the code for the actual token securely
                        val token = authManager.exchangeCodeForToken(authCode, redirectUri)

                        if (token.isNotEmpty()) {
                            // THE MISSING WIRE: Tell the Oracle the connection is forged!
                            playerViewModel.onAuthSuccess(token)

                            // Fetch initial cloud data
                            spotifyRepository.fetchUserPlaylists(token)
                            spotifyRepository.fetchUserSavedAlbums(token)
                        }
                    } catch (e: Exception) {
                        Log.e("SpotifyAuth", "Ritual failed during token exchange: ${e.message}")
                    }
                }
            }
            AuthorizationResponse.Type.ERROR -> {
                Log.e("SpotifyAuth", "Auth Error: ${response.error}")
            }
            else -> {
                Log.w("SpotifyAuth", "Flow cancelled or unknown type.")
            }
        }
    }

    // --- LIFECYCLE ---
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            TigerPlayerTheme(darkTheme = useDarkTheme) {
                // THE GLOBAL ANCHOR: Ensures background colors are applied to ALL tabs
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    TigerPlayerNavGraph(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
    /**
     * Cast this sign from the UI via LocalContext.current as MainActivity
     * to trigger the Spotify login flow.
     */
    fun authenticateSpotify() {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val builder = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.CODE,
            redirectUri
        )

        builder.setScopes(arrayOf(
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read",
            "user-read-private", // The "Gatekeeper" fix
            "streaming"          // Required for the Play button to work
        ))

        val request = builder.build()
        val intent = AuthorizationClient.createLoginActivityIntent(this, request)
        spotifyAuthLauncher.launch(intent)
    }
}