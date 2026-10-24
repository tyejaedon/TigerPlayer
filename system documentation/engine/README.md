# Architecture Plans & System Documentation

Welcome to the TigerPlayer Engine Architecture & System Documentation hub.

The architecture roadmap has been modularized into separate, dedicated plan files for maximum clarity, focus, and maintainability.

---

## Plan Files Index

| Plan Module | File | Objectives & Scope |
| :--- | :--- | :--- |
| **Phase 1: API Cleanup** | [`plan_01_api_cleanup.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/plan_01_api_cleanup.md) | Removes dead/redundant Retrofit APIs (`ITunesApi`, `WikipediaApi`, `SubsonicApi`) and migrates YouTube search to keyless Piped/InnerTube provider to eliminate 10k daily quota limits. |
| **Phase 2: History Analytics** | [`plan_02_analytics_engine.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/plan_02_analytics_engine.md) | Implements Time-of-Day track profiles, weekly skip rate analytics, and daily mood/energy profiles at the logic layer without Room schema migrations. |
| **Phase 3: Smart Playlists** | [`plan_03_smart_playlists.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/plan_03_smart_playlists.md) | Builds an on-device 8D feature-vector matching engine (`TrackFeatureVector`) for zero-cost, offline "AI-like" smart playlist generation. |
| **Phase 4: Multi-Band Waveform** | [`plan_04_multiband_waveform.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/plan_04_multiband_waveform.md) | Complete Option 2 design for contiguous 2-band (Bass vs. Treble) waveform extraction and dual-color Serato/Rekordbox-style Compose UI rendering. |
| **Phase 5: DSP Verification** | [`plan_05_dsp_verification.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/plan_05_dsp_verification.md) | Zero-allocation verification and thermal stability gates for SVF EQ, Prism 3-stem isolator, Schroeder Hall reverb, and FFT spectral analyzer. |
| **Phase 6: Equalizer & DSP Hub** | [`plan_06_equalizer_redesign.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/plan_06_equalizer_redesign.md) | Redesigns EQ controls into a unified Equalizer & DSP Hub inside Settings (fader sliders, live response curves, Aural Nexus 2D canvas, and acoustic environments). |
| **Phase 7: Bluetooth Acoustic Nexus** | [`plan_07_bluetooth_acoustic_nexus.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/plan_07_bluetooth_acoustic_nexus.md) | Blends Bluetooth hardware state with acoustic DSP profiles, high-res codec resolution (LDAC/aptX), spring-physics 3D earbud animations, and audio-reactive aura glows. |

---

## Detailed Technical Specifications

- **DSP Architecture Specifications**: [`dspengine.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/dspengine.md)
- **Analytics Technical Specifications**: [`analytics.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/analytics.md)
- **Smart Playlist Blueprint**: [`ai_playlist_blueprint.md`](file:///Users/tyejaedon/StudioProjects/TigerPlayer/system%20documentation/engine/ai_playlist_blueprint.md)
