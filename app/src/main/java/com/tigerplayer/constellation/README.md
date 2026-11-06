# TigerPlayer Constellation Spatial Compartment (`constellation/`)

This directory contains the data models, layout algorithms, and cluster calculations for the **Constellation Visualizer** — an interactive 2D star-map graph that maps the user's music library into celestial clusters based on acoustic metadata and genre similarity.

---

## 1. Plain-English Overview

Think of the **Constellation Compartment** as the "galaxy map generator" of TigerPlayer:
- Instead of showing your music in a simple flat list or grid, it places your songs and artists as bright stars and planets in a interactive night-sky map.
- Songs with similar acoustic characteristics (e.g. upbeat electronic tracks, quiet acoustic pieces, heavy bass rock) are gravitated into nearby constellations.
- Tapping on a star lets you explore interconnected songs and play tracks directly from the cosmic node.

---

## 2. Architecture & Graph Generation

```
┌────────────────────────────────────────────────────────────────────────┐
│ Library & Metadata Sources (LibraryEngine, MetadataEngine)            │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Constellation Engine (`constellation/`)                                │
│                                                                        │
│ ┌──────────────────────┐  ┌───────────────────┐  ┌───────────────────┐ │
│ │ ConstellationModel   │  │ ConstellationData │  │ConstellationLayout│ │
│ │ - Node & Link models │  │ - Cluster mapper  │  │ - Physics simulation│ │
│ │ - Spectral coordinates│ │ - Acoustic grouping│ │ - Velocity vectors │ │
│ └──────────────────────┘  └───────────────────┘  └───────────────────┘ │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ UI Render Surface (`ui/constellation/Constellation.kt`)               │
│ - Interactive GPU/Canvas interactive celestial view                    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Core Components & Responsibilities

### `ConstelationModel.kt`
* **Purpose:** Core domain data models for nodes, links, and constellation metadata.
* **Key Types:**
  - `ConstellationNode`: Represents a track or artist node with position `(x, y)`, velocity `(vx, vy)`, radius, energy, and cluster ID.
  - `ConstellationLink`: Represents gravitational or semantic links connecting related nodes.
  - `ConstellationCluster`: Represents a galaxy cluster grouping related tracks.

### `ConstellationData.kt`
* **Purpose:** Ingests `AudioTrack` library items and transforms them into spatial node graphs.
* **Key Functions:**
  - Groups tracks by genre, artist, or acoustic similarity.
  - Maps metadata parameters (BPM, spectral energy, valence) into coordinate seeds.

### `ConstellationLayout.kt`
* **Purpose:** Executes 2D force-directed physics simulations (repulsion, spring attraction, center gravity).
* **Key Algorithm:**
  - Calculates electrostatic repulsion between adjacent nodes to prevent overlapping.
  - Applies Hooke's Law spring forces along links to keep related tracks grouped together.
  - Damps node velocity over time to reach an equilibrium layout.

---

## 4. Performance & Threading Guidelines

1. **Off-Main Calculation:** Physics layout iterations (`ConstellationLayout`) run on background coroutines (`Dispatchers.Default`) to avoid blocking Compose UI rendering.
2. **Coordinate Normalization:** Positions are computed in normalized `[0.0, 1.0]` virtual space and scaled to screen resolution during render time.
3. **Allocation Rules:** Pre-allocate velocity vectors and node arrays during graph construction to prevent garbage collection pauses during pan/zoom gestures.

---

## 5. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Constellation Compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Force-Directed Layout** | A physics simulation algorithm that positions graph nodes using virtual spring and gravity forces. | Spatially positions music tracks so similar songs form visible clusters. | Nodes overlap into an unreadable clutter or explode off-screen. |
| **Repulsion Force** | Simulated electrostatic push between nodes. | Prevents song nodes from overlapping on top of each other. | Cluttered nodes that cannot be tapped individually. |
| **Damping** | Frictional velocity reduction applied over simulation steps. | Settles the star-map into a steady state after user interactions. | Star map continues shaking or moving indefinitely. |
