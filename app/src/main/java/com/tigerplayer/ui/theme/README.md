# Design System & Theming Sub-compartment (`ui/theme/`)

This directory houses the **Arcane Glass** design system, typography, color palettes, frosted glass modifiers, and dynamic color extraction engines of TigerPlayer.

---

## 1. Plain-English Overview

Think of the **Design System Sub-compartment** as the "architect, lighting director, and stylist":
- It defines the visual identity of TigerPlayer: deep AMOLED black backgrounds, glowing neon borders, and elegant frosted translucent glass cards that look like premium hi-fi audio equipment.
- It adapts dynamically to your music: when an album cover is warm sunset orange, the player buttons and background blurs softly shift to match the album's exact mood.
- It protects your eyes and legibility: it automatically checks the contrast of every color to guarantee you can always read song titles and artist names in bright daylight or dark rooms.

---

## 2. Sub-compartment Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│ ui/theme/ Architecture                                                 │
│                                                                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Core Design Foundation                                              │ │
│ │  ├── Theme.kt          (TigerPlayerTheme root & CompositionLocals) │ │
│ │  ├── Color.kt          (Arcane Glass Neon palette & deep blacks)   │ │
│ │  ├── Type.kt           (Clean typographic hierarchy)               │ │
│ │  └── Shape.kt          (Pill shapes & 12dp/16dp rounded corners)   │ │
│ └──────────────────────────────────┬─────────────────────────────────┘ │
│                                    │                                   │
│ ┌──────────────────────────────────┼─────────────────────────────────┐ │
│ │ Dynamic Color & Accessibility    │ Glass Components & Modifiers    │ │
│ │  ├── DominantColorExtractor      │  ├── PremiumGlassCard           │ │
│ │  │   (Palette extractor)         │  │   (Translucent card container│ │
│ │  ├── ColorSafety                 │  ├── Modifiers.kt               │ │
│ │  │   (WCAG contrast validator)   │  │   (Glass blur & neon borders)│ │
│ │  └── DynamicNeonLocals           │  └── GlassComponents.kt         │ │
│ │      (Reactive color overrides)  │      (Buttons, badges, sheets)  │ │
│ └──────────────────────────────────┴─────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Core Capabilities & Components

### Arcane Glass Design Standard
* **`PremiumGlassCard`**: High-performance translucent card surface featuring:
  - Frosted background blur (utilizing `RenderEffect` on Android 12+ / API 31+).
  - Semi-transparent gradient fills (`Color.White.copy(alpha = 0.08f)`).
  - Subtle 1dp inner border stroke with specular highlight gradients.
* **`Modifiers.kt`**: Reusable modifiers for `.glassmorphic()`, `.neonGlow()`, and `.shimmer()`.

### Dynamic Color Extraction (`DominantColorExtractor`)
* **Mechanism:** Leverages Android's `Palette` library to extract vibrant, muted, and dominant color swatches from album artwork bitmaps.
* **Ambient Lighting:** Generates multi-stop radial gradient backdrops (`rememberTigerAmbientGradient`) that illuminate the screen based on the current album art.

### Accessibility & Contrast Guardrails (`ColorSafety`)
* **WCAG Contrast Invariant:** Ensures that all dynamically extracted colors meet minimum WCAG AA contrast ratios ($4.5:1$ for normal text, $3.0:1$ for large text) against deep AMOLED black surfaces.
* **Luminance Clamping:** Automatically boosts the luminance of dark colors or softens harsh high-saturation neon values before applying them to text or interactive controls.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Theme Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Glassmorphism** | A design style featuring translucent frosted glass surfaces. | Powers `PremiumGlassCard` and player sheets for a modern look. | UI looks flat or dated if transparency effects fail. |
| **RenderEffect** | Android tool that blurs views directly on the graphics card (GPU). | Blurs background artwork smoothly beneath glass cards. | Choppy scrolling or lag if software blurs are used instead. |
| **WCAG Contrast Ratio** | Accessibility standard measuring how readable text is against its background. | `ColorSafety` validates extracted colors against black backgrounds. | Text becomes unreadable or invisible on certain album color themes. |
| **CompositionLocal** | A way to share design themes across Compose screens without passing variables everywhere. | Distributes theme colors (`LocalNeonColor`, `LocalGlassAlpha`) globally. | Components cannot access active theme colors. |
| **AMOLED Black** | Pure pitch-black color (`#000000`) where OLED screen pixels turn completely off. | Used as the foundation of the app to save battery and boost contrast. | Higher battery consumption on OLED displays if dark gray is used. |
