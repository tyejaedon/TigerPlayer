package com.example.tigerplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import com.example.tigerplayer.navigation.TigerPlayerNavGraph
import com.example.tigerplayer.ui.home.HomeViewModel
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
    lateinit var authManager: SpotifyAuthManager

    private val playerViewModel: PlayerViewModel by viewModels()
    private val homeViewModel : HomeViewModel by viewModels()

    private val redirectUri = "tigerplayer://callback"

    // --- 1. THE PERMISSION RITUAL (THE SCAN FIX) ---
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted =
            permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false

        if (audioGranted) {
            Log.d("TigerPlayer", "Archive access granted. Initiating primary scan.")
            // 🔥 THE FIX: Instantly trigger the scan the moment permissions are granted.
            // This ensures the ScanningOverlay UI shows up and populates the zero-state.
            playerViewModel.loadLocalAudio(forceRefresh = true)
        } else {
            Log.e("TigerPlayer", "Audio permissions denied. The local archive remains locked.")
        }
    }

    // --- 2. SPOTIFY AUTH RITUAL ---
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
                        val token = authManager.exchangeCodeForToken(authCode, redirectUri)
                        if (token.isNotEmpty()) {
                            playerViewModel.onAuthSuccess(token)
                            Log.d("SpotifyAuth", "Ritual complete. ViewModels will auto-sync.")
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

    // --- 3. LIFECYCLE ---
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request necessary permissions immediately upon launch
        requestSystemPermissions()

        setContent {
            val settingsViewModel: SettingsViewModel =
                hiltViewModel(checkNotNull(LocalViewModelStoreOwner.current) {
                    "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
                }, null)
            val themeMode by settingsViewModel.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            TigerPlayerTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    TigerPlayerNavGraph(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        homeViewModel = homeViewModel
                    )
                }
            }
        }
    }

    private fun requestSystemPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS) // Highly recommended for Audio Services

        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    fun authenticateSpotify() {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val redirectUri = "tigerplayer://callback"

        val builder = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.CODE,
            redirectUri
        ).apply {
            setScopes(arrayOf(
                "playlist-read-private",
                "playlist-read-collaborative",
                "user-library-read",
                "user-read-private",
                "streaming"
            ))
            setShowDialog(true)
        }

        val request = builder.build()
        val intent = AuthorizationClient.createLoginActivityIntent(this, request)
        spotifyAuthLauncher.launch(intent)
    }
}