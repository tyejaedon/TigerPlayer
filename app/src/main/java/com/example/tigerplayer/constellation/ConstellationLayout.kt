package com.example.tigerplayer.constellation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*
import kotlin.random.Random

/**
 * THE SUPREME ORBITAL LAYOUT ENGINE
 * Transforms an abstract Graph into a physically-mapped Galaxy.
 * Orchestrates Fibonacci spirals for artists and gravitational orbits for albums.
 */
@Singleton
class OrbitalLayoutEngine @Inject constructor() {

    /**
     * Maps a Graph to a list of PositionedNodes.
     * Note: This accepts the [ConstellationGraph] object. Reactive Flow handling
     * should occur at the ViewModel layer.
     */
    fun layout(graph: ConstellationGraph): List<PositionedNode> {
        val result = mutableListOf<PositionedNode>()
        val random = Random(if (graph.seed != 0L) graph.seed else System.currentTimeMillis())

        val galaxyCenter = Offset(0f, 0f)

        val palette = listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFF4FC3F7), // Aard Blue
            Color(0xFFFF5252), // Igni Red
            Color(0xFFB388FF), // Purple
            Color(0xFF1DB954), // Spotify Green
            Color(0xFF80DEEA), // Cyan
            Color(0xFFFF8A65)  // Coral
        )

        /* -----------------------------------
           🌌 1. GALAXY CORE (Gravitational Center)
        ----------------------------------- */
        graph.nodes.find { it.type == NodeType.GALAXY_CORE && it.parentId == null }?.let { core ->
            result.add(
                PositionedNode(
                    id = core.id,
                    label = core.label,
                    type = core.type,
                    playCount = core.playCount,
                    imageUrl = core.imageUrl,
                    weight = core.importance * 1000f,
                    orbitCenterId = null,
                    baseOrbitCenter = galaxyCenter,
                    orbitRadius = 0f,
                    baseAngle = 0f,
                    orbitSpeed = 0f,
                    color = Color.White,
                    depth = VisualDepth.CORE,
                    scale = 1.5f + (core.importance * 0.5f),
                    glow = 1.0f,
                    orbitMode = OrbitMode.STATIC,
                    gravityInfluence = 10f,
                    mass = core.importance * 1000f,
                    fluidInfluence = 0.5f,
                    energy = core.audioEnergyBias,
                    trailLength = 0f
                )
            )
        }

        /* -----------------------------------
           🌠 2. ARTIST LAYER (Fibonacci Spiral Arms)
        ----------------------------------- */
        val artists = graph.nodes.filter { it.type == NodeType.ARTIST }
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        val baseRadius = 2800f

        artists.forEachIndexed { i, artist ->
            val spiralFactor = i.toFloat() * 0.35f
            val radius = baseRadius * sqrt(i + 1f) + random.nextFloat() * 300f
            val angle = (i * goldenAngle + spiralFactor).toFloat()
            val color = palette.random(random)

            result.add(
                PositionedNode(
                    id = artist.id,
                    label = artist.label,
                    type = artist.type,
                    playCount = artist.playCount,
                    imageUrl = artist.imageUrl,
                    weight = artist.importance * 100f,
                    orbitCenterId = null,
                    baseOrbitCenter = galaxyCenter,
                    orbitRadius = radius,
                    baseAngle = angle,
                    orbitSpeed = (0.0008f + random.nextFloat() * 0.0006f),
                    color = color,
                    depth = VisualDepth.CORE,
                    scale = 1.0f + (artist.importance * 0.8f),
                    glow = 0.8f * artist.audioEnergyBias,
                    orbitMode = OrbitMode.GRAVITY_WELL,
                    gravityInfluence = artist.importance * 5f,
                    mass = artist.importance * 100f,
                    fluidInfluence = 0.8f,
                    energy = artist.audioEnergyBias,
                    trailLength = 0.8f
                )
            )
        }

        /* -----------------------------------
           🪐 3. ALBUM LAYER (Orbital Chains)
        ----------------------------------- */
        val albums = graph.nodes.filter { it.type == NodeType.ALBUM }
        albums.groupBy { it.parentId }.forEach { (artistId, group) ->
            val parent = result.find { it.id == artistId } ?: return@forEach
            val orbitStep = (2 * PI) / group.size.coerceAtLeast(1)

            group.forEachIndexed { i, album ->
                val radius = 700f + (i * 220f) + (random.nextFloat() * 120f)

                result.add(
                    PositionedNode(
                        id = album.id,
                        label = album.label,
                        type = album.type,
                        playCount = album.playCount,
                        imageUrl = album.imageUrl,
                        weight = album.importance * 60f,
                        orbitCenterId = artistId,
                        baseOrbitCenter = galaxyCenter,
                        orbitRadius = radius,
                        baseAngle = (i * orbitStep).toFloat(),
                        orbitSpeed = 0.006f + random.nextFloat() * 0.004f,
                        color = parent.color.copy(alpha = 0.85f),
                        depth = VisualDepth.MID,
                        scale = 0.8f + (album.importance * 0.5f),
                        glow = 0.5f * album.audioEnergyBias,
                        orbitMode = OrbitMode.DYNAMIC,
                        gravityInfluence = album.importance * 2f,
                        mass = album.importance * 50f,
                        fluidInfluence = 0.6f,
                        energy = album.audioEnergyBias,
                        trailLength = 0.5f
                    )
                )
            }
        }

        /* -----------------------------------
           ☄️ 4. TRACK LAYER (Debris Field)
        ----------------------------------- */
        graph.nodes.filter { it.type == NodeType.TRACK }.groupBy { it.parentId }.forEach { (parentId, group) ->
            val step = (2 * PI) / group.size.coerceAtLeast(1)

            group.forEachIndexed { i, track ->
                val radius = 300f + (i * 90f) + random.nextFloat() * 60f

                result.add(
                    PositionedNode(
                        id = track.id,
                        label = track.label,
                        type = track.type,
                        playCount = track.playCount,
                        imageUrl = track.imageUrl,
                        weight = track.importance * 30f,
                        orbitCenterId = parentId,
                        baseOrbitCenter = galaxyCenter,
                        orbitRadius = radius,
                        baseAngle = (i * step).toFloat() + random.nextFloat(),
                        orbitSpeed = 0.02f + random.nextFloat() * 0.01f,
                        color = Color.White.copy(alpha = 0.6f),
                        depth = VisualDepth.BACKGROUND,
                        scale = 0.5f + (track.importance * 0.4f),
                        glow = 0.3f * track.audioEnergyBias,
                        orbitMode = OrbitMode.FLOW_FIELD,
                        gravityInfluence = 0.1f,
                        mass = track.importance * 20f,
                        fluidInfluence = 1.0f,
                        energy = track.audioEnergyBias,
                        trailLength = 0.3f
                    )
                )
            }
        }

        return result
    }
}

/* -----------------------------------
   🛠 DATA STRUCTURES
----------------------------------- */
