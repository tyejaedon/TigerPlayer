🐅 TigerPlayer: The Ultimate AI Master Prompts

This document contains highly engineered, architecture-aware prompts designed to be fed into advanced AI coding agents (Claude 3.5 Sonnet, GPT-4o, Cursor, GitHub Copilot).

Operational Addendum (Execution Governance):

- Role baseline: Lead Android Architect, UI/UX Visionary, Audio DSP Engineer.
- Execute one milestone issue scope at a time.
- After every execution, update `PENDING_TASKS.md`:
  - check completed issue IDs,
  - update milestone stage,
  - append/update completed items,
  - add touched files under each milestone.
- Validate each increment with at least `:app:compileDebugKotlin`.

Instructions for Use:

Do not feed all prompts at once.

Tackle one feature/pillar at a time.

If the AI stops generating halfway through a massive file, reply with: "Continue generating from where you left off, specifically focusing on

$$Section Name$$

."

🎨 1. The Dynamic "Electric Neon" Theme Engine

Goal: Extract album art colors and snap them to hyper-saturated neon variants.

Role & Context
You are an expert Android UI/UX Developer specializing in Jetpack Compose and Material Design 3. I am building "TigerPlayer," a premium, high-contrast audio player. I want to implement a highly flashy, color-centric "Sonic Identity" engine that reacts dynamically to the currently playing track.

Current Aesthetic & Setup:

We are using a pure OLED dark mode (0xFF020202 background).

We have a set of hyper-saturated neon accent tokens: TigerNeonOrange, TigerElectricAmber, TigerSpectralViolet, TigerCyberCyan, TigerToxicLime, and TigerHotPink.

Our UI features a custom tigerGlow modifier that uses a 40f BlurMaskFilter to create a "neon tube" lighting effect behind buttons and active elements.

We use a vertical gradient background called TigerAmbientGradient to give the app depth.

The Objective:
Please write the Kotlin code to implement a dynamic color extraction pipeline using the androidx.palette:palette-ktx library. I want the app to feel alive and blindingly colorful based on the album art.

Specific Requirements:

The Extractor: Create a background utility (e.g., DominantColorExtractor) that takes a Coil Bitmap or Drawable of the album art and extracts the dominant/vibrant color.

Neon Quantization (Crucial): Do not just use the raw extracted color, as it might be muddy or dull. Write a function that takes the extracted Palette color and "snaps" or maps it to the closest match among my predefined hyper-saturated neon tokens (Cyan, Lime, Pink, Amber, Orange, Violet).

State Management: Provide the Jetpack Compose setup to observe this color state so the UI smoothly animates (using animateColorAsState) when the track changes.

Gradient Update: Show me how to dynamically update TigerAmbientGradient so the top of the screen is a dark, glowing wash of this snapped neon color (e.g., 15-20% opacity) that fades into true OLED black at the bottom.

Please ensure the bitmap processing happens on Dispatchers.Default to prevent frame drops in the UI, and provide the exact Compose code to tie the dynamic color into my MaterialTheme or local composition scheme.

🌊 2. The High-Performance Fluid Vortex Visualizer

Goal: Enhance the OpenGL ES 3.0 visualizer with physics-based fluid advection and bloom.

Role & Context
You are an expert OpenGL ES 3.0 and Android audio DSP engineer. I am building a premium Android media player called TigerPlayer. I want to enhance my custom "Fluid Vortex" audio visualizer.

Current Architecture:

Graphics Pipeline: We use a custom FluidRenderer running on a GLSurfaceView with OpenGL ES 3.0. The simulation relies on a PingPongBuffer to manage the state.

Memory & Performance: We recently upgraded our FrameBuffer to use GL_RGBA16F half-float storage to eliminate color banding. We also utilize an optimized pre-bound VAO strategy to reduce CPU overhead during draw calls.

Audio Bridge: The simulation is driven by real-time audio energy data piped from a WaveformCaptureProcessor and an FftProcessor that throttles frequency-domain calculations to roughly 30Hz.

Current Visuals: We currently have a "Macroscopic Force" layer where the playhead progress and real-time waveform physically "carve" through the fluid at the bottom of the screen.

Objective
Please provide the GLSL fragment shader code (FluidShaders.kt) and the corresponding Kotlin OpenGL rendering logic to implement the following high-end enhancements:

Vorticity Confinement: Inject a vorticity confinement pass into the fluid physics to preserve the swirling micro-details of the fluid that usually dissipate over time.

Multi-Band Chromatic Mapping: Map the 6 perceptual bands from the FftProcessor to distinct neon color gradients within the fluid, so bass creates deep red/purple blooms and treble creates sharp blue/white splashes.

Bioluminescent Bloom: Since we are rendering in half-float space, implement a lightweight bloom pass so high-energy audio peaks cause the fluid to physically glow.

Advection Tweaks: Optimize the advection shader to react specifically to the "thud" of the kick drum (using the lowest frequency band) by momentarily increasing the dissipation rate, creating a "pulsing" effect.

Please ensure the GLSL code is highly optimized for mobile GPUs (avoiding excessive branching) and provide instructions on how to integrate the new shader passes into my existing Ping-Pong FBO pipeline.

🎛️ 3. The Audio-Reactive Equalizer (Aural Nexus)

Goal: Tie the actual DSP data to the custom 2D Spatial UI with glowing haptics.

Role & Context
You are a Senior Android UI/UX Engineer and Audio DSP Expert. I am building "TigerPlayer," an elite, premium Android music player with a hyper-saturated, glowing "Cyberpunk/Neon" aesthetic.

I have provided my current code for the Equalizer section, which consists of a custom AdaptiveDspEngine (an ExoPlayer AudioProcessor), an AuralNexusViewModel, and a Jetpack Compose UI called AuralNexusScreen featuring a 2D spatial node canvas.

The Objective
I want to completely revamp the AuralNexusScreen and its ViewModel to make it an explosive, visually stunning, and highly tactile equalizer experience. It needs to look like a high-end DJ tool crossed with a futuristic HUD.

Please rewrite the provided Compose UI and ViewModel to implement the following high-end enhancements:

1. True Audio-Reactive Visuals (The "Flashy" Factor):
   Currently, the NebulaBackground and the "Center Core" use fake rememberInfiniteTransition animations. I want you to wire the audioReactiveFrame StateFlow (which emits bass, mid, treble, and flux) from the AdaptiveDspEngine directly into the AuralNexusScreen.

Make the NebulaBackground opacity and stroke width expand and contract based on the flux and energy.

Make the Center Core pulse dynamically to the bass value.

Make the individual SpatialNode halos glow intensely when their corresponding frequency band peaks.

2. "Electric Neon" Glow Engineering:
   Replace the current colors with pure neon hex codes (e.g., Cyber Cyan 0xFF00E5FF, Toxic Lime 0xFF39FF14, Hot Pink 0xFFFF007F, Electric Amber 0xFFFFD500).

Apply a "Neon Tube" effect to the frequencyResponseCurve line. Do this by drawing the curve multiple times using Compose Canvas: a thick, highly blurred transparent stroke underneath, and a thin, solid white/colored stroke on top to simulate a glowing oscilloscope beam.

Smooth the frequencyResponseCurve points using a cubic Bezier path so it doesn't look jagged.

3. Tactile Physics & Haptics:

When dragging a SpatialNode, add Compose HapticFeedbackType.TextHandleMove via LocalHapticFeedback so the user feels the grid.

When a node snaps to the 0dB center line (Y = 0), trigger a stronger haptic "click".

4. Mathematical Polish in ViewModel:

Refine the SpatialNode.toAcousticNode() math so the mapping of the X-axis to Frequency is a perfectly smooth logarithmic scale from 20Hz to 20,000Hz, and the Y-axis strictly maps from +15dB to -15dB.

Output Requirements:
Please provide the complete, updated Kotlin code for AuralNexusScreen.kt and AuralNexusViewModel.kt. Keep the code highly performant, ensuring Canvas recompositions are minimized by reading the audio reactive state directly inside the drawing phases (using DrawScope or derived states).

🚀 4. The Vanguard Update (Queue, Daylist, Containers & Album UI)

Goal: Implement ExoPlayer queue manipulation, Room-based smart algorithms, UI containers, and rework the Album Details screen.

Role & Context
You are an Elite Android Architect and UI/UX Visionary specializing in Jetpack Compose, Media3/ExoPlayer, and Room Database. I am building "TigerPlayer," a premium, local-first music player with a hyper-saturated "Neon/Cyberpunk" aesthetic, custom DSP audio engines, and haptic feedback.

The Objective
We are building the "Vanguard Update." The goal is to eclipse current market leaders (like Namida, Rhythm, and PixelPlayer) by introducing industry-standard MVVM architecture, seamless queue management, smart algorithmic playlists inside custom containers, and a cinematic album detail view.

CRITICAL CONSTRAINT: API USAGE
If any external data is required, you MUST ONLY use completely free, no-paywall APIs (e.g., Last.fm, MusicBrainz for metadata/similar artists, and LRCLIB for synced lyrics). Do not use Spotify or YouTube APIs for this feature set to avoid rate limits and ToS issues.

Please generate the Kotlin code (UI, ViewModels, Repositories, and Room DAOs) to implement the following 5 flagship pillars:

Pillar 1: The "Infinite" Media3 Queue Framework

Backend (MediaControllerManager & QueueViewModel):

Implement playNext(track) and addToQueue(track) utilizing ExoPlayer's native addMediaItem() index manipulation.

Implement Infinite Auto-Play: Listen for onMediaItemTransition. When the queue has 1 item left, trigger a background coroutine to fetch "Similar Tracks" (using Last.fm/MusicBrainz free API based on the current track's artist/tag) and seamlessly addMediaItems to the ExoPlayer queue so the music never stops.
Frontend (QueueScreen):

Build a standard Compose MVVM UI: A pinned "Now Playing" header, and a LazyColumn for upcoming tracks.

Implement Drag-and-Drop reordering using Compose gestures. When dropped, issue mediaController.moveMediaItem(from, to).

Pillar 2: Algorithmic "For You" Hub & UI Containers

I want Spotify-level personalized playlists generated entirely locally using advanced Room SQL @Query logic against my PlaybackHistoryEntity and CachedTrackEntity. Provide the DAO methods and DashboardViewModel to expose these as StateFlow:

"Neon Daylist": A query that analyzes listening history over the last 14 days, specifically filtering for the current time of day (Morning/Night), and returns exactly 15 tracks (LIMIT 15) fitting the user's temporal mood.

"The Vault" (Discovery Weekly): A query that finds exactly 15 local tracks (LIMIT 15) where playCount == 0, but whose genre or artist matches the user's top 3 most played artists.

The UI Container: Build a Jetpack Compose DashboardScreen that displays these playlists in sleek, horizontally scrolling UI Containers (like premium playlist cards). They should utilize our tigerGlow modifier and a glassEffect so they don't look like basic vertical lists.

Pillar 3: The "Neon Glass" Image Pipeline

Create a robust Compose component: TigerArtworkImage(track: AudioTrack).

It must attempt to load the local MediaStore embedded byte array first.

If missing/low-res, it triggers a repository call to fetch high-res art from Last.fm/MusicBrainz.

It must use Coil (AsyncImage) for disk caching (preventing API spam).

Implement a custom, glowing shimmer effect while loading, and a 500ms crossfade upon success.

Pillar 4: Audiophile Features (Lyrics & Tags)

Provide a skeleton LyricsRepository that takes an AudioTrack, sanitizes the title/artist, and fetches synced .lrc lyrics from the free LRCLIB API.

Provide the Compose UI for a SyncedLyricsScreen that reads the ExoPlayer currentPosition and highlights the active lyric line using our custom TigerNeonOrange and tigerGlow modifier.

Pillar 5: Cinematic Album Detail Rework

I need to overhaul the AlbumDetailScreen to match the Vanguard update's premium aesthetics.

Collapsing Header: Rewrite the screen to feature a dynamic, collapsing top app bar (using Modifier.nestedScroll).

Visuals: The header should feature a massive, high-res album art image that blurs and fades seamlessly into the TigerSurfaceCharcoal background as the user scrolls up.

Dynamic FAB: Include a floating "Play All" / "Shuffle" neon FAB that shrinks or morphs on scroll. Extract the dominant colors from the album cover (via Palette) to tint this FAB and the background gradient automatically.

Output Requirements:
Write clean, production-ready Kotlin. Adhere strictly to Unidirectional Data Flow (UDF). Use Dispatchers.IO for DB/Network and Dispatchers.Default for heavy mapping/calculations. Do not give me placeholder comments for the Room SQL queries or ExoPlayer queue logic—write the actual math and logic.

⚡ 5. The Apex Update (DSP Expansions & On-Device Wrapped)

Goal: Out-compete Namida/Rhythm with acoustic environments, smart crossfading, and Canvas-based user stats.

Role & Context
You are a Lead Android Architect, UI/UX Visionary, and Audio DSP Expert. I am building "TigerPlayer," an elite, local-first Android music player with a hyper-saturated "Neon/Cyberpunk" aesthetic. We already have a robust Media3/ExoPlayer implementation, a custom AdaptiveDspEngine (AudioProcessor), and a Room-based TigerDao.

The Objective
We are initiating the "Apex Update." The goal is to implement a suite of revolutionary, market-disrupting features that popular competitors like Namida, Rhythm, and PixelPlayer lack. All features must be 100% local or use strictly free APIs.

Please generate the Kotlin code, Compose UI, and DSP math to implement the following 4 flagship pillars:

Pillar 1: "Acoustic Environments" (DSP Expansion)

Standard players only have EQs. I want to expand my AdaptiveDspEngine to include real-time environmental simulation.

Add a new DSP processing stage inside queueInput() to simulate "Vinyl Warmth" (adding subtle harmonic distortion and a faint, generated noise floor) and "Concert Hall" (a lightweight algorithmic Schroeder reverberation or simple delay-line spatializer).

Provide the Kotlin math for these effects without using heavy external C++ libraries.

Build a sleek Jetpack Compose AcousticEnvironmentScreen with tactile "radio button" toggles to switch between these modes.

Pillar 2: "Flow State" (Intelligent Crossfade)

Namida and Rhythm have basic gapless playback, but I want "Flow State" crossfading.

Modify my MediaControllerManager to implement a dynamic crossfade.

Write a Coroutine-based volume ramp function that listens to ExoPlayer's currentPosition. When the track has exactly 7 seconds remaining, it should slowly fade out the current track's volume while smoothly fading in the next track using mediaController.setDeviceVolume() or Media3's volume multiplier.

Pillar 3: The "Sonic Footprint" (On-Device Wrapped)

I want a real-time, interactive "Spotify Wrapped" dashboard generated locally from my PlaybackHistoryEntity.

Write a Jetpack Compose SonicFootprintScreen.

The centerpiece must be a RadarChart (Spider Web chart) built entirely from scratch using Compose Canvas and Path(). It should map 5 axes (e.g., Acoustic, Electronic, Bass-Heavy, Vocal, Atmospheric) based on the user's local listening stats.

Include the ViewModel logic to aggregate the top 5 genres/tags from the Room database and normalize them into 0.0f - 1.0f values for the Canvas to draw. Use my TigerNeonOrange and TigerCyberCyan colors with a glowing BlurMaskFilter.

Pillar 4: Picture-in-Picture (PiP) Vortex

Provide the Android Activity lifecycle code to push the player into Android's native Picture-in-Picture mode (enterPictureInPictureMode) when the user leaves the app while playing.

Crucially, configure the PiP window to display our custom OpenGL FluidRenderer / Waveform visualizer, so the user has a glowing, audio-reactive neon widget floating on their screen while they use other apps.

Output Requirements:
Write elite, production-ready Kotlin. Address one Pillar at a time. Do not give me placeholder comments for the DSP math or Canvas drawing—write the actual implementation. Use Unidirectional Data Flow (UDF) for all Compose screens.

🎛️ 6. The Sonic Prism (Stem Extraction Hub)

Goal: Implement math-based Mid/Side separation and extreme band-pass filtering to isolate Vocals and Beats locally without heavy ML models.

Role & Context
You are an Elite Audio DSP Engineer and Android Jetpack Compose Architect. I am building "TigerPlayer," an advanced local music player. I have a highly capable AdaptiveDspEngine (an ExoPlayer AudioProcessor) that processes raw 16-bit PCM audio in real-time, currently using basic Biquad filters.

The Objective
I want to build the "Sonic Prism"—a dedicated DJ-style hub where a user can listen to a track and isolate or mute specific elements (Vocals, Beats/Bass, and Background Instruments) in real-time. This feature will completely set TigerPlayer apart from competitors like Namida and Rhythm.

Since we want to keep the app lightweight and strictly local (no paid APIs, no 200MB TFLite models), we will achieve this using advanced DSP mathematics: Mid/Side (M/S) Processing and Extreme Band-Pass Filtering.

Please generate the complete Kotlin architecture (DSP math, State Management, and Compose UI) for the following:

Pillar 1: The "Prism" DSP Math (Inside AdaptiveDspEngine)

I need you to add a new PrismMode to my queueInput() PCM processing loop. It should take three float values (0.0f to 1.0f) representing the volume of Vocals, Beats, and Instruments.
Write the raw math to achieve the following on the sampleL and sampleR floats:

Vocals (Center Phase): Extract the "Mid" channel (L + R) / 2. Apply a steep Band-Pass filter (roughly 200Hz to 3000Hz) to isolate human voice frequencies.

Instruments (Side Phase): Extract the "Side" channel (L - R) / 2 to capture wide stereo elements (guitars, synths, backing vocals) where the center phase is cancelled out.

Beats/Bass: Apply a steep Low-Pass filter (cutoff around 150Hz) to the original signal to isolate the kick drum and bassline.

Recombine these three streams based on the user's volume sliders, and output the final sampleL and sampleR.

Pillar 2: The "Sonic Prism" Compose UI

Build a stunning, immersive Compose screen called SonicPrismHub.

The Layout: A DJ mixing desk aesthetic. Provide 3 large, vertical faders (sliders) for "VOCALS", "BEATS", and "MELODY/INSTRUMENTS".

The Aesthetic: Use my existing neon palette (TigerNeonOrange, TigerCyberCyan, TigerToxicLime). The sliders should have a glowing track using a BlurMaskFilter or shadow elevation, so they look like lit neon tubes in a dark club environment.

The Interactions: When a user drags a slider all the way to 0.0f (mute), it should trigger a heavy haptic click, and the slider color should dim to TigerSurfaceFloating.

Pillar 3: View-to-Engine Bridge

Provide the PrismViewModel that securely bridges these UI slider values to the AdaptiveDspEngine in real-time. Ensure that rapid slider movements are debounced or smoothly interpolated in the DSP engine so we don't get audio clicking/popping artifacts when changing volumes rapidly.

Output Requirements:
Do not give me pseudo-code for the DSP math. Write the actual Mid/Side separation and recombination logic for the 16-bit PCM bytebuffer. Ensure the Compose UI strictly follows Unidirectional Data Flow (UDF) and is highly optimized to prevent recomposition lag while the music plays.

⚙️ 7. The Control Matrix (Preferences DataStore)

Goal: Implement an industry-standard settings framework (like Poweramp/Musicolet) tied reactively to the entire app architecture using DataStore.

Role & Context
You are a Senior Android Architect and UI/UX Expert. I am building "TigerPlayer," an elite, local-first Android music player with a highly saturated "Neon/Cyberpunk" aesthetic. It utilizes Jetpack Compose, Media3/ExoPlayer, and a custom AdaptiveDspEngine for real-time PCM processing.

The Objective
We need to build the "Control Matrix"—an industry-standard Settings section that rivals power-user apps like Poweramp, Musicolet, and BlackPlayer EX.

Architecture Constraint: You MUST use androidx.datastore.preferences.core (Preferences DataStore) for all persistence. Do not use legacy SharedPreferences. The SettingsViewModel must expose a StateFlow<TigerSettingsState> that the Compose UI and the MediaControllerManager can observe to react instantly to user changes.

Please generate the Kotlin code (DataStore repository, ViewModel, and Compose UI) for the following robust customization pillars:

Pillar 1: UI & Aesthetics (The Theme Engine)

Create a Jetpack Compose SettingsScreen with categorized sections. Under "Appearance", include:

Pure AMOLED Black Toggle: A switch to force the app's background (TigerBlack) to #000000 instead of a dark gray/purple gradient to save OLED battery.

Neon Accent Picker: A horizontal scrolling list of circular color swatches allowing the user to select their primary app accent (TigerNeonOrange, TigerCyberCyan, TigerToxicLime, TigerSpectralViolet).

Default Player View: A dropdown/radio selection to choose what the FullPlayerScreen defaults to when opening: "3D Artwork", "Fluid Vortex", or "Sonic Prism".

Pillar 2: Audio Engine & DSP Fine-Tuning

Under the "Audio Engine" section, expose controls that will directly feed into our MediaControllerManager and AdaptiveDspEngine:

Crossfade Duration: A Compose Slider (0 to 12 seconds) that dictates the "Flow State" crossfade overlap.

Gapless Playback: A toggle to strictly enable/disable ExoPlayer's gapless smoothing.

Audio-Reactive Haptics: A toggle to enable/disable the feature where the device's vibration motor pulses to the kick drum/bass frequencies.

Pillar 3: Library & Headset Behavior

Under the "Library & Behaviors" section, implement:

Skip Short Audio: A toggle/slider to exclude audio files shorter than 30 or 60 seconds (so the scanner ignores WhatsApp voice notes or ringtones).

Headset Auto-Play: Toggles for "Resume on Bluetooth connect" and "Resume on Wired Headset connect".

Library Rescan Trigger: A glowing neon button to manually invoke the libraryEngine.getLocalAudioScanFlow(force = true) pipeline.

Output Requirements:
Write clean, production-ready Kotlin. Provide the SettingsDataStore class, the SettingsViewModel, and a sleek, beautifully animated Compose SettingsScreen. Use ListItem components with appropriate Material icons, but style them with our custom tigerGlow modifiers and neon accents so it doesn't look like a generic Android settings menu. Do not use placeholders—write the actual DataStore mapping logic.

📱 8. The Foldable Cover Screen Hub

Goal: Adapt the app for Samsung Z Flip and Motorola Razr outer displays using pure gesture-driven Jetpack Compose UI.

Role & Context
You are a Senior Android Engineer specializing in Foldables, Jetpack WindowManager, and Compose UI. I am building "TigerPlayer," an elite, premium Android music player with a glowing Neon/Cyberpunk aesthetic.

The Objective
I want to make TigerPlayer fully navigable and optimized for the "Cover Screen" of flip phones (like the Samsung Galaxy Z Flip 5/6 and Motorola Razr). Currently, standard apps look broken or cramped on these tiny outer displays.

Please generate the architectural code, Manifest configurations, and Jetpack Compose UI to implement a bespoke "Cover Screen Mode."

Please address the following 3 pillars:

Pillar 1: Android Manifest & Foldable Meta-Data

To allow the app to run natively on the cover screen, provide the exact AndroidManifest.xml modifications required.

Include the specific <meta-data> tags required by Samsung (e.g., com.samsung.android.app.magicinfo or cover screen support flags) and Motorola to whitelist the app for the outer display.

Ensure android:resizeableActivity="true" and the correct configChanges are set so the app doesn't crash when folding/unfolding.

Pillar 2: Jetpack WindowManager State

I need a way for my Compose UI to know if it is currently running on the tiny cover screen or the main unfolded screen.

Write a Compose utility using WindowSizeClass or WindowInfoTracker that dynamically calculates the screen real estate.

Create a derived state (e.g., isCoverScreen) that returns true if the screen height/width indicates a tiny, squarish outer display (usually < 400dp).

Pillar 3: The CoverScreenMiniHub UI

When isCoverScreen is true, the app should completely swap its UI to a specialized CoverScreenMiniHub Composable. Because screen real estate is incredibly limited, this UI must rely on Gestures instead of cluttered buttons.

Background: A tightly cropped, blurred version of the current Album Art with a dark TigerSurfaceCharcoal overlay to ensure text legibility.

Center Stage: The Track Title and Artist Name in bold, glowing typography.

Gesture Controls: Implement pointerInput for the entire screen:

Single Tap: Play/Pause.

Swipe Left: Next Track.

Swipe Right: Previous Track.

Swipe Up: Slide up a miniaturized, transparent Queue list.

Micro-Visualizer: Along the very bottom edge of the screen, place a thin, glowing 10dp high audio-reactive waveform using our audioReactiveFrame data, glowing in TigerNeonOrange.

Output Requirements:
Write clean, production-ready Kotlin. Start with the Manifest flags, then the WindowManager state logic, and finally the completely gesture-driven Compose UI for the cover screen. Do not use placeholder comments for the gesture math.

🧪 9. Pre-Flight QA & Validation

Goal: Catch memory leaks, audio focus bugs, and R8 obfuscation issues before release.

Role & Context
You are a Lead Android QA Automation Engineer and Senior Android Architect. I am preparing for the final release-candidate test run of "TigerPlayer," an elite Android music player built with Jetpack Compose, Media3/ExoPlayer, a custom AdaptiveDspEngine (AudioProcessor), Room DB, and OpenGL ES 3.0 visualizers.

The Objective
We are entering the "Pre-Flight Validation" phase. Before I install the final release build, I need to verify, validate, test, and debug the entire application architecture to guarantee a crash-free, zero-jank experience.

Please generate the Kotlin testing code, debugging configurations, and the step-by-step manual validation matrix for the following 4 pillars:

Pillar 1: Automated Safety Nets (Unit & UI Tests)

I need robust automated tests for my most fragile components. Please provide:

DSP Unit Test: A JUnit4/MockK test for the AdaptiveDspEngine. Write a test that feeds a dummy byte array into queueInput() and asserts that the buffer doesn't overflow and isEnded() behaves correctly.

Room DB Migration & Integrity Test: A test to verify the TigerDao can successfully insert and read an AudioTrack with all fields without SQLite constraint failures.

Compose UI Test: A createComposeRule() test for my FullPlayerScreen that verifies the Play/Pause button correctly toggles state and the TigerNeonOrange styling modifier doesn't crash the render tree.

Pillar 2: Memory Leak & Performance Profiling

My app uses OpenGL and heavy Audio buffers, which are prime suspects for memory leaks.

Provide the setup code to integrate LeakCanary strictly for debug builds.

Write the Android StrictMode configuration for my Application class to flag and log any accidental Disk I/O or Network operations happening on the Main UI Thread.

Give me a brief Jetpack Macrobenchmark script to test the scrolling performance of my QueueScreen LazyColumn.

Pillar 3: The "Edge Case" Debugging Matrix (Manual QA)

Create a comprehensive, step-by-step manual test script that I must perform on my physical device (Samsung S22/Foldable). It must specifically test:

Audio Focus & Transients: What happens when a phone call comes in, a WhatsApp voice note plays, or an alarm goes off?

Hardware Interrupts: What happens when Bluetooth earbuds disconnect mid-song? Does it pause immediately or blast through the phone speakers?

Lifecycle Brutality: What happens if the user swipes the app away from the Recents menu while ExoPlayer is actively running a foreground service?

Pillar 4: Release Build Hardening (ProGuard/R8)

Before I build the signed APK/AAB, I need to ensure shrinking doesn't break the app.

Provide the necessary proguard-rules.pro configurations to prevent R8 from stripping out critical classes for Media3/ExoPlayer, Coil, Retrofit/Gson (for Last.fm/Spotify APIs), and Room.

Output Requirements:
Do not provide generic testing advice. Write the actual Kotlin test classes, the StrictMode implementation, and the exact ProGuard rules required for this specific modern Android stack.