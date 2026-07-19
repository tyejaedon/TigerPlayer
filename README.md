# TigerPlayer 🐅🐺

> **[v2.0 "NEON VANGUARD"]**
> *A high-fidelity, dual-engine Android music player forged to unify the scattered archives of modern audio through Navier-Stokes fluid dynamics and time-bucketed intelligence.*

TigerPlayer 2.0 is a massive overhaul of the original architecture, bridging local lossless libraries, remote Subsonic servers, and the Spotify Cloud into a single, cohesive listening experience. Built with modern Android architecture, it features a GPU-accelerated fluid visualizer, dynamic neon theming, and a deep-analytics engine that maps your "Sonic Footprint."

---

## 🌌 The Neon Arsenal (Key Features)

### 🌊 Fluid Vortex Visualizer
Experience your music through a high-performance **GLSL 3.0** fluid simulation. The visualizer implements **Navier-Stokes equations** for real-time turbulence, featuring:
- **ACES Filmic Tone Mapping:** Cinematic color compression for high-dynamic-range visuals.
- **Bloom & Vignette:** Noise-driven turbulence fields with halo edge glows.
- **Aspect-Corrected Splats:** Fluid interactions that react to the music's frequency spectrum.

### 🧪 Sonic Footprint & Analytics
Your listening history is more than just a list. TigerPlayer uses complex SQL heuristics in **Room (The Vault)** to calculate listening axes:
- **Listening Axes:** Maps your taste across Acoustic, Electronic, Bass-Heavy, and Instrumental dimensions.
- **Neon Daylist:** Time-of-day bucketed discovery (Morning, Afternoon, Evening, Night) that adapts to your routine.
- **Heavy Rotation:** Real-time density tracking of your most-played tracks in recent windows.

### 🎙️ Acoustic Environment (DSP)
Tailor the audio output through integrated DSP profiles:
- **Vinyl Warmth:** Adds subtle harmonic saturation and low-end glue.
- **Concert Hall:** Expands the spatial image with optimized reverb tails.
- **Neutral:** Pure bit-perfect playback for the purists.

### 🔮 Vanguard UI & Neon Theming
The UI is no longer just glassmorphic; it's reactive. 
- **Neon Token Snapping:** Utilizing a custom `DominantColorExtractor`, the UI accents snap to canonical neon tokens (Cyber Cyan, Toxic Lime, Igni Red).
- **Ambient Auras:** High-resolution album art generates animated gradients that cast atmospheric glows across the dashboard.
- **Fiery Seeker:** A custom Compose Canvas progress bar with animated sine waves reacting to playback state.

---

## 🛠️ The Alchemy (Tech Stack)

*   **UI Engine:** Jetpack Compose (Material 3), Compose Navigation, Custom Canvas API.
*   **Visuals:** GLSL 3.0 Shaders (Navier-Stokes, ACES, Bloom).
*   **Architecture:** MVVM + Clean Architecture, Kotlin Coroutines, StateFlow, Dagger Hilt.
*   **Audio Core:** AndroidX Media3 (ExoPlayer, MediaSession) & Spotify App Remote SDK.
*   **Persistence:** Room Database ("The Vault") with complex metadata normalization for collaboration delimiters (feat, ft, &, etc.).
*   **Networking:** Retrofit2, OkHttp, Spotify Web API integration.
*   **Storage:** Jetpack DataStore for low-latency preference management.

---

## 🗡️ The Trial of the Grasses (Setup)

TigerPlayer requires API credentials to awaken the cloud engine.

### Prerequisites
1. Register an application on the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Whitelist your app's package name (`com.example.tigerplayer`) and your **SHA-1 fingerprint**.
3. Create a `secrets.properties` file in your root project directory.

### Configuration (`secrets.properties`)
```properties
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
LASTFM_API_KEY=your_lastfm_key
YOUTUBE_API_KEY=your_youtube_key
```

### Installation
1. Clone the repository.
2. Open in Android Studio (Ladybug or newer recommended for AGP 9.3+ support).
3. Sync Gradle (The "Firewall" strategy ensures KSP & Kotlin version synchronization).
4. Build and deploy to a device running **Android 13+**.

---

## 🐺 The Vanguard

Forged in Nairobi by **Jaedon**. 

*If you find a bug in the archives or wish to contribute a new ritual to the codebase, pull requests are welcome.*
