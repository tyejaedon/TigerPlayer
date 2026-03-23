# TigerPlayer 🐅🐺

> *A high-fidelity, dual-engine Android music player forged to unify the scattered archives of modern audio.*

TigerPlayer seamlessly bridges local lossless libraries, remote Navidrome/Subsonic servers, and the Spotify Cloud into a single, cohesive listening experience. Built entirely with modern Android architecture and Jetpack Compose, it features a dynamic, glassmorphic UI that reacts to the music's essence, extracting dominant colors to cast ambient auras across the screen while maintaining strict audio focus.

---

##  The Arsenal (Key Features)

### 🎼 The Dual-Engine Proxy
TigerPlayer does not compromise. A custom `MediaControllerManager` and ViewModel architecture acts as a universal proxy. It instantly routes playback commands (Play, Pause, Seek, Skip, Shuffle, Repeat) to either the local Android Media3 `ExoPlayer` or the Spotify App Remote. TigerPlayer intelligently manages Audio Focus, ensuring the local engine and the cloud engine never clash.

### 🔮 Dynamic Glass UI & The Fiery Seeker
The UI is a living artifact. Utilizing **Coil** and the **Android Palette API**, TigerPlayer extracts the dominant colors from high-resolution album art to generate smooth, animated gradients and adaptive text contrast on the fly. 
Playback progress is visualized through the **Fiery Seeker**—a custom Jetpack Compose Canvas implementation featuring an animated sine wave and fluid scrub controls that react to the music's state (Igni Red for playing, Aard Blue for paused).

### 🛡️ Vanguard Artist Profiles
Bypassing basic metadata, TigerPlayer queries the Spotify Web API to build rich, gamified artist dossiers. It aggregates high-resolution artist press photos, renown (popularity) scores, and genre mapping into a seamless "Vanguard Profile" without infinite loading screens or mismatched data.

### 📜 Synced Lyrics Engine
Sing along with the archives. A custom parsing engine reads `[mm:ss.ms]` timestamped lyrics and auto-scrolls them in real-time alongside the active track, featuring smooth active/inactive text transitions.

### 🐺 Hunter's Stats
Your listening history is permanently etched into the local Room database. Track your total listening hours, top artists, lossless track counts, and most played songs through the beautifully animated Home screen statistics.

---

##  The Alchemy (Tech Stack)

* **UI Engine:** Jetpack Compose, Material Design 3, Compose Navigation
* **Architecture:** MVVM, Kotlin Coroutines, StateFlow, Dagger Hilt (Dependency Injection)
* **Audio Core:** AndroidX Media3 (ExoPlayer, MediaSessionService) & Spotify App Remote SDK
* **Data & Networking:** Room Database (Offline Caching & History), Retrofit2, OkHttp, Jetpack DataStore
* **Image Mastery:** Coil (with hardware acceleration, exact-size scaling for memory optimization, and Palette extraction)

---

## 🗡️ The Trial of the Grasses (Setup & Installation)

To build and run TigerPlayer locally, you must provide your own API credentials to awaken the cloud engine.

### Prerequisites
1. Register an application on the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Whitelist your app's package name and your machine's **SHA-1 fingerprint** in the dashboard settings.
3. Retrieve your `CLIENT_ID` and `REDIRECT_URI`.

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/TigerPlayer.git
   ```
2. Open the project in Android Studio.
3. Navigate to `MainActivity.kt` and `SpotifyRepository.kt` to insert your Spotify `CLIENT_ID` and `REDIRECT_URI`.
4. Sync the Gradle project to pull down the Media3 and Spotify SDK dependencies.
5. Build and deploy to your Android device (Android 13+ recommended for optimal SurfaceFlinger performance).

*Note: To test the remote library sync, ensure you have a valid Navidrome or Subsonic server running with accessible credentials.*

---

##  The Vanguard

Forged in Nairobi by **Jaedon**. 

*If you find a bug in the archives or wish to contribute a new ritual to the codebase, pull requests are welcome.*
