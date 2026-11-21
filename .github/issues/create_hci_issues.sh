#!/bin/bash

# Issue 1
echo "Creating Issue 1..."
gh issue create --title "Fix DSP state side-effects caused by Equalizer Hub tab navigation" \
  --label "area:ui,area:dsp,tech-debt,P1-high" \
  --body "### Background
In \`EqualizerHubViewModel.kt\`, clicking the \`SPATIAL_NEXUS\` tab instantly changes the underlying \`activeEngineSource\` on the DSP. UI Tabs should be for navigation only. Using them as state-mutating actions creates massive user confusion because looking at a tab silently disables the active PEQ.

### Acceptance Criteria
- [ ] Remove the state-mutating side-effect in \`EqualizerHubViewModel.selectStudioTab()\`.
- [ ] Add explicit \"Enable/Bypass\" toggles to the Spatial Nexus and PEQ views.
- [ ] Implement a persistent \"DSP Signal Chain\" UI pill at the top of the EQ Hub (e.g., \`[AutoEq] -> [PEQ] -> [Reverb]\`) that accurately reflects the \`AdaptiveDspEngine\`'s active blocks.
- [ ] Ensure navigating between tabs no longer interrupts or alters audio playback unless an explicit toggle is flipped."

# Issue 2
echo "Creating Issue 2..."
gh issue create --title "Implement proactive AutoEq smart card on Home Screen for newly connected headphones" \
  --label "area:ui,differentiator,P2-medium" \
  --body "### Background
\`AutoEqRepository\` currently identifies connected Bluetooth devices, but this information is buried deep inside the EQ Hub under an \"Auto-Match\" text button. We need to surface this magic to the user via Contextual Anticipation.

### Acceptance Criteria
- [ ] Create a new \`AutoEqSmartCard\` Composable.
- [ ] Inject \`AudioDeviceEngine\` and \`AutoEqRepository\` into the \`HomeViewModel\`.
- [ ] When a new audio device connects, if \`AutoEqRepository.findBestMatch()\` finds a hit, surface the \`AutoEqSmartCard\` at the top of the Home Screen.
- [ ] The card must display the device name, a brief explanation, and an [Apply Studio Flat Profile] button.
- [ ] Applying the profile from the card must configure the \`AdaptiveDspEngine\` without requiring the user to open the EQ Hub.
- [ ] Add a dismiss action to hide the card."

# Issue 3
echo "Creating Issue 3..."
gh issue create --title "Extract Acoustic Space (Vinyl/Concert Hall) out of EQ Hub into Home Screen quick actions" \
  --label "area:ui,parity,P2-medium" \
  --body "### Background
Acoustic environments (\"Vinyl Warmth\", \"Concert Hall\") are experiential moods, not precision tuning tools. Forcing users into a 10-band technical EQ screen to change a mood adds unnecessary friction.

### Acceptance Criteria
- [ ] Remove the \`ACOUSTIC_SPACE\` tab entirely from \`EqualizerHubView\`.
- [ ] Extract the existing \`AcousticSpacePanel\` logic into a swipeable/scrollable carousel card component for the Home Screen.
- [ ] Toggling \"Vinyl Warmth\" or \"Concert Hall\" from the Home Screen must immediately apply the DSP effect via \`PlaybackPrefs\` / \`AdaptiveDspEngine\`.
- [ ] Ensure the UI clearly indicates which mood is currently active on the Home screen."

# Issue 4
echo "Creating Issue 4..."
gh issue create --title "Relocate FluidRenderEngine Spatial Nexus to a dedicated full-screen immersive view" \
  --label "area:ui,differentiator,P2-medium" \
  --body "### Background
The \`FluidRenderEngine.kt\` runs a heavy 60/120fps OpenGL Navier-Stokes fluid simulation. Currently, it is trapped inside a small tab inside the technical EQ menu. This wastes its visual potential and unnecessarily burns battery when users are just trying to tweak a frequency band.

### Acceptance Criteria
- [ ] Remove \`SPATIAL_NEXUS\` from the \`EqualizerHubView\` tabs.
- [ ] Create a dedicated \`ImmersiveVisualizerScreen\` that hosts the \`FluidRenderEngine\` / \`GLSurfaceView\`.
- [ ] Add an entry point to this new screen directly from the \"Now Playing\" view (e.g., an \"Immersive Mode\" or \"Visualizer\" button).
- [ ] Allow users to interact with the spatial audio nodes over the fluid simulation in this new full-screen view.
- [ ] Ensure \`FluidRenderEngine\` resources are paused/released properly when the user leaves the full-screen view."

echo "Done!"
