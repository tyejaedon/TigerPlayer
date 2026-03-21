package com.example.tigerplayer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import com.example.tigerplayer.data.repository.SpotifyRepository
import com.example.tigerplayer.navigation.TigerPlayerNavGraph
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.TigerPlayerTheme
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var spotifyRepository: SpotifyRepository

    private val playerViewModel: PlayerViewModel by viewModels()

    private val clientId = "3a9ef0f202a04e6290cf0cb3b32dd3ab"
    private val redirectUri = "tigerplayer://callback"
    // --- SPOTIFY AUTH RITUAL ---

    @Inject lateinit var authManager: SpotifyAuthManager
    private val spotifyAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val response = AuthorizationClient.getResponse(result.resultCode, result.data)

        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                val token = response.accessToken
                Log.d("SpotifyAuth", "Ritual Success! Saving token...")

                // 1. UPDATE THE MANAGER: This is the most important step!
                // This flips 'isSpotifyConnected' to true across the whole app.
                authManager.updateToken(token)

                // 2. FETCH INITIAL DATA:
                // Since the CloudScreen has a LaunchedEffect(isConnected),
                // it might handle this automatically, but doing it here is a safe 'Pre-fetch'.
                lifecycleScope.launch {
                    spotifyRepository.fetchUserPlaylists(token)
                    spotifyRepository.fetchUserSavedAlbums(token)
                }
            }
            AuthorizationResponse.Type.ERROR -> {
                Log.e("SpotifyAuth", "Auth Error: ${response.error}")
            }
            else -> {
                Log.w("SpotifyAuth", "Flow cancelled by user.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            TigerPlayerTheme {
                // 1. Initialize the Navigator
                val navController = rememberNavController()

                // 2. Launch the NavGraph
                // This handles Splash, Permissions, MainApp, and NavidromeLogin automatically.
                TigerPlayerNavGraph(navController = navController)
            }
        }
    }

    /**
     * Can be called from the UI via LocalContext.current as MainActivity
     */
    fun authenticateSpotify() {
        val builder = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.TOKEN,
            redirectUri
        )

        builder.setScopes(arrayOf(
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read"
        ))

        val request = builder.build()
        val intent = AuthorizationClient.createLoginActivityIntent(this, request)
        spotifyAuthLauncher.launch(intent)
    }
}