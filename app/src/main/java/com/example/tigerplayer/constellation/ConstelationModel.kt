
package com.example.tigerplayer.constellation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color




// ------------------------------------------
// 1. RAW DATA LAYER (From Database/Network)
// ------------------------------------------




// ------------------------------------------
// 2. LAYOUT LAYER (Math Applied)
// ------------------------------------------

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


data class ConstellationGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val density: Float = 1f,
    val seed: Long = 0L
)

data class GraphNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val playCount: Int = 0,
    val imageUrl: String? = null,
    val parentId: String? = null,
    val importance: Float = 0.5f,
    val audioEnergyBias: Float = 0.5f
)

data class GraphEdge(val sourceId: String, val targetId: String, val strength: Float)

enum class NodeType { GALAXY_CORE, ARTIST, ALBUM, TRACK }
enum class VisualDepth { CORE, MID, BACKGROUND }
enum class OrbitMode { STATIC, GRAVITY_WELL, DYNAMIC, FLOW_FIELD }

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
    val trailLength: Float
)
