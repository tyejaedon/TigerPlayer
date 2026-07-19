package com.example.tigerplayer

import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import com.example.tigerplayer.data.local.ThemeMode
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import com.example.tigerplayer.navigation.TigerPlayerNavGraph
import com.example.tigerplayer.ui.coverscreen.CoverScreenMiniHub
import com.example.tigerplayer.ui.coverscreen.rememberCoverScreenWindowState
import com.example.tigerplayer.ui.player.PipVisualizerSurface
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.settings.SettingsViewModel
import com.example.tigerplayer.ui.theme.TigerPlayerTheme
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // --- DEPENDENCIES ---
    @Inject
    lateinit var authManager: SpotifyAuthManager

    private val playerViewModel: PlayerViewModel by viewModels()
    private val isInPipMode = MutableStateFlow(false)
    private val authMessage = MutableStateFlow<String?>(null)

    private val redirectUri = "tigerplayer://callback"

    // --- 1. SPOTIFY AUTH RITUAL ---
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
                        } else {
                            authMessage.value = "Spotify login returned an empty token. Please retry."
                        }
                    } catch (e: Exception) {
                        Log.e("SpotifyAuth", "Ritual failed during token exchange: ${e.message}")
                        authMessage.value = "Spotify token exchange failed. Check connection and retry."
                    }
                }
            }
            AuthorizationResponse.Type.ERROR -> {
                if (response.error == "NO_INTERNET_CONNECTION") {
                    Log.e("SpotifyAuth", "Auth failed: Spotify login requires an active internet connection.")
                    Toast.makeText(this, "No internet connection. Check network and retry.", Toast.LENGTH_SHORT).show()
                    authMessage.value = "Spotify login needs internet access."
                } else {
                    Log.e("SpotifyAuth", "Auth Error: ${response.error}")
                    authMessage.value = "Spotify auth error: ${response.error ?: "Unknown"}"
                }
            }
            else -> {
                Log.w("SpotifyAuth", "Flow cancelled or unknown type.")
            }
        }
    }

    // --- 2. LIFECYCLE ---
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val pipMode by isInPipMode.collectAsState()
            val authMessageState by authMessage.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            val coverWindowState = rememberCoverScreenWindowState()
            val settingsViewModel: SettingsViewModel =
                hiltViewModel(checkNotNull(LocalViewModelStoreOwner.current) {
                    "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
                }, null)
            val settingsState by settingsViewModel.settingsState.collectAsState()
            val themeMode = settingsState.themeMode

            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            TigerPlayerTheme(
                darkTheme = useDarkTheme,
                pureAmoledBlack = settingsState.pureAmoledBlack,
                accentStyle = settingsState.accentStyle
            ) {
                LaunchedEffect(authMessageState) {
                    authMessageState?.let { message ->
                        snackbarHostState.showSnackbar(message)
                        authMessage.value = null
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (pipMode) {
                            PipVisualizerSurface(playerViewModel = playerViewModel)
                        } else if (coverWindowState.isCoverScreen) {
                            CoverScreenMiniHub(
                                playerViewModel = playerViewModel,
                                windowState = coverWindowState
                            )
                        } else {
                            val navController = rememberNavController()
                            TigerPlayerNavGraph(
                                navController = navController,
                                playerViewModel = playerViewModel
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldEnterPictureInPicture()) {
            enterPlayerPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }

    override fun onResume() {
        super.onResume()
        playerViewModel.refreshBluetoothRouteState()
    }

    private fun shouldEnterPictureInPicture(): Boolean {
        if (isFinishing || isDestroyed || isInPipMode.value) return false
        val uiState = playerViewModel.uiState.value
        return uiState.isPlaying && uiState.currentTrack != null
    }

    private fun enterPlayerPictureInPicture() {
        val aspectRatio = Rational(1, 1)
        val paramsBuilder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)

        enterPictureInPictureMode(paramsBuilder.build())
    }


    fun authenticateSpotify() {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val redirectUri = "tigerplayer://callback"

        if (clientId.startsWith("MISSING_")) {
            Log.e("SpotifyAuth", "Spotify client ID is missing in BuildConfig/secrets.properties")
            Toast.makeText(this, "Spotify is not configured on this build.", Toast.LENGTH_SHORT).show()
            authMessage.value = "Spotify is not configured on this build."
            return
        }

        if (!hasInternetConnection()) {
            Log.e("SpotifyAuth", "Auth launch blocked: no active internet connection")
            Toast.makeText(this, "No internet connection. Try again when online.", Toast.LENGTH_SHORT).show()
            authMessage.value = "No internet connection. Try again when online."
            return
        }

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

    private fun hasInternetConnection(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}