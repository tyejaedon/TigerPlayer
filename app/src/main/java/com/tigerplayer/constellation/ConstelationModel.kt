
package com.tigerplayer.constellation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

// ------------------------------------------
// 1. RAW DATA LAYER (From Database/Network)
// ------------------------------------------

/**
 * Encapsulates orbital motion specifications for a celestial node in the constellation.
 *
 * @property radius Distance in spatial units (pixels) from the orbital center around which the node revolves.
 * @property angle Initial or current rotational angle (in radians) along the orbital ellipse/circle.
 * @property speed Angular velocity (radians per time step) controlling orbital rotation speed.
 * @property eccentricity Elliptical distortion factor (0.0 for a perfect circle, up to near 1.0 for elongated orbits).
 * @property phaseOffset Initial angular phase shift (in radians) to stagger nodes along shared orbital paths and prevent clustering.
 * @property damping Motion stabilization factor (0.0 - 1.0) applied to damp kinetic oscillations over time.
 * @property color Visual tint assigned to the orbital ring and associated celestial entity.
 */
data class OrbitParams(
    val radius: Float,
    val angle: Float,
    val speed: Float,

    // NEW: orbital physics
    val eccentricity: Float = 0.0f,   // ellipse distortion
    val phaseOffset: Float = 0f,

    // NEW: damping / motion stability
    val damping: Float = 0.98f,

    val color: Color
)

/**
 * Represents the complete graph structure of the user's music library transformed into a celestial constellation.
 *
 * @property nodes List of all celestial entities ([GraphNode]) in the graph (Core, Artists, Albums, Tracks).
 * @property edges List of structural and semantic relationships ([GraphEdge]) connecting nodes in the graph.
 * @property density Global spatial scaling multiplier governing how tightly or sparsely nodes are packed.
 * @property seed Random seed used for deterministic spatial layout generation across app launches.
 */
data class ConstellationGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val density: Float = 1f,
    val seed: Long = 0L
)

/**
 * Unpositioned raw domain node representing a music entity within the constellation graph.
 *
 * @property id Unique string identifier (e.g., artist ID, album key, or track UUID).
 * @property label Display title or name shown in the visualizer UI (e.g., Artist name, Album title, Track name).
 * @property type Structural hierarchy classification ([NodeType]) determining node behavior and visuals.
 * @property playCount Playback frequency used to derive importance, mass, and visual scaling.
 * @property imageUrl Optional artwork thumbnail URL or local URI rendered on or alongside the node.
 * @property parentId ID of the parent node (e.g., Artist ID for Albums; Album ID for Tracks) establishing hierarchy.
 * @property importance Computed priority weight (0.0 to 1.0) derived from playback metrics, affecting mass and gravity.
 * @property audioEnergyBias Acoustic energy level (0.0 to 1.0) derived from audio analysis, driving pulse and glow effects.
 * @property artistName Populated for TRACK nodes so the UI can show the artist name alongside song stats.
 * @property topTracks Populated for ARTIST nodes with their top most-played local tracks for UI popups.
 */
data class GraphNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val playCount: Int = 0,
    val imageUrl: String? = null,
    val parentId: String? = null,
    val importance: Float = 0.5f,
    val audioEnergyBias: Float = 0.5f,
    /** Populated for TRACK nodes so the UI can show "artist name" alongside a song's stats. */
    val artistName: String? = null,
    /** Populated for ARTIST nodes: their top 3 most-played local songs. */
    val topTracks: List<TopTrackEntry> = emptyList()
)

/**
 * A single ranked song entry, used to surface an artist's top songs in the UI detail popup.
 *
 * @property title Display title of the top track.
 * @property playCount Lifetime playback count of the track used for ranking.
 */
data class TopTrackEntry(val title: String, val playCount: Int)

/**
 * Represents a directed link or gravitational attraction line between two nodes in the graph.
 *
 * @property sourceId Unique identifier of the originating node.
 * @property targetId Unique identifier of the target node.
 * @property strength Link connection strength (0.0 to 1.0) determining line thickness, opacity, and pull force.
 */
data class GraphEdge(val sourceId: String, val targetId: String, val strength: Float)

/**
 * Structural hierarchy classification of a celestial node.
 */
enum class NodeType {
    /** Central supermassive gravitational hub of the music galaxy. */
    GALAXY_CORE,

    /** Major star systems forming spiral arms based on artist play volume and importance. */
    ARTIST,

    /** Planetary bodies orbiting around their respective artist parent star. */
    ALBUM,

    /** Satellite debris / smaller cosmic bodies orbiting around album or artist parents. */
    TRACK
}

/**
 * Visual depth sorting layer for parallax rendering and depth-of-field effects.
 */
enum class VisualDepth {
    /** Foreground focal layer rendering crisp, large, high-detail nodes. */
    CORE,

    /** Midground orbital layer rendering standard scale and opacity nodes. */
    MID,

    /** Background cosmic dust layer rendering subtle, smaller, translucent nodes. */
    BACKGROUND
}

/**
 * Movement simulation mode dictating how physics updates govern a node's motion.
 */
enum class OrbitMode {
    /** Stationary node fixed at coordinates without revolution (e.g., Galaxy Core). */
    STATIC,

    /** Primary orbital motion governed by gravitational attraction to a central hub (e.g., Artists). */
    GRAVITY_WELL,

    /** Dynamic multi-body elliptical orbit revolving around a moving parent center (e.g., Albums). */
    DYNAMIC,

    /** Micro-satellite motion guided by simulated fluid turbulence and vector fields (e.g., Tracks). */
    FLOW_FIELD
}

// ------------------------------------------
// 2. LAYOUT LAYER (Math Applied)
// ------------------------------------------

/**
 * Fully calculated and physically mapped node ready for dynamic 60/120 FPS rendering.
 *
 * @property id Unique string identifier matching the underlying [GraphNode].
 * @property label Display label (artist name, album title, or track name) rendered on canvas.
 * @property type Structural hierarchy classification ([NodeType]) dictating render style and motion rules.
 * @property playCount Playback frequency used for relative sizing, mass, and prominence.
 * @property imageUrl Optional artwork thumbnail URL or local URI for rendering node avatars.
 * @property weight Computed physics weight derived from importance, affecting gravitational pull calculations.
 * @property orbitCenterId Unique ID of the parent node acting as the dynamic center of rotation (null for galaxy center).
 * @property baseOrbitCenter Baseline spatial coordinates [Offset] (x, y) used when no parent orbit center is specified.
 * @property orbitRadius Radius distance in pixels from the orbit center to the node's revolving position path.
 * @property baseAngle Primary orientation angle (in radians) orienting the ellipse axis in spatial coordinates.
 * @property orbitSpeed Angular rotation velocity (radians per continuous time unit) for revolving motion.
 * @property color Primary color used for node core body, halo glow, and connection edge lines.
 * @property depth Depth layer ([VisualDepth]) controlling z-index draw order, scale factors, and opacity.
 * @property scale Visual rendering multiplier adjusting node diameter on the canvas.
 * @property glow Radial aura/bloom intensity multiplier (0.0 to 1.0) applied to node shader glow effects.
 * @property orbitMode Movement simulation mode ([OrbitMode]) specifying update rules in [ConstellationPhysics].
 * @property gravityInfluence Gravitational pull magnitude exerted on neighboring or child nodes.
 * @property mass Physical mass parameter utilized in impulse, kinetic momentum, and damping calculations.
 * @property fluidInfluence Sensitivity factor to audio-reactive turbulence fields and fluid vector forces.
 * @property energy Dynamic audio energy level driving real-time harmonic pulse and micro-breathing effects.
 * @property trailLength Visual multiplier for motion blur and particle trail rendering behind moving nodes.
 * @property eccentricity Elliptical distortion ratio (0.0 = perfect circle, up to 1.0 = elongated ellipse).
 * @property phaseOffset Angular phase shift (in radians) preventing orbital overlap along identical radii.
 * @property artistName Owning artist's display name populated for TRACK nodes to display in UI detail popups.
 * @property topTracks List of top 3 most-played tracks populated for ARTIST nodes to surface in detail popups.
 */
data class PositionedNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val playCount: Int,
    val imageUrl: String?,
    val weight: Float,
    val orbitCenterId: String?,
    val baseOrbitCenter: Offset,
    val orbitRadius: Float,
    val baseAngle: Float,
    val orbitSpeed: Float,
    val color: Color,
    val depth: VisualDepth,
    val scale: Float,
    val glow: Float,
    val orbitMode: OrbitMode,
    val gravityInfluence: Float,
    val mass: Float,
    val fluidInfluence: Float,
    val energy: Float,
    val trailLength: Float,
    val eccentricity: Float = 0.0f,
    val phaseOffset: Float = 0.0f,
    /** Populated for TRACK nodes: the owning artist's display name. */
    val artistName: String? = null,
    /** Populated for ARTIST nodes: their top 3 most-played local songs. */
    val topTracks: List<TopTrackEntry> = emptyList()
)

