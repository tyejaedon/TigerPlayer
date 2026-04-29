package com.example.tigerplayer.constellation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.random.Random

@Singleton
class OrbitalLayoutEngine @Inject constructor() {

    /**
     * Recalculates all orbital distances and angles to create a massively spread-out,
     * anti-clumping Golden Spiral galaxy. Items inside clusters are incrementally distanced.
     */
    fun layout(graph: ConstellationGraph): List<PositionedNode> {
        val processedNodes = mutableListOf<PositionedNode>()
        val random = Random(System.currentTimeMillis())

        val colorPalette = listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFF4FC3F7), // Aard Blue
            Color(0xFFFF5252), // Igni Red
            Color(0xFFB388FF), // Neural Purple
            Color(0xFF1DB954)  // Spotify Green
        )

        // 0. Set the Core
        val core = graph.nodes.find { it.type == NodeType.GALAXY_CORE }
        val rogueGalaxyCenter = Offset(0f, 15000f) // Just pushing rogue center way down to prevent overlap

        if (core != null) {
            processedNodes.add(
                PositionedNode(
                    id = core.id, label = core.label, type = core.type,
                    playCount = core.playCount, imageUrl = core.imageUrl,
                    weight = 0f, orbitCenterId = null, baseOrbitCenter = rogueGalaxyCenter,
                    orbitRadius = 0f, baseAngle = 0f, orbitSpeed = 0f, color = Color.Transparent
                )
            )
        }

        // 1. VAST MACRO-GALAXY: Spacing out ARTISTS using a Golden Ratio Spiral
        val artists = graph.nodes.filter { it.type == NodeType.ARTIST }
        val goldenAngle = 2.39996f // 137.5 degrees (Perfect natural distribution)

        artists.forEachIndexed { index, artist ->
            // Base radius 3000 + massive 1200 padding per artist
            val expandedRadius = 3000f + (index * 1200f) + (random.nextFloat() * 600f)
            val spiralAngle = index * goldenAngle
            val speed = if (index % 2 == 0) 0.001f else -0.001f // Slow, majestic macro orbits
            val starColor = colorPalette.random(random)

            processedNodes.add(
                PositionedNode(
                    id = artist.id, label = artist.label, type = artist.type,
                    playCount = artist.playCount, imageUrl = artist.imageUrl,
                    weight = 100f, orbitCenterId = null, baseOrbitCenter = Offset.Zero,
                    orbitRadius = expandedRadius, baseAngle = spiralAngle, orbitSpeed = speed,
                    color = starColor
                )
            )
        }

        // 2. WIDE CLUSTERS: Spacing out ALBUMS around Artists
        val albums = graph.nodes.filter { it.type == NodeType.ALBUM }
        val albumsByArtist = albums.groupBy { it.parentId }

        albumsByArtist.forEach { (artistId, albumGroup) ->
            val step = (2 * PI) / albumGroup.size
            albumGroup.forEachIndexed { index, album ->
                // Base 1000 + 400 padding per album so covers never touch
                val expandedRadius = 1000f + (index * 400f) + (random.nextFloat() * 200f)
                val speed = 0.008f + (random.nextFloat() * 0.005f)
                val starColor = processedNodes.find { it.id == artistId }?.color ?: Color.White

                processedNodes.add(
                    PositionedNode(
                        id = album.id, label = album.label, type = album.type,
                        playCount = album.playCount, imageUrl = album.imageUrl,
                        weight = 60f, orbitCenterId = artistId, baseOrbitCenter = Offset.Zero,
                        orbitRadius = expandedRadius, baseAngle = (index * step).toFloat() + random.nextFloat(), orbitSpeed = speed,
                        color = starColor.copy(alpha = 0.8f)
                    )
                )
            }
        }

        // 3. SCATTERED DEBRIS: Spacing out TRACKS around Albums/Core
        val tracks = graph.nodes.filter { it.type == NodeType.TRACK }
        val tracksByParent = tracks.groupBy { it.parentId }

        tracksByParent.forEach { (parentId, trackGroup) ->
            val step = (2 * PI) / trackGroup.size
            trackGroup.forEachIndexed { index, track ->
                // Base 500 + 150 padding per track
                val expandedRadius = 500f + (index * 150f) + (random.nextFloat() * 80f)
                val speed = 0.02f + (random.nextFloat() * 0.015f)

                processedNodes.add(
                    PositionedNode(
                        id = track.id, label = track.label, type = track.type,
                        playCount = track.playCount, imageUrl = track.imageUrl,
                        weight = 40f, orbitCenterId = parentId, baseOrbitCenter = rogueGalaxyCenter,
                        orbitRadius = expandedRadius, baseAngle = (index * step).toFloat() + random.nextFloat(), orbitSpeed = speed,
                        color = Color.White
                    )
                )
            }
        }

        // Catch-all safety net for any unmapped nodes
        val mappedIds = processedNodes.map { it.id }.toSet()
        val remaining = graph.nodes.filter { it.id !in mappedIds }
        remaining.forEach { node ->
            processedNodes.add(
                PositionedNode(
                    id = node.id, label = node.label, type = node.type,
                    playCount = node.playCount, imageUrl = node.imageUrl,
                    weight = 30f, orbitCenterId = null, baseOrbitCenter = Offset.Zero,
                    orbitRadius = 2000f, baseAngle = random.nextFloat() * 6f, orbitSpeed = 0.01f,
                    color = Color.Gray
                )
            )
        }

        return processedNodes
    }
}