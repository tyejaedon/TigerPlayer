package com.example.tigerplayer.constellation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class NodeType {
    GALAXY_CORE,
    ARTIST,
    ALBUM,
    TRACK
}

// ------------------------------------------
// 1. RAW DATA LAYER (From Database/Network)
// ------------------------------------------
data class GraphNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val playCount: Int,
    val imageUrl: String?,
    val parentId: String? = null // Defines hierarchical ownership (e.g., Album belongs to Artist)
)

data class GraphEdge(
    val sourceId: String,
    val targetId: String,
    val strength: Float
)

data class ConstellationGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

// ------------------------------------------
// 2. LAYOUT LAYER (Math Applied)
// ------------------------------------------
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
    val color: Color
)